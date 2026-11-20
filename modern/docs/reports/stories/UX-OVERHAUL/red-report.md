# UX-OVERHAUL red report

This story had no red-report.md; this file holds only the correction below (the tests of the story are listed in `modern/docs/reports/stories/UX-OVERHAUL.tests.sha256`).

## Test correction under Q-097 (plain dates + Reference, user decision 2026-11-19 19:54)

Cause: **the user decisions of 2026-11-19 19:54 that the UI now implements**, under the Q-097 C standing rule (superseded tests; scoping and wording only, no assertion weakened or removed, production code unchanged). **A2, plain dates:** every displayed date reads "19 Nov 2026", the day without a leading zero (`formatDate` in `web-ui/src/shared/format/format.ts`); typed date inputs stay MM/DD/YYYY, so no value a test types into an input was changed. **A1, application status page:** the application ID is the single text "Reference: <id>" under the h1, the "Application" row of the summary list is gone (the list keeps its title "Application", so region lookups still work), and the paragraph "Your application was accepted. ..." is removed.

Exact changes (`-` before, `+` after; the previous blob is the state at the start of this correction, line endings kept, a plain `diff` against it shows only these lines):

| File | Previous blob | New blob | Line endings | Changed lines (removed / added) |
|---|---|---|---|---|
| `tests/ux/portal-home.test.tsx` | `15f122d52f4f689ccc50333e20c47ad6f6fd9c4a` | `563d63a2275b2e606683c8e287725278624af6e5` | CRLF | 1 / 1 |

`tests/ux/portal-home.test.tsx` (CRLF)
```diff
@@ -67 +67 @@ describe('[UX] portal home: the "Your enrolment" summary', () => {
-    ['A', 'Active (A)', 'Your next revalidation is due on 03/02/2031.'],
+    ['A', 'Active (A)', 'Your next revalidation is due on 2 Mar 2031.'],
```

Test titles that named the old format were corrected for honesty (AC tags kept); typed date inputs were not touched.

**Runs (2026-11-19, one worker because the host was short of memory):** before the correction the full Vitest suite (`--maxWorkers=1 --testTimeout=60000 --hookTimeout=60000`, PowerShell, lowercase path) had **36 failures in 15 files (611 of 647 pass)**: 35 are exactly the lines of the A1/A2 list; the 36th is a timing failure in an unedited file, see the MAM-64 note. After the correction: **647 of 647 pass in 29 files**. `npx tsc -b` (it includes `tests` and `e2e`) and `eslint --max-warnings 0` on the 24 changed files: no findings.
The full default Playwright suite (16 specs, journeys excluded, `--workers=1`, `NODE_OPTIONS=--dns-result-order=ipv4first`, one run): **152 of 152 pass** (8.4 min). The Playwright suite was not run before the correction (no time; the UI agent's static list was used), so there is no before count for it.

**Hashes:** re-recorded with `test_protection.py record UX-OVERHAUL` with exactly the files of the story's `.tests.sha256`; `check` and `sha256sum -c` are OK.

Record correction (lead, 2026-11-19 20:58, code-reviewer round 2): line(s) 13, 15 labelled the file LF; it is CRLF (byte check by the reviewer). Only the label changed; the test file and its hash are unchanged.

## Tests added 2026-11-20 (test-writer, web UI lane): the overhaul and polish had almost no tests of their own

**Context.** The UX overhaul (B-i, B-iii) and the polish items 1 to 9 (B-iv) of 2026-11-19 were built in fast mode before their tests; only `tests/ux/portal-home.test.tsx` existed. These tests were written against the built UI, so **no new test was red on arrival**: the red step does not apply to them (there is no missing code to fail on). The only failures were mistakes in the new tests themselves, listed below; they were corrected in the test files before the hashes were recorded. **Production code (`src/**`) was not touched, nothing was built (`dist` untouched), no recorded test of any story was changed.** Naming: `[UX-OVERHAUL n]`, n continues the numbering of the existing file ("screen 1", portal home): 2 breadcrumb, 3 sorting, 4 empty state, 5 Tools panel, 6 summary lists, 7 step indicator, 8 approval brief, 9 plain words, 10 application status, 11 plain dates, 12 landing page, 13 error pages, 14 Review your answers, 15 live decision line, 16 queue tiles, 17 plain labels.

| File (new) | Tests | User decision checked | What it asserts |
|---|---|---|---|
| `tests/ux/breadcrumb.test.tsx` | 57 | Q-101 B3 | real USWDS `nav aria-label="Breadcrumb"` after the Primary navigation and outside main; trail of 12 screens x role; Home = start page of the role; current page plain text with aria-current; no breadcrumb on start, public, refused and 404 pages; no link repeats a menu name; Home link and keyboard order |
| `tests/ux/sorting.test.tsx` | 43 | B-i sorting | hook and header on a harness (none, ascending, descending, none; aria-sort; empty last; ties; numbers; natural text; keyboard) and the sortable columns and one real sort of every list table (search, directory, home, notices, 4 queues, jobs, 4 panels), no new request |
| `tests/ux/empty-state.test.tsx` | 18 | UX overhaul point 6, D-024 | shape of EmptyState (bold line first, then text, then actions), the five pages, never used for a failed load |
| `tests/ux/tools-panel.test.tsx` | 16 | Q-101 B2 | who sees the Tools panel (supervisor 1 tool, admin 2, analyst none), links, own wording, place, navigation, keyboard |
| `tests/ux/summary-lists.test.tsx` | 23 | B-iv 5, Q-109 | SummaryList, DefinitionList, RecordSummary; the lists on 6 pages incl. the approval page "Provider" list (the Part D note of 19:57); real tables stay tables |
| `tests/ux/step-indicator.test.tsx` | 23 | B-iv 1, Q-104 | five steps, current step highlighted, state as text, "Step n of 5", "Validation failed" at step 2 of a rejected application, on the status page |
| `tests/ux/approval-brief.test.tsx` | 32 | B-iv 2 and 4 | screeningSentences, riskSentence, whoSentence, the box, page order In short, results, decision, Provider list; plain source names and details on the page |
| `tests/ux/plain-words.test.ts` | 60 | B-iv 2 | sourceName, failedSourceText, screeningDetailText, auditActionText, noticeName, statusWithCatchAll, withCode |
| `tests/ux/application-status.test.tsx` | 21 | B-iv 7 and 8, Q-110 | "Reference: <id>" under the h1, no "Application" row, one green box "Application received" after a submit (router state), sentences on a later visit, badge, "Apply again" |
| `tests/ux/plain-dates.test.tsx` | 56 | B-iv 9, Q-110 | formatDate (12 months, no leading zero, instants), toIsoDate, 10 screens show only plain dates, typed dates keep MM/DD/YYYY |
| `tests/ux/plain-labels.test.tsx` | 11 | B-iv 2 and 3 | "Screening result" never "Screen flag"/"Screened"; badges in sentence case |
| `tests/ux/review-and-tiles.test.tsx` | 24 | Q-101 B4, B7, B2 | "Review your answers", the live "You are about to ..." line without a dialog, the queue count tiles |
| `tests/ux/landing-and-errors.test.tsx` | 33 | UX overhaul of every screen | landing page buttons and cards (and that the start page of each card is the real landing page of the role), "Page not found" and "Access denied" buttons and role words |
| `tests/support/uxWorld.tsx` | support | | renderAppWithState (router state), trail, sort and list helpers |
| `e2e/ux-overhaul.spec.ts` | 10 (Playwright) | | breadcrumb keyboard order and axe, sort click/Enter/Space + dark ink + axe, Tools panel for 3 roles + axe, 404 page axe and keyboard, menu on one line (2 roles), badge sentence case in the browser |

**Mistakes in the new tests found by the first run (test side, corrected before recording; no production code involved):**
- `plain-dates.test.tsx` (9 failures): the date regex used a word boundary on text where table cells run together ("10030001119 Mar 2026"): the text is now read with a space between elements; the form hints "for example 12/31/2026" (typed dates keep MM/DD/YYYY by decision) are left out of the sweep.
- `review-and-tiles.test.tsx` (8 failures): the NPI field has a maximum length of 10, so a padded value was cut (a shorter value is used); seven tests read the decision form before it had loaded (they now wait for the "Record decision" button).
- Every other new test (417 Vitest tests in 13 files, 10 Playwright tests) passed on its first run.

**Runs (2026-11-20, one worker because the host was short of memory; waited 60 s once for free memory above 1024 MB):**
- Vitest, full, first run (`--maxWorkers=1 --testTimeout=60000 --hookTimeout=60000`, PowerShell, lowercase path): 1063 of 1064 pass in 42 files; **one failure in a recorded, unchanged test of MAM-64**: `tests/portal/apply-submit.test.tsx:88` ("after the 201 the page opens /portal/applications/:applicationId and shows Validated") expected the path `/portal/applications/0b9c5d1e-...` and read `/portal/apply`, directly after the heading "Application status" had appeared (the router probe of the test world records the path in an effect that had not run yet). It is the file that already flaked once on 2026-11-19 (see the MAM-64 note above): a timing race of the test under load, not a UI defect. Nothing was changed (Q-097 C); it is reported.
- Vitest, full, second run, nothing changed in between: **1064 of 1064 pass in 42 files (966 s), no skipped test** (`scripts/no-skipped.mjs`: "no skipped tests (1064 tests)").
- Playwright, default suite (journeys excluded, `--workers=1`, `NODE_OPTIONS=--dns-result-order=ipv4first`, line + json reporter): **162 of 162 pass (8.1 min)**, 152 existing + the 10 new ones.
- `npx tsc -b` (includes `tests` and `e2e`): no findings. `npx eslint --max-warnings 0` on the 16 files of the story: no findings.
- Hashes: `test_protection.py record UX-OVERHAUL` with the 16 files (the old file `tests/ux/portal-home.test.tsx` unchanged, blob `563d63a2...`); `check` is OK for UX-OVERHAUL and for MAM-62, 64, 66, 68, 70, 73, 77, 80, 82, 85, 87, 90, 92 and 93.
