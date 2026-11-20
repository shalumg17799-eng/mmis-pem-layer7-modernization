```
Phase / item:      MAM-82 Console: provider detail panels for screening, sanctions, revalidation, notices and audit
Outcome:           DONE (fast-mode, gates postponed); AC-1..AC-8 built, tests 38/38 green
What was done:     - Red: detail-panels.test.tsx (32), console-panels.spec.ts (6)
                   - Green: below the record the page shows five panels, each its own query, heading and failure with Retry:
                     Screening (each screening with reason, date, overall result and the table Source | Result | Verdict | Detail |
                     Error code, "E203 owner excluded", "Conditional (C)", "Not screened yet"), Sanctions (Effective date | Action |
                     Reason | By, "None recorded"), Revalidation (the due date of the provider and Due date | Case | Opened | Closed,
                     "No revalidation case"), Notices (Sent | Notice | Status, "No notices sent"), Audit (When | Who | Action,
                     "No audit entries"). Roles: analyst1 four panels and no audit request; supervisor1 all five; admin1 no
                     Revalidation panel and no revalidation request. A panel that is not shown is not requested.
                     revalidation-service and the audit endpoint are not deployed yet: those panels show their own failure with
                     Retry and no data.
Files created/changed: web-ui/src/features/console/providers/panels/{DetailPanels,Panel}.tsx, ProviderDetail.tsx,
                   src/shared/api/types.ts, src/shared/format/format.ts;
                   tests/console/detail-panels.test.tsx, tests/support/panels.ts, e2e/console-panels.spec.ts
Checks run:        vitest detail-panels + provider-detail + approval-reach -> 70/70 (PowerShell, default pool);
                   playwright console-panels + console-detail + console-approval -> 44/44 (axe per role and with a failed panel);
                   lint, tsc -b, build clean
Not verified:      against the live services (the sanctions, screenings and notices answers are stubbed in the browser, the
                   revalidation and audit endpoints do not exist yet); code review; Jira compared by the coordinator
Questions for you: none
Next step:         MAM-90 (Record sanction link and form); code-reviewer
```
