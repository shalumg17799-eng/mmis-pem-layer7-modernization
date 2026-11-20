```
Phase / item:      MAM-66 Portal: see my application status
Outcome:           DONE (fast-mode, gates postponed); AC-1..AC-6 built, tests 20/20 green; 19 recorded MAM-64 tests are now superseded (decision below)
What was done:     - Red: application-status.test.tsx (15), portal-status.spec.ts (5)
                   - Green: /portal/applications/:applicationId reads GET /api/v1/applications/{applicationId} (MAM-54):
                     table Application, Status (text), Submitted, NPI, Medicaid ID, type, taxonomy and the individual or
                     organisation details; "No validation errors" or the table "Validation errors" (Code, Field, Message,
                     in API order); 403 "Access denied" with no data; 404 "Application not found" with a link to My enrolment;
                     401 "sign in again" with a button; 5xx or network "The application could not be loaded. Try again." with Retry
Files created/changed: web-ui/src/features/portal/applications/ApplicationStatus.tsx, src/shared/api/types.ts;
                   tests/portal/application-status.test.tsx, e2e/portal-status.spec.ts
Checks run:        vitest application-status.test.tsx (--pool=vmThreads) -> 15/15; playwright portal-status.spec.ts -> 5/5
                   (axe on validated, rejected with two errors, 404, 403, failed load; keyboard on the link and Retry);
                   lint, tsc -b, build clean; test_protection MAM-66, MAM-62, MAM-64, MAM-87 -> OK
                   Full vitest (vmThreads): 421 pass, 17 fail = 15 superseded MAM-64 tests + MAM-73 banner test + MAM-62 PKCE
                   test (crypto.subtle is not available in the vm pools; it passes in the default pool, which is broken here).
                   Playwright portal-apply.spec.ts: 21 pass, 4 fail (superseded).
Superseded MAM-64 tests (not edited; they stub only the list GET and assert that no by-id call is made):
                   Vitest apply-status-page.test.tsx (5 of 6: VALIDATED, REJECTED, NPI and date, picks by id from the list,
                   list failure), apply-submit.test.tsx (3: opens the status page and shows Validated, stale-cache case, no
                   summary after acceptance), apply-failures.test.tsx (6: "the user can submit again" for 400, 403, 500, 503,
                   network, 422 without errors), apply-followup.test.tsx (1: View link shows the rejected application).
                   Playwright portal-apply.spec.ts: AC-2 submit, AC-4 corrected application, AC-5 rejected follow-up, AC-7 keyboard.
Not verified:      against the live enrollment-service; code review; the Jira description was not compared with the spec
                   (no Atlassian tool available to this agent)
Questions for you: 1. Re-record the 19 MAM-64 tests so that their stub also answers GET /api/v1/applications/{id}
                      (recommended: the status page is MAM-66's, the interim list reading is gone), or tell me to put the
                      list-based page back? Copy of the old page: kept outside the repo, not committed.
Next step:         decision above; code-reviewer; journey J1 (status "Validated" after the submit)
```
