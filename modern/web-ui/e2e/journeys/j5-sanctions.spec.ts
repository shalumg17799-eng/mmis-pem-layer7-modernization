// MAM-79 Journey J5 (modern/docs/ux/journeys.md#J5) on the running stack, no mocks:
// supervisor1 suspends the seeded ACTIVE provider TESTPROV ALPHA -> Suspended, SUS notice, gone from the public directory ->
// reinstates it -> Active, RST notice -> both sanctions in the history -> an unknown action code is rejected.
// Needs: ./pem.sh up && ./pem.sh seed. The seed state (MAM-51): ALPHA is ACTIVE, listed in the public directory and has no
// sanction history. Passwords are read at run time from the environment or modern/infra/.env.local, never written anywhere.
// Events (Q-094 default A, accepted by the user): ProviderStatusChanged and SanctionApplied are proven here by their effects
// (status, directory, notices, Mailpit); their payloads are proven on their topics by MAM-33, MAM-52 and MAM-58.
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { expect, test, type APIRequestContext, type Page } from '@playwright/test';
import { signIn, type PemUser } from '../support/keycloak';
import { expectNoAxeViolations } from '../support/page';

const GATEWAY = process.env.PEM_GATEWAY_URL ?? 'http://localhost:8080';
const KEYCLOAK_TOKEN = `${process.env.PEM_KEYCLOAK_URL ?? 'http://localhost:8180'}/realms/pem/protocol/openid-connect/token`;
const MAILPIT = process.env.PEM_MAILPIT_URL ?? 'http://localhost:8025';
const NPI = '1005940865'; // TESTPROV ALPHA, seeded ACTIVE individual (modern/testdata/README.md)
const NOTICE_ADDRESS = `${NPI}@providers.pem.local`; // Q-092 default A
const SUSPEND_REASON = 'J5 SYNTHETIC SUSPENSION';
const REINSTATE_REASON = 'J5 SYNTHETIC REINSTATEMENT';

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

const statusOf = async (request: APIRequestContext, token: string): Promise<string> =>
  (await getJson(request, `/api/v1/providers/${NPI}`, token)).body?.enrollStatus;

const directoryNpis = async (request: APIRequestContext): Promise<string[]> =>
  ((await getJson(request, '/api/v1/directory/providers?name=ALPHA')).body?.items ?? []).map((entry: { npi: string }) => entry.npi);

const noticeTemplates = async (request: APIRequestContext, token: string): Promise<string[]> =>
  ((await getJson(request, `/api/v1/notices?npi=${NPI}`, token)).body?.items ?? [])
    .filter((notice: { status: string }) => notice.status === 'SENT')
    .map((notice: { template: string }) => notice.template);

async function mailSubjects(request: APIRequestContext): Promise<string[]> {
  const body = (await (await request.get(`${MAILPIT}/api/v1/messages`)).json()) as {
    messages: Array<{ To: Array<{ Address: string }>; Subject: string }>;
  };
  return body.messages.filter((m) => m.To.some((to) => to.Address === NOTICE_ADDRESS)).map((m) => m.Subject);
}

/** Fill and send the Record sanction form; returns the POST /api/v1/sanctions answer. */
async function recordSanction(page: Page, action: string, reason: string, effectiveDate: string) {
  await page.getByLabel(/^Action/).selectOption({ label: action });
  await page.getByLabel(/^Reason/).fill(reason);
  await page.getByLabel(/^Effective date/).fill(effectiveDate);
  const [answer] = await Promise.all([
    page.waitForResponse((response) => response.url().endsWith('/api/v1/sanctions') && response.request().method() === 'POST'),
    page.getByRole('button', { name: 'Record sanction' }).click(),
  ]);
  return answer;
}

const POLL = { timeout: 120_000, intervals: [2_000] };

test.describe('MAM-79 journey J5: a supervisor suspends and reinstates a provider', () => {
  test.setTimeout(300_000);

  test('MAM-79 AC-1..AC-7: suspend, leave the directory, reinstate, history, unknown action', async ({ browser, request }) => {
    const supervisorToken = await tokenOf(request, 'supervisor1');

    // Seed state (AC-7, MAM-51): ALPHA is ACTIVE, in the public directory, with no sanction history.
    expect(await statusOf(request, supervisorToken), 'ALPHA is not ACTIVE: run ./pem.sh seed first').toBe('A');
    const historyBefore = await getJson(request, `/api/v1/sanctions?npi=${NPI}`, supervisorToken);
    expect(historyBefore.status).toBe(200);
    expect(historyBefore.body.total, 'ALPHA already has sanctions: run ./pem.sh seed first').toBe(0);
    expect(await directoryNpis(request), 'ALPHA must be listed in the public directory at the seed state (MAM-51 AC-4)').toContain(NPI);

    // AC-1: from the provider detail, supervisor1 opens Record sanction.
    const context = await browser.newContext();
    const page = await context.newPage();
    await signIn(page, 'supervisor1', `/console/providers/${NPI}`);
    await expect(page.getByRole('heading', { level: 1, name: 'Provider detail' })).toBeVisible();
    await expectNoAxeViolations(page);
    const main = page.getByRole('main');
    await main.getByRole('link', { name: 'Record sanction' }).or(main.getByRole('button', { name: 'Record sanction' })).first().click();
    await expect(page).toHaveURL(new RegExp(`/console/providers/${NPI}/sanctions/new$`));
    await expect(page.getByLabel(/^Action/)).toBeVisible();
    await expect(page.getByLabel(/^Reason/)).toBeVisible();
    await expect(page.getByLabel(/^Effective date/)).toBeVisible();
    await expectNoAxeViolations(page);

    // AC-2: Suspend -> 201, Suspended (S), SUS notice in the notice list and in Mailpit.
    const suspended = await recordSanction(page, 'Suspend (S)', SUSPEND_REASON, '03/31/2026');
    expect(suspended.status()).toBe(201);
    expect(((await suspended.json()) as { resultingStatus: string }).resultingStatus).toBe('S');
    await expect(page.getByText(/Suspended \(S\)/).first()).toBeVisible();
    await expectNoAxeViolations(page);
    expect(await statusOf(request, supervisorToken)).toBe('S');
    await expect.poll(() => noticeTemplates(request, supervisorToken), { message: 'SUS notice listed', ...POLL }).toContain('SUS');
    await expect.poll(() => mailSubjects(request), { message: `SUS email to ${NOTICE_ADDRESS}`, ...POLL }).toContain('Suspension notice');

    // AC-3: the suspended provider leaves the public directory (API and /directory, no sign-in).
    await expect.poll(() => directoryNpis(request), { message: 'ALPHA removed from the directory', ...POLL }).not.toContain(NPI);
    const visitor = await (await browser.newContext()).newPage();
    await visitor.goto('/directory');
    await visitor.getByLabel('Name', { exact: true }).fill('ALPHA');
    await visitor.getByRole('button', { name: 'Search' }).click();
    await expect(visitor.getByText('No providers found')).toBeVisible();
    await expect(visitor.getByRole('row').filter({ hasText: NPI })).toHaveCount(0);
    await expectNoAxeViolations(visitor);

    // AC-4: Reinstate -> 201, Active (A), RST notice.
    await page.goto(`/console/providers/${NPI}/sanctions/new`);
    const reinstated = await recordSanction(page, 'Reinstate (R)', REINSTATE_REASON, '04/30/2026');
    expect(reinstated.status()).toBe(201);
    expect(((await reinstated.json()) as { resultingStatus: string }).resultingStatus).toBe('A');
    await expect(page.getByText(/Active \(A\)/).first()).toBeVisible();
    expect(await statusOf(request, supervisorToken)).toBe('A');
    await expect.poll(() => noticeTemplates(request, supervisorToken), { message: 'RST notice listed', ...POLL }).toContain('RST');
    await expect.poll(() => mailSubjects(request), { message: `RST email to ${NOTICE_ADDRESS}`, ...POLL }).toContain('Reinstatement notice');

    // AC-5: the history shows both actions, in order, with reasons and effective dates (API and the detail panel).
    const history = await getJson(request, `/api/v1/sanctions?npi=${NPI}`, supervisorToken);
    const rows = (history.body.items as Array<{ action: string; reason: string; effectiveDate: string; recordedAt: string }>)
      .slice()
      .sort((a, b) => a.recordedAt.localeCompare(b.recordedAt))
      .map((row) => `${row.action}|${row.reason}|${row.effectiveDate}`);
    expect(rows).toEqual([`S|${SUSPEND_REASON}|2026-03-31`, `R|${REINSTATE_REASON}|2026-04-30`]);
    await page.goto(`/console/providers/${NPI}`);
    const panel = page.locator('section').filter({ has: page.getByRole('heading', { name: /^Sanctions/ }) });
    await expect(panel.getByRole('row').filter({ hasText: 'Suspend (S)' }).filter({ hasText: SUSPEND_REASON })).toHaveCount(1);
    await expect(panel.getByRole('row').filter({ hasText: 'Reinstate (R)' }).filter({ hasText: REINSTATE_REASON })).toHaveCount(1);
    await expectNoAxeViolations(page);

    // AC-6: an unknown action code is rejected; nothing is stored and the status is unchanged.
    const unknown = await request.post(`${GATEWAY}/api/v1/sanctions`, {
      headers: { Authorization: `Bearer ${supervisorToken}`, 'Content-Type': 'application/json' },
      data: { npi: NPI, action: 'X', reason: 'J5 SYNTHETIC UNKNOWN ACTION', effectiveDate: '2026-05-31' },
    });
    expect(unknown.status()).toBe(422);
    expect(unknown.headers()['content-type']).toContain('application/problem+json');
    expect(JSON.stringify(await unknown.json())).toContain('action');
    expect((await getJson(request, `/api/v1/sanctions?npi=${NPI}`, supervisorToken)).body.total).toBe(2);
    expect(await statusOf(request, supervisorToken)).toBe('A');
  });
});
