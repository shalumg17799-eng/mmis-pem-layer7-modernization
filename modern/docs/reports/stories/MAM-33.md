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
