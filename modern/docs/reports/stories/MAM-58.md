```
Phase / item:      MAM-58 Send sanction notices by email (TRM, SUS, RST), reject unknown actions and handle failures
Outcome:           DONE (fast-mode, gates postponed)
What was done:     - Spec check: Jira MAM-58 description = modern/specs/MAM-58.md (same 8 ACs; markdown spacing only);
                     dependsOn MAM-31, MAM-24 (platform, built)
                   - Red: MAM-58.feature (8 scenarios, @AC-1..@AC-8; reuses the Mailpit/NoticeSent steps of MAM-59),
                     SanctionNoticeSteps, SanctionNoticeRulesTest (4), SendSanctionNoticeTest (6). The scenarios
                     failed because SanctionApplied was never consumed; the unit tests on the missing
                     SendSanctionNotice / NoticeRules.forSanction (MAM-58/red-run.txt)
                   - Green: notification-service now also consumes pem.sanctions.events (idempotent via
                     processed_event): T -> TRM, S -> SUS, R -> RST emailed to <npi>@providers.pem.local with the
                     seeded template ({npi} filled in), stored SENT and announced as NoticeSent (outbox ->
                     pem.notification.events, key = NPI); any other action -> ERROR log naming the event id and the
                     action, no email (no GEN), no NoticeSent, event acknowledged (D-026, Q-A10 A); a mail failure or
                     a missing template -> WARN 'NOTICE <code> FOR NPI <npi> STATUS FAILED (event <id>), cause: ...'
                     (missing template cause = 'NOTICE TEMPLATE NOT FOUND <code>'), a FAILED notice row, no NoticeSent,
                     never thrown, so the next event is processed (D-024); SMTP connect/read/write timeouts 5 s
Files created/changed: modern/services/notification-service/src/main/java/gov/medicaid/pem/notification/
                     application/SendSanctionNotice.java (new), application/NoticePorts.java (FailedNotices port),
                     domain/NoticeRules.java (forSanction), infrastructure/NoticeAdapters.java (failedNotices bean),
                     infrastructure/NoticeEventsListener.java (topic pem.sanctions.events, case SanctionApplied)
                   src/main/resources/notification-service.yml (spring.mail.properties timeouts)
                   tests: src/test/resources/features/MAM-58.feature, acceptance/SanctionNoticeSteps.java,
                     domain/SanctionNoticeRulesTest.java, application/SendSanctionNoticeTest.java
                   (no migration: notice.status already allows FAILED; the TRM/SUS/RST templates are seeded by V2)
Checks run:        ./gradlew --no-daemon :services:notification-service:spotlessApply :services:notification-service:test
                     -> BUILD SUCCESSFUL, 64 tests, 0 failures, 0 skipped (MAM-58: 8 scenarios + 10 unit tests),
                     1st attempt
                   test_protection check MAM-58, MAM-59, MAM-60 -> OK
Not verified:      the check gate (JaCoCo 85%, PIT 70%) is postponed (fast mode); no independent code review; not
                   deployed to the running stack. AC-6 deviations in how it is tested: (a) "Mailpit is stopped" is
                   injected by pointing the application's mail sender at a closed port for that scenario (the shared
                   Testcontainers Mailpit is not stopped, so other scenarios keep it); (b) "GET /api/v1/providers/
                   1003000111 still returns S" is a cross-service check: here it is shown structurally (ArchUnit: no
                   notification-service class depends on an HTTP client, so it cannot change or undo a status); the
                   real GET belongs to an end-to-end test (Phase 8). The failure cause is only in the log: the notice
                   table has no cause column (no AC asks for one)
Questions for you: 1. The MAM-59 notices (APPROVED, DENIED, REJECTED, REVALIDATION_DUE) still behave as built: a missing
                      template stores nothing, and a mail failure is thrown (the event is retried by Kafka) instead of
                      a FAILED row. Options: A) leave as is, decide in ntf-send-2 (recommended: no AC asks for it now);
                      B) apply the MAM-58 failure handling to them too (needs a story or an AC change)
Next step:         lead: code review of MAM-33, MAM-52, MAM-58; then the postponed :check gates
```
