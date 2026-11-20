// MAM-70 AC-7 in a real browser: provider1 signs in with the real Keycloak realm pem and opens /portal/notices; the notices API
// is answered in the browser (e2e/support/gateway-routes.ts). Axe scans: with notices, with none and with the load failure.
// The paging controls are used with the keyboard only.
import { expect, test, type Page } from '@playwright/test';
import { json, mockApi, type RouteHandler } from './support/gateway-routes';
import { signIn } from './support/keycloak';
import { expectNoAxeViolations, expectShellStructure, tabTo } from './support/page';

test.use({ trace: 'off' });

const notice = (index: number, over: Record<string, unknown> = {}) => ({
  noticeId: `00000000-0000-4000-8000-${String(index).padStart(12, '0')}`,
  npi: '1003000111',
  template: 'APPROVED',
  channel: 'EMAIL',
  status: 'SENT',
  sentAt: '2026-03-05T10:00:00Z',
  ...over,
});
const page_of = (items: unknown[], total = items.length, page = 0) => json(200, { items, page, size: 20, total });

async function open(page: Page, handler: RouteHandler) {
  const api = await mockApi(page, { 'GET /api/v1/notices': handler });
  await signIn(page, 'provider1', '/portal/notices');
  await expect(page.getByRole('heading', { level: 1, name: 'My notices' })).toBeVisible();
  return api;
}

test('[MAM-70 AC-1][MAM-70 AC-7] provider1 sees the approval notice and the page has zero axe violations', async ({ page }) => {
  const api = await open(page, () => page_of([notice(1), notice(2, { template: 'DENIED', status: 'FAILED', sentAt: '2026-03-06T09:00:00Z' })]));

  const row = page.getByRole('row').filter({ hasText: 'Enrolment approved (APPROVED)' });
  await expect(row).toHaveCount(1);
  await expect(row.getByRole('cell')).toHaveText(['5 Mar 2026', 'Enrolment approved (APPROVED)', 'Email', 'Sent']);
  await expect(page.getByRole('row').filter({ hasText: 'Enrolment denied (DENIED)' }).getByRole('cell').nth(3)).toHaveText('Failed');
  await expectShellStructure(page);
  await expectNoAxeViolations(page);
  expect(api.to('GET', '/api/v1/notices')[0]?.query.has('npi')).toBe(false);
});

test('[MAM-70 AC-6][MAM-70 AC-7] no notices: "No notices yet" and zero axe violations', async ({ page }) => {
  await open(page, () => page_of([]));

  await expect(page.getByText('No notices yet')).toBeVisible();
  await expectNoAxeViolations(page);
});

test('[MAM-70 AC-6][MAM-70 AC-7] a load failure: "The notices could not be loaded. Try again.", never "No notices yet", zero axe violations', async ({ page }) => {
  await open(page, () => json(503, { title: 'Service Unavailable', status: 503 }));

  await expect(page.getByText('The notices could not be loaded. Try again.')).toBeVisible();
  await expect(page.getByText('No notices yet')).toHaveCount(0);
  await expectNoAxeViolations(page);
});

test('[MAM-70 AC-6][MAM-70 AC-7] more than 20 notices: the paging controls work with the keyboard only and the page has zero axe violations', async ({ page }) => {
  const api = await open(page, (call) =>
    call.query.get('page') === '1'
      ? page_of([notice(21, { template: 'TRM' })], 21, 1)
      : page_of(Array.from({ length: 20 }, (_, index) => notice(index + 1)), 21),
  );
  await expect(page.getByText(/Page 1 of 2/)).toBeVisible();
  await expectNoAxeViolations(page);

  const next = page.getByRole('button', { name: /Next/ });
  await tabTo(page, next);
  await page.keyboard.press('Enter');

  await expect(page.getByRole('row').filter({ hasText: 'Termination notice (TRM)' })).toHaveCount(1);
  await expect(page.getByText(/Page 2 of 2/)).toBeVisible();
  expect(api.to('GET', '/api/v1/notices').map((call) => call.query.get('page'))).toEqual([null, '1']);
  await expectNoAxeViolations(page);
});
