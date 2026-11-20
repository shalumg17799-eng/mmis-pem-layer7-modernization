// Browser check of journey J6 (monthly revalidation) and the T-MSIS part of J7 on the live, freshly seeded stack, with
// screenshots into modern/docs/reports/ui-screenshots/. Demo tooling, not a story test (the J6/J7 tests come in Part D).
// Passwords are read at run time from modern/infra/.env.local and never written anywhere.
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { expect, test, type Page } from '@playwright/test';
import { signIn, type PemUser } from '../e2e/support/keycloak';

const OUT = fileURLToPath(new URL('../../docs/reports/ui-screenshots/', import.meta.url));
const GATEWAY = 'http://localhost:8080';
const BRAVO = '1005951276'; // seeded ACTIVE, due 2026-01-15 (modern/testdata/README.md)

function passwordOf(user: PemUser): string {
  const variable = { provider1: 'KC_PROVIDER_PASSWORD', analyst1: 'KC_ANALYST_PASSWORD', supervisor1: 'KC_SUPERVISOR_PASSWORD', admin1: 'KC_ADMIN_PASSWORD' }[user];
  const file = fileURLToPath(new URL('../../infra/.env.local', import.meta.url));
  const line = readFileSync(file, 'utf8').split(/\r?\n/).find((l) => l.startsWith(`${variable}=`));
  return line?.slice(variable.length + 1).trim() ?? '';
}

// Wait until no part of the page still says "Loading ..." so the picture shows real content.
const shot = async (page: Page, name: string) => {
  await expect(page.getByRole('main').getByText(/Loading/)).toHaveCount(0, { timeout: 60_000 });
  await page.screenshot({ path: `${OUT}${name}.png`, fullPage: true });
};

async function runJob(page: Page, job: string) {
  await page.getByLabel(/^Job/).selectOption({ label: job });
  await page.getByLabel(/^Business date/).fill('03/31/2026');
  await page.getByRole('button', { name: 'Run job' }).click();
  const runs = page.getByRole('table', { name: /Recent runs/ });
  await expect(runs.getByRole('row').filter({ hasText: job }).filter({ hasText: /Completed/ }).first()).toBeVisible({ timeout: 180_000 });
}

test('J6 and the T-MSIS part of J7 in the browser', async ({ browser, request }) => {
  const token = (
    (await (
      await request.post('http://localhost:8180/realms/pem/protocol/openid-connect/token', {
        form: { grant_type: 'password', client_id: 'pem-test', username: 'analyst1', password: passwordOf('analyst1') },
      })
    ).json()) as { access_token: string }
  ).access_token;
  const dueOf = async () =>
    ((await (await request.get(`${GATEWAY}/api/v1/providers/${BRAVO}`, { headers: { Authorization: `Bearer ${token}` } })).json()) as {
      revalidationDueDate: string;
    }).revalidationDueDate;
  expect(await dueOf(), 'run ./pem.sh seed first').toBe('2026-01-15');

  // J6: admin1 runs the revalidation sweep for 03/31/2026 from the batch jobs page.
  const admin = await (await browser.newContext()).newPage();
  await signIn(admin, 'admin1', '/console/admin/jobs');
  await runJob(admin, 'Revalidation sweep');
  await shot(admin, '12-admin-jobs-revalidation-sweep');

  // The due providers are re-screened and their cases complete; BRAVO gets a new due date five years on.
  await expect.poll(dueOf, { timeout: 120_000, intervals: [2_000] }).not.toBe('2026-01-15');
  const analyst = await (await browser.newContext()).newPage();
  await signIn(analyst, 'analyst1', `/console/providers/${BRAVO}`);
  const revalidation = analyst.locator('section').filter({ has: analyst.getByRole('heading', { name: /^Revalidation/ }) });
  await expect(revalidation.getByRole('row').nth(1)).toBeVisible({ timeout: 60_000 });
  await shot(analyst, '13-provider-detail-revalidation');

  // J7 (T-MSIS part): admin1 runs the T-MSIS extract for 03/31/2026; the run completes and its file can be downloaded.
  await runJob(admin, 'T-MSIS extract');
  await expect(admin.getByRole('table', { name: /Recent runs/ }).getByRole('row').filter({ hasText: 'T-MSIS extract' }).first()).toContainText(/Download|Completed/);
  await shot(admin, '14-admin-jobs-tmsis-extract');
});
