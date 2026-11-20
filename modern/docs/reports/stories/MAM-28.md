```
Phase / item:      MAM-28 Rescreen a provider on request or when revalidation is due
Outcome:           BUILT (build-phase: tests in Part D), batch A1
What was done:     - Spec check: Jira MAM-28 description = modern/specs/MAM-28.md (same 6 ACs)
                   - Built in screening-service: POST /api/v1/screenings {npi} (ANALYST, SUPERVISOR, SYSTEM): malformed
                     NPI 400; NPI unknown to provider-service 404 (nothing created); else 202 with a MANUAL screening in
                     status RUNNING, run in the background with the same id (ScreenProvider.screen(id, npi, reason));
                     RevalidationEventsListener: RevalidationDue starts one REVALIDATION screening (processed_event makes
                     a redelivery a no-op); every screening reads the provider's current data from provider-service
                   - AC status: AC-1 built, checked live (202 RUNNING for ALPHA 1005940865, then COMPLETED PASS with the
                     source results; earlier screenings are only ever added to, D-018); AC-2 built, checked live (123 ->
                     400, 1003000999 -> 404); AC-3 built (seen live: the RevalidationDue for 1003000111 reached
                     screening-service, but that NPI was no longer in provider-service, so the screening failed and went
                     to the retry/DLT path); AC-4 built (idempotent consumer), not checked live; AC-5 built (provider data
                     read per screening), not checked live; AC-6 built, checked live (PROVIDER and ADMIN 403, no token 401,
                     ANALYST 202)
Files created/changed: modern/services/screening-service/src/main/java/gov/medicaid/pem/screening/
                     application/RequestScreening (new), application/ScreenProvider (screen with a given id),
                     infrastructure/RevalidationEventsListener, ScreeningExecutorConfig (new), api/ScreeningController (POST)
                   test drafts for Part D (not run): modern/docs/reports/stories/MAM-28/part-d-drafts/
Checks run:        ./gradlew --no-daemon :services:screening-service:test -> 49/49 existing tests pass; bootJar;
                   pem-apps healthy; live checks above
Not verified:      automated tests (Part D); the RUNNING row is not stored: GET by id answers 404 until the screening
                   completes (a few seconds); AC-4/AC-5 live
Questions for you: none
```

## Lane 3 tests (2026-11-20)

Outcome line unchanged. Tests written after the code (the story was built without tests, batch A3); details and the reason of every red test: `MAM-28/red-report.md`.

- Test files (recorded in `MAM-28.tests.sha256`, `test_protection.py check MAM-28` prints OK): features/MAM-28.feature, acceptance/RescreenSteps, api/RescreenApiTest, application/RequestScreeningTest, application/ScreenProviderReasonsTest, application/ReadScreeningsArgumentsTest, infrastructure/JdbcScreeningStoreTest, RevalidationEventsTest, EventTransactionTest, RequestedScreeningFailureTest, CurrentDataTest, ScreeningEventContractTest, ProviderContractUseTest; shared: Lane3Test (base: real application, Postgres, Kafka, WireMock; bounded waits), Lane3Hooks, Bounded, ContractFile, EventsContractFile, ScreeningEvents, ScreeningWorld, LogCapture.
- Tests per AC (Cucumber scenarios counted as pickles, plus JUnit invocations): AC-1: 48 (2 Cucumber, 46 JUnit, 2 red); AC-2: 27 (1 Cucumber, 26 JUnit, 3 red); AC-3: 14 (1 Cucumber, 13 JUnit); AC-4: 7 (1 Cucumber, 6 JUnit); AC-5: 11 (1 Cucumber, 10 JUnit); AC-6: 17 (7 Cucumber, 10 JUnit).
Runs (all on 2026-11-20 and 2026-11-21, from `modern/`, one Gradle build at a time through the shared lock, `--no-daemon`):
- `./gradlew -I <init script: Spotless on src/test of screening-service only> :services:screening-service:spotlessApply` (formats the test sources only).
- `./gradlew :services:screening-service:check --continue` (tests, ArchUnit, PIT, Spotless on everything; JaCoCo verification is skipped by Gradle when a test fails, so the report comes from the next line).
- `./gradlew :services:screening-service:jacocoTestReport -x :services:screening-service:test` (the JaCoCo report of the same run).
- Last run: 705 tests, 65 failures, 0 skipped; JaCoCo line coverage 99.69 % (638 of 640 lines); PIT 98.59 % (70 of 71 mutations killed). ArchUnit (`PlatformChecksTest`) passes. `spotlessJavaCheck` fails on 5 production files (javadoc wrapping): ScrnoutWriter, RunScreeningBatch, SourceRules, ProviderReadRetry, ScreeningBatchJobConfig; none is a test file.
- The last full run was made with `JAVA_TOOL_OPTIONS=-XX:ReservedCodeCacheSize=128m` (a diagnostic of the test lane, not a change of the build): with the build's own setting (`-XX:TieredStopAtLevel=1`, code cache 48 MB) the code cache filled up in a run of 705 tests (`CodeCache: size=49152Kb used=49151Kb`, 8 unit tests failed with `VirtualMachineError: Out of space in CodeCache`), while the run before with 698 tests passed. Reported as a build-configuration defect, see the defect list.
- Defects found (tests kept red, production code not touched):
- AC-2 (lesson d), `RequestScreening.java:41-47` (and `ScreeningController.requestScreening`): provider-service answering 500, resetting the connection or not answering within the 5 s limit gives no 503: `ProviderFactsSource.Unavailable` is not handled and escapes the controller (a 500 on a real server). 3 tests of `RescreenApiTest`, red.
- AC-1, `RequestScreening.java:51-57`: the ScreeningCompleted of a requested screening carries a new correlation id, not the one of the request (the background thread has none). 1 test of `RescreenApiTest`, red.
- AC-1 (lesson a, D-024), `RequestScreening.java:56`: a failing background screening logs only `e.toString()`; the exception itself (stack, cause) is not logged. 1 test of `RequestedScreeningFailureTest`, red.
- MAM-25 AC-5 (lesson f), `ScreenProvider.java:70` and `ScreeningCompletedData.java:12`: a provider record without a taxonomy gives `"taxonomy": null` in ScreeningCompleted; events.yaml says string. 1 test of `ScreeningEventContractTest`, red.
- Build configuration (not a story): `modern/buildSrc/src/main/kotlin/pem.java-conventions.gradle.kts`, `tasks.test` `jvmArgs(... "-XX:TieredStopAtLevel=1" ...)`: the test JVM runs the whole module in one process with a 48 MB code cache; with 705 tests it filled up (see Runs) and 8 unit tests failed with `VirtualMachineError: Out of space in CodeCache`. Needs `-XX:ReservedCodeCacheSize=128m` (or a fork per class group).
- Questions for you:
  1. A requested screening (202, RUNNING) that fails in the background (database error, provider removed meanwhile) leaves no trace but a log line, and `Screening.status` has no FAILED value, so an analyst polling the id gets 404 for ever. Options: A) add FAILED (and an error) to the Screening schema, store the failed screening (needs your approval of a contract change); B) keep as it is and document that a screening that never appears failed. Recommended: A.
  2. A RevalidationDue with a new event id but the same caseId starts a second re-screening (idempotency is by event id, ADR-003). Options: A) keep, the relay never changes an event id; B) also remember the case id. Recommended: A.

## Lane 3 code (2026-11-21)

Outcome line unchanged. Production code written by the java-developer to make the red tests of `MAM-28/red-report.md` pass; no test file touched.

- `modern/services/screening-service/src/main/java/gov/medicaid/pem/screening/api/ScreeningController.java` (requestScreening): `ProviderFactsSource.Unavailable` (provider-service answered 5xx, reset the connection or did not answer within its limit) is a 503 problem+json and nothing is created (3 tests of `RescreenApiTest`, MAM-28 AC-2, lesson d). `RequestScreening.request` still throws `Unavailable` because the recorded unit test `RequestScreeningTest.unavailableIsNotNotFound` pins that.
- `modern/services/screening-service/src/main/java/gov/medicaid/pem/screening/application/RequestScreening.java`: the background run takes over the correlation id of the request (`CorrelationId.currentOrNew()` on the request thread, `CorrelationId.with` in the executor thread), so the ScreeningCompleted of a requested screening and the log lines of its failure carry it (MAM-28 AC-1); a failing background screening is logged at ERROR with the exception itself (`RequestedScreeningFailureTest`, lesson a).
- `modern/services/screening-service/src/main/java/gov/medicaid/pem/screening/application/ScreeningCompletedData.java`: no `taxonomy` member when the provider has none (MAM-25 AC-5, lesson f).
- Contract gap, not changed: `contracts/openapi/screening-service.yaml#requestScreening` lists no 503 response. The code and the tests answer 503 (as sanctions-service did with contract 1.0.1, Q-096); the contract needs the same line, which needs the user's approval.
- Checks: `./gradlew --no-daemon :services:screening-service:check --continue` (2026-11-21 02:49 to 03:00, after `spotlessApply` on src/main only): BUILD SUCCESSFUL; 705 tests, 0 failures, 0 skipped; JaCoCo line coverage 98.82 % (668 of 676 lines); PIT 84 % (74 of 88 mutations killed, 5 without coverage); ArchUnit and Spotless green; `python modern/tools/checks/test_protection.py check MAM-28` prints OK.
- Not done, needs a decision: question 1 of the red report (a requested screening that fails in the background stays invisible, 404 for ever; option A needs a FAILED status in the Screening schema, a contract change). Question 2 (a new event id with the same caseId): option A (keep) is what the code does.
- Traceability: `MAM-28/trace.json`.
