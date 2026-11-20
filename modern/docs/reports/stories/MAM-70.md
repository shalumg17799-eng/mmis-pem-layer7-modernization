```
Phase / item:      MAM-70 Portal: see my notices
Outcome:           DONE (fast-mode, gates postponed); AC-1..AC-7 built, tests 21/21 green
What was done:     - Red: notices.test.tsx (17), portal-notices.spec.ts (4); failed because the page was a placeholder
                   - Green: /portal/notices reads GET /api/v1/notices (no npi parameter; page only when > 0); table Sent, Notice,
                     Channel, Status; notice names "Enrolment approved (APPROVED)", "Application rejected (REJECTED)", denial,
                     termination, suspension, reinstatement, general, revalidation, an unknown template as its code; Sent / Failed
                     as words; "No notices yet"; total + paging; "The notices could not be loaded. Try again." with Retry on 401,
                     5xx and network failure; notices of another NPI are dropped
Files created/changed: web-ui/src/features/portal/notices/NoticesPage.tsx, src/shared/api/{client,types}.ts,
                   src/shared/format/format.ts, src/app/screens.tsx; tests/portal/notices.test.tsx, tests/support/notices.ts,
                   e2e/portal-notices.spec.ts
Checks run:        vitest tests/portal/notices.test.tsx (--pool=vmThreads) -> 17/17; playwright portal-notices.spec.ts -> 4/4
                   (axe with notices, none, failure and page 2); lint, tsc -b, build clean;
                   test_protection MAM-70, MAM-62, MAM-64, MAM-87 -> OK
Not verified:      against the live notification-service; code review; the Jira description was not compared with the spec
                   (no Atlassian tool available to this agent)
Questions for you: none (the 401 reading is in the red report)
Next step:         code-reviewer; journey J1 (row "Enrolment approved (APPROVED)")
```
