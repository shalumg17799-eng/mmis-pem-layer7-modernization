# Red report: MAM-86 accessibility (WCAG 2.1 AA, zero axe violations, keyboard-only walk-through)

Written by the test-writer on 2026-11-20; corrected on 2026-11-20 evening / 2026-11-21 after an independent review (see "Correction round"); changed again on 2026-11-21 night (see "Round of 2026-11-21": the AC-6 label check, new gate cases, unit tests of ScrollableTable). This story verifies a UI that was built before it: the 13 screens, the shared shell and the
seven journeys already exist. So the "red" step looks different from a feature story: a test goes red only where the built UI really
breaks the acceptance criterion. Tests that go green at once describe behaviour the UI already has; they are still the regression net for
AC-1 to AC-7 and are protected by the hash file. No production code (`modern/web-ui/src`) was changed.

## Round of 2026-11-21 (00:20 to 00:50): the AC-6 label check brought in line with its criterion (Q-137 A), 28 new gate cases, 20 unit tests of ScrollableTable

The sections below this one describe the rounds of 2026-11-20 and stay as they were written (history). This section is the current state.
The test-writer changed no production code (`modern/web-ui/src`) and built nothing; the lead deployed the build of 2026-11-21 00:19 (`modern/web-ui/dist`, files dated 00:19:47), which contains the fix of D1 (`src/shared/ui/ScrollableTable.tsx`).

### Result of the final runs

| File | Level | Tests | Passed | Failed |
|---|---|---|---|---|
| e2e/a11y/pages.a11y.spec.ts | Playwright, page scans on the live stack (PEM_JOURNEYS=1), build of 00:19 | 77 | 77 | 0 |
| e2e/a11y/gate.a11y.spec.ts | Playwright, gate (4) and the AC-6 label rule (28 new), default suite | 32 | 32 | 0 |
| e2e/a11y/policy.a11y.spec.ts | Playwright, repository checks, no browser, default suite | 12 | 12 | 0 |
| tests/ux/scrollable-table.test.tsx | Vitest, component (new) | 20 | 20 | 0 |
| e2e/a11y/keyboard-journeys.spec.ts | Playwright, keyboard-only journeys | 7 | not run in this round | 7 of 7 passed in the correction round, on the build of 2026-11-20 22:02 |
| Total | | 148 | 141 run, 141 passed | 0 |

Per criterion, computed with the trace tool's own `tests()` function (imported, run over a scratch copy of the reports; `main()` not run, no file of the tool written), the 7 journeys counted from the correction round: AC-1 19 (19 passed), AC-2 5 (5), AC-3 11 (11), AC-4 34 (34: 14 Playwright and 20 Vitest), AC-5 30 (30), AC-6 42 (42: 14 pages and 28 gate), AC-7 7 (7); total 148, 148 passed, 0 failed (141 of them run in this round).
`npx tsc -b` and `npx eslint . --max-warnings 0` in modern/web-ui: both clean (exit 0).
The 7 reds of the correction round are gone: the 6 tests of D1 pass on the build of 00:19 (the container is no longer a Tab stop where the table fits), and the AC-6 test of console.risk-rules passes with the checker changed as described next.

### 1. The change of a recorded test: the AC-6 check (user rule Q-097 C, decision Q-137 A of the lead, 2026-11-21)

What the check accepted before: an input counted as labelled only when it had a label element with text that is shown (`labelVisible`). A name from aria-label, aria-labelledby or the column header never counted. For console.risk-rules this gave the one red of AC-6: its 10 inputs ("Taxonomy prefix, row N", "Tier, row N") are named by aria-label and the visible text is the column header.

What it accepts now (support/structure.ts: `readFacts` reads more facts in the browser, `structureProblems` decides): an input counts as labelled when
(a) it has a visible label element, as before, OR
(b) it sits in a table cell (td or th), the header cell over its column is shown and has visible text, and that text (trimmed, case kept) is contained in the input's accessible name. The column is found on the column grid of the table (colspan widens a cell; a rowspan keeps its columns taken in the rows below); the header cell is the lowest one over that column in the thead, or in the first row made only of th when there is no thead. "Visible text" leaves out parts of the header that are not shown (display none, visibility hidden, opacity 0, one pixel clipped). The accessible name is the text of the elements named by aria-labelledby, else aria-label, else the label elements.
An input that meets neither is a problem, and its message names the input and says what each case misses, for example: `input "tier-1" (select-one) is not labelled for a sighted user: it meets neither (a) a visible label element (it is named only by aria-label "Quantity, row 1") nor (b) a visible column header whose text is in its accessible name (its accessible name "Quantity, row 1" does not contain the visible header text "Tier" of its column)`.

Why: the criterion and the front-end standard say "every input has a visible label". Option B of Q-137 (a visible label element per input of the table) was tried first and failed the recorded test of MAM-92 AC-1 (a rule row may hold no other text), so the lead decided A (Q-137, default applied at 23:56): the visible column header is the visible label, and the check is brought in line with the wording of the criterion. WCAG 2.1: 3.3.2 Labels or Instructions, 2.5.3 Label in Name, 1.3.1 Info and Relationships.

What it still refuses (each proved by a gate test, section 2): an input outside a table that is named only by aria-label; a table input whose name does not contain the header text of its column; a table input under an empty header cell, or under a header cell that is not shown; a table input whose name holds only the header text of another column; an input whose label element is not shown; also (extra cases): a table input with no name at all, an aria-labelledby that wins over an aria-label, a column with no header cell, the header text matched without regard to case, a header whose only text is hidden inside it.
What did not change: every other assertion. One h1 (and, on the pages, equal to the title of the inventory), exactly one banner and one main, a navigation landmark, header cells in every table, none empty. The test of console.risk-rules keeps its title, its group and its assertion (`expect(problems).toEqual([])`); only the checker that it calls changed. pages.a11y.spec.ts itself is unchanged in this round. `Facts.inputs` has three more fields (`name`, `inCell`, `header`), and the other tests that read `facts.inputs` only count it.
This is a change of a recorded test; the hashes are recorded again (section 5).

### 2. New gate cases (28 tests in e2e/a11y/gate.a11y.spec.ts, groups tagged [MAM-86 AC-6], no stack needed)

Each case is a small page built with `page.setContent` (landmarks and one h1 around the case, so that the only problems a case can raise are about its inputs) and is read by the same checker as the page scans (`readFacts`, then `structureProblems`). An accepted case must give no problem at all; a refused case must give exactly one problem that names the input, with the text of what (a) and (b) miss.

| Case | Tests | Expectation |
|---|---|---|
| (i) a visible label element | 2: an input with a label; a table input with a label under a header that says something else | accepted |
| (ii) a table input whose name contains the visible header of its own column | 7: text field and select (as on the risk rules page); colspan; rowspan; name by aria-labelledby; a hidden suffix of the header; no thead; two header rows | accepted |
| (iii) an input outside a table named only by aria-label | 2: alone; beside a table whose header has the same words | refused |
| (iv) a table input whose name does not contain its column's header | 5: other words (the neighbour that is right is not reported); case differs; no name; aria-labelledby wins over aria-label; a column with no header cell | refused |
| (v) a table input under an empty or hidden header | 6: empty header; header whose text is hidden inside it; header cell with display none, visibility hidden, opacity 0, one pixel clipped | refused |
| (vi) a name that holds only another column's header | 2: the two inputs swapped; over a colspan | refused |
| (vii) a label element that is not shown | 4: display none, visibility hidden, opacity 0, one pixel clipped | refused |

Red evidence (the tests were written after the checker, so red is shown against the old rule and by mutation):
- Against the old rule (the version of structure.ts in HEAD put back for one run, then restored byte for byte, sha256 checked): 26 of the 28 fail. The 7 accepted cases of (ii) fail because the old rule refuses them (the real red). The 19 refused cases fail on the message only: the old rule refused them too, by its stricter test, but did not say which of the two cases it misses, which the lead asked for; for these the mutants below are the proof that they bite. The 2 that pass are the two (i) cases, which the old rule already accepted (they guard that case (a) is kept).
- Ten mutants of the new checker, each run against the 28 tests, each killed (the mutant is applied to structure.ts for one run and the file is restored byte for byte afterwards): case (b) removed (8 tests fail); the header text need not be in the name (5); a hidden header still counts (4); the column taken by position in the row, no rowspan grid (1); a label element that is not shown still counts (4); case (b) also outside a table (6); the match ignores case (1); the header text taken as textContent, hidden parts included (2); aria-labelledby ignored (2); an empty header text accepted (2).
- Run on the live pages: the AC-6 test of console.risk-rules passes (10 table inputs under "Taxonomy prefix" and "Tier"; the 11th input of the page has a label element); the other 12 screens and the filled work queues pass as before.

### 3. A fault in the start state of a test, found by these runs and fixed (e2e/a11y/support/screens.ts)

The first run of the pages file on the build of 00:19 gave 76 passed and 1 failed: "MAM-86 AC-4 console.provider-search" waited 30 s for the table "Search results" and did not find it. The page showed "No provider found" with the NPI field already filled with 1003000999 and the name TESTPROV. Cause: the search page pre-fills the NPI of the last search made in the tab (sessionStorage, MAM-77 AC-6), and the AC-2 test "No provider found" (same tab, runs earlier) had typed 1003000999; the preparation of the screen typed only the name, so the search was NPI and name together, and every given filter must match. Evidence: the test passes alone; AC-2 then AC-4 of the search fails; after the fix both pass. It is a fault of the test start state (the screen's main state is "a search by name"), not of the page and not of the label rule. It is not established why the same order passed in the correction round (the page file ProviderSearch.tsx did not change between the commits).
Fix: the preparation of console.provider-search empties the NPI field before it types the name (one line, with a comment); no assertion changed. The next run of the whole pages file: 77 of 77 passed. The other tests that use this screen (AC-1, AC-5, AC-6) are now independent of the order too.

### 4. Unit tests of ScrollableTable (20 tests, tests/ux/scrollable-table.test.tsx, tagged [MAM-86 AC-4])

Written after the component, not before it (the tests that were red first for D1 are the six Playwright Tab walks of AC-4, which failed against the old build). jsdom has no layout, so the test stubs `ResizeObserver` (a fake that the test fires by hand, as the browser would) and the `scrollWidth` and `clientWidth` of the container; the real measuring is proved only by the Playwright runs on the live build. All 20 passed at the first run, so no defect of the component was found.
Cases: overflowing gives tabindex 0, role region and the name "<label> (scrollable table)" (4 tests, one of them with a second label); fitting gives no tabindex, no role, no name; equal widths fit, one pixel more overflows; after the sizes change and the observer fires the attributes follow in both directions, on the same element; an observer that fires with the sizes unchanged changes nothing; the observer watches the container and the table in it and is disconnected on unmount; without a ResizeObserver (jsdom as it is) the container is no Tab stop; with the user-event Tab key an overflowing table's container takes the focus before the link in the first row, a fitting table's does not (the first Tab goes to the link); the USWDS container class is on the container, there is one container not two, and the properties of the table (caption, class) reach the table.
Mutation check (the component itself was not touched; each mutant is a temporary copy of it inside tests/ux, with a copy of the test pointed at it, both removed afterwards): 11 mutants, all killed: always a Tab stop (7 tests fail); never a Tab stop (9); no role (4); no name (3); the name without the label (3); >= instead of > (7); the observer never created (4); the table not observed (1); never disconnected (1); the USWDS scrollable option used as well (19); the table properties lost (4).

### 5. How the runs were made

Procedure: the new gate cases first (no stack, 28 of 28 passed, 13 s), then the old-rule run and the mutants (no stack, 00:24 to 00:34); then, under the stack lock (taken 00:35:03, released 00:45:10; seed before taking the run and again before the release): the whole pages file (00:35: 76 passed, 1 failed, 2.1 min), the two small runs that located the fault of section 3, the whole pages file again (00:42: 77 passed, 1.6 min), and the default suite `npx playwright test e2e/a11y` without PEM_JOURNEYS (00:44: 44 passed in 32 s: gate 32, repository checks 12; it includes the nested real Playwright run of the gate, which needs only the web UI). Free memory before the runs: 1025 MB (first gate run, started after waiting until it reached 1024), 1070 MB (mutants), 1051, 1826 and 2110 MB (the three runs on the stack). The Vitest file ran from PowerShell (20 passed, 13.6 s), tsc and eslint last.
Test protection: recorded again for the 11 files of the story and the new Vitest file (12 files): `check MAM-86` prints OK; the changed files are gate.a11y.spec.ts, support/structure.ts and support/screens.ts, the new one is tests/ux/scrollable-table.test.tsx; the other files of the story are byte-identical to the correction round.
Not run in this round: the seven keyboard journeys (they need no change; they were not part of this task), and no run on the old build (the build of 22:02 was replaced at 00:19, so D1 and D2 cannot be reproduced any more except from the history above).

## Result of the final runs (correction round, 2026-11-20 23:55 to 2026-11-21 00:09; history)

| File | Level | Tests | Passed | Failed |
|---|---|---|---|---|
| e2e/a11y/pages.a11y.spec.ts | Playwright, page scans on the live stack (PEM_JOURNEYS=1) | 77 | 70 | 7 |
| e2e/a11y/keyboard-journeys.spec.ts | Playwright, keyboard-only journeys on the live stack (PEM_JOURNEYS=1) | 7 | 7 | 0 |
| e2e/a11y/gate.a11y.spec.ts | Playwright, gate on the served UI (default suite) | 4 | 4 | 0 |
| e2e/a11y/policy.a11y.spec.ts | Playwright, repository checks, no browser (default suite) | 12 | 12 | 0 |
| Total | | 100 | 93 | 7 |

Support files (shared by the specs, no tests of their own): e2e/a11y/support/api.ts, axe-gate.ts, focus.ts, keyboard.ts, screens.ts, sessions.ts, structure.ts.
There is no unit, integration or contract level in this story: no endpoint, event or domain rule changes (rules: none, enabler).

Failures (7) of the correction round, expected and kept as they were (a failing test for a UI defect is not weakened; see modern/docs/reports/accessibility-report.md). Both are closed in the round of 2026-11-21: D1 by the fix in the source (confirmed on the build of 00:19), D2 by the decision Q-137 A and the changed AC-6 check (no change to the page):

- D1 (6 tests, AC-4): the Tab key stops on `div.usa-table-container--scrollable[tabindex=0]`, which has no role, no name and does not scroll (client width = scroll width).
  Pages: portal.home, portal.notices, console.provider-detail, console.approval, public.directory, and the four tables of console.work-queues when filled.
- D2 (1 test, AC-6): console.risk-rules, 10 inputs named only by aria-label, no label element.

## How the red step ran (first round; the correction round follows below)

1. First run of the page scans (2026-11-20 22:31): 64 passed, 7 failed. Failures: 5 real (portal.home, portal.notices, console.provider-detail, console.approval, console.risk-rules) and 2 test faults (console.provider-search and public.directory: the Tab walk started after the results table because Chrome starts at the last click).
2. The test faults were repaired in the test files (the walk puts the focus on the body first). Run 2 (22:50): the 2 faults are gone; public.directory fails for D1 like the others; the filled work-queue state (added) fails for D1.
3. Journeys: J1, J3, J4 passed (22:42); J5, J6 passed and J7 failed on a test fault (the arrow-key helper could only move down; fixed). J7 then passed (22:50).
4. The gate: the low-contrast injection test failed once on a test fault (the injected paragraph had no padding and axe reported it as "partially obscured"; fixed, then passed).
5. The suppression checker: two patterns matched code that is not axe (the Playwright call `test.describe.configure` and its own label text); fixed, then the repository check passed.
6. Final runs after the last edit of each file: gate and policy 23:17 and 23:31 (13 of 13 passed); pages 23:18 and 23:28 (the second after one more test, the structure of the filled work queues: 70 passed, 7 failed with D1 and D2 as above); journeys 23:20 (7 of 7 passed).
   Each run was made under the shared stack lock, from the seed state, and the stack was seeded again before the lock was released.

JSON reports (for the trace tool): modern/web-ui/test-results/playwright-a11y-pages.json, playwright-a11y-journeys.json, playwright-a11y-gate.json.

## Correction round (review of 2026-11-20 evening)

A review found two faults in the tests (never in production code, which this story does not touch). Both are fixed and re-run.

1. The keyboard journeys checked less than the mouse journeys in e2e/journeys, so "the same outcome checks" was not true. Added to keyboard-journeys.spec.ts: J1 the scan of the apply form, screening reason INITIAL and status COMPLETED, and the Failed screening list shown before "the provider is not in it"; J2 the scan of the empty apply form; J3 application VALIDATED, status stays P after the FAIL, screening FAIL / OIGLEIE F / E203 from the API; J5 the three form fields shown and `application/problem+json` on the 422; J7 the new run is the newest T-MSIS row, no second row for the run, TMSISOUT and DIROUT each with exactly one record per active provider (count as well as set), the directory search answers 200 and every name contains HOSPICE. J4 and J6 already had every mouse check. All seven journeys passed again from the seed (the added checks are met by the UI, so they went green at once; they are regression checks, not new reds). Differences that remain on purpose are listed in modern/docs/reports/accessibility-report.md ("Keyboard-only journeys").
2. The trace tool takes a test's criterion only from the bracket form `[MAM-86 AC-n]` in the group titles and the title. A Tab-walk test of AC-4 sat in the group "[MAM-86 AC-1] other states" and the AC-5 contrast gate test in the group "[MAM-86 AC-7]", so the tool counted them for two criteria (AC-1 would have shown a failure, AC-4 and AC-5 wrong counts). Both tests moved into a group of their own criterion (pages.a11y.spec.ts: into the AC-4 group; gate.a11y.spec.ts: a new group "[MAM-86 AC-5] the colour-contrast rule is not disabled"). Three tests were added to policy.a11y.spec.ts, group "[MAM-86 AC-1] every test of the story is counted under its own criterion": the checker flags a test in the wrong group, a group with no tag and a title with no criterion (sample); it accepts a correct group with plain and template titles (sample); and every test of the four spec files sits in a group with its own tag (the real files).

Per-criterion counts as the tool computes them (its own `tests()` function imported and run over the three JSON reports; `main()` not run, so no file of the tool was written), after the correction round:

| Criterion | Tests | Passed | Failed |
|---|---|---|---|
| AC-1 | 19 | 19 | 0 |
| AC-2 | 5 | 5 | 0 |
| AC-3 | 11 | 11 | 0 |
| AC-4 | 14 | 8 | 6 (D1) |
| AC-5 | 30 | 30 | 0 |
| AC-6 | 14 | 13 | 1 (D2) |
| AC-7 | 7 | 7 | 0 |
| Total | 100 | 93 | 7 |

Before the correction the same function gave AC-1 17 (1 failed), AC-4 13 (5 failed), AC-5 29 and AC-7 8. The 7 reds are unchanged: the same 6 tests for D1 and 1 test for D2, with the same messages as in the first round; none of them is a timing fault.
Build tested: the one the stack serves (modern/web-ui/dist, 2026-11-20 22:02). The UI source changed after it (commit 14e0707 at 23:57 touches the table pages, a new ScrollableTable.tsx and RiskRulesPage.tsx, which differs again in the working tree); nothing was built or run against that source, so the 7 reds stand until a deploy and a new run of the pages file.
Run procedure: seed, pages (23:55 to 23:59), keyboard journeys (23:59 to 00:08, seeded again before each journey), gate and repository checks (00:08 to 00:09), seed; stack lock taken 23:54, released 00:09; free memory before the three runs 1382, 3088 and 1876 MB. Test protection recorded again (11 files, `check MAM-86` prints OK; the four spec files changed, the seven support files did not).

## Every test and its result (correction round, with the tests added in the round of 2026-11-21 at the end)

Rows marked FAIL (the six D1 tests of AC-4 and the D2 test of AC-6) were the result of the correction round; in the round of 2026-11-21 all of them pass (section "Round of 2026-11-21").

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
| MAM-86 AC-4 console.work-queues with an entry in each of the four lists (scripted answers): Tab order and focus indicators | FAIL | D1: same container around each of the four tables (4 problems) |
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

All seven re-run in the correction round with the added outcome checks (J1 81 s, J2 50 s, J3 82 s, J4 135 s, J5 66 s, J6 55 s, J7 40 s on a loaded machine).

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
| MAM-86 AC-1 the checker flags a test that sits in the group of another criterion, a group with no tag and a title with no criterion | PASS | added in the correction round; passes (the checker is tested on samples) |
| MAM-86 AC-1 the checker accepts a test in the group of its criterion, written with a plain or a template title | PASS | added in the correction round; passes |
| MAM-86 AC-1 every test in the four spec files of this story sits in a group tagged with its own criterion | PASS | added in the correction round; passes after the two tests were moved (the mis-grouping it guards against is the sample of the first of these three tests) |
| MAM-86 AC-1 no test of the files of this story is skipped, marked fixme or restricted with only | PASS | passes: the built UI already meets it, no production change needed |

### Tests added in the round of 2026-11-21

#### a11y/gate.a11y.spec.ts, the AC-6 label rule (28 tests; no stack needed). Result against the old rule (HEAD version of structure.ts): FAIL for 26, PASS for the two (i) cases; against the new rule: all PASS

| Test | Result | Reason |
|---|---|---|
| MAM-86 AC-6 (i) an input with a visible label element is accepted: no problem at all on the page | PASS | passes; against the old rule it passes too (case (a) kept) |
| MAM-86 AC-6 (i) a table input with a visible label element is accepted, whatever the header of its column says | PASS | passes; against the old rule it passes too (case (a) kept) |
| MAM-86 AC-6 (ii) a table input whose aria-label contains the visible header text of its own column is accepted (a text field and a select, as on the risk rules page) | PASS | passes; against the old rule it FAILS: the old rule refuses this input |
| MAM-86 AC-6 (ii) the column is counted over a colspan: a header that spans two columns is the header of both | PASS | passes; against the old rule it FAILS: the old rule refuses this input |
| MAM-86 AC-6 (ii) the column is counted over a rowspan: a cell that reaches into the next row keeps its column taken there | PASS | passes; against the old rule it FAILS: the old rule refuses this input |
| MAM-86 AC-6 (ii) the name may come from aria-labelledby: the text of the elements it points to is the accessible name | PASS | passes; against the old rule it FAILS: the old rule refuses this input |
| MAM-86 AC-6 (ii) only the text a person can read in the header counts: a visually hidden suffix of the header need not be in the name | PASS | passes; against the old rule it FAILS: the old rule refuses this input |
| MAM-86 AC-6 (ii) a table without a thead has its header in the first row made only of th | PASS | passes; against the old rule it FAILS: the old rule refuses this input |
| MAM-86 AC-6 (ii) with two header rows the lowest header cell over the column is the one that counts | PASS | passes; against the old rule it FAILS: the old rule refuses this input |
| MAM-86 AC-6 (iii) an input outside a table that is named only by aria-label is refused | PASS | passes; against the old rule it FAILS on the message (no "neither (a) nor (b)" text) |
| MAM-86 AC-6 (iii) an input beside a table is refused even when its name equals a header of that table (only a cell of the table counts) | PASS | passes; against the old rule it FAILS on the message (no "neither (a) nor (b)" text) |
| MAM-86 AC-6 (iv) a table input whose aria-label does not contain the header text of its column is refused; the other input of the row, which does, is not | PASS | passes; against the old rule it FAILS on the message (no "neither (a) nor (b)" text) |
| MAM-86 AC-6 (iv) the header text is matched with its case kept: "tier, row 1" does not contain "Tier" | PASS | passes; against the old rule it FAILS on the message (no "neither (a) nor (b)" text) |
| MAM-86 AC-6 (iv) a table input with no name at all is refused | PASS | passes; against the old rule it FAILS on the message (no "neither (a) nor (b)" text) |
| MAM-86 AC-6 (iv) aria-labelledby comes before aria-label: an aria-label that holds the header text does not help when the name is taken from elsewhere | PASS | passes; against the old rule it FAILS on the message (no "neither (a) nor (b)" text) |
| MAM-86 AC-6 (iv) an input in a column that has no header cell is refused (more cells in the row than header cells) | PASS | passes; against the old rule it FAILS on the message (no "neither (a) nor (b)" text) |
| MAM-86 AC-6 (v) a table input under an empty header cell is refused, even when its name is a full sentence | PASS | passes; against the old rule it FAILS on the message (no "neither (a) nor (b)" text) |
| MAM-86 AC-6 (v) a table input under a header cell whose only text is hidden inside it is refused | PASS | passes; against the old rule it FAILS on the message (no "neither (a) nor (b)" text) |
| MAM-86 AC-6 (v) a table input under a hidden header cell is refused, though the header text is in its name: header with display none | PASS | passes; against the old rule it FAILS on the message (no "neither (a) nor (b)" text) |
| MAM-86 AC-6 (v) a table input under a hidden header cell is refused, though the header text is in its name: header with visibility hidden | PASS | passes; against the old rule it FAILS on the message (no "neither (a) nor (b)" text) |
| MAM-86 AC-6 (v) a table input under a hidden header cell is refused, though the header text is in its name: header with opacity 0 | PASS | passes; against the old rule it FAILS on the message (no "neither (a) nor (b)" text) |
| MAM-86 AC-6 (v) a table input under a hidden header cell is refused, though the header text is in its name: header with visually hidden (one pixel, clipped) | PASS | passes; against the old rule it FAILS on the message (no "neither (a) nor (b)" text) |
| MAM-86 AC-6 (vi) a table input whose name holds only the header text of ANOTHER column is refused (the two inputs are swapped here) | PASS | passes; against the old rule it FAILS on the message (no "neither (a) nor (b)" text) |
| MAM-86 AC-6 (vi) the other column is found over a colspan too: the name holds the header of the spanning cell, the column is under "Tier" | PASS | passes; against the old rule it FAILS on the message (no "neither (a) nor (b)" text) |
| MAM-86 AC-6 (vii) an input whose label element is not shown is refused: label with display none | PASS | passes; against the old rule it FAILS on the message (no "neither (a) nor (b)" text) |
| MAM-86 AC-6 (vii) an input whose label element is not shown is refused: label with visibility hidden | PASS | passes; against the old rule it FAILS on the message (no "neither (a) nor (b)" text) |
| MAM-86 AC-6 (vii) an input whose label element is not shown is refused: label with opacity 0 | PASS | passes; against the old rule it FAILS on the message (no "neither (a) nor (b)" text) |
| MAM-86 AC-6 (vii) an input whose label element is not shown is refused: label with visually hidden (one pixel, clipped) | PASS | passes; against the old rule it FAILS on the message (no "neither (a) nor (b)" text) |

#### tests/ux/scrollable-table.test.tsx (Vitest, 20 tests, written after the component; mutants of the component are all killed, see section 4 of the round)

| Test | Result | Reason |
|---|---|---|
| [MAM-86 AC-4] a table wider than its container makes the container a Tab stop: tabindex is 0 | PASS | passes at the first run: written after the component, which already meets it |
| [MAM-86 AC-4] a table wider than its container gives the container the role region | PASS | passes at the first run: written after the component, which already meets it |
| [MAM-86 AC-4] the name of the scrolling region contains the label of the table: "Screening results (scrollable table)" | PASS | passes at the first run: written after the component, which already meets it |
| [MAM-86 AC-4] the name follows the label that the page gives: another table is named after its own label | PASS | passes at the first run: written after the component, which already meets it |
| [MAM-86 AC-4] a table that fits has no tabindex on its container | PASS | passes at the first run: written after the component, which already meets it |
| [MAM-86 AC-4] a table that fits has no role and no name on its container, so there is no region to announce | PASS | passes at the first run: written after the component, which already meets it |
| [MAM-86 AC-4] a table exactly as wide as its container fits: equal widths are not an overflow | PASS | passes at the first run: written after the component, which already meets it |
| [MAM-86 AC-4] a table one pixel wider than its container overflows | PASS | passes at the first run: written after the component, which already meets it |
| [MAM-86 AC-4] when the sizes change and the observer fires, a table that fitted becomes a Tab stop with role and name | PASS | passes at the first run: written after the component, which already meets it |
| [MAM-86 AC-4] when the sizes change and the observer fires, a table that overflowed stops being a Tab stop and loses role and name | PASS | passes at the first run: written after the component, which already meets it |
| [MAM-86 AC-4] the attributes follow the sizes in both directions on the same element, so that the focus is not lost by a new element | PASS | passes at the first run: written after the component, which already meets it |
| [MAM-86 AC-4] an observer that fires with the sizes unchanged changes nothing | PASS | passes at the first run: written after the component, which already meets it |
| [MAM-86 AC-4] the observer watches the container and the table in it (rows that arrive or a window that changes both change the answer) | PASS | passes at the first run: written after the component, which already meets it |
| [MAM-86 AC-4] the observer is disconnected when the table leaves the page | PASS | passes at the first run: written after the component, which already meets it |
| [MAM-86 AC-4] where the browser has no ResizeObserver and nothing is measured (jsdom: no layout), the container is not a Tab stop | PASS | passes at the first run: written after the component, which already meets it |
| [MAM-86 AC-4] a table that overflows is reached by Tab: the container takes the focus before the link in the first row | PASS | passes at the first run: written after the component, which already meets it |
| [MAM-86 AC-4] a table that fits is not a stop: the first Tab goes straight to the link in the first row | PASS | passes at the first run: written after the component, which already meets it |
| [MAM-86 AC-4] the table is inside the USWDS scrolling container, with its header cells and its data | PASS | passes at the first run: written after the component, which already meets it |
| [MAM-86 AC-4] there is one scrolling container, not two (the USWDS scrollable option of the table is not used as well) | PASS | passes at the first run: written after the component, which already meets it |
| [MAM-86 AC-4] the properties of the table (a caption, a class) reach the table, not the container | PASS | passes at the first run: written after the component, which already meets it |
