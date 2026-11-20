// MAM-62 end to end: the real Keycloak realm pem (sign-in, sign-out) in a real browser; the gateway answers with synthetic
// data (e2e/support/gateway.ts). Needs the stack's Keycloak on http://localhost:8180 (make up) and the built web UI on 5173.
import { expect, test } from '@playwright/test';
import { APPLICATION_ID, GATEWAY, mockGateway, PROVIDER_NPI } from './support/gateway';
import { claimsOf, KEYCLOAK_AUTHORIZE, signIn, signOut, type PemUser } from './support/keycloak';
import { expectNoAxeViolations, expectShellStructure, focusedName, focusIsVisible, menuEntries, tabTo } from './support/page';

// The Keycloak form is filled with the test users' passwords: keep them out of Playwright traces.
test.use({ trace: 'off' });

const MENU: Record<PemUser, Array<[string, string]>> = {
  provider1: [
    ['My enrolment', '/portal'],
    ['Apply for enrolment', '/portal/apply'],
    ['My provider record', '/portal/provider'],
    ['My notices', '/portal/notices'],
  ],
  analyst1: [
    ['Provider search', '/console/providers'],
    ['Work queues', '/console/queues'],
  ],
  supervisor1: [
    ['Provider search', '/console/providers'],
    ['Work queues', '/console/queues'],
  ],
  admin1: [
    ['Provider search', '/console/providers'],
    ['Risk-tier rules', '/console/admin/risk-rules'],
    ['Batch jobs', '/console/admin/jobs'],
  ],
};

const sorted = (entries: Array<[string, string]>) => [...entries].sort((a, b) => a[1].localeCompare(b[1]));

test.describe('[MAM-62 AC-1] sign-in is required and uses the identity service', () => {
  test('[MAM-62 AC-1] a visitor who is not signed in is sent to Keycloak realm pem, client pem-web, code flow with an S256 code challenge', async ({ page, baseURL }) => {
    const authorize = page.waitForRequest(KEYCLOAK_AUTHORIZE);
    await page.goto('/portal', { waitUntil: 'commit' });
    const url = new URL((await authorize).url());

    expect(url.origin).toBe('http://localhost:8180');
    expect(url.searchParams.get('client_id')).toBe('pem-web');
    expect(url.searchParams.get('response_type')).toBe('code');
    expect(url.searchParams.get('code_challenge_method')).toBe('S256');
    expect(url.searchParams.get('code_challenge') ?? '').toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect(url.searchParams.get('redirect_uri')?.startsWith(new URL(baseURL!).origin)).toBe(true);
    await expect(page.locator('#username')).toBeVisible(); // the password is typed on the Keycloak page only
  });

  for (const path of ['/portal/apply', '/portal/provider', '/portal/notices', '/console/providers', '/console/queues']) {
    test(`[MAM-62 AC-1] opening ${path} without a session goes to the Keycloak sign-in page`, async ({ page }) => {
      await mockGateway(page);
      await page.goto(path, { waitUntil: 'commit' });

      await page.waitForURL(KEYCLOAK_AUTHORIZE);
      await expect(page.locator('#password')).toBeVisible();
    });
  }

  test('[MAM-62 AC-1] after signing in as provider1 the visitor lands on /portal and every API call carries provider1\'s bearer token to the gateway', async ({ page }) => {
    const gateway = await mockGateway(page);
    await signIn(page, 'provider1', '/portal');

    await expect.poll(() => new URL(page.url()).pathname + new URL(page.url()).search).toBe('/portal');
    await expect.poll(() => gateway.calls.length).toBeGreaterThanOrEqual(2);
    expect(await page.locator('input[type="password"]').count()).toBe(0);
    for (const call of gateway.calls) {
      const claims = claimsOf(call.authorization);
      expect(claims['preferred_username']).toBe('provider1');
      expect((claims['realm_access'] as { roles: string[] }).roles).toContain('PROVIDER');
      expect(claims['npi']).toBe(PROVIDER_NPI);
    }
    expect(new URL(GATEWAY).port).toBe('8080');
  });

  test('[MAM-62 AC-1] an analyst who asked for /console/providers lands on /console/providers after signing in', async ({ page }) => {
    await mockGateway(page);
    await signIn(page, 'analyst1', '/console/providers');

    await expect.poll(() => new URL(page.url()).pathname).toBe('/console/providers');
    await expect(page.getByRole('link', { name: 'Provider search' })).toBeVisible();
  });
});

test.describe('[MAM-62 AC-2] the menu shows only what the role allows', () => {
  for (const user of Object.keys(MENU) as PemUser[]) {
    test(`[MAM-62 AC-2] ${user} signs in with a real Keycloak token and sees exactly the menu of the role`, async ({ page }) => {
      await mockGateway(page);
      await signIn(page, user, user === 'provider1' ? '/portal' : '/console/providers');

      await expect.poll(() => menuEntries(page)).toEqual(sorted(MENU[user]));
    });
  }
});

test.describe('[MAM-62 AC-3] a role cannot open the areas of other roles', () => {
  const refused: Array<[PemUser, string, string]> = [
    ['provider1', '/portal', '/console/providers'],
    ['analyst1', '/console/providers', '/portal/apply'],
    ['admin1', '/console/providers', '/console/queues'],
  ];
  for (const [user, start, typed] of refused) {
    test(`[MAM-62 AC-3] ${user} typing ${typed} in the address bar sees Access denied (403), a link to the start page, and no API call is made`, async ({ page }) => {
      const gateway = await mockGateway(page);
      await signIn(page, user, start);
      await expect(page.getByRole('navigation').getByRole('link').first()).toBeVisible();
      // Q-097 C (racy test): the portal home of provider1 makes two calls when it mounts (GET /api/v1/applications and GET /api/v1/providers/{npi}
      // of the token's NPI); the log is cleared only after both reached the stub, so that neither can arrive after the clear. The console start page makes no call.
      if (user === 'provider1') {
        await expect.poll(() => ['/api/v1/applications', `/api/v1/providers/${PROVIDER_NPI}`].every((path) => gateway.calls.some((call) => call.path === path))).toBe(true);
      }
      gateway.calls.length = 0;

      await page.goto(typed);

      await expect(page.getByRole('heading', { level: 1, name: 'Access denied' })).toBeVisible();
      await expect(page.getByRole('main').getByText(/\b403\b/).first()).toBeVisible();
      await expect(page.getByRole('main').getByRole('link').first()).toBeVisible();
      expect(gateway.calls).toEqual([]);
    });
  }
});

test.describe('[MAM-62 AC-4][MAM-62 AC-5] portal home', () => {
  test('[MAM-62 AC-4] provider1 sees the application table with 1 Mar 2026, 1003000111 in full and Validated, and a View link', async ({ page }) => {
    await mockGateway(page);
    await signIn(page, 'provider1', '/portal');

    const row = page.getByRole('row').filter({ hasText: '1 Mar 2026' });
    await expect(row.getByRole('cell').nth(1)).toHaveText('1003000111');
    await expect(row.getByRole('cell').nth(2)).toHaveText('Validated');
    await expect(row.getByRole('link', { name: /view/i })).toHaveAttribute('href', `/portal/applications/${APPLICATION_ID}`);
  });

  test('[MAM-62 AC-4] with no applications the page says "No applications yet" and still offers Apply for enrolment', async ({ page }) => {
    await mockGateway(page, { applications: [] });
    await signIn(page, 'provider1', '/portal');

    await expect(page.getByText('No applications yet')).toBeVisible();
    const main = page.getByRole('main');
    await expect(main.getByRole('button', { name: 'Apply for enrolment' }).or(main.getByRole('link', { name: 'Apply for enrolment' }))).toBeVisible();
  });

  test('[MAM-62 AC-5] the summary shows the own record with its codes and meanings', async ({ page }) => {
    const gateway = await mockGateway(page);
    await signIn(page, 'provider1', '/portal');

    const summary = (label: string) => page.locator('dl > div').filter({ has: page.getByText(label, { exact: true }) }).locator('dd');
    await expect(summary('NPI')).toHaveText('1003000111');
    await expect(summary('Provider type')).toHaveText('Individual (IN)');
    await expect(summary('Enrolment status')).toHaveText('Active (A)');
    await expect(summary('Screening result')).toHaveText('Pass (P)');
    expect(gateway.calls.map((c) => c.path)).toContain(`/api/v1/providers/${PROVIDER_NPI}`);
  });
});

test.describe('[MAM-62 AC-6] sign-out and ended sessions', () => {
  test('[MAM-62 AC-6] Sign out ends the Keycloak session, shows the signed-out page, and opening /portal asks for sign-in again', async ({ page }) => {
    await mockGateway(page);
    await signIn(page, 'provider1', '/portal');
    await expect(page.getByRole('link', { name: 'My enrolment' })).toBeVisible();

    await signOut(page);
    await page.goto('/portal', { waitUntil: 'commit' });

    await page.waitForURL(KEYCLOAK_AUTHORIZE);
    await expect(page.locator('#username')).toBeVisible(); // the Keycloak session is gone: the sign-in form is shown, not an automatic sign-in
  });

  test('[MAM-62 AC-6] a 401 from the API sends the user to sign in again and the page then shows its data, not an empty page', async ({ page }) => {
    const authorizeRequests: string[] = [];
    page.on('request', (request) => {
      if (KEYCLOAK_AUTHORIZE.test(request.url())) authorizeRequests.push(request.url());
    });
    await mockGateway(page, { applicationStatuses: [401, 200] });

    await signIn(page, 'provider1', '/portal');

    await expect.poll(() => authorizeRequests.length).toBeGreaterThanOrEqual(2); // the sign-in and the sign-in after the 401
    await expect(page.getByRole('row').filter({ hasText: '1 Mar 2026' })).toBeVisible();
    await expect.poll(() => new URL(page.url()).pathname).toBe('/portal');
  });
});

test.describe('[MAM-62 AC-7] the shell and portal home have zero axe violations', () => {
  test('[MAM-62 AC-7] the portal home with applications: axe, one h1, landmarks', async ({ page }) => {
    await mockGateway(page);
    await signIn(page, 'provider1', '/portal');
    await expect(page.getByRole('row').filter({ hasText: '1 Mar 2026' })).toBeVisible();

    await expectShellStructure(page);
    await expectNoAxeViolations(page);
  });

  test('[MAM-62 AC-7] the portal home without applications: axe, one h1, landmarks', async ({ page }) => {
    await mockGateway(page, { applications: [] });
    await signIn(page, 'provider1', '/portal');
    await expect(page.getByText('No applications yet')).toBeVisible();

    await expectShellStructure(page);
    await expectNoAxeViolations(page);
  });

  test('[MAM-62 AC-7] the access-denied page: axe, one h1, landmarks', async ({ page }) => {
    await mockGateway(page);
    await signIn(page, 'provider1', '/portal');
    await expect(page.getByRole('link', { name: 'My enrolment' })).toBeVisible();
    await page.goto('/console/providers');
    await expect(page.getByRole('heading', { level: 1, name: 'Access denied' })).toBeVisible();

    await expectShellStructure(page);
    await expectNoAxeViolations(page);
  });

  test('[MAM-62 AC-7] the signed-out page: axe, one h1, landmarks', async ({ page }) => {
    await mockGateway(page);
    await signIn(page, 'provider1', '/portal');
    await expect(page.getByRole('link', { name: 'My enrolment' })).toBeVisible();
    await signOut(page);

    await expectShellStructure(page);
    await expectNoAxeViolations(page);
  });

  test('[MAM-62 AC-7] "Skip to main content" is the first focus stop and moves the focus to the main content', async ({ page }) => {
    await mockGateway(page);
    await signIn(page, 'provider1', '/portal');
    await expect(page.getByRole('row').filter({ hasText: '1 Mar 2026' })).toBeVisible();

    await page.keyboard.press('Tab');
    expect(await focusedName(page)).toBe('Skip to main content');
    expect(await focusIsVisible(page)).toBe(true);
    await page.keyboard.press('Enter');
    await expect(page.getByRole('main')).toBeFocused();
  });

  test('[MAM-62 AC-7] the menu entries, the View link and Apply for enrolment are reached with Tab, have a visible focus and work with Enter', async ({ page }) => {
    await mockGateway(page);
    await signIn(page, 'provider1', '/portal');
    await expect(page.getByRole('row').filter({ hasText: '1 Mar 2026' })).toBeVisible();

    const nav = page.getByRole('navigation', { name: 'Primary navigation' });
    for (const [name] of MENU.provider1) {
      const stops = await tabTo(page, nav.getByRole('link', { name, exact: true }));
      expect(stops.at(-1)).toBe(name);
      expect(await focusIsVisible(page), `the focus on "${name}" is visible`).toBe(true);
    }

    const main = page.getByRole('main');
    await tabTo(page, main.getByRole('link', { name: /view/i }));
    expect(await focusIsVisible(page), 'the focus on View is visible').toBe(true);
    const apply = main.getByRole('button', { name: 'Apply for enrolment' }).or(main.getByRole('link', { name: 'Apply for enrolment' }));
    await tabTo(page, apply);
    expect(await focusIsVisible(page), 'the focus on Apply for enrolment is visible').toBe(true);
    await page.keyboard.press('Enter');

    await expect.poll(() => new URL(page.url()).pathname).toBe('/portal/apply');
  });

  test('[MAM-62 AC-7] a menu entry opened with Enter and the View link opened with Enter reach their routes', async ({ page }) => {
    await mockGateway(page);
    await signIn(page, 'provider1', '/portal');
    await expect(page.getByRole('row').filter({ hasText: '1 Mar 2026' })).toBeVisible();

    await tabTo(page, page.getByRole('navigation', { name: 'Primary navigation' }).getByRole('link', { name: 'My notices', exact: true }));
    await page.keyboard.press('Enter');
    await expect.poll(() => new URL(page.url()).pathname).toBe('/portal/notices');

    await page.goto('/portal');
    await tabTo(page, page.getByRole('main').getByRole('link', { name: /view/i }));
    await page.keyboard.press('Enter');
    await expect.poll(() => new URL(page.url()).pathname).toBe(`/portal/applications/${APPLICATION_ID}`);
  });
});
