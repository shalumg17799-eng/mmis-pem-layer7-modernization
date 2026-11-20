// MAM-76 Journey J4 (modern/docs/ux/journeys.md#J4, the CICS PINQ replacement) on the running stack, no mocks:
// the set-up replays the J1 API steps (provider 1003000111 enrolled and Active) -> analyst1 finds it by NPI and by Medicaid ID
// -> the detail shows every field as text and code with its panels -> supervisor1 also sees the audit history -> an unknown NPI
// says No provider found -> provider1 cannot use the console and sees only their own record.
// Needs: ./pem.sh up && ./pem.sh seed (no application or provider for NPI 1003000111).
// Passwords are read at run time from the environment or modern/infra/.env.local and never written anywhere.
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { expect, test, type APIRequestContext, type Browser, type Page } from '@playwright/test';
import { signIn, type PemUser } from '../support/keycloak';
import { expectNoAxeViolations } from '../support/page';

const GATEWAY = process.env.PEM_GATEWAY_URL ?? 'http://localhost:8080';
const KEYCLOAK_TOKEN = `${process.env.PEM_KEYCLOAK_URL ?? 'http://localhost:8180'}/realms/pem/protocol/openid-connect/token`;
const NPI = '1003000111';
const MEDICAID_ID = 'TX0000111';
const OTHER_NPI = '1005951276'; // TESTPROV BRAVO, seeded (modern/testdata/README.md)
const UNKNOWN_NPI = '1003000999';

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

async function userPage(browser: Browser, user: PemUser, path: string): Promise<Page> {
  const page = await (await browser.newContext()).newPage();
  await signIn(page, user, path);
  return page;
}

/** "19 Nov 2026" for an ISO date (yyyy-mm-dd): the plain form the UI displays (Q-097, user decision 2026-11-19 19:54). */
const MONTH = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const shownDate = (iso: string): string => `${Number(iso.slice(8, 10))} ${MONTH[Number(iso.slice(5, 7)) - 1]} ${iso.slice(0, 4)}`;
const section = (page: Page, heading: RegExp) =>
  page.locator('section').filter({ has: page.getByRole('heading', { name: heading }) });
const POLL = { timeout: 120_000, intervals: [2_000] };

/** Search on /console/providers with one filled field; returns the GET /api/v1/providers request URL. */
async function search(page: Page, label: string, value: string): Promise<URL> {
  await page.goto('/console/providers');
  await page.getByLabel(label, { exact: true }).fill(value);
  const [sent] = await Promise.all([
    page.waitForRequest((r) => new URL(r.url()).pathname === '/api/v1/providers' && r.method() === 'GET'),
    page.getByRole('button', { name: 'Search' }).click(),
  ]);
  return new URL(sent.url());
}

test.describe('MAM-76 journey J4: analyst inquiry (PINQ replacement)', () => {
  test.setTimeout(300_000);

  test('MAM-76 AC-1..AC-7: search by NPI and Medicaid ID, full detail, audit for supervisors, roles', async ({ browser, request }) => {
    const analystToken = await tokenOf(request, 'analyst1');
    const providerToken = await tokenOf(request, 'provider1');

    // Seed state (AC-7), then the set-up replays the J1 API steps: apply, screened PASS, tier L, approved.
    expect((await getJson(request, `/api/v1/providers/${NPI}`, analystToken)).status, 'run ./pem.sh seed first').toBe(404);
    const applied = await request.post(`${GATEWAY}/api/v1/applications`, {
      headers: { Authorization: `Bearer ${providerToken}`, 'Content-Type': 'application/json' },
      data: {
        npi: NPI,
        medicaidId: MEDICAID_ID,
        providerType: 'IN',
        taxonomy: '207Q00000X',
        individual: { lastName: 'RIVERA', firstName: 'MARIA', licenseNo: 'MD12345', licenseState: 'TX' },
      },
    });
    expect(applied.status(), await applied.text()).toBe(201);
    await expect
      .poll(async () => (await getJson(request, `/api/v1/providers/${NPI}`, analystToken)).body?.riskTier, { message: 'tier L', ...POLL })
      .toBe('L');
    const approved = await request.post(`${GATEWAY}/api/v1/providers/${NPI}/approval`, {
      headers: { Authorization: `Bearer ${analystToken}`, 'Content-Type': 'application/json' },
      data: { decision: 'APPROVE' },
    });
    expect(approved.status(), await approved.text()).toBe(200);
    const provider = (await getJson(request, `/api/v1/providers/${NPI}`, analystToken)).body as {
      enrollStatus: string;
      effectiveDate: string;
      revalidationDueDate: string;
    };
    expect(provider.enrollStatus).toBe('A');

    // AC-1: search by NPI.
    const analyst = await userPage(browser, 'analyst1', '/console/providers');
    const byNpi = await search(analyst, 'NPI', NPI);
    expect(byNpi.searchParams.get('npi')).toBe(NPI);
    await expect(analyst.getByRole('row').filter({ hasText: NPI })).toHaveCount(1);
    await expectNoAxeViolations(analyst);

    // AC-2: search by Medicaid ID.
    const byMedicaidId = await search(analyst, 'Medicaid ID', MEDICAID_ID);
    expect(byMedicaidId.searchParams.get('medicaidId')).toBe(MEDICAID_ID);
    await expect(analyst.getByRole('row').filter({ hasText: NPI })).toHaveCount(1);

    // AC-3: the detail shows every field with its meaning, and the panels (analyst: no audit panel, no audit call).
    const auditCalls: string[] = [];
    analyst.on('request', (r) => {
      if (new URL(r.url()).pathname === `/api/v1/providers/${NPI}/audit`) auditCalls.push(r.url());
    });
    await analyst.goto(`/console/providers/${NPI}`);
    await expect(analyst.getByRole('heading', { level: 1, name: 'Provider detail' })).toBeVisible();
    const main = analyst.getByRole('main');
    for (const text of [NPI, MEDICAID_ID, 'Individual (IN)', '207Q00000X', 'Limited (L)', 'Active (A)', 'Pass (P)',
      shownDate(provider.effectiveDate), shownDate(provider.revalidationDueDate), 'RIVERA', 'MARIA', 'MD12345']) {
      await expect(main, `detail shows ${text}`).toContainText(text);
    }
    for (const panel of [/^Screening/, /^Sanctions/, /^Revalidation/, /^Notices/]) {
      await expect(section(analyst, panel), `panel ${panel}`).toBeVisible();
    }
    await expect(section(analyst, /^Audit/)).toHaveCount(0);
    await expectNoAxeViolations(analyst);
    expect(auditCalls, 'analyst1 makes no audit call').toEqual([]);

    // AC-4: supervisor1 also sees the audit history.
    const supervisor = await (await browser.newContext()).newPage();
    const [auditAnswer] = await Promise.all([
      supervisor.waitForResponse((r) => new URL(r.url()).pathname === `/api/v1/providers/${NPI}/audit`),
      signIn(supervisor, 'supervisor1', `/console/providers/${NPI}`),
    ]);
    expect(auditAnswer.status()).toBe(200);
    await expect(section(supervisor, /^Audit/)).toBeVisible();
    await expectNoAxeViolations(supervisor);

    // AC-5: an unknown NPI says No provider found; the API answers an empty page, not an error.
    await search(analyst, 'NPI', UNKNOWN_NPI);
    await expect(analyst.getByText('No provider found')).toBeVisible();
    const empty = await getJson(request, `/api/v1/providers?npi=${UNKNOWN_NPI}`, analystToken);
    expect(empty.status).toBe(200);
    expect(empty.body.total).toBe(0);

    // AC-6: provider1 cannot use the console and sees only their own record.
    const portal = await userPage(browser, 'provider1', '/console/providers');
    await expect(portal.getByRole('heading', { level: 1, name: 'Access denied' })).toBeVisible();
    expect((await getJson(request, '/api/v1/providers', providerToken)).status).toBe(403);
    expect((await getJson(request, `/api/v1/providers/${OTHER_NPI}`, providerToken)).status).toBe(403);
    await portal.goto('/portal/provider');
    await expect(portal.getByRole('main')).toContainText(NPI);
    await expect(portal.getByRole('main')).not.toContainText(OTHER_NPI);
    await expectNoAxeViolations(portal);
  });
});
