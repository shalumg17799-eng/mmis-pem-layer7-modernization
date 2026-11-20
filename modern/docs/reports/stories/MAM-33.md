```
Phase / item:      MAM-33 Apply a sanction status change to the provider (TERMINATED, SUSPENDED, ACTIVE)
Outcome:           DONE (fast-mode, gates postponed)
What was done:     - Spec check: Jira MAM-33 description = modern/specs/MAM-33.md (same 8 ACs; markdown spacing only)
                   - Red: MAM-33.feature (8 scenarios, @AC-1..@AC-8), StatusChangeSteps, StatusActionTest (10),
                     ChangeProviderStatusTest (8). The scenarios failed on the missing endpoint (404); the unit tests
                     on the missing StatusAction / ChangeProviderStatus / StatusChangeCommand (MAM-33/red-run.txt)
                   - Green: POST /api/v1/providers/{npi}/status-changes (SYSTEM, SUPERVISOR; others 403, no token 401):
                     TERMINATE -> T, SUSPEND -> S (not D, D-006), REINSTATE -> A, whatever the current status
                     (D-027); saved and announced once as ProviderStatusChanged (from, to, reason, actor =
                     preferred_username) in one transaction; counter pem.provider.terminations (+1 on TERMINATE only);
                     unknown action, missing effectiveDate or reason > 60 -> 422 problem+json with no change and no
                     event (D-024, D-026); unknown NPI -> 404, nothing created or announced
Files created/changed: modern/services/provider-service/src/main/java/gov/medicaid/pem/provider/
                     domain/StatusAction.java (new), domain/Provider.java (withEnrollStatus),
                     application/ChangeProviderStatus.java, StatusChangeCommand.java, StatusChangeMetrics.java (new),
                     infrastructure/MicrometerStatusChangeMetrics.java (new), api/ProviderController.java (endpoint)
                   tests: src/test/resources/features/MAM-33.feature, acceptance/StatusChangeSteps.java,
                     domain/StatusActionTest.java, application/ChangeProviderStatusTest.java
Checks run:        ./gradlew --no-daemon :services:provider-service:spotlessApply :services:provider-service:test
                     -> BUILD SUCCESSFUL, 125 tests, 0 failures, 0 skipped (MAM-33: 8 scenarios + 18 unit tests)
                   test_protection check MAM-33, MAM-17, MAM-21, MAM-23, MAM-27 -> OK; MAM-34 -> its recorded files
                     unchanged, FAILED only on 3 NEW web-ui test files of the parallel UI work (not provider-service)
Not verified:      the check gate (JaCoCo 85%, PIT 70%) is postponed (fast mode); no independent code review;
                   not deployed to the running stack; the audit_log entry of a status change (Q-A6 default A) is not
                   written: no MAM-33 AC asks for it and the audit trail is story MAM-40 (prv-audit); a repeated
                   sanctionId is not de-duplicated (out of scope in the spec)
Questions for you: none new (metric name pem.provider.terminations chosen; no name is fixed in any document)
Next step:         MAM-52 (sanctions-service), which calls this endpoint
```

## Lane 3 tests (2026-11-20)

Outcome line unchanged. Additions of the provider-service test lane.

- New or changed test files: ProviderOutboxContractTest, ProviderWriteInputTest, ProviderWriteAtomicityTest, ChangeProviderStatusReasonTest, StatusActionResultTest; the parameterized name of StatusActionTest (names only). Details: `MAM-33/red-report.md`, section "Lane 3 additions".
- `MAM-33.tests.sha256` re-recorded; `test_protection.py check MAM-33` prints OK.
Runs (all on 2026-11-20, from `modern/`, one Gradle build at a time through the shared lock, `--no-daemon`):
- `./gradlew -I <init script: Spotless on src/test of provider-service only> :services:provider-service:spotlessApply` (formats the test sources only).
- `./gradlew :services:provider-service:check --continue` (tests, Spotless on everything, ArchUnit, JaCoCo, PIT). Last run 21:00-21:09: **417 tests, 9 failures, 0 skipped**;
  the 9 failures are the Q-124 tests (red by design until `PrvrecrdFile` is changed, see MAM-43); PIT **130 of 130 mutations killed (100 %)** on `domain` and `application`;
  Spotless and ArchUnit passed.
- `./gradlew :services:provider-service:jacocoTestReport -x test` (the report of the same run; with a failing test `check` does not reach `jacocoTestCoverageVerification`):
  **line coverage 98.34 %** (768 of 781 lines; 98.84 % without `*Application`, as the gate counts), branch 88.31 %. Gate 85 %.
