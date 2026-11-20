```
Phase / item:      MAM-87 Console: approve or deny an enrolment
Outcome:           DONE (AC-1..AC-7), In Review
What was done:     - Red: 74 Vitest (tests/console/approval-*.test.tsx) + 28 Playwright (e2e/console-approval.spec.ts);
                     71/74 and 26/28 failed on the missing page (the rest are MAM-62 access-denied guards kept as regression)
                   - Green: ApprovalPage, DecisionForm, ScreeningResults, decision.ts, OnScreenRadio.css; ProviderDetail,
                     ProviderLoader, ProviderTable; useProvider; Provider search NPI links to the detail page
                   - Q-093 (user, option A): the two ambiguous "Pass (P)" lookups scoped to the provider table; byte-level
                     diff shows only those lines (code-reviewer CONFIRMED); hashes re-recorded
Files created/changed: modern/web-ui/src/features/console/approval/*, features/console/providers/*, shared/api/useProvider.ts,
                   shared/format/format.ts, app/screens.tsx; tests and reports under MAM-87/
Checks run:        (lead) Vitest approval-*.test.tsx 74/74; Playwright console-approval.spec.ts 28/28;
                   test_protection MAM-87 -> OK; live J1 AC-5 passes through this page (Approve -> Active, due +5y)
                   (code-reviewer) review of MAM-64/87/69: no MUST-FIX for MAM-87; Q-093 change CONFIRMED byte-level
Not verified:      SHOULD-FIX items deferred to the step-8 gates (focus after decision, refetch error after 409, live
                   character counter, detail on other statuses, code meanings); Chromium only
Questions for you: none open (Q-093 answered)
Next step:         J1 twice (./pem.sh j1 2) after MAM-70
```
