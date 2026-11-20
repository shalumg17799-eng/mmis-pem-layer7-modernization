// MAM-82 AC-8 in a real browser: analyst1, supervisor1 and admin1 sign in with the real Keycloak realm pem and open the detail page
// of 1003000111; the five services are answered in the browser (e2e/support/gateway-routes.ts). Axe scans: a fully populated page per
// role and a page with a failed panel. The panels are read with the keyboard only (the Retry button).
import { expect, test, type Page } from '@playwright/test';
import { json, mockApi, type RouteHandler } from './support/gateway-routes';
import { signIn, type PemUser } from './support/keycloak';
import { expectNoAxeViolations, tabTo } from './support/page';

test.use({ trace: 'off' });

const NPI = '1003000111';
const RIVERA = {
  npi: NPI, medicaidId: 'TX0000111', providerType: 'IN', taxonomy: '207Q00000X', riskTier: 'L', enrollStatus: 'A', screenFlag: 'P',
  effectiveDate: '2026-03-02', revalidationDueDate: '2031-03-02', version: 1, individual: { lastName: 'RIVERA', firstName: 'MARIA', licenseNo: 'MD12345', licenseState: 'TX' },
};
const page_of = (items: unknown[]) => json(200, { items, page: 0, size: 20, total: items.length });
const SCREENING = page_of([
  {
    screeningId: '5c1f0a9e-3b7d-4a52-9e64-0d8f2b6a7c10', npi: NPI, reason: 'INITIAL', status: 'COMPLETED', result: 'PASS', startedAt: '2026-03-02T09:13:00Z', completedAt: '2026-03-02T09:14:00Z',
    results: ['LICBOARD', 'OIGLEIE', 'SAMGOV', 'SSADMF'].map((source) => ({ source, result: 'P', verdictAt: '2026-03-02T09:14:00Z', detail: 'No finding', errorCode: null })),
  },
]);
const SANCTIONS = page_of([
  { sanctionId: '4c8a1f20-6d3b-4b7e-9a15-2e7f0b3c9d42', npi: NPI, action: 'R', reason: 'Licence confirmed', effectiveDate: '2026-03-20', recordedBy: 'supervisor1', recordedAt: '2026-03-20T10:00:00Z', resultingStatus: 'A' },
  { sanctionId: '4c8a1f20-6d3b-4b7e-9a15-2e7f0b3c9d41', npi: NPI, action: 'S', reason: 'Licence under review', effectiveDate: '2026-03-10', recordedBy: 'supervisor1', recordedAt: '2026-03-10T14:00:00Z', resultingStatus: 'S' },
]);
const REVALIDATIONS = page_of([{ caseId: '3b1f6c52-8d0e-4a77-b0a4-6f2d9c1e5a10', npi: NPI, dueDate: '2026-02-28', status: 'DUE', openedAt: '2026-03-01T00:00:00Z' }]);
const NOTICES = page_of([
  { noticeId: '00000000-0000-4000-8000-000000000001', npi: NPI, template: 'SUS', channel: 'EMAIL', status: 'SENT', sentAt: '2026-03-10T14:05:00Z' },
  { noticeId: '00000000-0000-4000-8000-000000000002', npi: NPI, template: 'RST', channel: 'EMAIL', status: 'FAILED', sentAt: '2026-03-20T10:00:00Z' },
]);
const AUDIT = page_of([{ at: '2026-03-02T09:30:00Z', actor: 'analyst1', action: 'APPROVE' }]);

function routes(over: Record<string, RouteHandler> = {}): Record<string, RouteHandler> {
  return {
    [`GET /api/v1/providers/${NPI}`]: () => json(200, RIVERA),
    'GET /api/v1/screenings': () => SCREENING,
    'GET /api/v1/sanctions': () => SANCTIONS,
    'GET /api/v1/revalidations': () => REVALIDATIONS,
    'GET /api/v1/notices': () => NOTICES,
    [`GET /api/v1/providers/${NPI}/audit`]: () => AUDIT,
    ...over,
  };
}

const panel = (page: Page, name: string) => page.locator('section').filter({ has: page.getByRole('heading', { level: 2, name, exact: true }) });

async function open(page: Page, who: PemUser, handlers: Record<string, RouteHandler>) {
  const api = await mockApi(page, handlers);
  await signIn(page, who, `/console/providers/${NPI}`);
  await expect(page.getByRole('heading', { level: 1, name: 'Provider detail' })).toBeVisible();
  return api;
}

const EXPECTED: Record<string, string[]> = {
  analyst1: ['Screening', 'Sanctions', 'Revalidation', 'Notices'],
  supervisor1: ['Screening', 'Sanctions', 'Revalidation', 'Notices', 'Audit'],
  admin1: ['Screening', 'Sanctions', 'Notices', 'Audit'],
};

for (const who of ['analyst1', 'supervisor1', 'admin1'] as const) {
  test(`[MAM-82 AC-6][MAM-82 AC-8] ${who} sees exactly its panels, populated, with zero axe violations`, async ({ page }) => {
    const api = await open(page, who, routes());

    for (const name of EXPECTED[who]!) await expect(panel(page, name)).toBeVisible();
    for (const name of ['Screening', 'Sanctions', 'Revalidation', 'Notices', 'Audit'].filter((n) => !EXPECTED[who]!.includes(n))) await expect(panel(page, name)).toHaveCount(0);
    await expect(panel(page, 'Screening').getByRole('row').filter({ hasText: 'Licence board' }).getByRole('cell').nth(1)).toHaveText('Pass (P)');
    await expect(panel(page, 'Sanctions').getByRole('row').filter({ hasText: 'Suspend (S)' }).getByRole('cell')).toHaveText(['10 Mar 2026', 'Suspend (S)', 'Licence under review', 'supervisor1']);
    await expect(panel(page, 'Notices').getByRole('row').filter({ hasText: 'RST' }).getByRole('cell').nth(2)).toHaveText('Failed');
    await expectNoAxeViolations(page);
    expect(api.to('GET', `/api/v1/providers/${NPI}/audit`).length > 0).toBe(EXPECTED[who]!.includes('Audit'));
    expect(api.to('GET', '/api/v1/revalidations').length > 0).toBe(EXPECTED[who]!.includes('Revalidation'));
  });
}

test('[MAM-82 AC-5] supervisor1 sees the audit row 2 Mar 2026 | analyst1 | APPROVE', async ({ page }) => {
  await open(page, 'supervisor1', routes());

  await expect(panel(page, 'Audit').getByRole('row').filter({ hasText: 'analyst1' }).getByRole('cell')).toHaveText(['2 Mar 2026', 'analyst1', 'Approve (APPROVE)']);
});

test('[MAM-82 AC-7][MAM-82 AC-8] a failed panel (sanctions 503): the others are shown, the panel says so with Retry, zero axe violations; Retry works with the keyboard only', async ({ page }) => {
  let up = false;
  await open(page, 'supervisor1', routes({ 'GET /api/v1/sanctions': () => (up ? SANCTIONS : json(503, { title: 'Service Unavailable', status: 503 })) }));

  const sanctions = panel(page, 'Sanctions');
  await expect(sanctions.getByText('The sanctions could not be loaded')).toBeVisible();
  await expect(sanctions.getByText('None recorded')).toHaveCount(0);
  await expect(panel(page, 'Screening').getByRole('row').filter({ hasText: 'SAM.gov debarment' })).toHaveCount(1);
  await expectNoAxeViolations(page);

  up = true;
  await tabTo(page, sanctions.getByRole('button', { name: 'Retry' }));
  await page.keyboard.press('Enter');
  await expect(sanctions.getByRole('row').filter({ hasText: 'Reinstate (R)' })).toHaveCount(1);
});

test('[MAM-82 AC-7] revalidation and audit endpoints that are not built yet (404) show their own message with Retry and no invented data', async ({ page }) => {
  await open(page, 'supervisor1', routes({
    'GET /api/v1/revalidations': () => json(404, { title: 'Not Found', status: 404 }),
    [`GET /api/v1/providers/${NPI}/audit`]: () => json(404, { title: 'Not Found', status: 404 }),
  }));

  await expect(panel(page, 'Revalidation').getByText('The revalidation cases could not be loaded')).toBeVisible();
  await expect(panel(page, 'Audit').getByText('The audit history could not be loaded')).toBeVisible();
  await expect(panel(page, 'Audit').getByRole('button', { name: 'Retry' })).toBeVisible();
  await expectNoAxeViolations(page);
});
