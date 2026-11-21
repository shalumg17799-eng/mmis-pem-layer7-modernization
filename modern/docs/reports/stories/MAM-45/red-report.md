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

## Test addition at the end of the lane (2026-11-21, additive)

State on entry (from the lead): the java-developer fixed the defects of the first red list (strict business date in
`SweepController`, new class `application/FailureReason`, short reasons in `RunSweep`, `ExecuteSweep`, `SweepJobConfig`) and the
module check passed at 04:52 (529 tests). This addition covers what the developer named and could not do. Nothing recorded was
weakened or removed; the only recorded file changed in this story is `SweepJobTest` (and, shared with MAM-48 and MAM-49,
`RevalidationSteps`, see `MAM-48/red-report.md`).

### 1. SweepJobTest: the explanation of the one failure is right, it is the timing of the test (changed under Q-097 C)

Question: can a run be reported COMPLETED while its batch job can still fail (then it would be a defect of the code), or is it only
the test's timing? Read first, then proved:

- `SweepJobConfig.revalidationSweepStep` runs the tasklet in the step's transaction and calls `ExecuteSweep.execute`
  (`@Transactional`, so it joins it), whose last write is `runs.complete(...)`: `sweep_run` turns COMPLETED when the step's
  transaction commits.
- Spring Batch 5.2.6 (read with `javap -c` on the jar, output in `scratchpad/reval-w/javap/`): `AbstractStep.execute` calls
  `doExecute` (the tasklet transaction, offset 152), then upgrades the step to COMPLETED (198) and writes it with
  `JobRepository.update(StepExecution)` only after that (492); `AbstractJob.execute` calls `doExecute` (202), `setEndTime` (392), the
  listeners' `afterJob` (400) and only then `JobRepository.update(JobExecution)` (426). So for a moment `sweep_run` is COMPLETED and
  committed while `batch_step_execution.status` and `batch_job_execution.status` are still STARTED. `RevalidationFixtures.sweep`
  waits for `sweep_run`, not for the batch tables; `emptyStep` (:135) and `runIsASpringBatchJob` (:77) read the statuses right after
  it and read STARTED once. `stepCounts` (:120) reads only the counts; whether Spring Batch writes them inside or after the step's
  transaction was not verified, the wait below covers both.
- Change: the helpers `step(UUID)` and `jobStatus(UUID)` first call the new `awaitJobEnd(UUID)`, which waits until the batch job of
  the run has a status other than STARTING or STARTED (Awaitility, 45 s guard, 100 ms poll, message naming the run), so every test
  that reads the batch tables (also `everyRunIsItsOwnJobInstance` and `largeRun`) reads the final state. No assertion was changed:
  every `assertThat` is as recorded, and a job that ends FAILED or never ends still fails the test.
- Result: `SweepJobTest` 10 of 10 green in run 1 and in the full runs 4 and 7.

OBSERVATION that needs a decision, not a failure of a test: a run CAN be reported COMPLETED and the batch job can still fail
afterwards (the status writes of Spring Batch come after the commit). Proof in a scratch copy only (a probe test that is not in the
repository; result in `scratchpad/reval-w/run5-test-results/TEST-gov.medicaid.pem.revalidation.infrastructure.PostCommitFailureProbeTest.xml`):
a database trigger refuses the one write `UPDATE batch_step_execution ... status = 'COMPLETED'` (the write after the commit) and a
sweep is started. Observed: the run is RUNNING, then COMPLETED, then FAILED (`failure_reason` "SYNTHETIC BATCH REPOSITORY DOWN");
the batch job ends UNKNOWN; the work stays committed (1 case opened, 1 RevalidationDue in the outbox, the queue file is in the row).
Cause: `SweepJobConfig.failedRunListener` (:64-77) marks every job that did not complete as failed, and `JdbcRevalidationStore.fail`
(:205-213) updates `WHERE public_id = ?` with no `AND status = 'RUNNING'`, so a COMPLETED run is overwritten. Effect: the REVALQ
file of that run is no longer served (`queueFile` serves COMPLETED runs only), its case is open, and a re-run opens no new case
(D-029): that provider is in no REVALQ file until its case closes. No document says which answer is right, so there is NO red test
for it (rule 6.1: no invented business rule); a question with a recommended default is raised in the result of this task.

### 2. FailureReason (new class of the developer): FailureReasonTest, 53 tests, green at once

`application/FailureReasonTest` (unit, in the application package so that PIT runs it): a SQLException wrapped by a Spring data
access exception gives the database's own first line without the ERROR: mark, without the Detail lines and without the SQL
statement; a plain SQLException and a SQLException deep in the chain of a failure that is none itself give the same; a data access
error without a SQLException shows its root cause; a message without the mark is shown as it is; the mark is cut where it stands in
the line; the root cause, not the wrapper, is shown; a root without a message is shown by class name; any other failure gives its own
message (not the cause's; an ERROR: mark is kept; the first line of several; blank lines before the message skipped; no message: the
class name); the length: 1, 199, 200 (kept), 201, 202, 1000 and 50000 characters (cut at 200), a long first line with more lines, a
long database message cut after the mark; two properties over nine kinds of failure (long message, line breaks, no message, nested
causes, database errors with long messages, deep in the chain, root without message): at most 200 characters, and one line; and four
SQL statements (INSERT, UPDATE, SELECT, DELETE) that must not appear. Effect on mutation testing: FailureReason has 15 mutants, all
killed, and the killing test recorded by PIT for each is this class (PIT does not run the integration tests); the module went from 45 of 56 (80 %) to 56 of 56 (100 %).

### 3. Defect kept red: the blank-message fallback of FailureReason is not cut and not one line

The review of screening-service found that its copy of this class does not cut the fallback to 200 characters. It is so here too
(`application/FailureReason.java:29-31`): when the message, or the message of the root database cause, is blank,
`reason.isBlank() ? shown.toString() : ...` returns `Throwable.toString()`, which is neither cut nor one line.
`infrastructure/FailureReasonBlankMessageTest` (9 invocations, all RED in runs 1, 4 and 7):

| Test | Expected | Actual |
|---|---|---|
| `blankMessageIsCutToo` (message of 200, 300, 5000 spaces) | at most 200 characters | 233, 333 and 5033 characters ("java.lang.IllegalStateException: " plus the blanks) |
| `blankMessageIsOneLine` (a line break, " \n ", two CR LF, five line breaks) | no line break | "java.lang.IllegalStateException: " followed by the line breaks |
| `blankDatabaseRootIsCutToo` (SQLException message of 300 and of 5000 line breaks) | at most 200 characters, no line break | "java.sql.SQLException: " plus 300 and 5000 line breaks |

Practical weight: low (the column holds 500 and `JdbcRevalidationStore.fail` cuts there), but the rule "never longer than 200,
one line, whatever the exception carries" is broken. The fix is one cut and one line-join on the fallback as well. These nine tests
are in the `infrastructure` package ON PURPOSE: PIT runs every test of the application package for every mutant and stops the whole
analysis when one of them fails, so a red test there would have hidden the mutation score of the module. Once the code is fixed they
can be moved into `FailureReasonTest` (test-writer task).

### 4. Gate

`:services:revalidation-service:check --continue`, run 7 (2026-11-21 05:32-05:42), whole module: 610 tests (the 529 of the last run
plus 81 new: 19 concurrency, 53 FailureReasonTest, 9 FailureReasonBlankMessageTest), 9 failed (exactly the nine above), 0 skipped;
PIT 56 of 56 mutants killed (100 %); Spotless, ArchUnit, `testSourceCheck` and `versionCheck` green. The JaCoCo verification (85 %)
cannot run while a test is red (it depends on `test`). Line coverage: `jacocoTestReport -x test` on the `test.exec` of the full run
(run 9, 05:55, 33 s): 408 of 408 lines (100.00 %) without the `*Application` classes (the module's own rule), 408 of 412 (99.03 %,
the figure of the lead's run of 04:52) with the 4 lines of `RevalidationServiceApplication`. The same figure from the JaCoCo 0.8.13
core API (small program in `scratchpad/reval-w/jacocoprobe`): 408 of 408. One earlier full run (run 4, 05:19-05:26) had 10 failures: these nine and the Cucumber step race described in
`MAM-48/red-report.md` (corrected, Q-097 C). `MAM-45.tests.sha256` now holds 30 files (recorded 2026-11-21 after run 7;
`test_protection.py check MAM-45` prints OK).
