// MAM-86 AC-3: the main steps of journeys J1 to J7 (modern/docs/ux/journeys.md) completed with the KEYBOARD ONLY, on the running stack,
// no mocks. Every control is reached with Tab / Shift+Tab (support/keyboard.ts: bounded, the error names the control that could
// not be reached) and operated with Enter, the space bar, the arrow keys or typed text. There is no mouse call, no fill, no check,
// no selectOption, no dispatchEvent and no programmatic focus here or in support/keyboard.ts (policy.a11y.spec.ts checks it).
// Typing an address (page.goto of the first page of a browser tab) and the Keycloak form, completed with the keyboard too, are the
// only things done before the page has the focus. A native file dialog cannot be driven, so a file upload would be the one
// accepted exception; none of the seven journeys uploads a file, so the exception is not used.
// Each journey carries every outcome check of its mouse version in e2e/journeys (the status reached, the screening, the notice, the
// file downloaded, the rows and records), read from the page or from the API. Compared assertion by assertion on 2026-11-20 evening,
// after a review found missing ones in J1, J3, J5 and J7; the differences that remain are on purpose: a link or the menu is used where
// the mouse journey types an address (page.goto), and the pages are scanned with the checker of this story (the four WCAG tags of
// AC-1) where the mouse journeys use the older helper that runs every axe rule.
// Each journey starts from the seed state: seedStack() runs infra/scripts/seed.sh before every journey.
// Gate: registered only with PEM_JOURNEYS=1 (it changes the data of the stack). Run one at a time on the shared stack:
//   PEM_JOURNEYS=1 npx playwright test e2e/a11y/keyboard-journeys.spec.ts
import { readFileSync } from 'node:fs';
import { expect, test, type Browser, type Locator, type Page } from '@playwright/test';
import { GATEWAY, POLL, fiveYearsAfter, getJson, mailSubjects, seedStack, shownDate, tokenOf, type Json } from './support/api';
import { expectNoViolations } from './support/axe-gate';
import { chooseOption, chooseRadio, openMenuEntry, pressAndDownload, pressAndWait, signInWithKeyboard, tabAndEnter, tabTo, typeInto } from './support/keyboard';

const live = Boolean(process.env.PEM_JOURNEYS);

const NPI = '1003000111';
const MEDICAID_ID = 'TX0000111';
const NOTICE_ADDRESS = (npi: string) => `${npi}@providers.pem.local`; // Q-092 default A
const SOURCES = ['LICBOARD', 'OIGLEIE', 'SAMGOV', 'SSADMF'];

async function newPage(browser: Browser): Promise<Page> {
  return (await browser.newContext()).newPage();
}

const section = (page: Page, heading: RegExp) => page.locator('section').filter({ has: page.getByRole('heading', { name: heading }) });
const h1 = (page: Page, name: string) => page.getByRole('heading', { level: 1, name });

/** Fill the apply form with the keyboard, in the order of the page: NPI, Medicaid ID, Provider type, Taxonomy, then the individual's details. */
async function fillApplyForm(page: Page, values: { taxonomy: string }): Promise<void> {
  await typeInto(page, page.getByLabel(/^NPI/), 'NPI', NPI);
  await typeInto(page, page.getByLabel(/^Medicaid ID/), 'Medicaid ID', MEDICAID_ID);
  await chooseOption(page, page.getByLabel(/^Provider type/), 'Provider type', 'Individual (IN)');
  await typeInto(page, page.getByLabel(/^Taxonomy code/), 'Taxonomy code', values.taxonomy);
  await typeInto(page, page.getByLabel(/^Last name/), 'Last name', 'RIVERA');
  await typeInto(page, page.getByLabel(/^First name/), 'First name', 'MARIA');
  await typeInto(page, page.getByLabel(/^Licence number/), 'Licence number', 'MD12345');
  await typeInto(page, page.getByLabel(/^Licence state/), 'Licence state', 'TX');
}

/** Tab to "Submit application" and press Enter; returns the answer of POST /api/v1/applications. */
async function submitApplication(page: Page) {
  await tabTo(page, page.getByRole('button', { name: 'Submit application' }), 'Submit application');
  return pressAndWait(page, (response) => response.url().endsWith('/api/v1/applications') && response.request().method() === 'POST');
}

/** Provider search with the keyboard: every field of the form is set (an empty value clears it), Enter in the last one searches. */
async function searchProviders(page: Page, criteria: { npi?: string; medicaidId?: string; name?: string }): Promise<URL> {
  await typeInto(page, page.getByLabel('NPI', { exact: true }), 'NPI', criteria.npi ?? '');
  await typeInto(page, page.getByLabel('Medicaid ID', { exact: true }), 'Medicaid ID', criteria.medicaidId ?? '');
  await typeInto(page, page.getByLabel(/^Name contains/), 'Name contains', criteria.name ?? '');
  const sent = await Promise.all([page.waitForRequest((request) => new URL(request.url()).pathname === '/api/v1/providers' && request.method() === 'GET'), page.keyboard.press('Enter')]);
  return new URL(sent[0].url());
}

/** Run a job on /console/admin/jobs for 03/31/2026 with the keyboard; returns the run the service accepted. */
async function runJob(page: Page, option: string, startPath: string): Promise<{ runId: string; businessDate: string }> {
  await chooseOption(page, page.getByLabel(/^Job/), 'Job', option);
  await typeInto(page, page.getByLabel(/^Business date/), 'Business date', '03/31/2026');
  await tabTo(page, page.getByRole('button', { name: 'Run job' }), 'Run job');
  const response = await pressAndWait(page, (candidate) => candidate.url().endsWith(startPath) && candidate.request().method() === 'POST');
  expect(response.status(), await response.text()).toBe(202);
  const run = (await response.json()) as { runId: string; businessDate: string };
  expect(run.businessDate).toBe('2026-03-31');
  return run;
}

/**
 * Tab to the Download button in the row of ONE run and press Enter; returns the bytes the browser saved. The row must show
 * Completed, the browser must request exactly the file path of that run, and the saved name is the legacy DD name.
 */
async function downloadFile(page: Page, row: Locator, filePath: string, savedName: string): Promise<Buffer> {
  await expect(row.getByRole('cell').nth(2), 'the run shows Completed').toHaveText('Completed', { timeout: 120_000 });
  const button = row.getByRole('button', { name: /^Download / });
  await tabTo(page, button, 'Download in the row of the run');
  const [download] = await Promise.all([
    pressAndDownload(page),
    page.waitForRequest((request) => new URL(request.url()).pathname === filePath && request.method() === 'GET', { timeout: 60_000 }),
  ]);
  expect(download.suggestedFilename()).toBe(savedName);
  return readFileSync(await download.path());
}

const recordsOf = (file: Buffer, length: number): string[] => Array.from({ length: file.length / length }, (_, i) => file.subarray(i * length, i * length + length).toString('latin1'));

if (live) {
  test.describe('[MAM-86 AC-3] journeys J1 to J7 with the keyboard only', () => {
    test.describe.configure({ mode: 'default', timeout: 480_000 });
    test.beforeEach(() => {
      seedStack();
    });

    test('MAM-86 AC-3 J1 keyboard only: a provider applies, the analyst approves, the directory and the notices show it', async ({ browser, request }) => {
      const analystToken = await tokenOf(request, 'analyst1');
      expect((await getJson(request, `/api/v1/providers/${NPI}`, analystToken)).status, 'run the seed first').toBe(404);
      expect((await getJson(request, `/api/v1/applications?npi=${NPI}`, analystToken)).body.total, 'run the seed first').toBe(0);
      const provider = await newPage(browser);

      await test.step('provider1 signs in with the keyboard and lands on My enrolment', async () => {
        await signInWithKeyboard(provider, 'provider1', '/portal');
        await expect(h1(provider, 'My enrolment')).toBeVisible();
        await expectNoViolations(provider, 'J1 portal.home');
      });
      await test.step('Tab to "Apply for enrolment" in the page and press Enter', async () => {
        const main = provider.getByRole('main');
        await tabAndEnter(provider, main.getByRole('link', { name: 'Apply for enrolment' }).or(main.getByRole('button', { name: 'Apply for enrolment' })), 'Apply for enrolment');
        await expect(provider).toHaveURL(/\/portal\/apply$/);
        await expect(h1(provider, 'Apply for enrolment')).toBeVisible();
        await expectNoViolations(provider, 'J1 portal.apply');
      });
      let applicationId = '';
      await test.step('type the application (arrow keys choose Individual (IN)), Tab to Submit application, Enter: Validated', async () => {
        await fillApplyForm(provider, { taxonomy: '207Q00000X' });
        const answer = await submitApplication(provider);
        expect(answer.status()).toBe(201);
        await expect(provider).toHaveURL(/\/portal\/applications\/[0-9a-f-]{36}$/);
        applicationId = provider.url().split('/').pop() as string;
        await expect(h1(provider, 'Application status')).toBeVisible();
        await expect(provider.getByRole('main').getByText('Validated', { exact: true })).toBeVisible();
        await expectNoViolations(provider, 'J1 portal.application-status');
        const providerToken = await tokenOf(request, 'provider1');
        const application = await getJson(request, `/api/v1/applications/${applicationId}`, providerToken);
        expect(application.status).toBe(200);
        expect(application.body.status).toBe('VALIDATED');
        expect(application.body.errors ?? []).toEqual([]);
      });
      await test.step('(system) registered, screened PASS on four sources, tier L', async () => {
        await expect.poll(async () => (await getJson(request, `/api/v1/providers/${NPI}`, analystToken)).body?.screenFlag, { message: 'screen flag P', ...POLL }).toBe('P');
        const screenings = (await getJson(request, `/api/v1/screenings?npi=${NPI}`, analystToken)).body.items as Json[];
        expect(screenings, 'exactly one screening of 1003000111 (one INITIAL screening per registration)').toHaveLength(1);
        expect(screenings[0].reason).toBe('INITIAL');
        expect(screenings[0].status).toBe('COMPLETED');
        expect(screenings[0].result).toBe('PASS');
        expect((screenings[0].results as Json[]).map((r) => `${r.source}=${r.result}`).sort()).toEqual(SOURCES.map((source) => `${source}=P`));
        await expect.poll(async () => (await getJson(request, `/api/v1/providers/${NPI}`, analystToken)).body?.riskTier, { message: 'risk tier L', ...POLL }).toBe('L');
      });

      const analyst = await newPage(browser);
      await test.step('analyst1 signs in with the keyboard; the provider is under Pending approval', async () => {
        await signInWithKeyboard(analyst, 'analyst1', '/console/queues');
        const pending = section(analyst, /^Pending approval/);
        await expect(pending.getByRole('row').filter({ hasText: NPI })).toHaveCount(1);
        await expect(section(analyst, /^Failed screening/), 'the Failed screening list is shown, so that "not in it" means something').toBeVisible();
        await expect(section(analyst, /^Failed screening/).getByRole('row').filter({ hasText: NPI })).toHaveCount(0);
        await expectNoViolations(analyst, 'J1 console.work-queues');
      });
      await test.step('Tab to Review in the row and press Enter; the arrow keys choose Approve; Tab to Record decision, Enter', async () => {
        await tabAndEnter(analyst, section(analyst, /^Pending approval/).getByRole('row').filter({ hasText: NPI }).getByRole('link', { name: 'Review' }), 'Review in the row of the provider');
        await expect(analyst).toHaveURL(new RegExp(`/console/providers/${NPI}/approval$`));
        await expect(h1(analyst, 'Approve or deny')).toBeVisible();
        await expectNoViolations(analyst, 'J1 console.approval');
        await chooseRadio(analyst, analyst.locator('input[name="decision"]'), analyst.getByRole('radio', { name: 'Approve (becomes Active)' }), 'Approve (becomes Active)');
        await tabTo(analyst, analyst.getByRole('button', { name: 'Record decision' }), 'Record decision');
        const dateBefore = (await request.get(`${GATEWAY}/api/v1/directory/providers?size=1`)).headers()['date'] ?? '';
        const decision = await pressAndWait(analyst, (response) => response.url().endsWith(`/api/v1/providers/${NPI}/approval`) && response.request().method() === 'POST');
        expect(decision.status()).toBe(200);
        const decided = (await decision.json()) as { enrollStatus: string; revalidationDueDate: string };
        expect(decided.enrollStatus).toBe('A');
        expect([fiveYearsAfter(dateBefore), fiveYearsAfter(decision.headers()['date'] ?? '')]).toContain(decided.revalidationDueDate);
        await expect(analyst.getByText(/Decision recorded/)).toContainText('Active (A)');
        await expect(analyst.getByText(/Decision recorded/)).toContainText(shownDate(decided.revalidationDueDate));
        await expectNoViolations(analyst, 'J1 console.approval (decision recorded)');
      });
      await test.step('a visitor types RIVERA in the public directory and presses Enter: MARIA RIVERA is listed', async () => {
        await expect
          .poll(async () => ((await getJson(request, '/api/v1/directory/providers?name=RIVERA')).body?.items ?? []).filter((e: Json) => e.npi === NPI).map((e: Json) => e.displayName), { message: 'directory lists the provider', ...POLL })
          .toEqual(['MARIA RIVERA']);
        const visitor = await newPage(browser);
        await visitor.goto('/directory');
        await expect(h1(visitor, 'Find a Medicaid provider')).toBeVisible();
        await typeInto(visitor, visitor.getByLabel('Name', { exact: true }), 'Name', 'RIVERA');
        await visitor.keyboard.press('Enter');
        const row = visitor.getByRole('row').filter({ hasText: NPI });
        await expect(row).toHaveCount(1);
        await expect(row).toContainText('MARIA RIVERA');
        await expectNoViolations(visitor, 'J1 public.directory');
      });
      await test.step('the notice is in Mailpit; the provider opens My notices and My provider record through the menu', async () => {
        await expect.poll(() => mailSubjects(request, NOTICE_ADDRESS(NPI)), { message: 'Mailpit holds the notice', ...POLL }).toContain('Enrolment approved');
        await openMenuEntry(provider, 'My notices', 'My notices');
        await expect(provider.getByRole('row').filter({ hasText: 'Enrolment approved (APPROVED)' })).toHaveCount(1);
        await expectNoViolations(provider, 'J1 portal.notices');
        await openMenuEntry(provider, 'My provider record', 'My provider record');
        await expect(provider.getByRole('main')).toContainText('Active (A)');
        await expectNoViolations(provider, 'J1 portal.provider-record');
      });
    });

    test('MAM-86 AC-3 J2 keyboard only: an application without a taxonomy is rejected with the error at the field and in the summary', async ({ browser, request }) => {
      const analystToken = await tokenOf(request, 'analyst1');
      const providerToken = await tokenOf(request, 'provider1');
      expect((await getJson(request, `/api/v1/providers/${NPI}`, analystToken)).status, 'run the seed first').toBe(404);
      const page = await newPage(browser);

      await test.step('provider1 signs in with the keyboard at the apply form, types everything except the taxonomy and presses Enter on Submit', async () => {
        await signInWithKeyboard(page, 'provider1', '/portal/apply');
        await expect(h1(page, 'Apply for enrolment')).toBeVisible();
        await expectNoViolations(page, 'J2 portal.apply');
        await typeInto(page, page.getByLabel(/^NPI/), 'NPI', NPI);
        await typeInto(page, page.getByLabel(/^Medicaid ID/), 'Medicaid ID', MEDICAID_ID);
        await chooseOption(page, page.getByLabel(/^Provider type/), 'Provider type', 'Individual (IN)');
        await typeInto(page, page.getByLabel(/^Last name/), 'Last name', 'RIVERA');
        await typeInto(page, page.getByLabel(/^First name/), 'First name', 'MARIA');
        await typeInto(page, page.getByLabel(/^Licence number/), 'Licence number', 'MD12345');
        await typeInto(page, page.getByLabel(/^Licence state/), 'Licence state', 'TX');
        const answer = await submitApplication(page);
        expect(answer.status()).toBe(422);
        await expect(page).toHaveURL(/\/portal\/apply$/);
      });
      await test.step('the summary takes the focus; Tab reaches its first entry; Enter puts the focus on the Taxonomy code field', async () => {
        const summary = page.getByRole('alert').filter({ has: page.getByRole('heading', { level: 2, name: '1 error in the application' }) });
        await expect(summary).toBeFocused();
        await expect(page.getByText(/\bE104:/)).toHaveCount(2); // once in the summary, once at the taxonomy field
        await expect(page.getByText(/\bE10[123]:/)).toHaveCount(0);
        const entry = page.getByRole('main').getByRole('link', { name: /^E104: / });
        await page.keyboard.press('Tab');
        await expect(entry, 'the first Tab after the summary reaches its first entry').toBeFocused();
        await page.keyboard.press('Enter');
        await expect(page.getByLabel(/^Taxonomy code/)).toBeFocused();
        await expectNoViolations(page, 'J2 portal.apply (E104)');
      });
      let applicationId = '';
      await test.step('through the menu: My enrolment lists one Rejected application; Tab to View and Enter: one error row E104', async () => {
        await openMenuEntry(page, 'My enrolment', 'My enrolment');
        const row = page.getByRole('row').filter({ hasText: 'Rejected' });
        await expect(row).toHaveCount(1);
        await tabAndEnter(page, row.getByRole('link', { name: /View/ }), 'View in the row of the rejected application');
        await expect(page).toHaveURL(/\/portal\/applications\/[0-9a-f-]{36}$/);
        applicationId = page.url().split('/').pop() as string;
        await expect(h1(page, 'Application status')).toBeVisible();
        await expect(page.getByRole('main').getByText('Rejected', { exact: true })).toBeVisible();
        const errors = page.getByRole('table', { name: 'Validation errors' });
        await expect(errors.getByRole('row')).toHaveCount(2);
        await expect(errors.getByRole('row').nth(1)).toContainText('E104');
        await expect(errors.getByRole('row').nth(1)).toContainText('taxonomy');
        await expectNoViolations(page, 'J2 portal.application-status (Rejected)');
        const stored = await getJson(request, `/api/v1/applications/${applicationId}`, providerToken);
        expect(stored.body.status).toBe('REJECTED');
        expect((stored.body.errors as Json[]).map((e) => `${e.code}/${e.field}`)).toEqual(['E104/taxonomy']);
      });
      await test.step('the REJECTED notice is listed (menu entry My notices) and nothing was registered', async () => {
        await expect.poll(async () => ((await getJson(request, '/api/v1/notices', providerToken)).body?.items ?? []).map((n: Json) => n.template), { message: 'REJECTED notice', ...POLL }).toContain('REJECTED');
        await openMenuEntry(page, 'My notices', 'My notices');
        await expect(page.getByRole('row').filter({ hasText: 'Application rejected (REJECTED)' })).toHaveCount(1);
        await expectNoViolations(page, 'J2 portal.notices');
        expect((await getJson(request, `/api/v1/providers/${NPI}`, analystToken)).status).toBe(404);
      });
    });

    test('MAM-86 AC-3 J3 keyboard only: screening fails on the owner, the analyst finds it under Failed screening and denies', async ({ browser, request }) => {
      const GROUP_NPI = '1003000444';
      const DENY_REASON = 'J3 SYNTHETIC: OWNER EXCLUDED ON LEIE';
      const analystToken = await tokenOf(request, 'analyst1');
      expect((await getJson(request, `/api/v1/providers/${GROUP_NPI}`, analystToken)).status, 'run the seed first').toBe(404);
      const submitted = await request.post(`${GATEWAY}/api/v1/applications`, {
        headers: { Authorization: `Bearer ${analystToken}`, 'Content-Type': 'application/json' },
        data: {
          npi: GROUP_NPI,
          medicaidId: 'TX0000444',
          providerType: 'GRP',
          taxonomy: '193200000X',
          organization: { legalName: 'EXCLUDED CLINIC LLC', dbaName: 'EXCLUDED CLINIC', ownerNpi: '1990000111' },
        },
      });
      expect(submitted.status(), await submitted.text()).toBe(201);
      expect(((await submitted.json()) as { status: string }).status).toBe('VALIDATED');
      await expect.poll(async () => (await getJson(request, `/api/v1/providers/${GROUP_NPI}`, analystToken)).body?.screenFlag, { message: 'screen flag F', ...POLL }).toBe('F');
      expect((await getJson(request, `/api/v1/providers/${GROUP_NPI}`, analystToken)).body.enrollStatus, 'a failed screening leaves the provider Pending').toBe('P');
      const failedScreenings = (await getJson(request, `/api/v1/screenings?npi=${GROUP_NPI}`, analystToken)).body.items as Json[];
      expect(failedScreenings).toHaveLength(1);
      expect(failedScreenings[0].result).toBe('FAIL');
      const leieResult = (failedScreenings[0].results as Json[]).find((r) => r.source === 'OIGLEIE');
      expect(leieResult?.result).toBe('F');
      expect(leieResult?.errorCode).toBe('E203');

      const page = await newPage(browser);
      await test.step('analyst1 signs in with the keyboard: the provider is under Failed screening, not under Pending approval', async () => {
        await signInWithKeyboard(page, 'analyst1', '/console/queues');
        await expect(section(page, /^Failed screening/).getByRole('row').filter({ hasText: GROUP_NPI })).toHaveCount(1);
        await expect(section(page, /^Pending approval/)).toBeVisible();
        await expect(section(page, /^Pending approval/).getByRole('row').filter({ hasText: GROUP_NPI })).toHaveCount(0);
        await expectNoViolations(page, 'J3 console.work-queues');
      });
      await test.step('Tab to the NPI link in the row and press Enter: the detail shows OIGLEIE Fail (F) with E203', async () => {
        await tabAndEnter(page, section(page, /^Failed screening/).getByRole('row').filter({ hasText: GROUP_NPI }).getByRole('link', { name: GROUP_NPI }), 'NPI link in the failed-screening row');
        await expect(h1(page, 'Provider detail')).toBeVisible();
        const leie = section(page, /^Screening/).getByRole('row').filter({ hasText: 'OIG exclusion list (LEIE)' });
        await expect(leie).toHaveCount(1);
        await expect(leie).toContainText('Fail (F)');
        await expect(leie).toContainText('E203');
        await expectNoViolations(page, 'J3 console.provider-detail');
      });
      await test.step('Tab to Approve or deny, Enter; the arrow keys choose Deny; type the reason; Tab to Record decision, Enter: Denied (D)', async () => {
        await tabAndEnter(page, page.getByRole('main').getByRole('link', { name: 'Approve or deny' }), 'Approve or deny');
        await expect(h1(page, 'Approve or deny')).toBeVisible();
        await chooseRadio(page, page.locator('input[name="decision"]'), page.getByRole('radio', { name: 'Deny (becomes Denied)' }), 'Deny (becomes Denied)');
        await typeInto(page, page.getByLabel(/^Reason/), 'Reason', DENY_REASON);
        await tabTo(page, page.getByRole('button', { name: 'Record decision' }), 'Record decision');
        const decision = await pressAndWait(page, (response) => response.url().endsWith(`/api/v1/providers/${GROUP_NPI}/approval`) && response.request().method() === 'POST');
        expect(decision.status()).toBe(200);
        expect(((await decision.json()) as { enrollStatus: string }).enrollStatus).toBe('D');
        await expect(page.getByText(/Decision recorded/)).toContainText('Denied (D)');
        await expectNoViolations(page, 'J3 console.approval (denied)');
      });
      await test.step('the DENIED notice is listed and the email is in Mailpit', async () => {
        await expect
          .poll(async () => ((await getJson(request, `/api/v1/notices?npi=${GROUP_NPI}`, analystToken)).body?.items ?? []).filter((n: Json) => n.status === 'SENT').map((n: Json) => n.template), { message: 'DENIED notice', ...POLL })
          .toContain('DENIED');
        await expect.poll(() => mailSubjects(request, NOTICE_ADDRESS(GROUP_NPI)), { message: 'DENIED email', ...POLL }).toContain('Enrolment denied');
      });
    });

    test('MAM-86 AC-3 J4 keyboard only: the analyst searches by NPI and Medicaid ID, reads the detail, an unknown NPI says so, a provider cannot use the console', async ({ browser, request }) => {
      const OTHER_NPI = '1005951276'; // TESTPROV BRAVO, seeded
      const UNKNOWN_NPI = '1003000999';
      const analystToken = await tokenOf(request, 'analyst1');
      const providerToken = await tokenOf(request, 'provider1');
      expect((await getJson(request, `/api/v1/providers/${NPI}`, analystToken)).status, 'run the seed first').toBe(404);
      const applied = await request.post(`${GATEWAY}/api/v1/applications`, {
        headers: { Authorization: `Bearer ${providerToken}`, 'Content-Type': 'application/json' },
        data: { npi: NPI, medicaidId: MEDICAID_ID, providerType: 'IN', taxonomy: '207Q00000X', individual: { lastName: 'RIVERA', firstName: 'MARIA', licenseNo: 'MD12345', licenseState: 'TX' } },
      });
      expect(applied.status(), await applied.text()).toBe(201);
      await expect.poll(async () => (await getJson(request, `/api/v1/providers/${NPI}`, analystToken)).body?.riskTier, { message: 'risk tier L', ...POLL }).toBe('L');
      const approved = await request.post(`${GATEWAY}/api/v1/providers/${NPI}/approval`, { headers: { Authorization: `Bearer ${analystToken}`, 'Content-Type': 'application/json' }, data: { decision: 'APPROVE' } });
      expect(approved.status(), await approved.text()).toBe(200);
      const record = (await getJson(request, `/api/v1/providers/${NPI}`, analystToken)).body as Json;
      expect(record.enrollStatus).toBe('A');

      const analyst = await newPage(browser);
      await test.step('analyst1 signs in with the keyboard and searches by NPI (typed, Enter)', async () => {
        await signInWithKeyboard(analyst, 'analyst1', '/console/providers');
        await expect(h1(analyst, 'Provider search')).toBeVisible();
        const sent = await searchProviders(analyst, { npi: NPI });
        expect(sent.searchParams.get('npi')).toBe(NPI);
        await expect(analyst.getByRole('row').filter({ hasText: NPI })).toHaveCount(1);
        await expectNoViolations(analyst, 'J4 console.provider-search (by NPI)');
      });
      await test.step('searches by Medicaid ID (the NPI field is cleared with the keyboard)', async () => {
        const sent = await searchProviders(analyst, { medicaidId: MEDICAID_ID });
        expect(sent.searchParams.get('medicaidId')).toBe(MEDICAID_ID);
        expect(sent.searchParams.get('npi')).toBeNull();
        await expect(analyst.getByRole('row').filter({ hasText: NPI })).toHaveCount(1);
      });
      await test.step('Tab to the NPI link of the result and press Enter: every field with its meaning and the four panels; no audit for an analyst', async () => {
        const auditCalls: string[] = [];
        analyst.on('request', (r) => {
          if (new URL(r.url()).pathname === `/api/v1/providers/${NPI}/audit`) auditCalls.push(r.url());
        });
        await tabAndEnter(analyst, analyst.getByRole('row').filter({ hasText: NPI }).getByRole('link', { name: NPI }), 'NPI link of the result');
        await expect(h1(analyst, 'Provider detail')).toBeVisible();
        const main = analyst.getByRole('main');
        for (const text of [NPI, MEDICAID_ID, 'Individual (IN)', '207Q00000X', 'Limited (L)', 'Active (A)', 'Pass (P)', shownDate(record.effectiveDate), shownDate(record.revalidationDueDate), 'RIVERA', 'MARIA', 'MD12345']) {
          await expect(main, `the detail shows ${text}`).toContainText(text);
        }
        for (const panel of [/^Screening/, /^Sanctions/, /^Revalidation/, /^Notices/]) await expect(section(analyst, panel), `panel ${panel}`).toBeVisible();
        await expect(section(analyst, /^Audit/)).toHaveCount(0);
        await expectNoViolations(analyst, 'J4 console.provider-detail');
        expect(auditCalls, 'analyst1 makes no audit call').toEqual([]);
      });
      await test.step('supervisor1 signs in with the keyboard, searches by NPI, opens the detail with Enter: the audit history is there', async () => {
        const supervisor = await newPage(browser);
        await signInWithKeyboard(supervisor, 'supervisor1', '/console/providers');
        await searchProviders(supervisor, { npi: NPI });
        const link = supervisor.getByRole('row').filter({ hasText: NPI }).getByRole('link', { name: NPI });
        await tabTo(supervisor, link, 'NPI link of the result');
        const [audit] = await Promise.all([supervisor.waitForResponse((r) => new URL(r.url()).pathname === `/api/v1/providers/${NPI}/audit`, { timeout: 60_000 }), supervisor.keyboard.press('Enter')]);
        expect(audit.status()).toBe(200);
        await expect(section(supervisor, /^Audit/)).toBeVisible();
        await expectNoViolations(supervisor, 'J4 console.provider-detail (supervisor, audit)');
      });
      await test.step('an unknown NPI typed in the search says "No provider found"', async () => {
        await tabAndEnter(analyst, analyst.getByRole('navigation', { name: 'Primary navigation' }).getByRole('link', { name: 'Provider search', exact: true }), 'menu entry Provider search');
        await expect(h1(analyst, 'Provider search')).toBeVisible();
        await searchProviders(analyst, { npi: UNKNOWN_NPI });
        await expect(analyst.getByText('No provider found')).toBeVisible();
        const empty = await getJson(request, `/api/v1/providers?npi=${UNKNOWN_NPI}`, analystToken);
        expect(empty.status).toBe(200);
        expect(empty.body.total).toBe(0);
      });
      await test.step('provider1 signs in at a console address: Access denied; Tab to the button, Enter; the menu opens My provider record: only their own', async () => {
        const portal = await newPage(browser);
        await signInWithKeyboard(portal, 'provider1', '/console/providers');
        await expect(h1(portal, 'Access denied')).toBeVisible();
        await expectNoViolations(portal, 'J4 console 403');
        expect((await getJson(request, '/api/v1/providers', providerToken)).status).toBe(403);
        expect((await getJson(request, `/api/v1/providers/${OTHER_NPI}`, providerToken)).status).toBe(403);
        await tabAndEnter(portal, portal.getByRole('main').getByRole('link', { name: 'Go to My enrolment' }), 'Go to My enrolment');
        await expect(h1(portal, 'My enrolment')).toBeVisible();
        await openMenuEntry(portal, 'My provider record', 'My provider record');
        await expect(portal.getByRole('main')).toContainText(NPI);
        await expect(portal.getByRole('main')).not.toContainText(OTHER_NPI);
        await expectNoViolations(portal, 'J4 portal.provider-record');
      });
    });

    test('MAM-86 AC-3 J5 keyboard only: a supervisor suspends and reinstates a provider', async ({ browser, request }) => {
      const ALPHA = '1005940865';
      const SUSPEND_REASON = 'J5 SYNTHETIC SUSPENSION';
      const REINSTATE_REASON = 'J5 SYNTHETIC REINSTATEMENT';
      const supervisorToken = await tokenOf(request, 'supervisor1');
      const statusOf = async () => (await getJson(request, `/api/v1/providers/${ALPHA}`, supervisorToken)).body?.enrollStatus;
      const directoryNpis = async () => ((await getJson(request, '/api/v1/directory/providers?name=ALPHA')).body?.items ?? []).map((entry: Json) => entry.npi);
      const noticeTemplates = async () =>
        ((await getJson(request, `/api/v1/notices?npi=${ALPHA}`, supervisorToken)).body?.items ?? []).filter((n: Json) => n.status === 'SENT').map((n: Json) => n.template);
      expect(await statusOf(), 'ALPHA is not ACTIVE: run the seed first').toBe('A');
      expect((await getJson(request, `/api/v1/sanctions?npi=${ALPHA}`, supervisorToken)).body.total, 'ALPHA already has sanctions').toBe(0);
      expect(await directoryNpis()).toContain(ALPHA);

      const page = await newPage(browser);
      const recordSanction = async (action: string, reason: string, date: string) => {
        await chooseOption(page, page.getByLabel(/^Action/), 'Action', action);
        await typeInto(page, page.getByLabel(/^Reason/), 'Reason', reason);
        await typeInto(page, page.getByLabel(/^Effective date/), 'Effective date', date);
        await tabTo(page, page.getByRole('button', { name: 'Record sanction' }), 'Record sanction');
        return pressAndWait(page, (response) => response.url().endsWith('/api/v1/sanctions') && response.request().method() === 'POST', 'Space');
      };

      await test.step('supervisor1 signs in with the keyboard at the provider detail; Tab to Record sanction, Enter', async () => {
        await signInWithKeyboard(page, 'supervisor1', `/console/providers/${ALPHA}`);
        await expect(h1(page, 'Provider detail')).toBeVisible();
        await expectNoViolations(page, 'J5 console.provider-detail');
        await tabAndEnter(page, page.getByRole('main').getByRole('link', { name: 'Record sanction' }), 'Record sanction');
        await expect(page).toHaveURL(new RegExp(`/console/providers/${ALPHA}/sanctions/new$`));
        await expect(h1(page, 'Record a sanction')).toBeVisible();
        await expect(page.getByLabel(/^Action/)).toBeVisible();
        await expect(page.getByLabel(/^Reason/)).toBeVisible();
        await expect(page.getByLabel(/^Effective date/)).toBeVisible();
        await expectNoViolations(page, 'J5 console.record-sanction');
      });
      await test.step('an empty form is refused: the summary links to the fields (Tab, Enter put the focus on Action)', async () => {
        await tabTo(page, page.getByRole('button', { name: 'Record sanction' }), 'Record sanction');
        await page.keyboard.press('Enter');
        await expect(page.getByRole('heading', { level: 2, name: '2 errors in the sanction' })).toBeVisible();
        await page.keyboard.press('Tab');
        await expect(page.getByRole('link', { name: 'Choose an action' }).first(), 'the first Tab after the summary reaches its first entry').toBeFocused();
        await page.keyboard.press('Enter');
        await expect(page.getByLabel(/^Action/)).toBeFocused();
        expect((await getJson(request, `/api/v1/sanctions?npi=${ALPHA}`, supervisorToken)).body.total, 'nothing was stored').toBe(0);
      });
      await test.step('Suspend (S) with a reason and a date, the space bar on Record sanction: Suspended (S), SUS notice', async () => {
        const suspended = await recordSanction('Suspend (S)', SUSPEND_REASON, '03/31/2026');
        expect(suspended.status()).toBe(201);
        expect(((await suspended.json()) as { resultingStatus: string }).resultingStatus).toBe('S');
        await expect(page.getByText(/Suspended \(S\)/).first()).toBeVisible();
        await expectNoViolations(page, 'J5 console.record-sanction (suspended)');
        expect(await statusOf()).toBe('S');
        await expect.poll(noticeTemplates, { message: 'SUS notice', ...POLL }).toContain('SUS');
        await expect.poll(() => mailSubjects(request, NOTICE_ADDRESS(ALPHA)), { message: 'SUS email', ...POLL }).toContain('Suspension notice');
      });
      await test.step('a visitor types ALPHA in the public directory and presses Enter: the suspended provider is gone', async () => {
        await expect.poll(directoryNpis, { message: 'ALPHA removed from the directory', ...POLL }).not.toContain(ALPHA);
        const visitor = await newPage(browser);
        await visitor.goto('/directory');
        await typeInto(visitor, visitor.getByLabel('Name', { exact: true }), 'Name', 'ALPHA');
        await visitor.keyboard.press('Enter');
        await expect(visitor.getByText('No providers found')).toBeVisible();
        await expect(visitor.getByRole('row').filter({ hasText: ALPHA })).toHaveCount(0);
        await expectNoViolations(visitor, 'J5 public.directory (No providers found)');
      });
      await test.step('Tab to Provider detail and Enter, Tab to Record sanction and Enter again; Reinstate (R): Active (A), RST notice', async () => {
        await tabAndEnter(page, page.getByRole('main').getByRole('link', { name: 'Provider detail' }), 'Provider detail');
        await expect(h1(page, 'Provider detail')).toBeVisible();
        await tabAndEnter(page, page.getByRole('main').getByRole('link', { name: 'Record sanction' }), 'Record sanction');
        await expect(h1(page, 'Record a sanction')).toBeVisible();
        const reinstated = await recordSanction('Reinstate (R)', REINSTATE_REASON, '04/30/2026');
        expect(reinstated.status()).toBe(201);
        expect(((await reinstated.json()) as { resultingStatus: string }).resultingStatus).toBe('A');
        await expect(page.getByText(/Active \(A\)/).first()).toBeVisible();
        expect(await statusOf()).toBe('A');
        await expect.poll(noticeTemplates, { message: 'RST notice', ...POLL }).toContain('RST');
        await expect.poll(() => mailSubjects(request, NOTICE_ADDRESS(ALPHA)), { message: 'RST email', ...POLL }).toContain('Reinstatement notice');
      });
      await test.step('the history lists both actions, in order, on the provider detail (reached with the keyboard) and in the API', async () => {
        const history = await getJson(request, `/api/v1/sanctions?npi=${ALPHA}`, supervisorToken);
        const rows = (history.body.items as Json[]).slice().sort((a, b) => a.recordedAt.localeCompare(b.recordedAt)).map((row) => `${row.action}|${row.reason}|${row.effectiveDate}`);
        expect(rows).toEqual([`S|${SUSPEND_REASON}|2026-03-31`, `R|${REINSTATE_REASON}|2026-04-30`]);
        await tabAndEnter(page, page.getByRole('main').getByRole('link', { name: 'Provider detail' }), 'Provider detail');
        await expect(h1(page, 'Provider detail')).toBeVisible();
        const panel = section(page, /^Sanctions/);
        await expect(panel.getByRole('row').filter({ hasText: 'Suspend (S)' }).filter({ hasText: SUSPEND_REASON })).toHaveCount(1);
        await expect(panel.getByRole('row').filter({ hasText: 'Reinstate (R)' }).filter({ hasText: REINSTATE_REASON })).toHaveCount(1);
        await expectNoViolations(page, 'J5 console.provider-detail (history)');
      });
      await test.step('an unknown action code is refused by the service; nothing is stored', async () => {
        const unknown = await request.post(`${GATEWAY}/api/v1/sanctions`, {
          headers: { Authorization: `Bearer ${supervisorToken}`, 'Content-Type': 'application/json' },
          data: { npi: ALPHA, action: 'X', reason: 'J5 SYNTHETIC UNKNOWN ACTION', effectiveDate: '2026-05-31' },
        });
        expect(unknown.status()).toBe(422);
        expect(unknown.headers()['content-type']).toContain('application/problem+json');
        expect(JSON.stringify(await unknown.json())).toContain('action');
        expect((await getJson(request, `/api/v1/sanctions?npi=${ALPHA}`, supervisorToken)).body.total).toBe(2);
        expect(await statusOf()).toBe('A');
      });
    });

    test('MAM-86 AC-3 J6 keyboard only: the administrator runs the monthly revalidation sweep and downloads the REVALQ file', async ({ browser, request }) => {
      const BUSINESS_DATE = '2026-03-31';
      const BRAVO = '1005951276';
      const adminToken = await tokenOf(request, 'admin1');
      const analystToken = await tokenOf(request, 'analyst1');
      expect((await getJson(request, `/api/v1/providers/${BRAVO}`, analystToken)).body?.revalidationDueDate, 'run the seed first').toBe('2026-01-15');
      const all = (await getJson(request, '/api/v1/providers?size=200', analystToken)).body.items as Json[];
      const due = all.filter((p) => p.enrollStatus === 'A' && !!p.revalidationDueDate && p.revalidationDueDate <= BUSINESS_DATE);
      const dueDate = new Map<string, string>(due.map((p) => [p.npi as string, p.revalidationDueDate as string]));
      expect(dueDate.has(BRAVO)).toBe(true);
      const notDue = all.filter((p) => !dueDate.has(p.npi)).map((p) => p.npi as string);

      const admin = await newPage(browser);
      let run = { runId: '', businessDate: '' };
      await test.step('admin1 signs in with the keyboard; arrow keys choose the Revalidation sweep, the date is typed, Enter on Run job', async () => {
        await signInWithKeyboard(admin, 'admin1', '/console/admin/jobs');
        await expect(h1(admin, 'Batch jobs')).toBeVisible();
        run = await runJob(admin, 'Revalidation sweep', '/api/v1/batch/revalidation-sweeps');
        await expect.poll(async () => (await getJson(request, `/api/v1/batch/revalidation-sweeps/${run.runId}`, adminToken)).body?.status, { message: 'sweep COMPLETED', ...POLL }).toBe('COMPLETED');
        const finished = (await getJson(request, `/api/v1/batch/revalidation-sweeps/${run.runId}`, adminToken)).body;
        expect(finished.counts?.queued).toBe(due.length);
        expect(finished.error ?? null).toBeNull();
        const sweepRows = admin.getByRole('table', { name: /Recent runs/ }).getByRole('row').filter({ hasText: 'Revalidation sweep' });
        await expect(sweepRows).toHaveCount(1);
        await expect(sweepRows.getByRole('cell').nth(2)).toHaveText('Completed', { timeout: 120_000 });
        await expectNoViolations(admin, 'J6 console.batch-jobs');
      });
      await test.step('Tab to Download in the row of the run and press Enter: REVALQ, 80-byte records, the same bytes as the API', async () => {
        const sweepRows = admin.getByRole('table', { name: /Recent runs/ }).getByRole('row').filter({ hasText: 'Revalidation sweep' });
        const revalq = await downloadFile(admin, sweepRows, `/api/v1/batch/revalidation-sweeps/${run.runId}/queue-file`, 'REVALQ.D20260331');
        const viaApi = await request.get(`${GATEWAY}/api/v1/batch/revalidation-sweeps/${run.runId}/queue-file`, { headers: { Authorization: `Bearer ${adminToken}` } });
        expect(viaApi.status()).toBe(200);
        expect(Buffer.compare(revalq, await viaApi.body())).toBe(0);
        expect(revalq.length % 80, 'REVALQ is a whole number of 80-byte records').toBe(0);
        const records = recordsOf(revalq, 80);
        expect(records).toHaveLength(due.length);
        for (const record of records) {
          const npi = record.slice(0, 10);
          expect(dueDate.has(npi), `${npi} is due`).toBe(true);
          expect(record.slice(10, 18)).toBe((dueDate.get(npi) as string).replaceAll('-', ''));
          expect(record.slice(18, 35)).toBe('RESCREEN-REQUIRED');
          expect(record.slice(35)).toBe(' '.repeat(45));
        }
        expect(new Set(records.map((r) => r.slice(0, 10)))).toEqual(new Set(dueDate.keys()));
      });
      await test.step('one case per due provider (API); analyst1 signs in with the keyboard: the queue lists nobody twice', async () => {
        for (const npi of dueDate.keys()) expect((await getJson(request, `/api/v1/revalidations?npi=${npi}`, analystToken)).body.total, `one case for ${npi}`).toBe(1);
        for (const npi of notDue) expect((await getJson(request, `/api/v1/revalidations?npi=${npi}`, analystToken)).body.total, `no case for ${npi}`).toBe(0);
        const analyst = await newPage(browser);
        await signInWithKeyboard(analyst, 'analyst1', '/console/queues');
        const queue = section(analyst, /^Revalidation due/);
        await expect(queue).toBeVisible();
        await expect(queue.getByText(/^Loading/)).toHaveCount(0, { timeout: 60_000 });
        for (const npi of dueDate.keys()) expect(await queue.getByRole('row').filter({ hasText: npi }).count(), `${npi} listed at most once`).toBeLessThanOrEqual(1);
        await expectNoViolations(analyst, 'J6 console.work-queues');
      });
      await test.step('re-screening completes each case and sets a later due date', async () => {
        for (const npi of dueDate.keys()) {
          await expect.poll(async () => (await getJson(request, `/api/v1/revalidations?npi=${npi}`, analystToken)).body.items?.[0]?.status, { message: `case of ${npi} COMPLETED`, ...POLL }).toBe('COMPLETED');
          const provider = (await getJson(request, `/api/v1/providers/${npi}`, analystToken)).body;
          expect(provider.revalidationDueDate > BUSINESS_DATE, `${npi} next due ${provider.revalidationDueDate}`).toBe(true);
        }
      });
      await test.step('a second sweep for the same date, started with the keyboard, adds no case', async () => {
        const again = await runJob(admin, 'Revalidation sweep', '/api/v1/batch/revalidation-sweeps');
        expect(again.runId).not.toBe(run.runId);
        await expect.poll(async () => (await getJson(request, `/api/v1/batch/revalidation-sweeps/${again.runId}`, adminToken)).body?.status, { message: 'second sweep COMPLETED', ...POLL }).toBe('COMPLETED');
        for (const npi of dueDate.keys()) expect((await getJson(request, `/api/v1/revalidations?npi=${npi}`, analystToken)).body.total, `still one case for ${npi}`).toBe(1);
        for (const npi of notDue) expect((await getJson(request, `/api/v1/revalidations?npi=${npi}`, analystToken)).body.total, `still no case for ${npi}`).toBe(0);
        await expectNoViolations(admin, 'J6 console.batch-jobs (second run)');
      });
    });

    test('MAM-86 AC-3 J7 keyboard only: the administrator runs the T-MSIS and directory extracts and downloads both files; a visitor finds the hospice', async ({ browser, request }) => {
      const BUSINESS_DATE = '2026-03-31';
      const ALPHA = '1005940865';
      const CLINIC = '1005912765';
      const HOSPICE_ACTIVE = '1005947811';
      const HOSPICE_TERMINATED = '1005932946';
      const adminToken = await tokenOf(request, 'admin1');
      const analystToken = await tokenOf(request, 'analyst1');
      const active = (await getJson(request, '/api/v1/providers?status=A&size=200', analystToken)).body.items as Json[];
      const activeNpis = new Set<string>(active.map((p) => p.npi as string));
      expect(activeNpis.has(ALPHA) && activeNpis.has(HOSPICE_ACTIVE), 'run the seed first').toBe(true);
      expect(activeNpis.has(HOSPICE_TERMINATED)).toBe(false);
      const medicaidIdOf = new Map<string, string>(active.map((p) => [p.npi as string, p.medicaidId as string]));
      const listedBefore = (await getJson(request, '/api/v1/batch/tmsis-extracts?size=200', adminToken)).body.total as number;

      const admin = await newPage(browser);
      const runs = admin.getByRole('table', { name: /Recent runs/ });
      let tmsisRecords: string[] = [];
      await test.step('admin1 signs in with the keyboard and starts the T-MSIS extract for 03/31/2026; the page follows the run without a reload', async () => {
        await signInWithKeyboard(admin, 'admin1', '/console/admin/jobs');
        await expect(h1(admin, 'Batch jobs')).toBeVisible();
        let navigations = 0;
        admin.on('framenavigated', (frame) => {
          if (frame === admin.mainFrame()) navigations += 1;
        });
        const tmsisRun = await runJob(admin, 'T-MSIS extract', '/api/v1/batch/tmsis-extracts');
        const listed = async () => ((await getJson(request, '/api/v1/batch/tmsis-extracts?size=200', adminToken)).body.items as Json[]).find((r) => r.runId === tmsisRun.runId);
        await expect.poll(async () => (await listed())?.status, { message: 'T-MSIS run COMPLETED', ...POLL }).toBe('COMPLETED');
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
        const tmsisApi = await request.get(`${GATEWAY}/api/v1/batch/tmsis-extracts/${tmsisRun.runId}/file`, { headers: { Authorization: `Bearer ${adminToken}` } });
        expect(tmsisApi.status()).toBe(200);
        expect(Buffer.compare(tmsisOut, await tmsisApi.body())).toBe(0);
        expect(tmsisOut.length % 120, 'TMSISOUT is a whole number of 120-byte records').toBe(0);
        tmsisRecords = recordsOf(tmsisOut, 120);
        for (const record of tmsisRecords) {
          const npi = record.slice(14, 24);
          expect(record.slice(0, 5)).toBe('PRV01');
          expect(record.slice(5, 14), `Medicaid ID of ${npi}`).toBe(medicaidIdOf.get(npi));
          expect(record.slice(24, 25)).toBe('A');
          expect(record.slice(25, 30)).toBe('26090');
          expect(record.slice(30)).toBe(' '.repeat(90));
        }
        expect(entry.businessDate).toBe(BUSINESS_DATE);
        expect(entry.counts?.rows).toBe(tmsisRecords.length);
        expect((await getJson(request, '/api/v1/batch/tmsis-extracts?size=200', adminToken)).body.total).toBe(listedBefore + 1);
        await expectNoViolations(admin, 'J7 console.batch-jobs (T-MSIS)');
      });
      let dirRecords: string[] = [];
      await test.step('starts the directory extract with the keyboard and downloads DIROUT: 100-byte records, composed names', async () => {
        const dirRun = await runJob(admin, 'Directory extract (DIROUT)', '/api/v1/batch/directory-extracts');
        await expect.poll(async () => (await getJson(request, `/api/v1/batch/directory-extracts/${dirRun.runId}`, adminToken)).body?.status, { message: 'directory run COMPLETED', ...POLL }).toBe('COMPLETED');
        const dirRows = runs.getByRole('row').filter({ hasText: 'Directory extract' });
        await expect(dirRows).toHaveCount(1);
        const dirOut = await downloadFile(admin, dirRows, `/api/v1/batch/directory-extracts/${dirRun.runId}/file`, 'DIROUT.D20260331');
        const dirApi = await request.get(`${GATEWAY}/api/v1/batch/directory-extracts/${dirRun.runId}/file`, { headers: { Authorization: `Bearer ${adminToken}` } });
        expect(dirApi.status()).toBe(200);
        expect(Buffer.compare(dirOut, await dirApi.body())).toBe(0);
        expect(dirOut.length % 100, 'DIROUT is a whole number of 100-byte records').toBe(0);
        dirRecords = recordsOf(dirOut, 100);
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
        const nameOf = (npi: string) => dirRecords.find((r) => r.slice(9, 19) === npi)?.slice(22, 82) ?? '';
        expect(nameOf(ALPHA)).toMatch(/TESTPROV\s+ALPHA/);
        expect(nameOf(ALPHA)).not.toContain('SD10001');
        expect(nameOf(CLINIC).trim()).toBe('SAMPLE CLINIC 01');
        expect(new Set(tmsisRecords.map((r) => r.slice(14, 24)))).toEqual(activeNpis);
        expect(new Set(dirRecords.map((r) => r.slice(9, 19)))).toEqual(activeNpis);
        expect(tmsisRecords, 'TMSISOUT holds one record for each active provider, none twice').toHaveLength(activeNpis.size);
        expect(dirRecords, 'DIROUT holds one record for each active provider, none twice').toHaveLength(activeNpis.size);
        await expectNoViolations(admin, 'J7 console.batch-jobs (DIROUT)');
      });
      await test.step('a visitor types HOSPICE in the public directory and presses Enter: only the active hospice, with its composed name', async () => {
        const visitor = await newPage(browser);
        await visitor.goto('/directory');
        await expect(h1(visitor, 'Find a Medicaid provider')).toBeVisible();
        await typeInto(visitor, visitor.getByLabel('Name', { exact: true }), 'Name', 'HOSPICE');
        await visitor.keyboard.press('Enter');
        await expect(visitor.getByRole('row').filter({ hasText: HOSPICE_ACTIVE })).toHaveCount(1);
        await expect(visitor.getByRole('row').filter({ hasText: HOSPICE_ACTIVE })).toContainText('SAMPLE HOSPICE 01');
        await expect(visitor.getByRole('row').filter({ hasText: HOSPICE_TERMINATED })).toHaveCount(0);
        await expectNoViolations(visitor, 'J7 public.directory');
        const search = await getJson(request, '/api/v1/directory/providers?name=HOSPICE');
        expect(search.status).toBe(200);
        const items = search.body.items as Json[];
        expect(items.length).toBeGreaterThan(0);
        for (const item of items) {
          expect((item.displayName as string).toUpperCase()).toContain('HOSPICE');
          expect(activeNpis.has(item.npi), `${item.npi} is ACTIVE`).toBe(true);
        }
        expect(items.map((i) => i.npi)).not.toContain(HOSPICE_TERMINATED);
      });
    });
  });
}

