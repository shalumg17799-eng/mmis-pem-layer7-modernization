// MAM-86 repository checks, no browser and no stack needed (they run in the default suite):
//   AC-7  no axe rule is disabled, excluded, narrowed or switched off anywhere under modern/web-ui/e2e unless a written justification
//         sits next to the call: a comment that starts with the marker "axe-justification:" and says why, on the line itself or on
//         one of the three lines above it. The checker is itself tested on samples (it must flag an unjustified one).
//   AC-3  the keyboard-only journeys (keyboard-journeys.spec.ts and support/keyboard.ts) contain no mouse call, no fill, no check,
//         no selectOption, no dispatchEvent and no programmatic focus; the only allowed exception is choosing a file for an upload.
//   rule  the story's own files contain no skipped, fixme or only test.
// The patterns are put together from pieces, so that this file does not contain the calls it looks for.
import { readdirSync, readFileSync } from 'node:fs';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { expect, test } from '@playwright/test';

const E2E = fileURLToPath(new URL('..', import.meta.url));
const MARKER = /(\/\/|\*)\s*axe-justification:\s*\S.{29,}/;

const call = (name: string) => new RegExp(`\\.${name}\\s*\\(`);
/** Calls and options that switch a rule off, leave a part of the page out, or narrow a scan. */
const SUPPRESSIONS: Array<[string, RegExp]> = [
  ['disable' + 'Rules', call('disable' + 'Rules')],
  ['ex' + 'clude', call('ex' + 'clude')],
  ['in' + 'clude', call('in' + 'clude')],
  ['with' + 'Rules', call('with' + 'Rules')],
  ['with' + 'Tags', call('with' + 'Tags')],
  ['con' + 'figure', new RegExp(String.raw`(?<!describe)\.con` + String.raw`figure\s*\(`)],
  ['run' + 'Only', /\brunOnly\b/],
  ['rule switched off by option', /enabled\s*:\s*false/],
];

interface Finding {
  file: string;
  line: number;
  what: string;
  text: string;
}

const isComment = (line: string): boolean => /^\s*(\/\/|\*|\/\*)/.test(line);

/** Every unjustified suppression in `source` (a comment line is never itself a suppression). */
function suppressionsIn(file: string, source: string): Finding[] {
  const lines = source.split(/\r?\n/);
  const findings: Finding[] = [];
  lines.forEach((text, index) => {
    if (isComment(text)) return;
    for (const [what, pattern] of SUPPRESSIONS) {
      if (!pattern.test(text)) continue;
      const near = lines.slice(Math.max(0, index - 3), index + 1);
      if (near.some((candidate) => MARKER.test(candidate))) continue;
      findings.push({ file, line: index + 1, what, text: text.trim() });
    }
  });
  return findings;
}

function filesUnder(folder: string): string[] {
  return readdirSync(folder, { withFileTypes: true }).flatMap((entry) => {
    const path = join(folder, entry.name);
    if (entry.isDirectory()) return entry.name === 'node_modules' ? [] : filesUnder(path);
    return entry.name.endsWith('.ts') ? [path] : [];
  });
}

const where = (finding: Finding) => `${finding.file}:${finding.line} uses ${finding.what} without a written justification: ${finding.text}`;

test.describe('[MAM-86 AC-7] no axe violation is suppressed without a written justification', () => {
  test('MAM-86 AC-7 the checker flags a disabled rule that has no justification', () => {
    const sample = [`const results = await new Axe${'Builder'}({ page })`, `  .${'disable' + 'Rules'}(['color-contrast'])`, '  .analyze();'].join('\n');
    const found = suppressionsIn('sample.ts', sample);
    expect(found.map(where)).toEqual([`sample.ts:2 uses ${'disable' + 'Rules'} without a written justification: .${'disable' + 'Rules'}(['color-contrast'])`]);
  });

  test('MAM-86 AC-7 the checker flags an excluded part of the page and a rule switched off by option', () => {
    const excluded = suppressionsIn('sample.ts', `await new Axe${'Builder'}({ page }).${'ex' + 'clude'}('#map').analyze();`);
    const off = suppressionsIn('sample.ts', `axe.run(document, { rules: { 'color-contrast': { ${'en' + 'abled'}: false } } });`);
    expect(excluded.map((finding) => finding.what)).toEqual(['ex' + 'clude']);
    expect(off.map((finding) => finding.what)).toEqual(['rule switched off by option']);
  });

  test('MAM-86 AC-7 the checker accepts a suppression with a written reason next to it and rejects an empty one', () => {
    const suppressed = `.${'disable' + 'Rules'}(['region'])`;
    const reason = '// axe-justification: the embedded third-party widget is outside this repository and is tracked in an issue';
    expect(suppressionsIn('sample.ts', `${reason}\n${suppressed}`)).toEqual([]);
    expect(suppressionsIn('sample.ts', `${suppressed} ${reason}`)).toEqual([]);
    expect(suppressionsIn('sample.ts', `// axe-justification:\n${suppressed}`), 'a marker with no reason does not count').toHaveLength(1);
    expect(suppressionsIn('sample.ts', `// axe-justification: too short\n${suppressed}`), 'a one-word reason does not count').toHaveLength(1);
    expect(suppressionsIn('sample.ts', `${reason}\n\n\n\n${suppressed}`), 'a reason four lines away is not next to the call').toHaveLength(1);
  });

  test('MAM-86 AC-7 no file under modern/web-ui/e2e disables, excludes or narrows an axe rule without a justification', () => {
    const files = filesUnder(E2E);
    expect(files.length, 'the e2e folder was read').toBeGreaterThan(20);
    const findings = files.flatMap((file) => suppressionsIn(relative(E2E, file).replaceAll('\\', '/'), readFileSync(file, 'utf8')));
    expect(findings.map(where)).toEqual([]);
  });

  test('MAM-86 AC-7 every scan of this story goes through the one checker, whose only option is the list of tags of AC-1', () => {
    const gate = readFileSync(join(E2E, 'a11y/support/axe-gate.ts'), 'utf8');
    const tagLists = gate.match(/with[T]ags\([^)]*\)/g) ?? [];
    expect(tagLists, 'one scan call, restricted to the AC-1 tags and nothing else').toEqual(['with' + 'Tags(AXE_TAGS)']);
    expect(gate).toContain("['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']");
    for (const file of filesUnder(join(E2E, 'a11y')).filter((path) => path.endsWith('.spec.ts'))) {
      const text = readFileSync(file, 'utf8');
      expect(text.includes('new Axe' + 'Builder'), `${relative(E2E, file)} scans only through support/axe-gate.ts`).toBe(false);
    }
  });
});

const KEYBOARD_ONLY_FILES = ['a11y/keyboard-journeys.spec.ts', 'a11y/support/keyboard.ts'];
/** Calls that a mouse, a pointer or a script would use instead of the keyboard. `keyboard.press` and `keyboard.type` are the way in. */
const NOT_KEYBOARD: Array<[string, RegExp]> = [
  ['click', call('cl' + 'ick')],
  ['dblclick', call('dbl' + 'click')],
  ['tap', call('t' + 'ap')],
  ['hover', call('ho' + 'ver')],
  ['mouse', /\.mouse\b/],
  ['fill', call('fi' + 'll')],
  ['clear', call('cle' + 'ar')],
  ['check', call('che' + 'ck')],
  ['uncheck', call('unche' + 'ck')],
  ['setChecked', call('setChe' + 'cked')],
  ['selectOption', call('select' + 'Option')],
  ['dispatchEvent', call('dispatch' + 'Event')],
  ['focus', call('fo' + 'cus')],
  ['blur', call('bl' + 'ur')],
  ['selectText', call('select' + 'Text')],
  ['dragTo', call('drag' + 'To')],
  ['press on an element (it focuses the element first)', /(?<!keyboard)\.press\s*\(/],
  ['type on an element (it focuses the element first)', /(?<!keyboard)\.type\s*\(/],
  ['pressSequentially', call('pressSequen' + 'tially')],
  ['setInputFiles', call('setInput' + 'Files')],
];

/** The calls of `source` that are not keyboard input. setInputFiles is allowed only on a line that says it is the file-dialog exception. */
function mouseCallsIn(file: string, source: string): string[] {
  const out: string[] = [];
  source.split(/\r?\n/).forEach((text, index) => {
    if (isComment(text)) return;
    for (const [what, pattern] of NOT_KEYBOARD) {
      if (pattern.test(text) && !(what === 'setInputFiles' && /file-dialog-exception:/.test(text))) out.push(`${file}:${index + 1} ${what}: ${text.trim()}`);
    }
  });
  return out;
}

test.describe('[MAM-86 AC-3] the keyboard journeys use only the keyboard', () => {
  test('MAM-86 AC-3 the checker finds a mouse call, a fill and a programmatic focus in a sample', () => {
    const sample = [`await page.getByRole('link').${'cl' + 'ick'}();`, `await field.${'fi' + 'll'}('x');`, `element.${'fo' + 'cus'}();`, "await page.keyboard.press('Tab');"].join('\n');
    expect(mouseCallsIn('sample.ts', sample).map((entry) => entry.split(' ')[1]?.replace(':', ''))).toEqual(['click', 'fill', 'focus']);
  });

  test('MAM-86 AC-3 a file chosen for an upload is the only call that may be made without the keyboard, and only when it says so', () => {
    const upload = `await input.${'setInput' + 'Files'}(path);`;
    expect(mouseCallsIn('sample.ts', upload)).toHaveLength(1);
    expect(mouseCallsIn('sample.ts', `${upload} // file-dialog-exception: a native file dialog cannot be driven`)).toEqual([]);
  });

  test('MAM-86 AC-3 keyboard-journeys.spec.ts and support/keyboard.ts contain no mouse, fill, check, selectOption, dispatchEvent or focus call', () => {
    const found = KEYBOARD_ONLY_FILES.flatMap((file) => mouseCallsIn(file, readFileSync(join(E2E, file), 'utf8')));
    expect(found).toEqual([]);
  });
});

/**
 * What the trace tool (modern/tools/trace/build_rule_trace.py) sees: it takes the criteria of a test only from the bracket form
 * "[MAM-n AC-m]" in the group titles and the title, joined with spaces. So every test of the story must start with "MAM-86 AC-m" and
 * sit in a group whose title carries exactly that one bracket tag (groups are not nested in these files). A test in a group of another
 * criterion would be counted for both (the review of 2026-11-20 found two such tests).
 */
function groupTagProblems(file: string, source: string): string[] {
  const problems: string[] = [];
  let group: string | null = null;
  source.split(/\r?\n/).forEach((text, index) => {
    if (isComment(text)) return;
    const at = `${file}:${index + 1}`;
    const describe = /^\s*test\.describe\(\s*[`'"]([^`'"]*)[`'"]/.exec(text);
    if (describe) {
      const tags = Array.from((describe[1] ?? '').matchAll(/\[(MAM-\d+ AC-\d+)\]/g), (match) => match[1] ?? '');
      group = tags.length === 1 ? (tags[0] ?? null) : null;
      if (group === null) problems.push(`${at} the group title must carry exactly one bracket tag [MAM-n AC-m]: ${(describe[1] ?? '').trim()}`);
      return;
    }
    if (!/^\s*test\(/.test(text)) return;
    const title = /^\s*test\(\s*[`'"](MAM-\d+ AC-\d+)\b/.exec(text);
    if (title === null) problems.push(`${at} the title of a test must start with "MAM-n AC-m": ${text.trim()}`);
    else if (title[1] !== group) problems.push(`${at} the test is for ${title[1]} but sits in a group tagged ${group ?? 'with no tag'}: ${text.trim()}`);
  });
  return problems;
}

test.describe('[MAM-86 AC-1] every test of the story is counted under its own criterion', () => {
  test('MAM-86 AC-1 the checker flags a test that sits in the group of another criterion, a group with no tag and a title with no criterion', () => {
    const wrongGroup = ["test.describe('[MAM-86 AC-1] other states', () => {", "  test('MAM-86 AC-4 a tab walk', async () => {});", '});'].join('\n');
    expect(groupTagProblems('sample.ts', wrongGroup)).toEqual(['sample.ts:2 the test is for MAM-86 AC-4 but sits in a group tagged MAM-86 AC-1: test(\'MAM-86 AC-4 a tab walk\', async () => {});']);
    const noTag = ["test.describe('other states', () => {", "  test('MAM-86 AC-1 a scan', async () => {});", '});'].join('\n');
    expect(groupTagProblems('sample.ts', noTag).map((problem) => problem.split(' ').slice(0, 3).join(' '))).toEqual(['sample.ts:1 the group', 'sample.ts:2 the test']);
    const noCriterion = ["test.describe('[MAM-86 AC-1] states', () => {", "  test('a scan', async () => {});", '});'].join('\n');
    expect(groupTagProblems('sample.ts', noCriterion)).toHaveLength(1);
  });

  test('MAM-86 AC-1 the checker accepts a test in the group of its criterion, written with a plain or a template title', () => {
    const fine = ["test.describe('[MAM-86 AC-5] contrast', () => {", "  test('MAM-86 AC-5 a', async () => {});", '  test(`MAM-86 AC-5 ${id}: b`, async () => {});', '});', "test.describe('[MAM-86 AC-7] gate', () => {", "  test('MAM-86 AC-7 c', async () => {});", '});'].join('\n');
    expect(groupTagProblems('sample.ts', fine)).toEqual([]);
  });

  test('MAM-86 AC-1 every test in the four spec files of this story sits in a group tagged with its own criterion', () => {
    const specs = filesUnder(join(E2E, 'a11y')).filter((path) => path.endsWith('.spec.ts'));
    expect(specs.map((file) => relative(E2E, file).replaceAll('\\', '/')).sort()).toEqual(['a11y/gate.a11y.spec.ts', 'a11y/keyboard-journeys.spec.ts', 'a11y/pages.a11y.spec.ts', 'a11y/policy.a11y.spec.ts']);
    const found = specs.flatMap((file) => groupTagProblems(relative(E2E, file).replaceAll('\\', '/'), readFileSync(file, 'utf8')));
    expect(found).toEqual([]);
  });
});

test.describe('[MAM-86 AC-1] the story files are not skipped', () => {
  test('MAM-86 AC-1 no test of the files of this story is skipped, marked fixme or restricted with only', () => {
    const marks = [`test.${'ski' + 'p'}`, `test.${'fix' + 'me'}`, `test.${'on' + 'ly'}`, `describe.${'on' + 'ly'}`, `.${'ski' + 'p'}(`];
    const found: string[] = [];
    for (const file of filesUnder(join(E2E, 'a11y'))) {
      readFileSync(file, 'utf8')
        .split(/\r?\n/)
        .forEach((text, index) => {
          if (!isComment(text) && marks.some((mark) => text.includes(mark))) found.push(`${relative(E2E, file)}:${index + 1} ${text.trim()}`);
        });
    }
    expect(found).toEqual([]);
  });
});
