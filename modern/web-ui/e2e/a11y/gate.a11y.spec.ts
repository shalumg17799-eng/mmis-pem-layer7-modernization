// MAM-86 AC-7 (and the "not disabled" half of AC-5): a violation breaks the run. One real page of the served UI (the public directory,
// no sign-in) gets one injected violation; the SAME checker the page scans use (support/axe-gate.ts, expectNoViolations) must
// fail, and its message must name the page and the rule. A second test does it through a real, separate Playwright run and checks
// the exit code and the output, which is what `make e2e` (modern/pem.sh) relies on. Needs only the web UI on :5173 (no sign-in, no
// gateway call), so this file also runs in the default suite.
// AC-6 (the label rule of decision Q-137 A, added 2026-11-21): small pages are built here with page.setContent and read by the SAME
// checker the page scans use (support/structure.ts: readFacts, then structureProblems). Each side of the rule is proved on its own:
// what it accepts (a visible label element; a table input whose name contains the visible header text of its own column) and what it
// still refuses, with a problem that names the input (no table, wrong or empty or hidden header, another column's header, label not shown).
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

/** A page with the landmarks and one h1 around `body`, so that the only problems a case can raise are about its own inputs and tables. */
function pageWith(body: string): string {
  return (
    '<!doctype html><html lang="en"><head><title>Label rule</title></head><body>' +
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
});
