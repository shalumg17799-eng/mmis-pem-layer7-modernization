```
Phase / item:      MAM-69 Journey J1 end to end: a provider applies and is enrolled (main journey)
Outcome:           DONE (AC-1..AC-8), In Review
What was done:     - Red (13:40): AC-1..AC-3 passed live, failed at AC-4 (/console/queues missing)
                   - Review fixes before green (code-reviewer MUST-FIX 2, 3 and SHOULD-FIX 7-10): exact screening check,
                     start at /portal, Failed screening section asserted, leap-safe due date, `./pem.sh j1 [runs]` runner,
                     journeys out of the default e2e run; Kafka payloads proven by the service tests (Q-094 A, user)
                   - Green after the pages MAM-85, 73, 70, 68, 66 were built
                   - Root cause of intermittent net::ERR_CONNECTION_RESET found and fixed in config: "localhost" resolved
                     to ::1 where WSL's wslrelay (not Docker) listens; playwright.config maps localhost to 127.0.0.1 and the
                     runner sets NODE_OPTIONS=--dns-result-order=ipv4first (no test change)
Files created/changed: modern/web-ui/e2e/journeys/j1-enrolment.spec.ts, web-ui/playwright.config.ts, pem.sh,
                   docs/DEMO.md, docs/reports/stories/MAM-69/*
Checks run:        after ./pem.sh down, ./pem.sh up, ./pem.sh seed: ./pem.sh j1 2 -> 2/2 PASSED (20.1 s, 18.3 s);
                   ./pem.sh j1 3 -> 3/3 PASSED (16.5 s, 20.4 s, 14.7 s); zero axe violations on every page visited;
                   test_protection MAM-69 -> OK; stack reseeded afterwards
Not verified:      the Kafka payload facts inside J1 itself (proven by MAM-17, 25, 35, 27, 59/58 on their topics, Q-094 A);
                   Chromium only
Questions for you: none
Next step:         Part 2
```
