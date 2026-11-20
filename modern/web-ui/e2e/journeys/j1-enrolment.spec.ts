// MAM-69 Journey J1 (modern/docs/ux/journeys.md#J1) on the running stack, no mocks:
// provider1 applies -> Validated -> registered P/U -> screened on four sources -> tier L -> analyst1 approves ->
// the provider is in the public directory -> the notice is in Mailpit and in the portal.
// Needs: ./pem.sh up && ./pem.sh seed (the seed state: no application, provider or notice for NPI 1003000111).
// Passwords are read at run time from the environment or modern/infra/.env.local and never written anywhere.
// Events (Q-094 default A): each event named by AC-2, AC-3, AC-5 and AC-7 is proven here by its effect in the running stack;
// its payload is proven on its real topic by the owning service's tests (MAM-17 ProviderRegistered P/U, MAM-25
// ScreeningCompleted INITIAL PASS, MAM-35 RiskTierAssigned L, MAM-27 ProviderStatusChanged P->A with the due date,
// MAM-59 / MAM-58 NoticeSent).
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { expect, test, type APIRequestContext, type Browser, type Page } from '@playwright/test';
import { signIn, type PemUser } from '../support/keycloak';
import { expectNoAxeViolations } from '../support/page';

const GATEWAY = process.env.PEM_GATEWAY_URL ?? 'http://localhost:8080';
const KEYCLOAK_TOKEN = `${process.env.PEM_KEYCLOAK_URL ?? 'http://localhost:8180'}/realms/pem/protocol/openid-connect/token`;
const MAILPIT = process.env.PEM_MAILPIT_URL ?? 'http://localhost:8025';
const NPI = '1003000111';
// Q-092 default A: notices go to <npi>@providers.pem.local (MAM-59); the spec text of AC-7 names example.test.
const NOTICE_ADDRESS = `${NPI}@providers.pem.local`;
const SOURCES = ['LICBOARD', 'OIGLEIE', 'SAMGOV', 'SSADMF'];

const RIVERA = {
  npi: NPI,
  medicaidId: 'TX0000111',
  providerType: 'IN',
  taxonomy: '207Q00000X',
  lastName: 'RIVERA',
  firstName: 'MARIA',
  licenseNo: 'MD12345',
  licenseState: 'TX',
};

const PASSWORD_VARIABLE: Record<PemUser, string> = {
  provider1: 'KC_PROVIDER_PASSWORD',
  analyst1: 'KC_ANALYST_PASSWORD',
  supervisor1: 'KC_SUPERVISOR_PASSWORD',
  admin1: 'KC_ADMIN_PASSWORD',
};

function passwordOf(user: PemUser): string {
  const variable = PASSWORD_VARIABLE[user];
  if (process.env[variable]) return process.env[variable] as string;
  const file = fileURLToPath(new URL('../../../infra/.env.local', import.meta.url));
  const line = readFileSync(file, 'utf8')
    .split(/\r?\n/)
    .find((candidate) => candidate.startsWith(`${variable}=`));
  const value = line?.slice(variable.length + 1).trim();
  if (!value) throw new Error(`${variable} is not set (environment or modern/infra/.env.local)`);
  return value;
}

/** A real access token for the API checks (Keycloak client pem-test, password grant); never printed. */
async function tokenOf(request: APIRequestContext, user: PemUser): Promise<string> {
  const response = await request.post(KEYCLOAK_TOKEN, {
    form: { grant_type: 'password', client_id: 'pem-test', username: user, password: passwordOf(user) },
  });
  expect(response.status(), `Keycloak token for ${user}`).toBe(200);
  return ((await response.json()) as { access_token: string }).access_token;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any -- untyped JSON answers of several services
type Json = any;

async function getJson(request: APIRequestContext, path: string, token?: string): Promise<{ status: number; body: Json }> {
  const response = await request.get(`${GATEWAY}${path}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  const text = await response.text();
  return { status: response.status(), body: text ? JSON.parse(text) : null };
}

async function newUserPage(browser: Browser, user: PemUser, path: string): Promise<Page> {
  const context = await browser.newContext();
  const page = await context.newPage();
  await signIn(page, user, path);
  return page;
}

/** "19 Nov 2026" for an ISO date (yyyy-mm-dd): the plain form the UI displays (Q-097, user decision 2026-11-19 19:54). */
const MONTH = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const shownDate = (iso: string): string => `${Number(iso.slice(8, 10))} ${MONTH[Number(iso.slice(5, 7)) - 1]} ${iso.slice(0, 4)}`;

/** The UTC date of an HTTP Date header plus five years, as yyyy-mm-dd, the way LocalDate.plusYears(5) does it
 * (29 February becomes 28 February when the target year is not a leap year). The services run on UTC. */
function fiveYearsAfter(httpDate: string): string {
  const day = new Date(httpDate);
  const year = day.getUTCFullYear() + 5;
  const month = day.getUTCMonth() + 1;
  const leap = (year % 4 === 0 && year % 100 !== 0) || year % 400 === 0;
  const date = month === 2 && day.getUTCDate() === 29 && !leap ? 28 : day.getUTCDate();
  return `${year}-${String(month).padStart(2, '0')}-${String(date).padStart(2, '0')}`;
}

const section = (page: Page, heading: RegExp) =>
  page.locator('section').filter({ has: page.getByRole('heading', { name: heading }) });

test.describe('MAM-69 journey J1: a provider enrols and is approved', () => {
  test.setTimeout(300_000);

  test('MAM-69 AC-1..AC-8: apply, screen, tier, approve, directory, notice', async ({ browser, request }) => {
    const analystToken = await tokenOf(request, 'analyst1');

    // Seed state (AC-8): nothing yet for NPI 1003000111.
    const before = await getJson(request, `/api/v1/providers/${NPI}`, analystToken);
    expect(before.status, 'provider 1003000111 already exists: run ./pem.sh seed first').toBe(404);
    const appsBefore = await getJson(request, `/api/v1/applications?npi=${NPI}`, analystToken);
    expect(appsBefore.body.total, 'applications for 1003000111 already exist: run ./pem.sh seed first').toBe(0);

    // AC-1: provider1 signs in at the portal home, chooses Apply, applies and sees Validated.
    const portal = await newUserPage(browser, 'provider1', '/portal');
    await expect(portal.getByRole('heading', { level: 1, name: 'My enrolment' })).toBeVisible();
    await expectNoAxeViolations(portal);
    const main = portal.getByRole('main');
    await main.getByRole('link', { name: 'Apply for enrolment' }).or(main.getByRole('button', { name: 'Apply for enrolment' })).first().click();
    await expect(portal).toHaveURL(/\/portal\/apply$/);
    await expect(portal.getByRole('heading', { level: 1, name: 'Apply for enrolment' })).toBeVisible();
    await expectNoAxeViolations(portal);
    await portal.getByLabel(/^Provider type/).selectOption(RIVERA.providerType);
    await portal.getByLabel(/^NPI/).fill(RIVERA.npi);
    await portal.getByLabel(/^Medicaid ID/).fill(RIVERA.medicaidId);
    await portal.getByLabel(/^Taxonomy code/).fill(RIVERA.taxonomy);
    await portal.getByLabel(/^Last name/).fill(RIVERA.lastName);
    await portal.getByLabel(/^First name/).fill(RIVERA.firstName);
    await portal.getByLabel(/^Licence number/).fill(RIVERA.licenseNo);
    await portal.getByLabel(/^Licence state/).fill(RIVERA.licenseState);
    await portal.getByRole('button', { name: 'Submit application' }).click();
    await expect(portal).toHaveURL(/\/portal\/applications\/[0-9a-f-]{36}$/);
    const applicationId = portal.url().split('/').pop() as string;
    await expect(portal.getByRole('heading', { level: 1, name: 'Application status' })).toBeVisible();
    await expect(portal.getByRole('main').getByText('Validated', { exact: true })).toBeVisible();
    await expectNoAxeViolations(portal);
    const providerToken = await tokenOf(request, 'provider1');
    const application = await getJson(request, `/api/v1/applications/${applicationId}`, providerToken);
    expect(application.status).toBe(200);
    expect(application.body.status).toBe('VALIDATED');
    expect(application.body.errors ?? []).toEqual([]);

    // AC-2: registered P/U, then screened PASS on the four sources (screen flag P).
    await expect
      .poll(async () => (await getJson(request, `/api/v1/providers/${NPI}`, analystToken)).body?.screenFlag, {
        message: 'provider 1003000111 screened (screen flag P)',
        timeout: 120_000,
        intervals: [2_000],
      })
      .toBe('P');
    const screenings = await getJson(request, `/api/v1/screenings?npi=${NPI}`, analystToken);
    expect(screenings.status).toBe(200);
    const items = screenings.body.items as Array<{
      reason: string;
      status: string;
      result: string;
      results: Array<{ source: string; result: string }>;
    }>;
    expect(items, 'exactly one screening of 1003000111 (one INITIAL screening per registration)').toHaveLength(1);
    const [screening] = items;
    expect(screening?.reason).toBe('INITIAL');
    expect(screening?.status).toBe('COMPLETED');
    expect(screening?.result).toBe('PASS');
    expect((screening?.results ?? []).map((r) => `${r.source}=${r.result}`).sort()).toEqual(SOURCES.map((source) => `${source}=P`));

    // AC-3: risk tier L.
    await expect
      .poll(async () => (await getJson(request, `/api/v1/providers/${NPI}`, analystToken)).body?.riskTier, {
        message: 'provider 1003000111 has risk tier L',
        timeout: 120_000,
        intervals: [2_000],
      })
      .toBe('L');

    // AC-4: analyst1 sees it under Pending approval, not under Failed screening.
    const consolePage = await newUserPage(browser, 'analyst1', '/console/queues');
    const pending = section(consolePage, /^Pending approval/);
    await expect(pending.getByRole('row').filter({ hasText: NPI })).toHaveCount(1);
    const failed = section(consolePage, /^Failed screening/);
    await expect(failed).toBeVisible();
    await expect(failed.getByRole('row').filter({ hasText: NPI })).toHaveCount(0);
    await expectNoAxeViolations(consolePage);

    // AC-5: analyst1 approves: Active, due date five years after the approval date.
    await pending.getByRole('row').filter({ hasText: NPI }).getByRole('link', { name: 'Review' }).click();
    await expect(consolePage).toHaveURL(new RegExp(`/console/providers/${NPI}/approval$`));
    await expect(consolePage.getByRole('heading', { level: 1, name: 'Approve or deny' })).toBeVisible();
    await expectNoAxeViolations(consolePage);
    await consolePage.getByRole('radio', { name: 'Approve (becomes Active)' }).check();
    const dateBefore = (await request.get(`${GATEWAY}/api/v1/directory/providers?size=1`)).headers()['date'] ?? '';
    const [decision] = await Promise.all([
      consolePage.waitForResponse(
        (response) => response.url().endsWith(`/api/v1/providers/${NPI}/approval`) && response.request().method() === 'POST',
      ),
      consolePage.getByRole('button', { name: 'Record decision' }).click(),
    ]);
    expect(decision.status()).toBe(200);
    const decided = (await decision.json()) as { enrollStatus: string; revalidationDueDate: string };
    expect(decided.enrollStatus).toBe('A');
    // Five years after the approval date, read from the service clock just before and right after the decision.
    expect([fiveYearsAfter(dateBefore), fiveYearsAfter(decision.headers()['date'] ?? '')]).toContain(decided.revalidationDueDate);
    await expect(consolePage.getByText(/Decision recorded/)).toContainText('Active (A)');
    await expect(consolePage.getByText(/Decision recorded/)).toContainText(shownDate(decided.revalidationDueDate));
    await expectNoAxeViolations(consolePage);

    // AC-6: the public directory lists MARIA RIVERA, in the API and on /directory (no sign-in).
    await expect
      .poll(
        async () =>
          ((await getJson(request, '/api/v1/directory/providers?name=RIVERA')).body?.items ?? [])
            .filter((entry: { npi: string }) => entry.npi === NPI)
            .map((entry: { displayName: string }) => entry.displayName),
        { message: 'directory lists 1003000111', timeout: 120_000, intervals: [2_000] },
      )
      .toEqual(['MARIA RIVERA']);
    const publicContext = await browser.newContext();
    const visitor = await publicContext.newPage();
    await visitor.goto('/directory');
    await expect(visitor.getByRole('heading', { level: 1, name: 'Find a Medicaid provider' })).toBeVisible();
    await visitor.getByLabel('Name', { exact: true }).fill('RIVERA');
    await visitor.getByRole('button', { name: 'Search' }).click();
    const row = visitor.getByRole('row').filter({ hasText: NPI });
    await expect(row).toHaveCount(1);
    await expect(row).toContainText('MARIA RIVERA');
    await expectNoAxeViolations(visitor);

    // AC-7: the notice is in Mailpit and in the portal; the portal shows Active (A).
    await expect
      .poll(
        async () => {
          const response = await request.get(`${MAILPIT}/api/v1/messages`);
          const body = (await response.json()) as { messages: Array<{ To: Array<{ Address: string }>; Subject: string }> };
          return body.messages
            .filter((m) => m.To.some((to) => to.Address === NOTICE_ADDRESS))
            .map((m) => m.Subject);
        },
        { message: `Mailpit holds the notice to ${NOTICE_ADDRESS}`, timeout: 120_000, intervals: [2_000] },
      )
      .toContain('Enrolment approved');
    await portal.goto('/portal/notices');
    await expect(portal.getByRole('row').filter({ hasText: 'Enrolment approved (APPROVED)' })).toHaveCount(1);
    await expectNoAxeViolations(portal);
    await portal.goto('/portal/provider');
    await expect(portal.getByRole('main')).toContainText('Active (A)');
    await expectNoAxeViolations(portal);
  });
});
