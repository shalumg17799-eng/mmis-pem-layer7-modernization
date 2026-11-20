// MAM-81 Journey J6 (modern/docs/ux/journeys.md#J6) on the running stack, no mocks: admin1 runs the monthly revalidation
// sweep for 03/31/2026 on the batch jobs page -> one case per ACTIVE provider due on or before that date -> re-screening
// completes each case and sets the next due date -> the REVALQ file (80 bytes per record) can be downloaded -> a second
// run for the same date adds no case.
// Needs: ./pem.sh up && ./pem.sh seed (TESTPROV BRAVO 1005951276 due 2026-01-15, modern/testdata/README.md).
// Passwords are read at run time from the environment or modern/infra/.env.local and never written anywhere.
// Events (Q-094 default A, accepted by the user): RevalidationDue and ScreeningCompleted (reason REVALIDATION) are proven
// here by their effects (case COMPLETED, new due date); their payloads by the revalidation and screening service tests.
// Re-screening completes a case within seconds, so the DUE queue may already be empty when analyst1 opens it: AC-2 checks
// one case per due provider through the API and that the queue never lists a provider twice.
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { expect, test, type APIRequestContext, type Locator, type Page } from '@playwright/test';
import { signIn, type PemUser } from '../support/keycloak';
import { expectNoAxeViolations } from '../support/page';

const GATEWAY = process.env.PEM_GATEWAY_URL ?? 'http://localhost:8080';
const KEYCLOAK_TOKEN = `${process.env.PEM_KEYCLOAK_URL ?? 'http://localhost:8180'}/realms/pem/protocol/openid-connect/token`;
const BUSINESS_DATE = '2026-03-31';
const BRAVO = '1005951276';

const PASSWORD_VARIABLE: Record<PemUser, string> = {
  provider1: 'KC_PROVIDER_PASSWORD',
  analyst1: 'KC_ANALYST_PASSWORD',
  supervisor1: 'KC_SUPERVISOR_PASSWORD',
  admin1: 'KC_ADMIN_PASSWORD',
};

function passwordOf(user: PemUser): string {
  const variable = PASSWORD_VARIABLE[user];
  if (process.env[variable]) return process.env[variable] as string;
  const file = fileURLToPath(new URL('../../../infra/.env.local', import.meta.url));
  const line = readFileSync(file, 'utf8')
    .split(/\r?\n/)
    .find((candidate) => candidate.startsWith(`${variable}=`));
  const value = line?.slice(variable.length + 1).trim();
  if (!value) throw new Error(`${variable} is not set (environment or modern/infra/.env.local)`);
  return value;
}

async function tokenOf(request: APIRequestContext, user: PemUser): Promise<string> {
  const response = await request.post(KEYCLOAK_TOKEN, {
    form: { grant_type: 'password', client_id: 'pem-test', username: user, password: passwordOf(user) },
  });
  expect(response.status(), `Keycloak token for ${user}`).toBe(200);
  return ((await response.json()) as { access_token: string }).access_token;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any -- untyped JSON answers of several services
type Json = any;

async function getJson(request: APIRequestContext, path: string, token?: string): Promise<{ status: number; body: Json }> {
  const response = await request.get(`${GATEWAY}${path}`, { headers: token ? { Authorization: `Bearer ${token}` } : {} });
  const text = await response.text();
  return { status: response.status(), body: text ? JSON.parse(text) : null };
}

const POLL = { timeout: 120_000, intervals: [2_000] };

/** Runs a job on /console/admin/jobs for 03/31/2026 and returns the run the service accepted. */
async function runJob(page: Page, option: string, startPath: string): Promise<{ runId: string; businessDate: string }> {
  await page.getByLabel(/^Job/).selectOption({ label: option });
  await page.getByLabel(/^Business date/).fill('03/31/2026');
  const [response] = await Promise.all([
    page.waitForResponse((r) => r.url().endsWith(startPath) && r.request().method() === 'POST'),
    page.getByRole('button', { name: 'Run job' }).click(),
  ]);
  expect(response.status(), await response.text()).toBe(202);
  const run = (await response.json()) as { runId: string; businessDate: string };
  expect(run.businessDate).toBe(BUSINESS_DATE);
  return run;
}

/**
 * Clicks the Download button in the row of ONE run and returns the bytes the browser saved. Corrected 2026-11-20 (Q-097 C, review of
 * the J7 correction): the click is bound to the run, not to the first Download button of a job. The row must show Completed, the
 * button must belong to that row, the browser must request exactly the file path of that run, and the saved name is the legacy DD name.
 */
async function downloadFile(page: Page, row: Locator, filePath: string, savedName: string): Promise<Buffer> {
  await expect(row.getByRole('cell').nth(2), 'the run shows Completed').toHaveText('Completed', { timeout: 120_000 });
  const [download] = await Promise.all([
    page.waitForEvent('download'),
    page.waitForRequest((request) => new URL(request.url()).pathname === filePath && request.method() === 'GET'),
    row.getByRole('button', { name: /^Download / }).click(),
  ]);
  expect(download.suggestedFilename()).toBe(savedName);
  return readFileSync(await download.path());
}

test.describe('MAM-81 journey J6: monthly revalidation', () => {
  test.setTimeout(300_000);

  test('MAM-81 AC-1..AC-6: sweep 03/31/2026 -> one case per due provider -> re-screened -> REVALQ -> re-run adds none', async ({
    browser,
    request,
  }) => {
    const adminToken = await tokenOf(request, 'admin1');
    const analystToken = await tokenOf(request, 'analyst1');

    // Seed state, and the providers that are due: ACTIVE with a due date on or before 2026-03-31.
    const bravo = await getJson(request, `/api/v1/providers/${BRAVO}`, analystToken);
    expect(bravo.body?.revalidationDueDate, 'run ./pem.sh seed first').toBe('2026-01-15');
    const all = (await getJson(request, '/api/v1/providers?size=200', analystToken)).body.items as Array<{
      npi: string;
      enrollStatus: string;
      revalidationDueDate?: string | null;
    }>;
    const due = all.filter((p) => p.enrollStatus === 'A' && !!p.revalidationDueDate && p.revalidationDueDate <= BUSINESS_DATE);
    const dueDate = new Map(due.map((p) => [p.npi, p.revalidationDueDate as string]));
    expect(dueDate.has(BRAVO)).toBe(true);
    const notDue = all.filter((p) => !dueDate.has(p.npi)).map((p) => p.npi);

    // AC-1: admin1 runs the sweep on the batch jobs page; the run completes with queued = number of due providers.
    const admin = await (await browser.newContext()).newPage();
    await signIn(admin, 'admin1', '/console/admin/jobs');
    await expect(admin.getByRole('heading', { level: 1, name: 'Batch jobs' })).toBeVisible();
    const run = await runJob(admin, 'Revalidation sweep', '/api/v1/batch/revalidation-sweeps');
    await expect
      .poll(async () => (await getJson(request, `/api/v1/batch/revalidation-sweeps/${run.runId}`, adminToken)).body?.status, {
        message: 'sweep run COMPLETED',
        ...POLL,
      })
      .toBe('COMPLETED');
    const finished = (await getJson(request, `/api/v1/batch/revalidation-sweeps/${run.runId}`, adminToken)).body;
    expect(finished.counts?.queued).toBe(due.length);
    expect(finished.error ?? null).toBeNull();
    const runs = admin.getByRole('table', { name: /Recent runs/ });
    const sweepRows = runs.getByRole('row').filter({ hasText: 'Revalidation sweep' });
    await expect(sweepRows).toHaveCount(1);
    await expect(sweepRows.getByRole('cell').nth(2)).toHaveText('Completed', { timeout: 120_000 });
    await expectNoAxeViolations(admin);

    // AC-5: the REVALQ file downloaded in the browser: 80-byte records, NPI 1-10, due date 11-18 CCYYMMDD,
    // RESCREEN-REQUIRED from byte 19, spaces to byte 80, one record per queued provider; same bytes as the API gives.
    const revalq = await downloadFile(admin, sweepRows, `/api/v1/batch/revalidation-sweeps/${run.runId}/queue-file`, 'REVALQ.D20260331');
    const viaApi = await request.get(`${GATEWAY}/api/v1/batch/revalidation-sweeps/${run.runId}/queue-file`, {
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    expect(viaApi.status()).toBe(200);
    expect(Buffer.compare(revalq, await viaApi.body())).toBe(0);
    expect(revalq.length % 80, 'REVALQ is a whole number of 80-byte records').toBe(0);
    const records = Array.from({ length: revalq.length / 80 }, (_, i) => revalq.subarray(i * 80, i * 80 + 80).toString('latin1'));
    expect(records).toHaveLength(due.length);
    for (const record of records) {
      const npi = record.slice(0, 10);
      expect(dueDate.has(npi), `${npi} is due`).toBe(true);
      expect(record.slice(10, 18)).toBe((dueDate.get(npi) as string).replaceAll('-', ''));
      expect(record.slice(18, 35)).toBe('RESCREEN-REQUIRED');
      expect(record.slice(35)).toBe(' '.repeat(45));
    }
    expect(new Set(records.map((r) => r.slice(0, 10)))).toEqual(new Set(dueDate.keys()));

    // AC-2: each due provider has exactly one case; no provider that is not due or not ACTIVE has one; the queue page
    // never lists a provider twice.
    for (const npi of dueDate.keys()) {
      expect((await getJson(request, `/api/v1/revalidations?npi=${npi}`, analystToken)).body.total, `one case for ${npi}`).toBe(1);
    }
    for (const npi of notDue) {
      expect((await getJson(request, `/api/v1/revalidations?npi=${npi}`, analystToken)).body.total, `no case for ${npi}`).toBe(0);
    }
    const analyst = await (await browser.newContext()).newPage();
    await signIn(analyst, 'analyst1', '/console/queues');
    const queue = analyst.locator('section').filter({ has: analyst.getByRole('heading', { name: /^Revalidation due/ }) });
    await expect(queue).toBeVisible();
    await expect(queue.getByText(/^Loading/)).toHaveCount(0, { timeout: 60_000 });
    for (const npi of dueDate.keys()) {
      expect(await queue.getByRole('row').filter({ hasText: npi }).count(), `${npi} listed at most once`).toBeLessThanOrEqual(1);
    }
    await expectNoAxeViolations(analyst);

    // AC-4: re-screening passes, each case becomes COMPLETED and the provider gets a due date later than 2026-03-31.
    for (const npi of dueDate.keys()) {
      await expect
        .poll(async () => (await getJson(request, `/api/v1/revalidations?npi=${npi}`, analystToken)).body.items?.[0]?.status, {
          message: `case of ${npi} COMPLETED`,
          ...POLL,
        })
        .toBe('COMPLETED');
      const provider = (await getJson(request, `/api/v1/providers/${npi}`, analystToken)).body;
      expect(provider.revalidationDueDate > BUSINESS_DATE, `${npi} next due ${provider.revalidationDueDate}`).toBe(true);
    }

    // AC-3: a second sweep for the same date completes and adds no case.
    const again = await runJob(admin, 'Revalidation sweep', '/api/v1/batch/revalidation-sweeps');
    expect(again.runId).not.toBe(run.runId);
    await expect
      .poll(async () => (await getJson(request, `/api/v1/batch/revalidation-sweeps/${again.runId}`, adminToken)).body?.status, {
        message: 'second sweep run COMPLETED',
        ...POLL,
      })
      .toBe('COMPLETED');
    for (const npi of dueDate.keys()) {
      expect((await getJson(request, `/api/v1/revalidations?npi=${npi}`, analystToken)).body.total, `still one case for ${npi}`).toBe(1);
    }
    for (const npi of notDue) {
      expect((await getJson(request, `/api/v1/revalidations?npi=${npi}`, analystToken)).body.total, `still no case for ${npi}`).toBe(0);
    }
    await expectNoAxeViolations(admin);
  });
});
