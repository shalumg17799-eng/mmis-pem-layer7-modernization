```
Phase / item:      MAM-32 Run screening as a batch job and produce the legacy SCRNOUT file
Outcome:           BUILT (build-phase: tests in Part D), batch A5; the job itself not run live (see question)
What was done:     - Spring Batch job PRVSCR01 (screening_db Flyway V2 Spring Batch tables, approved): POST
                     /api/v1/batch/screening-runs (ADMIN, SYSTEM) -> 202 RUNNING, job on one background thread; step
                     "screen" reads every provider of GET /api/v1/providers page by page (200) until the last page and
                     screens (reason INITIAL, ScreenProvider, each screening committing on its own) a provider whose screen
                     flag is U and who has no INITIAL screening yet (AC-7), linking it to the run; step "results" writes
                     SCRNOUT (4 rows of 67 bytes per screened provider, NPI order, LICBOARD/OIGLEIE/SAMGOV/SSADMF, verdict
                     date = business date, detail cut to 40) and completes the run (read, screened, skipped = read -
                     screened, failed = screened providers with a result other than P); provider-service 5xx ends it
                     FAILED, no endless retry. GET .../{runId} (ADMIN, contract 1.1.0 per the user), GET
                     .../{runId}/results-file (ADMIN)
                   - AC status: AC-1 checked live for the refusals (ANALYST/SUPERVISOR/PROVIDER 403, no token 401, {} 400);
                     the ADMIN/SYSTEM start not run live; AC-2..AC-7 built, not run live; AC-8 checked in part (results
                     file SYSTEM/ANALYST 403, no token 401, unknown 404)
Files created/changed: screening-service application/ScreeningRuns, ProviderListing, ScrnoutFile, RunScreeningBatch,
                     StartScreeningBatch, ScreeningBatchLauncher; adapter/legacy/ScrnoutWriter; infrastructure/
                     JdbcScreeningRuns, ScreeningBatchJobConfig, ProviderServiceClient (provider pages);
                     api/ScreeningRunController; db/migration/screening/V2__spring_batch.sql
Checks run:        screening-service:test 49/49; bootJar; pem-apps healthy; live refusals above
Not verified:      the job live: on the current data it would screen the seeded DELTA and HHA (flag U, no INITIAL
                   screening) and change their screen flags; not done without a seed
Questions for you: 1. Run MAM-32 live now (changes DELTA and HHA in the shared data), or right after your next seed?
                      Recommended: right after your next seed
```

## Lane 3 tests (2026-11-20)

Outcome line unchanged. Tests written after the code (the story was built without tests, batch A3); details and the reason of every red test: `MAM-32/red-report.md`.

- Test files (recorded in `MAM-32.tests.sha256`, `test_protection.py check MAM-32` prints OK): features/MAM-32.feature, acceptance/BatchRunSteps, adapter/legacy/ScrnoutWriterTest, ScrnoutRoundTripTest, api/BatchRunApiTest, application/RunScreeningBatchTest, application/StartScreeningBatchTest, infrastructure/BatchRunFailureTest, BatchRunDatabaseErrorTest, BatchRunFileTest, BatchRunRepositoryTest, JdbcScreeningRunsTest, ProviderContractUseTest; shared: Lane3Test (base: real application, Postgres, Kafka, WireMock; bounded waits), Lane3Hooks, Bounded, ContractFile, EventsContractFile, ScreeningEvents, ScreeningWorld, LogCapture.
- Tests per AC (Cucumber scenarios counted as pickles, plus JUnit invocations): AC-1: 58 (8 Cucumber, 50 JUnit, 8 red); AC-2: 31 (2 Cucumber, 29 JUnit); AC-3: 33 (1 Cucumber, 32 JUnit); AC-4: 7 (1 Cucumber, 6 JUnit); AC-5: 24 (1 Cucumber, 23 JUnit); AC-6: 73 (2 Cucumber, 71 JUnit, 24 red); AC-7: 21 (1 Cucumber, 20 JUnit, 2 red); AC-8: 13 (6 Cucumber, 7 JUnit).
Runs (all on 2026-11-20 and 2026-11-21, from `modern/`, one Gradle build at a time through the shared lock, `--no-daemon`):
- `./gradlew -I <init script: Spotless on src/test of screening-service only> :services:screening-service:spotlessApply` (formats the test sources only).
- `./gradlew :services:screening-service:check --continue` (tests, ArchUnit, PIT, Spotless on everything; JaCoCo verification is skipped by Gradle when a test fails, so the report comes from the next line).
- `./gradlew :services:screening-service:jacocoTestReport -x :services:screening-service:test` (the JaCoCo report of the same run).
- Last run: 705 tests, 65 failures, 0 skipped; JaCoCo line coverage 99.69 % (638 of 640 lines); PIT 98.59 % (70 of 71 mutations killed). ArchUnit (`PlatformChecksTest`) passes. `spotlessJavaCheck` fails on 5 production files (javadoc wrapping): ScrnoutWriter, RunScreeningBatch, SourceRules, ProviderReadRetry, ScreeningBatchJobConfig; none is a test file.
- The last full run was made with `JAVA_TOOL_OPTIONS=-XX:ReservedCodeCacheSize=128m` (a diagnostic of the test lane, not a change of the build): with the build's own setting (`-XX:TieredStopAtLevel=1`, code cache 48 MB) the code cache filled up in a run of 705 tests (`CodeCache: size=49152Kb used=49151Kb`, 8 unit tests failed with `VirtualMachineError: Out of space in CodeCache`), while the run before with 698 tests passed. Reported as a build-configuration defect, see the defect list.
- Defects found (tests kept red, production code not touched):
- AC-1 (lesson b), `ScreeningRunController.java:43` (`BatchRunRequest(LocalDate businessDate)`, lenient Jackson): the business date `2026-03-31T00:00:00`, `...T00:00:00Z`, `...T23:59:59.999`, ` 2026-03-31`, `2026-03-31 `, `2026-03-31` plus a line break, the number `20260331` (read as day number, date +57440-11-26) and `[2026,3,31]` start a run (202); expected 400 problem+json. 8 tests of `BatchRunApiTest`, red.
- AC-6 (lesson a, D-024), `JdbcScreeningRuns.java:28,120` (ERROR_MAX 500), `ScreeningBatchJobConfig.java:152,174-176`, `StartScreeningBatch.java:31`: the error of a run that failed on a database error is the exception message: `PreparedStatementCallback; uncategorized SQLException for SQL [UPDATE screening_run SET ...`, up to 500 characters; expected a reason of at most 200 characters without SQL text, with the full exception in the log (that part passes). 24 tests (`BatchRunFailureTest` 9, `BatchRunDatabaseErrorTest` 15; five failure points: screening insert, run link, outbox insert, run completion, job launch), red.
- AC-7 (lesson c), `RunScreeningBatch.java:50-51` (`screen.screen(...)` commits, then `runs.link(...)`): when the link of a screening to its run fails the screening and its ScreeningCompleted stay although the run is FAILED and no SCRNOUT file lists them. 2 tests (`BatchRunFailureTest`, `BatchRunDatabaseErrorTest`), red.
- Build configuration (not a story): `modern/buildSrc/src/main/kotlin/pem.java-conventions.gradle.kts`, `tasks.test` `jvmArgs(... "-XX:TieredStopAtLevel=1" ...)`: the test JVM runs the whole module in one process with a 48 MB code cache; with 705 tests it filled up (see Runs) and 8 unit tests failed with `VirtualMachineError: Out of space in CodeCache`. Needs `-XX:ReservedCodeCacheSize=128m` (or a fork per class group).
- Questions for you:
  1. When a run FAILS after it screened some providers, those screenings stay (their ScreeningCompleted is out) but no SCRNOUT file ever lists them: the next run skips them (AC-7) and the failed run has no file. Options: A) accept (the file is a per-run output; parity compares complete runs); B) the next run for the same business date also writes the rows of the screenings of earlier FAILED runs. Recommended: A until the parity phase says otherwise.

## Lane 3 code (2026-11-21)

Outcome line unchanged. Production code written by the java-developer to make the red tests of `MAM-32/red-report.md` pass; no test file touched.

- `modern/services/screening-service/src/main/java/gov/medicaid/pem/screening/api/ScreeningRunController.java`: the business date is read as text and parsed strictly as `uuuu-MM-dd` (the contract's format date): a date-time, a padded date, a number or an array is a 400 problem+json and starts no run (8 tests of `BatchRunApiTest`, AC-1, lesson b).
- `modern/services/screening-service/src/main/java/gov/medicaid/pem/screening/application/FailureReason.java` (new) with `RunScreeningBatch.failed(.., Throwable)`, `StartScreeningBatch` and the job listener in `infrastructure/ScreeningBatchJobConfig.java`: the reason stored with and shown for a failed run is one line of at most 200 characters without SQL text: for a database error the database's own words (first line of the root cause, without its "ERROR:" mark), otherwise the failure's own message; the log gets the whole exception (24 tests of `BatchRunFailureTest` and `BatchRunDatabaseErrorTest`, AC-6, lesson a, D-024). The column guard of 500 characters in `JdbcScreeningRuns` stays.
- `modern/services/screening-service/src/main/java/gov/medicaid/pem/screening/application/RunScreeningBatch.java`: `screenIfDue` is `@Transactional`: the screening with its results, its ScreeningCompleted and its link to the run commit together, so a failing link keeps neither the screening nor the event and the next run screens that provider once (2 tests, AC-7, lesson c).
- Checks: `./gradlew --no-daemon :services:screening-service:check --continue` (2026-11-21 02:49 to 03:00, after `spotlessApply` on src/main only): BUILD SUCCESSFUL; 705 tests, 0 failures, 0 skipped; JaCoCo line coverage 98.82 % (668 of 676 lines); PIT 84 % (74 of 88 mutations killed, 5 without coverage); ArchUnit and Spotless green; `python modern/tools/checks/test_protection.py check MAM-32` prints OK.
- Not done, needs a decision: question 1 of the red report (the screenings of a FAILED run are in no SCRNOUT file); the code keeps option A, the recommended default.
- Not verified: the live stack (no deploy, no bootJar, by the rules of this lane); `FailureReason` has no unit test of its own, its database branch is covered by the integration tests only (PIT sees the unit tests: 5 mutants without coverage are in that branch).
- Traceability: `MAM-32/trace.json`.
- Second verification (2026-11-21 03:12 to 03:21, java-developer, production code unchanged since the run above): `./gradlew --no-daemon --continue :services:screening-service:test --rerun :services:screening-service:check` (the tests re-run on purpose, PIT and Spotless were up to date): BUILD SUCCESSFUL in 9m 21s; 705 tests, 0 failures, 0 skipped; JaCoCo line coverage 98.82 % (668 of 676); PIT 84 % (74 of 88; 9 survivors: 6 in `FailureReason` (database branch, covered by integration tests only), 1 equivalent boundary mutant in `SourceRules`, 2 in `RequestScreening` (return value of the background run, ignored by design); `test_protection.py check` prints OK for MAM-18, 20, 22, 25, 28, 32.
