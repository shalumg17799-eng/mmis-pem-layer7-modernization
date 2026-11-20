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
