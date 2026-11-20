```
Phase / item:      MAM-60 List the notices sent and the notice templates
Outcome:           DONE (AC-1..AC-8), In Review
What was done:     - Red: MAM-60.feature (8 ACs, 17 scenarios), NoticeListSteps, ListNoticesTest (7); failed on
                     the missing ListNotices / NoticeLog / NoticePage / NoticeView (MAM-60/red-run.txt)
                   - Green: GET /api/v1/notices (staff all; PROVIDER only the token NPI, another NPI or no NPI 403;
                     npi 10 digits, page >= 0, size 1..200 else 400), GET /api/v1/notice-templates (ADMIN);
                     V3 adds EXTRACT_READY (subject 'Extract ready', not sent by any event, Q-A9)
Files created/changed: modern/services/notification-service/src/main/** (application, infrastructure, api,
                   db/migration/notification/V3__extract_ready_template.sql); tests under src/test/**
Checks run:        ./gradlew :services:notification-service:check -> BUILD SUCCESSFUL, 46 tests 0 failures,
                   PIT 19/21 (90%); test_protection MAM-60, MAM-59 -> OK
Not verified:      independent code review; not yet deployed to the running stack
Questions for you: none (the EXTRACT_READY subject can be reworded under Q-067)
Next step:         portal and console screens (MAM-64, MAM-87, MAM-66, MAM-68, MAM-70, MAM-73, MAM-85)
```
