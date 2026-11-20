```
Phase / item:      MAM-36 Health, structured logs and correlation IDs across services (NFR)
Outcome:           DONE for AC-1, AC-2, AC-3, AC-4 (fast-mode, gates postponed); AC-5 MOVED TO PART D (user plan
                   2026-11-19: new tests are written in Part D)
AC-5 note:         Q-099 answer B (verify per service by taking the test's own database away) was tried on 2026-11-19:
                   a shared helper (test-support HealthOutage) and one HealthOutageTest per service (enrollment,
                   screening, risk, revalidation, sanctions, notification, directory, tmsis). Result with the first
                   helper: 6 services PASSED (503 DOWN while the paused test Postgres was away, 200 UP after, ~32-36 s
                   each), enrollment and sanctions HUNG (the pool handed out a connection used <500 ms before without
                   validating it, and the query had no timeout; the test database was unpaused after 102 s and after
                   ~18 min). A corrected helper (1.5 s wait after the pause so the pool validates, and a 90 s bound so
                   a hang fails visibly) was written but never run. Per the user's plan the helper and the 8 test
                   classes were taken out of the services (kept for Part D in
                   modern/docs/reports/stories/MAM-36/part-d-drafts/) and MAM-36.tests.sha256 was recorded again with the 6 AC-1..4 files only.
                   The readiness group was not changed (Q-099)
What was done:     - Spec check: Jira MAM-36 description = modern/specs/MAM-36.md (same 5 ACs; markdown spacing only)
                   - Red (MAM-36/red-run.txt): service-kit ObservabilityKitTest (5) did not compile (no
                     CorrelationIdRecordInterceptor); provider-service ObservabilityTest: no 'kafka' health component,
                     ProviderRegistered got a random correlationId instead of the consumed one; enrollment-service
                     CorrelationIdTest: no log line for a submission. The DB-down test (503 DOWN while the test Postgres
                     is paused, 200 UP after) passed already (Spring Boot default) and is kept as a regression test.
                     Hashes: MAM-36.tests.sha256 re-recorded with the 3 earlier files unchanged + the 3 new ones
                   - Green: service-kit CorrelationIdRecordInterceptor (registered as the Kafka RecordInterceptor: the
                     envelope's correlationId is in the MDC while a consumer handles it, a generated one if missing,
                     removed afterwards) and KafkaHealthIndicator (component 'kafka', AdminClient describeCluster,
                     pem.health.kafka-timeout default 3s), auto-configured only where Kafka and actuator are present;
                     INFO log lines "Application <id> for NPI <npi>: <status>" (enrollment) and "Provider <npi> registered
                     as PENDING, UNSCREENED" (provider), so the id shows in both services' logs
                   - AC status: AC-1 met (UP/503 DOWN/UP again, db and kafka components); AC-2 met (earlier test);
                     AC-3 met by the default configuration (pem-common.yml selects ECS JSON; the test encodes a line with
                     the real encoder and pem-common.yml: one JSON object with @timestamp, log.level, service.name,
                     message, correlationId); AC-4 met in parts across services (gateway: MAM-29 AC-5; enrollment: answer
                     header, log line, ApplicationValidated; provider: log line and ProviderRegistered carry the consumed
                     id); AC-5 not built and not in the recorded tests: it waits for the user's answer to Q-099 (readiness
                     group with db and Kafka) and needs a database stopped in the running stack
Files created/changed: modern/libs/service-kit/build.gradle.kts (spring-boot-actuator as optional dependency)
                   modern/libs/service-kit/src/main/java/gov/medicaid/pem/kit/events/CorrelationIdRecordInterceptor.java,
                     KafkaHealthIndicator.java (new), kit/config/PemKafkaAutoConfiguration.java (2 beans)
                   modern/services/enrollment-service/.../application/SubmitApplication.java (log line)
                   modern/services/provider-service/.../application/RegisterProvider.java (log line)
                   tests (new): libs/service-kit/src/test/java/gov/medicaid/pem/kit/ObservabilityKitTest.java,
                     services/provider-service/src/test/java/gov/medicaid/pem/provider/ObservabilityTest.java,
                     services/enrollment-service/src/test/java/gov/medicaid/pem/enrollment/CorrelationIdTest.java
Checks run:        ./gradlew --no-daemon --continue :libs:service-kit:check :services:provider-service:test
                     :services:enrollment-service:test (attempt 1): service-kit check PASSED (43 tests), enrollment 90/90;
                     provider 91/128: every Cucumber scenario failed because its Spring context could not connect to the
                     test Postgres at start-up ("Connection to localhost:63530 refused"), before any MAM-36 test ran; the
                     3 ObservabilityTest tests and all other classes passed in that run
                   ./gradlew --no-daemon :services:provider-service:test (attempt 2, nothing changed) -> BUILD SUCCESSFUL,
                     128/128
                   test_protection check MAM-36, MAM-16, 17, 21, 23, 24, 26, 27, 29, 31, 33, 46, 50, 54, Q-095 -> OK;
                     MAM-34 -> its files unchanged, FAILED only on 3 NEW web-ui test files of the UI agent
Not verified:      the start-up refusal of attempt 1 is reported as a flaky test-infrastructure event (host busy; the
                   test Postgres container did not accept connections for 30 s); it did not repeat in attempt 2, cause
                   not proven. The kit change reaches every service with Kafka (screening, risk, revalidation,
                   sanctions, notification, directory, tmsis): those services' suites were NOT re-run here. The
                   end-to-end AC-4 path through the running stack (gateway -> enrollment -> provider) was not run.
                   AC-5 not built. Gates (JaCoCo, PIT) postponed for the services; no code review
Questions for you: Q-099 (readiness group with db and kafka, needed for AC-5) stays open as you logged it
Next step:         MAM-30 (provider-service due date)
```

## Lane 3 tests (2026-11-20)

Outcome line unchanged. AC-5 of provider-service (Q-099 B) and the correction of a hanging recorded test.

- New: `ProviderOutageTest.healthFollowsTheDatabase` [MAM-36 AC-5]: with the test's own Testcontainers Postgres paused mid-test `/actuator/health` answers 503 DOWN, and 200 UP again within 60 s of the database coming back. Every wait has a limit (90 s for the answer, 60 s for the recovery; the database is un-paused in a `finally`).
- Found and corrected: the recorded `ObservabilityTest.healthFollowsTheDatabase` could hang the build indefinitely (the pool's 500 ms validation bypass plus pgjdbc without a socket timeout); the correction without weakening the assertion is in `MAM-36/red-report.md`. A default limit of 6 minutes for every test is in `junit-platform.properties`.
- Production observation: with a frozen database `/actuator/health` can block (no JDBC `socketTimeout`); a stopped database is answered at once. Not changed (production code); suggested: a `socketTimeout` on the datasource.
- `MAM-36.tests.sha256` re-recorded.
Runs (all on 2026-11-20, from `modern/`, one Gradle build at a time through the shared lock, `--no-daemon`):
- `./gradlew -I <init script: Spotless on src/test of provider-service only> :services:provider-service:spotlessApply` (formats the test sources only).
- `./gradlew :services:provider-service:check --continue` (tests, Spotless on everything, ArchUnit, JaCoCo, PIT). Last run 21:00-21:09: **417 tests, 9 failures, 0 skipped**;
  the 9 failures are the Q-124 tests (red by design until `PrvrecrdFile` is changed, see MAM-43); PIT **130 of 130 mutations killed (100 %)** on `domain` and `application`;
  Spotless and ArchUnit passed.
- `./gradlew :services:provider-service:jacocoTestReport -x test` (the report of the same run; with a failing test `check` does not reach `jacocoTestCoverageVerification`):
  **line coverage 98.34 %** (768 of 781 lines; 98.84 % without `*Application`, as the gate counts), branch 88.31 %. Gate 85 %.
