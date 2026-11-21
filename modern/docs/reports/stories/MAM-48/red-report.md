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

## Test addition at the end of the lane (2026-11-21, additive)

Why: after the review round the java-developer changed `JdbcRevalidationStore.findCase` (line 127) and `openCaseOf` (line 139) to read
`FOR UPDATE`, so that two completions of one case at the same moment are serialised (AC-3: the second must be answered 409; before,
both were answered 200 and the screening of the second caller was not stored: observation 1 above). That change had no test, and the
user's standing wish for such cases (Q-132) is that the concurrent case IS a test. No recorded test was weakened or removed.

New test class `api/CaseCompletionConcurrencyTest` (19 tests; recorded under MAM-48 and, for the last 5, MAM-49). All 19 are green at
once in the real module (run 3 and run 7, 15 s). Two techniques, no sleeping:

- Deterministic: the test holds the row of the case locked in a transaction of its own (`SELECT ... FOR UPDATE` on a connection that is
  not from the application's pool, so the pool of 5 can never run dry), starts the callers in threads, proves from `pg_stat_activity`
  (`wait_event_type = 'Lock'`, statement on `revalidation_case`) that they wait (Awaitility, 20 s guard; a request that does not wait
  fails the test), then lets the first transaction end (commit after closing the case, or rollback) and reads the answers (30 s guard
  per answer, so a defect fails and never hangs).
- Natural: two callers released together by a latch, over 30 fresh cases (30 rounds in 1 s).

| Test (all in `CaseCompletionConcurrencyTest`) | AC | What it proves | In the copy without the two `FOR UPDATE` |
|---|---|---|---|
| `waitingRequestIsRefused` | AC-3 | a request that waited for the row of a case closed in the meantime is answered 409 problem+json | FAILS: expected 409 but was 200 |
| `waitingRequestKeepsTheFirstScreening` | AC-3 | the stored case keeps the first screening id | passes (the second UPDATE matches no row, so the stored values stay right: only the answer is wrong) |
| `waitingRequestKeepsTheFirstClosedAt` | AC-3 | one closedAt (the first), version 1 | passes (same reason) |
| `secondRequestWaitsForTheUncommittedFirst` | AC-3 | a real first completion (`CompleteCase.byRequest` in a transaction not yet committed): the second request waits, is answered 409, the first answer equals the stored case | FAILS: expected 409 but was 200 |
| `twoWaitingRequestsOneWins` | AC-3 | two requests waiting together: answers are exactly 200 and 409 | FAILS: [200, 200] |
| `twoWaitingRequestsLoserIsAProblem` | AC-3 | the 409 is problem+json | FAILS: expected 409 but was 200 |
| `twoWaitingRequestsStoredIsTheWinner` | AC-3 | the stored case (screening id, closedAt, version 1) is the one of the 200 answer | FAILS: the stored screening id is that of the other request |
| `requestAndEventOneCloses` | AC-3 | a request and `CompleteCase.byScreening` (REVALIDATION) waiting together: exactly one closes the case, the request is 200 or 409 | FAILS: request 200 and event closed |
| `requestAndEventStoredIsTheWinner` | AC-3 | the stored case has the screening of the one that closed it | passes in the copy only when the request happened to be first |
| `twoEventsOneCloses` | AC-4 | two REVALIDATION events for one provider: exactly one closes the case, stored = its screening | FAILS: both report closed |
| `latchedRequestsOneWins` | AC-3 | 30 rounds of two requests released together: every round exactly one 200 and one 409 | FAILS in round 0: [200, 200] |
| `latchedRequestsStoredIsTheWinner` | AC-3 | every round: the stored case equals the 200 answer, version 1 | FAILS in round 0 |
| `latchedRequestAndEventOneCloses` | AC-3 | 30 rounds of a request and an event released together: exactly one closes | FAILS in round 1 |
| `latchedRequestAndEventStoredIsTheWinner` | AC-3 | every round: the stored case carries the winner's screening | FAILS in round 0 |

Request against event (`CompleteCase.byScreening`) is deterministic too: the Kafka listener only adds the `processed_event` marker
and calls `byScreening`, which returns whether it closed a case; the tests call it directly (`EventListenersTest` already covers the
listener and its marker). Completion against the cancellation by a ProviderStatusChanged (the other user of `openCaseOf`) is
MAM-49 AC-2, see `MAM-49/red-report.md`.

SHOWN THAT THEY CAN FAIL: a copy of the build (`scratchpad/rev-scratch/modern`: buildSrc, the three libs, the module, contracts,
testdata; `src/main` identical to the repository except that the two `FOR UPDATE` clauses are removed; never an edit of the
repository) was run under the Gradle lock with `:services:revalidation-service:test --tests ...CaseCompletionConcurrencyTest
--no-build-cache`. Result: 19 tests, 12 failed (11 of MAM-48 as in the table, and `completionAfterTheCancellationIsRefused` of
MAM-49), 7 passed (the 5 marked "passes" above and the 2 `trackedStatusFollowsInBothOrders`, which are about the read model).
Log `scratchpad/reval-w/run5-scratch-noforupdate.log`, results `scratchpad/reval-w/run5-test-results/`. (A first attempt at the copy
failed to build because my copy had dropped the folder `buildSrc/.../pem/build`; the copy was repaired and the run repeated.)

Correction of a recorded step (rule Q-097 C, nothing weakened): `acceptance/RevalidationSteps.openCases` read the open cases of a
provider with two GETs (DUE, then IN_SCREENING) and failed once in the full run (run 4, MAM-45.feature:224 "After a failed run the
next run still works", step "has exactly one open case"): "Expected size: 1 but was: 2" with the SAME caseId listed as DUE and as
IN_SCREENING. A case opened by a sweep moves from DUE to IN_SCREENING by itself once its RevalidationDue is out (MAM-49 AC-1, the
relay polls every 500 ms), which can happen between the two reads. It now reads the cases once and keeps those in DUE or
IN_SCREENING; two different open cases still fail it. Same file is in MAM-45, MAM-48 and MAM-49 (all three re-recorded). A code
defect it is not: each single list call is consistent. To report in the Jira comment of the stories (Q-097 C).

Gate (run 7, 05:32-05:42, `:services:revalidation-service:check --continue`, whole module): see the MAM-45 section of this lane
(`MAM-45/red-report.md`) for the numbers of the module; MAM-48's own tests all pass.
