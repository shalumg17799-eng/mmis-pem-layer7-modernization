```
Phase / item:      MAM-80 Console: provider detail with every record field and the status shown as text and code
Outcome:           DONE (fast-mode, gates postponed); AC-1..AC-8 built, tests 31/31 green
What was done:     - Red: provider-detail.test.tsx (21), console-detail.spec.ts (10)
                   - Green: /console/providers/:npi shows the banner "Status: Active (A)" (h2, one label; the catch-all "Denied (X)" /
                     "Denied (blank)" for a value that is not P, A, T, D, S), an h2 with the NPI in full and the name, the compact summary
                     table and the table "Provider record" (13 rows for an individual, Legal name / DBA name / Owner NPI for GR and FA, risk
                     tier "Not assigned", due date "Not set"); 404 "No provider found" with "Back to provider search", 403 "You are not allowed
                     to see this provider", 5xx or network "The provider could not be loaded. Try again.", 401 to sign-in. The record table is
                     shared with the portal page (MAM-68).
Files created/changed: web-ui/src/features/console/providers/{ProviderDetail,ProviderLoader}.tsx, src/shared/ui/ProviderRecordTable.tsx,
                   src/features/portal/provider/ProviderRecord.tsx, src/shared/format/format.ts;
                   tests/console/provider-detail.test.tsx, tests/support/detail.ts, e2e/console-detail.spec.ts
Checks run:        vitest provider-detail + approval-reach + approval-page + provider-record -> 96/96 (PowerShell, default pool);
                   playwright console-detail + console-approval + portal-record -> 42/42 (axe on individual, organisation, five statuses,
                   X, not found); lint, tsc -b, build clean; test_protection MAM-80 and the earlier stories -> see the batch report
Not verified:      against the live provider-service; code review; the Jira description was compared by the coordinator
Questions for you: none
Next step:         MAM-82 panels below the record; code-reviewer
```
