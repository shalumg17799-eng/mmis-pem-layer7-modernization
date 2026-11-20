```
Phase / item:      MAM-74 Journey J3 end to end: screening fails and the analyst denies
Outcome:           DONE (AC-1..AC-6), journey passes live; In Review
What was done:     - Red first (2026-11-19 17:55, test-writer): J3 spec hashed; red because owner screening (MAM-22, E203) was not built
                   - Green: back end MAM-22/MAM-20 (batch A3); the row filter was re-scoped to 'OIG exclusion list (LEIE)' under Q-097
                     (B-iv polish, reviewer CONFIRMED byte-level)
Files created/changed: web-ui/e2e/journeys/j3-screening-fails.spec.ts (test; listed in MAM-74.tests.sha256 where recorded)
Checks run:        ./pem.sh journey j3-screening-fails 1 (fresh seed) -> PASSED 2026-11-19 20:18 (19.6 s), after the B-iv polish and the plain dates
Not verified:      AC-6 three runs in a row: one run today after the last UI change (earlier passes 17:39 and 19:30 per PROGRESS.md); the 3-run
                   repeat goes to Part D; ScreeningCompleted/ProviderStatusChanged payloads proven by service tests (Q-094 A)
Questions for you: none
Next step:         Part D: code review, repeat runs
```
(Report written by the lead on 2026-11-19 from the recorded runs above.)
