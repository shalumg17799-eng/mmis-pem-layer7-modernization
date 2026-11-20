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

## Lane 3 tests (2026-11-20)

Outcome line unchanged. The 12 recorded files of MAM-46 (`MAM-46.tests.sha256`) were extended by the lessons of the earlier reviews; the first result of the whole module on 2026-11-21 is in the MAM-56 report.

- Tests per AC (JUnit + Cucumber, run 4 of 2026-11-21): AC-1 11+1, AC-2 10+2, AC-3 13+1, AC-4 3+1, AC-5 2+1, AC-6 4+1, AC-7 3+1, AC-8 0+6 (the role outline: 6 examples). `ApplicationLessonsTest` adds 12 tests tagged MAM-46
  (events conform to events.yaml, the correlation id, one transaction for the application and its event).
- Correlation id (lesson g): six unsafe ids that can travel in an HTTP header (a forged log line after a space, a forged JSON log line, 65 characters, a path with a quote, markup, an empty string) are replaced by a
  generated id in the answer, the log and the ApplicationValidated event (green); a header with a tab is answered consistently (an application is never stored behind a 400).
- One test is RED and stays so: `ApplicationLessonsTest` eventCorrelationIdIsAlwaysAUuid (defect 6 in the MAM-56 report): the event carries `trace-42`, events.yaml says uuid. The cause is in the shared kit
  (`OutboxWriter.java:41`), a fix inside the service is possible in `KitEventOutbox.java`.
- Result of the module: 467 tests, 21 failures (none of MAM-46 except the one above), 0 skipped; JaCoCo 98.6 % lines; PIT 109/109; `spotlessJavaCheck` fails on `src/main` of the intake (MAM-56 defect 8).
