// MAM-73 AC-1 and AC-8 in a real browser: /directory is opened by a visitor who is NOT signed in (no Keycloak at all). The
// directory API is answered in the browser (e2e/support/gateway-routes.ts); every request is recorded, so the test can show that
// none carried an Authorization header. Axe scans: the empty page, a results page, the no-match state and the failure state.
import { expect, test, type Page } from '@playwright/test';
import { json, mockApi, type RouteHandler } from './support/gateway-routes';
import { expectNoAxeViolations, expectShellStructure, tabTo } from './support/page';

const RIVERA = { npi: '1003000111', medicaidId: 'TX0000111', providerType: 'IN', riskTier: 'L', displayName: 'MARIA RIVERA', taxonomy: '207Q00000X' };
const HOSPICE = { npi: '1003000333', medicaidId: 'TX0000333', providerType: 'FA', riskTier: 'H', displayName: 'SUNSET HOSPICE INC', taxonomy: '251G00000X' };

const answer = (items: unknown[], total = items.length) => json(200, { items, page: 0, size: 20, total });

const found: Record<string, RouteHandler> = {
  'GET /api/v1/directory/providers': (call) => {
    const name = call.query.get('name')?.toUpperCase();
    return answer([RIVERA, HOSPICE].filter((entry) => !name || entry.displayName.includes(name)));
  },
};

async function open(page: Page, routes: Record<string, RouteHandler>) {
  const api = await mockApi(page, routes);
  await page.goto('/directory');
  await expect(page.getByRole('heading', { level: 1, name: 'Find a Medicaid provider' })).toBeVisible();
  return api;
}

const search = async (page: Page, name: string) => {
  await page.getByLabel('Name', { exact: true }).fill(name);
  await page.getByRole('button', { name: 'Search' }).click();
};

test('[MAM-73 AC-1] a visitor who is not signed in opens /directory: no redirect to Keycloak, the form is there', async ({ page }) => {
  await open(page, found);

  await expect(page).toHaveURL(/\/directory$/);
  await expect(page.getByLabel('Name', { exact: true })).toBeVisible();
  const options = (await page.getByLabel('Provider type', { exact: true }).locator('option').allTextContents()).map((option) => option.trim());
  expect(options).toEqual(['Any', 'Individual (IN)', 'Group (GR)', 'Facility (FA)']);
  await expect(page.getByRole('button', { name: 'Search' })).toBeVisible();
});

test('[MAM-73 AC-1][MAM-73 AC-2] searching HOSPICE then RIVERA lists the providers and no request carries an Authorization header', async ({ page }) => {
  const api = await open(page, found);

  await search(page, 'HOSPICE');
  await expect(page.getByRole('row').filter({ hasText: '1003000333' })).toContainText('SUNSET HOSPICE INC');
  await search(page, 'RIVERA');
  const row = page.getByRole('row').filter({ hasText: '1003000111' });
  await expect(row).toHaveCount(1);
  await expect(row).toContainText('MARIA RIVERA');
  await expect(row).toContainText('Individual (IN)');

  const calls = api.to('GET', '/api/v1/directory/providers');
  expect(calls.map((call) => call.query.get('name'))).toEqual(['HOSPICE', 'RIVERA']);
  expect(api.calls.length).toBeGreaterThan(0);
  for (const call of api.calls) expect(call.authorization).toBeUndefined();
});

test('[MAM-73 AC-8] the empty page has zero axe violations, one h1 and the landmarks header and main with a skip link', async ({ page }) => {
  await open(page, found);

  await expectShellStructure(page);
  await expect(page.getByRole('link', { name: 'Skip to main content' })).toBeAttached();
  await expectNoAxeViolations(page);
});

test('[MAM-73 AC-8] a results page has zero axe violations', async ({ page }) => {
  await open(page, found);
  await search(page, 'RIVERA');
  await expect(page.getByRole('row').filter({ hasText: '1003000111' })).toHaveCount(1);

  await expectNoAxeViolations(page);
});

test('[MAM-73 AC-8] the no-match state ("No providers found") has zero axe violations', async ({ page }) => {
  await open(page, found);
  await search(page, 'NOBODY');
  await expect(page.getByText('No providers found')).toBeVisible();

  await expectNoAxeViolations(page);
});

test('[MAM-73 AC-7][MAM-73 AC-8] the failure state has zero axe violations and says "The directory is not available right now. Try again."', async ({ page }) => {
  await open(page, { 'GET /api/v1/directory/providers': () => json(503, { title: 'Service Unavailable', status: 503 }) });
  await search(page, 'RIVERA');
  await expect(page.getByText('The directory is not available right now. Try again.')).toBeVisible();
  await expect(page.getByText('No providers found')).toHaveCount(0);

  await expectNoAxeViolations(page);
});

test('[MAM-73 AC-8] the whole search is done with the keyboard only: Tab to Name, type, Tab to the list, Tab to Search, Enter', async ({ page }) => {
  await open(page, found);
  const name = page.getByLabel('Name', { exact: true });

  await tabTo(page, name);
  await page.keyboard.type('HOSPICE');
  await page.keyboard.press('Tab');
  await expect(page.getByLabel('Provider type', { exact: true })).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(page.getByRole('button', { name: 'Search' })).toBeFocused();
  await page.keyboard.press('Enter');

  await expect(page.getByRole('row').filter({ hasText: '1003000333' })).toHaveCount(1);
});
