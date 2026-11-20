# Red report: MAM-86 accessibility (WCAG 2.1 AA, zero axe violations, keyboard-only walk-through)

Written by the test-writer on 2026-11-20; corrected on 2026-11-20 evening / 2026-11-21 after an independent review (see "Correction round"); changed again on 2026-11-21 night (see "Round of 2026-11-21": the AC-6 label check, new gate cases, unit tests of ScrollableTable) and corrected once more the same night after a review of the label check (see "Correction round of 2026-11-21 night": what the check counts as hidden). This story verifies a UI that was built before it: the 13 screens, the shared shell and the
seven journeys already exist. So the "red" step looks different from a feature story: a test goes red only where the built UI really
breaks the acceptance criterion. Tests that go green at once describe behaviour the UI already has; they are still the regression net for
AC-1 to AC-7 and are protected by the hash file. No production code (`modern/web-ui/src`) was changed.

## Correction round of 2026-11-21 night (about 01:10 to 01:40): what the AC-6 label check counts as hidden (review of the label check, three MUST-FIX items)

This is the newest section. The section after it (round of 00:20 to 00:50) is history, with one correction noted at its top. The test-writer changed no production code (`modern/web-ui/src`), built nothing (no `npm run build`, `dist` untouched) and ran no Gradle and no git command.

### The review and what it found

1. Case (b) of the label rule accepted a header that no sighted user sees, because `shown()` read only the element's own style and size. Both markups passed over `<td><input aria-label="Taxonomy prefix, row 1"></td>`: `<th><span class="usa-sr-only">Taxonomy prefix</span></th>` (the USWDS class of this app: `position:absolute;left:-999em`) and `<thead style="opacity:0">` (opacity is not inherited).
2. Case (a) had the same gap: `<label for="npi" class="usa-sr-only">NPI</label><input id="npi">`, or a label inside `<div style="opacity:0">`, counted as a visible label. The four (vii) cases tested only hiding set on the label itself.
3. Both reports said the check refuses a header that is not shown, text hidden inside the cell and a label that is not shown. That was false for the markups above, and no limit was stated.

### The change of a recorded test (user rule Q-097 C, decision Q-137 A of the lead; dated 2026-11-21 night)

What the check accepted before this round (the version of the round of 00:20 to 00:50, sha256 e17fb573...): an input was labelled when (a) it had a label element with text and a box of at least 2 by 2 pixels not hidden by the label's own style, or (b) it sat in a table cell, the header cell over its column passed the same own-style test and had text, and that text was in the input's accessible name. Anything hidden through an ancestor (opacity 0 on a wrapper, a thead or a row) or by moving the box off the page (usa-sr-only, top -999em, a transform) still counted as seen.

What it accepts now (support/structure.ts: a new test `seen`, used for the label element, the header cell and every element between a text and its cell or label): the same two cases, but an element counts as seen only when all of these hold: its own style and size pass as before; no ancestor has opacity 0 and `checkVisibility` (called with the options of both the old and the new spelling) says it is rendered (this catches display none on an ancestor, a closed details element and content-visibility hidden, which keep a box); and its box is not wholly off the page (right edge at or left of the page origin, or bottom edge at or above it, in page coordinates, so a scrolled page is not "off"). The text of a header or of a label is the text of its parts that are seen (a label that is a box with its only text off the page has no visible text). The messages are unchanged ("is not shown" now means "not seen by a person").

Why: Q-137 A makes the visible column header (or label) the thing a sighted user reads, so the check must refuse a header or label that no sighted user can see. WCAG 2.1: 3.3.2 Labels or Instructions, 2.5.3 Label in Name, 1.3.1 Info and Relationships.

What it still refuses, with the new tests: everything of the first version (an input outside a table named only by aria-label; a name that lacks its column's header text, also by case, no name, aria-labelledby elsewhere, no header cell; an empty header; another column's header; a label element that is not shown), and now also a header or label that is hidden through usa-sr-only, an opacity 0 on an ancestor (wrapper, thead, row), a box above the page, a transform out of the page, a closed details element, content-visibility hidden, or a one-pixel clipping wrapper around its text.

What it does NOT detect (checked in the browser with the checker as it is, by a throw-away probe, not by a recorded test; none of the 13 screens does any of these): text made invisible by its own colour (`color: transparent`, the colour of the background); `font-size: 0` on a cell that keeps its box (on a small inline element the box collapses and is caught); `text-indent: -9999px` in a large box with overflow hidden; text covered by another element; `aria-hidden` (not a visual method). A box far to the right or below the page counts as seen (it can be scrolled to). The inputs that are examined at all are still chosen by the old own-style test, so an input in a transparent wrapper is examined (it errs on the side of reporting). Case (b) still matches the header text as a substring of the name, and an input named through a label that is not seen, under a header that this text contains, passes case (b).

What did not change: every other assertion, the titles and groups of the 28 first-version tests, `pages.a11y.spec.ts` (the AC-6 test of every screen calls the same `structureProblems`), `support/screens.ts` and the other files of the story. Only `gate.a11y.spec.ts` (19 new tests, one `<style>` rule in the helper that builds the small pages, and the header comment) and `support/structure.ts` changed. Hashes recorded again (see "Test protection").

### Result of the runs of this round

| File | Level | Tests | Passed | Failed |
|---|---|---|---|---|
| e2e/a11y/pages.a11y.spec.ts | Playwright, page scans on the live stack (PEM_JOURNEYS=1), build of 2026-11-21 00:19 | 77 | 77 | 0 |
| e2e/a11y/gate.a11y.spec.ts | Playwright, gate (4) and the AC-6 label rule (47: 28 first version, 19 new), default suite | 51 | 51 | 0 |
| e2e/a11y/policy.a11y.spec.ts | Playwright, repository checks, no browser, default suite | 12 | 12 | 0 |
| tests/ux/scrollable-table.test.tsx | Vitest, component (file unchanged, re-run) | 20 | 20 | 0 |
| e2e/a11y/keyboard-journeys.spec.ts | Playwright, keyboard-only journeys | 7 | not run | 7 of 7 passed in the correction round of 2026-11-20 (they do not use the structure checker) |
| Total | | 167 | 160 run, 160 passed | 0 |

Per criterion, by the trace tool's own `tests()` function (imported, run over a scratch copy of the JSON reports; `main()` not run, no file of the tool written), with the 7 journeys added by hand to AC-3: AC-1 19, AC-2 5, AC-3 11, AC-4 34, AC-5 30, AC-6 61 (14 pages and 47 gate), AC-7 7; total 167, all passed in the runs above (160) or in the correction round of 2026-11-20 (the 7 journeys), 0 failed. `npx tsc -b` and `npx eslint . --max-warnings 0` in modern/web-ui: both clean (exit 0). The default suite `npx playwright test e2e/a11y` ran 63 tests (51 gate, 12 repository checks): 63 passed.

### Red evidence: the 19 new tests against the checker of 00:50

The 19 tests were written first and run against the unchanged checker (a copy of it was kept, the checker was changed only afterwards). First run, against the 14 tests written at that point: 9 failed. Of the 5 that passed, 3 are guards (one (i), one (ii), one (v)) and 2 passed by accident because the label had an empty box (an inline label whose only child is positioned absolutely has a size of 0, which the old own-style test already refused); those two were given a box of their own (inline-block, 60 by 20 pixels) so that it is the text that is not seen. Five tests were added later: the scrolled table and the label that sets visibility visible again (guards), and the transform, the closed details element and content-visibility hidden (which needed `checkVisibility`, see the mutants). The checker of 00:50 was then put back for one run (sha256 checked, restored byte for byte afterwards): 14 of the 47 AC-6 cases failed, 33 passed. The 14 are both markups of the review and 12 more hiding cases; the 5 new tests that pass on both versions are guards (listed in the table below). After the change of the checker: 47 of 47 pass. JSON: scratchpad ui-results mam86-gate-old-checker-red2.json (old checker) and mam86-gate-new-green3.json (new).

### The 19 new tests

| Test | Result | Reason |
|---|---|---|
| MAM-86 AC-6 (i) a label that also holds a usa-sr-only part is accepted: the part a person reads is enough | PASS | guard: passes against the checker of 00:50 too; it keeps case (a) from becoming too strict |
| MAM-86 AC-6 (i) a label that sets visibility visible inside a wrapper that is visibility hidden is seen, so it is accepted | PASS | guard: passes against the checker of 00:50 too; it keeps case (a) from becoming too strict |
| MAM-86 AC-6 (ii) a usa-sr-only suffix of the header is not part of what a person reads, so the name need not contain it | PASS | against the checker of 00:50 it FAILS: it keeps the hidden suffix in the header text ("Tier (required)"), so the name "Tier, row 1" does not contain it and a correct input is refused |
| MAM-86 AC-6 (ii) header text that starts a little left of the page edge but reaches into the page is still seen | PASS | guard for the new off-page test: passes against the checker of 00:50 (no such test there); fails under the mutants that break the test (see the mutants) |
| MAM-86 AC-6 (ii) a table scrolled out of the window (above and to the left of it) is still on the page: its header is seen | PASS | guard for the new off-page test: passes against the checker of 00:50 (no such test there); fails under the mutants that break the test (see the mutants) |
| MAM-86 AC-6 (v) a table input under a header whose only text is in a usa-sr-only element (moved off the page) is refused | PASS | against the checker of 00:50 it FAILS: the checker reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (v) a table input under a header whose only text is moved above the page (top -999em) is refused | PASS | against the checker of 00:50 it FAILS: the checker reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (v) a table input under a header whose text sits in a wrapper that is clipped to one pixel is refused | PASS | guard: passes against the checker of 00:50 too (it already skipped an element that is not shown); keeps the walk of the text honest |
| MAM-86 AC-6 (v) a table input under a header cell whose opacity 0 sits on the thead (opacity is not inherited, but nobody sees the cell) is refused | PASS | against the checker of 00:50 it FAILS: the checker reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (v) a table input under a header cell inside a table row with opacity 0 is refused | PASS | against the checker of 00:50 it FAILS: the checker reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label is not seen by a person is refused: the usa-sr-only class on the label (moved off the page) | PASS | against the checker of 00:50 it FAILS: the checker reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label is not seen by a person is refused: its only text in a usa-sr-only element inside the label | PASS | against the checker of 00:50 it FAILS: the checker reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label is not seen by a person is refused: the label inside a wrapper with opacity 0 (opacity is not inherited, but nobody sees the label) | PASS | against the checker of 00:50 it FAILS: the checker reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label is not seen by a person is refused: the label inside a wrapper with opacity 0, two levels up | PASS | against the checker of 00:50 it FAILS: the checker reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label is not seen by a person is refused: the label moved above the page (top -999em) | PASS | against the checker of 00:50 it FAILS: the checker reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label is not seen by a person is refused: the label moved out of the page by a transform (translateX -5000px) | PASS | against the checker of 00:50 it FAILS: the checker reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label is not seen by a person is refused: the label inside a closed details element (an accordion that is not open) | PASS | against the checker of 00:50 it FAILS: the checker reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label is not seen by a person is refused: the label inside a wrapper with content-visibility hidden | PASS | against the checker of 00:50 it FAILS: the checker reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label is not seen by a person is refused: the text of the label in a wrapper clipped to one pixel | PASS | against the checker of 00:50 it FAILS: the checker reports no problem, so an input that nobody can label by sight is accepted |

### Mutants of the new `seen` test (the checker is changed for one run and restored byte for byte, sha256 checked)

Twelve mutants, each run against the AC-6 gate cases, all killed. Counts of failing tests are those of the run (the first eleven ran against 43 tests; the survivor was run again and the twelfth was run against 47, after the closed-details and content-visibility cases were added): no ancestor opacity test (4 fail); no off-page test (6); the off-page test ignores the bottom edge (2); ignores the right edge (4); any part of the box off the page counts as off (1); the window is used, not the page (1, the scrolled table); the text of a header is walked with `shown` instead of `seen` (4); the header cell is judged with `shown` (2); the label is judged with `shown` (4); the text of a label is its textContent (2); an ancestor with visibility hidden is treated as hiding (1, the label that sets visibility visible again); `checkVisibility` not called (first 0 failed: the mutant SURVIVED, because no test had a box that is rendered as nothing; after the closed-details and content-visibility tests were added: 2 fail). Evidence that `checkVisibility` is needed: in Chromium 159 a label in a closed details element or under content-visibility hidden has a box of 26 by 17 pixels and `checkVisibility` is false.

### Test protection

`python modern/tools/checks/test_protection.py record MAM-86 <the 12 files>` was run again for the 12 files of the story (printed: recorded 12 test files). Only two lines of the hash file and of `red-blobs.txt` changed: `e2e/a11y/gate.a11y.spec.ts` and `e2e/a11y/support/structure.ts`; the other 10 files are byte-identical to the round of 00:20 to 00:50. Afterwards `check MAM-86` printed OK (12 test files unchanged, no unrecorded test files), `check MAM-92` OK (3 files) and `check MAM-80` OK (3 files). Line endings kept (the working files are CRLF; the edit tool wrote them back unchanged in kind: all lines of both files end in CRLF).

### Runs of this round

Procedure: new gate cases (no stack) first against the checker of 00:50, then against the new one, then the mutants (no stack); under the stack lock (taken 01:31:32, released 01:35:13; seed before the run and again before the release): the whole pages file (77 passed, 1.6 min) and `npx playwright test e2e/a11y` (63 passed, 40 s); then Vitest (20 passed), tsc and eslint. Free memory: 1724 MB before the first browser run (the red, green and mutant runs followed without a second reading), 1580 MB before taking the lock, 1431 MB before the page scans (the default suite ran right after without a new reading). Live pages: the stricter test reads 32 inputs on 7 screens (portal.apply 8, console.provider-search 3, console.approval 2, console.record-sanction 4, console.risk-rules 11, console.batch-jobs 2, public.directory 2) and reports no problem. Not run: the seven keyboard journeys (they do not use the structure checker).

## Round of 2026-11-21 (00:20 to 00:50): the AC-6 label check brought in line with its criterion (Q-137 A), 28 new gate cases, 20 unit tests of ScrollableTable

The sections below this one describe the rounds of 2026-11-20 and stay as they were written (history). This section is the state at 00:50.
Correction of 2026-11-21 night: two statements of this section, that the check refuses a header cell that is "not shown" and a label element that is "not shown" (with the hiding methods named in brackets), were too broad. The check of this round judged only the element's own style and size, so a header or label hidden through an ancestor or moved off the page was still accepted. The section above fixes the check and states what it detects and what it does not.
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

#### a11y/gate.a11y.spec.ts, the 19 tests added in the correction round of 2026-11-21 night (what counts as hidden; no stack needed). Result against the checker of 00:50: FAIL for 14, PASS for 5 guards; against the new checker: all PASS

| Test | Result | Reason |
|---|---|---|
| MAM-86 AC-6 (i) a label that also holds a usa-sr-only part is accepted: the part a person reads is enough | PASS | guard: passes against the checker of 00:50 too; it keeps case (a) from becoming too strict |
| MAM-86 AC-6 (i) a label that sets visibility visible inside a wrapper that is visibility hidden is seen, so it is accepted | PASS | guard: passes against the checker of 00:50 too; it keeps case (a) from becoming too strict |
| MAM-86 AC-6 (ii) a usa-sr-only suffix of the header is not part of what a person reads, so the name need not contain it | PASS | against the checker of 00:50 it FAILS: it keeps the hidden suffix in the header text ("Tier (required)"), so the name "Tier, row 1" does not contain it and a correct input is refused |
| MAM-86 AC-6 (ii) header text that starts a little left of the page edge but reaches into the page is still seen | PASS | guard for the new off-page test: passes against the checker of 00:50 (no such test there); fails under the mutants that break the test (see the mutants) |
| MAM-86 AC-6 (ii) a table scrolled out of the window (above and to the left of it) is still on the page: its header is seen | PASS | guard for the new off-page test: passes against the checker of 00:50 (no such test there); fails under the mutants that break the test (see the mutants) |
| MAM-86 AC-6 (v) a table input under a header whose only text is in a usa-sr-only element (moved off the page) is refused | PASS | against the checker of 00:50 it FAILS: the checker reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (v) a table input under a header whose only text is moved above the page (top -999em) is refused | PASS | against the checker of 00:50 it FAILS: the checker reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (v) a table input under a header whose text sits in a wrapper that is clipped to one pixel is refused | PASS | guard: passes against the checker of 00:50 too (it already skipped an element that is not shown); keeps the walk of the text honest |
| MAM-86 AC-6 (v) a table input under a header cell whose opacity 0 sits on the thead (opacity is not inherited, but nobody sees the cell) is refused | PASS | against the checker of 00:50 it FAILS: the checker reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (v) a table input under a header cell inside a table row with opacity 0 is refused | PASS | against the checker of 00:50 it FAILS: the checker reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label is not seen by a person is refused: the usa-sr-only class on the label (moved off the page) | PASS | against the checker of 00:50 it FAILS: the checker reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label is not seen by a person is refused: its only text in a usa-sr-only element inside the label | PASS | against the checker of 00:50 it FAILS: the checker reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label is not seen by a person is refused: the label inside a wrapper with opacity 0 (opacity is not inherited, but nobody sees the label) | PASS | against the checker of 00:50 it FAILS: the checker reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label is not seen by a person is refused: the label inside a wrapper with opacity 0, two levels up | PASS | against the checker of 00:50 it FAILS: the checker reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label is not seen by a person is refused: the label moved above the page (top -999em) | PASS | against the checker of 00:50 it FAILS: the checker reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label is not seen by a person is refused: the label moved out of the page by a transform (translateX -5000px) | PASS | against the checker of 00:50 it FAILS: the checker reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label is not seen by a person is refused: the label inside a closed details element (an accordion that is not open) | PASS | against the checker of 00:50 it FAILS: the checker reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label is not seen by a person is refused: the label inside a wrapper with content-visibility hidden | PASS | against the checker of 00:50 it FAILS: the checker reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label is not seen by a person is refused: the text of the label in a wrapper clipped to one pixel | PASS | against the checker of 00:50 it FAILS: the checker reports no problem, so an input that nobody can label by sight is accepted |

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
