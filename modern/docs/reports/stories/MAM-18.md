```
Phase / item:      MAM-18 Screen a newly registered provider against the four sources
Outcome:           DONE (built test-first with MAM-25), In Review
What was done:     ProviderRegistered -> INITIAL screening against LICBOARD, OIGLEIE, SAMGOV, SSADMF (WireMock, 2 s limit, 1 retry, Q-039 A); licence and name read from provider-service with a SYSTEM token (Q-040 A); per-source results stored; ScreeningCompleted published; GET /api/v1/screenings
Files created/changed: Source, SourceResult, SourceRules, Verdict (domain); ProviderFacts, ProviderFactsSource, ScreeningSources, ScreeningStore, EventOutbox, Screening, ScreeningPage, ScreeningCompletedData, ScreenProvider, ReadScreenings (application); ProviderServiceClient, HttpScreeningSources, JdbcScreeningStore, KitEventOutbox, ProviderEventsListener (infrastructure); ScreeningController (api) in modern/services/screening-service
Checks run:        ./gradlew :services:screening-service:check -> green, 49 tests, PIT 91 %; live: J1 AC-2 (four sources P)
Not verified:      independent code review per service (Part D)
Questions for you: none
Next step:         Part D gates (PIT, coverage, review)
```
Backfilled 2026-11-19 by the lead from the session record (PROGRESS.md activity rows and Jira report comments); the story was
built test-first earlier the same day.
