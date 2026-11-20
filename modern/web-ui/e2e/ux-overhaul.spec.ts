// UX overhaul in a real browser (what jsdom cannot show: the keyboard order on the real layout, the computed colour of a sort button, a
// contrast scan of a sorted header, of the Tools panel and of the 404 page). Users sign in with the real Keycloak realm pem; the
// gateway is answered in the browser (e2e/support/gateway-routes.ts). The component tests of the same decisions are tests/ux/*.
//   [UX-OVERHAUL 2] breadcrumb: after the menu in the tab order, Enter follows it, zero axe violations
//   [UX-OVERHAUL 3] sorting: aria-sort and the order of the rows on click, Enter and Space; dark ink on the sorted header; axe
//   [UX-OVERHAUL 5] Tools panel: supervisor, administrator, analyst; axe; keyboard
//   [UX-OVERHAUL 13] page not found: axe, the button to the start page with the keyboard
import { expect, test, type Page } from '@playwright/test';
import { json, mockApi } from './support/gateway-routes';
import { signIn } from './support/keycloak';
import { expectNoAxeViolations, expectShellStructure, focusIsVisible, tabTo } from './support/page';

test.use({ trace: 'off' });

const EMPTY_PAGE = json(200, { items: [], page: 0, size: 20, total: 0 });
const NO_RECORD = json(404, { title: 'Not Found', status: 404 });

const provider = (npi: string, lastName: string, firstName: string, enrollStatus: string, riskTier: string | null) => ({
  npi, medicaidId: `TX${npi.slice(3)}`, providerType: 'IN', taxonomy: '207Q00000X', riskTier, enrollStatus, screenFlag: 'P',
  effectiveDate: '2026-03-02', revalidationDueDate: null, version: 1, individual: { lastName, firstName, licenseNo: 'TX123456', licenseState: 'TX' },
});
const MORENO = provider('1003000201', 'MORENO', 'ANA', 'A', 'M');
const ADAMS = provider('1003000202', 'ADAMS', 'ALAN', 'P', null);
const ZIMMER = provider('1003000203', 'ZIMMER', 'AMY', 'A', 'H');

async function openProviderSearch(page: Page, user: 'analyst1' | 'supervisor1' | 'admin1') {
  const api = await mockApi(page, { 'GET /api/v1/providers': () => json(200, { items: [MORENO, ADAMS, ZIMMER], page: 0, size: 20, total: 3 }) });
  await signIn(page, user, '/console/providers');
  await expect(page.getByRole('heading', { level: 1, name: 'Provider search' })).toBeVisible();
  return api;
}

test('[UX-OVERHAUL 2] the breadcrumb comes after the menu, is reached by keyboard after Sign out, Enter follows it and the page has zero axe violations', async ({ page }) => {
  await mockApi(page, {
    'GET /api/v1/notices': () => EMPTY_PAGE,
    'GET /api/v1/applications': () => EMPTY_PAGE,
    'GET /api/v1/providers/1003000111': () => NO_RECORD,
  });
  await signIn(page, 'provider1', '/portal/notices');
  await expect(page.getByRole('heading', { level: 1, name: 'My notices' })).toBeVisible();

  const names = await page.getByRole('navigation').evaluateAll((elements) => elements.map((element) => element.getAttribute('aria-label')));
  expect(names.slice(0, 2)).toEqual(['Primary navigation', 'Breadcrumb']);
  const trail = page.getByRole('navigation', { name: 'Breadcrumb' });
  await expect(trail.getByRole('listitem')).toHaveText(['Home', 'My notices']);
  await expect(trail.getByRole('listitem').last()).toHaveAttribute('aria-current', 'page');
  await expectShellStructure(page);
  await expectNoAxeViolations(page);

  const home = trail.getByRole('link', { name: 'Home' });
  const stops = await tabTo(page, home);
  expect(stops.at(-1)).toBe('Home');
  expect(stops.indexOf('Sign out')).toBeGreaterThan(stops.indexOf('My notices'));
  expect(await focusIsVisible(page)).toBe(true);
  await page.keyboard.press('Enter');

  await expect(page.getByRole('heading', { level: 1, name: 'My enrolment' })).toBeVisible();
  await expect(page).toHaveURL(/\/portal$/);
  await expect(page.getByRole('navigation', { name: 'Breadcrumb' })).toHaveCount(0);
});

test('[UX-OVERHAUL 3] a sort header sorts on click, Enter and Space, tells the state with aria-sort, keeps dark ink and the sorted page has zero axe violations', async ({ page }) => {
  const api = await openProviderSearch(page, 'analyst1');
  await page.getByLabel('NPI', { exact: true }).fill('1003000201');
  await page.getByRole('button', { name: 'Search' }).click();
  const table = page.getByRole('table', { name: 'Search results' });
  await expect(table.getByRole('row')).toHaveCount(4);
  const names = () => table.locator('tbody tr td:nth-child(2)').allTextContents();
  const header = table.getByRole('columnheader', { name: 'Name' });
  const button = header.getByRole('button', { name: 'Name' });

  expect(await names()).toEqual(['MORENO ANA', 'ADAMS ALAN', 'ZIMMER AMY']);
  await expect(header).not.toHaveAttribute('aria-sort');

  await button.click();
  await expect(header).toHaveAttribute('aria-sort', 'ascending');
  expect(await names()).toEqual(['ADAMS ALAN', 'MORENO ANA', 'ZIMMER AMY']);
  await expect(button).toHaveCSS('color', 'rgb(27, 27, 27)');
  await expectNoAxeViolations(page);

  await button.focus();
  expect(await focusIsVisible(page)).toBe(true);
  await page.keyboard.press('Enter');
  await expect(header).toHaveAttribute('aria-sort', 'descending');
  expect(await names()).toEqual(['ZIMMER AMY', 'MORENO ANA', 'ADAMS ALAN']);
  await expectNoAxeViolations(page);

  await page.keyboard.press('Space');
  await expect(header).not.toHaveAttribute('aria-sort');
  expect(await names()).toEqual(['MORENO ANA', 'ADAMS ALAN', 'ZIMMER AMY']);
  expect(api.to('GET', '/api/v1/providers')).toHaveLength(1);
});

test('[UX-OVERHAUL 3] pressing another column moves the sort and the empty risk tier stays last both ways', async ({ page }) => {
  await openProviderSearch(page, 'analyst1');
  await page.getByLabel('NPI', { exact: true }).fill('1003000201');
  await page.getByRole('button', { name: 'Search' }).click();
  const table = page.getByRole('table', { name: 'Search results' });
  await expect(table.getByRole('row')).toHaveCount(4);
  const npis = () => table.locator('tbody tr td:nth-child(1)').allTextContents();

  await table.getByRole('columnheader', { name: 'Name' }).getByRole('button').click();
  await table.getByRole('columnheader', { name: 'Risk' }).getByRole('button').click();

  await expect(table.getByRole('columnheader', { name: 'Name' })).not.toHaveAttribute('aria-sort');
  await expect(table.getByRole('columnheader', { name: 'Risk' })).toHaveAttribute('aria-sort', 'ascending');
  expect(await npis()).toEqual(['1003000203', '1003000201', '1003000202']);
  await table.getByRole('columnheader', { name: 'Risk' }).getByRole('button').click();
  expect(await npis()).toEqual(['1003000201', '1003000203', '1003000202']);
});

test('[UX-OVERHAUL 5] the supervisor sees the Tools panel with the work queues, it has zero axe violations and the link works with the keyboard', async ({ page }) => {
  await openProviderSearch(page, 'supervisor1');

  const tools = page.getByRole('region', { name: 'Tools' });
  await expect(tools.getByRole('heading', { level: 2, name: 'Tools' })).toBeVisible();
  await expect(tools.getByRole('link')).toHaveText(['Review providers waiting for a decision']);
  await expectNoAxeViolations(page);

  const link = tools.getByRole('link', { name: 'Review providers waiting for a decision' });
  const stops = await tabTo(page, link);
  expect(stops.indexOf('Sign out')).toBeLessThan(stops.length - 1);
  expect(await focusIsVisible(page)).toBe(true);
  await page.keyboard.press('Enter');
  await expect(page.getByRole('heading', { level: 1, name: 'Work queues' })).toBeVisible();
  await expect(page).toHaveURL(/\/console\/queues$/);
});

test('[UX-OVERHAUL 5] the administrator sees two tools, the batch jobs and the risk-tier rules, and the page has zero axe violations', async ({ page }) => {
  await openProviderSearch(page, 'admin1');

  const tools = page.getByRole('region', { name: 'Tools' });
  await expect(tools.getByRole('link')).toHaveText(['Run legacy jobs and download their files', 'Change how risk levels are assigned']);
  await expect(tools.getByRole('link', { name: 'Run legacy jobs and download their files' })).toHaveAttribute('href', '/console/admin/jobs');
  await expect(tools.getByRole('link', { name: 'Change how risk levels are assigned' })).toHaveAttribute('href', '/console/admin/risk-rules');
  await expectNoAxeViolations(page);
});

test('[UX-OVERHAUL 5] the analyst has no Tools panel', async ({ page }) => {
  await openProviderSearch(page, 'analyst1');

  await expect(page.getByRole('region', { name: 'Tools' })).toHaveCount(0);
  await expect(page.getByRole('heading', { name: 'Tools' })).toHaveCount(0);
});

test('[UX-OVERHAUL 13] the page-not-found page has zero axe violations and its button leads to the start page of the role with the keyboard', async ({ page }) => {
  await mockApi(page, {
    'GET /api/v1/applications': () => EMPTY_PAGE,
    'GET /api/v1/providers/1003000111': () => NO_RECORD,
  });
  await signIn(page, 'provider1', '/portal');
  await expect(page.getByRole('heading', { level: 1, name: 'My enrolment' })).toBeVisible();

  await page.goto('/no/such/page');

  await expect(page.getByRole('heading', { level: 1, name: 'Page not found' })).toBeVisible();
  await expect(page.getByText('Error 404. The page you asked for does not exist. Use the menu to continue.')).toBeVisible();
  await expectShellStructure(page);
  await expect(page.getByRole('navigation', { name: 'Breadcrumb' })).toHaveCount(0);
  await expectNoAxeViolations(page);

  const button = page.getByRole('main').getByRole('link', { name: 'Go to My enrolment' });
  await tabTo(page, button);
  expect(await focusIsVisible(page)).toBe(true);
  await page.keyboard.press('Enter');
  await expect(page.getByRole('heading', { level: 1, name: 'My enrolment' })).toBeVisible();
  await expect(page).toHaveURL(/\/portal$/);
});
