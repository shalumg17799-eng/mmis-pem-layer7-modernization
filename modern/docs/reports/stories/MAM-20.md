```
Phase / item:      MAM-20 Handle unknown licences, organisation providers and sources that fail or are slow
Outcome:           BUILT (build-phase: tests in Part D), batch A3
What was done:     - Spec check: Jira MAM-20 description = modern/specs/MAM-20.md (same 5 ACs)
                   - AC-1, AC-3 and AC-4 were already met by MAM-18/MAM-25 (unreachable source C "COULD NOT BE
                     REACHED" after one retry; unknown licence F "LICENCE NOT FOUND" without a code; organisation
                     LICBOARD P "NOT APPLICABLE" with no lookup)
                   - AC-2 built: a source whose last attempt ran out of its 2-second limit (HttpTimeoutException or
                     SocketTimeoutException in the cause chain) is recorded as C without a code, detail "DID NOT ANSWER
                     IN TIME" (SourceRules.timedOut); any other failure keeps "COULD NOT BE REACHED"
                   - AC-5 built: provider-service answering 5xx or not answering is reported by ProviderServiceClient as
                     ProviderFactsSource.Unavailable (a 4xx such as 404 is passed on unchanged). ProviderReadRetry sets a
                     back-off function on the kit's DefaultErrorHandler in screening-service only: for that failure the
                     event is retried with an exponential pause (1 s doubling, capped by
                     pem.screening.provider-retry-max-interval, default 60 s) with no attempt limit, instead of 2
                     retries then DLT; the listener transaction (processed_event, screening, outbox) rolls back with each
                     failed attempt, so nothing is COMPLETED or published until provider-service answers, then exactly
                     once. Every other failure keeps the kit's retries and DLT
                   - AC status: AC-1 built (earlier), not re-checked live (error stub needs the X-Pem-Stub header);
                     AC-2 built, not checked live (same reason); AC-3 built (earlier), not re-checked live; AC-4 built
                     (earlier), checked live in passing (GR 1003000444 and 1003000222: LICBOARD P NOT APPLICABLE); AC-5
                     built, not checked live (provider-service cannot be made to fail without stopping a container)
Files created/changed: modern/services/screening-service/src/main/java/gov/medicaid/pem/screening/
                     application/ProviderFactsSource (Unavailable), domain/SourceRules (timedOut),
                     infrastructure/HttpScreeningSources (timeout detail), ProviderServiceClient (5xx/no answer ->
                     Unavailable), ProviderReadRetry (new)
Checks run:        ./gradlew --no-daemon :services:screening-service:test -> 49/49 existing tests pass; bootJar;
                   pem-apps restarted and healthy; live checks above
Not verified:      automated tests (Part D); AC-1, AC-2, AC-5 live. AC-2's 5-second budget: 2 attempts x 2 s = 4 s plus
                   the provider read, so it is close to the limit; a Part D test must measure it
Questions for you: none
```

## Lane 3 tests (2026-11-20)

Outcome line unchanged. Tests written after the code (the story was built without tests, batch A3); details and the reason of every red test: `MAM-20/red-report.md`.

- Test files (recorded in `MAM-20.tests.sha256`, `test_protection.py check MAM-20` prints OK): features/MAM-20.feature, acceptance/SourceSteps, domain/SourceRulesNotAnsweredTest, infrastructure/HttpScreeningSourcesTest, SourceAnswerShapeTest, TimeoutDetailRepeatedTest, SlowSourceBudgetTest, ProviderUnavailableTest, ProviderServiceClientTest, application/ApplicationRecordsTest, BatchRunRepositoryTest; shared: Lane3Test (base: real application, Postgres, Kafka, WireMock; bounded waits), Lane3Hooks, Bounded, ContractFile, EventsContractFile, ScreeningEvents, ScreeningWorld, LogCapture.
- Tests per AC (Cucumber scenarios counted as pickles, plus JUnit invocations): AC-1: 62 (5 Cucumber, 57 JUnit, 21 red); AC-2: 81 (4 Cucumber, 77 JUnit); AC-3: 18 (1 Cucumber, 17 JUnit); AC-4: 14 (2 Cucumber, 12 JUnit); AC-5: 27 (1 Cucumber, 26 JUnit).
Runs (all on 2026-11-20 and 2026-11-21, from `modern/`, one Gradle build at a time through the shared lock, `--no-daemon`):
- `./gradlew -I <init script: Spotless on src/test of screening-service only> :services:screening-service:spotlessApply` (formats the test sources only).
- `./gradlew :services:screening-service:check --continue` (tests, ArchUnit, PIT, Spotless on everything; JaCoCo verification is skipped by Gradle when a test fails, so the report comes from the next line).
- `./gradlew :services:screening-service:jacocoTestReport -x :services:screening-service:test` (the JaCoCo report of the same run).
- Last run: 705 tests, 65 failures, 0 skipped; JaCoCo line coverage 99.69 % (638 of 640 lines); PIT 98.59 % (70 of 71 mutations killed). ArchUnit (`PlatformChecksTest`) passes. `spotlessJavaCheck` fails on 5 production files (javadoc wrapping): ScrnoutWriter, RunScreeningBatch, SourceRules, ProviderReadRetry, ScreeningBatchJobConfig; none is a test file.
- The last full run was made with `JAVA_TOOL_OPTIONS=-XX:ReservedCodeCacheSize=128m` (a diagnostic of the test lane, not a change of the build): with the build's own setting (`-XX:TieredStopAtLevel=1`, code cache 48 MB) the code cache filled up in a run of 705 tests (`CodeCache: size=49152Kb used=49151Kb`, 8 unit tests failed with `VirtualMachineError: Out of space in CodeCache`), while the run before with 698 tests passed. Reported as a build-configuration defect, see the defect list.
- Defects found (tests kept red, production code not touched):
- AC-1, `HttpScreeningSources.java:68,77,116,140,146-149` (ask / rule.apply): an HTTP 200 answer that holds no verdict (`{}`, an error document, `[]`, a text, or the verdict member null) is read as P (not excluded, not debarred, not deceased) or F (licence not found); expected C without an error code (question C-1 A, option C of that question, "treat an unanswered source as P", was not chosen). 21 tests of `SourceAnswerShapeTest`, all red.
- Flaky, not a defect of a test: `HttpScreeningSourcesTest` "an error, then a timeout is DID NOT ANSWER IN TIME" failed once in 6 full runs (`COULD NOT BE REACHED` instead); not reproduced in the 180 repetitions of both ways to reach the last attempt (`TimeoutDetailRepeatedTest`, 60 tests per run) of the 3 runs that followed. Suspected cause, not confirmed: the JDK HttpClient sometimes reports a cancelled call as `IOException: Request cancelled` without an `HttpTimeoutException` in the cause chain, and `HttpScreeningSources.isTimeout` (line 176) then does not see a timeout.
- Build configuration (not a story): `modern/buildSrc/src/main/kotlin/pem.java-conventions.gradle.kts`, `tasks.test` `jvmArgs(... "-XX:TieredStopAtLevel=1" ...)`: the test JVM runs the whole module in one process with a 48 MB code cache; with 705 tests it filled up (see Runs) and 8 unit tests failed with `VirtualMachineError: Out of space in CodeCache`. Needs `-XX:ReservedCodeCacheSize=128m` (or a fork per class group).
- Questions for you:
  1. Is an HTTP 200 answer of a source that holds no verdict (empty object, error document, array, text, verdict member null) "no answer" (C, FAIL)? Options: A) yes, C without an error code (the tests of this lane assume it; same wording as C-1 A); B) no, read the missing member as "not listed" (current code: a screening can then pass on a document that says nothing). Recommended: A.

## Lane 3 code (2026-11-21)

Outcome line unchanged. Production code written by the java-developer to make the red tests of `MAM-20/red-report.md` pass; no test file touched.

- `modern/services/screening-service/src/main/java/gov/medicaid/pem/screening/infrastructure/HttpScreeningSources.java`: an HTTP 200 answer is only an answer when it holds the verdict of its source as a boolean (`found`, `excluded`, `debarred`, `deceased`). An empty object, an error document, an array, a text or a verdict that is null is no answer: it is retried like a failed attempt and then recorded as C without an error code (never P, never the F of a hit). 25 tests of `SourceAnswerShapeTest` (MAM-20 AC-1, MAM-22 AC-5). Question 1 of the red report was answered with its recommended default A; if the user chooses B this change must be reverted.
- Same file, `isTimeout`: a `CancellationException` in the cause chain now counts as a timeout. Spring's `JdkClientHttpRequest` (6.2.19) reports a call it cancelled as `IOException("Request cancelled", CancellationException)`, and only a call cancelled by its own timer carries an `HttpTimeoutException`; the detail DID NOT ANSWER IN TIME must not depend on which of the two arrives. This is the fix for the flaky "an error, then a timeout" test (see Not verified).
- Checks: `./gradlew --no-daemon :services:screening-service:check --continue` (2026-11-21 02:49 to 03:00, after `spotlessApply` on src/main only): BUILD SUCCESSFUL; 705 tests, 0 failures, 0 skipped; JaCoCo line coverage 98.82 % (668 of 676 lines); PIT 84 % (74 of 88 mutations killed, 5 without coverage); ArchUnit and Spotless green; `python modern/tools/checks/test_protection.py check MAM-20` prints OK.
- Not verified: the cause of the flaky test was not reproduced (it failed once in 6 full runs of the test lane, never in the 2 full runs of this lane nor in the repetitions of `TimeoutDetailRepeatedTest`). The change above closes the one path found in the Spring code that gives a cancelled call without a timeout exception; if the flake returns it is another path and the WARN lines of `TimeoutDetailRepeatedTest` will show it.
- Traceability: `MAM-20/trace.json`.
