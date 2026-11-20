// MAM-86 AC-7 (and the "not disabled" half of AC-5): a violation breaks the run. One real page of the served UI (the public directory,
// no sign-in) gets one injected violation; the SAME checker the page scans use (support/axe-gate.ts, expectNoViolations) must
// fail, and its message must name the page and the rule. A second test does it through a real, separate Playwright run and checks
// the exit code and the output, which is what `make e2e` (modern/pem.sh) relies on. Needs only the web UI on :5173 (no sign-in, no
// gateway call), so this file also runs in the default suite.
import { execFileSync } from 'node:child_process';
import { mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { expect, test, type Page } from '@playwright/test';
import { expectNoViolations } from './support/axe-gate';
import { tabTo } from './support/keyboard';

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
