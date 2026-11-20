```
Phase / item:      MAM-81 Journey J6 end to end: monthly revalidation
Outcome:           BUILT (build-phase: journey test written by the lead after the build, on the user's instruction 2026-11-19 20:00); AC-1..AC-6 pass live
What was done:     - One Playwright test: admin1 runs the revalidation sweep for 03/31/2026 on /console/admin/jobs, run polled to COMPLETED,
                     queued = ACTIVE providers due on or before 2026-03-31 (4 at the seed state); REVALQ downloaded in the browser
                     and equal to the API file, 80-byte records checked field by field; one case per due provider, none for
                     the others; queue never lists a provider twice; cases COMPLETED and new due date > 2026-03-31; a second
                     sweep adds no case; axe on every page
                   - AC-2 note: re-screening completes a case within seconds, so 'appears under Revalidation due' is checked as
                     one case per due provider (API) and at most one queue row per provider (UI), not as presence in the queue
Files created/changed: web-ui/e2e/journeys/j6-revalidation.spec.ts (test; listed in MAM-81.tests.sha256 where recorded)
Checks run:        ./pem.sh journey j6-revalidation 1 -> PASSED 20:12 (1.4 min); ./pem.sh journey j6-revalidation 2 -> 2 of 2 PASSED 20:21-20:23
                   (3 of 3 from a fresh seed each, AC-6); tsc --noEmit and eslint clean; hashes recorded (MAM-81.tests.sha256)
Not verified:      RevalidationDue / ScreeningCompleted payloads on Kafka (Q-094 A: proven by effects here, payloads by service tests); code review
Questions for you: none
Next step:         Part D: code review, repeat runs
```
(Report written by the lead on 2026-11-19 from the recorded runs above.)
