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
