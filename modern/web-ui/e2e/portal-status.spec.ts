// MAM-66 AC-6 in a real browser: provider1 signs in with the real Keycloak realm pem and opens /portal/applications/:id; the
// enrollment API is answered in the browser (e2e/support/gateway-routes.ts). Axe scans: a validated application and a rejected
// application with two errors (and the not-found and failed-load states). The links and Retry are used with the keyboard only.
import { expect, test, type Page } from '@playwright/test';
import { json, mockApi, type RouteHandler } from './support/gateway-routes';
import { signIn } from './support/keycloak';
import { expectNoAxeViolations, expectShellStructure, tabTo } from './support/page';

test.use({ trace: 'off' });

const ID = '0b9c5d1e-6a4f-4c1a-9a43-3c2d6f0a1b11';
const VALIDATED = {
  applicationId: ID, status: 'VALIDATED', npi: '1003000111', medicaidId: 'TX0000111', providerType: 'IN', taxonomy: '207Q00000X',
  individual: { lastName: 'RIVERA', firstName: 'MARIA', licenseNo: 'MD12345', licenseState: 'TX' }, source: 'PORTAL', submittedAt: '2026-03-01T10:00:00Z',
};
const REJECTED = {
  ...VALIDATED, status: 'REJECTED', providerType: 'XX', taxonomy: '', individual: undefined,
  errors: [
    { code: 'E102', field: 'providerType', message: 'The provider type must be IN, GR or FA.' },
    { code: 'E104', field: 'taxonomy', message: 'Taxonomy code is required' },
  ],
};

async function open(page: Page, handler: RouteHandler) {
  const api = await mockApi(page, { [`GET /api/v1/applications/${ID}`]: handler });
  await signIn(page, 'provider1', `/portal/applications/${ID}`);
  await expect(page.getByRole('heading', { level: 1, name: 'Application status' })).toBeVisible();
  return api;
}

/** The value of a row of a key-value list (UI polish, Q-097): <div><dt>label</dt><dd>value</dd></div>. */
const valueOf = (page: Page, label: string) => page.locator('dl > div').filter({ has: page.getByText(label, { exact: true }) }).locator('dd');

test('[MAM-66 AC-1][MAM-66 AC-6] a validated application shows its details and "No validation errors" and has zero axe violations', async ({ page }) => {
  const api = await open(page, () => json(200, VALIDATED));

  await expect(page.getByRole('main').getByText('Validated', { exact: true })).toBeVisible();
  await expect(valueOf(page, 'NPI')).toHaveText('1003000111');
  await expect(valueOf(page, 'Submitted')).toHaveText('1 Mar 2026');
  await expect(valueOf(page, 'Provider type')).toHaveText('Individual (IN)');
  await expect(page.getByText('No validation errors')).toBeVisible();
  await expectShellStructure(page);
  await expectNoAxeViolations(page);
  expect(api.to('GET', `/api/v1/applications/${ID}`)).toHaveLength(1);
  expect(api.to('GET', '/api/v1/applications')).toHaveLength(0);
});

test('[MAM-66 AC-2][MAM-66 AC-3][MAM-66 AC-6] a rejected application lists its two errors once each and has zero axe violations', async ({ page }) => {
  await open(page, () => json(200, REJECTED));

  await expect(page.getByRole('main').getByText('Rejected', { exact: true })).toBeVisible();
  const errors = page.getByRole('table').filter({ has: page.getByRole('columnheader', { name: 'Code' }) });
  await expect(errors.getByRole('columnheader')).toHaveText(['Code', 'Field', 'Message']);
  await expect(errors.locator('tbody tr')).toHaveCount(2);
  await expect(errors.locator('tbody tr').nth(0).getByRole('cell')).toHaveText(['E102', 'providerType', 'The provider type must be IN, GR or FA.']);
  await expect(errors.locator('tbody tr').nth(1).getByRole('cell')).toHaveText(['E104', 'taxonomy', 'Taxonomy code is required']);
  await expectNoAxeViolations(page);
});

test('[MAM-66 AC-4][MAM-66 AC-6] an unknown application shows "Application not found"; the link to My enrolment works with the keyboard only; zero axe violations', async ({ page }) => {
  await open(page, () => json(404, { title: 'Not Found', status: 404 }));

  await expect(page.getByText('Application not found')).toBeVisible();
  await expectNoAxeViolations(page);
  const link = page.getByRole('main').getByRole('link', { name: 'My enrolment' });
  await tabTo(page, link);
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/portal$/);
});

test('[MAM-66 AC-4] another provider\'s application (403) shows an access-denied message and none of its data', async ({ page }) => {
  await open(page, () => json(403, { title: 'Forbidden', status: 403 }));

  await expect(page.getByText(/Access denied/i)).toBeVisible();
  await expect(page.getByRole('table')).toHaveCount(0);
  await expect(page.getByRole('region', { name: 'Application', exact: true })).toHaveCount(0);
  await expectNoAxeViolations(page);
});

test('[MAM-66 AC-5][MAM-66 AC-6] a failed load shows "The application could not be loaded. Try again."; Retry works with the keyboard only; zero axe violations', async ({ page }) => {
  let up = false;
  await open(page, () => (up ? json(200, VALIDATED) : json(503, { title: 'Service Unavailable', status: 503 })));
  await expect(page.getByText('The application could not be loaded. Try again.')).toBeVisible();
  await expect(page.getByRole('main').getByText(/Validated|Rejected/)).toHaveCount(0);
  await expectNoAxeViolations(page);

  up = true;
  await tabTo(page, page.getByRole('button', { name: 'Retry' }));
  await page.keyboard.press('Enter');
  await expect(page.getByRole('main').getByText('Validated', { exact: true })).toBeVisible();
});
