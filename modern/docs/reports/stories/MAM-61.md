```
Phase / item:      MAM-61 Project ACTIVE providers into the directory read model
Outcome:           DONE (built test-first), In Review
What was done:     ProviderRegistered/ProviderStatusChanged/RiskTierAssigned -> directory_entry upsert; only status A is published; composed display name <= 60 (D-028); public GET /api/v1/directory/providers
Files created/changed: DirectoryRules (domain); DirectoryEntry, DirectoryPage, DirectoryStore, ProviderDetails, ProviderFacts, ProjectDirectory (application); JdbcDirectoryStore, ProviderServiceClient, ProviderEventsListener (infrastructure); DirectoryController (api) in modern/services/directory-service
Checks run:        ./gradlew :services:directory-service:check -> green, 23 tests, PIT 94 %; live: J1 AC-6
Not verified:      independent code review per service (Part D)
Questions for you: none
Next step:         Part D gates (PIT, coverage, review)
```
Backfilled 2026-11-19 by the lead from the session record (PROGRESS.md activity rows and Jira report comments); the story was
built test-first earlier the same day.
