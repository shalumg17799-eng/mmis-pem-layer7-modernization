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

## Lane 3 tests (2026-11-20)

```
Written by:        test-writer, notification-service lane. MAM-58, MAM-59 and MAM-60 already had recorded tests (built
                   test-first on 2026-11-18/19), so nothing was red first: the new tests were written against the built code,
                   to hold the lessons of the three services reviewed before (a to h), to add the boundary cases of the
                   sanction rules, and to close the gates. No file under src/main was changed.
New test files (modern/services/notification-service/src/test/java/gov/medicaid/pem/notification/):
  SanctionNoticeEventsTest.java                    12 integration: real Kafka, Postgres, Mailpit; stored notice row, NoticeSent
                                                   against events.yaml, hostile correlation id, refused actions, redelivery
  SanctionNoticeFailureTest.java                    3 integration: real mail sender at a closed port and at a silent server;
                                                   a database trigger that fails the outbox insert (one transaction)
  application/SendSanctionNoticeFailureTest.java   12 unit: exact WARN and ERROR lines, recorded cause, which failures are
                                                   recorded as FAILED and which are thrown
  domain/NoticeRulesMatrixTest.java                shared with MAM-59 (16 of its 47 tests are the MAM-58 action table)
  NoticeFixtures.java, LogCapture.java, EventsContractFile.java   shared test support, recorded with the story
Tests per AC:      new JUnit tests that name the AC (a test that names two or three ACs counts under each; the 8 Cucumber
                   scenarios of the build stay)
                   AC-1 7   AC-2 6   AC-3 6   AC-4 20   AC-5 2   AC-6 7   AC-7 3   AC-8 4   (43 tests: 27 in the 3 classes
                   above plus the 16 of the shared matrix)
                   AC-1/2/3 stored SENT notice (template, EMAIL, recipient, event id, sent_at) and email subject for T, S, R;
                   AC-4 five refused actions (no action, 's', GEN, '', TS): no email, no notice, no NoticeSent, ERROR line,
                   acknowledged; 13 more in the rule matrix; AC-5 NoticeSent is a valid NoticeSentEnvelope, a hostile
                   correlationId is replaced; AC-6 refused connection and silent server give a FAILED row without sent_at and
                   the exact WARN words, cause with and without message, the next event goes on; AC-7 the cause
                   'NOTICE TEMPLATE NOT FOUND <code>' for TRM, SUS and RST; AC-8 one notice row, one NoticeSent, one email, one
                   processed marker after two redeliveries; a failure after the email rolls back and is then applied once
Run 1:             ./gradlew --no-daemon :services:notification-service:check --continue (2026-11-20 17:52-18:06, 13 min 22 s)
                   Module: 249 tests = 33 Cucumber scenarios + 216 JUnit; 247 passed, 2 FAILED, 0 skipped. The 2 failures are
                   the defect D-1 below (MAM-60 AC-5), kept red on purpose, not weakened. The module had 64 tests before
                   (33 Cucumber + 31 JUnit); 185 are new (102 integration, 83 unit). The 33 scenarios of the build all pass.
                   JaCoCo line coverage 100 % of the 186 lines of the gate classes (97.9 % with NotificationServiceApplication,
                   4 lines; gate 85 %). jacocoTestCoverageVerification itself did not run in run 1 (it depends on the test
                   task, which failed); the percentage is from jacocoTestReport.xml.
                   PIT (domain + application): 34 mutations, 34 killed = 100 % (gate 70 %). The 2 survivors of the 2026-11-19
                   report (SendNotice.send and ListNotices.notices return values) are killed now.
                   Spotless: run 1 failed on the 12 new test files only (they were LF; the working tree of this module is
                   CRLF). They were converted to CRLF and are now byte-identical to the files Spotless wrote to
                   build/spotless-clean; the 13 older test files and the production files were not reported. ArchUnit passes.
Run 2:             ./gradlew --no-daemon :services:notification-service:spotlessCheck
                   :services:notification-service:jacocoTestCoverageVerification -x :services:notification-service:test
                   --continue (the execution data of run 1) -> RUN2PENDING
Hashes:            modern/docs/reports/stories/MAM-58.tests.sha256 (11 files); test_protection.py check MAM-58 -> OK (also OK for
                   the other two stories of the module); all older entries of the file are unchanged (no recorded test was
                   edited, so there is nothing to add to a red-report.md).
Lessons (the three services reviewed before): a) N/A: no run or request of this service stores or returns a
                   failure reason; the cause of a failed send is only in the WARN log (no column), and a database failure in
                   an event handler is retried twice and parked on the .DLT topic by the kit; a GET that fails on the database
                   cannot be shown with MockMvc (the container's error page), so its 500 body is not verified.
                   b) N/A: no businessDate input; the ExtractProduced.businessDate is not read by the service.
                   c) holds: SanctionNoticeFailureTest and StatusNoticeEventsTest 'a failure after the email was sent' (a
                   trigger makes the outbox insert fail: no processed marker, no notice, no NoticeSent; the redelivered event
                   then gives exactly one of each); SendSanctionNoticeFailureTest (store and outbox failures are thrown).
                   d) holds: a server that accepts and never answers gives a FAILED notice in 5.5 s (5 s read timeout of
                   notification-service.yml). e) holds: % _ and the backslash in npi are 400, there is no LIKE (NoticeApiTest).
                   f) NoticeSent envelopes in the outbox validate against events.yaml NoticeSentEnvelope (holds); the REST
                   answer for a FAILED notice has 'sentAt': null where the contract says string (D-1).
                   g) holds for events (log lines and the NoticeSent event get a fresh id) and for the X-Correlation-Id header.
                   h) holds: 400, 401 (no token, garbage token) and 403 are application/problem+json on both endpoints.
Defects found:     D-1  MUST-FIX, contract (lesson f). A notice whose send failed is listed with "sentAt": null; the
                        contract (notification-service.yaml Notice.sentAt: type string, format date-time, not nullable) allows a
                        string or no member. NoticeView.java:8 (record component sentAt), filled by JdbcNoticeLog.java:56-63
                        (sent_at is NULL for FAILED rows, written by NoticeAdapters.failedNotices). Expected: the member sentAt
                        is absent for a FAILED notice. Actual: "sentAt":null. MAM-60 AC-5 and MAM-58 AC-6 (the FAILED notice
                        that AC-6 produces is what MAM-60 AC-5 lists). The recorded MAM-60 scenario hid it: its step inserts
                        the FAILED row with sent_at = now(). Red tests: NoticeApiTest 'the notice of a send that really failed
                        has no sent time' and 'a list with the notice of a send that really failed follows the NoticePage
                        contract'. Fix for the java-developer: @JsonInclude(NON_NULL) on NoticeView (as ApplicationResponse in
                        enrollment-service does).
                   No Spotless or ArchUnit failure in production code. No AC is otherwise unmet.
Observations (SHOULD-FIX or questions, no red test):
                   O-1  Log injection of event data: SendSanctionNotice.java:57-61 logs the action and NPI of the event raw,
                        NoticeEventsListener.java:79-83 the runId, SendNotice.java:45 the NPI. A value with a line break from
                        a producer would forge a log line. Only the correlation id is sanitised (kit). The topics are internal,
                        so the risk is low; no AC asks for it.
                   O-2  The email is sent before the notice and the NoticeSent are written. If the database then fails, the
                        transaction rolls back (tested), the event is retried 2 times and parked on the DLT, and each attempt
                        has already sent the email: a provider can get the same email up to 3 times. Kafka plus SMTP cannot be
                        one transaction; options in the questions of the result.
                   O-3  An event whose subject is not a 10-digit NPI (ProviderStatusChanged, RevalidationDue, SanctionApplied)
                        is emailed to '<subject>@providers.pem.local' and announced as NoticeSent with that value, which breaks
                        NoticeSentData.npi (pattern). Only ApplicationRejected checks the NPI (MAM-59 AC-5). Contract-conforming
                        producers never send such an event, so no test is red; see the questions of the result.
                   O-4  Two Spring contexts run in the test JVM (Cucumber's runs first, the JUnit base class's next) and both
                        listen in the group 'notification-service', so an event is handled by either. The new tests do not
                        depend on which; the MAM-58 AC-6 scenario (it changes the mail sender of its own context) ran first and
                        alone in the two runs whose results I read (2026-11-19 and run 1), so it is not affected today, but
                        it would be flaky if the order changed.
Not verified:      the 500 body of a GET that fails on the database (MockMvc shows no error page); AC-6 of MAM-58 second
                   half (GET /api/v1/providers/1003000111 still S) is another service (J-run); real SMTP other than Mailpit;
                   behaviour after a restart of the consumer (offsets); the notice list in the UI (ui-portal-notices).
```
