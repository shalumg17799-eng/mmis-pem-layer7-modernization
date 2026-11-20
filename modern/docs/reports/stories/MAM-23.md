```
Phase / item:      MAM-23 Save the risk tier on the provider
Outcome:           DONE (built test-first with MAM-21), In Review
What was done:     RiskTierAssigned -> risk tier saved on the provider (D-002)
Files created/changed: ScreenFlag (domain); ProviderChanges, RecordVerdicts (application); VerdictEventsListener, JpaProviderChanges (infrastructure) in modern/services/provider-service
Checks run:        ./gradlew :services:provider-service:check -> green, 99 tests, PIT 97 %; live: J1 AC-3 (tier L)
Not verified:      independent code review per service (Part D)
Questions for you: none
Next step:         Part D gates (PIT, coverage, review)
```
Backfilled 2026-11-19 by the lead from the session record (PROGRESS.md activity rows and Jira report comments); the story was
built test-first earlier the same day.
