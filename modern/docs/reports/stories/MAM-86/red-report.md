# Red report: MAM-86 accessibility (WCAG 2.1 AA, zero axe violations, keyboard-only walk-through)

Written by the test-writer on 2026-11-20. This story verifies a UI that was built before it: the 13 screens, the shared shell and the
seven journeys already exist. So the "red" step looks different from a feature story: a test goes red only where the built UI really
breaks the acceptance criterion. Tests that go green at once describe behaviour the UI already has; they are still the regression net for
AC-1 to AC-7 and are protected by the hash file. No production code (`modern/web-ui/src`) was changed.

## Result of the final runs

| File | Level | Tests | Passed | Failed |
|---|---|---|---|---|
| e2e/a11y/pages.a11y.spec.ts | Playwright, page scans on the live stack (PEM_JOURNEYS=1) | 77 | 70 | 7 |
| e2e/a11y/keyboard-journeys.spec.ts | Playwright, keyboard-only journeys on the live stack (PEM_JOURNEYS=1) | 7 | 7 | 0 |
| e2e/a11y/gate.a11y.spec.ts | Playwright, gate on the served UI (default suite) | 4 | 4 | 0 |
| e2e/a11y/policy.a11y.spec.ts | Playwright, repository checks, no browser (default suite) | 9 | 9 | 0 |
| Total | | 97 | 90 | 7 |

Support files (shared by the specs, no tests of their own): e2e/a11y/support/api.ts, axe-gate.ts, focus.ts, keyboard.ts, screens.ts, sessions.ts, structure.ts.
There is no unit, integration or contract level in this story: no endpoint, event or domain rule changes (rules: none, enabler).

Failures (7), expected and kept as they are (a failing test for a UI defect is not weakened; see modern/docs/reports/accessibility-report.md):

- D1 (6 tests, AC-4): the Tab key stops on `div.usa-table-container--scrollable[tabindex=0]`, which has no role, no name and does not scroll (client width = scroll width).
  Pages: portal.home, portal.notices, console.provider-detail, console.approval, public.directory, and the four tables of console.work-queues when filled.
- D2 (1 test, AC-6): console.risk-rules, 10 inputs named only by aria-label, no label element.

## How the red step ran

1. First run of the page scans (2026-11-20 22:31): 64 passed, 7 failed. Failures: 5 real (portal.home, portal.notices, console.provider-detail, console.approval, console.risk-rules) and 2 test faults (console.provider-search and public.directory: the Tab walk started after the results table because Chrome starts at the last click).
2. The test faults were repaired in the test files (the walk puts the focus on the body first). Run 2 (22:50): the 2 faults are gone; public.directory fails for D1 like the others; the filled work-queue state (added) fails for D1.
3. Journeys: J1, J3, J4 passed (22:42); J5, J6 passed and J7 failed on a test fault (the arrow-key helper could only move down; fixed). J7 then passed (22:50).
4. The gate: the low-contrast injection test failed once on a test fault (the injected paragraph had no padding and axe reported it as "partially obscured"; fixed, then passed).
5. The suppression checker: two patterns matched code that is not axe (the Playwright call `test.describe.configure` and its own label text); fixed, then the repository check passed.
6. Final runs after the last edit of each file: gate and policy 23:17 and 23:31 (13 of 13 passed); pages 23:18 and 23:28 (the second after one more test, the structure of the filled work queues: 70 passed, 7 failed with D1 and D2 as above); journeys 23:20 (7 of 7 passed).
   Each run was made under the shared stack lock, from the seed state, and the stack was seeded again before the lock was released.

JSON reports (for the trace tool): modern/web-ui/test-results/playwright-a11y-pages.json, playwright-a11y-journeys.json, playwright-a11y-gate.json.

## Every test and its result


### a11y/pages.a11y.spec.ts

| Test | Result | Reason |
|---|---|---|
| MAM-86 AC-1 portal.home: axe reports no violation for wcag2a, wcag2aa, wcag21a, wcag21aa | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-1 portal.apply: axe reports no violation for wcag2a, wcag2aa, wcag21a, wcag21aa | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-1 portal.application-status: axe reports no violation for wcag2a, wcag2aa, wcag21a, wcag21aa | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-1 portal.provider-record: axe reports no violation for wcag2a, wcag2aa, wcag21a, wcag21aa | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-1 portal.notices: axe reports no violation for wcag2a, wcag2aa, wcag21a, wcag21aa | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-1 console.provider-search: axe reports no violation for wcag2a, wcag2aa, wcag21a, wcag21aa | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-1 console.provider-detail: axe reports no violation for wcag2a, wcag2aa, wcag21a, wcag21aa | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-1 console.work-queues: axe reports no violation for wcag2a, wcag2aa, wcag21a, wcag21aa | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-1 console.approval: axe reports no violation for wcag2a, wcag2aa, wcag21a, wcag21aa | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-1 console.record-sanction: axe reports no violation for wcag2a, wcag2aa, wcag21a, wcag21aa | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-1 console.risk-rules: axe reports no violation for wcag2a, wcag2aa, wcag21a, wcag21aa | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-1 console.batch-jobs: axe reports no violation for wcag2a, wcag2aa, wcag21a, wcag21aa | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-1 public.directory: axe reports no violation for wcag2a, wcag2aa, wcag21a, wcag21aa | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-1 console.work-queues with an entry in each of the four lists (scripted answers): zero axe violations | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-4 console.work-queues with an entry in each of the four lists (scripted answers): Tab order and focus indicators | FAIL | D1: same container around each of the four tables (4 problems) |
| MAM-86 AC-1 console.batch-jobs with a file job chosen (the file input and the batch id are shown): zero axe violations and every input labelled | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-2 portal.apply with errors: errors at the fields, a summary linked to them, zero axe violations | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-2 console.provider-search with "No provider found": zero axe violations | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-2 a console route refused with 403 (a PROVIDER opens /console/providers): zero axe violations | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-2 console.record-sanction with errors: errors next to the fields, a summary linked to them, zero axe violations | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-2 console.approval with no decision chosen: the error summary links to the first choice, zero axe violations | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-4 portal.home: every Tab stop is an interactive, visible element with a focus indicator, in the visual order | FAIL | D1: Tab stops on div.usa-table-container--scrollable (tabindex 0, no role, no name) that is not scrollable |
| MAM-86 AC-4 portal.apply: every Tab stop is an interactive, visible element with a focus indicator, in the visual order | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-4 portal.application-status: every Tab stop is an interactive, visible element with a focus indicator, in the visual order | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-4 portal.provider-record: every Tab stop is an interactive, visible element with a focus indicator, in the visual order | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-4 portal.notices: every Tab stop is an interactive, visible element with a focus indicator, in the visual order | FAIL | D1: same container around the notices table |
| MAM-86 AC-4 console.provider-search: every Tab stop is an interactive, visible element with a focus indicator, in the visual order | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-4 console.provider-detail: every Tab stop is an interactive, visible element with a focus indicator, in the visual order | FAIL | D1: same container around the screening table |
| MAM-86 AC-4 console.work-queues: every Tab stop is an interactive, visible element with a focus indicator, in the visual order | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-4 console.approval: every Tab stop is an interactive, visible element with a focus indicator, in the visual order | FAIL | D1: same container around the screening results table |
| MAM-86 AC-4 console.record-sanction: every Tab stop is an interactive, visible element with a focus indicator, in the visual order | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-4 console.risk-rules: every Tab stop is an interactive, visible element with a focus indicator, in the visual order | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-4 console.batch-jobs: every Tab stop is an interactive, visible element with a focus indicator, in the visual order | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-4 public.directory: every Tab stop is an interactive, visible element with a focus indicator, in the visual order | FAIL | D1: same container around the active providers table |
| MAM-86 AC-5 portal.home: the colour-contrast rule ran on the text and found no text below 4.5:1 | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-5 portal.home: every status badge and status word shows its text with its code | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-5 portal.apply: the colour-contrast rule ran on the text and found no text below 4.5:1 | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-5 portal.apply: every status badge and status word shows its text with its code | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-5 portal.application-status: the colour-contrast rule ran on the text and found no text below 4.5:1 | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-5 portal.application-status: every status badge and status word shows its text with its code | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-5 portal.provider-record: the colour-contrast rule ran on the text and found no text below 4.5:1 | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-5 portal.provider-record: every status badge and status word shows its text with its code | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-5 portal.notices: the colour-contrast rule ran on the text and found no text below 4.5:1 | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-5 portal.notices: every status badge and status word shows its text with its code | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-5 console.provider-search: the colour-contrast rule ran on the text and found no text below 4.5:1 | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-5 console.provider-search: every status badge and status word shows its text with its code | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-5 console.provider-detail: the colour-contrast rule ran on the text and found no text below 4.5:1 | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-5 console.provider-detail: every status badge and status word shows its text with its code | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-5 console.work-queues: the colour-contrast rule ran on the text and found no text below 4.5:1 | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-5 console.work-queues: every status badge and status word shows its text with its code | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-5 console.approval: the colour-contrast rule ran on the text and found no text below 4.5:1 | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-5 console.approval: every status badge and status word shows its text with its code | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-5 console.record-sanction: the colour-contrast rule ran on the text and found no text below 4.5:1 | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-5 console.record-sanction: every status badge and status word shows its text with its code | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-5 console.risk-rules: the colour-contrast rule ran on the text and found no text below 4.5:1 | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-5 console.risk-rules: every status badge and status word shows its text with its code | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-5 console.batch-jobs: the colour-contrast rule ran on the text and found no text below 4.5:1 | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-5 console.batch-jobs: every status badge and status word shows its text with its code | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-5 public.directory: the colour-contrast rule ran on the text and found no text below 4.5:1 | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-5 public.directory: every status badge and status word shows its text with its code | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-5 console.provider-search: Suspended (S) and every other status are text with their code, never colour alone | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-5 console.provider-detail: a suspended provider reads Suspended (S) in the banner and in the record | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-5 portal.provider-record: the status is text with its code | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-6 portal.home: one h1, banner, main and navigation landmarks, headers in every table, a visible label on every input | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-6 portal.apply: one h1, banner, main and navigation landmarks, headers in every table, a visible label on every input | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-6 portal.application-status: one h1, banner, main and navigation landmarks, headers in every table, a visible label on every input | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-6 portal.provider-record: one h1, banner, main and navigation landmarks, headers in every table, a visible label on every input | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-6 portal.notices: one h1, banner, main and navigation landmarks, headers in every table, a visible label on every input | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-6 console.provider-search: one h1, banner, main and navigation landmarks, headers in every table, a visible label on every input | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-6 console.provider-detail: one h1, banner, main and navigation landmarks, headers in every table, a visible label on every input | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-6 console.work-queues: one h1, banner, main and navigation landmarks, headers in every table, a visible label on every input | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-6 console.approval: one h1, banner, main and navigation landmarks, headers in every table, a visible label on every input | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-6 console.record-sanction: one h1, banner, main and navigation landmarks, headers in every table, a visible label on every input | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-6 console.risk-rules: one h1, banner, main and navigation landmarks, headers in every table, a visible label on every input | FAIL | D2: 10 inputs (prefix-1..5, tier-1..5) have no visible label element, only aria-label |
| MAM-86 AC-6 console.batch-jobs: one h1, banner, main and navigation landmarks, headers in every table, a visible label on every input | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-6 public.directory: one h1, banner, main and navigation landmarks, headers in every table, a visible label on every input | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-6 console.work-queues with an entry in each of the four lists: every list is a table with header cells, one h1, the landmarks | PASS | passes: the built UI already meets it, no production change needed |

### a11y/keyboard-journeys.spec.ts

| Test | Result | Reason |
|---|---|---|
| MAM-86 AC-3 J1 keyboard only: a provider applies, the analyst approves, the directory and the notices show it | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-3 J2 keyboard only: an application without a taxonomy is rejected with the error at the field and in the summary | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-3 J3 keyboard only: screening fails on the owner, the analyst finds it under Failed screening and denies | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-3 J4 keyboard only: the analyst searches by NPI and Medicaid ID, reads the detail, an unknown NPI says so, a provider cannot use the console | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-3 J5 keyboard only: a supervisor suspends and reinstates a provider | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-3 J6 keyboard only: the administrator runs the monthly revalidation sweep and downloads the REVALQ file | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-3 J7 keyboard only: the administrator runs the T-MSIS and directory extracts and downloads both files; a visitor finds the hospice | PASS | passes: the built UI already meets it, no production change needed |

### a11y/gate.a11y.spec.ts

| Test | Result | Reason |
|---|---|---|
| MAM-86 AC-7 one injected violation on a real page makes the checker fail, naming the page and the rule | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-5 the colour-contrast rule is not disabled: injected low-contrast text is reported as color-contrast | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-7 a real Playwright run that meets a violation exits non-zero and prints the page and the rule (what make e2e relies on) | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-3 a control that Tab never reaches fails the step with its name, within the bound; a reachable one is reached | PASS | passes: the built UI already meets it, no production change needed |

### a11y/policy.a11y.spec.ts

| Test | Result | Reason |
|---|---|---|
| MAM-86 AC-7 the checker flags a disabled rule that has no justification | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-7 the checker flags an excluded part of the page and a rule switched off by option | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-7 the checker accepts a suppression with a written reason next to it and rejects an empty one | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-7 no file under modern/web-ui/e2e disables, excludes or narrows an axe rule without a justification | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-7 every scan of this story goes through the one checker, whose only option is the list of tags of AC-1 | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-3 the checker finds a mouse call, a fill and a programmatic focus in a sample | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-3 a file chosen for an upload is the only call that may be made without the keyboard, and only when it says so | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-3 keyboard-journeys.spec.ts and support/keyboard.ts contain no mouse, fill, check, selectOption, dispatchEvent or focus call | PASS | passes: the built UI already meets it, no production change needed |
| MAM-86 AC-1 no test of the files of this story is skipped, marked fixme or restricted with only | PASS | passes: the built UI already meets it, no production change needed |
