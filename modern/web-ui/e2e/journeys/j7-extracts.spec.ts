// MAM-83 Journey J7 (modern/docs/ux/journeys.md#J7) on the running stack, no mocks: admin1 runs the T-MSIS extract and
// the directory extract for 03/31/2026 on the batch jobs page and downloads the legacy files (TMSISOUT 120 bytes, DIROUT
// 100 bytes) -> both hold exactly the ACTIVE providers, the directory names are composed -> a visitor finds the active
// hospice, and not the terminated one, in the public directory.
// Needs: ./pem.sh up && ./pem.sh seed (modern/testdata/README.md: TESTPROV ALPHA 1005940865, SAMPLE CLINIC 01 1005912765,
// SAMPLE HOSPICE 01 1005947811 ACTIVE, SAMPLE HOSPICE 02 1005932946 TERMINATED).
// Passwords are read at run time from the environment or modern/infra/.env.local and never written anywhere.
// Events (Q-094 default A, accepted by the user): ExtractProduced is proven here by its effect (the run is listed with its
// row count); its payload (businessDate, periodYyddd, rowCount) by the tmsis-service tests (MAM-75).
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { expect, test, type APIRequestContext, type Locator, type Page } from '@playwright/test';
import { signIn, type PemUser } from '../support/keycloak';
import { expectNoAxeViolations } from '../support/page';

const GATEWAY = process.env.PEM_GATEWAY_URL ?? 'http://localhost:8080';
const KEYCLOAK_TOKEN = `${process.env.PEM_KEYCLOAK_URL ?? 'http://localhost:8180'}/realms/pem/protocol/openid-connect/token`;
const BUSINESS_DATE = '2026-03-31';
const ALPHA = '1005940865';
const CLINIC = '1005912765';
const HOSPICE_ACTIVE = '1005947811';
const HOSPICE_TERMINATED = '1005932946';

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
 * the 19:25 correction): the click is bound to the run, not to a position in the table. The row must show Completed, the button
 * must belong to that row, the browser must request exactly the file path of that run, and the saved name is the legacy DD name.
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

const recordsOf = (file: Buffer, length: number): string[] =>
  Array.from({ length: file.length / length }, (_, i) => file.subarray(i * length, i * length + length).toString('latin1'));

test.describe('MAM-83 journey J7: month-end extracts and the public directory', () => {
  test.setTimeout(300_000);

  test('MAM-83 AC-1..AC-7: TMSISOUT and DIROUT for 03/31/2026 hold the ACTIVE providers; HOSPICE in the directory', async ({
    browser,
    request,
  }) => {
    const adminToken = await tokenOf(request, 'admin1');
    const analystToken = await tokenOf(request, 'analyst1');

    // Seed state: the ACTIVE providers (AC-5) and the T-MSIS runs already listed (AC-2).
    const active = (await getJson(request, '/api/v1/providers?status=A&size=200', analystToken)).body.items as Array<{
      npi: string;
      medicaidId: string;
    }>;
    const activeNpis = new Set(active.map((p) => p.npi));
    expect(activeNpis.has(ALPHA) && activeNpis.has(HOSPICE_ACTIVE), 'run ./pem.sh seed first').toBe(true);
    expect(activeNpis.has(HOSPICE_TERMINATED)).toBe(false);
    const medicaidIdOf = new Map(active.map((p) => [p.npi, p.medicaidId]));
    const listedBefore = (await getJson(request, '/api/v1/batch/tmsis-extracts?size=200', adminToken)).body.total as number;

    // AC-1: admin1 runs the T-MSIS extract and downloads TMSISOUT: 120-byte records PRV01 | Medicaid ID | NPI | A | 26090 |
    // spaces; the browser file equals the API file.
    const admin = await (await browser.newContext()).newPage();
    await signIn(admin, 'admin1', '/console/admin/jobs');
    await expect(admin.getByRole('heading', { level: 1, name: 'Batch jobs' })).toBeVisible();
    // The page must follow the run it started by itself: no reload and no new address from here on (counted below).
    let navigations = 0;
    admin.on('framenavigated', (frame) => {
      if (frame === admin.mainFrame()) navigations += 1;
    });
    const runs = admin.getByRole('table', { name: /Recent runs/ });
    const tmsisRun = await runJob(admin, 'T-MSIS extract', '/api/v1/batch/tmsis-extracts');
    const listed = async () =>
      ((await getJson(request, '/api/v1/batch/tmsis-extracts?size=200', adminToken)).body.items as Json[]).find(
        (r) => r.runId === tmsisRun.runId,
      );
    await expect.poll(async () => (await listed())?.status, { message: 'T-MSIS run COMPLETED', ...POLL }).toBe('COMPLETED');
    // Corrected 2026-11-20 (Q-097 C, review of the 19:25 correction; the user's order of 19:32): no reload. The T-MSIS job has no
    // run-status call, so the page has to move its own row off "Running" from the run list. The row of THIS run is the T-MSIS
    // row that shows its archive generation (unique per run); it must be the newest T-MSIS row, the only one for the run, and the
    // page must not have been reloaded (a reload would also clear the business date typed in the form).
    const entry = await listed();
    expect(entry.counts?.archiveGeneration, 'the completed run lists its archive generation').toEqual(expect.any(Number));
    const generation = new RegExp(`archiveGeneration ${entry.counts.archiveGeneration}(?!\\d)`);
    const tmsisRows = runs.getByRole('row').filter({ hasText: 'T-MSIS extract' });
    const tmsisRow = tmsisRows.filter({ hasText: generation });
    await expect(tmsisRow, 'the page shows the run it started with its archive generation, without a reload').toHaveCount(1, { timeout: 120_000 });
    await expect(tmsisRow.getByRole('cell').nth(2)).toHaveText('Completed');
    await expect(tmsisRow.getByRole('cell').nth(3)).toContainText(`rows ${entry.counts.rows}`);
    await expect(tmsisRows.first(), 'the new run is the newest T-MSIS row').toContainText(generation);
    await expect(tmsisRows, 'no second row for the same run').toHaveCount(Math.min(listedBefore + 1, 20));
    expect(navigations, 'the page was not reloaded').toBe(0);
    await expect(admin.getByLabel(/^Business date/)).toHaveValue('03/31/2026');
    const tmsisOut = await downloadFile(admin, tmsisRow, `/api/v1/batch/tmsis-extracts/${tmsisRun.runId}/file`, 'TMSISOUT.D20260331');
    const tmsisApi = await request.get(`${GATEWAY}/api/v1/batch/tmsis-extracts/${tmsisRun.runId}/file`, {
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    expect(tmsisApi.status()).toBe(200);
    expect(Buffer.compare(tmsisOut, await tmsisApi.body())).toBe(0);
    expect(tmsisOut.length % 120, 'TMSISOUT is a whole number of 120-byte records').toBe(0);
    const tmsisRecords = recordsOf(tmsisOut, 120);
    for (const record of tmsisRecords) {
      const npi = record.slice(14, 24);
      expect(record.slice(0, 5)).toBe('PRV01');
      expect(record.slice(5, 14), `Medicaid ID of ${npi}`).toBe(medicaidIdOf.get(npi));
      expect(record.slice(24, 25)).toBe('A');
      expect(record.slice(25, 30)).toBe('26090');
      expect(record.slice(30)).toBe(' '.repeat(90));
    }
    await expectNoAxeViolations(admin);

    // AC-2: the run is listed as a new entry (COMPLETED, 2026-03-31) next to the earlier runs, with its row count and an
    // archive generation.
    expect(entry.businessDate).toBe(BUSINESS_DATE);
    expect(entry.counts?.rows).toBe(tmsisRecords.length);
    expect(typeof entry.counts?.archiveGeneration).toBe('number');
    expect((await getJson(request, '/api/v1/batch/tmsis-extracts?size=200', adminToken)).body.total).toBe(listedBefore + 1);

    // AC-3: admin1 runs the directory extract and downloads DIROUT: 100-byte records Medicaid ID | NPI | type | tier |
    // name (60) | spaces.
    const dirRun = await runJob(admin, 'Directory extract (DIROUT)', '/api/v1/batch/directory-extracts');
    await expect
      .poll(async () => (await getJson(request, `/api/v1/batch/directory-extracts/${dirRun.runId}`, adminToken)).body?.status, {
        message: 'directory run COMPLETED',
        ...POLL,
      })
      .toBe('COMPLETED');
    const dirRows = runs.getByRole('row').filter({ hasText: 'Directory extract' });
    await expect(dirRows).toHaveCount(1);
    const dirOut = await downloadFile(admin, dirRows, `/api/v1/batch/directory-extracts/${dirRun.runId}/file`, 'DIROUT.D20260331');
    const dirApi = await request.get(`${GATEWAY}/api/v1/batch/directory-extracts/${dirRun.runId}/file`, {
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    expect(dirApi.status()).toBe(200);
    expect(Buffer.compare(dirOut, await dirApi.body())).toBe(0);
    expect(dirOut.length % 100, 'DIROUT is a whole number of 100-byte records').toBe(0);
    const dirRecords = recordsOf(dirOut, 100);
    const finishedDir = (await getJson(request, `/api/v1/batch/directory-extracts/${dirRun.runId}`, adminToken)).body;
    expect(finishedDir.counts?.rows).toBe(dirRecords.length);
    for (const record of dirRecords) {
      const npi = record.slice(9, 19);
      expect(record.slice(0, 9), `Medicaid ID of ${npi}`).toBe(medicaidIdOf.get(npi));
      expect(['IN', 'GR', 'FA']).toContain(record.slice(19, 21));
      expect(['L', 'M', 'H']).toContain(record.slice(21, 22));
      expect(record.slice(22, 82).trim().length, `name of ${npi}`).toBeGreaterThan(0);
      expect(record.slice(82)).toBe(' '.repeat(18));
    }

    // AC-4: names are composed: TESTPROV then ALPHA and no licence number; the clinic shows its legal name.
    const nameOf = (npi: string) => dirRecords.find((r) => r.slice(9, 19) === npi)?.slice(22, 82) ?? '';
    expect(nameOf(ALPHA)).toMatch(/TESTPROV\s+ALPHA/);
    expect(nameOf(ALPHA)).not.toContain('SD10001');
    expect(nameOf(CLINIC).trim()).toBe('SAMPLE CLINIC 01');

    // AC-5: both files hold exactly the ACTIVE providers.
    expect(new Set(tmsisRecords.map((r) => r.slice(14, 24)))).toEqual(activeNpis);
    expect(new Set(dirRecords.map((r) => r.slice(9, 19)))).toEqual(activeNpis);
    expect(tmsisRecords).toHaveLength(activeNpis.size);
    expect(dirRecords).toHaveLength(activeNpis.size);
    await expectNoAxeViolations(admin);

    // AC-6: a visitor with no sign-in searches HOSPICE: only the ACTIVE hospice, with its composed name.
    const visitor = await (await browser.newContext()).newPage();
    await visitor.goto('/directory');
    await expect(visitor.getByRole('heading', { level: 1, name: 'Find a Medicaid provider' })).toBeVisible();
    await visitor.getByLabel('Name', { exact: true }).fill('HOSPICE');
    await visitor.getByRole('button', { name: 'Search' }).click();
    await expect(visitor.getByRole('row').filter({ hasText: HOSPICE_ACTIVE })).toHaveCount(1);
    await expect(visitor.getByRole('row').filter({ hasText: HOSPICE_ACTIVE })).toContainText('SAMPLE HOSPICE 01');
    await expect(visitor.getByRole('row').filter({ hasText: HOSPICE_TERMINATED })).toHaveCount(0);
    await expectNoAxeViolations(visitor);
    const search = await getJson(request, '/api/v1/directory/providers?name=HOSPICE');
    expect(search.status).toBe(200);
    const items = search.body.items as Array<{ npi: string; displayName: string }>;
    expect(items.length).toBeGreaterThan(0);
    for (const item of items) {
      expect(item.displayName.toUpperCase()).toContain('HOSPICE');
      expect(activeNpis.has(item.npi), `${item.npi} is ACTIVE`).toBe(true);
    }
    expect(items.map((i) => i.npi)).not.toContain(HOSPICE_TERMINATED);
  });
});
