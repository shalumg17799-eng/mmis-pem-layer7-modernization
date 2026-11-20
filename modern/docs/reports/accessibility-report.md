# Accessibility report (MAM-86)

Date: 2026-11-20 (first runs between 22:30 and 23:31), the correction round after an independent review (2026-11-20 23:54 to 2026-11-21 00:09), the round of 2026-11-21 (00:20 to 00:50, local time of the build host) that ran the page scans again on the build deployed at 00:19, and a correction round of 2026-11-21 night (about 01:10 to 01:40) after a review of the AC-6 label check, and a second correction round of 2026-11-21 night (about 01:55 to 04:00) after a second review of it (what the check counts as hidden; see "Page structure (AC-6)"). The results below are those of the last round; where the earlier rounds differ, the text says so.
Standard checked: WCAG 2.0 and 2.1, levels A and AA, with axe-core 4.14.1 (through @axe-core/playwright 4.14.0), plus keyboard checks written for this story.
Browser: Chromium of Playwright 1.65.1, desktop window 1280 by 720, one worker.
What was tested: the build that the running stack serves at http://localhost:5173 (`modern/web-ui/dist`, files dated 2026-11-21 00:19; the rounds of 2026-11-20 used the build of 2026-11-20 22:02), against the gateway at http://localhost:8080 and the real Keycloak at http://localhost:8180.
The test-writer changed nothing in `modern/web-ui/src` and built nothing for this report. The build of 00:19 was made and deployed by the lead; it contains the fix of defect D1 (`src/shared/ui/ScrollableTable.tsx`, one shared component for every table container).

## Result in short

| Acceptance criterion | Result | Why |
|---|---|---|
| AC-1 zero axe violations on the 13 screens | PASS | 13 of 13 screens, plus 2 further states, report 0 violations for wcag2a, wcag2aa, wcag21a, wcag21aa |
| AC-2 error, empty and refused states | PASS | apply form with errors, provider search with "No provider found", console route refused with 403: 0 violations each; two more error states scanned too |
| AC-3 keyboard-only pass per journey | PASS (journeys not re-run in the round of 2026-11-21) | J1 to J7 all completed with the keyboard only, each with every outcome check of its mouse journey (section "Keyboard-only journeys") |
| AC-4 focus visible and tab order logical | PASS | all 13 screens and the filled work queues pass the Tab walk on the build of 2026-11-21 00:19. Defect D1 (a Tab stop on a table container that does not scroll, 6 tests red on the build of 2026-11-20 22:02) is fixed in the source and confirmed by this run. 20 component tests of the new `ScrollableTable` pass too (jsdom, stubbed sizes) |
| AC-5 contrast and status not left to colour | PASS | contrast rule ran on every screen with no text below 4.5:1; every status badge shows its text with its code |
| AC-6 page structure | PASS | 13 of 13 screens, plus the filled work queues. The label rule is read as decided in Q-137 A (see "Page structure (AC-6)"): the risk-rules inputs, named by aria-label under a visible column header that their name contains, count as labelled. Item D2 is no longer a defect of the page but a decided reading. 101 gate cases prove what the rule accepts and refuses, and what it counts as hidden (28 of the first version, 19 added in the first and 54 in the second correction round of 2026-11-21 night). What the check detects and what it does not is stated once, exactly, under "Page structure (AC-6)" |
| AC-7 a violation breaks the run | PASS for the gate; wiring of the page scans into `make e2e` is for the lead (see "How a violation breaks the run") | checker fails naming page and rule; a separate real Playwright run exits non-zero; no suppression without a written justification |

Tests: 221 written (pages 77, keyboard journeys 7, gate 105, repository checks 12, component tests 20). Run in the second correction round of 2026-11-21 night: 214 (pages 77, gate 105, repository checks 12, component tests 20), all 214 passed. The page scans and the default suite ran under the stack lock (taken 03:32:55, released 03:38:31, seed before and after); the gate cases of the label rule, the old-checker and mutant runs, the component tests, tsc and eslint ran without the stack. The 7 keyboard journeys were not run in this round or in the rounds before it on 2026-11-21 (7 of 7 passed in the correction round of 2026-11-20 on the build of 2026-11-20 22:02); they do not use the structure checker.
History: the correction round of 2026-11-20 ran 100 tests, 93 passed and 7 failed: 6 tests for D1 and 1 test for D2, each with the same message in every run (not timing faults). In the round of 2026-11-21 D1 is fixed in the source (the 6 pass on the build of 00:19) and D2 is closed by decision Q-137 A with the AC-6 check brought in line with the wording of the criterion (a change of a recorded test, see "Page structure (AC-6)" and `modern/docs/reports/stories/MAM-86/red-report.md`). One further fault was found and fixed in the tests (the start state of the provider-search Tab walk, see "Round of 2026-11-21").

## How it was run

1. Under the shared stack lock: `bash infra/scripts/seed.sh` (seed state, 16 providers), then the run, then the seed again before the lock was released.
2. Page scans (read-only): `PEM_JOURNEYS=1 npx playwright test e2e/a11y/pages.a11y.spec.ts` from `modern/web-ui` with `NODE_OPTIONS=--dns-result-order=ipv4first`.
3. Keyboard journeys (change data): `PEM_JOURNEYS=1 npx playwright test e2e/a11y/keyboard-journeys.spec.ts`. The file seeds the stack itself before each of the seven journeys.
4. Gate and repository checks (need only the web UI): `npx playwright test e2e/a11y` without `PEM_JOURNEYS`; this runs exactly 117 tests: the gate (4 tests, and 101 cases of the AC-6 label rule: 105 in the file) and the repository checks (12); the pages and keyboard files register no test without `PEM_JOURNEYS`, like the journeys. The 101 label-rule cases use `page.setContent` and need no stack at all.
5. JSON reports for the trace tool: `modern/web-ui/test-results/playwright-a11y-pages.json`, `playwright-a11y-journeys.json`, `playwright-a11y-gate.json` (the trace tool maps a test to an acceptance criterion by the bracket tag `[MAM-86 AC-n]` in the titles of its groups and of the test, so every test sits in a group tagged with its own criterion, and a repository check enforces that). Each run writes its own file with `PLAYWRIGHT_JSON_OUTPUT_NAME` and its own artefact folder with `--output=test-results/artifacts-a11y-<part>`, so that one run does not delete the report of another. In the round of 2026-11-21 the JSON reports were written to the session scratchpad of the build host (the folder `modern/web-ui/test-results` is emptied by every Playwright run that does not use its own output folder), not to `modern/web-ui/test-results`.
6. Component tests of `ScrollableTable` (jsdom, no stack): from PowerShell, `cd modern\web-ui; npx vitest run tests/ux/scrollable-table.test.tsx`.

Data used by the page scans. The seven console screens and the public directory read the live stack at its seed state. The five portal screens and the approval page are answered with synthetic gateway data in the browser (`e2e/support/gateway-routes.ts` is reused), because the seed holds no application, notice or screened pending provider for the portal user and a scan must not write. Keycloak sign-in is always real. The same portal screens and the approval page are also scanned with live data inside the keyboard journeys J1 and J2 (see below).

## The 13 screens

Columns: AC-1 axe scan of the main state; AC-4 Tab walk; AC-5a colour contrast; AC-5b status shown as text with its code; AC-6 structure. "Stops" is the number of Tab stops the walk made before the focus left the page. All results are those of the run of 2026-11-21 on the build of 00:19; the stop counts are from that run (on the build of 2026-11-20 22:02 the five screens with a table container that did not scroll had one stop more each: portal.home 16, portal.notices 14, console.provider-detail 8, console.approval 12, public.directory 12; defect D1).

| Screen | Route | Data | AC-1 axe | AC-4 Tab walk (stops) | AC-5a contrast | AC-5b status text | AC-6 structure |
|---|---|---|---|---|---|---|---|
| portal.home | /portal | scripted | PASS, 0 violations | PASS (15) | PASS | PASS (Validated, Rejected) | PASS |
| portal.apply | /portal/apply | scripted | PASS | PASS (18) | PASS | PASS (no badges) | PASS |
| portal.application-status | /portal/applications/:id | scripted | PASS | PASS (9) | PASS | PASS (Validated) | PASS |
| portal.provider-record | /portal/provider | scripted | PASS | PASS (9) | PASS | PASS (Active (A), Pass (P), Limited (L)) | PASS |
| portal.notices | /portal/notices | scripted | PASS | PASS (13) | PASS | PASS (Sent, Failed) | PASS |
| console.provider-search | /console/providers | live, search "TESTPROV" | PASS | PASS (22) | PASS | PASS (Terminated (T), Pending (P), Active (A), Denied (D), Suspended (S)) | PASS |
| console.provider-detail | /console/providers/1005940865 | live | PASS | PASS (7) | PASS | PASS | PASS |
| console.work-queues | /console/queues | live (all four lists empty at the seed) | PASS | PASS (11) | PASS | PASS | PASS (no table at the seed; the filled lists are a further state below: 4 tables with headers) |
| console.approval | /console/providers/1003000111/approval | scripted | PASS | PASS (11) | PASS | PASS | PASS |
| console.record-sanction | /console/providers/1005940865/sanctions/new | live | PASS | PASS (14) | PASS | PASS | PASS |
| console.risk-rules | /console/admin/risk-rules | live | PASS | PASS (26) | PASS | PASS | PASS (10 table inputs by rule (b) of Q-137 A) |
| console.batch-jobs | /console/admin/jobs | live (20 runs listed) | PASS | PASS (35) | PASS | PASS (run states Completed; run states have no legacy code) | PASS |
| public.directory | /directory | live, search with an empty name | PASS | PASS (11) | PASS | PASS | PASS |

Further states scanned (beyond the 13 main states):

| State | Data | Axe (AC-1 or AC-2) | Other result |
|---|---|---|---|
| portal.apply with errors (E101, E102, E104 returned by a scripted 422) | scripted | PASS, 0 violations | errors next to the three fields (message present, input described by it, `aria-invalid`); summary of 3 entries; each entry follows to its field and the field receives the focus: PASS |
| console.provider-search, unknown NPI 1003000999, "No provider found" | live | PASS | PASS |
| console route refused with 403 (provider1 opens /console/providers, "Access denied") | live | PASS | PASS |
| console.record-sanction with no action and no date (2 errors) | live | PASS | errors at the fields, summary links move the focus to the fields: PASS |
| console.approval with no decision chosen (1 error) | scripted | PASS | the summary entry moves the focus to the first choice: PASS |
| console.work-queues with an entry in each of the four lists | scripted | PASS | structure: PASS (4 tables, all with header cells). Tab walk (27 stops): PASS (on the build of 2026-11-20 22:02: 31 stops, FAIL, D1, four table containers) |
| console.batch-jobs with the job "Sanction intake (SANCIN)" chosen (file input and batch id shown) | live | PASS | every input has a visible label: PASS |

The work-queues screen is empty at the seed state, so its main state in the table above shows four empty lists. The filled lists are scanned in the scripted state above and, with live data, inside journeys J1 (Pending approval), J3 (Failed screening) and J6 (Revalidation due).

## Focus and tab order (AC-4)

How it is measured: Tab is pressed from the top of the page until the focus leaves the page or comes back to the first stop (bound: 250 presses). At every stop the browser is asked, not assumed:

- which element has the focus and whether it is interactive (link with href, button, input, select, textarea, summary, a role of a widget, or a scrollable region that really overflows);
- whether it can be seen (size, visibility, inside the window); a USWDS radio button or check box whose native input is hidden is judged by its visible label tile;
- the computed outline and box shadow of the element (or of the label tile) before it had the focus and with the focus; the stop passes only if something is drawn with the focus that was not drawn before;
- the box of what the user sees, for the order check.

Order rules: the first stop must be the skip link; the header, the government banner and the menu must come before the page content; then the order must follow the visual order: top to bottom, and left to right inside one row. Tolerance stated: two boxes are in the same row when their vertical extents overlap by at least half of the shorter one; inside a row the left edge may move back by at most 2 pixels. Visible interactive elements that no Tab press reaches are listed as a problem too.

Results on all 13 screens and the filled work queues: the first stop is always "Skip to main content" (a link), followed by the government banner button, the site title, the menu and "Sign out" (or "Sign in" on the public page); every stop was visible; every stop drew a focus indicator that was not there before; no stop was out of the visual order; no visible control was unreachable; no keyboard trap (the focus left the page at the end of every walk). On the build of 2026-11-21 00:19 no problem is found on any screen. On the build of 2026-11-20 22:02 the only problem was D1: a Tab stop on `div.usa-table-container--scrollable[tabindex=0]` around a table that fits (no role, no name, nothing to scroll). The fix is in the source (one shared `ScrollableTable`: a container is a Tab stop only while its content overflows, then with tabindex 0, role region and the name "<label> (scrollable table)"; measured with a ResizeObserver on the container and the table) and the run on the build of 00:19 confirms it on the five screens and on the filled work queues. The container whose table does overflow stays a stop, now with a role and a name: on console.batch-jobs the walk stops once on the region "Recent runs (scrollable table)", and the test accepts it (a scrollable region that really overflows is interactive). The component has 20 tests of its own (jsdom with stubbed sizes and a stubbed observer: overflow and fit, the boundary of one pixel, both directions of a change, Tab reaches the container only when it overflows, the observer is disconnected on unmount); the real measuring is proved only by the Tab walks above.

## Colour contrast and status text (AC-5)

- The colour-contrast rule is not switched off. Every scan fails if the rule did not look at anything, and the AC-5 test of each screen requires that it measured and passed elements (between 27 and 126 per screen). Evidence that the rule is alive: a test injects text of colour #cfcfcf on white (about 1.5:1) into the public directory and the checker reports `color-contrast` (test "MAM-86 AC-5 the colour-contrast rule is not disabled").
- No screen has a contrast violation.
- Axe cannot decide the contrast of text over a gradient or a CSS pseudo-element and reports it as "needs review": 23 elements in all on the 13 screens (the drop-down lists with their arrow image, the current-page marker of the menu, the labels of the step indicator). They are not left unchecked: the tests compute the ratio from the computed text colour and the first opaque background behind the element (WCAG 2.1 relative luminance). The lowest value found is 6.72:1 (the current step of the step indicator); drop-down lists 17.22:1; menu entries 6.74:1. The limit is 4.5:1. A background image is ignored by this calculation (it is only an arrow or a marker bar).
- Status as text: on every screen every `usa-tag` badge has text, and a badge that names a status word carries its code ("Pending (P)", "Active (A)", "Terminated (T)", "Denied (D)", "Suspended (S)", "Pass (P)", "Limited (L)"); no cell, list value or badge is a bare status word. In the provider search the seed's suspended provider is listed as "Suspended (S)"; its provider detail shows the banner "Status: Suspended (S)", never "Denied" (D-006). The portal provider record shows "Active (A)".
- Not covered by a code: the run states of the batch jobs (Running, Completed, Failed) and the notice states (Sent, Failed) have no legacy code, so they are text without a code.

## Page structure (AC-6)

On all 13 screens: exactly one h1 and it equals the title of the screen inventory; exactly one banner and one main landmark; a navigation landmark ("Primary navigation", and "Breadcrumb" where the page has one); every table has header cells (`th`) with text (the notices, screening, search, directory, batch-run and risk-rule tables were read; the four work-queue tables were read in the filled, scripted state, because the seed state has them empty); every visible input is labelled as the rule below says.

The label rule (decision Q-137 A of 2026-11-21, `modern/docs/analysis/questions.md`; implemented in `e2e/a11y/support/structure.ts`). The criterion says "every input has a visible label". On the risk-rules page the ten inputs of the rules table are named by aria-label ("Taxonomy prefix, row N", "Tier, row N") and what a sighted user sees is the column header. A visible label element per input was tried first and broke the recorded test of MAM-92 AC-1 (a rule row may hold no other text), so the page stays as it is and the check is brought in line with the wording of the criterion. An input counts as labelled when (a) it has a visible label element (a label that is seen and has text that is seen), as before, OR (b) it sits in a table cell, the header cell over its column is seen and has visible text, and that text (trimmed, case kept) is contained in the input's accessible name (aria-labelledby, else aria-label, else the label elements). Case (b) rests on WCAG 2.1: 3.3.2 Labels or Instructions, 2.5.3 Label in Name, 1.3.1 Info and Relationships. The column is found on the column grid of the table (colspan and rowspan counted); the header cell is the lowest one over the column in the thead, or in the first row made only of th. An input that meets neither is a problem and the message says what each case misses.
What it still refuses (each proved by a gate test): an input outside a table named only by aria-label; a table input whose name does not contain the header text of its column (also when only the case differs, when there is no name, when aria-labelledby points elsewhere, when the column has no header cell); a table input under an empty header cell or under a header cell that is not seen (see the exact statement below), or whose header text is hidden inside the cell; a table input whose name holds only the text of another column's header; an input whose label element is not seen, or whose label holds no text that is seen. Nothing else of the structure check was loosened.

### Exact statement of what the AC-6 label check detects, and exact list of what it does not

This is the one place in this report that says what the label check judges; the same text stands in `modern/docs/reports/stories/MAM-86/red-report.md`. History of the wording: the first version of the check (2026-11-21 00:50) judged only the element's own style and size; the first correction (01:40) added ancestors with opacity 0, `checkVisibility` and the off-page test; the second correction (the one this section belongs to) added the rest, and each addition is proved by gate tests that are red against the version before it (see the red report). Earlier wording of this report that said more than the statement below, or that listed fewer limits than the list below, is withdrawn. An earlier example of the substring limit ("Frontier" for the header "Tier") was wrong, because the match keeps the case.

#### What the AC-6 label check detects (exact statement; `e2e/a11y/support/structure.ts`, state after the second correction round of 2026-11-21 night)

An input counts as labelled when (a) one of its label elements is seen and holds text that can be read, or (b) it sits in a table cell, the header cell over its column is seen and holds text that can be read, and that text (trimmed, case kept) is part of the input's accessible name (aria-labelledby, else aria-label, else the label elements). An element (a label element, a header cell, or an element inside one of them) is NOT seen when any one of these holds:

1. It has display none, or an ancestor has display none or content-visibility hidden, or it is inside a closed details element, or it has visibility hidden (the browser's `checkVisibility` says it is not rendered).
2. It or an ancestor has opacity 0, or a filter that contains opacity(0) (also opacity(0%)).
3. It or an ancestor has a clip-path other than none (a decorative clip-path counts too, so the check reports it), or has the old `clip` property, on an absolutely or fixed positioned box, with a rectangle under 2 by 2 pixels.
4. Its own box is under 2 pixels wide or under 2 pixels high.
5. The part of its box that its clipping ancestors leave is under 2 pixels wide or under 2 pixels high. An ancestor with overflow hidden or clip cuts the box (on the axis that sets it). An ancestor with overflow auto or scroll is a scroll container: its content counts as reachable by scrolling, so only a scroll container that is itself under 2 pixels wide or high hides what is in it. An absolutely positioned box is cut only by its containing block and the boxes above it, not by a wrapper in between.
6. Its box lies wholly off the page: its right edge is at or left of the left edge of the page, or its bottom edge is at or above the top edge of the page (usa-sr-only, top -999em, a transform that throws it out of the page). The page scroll is added, so a table scrolled out of the window is still on the page.

A text counts as read only when the element that holds it has a font size above 0 and a text colour with an alpha above 0, that element and every element between it and the label or header cell are seen, and it is not inside a textarea, select, option, input or button (what those hold is their content, not the text of the label).

#### What the AC-6 label check does NOT detect (exact list)

Items 1 to 8 were each run through the check by a throw-away probe (not a recorded test, so they are not pinned by the hash file): the check accepted the label. Whether any of the 13 screens uses one of these methods was not searched for in the source: the page tests pass, which proves only that the check finds no problem.

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

The check also reports a label that a person can read; it errs on the side of reporting, never the other way (probed; the button caption by a gate test): a label whose only text is generated by CSS (`::before`, `::after`), the alt text of an image, or the caption of a button inside it; and any clip-path other than none, even one that clips nothing (`inset(0)`).

On the live pages: the 14 AC-6 page tests pass on the build of 2026-11-21 00:19 with the check as stated (77 of 77 page scans, 03:33 to 03:36), so no input of the 13 screens relies on a label or header that the check counts as not seen. The number of inputs read was 32 on 7 screens (portal.apply 8, console.provider-search 3, console.approval 2, console.record-sanction 4, console.risk-rules 11, console.batch-jobs 2, public.directory 2) in the round of 01:10 to 01:40, counted by a probe; it was not counted again in the second correction round.

## Keyboard-only journeys (AC-3)

Durations are those of the correction round, run while another lane compiled on the same machine (about twice as long as in the first round, where J1 took 42 s); each test has a limit of 480 s.

Rules kept by the journeys file and its helper (checked by a test that reads both files): input only through `page.keyboard` (Tab, Shift+Tab, Enter, the space bar, the arrow keys, typed text, Control+A); no mouse call, no `click`, `fill`, `check`, `selectOption`, `dispatchEvent`, no element-level `press` or `type`, no programmatic `focus`. A control is reached by pressing Tab (Shift+Tab when it lies above the focus) until it has the focus, with a bound of 120 presses; when it cannot be reached the step fails with the name of the control and the list of controls the focus passed (proved on a sample page by the test "a control that Tab never reaches fails the step with its name").
Things done before the page has the focus: typing the first address of a tab (`page.goto`), as a person types it in the address bar. The Keycloak sign-in form is completed with the keyboard too: Tab to the user name, type, Tab to the password, type, Enter.
Exception for a native file dialog (`setInputFiles`): not used. None of the seven journeys uploads a file.
Each journey starts from the seed state (the file runs `bash infra/scripts/seed.sh` first) and scans the pages it meets with the one axe checker of this story (0 violations in every scan).

Outcome checks. The first version of the file did not repeat everything its mouse version in `e2e/journeys` checks (a review on 2026-11-20 found the gaps, and this report had wrongly said "the same checks"). The file was corrected and compared with the seven mouse journeys assertion by assertion. Added after the review:

- J1: the scan of the apply form; the screening has reason INITIAL and status COMPLETED (besides result PASS and the four sources); the "Failed screening" list is shown before the check that the provider is not in it (before, a missing list would have passed that check).
- J2: the scan of the empty apply form when it is first shown.
- J3: the submitted application is VALIDATED; the provider stays Pending (P) after the failed screening; the screening read from the API is FAIL, with OIGLEIE F and error code E203.
- J5: the three form fields (Action, Reason, Effective date) are shown; the refusal of the unknown action code is `application/problem+json`.
- J7: the new T-MSIS run is the newest T-MSIS row and the only row for that run (the row count is the earlier number of runs plus one, at most 20); TMSISOUT and DIROUT each hold exactly one record per active provider (the count, besides the set of NPIs, so a duplicated record fails); the directory search answers 200 and every name it returns contains HOSPICE.

J4 and J6 already had all of their mouse checks. Differences that remain on purpose, none of them a weaker outcome check: the keyboard journeys reach a page with a link or a menu entry where the mouse journeys type an address (`page.goto`), so they also prove that the link works; and the keyboard journeys scan with the four WCAG tags of AC-1 (the story's checker), while the mouse journeys use the older helper `e2e/support/page.ts`, which runs every axe rule, including best-practice rules. The scans of the keyboard journeys are therefore narrower in rules than those of the mouse journeys; the page scans of AC-1 are the ones that count for the criterion.

### J1 A provider applies and is enrolled: PASS (81 s)

1. provider1 opens /portal and signs in with the keyboard; "My enrolment" is shown.
2. Tab to "Apply for enrolment" in the page, Enter. The apply form is scanned.
3. Tab and type NPI, Medicaid ID; Tab to Provider type and choose "Individual (IN)" with the arrow keys; type taxonomy, last name, first name, licence number and state; Tab to "Submit application", Enter. Status "Validated" (API: VALIDATED, no errors).
4. (System) registered, screened PASS on the four sources (exactly one screening, reason INITIAL, status COMPLETED), risk tier L (read from the API).
5. analyst1 signs in with the keyboard at the work queues; the provider is under Pending approval and not under Failed screening (the Failed screening list is shown first).
6. Tab to "Review" in the row, Enter; the arrow keys choose "Approve (becomes Active)"; Tab to "Record decision", Enter. Answer 200, status A, revalidation due date five years after the approval date; the page says "Decision recorded" with "Active (A)" and the date.
7. A visitor opens /directory, types RIVERA in Name, Enter: MARIA RIVERA is listed.
8. The notice is in Mailpit; the provider uses the menu (Tab to "My notices", Enter; Tab to "My provider record", Enter): "Enrolment approved (APPROVED)" is listed and the record shows "Active (A)".

### J2 An application is rejected: PASS (50 s)

1. provider1 signs in with the keyboard at /portal/apply (the empty form is scanned); types everything except the taxonomy; Tab to "Submit application", Enter. Answer 422, the page stays on the form.
2. The error summary takes the focus ("1 error in the application"); E104 is shown twice (summary and field), no other code; the first Tab reaches the summary entry "E104: ..."; Enter puts the focus on "Taxonomy code".
3. Through the menu ("My enrolment") one row is "Rejected"; Tab to "View", Enter: "Application status", Rejected, exactly one error row (E104, taxonomy); API agrees.
4. The REJECTED notice is listed ("Application rejected (REJECTED)") through the menu entry "My notices"; the provider was not registered (API 404).

### J3 Screening fails and the analyst denies: PASS (82 s)

Set-up by API (as the mouse journey): the group application with owner 1990000111 is accepted as VALIDATED and screened FAIL: the provider stays Pending (P) and the API shows the screening FAIL with OIGLEIE F and E203.
1. analyst1 signs in with the keyboard at the work queues: the provider is under Failed screening and not under Pending approval.
2. Tab to the NPI link in the row, Enter: the detail shows OIG exclusion list (LEIE) "Fail (F)" with E203.
3. Tab to "Approve or deny", Enter; the arrow keys choose "Deny (becomes Denied)"; Tab to Reason and type it; Tab to "Record decision", Enter. Answer 200, status D, "Denied (D)" on the page.
4. The DENIED notice is listed and the email is in Mailpit.

### J4 Analyst inquiry: PASS (135 s)

Set-up by API: apply, screened, approved.
1. analyst1 signs in with the keyboard at the provider search; types the NPI, Enter. One row; request carries `npi`.
2. Control+A and Backspace clear the NPI; Tab and type the Medicaid ID, Enter. One row; request carries `medicaidId` and no `npi`.
3. Tab to the NPI link of the result, Enter: every field with its meaning (Individual (IN), Limited (L), Active (A), Pass (P), both dates) and the four panels; no audit panel and no audit call for an analyst.
4. supervisor1 signs in with the keyboard, searches by NPI, Tab to the link, Enter: the audit call answers 200 and the audit panel is shown.
5. analyst1 uses the menu (Tab to "Provider search", Enter), types an unknown NPI, Enter: "No provider found" (API: 200, total 0).
6. provider1 signs in at a console address: "Access denied"; Tab to "Go to My enrolment", Enter; the menu "My provider record" shows their NPI and no other provider's NPI; the API answers 403 for the console list and for another provider.

### J5 A supervisor suspends and reinstates a provider: PASS (66 s)

1. supervisor1 signs in with the keyboard at the provider detail of TESTPROV ALPHA; Tab to "Record sanction", Enter; the form shows Action, Reason and Effective date.
2. Enter on "Record sanction" with an empty form: "2 errors in the sanction"; Tab reaches "Choose an action", Enter puts the focus on Action; nothing was stored.
3. The arrow keys choose "Suspend (S)"; type reason and date 03/31/2026; Tab to "Record sanction", the space bar. Answer 201, result S, "Suspended (S)" on the page, notice SUS in the list and in Mailpit.
4. A visitor types ALPHA in /directory, Enter: "No providers found"; the provider left the directory.
5. Tab to "Provider detail", Enter; Tab to "Record sanction", Enter; "Reinstate (R)", reason, date 04/30/2026, space bar: 201, result A, "Active (A)", notice RST.
6. Tab to "Provider detail", Enter: the history panel lists both actions with reasons and dates (API: S then R, in order).
7. An unknown action code (API) is refused with 422 as `application/problem+json` and nothing is stored.

### J6 Monthly revalidation: PASS (55 s)

1. admin1 signs in with the keyboard at the batch jobs; the arrow keys choose "Revalidation sweep"; type 03/31/2026; Tab to "Run job", Enter. Answer 202; the run completes with `queued` equal to the number of due providers; the row shows "Completed".
2. Tab to "Download" in the row of that run, Enter: file REVALQ.D20260331, 80-byte records, due date, RESCREEN-REQUIRED, same bytes as the API.
3. One case per due provider and none for the others (API); analyst1 signs in with the keyboard: no provider is listed twice under Revalidation due.
4. Each case becomes COMPLETED and each provider gets a later due date.
5. A second sweep started with the keyboard adds no case.

### J7 Month-end extracts and the public directory: PASS (40 s)

1. admin1 signs in with the keyboard; the arrow keys choose "T-MSIS extract"; date; Enter. The page follows the run to "Completed" with its archive generation and row count and without a reload (no navigation, the typed date is still in the form); it is the newest T-MSIS row and the only row for the run.
2. Tab to "Download" in the row of that run, Enter: TMSISOUT.D20260331, 120-byte records for exactly the active providers (one record each), same bytes as the API.
3. Arrow Up to "Directory extract (DIROUT)"; Enter; Tab to its "Download", Enter: DIROUT.D20260331, 100-byte records, composed names (TESTPROV ALPHA without licence number, SAMPLE CLINIC 01), exactly the active providers (one record each).
4. A visitor types HOSPICE in /directory, Enter: only the active hospice, with its composed name; the terminated one is not listed (the API answers 200 and every name contains HOSPICE).

## How the tests are counted by the trace tool

`modern/tools/trace/build_rule_trace.py` reads `modern/web-ui/test-results/playwright*.json` and maps a test to a criterion only through the bracket form `[MAM-86 AC-n]` in the group titles and the title, joined with spaces. The review found two tests in a group of another criterion (a Tab-walk test of AC-4 in the group "[MAM-86 AC-1] other states", and the contrast test of AC-5 in the group "[MAM-86 AC-7]"): the tool counted them for both criteria, so AC-1 would have shown a failure. Both tests now sit in a group of their own criterion, and `policy.a11y.spec.ts` checks every test of the four spec files (a sample test proves that the checker flags a wrong group). The counts below are the output of the tool's own functions (`tests()` of `build_rule_trace.py`, imported, with the module's path pointed at a scratch copy of the reports; `main()` not run, so no file of the tool was written) over the reports of the round of 2026-11-21 (pages, default suite, Vitest). The 7 journeys (AC-3) are counted from the correction round, because they were not run again:

| Criterion | Tests the tool counts | Passed | Failed | Verdict in this report |
|---|---|---|---|---|
| AC-1 | 19 | 19 | 0 | PASS |
| AC-2 | 5 | 5 | 0 | PASS |
| AC-3 | 11 (7 journeys from the correction round) | 11 | 0 | PASS |
| AC-4 | 34 (14 Playwright, 20 Vitest) | 34 | 0 | PASS (D1 fixed) |
| AC-5 | 30 | 30 | 0 | PASS |
| AC-6 | 115 (14 pages, 101 gate) | 115 | 0 | PASS (label rule of Q-137 A, with what counts as seen) |
| AC-7 | 7 | 7 | 0 | PASS |
| Total | 221 | 221 (214 run in the second correction round of 2026-11-21 night; the 7 journeys are those of the correction round of 2026-11-20) | 0 | |

The same function over the JSON reports of the second correction round of 2026-11-21 night (scratch copy; `main()` not run) gave AC-1 19, AC-2 5, AC-3 4 (plus the 7 journeys by hand: 11), AC-4 34, AC-5 30, AC-6 115, AC-7 7: 214 tests, all passed. In the correction round of 2026-11-20 the same function gave 100 tests: AC-4 14 (8 passed, 6 failed for D1) and AC-6 14 (13 passed, 1 failed for D2), the others as above.

What the groups hold. AC-1: the 13 screens, 2 further states, 3 repository checks that every test is counted under its own criterion, and the check that no test is skipped. AC-2: 5 states. AC-3: the 7 journeys, 3 checks that the keyboard files contain no mouse call, and the helper test that names an unreachable control. AC-4: the 13 Tab walks, the walk of the filled work queues, and the 20 component tests of `ScrollableTable` (their describe titles carry no bracket tag, so that the tool does not count a Vitest test twice). AC-5: 13 contrast tests, 13 status-text tests, 3 further status tests, and the test that the contrast rule is not disabled. AC-6: 13 screens and the filled work queues, and the 101 gate cases of the label rule. AC-7: 2 gate tests and 5 repository checks of suppressions.

## Correction round (2026-11-20 evening, after an independent review)

The review found two faults in the tests and in this report; both are corrected, and no production code was touched.

1. The keyboard journeys checked less than the mouse journeys, so the statement "the same outcome checks" was false (J1, J2, J3, J5 and J7; the list is under "Keyboard-only journeys"). The missing assertions were added; J1 to J7 were run again from the seed and all pass, so the UI does meet the added checks. The omissions that remain are listed there as differences on purpose.
2. Two tests were counted for the wrong criterion by the trace tool (see the section above). Fixed, and guarded by a repository check.

## Round of 2026-11-21 (00:20 to 00:50)

1. D1 is fixed in the source and was deployed by the lead (build of 00:19). The six Tab walks that were red now pass.
2. D2 is closed by decision Q-137 A of the lead: the page keeps its aria-labels under the visible column headers, and the AC-6 check is brought in line with the wording of the criterion (section "Page structure (AC-6)"). This changes a recorded test, under the user's standing rule Q-097 C and the recorded decision Q-137 A; every other assertion is kept. The change is documented in `modern/docs/reports/stories/MAM-86/red-report.md` (what the check accepted before, what it accepts now, why, what it still refuses). Gate tests prove both sides of the rule and what it counts as hidden, each with a mutation check (28 at the time of this round, 47 after the first correction of 2026-11-21 night, 101 after the second).
3. A fault in the start state of one test was found by the first run of the round and fixed: the search page pre-fills the NPI of the last search made in the tab (MAM-77 AC-6), and the AC-2 "No provider found" test had typed 1003000999 in the same tab; the Tab walk of console.provider-search typed only a name, so the search was for that NPI and that name together and found nothing. The preparation of the screen now empties the NPI first. It was not a defect of the page. It is not established why the same order passed in the correction round.
4. 20 component tests were added for `ScrollableTable` (written after the component; stated in the red report).

## Correction round of 2026-11-21 night (about 01:10 to 01:40): what the label check counts as hidden

A review of the AC-6 label check returned three MUST-FIX items. Item 1: case (b) accepted a header that no sighted user sees (`<th><span class="usa-sr-only">Taxonomy prefix</span></th>`, and `<thead style="opacity:0">`), because the check read only the element's own style and size. Item 2: case (a) had the same gap for a label (`<label class="usa-sr-only">`, a label in an opacity-0 wrapper). Item 3: this report said the check refuses a header or label that is not shown, which was false for those markups, and named only one limit.

What was done (tests and reports only; `modern/web-ui/src` and `dist` untouched, nothing built):

1. One stricter test, `seen`, in `e2e/a11y/support/structure.ts`, used for the header cell, the label element and every element between a text and its cell or label (own style, ancestors, a box wholly off the page; see "Page structure (AC-6)").
2. 19 new gate tests in `e2e/a11y/gate.a11y.spec.ts` (47 AC-6 cases in all): both markups of the review as (v) and (vii) cases, plus the usa-sr-only class on a label and on a text inside a label, an opacity-0 wrapper (one and two levels up, the thead, a table row), a box above the page, a transform, a closed details element, content-visibility hidden, a wrapper clipped to one pixel, a usa-sr-only suffix that need not be in the name, a box that starts left of the edge but reaches into the page, a table scrolled out of the window, a label that sets visibility visible inside a hidden wrapper. 14 of the 19 are red against the first version of the check, for the expected reason (the checker reports no problem, or keeps hidden text in the header text); 5 are guards that pass on both versions by design.
3. Page scans on the live stack, build of 2026-11-21 00:19: 77 of 77 passed (the 14 AC-6 tests included; 32 inputs on 7 screens read, no problem). The default suite `e2e/a11y`: 63 of 63. tsc and eslint clean. The 20 component tests of `ScrollableTable` re-run: 20 of 20.
4. The two reports state the hiding methods that the check detects and those it does not. This statement was incomplete and is replaced by the exact statement and the exact list under "Page structure (AC-6)" (second correction round, below).

## Second correction round of 2026-11-21 night (about 01:55 to 04:00): what the label check counts as hidden, second review

A second review of the AC-6 label check (01:53) returned four MUST-FIX items and two SHOULD-FIX notes. Item 1: `seen` did not judge clipping by an ancestor (a label inside a wrapper of one pixel, or of height 0, with overflow hidden, and a header cell with `clip-path: inset(50%)` passed). Item 2: the text of a label included the content of a control inside it (`<label><textarea id="note">Write the reason here</textarea></label>` counted as a visible label). Item 3: this report and the red report said more than is true about what the check detects, and their lists of what it does not detect left out ancestor clipping, clip-path, filter opacity(0) and control content inside a label. Item 4: the red report said that two working files are CRLF; they are LF only, as committed. Notes: text with a colour of alpha 0, and font size 0 on the element that holds the text.

What was done (tests and reports only; `modern/web-ui/src` and `dist` untouched, nothing built, no Gradle, no git command; this is a change of recorded tests under the user's rule Q-097 C and decision Q-137 A, and every change makes the check stricter, no assertion was weakened):

1. 54 new AC-6 gate cases were written first (101 in all): 40 that must be refused (17 about header cells, 23 about labels) and 14 guards that must still be accepted. Against the unchanged checker (the one of 01:40, sha256 d0a314f1...) 39 of the 40 refused cases were red, every one for the same reason: the checker reported no problem, so a label or header that nobody sees was accepted. The fortieth (a select, as a drop-down, inside a label) is refused by the old checker too, because the options of a drop-down have no box in Chromium; the 14 guards were green, as designed.
2. The checker (`e2e/a11y/support/structure.ts`) now also judges clipping by ancestors, clip-path, filter opacity(0), the legacy clip rectangle, text whose own element has font size 0 or a colour with alpha 0, and no longer reads the content of a textarea, select, option, input or button inside a label or header cell. The 47 earlier AC-6 gate cases give the same result as before; nothing became more lenient.
3. The two reports now say what the check detects and what it does not once, exactly (see "Page structure (AC-6)"); the sentences that said more are removed, and the line-ending sentence of the red report is corrected.
4. 21 mutants of the new code were run against the AC-6 gate cases; all are killed (four survived at first and were killed after one test was added for each; see the red report).
5. Runs: AC-6 gate cases 101 of 101 green on the new checker (the red evidence on the old one is in the red report); page scans on the live stack, build of 2026-11-21 00:19: 77 of 77 (03:33 to 03:36, 2.5 min); the default suite `e2e/a11y`: 117 of 117 (gate 105, repository checks 12; 03:36 to 03:38); the 20 component tests of `ScrollableTable`: 20 of 20; `npx tsc -b` and `npx eslint . --max-warnings 0`: both exit 0. A real page did not fail: no input of the 13 screens relies on a label or header that the check counts as not seen.

## How a violation breaks the run (AC-7)

- One checker, `e2e/a11y/support/axe-gate.ts` (`expectNoViolations`), is used by every page scan and by the gate test. It throws with the page name, the address and every rule id: `accessibility: page "public.directory" (http://localhost:5173/directory) has 1 axe violation (tags wcag2a, wcag2aa, wcag21a, wcag21aa): - image-alt (critical, 1 element) ...`. It also throws when the colour-contrast rule did not look at anything.
- Test 1 (`gate.a11y.spec.ts`): the public directory page is clean; an image without a text alternative is injected; the same checker fails and its message contains the page, the address and `image-alt`.
- Test 2: a separate, real `playwright test` process is started on a spec with the same injection; its exit status is not 0, its output contains the page and the rule and "1 failed". This is what `make e2e` relies on (`|| exit 1` in `pem.sh`).
- Test 3 (`policy.a11y.spec.ts`): no file under `modern/web-ui/e2e` disables a rule, excludes part of the page, narrows a scan (including `withTags`, `withRules`, `runOnly`, `configure`) or switches a rule off by option, unless a comment that starts with `axe-justification:` and gives a reason of at least 30 characters is on the same line or in the three lines above. The checker itself is tested on samples (flags an unjustified one, accepts a justified one, rejects an empty or too short justification).
- Result: there is no suppression. The one narrowing is the scan call of the gate, restricted to the four tags of AC-1, with its justification written next to it. The shared helper `e2e/support/page.ts` used by the older journeys runs all axe rules without options.
- Wiring into `make e2e`: see the notes in the story result. The gate and policy tests are in the default suite (`npx playwright test`) without a change; the page scans and the keyboard journeys need the lines listed there.

## Defects found

| # | Where | Rule or step | Element | What a user experiences | Failing tests |
|---|---|---|---|---|---|
| D1 (low), FIXED in the source and confirmed on the build of 2026-11-21 00:19 | portal.home (applications table), portal.notices, console.provider-detail (screening table), console.approval (screening results), public.directory, and the four tables of console.work-queues when filled | AC-4: every focused element is interactive | `div.usa-table-container--scrollable`, `tabindex="0"`, no role, no name; the table fits (client width 960, scroll width 960), so nothing can scroll | A keyboard user pressing Tab lands on a frame around the whole table that does nothing (an extra stop on each table). The stop has no role and no name; what a screen reader says there was not tested. Axe does not report it, and the focus ring is drawn. The same container on console.batch-jobs does overflow and is accepted there | "MAM-86 AC-4 portal.home", "portal.notices", "console.provider-detail", "console.approval", "public.directory", and "MAM-86 AC-4 console.work-queues with an entry in each of the four lists" |
| D2 (low to medium), CLOSED: no defect of the page but a decided reading (Q-137 A, 2026-11-21): the column header is the visible label, and the AC-6 check accepts it | console.risk-rules | AC-6: every input has a visible label (an accessible name from a visible label element) | 10 inputs `prefix-1` to `prefix-5` (text) and `tier-1` to `tier-5` (select) in the rules table, named only by `aria-label` "Taxonomy prefix, row N" and "Tier, row N" | Sighted users read the column headers "Taxonomy prefix" and "Tier" above the cells; screen-reader and speech-control users get the aria-label. Axe's `label` rule passes (every input has an accessible name, and it starts with the visible column header text); only the story's stricter wording (a visible label element) is not met. Decision for the lead: accept column headers as the visible label and reword AC-6, or add visible per-row labels | "MAM-86 AC-6 console.risk-rules" |

Observations without a failing test (nothing in the story asks for them; listed so they are not lost):

- console.batch-jobs: the 20 runs listed at the seed have 16 "Download" buttons that carry only 3 distinct accessible names (for example 14 times "Download T-MSIS extract 31 Mar 2026"). A keyboard or screen-reader user cannot tell the runs apart from the button name; the archive generation is in the Counts column of the same row.

Test faults found and repaired while writing the tests (not UI defects): the Tab walk started after the results table on pages where a search had just been pressed (the starting point of Chrome's sequential navigation is the last click; fixed by putting the focus on the body first); the arrow-key helper could only move down (needed one step up in J7); the injected low-contrast paragraph had no padding and axe marked it "partially obscured" (fixed with padding); two patterns of the suppression checker matched `test.describe.configure` and its own label text.

## Exceptions and suppressions

- Exception to the keyboard-only rule (native file dialog, `setInputFiles`): none used.
- Axe rules disabled, excluded or narrowed without a written justification: none. One justified narrowing: the AC-1 tag list in `axe-gate.ts`.
- Typing the first address of a browser tab (`page.goto`) is done by script; everything after it in the keyboard journeys is keyboard input.

## What was not verified

- No screen reader or other assistive technology was used (out of scope of the story). Names, roles and the announcement of the error summary (`role="alert"`) were checked only through the browser's accessibility tree and focus behaviour.
- Only Chromium at 1280 by 720. Not checked: narrow screens and reflow at 320 px, zoom to 200 percent and 400 percent, text-spacing overrides, forced-colours (high contrast) mode, dark mode, other browsers.
- Contrast of the focus indicators themselves and of component borders (WCAG 1.4.11) was not calculated; the tests prove that an indicator is drawn that was not there before, not how strong it is. Text contrast of 23 elements that axe could not decide was measured by script from the computed colours, ignoring background images (see above).
- Reading order was judged from the position of each focus stop (rule and tolerance above), not by a person reading the page.
- States not scanned: loading states, "The list could not be loaded" alerts, the sort and pager controls in use, the signed-out page, the landing page, the not-found page, the sign-in callback pages, other form errors than the ones listed. The Keycloak sign-in page is out of scope.
- Five portal screens and the approval page were scanned with synthetic gateway answers (live data is only seen on them in J1 to J4 through the same checker). The work-queue lists were scanned filled only with synthetic answers (live data in J1, J3 and J6).
- Every result of the page scans and of the default suite is for the build that the stack served from 2026-11-21 00:19 (`modern/web-ui/dist`, files dated 00:19:47), which the lead built and deployed; the test-writer did not build. Whether that build holds exactly the working tree of `modern/web-ui/src` at 00:19 was not checked by the test-writer. The seven keyboard journeys were not run on it (they passed on the build of 2026-11-20 22:02).
- The 20 component tests of `ScrollableTable` run in jsdom with a stubbed `ResizeObserver` and stubbed `scrollWidth` and `clientWidth` (jsdom has no layout). They prove what the component does with a measurement, not that the browser measures correctly; that is shown only by the Tab walks on the live pages (on the five screens the container is no longer a stop; on console.batch-jobs the overflowing container is a stop named "Recent runs (scrollable table)").
- Case (b) of the label rule was proved on small synthetic pages and on the one real page that uses it (console.risk-rules). Other tables with inputs in cells would be judged by the same rule, but none exists on the 13 screens.
- Hidden text. What the label check does not detect is the exact list under "Page structure (AC-6)". Items 1 to 8 of that list, and the readable labels that the check reports anyway, were each run through the check by a throw-away probe (a scratch Playwright spec in the session scratchpad, not a recorded test), so they are not pinned by the hash file; items 9 and 10 are known from the code and the decision. No other hiding method was tried: any method that the statement does not name is not detected.
- The pages and keyboard specs run only with `PEM_JOURNEYS=1`; they are not part of `make e2e` until the lines in the story result are added to `modern/pem.sh`.
- In the first round one final run was started while the host reported 575 MB free memory (the rule is to wait below 1024 MB); it completed normally. In the correction round the free memory was 1382, 3088 and 1876 MB before the three runs, so no run was started below the limit.
- Correction round of 2026-11-21 night: free memory was read at 1724 MB before the first browser run (the gate cases, then the old-checker and mutant runs followed without a second reading), at 1580 MB before taking the lock and at 1431 MB before the page scans; the default suite ran straight after the page scans without a new reading. No reading was below 1024 MB.
- The `seen` test uses `checkVisibility` (closed details elements, content-visibility hidden), whose behaviour was probed and tested in Chromium 159 only; other engines were not tried. The probe that lists the methods the check does not detect is a throw-away script, not a recorded test, so those limits are not pinned by the hash file.
- Second correction round of 2026-11-21 night: free memory was below 1024 MB at the first readings (693, 1018, 325, 235 MB), then 1170 MB, and 1086 MB just before the first red run; one single-test run (the corrected guard against the old checker) was started at 728 MB, because the reading and the start were in one command that did not wait: it completed normally; every later run waited for 1024 MB or more (a helper script read the memory, and waited 60 seconds when it was below). The first batch of mutants was stopped after a few minutes because its memory wait did not work (the shell it called was the wrong one); it was started again with the wait working, and only that second batch is reported.
- Commit 6e24725 (2026-11-21 03:22:58, not made by the test-writer) was made while mutant M19 was applied to `e2e/a11y/support/structure.ts` for one run (the mutants change the file for about a minute each and restore it byte for byte afterwards). So HEAD holds the loop line `for (let up = node.parentElement; up !== null; ...)` without the exclusion of the html and body elements. The working tree has the right version (sha256 3672012c... at the time, hash file recorded afterwards); the next commit corrects HEAD. The mutation runs changed the file in place; they should have used a copy.
- The number of inputs that the label check reads on the live pages (32 on 7 screens) was counted in the round of 01:10 to 01:40 and not again in the second correction round; the 14 AC-6 page tests pass.
