# MAM-22 red report: Screen the owner of a group or facility (E203 owner excluded)

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

Tests per AC (Cucumber scenarios counted as pickles incl. outline rows, plus JUnit invocations): AC-1: 9 (1 Cucumber, 8 JUnit); AC-2: 5 (1 Cucumber, 4 JUnit); AC-3: 20 (4 Cucumber, 16 JUnit); AC-4: 5 (1 Cucumber, 4 JUnit); AC-5: 9 (1 Cucumber, 8 JUnit, 4 red).

Test classes of this story (src/test): features/MAM-22.feature, acceptance/SourceSteps, domain/SourceRulesOwnerTest, infrastructure/HttpScreeningSourcesTest, ProviderServiceClientTest, SourceAnswerShapeTest, ProviderContractUseTest; shared: Lane3Test (base: real application, Postgres, Kafka, WireMock; bounded waits), Lane3Hooks, Bounded, ContractFile, EventsContractFile, ScreeningEvents, ScreeningWorld, LogCapture.

## Red tests and the reason

- AC-5, same defect as MAM-20 AC-1, owner lookup: `HttpScreeningSources.java:90-119` (`leie`, `exclusion`): an owner lookup answered with HTTP 200 and no verdict is read as not excluded (P). 4 tests of `SourceAnswerShapeTest`, red.

Every other test of the story passed in the first real run: "tests written after the code". The recorded hashes are in `modern/docs/reports/stories/MAM-22.tests.sha256` with restore copies in `red-blobs.txt`; `python modern/tools/checks/test_protection.py check MAM-22` prints OK.

Questions for you: the one of MAM-20 (an owner answer without a verdict), recommended A. The earlier question of the build report (provider listed, owner lookup unanswered: C or E201) stays open; recommended A (C), which the tests assert.
