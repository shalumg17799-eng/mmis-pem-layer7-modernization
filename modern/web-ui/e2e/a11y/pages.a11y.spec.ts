// MAM-86 accessibility of every page (WCAG 2.1 AA), on the running stack at its seed state. Read-only: nothing is written.
//   AC-1  the 13 screens of modern/docs/ux/screen-inventory.md in their main state: zero axe violations
//   AC-2  the apply form with errors, the provider search with "No provider found", a console route refused with 403: zero violations
//   AC-4  Tab through every screen: interactive, visible, a focus indicator that was not there before, order follows the page
//   AC-5  colour contrast (the axe rule ran, nothing undecided hidden) and every status read as text with its code
//   AC-6  one h1, landmarks, header cells in every table, a visible label on every input
// Gate: like the journeys, the tests are registered only with PEM_JOURNEYS=1 (they sign in through the real Keycloak and read the
// live gateway); without it the file registers nothing. Run: PEM_JOURNEYS=1 npx playwright test e2e/a11y/pages.a11y.spec.ts
// after `bash infra/scripts/seed.sh`. Passwords are read at run time from the environment or modern/infra/.env.local.
// Data: see support/screens.ts (live data for the console and the directory; synthetic gateway answers for the portal screens
// and the approval page, so that a scan never writes). Keycloak is always the real one.
import { expect, test, type Browser } from '@playwright/test';
import { json, mockApi } from '../support/gateway-routes';
import { expectNoViolations, scanPage } from './support/axe-gate';
import { problemsOf, walkTabOrder } from './support/focus';
import { APPLY_ERRORS, QUEUES_POPULATED, SCREENS, openScreen, screenOf } from './support/screens';
import { closeSessions, openAs, tabOf } from './support/sessions';
import { CODES, badgeProblems, readFacts, structureProblems } from './support/structure';

const live = Boolean(process.env.PEM_JOURNEYS);

const note = (type: string, description: string) => test.info().annotations.push({ type, description });

if (live) {
  test.describe.configure({ mode: 'default' });
  test.afterAll(async () => {
    await closeSessions();
  });

  test.describe('[MAM-86 AC-1] zero axe violations on the 13 screens', () => {
    for (const screen of SCREENS) {
      test(`MAM-86 AC-1 ${screen.id}: axe reports no violation for wcag2a, wcag2aa, wcag21a, wcag21aa`, async ({ browser }) => {
        const page = await openScreen(browser, screen);
        const scan = await expectNoViolations(page, screen.id);
        note('axe', `${screen.id} ${scan.url}: ${scan.ran.length} rules ran, colour-contrast passed ${scan.contrastPassed} elements, undecided ${scan.contrastUndecided}, data ${screen.data}`);
      });
    }
  });

  test.describe('[MAM-86 AC-1] other states of the screens (beyond the main state of the inventory)', () => {
    test('MAM-86 AC-1 console.work-queues with an entry in each of the four lists (scripted answers): zero axe violations', async ({ browser }) => {
      const page = await openPopulatedQueues(browser);
      const scan = await expectNoViolations(page, 'console.work-queues (four lists with entries)');
      note('axe', `console.work-queues (four lists with entries): ${scan.ran.length} rules ran, colour-contrast passed ${scan.contrastPassed} elements, undecided ${scan.contrastUndecided}`);
    });

    test('MAM-86 AC-1 console.batch-jobs with a file job chosen (the file input and the batch id are shown): zero axe violations and every input labelled', async ({ browser }) => {
      const page = await openScreen(browser, screenOf('console.batch-jobs'));
      await page.getByLabel(/^Job/).selectOption({ label: 'Sanction intake (SANCIN)' });
      await expect(page.getByLabel(/^File \(SANCIN\)/)).toBeVisible();
      await expectNoViolations(page, 'console.batch-jobs (file job chosen)');
      const problems = structureProblems(await readFacts(page));
      expect(problems, problems.join('; ')).toEqual([]);
    });
  });

  test.describe('[MAM-86 AC-2] error, empty and refused states', () => {
    test('MAM-86 AC-2 portal.apply with errors: errors at the fields, a summary linked to them, zero axe violations', async ({ browser }) => {
      const page = await tabOf(browser, 'provider1');
      await page.unrouteAll({ behavior: 'ignoreErrors' });
      const api = await mockApi(page, {
        'POST /api/v1/applications': () => json(422, { title: 'Application rejected', status: 422, errors: APPLY_ERRORS }),
        'GET /api/v1/applications': () => json(200, { items: [], page: 0, size: 20, total: 0 }),
      });
      await openAs(page, 'provider1', '/portal/apply', () => page.getByRole('button', { name: 'Submit application' }));
      await page.getByRole('button', { name: 'Submit application' }).click();
      await expect(page.getByRole('heading', { level: 2, name: '3 errors in the application' })).toBeVisible();
      expect(api.to('POST', '/api/v1/applications'), 'the empty form was sent once').toHaveLength(1);

      // every error is next to its field: the message is there, the input points at it and is marked invalid
      const fields: Array<[string, string, string]> = [
        ['npi', 'E101', 'NPI'],
        ['providerType', 'E102', 'Provider type'],
        ['taxonomy', 'E104', 'Taxonomy code'],
      ];
      for (const [id, code, label] of fields) {
        const message = page.locator(`#${id}-error-0`);
        await expect(message, `${label}: the error is shown next to the field`).toContainText(`${code}:`);
        const input = page.locator(`#${id}`);
        await expect(input, `${label} is described by its error`).toHaveAttribute('aria-describedby', new RegExp(`${id}-error-0`));
        await expect(input, `${label} is marked invalid`).toHaveAttribute('aria-invalid', 'true');
      }

      // the summary lists them all, each entry is a link that moves the focus to its field
      const links = page.getByRole('main').getByRole('alert').getByRole('link');
      await expect(links, 'the summary has one link for each of the three errors (the fourth link is "My applications")').toHaveCount(4);
      for (const [id, code, label] of fields) {
        const link = page.getByRole('main').getByRole('alert').getByRole('link', { name: new RegExp(`^${code}: `) });
        await expect(link, `the summary has an entry for ${code}`).toHaveCount(1);
        await expect(link).toHaveAttribute('href', `#${id}`);
        await link.click();
        await expect(page.locator(`#${id}`), `following the ${code} entry puts the focus on ${label}`).toBeFocused();
      }
      await expectNoViolations(page, 'portal.apply (with errors)');
    });

    test('MAM-86 AC-2 console.provider-search with "No provider found": zero axe violations', async ({ browser }) => {
      const page = await tabOf(browser, 'analyst1');
      await page.unrouteAll({ behavior: 'ignoreErrors' });
      await openAs(page, 'analyst1', '/console/providers', () => page.getByRole('button', { name: 'Search' }));
      await page.getByLabel(/^NPI/).fill('1003000999');
      await page.getByRole('button', { name: 'Search' }).click();
      await expect(page.getByText('No provider found'), 'an unknown NPI says so').toBeVisible({ timeout: 30_000 });
      await expect(page.getByRole('table', { name: 'Search results' })).toHaveCount(0);
      await expectNoViolations(page, 'console.provider-search (No provider found)');
    });

    test('MAM-86 AC-2 a console route refused with 403 (a PROVIDER opens /console/providers): zero axe violations', async ({ browser }) => {
      const page = await tabOf(browser, 'provider1');
      await page.unrouteAll({ behavior: 'ignoreErrors' });
      await openAs(page, 'provider1', '/console/providers', () => page.getByRole('heading', { level: 1, name: 'Access denied' }));
      await expect(page.getByRole('main'), 'the page says what happened').toContainText('Error 403');
      await expect(page.getByRole('heading', { level: 1 }), 'the console page itself is not shown').toHaveCount(1);
      await expectNoViolations(page, 'console 403 (Access denied)');
    });

    test('MAM-86 AC-2 console.record-sanction with errors: errors next to the fields, a summary linked to them, zero axe violations', async ({ browser }) => {
      const page = await tabOf(browser, 'supervisor1');
      await page.unrouteAll({ behavior: 'ignoreErrors' });
      await openAs(page, 'supervisor1', '/console/providers/1005940865/sanctions/new', () => page.getByRole('button', { name: 'Record sanction' }));
      await page.getByRole('button', { name: 'Record sanction' }).click(); // nothing chosen: the page checks it before it sends anything
      await expect(page.getByRole('heading', { level: 2, name: '2 errors in the sanction' })).toBeVisible();
      await expect(page.locator('#action-error-0')).toContainText('Choose an action');
      await expect(page.locator('#effectiveDate-error-0')).toContainText('Enter the effective date');
      await expect(page.locator('#action')).toHaveAttribute('aria-invalid', 'true');
      await expect(page.locator('#effectiveDate')).toHaveAttribute('aria-invalid', 'true');
      for (const [id, text] of [['action', 'Choose an action'], ['effectiveDate', 'Enter the effective date']] as const) {
        const link = page.getByRole('main').getByRole('alert').getByRole('link', { name: new RegExp(`^${text}`) });
        await expect(link).toHaveAttribute('href', `#${id}`);
        await link.click();
        await expect(page.locator(`#${id}`), `following the "${text}" entry puts the focus on its field`).toBeFocused();
      }
      await expectNoViolations(page, 'console.record-sanction (with errors)');
    });

    test('MAM-86 AC-2 console.approval with no decision chosen: the error summary links to the first choice, zero axe violations', async ({ browser }) => {
      const page = await tabOf(browser, 'analyst1');
      await page.unrouteAll({ behavior: 'ignoreErrors' });
      await mockApi(page, screenOf('console.approval').routes ?? {});
      await openAs(page, 'analyst1', screenOf('console.approval').path, () => page.getByRole('radio', { name: 'Approve (becomes Active)' }));
      await page.getByRole('button', { name: 'Record decision' }).click();
      await expect(page.getByRole('heading', { level: 2, name: '1 error in the decision' })).toBeVisible();
      await page.getByRole('main').getByRole('alert').getByRole('link', { name: 'Choose Approve or Deny' }).click();
      await expect(page.locator('#decision-approve'), 'following the entry puts the focus on the first choice').toBeFocused();
      await expectNoViolations(page, 'console.approval (no decision chosen)');
    });
  });

  test.describe('[MAM-86 AC-4] focus is visible and the tab order is logical', () => {
    for (const screen of SCREENS) {
      test(`MAM-86 AC-4 ${screen.id}: every Tab stop is an interactive, visible element with a focus indicator, in the visual order`, async ({ browser }) => {
        const page = await openScreen(browser, screen);
        const walk = await walkTabOrder(page);
        note('tab-walk', `${screen.id}: ${walk.stops.length} stops, ended ${walk.ended}: ${walk.stops.map((stop) => `${stop.kind || stop.tag} "${stop.name}"`).join(' > ')}`);
        const problems = problemsOf(walk);
        expect(problems, `${screen.id}: ${problems.length} problem(s) in the Tab walk:\n${problems.join('\n')}`).toEqual([]);
        expect(walk.stops.length, `${screen.id} has stops to walk`).toBeGreaterThan(3);
      });
    }

    // Kept in this group (not in the "other states" group of AC-1): the trace tool reads the bracket tag of every group in front of
    // the title, so a test of AC-4 must not sit in a group tagged AC-1 (policy.a11y.spec.ts checks it).
    test('MAM-86 AC-4 console.work-queues with an entry in each of the four lists (scripted answers): Tab order and focus indicators', async ({ browser }) => {
      const page = await openPopulatedQueues(browser);
      const walk = await walkTabOrder(page);
      note('tab-walk', `console.work-queues (four lists with entries): ${walk.stops.length} stops: ${walk.stops.map((stop) => `${stop.kind || stop.tag} "${stop.name}"`).join(' > ')}`);
      const problems = problemsOf(walk);
      expect(problems, `${problems.length} problem(s) in the Tab walk:\n${problems.join('\n')}`).toEqual([]);
    });
  });

  test.describe('[MAM-86 AC-5] contrast and status are not left to colour', () => {
    for (const screen of SCREENS) {
      test(`MAM-86 AC-5 ${screen.id}: the colour-contrast rule ran on the text and found no text below 4.5:1`, async ({ browser }) => {
        const page = await openScreen(browser, screen);
        const scan = await scanPage(page, screen.id);
        const contrast = scan.violations.filter((violation) => violation.id === 'color-contrast');
        expect(contrast.map((violation) => `${violation.id}: ${violation.nodes.map((node) => node.target.join(' ')).join(' | ')}`), `${screen.id} contrast violations`).toEqual([]);
        expect(scan.ran, 'the colour-contrast rule is not disabled: it looked at elements of the page').toContain('color-contrast');
        expect(scan.contrastPassed, `${screen.id}: elements whose contrast was measured and passed`).toBeGreaterThan(0);
        const below = scan.contrastUndecidedMeasured.filter((item) => !item.ok);
        expect(below.map((item) => `${item.target} (${item.reason}): ${item.ratio}:1, needs ${item.required}:1`), `${screen.id}: text axe could not decide, measured here from the computed colours`).toEqual([]);
        note('contrast', `${screen.id}: ${scan.contrastPassed} elements passed, ${scan.contrastUndecided} undecided by axe and measured here: ${scan.contrastUndecidedTargets.join('; ')}`);
      });

      test(`MAM-86 AC-5 ${screen.id}: every status badge and status word shows its text with its code`, async ({ browser }) => {
        const page = await openScreen(browser, screen);
        const facts = await readFacts(page);
        note('badges', `${screen.id}: ${JSON.stringify(facts.badges)}`);
        expect(badgeProblems(facts), `${screen.id} badges ${JSON.stringify(facts.badges)}`).toEqual([]);
      });
    }

    test('MAM-86 AC-5 console.provider-search: Suspended (S) and every other status are text with their code, never colour alone', async ({ browser }) => {
      const page = await openScreen(browser, screenOf('console.provider-search'));
      const results = page.getByRole('table', { name: 'Search results' });
      const tags = await results.locator('.usa-tag').allTextContents();
      const words = Object.entries(CODES).filter(([word]) => ['Pending', 'Active', 'Terminated', 'Denied', 'Suspended'].includes(word));
      const allowed = new Set(words.map(([word, letter]) => `${word} (${letter})`));
      expect(tags.length, 'the seed has providers of several statuses').toBeGreaterThanOrEqual(5);
      expect(tags.filter((text) => !allowed.has(text.trim())), 'badges that are not "<Word> (<code>)"').toEqual([]);
      expect(tags.map((text) => text.trim()), 'the seeded suspended provider is listed as Suspended (S)').toContain('Suspended (S)');
      await expect(results.getByText(/^Suspended$/), 'no status is the bare word Suspended').toHaveCount(0);
    });

    test('MAM-86 AC-5 console.provider-detail: a suspended provider reads Suspended (S) in the banner and in the record', async ({ browser }) => {
      const page = await openScreen(browser, screenOf('console.provider-search'));
      const suspended = page.getByRole('table', { name: 'Search results' }).getByRole('row').filter({ has: page.locator('.usa-tag', { hasText: 'Suspended (S)' }) });
      await expect(suspended, 'the seed holds one suspended provider').toHaveCount(1);
      const npi = ((await suspended.getByRole('cell').first().textContent()) ?? '').trim();
      await suspended.getByRole('link', { name: npi }).click();
      await expect(page.getByRole('heading', { level: 1, name: 'Provider detail' })).toBeVisible();
      await expect(page.getByRole('heading', { level: 2, name: 'Status: Suspended (S)' }), `the banner of ${npi}`).toBeVisible({ timeout: 30_000 });
      const main = page.getByRole('main');
      await expect(main.getByText(/Suspended \(S\)/).first()).toBeVisible();
      await expect(main.getByText(/^Suspended$/), 'no status is the bare word Suspended').toHaveCount(0);
      await expect(main.getByText(/\bDenied\b/).filter({ hasNotText: /\(D\)/ }), 'a suspended provider is never shown as Denied (D-006)').toHaveCount(0);
      const facts = await readFacts(page);
      expect(badgeProblems(facts), `${npi} detail badges ${JSON.stringify(facts.badges)}`).toEqual([]);
      note('suspended', `${npi}: badges ${JSON.stringify(facts.badges)}`);
    });

    test('MAM-86 AC-5 portal.provider-record: the status is text with its code', async ({ browser }) => {
      const page = await openScreen(browser, screenOf('portal.provider-record'));
      await expect(page.getByRole('main')).toContainText('Active (A)');
      const facts = await readFacts(page);
      expect(badgeProblems(facts)).toEqual([]);
    });
  });

  test.describe('[MAM-86 AC-6] page structure', () => {
    for (const screen of SCREENS) {
      test(`MAM-86 AC-6 ${screen.id}: one h1, banner, main and navigation landmarks, headers in every table, a visible label on every input`, async ({ browser }) => {
        const page = await openScreen(browser, screen);
        const facts = await readFacts(page);
        note('structure', `${screen.id}: h1 ${JSON.stringify(facts.h1)}, banners ${facts.banners}, mains ${facts.mains}, navigations ${JSON.stringify(facts.navigations)}, tables ${JSON.stringify(facts.tables)}, inputs ${facts.inputs.length}`);
        const problems = structureProblems(facts);
        expect(problems, `${screen.id}: ${problems.join('; ')}`).toEqual([]);
        expect(facts.h1, 'the one h1 is the title of the inventory').toEqual([screen.title]);
      });
    }

    test('MAM-86 AC-6 console.work-queues with an entry in each of the four lists: every list is a table with header cells, one h1, the landmarks', async ({ browser }) => {
      const page = await openPopulatedQueues(browser);
      const facts = await readFacts(page);
      note('structure', `console.work-queues (four lists with entries): h1 ${JSON.stringify(facts.h1)}, tables ${JSON.stringify(facts.tables)}`);
      expect(facts.tables, 'one table for each of the four lists').toHaveLength(4);
      const problems = structureProblems(facts);
      expect(problems, problems.join('; ')).toEqual([]);
    });
  });
}

/** The work queues with an entry in each of the four lists (scripted gateway answers; the seed state has all four lists empty). */
async function openPopulatedQueues(browser: Browser) {
  const page = await tabOf(browser, 'analyst1');
  await page.unrouteAll({ behavior: 'ignoreErrors' });
  await mockApi(page, QUEUES_POPULATED);
  await openAs(page, 'analyst1', '/console/queues', () => page.getByRole('heading', { name: /^Pending approval \(2\)/ }));
  await expect(page.getByRole('heading', { name: /^Failed screening \(1\)/ })).toBeVisible();
  await expect(page.getByRole('heading', { name: /^Revalidation due \(1\)/ })).toBeVisible();
  await expect(page.getByRole('heading', { name: /^Rejected applications \(1\)/ })).toBeVisible();
  await expect(page.getByText(/^Loading/)).toHaveCount(0, { timeout: 30_000 });
  await expect(page.getByText('OIG exclusion list (LEIE)').first()).toBeVisible({ timeout: 30_000 });
  return page;
}
