```
Phase / item:      MAM-21 Save the screening result on the provider (screen flag)
Outcome:           DONE (built test-first with MAM-23), In Review
What was done:     ScreeningCompleted -> screen flag P or F (ScreenFlag.fromVerdict), idempotent consumer
Files created/changed: ScreenFlag (domain); ProviderChanges, RecordVerdicts (application); VerdictEventsListener, JpaProviderChanges (infrastructure) in modern/services/provider-service
Checks run:        ./gradlew :services:provider-service:check -> green, 99 tests, PIT 97 %; live: J1 AC-2 (flag P)
Not verified:      independent code review per service (Part D)
Questions for you: none
Next step:         Part D gates (PIT, coverage, review)
```
Backfilled 2026-11-19 by the lead from the session record (PROGRESS.md activity rows and Jira report comments); the story was
built test-first earlier the same day.

## Lane 3 tests (2026-11-20)

Outcome line unchanged. Additions of the provider-service test lane.

- New or changed test files: ListenerTransactionTest, RecordVerdictsOutcomeTest, UnsafeCorrelationIdFromEventsTest. Details: `MAM-21/red-report.md`, section "Lane 3 additions".
- `MAM-21.tests.sha256` re-recorded; `test_protection.py check MAM-21` prints OK.
Runs (all on 2026-11-20, from `modern/`, one Gradle build at a time through the shared lock, `--no-daemon`):
- `./gradlew -I <init script: Spotless on src/test of provider-service only> :services:provider-service:spotlessApply` (formats the test sources only).
- `./gradlew :services:provider-service:check --continue` (tests, Spotless on everything, ArchUnit, JaCoCo, PIT). Last run 21:00-21:09: **417 tests, 9 failures, 0 skipped**;
  the 9 failures are the Q-124 tests (red by design until `PrvrecrdFile` is changed, see MAM-43); PIT **130 of 130 mutations killed (100 %)** on `domain` and `application`;
  Spotless and ArchUnit passed.
- `./gradlew :services:provider-service:jacocoTestReport -x test` (the report of the same run; with a failing test `check` does not reach `jacocoTestCoverageVerification`):
  **line coverage 98.34 %** (768 of 781 lines; 98.84 % without `*Application`, as the gate counts), branch 88.31 %. Gate 85 %.
