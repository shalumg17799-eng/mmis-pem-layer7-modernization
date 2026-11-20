```
Phase / item:      UX-OVERHAUL: tests for the UX overhaul and polish of 2026-11-19 / 2026-11-20 (web UI lane, test-writer)
Outcome:           DONE. 417 new Vitest tests in 13 files and 10 new Playwright tests; no UI defect found; one flaky recorded test reported
What was done:     The overhaul and polish were built in fast mode and had only one test file (tests/ux/portal-home.test.tsx). What they
                   changed, and what is now tested (user decisions in docs/PROGRESS.md "B-iv" and docs/analysis/questions.md Q-101, Q-104,
                   Q-109, Q-110):
                   - [2] breadcrumb: the real USWDS nav aria-label="Breadcrumb" after the Primary navigation, Home = start page of the role,
                     the provider detail in the trail of the approval and sanction screens (57 tests)
                   - [3] sortable table headers: button in the header, aria-sort, none -> ascending -> descending -> none, by raw value,
                     empty last, no new request; 12 list tables (43 tests)
                   - [4] shared empty state on the directory, notices, home (2 lists), provider record and provider search; never for a
                     failed load (18)
                   - [5] Tools panel: supervisor 1 tool, administrator 2, analyst none (16)
                   - [6] key-value tables replaced by summary lists (SummaryList, DefinitionList, RecordSummary), 6 pages (23)
                   - [7] step indicator: current step highlighted, state as text, "Validation failed" at step 2 of a rejected application (23)
                   - [8] approval brief: "In short", then the screening results, then the decision, then the Provider list (32)
                   - [9] plain words: source names, failed source with error code, sentence-case details with plain dates, audit and notice names (60)
                   - [10] application status: "Reference: <id>" under the heading, one green box "Application received" (21)
                   - [11] plain dates "19 Nov 2026" on 10 screens; typed dates keep MM/DD/YYYY (56)
                   - [12, 13] landing page cards and buttons; "Page not found" and "Access denied" buttons with the words of the role (33)
                   - [14, 15, 16] the three B-i additions that were also untested: "Review your answers" (Q-101 B4), the live "You are about
                     to ..." line (B7), the queue count tiles (B2) (24)
                   - [17] "Screening result" never "Screen flag"; badges in sentence case (11)
                   - e2e/ux-overhaul.spec.ts (10): breadcrumb keyboard order + axe, a real sort (click, Enter, Space) + dark ink + axe, Tools
                     panel for three roles + axe, 404 page axe + keyboard, menu on one line (2 roles), badge sentence case in the browser
                   - Red step: not applicable, the tests were written against the built UI and passed (the only failures, 17 in two files, were
                     mistakes of the new tests and are listed with their cause in UX-OVERHAUL/red-report.md)
Files created/changed: web-ui/tests/ux/{application-status,approval-brief,breadcrumb,empty-state,landing-and-errors,plain-dates,plain-labels,
                   review-and-tiles,sorting,step-indicator,summary-lists,tools-panel}.test.tsx, web-ui/tests/ux/plain-words.test.ts,
                   web-ui/tests/support/uxWorld.tsx (support), web-ui/e2e/ux-overhaul.spec.ts (all new, CRLF like the folder);
                   docs/reports/stories/UX-OVERHAUL.tests.sha256 (16 files), UX-OVERHAUL/red-blobs.txt, UX-OVERHAUL/red-report.md (section
                   2026-11-20), this report. No recorded test of any story changed; web-ui/src and dist untouched.
Checks run:        Vitest full (--maxWorkers=1 --testTimeout=60000 --hookTimeout=60000, PowerShell) 2026-11-20:
                     run 1: 1063/1064, 42 files; the one failure is tests/portal/apply-submit.test.tsx:88 (MAM-64, recorded, unchanged)
                     run 2 (nothing changed): 1064/1064, 42 files, 966 s; node scripts/no-skipped.mjs: "no skipped tests (1064 tests)"
                   Playwright default suite (journeys excluded, --workers=1, ipv4first) -> 162/162, 8.1 min (152 existing + 10 new)
                   npx tsc -b -> no findings; npx eslint --max-warnings 0 on the 16 files -> no findings
                   test_protection.py check -> OK for UX-OVERHAUL (16 files) and MAM-62, 64, 66, 68, 70, 73, 77, 80, 82, 85, 87, 90, 92, 93
Defect to watch:   tests/portal/apply-submit.test.tsx:88 (MAM-64 AC-2), flaky under load: it reads the router path right after the heading
                   "Application status" appeared and got "/portal/apply" instead of "/portal/applications/<id>" in run 1 (the path probe of
                   tests/support/world.tsx sets it in an effect that had not run yet); it passed in run 2 and alone. A race of the test, not
                   of the UI; reported, not changed (Q-097 C). The same test failed once on 2026-11-19 (MAM-64 red-report). Fix when the user
                   allows it: wrap the path assertion in waitFor (same assertion, no weakening).
Observations on the UI (no test fails, nothing asserted that would pin them; for the UI agent or Part D):
                   - ApprovalBrief.tsx:19 "All 1 checks passed" when a screening has one result (real screenings have 4 sources)
                   - format.ts detailDate: a detail "2028-13" is shown as "13 2028" (impossible month is not left as it was)
                   - ApprovalPage shows two links named "Provider detail" (breadcrumb and the link under the Provider list)
                   - the landing page of a visitor has two "Sign in" buttons (header and main), a strict-mode trap for browser tests
                   - Q-108 (Provider detail repeats five fields) is still open by default A; not asserted
Not verified:      - Negative control: the tests were not shown to fail on a broken UI (src is read-only for this role; no mutation run)
                   - Journeys J1 to J7 (excluded from the default suite; they need the freshly seeded stack)
                   - Playwright ran with the mocked gateway and the real Keycloak; the real back end was not exercised
                   - Layout below 64 em (mobile menu, breadcrumb on a narrow screen) and the visual look; contrast is covered by 5 axe scans
                   - CSS is off in Vitest (css:false); sort-button colour, tag case and the one-line menu are checked in the browser spec only
                   - Spec wording changed by Q-109 and Q-110 is not yet updated in the specs (Jira change loop, MODERNIZATION.md section 8)
                   - The JSON reports of run 2 and of the Playwright run were deleted from web-ui/test-results by another Playwright run
                     (the lead's journeys clean that folder); the numbers above are from the console output of the runs
Questions for you: 1. Fix the flaky assertion in the recorded MAM-64 test (waitFor around the path check)? Options: A) yes, one line, hash
                      re-recorded, documented under Q-097 C | B) leave it and re-run on failure. Recommended default: A.
                   2. The four UI observations above: fix in the UI (a small ui-developer change with tests first) or leave? Recommended: leave
                      the plural and the double Sign in; fix the impossible-month display in Part D.
Next step:         code-reviewer round on the 16 files of UX-OVERHAUL.tests.sha256; then Part D as planned
```
(Report written by the test-writer on 2026-11-20 from the runs listed above.)
