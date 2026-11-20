// MAM-77 AC-8 in a real browser: analyst1 signs in with the real Keycloak realm pem and uses /console/providers; the provider API
// (GET /api/v1/providers?npi= | medicaidId= | name=) is answered in the browser (e2e/support/gateway-routes.ts). Axe scans: the empty
// screen, a results page, the "No provider found" state and a validation-error state. The screen is used with the keyboard only.
import { expect, test, type Page } from '@playwright/test';
import { json, mockApi, type RouteHandler } from './support/gateway-routes';
import { signIn } from './support/keycloak';
import { expectNoAxeViolations, tabTo } from './support/page';

test.use({ trace: 'off' });

const RIVERA = {
  npi: '1003000111', medicaidId: 'TX0000111', providerType: 'IN', taxonomy: '207Q00000X', riskTier: 'L', enrollStatus: 'A', screenFlag: 'P',
  effectiveDate: '2026-03-02', revalidationDueDate: '2031-03-02', version: 1, individual: { lastName: 'RIVERA', firstName: 'MARIA', licenseNo: 'MD12345', licenseState: 'TX' },
};

const providers: RouteHandler = (call) => {
  const npi = call.query.get('npi');
  const medicaidId = call.query.get('medicaidId');
  const name = call.query.get('name')?.toUpperCase();
  const hit = (!npi || npi === RIVERA.npi) && (!medicaidId || medicaidId === RIVERA.medicaidId) && (!name || 'RIVERA MARIA'.includes(name));
  return json(200, { items: hit ? [RIVERA] : [], page: 0, size: 20, total: hit ? 1 : 0 });
};

async function open(page: Page, handler: RouteHandler = providers) {
  const api = await mockApi(page, { 'GET /api/v1/providers': handler });
  await signIn(page, 'analyst1', '/console/providers');
  await expect(page.getByRole('heading', { level: 1, name: 'Provider search' })).toBeVisible();
  return api;
}

const field = (page: Page, label: string) => page.getByLabel(label, { exact: true });

test('[MAM-77 AC-1][MAM-77 AC-8] the empty screen shows the three labelled inputs and has zero axe violations; no search call was made', async ({ page }) => {
  const api = await open(page);

  for (const label of ['NPI', 'Medicaid ID', 'Name contains']) await expect(field(page, label)).toBeVisible();
  await expect(field(page, 'NPI')).toHaveValue('');
  await expect(page.getByRole('button', { name: 'Search' })).toBeVisible();
  await expect(page.getByRole('table')).toHaveCount(0);
  expect(api.to('GET', '/api/v1/providers')).toHaveLength(0);
  await expectNoAxeViolations(page);
});

test('[MAM-77 AC-2][MAM-77 AC-3][MAM-77 AC-8] search by NPI, by Medicaid ID and by name: the request carries only the filled input; the results page has zero axe violations', async ({ page }) => {
  const api = await open(page);

  await field(page, 'NPI').fill('1003000111');
  await page.getByRole('button', { name: 'Search' }).click();
  const row = page.getByRole('row').filter({ hasText: '1003000111' });
  await expect(row).toHaveCount(1);
  await expect(row.getByRole('cell')).toHaveText(['1003000111', 'RIVERA MARIA', 'Individual (IN)', 'Active (A)', 'Limited (L)']);
  await expect(row.getByRole('link', { name: '1003000111' })).toHaveAttribute('href', '/console/providers/1003000111');
  await expectNoAxeViolations(page);

  await field(page, 'NPI').fill('');
  await field(page, 'Medicaid ID').fill('TX0000111');
  await page.getByRole('button', { name: 'Search' }).click();
  await expect.poll(() => api.to('GET', '/api/v1/providers').length).toBe(2);
  await field(page, 'Medicaid ID').fill('');
  await field(page, 'Name contains').fill('RIVERA');
  await page.getByRole('button', { name: 'Search' }).click();
  await expect.poll(() => api.to('GET', '/api/v1/providers').length).toBe(3);

  expect(api.to('GET', '/api/v1/providers').map((call) => Object.fromEntries(call.query.entries()))).toEqual([
    { npi: '1003000111' },
    { medicaidId: 'TX0000111' },
    { name: 'RIVERA' },
  ]);
  expect(api.to('GET', '/api/v1/providers/1003000111')).toHaveLength(0);
});

test('[MAM-77 AC-4][MAM-77 AC-8] an empty and a malformed entry are not searched: the summary links to the NPI field and has zero axe violations', async ({ page }) => {
  const api = await open(page);

  await page.getByRole('button', { name: 'Search' }).click();
  await expect(page.getByRole('heading', { level: 2, name: '1 error in the search' })).toBeVisible();
  await expect(field(page, 'NPI')).toHaveAccessibleDescription(/Enter an NPI, a Medicaid ID or a name/);
  await expectNoAxeViolations(page);
  await page.getByRole('link', { name: 'Enter an NPI, a Medicaid ID or a name' }).click();
  await expect(field(page, 'NPI')).toBeFocused();

  await field(page, 'NPI').fill('12345');
  await page.getByRole('button', { name: 'Search' }).click();
  await expect(page.getByRole('link', { name: 'NPI must be 10 digits' })).toBeVisible();
  await expect(field(page, 'NPI')).toHaveValue('12345');
  expect(api.to('GET', '/api/v1/providers')).toHaveLength(0);
});

test('[MAM-77 AC-5][MAM-77 AC-8] an unknown NPI shows "No provider found" in a status area and has zero axe violations', async ({ page }) => {
  await open(page);

  await field(page, 'NPI').fill('1003000999');
  await page.getByRole('button', { name: 'Search' }).click();

  await expect(page.getByRole('status').filter({ hasText: 'No provider found' })).toBeVisible();
  await expect(page.getByRole('table')).toHaveCount(0);
  await expectNoAxeViolations(page);
});

test('[MAM-77 AC-7] a 503 reads "The search could not be completed. Try again." and is not "No provider found"', async ({ page }) => {
  await open(page, () => json(503, { title: 'Service Unavailable', status: 503 }));

  await field(page, 'NPI').fill('1003000111');
  await page.getByRole('button', { name: 'Search' }).click();

  await expect(page.getByText('The search could not be completed. Try again.')).toBeVisible();
  await expect(page.getByText('No provider found')).toHaveCount(0);
  await expectNoAxeViolations(page);
});

test('[MAM-77 AC-8] the whole inquiry is done with the keyboard only: Tab to the name, type, Enter, Tab to the result link, Enter', async ({ page }) => {
  await open(page);

  await tabTo(page, field(page, 'Name contains'));
  await page.keyboard.type('RIVERA');
  await page.keyboard.press('Enter');
  const link = page.getByRole('link', { name: '1003000111' });
  await expect(link).toBeVisible();
  await tabTo(page, link);
  await page.keyboard.press('Enter');

  await expect(page).toHaveURL(/\/console\/providers\/1003000111$/);
});
