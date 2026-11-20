```
Phase / item:      MAM-48 Revalidation cases: list them and complete one
Outcome:           BUILT (build-phase: tests in Part D), batch A1
What was done:     - Spec check: Jira MAM-48 description = modern/specs/MAM-48.md (same 8 ACs)
                   - Built in revalidation-service: GET /api/v1/revalidations (ANALYST, SUPERVISOR; status DUE,
                     IN_SCREENING, COMPLETED, CANCELLED, else 400; npi 10 digits; page/size); POST
                     /api/v1/revalidations/{caseId}/completion (SYSTEM, ANALYST; screeningId + result PASS|FAIL) ->
                     200 COMPLETED with closedAt; 404 unknown; 409 already COMPLETED/CANCELLED (case unchanged);
                     ScreeningCompleted consumer: reason REVALIDATION (PASS or FAIL) completes the provider's open case,
                     any other reason or no open case is ignored; the due date is never set here (D-003, C-10)
                   - AC status: AC-1 built, checked live (200 page, status=OPEN 400); AC-2 built, checked live (ANALYST
                     200 COMPLETED with closedAt); AC-3 built, checked live (unknown 404, second completion 409);
                     AC-4 built (consumer), not seen live (the live REVALIDATION screening could not run: the tracked
                     NPI was no longer in provider-service after a seed); AC-5 built (FAIL completes; read model only
                     changes from ProviderStatusChanged; publishes nothing); the cross-service part "listed by GET
                     /api/v1/screenings?result=FAIL" is screening-service behaviour (MAM-25), not re-checked; AC-6 built;
                     AC-7 built (tracked due date follows ProviderStatusChanged), not checked live; AC-8 built, checked
                     live in part (ADMIN GET 403, SUPERVISOR POST 403)
Files created/changed: revalidation-service application/CompleteCase, CaseCompletions; infrastructure/
                     ScreeningEventsListener, JdbcRevalidationStore (completion methods); api/RevalidationController (POST)
                   test drafts for Part D (not run): modern/docs/reports/stories/MAM-48/part-d-drafts/
Checks run:        compile + bootJar + pem-apps healthy + live checks above
Not verified:      automated tests (Part D); AC-4, AC-6, AC-7 live
Questions for you: none
```

## Lane 3 tests (2026-11-20)

Outcome line unchanged. `MAM-48.tests.sha256` holds 21 files (recorded 2026-11-21, `test_protection.py check MAM-48`: OK). Tests were
written after the code (see `MAM-48/red-report.md`); all 215 tests of this story pass (run 2).

- Test files: `MAM-48.feature` and `RevalidationSteps` (Cucumber, 45 scenarios and examples), unit `CompleteCaseTest`,
  `FollowProviderTest`, `CaseStagesAndListTest`, `CaseStatusTest`; integration `CaseApiTest` (list, filters, paging, completion
  bodies, 404, 409), `JdbcRevalidationStoreTest`, `EventListenersTest`, `RevalidationCorrelationIdTest`,
  `NextDueDateFollowsProviderTest`, `RequestFailureTest`; contract `RevalidationContractTest` (roles of the five operations,
  401/403 problem+json, answer shapes against `revalidation-service.yaml` 1.1.0), `ConsumedEventFixturesTest`; support files
  shared with MAM-45 and MAM-49.
- Tests per AC (JUnit + Cucumber examples, run 2): AC-1 51, AC-2 34, AC-3 16, AC-4 35, AC-5 5, AC-6 14, AC-7 7, AC-8 53.
- Lessons: a database failure while a case is completed is a short 5xx, rolled back and logged (AC-2): green; `%`, `_` and a
  backslash are refused as NPI filter, the filter is an exact match (AC-1): green; the marker "event applied" and the case change
  are one transaction (AC-4): green; unsafe correlation ids of a consumed event never reach the log (AC-4): green; 400, 401, 403,
  404 and 409 are problem+json (AC-1 to AC-3, AC-8): green. Not applicable: lesson d (no call to another service), lesson b
  (no business date in this story).
- Q-018 (date of a completed revalidation): the tests assume that revalidation-service computes no date; the next date is the
  `revalidationDueDate` of a ProviderStatusChanged. They feed 2031-03-31 (business date 2026-03-31 plus five years, the AC
  example) and 2031-11-21 (processing date 2026-11-21 plus five years, what provider-service writes, Q-018 default A); both are
  followed on the day they fall due, not a day earlier. Nothing to change in this service.
- Commands and results: as in the MAM-45 section (run 2, 2026-11-21 03:24-03:34): 529 tests of the module, 19 failed (none of
  MAM-48), 0 skipped; PIT 41/41 (100 %); JaCoCo line coverage 99.74 % computed from the report.
- Defects: none in this story. Observations: two simultaneous completions of one case both answer 200
  (`application/CompleteCase.java:38-50` reads, `infrastructure/JdbcRevalidationStore.java:142-145` updates `WHERE status IN
  ('DUE','IN_SCREENING')`); between a PASS and the new date from provider-service a sweep would open a new case for the old date
  (no document covers that window).
- Not verified: the cross-service half of AC-5 ("listed by GET /api/v1/screenings?result=FAIL", screening-service MAM-25);
  Jira text against the spec; code review.
