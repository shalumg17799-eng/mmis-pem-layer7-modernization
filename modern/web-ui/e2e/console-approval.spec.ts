// MAM-87 end to end: analyst1, supervisor1, admin1 and provider1 sign in with the real Keycloak realm pem and open the
// provider detail and the approval page of the console in a real browser. provider-service and screening-service are
// answered in the browser (e2e/support/gateway-routes.ts) with the answers of their contracts: 200, 404, 409, 422, 403,
// 5xx and a network failure. Every state of the approval page is scanned with axe (AC-7).
import { expect, test, type Locator, type Page } from '@playwright/test';
import { json, mockApi, type MockApi, type RouteHandler } from './support/gateway-routes';
import { signIn, type PemUser } from './support/keycloak';
import { expectNoAxeViolations, tabTo } from './support/page';

// The Keycloak form is filled with the test users' passwords: keep them out of Playwright traces.
test.use({ trace: 'off' });

const RIVERA_NPI = '1003000111';
const HOSPICE_NPI = '1003000333';
const CLINIC_NPI = '1003000444';

const PENDING = { riskTier: null, enrollStatus: 'P', effectiveDate: '2026-03-02', revalidationDueDate: null, version: 1 };
const RIVERA = {
  ...PENDING,
  npi: RIVERA_NPI,
  medicaidId: 'TX0000111',
  providerType: 'IN',
  taxonomy: '207Q00000X',
  screenFlag: 'P',
  individual: { lastName: 'RIVERA', firstName: 'MARIA', licenseNo: 'MD12345', licenseState: 'TX' },
};
const CLINIC = {
  ...PENDING,
  npi: CLINIC_NPI,
  medicaidId: 'TX0000444',
  providerType: 'GR',
  taxonomy: '193200000X',
  screenFlag: 'F',
  organization: { legalName: 'EXCLUDED CLINIC LLC', dbaName: 'EXCLUDED CLINIC', ownerNpi: '1990000111' },
};
const HOSPICE = {
  ...PENDING,
  npi: HOSPICE_NPI,
  medicaidId: 'TX0000333',
  providerType: 'FA',
  taxonomy: '251G00000X',
  screenFlag: 'U',
  organization: { legalName: 'SUNSET HOSPICE INC', dbaName: 'SUNSET HOSPICE', ownerNpi: '1003000111' },
};

const VERDICT_AT = '2026-03-02T09:14:00Z';
const result = (source: string, outcome: string, detail: string, errorCode: string | null = null) => ({ source, result: outcome, verdictAt: VERDICT_AT, detail, errorCode });
const screening = (npi: string, outcome: string, results: unknown[]) => ({
  items: [
    {
      screeningId: '5c1f0a9e-3b7d-4a52-9e64-0d8f2b6a7c10',
      npi,
      reason: 'INITIAL',
      status: 'COMPLETED',
      result: outcome,
      results,
      startedAt: '2026-03-02T09:13:00Z',
      completedAt: VERDICT_AT,
    },
  ],
  page: 0,
  size: 20,
  total: 1,
});
const PASSED = screening(RIVERA_NPI, 'PASS', [
  result('LICBOARD', 'P', 'MD12345 active to 09/2028'),
  result('OIGLEIE', 'P', 'No exclusion'),
  result('SAMGOV', 'P', 'Not debarred'),
  result('SSADMF', 'P', 'No death record'),
]);
const FAILED = screening(CLINIC_NPI, 'FAIL', [
  result('LICBOARD', 'P', 'GR11100 active to 09/2028'),
  result('OIGLEIE', 'F', 'Owner 1990000111 is on the OIG exclusion list', 'E203'),
  result('SAMGOV', 'P', 'Not debarred'),
  result('SSADMF', 'P', 'No death record'),
]);
const NONE = { items: [], page: 0, size: 20, total: 0 };

interface World {
  routes: Record<string, RouteHandler>;
  /** The provider as the service holds it now. */
  set: (record: Record<string, unknown>) => void;
  npi: string;
}

/** The gateway for one provider: GET provider, GET screenings and the decision, which changes the provider the way the contract says. */
function world(record: Record<string, unknown> & { npi: string }, screenings: unknown, decision?: RouteHandler): World {
  let current: Record<string, unknown> = record;
  const decide: RouteHandler =
    decision ??
    ((call) => {
      const sent = (call.body as { decision?: string } | undefined)?.decision;
      current = sent === 'APPROVE' ? { ...current, enrollStatus: 'A', revalidationDueDate: '2031-03-05' } : { ...current, enrollStatus: 'D' };
      return json(200, current);
    });
  return {
    npi: record.npi,
    set: (next) => {
      current = next;
    },
    routes: {
      [`GET /api/v1/providers/${record.npi}`]: () => json(200, current),
      'GET /api/v1/screenings': () => json(200, screenings),
      [`POST /api/v1/providers/${record.npi}/approval`]: decide,
    },
  };
}

const DECISIONS = (api: MockApi, npi: string) => api.to('POST', `/api/v1/providers/${npi}/approval`);
const approvePath = (npi: string) => `/console/providers/${npi}/approval`;

async function openApproval(page: Page, who: PemUser, w: World, name: string): Promise<MockApi> {
  const api = await mockApi(page, w.routes);
  await signIn(page, who, approvePath(w.npi));
  await expect(page.getByRole('heading', { level: 1, name: 'Approve or deny' })).toBeVisible();
  await expect(page.getByRole('region', { name: 'Provider', exact: true }).locator('dd').filter({ hasText: name })).toBeVisible();
  return api;
}

const approve = (page: Page): Locator => page.getByRole('radio', { name: /^Approve/ });
const deny = (page: Page): Locator => page.getByRole('radio', { name: /^Deny/ });
const reason = (page: Page): Locator => page.getByLabel(/^Reason/);
const record = (page: Page): Locator => page.getByRole('button', { name: 'Record decision' });
const alertBox = (page: Page): Locator => page.getByRole('alert');
/** Value of a row of the list "Provider" (UI polish, Q-097): <div><dt>label</dt><dd>value</dd></div>. The detail page also shows the list "Provider record", so the region is named. */
const valueOf = (page: Page, label: string): Locator => page.getByRole('region', { name: 'Provider', exact: true }).locator('dl > div').filter({ has: page.getByText(label, { exact: true }) }).locator('dd');

test.describe('[MAM-87 AC-1] the approval page and how to reach it', () => {
  test('[MAM-87 AC-1] analyst1 opens the provider detail, chooses "Approve or deny" and sees NPI, Name, Status, Screen flag and the per-source results', async ({ page }) => {
    const w = world(RIVERA, PASSED);
    const api = await mockApi(page, w.routes);
    await signIn(page, 'analyst1', `/console/providers/${RIVERA_NPI}`);
    await expect(page.getByRole('heading', { level: 1, name: 'Provider detail' })).toBeVisible();
    await expect(valueOf(page, 'Status')).toHaveText('Pending (P)');

    await page.getByRole('link', { name: 'Approve or deny' }).click();

    await expect(page).toHaveURL(new RegExp(`${approvePath(RIVERA_NPI)}$`));
    await expect(page.getByRole('heading', { level: 1, name: 'Approve or deny' })).toBeVisible();
    await expect(valueOf(page, 'NPI')).toHaveText('1003000111');
    await expect(valueOf(page, 'Name')).toHaveText('RIVERA MARIA');
    await expect(valueOf(page, 'Status')).toHaveText('Pending (P)');
    await expect(valueOf(page, 'Screening result')).toHaveText('Pass (P)');
    const table = page.getByRole('table').filter({ has: page.getByRole('columnheader', { name: 'Source' }) });
    await expect(table.getByRole('row')).toHaveCount(5); // header + Licence board, OIG exclusion list (LEIE), SAM.gov debarment, SSA death records
    await expect(table.getByRole('row').filter({ hasText: 'Licence board' }).getByRole('cell').nth(1)).toHaveText('Pass (P)');
    expect(api.to('GET', '/api/v1/screenings')[0]?.query.get('npi')).toBe(RIVERA_NPI);
    expect(api.to('GET', `/api/v1/providers/${RIVERA_NPI}`).length).toBeGreaterThan(0);
  });

  test('[MAM-87 AC-1] the detail page offers no action for a Pending provider that is Unscreened, and none to admin1', async ({ page }) => {
    const unscreened = world(HOSPICE, NONE);
    await mockApi(page, unscreened.routes);
    await signIn(page, 'analyst1', `/console/providers/${HOSPICE_NPI}`);
    await expect(valueOf(page, 'Screening result')).toHaveText('Unscreened (U)');
    await expect(page.getByRole('link', { name: 'Approve or deny' })).toHaveCount(0);
  });

  test('[MAM-87 AC-1] admin1 sees the provider detail without the "Approve or deny" action', async ({ page }) => {
    await mockApi(page, world(RIVERA, PASSED).routes);
    await signIn(page, 'admin1', `/console/providers/${RIVERA_NPI}`);

    await expect(valueOf(page, 'Status')).toHaveText('Pending (P)');
    await expect(page.getByRole('link', { name: 'Approve or deny' })).toHaveCount(0);
  });

  test('[MAM-87 AC-1] in Provider search the NPI of the result is a link to the provider detail', async ({ page }) => {
    await mockApi(page, {
      ...world(RIVERA, PASSED).routes,
      'GET /api/v1/providers': () => json(200, { items: [RIVERA], page: 0, size: 20, total: 1 }),
    });
    await signIn(page, 'analyst1', '/console/providers');
    await page.getByLabel('NPI', { exact: true }).fill(RIVERA_NPI);
    await page.getByRole('button', { name: 'Search' }).click();

    const link = page.getByRole('row').filter({ hasText: RIVERA_NPI }).getByRole('link', { name: RIVERA_NPI });
    await expect(link).toBeVisible();
    await expect(link).toHaveAttribute('href', `/console/providers/${RIVERA_NPI}`);
    await link.click();

    await expect(page).toHaveURL(new RegExp(`/console/providers/${RIVERA_NPI}$`));
    await expect(page.getByRole('heading', { level: 1, name: 'Provider detail' })).toBeVisible();
  });
});

test.describe('[MAM-87 AC-2] Approve makes the provider Active (journey J1)', () => {
  test('[MAM-87 AC-2] analyst1 approves 1003000111: POST with decision APPROVE, then Active (A) with the due date 5 Mar 2031', async ({ page }) => {
    const api = await openApproval(page, 'analyst1', world(RIVERA, PASSED), 'RIVERA MARIA');

    await page.getByRole('radio', { name: 'Approve (becomes Active)' }).check();
    await record(page).click();

    await expect(page.getByText(/Decision recorded/)).toContainText('Active (A)');
    await expect(page.getByText(/Decision recorded/)).toContainText('5 Mar 2031');
    await expect(valueOf(page, 'Status')).toHaveText('Active (A)');
    await expect(valueOf(page, 'Revalidation due')).toHaveText('5 Mar 2031');
    await expect(approve(page)).toHaveCount(0);
    await expect(record(page)).toHaveCount(0);
    const posts = DECISIONS(api, RIVERA_NPI);
    expect(posts).toHaveLength(1);
    expect(posts[0]?.body).toEqual({ decision: 'APPROVE' });
    expect(posts[0]?.authorization).toMatch(/^Bearer \S+/);
  });
});

test.describe('[MAM-87 AC-3] Deny with a reason (journey J3)', () => {
  test('[MAM-87 AC-3] analyst1 denies 1003000444 with the reason: POST with decision DENY and the reason, then Denied (D)', async ({ page }) => {
    const api = await openApproval(page, 'analyst1', world(CLINIC, FAILED), 'EXCLUDED CLINIC LLC');

    await page.getByRole('radio', { name: 'Deny (becomes Denied)' }).check();
    await reason(page).fill('Owner is on the OIG exclusion list');
    await expect(reason(page)).toHaveAccessibleDescription(/\b34\b.*\b200\b|\b166\b/);
    await record(page).click();

    await expect(page.getByText(/Decision recorded/)).toContainText('Denied (D)');
    await expect(valueOf(page, 'Status')).toHaveText('Denied (D)');
    expect(DECISIONS(api, CLINIC_NPI)[0]?.body).toEqual({ decision: 'DENY', reason: 'Owner is on the OIG exclusion list' });
  });

  test('[MAM-87 AC-3] a reason longer than 200 characters cannot be entered and is not sent', async ({ page }) => {
    const api = await openApproval(page, 'analyst1', world(CLINIC, FAILED), 'EXCLUDED CLINIC LLC');

    await deny(page).check();
    await reason(page).focus();
    await page.keyboard.type('x'.repeat(205));
    await expect(reason(page)).toHaveValue('x'.repeat(200));
    await record(page).click();

    await expect(page.getByText(/Decision recorded/)).toBeVisible();
    const sent = DECISIONS(api, CLINIC_NPI)[0]?.body as { reason?: string };
    expect(sent.reason).toBe('x'.repeat(200));
  });
});

test.describe('[MAM-87 AC-4] approval needs a passed screening', () => {
  test('[MAM-87 AC-4] screen flag Fail: Approve is disabled with "Approval needs a passed screening" and only Deny can be recorded', async ({ page }) => {
    const api = await openApproval(page, 'analyst1', world(CLINIC, FAILED), 'EXCLUDED CLINIC LLC');

    await expect(approve(page)).toBeDisabled();
    await expect(page.getByText('Approval needs a passed screening')).toBeVisible();
    await expect(deny(page)).toBeEnabled();
    expect(DECISIONS(api, CLINIC_NPI)).toHaveLength(0);
    await deny(page).check();
    await record(page).click();
    await expect(page.getByText(/Decision recorded/)).toContainText('Denied (D)');
    expect(DECISIONS(api, CLINIC_NPI)[0]?.body).toEqual({ decision: 'DENY' });
  });

  test('[MAM-87 AC-4] screen flag Unscreened: neither decision is offered and the page says that screening has not finished', async ({ page }) => {
    const api = await openApproval(page, 'analyst1', world(HOSPICE, NONE), 'SUNSET HOSPICE INC');

    await expect(page.getByRole('radio')).toHaveCount(0);
    await expect(record(page)).toHaveCount(0);
    await expect(page.getByText(/screening has not finished/i)).toBeVisible();
    await expect(valueOf(page, 'Screening result')).toHaveText('Unscreened (U)');
    expect(DECISIONS(api, HOSPICE_NPI)).toHaveLength(0);
  });
});

test.describe('[MAM-87 AC-5] refusals from the service are shown and the page is refreshed', () => {
  test('[MAM-87 AC-5] 409: the problem detail is shown, the provider reloads as Active (A) and no further decision is offered', async ({ page }) => {
    const w = world(RIVERA, PASSED);
    const detail = 'Provider 1003000111 has already been decided: Active.';
    w.routes[`POST /api/v1/providers/${RIVERA_NPI}/approval`] = () => {
      w.set({ ...RIVERA, enrollStatus: 'A', revalidationDueDate: '2031-03-05' });
      return json(409, { title: 'Conflict', status: 409, detail });
    };
    await openApproval(page, 'analyst1', w, 'RIVERA MARIA');

    await approve(page).check();
    await record(page).click();

    await expect(page.getByText(detail)).toBeVisible();
    await expect(valueOf(page, 'Status')).toHaveText('Active (A)');
    await expect(page.getByRole('radio')).toHaveCount(0);
    await expect(record(page)).toHaveCount(0);
    await expect(page.getByText(/Decision recorded/)).toHaveCount(0);
  });

  test('[MAM-87 AC-5] 422: the detail is shown at the decision form and what was typed is kept', async ({ page }) => {
    const detail = 'The screening of this provider has not passed (message of this test).';
    const w = world(CLINIC, FAILED, () => json(422, { title: 'Unprocessable Entity', status: 422, detail }));
    await openApproval(page, 'analyst1', w, 'EXCLUDED CLINIC LLC');

    await deny(page).check();
    await reason(page).fill('Owner is on the OIG exclusion list');
    await record(page).click();

    await expect(page.getByText(detail)).toBeVisible();
    await expect(deny(page)).toBeChecked();
    await expect(reason(page)).toHaveValue('Owner is on the OIG exclusion list');
    await expect(valueOf(page, 'Status')).toHaveText('Pending (P)');
  });

  test('[MAM-87 AC-5] 404: the page shows "No provider found"', async ({ page }) => {
    await openApproval(page, 'analyst1', world(RIVERA, PASSED, () => json(404, { title: 'Not Found', status: 404 })), 'RIVERA MARIA');

    await approve(page).check();
    await record(page).click();

    await expect(page.getByText('No provider found')).toBeVisible();
    await expect(page.getByText(/Decision recorded/)).toHaveCount(0);
  });

  for (const [name, handler] of [
    ['500', () => json(500, { title: 'Internal Server Error', status: 500 })],
    ['a network failure', () => 'abort'],
  ] as Array<[string, RouteHandler]>) {
    test(`[MAM-87 AC-5] ${name}: "The decision could not be recorded. Try again." and no claim that a decision was recorded`, async ({ page }) => {
      await openApproval(page, 'analyst1', world(RIVERA, PASSED, handler), 'RIVERA MARIA');

      await approve(page).check();
      await record(page).click();

      await expect(page.getByText('The decision could not be recorded. Try again.')).toBeVisible();
      await expect(page.getByText(/Decision recorded/)).toHaveCount(0);
      await expect(valueOf(page, 'Status')).toHaveText('Pending (P)');
      await expect(record(page)).toBeEnabled();
    });
  }
});

test.describe('[MAM-87 AC-6] only analysts and supervisors decide', () => {
  for (const who of ['analyst1', 'supervisor1'] as const) {
    test(`[MAM-87 AC-6] ${who} sees the decision form`, async ({ page }) => {
      await openApproval(page, who, world(RIVERA, PASSED), 'RIVERA MARIA');

      await expect(page.getByRole('group', { name: 'Decision' })).toBeVisible();
      await expect(record(page)).toBeEnabled();
    });
  }

  for (const who of ['admin1', 'provider1'] as const) {
    test(`[MAM-87 AC-6] ${who} sees the access-denied page and no provider data is fetched`, async ({ page }) => {
      const api = await mockApi(page, world(RIVERA, PASSED).routes);
      await signIn(page, who, who === 'admin1' ? '/console/providers' : '/portal');
      await expect(page.getByRole('navigation').getByRole('link').first()).toBeVisible();
      // Q-097 C: the portal home of provider1 makes two calls when it mounts (GET /api/v1/applications and GET /api/v1/providers/{npi} of the
      // token's NPI); the log is cleared only after both reached the stub, so that neither can arrive after the clear. admin1's page makes no call.
      if (who === 'provider1') {
        await expect.poll(() => ['/api/v1/applications', `/api/v1/providers/${RIVERA_NPI}`].every((path) => api.calls.some((call) => call.path === path))).toBe(true);
      }
      api.calls.length = 0;

      await page.goto(approvePath(RIVERA_NPI));

      await expect(page.getByRole('heading', { level: 1, name: 'Access denied' })).toBeVisible();
      await expect(page.getByRole('group', { name: 'Decision' })).toHaveCount(0);
      expect(api.calls).toEqual([]);
    });
  }

  test('[MAM-87 AC-6] if the API answers 403 to a decision the page shows "You are not allowed to record this decision"', async ({ page }) => {
    await openApproval(page, 'analyst1', world(RIVERA, PASSED, () => json(403, { title: 'Forbidden', status: 403 })), 'RIVERA MARIA');

    await approve(page).check();
    await record(page).click();

    await expect(page.getByText('You are not allowed to record this decision')).toBeVisible();
    await expect(page.getByText(/Decision recorded/)).toHaveCount(0);
  });
});

test.describe('[MAM-87 AC-7] the approval page has zero axe violations', () => {
  test('[MAM-87 AC-7] the page for a Pass provider has zero axe violations', async ({ page }) => {
    await openApproval(page, 'analyst1', world(RIVERA, PASSED), 'RIVERA MARIA');

    await expectNoAxeViolations(page);
  });

  test('[MAM-87 AC-7] the page for a Pass provider with Deny chosen (Reason and its counter) has zero axe violations', async ({ page }) => {
    await openApproval(page, 'analyst1', world(RIVERA, PASSED), 'RIVERA MARIA');
    await deny(page).check();
    await reason(page).fill('typed for the scan');

    await expectNoAxeViolations(page);
  });

  test('[MAM-87 AC-7] the page for a Fail provider has zero axe violations', async ({ page }) => {
    await openApproval(page, 'analyst1', world(CLINIC, FAILED), 'EXCLUDED CLINIC LLC');

    await expectNoAxeViolations(page);
  });

  test('[MAM-87 AC-7] the page for an Unscreened provider has zero axe violations', async ({ page }) => {
    await openApproval(page, 'analyst1', world(HOSPICE, NONE), 'SUNSET HOSPICE INC');

    await expectNoAxeViolations(page);
  });

  test('[MAM-87 AC-7] the page after a refusal (409) has zero axe violations', async ({ page }) => {
    const w = world(RIVERA, PASSED);
    w.routes[`POST /api/v1/providers/${RIVERA_NPI}/approval`] = () => {
      w.set({ ...RIVERA, enrollStatus: 'A', revalidationDueDate: '2031-03-05' });
      return json(409, { title: 'Conflict', status: 409, detail: 'Provider 1003000111 has already been decided: Active.' });
    };
    await openApproval(page, 'analyst1', w, 'RIVERA MARIA');
    await approve(page).check();
    await record(page).click();
    await expect(valueOf(page, 'Status')).toHaveText('Active (A)');
    await expect(alertBox(page)).toBeVisible();

    await expectNoAxeViolations(page);
  });

  test('[MAM-87 AC-7] the page after a failed call (500) has zero axe violations, and the focus is on the message', async ({ page }) => {
    await openApproval(page, 'analyst1', world(RIVERA, PASSED, () => json(500, { title: 'Internal Server Error', status: 500 })), 'RIVERA MARIA');
    await approve(page).check();
    await record(page).click();
    await expect(page.getByText('The decision could not be recorded. Try again.')).toBeVisible();

    await expect(alertBox(page)).toBeFocused();
    await expectNoAxeViolations(page);
  });

  test('[MAM-87 AC-7] the decision is a labelled group of radio buttons, the Reason has a visible label, status and flag are text plus code', async ({ page }) => {
    await openApproval(page, 'analyst1', world(RIVERA, PASSED), 'RIVERA MARIA');

    const group = page.getByRole('group', { name: 'Decision' });
    await expect(group.getByRole('radio')).toHaveCount(2);
    await deny(page).check();
    const label = page.locator('label', { hasText: /^Reason/ });
    await expect(label).toBeVisible();
    await expect(reason(page)).toBeVisible();
    await expect(page.getByRole('region', { name: 'Provider', exact: true }).getByText('Pending (P)', { exact: true })).toBeVisible();
    await expect(page.getByRole('region', { name: 'Provider', exact: true }).getByText('Pass (P)', { exact: true })).toBeVisible();
  });

  test('[MAM-87 AC-7] an approval is recorded with the keyboard only (Tab, Space, Enter)', async ({ page }) => {
    const api = await openApproval(page, 'analyst1', world(RIVERA, PASSED), 'RIVERA MARIA');

    await tabTo(page, approve(page));
    await page.keyboard.press('Space');
    await expect(approve(page)).toBeChecked();
    await page.keyboard.press('Tab');
    await expect(record(page)).toBeFocused();
    await page.keyboard.press('Enter');

    await expect(page.getByText(/Decision recorded/)).toContainText('Active (A)');
    expect(DECISIONS(api, RIVERA_NPI)[0]?.body).toEqual({ decision: 'APPROVE' });
  });

  test('[MAM-87 AC-7] a denial with a reason is recorded with the keyboard only', async ({ page }) => {
    const api = await openApproval(page, 'analyst1', world(CLINIC, FAILED), 'EXCLUDED CLINIC LLC');

    await tabTo(page, deny(page)); // the disabled Approve is skipped
    await page.keyboard.press('Space');
    await expect(deny(page)).toBeChecked();
    await page.keyboard.press('Tab');
    await expect(reason(page)).toBeFocused();
    await page.keyboard.type('Owner is on the OIG exclusion list');
    await page.keyboard.press('Tab');
    await expect(record(page)).toBeFocused();
    await page.keyboard.press('Enter');

    await expect(page.getByText(/Decision recorded/)).toContainText('Denied (D)');
    expect(DECISIONS(api, CLINIC_NPI)[0]?.body).toEqual({ decision: 'DENY', reason: 'Owner is on the OIG exclusion list' });
  });
});
