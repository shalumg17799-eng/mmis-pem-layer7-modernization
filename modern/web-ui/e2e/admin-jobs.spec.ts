// MAM-93 AC-8 in a real browser: admin1 signs in with the real Keycloak realm pem and runs batch jobs on /console/admin/jobs; the
// batch endpoints are answered in the browser (e2e/support/gateway-routes.ts; the legacy files by a raw route that keeps their bytes).
// Axe scans: the empty page, the page with an intake job selected, the page with runs and the page with a failed run. A job is run
// with the keyboard only, a file is downloaded byte for byte and a multipart intake request is checked.
import { expect, test, type Page } from '@playwright/test';
import { json, mockApi, type RouteHandler } from './support/gateway-routes';
import { signIn } from './support/keycloak';
import { expectNoAxeViolations, tabTo } from './support/page';

test.use({ trace: 'off' });

const LEGACY_BYTES = Buffer.from([0x31, 0x30, 0x30, 0x33, 0x30, 0x30, 0x30, 0x31, 0x31, 0x31, 0xe9, 0x00, 0x20, 0x20, 0x0d, 0x0a, 0xff]);
const RUN_ID = '7a3c1e90-5b2d-4f68-9c04-1d8e6f2a3b51';
const run = (over: Record<string, unknown> = {}) => ({ runId: RUN_ID, job: 'JOB', businessDate: '2026-03-31', status: 'COMPLETED', counts: { read: 12, written: 10 }, startedAt: '2026-03-31T10:00:00Z', endedAt: '2026-03-31T10:00:05Z', ...over });

function routes(over: Record<string, RouteHandler> = {}): Record<string, RouteHandler> {
  return {
    'GET /api/v1/batch/tmsis-extracts': () => json(200, { items: [], page: 0, size: 20, total: 0 }),
    'POST /api/v1/batch/screening-runs': () => json(202, run()),
    'POST /api/v1/batch/revalidation-sweeps': () => json(202, run({ runId: '9b4d2f01-6c3e-4a79-8d15-2e9f7a3c4b62', status: 'RUNNING', counts: undefined })),
    ...over,
  };
}

async function open(page: Page, handlers: Record<string, RouteHandler>) {
  const api = await mockApi(page, handlers);
  await signIn(page, 'admin1', '/console/admin/jobs');
  await expect(page.getByRole('heading', { level: 1, name: 'Batch jobs' })).toBeVisible();
  return api;
}

const job = (page: Page) => page.getByLabel('Job', { exact: true });
const date = (page: Page) => page.getByLabel('Business date (MM/DD/YYYY)', { exact: true });
const runs = (page: Page) => page.getByRole('table', { name: 'Recent runs' });

async function start(page: Page, which: string, businessDate: string) {
  await job(page).selectOption({ label: which });
  await date(page).fill(businessDate);
  await page.getByRole('button', { name: 'Run job' }).click();
}

test('[MAM-93 AC-1][MAM-93 AC-8] the empty page: every input has a label, the date is empty, and there are zero axe violations', async ({ page }) => {
  await open(page, routes());

  await expect(date(page)).toHaveValue('');
  const options = (await job(page).locator('option').allTextContents()).map((option) => option.trim());
  expect(options).toEqual(['Choose...', 'Enrolment intake (APPINPUT)', 'Screening run', 'Revalidation sweep', 'Sanction intake (SANCIN)', 'Directory extract (DIROUT)', 'T-MSIS extract']);
  await expect(runs(page).getByRole('columnheader')).toHaveText(['Job', 'Business date', 'Status', 'Counts', 'Error', 'File']);
  await expectNoAxeViolations(page);
});

test('[MAM-93 AC-1][MAM-93 AC-8] an intake job adds the file input and the Batch id; the page has zero axe violations', async ({ page }) => {
  await open(page, routes());

  await job(page).selectOption({ label: 'Sanction intake (SANCIN)' });

  await expect(page.getByLabel(/^File/)).toBeVisible();
  await expect(page.getByLabel(/^Batch id/)).toHaveAttribute('maxlength', '8');
  await expectNoAxeViolations(page);
  await page.getByRole('button', { name: 'Run job' }).click();
  await expect(page.getByRole('heading', { level: 2, name: '2 errors in the job' })).toBeVisible();
  await expectNoAxeViolations(page);
});

test('[MAM-93 AC-2][MAM-93 AC-3][MAM-93 AC-8] runs of the sweep and the screening are listed with status in words; zero axe violations', async ({ page }) => {
  const api = await open(page, routes());

  await start(page, 'Revalidation sweep', '03/31/2026');
  await expect(runs(page).getByRole('row').filter({ hasText: 'Revalidation sweep' }).getByRole('cell').nth(2)).toHaveText('Running');
  await start(page, 'Screening run', '03/31/2026');
  const screening = runs(page).getByRole('row').filter({ hasText: 'Screening run' });
  await expect(screening.getByRole('cell')).toHaveText(['Screening run', '31 Mar 2026', 'Completed', 'read 12, written 10', '', 'Download']);
  await expect(screening.getByRole('button', { name: 'Download Screening run 31 Mar 2026' })).toBeVisible();
  await expectNoAxeViolations(page);
  expect(api.to('POST', '/api/v1/batch/revalidation-sweeps')[0]?.body).toEqual({ businessDate: '2026-03-31' });
  expect(api.to('POST', '/api/v1/batch/screening-runs')[0]?.body).toEqual({ businessDate: '2026-03-31' });
});

test('[MAM-93 AC-5][MAM-93 AC-8] a failed run shows "Failed" and its error text; a 422 shows the detail in the summary; zero axe violations', async ({ page }) => {
  await open(page, routes({
    'POST /api/v1/batch/directory-extracts': () => json(202, run({ status: 'FAILED', counts: undefined, error: 'The provider master could not be read' })),
    'POST /api/v1/batch/tmsis-extracts': () => json(422, { title: 'Unprocessable Entity', status: 422, detail: 'No active provider for the period' }),
  }));

  await start(page, 'Directory extract (DIROUT)', '03/31/2026');
  const failed = runs(page).getByRole('row').filter({ hasText: 'Directory extract' });
  await expect(failed.getByRole('cell').nth(2)).toHaveText('Failed');
  await expect(failed.getByRole('cell').nth(4)).toHaveText('The provider master could not be read');
  await expectNoAxeViolations(page);

  await start(page, 'T-MSIS extract', '03/31/2026');
  await expect(page.getByRole('heading', { level: 2, name: 'The job could not be started' })).toBeVisible();
  await expect(page.getByText('No active provider for the period')).toBeVisible();
  await expectNoAxeViolations(page);
});

test('[MAM-93 AC-8] a job is run with the keyboard only: Tab to the list, a letter, Tab to the date, type, Tab to Run job, Enter', async ({ page }) => {
  const api = await open(page, routes());

  await tabTo(page, job(page));
  await page.keyboard.press('r'); // Revalidation sweep
  await expect(job(page)).toHaveValue(/sweep|REVALIDATION/i);
  await page.keyboard.press('Tab');
  await expect(date(page)).toBeFocused();
  await page.keyboard.type('03/31/2026');
  await page.keyboard.press('Tab');
  await expect(page.getByRole('button', { name: 'Run job' })).toBeFocused();
  await page.keyboard.press('Enter');

  await expect(runs(page).getByRole('row').filter({ hasText: 'Revalidation sweep' })).toHaveCount(1);
  expect(api.to('POST', '/api/v1/batch/revalidation-sweeps')[0]?.body).toEqual({ businessDate: '2026-03-31' });
});

test('[MAM-93 AC-6] Download saves the legacy file byte for byte, with the administrator token', async ({ page }) => {
  const api = await open(page, routes());
  let authorization: string | undefined;
  await page.route(`http://localhost:8080/api/v1/batch/screening-runs/${RUN_ID}/results-file`, async (route) => {
    const request = route.request();
    const cors = { 'access-control-allow-origin': request.headers()['origin'] ?? '*', 'access-control-allow-headers': 'authorization, content-type', 'access-control-allow-methods': 'GET, OPTIONS', vary: 'Origin' };
    if (request.method() === 'OPTIONS') return route.fulfill({ status: 204, headers: cors });
    authorization = request.headers()['authorization'];
    return route.fulfill({ status: 200, headers: { ...cors, 'content-type': 'text/plain' }, body: LEGACY_BYTES });
  });
  await start(page, 'Screening run', '03/31/2026');
  const download = page.getByRole('button', { name: 'Download Screening run 31 Mar 2026' });
  await expect(download).toBeVisible();

  const [saved] = await Promise.all([page.waitForEvent('download'), download.click()]);

  const stream = await saved.createReadStream();
  const chunks: Buffer[] = [];
  for await (const chunk of stream) chunks.push(chunk as Buffer);
  expect(Buffer.concat(chunks).equals(LEGACY_BYTES)).toBe(true);
  expect(saved.suggestedFilename().startsWith('SCRNOUT')).toBe(true);
  expect(authorization).toMatch(/^Bearer \S+/);
  expect(api.calls.filter((call) => call.path.startsWith('/api/v1/batch')).every((call) => call.authorization?.startsWith('Bearer '))).toBe(true);
});

test('[MAM-93 AC-4] an intake sends a multipart request with the file, the business date and the batch id, then follows the run until Completed', async ({ page }) => {
  let polls = 0;
  const api = await open(page, routes({
    'POST /api/v1/batch/intake-runs': () => json(202, run({ status: 'RUNNING', counts: undefined })),
    [`GET /api/v1/batch/intake-runs/${RUN_ID}`]: () => {
      polls += 1;
      return json(200, run({ status: polls < 2 ? 'RUNNING' : 'COMPLETED', counts: { read: 12, written: 10, errors: 2 } }));
    },
  }));

  await job(page).selectOption({ label: 'Enrolment intake (APPINPUT)' });
  await date(page).fill('03/31/2026');
  await page.getByLabel(/^File/).setInputFiles({ name: 'APPINPUT.txt', mimeType: 'text/plain', buffer: Buffer.from('1003000111IN...') });
  await page.getByLabel(/^Batch id/).fill('B0331');
  await page.getByRole('button', { name: 'Run job' }).click();

  const row = runs(page).getByRole('row').filter({ hasText: 'Enrolment intake' });
  await expect(row.getByRole('cell').nth(2)).toHaveText('Completed', { timeout: 15_000 });
  await expect(row.getByRole('cell').nth(3)).toHaveText('read 12, written 10, errors 2');
  const sent = api.to('POST', '/api/v1/batch/intake-runs')[0];
  expect(sent?.contentType).toMatch(/^multipart\/form-data; boundary=/);
  const body = String(sent?.body);
  expect(body).toContain('name="businessDate"');
  expect(body).toContain('2026-03-31');
  expect(body).toContain('name="batchId"');
  expect(body).toContain('B0331');
  expect(body).toContain('filename="APPINPUT.txt"');
  expect(body).toContain('1003000111IN...');
  await expectNoAxeViolations(page);
});
