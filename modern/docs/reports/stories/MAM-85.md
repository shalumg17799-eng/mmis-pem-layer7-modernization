```
Phase / item:      MAM-85 Console: work queues (pending approval, failed screening, revalidation due, rejected applications)
Outcome:           DONE (fast-mode, gates postponed); AC-1..AC-8 built, tests 35/35 green
What was done:     - Red: queues.test.tsx (30), console-queues.spec.ts (5); failed because the page was a placeholder
                   - Green: /console/queues with four sections, each its own query and its own failure (D-024):
                     Pending approval (GET /api/v1/providers?status=P&screenFlag=P, Review link), Failed screening
                     (screenFlag=F plus GET /api/v1/screenings?npi=&result=FAIL per provider, "OIGLEIE (E203 owner excluded)"),
                     Revalidation due (DUE and IN_SCREENING merged, each case once), Rejected applications
                     (GET /api/v1/applications?status=REJECTED); counts in the headings, empty messages, paging (shared Pager),
                     "The list could not be loaded" with Retry. revalidation-service is not deployed yet: its section shows
                     the failure message, never an invented list.
Files created/changed: web-ui/src/features/console/queues/{QueuesPage,QueueSection,queries}, src/shared/ui/Pager.tsx,
                   src/shared/api/types.ts, src/shared/format/format.ts, src/app/screens.tsx;
                   tests/console/queues.test.tsx, tests/support/queues.ts, e2e/console-queues.spec.ts
Checks run:        vitest tests/console/queues.test.tsx (--pool=vmThreads) -> 30/30; playwright console-queues.spec.ts -> 5/5
                   (axe on filled, empty and one-failed pages, keyboard on Review and NPI); lint, tsc -b, build clean;
                   test_protection MAM-85, MAM-62, MAM-64, MAM-87 -> OK
Not verified:      against the live stack (answers are stubbed in the browser); code review; the Jira description was not
                   compared with the spec (no Atlassian tool available to this agent)
Questions for you: 1. Revalidation paging asks both statuses for the same page; a provider-side merged page can show up to 40 rows.
                   Accept, or change the contract to take several statuses?
Next step:         code-reviewer; run journey J1 (the Pending approval row with its Review link is what J1 uses)
```
