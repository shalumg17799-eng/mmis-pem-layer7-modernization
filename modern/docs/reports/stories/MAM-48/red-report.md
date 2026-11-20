# MAM-48 red report (test-writer, lane 3, 2026-11-21)

Situation: the story was built on 2026-11-19 without tests (Part D), so these tests were written after the code. "Red" means a
test fails only where the code does not meet an acceptance criterion or a lesson of the earlier reviews; tests of code that
already works are **green at once**. Nothing was skipped, disabled or filtered.

- Spec check: the tests follow `modern/specs/MAM-48.md` (8 ACs); the Jira text was not compared with it again in this lane.
- The drafts of 2026-11-19/20 compiled as written; they were completed, formatted (test sources only) and recorded. The old drafts
  in `MAM-48/part-d-drafts/` are superseded and were left in place.
- Hashes: `MAM-48.tests.sha256` holds 21 files (recorded 2026-11-21 after run 2; `test_protection.py check MAM-48` prints OK).
  Shared support (`IntegrationTest`, `RevalidationFixtures`, the Cucumber configuration and steps, `InMemoryStore`,
  `ContractFile`, `EventsContractFile`) is in the lists of all three stories of this service.

## Runs

| Run | When | Result |
|---|---|---|
| 1 | 2026-11-21 03:00-03:10 | 499 tests of the module, 22 failed, none of them a test of MAM-48 |
| 2 | 2026-11-21 03:24-03:34 | **529 tests of the module, 19 failed, none of them a test of MAM-48**; PIT 41/41 (100 %); line coverage 99.74 % |

## Tests that fail

None for this story: all 215 tests of MAM-48 pass (run 2). The code meets AC-1 to AC-8 as built, including the lessons that
apply: 400 problem+json for an unreadable completion body and for the values the list refuses, 404 and 409 problem+json,
401 and 403 problem+json for both operations, `%`, `_` and a backslash as NPI filter are refused (the filter is an exact
10-digit match), a correlation id in a consumed ScreeningCompleted is replaced when unsafe (never reaches the log), the
marker "event applied" is written in the same transaction as the case change (a failed close leaves no marker and the
redelivered event closes the case exactly once).

## Green at once (tests written after the code)

Per AC (JUnit + Cucumber examples, run 2): AC-1 51, AC-2 34, AC-3 16, AC-4 35, AC-5 5, AC-6 14, AC-7 7, AC-8 53.

New in this lane:

- `NextDueDateFollowsProviderTest` (4 tests, AC-5 and AC-7). It states which date the tests assume and why (question Q-018,
  default A applied, nothing to change): revalidation-service never computes a due date. After a PASS the case is COMPLETED,
  the tracked date stays 2026-02-28 and nothing but the case's own RevalidationDue is announced; the next date reaches the
  service only as the `revalidationDueDate` of a ProviderStatusChanged (A to A). The parity finding is that provider-service
  writes the processing date of its clock plus five years, not the business date of the sweep plus five years, so the tests
  feed both candidates as event data: 2031-03-31 (2026-03-31 plus five years, the example of the AC) and 2031-11-21
  (2026-11-21 plus five years). The sweep of the day before opens no case, the sweep of the day itself opens a case with
  exactly that due date. A FAIL moves no date either (AC-5).
- `RequestFailureTest` (2 of its 3 tests, AC-2): a database failure while a case is completed is a short 5xx for the real web
  server (body of at most 200 characters, no SQL text, table or driver name), the case stays DUE, the whole exception is logged
  at ERROR, and the same request works once the database is healthy again.
- `ConsumedEventFixturesTest` (AC-4, AC-6 and others): the ScreeningCompleted events of the tests (reasons INITIAL, REVALIDATION,
  MANUAL; results PASS, FAIL) conform to `ScreeningCompletedEnvelope` of `events.yaml`; the checker refuses a reason or a result
  outside the enums.

Not tested here, and why: the second half of AC-5, "the screening is listed by GET /api/v1/screenings?result=FAIL", is
screening-service behaviour (MAM-25).

Observations (no test; reported to the lead, see the Lane 3 section of the story report):
1. Two completions of the same open case at the same instant both answer 200: `CompleteCase.byRequest` reads the case, then
   `JdbcRevalidationStore.close` updates `WHERE status IN ('DUE','IN_SCREENING')`. The second answer shows its own `closedAt`,
   which is not the stored one. Sequential calls are correct (200, then 409: tested).
2. Between a PASS and the arrival of the new due date from provider-service, the tracked date is still the old one. A sweep in
   that window would open a new case for the same overdue date. The AC-7 order (event, then sweep) is tested; the window is not
   asserted because no document says what should happen in it.
