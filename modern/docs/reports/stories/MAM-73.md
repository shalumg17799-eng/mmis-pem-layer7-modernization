```
Phase / item:      MAM-73 Public directory page (no sign-in)
Outcome:           DONE (fast-mode, gates postponed); AC-1..AC-8 built, Playwright 7/7, Vitest 24/25 (1 recorded test is wrong)
What was done:     - Red: directory.test.tsx (25), public-directory.spec.ts (7); failed because /directory did not exist
                   - Green: /directory outside the role guard; a public API client (createPublicApiClient / usePublicApi) that
                     never sends an Authorization header; form Name + Provider type (Any, IN, GR, FA) + Search; table "Active
                     providers" with Name (displayName as returned), NPI, Type, Specialty (taxonomy); "No providers found",
                     total + paging, 400 detail next to the form, "The directory is not available right now. Try again."
Files created/changed: web-ui/src/features/public/directory/PublicDirectory.tsx, src/shared/api/{client,useApi,types}.ts,
                   src/app/App.tsx; tests/public/directory.test.tsx, tests/support/directory.ts, e2e/public-directory.spec.ts
Checks run:        vitest tests/public/directory.test.tsx (--pool=vmThreads) -> 24/25; playwright public-directory.spec.ts -> 7/7
                   (axe on empty, results, no-match and failure; the Authorization header checked on every request);
                   lint, tsc -b, build clean; test_protection MAM-73, MAM-62, MAM-64, MAM-87 -> OK
Not verified:      against the live gateway (the directory answers are stubbed in the browser); code review; the Jira
                   description was not compared with the spec (no Atlassian tool available to this agent)
Questions for you: 1. The failing test "[MAM-73 AC-8] one h1, the landmarks banner and main ..." (tests/public/directory.test.tsx)
                      uses getByRole('banner'), which finds the GovBanner header too. Allow the test-writer to change it to
                      getAllByRole('banner') and re-record? The page is correct.
Next step:         fix and re-record that one test; code-reviewer; journey J1 (/directory step)
```
