```
Phase / item:      MAM-68 Portal: see my provider record
Outcome:           DONE (fast-mode, gates postponed); AC-1..AC-7 built, tests 30/30 green
What was done:     - Red: provider-record.test.tsx (26), portal-record.spec.ts (4)
                   - Green: /portal/provider shows the own record (GET /api/v1/providers/{npi from the token claim}, no other
                     provider request): table Field / Value with NPI, Medicaid ID, type, taxonomy, status, screen flag, risk
                     tier ("Not assigned"), effective date, revalidation due ("Not set") and the personal details (IN) or Legal
                     name, DBA name, Owner NPI (GR, FA); 404 explains that a record is created when an application is accepted,
                     with links to My enrolment and Apply for enrolment; 403 is "Access denied" with no data; other failures
                     say the record could not be loaded. The standalone mode added to ProviderSummary in MAM-64 was removed.
Files created/changed: web-ui/src/features/portal/provider/ProviderRecord.tsx, src/features/portal/home/ProviderSummary.tsx;
                   tests/portal/provider-record.test.tsx, e2e/portal-record.spec.ts
Checks run:        vitest provider-record + apply-followup + home-provider-summary (--pool=vmThreads) -> 53/53;
                   playwright portal-record.spec.ts -> 4/4 (axe on individual, organisation, no-record, 403);
                   lint, tsc -b, build clean; test_protection MAM-68, MAM-62, MAM-64, MAM-87 -> OK
Not verified:      against the live provider-service; code review; the Jira description was not compared with the spec
                   (no Atlassian tool available to this agent)
Questions for you: none
Next step:         code-reviewer; journey J1 (main contains "Active (A)")
```
