# MAM-32 red report: Run screening as a batch job and produce the legacy SCRNOUT file

Test-writer, screening-service lane 3, 2026-11-20 and 2026-11-21. The story was built without tests (batch A3), so its tests were written **after** the code: tests of code that already works were green at once; a test is red here only where the code does not meet an acceptance criterion or one of the lessons of the earlier reviews (a to h).

State found on 2026-11-20 22:50: the test files of the earlier lane (stopped 18:35) were in the committed tree, none recorded; its whole-module run had been interrupted with 58 failures. The first whole-module run of this lane (22:56, stopped by the test-writer after 23 minutes) showed that most of them came from the tests themselves, not from the code:

Test-code defects of the earlier lane's unrecorded files, found and fixed by the first whole run (no assertion weakened, no test removed):
- `Lane3Test.liftRejections` passed the entry `name|table` to `lift(name)`, which looks for `name|table|`: no failure-injection trigger was ever dropped, so every later test saw a database that rejected writes (`CREATE FUNCTION reject_screening() ... already exists`, runs FAILED by a trigger of an earlier test). That is the likely cause of most of the 58 failures of 18:35 (the same code was in the files then). It now lifts by name, and `freshWorld` also drops any `reject_*` trigger left over from a test that was cut off.
- `BatchRunSteps.endRuns` (Cucumber) dropped its trigger only after the runs had ended; it now does it in `finally`.
- `JdbcScreeningRunsTest` creates RUNNING run rows directly; the base class waits for "no run RUNNING" after every test (60 s, 4 failures); the test now removes its own rows.
- `RescreenApiTest`: the hostile header value with a line break is refused by the security firewall with 400 before the application (a real server cannot even receive it); the case is now its own test that accepts 400 or a replaced id (never the raw value).
- `EventTransactionTest`: a fixed `Thread.sleep(1500)` was replaced by a wait on the logged failure; the test is tagged MAM-28 AC-1 (it was MAM-32 AC-7 by mistake).

After these fixes the run shows only the reds listed below.

Result (last run): 705 tests, 65 failures, 0 skipped; JaCoCo line coverage 99.69 % (638 of 640 lines); PIT 98.59 % (70 of 71 mutations killed).

Tests per AC (Cucumber scenarios counted as pickles incl. outline rows, plus JUnit invocations): AC-1: 58 (8 Cucumber, 50 JUnit, 8 red); AC-2: 31 (2 Cucumber, 29 JUnit); AC-3: 33 (1 Cucumber, 32 JUnit); AC-4: 7 (1 Cucumber, 6 JUnit); AC-5: 24 (1 Cucumber, 23 JUnit); AC-6: 73 (2 Cucumber, 71 JUnit, 24 red); AC-7: 21 (1 Cucumber, 20 JUnit, 2 red); AC-8: 13 (6 Cucumber, 7 JUnit).

Test classes of this story (src/test): features/MAM-32.feature, acceptance/BatchRunSteps, adapter/legacy/ScrnoutWriterTest, ScrnoutRoundTripTest, api/BatchRunApiTest, application/RunScreeningBatchTest, application/StartScreeningBatchTest, infrastructure/BatchRunFailureTest, BatchRunDatabaseErrorTest, BatchRunFileTest, BatchRunRepositoryTest, JdbcScreeningRunsTest, ProviderContractUseTest; shared: Lane3Test (base: real application, Postgres, Kafka, WireMock; bounded waits), Lane3Hooks, Bounded, ContractFile, EventsContractFile, ScreeningEvents, ScreeningWorld, LogCapture.

## Red tests and the reason

- AC-1 (lesson b), `ScreeningRunController.java:43` (`BatchRunRequest(LocalDate businessDate)`, lenient Jackson): the business date `2026-03-31T00:00:00`, `...T00:00:00Z`, `...T23:59:59.999`, ` 2026-03-31`, `2026-03-31 `, `2026-03-31` plus a line break, the number `20260331` (read as day number, date +57440-11-26) and `[2026,3,31]` start a run (202); expected 400 problem+json. 8 tests of `BatchRunApiTest`, red.
- AC-6 (lesson a, D-024), `JdbcScreeningRuns.java:28,120` (ERROR_MAX 500), `ScreeningBatchJobConfig.java:152,174-176`, `StartScreeningBatch.java:31`: the error of a run that failed on a database error is the exception message: `PreparedStatementCallback; uncategorized SQLException for SQL [UPDATE screening_run SET ...`, up to 500 characters; expected a reason of at most 200 characters without SQL text, with the full exception in the log (that part passes). 24 tests (`BatchRunFailureTest` 9, `BatchRunDatabaseErrorTest` 15; five failure points: screening insert, run link, outbox insert, run completion, job launch), red.
- AC-7 (lesson c), `RunScreeningBatch.java:50-51` (`screen.screen(...)` commits, then `runs.link(...)`): when the link of a screening to its run fails the screening and its ScreeningCompleted stay although the run is FAILED and no SCRNOUT file lists them. 2 tests (`BatchRunFailureTest`, `BatchRunDatabaseErrorTest`), red.

Every other test of the story passed in the first real run: "tests written after the code". The recorded hashes are in `modern/docs/reports/stories/MAM-32.tests.sha256` with restore copies in `red-blobs.txt`; `python modern/tools/checks/test_protection.py check MAM-32` prints OK.

Questions for you:
1. When a run FAILS after it screened some providers, those screenings stay (their ScreeningCompleted is out) but no SCRNOUT file ever lists them: the next run skips them (AC-7) and the failed run has no file. Options: A) accept (the file is a per-run output; parity compares complete runs); B) the next run for the same business date also writes the rows of the screenings of earlier FAILED runs. Recommended: A until the parity phase says otherwise.
