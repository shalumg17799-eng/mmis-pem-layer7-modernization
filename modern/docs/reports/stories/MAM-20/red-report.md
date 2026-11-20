# MAM-20 red report: Handle unknown licences, organisation providers and sources that fail or are slow

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

Tests per AC (Cucumber scenarios counted as pickles incl. outline rows, plus JUnit invocations): AC-1: 62 (5 Cucumber, 57 JUnit, 21 red); AC-2: 81 (4 Cucumber, 77 JUnit); AC-3: 18 (1 Cucumber, 17 JUnit); AC-4: 14 (2 Cucumber, 12 JUnit); AC-5: 27 (1 Cucumber, 26 JUnit).

Test classes of this story (src/test): features/MAM-20.feature, acceptance/SourceSteps, domain/SourceRulesNotAnsweredTest, infrastructure/HttpScreeningSourcesTest, SourceAnswerShapeTest, TimeoutDetailRepeatedTest, SlowSourceBudgetTest, ProviderUnavailableTest, ProviderServiceClientTest, application/ApplicationRecordsTest, BatchRunRepositoryTest; shared: Lane3Test (base: real application, Postgres, Kafka, WireMock; bounded waits), Lane3Hooks, Bounded, ContractFile, EventsContractFile, ScreeningEvents, ScreeningWorld, LogCapture.

## Red tests and the reason

- AC-1, `HttpScreeningSources.java:68,77,116,140,146-149` (ask / rule.apply): an HTTP 200 answer that holds no verdict (`{}`, an error document, `[]`, a text, or the verdict member null) is read as P (not excluded, not debarred, not deceased) or F (licence not found); expected C without an error code (question C-1 A, option C of that question, "treat an unanswered source as P", was not chosen). 21 tests of `SourceAnswerShapeTest`, all red.
- Flaky, not a defect of a test: `HttpScreeningSourcesTest` "an error, then a timeout is DID NOT ANSWER IN TIME" failed once in 6 full runs (`COULD NOT BE REACHED` instead); not reproduced in the 180 repetitions of both ways to reach the last attempt (`TimeoutDetailRepeatedTest`, 60 tests per run) of the 3 runs that followed. Suspected cause, not confirmed: the JDK HttpClient sometimes reports a cancelled call as `IOException: Request cancelled` without an `HttpTimeoutException` in the cause chain, and `HttpScreeningSources.isTimeout` (line 176) then does not see a timeout.

Every other test of the story passed in the first real run: "tests written after the code". The recorded hashes are in `modern/docs/reports/stories/MAM-20.tests.sha256` with restore copies in `red-blobs.txt`; `python modern/tools/checks/test_protection.py check MAM-20` prints OK.

Questions for you:
1. Is an HTTP 200 answer of a source that holds no verdict (empty object, error document, array, text, verdict member null) "no answer" (C, FAIL)? Options: A) yes, C without an error code (the tests of this lane assume it; same wording as C-1 A); B) no, read the missing member as "not listed" (current code: a screening can then pass on a document that says nothing). Recommended: A.
