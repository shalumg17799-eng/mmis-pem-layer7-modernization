```
Phase / item:      MAM-30 Revalidation due date: five years, four-digit years, leap day and each completed revalidation
Outcome:           DONE (fast-mode, gates postponed)
What was done:     - Spec check: Jira MAM-30 description = modern/specs/MAM-30.md (same 8 ACs; markdown spacing only)
                   - Red (MAM-30/red-run.txt): MAM-30.feature (4 scenarios: AC-4+5, AC-6, AC-7, AC-8),
                     RevalidationDueDateSteps, RevalidationDueDateTest (8), CompleteRevalidationTest (2). AC-4/5 and AC-8
                     failed (due date stayed 2026-03-31); AC-6 and AC-7 already held; unit tests did not compile
                     (Revalidation, CompleteRevalidation missing)
                   - Green: domain Revalidation.passed (completion date + 5 years, Provider.withRevalidationDueDate);
                     application CompleteRevalidation (service clock, save, ProviderStatusChanged A->A with the new
                     revalidationDueDate, reason 'Revalidation passed', actor screening-service);
                     VerdictEventsListener calls it after the screen flag only for reason REVALIDATION and result PASS;
                     idempotent through processed_event (AC-8)
                   - AC-1/2/3 (approval dates 2029-03-15, 2065-06-30, 2097-04-01, 2028-02-29) were already met by the
                     MAM-27 Approval rule; they are verified by unit tests with those dates (the scenarios cannot choose
                     the service clock date: the shared test context has the real clock)
Files created/changed: modern/services/provider-service/src/main/java/gov/medicaid/pem/provider/
                     domain/Revalidation.java (new), domain/Provider.java (withRevalidationDueDate),
                     application/CompleteRevalidation.java (new), infrastructure/VerdictEventsListener.java
                   tests: src/test/resources/features/MAM-30.feature, acceptance/RevalidationDueDateSteps.java,
                     domain/RevalidationDueDateTest.java, application/CompleteRevalidationTest.java
Checks run:        ./gradlew --no-daemon :services:provider-service:spotlessApply :services:provider-service:test
                     -> BUILD SUCCESSFUL, 142 tests, 0 failures (MAM-30: 4 scenarios + 10 unit tests), 1st attempt
                   test_protection check MAM-30 and the other provider-service stories -> OK (MAM-34: only NEW web-ui
                     files of the UI agent)
Not verified:      AC-1..3 at the API level with a chosen clock date (unit level only); gates postponed; no review;
                   not deployed to the running stack
Questions for you: 1. A REVALIDATION PASS for a provider that is no longer ACTIVE (for example suspended after the case
                      was opened) also sets a new due date today (no AC restricts it; the sweep only queues ACTIVE
                      providers). Options: A) leave it (applied); B) move the date only for ACTIVE providers
Next step:         MAM-36 AC-5 per service (Q-099 B), then MAM-45
```

## Lane 3 tests (2026-11-20)

Outcome line unchanged. Additions of the provider-service test lane.

- New or changed test files: ProviderOutboxContractTest [MAM-30 AC-5]; the parameterized names of RevalidationDueDateTest now carry the tag (names only). Details: `MAM-30/red-report.md`, section "Lane 3 additions".
- `MAM-30.tests.sha256` re-recorded; `test_protection.py check MAM-30` prints OK.
Runs (all on 2026-11-20, from `modern/`, one Gradle build at a time through the shared lock, `--no-daemon`):
- `./gradlew -I <init script: Spotless on src/test of provider-service only> :services:provider-service:spotlessApply` (formats the test sources only).
- `./gradlew :services:provider-service:check --continue` (tests, Spotless on everything, ArchUnit, JaCoCo, PIT). Last run 21:00-21:09: **417 tests, 9 failures, 0 skipped**;
  the 9 failures are the Q-124 tests (red by design until `PrvrecrdFile` is changed, see MAM-43); PIT **130 of 130 mutations killed (100 %)** on `domain` and `application`;
  Spotless and ArchUnit passed.
- `./gradlew :services:provider-service:jacocoTestReport -x test` (the report of the same run; with a failing test `check` does not reach `jacocoTestCoverageVerification`):
  **line coverage 98.34 %** (768 of 781 lines; 98.84 % without `*Application`, as the gate counts), branch 88.31 %. Gate 85 %.
