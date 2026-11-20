```
Phase / item:      MAM-90 Console: record a sanction (supervisor)
Outcome:           DONE (fast-mode, gates postponed); AC-1..AC-8 built, tests 44/44 green
What was done:     - Red: sanction.test.tsx (35), console-sanction.spec.ts (9)
                   - Green: the detail page offers "Record sanction" to SUPERVISOR only; /console/providers/:npi/sanctions/new
                     (supervisor only, the route guard shows Access denied to the other roles) has the form NPI (read-only),
                     Action (Terminate (T), Suspend (S), Reinstate (R)), Reason (60) and Effective date (MM/DD/YYYY, sent as
                     2026-03-31); no action or an empty or impossible date sends nothing and shows field errors, a linked
                     summary and the focus in it; POST /api/v1/sanctions {npi, action, reason?, effectiveDate}; the page
                     confirms the status of the 201 ("Suspended (S)", "Terminated (T)", "Active (A)"); the Sanction history
                     (GET /api/v1/sanctions?npi=) refreshes after every action; a 422 is shown at the field that its detail
                     names and in the summary; 404 "No provider found", 403 "You are not allowed to record sanctions", 5xx,
                     503 and network "The sanction could not be applied. Nothing was recorded."; no status is blocked.
Files created/changed: web-ui/src/features/console/sanction/{SanctionPage.tsx,sanctionForm.ts},
                   src/features/console/providers/{ProviderDetail.tsx,panels/SanctionTable.tsx,panels/DetailPanels.tsx},
                   src/app/screens.tsx; tests/console/sanction.test.tsx, tests/support/sanction.ts, e2e/console-sanction.spec.ts
Checks run:        vitest sanction + detail-panels + provider-detail -> 88/88 (PowerShell, default pool);
                   playwright console-sanction + console-panels -> 15/15 (axe on the empty form, field errors, after a success,
                   404 / 403 / 503, 422; keyboard-only recording); lint, tsc -b, build clean
Not verified:      against the live sanctions-service and provider-service (journey J5 live was not run; the request body and the
                   answers follow the contract and the Java service); the 503 body shape (Q-096); code review
Questions for you: none
Next step:         code-reviewer; journey J5 (MAM-79)
```
