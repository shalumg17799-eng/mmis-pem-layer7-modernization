```
Phase / item:      MAM-17 (local L-S016) Register a validated application as a PENDING, UNSCREENED provider
Outcome:           DONE (AC-1..AC-7), In Review
What was done:     - Red: 4 test files (feature MAM-17 with 7 scenarios on a real Kafka, RegistrationSteps,
                     ProviderRegistrationTest 5, RegisterProviderTest 3); failed only on missing classes
                   - Green (attempt 1): ProviderRegistration (P/U, no tier, no due date, effective date = clock
                     date, display name first + last or legal name, D-028), RegisterProvider (duplicate NPI logged
                     "DUPLICATE NPI <npi>", counted in pem.provider.registrations.skipped, never overwritten),
                     EnrollmentEventsListener (processed_event for every id, only ApplicationValidated registers),
                     JpaProviderRegistry, outbox bridge, GET /api/v1/providers?npi= (other filters 400 until the
                     search story)
Files created/changed: modern/services/provider-service/src/main/** , tests under src/test/**
Checks run:        ./gradlew :services:provider-service:check -> BUILD SUCCESSFUL, 58 tests 0 failures,
                   PIT 43/44 (98%); test_protection MAM-17, MAM-34, MAM-50 -> OK
                   live: provider1 submits through the gateway -> provider 1003000111 P/U, ProviderRegistered
                   published (after one restart of pem Kafka, whose group coordinator was stuck)
Not verified:      AC-7 with a frozen 2026-11-18 clock (checked against the service clock date instead);
                   independent code review of this story
Questions for you: none
Next step:         MAM-18 screening
```

## Lane 3 tests (2026-11-20)

Outcome line unchanged. Additions of the provider-service test lane.

- New or changed test files: ListenerTransactionTest, ProviderOutboxContractTest, UnsafeCorrelationIdFromEventsTest. Details: `MAM-17/red-report.md`, section "Lane 3 additions".
- `MAM-17.tests.sha256` re-recorded; `test_protection.py check MAM-17` prints OK.
Runs (all on 2026-11-20, from `modern/`, one Gradle build at a time through the shared lock, `--no-daemon`):
- `./gradlew -I <init script: Spotless on src/test of provider-service only> :services:provider-service:spotlessApply` (formats the test sources only).
- `./gradlew :services:provider-service:check --continue` (tests, Spotless on everything, ArchUnit, JaCoCo, PIT). Last run 21:00-21:09: **417 tests, 9 failures, 0 skipped**;
  the 9 failures are the Q-124 tests (red by design until `PrvrecrdFile` is changed, see MAM-43); PIT **130 of 130 mutations killed (100 %)** on `domain` and `application`;
  Spotless and ArchUnit passed.
- `./gradlew :services:provider-service:jacocoTestReport -x test` (the report of the same run; with a failing test `check` does not reach `jacocoTestCoverageVerification`):
  **line coverage 98.34 %** (768 of 781 lines; 98.84 % without `*Application`, as the gate counts), branch 88.31 %. Gate 85 %.
