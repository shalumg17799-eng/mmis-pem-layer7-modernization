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

## Lane 3 tests (2026-11-20)

Outcome line unchanged. This story already had recorded tests (`MAM-18.tests.sha256`, 9 files, `test_protection.py check MAM-18` prints OK; none of them was changed). The screening-service lane added tests that carry the tag of this story in files recorded under MAM-20, MAM-22, MAM-28 and MAM-32: `ScreeningEventContractTest` (a hostile correlation id of an incoming event is replaced, AC-1, lesson g), `HttpScreeningSourcesTest` (the real HTTP client of the four sources, AC-2, AC-5, AC-6), `ScreenProviderReasonsTest` and `ApplicationRecordsTest` (AC-1).
- Tests per AC (Cucumber scenarios plus JUnit invocations, recorded and added together): AC-1: 15; AC-2: 3; AC-3: 2; AC-4: 2; AC-5: 4; AC-6: 3; AC-7: 1. All passed.
- Last full run: 705 tests, 65 failures, 0 skipped; JaCoCo line coverage 99.69 % (638 of 640 lines); PIT 98.59 % (70 of 71 mutations killed); the 65 red tests belong to MAM-20, MAM-22, MAM-25 (one), MAM-28 and MAM-32; details in `MAM-20/red-report.md` to `MAM-32/red-report.md`.
