```
Phase / item:      MAM-83 Journey J7 end to end: month-end extracts and the public directory
Outcome:           BUILT (build-phase: journey test written by the lead after the build, on the user's instruction 2026-11-19 20:00); AC-1..AC-7 pass live
What was done:     - One Playwright test: admin1 runs the T-MSIS extract and the directory extract for 03/31/2026 on /console/admin/jobs,
                     downloads TMSISOUT (120 B: PRV01, Medicaid ID, NPI, A, 26090, spaces) and DIROUT (100 B: Medicaid ID, NPI,
                     type, tier, composed name, spaces) in the browser, both equal to the API files; the T-MSIS run is a new
                     list entry with its row count and archive generation; both files hold exactly the ACTIVE providers;
                     TESTPROV ALPHA is composed without the licence; SAMPLE CLINIC 01 legal name; a visitor finds SAMPLE
                     HOSPICE 01 and not the terminated HOSPICE 02 (UI and API without a token); axe on every page
Files created/changed: web-ui/e2e/journeys/j7-extracts.spec.ts (test; listed in MAM-83.tests.sha256 where recorded)
Checks run:        ./pem.sh journey j7-extracts 1 -> PASSED 20:14 (27.4 s); ./pem.sh journey j7-extracts 2 -> 2 of 2 PASSED 20:23-20:24
                   (3 of 3 from a fresh seed each, AC-7); tsc --noEmit and eslint clean; hashes recorded (MAM-83.tests.sha256)
Not verified:      ExtractProduced payload on Kafka (Q-094 A: shown by the listed run and its row count, payload by the MAM-75 tests); code review
Questions for you: none
Next step:         Part D: code review, repeat runs
```
(Report written by the lead on 2026-11-19 from the recorded runs above.)
