```
Phase / item:      MAM-86 Accessibility: axe on every page, keyboard-only journeys J1 to J7, focus, contrast, page structure
Outcome:           DONE (closed by the lead on 2026-11-21; details in modern/docs/reports/accessibility-report.md)
What was done:     - Tests first (web UI, Playwright with axe-core 4.14.1, WCAG 2.0 and 2.1 A and AA): page scans of the 13
                     screens with their error, empty and refused states (77 tests; AC-1, AC-2, AC-4, AC-5, AC-6), the seven
                     journeys with the keyboard only (7 tests; AC-3), the gate and repository checks (117 tests; AC-7 and
                     101 cases of the AC-6 label check), 20 component tests of ScrollableTable. 221 tests in 12 recorded files.
                   - Defect found by the tests and fixed (D1): a table container was a Tab stop although nothing could
                     scroll. One shared component, ScrollableTable (a Tab stop with a name only while the table overflows),
                     now wraps 15 tables in 9 files; live since UI deploy 3 (2026-11-21 00:21).
                   - Decision Q-137 A (default, for the user to revisit): on the Risk rules page the visible column header
                     is the label of the inputs under it; the page was not changed. The AC-6 label check was brought in
                     line with that in two correction rounds, each after an independent review (what counts as hidden).
                   - `./pem.sh a11y` and `make a11y` run the page scans and the keyboard journeys; `./pem.sh e2e` calls them.
Files created/changed: web-ui/e2e/a11y/{pages,keyboard-journeys,gate,policy}.a11y.spec.ts (keyboard-journeys.spec.ts),
                   web-ui/e2e/a11y/support/{api,axe-gate,focus,keyboard,screens,sessions,structure}.ts,
                   web-ui/tests/ux/scrollable-table.test.tsx; web-ui/src/shared/ui/ScrollableTable.tsx and its 15 uses
                   (9 files); modern/pem.sh, modern/Makefile; docs/reports/accessibility-report.md,
                   docs/reports/stories/MAM-86/red-report.md; docs/analysis/questions.md (Q-137)
Checks run:        2026-11-21 04:47-05:01 on the current stack (web UI build 00:19, back end deploy 6), under the stack lock:
                   page scans 77 of 77; keyboard-only journeys 7 of 7; gate and repository checks 117 of 117; default suite
                   with mocked gateway 279 of 279; no test skipped or flaky. Component tests 20 of 20 (03:42).
                   Label check: 39 new cases red on the old checker, 101 of 101 green on the new one, 21 of 21 mutants killed.
                   test_protection.py check MAM-86 -> OK (12 files); MAM-80, MAM-92, MAM-34 -> OK.
                   Reviews: three reviews of the label check; the last (04:44) confirmed code, tests and runs and left three
                   points about report sentences; these and seven more from an independent check were put into the reports.
Not verified:      the last text pass was not reviewed again; one browser (Chromium); no screen reader; the limits of the
                   label check are listed exactly in the accessibility report and the list does not claim to be complete.
Questions for you: 1. Q-137: accept the column header as the visible label of the inputs of the Risk rules table (A, applied),
                      or give each input its own visible label (B; needs a change of the recorded MAM-92 AC-1 test)?
                      Recommended: A.
                   2. The 16 "Download" buttons of the Batch jobs page carry only 3 distinct names (an observation of the
                      tests, no criterion asks for more): leave, or add the run to the name? Recommended: add it, as a small story.
Next step:         MAM-88 (performance smoke), then epic MAM-14 can be closed
```
