// Screenshots of every other screen (the J1 screens are in screens.demo.ts) on the live, freshly seeded stack, into
// modern/docs/reports/ui-screenshots/. Demo tooling for the UI overhaul review and DEMO.md, not a story test.
// Uses only seeded data (modern/testdata/README.md): TESTPROV ALPHA 1005940865 (ACTIVE), TESTPROV DELTA 1005925213 (PENDING).
import { fileURLToPath } from 'node:url';
import { expect, test, type Page } from '@playwright/test';
import { signIn } from '../e2e/support/keycloak';

const OUT = fileURLToPath(new URL('../../docs/reports/ui-screenshots/', import.meta.url));
const ALPHA = '1005940865';
const main = (page: Page) => page.getByRole('main');
// Wait until no part of the page still says "Loading ..." so the picture shows real content.
const shot = async (page: Page, name: string) => {
  await expect(main(page).getByText(/Loading/)).toHaveCount(0, { timeout: 60_000 });
  await page.screenshot({ path: `${OUT}${name}.png`, fullPage: true });
};

test('screenshots of every other screen', async ({ browser }) => {
  // Public: landing and the directory without a search.
  const visitor = await (await browser.newContext()).newPage();
  await visitor.goto('/');
  await expect(main(visitor)).toBeVisible();
  await shot(visitor, '20-landing');
  await visitor.goto('/directory');
  await expect(visitor.getByRole('heading', { level: 1, name: 'Find a Medicaid provider' })).toBeVisible();
  await shot(visitor, '21-public-directory-empty');

  // Analyst: provider search with a result, provider detail with its panels, access denied for an admin page.
  const analyst = await (await browser.newContext()).newPage();
  await signIn(analyst, 'analyst1', '/console/providers');
  await analyst.getByLabel('NPI', { exact: true }).fill(ALPHA);
  await analyst.getByRole('button', { name: 'Search' }).click();
  await expect(analyst.getByRole('row').filter({ hasText: ALPHA })).toHaveCount(1);
  await shot(analyst, '22-console-provider-search');
  await analyst.goto(`/console/providers/${ALPHA}`);
  await expect(analyst.getByRole('heading', { level: 1, name: 'Provider detail' })).toBeVisible();
  await expect(analyst.locator('section').filter({ has: analyst.getByRole('heading', { name: /^Screening/ }) })).toBeVisible();
  await shot(analyst, '23-console-provider-detail-active');
  await analyst.goto('/console/admin/jobs');
  await expect(analyst.getByRole('heading', { level: 1, name: 'Access denied' })).toBeVisible();
  await shot(analyst, '24-access-denied');

  // Supervisor: the record-sanction form and the work queues with the tiles.
  const supervisor = await (await browser.newContext()).newPage();
  await signIn(supervisor, 'supervisor1', `/console/providers/${ALPHA}/sanctions/new`);
  await expect(supervisor.getByLabel(/^Action/)).toBeVisible();
  await shot(supervisor, '25-record-sanction');
  await supervisor.goto('/console/queues');
  await expect(supervisor.locator('section').filter({ has: supervisor.getByRole('heading', { name: /^Pending approval/ }) })).toBeVisible();
  await shot(supervisor, '26-work-queues-supervisor');

  // Admin: batch jobs and risk-tier rules.
  const admin = await (await browser.newContext()).newPage();
  await signIn(admin, 'admin1', '/console/admin/jobs');
  await expect(admin.getByLabel(/^Job/)).toBeVisible();
  await shot(admin, '27-admin-batch-jobs');
  await admin.goto('/console/admin/risk-rules');
  await expect(main(admin).getByRole('heading', { level: 1 })).toBeVisible();
  await shot(admin, '28-admin-risk-rules');
  await admin.goto('/console/providers');
  await expect(main(admin).getByRole('heading', { level: 1, name: 'Provider search' })).toBeVisible();
  await shot(admin, '29-admin-start-tools-panel');

  // Provider at the seed state: home without an application, notices and provider record empty states.
  const provider = await (await browser.newContext()).newPage();
  await signIn(provider, 'provider1', '/portal');
  await expect(provider.getByRole('heading', { level: 1, name: 'My enrolment' })).toBeVisible();
  await shot(provider, '30-portal-home-no-application');
  await provider.goto('/portal/notices');
  await expect(main(provider).getByRole('heading', { level: 1 })).toBeVisible();
  await shot(provider, '31-my-notices-empty');
  await provider.goto('/portal/provider');
  await expect(main(provider).getByRole('heading', { level: 1 })).toBeVisible();
  await shot(provider, '32-my-provider-record-none');
});
