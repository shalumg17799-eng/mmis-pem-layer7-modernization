// MAM-72 Journey J2 (modern/docs/ux/journeys.md#J2) on the running stack, no mocks:
// provider1 submits the J1 values with an empty taxonomy -> E104 at the field and in the summary -> nothing registered ->
// the application shows Rejected with exactly one error row -> the "Application rejected" notice is listed.
// Needs: ./pem.sh up && ./pem.sh seed (no application, provider or notice for NPI 1003000111).
// Passwords are read at run time from the environment or modern/infra/.env.local and never written anywhere.
// Events (Q-094 default A, accepted by the user): ApplicationRejected is proven here by its effects (stored rejection with
// E104 only, REJECTED notice); its payload is proven on its topic by MAM-46 and consumed in MAM-59.
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { expect, test, type APIRequestContext } from '@playwright/test';
import { signIn, type PemUser } from '../support/keycloak';
import { expectNoAxeViolations } from '../support/page';

const GATEWAY = process.env.PEM_GATEWAY_URL ?? 'http://localhost:8080';
const KEYCLOAK_TOKEN = `${process.env.PEM_KEYCLOAK_URL ?? 'http://localhost:8180'}/realms/pem/protocol/openid-connect/token`;
const NPI = '1003000111';

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

test.describe('MAM-72 journey J2: an application is rejected', () => {
  test.setTimeout(240_000);

  test('MAM-72 AC-1..AC-5: empty taxonomy -> E104, nothing registered, Rejected with one error, notice', async ({ browser, request }) => {
    const analystToken = await tokenOf(request, 'analyst1');
    const providerToken = await tokenOf(request, 'provider1');

    // Seed state (AC-5): nothing yet for NPI 1003000111.
    expect((await getJson(request, `/api/v1/providers/${NPI}`, analystToken)).status, 'run ./pem.sh seed first').toBe(404);
    expect((await getJson(request, `/api/v1/applications?npi=${NPI}`, analystToken)).body.total, 'run ./pem.sh seed first').toBe(0);

    // AC-1: the J1 values with an empty taxonomy -> E104 in the summary (linked to the field) and at the field only.
    const page = await (await browser.newContext()).newPage();
    await signIn(page, 'provider1', '/portal/apply');
    await expect(page.getByRole('heading', { level: 1, name: 'Apply for enrolment' })).toBeVisible();
    await expectNoAxeViolations(page);
    await page.getByLabel(/^Provider type/).selectOption('IN');
    await page.getByLabel(/^NPI/).fill(NPI);
    await page.getByLabel(/^Medicaid ID/).fill('TX0000111');
    await page.getByLabel(/^Last name/).fill('RIVERA');
    await page.getByLabel(/^First name/).fill('MARIA');
    await page.getByLabel(/^Licence number/).fill('MD12345');
    await page.getByLabel(/^Licence state/).fill('TX');
    const [answer] = await Promise.all([
      page.waitForResponse((response) => response.url().endsWith('/api/v1/applications') && response.request().method() === 'POST'),
      page.getByRole('button', { name: 'Submit application' }).click(),
    ]);
    expect(answer.status()).toBe(422);
    await expect(page).toHaveURL(/\/portal\/apply$/);
    await expect(page.getByRole('heading', { level: 2, name: '1 error in the application' })).toBeVisible();
    const summaryLink = page.getByRole('main').getByRole('link', { name: /^E104: / });
    await expect(summaryLink).toHaveCount(1);
    await expect(page.getByText(/\bE104:/)).toHaveCount(2); // once in the summary, once at the taxonomy field
    await expect(page.getByText(/\bE10[123]:/)).toHaveCount(0); // no other field shows an error
    await summaryLink.click();
    await expect(page.getByLabel(/^Taxonomy code/)).toBeFocused();
    await expectNoAxeViolations(page);

    // AC-3: the application shows Rejected with exactly one error row, E104 on taxonomy (found from My enrolment).
    await page.goto('/portal');
    const row = page.getByRole('row').filter({ hasText: 'Rejected' });
    await expect(row).toHaveCount(1);
    await row.getByRole('link', { name: /View/ }).click();
    await expect(page).toHaveURL(/\/portal\/applications\/[0-9a-f-]{36}$/);
    const applicationId = page.url().split('/').pop() as string;
    await expect(page.getByRole('heading', { level: 1, name: 'Application status' })).toBeVisible();
    await expect(page.getByRole('main').getByText('Rejected', { exact: true })).toBeVisible();
    const errorTable = page.getByRole('table', { name: 'Validation errors' });
    await expect(errorTable.getByRole('row')).toHaveCount(2); // header + exactly one error row
    await expect(errorTable.getByRole('row').nth(1)).toContainText('E104');
    await expect(errorTable.getByRole('row').nth(1)).toContainText('taxonomy');
    await expectNoAxeViolations(page);
    const stored = await getJson(request, `/api/v1/applications/${applicationId}`, providerToken);
    expect(stored.body.status).toBe('REJECTED');
    expect((stored.body.errors as Array<{ code: string; field: string }>).map((e) => `${e.code}/${e.field}`)).toEqual(['E104/taxonomy']);

    // AC-4: the provider is told: REJECTED in the notice API and "Application rejected" on /portal/notices.
    await expect
      .poll(
        async () =>
          ((await getJson(request, '/api/v1/notices', providerToken)).body?.items ?? []).map((n: { template: string }) => n.template),
        { message: 'REJECTED notice listed for 1003000111', timeout: 120_000, intervals: [2_000] },
      )
      .toContain('REJECTED');
    await page.goto('/portal/notices');
    await expect(page.getByRole('row').filter({ hasText: 'Application rejected (REJECTED)' })).toHaveCount(1);
    await expectNoAxeViolations(page);

    // AC-2: nothing was registered (checked after the rejection was fully handled, shown by the notice).
    expect((await getJson(request, `/api/v1/providers/${NPI}`, analystToken)).status).toBe(404);
  });
});
