```
Phase / item:      MAM-54 Read application status (a provider sees their own; an analyst sees all)
Outcome:           DONE (AC-1..AC-8), In Review
What was done:     - Red: MAM-54.feature (8 ACs), ReadApplicationsSteps, ReadApplicationTest; failed on the missing
                     GET /api/v1/applications/{applicationId}
                   - Green: ReadApplication (404 when unknown; a PROVIDER must be the submitter or own the NPI,
                     else 403, D-023), ApplicationReader/OwnedApplication, JdbcApplicationStore.byId, controller
                   - One test-data fix after recording (AC-4 used an NPI that already had a VALIDATED application,
                     so E103 fired correctly); see MAM-54/red-report.md; hashes re-recorded
Files created/changed: modern/services/enrollment-service/src/main/** (application, infrastructure, api);
                   tests under src/test/**; docs/reports/stories/MAM-54*
Checks run:        ./gradlew :services:enrollment-service:check -> BUILD SUCCESSFUL, 70 tests 0 failures,
                   PIT 36/38 (95%), test strength 97%; test_protection MAM-54, MAM-46 -> OK
Not verified:      independent code review; not yet deployed to the running stack
Questions for you: none
Next step:         MAM-63, MAM-60, then the portal and console screens
```

## Lane 3 tests (2026-11-20)

Outcome line unchanged. `MAM-54.tests.sha256` now holds 7 files (new: `ListApplicationsTest`).

- Tests per AC (JUnit + Cucumber, run 4 of 2026-11-21): AC-1 4+1, AC-2 1+1, AC-3 2+1, AC-4 0+1, AC-5 4+2, AC-6 11+1, AC-7 2+1, AC-8 4+6 (the role outline: 6 examples).
- New: `ListApplicationsTest` (6 unit tests, AC-1 and AC-5): a PROVIDER's query is bound to their login and the NPI of their token, staff ask without a binding, the filters and the paging are passed on, the page is returned
  as it is. It kills the one PIT mutant that survived the first run (`ListApplications.list` returning null).
- Lesson e (% and _ literal): `ApplicationLessonsTest` refuses `100300011%`, `%003000111`, `%`, `_`, a backslash as NPI filter and `VAL%`, `_ALIDATED`, `%` as status filter with 400 problem+json (the filters are exact
  values, never patterns): green. AC-8: 401 and 403 are application/problem+json on both reads: green.
- Result of the module: see the MAM-56 report (467 tests, 21 failures, none of MAM-54; JaCoCo 98.6 %; PIT 109/109).
