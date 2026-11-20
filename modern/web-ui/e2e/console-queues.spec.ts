// MAM-85 AC-8 in a real browser: analyst1 signs in with the real Keycloak realm pem and opens /console/queues; the four APIs are
// answered in the browser (e2e/support/gateway-routes.ts) with the answers of their contracts. Axe scans: all four sections
// filled, all empty, and one section failed. The Review and NPI links are used with the keyboard only.
import { expect, test, type Page } from '@playwright/test';
import { json, mockApi, type RouteHandler } from './support/gateway-routes';
import { signIn } from './support/keycloak';
import { expectNoAxeViolations, tabTo } from './support/page';

test.use({ trace: 'off' });

const page_of = (items: unknown[], total = items.length) => ({ items, page: 0, size: 20, total });
const PENDING = {
  npi: '1003000111', medicaidId: 'TX0000111', providerType: 'IN', taxonomy: '207Q00000X', riskTier: null, enrollStatus: 'P', screenFlag: 'P',
  effectiveDate: '2026-03-02', revalidationDueDate: null, version: 1, individual: { lastName: 'RIVERA', firstName: 'MARIA', licenseNo: 'MD12345', licenseState: 'TX' },
};
const FAILED = {
  npi: '1003000444', medicaidId: 'TX0000444', providerType: 'GR', taxonomy: '193200000X', riskTier: null, enrollStatus: 'P', screenFlag: 'F',
  effectiveDate: '2026-03-02', revalidationDueDate: null, version: 1, organization: { legalName: 'EXCLUDED CLINIC LLC', dbaName: 'EXCLUDED CLINIC', ownerNpi: '1990000111' },
};
const SCREENING = page_of([
  {
    screeningId: '5c1f0a9e-3b7d-4a52-9e64-0d8f2b6a7c10', npi: '1003000444', reason: 'INITIAL', status: 'COMPLETED', result: 'FAIL',
    results: [{ source: 'OIGLEIE', result: 'F', verdictAt: '2026-03-02T09:14:00Z', detail: 'Owner 1990000111 is on the OIG exclusion list', errorCode: 'E203' }],
    startedAt: '2026-03-02T09:13:00Z', completedAt: '2026-03-02T09:14:00Z',
  },
]);
const REJECTED = {
  applicationId: '8f1c2e0a-5b3d-4e7a-9c11-2a6d4b8e0f33', status: 'REJECTED', npi: '1003000111', medicaidId: 'TX0000111', providerType: 'IN', taxonomy: '',
  errors: [{ code: 'E104', field: 'taxonomy', message: 'Enter the taxonomy code.' }], source: 'PORTAL', submittedAt: '2026-03-01T10:00:00Z',
};
const DUE = { caseId: '3b1f6c52-8d0e-4a77-b0a4-6f2d9c1e5a10', npi: '1003000222', dueDate: '2026-02-28', status: 'DUE', openedAt: '2026-03-01T00:00:00Z' };

function routes(filled: boolean, over: Record<string, RouteHandler> = {}): Record<string, RouteHandler> {
  return {
    'GET /api/v1/providers': (call) => json(200, page_of(filled ? [call.query.get('screenFlag') === 'F' ? FAILED : PENDING] : [])),
    'GET /api/v1/screenings': () => json(200, filled ? SCREENING : page_of([])),
    'GET /api/v1/revalidations': (call) => json(200, page_of(filled && call.query.get('status') === 'DUE' ? [DUE] : [])),
    'GET /api/v1/applications': () => json(200, page_of(filled ? [REJECTED] : [])),
    // the approval page that the Review link opens
    [`GET /api/v1/providers/${PENDING.npi}`]: () => json(200, PENDING),
    ...over,
  };
}

const section = (page: Page, name: RegExp) => page.locator('section').filter({ has: page.getByRole('heading', { name }) });

async function open(page: Page, handlers: Record<string, RouteHandler>) {
  const api = await mockApi(page, handlers);
  await signIn(page, 'analyst1', '/console/queues');
  await expect(page.getByRole('heading', { level: 1, name: 'Work queues' })).toBeVisible();
  return api;
}

test('[MAM-85 AC-8] all four sections filled: Pending approval, Failed screening, Revalidation due and Rejected applications have zero axe violations', async ({ page }) => {
  await open(page, routes(true));

  await expect(section(page, /^Pending approval \(1\)/).getByRole('row').filter({ hasText: '1003000111' })).toHaveCount(1);
  await expect(section(page, /^Failed screening \(1\)/).getByRole('row').filter({ hasText: 'OIG exclusion list (LEIE): E203 owner excluded' })).toHaveCount(1);
  await expect(section(page, /^Revalidation due \(1\)/).getByRole('row').filter({ hasText: '28 Feb 2026' })).toHaveCount(1);
  await expect(section(page, /^Rejected applications \(1\)/).getByRole('row').filter({ hasText: 'E104' })).toHaveCount(1);
  await expectNoAxeViolations(page);
});

test('[MAM-85 AC-8] all four sections empty: the four messages are shown and the page has zero axe violations', async ({ page }) => {
  await open(page, routes(false));

  for (const message of ['Nothing pending approval', 'No failed screenings', 'No revalidations due', 'No rejected applications']) {
    await expect(page.getByText(message)).toBeVisible();
  }
  await expectNoAxeViolations(page);
});

test('[MAM-85 AC-6][MAM-85 AC-8] one section failed (revalidations 503): the others are shown, Revalidation due says "The list could not be loaded" with Retry, zero axe violations', async ({ page }) => {
  await open(page, routes(true, { 'GET /api/v1/revalidations': () => json(503, { title: 'Service Unavailable', status: 503 }) }));

  const failed = section(page, /^Revalidation due/);
  await expect(failed.getByText('The list could not be loaded')).toBeVisible();
  await expect(failed.getByRole('button', { name: 'Retry' })).toBeVisible();
  await expect(page.getByText('No revalidations due')).toHaveCount(0);
  await expect(section(page, /^Pending approval/).getByRole('row').filter({ hasText: '1003000111' })).toHaveCount(1);
  await expectNoAxeViolations(page);
});

test('[MAM-85 AC-8] the Review link is reached with Tab and opened with Enter', async ({ page }) => {
  await open(page, routes(true));
  const review = section(page, /^Pending approval/).getByRole('link', { name: 'Review' });
  await expect(review).toBeVisible();

  await tabTo(page, review);
  await page.keyboard.press('Enter');

  await expect(page).toHaveURL(/\/console\/providers\/1003000111\/approval$/);
  await expect(page.getByRole('heading', { level: 1, name: 'Approve or deny' })).toBeVisible();
});

test('[MAM-85 AC-8] the NPI link of a failed screening is reached with Tab and opened with Enter', async ({ page }) => {
  await open(page, routes(true, { [`GET /api/v1/providers/${FAILED.npi}`]: () => json(200, FAILED) }));
  const npi = section(page, /^Failed screening/).getByRole('link', { name: FAILED.npi });
  await expect(npi).toBeVisible();

  await tabTo(page, npi);
  await page.keyboard.press('Enter');

  await expect(page).toHaveURL(/\/console\/providers\/1003000444$/);
  await expect(page.getByRole('heading', { level: 1, name: 'Provider detail' })).toBeVisible();
});
