```
Phase / item:      MAM-64 Portal: apply for enrolment online, with field errors E101 to E104
Outcome:           DONE (AC-1..AC-7), In Review
What was done:     - Red: 101 Vitest (tests/portal/apply-*.test.tsx) + 25 Playwright (e2e/portal-apply.spec.ts) failed on
                     the missing page (no Submit application button, no Validated, no provider-record message)
                   - Green (first attempt): ApplyPage, applicationForm, fields, ErrorSummary, minimal ApplicationStatus
                     and ProviderRecord (full pages are MAM-66 and MAM-68); client POST keeps problem+json detail/errors
                   - Readings (accepted by the lead): individual inputs shown until a type is chosen; a 401 on submit keeps
                     the form and offers "Sign in again" (endSessionOn401: false for this call only, AC-6)
Files created/changed: modern/web-ui/src/features/portal/apply/*, features/portal/applications/ApplicationStatus.tsx,
                   features/portal/provider/ProviderRecord.tsx, shared/ui/ErrorSummary.*, shared/api/client.ts, types.ts,
                   features/portal/home/ProviderSummary.tsx, app/screens.tsx; tests and reports under MAM-64/
Checks run:        (lead, this session) npm test -> 324/325 (the 1 failure is MAM-87, see MAM-87); npx playwright test
                   e2e/portal-apply.spec.ts -> 25/25; test_protection MAM-64, MAM-62 -> OK
                   (UI agent) npm run lint, npx tsc -b, npm run api:check, npm run build -> clean
                   Live: J1 AC-1 passed through this page against the real stack (submit -> Validated)
Not verified:      independent code review (Part 1 step 4, together with MAM-87 and MAM-69); Chromium only
Questions for you: none
Next step:         code review with MAM-87 and MAM-69
```
