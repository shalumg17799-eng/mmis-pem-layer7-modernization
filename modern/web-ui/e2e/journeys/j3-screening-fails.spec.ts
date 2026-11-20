// MAM-74 Journey J3 (modern/docs/ux/journeys.md#J3) on the running stack, no mocks:
// a group application whose owner (NPI 1990000111) is on the mocked LEIE -> screening FAIL with E203 owner excluded ->
// listed under Failed screening -> the detail shows the failing source -> analyst1 denies with a reason -> Denied ->
// the DENIED notice is listed and in Mailpit.
// Needs: ./pem.sh up && ./pem.sh seed (no application or provider for NPI 1003000444).
// Passwords are read at run time from the environment or modern/infra/.env.local and never written anywhere.
// Events (Q-094 default A, accepted by the user): ScreeningCompleted and ProviderStatusChanged are proven here by their
// effects (screen flag, screening result, status, notice); their payloads by MAM-25 / MAM-22 and MAM-27 on their topics.
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { expect, test, type APIRequestContext, type Page } from '@playwright/test';
import { signIn, type PemUser } from '../support/keycloak';
import { expectNoAxeViolations } from '../support/page';

const GATEWAY = process.env.PEM_GATEWAY_URL ?? 'http://localhost:8080';
const KEYCLOAK_TOKEN = `${process.env.PEM_KEYCLOAK_URL ?? 'http://localhost:8180'}/realms/pem/protocol/openid-connect/token`;
const MAILPIT = process.env.PEM_MAILPIT_URL ?? 'http://localhost:8025';
const NPI = '1003000444';
const NOTICE_ADDRESS = `${NPI}@providers.pem.local`; // Q-092 default A (the spec text names example.test)
const DENY_REASON = 'J3 SYNTHETIC: OWNER EXCLUDED ON LEIE';

// aws-transform/test-data/providers.txt line 8 (1003000444|EXCLUDED CLINIC LLC|GRP|GR11100|TX|1990000111); the Medicaid
// ID follows the TX0000<nnn> pattern of the other journeys; taxonomy as in the MAM-46 feed scenario.
const APPLICATION = {
  npi: NPI,
  medicaidId: 'TX0000444',
  providerType: 'GRP',
  taxonomy: '193200000X',
  organization: { legalName: 'EXCLUDED CLINIC LLC', dbaName: 'EXCLUDED CLINIC', ownerNpi: '1990000111' },
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
  const response = await request.get(`${GATEWAY}${path}`, { headers: token ? { Authorization: `Bearer ${token}` } : {} });
  const text = await response.text();
  return { status: response.status(), body: text ? JSON.parse(text) : null };
}

const section = (page: Page, heading: RegExp) =>
  page.locator('section').filter({ has: page.getByRole('heading', { name: heading }) });

const POLL = { timeout: 120_000, intervals: [2_000] };

test.describe('MAM-74 journey J3: screening fails and the analyst denies', () => {
  test.setTimeout(300_000);

  test('MAM-74 AC-1..AC-6: owner on LEIE -> FAIL E203 -> Failed screening -> deny -> DENIED notice', async ({ browser, request }) => {
    const analystToken = await tokenOf(request, 'analyst1');

    // Seed state (AC-6): nothing yet for NPI 1003000444.
    expect((await getJson(request, `/api/v1/providers/${NPI}`, analystToken)).status, 'run ./pem.sh seed first').toBe(404);

    // Set-up: analyst1 submits the group application (staff are not limited to their own NPI, Q-095).
    const submitted = await request.post(`${GATEWAY}/api/v1/applications`, {
      headers: { Authorization: `Bearer ${analystToken}`, 'Content-Type': 'application/json' },
      data: APPLICATION,
    });
    expect(submitted.status(), await submitted.text()).toBe(201);
    expect(((await submitted.json()) as { status: string }).status).toBe('VALIDATED');

    // AC-1: registered, screened FAIL: OIGLEIE F with E203 (owner excluded); status stays P.
    await expect
      .poll(async () => (await getJson(request, `/api/v1/providers/${NPI}`, analystToken)).body?.screenFlag, {
        message: 'provider 1003000444 screened with flag F',
        ...POLL,
      })
      .toBe('F');
    expect((await getJson(request, `/api/v1/providers/${NPI}`, analystToken)).body.enrollStatus).toBe('P');
    const screenings = (await getJson(request, `/api/v1/screenings?npi=${NPI}`, analystToken)).body.items as Array<{
      result: string;
      results: Array<{ source: string; result: string; errorCode?: string }>;
    }>;
    expect(screenings).toHaveLength(1);
    expect(screenings[0]?.result).toBe('FAIL');
    const leie = screenings[0]?.results.find((r) => r.source === 'OIGLEIE');
    expect(leie?.result).toBe('F');
    expect(leie?.errorCode).toBe('E203');

    // AC-2: analyst1 sees it under Failed screening and not under Pending approval.
    const page = await (await browser.newContext()).newPage();
    await signIn(page, 'analyst1', '/console/queues');
    const failed = section(page, /^Failed screening/);
    await expect(failed.getByRole('row').filter({ hasText: NPI })).toHaveCount(1);
    const pending = section(page, /^Pending approval/);
    await expect(pending).toBeVisible();
    await expect(pending.getByRole('row').filter({ hasText: NPI })).toHaveCount(0);
    await expectNoAxeViolations(page);

    // AC-3: the detail shows OIGLEIE Fail (F) with its detail and E203.
    await page.goto(`/console/providers/${NPI}`);
    await expect(page.getByRole('heading', { level: 1, name: 'Provider detail' })).toBeVisible();
    const screeningPanel = section(page, /^Screening/);
    const leieRow = screeningPanel.getByRole('row').filter({ hasText: 'OIG exclusion list (LEIE)' });
    await expect(leieRow).toHaveCount(1);
    await expect(leieRow).toContainText('Fail (F)');
    await expect(leieRow).toContainText('E203');
    await expectNoAxeViolations(page);

    // AC-4: analyst1 denies with a reason -> 200, Denied (D).
    await page.goto(`/console/providers/${NPI}/approval`);
    await expect(page.getByRole('heading', { level: 1, name: 'Approve or deny' })).toBeVisible();
    await page.getByRole('radio', { name: 'Deny (becomes Denied)' }).check();
    await page.getByLabel(/^Reason/).fill(DENY_REASON);
    const [decision] = await Promise.all([
      page.waitForResponse((response) => response.url().endsWith(`/api/v1/providers/${NPI}/approval`) && response.request().method() === 'POST'),
      page.getByRole('button', { name: 'Record decision' }).click(),
    ]);
    expect(decision.status()).toBe(200);
    expect(((await decision.json()) as { enrollStatus: string }).enrollStatus).toBe('D');
    await expect(page.getByText(/Decision recorded/)).toContainText('Denied (D)');
    await expectNoAxeViolations(page);

    // AC-5: the DENIED notice is listed and the email is in Mailpit.
    await expect
      .poll(
        async () =>
          ((await getJson(request, `/api/v1/notices?npi=${NPI}`, analystToken)).body?.items ?? [])
            .filter((n: { status: string }) => n.status === 'SENT')
            .map((n: { template: string }) => n.template),
        { message: 'DENIED notice listed', ...POLL },
      )
      .toContain('DENIED');
    await expect
      .poll(
        async () => {
          const body = (await (await request.get(`${MAILPIT}/api/v1/messages`)).json()) as {
            messages: Array<{ To: Array<{ Address: string }>; Subject: string }>;
          };
          return body.messages.filter((m) => m.To.some((to) => to.Address === NOTICE_ADDRESS)).map((m) => m.Subject);
        },
        { message: `DENIED email to ${NOTICE_ADDRESS}`, ...POLL },
      )
      .toContain('Enrolment denied');
  });
});
