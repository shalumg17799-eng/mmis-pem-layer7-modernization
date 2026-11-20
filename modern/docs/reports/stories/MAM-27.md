```
Phase / item:      MAM-27 Analyst approval or denial of a pending provider
Outcome:           DONE (built test-first), In Review
What was done:     POST /api/v1/providers/{npi}/approval: only PENDING; APPROVE needs screen flag P -> ACTIVE, due date today + 5 years (D-003, D-019); DENY needs a flag other than U -> DENIED; 404/409/422; ProviderStatusChanged with actor
Files created/changed: Decision, DecisionRefused, Approval (domain); DecisionWriter, ProviderStatusChangedData, DecideProvider (application); JpaProviderChanges (infrastructure); ProviderController (api) in modern/services/provider-service
Checks run:        ./gradlew :services:provider-service:check -> green, PIT 97 %; live: J1 AC-5 (Active, due +5 y)
Not verified:      independent code review per service (Part D)
Questions for you: none
Next step:         Part D gates (PIT, coverage, review)
```
Backfilled 2026-11-19 by the lead from the session record (PROGRESS.md activity rows and Jira report comments); the story was
built test-first earlier the same day.
