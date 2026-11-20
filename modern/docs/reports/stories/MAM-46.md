```
Phase / item:      MAM-46 (local L-S025) Submit an enrolment application online and validate it (E101 to E104)
Outcome:           DONE (AC-1..AC-8), In Review
What was done:     - Red: 8 test files (feature with 14 scenarios, steps, suite, config, base, platform checks,
                     ApplicationValidatorTest 27, SubmitApplicationTest 7); corrections listed in MAM-46/red-report.md
                   - Green: domain ApplicationValidator (every check runs, one error each, E103 only for a valid NPI),
                     ProviderTypeMapping (IN/GR/FA + feed table), use cases SubmitApplication / ListApplications,
                     JDBC store, outbox bridge, controller (201 / 422 problem+json with errors / 400 / 403),
                     V2 migration (IND, GRP, HOSP mapping; one VALIDATED application per NPI)
Files created/changed: modern/services/enrollment-service/src/main/** , tests under src/test/**
Checks run:        ./gradlew :services:enrollment-service:check -> BUILD SUCCESSFUL, 51 tests 0 failures,
                   PIT 28/30 (93%); test_protection check MAM-46 -> OK; live via gateway: 422 E104, 201 VALIDATED
Not verified:      independent code review of this story
Questions for you: none
Next step:         MAM-17 consumes ApplicationValidated
```
