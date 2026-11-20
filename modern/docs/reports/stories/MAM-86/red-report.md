# Red report: MAM-86 accessibility (WCAG 2.1 AA, zero axe violations, keyboard-only walk-through)

Written by the test-writer on 2026-11-20; corrected on 2026-11-20 evening / 2026-11-21 after an independent review (see "Correction round"); changed again on 2026-11-21 night (see "Round of 2026-11-21": the AC-6 label check, new gate cases, unit tests of ScrollableTable) and corrected once more the same night after a review of the label check (see "Correction round of 2026-11-21 night": what the check counts as hidden), and a second time after a second review (see "Second correction round of 2026-11-21 night": the exact statement of what the check detects and the exact list of what it does not). This story verifies a UI that was built before it: the 13 screens, the shared shell and the
seven journeys already exist. So the "red" step looks different from a feature story: a test goes red only where the built UI really
breaks the acceptance criterion. Tests that go green at once describe behaviour the UI already has; they are still the regression net for
AC-1 to AC-7 and are protected by the hash file. No production code (`modern/web-ui/src`) was changed.

## Second correction round of 2026-11-21 night (about 01:55 to 04:00): what the AC-6 label check counts as hidden, second review (four MUST-FIX items, two SHOULD-FIX notes)

This is the newest section. The sections after it are history; two passages of the section of 01:10 to 01:40 that were wrong are corrected in place (its paragraph on what the check does not detect, and its sentence on line endings under "Test protection"). The test-writer changed no production code (`modern/web-ui/src`), built nothing (no `npm run build`, `dist` untouched: files dated 2026-11-21 00:19:47), ran no Gradle and no git command. The commit 6e24725 of 03:22:58 was not made by the test-writer; see "Notes" at the end of this section.

### The review (01:53) and what it found

1. `seen()` did not judge clipping by an ancestor. A label inside `<div style="position:absolute;width:1px;height:1px;overflow:hidden">` or inside `<div style="height:0;overflow:hidden">` passed, and so did a header cell with `clip-path: inset(50%)`.
2. `visibleText` walked into form controls, so the content of a textarea (or the options of a select) inside a label counted as the label's text: `<label><textarea id="note">Write the reason here</textarea></label>` passed with no visible label.
3. `accessibility-report.md` (about lines 105 and 272) and this report (about line 28) said more than is true about what the check detects; their "not detected" lists left out ancestor clipping, clip-path, filter opacity(0) and control content inside a label.
4. This report (about line 79) said that `gate.a11y.spec.ts` and `support/structure.ts` are CRLF. They are LF only (as committed; `git ls-files --eol` says i/lf w/lf, and a byte count finds no CR). The line endings were kept; the sentence was false.
5. Notes (cheap, close stated limits): text with a colour of alpha 0 (transparent text), and font size 0 on the element that holds the text.

### The change of a recorded test (user rule Q-097 C, decision Q-137 A of the lead; dated 2026-11-21 night; no assertion is weakened or removed; the check is stricter everywhere except one place, stated below)

What the check refused before this round (the version of the first correction, 01:40, sha256 d0a314f1...): a label or header cell that is not seen by the test of that version (own style and size, opacity 0 on an ancestor, `checkVisibility`, a box wholly off the page), and text parts of a label or header that fail the same test. It did NOT refuse (the red run below shows each): a label or header cell inside a wrapper that clips it to under 2 by 2 pixels (overflow hidden on a 1 pixel box, a box of height 0 or width 0, an overflow auto box of 1 pixel in one or both dimensions, a text pushed out of the box that clips it, the legacy `clip` rectangle on a wrapper), a clip-path on the element or on an ancestor, filter opacity(0) on the element or on an ancestor, text whose colour has alpha 0, text in an element with font size 0, and a label whose only text is the content of a textarea, a select or a button inside it.

What it refuses now: everything of the statement below. The statement replaces every earlier sentence about what the check detects.

What did not change: the 47 earlier AC-6 gate cases (same titles, same assertions; all 47 pass on the old and on the new checker), `pages.a11y.spec.ts` (its AC-6 test of every screen calls the same `structureProblems`), `support/screens.ts` and the other files of the story. Only `gate.a11y.spec.ts` (54 new tests, no test changed, the header comment extended) and `support/structure.ts` changed. Hashes recorded again (see "Test protection").

Stricter everywhere except one place, by design of this correction round (decided by the lead and recorded under Q-137 on 2026-11-21 04:47; it follows Q-137 A, where the readable text of the header is the visible label, like the display:none suffix guard): the header text that case (b) looks for in the name is now only the readable text outside controls, so a header with a control or unreadable text beside its readable text passes (b) when the name holds the readable part. Example (from reading the code, not run): `<th>Taxonomy prefix <button>Sort</button></th>` over an input named "Taxonomy prefix, row 1" is refused by the checker of 01:40 (its header text includes "Sort") and accepted by the current one. Searched by the lead on 2026-11-21 04:45: of the 22 header cells in `modern/web-ui/src` only the sortable header (`shared/ui/sorting.tsx:71`) holds a control, and all of its text is inside that button, so no header of the application has readable text beside a control; headers with unreadable text beside readable text were not searched for.

#### What the AC-6 label check detects (exact statement; `e2e/a11y/support/structure.ts`, state after the second correction round of 2026-11-21 night)

An input counts as labelled when (a) one of its label elements is seen and holds text that can be read, or (b) it sits in a table cell, the header cell over its column is seen and holds text that can be read, and that text (trimmed, case kept) is part of the input's accessible name (aria-labelledby, else aria-label, else the label elements). An element (a label element, a header cell, or an element inside one of them) is NOT seen when any one of these holds:

1. It has display none, or an ancestor has display none or content-visibility hidden, or it is inside a closed details element, or it has visibility hidden (the browser's `checkVisibility` says it is not rendered).
2. It or an ancestor has opacity 0, or a filter that contains opacity(0) (also opacity(0%)).
3. It or an ancestor has a clip-path other than none (a decorative clip-path counts too, so the check reports it), or has the old `clip` property, on an absolutely or fixed positioned box, with a rectangle under 2 pixels wide whose left and right edges are both lengths, or under 2 pixels high whose top and bottom edges are both lengths (an edge given as auto is not compared, so the width or height it belongs to is not judged: item 11 of the list below).
4. Its own box is under 2 pixels wide or under 2 pixels high.
5. The part of its box that its clipping ancestors leave is under 2 pixels wide or under 2 pixels high. An ancestor with overflow hidden or clip cuts the box (on the axis that sets it). An ancestor with overflow auto or scroll is a scroll container: its content counts as reachable by scrolling, so only a scroll container that is itself under 2 pixels wide or high hides what is in it. An absolutely positioned box is cut only by its containing block and the boxes above it, not by a wrapper in between. As containing block the check counts a box with a position other than static (not for a fixed box), or with a transform, translate, rotate, scale, perspective or filter other than none, or with contain layout, paint, strict or content; a wrapper that is the containing block only through will-change, backdrop-filter or container-type is taken for a wrapper in between, so its overflow does not cut the box (from reading the code, not probed).
6. Its box lies wholly off the page: its right edge is at or left of the left edge of the page, or its bottom edge is at or above the top edge of the page (usa-sr-only, top -999em, a transform that throws it out of the page). The page scroll is added, so a table scrolled out of the window is still on the page.

A text counts as read only when the element that holds it has a font size above 0 and a text colour with an alpha above 0, that element and every element between it and the label or header cell are seen, and it is not inside a textarea, select, option, input or button (what those hold is their content, not the text of the label).

#### What the AC-6 label check does NOT detect (exact list)

Items 1 to 8 were each run through the check by a throw-away probe (not a recorded test, so they are not pinned by the hash file): the check accepted the label. Items 11 and 12 were found by reading the code in the final review (2026-11-21 04:44) and were not probed. Whether any of the 13 screens uses one of these methods was not searched for in the source: the page tests pass, which proves only that the check finds no problem.

1. Text in the colour of its background (contrast is the business of AC-5), and a text colour with an alpha above 0 or a font size above 0 that is too faint or too small to read (probed: alpha 0.01, font size 0.1px in a box of its own).
2. Text moved or cut inside the box of the element that holds it: `text-indent: -9999px` in a box with overflow hidden, the overflow of the element itself cutting its own text, a box of 2 by 2 pixels or more that shows only a sliver of its text (probed: a box 60 by 2 pixels). Only the overflow of ancestors is judged.
3. Text covered by another element (a positioned overlay with a background and a z-index).
4. CSS that draws nothing but keeps the box and the colour: `mask-image` and `-webkit-text-fill-color: transparent` (probed). Any other property that does so and is not named in the statement above is not detected either.
5. `aria-hidden="true"`: it hides from assistive technology, not from the eye, so it is not a visual method.
6. A box far to the right of the page or far below it, and content of a scroll container (overflow auto or scroll) that is scrolled out of the container's view: counted as seen, because it can be scrolled to.
7. A position:fixed box above the window while the page is scrolled down (the page scroll is added to every box).
8. The overflow of the body element when the html element sets overflow too (the overflow of html and body is skipped as the page's own; probed), and the overflow of a table box (display table or inline-table), which is skipped; whether the browser clips by a table box with overflow hidden was not tried.
9. Which inputs are examined: an input is examined when it, or one of its labels, has a box of at least 2 by 2 pixels and its own style does not hide it, so an input inside a hidden wrapper is examined and, with no label that is seen, reported (it errs on the side of reporting).
10. Case (b) matches the header text as a substring of the accessible name (the name "Tiered, row 1" holds the header "Tier"), and an input named through a label element that is not seen, under a header whose text its name contains, passes case (b).
11. The old `clip` property with an edge given as auto: an auto edge is not compared, so the width (left and right edges) or the height (top and bottom edges) that it belongs to is not judged; a rectangle with an auto edge in both pairs (for example `rect(auto, auto, 0, auto)` or `rect(0, 0, auto, auto)`) is not judged at all, while `rect(0, auto, 0, 0)` is still refused by its height of 0.
12. Other values of properties the statement names: an opacity or filter opacity() above 0 that is too faint to see (for example 0.01), any other filter function (`blur()`, `brightness()`, `contrast(0)`, `url()`), a colour or a font size set on `::first-line` or `::first-letter` (only the element's own computed style is read), content-visibility hidden on the label itself or on the element inside a label or header cell that holds the text (only an ancestor's content-visibility is judged; such an element keeps a box, for example through padding or `contain-intrinsic-size`, and its own text is counted though it is not drawn), and a transform that shrinks a box (a scale) but leaves it 2 by 2 pixels or more, so that its text is drawn too small to read.

In these cases the check reports a label or header cell that a person can read (it errs on the side of reporting; probed, and the button caption in a label by a gate test): a label whose only text is generated by CSS (`::before`, `::after`), the alt text of an image, or the caption of a button inside it; a header cell whose only text is the caption of a button inside it, which is the sortable header of the application (`shared/ui/sorting.tsx:71`), so an input in a sortable column can be labelled only by case (a) (from reading the code, not probed; no table with a sortable header holds an input today); and any clip-path other than none, even one that clips nothing (`inset(0)`).

### Result of the runs of this round

| File | Level | Tests | Passed | Failed |
|---|---|---|---|---|
| e2e/a11y/pages.a11y.spec.ts | Playwright, page scans on the live stack (PEM_JOURNEYS=1), build of 2026-11-21 00:19 | 77 | 77 | 0 |
| e2e/a11y/gate.a11y.spec.ts | Playwright, gate (4) and the AC-6 label rule (101: 28 first version, 19 first correction, 54 new), default suite | 105 | 105 | 0 |
| e2e/a11y/policy.a11y.spec.ts | Playwright, repository checks, no browser, default suite | 12 | 12 | 0 |
| tests/ux/scrollable-table.test.tsx | Vitest, component (file unchanged, re-run) | 20 | 20 | 0 |
| e2e/a11y/keyboard-journeys.spec.ts | Playwright, keyboard-only journeys | 7 | not run | 7 of 7 passed in the correction round of 2026-11-20 (they do not use the structure checker) |
| Total | | 221 | 214 run, 214 passed | 0 |

Per criterion, by the trace tool's own `tests()` function (imported, run over a scratch copy of the JSON reports in the session scratchpad; `main()` not run, no file of the tool written), with the 7 journeys added by hand to AC-3: AC-1 19, AC-2 5, AC-3 11, AC-4 34, AC-5 30, AC-6 115 (14 pages and 101 gate), AC-7 7; total 221, all passed in the runs above (214) or in the correction round of 2026-11-20 (the 7 journeys), 0 failed. `npx tsc -b` and `npx eslint . --max-warnings 0` in modern/web-ui: both exit 0. The default suite `npx playwright test e2e/a11y` ran 117 tests (105 gate, 12 repository checks): 117 passed (1.6 min). The page scans: 77 passed (2.5 min). A real page did not fail: no input of the 13 screens relies on a label or header that the check counts as not seen.

### Red evidence: the 54 new tests against the checker of 01:40

The 54 tests were written first and run against the unchanged checker (sha256 d0a314f11488602ab3f9eda658d655182cb1a057860f37df287596b39cbc6a69, the version of the first correction; the new checker was written only afterwards; the first red run, of 96 cases, ran before the checker was touched, and for the final run of 101 cases the old file was put back from a copy and the new one restored byte for byte, sha256 checked both times). Final run of all 101 AC-6 cases against it: 62 passed, 39 failed. The 47 earlier cases all pass; of the 54 new ones 39 are red and 15 green. Every one of the 39 fails for the expected reason, the same message: `the checker must report exactly one problem that names input "<id>"; all problems: []`, that is, a label or header cell that nobody sees (or reads) is accepted. The 15 green: 14 are guards by design (they must stay accepted, and they keep the stricter test from over-refusing), and one is a refused case that the old checker already refused: a select shown as a drop-down inside a label (the options of a drop-down have no box in Chromium, so the old test of the box already refused it; the list-box variant of the same case is red).
Two corrections of my own tests during the red step, kept for honesty: (1) the guard "a label whose text sits in an element with a normal font size inside an element with font-size 0" failed against the old checker in its first form, because the outer inline element with font size 0 has a box of height 0 and was therefore not seen by either version; it was given a box of its own (inline-block, 60 by 20 pixels), then it passes on both. (2) Four cases were added after the first batch of mutants (see the mutants): two refused cases of an overflow auto box that is one pixel wide or one pixel high only, and two guards (display contents; overflow hidden on the body element).
After the change of the checker: 101 of 101 pass. JSON files in the session scratchpad (`ui-results`): mam86-gate-r2-old-checker-red-final.json (old checker, 101 cases: 62 passed, 39 failed), mam86-gate-r2-new-green-final.json (new checker: 101 passed), and the earlier runs mam86-gate-r2-old-checker-red.json (96 cases, 59 passed, 37 failed, before the cases listed under (2) and before the guard of (1) was repaired), mam86-gate-r2-old-checker-guard-fix.json, mam86-gate-r2-old-checker-scrollbox.json, mam86-gate-r2-new-green1.json, -green2.json, -scrollbox.json.

### The 54 new tests

| Test | Result | Reason |
|---|---|---|
| MAM-86 AC-6 (i) a label inside a wrapper with overflow hidden that is large enough to show it is accepted | PASS | guard: passes against the checker of 01:40 too; it keeps the stricter test from refusing what a person can see |
| MAM-86 AC-6 (i) a label that its wrapper cuts but whose first letters show is accepted (only a clip to under 2 by 2 pixels hides it) | PASS | guard: passes against the checker of 01:40 too; it keeps the stricter test from refusing what a person can see |
| MAM-86 AC-6 (i) a label beyond the edge of a wrapper that scrolls (overflow auto) can be scrolled to, so it is accepted | PASS | guard: passes against the checker of 01:40 too; it keeps the stricter test from refusing what a person can see |
| MAM-86 AC-6 (i) overflow hidden on an inline wrapper clips nothing, so the label inside it is accepted | PASS | guard: passes against the checker of 01:40 too; it keeps the stricter test from refusing what a person can see |
| MAM-86 AC-6 (i) overflow hidden on a wrapper that has no box of its own (display contents) clips nothing, so the label inside it is accepted | PASS | guard: passes against the checker of 01:40 too; it keeps the stricter test from refusing what a person can see |
| MAM-86 AC-6 (i) overflow hidden on the body element belongs to the page, not to the body box: a label below the end of the body box is accepted | PASS | guard: passes against the checker of 01:40 too; it keeps the stricter test from refusing what a person can see |
| MAM-86 AC-6 (i) an absolutely positioned label is not clipped by an overflow hidden wrapper that is not its containing block | PASS | guard: passes against the checker of 01:40 too; it keeps the stricter test from refusing what a person can see |
| MAM-86 AC-6 (i) a label that has its own text and also wraps a textarea is accepted: the text a person reads is "Reason" | PASS | guard: passes against the checker of 01:40 too; it keeps the stricter test from refusing what a person can see |
| MAM-86 AC-6 (i) a label with a partly transparent colour (alpha 0.5) is accepted: only alpha 0 hides the text | PASS | guard: passes against the checker of 01:40 too; it keeps the stricter test from refusing what a person can see |
| MAM-86 AC-6 (i) a label inside a wrapper with filter opacity(0.5) is accepted: only opacity 0 hides it | PASS | guard: passes against the checker of 01:40 too; it keeps the stricter test from refusing what a person can see |
| MAM-86 AC-6 (i) a label whose text sits in an element with a normal font size inside an element with font-size 0 is accepted: the element that holds the text is judged | PASS | guard: passes against the checker of 01:40 too; it keeps the stricter test from refusing what a person can see |
| MAM-86 AC-6 (ii) a header with a normal colour is accepted | PASS | guard: passes against the checker of 01:40 too; it keeps the stricter test from refusing what a person can see |
| MAM-86 AC-6 (ii) a table inside a wrapper with overflow hidden that is large enough: its header is seen | PASS | guard: passes against the checker of 01:40 too; it keeps the stricter test from refusing what a person can see |
| MAM-86 AC-6 (ii) a table wider than its scrolling wrapper (overflow-x auto): the header of the column beyond the edge is seen, as it can be scrolled to | PASS | guard: passes against the checker of 01:40 too; it keeps the stricter test from refusing what a person can see |
| MAM-86 AC-6 (v) a table input under a header cell that nobody sees is refused, though the header text is in its name: header with clip-path inset(50%) on the header cell | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (v) a table input under a header cell that nobody sees is refused, though the header text is in its name: header with clip-path inset(50%) on the thead (an ancestor of the header cell) | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (v) a table input under a header cell that nobody sees is refused, though the header text is in its name: header with filter opacity(0) on the header cell | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (v) a table input under a header cell that nobody sees is refused, though the header text is in its name: header with filter opacity(0) on the thead (an ancestor of the header cell) | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (v) a table input under a header cell whose table is clipped away by its wrapper is refused: a wrapper one pixel wide and high with overflow hidden | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (v) a table input under a header cell whose table is clipped away by its wrapper is refused: a wrapper of height 0 with overflow hidden | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (v) a table input under a header cell whose table is clipped away by its wrapper is refused: a wrapper of width 0 with overflow hidden | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (v) a table input under a header cell whose table is clipped away by its wrapper is refused: a wrapper with the clip property at rect(0,0,0,0) | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (v) a table input under a header cell whose table is clipped away by its wrapper is refused: a wrapper with clip-path inset(50%) | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (v) a table input under a header cell whose text nobody can read is refused: the colour transparent on the header cell | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (v) a table input under a header cell whose text nobody can read is refused: a colour with alpha 0 on the header cell | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (v) a table input under a header cell whose text nobody can read is refused: font-size 0 on the header cell (its padding keeps its box) | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (v) a table input under a header cell whose text nobody can read is refused: the text in a span with clip-path inset(50%) | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (v) a table input under a header cell whose text nobody can read is refused: the text in a span with filter opacity(0) | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (v) a table input under a header cell whose text nobody can read is refused: the text in a span with the colour transparent | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (v) a table input under a header cell whose text nobody can read is refused: the text in a box of its own (inline-block) with font-size 0 | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (v) a table input under a header cell whose text nobody can read is refused: the text pushed out of the box that clips it (a wrapper 100 by 20 pixels with overflow hidden, the text moved 300 pixels to the right) | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label nobody sees or reads is refused: the label inside a wrapper one pixel wide and high with overflow hidden | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label nobody sees or reads is refused: the label inside a wrapper of height 0 with overflow hidden | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label nobody sees or reads is refused: the label inside a wrapper of width 0 with overflow hidden | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label nobody sees or reads is refused: the label inside a wrapper one pixel wide and high with overflow auto (a scroll container too small to show anything) | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label nobody sees or reads is refused: the label inside a wrapper one pixel wide and 100 high with overflow auto (a scroll container with no width) | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label nobody sees or reads is refused: the label inside a wrapper 200 wide and one pixel high with overflow auto (a scroll container with no height) | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label nobody sees or reads is refused: the label two levels inside a wrapper one pixel wide and high with overflow hidden | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label nobody sees or reads is refused: the label pushed out of the box that clips it (a wrapper 100 by 20 pixels with overflow hidden, the label moved 300 pixels to the right) | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label nobody sees or reads is refused: the label inside a wrapper with the clip property at rect(0,0,0,0) | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label nobody sees or reads is refused: clip-path inset(50%) on the label | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label nobody sees or reads is refused: clip-path inset(50%) on a wrapper of the label | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label nobody sees or reads is refused: filter opacity(0) on the label | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label nobody sees or reads is refused: filter opacity(0) on a wrapper of the label | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label nobody sees or reads is refused: the colour transparent on the label | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label nobody sees or reads is refused: a colour with alpha 0 on the label | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label nobody sees or reads is refused: font-size 0 on the label (it has a box of its own) | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label nobody sees or reads is refused: its only text in a box of its own (inline-block) with font-size 0 inside the label | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label nobody sees or reads is refused: its only text in a span with the colour transparent inside the label | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label nobody sees or reads is refused: its only text in a span with clip-path inset(50%) inside the label | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label nobody sees or reads is refused: no text of its own and a textarea with content inside it | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label nobody sees or reads is refused: no text of its own and a select (a list box of three rows) inside it | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |
| MAM-86 AC-6 (vii) an input whose label nobody sees or reads is refused: no text of its own and a select (a drop-down) inside it | PASS | passes against the checker of 01:40 too: the options of a drop-down have no box in Chromium, so the old test already refused it; kept as the pair of the list-box case |
| MAM-86 AC-6 (vii) an input whose label nobody sees or reads is refused: its only text in a button inside the label | PASS | against the checker of 01:40 it FAILS: it reports no problem, so an input that nobody can label by sight is accepted |

### Mutants of the new code (the checker is changed for one run and restored byte for byte, sha256 checked after each)

Twenty-one mutants, each run against the AC-6 gate cases, all killed in the end. The counts are the failing tests of the run. M1 to M20 ran against the 97 cases that existed at that time, M6, M19, M20 and M21 were run again against the 101 cases after the cases were added. The mutants: M1 no ancestor clipping at all (10 fail); M2 the x axis is not cut (4); M3 the y axis is not cut (2); M4 overflow auto and scroll cut the box like hidden, no scroll container exception (2); M5 every ancestor counts as containing block, so an absolute box is cut by any wrapper (1); M6 overflow applies to inline and display contents boxes too (first 0 fail: it SURVIVED, because the inline guard is equivalent when the box of the inline element is the box of its content; killed by the new display contents guard: 1); M7 the element itself is not asked for clip-path, filter or clip, only its ancestors (7); M8 clip-path not checked (7); M9 filter opacity(0) not checked (5); M10 the clip rectangle not checked (2); M11 font size not checked (4); M12 colour alpha not checked (6); M13 a textarea is read (1); M14 a button is read (1); M15 a select and its options are read (1); M16 the limit is under 1 pixel, not under 2 (1; the cases that sit at exactly one pixel are decided by the rounding of fractional positions, so the count is not stable); M17 the alpha of the rgba comma form is not read (6); M18 the colour and size of a text are not asked (10); M19 the html and body elements clip too (first 0 fail: it SURVIVED; killed by the new guard on the body element: 1); M20 a scroll container under 2 pixels wide is not refused (first 0 fail: it SURVIVED, because the test box was one pixel in both dimensions and the other axis refused it; killed by the new cases with one dimension only: 1); M21 a scroll container under 2 pixels high is not refused (1, same new case). Three mutants survived the first batch (M6, M19, M20); the cases above were added for them, M21 (the y-axis twin of M20) was written, and the four were run again. JSON in the scratchpad: mam86-gate-r2-mutant-M1.json to -M21.json.

### Test protection

`modern/kag/.venv/Scripts/python.exe modern/tools/checks/test_protection.py record MAM-86 <the 12 files>` was run again for the 12 files of the story (printed: recorded 12 test files for MAM-86). Only two lines of the hash file and of `red-blobs.txt` changed: `e2e/a11y/gate.a11y.spec.ts` (972e7bb8... to 8b8e5fae...) and `e2e/a11y/support/structure.ts` (d0a314f1... to aefdd63e...); the other 10 files are byte-identical to the round of 01:10 to 01:40. The hash file `modern/docs/reports/stories/MAM-86.tests.sha256` now has the sha256 42b9620a1e1e147504acdb2e06068239161f051415e9d099a841d81c893dcd00. Afterwards `check MAM-86` printed OK (12 test files unchanged, no unrecorded test files), `check MAM-92` OK (3 files) and `check MAM-80` OK (3 files). Line endings kept: the working files are LF only, as committed (a byte count finds no CR in `gate.a11y.spec.ts`, `support/structure.ts`, the hash file or `red-blobs.txt`; `git ls-files --eol` says i/lf w/lf for both spec files and both reports). The sentence of the round of 01:10 to 01:40 that said they are CRLF was false.

### Runs of this round

Procedure: new gate cases first, against the unchanged checker (no stack), then the checker, then the AC-6 gate cases green, then the mutants (no stack), the probe of what the check does not detect (no stack), then under the stack lock (taken 03:32:55, released 03:38:31; seed before the run and again before the release): the whole pages file (77 passed, 03:33:21 to 03:36:10) and `npx playwright test e2e/a11y` (117 passed, 03:36:18 to 03:38:11); then Vitest (20 passed), tsc and eslint (both exit 0). The gate cases need no stack and no lock. One comment of `support/structure.ts` (the one above the list of skipped boxes) was edited after the page scans and the default suite; after it the 101 AC-6 gate cases (101 passed, mam86-gate-r2-new-green-last.json), the 12 repository checks (12 passed), tsc and eslint (both exit 0) were run again, the page scans and the 4 other gate tests were not. Free memory was read before every browser run by a helper that waits 60 seconds and reads again when the value is under 1024 MB: 1086 MB before the first red run (after readings of 693, 1018, 325, 235 and 1170 MB), 1276, 1312 and 1165 MB before three of the later gate runs, 1217 MB before the page scans, 1331 MB before the default suite, 1556 MB before tsc, 1774 MB before Vitest; the mutant runs read 1050 MB and more before each mutant. Exception, stated openly: one single-test run (the repaired guard, against the old checker) was started at 728 MB, because the reading and the start were in one command that did not wait; it completed normally. Not run: the seven keyboard journeys (they do not use the structure checker).

The probe of what the check does not detect (a scratch Playwright spec in the session scratchpad, with a junction to the node_modules of the web UI that was removed again, not a recorded test, not pinned by the hash file): 13 hiding methods are accepted by the check (listed in the list above: colour equal to the background, alpha 0.01, font size 0.1px, text-indent -9999px, a box 60 by 2 pixels, a covering element, `-webkit-text-fill-color`, `mask-image`, `aria-hidden`, a box far right, a box far below, a scroll container scrolled away, overflow on a table row group, which is skipped; the browser's behaviour for it was not tried), and the overflow on html and body together and a fixed box above the window are accepted too; 4 readable labels are refused (CSS generated content, an image with alt text, a clip-path `inset(0)` on the label and on a wrapper). Results: scratchpad `ui-results/mam86-r2-probe.json` and `mam86-r2-probe2.json`.

### Notes

- Commit 6e24725 (2026-11-21 03:22:58) was not made by the test-writer. Its content shows that it was made while mutant M19 was applied to `e2e/a11y/support/structure.ts` for one run, so HEAD holds the loop line `for (let up = node.parentElement; up !== null; ...)` without the exclusion of the html and body elements (HEAD's `gate.a11y.spec.ts` is the final one). The working tree has the right version, and the hash file (see "Test protection" above) records it; the next commit corrects HEAD. Mutating the file in place was a risk that I should have avoided by working on a copy; nothing was committed or staged by me.
- The first batch of mutants was stopped after a few minutes because its memory wait did not work (the shell it called was the wrong one); the file was restored (sha256 checked) and the batch started again with the wait working. Only the second batch is reported.
- The earlier example of the substring limit of case (b), the name "Frontier" for the header "Tier", was wrong: the match keeps the case ("Frontier" holds "tier"). It is replaced by "Tiered, row 1".
- One thing that the new checker still judges generously on purpose and that a reader may expect to be refused: a scroll container (overflow auto or scroll) is not treated as clipping, because its content can be scrolled to; this is in the list above (item 6).

## Correction round of 2026-11-21 night (about 01:10 to 01:40): what the AC-6 label check counts as hidden (review of the label check, three MUST-FIX items)

This section is history; the newest section is the one above it (second correction round), which replaces every statement below about what the check detects. The section after this one (round of 00:20 to 00:50) is history, with one correction noted at its top. The test-writer changed no production code (`modern/web-ui/src`), built nothing (no `npm run build`, `dist` untouched) and ran no Gradle and no git command.

### The review and what it found

1. Case (b) of the label rule accepted a header that no sighted user sees, because `shown()` read only the element's own style and size. Both markups passed over `<td><input aria-label="Taxonomy prefix, row 1"></td>`: `<th><span class="usa-sr-only">Taxonomy prefix</span></th>` (the USWDS class of this app: `position:absolute;left:-999em`) and `<thead style="opacity:0">` (opacity is not inherited).
2. Case (a) had the same gap: `<label for="npi" class="usa-sr-only">NPI</label><input id="npi">`, or a label inside `<div style="opacity:0">`, counted as a visible label. The four (vii) cases tested only hiding set on the label itself.
3. Both reports said the check refuses a header that is not shown, text hidden inside the cell and a label that is not shown. That was false for the markups above, and no limit was stated.

### The change of a recorded test (user rule Q-097 C, decision Q-137 A of the lead; dated 2026-11-21 night)

What the check accepted before this round (the version of the round of 00:20 to 00:50, sha256 e17fb573...): an input was labelled when (a) it had a label element with text and a box of at least 2 by 2 pixels not hidden by the label's own style, or (b) it sat in a table cell, the header cell over its column passed the same own-style test and had text, and that text was in the input's accessible name. Anything hidden through an ancestor (opacity 0 on a wrapper, a thead or a row) or by moving the box off the page (usa-sr-only, top -999em, a transform) still counted as seen.

What it accepts now (support/structure.ts: a new test `seen`, used for the label element, the header cell and every element between a text and its cell or label): the same two cases, but an element counts as seen only when all of these hold: its own style and size pass as before; no ancestor has opacity 0 and `checkVisibility` (called with the options of both the old and the new spelling) says it is rendered (this catches display none on an ancestor, a closed details element and content-visibility hidden, which keep a box); and its box is not wholly off the page (right edge at or left of the page origin, or bottom edge at or above it, in page coordinates, so a scrolled page is not "off"). The text of a header or of a label is the text of its parts that are seen (a label that is a box with its only text off the page has no visible text). The messages are unchanged ("is not shown" now means "not seen by a person").

Why: Q-137 A makes the visible column header (or label) the thing a sighted user reads, so the check must refuse a header or label that no sighted user can see. WCAG 2.1: 3.3.2 Labels or Instructions, 2.5.3 Label in Name, 1.3.1 Info and Relationships.

What it still refuses, with the new tests: everything of the first version (an input outside a table named only by aria-label; a name that lacks its column's header text, also by case, no name, aria-labelledby elsewhere, no header cell; an empty header; another column's header; a label element that is not shown), and now also a header or label that is hidden through usa-sr-only, an opacity 0 on an ancestor (wrapper, thead, row), a box above the page, a transform out of the page, a closed details element, content-visibility hidden, or a one-pixel clipping wrapper around its text.

What it does NOT detect: withdrawn. The list that stood here was incomplete (it left out ancestor clipping, clip-path, filter opacity(0), transparent text, font size 0 and control content inside a label) and its example for case (b) was wrong. The exact statement and the exact list are in the section above, "Second correction round of 2026-11-21 night".

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

`python modern/tools/checks/test_protection.py record MAM-86 <the 12 files>` was run again for the 12 files of the story (printed: recorded 12 test files). Only two lines of the hash file and of `red-blobs.txt` changed: `e2e/a11y/gate.a11y.spec.ts` and `e2e/a11y/support/structure.ts`; the other 10 files are byte-identical to the round of 00:20 to 00:50. Afterwards `check MAM-86` printed OK (12 test files unchanged, no unrecorded test files), `check MAM-92` OK (3 files) and `check MAM-80` OK (3 files). Line endings kept: the working files are LF only, as committed (corrected in the second correction round: the sentence that said CRLF was false; a byte count finds no CR in the two files).

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
