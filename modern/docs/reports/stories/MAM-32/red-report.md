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

## Test addition at the end of the lane (2026-11-21, additive)

State on entry (from the lead): the whole check of the module had passed (705 tests, 0 failures, 0 skipped, JaCoCo 98.82 %, PIT 84.1 % = 74 of 88) and the review had listed the blank-message fallback of the new class `application/FailureReason` as a note. Q-152 (A) decides: fix it in every service that has the class, tests first, as revalidation-service did (`application/FailureReason`, its `FailureReasonTest` and `FailureReasonBlankMessageTest`, MAM-45 red report, section "Test addition at the end of the lane"). This round adds the tests of this one class. Nothing recorded was changed, weakened or removed (two NEW files, recorded: 23 to 25 files); no production code was touched (the defect below is for the developer).

### 1. FailureReasonTest (new, `application` package): 59 tests, green at once

Unit tests of every branch of `FailureReason.of` (MAM-32 AC-6, D-024), so that mutation testing does not depend on the integration tests (`BatchRunFailureTest` and `BatchRunDatabaseErrorTest` test the same rule through the whole service):
- a database error in the chain gives the database's own first line, without the "ERROR:" mark, the Detail lines and the SQL statement: a Spring `DuplicateKeyException` over a `SQLException`; a plain `SQLException`; one deep in the chain of a failure that is none itself; one a hundred causes down; a data access error without a `SQLException` shows the root cause; one that is its own root shows its message; a message without the mark; the mark in the middle of the line; blank lines before the mark; the root cause, not the wrapper; a root without a message shows its class name;
- any other failure gives its own message, not its cause's; an "ERROR: " mark is kept; a message of several lines gives the first (LF, CR LF, lone CR); blank lines and spaces before the message are skipped; no message gives the class name; a blank message starts with the class name;
- the length: messages of 1, 199, 200, 201, 202, 1000 and 50000 characters (cut at 200, 200 kept), `MAX` is 200, a long first line with more lines, a long database message cut after the mark, the 200 counted after the mark;
- two properties over ten kinds of failure (long message, line breaks, lone CRs, no message, nested causes with long messages, database errors with long messages and line breaks, deep in the chain, root without message, leading line breaks): at most 200 characters, and one line;
- five SQL statements (INSERT, UPDATE, SELECT, INSERT into the outbox, DELETE) that the wrapper carries and the reason must not show.

Effect on mutation testing (PIT, run of 06:51-07:02): `FailureReason` has 15 mutants, all 15 killed, and the killing test recorded by PIT for each is this class. The module went from 74 of 88 (84.1 %, 9 survivors and 5 without coverage, 11 of those 14 in this class) to 85 of 88 (96.6 %, 0 without coverage). The 3 survivors are not in this class: `SourceRules.cut` :107 (equivalent boundary) and `RequestScreening` :54 and :61 (return values of the background run), as in the review notes.

### 2. Defect kept red: the blank-message fallback is not cut and not one line (25 tests, all RED)

`application/FailureReason.java:29-31`: when the message, or the message of the root database cause, is empty or only white space (also when it is only the "ERROR: " mark with detail lines after it), `reason.isBlank() ? shown.toString() : ...` returns `Throwable.toString()`: the class name, a colon and the message with its line breaks, neither cut to 200 characters nor joined to one line. D-024 and AC-6 say one line, at most 200 characters, no SQL text. `infrastructure/FailureReasonBlankMessageTest` (25 invocations, all red in the run of 06:51-07:02):

| Test | Expected | Actual |
|---|---|---|
| `blankMessageIsCutToo` (200, 300, 5000 spaces), 3 | at most 200 characters | 233, 333 and 5033 characters ("java.lang.IllegalStateException: " plus the blanks) |
| `blankMessageIsOneLine` (one line break, space line break space, two CR LF, five line breaks), 4 | no line break | the class name, a colon and the line breaks |
| `blankDatabaseRootIsCutToo` (SQLException of 300 and 5000 line breaks), 2 | at most 200 characters, no line break | 323 and 5023 characters with 300 and 5000 line breaks ("java.sql.SQLException: ...") |
| `rootWithOnlyTheMarkInItsFirstLineIsShortToo` (SQLException "ERROR: ", a line break, a Detail line of 10, 300, 5000 characters), 3 | at most 200 characters, one line, no "Detail" | 75, 365 and 5065 characters: "java.sql.SQLException: ERROR: ", a line break and the whole Detail line |
| `blankMessageShowsTheClassName` (empty, one space, three spaces, a tab, a line break, space line break space, two CR LF, five line breaks), 8 | "java.lang.IllegalStateException" | the same name followed by ": " and the message (33 to 38 characters) |
| `blankDatabaseRootShowsItsClassName` (root message empty, three spaces, a line break, space line break space, "ERROR: " line break "  Detail: Key (npi)=(x)"), 5 | "java.sql.SQLException" | "java.sql.SQLException: " followed by the message (23 to 26 characters; the last one on two lines) |

The first three rows (3 + 4 + 2 = 9 tests) are the same as revalidation-service's nine. The 3 tests of the fourth row and the 13 of the last two rows are new here: the class name is what revalidation-service shows for a blank message and its class doc says so ("A failure whose message is missing or only white space shows its class name"); the lead's brief for this round says both services must behave alike. If the lead prefers another reason for a blank message, only the last two rows (13 tests) change.

Practical weight: low (an exception without a message, or a database error whose first line is only the mark; `JdbcScreeningRuns` cuts at 500 characters, the column's size), but the rule "never longer than 200, one line, whatever the exception carries" is broken, and the Detail lines of a database error can be shown that way. The fix is the one of revalidation-service: `String reason = words.isBlank() ? shown.getClass().getName() : words;` followed by the cut of `reason` to `MAX` (the cut then applies to every branch).

Why in the `infrastructure` package: PIT runs every test of the `application` package for every mutant and stops the whole analysis when one of them fails, so a red test there would have hidden the mutation score of the module (it ran: 190 test runs for 88 mutants). After the fix the 25 tests can be moved into `application/FailureReasonTest` (test-writer task), so that the mutants of the new fallback are killed by them in PIT too; the 59 green tests already kill the hand-made mutants of the fixed class (section 3).

### 3. Proof that the tests can fail and can pass, in copies outside the repository (scratchpad `scr-fr/`, no Gradle)

- The 84 tests compiled with a copy of today's class: 59 green, 25 red (the table above). With a copy of revalidation-service's `FailureReason` repackaged for this service (the fix): all 84 green, so the 25 red tests can be satisfied by exactly that fix and the 59 green tests do not depend on the defect.
- Hand-made mutants (`scr-fr/mutate.py`): 12 of today's class and 16 of the fixed copy (negated conditions, boundary `<=`, `mark - length`, empty return, negated loop condition and both `instanceof`, both boolean returns, null message; for the fixed copy also negated `isBlank`, no cut, cut at 199 and at 201): every one is killed by the 59 green tests alone. PIT then confirmed it on the real class (15 of 15).
- One draft test was dropped before recording: "spaces around the message are not part of the reason" failed against today's class AND against revalidation's: `strip()` trims the whole message, but the first line keeps its trailing spaces ("  boom  " + line break + "more" gives "boom  "). Nothing specifies it, so nothing pins it.

### 4. Not tested, and why

Two exotic inputs have no criterion and were not invented: a cause chain that is a cycle (A caused by B caused by A: the loop in `hasDatabaseError` would not end; reasoned from the code, not run) and a surrogate pair cut in the middle at character 200 (the cut counts UTF-16 units). `RunScreeningBatch.failed(Throwable)` has no unit test of its own with a blank-message exception (the integration tests `BatchRunFailureTest` and `BatchRunDatabaseErrorTest` cover the path with SQL errors); not added: out of this round's scope.

### 5. Gate

`:services:screening-service:check --continue`, after `spotlessApply` on src/test only (06:50-06:51; the two new files were only re-wrapped), run of 06:51-07:02, 10 min 55 s, whole module, under the shared lock (taken after 223 s of waiting, of which about 100 s because free memory was under 1024 MB): 789 tests (the 705 of the last run plus 84 new), 25 failed (exactly the 25 above, all in `FailureReasonBlankMessageTest`), 0 skipped, 0 errors; PIT 85 of 88 mutants killed (96.6 %), 0 without coverage; Spotless check, `testSourceCheck` and `versionCheck` green; ArchUnit (`PlatformChecksTest`) green. The JaCoCo verification (85 %) did not run (it depends on `test`, which is red). Line coverage from `build/jacoco/test.exec` of this run with the JaCoCo 0.8.13 core API (`scratchpad/reval-w/jacocoprobe`): 669 of 673 lines (99.41 %) without the `*Application` classes (the module's own rule), 670 of 678 (98.82 %, the percentage of the earlier runs) with them; the 4 missed lines are in `ScreeningBatchJobConfig` (two in the class, two in its inner class `$2`).

`MAM-32.tests.sha256` now holds 25 files; `python modern/tools/checks/test_protection.py check` prints OK for MAM-18, 20, 22, 25, 28 and 32. The old line "FailureReason has no unit test of its own" in `MAM-32.md` (Not verified) is out of date; the story report is not a test-writer file and was left as it is.
