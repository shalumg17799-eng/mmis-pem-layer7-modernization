```
Phase / item:      MAM-37 Provider lookup and search (replaces CICS PINQ)
Outcome:           BUILT (build-phase: tests in Part D), batch A3
What was done:     - Spec check: Jira MAM-37 description = modern/specs/MAM-37.md (same 8 ACs)
                   - Built in provider-service: searchProviders takes every contract filter: exact NPI, exact Medicaid ID
                     (1 to 9 characters, else 400), name contains (last, first, legal or DBA name, case-blind, LIKE
                     wildcards escaped), status, screen flag; all given filters must match; no filter lists every
                     provider, paged and ordered by NPI (work queues). ProviderSearch.Criteria + a JPA Specification
                     replace the query-by-example; the 400 "not available yet" and "give a filter" refusals are gone
                   - AC-8: new api/ProviderProblems: DataAccessException or TransactionException -> 503 problem+json
                     (never 404), logged at ERROR with the request's correlation id; the kit filter already echoes
                     X-Correlation-Id on every answer
                   - AC status: AC-1 built (earlier lookup), checked live on ALPHA (every field returned); AC-2 built,
                     checked live (1003000999 -> 404 problem, no other provider's data); AC-3 built (the typed NPI is the
                     key, no session), checked live in passing; AC-4 built, checked live (FOXTROT shows S); AC-5 built,
                     checked live (?medicaidId=989455927 and ?npi=1005940865 -> exactly ALPHA, total 1; ?npi=1003000999
                     -> 200, items [], total 0); AC-6 built, checked live on the seed (?status=P&screenFlag=U -> DELTA and
                     HHA only; ?name=alpha -> ALPHA; unfiltered page 0 size 20 -> total 16; page 1 size 10 -> 6 items,
                     total 16); AC-7 built, checked live (provider1: 403 on the search and on another NPI; analyst1,
                     supervisor1, admin1 and the SYSTEM client: 200 on both; no token 401); AC-8 built, not checked live
                     (needs the provider database stopped)
Files created/changed: modern/services/provider-service/src/main/java/gov/medicaid/pem/provider/
                     application/ProviderSearch (Criteria), infrastructure/ProviderJpaRepository (Specification
                     executor), JpaProviderChanges (search), api/ProviderController (searchProviders), api/ProviderProblems
                     (new)
Checks run:        ./gradlew --no-daemon :services:provider-service:test -> 142/142 existing tests pass; bootJar;
                   pem-apps restarted and healthy; live checks above
Not verified:      automated tests (Part D); AC-8 live; AC-1/AC-6 with the journey NPIs 1003000111/444/222 exactly as
                   written (checked on the seeded providers instead)
Questions for you: none
```

## Lane 3 tests (2026-11-20)

Outcome line unchanged. Tests for every AC of MAM-37 (AC-8 also with the real outage).

- Test files (10, recorded in `MAM-37.tests.sha256`): `ProviderSearchApiTest`, `ProviderReadFailureTest`, `ProviderOutageTest`, `features/MAM-37.feature`, `ProviderSearchSteps`, `HttpAnswerSteps`, `ProviderFixtureSteps`, `LogCapture`, `ProviderRows`, `junit-platform.properties`.
- Tests per AC (JUnit + Cucumber scenarios): AC-1 3+1, AC-2 1+1, AC-3 2+1, AC-4 6+1, AC-5 13+1, AC-6 15+2, AC-7 9+1, AC-8 6+1.
- Written after the code. Found by the first run of the earlier lane: the exception was not attached to the log call (AC-8, D-024); fixed in `ProviderProblems` and verified. Lesson e (percent, underscore and backslash literal) holds.
- No open defect of this story.
Runs (all on 2026-11-20, from `modern/`, one Gradle build at a time through the shared lock, `--no-daemon`):
- `./gradlew -I <init script: Spotless on src/test of provider-service only> :services:provider-service:spotlessApply` (formats the test sources only).
- `./gradlew :services:provider-service:check --continue` (tests, Spotless on everything, ArchUnit, JaCoCo, PIT). Last run 21:00-21:09: **417 tests, 9 failures, 0 skipped**;
  the 9 failures are the Q-124 tests (red by design until `PrvrecrdFile` is changed, see MAM-43); PIT **130 of 130 mutations killed (100 %)** on `domain` and `application`;
  Spotless and ArchUnit passed.
- `./gradlew :services:provider-service:jacocoTestReport -x test` (the report of the same run; with a failing test `check` does not reach `jacocoTestCoverageVerification`):
  **line coverage 98.34 %** (768 of 781 lines; 98.84 % without `*Application`, as the gate counts), branch 88.31 %. Gate 85 %.
- Not verified: the real outage against the running stack (the lead's journeys J1 and J2).
