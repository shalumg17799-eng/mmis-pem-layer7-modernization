// MAM-86 AC-7 (and the "not disabled" half of AC-5): a violation breaks the run. One real page of the served UI (the public directory,
// no sign-in) gets one injected violation; the SAME checker the page scans use (support/axe-gate.ts, expectNoViolations) must
// fail, and its message must name the page and the rule. A second test does it through a real, separate Playwright run and checks
// the exit code and the output, which is what `make e2e` (modern/pem.sh) relies on. Needs only the web UI on :5173 (no sign-in, no
// gateway call), so this file also runs in the default suite.
// AC-6 (the label rule of decision Q-137 A, added 2026-11-21): small pages are built here with page.setContent and read by the SAME
// checker the page scans use (support/structure.ts: readFacts, then structureProblems). Each side of the rule is proved on its own:
// what it accepts (a visible label element; a table input whose name contains the visible header text of its own column) and what it
// still refuses, with a problem that names the input (no table, wrong or empty or hidden header, another column's header, label not shown).
// "Shown" and "hidden" mean what a sighted user sees (correction of 2026-11-21 night, after a review): the element's own style, the
// style of its ancestors (opacity 0, display none, a closed details element, content-visibility hidden) and a box that lies wholly off
// the page (the USWDS class usa-sr-only, top -999em, a transform) are all judged, for the label, the header cell and each part of their text.
// Second correction (2026-11-21 night, after a second review): also judged now are clipping by an ancestor (overflow hidden on a box
// under 2 by 2 pixels, a wrapper that pushes the element out of its box, the clip property), clip-path, filter opacity(0), text whose own
// element has font-size 0 or a colour with alpha 0, and the content of a control (textarea, select, option, input, button) inside a
// label or a header cell, which is not the label's text. Guards keep what a person can see accepted (a wrapper large enough, a scroll
// container, a label with its own text beside a textarea, a partly transparent colour).
import { execFileSync } from 'node:child_process';
import { mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { expect, test, type Page } from '@playwright/test';
import { expectNoViolations } from './support/axe-gate';
import { tabTo } from './support/keyboard';
import { readFacts, structureProblems } from './support/structure';

const SCREEN = 'public.directory';
const WEB_UI = fileURLToPath(new URL('../../', import.meta.url));

async function openDirectory(page: Page): Promise<void> {
  await page.goto('/directory');
  await expect(page.getByRole('heading', { level: 1, name: 'Find a Medicaid provider' })).toBeVisible();
}

/** The one violation: an image with no text alternative (rule image-alt, WCAG 1.1.1). */
async function injectImageWithoutAlternative(page: Page): Promise<void> {
  await page.evaluate(() => {
    const image = document.createElement('img');
    image.src = 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7';
    image.width = 48;
    image.height = 48;
    document.querySelector('main')?.appendChild(image);
  });
}

async function failureOf(check: Promise<unknown>): Promise<Error | null> {
  return check.then(
    () => null,
    (error: unknown) => (error instanceof Error ? error : new Error(String(error))),
  );
}

test.describe('[MAM-86 AC-7] a violation breaks the run', () => {
  test('MAM-86 AC-7 one injected violation on a real page makes the checker fail, naming the page and the rule', async ({ page }) => {
    await openDirectory(page);
    await expectNoViolations(page, SCREEN); // the page is clean first, so the failure below comes from the injection only
    await injectImageWithoutAlternative(page);
    const failure = await failureOf(expectNoViolations(page, SCREEN));
    expect(failure, 'the checker must throw when the page has a violation').not.toBeNull();
    expect(failure?.message, 'the message names the page').toContain(`page "${SCREEN}"`);
    expect(failure?.message, 'the message names the address of the page').toContain('/directory');
    expect(failure?.message, 'the message names the rule').toContain('image-alt');
    expect(failure?.message, 'one violation only').toContain('1 axe violation');
  });

  test('MAM-86 AC-7 a real Playwright run that meets a violation exits non-zero and prints the page and the rule (what make e2e relies on)', async ({ baseURL }) => {
    test.setTimeout(240_000);
    const folder = fileURLToPath(new URL('./test-results/mam86-nested-gate/', new URL('../../', import.meta.url)));
    const specFolder = `${folder}specs/`;
    mkdirSync(specFolder, { recursive: true });
    const axeGate = '../../../e2e/a11y/support/axe-gate'; // relative to test-results/mam86-nested-gate/specs/
    writeFileSync(
      `${specFolder}nested-gate.spec.ts`,
      [
        "import { expect, test } from '@playwright/test';",
        `import { expectNoViolations } from '${axeGate}';`,
        "test('nested: a page with one violation', async ({ page }) => {",
        "  await page.goto('/directory');",
        "  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();",
        '  await page.evaluate(() => {',
        "    const image = document.createElement('img');",
        "    image.src = 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7';",
        "    document.querySelector('main')?.appendChild(image);",
        '  });',
        `  await expectNoViolations(page, '${SCREEN}');`,
        '});',
        '',
      ].join('\n'),
    );
    writeFileSync(
      `${folder}nested.config.ts`,
      [
        "import { defineConfig, devices } from '@playwright/test';",
        'export default defineConfig({',
        "  testDir: './specs',",
        "  outputDir: './artifacts',",
        '  retries: 0,',
        '  workers: 1,',
        "  reporter: [['line']],",
        `  use: { baseURL: '${baseURL ?? 'http://localhost:5173'}', trace: 'off', screenshot: 'off', launchOptions: { args: ['--host-resolver-rules=MAP localhost 127.0.0.1'] } },`,
        "  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],",
        '});',
        '',
      ].join('\n'),
    );
    const cli = createRequire(import.meta.url).resolve('@playwright/test/cli');
    let status: number | null = 0;
    let output = '';
    try {
      output = execFileSync(process.execPath, [cli, 'test', '--config', `${folder}nested.config.ts`], {
        cwd: WEB_UI,
        encoding: 'utf8',
        timeout: 200_000,
        env: { ...process.env, PEM_JOURNEYS: '', NODE_OPTIONS: '--dns-result-order=ipv4first' },
        stdio: ['ignore', 'pipe', 'pipe'],
      });
    } catch (error) {
      const failed = error as { status: number | null; stdout?: string; stderr?: string };
      status = failed.status;
      output = `${failed.stdout ?? ''}\n${failed.stderr ?? ''}`;
    } finally {
      rmSync(folder, { recursive: true, force: true });
    }
    expect(status, 'the run with one violation must fail (non-zero exit status)').not.toBe(0);
    expect(output, 'the run output names the page').toContain(`page "${SCREEN}"`);
    expect(output, 'the run output names the rule').toContain('image-alt');
    expect(output, 'the run reports the failed test').toMatch(/1 failed/);
  });
});

// A group of its own: the trace tool reads the bracket tag of the group in front of the title, so an AC-5 test must not sit in the AC-7 group.
test.describe('[MAM-86 AC-5] the colour-contrast rule is not disabled', () => {
  test('MAM-86 AC-5 the colour-contrast rule is not disabled: injected low-contrast text is reported as color-contrast', async ({ page }) => {
    await openDirectory(page);
    await page.evaluate(() => {
      const text = document.createElement('p');
      text.textContent = 'Pale grey text on white, far below 4.5 to 1';
      text.style.cssText = 'color:#cfcfcf;background:#ffffff;font-size:16px;margin:8px;padding:4px';
      document.querySelector('main')?.prepend(text);
    });
    const failure = await failureOf(expectNoViolations(page, SCREEN));
    expect(failure, 'the checker must throw for text below 4.5 to 1').not.toBeNull();
    expect(failure?.message).toContain('color-contrast');
    expect(failure?.message).toContain(`page "${SCREEN}"`);
  });
});

test.describe('[MAM-86 AC-3] the keyboard helper fails, naming the control, when a control cannot be reached', () => {
  test('MAM-86 AC-3 a control that Tab never reaches fails the step with its name, within the bound; a reachable one is reached', async ({ page }) => {
    await page.setContent(
      '<!doctype html><html lang="en"><head><title>t</title></head><body><main><h1>Form</h1>' +
        '<button id="first">First</button><button id="hidden-from-tab" tabindex="-1">Skipped</button><button id="last">Last</button></main></body></html>',
    );
    const passed = await tabTo(page, page.locator('#last'), 'the Last button');
    expect(passed.join(' | '), 'the focus passed the First button on the way').toContain('First');
    await expect(page.locator('#last')).toBeFocused();
    const failure = await failureOf(tabTo(page, page.locator('#hidden-from-tab'), 'the Skipped button', { max: 6 }));
    expect(failure, 'the helper must throw when the control cannot be reached').not.toBeNull();
    expect(failure?.message).toContain('"the Skipped button" could not be reached with 6');
    expect(failure?.message, 'the message lists the controls the focus passed').toContain('First');
  });
});

// ---------------------------------------------------------------------------------------------------------------------------------
// AC-6, the label rule (Q-137 A): an input is labelled when (a) it has a visible label element, OR (b) it sits in a table cell, the
// header cell of its column is shown and has text, and that text is contained in the input's accessible name.
// ---------------------------------------------------------------------------------------------------------------------------------

// The rule of the class that this app uses to hide text from the eye and keep it for screen readers (modern/web-ui/node_modules/
// @uswds/uswds/dist/css/uswds.css, ".usa-sr-only"): the box is taken out of the flow and moved 999em to the left, off the page.
// It is not display none, not visibility hidden, not opacity 0 and not one pixel in size, so it needs a test of its own.
const USWDS_SR_ONLY = '.usa-sr-only{position:absolute;left:-999em;right:auto}';

/** A page with the landmarks and one h1 around `body`, so that the only problems a case can raise are about its own inputs and tables. */
function pageWith(body: string): string {
  return (
    `<!doctype html><html lang="en"><head><title>Label rule</title><style>${USWDS_SR_ONLY}</style></head><body>` +
    '<header><a href="#main">Skip to main content</a></header><nav aria-label="Primary navigation"><a href="#main">Home</a></nav>' +
    `<main id="main"><h1>Label rule</h1>${body}</main></body></html>`
  );
}

/** What a page scan does: read the facts of the page in the browser, then ask the one checker for the problems. */
async function problemsOf(page: Page, body: string): Promise<string[]> {
  await page.setContent(pageWith(body));
  return structureProblems(await readFacts(page));
}

/** The one problem that names input `id`. Fails the test when there is none, or more than one. */
function problemAbout(problems: string[], id: string): string {
  const named = problems.filter((problem) => problem.startsWith(`input "${id}" `));
  expect(named, `the checker must report exactly one problem that names input "${id}"; all problems: ${JSON.stringify(problems)}`).toHaveLength(1);
  return named[0] ?? '';
}

const NEITHER_A = 'is not labelled for a sighted user: it meets neither (a) a visible label element (';
const NEITHER_B = ') nor (b) a visible column header whose text is in its accessible name (';

/** A table of risk rules: a thead with the headers "Taxonomy prefix" and "Tier" and one body row made of `cells`. */
const rulesTable = (cells: string, head = '<thead><tr><th scope="col">Taxonomy prefix</th><th scope="col">Tier</th></tr></thead>'): string =>
  `<table><caption>Risk rules</caption>${head}<tbody><tr>${cells}</tr></tbody></table>`;
const prefixCell = (name: string): string => `<td><input id="prefix-1" type="text" aria-label="${name}"></td>`;
const tierCell = (name: string): string => `<td><select id="tier-1" aria-label="${name}"><option>Limited (L)</option></select></td>`;

test.describe('[MAM-86 AC-6] the label rule accepts a visible label element, or a table input whose name contains the visible header of its column', () => {
  test('MAM-86 AC-6 (i) an input with a visible label element is accepted: no problem at all on the page', async ({ page }) => {
    const problems = await problemsOf(page, '<label for="npi">NPI</label><input id="npi" type="text">');
    expect(problems).toEqual([]);
  });

  test('MAM-86 AC-6 (i) a label that also holds a usa-sr-only part is accepted: the part a person reads is enough', async ({ page }) => {
    const problems = await problemsOf(page, '<label for="npi">NPI <span class="usa-sr-only">(ten digits)</span></label><input id="npi" type="text">');
    expect(problems).toEqual([]);
  });

  test('MAM-86 AC-6 (i) a label that sets visibility visible inside a wrapper that is visibility hidden is seen, so it is accepted', async ({ page }) => {
    const problems = await problemsOf(page, '<div style="visibility:hidden"><label for="npi" style="visibility:visible">NPI</label></div><input id="npi" type="text">');
    expect(problems, 'visibility is inherited but can be set again; opacity is not inherited and cannot').toEqual([]);
  });

  test('MAM-86 AC-6 (i) a table input with a visible label element is accepted, whatever the header of its column says', async ({ page }) => {
    const problems = await problemsOf(page, rulesTable('<td><label for="note-1">Note for row 1</label><input id="note-1" type="text"></td><td>x</td>'));
    expect(problems).toEqual([]);
  });

  test('MAM-86 AC-6 (ii) a table input whose aria-label contains the visible header text of its own column is accepted (a text field and a select, as on the risk rules page)', async ({ page }) => {
    const problems = await problemsOf(page, rulesTable(prefixCell('Taxonomy prefix, row 1') + tierCell('Tier, row 1')));
    expect(problems).toEqual([]);
  });

  test('MAM-86 AC-6 (ii) the column is counted over a colspan: a header that spans two columns is the header of both', async ({ page }) => {
    const head = '<thead><tr><th scope="col" colspan="2">Contact</th><th scope="col">Tier</th></tr></thead>';
    const cells = '<td><input id="phone-1" type="text" aria-label="Contact phone, row 1"></td><td><input id="email-1" type="text" aria-label="Contact email, row 1"></td>' + tierCell('Tier, row 1');
    const problems = await problemsOf(page, rulesTable(cells, head));
    expect(problems).toEqual([]);
  });

  test('MAM-86 AC-6 (ii) the column is counted over a rowspan: a cell that reaches into the next row keeps its column taken there', async ({ page }) => {
    const table =
      '<table><caption>Rules by group</caption><thead><tr><th scope="col">Group</th><th scope="col">Tier</th></tr></thead><tbody>' +
      '<tr><td rowspan="2">Group one</td><td><select id="tier-1" aria-label="Tier, row 1"><option>Limited (L)</option></select></td></tr>' +
      '<tr><td><select id="tier-2" aria-label="Tier, row 2"><option>Limited (L)</option></select></td></tr></tbody></table>';
    const problems = await problemsOf(page, table);
    expect(problems, 'the second row has one cell, which lies in column 2 because column 1 is still taken by the rowspan').toEqual([]);
  });

  test('MAM-86 AC-6 (ii) the name may come from aria-labelledby: the text of the elements it points to is the accessible name', async ({ page }) => {
    const head = '<thead><tr><th scope="col" id="h-prefix">Taxonomy prefix</th><th scope="col">Tier</th></tr></thead>';
    const cells = '<td><span id="row-1">row 1</span><input id="prefix-1" type="text" aria-labelledby="h-prefix row-1"></td>' + tierCell('Tier, row 1');
    const problems = await problemsOf(page, rulesTable(cells, head));
    expect(problems).toEqual([]);
  });

  test('MAM-86 AC-6 (ii) only the text a person can read in the header counts: a visually hidden suffix of the header need not be in the name', async ({ page }) => {
    const head = '<thead><tr><th scope="col">Taxonomy prefix</th><th scope="col">Tier<span style="display:none"> (required)</span></th></tr></thead>';
    const problems = await problemsOf(page, rulesTable(prefixCell('Taxonomy prefix, row 1') + tierCell('Tier, row 1'), head));
    expect(problems).toEqual([]);
  });

  test('MAM-86 AC-6 (ii) a usa-sr-only suffix of the header is not part of what a person reads, so the name need not contain it', async ({ page }) => {
    const head = '<thead><tr><th scope="col">Taxonomy prefix</th><th scope="col">Tier<span class="usa-sr-only"> (required)</span></th></tr></thead>';
    const problems = await problemsOf(page, rulesTable(prefixCell('Taxonomy prefix, row 1') + tierCell('Tier, row 1'), head));
    expect(problems).toEqual([]);
  });

  test('MAM-86 AC-6 (ii) header text that starts a little left of the page edge but reaches into the page is still seen', async ({ page }) => {
    const head = '<thead><tr><th scope="col"><span style="position:absolute;left:-10px;top:60px">Taxonomy prefix</span></th><th scope="col">Tier</th></tr></thead>';
    const problems = await problemsOf(page, rulesTable(prefixCell('Taxonomy prefix, row 1') + tierCell('Tier, row 1'), head));
    expect(problems, 'only a box wholly outside the page (its right or bottom edge at or beyond the origin) is not seen').toEqual([]);
  });

  test('MAM-86 AC-6 (ii) a table scrolled out of the window (above and to the left of it) is still on the page: its header is seen', async ({ page }) => {
    const spacer = '<div style="width:6000px;height:6000px"></div>';
    await page.setContent(pageWith(rulesTable(prefixCell('Taxonomy prefix, row 1') + tierCell('Tier, row 1')) + spacer));
    await page.evaluate(() => window.scrollTo(2000, 2000));
    expect(await page.evaluate(() => [window.scrollX, window.scrollY]), 'the page really is scrolled past the table').toEqual([2000, 2000]);
    expect(structureProblems(await readFacts(page)), 'the position is judged on the page, not in the window').toEqual([]);
  });

  test('MAM-86 AC-6 (ii) a table without a thead has its header in the first row made only of th', async ({ page }) => {
    const table = `<table><caption>Risk rules</caption><tr><th scope="col">Taxonomy prefix</th><th scope="col">Tier</th></tr><tr>${prefixCell('Taxonomy prefix, row 1')}${tierCell('Tier, row 1')}</tr></table>`;
    const problems = await problemsOf(page, table);
    expect(problems).toEqual([]);
  });

  test('MAM-86 AC-6 (ii) with two header rows the lowest header cell over the column is the one that counts', async ({ page }) => {
    const head = '<thead><tr><th scope="col" colspan="2">Rule</th></tr><tr><th scope="col">Taxonomy prefix</th><th scope="col">Tier</th></tr></thead>';
    const problems = await problemsOf(page, rulesTable(prefixCell('Taxonomy prefix, row 1') + tierCell('Tier, row 1'), head));
    expect(problems).toEqual([]);
  });

  // Guards of the second correction round (2026-11-21 night): the stricter `seen` must still accept everything that a person can see.
  test('MAM-86 AC-6 (i) a label inside a wrapper with overflow hidden that is large enough to show it is accepted', async ({ page }) => {
    const problems = await problemsOf(page, '<div style="width:200px;height:40px;overflow:hidden"><label for="npi">NPI</label></div><input id="npi" type="text">');
    expect(problems).toEqual([]);
  });

  test('MAM-86 AC-6 (i) a label that its wrapper cuts but whose first letters show is accepted (only a clip to under 2 by 2 pixels hides it)', async ({ page }) => {
    const problems = await problemsOf(page, '<div style="width:30px;overflow:hidden;white-space:nowrap"><label for="npi">NPI number</label></div><input id="npi" type="text">');
    expect(problems).toEqual([]);
  });

  test('MAM-86 AC-6 (i) a label beyond the edge of a wrapper that scrolls (overflow auto) can be scrolled to, so it is accepted', async ({ page }) => {
    const wrapper = '<div style="width:80px;height:40px;overflow:auto;white-space:nowrap"><span style="display:inline-block;width:300px">Filler</span><label for="npi">NPI</label></div>';
    const problems = await problemsOf(page, `${wrapper}<input id="npi" type="text">`);
    expect(problems, 'a scroll container is judged by its own size, not by the edge that its content passes').toEqual([]);
  });

  test('MAM-86 AC-6 (i) overflow hidden on an inline wrapper clips nothing, so the label inside it is accepted', async ({ page }) => {
    const problems = await problemsOf(page, '<span style="overflow:hidden"><label for="npi">NPI</label></span><input id="npi" type="text">');
    expect(problems).toEqual([]);
  });

  test('MAM-86 AC-6 (i) overflow hidden on a wrapper that has no box of its own (display contents) clips nothing, so the label inside it is accepted', async ({ page }) => {
    const problems = await problemsOf(page, '<div style="display:contents;overflow:hidden"><label for="npi">NPI</label></div><input id="npi" type="text">');
    expect(problems, 'a display contents element has no box: its overflow does not apply and its empty rectangle must not clip the label').toEqual([]);
  });

  test('MAM-86 AC-6 (i) overflow hidden on the body element belongs to the page, not to the body box: a label below the end of the body box is accepted', async ({ page }) => {
    await page.setContent(pageWith('<div style="height:200px"></div><label for="npi">NPI</label><input id="npi" type="text">'));
    await page.evaluate(() => {
      document.body.style.cssText = 'margin:0;height:30px;overflow:hidden';
    });
    expect(structureProblems(await readFacts(page)), 'the label lies 200 pixels below the end of the 30 pixel body, inside the window').toEqual([]);
  });

  test('MAM-86 AC-6 (i) an absolutely positioned label is not clipped by an overflow hidden wrapper that is not its containing block', async ({ page }) => {
    const wrapper = '<div style="position:relative"><div style="width:1px;height:1px;overflow:hidden"><label for="npi" style="position:absolute;left:0;top:0">NPI</label></div></div>';
    const problems = await problemsOf(page, `${wrapper}<input id="npi" type="text">`);
    expect(problems, 'the label belongs to the relative outer box; the 1 pixel box between them does not clip it').toEqual([]);
  });

  test('MAM-86 AC-6 (i) a label that has its own text and also wraps a textarea is accepted: the text a person reads is "Reason"', async ({ page }) => {
    const problems = await problemsOf(page, '<label for="note">Reason <textarea id="note">Write the reason here</textarea></label>');
    expect(problems).toEqual([]);
  });

  test('MAM-86 AC-6 (i) a label with a partly transparent colour (alpha 0.5) is accepted: only alpha 0 hides the text', async ({ page }) => {
    const problems = await problemsOf(page, '<label for="npi" style="color:rgba(27,27,27,0.5)">NPI</label><input id="npi" type="text">');
    expect(problems).toEqual([]);
  });

  test('MAM-86 AC-6 (i) a label inside a wrapper with filter opacity(0.5) is accepted: only opacity 0 hides it', async ({ page }) => {
    const problems = await problemsOf(page, '<div style="filter:opacity(0.5)"><label for="npi">NPI</label></div><input id="npi" type="text">');
    expect(problems).toEqual([]);
  });

  test('MAM-86 AC-6 (i) a label whose text sits in an element with a normal font size inside an element with font-size 0 is accepted: the element that holds the text is judged', async ({ page }) => {
    const outer = '<span style="display:inline-block;width:60px;height:20px;font-size:0"><span style="font-size:16px">NPI</span></span>';
    const problems = await problemsOf(page, `<label for="npi">${outer}</label><input id="npi" type="text">`);
    expect(problems).toEqual([]);
  });

  test('MAM-86 AC-6 (ii) a header with a normal colour is accepted', async ({ page }) => {
    const head = '<thead><tr><th scope="col" style="color:#1b1b1b">Taxonomy prefix</th><th scope="col" style="color:rgba(27,27,27,0.7)">Tier</th></tr></thead>';
    const problems = await problemsOf(page, rulesTable(prefixCell('Taxonomy prefix, row 1') + tierCell('Tier, row 1'), head));
    expect(problems).toEqual([]);
  });

  test('MAM-86 AC-6 (ii) a table inside a wrapper with overflow hidden that is large enough: its header is seen', async ({ page }) => {
    const problems = await problemsOf(page, `<div style="width:700px;height:200px;overflow:hidden">${rulesTable(prefixCell('Taxonomy prefix, row 1') + tierCell('Tier, row 1'))}</div>`);
    expect(problems).toEqual([]);
  });

  test('MAM-86 AC-6 (ii) a table wider than its scrolling wrapper (overflow-x auto): the header of the column beyond the edge is seen, as it can be scrolled to', async ({ page }) => {
    const problems = await problemsOf(page, `<div style="width:150px;overflow-x:auto">${rulesTable(prefixCell('Taxonomy prefix, row 1') + tierCell('Tier, row 1'))}</div>`);
    expect(problems).toEqual([]);
  });
});

test.describe('[MAM-86 AC-6] the label rule still refuses an input with no visible label, and the problem names the input and says what each case misses', () => {
  test('MAM-86 AC-6 (iii) an input outside a table that is named only by aria-label is refused', async ({ page }) => {
    const message = problemAbout(await problemsOf(page, '<input id="search" type="text" aria-label="Search providers">'), 'search');
    expect(message).toContain(`${NEITHER_A}it is named only by aria-label "Search providers"`);
    expect(message).toContain(`${NEITHER_B}it is not in a table cell, so no column header can label it)`);
  });

  test('MAM-86 AC-6 (iii) an input beside a table is refused even when its name equals a header of that table (only a cell of the table counts)', async ({ page }) => {
    const table = '<table><caption>Providers</caption><thead><tr><th scope="col">Search providers</th></tr></thead><tbody><tr><td>TESTPROV ALPHA</td></tr></tbody></table>';
    const message = problemAbout(await problemsOf(page, `${table}<input id="search" type="text" aria-label="Search providers">`), 'search');
    expect(message).toContain('it is not in a table cell');
  });

  test('MAM-86 AC-6 (iv) a table input whose aria-label does not contain the header text of its column is refused; the other input of the row, which does, is not', async ({ page }) => {
    const problems = await problemsOf(page, rulesTable(prefixCell('Taxonomy prefix, row 1') + tierCell('Quantity, row 1')));
    const message = problemAbout(problems, 'tier-1');
    expect(message).toContain(`${NEITHER_A}it is named only by aria-label "Quantity, row 1"`);
    expect(message).toContain('its accessible name "Quantity, row 1" does not contain the visible header text "Tier" of its column');
    expect(problems, 'only the one input is reported').toHaveLength(1);
  });

  test('MAM-86 AC-6 (iv) the header text is matched with its case kept: "tier, row 1" does not contain "Tier"', async ({ page }) => {
    const message = problemAbout(await problemsOf(page, rulesTable(prefixCell('Taxonomy prefix, row 1') + tierCell('tier, row 1'))), 'tier-1');
    expect(message).toContain('its accessible name "tier, row 1" does not contain the visible header text "Tier" of its column');
  });

  test('MAM-86 AC-6 (iv) a table input with no name at all is refused', async ({ page }) => {
    const message = problemAbout(await problemsOf(page, rulesTable('<td><input id="prefix-1" type="text"></td>' + tierCell('Tier, row 1'))), 'prefix-1');
    expect(message).toContain(`${NEITHER_A}it has no label element and no name`);
    expect(message).toContain('it has no accessible name, so it does not contain the visible header text "Taxonomy prefix" of its column');
  });

  test('MAM-86 AC-6 (iv) aria-labelledby comes before aria-label: an aria-label that holds the header text does not help when the name is taken from elsewhere', async ({ page }) => {
    const cells = '<td><span id="other">Something else</span><input id="prefix-1" type="text" aria-labelledby="other" aria-label="Taxonomy prefix, row 1"></td>' + tierCell('Tier, row 1');
    const message = problemAbout(await problemsOf(page, rulesTable(cells)), 'prefix-1');
    expect(message).toContain('its accessible name "Something else" does not contain the visible header text "Taxonomy prefix" of its column');
  });

  test('MAM-86 AC-6 (iv) an input in a column that has no header cell is refused (more cells in the row than header cells)', async ({ page }) => {
    const table = `<table><caption>Risk rules</caption><thead><tr><th scope="col">Tier</th></tr></thead><tbody><tr><td>Limited</td>${prefixCell('Tier, row 1')}</tr></tbody></table>`;
    const message = problemAbout(await problemsOf(page, table), 'prefix-1');
    expect(message).toContain(`${NEITHER_B}its table has no header cell over its column)`);
  });

  test('MAM-86 AC-6 (v) a table input under an empty header cell is refused, even when its name is a full sentence', async ({ page }) => {
    const head = '<thead><tr><th scope="col">  </th><th scope="col">Tier</th></tr></thead>';
    const message = problemAbout(await problemsOf(page, rulesTable(prefixCell('Taxonomy prefix, row 1') + tierCell('Tier, row 1'), head)), 'prefix-1');
    expect(message).toContain('the header cell of its column has no visible text');
  });

  test('MAM-86 AC-6 (v) a table input under a header cell whose only text is hidden inside it is refused', async ({ page }) => {
    const head = '<thead><tr><th scope="col"><span style="display:none">Taxonomy prefix</span></th><th scope="col">Tier</th></tr></thead>';
    const message = problemAbout(await problemsOf(page, rulesTable(prefixCell('Taxonomy prefix, row 1') + tierCell('Tier, row 1'), head)), 'prefix-1');
    expect(message).toContain('the header cell of its column has no visible text');
  });

  for (const [how, style] of [
    ['display none', 'display:none'],
    ['visibility hidden', 'visibility:hidden'],
    ['opacity 0', 'opacity:0'],
    ['visually hidden (one pixel, clipped)', 'position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0,0,0,0)'],
  ] as const) {
    test(`MAM-86 AC-6 (v) a table input under a hidden header cell is refused, though the header text is in its name: header with ${how}`, async ({ page }) => {
      const head = `<thead><tr><th scope="col" style="${style}">Taxonomy prefix</th><th scope="col">Tier</th></tr></thead>`;
      const message = problemAbout(await problemsOf(page, rulesTable(prefixCell('Taxonomy prefix, row 1') + tierCell('Tier, row 1'), head)), 'prefix-1');
      expect(message).toContain('the header cell of its column ("Taxonomy prefix") is not shown');
    });
  }

  // The hiding methods that the first version of the check did not see, because it read only the style of the element itself:
  // the USWDS class that moves text off the page, an opacity that sits on an ancestor, a box above the page.
  test('MAM-86 AC-6 (v) a table input under a header whose only text is in a usa-sr-only element (moved off the page) is refused', async ({ page }) => {
    const head = '<thead><tr><th scope="col"><span class="usa-sr-only">Taxonomy prefix</span></th><th scope="col">Tier</th></tr></thead>';
    const message = problemAbout(await problemsOf(page, rulesTable(prefixCell('Taxonomy prefix, row 1') + tierCell('Tier, row 1'), head)), 'prefix-1');
    expect(message).toContain('the header cell of its column has no visible text');
  });

  test('MAM-86 AC-6 (v) a table input under a header whose only text is moved above the page (top -999em) is refused', async ({ page }) => {
    const head = '<thead><tr><th scope="col"><span style="position:absolute;top:-999em;left:20px">Taxonomy prefix</span></th><th scope="col">Tier</th></tr></thead>';
    const message = problemAbout(await problemsOf(page, rulesTable(prefixCell('Taxonomy prefix, row 1') + tierCell('Tier, row 1'), head)), 'prefix-1');
    expect(message).toContain('the header cell of its column has no visible text');
  });

  test('MAM-86 AC-6 (v) a table input under a header whose text sits in a wrapper that is clipped to one pixel is refused', async ({ page }) => {
    const wrapper = '<span style="position:absolute;width:1px;height:1px;overflow:hidden"><span>Taxonomy prefix</span></span>';
    const head = `<thead><tr><th scope="col">${wrapper}</th><th scope="col">Tier</th></tr></thead>`;
    const message = problemAbout(await problemsOf(page, rulesTable(prefixCell('Taxonomy prefix, row 1') + tierCell('Tier, row 1'), head)), 'prefix-1');
    expect(message).toContain('the header cell of its column has no visible text');
  });

  test('MAM-86 AC-6 (v) a table input under a header cell whose opacity 0 sits on the thead (opacity is not inherited, but nobody sees the cell) is refused', async ({ page }) => {
    const head = '<thead style="opacity:0"><tr><th scope="col">Taxonomy prefix</th><th scope="col">Tier</th></tr></thead>';
    const problems = await problemsOf(page, rulesTable(prefixCell('Taxonomy prefix, row 1') + tierCell('Tier, row 1'), head));
    expect(problemAbout(problems, 'prefix-1')).toContain('the header cell of its column ("Taxonomy prefix") is not shown');
    expect(problemAbout(problems, 'tier-1'), 'the other column is under the same thead').toContain('the header cell of its column ("Tier") is not shown');
  });

  test('MAM-86 AC-6 (v) a table input under a header cell inside a table row with opacity 0 is refused', async ({ page }) => {
    const head = '<thead><tr style="opacity:0"><th scope="col">Taxonomy prefix</th><th scope="col">Tier</th></tr></thead>';
    const message = problemAbout(await problemsOf(page, rulesTable(prefixCell('Taxonomy prefix, row 1') + tierCell('Tier, row 1'), head)), 'prefix-1');
    expect(message).toContain('the header cell of its column ("Taxonomy prefix") is not shown');
  });

  // Second correction round (2026-11-21 night): hiding methods that a second review found the check did not see. The header text is in
  // the name of the input every time, so that only the hiding is what is judged.
  const named = prefixCell('Taxonomy prefix, row 1') + tierCell('Tier, row 1');
  /** A thead with the header cell of the "Taxonomy prefix" column as given, then the plain "Tier" header; `style` goes on the thead. */
  const theadOf = (prefixHeader: string, style = ''): string => `<thead style="${style}"><tr>${prefixHeader}<th scope="col">Tier</th></tr></thead>`;
  const plainPrefixHeader = '<th scope="col">Taxonomy prefix</th>';

  for (const [how, head] of [
    ['clip-path inset(50%) on the header cell', theadOf('<th scope="col" style="clip-path:inset(50%)">Taxonomy prefix</th>')],
    ['clip-path inset(50%) on the thead (an ancestor of the header cell)', theadOf(plainPrefixHeader, 'clip-path:inset(50%)')],
    ['filter opacity(0) on the header cell', theadOf('<th scope="col" style="filter:opacity(0)">Taxonomy prefix</th>')],
    ['filter opacity(0) on the thead (an ancestor of the header cell)', theadOf(plainPrefixHeader, 'filter:opacity(0)')],
  ] as const) {
    test(`MAM-86 AC-6 (v) a table input under a header cell that nobody sees is refused, though the header text is in its name: header with ${how}`, async ({ page }) => {
      const message = problemAbout(await problemsOf(page, rulesTable(named, head)), 'prefix-1');
      expect(message).toContain('the header cell of its column ("Taxonomy prefix") is not shown');
    });
  }

  // The whole table sits in a wrapper that clips it away: the header cell has a box of its own, the wrapper shows none of it.
  for (const [how, style] of [
    ['a wrapper one pixel wide and high with overflow hidden', 'position:absolute;width:1px;height:1px;overflow:hidden'],
    ['a wrapper of height 0 with overflow hidden', 'height:0;overflow:hidden'],
    ['a wrapper of width 0 with overflow hidden', 'width:0;overflow:hidden'],
    ['a wrapper with the clip property at rect(0,0,0,0)', 'position:absolute;clip:rect(0,0,0,0)'],
    ['a wrapper with clip-path inset(50%)', 'clip-path:inset(50%)'],
  ] as const) {
    test(`MAM-86 AC-6 (v) a table input under a header cell whose table is clipped away by its wrapper is refused: ${how}`, async ({ page }) => {
      const message = problemAbout(await problemsOf(page, `<div style="${style}">${rulesTable(named)}</div>`), 'prefix-1');
      expect(message).toContain('the header cell of its column ("Taxonomy prefix") is not shown');
    });
  }

  // The header cell is seen but nobody can read the text in it.
  for (const [how, cell] of [
    ['the colour transparent on the header cell', '<th scope="col" style="color:transparent">Taxonomy prefix</th>'],
    ['a colour with alpha 0 on the header cell', '<th scope="col" style="color:rgba(20,30,40,0)">Taxonomy prefix</th>'],
    ['font-size 0 on the header cell (its padding keeps its box)', '<th scope="col" style="font-size:0;padding:8px">Taxonomy prefix</th>'],
    ['the text in a span with clip-path inset(50%)', '<th scope="col"><span style="clip-path:inset(50%)">Taxonomy prefix</span></th>'],
    ['the text in a span with filter opacity(0)', '<th scope="col"><span style="filter:opacity(0)">Taxonomy prefix</span></th>'],
    ['the text in a span with the colour transparent', '<th scope="col"><span style="color:transparent">Taxonomy prefix</span></th>'],
    ['the text in a box of its own (inline-block) with font-size 0', '<th scope="col"><span style="display:inline-block;width:80px;height:20px;font-size:0">Taxonomy prefix</span></th>'],
    [
      'the text pushed out of the box that clips it (a wrapper 100 by 20 pixels with overflow hidden, the text moved 300 pixels to the right)',
      '<th scope="col"><div style="width:100px;height:20px;overflow:hidden"><span style="position:relative;left:300px">Taxonomy prefix</span></div></th>',
    ],
  ] as const) {
    test(`MAM-86 AC-6 (v) a table input under a header cell whose text nobody can read is refused: ${how}`, async ({ page }) => {
      const message = problemAbout(await problemsOf(page, rulesTable(named, theadOf(cell))), 'prefix-1');
      expect(message).toContain('the header cell of its column has no visible text');
    });
  }

  test('MAM-86 AC-6 (vi) a table input whose name holds only the header text of ANOTHER column is refused (the two inputs are swapped here)', async ({ page }) => {
    const problems = await problemsOf(page, rulesTable(prefixCell('Tier, row 1') + tierCell('Taxonomy prefix, row 1')));
    expect(problemAbout(problems, 'prefix-1')).toContain('its accessible name "Tier, row 1" does not contain the visible header text "Taxonomy prefix" of its column');
    expect(problemAbout(problems, 'tier-1')).toContain('its accessible name "Taxonomy prefix, row 1" does not contain the visible header text "Tier" of its column');
  });

  test('MAM-86 AC-6 (vi) the other column is found over a colspan too: the name holds the header of the spanning cell, the column is under "Tier"', async ({ page }) => {
    const head = '<thead><tr><th scope="col" colspan="2">Contact</th><th scope="col">Tier</th></tr></thead>';
    const cells = '<td>a</td><td>b</td>' + tierCell('Contact, row 1');
    const message = problemAbout(await problemsOf(page, rulesTable(cells, head)), 'tier-1');
    expect(message).toContain('its accessible name "Contact, row 1" does not contain the visible header text "Tier" of its column');
  });

  for (const [how, style] of [
    ['display none', 'display:none'],
    ['visibility hidden', 'visibility:hidden'],
    ['opacity 0', 'opacity:0'],
    ['visually hidden (one pixel, clipped)', 'position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0,0,0,0)'],
  ] as const) {
    test(`MAM-86 AC-6 (vii) an input whose label element is not shown is refused: label with ${how}`, async ({ page }) => {
      const body = `<label for="npi" style="${style}">NPI</label><input id="npi" type="text">`;
      const message = problemAbout(await problemsOf(page, body), 'npi');
      expect(message).toContain(`${NEITHER_A}its label element "NPI" is not shown)`);
      expect(message).toContain('it is not in a table cell');
    });
  }

  // The label that the first version of the check accepted, because it read only the style of the label element itself.
  for (const [how, body] of [
    ['the usa-sr-only class on the label (moved off the page)', '<label for="npi" class="usa-sr-only">NPI</label><input id="npi" type="text">'],
    // The label keeps a box of its own here (inline-block, 60 by 20 pixels), so that it is the TEXT inside it that is not seen, not an empty box.
    ['its only text in a usa-sr-only element inside the label', '<label for="npi" style="display:inline-block;width:60px;height:20px"><span class="usa-sr-only">NPI</span></label><input id="npi" type="text">'],
    ['the label inside a wrapper with opacity 0 (opacity is not inherited, but nobody sees the label)', '<div style="opacity:0"><label for="npi">NPI</label></div><input id="npi" type="text">'],
    ['the label inside a wrapper with opacity 0, two levels up', '<div style="opacity:0"><p><label for="npi">NPI</label></p></div><input id="npi" type="text">'],
    ['the label moved above the page (top -999em)', '<label for="npi" style="position:absolute;top:-999em;left:20px">NPI</label><input id="npi" type="text">'],
    ['the label moved out of the page by a transform (translateX -5000px)', '<label for="npi" style="display:inline-block;transform:translateX(-5000px)">NPI</label><input id="npi" type="text">'],
    // These two keep a real box (about 26 by 17 pixels, read in Chromium) while the browser does not render their content.
    ['the label inside a closed details element (an accordion that is not open)', '<details><summary>More</summary><label for="npi">NPI</label></details><input id="npi" type="text">'],
    ['the label inside a wrapper with content-visibility hidden', '<div style="content-visibility:hidden"><label for="npi">NPI</label></div><input id="npi" type="text">'],
    ['the text of the label in a wrapper clipped to one pixel', '<label for="npi" style="display:inline-block;width:60px;height:20px"><span style="position:absolute;width:1px;height:1px;overflow:hidden"><span>NPI</span></span></label><input id="npi" type="text">'],
  ] as const) {
    test(`MAM-86 AC-6 (vii) an input whose label is not seen by a person is refused: ${how}`, async ({ page }) => {
      const message = problemAbout(await problemsOf(page, body), 'npi');
      expect(message).toContain(`${NEITHER_A}its label element "NPI" is not shown)`);
      expect(message).toContain('it is not in a table cell');
    });
  }

  // Second correction round (2026-11-21 night): the labels that a second review found the check did not refuse. Each case is
  // [how, markup, id of the input, the label text that the problem quotes].
  const BOX = 'display:inline-block;width:60px;height:20px'; // a box of its own, so that it is the clipping or the text that hides the label
  for (const [how, body, id, text] of [
    ['the label inside a wrapper one pixel wide and high with overflow hidden', '<div style="position:absolute;width:1px;height:1px;overflow:hidden"><label for="npi">NPI</label></div><input id="npi" type="text">', 'npi', 'NPI'],
    ['the label inside a wrapper of height 0 with overflow hidden', '<div style="height:0;overflow:hidden"><label for="npi">NPI</label></div><input id="npi" type="text">', 'npi', 'NPI'],
    ['the label inside a wrapper of width 0 with overflow hidden', '<div style="width:0;overflow:hidden"><label for="npi">NPI</label></div><input id="npi" type="text">', 'npi', 'NPI'],
    ['the label inside a wrapper one pixel wide and high with overflow auto (a scroll container too small to show anything)', '<div style="position:absolute;width:1px;height:1px;overflow:auto"><label for="npi">NPI</label></div><input id="npi" type="text">', 'npi', 'NPI'],
    ['the label inside a wrapper one pixel wide and 100 high with overflow auto (a scroll container with no width)', '<div style="width:1px;height:100px;overflow:auto"><label for="npi">NPI</label></div><input id="npi" type="text">', 'npi', 'NPI'],
    ['the label inside a wrapper 200 wide and one pixel high with overflow auto (a scroll container with no height)', '<div style="width:200px;height:1px;overflow:auto"><label for="npi">NPI</label></div><input id="npi" type="text">', 'npi', 'NPI'],
    ['the label two levels inside a wrapper one pixel wide and high with overflow hidden', '<div style="position:absolute;width:1px;height:1px;overflow:hidden"><p><label for="npi">NPI</label></p></div><input id="npi" type="text">', 'npi', 'NPI'],
    [
      'the label pushed out of the box that clips it (a wrapper 100 by 20 pixels with overflow hidden, the label moved 300 pixels to the right)',
      '<div style="width:100px;height:20px;overflow:hidden"><label for="npi" style="position:relative;left:300px">NPI</label></div><input id="npi" type="text">',
      'npi',
      'NPI',
    ],
    ['the label inside a wrapper with the clip property at rect(0,0,0,0)', '<div style="position:absolute;clip:rect(0,0,0,0)"><label for="npi">NPI</label></div><input id="npi" type="text">', 'npi', 'NPI'],
    ['clip-path inset(50%) on the label', '<label for="npi" style="clip-path:inset(50%)">NPI</label><input id="npi" type="text">', 'npi', 'NPI'],
    ['clip-path inset(50%) on a wrapper of the label', '<div style="clip-path:inset(50%)"><label for="npi">NPI</label></div><input id="npi" type="text">', 'npi', 'NPI'],
    ['filter opacity(0) on the label', '<label for="npi" style="filter:opacity(0)">NPI</label><input id="npi" type="text">', 'npi', 'NPI'],
    ['filter opacity(0) on a wrapper of the label', '<div style="filter:opacity(0)"><label for="npi">NPI</label></div><input id="npi" type="text">', 'npi', 'NPI'],
    ['the colour transparent on the label', '<label for="npi" style="color:transparent">NPI</label><input id="npi" type="text">', 'npi', 'NPI'],
    ['a colour with alpha 0 on the label', '<label for="npi" style="color:rgba(20,30,40,0)">NPI</label><input id="npi" type="text">', 'npi', 'NPI'],
    ['font-size 0 on the label (it has a box of its own)', `<label for="npi" style="${BOX};font-size:0">NPI</label><input id="npi" type="text">`, 'npi', 'NPI'],
    [
      'its only text in a box of its own (inline-block) with font-size 0 inside the label',
      `<label for="npi" style="${BOX}"><span style="display:inline-block;width:40px;height:10px;font-size:0">NPI</span></label><input id="npi" type="text">`,
      'npi',
      'NPI',
    ],
    ['its only text in a span with the colour transparent inside the label', '<label for="npi"><span style="color:transparent">NPI</span></label><input id="npi" type="text">', 'npi', 'NPI'],
    ['its only text in a span with clip-path inset(50%) inside the label', '<label for="npi"><span style="clip-path:inset(50%)">NPI</span></label><input id="npi" type="text">', 'npi', 'NPI'],
    // A label whose only text is the content of a control inside it: what a person reads there is the control, not a label.
    ['no text of its own and a textarea with content inside it', '<label><textarea id="note">Write the reason here</textarea></label>', 'note', 'Write the reason here'],
    ['no text of its own and a select (a list box of three rows) inside it', '<label><select id="tier" size="3"><option>Limited (L)</option></select></label>', 'tier', 'Limited (L)'],
    ['no text of its own and a select (a drop-down) inside it', '<label><select id="tier"><option>Limited (L)</option></select></label>', 'tier', 'Limited (L)'],
    ['its only text in a button inside the label', '<label for="npi"><button type="button">NPI</button></label><input id="npi" type="text">', 'npi', 'NPI'],
  ] as const) {
    test(`MAM-86 AC-6 (vii) an input whose label nobody sees or reads is refused: ${how}`, async ({ page }) => {
      const message = problemAbout(await problemsOf(page, body), id);
      expect(message).toContain(`${NEITHER_A}its label element "${text}" is not shown)`);
      expect(message).toContain('it is not in a table cell');
    });
  }
});
