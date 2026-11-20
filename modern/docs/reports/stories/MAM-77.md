```
Phase / item:      MAM-77 Console: provider search by NPI, Medicaid ID or name (replaces CICS PINQ)
Outcome:           DONE (fast-mode, gates postponed); AC-1..AC-8 built, tests 35/35 green; 3 recorded MAM-87 tests conflict (below)
What was done:     - Red: provider-search.test.tsx (29), console-search.spec.ts (6)
                   - Green: /console/providers has the inputs NPI, Medicaid ID and Name contains and Search; only the filled inputs go to
                     GET /api/v1/providers?npi= | medicaidId= | name= (never GET /api/v1/providers/{npi}); a blank or malformed entry sends
                     nothing and shows "1 error in the search" (linked summary, the same text at the NPI field, focus in the summary, typed
                     text kept): "Enter an NPI, a Medicaid ID or a name", "NPI must be 10 digits"; the results table NPI | Name | Type | Status |
                     Risk with the NPI linking to the detail; an empty answer is "No provider found" in a status area; 403 "You are not allowed to
                     search providers", 400 the API detail, 5xx / network "The search could not be completed. Try again."; the last valid NPI is
                     kept in sessionStorage (this tab only) and pre-fills the field. More matches than the page holds are announced
                     ("Showing 20 of N providers"); that sentence is not covered by a test.
Files created/changed: web-ui/src/features/console/providers/ProviderSearch.tsx;
                   tests/console/provider-search.test.tsx, tests/support/search.ts, e2e/console-search.spec.ts
Checks run:        vitest provider-search + provider-lookup (MAM-34) + approval-reach -> 47 pass, 2 fail (MAM-87, below);
                   playwright console-search + provider-lookup (live stack, MAM-34 AC-3 passes with the list search) + console-approval ->
                   34 pass, 1 fail (MAM-87, below); lint, tsc -b, build clean
Conflict with recorded tests of MAM-87 (not edited; for the test-writer, they stub only GET /api/v1/providers/{npi} for the search, which the page no longer calls):
                   tests/console/approval-reach.test.tsx "[MAM-87 AC-1] the NPI of a Provider search result links to the provider detail (MAM-77 AC-2)":
                     - "[MAM-87 AC-1] the NPI in the result row is a link to /console/providers/:npi and is still shown in full"
                     - "[MAM-87 AC-1] following the link opens the provider detail page of that provider"
                   e2e/console-approval.spec.ts "[MAM-87 AC-1] in Provider search the NPI of the result is a link to the provider detail"
                   Fix: answer GET /api/v1/providers (with the npi parameter) in those stubs; the assertions themselves stay.
Not verified:      the medicaidId and name parameters against the live provider-service (MAM-37 is not built); code review
Questions for you: none
Next step:         test-writer for the three MAM-87 stubs; journey J4 (MAM-76)
```
