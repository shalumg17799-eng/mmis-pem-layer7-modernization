```
Phase / item:      MAM-54 Read application status (a provider sees their own; an analyst sees all)
Outcome:           DONE (AC-1..AC-8), In Review
What was done:     - Red: MAM-54.feature (8 ACs), ReadApplicationsSteps, ReadApplicationTest; failed on the missing
                     GET /api/v1/applications/{applicationId}
                   - Green: ReadApplication (404 when unknown; a PROVIDER must be the submitter or own the NPI,
                     else 403, D-023), ApplicationReader/OwnedApplication, JdbcApplicationStore.byId, controller
                   - One test-data fix after recording (AC-4 used an NPI that already had a VALIDATED application,
                     so E103 fired correctly); see MAM-54/red-report.md; hashes re-recorded
Files created/changed: modern/services/enrollment-service/src/main/** (application, infrastructure, api);
                   tests under src/test/**; docs/reports/stories/MAM-54*
Checks run:        ./gradlew :services:enrollment-service:check -> BUILD SUCCESSFUL, 70 tests 0 failures,
                   PIT 36/38 (95%), test strength 97%; test_protection MAM-54, MAM-46 -> OK
Not verified:      independent code review; not yet deployed to the running stack
Questions for you: none
Next step:         MAM-63, MAM-60, then the portal and console screens
```
