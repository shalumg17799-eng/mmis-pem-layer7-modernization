```
Phase / item:      MAM-59 Send approval, denial, rejection and revalidation notices by email
Outcome:           DONE (built test-first), In Review
What was done:     ProviderStatusChanged P->A APPROVED, P->D DENIED (Q-A7/Q-A8), ApplicationRejected REJECTED, RevalidationDue REVALIDATION_DUE; email to <npi>@providers.pem.local (Q-092 A) via Mailpit; notice log; NoticeSent; idempotent
Files created/changed: NoticeRules (domain); Template, Notice, NoticeSentData, NoticePorts, SendNotice (application); NoticeAdapters, NoticeEventsListener (infrastructure); V2__notice_templates.sql in modern/services/notification-service
Checks run:        ./gradlew :services:notification-service:check -> green, 22 tests, PIT 93 %; live: J1 AC-7 (Mailpit "Enrolment approved")
Not verified:      independent code review per service (Part D)
Questions for you: none
Next step:         Part D gates (PIT, coverage, review)
```
Backfilled 2026-11-19 by the lead from the session record (PROGRESS.md activity rows and Jira report comments); the story was
built test-first earlier the same day.

## Lane 3 tests (2026-11-20)

```
Written by:        test-writer, notification-service lane (see the MAM-58 report for the whole lane). The story already had
                   recorded tests; the new ones were written against the built code. No file under src/main was changed.
New test files (modern/services/notification-service/src/test/java/gov/medicaid/pem/notification/):
  StatusNoticeEventsTest.java                     20 integration: real Kafka, Postgres, Mailpit
  application/SendNoticeResultTest.java            7 unit: what send returns, the missing-template line, each of the four
                                                   templates, one notice per provider
  application/TemplateTest.java                    5 unit (4 for this story, 1 for MAM-60 AC-7): the {npi} placeholder
  domain/NoticeRulesMatrixTest.java               47 unit (31 for this story, 16 for MAM-58): all 25 status transitions,
                                                   rejection NPIs, addresses
  NoticeFixtures.java, LogCapture.java, EventsContractFile.java   shared test support, recorded with the story
Tests per AC:      new JUnit tests that name the AC (a test that names several ACs counts under each; the 8 Cucumber
                   scenarios of the build stay)
                   AC-1 16   AC-2 7   AC-3 21   AC-4 9   AC-5 15   AC-6 6   AC-7 1   AC-8 3   (62 tests)
                   AC-1/AC-2 stored SENT notice (template, EMAIL, recipient, event id, sent_at), email subject, NoticeSent
                   valid against events.yaml for P to A and P to D; AC-3 eight silent transitions end to end (A-S, S-A, A-T,
                   A-A, D-A, P-T, P-S, P-P: no email, no notice, no NoticeSent) and all 25 in the rule matrix; AC-4 REJECTED
                   notice; AC-5 rejections with no npi member, a null npi, five digits, ten letters: no email, no notice, INFO
                   line names the event id, no WARN; AC-6 REVALIDATION_DUE notice; AC-7 ExtractProduced keyed by its run id
                   (Q-061 B, events.yaml 1.1.0): consumed, INFO line with event id and run id, no email, no notice, no
                   NoticeSent, no warning; AC-8 redelivery leaves one row of each, and a failure after the email rolls back and
                   is then applied once; the seven notice types all go to <npi>@providers.pem.local and to no other mailbox
                   (Q-092 A: nothing to example.test)
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
Hashes:            modern/docs/reports/stories/MAM-59.tests.sha256 (16 files); test_protection.py check MAM-59 -> OK (also OK for
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

## Review fix (2026-11-21, review-fix step of the lead's plan)

The code review of 2026-11-20 rated one point MUST-FIX: the notices for APPROVED, DENIED, REJECTED and REVALIDATION_DUE did
not have the failure handling of D-024 that the sanction notices have. A missing template was only logged and nothing was
stored; a mail failure was thrown out of the listener, retried twice and the event parked on the dead-letter topic, so
"a notice that could not be sent shows as failed" (MAM-60 AC-5) did not hold for these notices.

- Tests first (test-writer, 03:20 to 04:45): SendNoticeFailureTest, NoticeSendFailureTest, NoticeSendFailureEventsTest and
  NoticeFailureSteps; 4 new scenarios in MAM-59.feature and 1 in MAM-60.feature; two recorded unit tests corrected under rule
  Q-097 C (a constructor argument and the expected FAILED notice; no assertion removed; see the red reports). Shown red
  against the code of that moment: 87 of 139 failing in a copy with only the new constructor.
- Fix (java-developer, 04:45 to 05:00): one delivery path for all notices (new class NoticeDelivery): a missing template or
  a mail failure gives one WARN line and a FAILED notice and is never thrown; a failure of the store or of the outbox is
  still thrown. SendNotice and SendSanctionNotice both use it.
- Whole check of the module (04:52 to 04:58): 346 tests, 0 failures, 0 skipped, JaCoCo 97.8 % of lines, PIT 94 % (29 of 31;
  the two survivors are equivalent mutants).
- Second review (05:08): APPROVE, no MUST-FIX. Its notes are in `modern/docs/reports/review-should-fix.md` (among them: the
  WARN line carries the message of the mail error, not its stack trace; an email is sent before its row is committed, so a
  database failure after the send can repeat the email on the retry).
- Not verified: a failed send on the running stack (the stack's mail catcher was not stopped for it).
