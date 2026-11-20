```
Phase / item:      MAM-22 Screen the owner of a group or facility (E203 owner excluded)
Outcome:           BUILT (build-phase: tests in Part D), batch A3
What was done:     - Spec check: Jira MAM-22 description = modern/specs/MAM-22.md (same 5 ACs)
                   - Built in screening-service: the OIGLEIE source looks up the provider's own NPI and, when the
                     provider record holds an owner NPI (organisation view only), the owner as a second lookup, in
                     parallel, each with the usual one retry (D-005, R-PRVSCR01-cbl-00033). SourceRules.leie(npi, own,
                     ownerNpi, owner): both listed -> F E201 "NPI <npi> AND OWNER <owner> EXCLUDED" (question C-6);
                     own only -> F E201 as before; owner only -> F E203 "OWNER <owner> EXCLUDED <type> SINCE <date>"
                     (D-016); neither -> P "NOT EXCLUDED"; either lookup unanswered -> C without a code (AC-5). One
                     OIGLEIE result per screening, as before
                   - AC status: AC-1 built, checked live (J3 set-up: analyst1 submitted GR 1003000444 with owner
                     1990000111 -> OIGLEIE F E203 "OWNER 1990000111 EXCLUDED 1128(a)(1) SINCE 2023-01-15", the other
                     three sources recorded, screening FAIL, listed by ?result=FAIL, screen flag F, status P); AC-2
                     built, checked live (GR 1003000222 owner 1990000999: OIGLEIE P; WireMock journal shows one lookup
                     of each NPI); AC-3 built, checked live for an IN provider (MANUAL screening of ALPHA: exactly one
                     LEIE lookup); the FA-without-owner case not checked live (no seeded organisation without an owner);
                     AC-4 built (rule above), not checked live (the LEIE mock lists only 1990000111); AC-5 built, not
                     checked live (the error stub needs the X-Pem-Stub header, which only a test sends)
Files created/changed: modern/services/screening-service/src/main/java/gov/medicaid/pem/screening/
                     domain/SourceRules (Exclusion record, leie with owner), infrastructure/HttpScreeningSources
Checks run:        ./gradlew --no-daemon :services:screening-service:test -> 49/49 existing tests pass; bootJar;
                   pem-apps restarted and healthy; live checks above; ./pem.sh seed afterwards
Not verified:      automated tests (Part D); AC-3 facility without owner, AC-4 and AC-5 live
Questions for you: 1. When the provider itself is listed but the owner lookup gets no answer, the result is C (the AC-5
                      wording "an owner lookup that fails is recorded as not answered"), so the E201 is not shown.
                      Options: A) keep C (literal AC-5, current build); B) F E201 for the provider, noting the owner
                      was not checked. Recommended: A until the business says otherwise
```

## Lane 3 tests (2026-11-20)

Outcome line unchanged. Tests written after the code (the story was built without tests, batch A3); details and the reason of every red test: `MAM-22/red-report.md`.

- Test files (recorded in `MAM-22.tests.sha256`, `test_protection.py check MAM-22` prints OK): features/MAM-22.feature, acceptance/SourceSteps, domain/SourceRulesOwnerTest, infrastructure/HttpScreeningSourcesTest, ProviderServiceClientTest, SourceAnswerShapeTest, ProviderContractUseTest; shared: Lane3Test (base: real application, Postgres, Kafka, WireMock; bounded waits), Lane3Hooks, Bounded, ContractFile, EventsContractFile, ScreeningEvents, ScreeningWorld, LogCapture.
- Tests per AC (Cucumber scenarios counted as pickles, plus JUnit invocations): AC-1: 9 (1 Cucumber, 8 JUnit); AC-2: 5 (1 Cucumber, 4 JUnit); AC-3: 20 (4 Cucumber, 16 JUnit); AC-4: 5 (1 Cucumber, 4 JUnit); AC-5: 9 (1 Cucumber, 8 JUnit, 4 red).
Runs (all on 2026-11-20 and 2026-11-21, from `modern/`, one Gradle build at a time through the shared lock, `--no-daemon`):
- `./gradlew -I <init script: Spotless on src/test of screening-service only> :services:screening-service:spotlessApply` (formats the test sources only).
- `./gradlew :services:screening-service:check --continue` (tests, ArchUnit, PIT, Spotless on everything; JaCoCo verification is skipped by Gradle when a test fails, so the report comes from the next line).
- `./gradlew :services:screening-service:jacocoTestReport -x :services:screening-service:test` (the JaCoCo report of the same run).
- Last run: 705 tests, 65 failures, 0 skipped; JaCoCo line coverage 99.69 % (638 of 640 lines); PIT 98.59 % (70 of 71 mutations killed). ArchUnit (`PlatformChecksTest`) passes. `spotlessJavaCheck` fails on 5 production files (javadoc wrapping): ScrnoutWriter, RunScreeningBatch, SourceRules, ProviderReadRetry, ScreeningBatchJobConfig; none is a test file.
- The last full run was made with `JAVA_TOOL_OPTIONS=-XX:ReservedCodeCacheSize=128m` (a diagnostic of the test lane, not a change of the build): with the build's own setting (`-XX:TieredStopAtLevel=1`, code cache 48 MB) the code cache filled up in a run of 705 tests (`CodeCache: size=49152Kb used=49151Kb`, 8 unit tests failed with `VirtualMachineError: Out of space in CodeCache`), while the run before with 698 tests passed. Reported as a build-configuration defect, see the defect list.
- Defects found (tests kept red, production code not touched):
- AC-5, same defect as MAM-20 AC-1, owner lookup: `HttpScreeningSources.java:90-119` (`leie`, `exclusion`): an owner lookup answered with HTTP 200 and no verdict is read as not excluded (P). 4 tests of `SourceAnswerShapeTest`, red.
- Build configuration (not a story): `modern/buildSrc/src/main/kotlin/pem.java-conventions.gradle.kts`, `tasks.test` `jvmArgs(... "-XX:TieredStopAtLevel=1" ...)`: the test JVM runs the whole module in one process with a 48 MB code cache; with 705 tests it filled up (see Runs) and 8 unit tests failed with `VirtualMachineError: Out of space in CodeCache`. Needs `-XX:ReservedCodeCacheSize=128m` (or a fork per class group).
- Questions for you: the one of MAM-20 (an owner answer without a verdict), recommended A. The earlier question of the build report (provider listed, owner lookup unanswered: C or E201) stays open; recommended A (C), which the tests assert.

## Lane 3 code (2026-11-21)

Outcome line unchanged. Production code written by the java-developer to make the red tests of `MAM-22/red-report.md` pass; no test file touched.

- `modern/services/screening-service/src/main/java/gov/medicaid/pem/screening/infrastructure/HttpScreeningSources.java`: the lookup of the owner is judged like every source answer: an HTTP 200 without a boolean `excluded` (empty object, error document, array, text, null) is no answer, so OIGLEIE is C without an error code, never P (4 tests of `SourceAnswerShapeTest`, MAM-22 AC-5).
- Checks: `./gradlew --no-daemon :services:screening-service:check --continue` (2026-11-21 02:49 to 03:00, after `spotlessApply` on src/main only): BUILD SUCCESSFUL; 705 tests, 0 failures, 0 skipped; JaCoCo line coverage 98.82 % (668 of 676 lines); PIT 84 % (74 of 88 mutations killed, 5 without coverage); ArchUnit and Spotless green; `python modern/tools/checks/test_protection.py check MAM-22` prints OK.
- Traceability: `MAM-22/trace.json`.
