# MAM-45 red report (test-writer, lane 3, 2026-11-21)

Situation: the story was built on 2026-11-19 without tests (Part D), so these tests were written after the code. "Red" therefore
means: a test fails only where the code does not meet an acceptance criterion or a lesson of the earlier reviews; a test of code
that already works is **green at once** (stated per AC below). Nothing was skipped, disabled or filtered.

- Spec check: the tests follow `modern/specs/MAM-45.md` (8 ACs); the Jira text was not compared with it again in this lane.
- Drafts of 2026-11-19/20 (26 Java files, never compiled before today) were brought to compile (they compiled as written),
  completed, formatted (test sources only) and recorded. The old drafts in `MAM-45/part-d-drafts/` are superseded
  (`RunSweepTest` there targets the removed executor constructor); they were left in place.
- Hashes: `MAM-45.tests.sha256` holds 28 files (recorded 2026-11-21 after run 2; `test_protection.py check MAM-45` prints OK).

## Runs

| Run | When | Command | Result |
|---|---|---|---|
| 1 | 2026-11-21 03:00-03:10 | `:services:revalidation-service:check --continue` (after formatting the test sources) | 499 tests, 22 failed, 0 skipped; PIT 41/41; Spotless: 4 production files |
| 2 | 2026-11-21 03:24-03:34 | the same, with 30 new tests and the correction below | **529 tests, 19 failed, 0 skipped**; PIT 41/41 (100 %); Spotless: the same 4 production files |
| 2b | 03:34 | `jacocoTestReport` (the verification task cannot run without the tests: see the Lane 3 section) | line coverage 389/390 = 99.74 % (without `*Application`) |

## Tests that fail and are kept (defects of the code, not of the tests)

| Test | AC | Expected | Actual |
|---|---|---|---|
| `MAM-45.feature` outline "A business date that is not an ISO date (yyyy-mm-dd) is refused", 6 of 11 examples: `2026-03-31T00:00:00`, `2026-03-31T00:00:00Z`, `"2026-03-31 "`, `" 2026-03-31"`, `[2026,3,31]`, the number `20260331` | AC-1 (lesson b) | 400 problem+json, no run | 202 BatchRun RUNNING; the first four and the array run the sweep for 2026-03-31, the number is read as an epoch day (business date `+57440-11-26`) |
| `SweepFailureTest.errorHasNoSqlText` x4 (CASE_INSERT, EVENT_INSERT, RUN_COMPLETE, JOB_LAUNCH) | AC-8 (lesson a) | the run's `error` has none of select, insert, update, sql | `PreparedStatementCallback; uncategorized SQLException for SQL [INSERT INTO revalidation_case (public_id, ...) VALUES (...)]; SQL state [P0001]; error code [0]; ERROR: SYNTHETIC DISK FULL Where: PL/pgSQL function ...` |
| `SweepFailureTest.errorIsShort` x4 | AC-8 (lesson a) | 1 to 200 characters | 355 (CASE_INSERT), 301 (EVENT_INSERT), 374 (RUN_COMPLETE), 337 (JOB_LAUNCH) characters |
| `SweepFailureTest.storedReasonIsShortAndHasNoSqlText` x4 | AC-8 (lesson a) | `sweep_run.failure_reason` at most 200 characters, no SQL text | the same texts are stored (the column accepts 500) |
| `MAM-45.feature` "The reason of a failed run is short and has no SQL text" | AC-8 (lesson a) | error at most 200 characters, no SQL text | 355 characters with the INSERT statement |

Code sites (reported, not changed): `api/SweepController.java:43` (`BatchRunRequest(LocalDate businessDate)`: Jackson's lenient
LocalDate reading) and `:77-81`; `application/RunSweep.java:40-42`, `infrastructure/SweepJobConfig.java:78-86` (`reason(job)`),
`infrastructure/SpringBatchSweepLauncher.java:50` and `infrastructure/JdbcRevalidationStore.java:204` (the reason is the raw
exception message, cut at 500). The failure of a run is otherwise correct: FAILED, never COMPLETED, nothing of the run stays,
a re-run applies the work once (the other 18 tests of `SweepFailureTest` pass).

## Test correction (before recording; premise of the test, no assertion weakened)

`RevalidationCorrelationIdTest.outgoingEventIsSafe`: in run 1 three of its seven invocations failed (header values with CR/LF,
a bare LF, an ESC and a tab: "Expected size: 1 but was: 0"). Cause: Spring Security's request firewall refuses a header value
with a control character with 400 and no body before the controller runs, so no sweep starts. MockMvc goes through the same
firewall as the real server, so the draft's premise (202) never was a case of the real system. Change: the method now takes only the
four crafted values that reach the controller (65 and 200 characters, a path with a quote, an empty string) and also asserts 202;
new test `controlCharactersAreRefusedBeforeTheController` (3 invocations): 400, safe echoed id, no run, no event, nothing
logged with the raw value. All assertions of the draft are kept. Same finding as in MAM-46 (enrollment lane).

## Green at once (tests written after the code)

Per AC, tests of MAM-45 in run 2 (JUnit + Cucumber examples): AC-1 57 (6 red, see above), AC-2 22, AC-3 27, AC-4 21, AC-5 9,
AC-6 13, AC-7 33, AC-8 69 (13 red), platform checks 3. Every other test passes. The levels: unit (`SweepRuleTest`,
`ExecuteSweepTest`, `ExecuteSweepFileFailureTest`, `RunSweepTest`, `RevalqFileTest`, `RevalqGoldenFileTest`), integration with
Postgres and Kafka (`SweepApiTest`, `SweepJobTest`, `SweepFailureTest`, `JdbcRevalidationStoreTest`, `EventListenersTest`,
`RequestFailureTest`, `RevalidationCorrelationIdTest`), contract (`RevalidationContractTest`, `RevalidationDueEventContractTest`,
`ConsumedEventFixturesTest`), acceptance (`MAM-45.feature`, 50 scenarios and outline examples).

New in this lane: `RevalqGoldenFileTest` (AC-2, AC-3, AC-7: the 16-record legacy master of the Hercules run PRVMTH01
20261120-seed with the sweep rule for the MVS date 2026-11-20 gives the 4 legacy REVALQ records, byte for byte; the golden
file read and written back by the codec gives the same 320 bytes), `ExecuteSweepFileFailureTest` (AC-8: a failing REVALQ
writer never completes the run), `RequestFailureTest` (AC-8: a database failure while the sweep is started is a short 5xx
without SQL text, no run, no job, whole exception logged), `ConsumedEventFixturesTest` (AC-3, AC-6: the events fed to the
consumers conform to the contract). Not applicable and said so: error codes E1xx/E2xx (this service has none), lesson d
(this service calls no other service), 422 (the contract has none).
