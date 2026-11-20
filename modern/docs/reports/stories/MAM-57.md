```
Phase / item:      MAM-57 Report the result of an intake run: exclusive counts, completion, failures and safe re-runs
Outcome:           BUILT (build-phase: tests in Part D), batch A5 (with MAM-56)
What was done:     - Counts kept after every record in intake_run (each record exactly once): written = VALIDATED; skipped =
                     a duplicate NPI rejected with E103 only, or a record an earlier run of the same business date already
                     processed (no application, no row, no event); errored = any other rejection, an unreadable record or
                     a record that could not be stored. Rejections alone give COMPLETED (legacy RC 4); an unreadable record
                     (length not 200) or a storage failure ends the run FAILED naming the record (and NPI); later records
                     are not processed, earlier results are kept
                   - AC status (live, synthetic NPIs): AC-1 built (fresh counts per run); AC-2 checked (read 7 = written 3
                     + errored 3 + skipped 1; the record with two failed checks counted once); AC-3 checked (second
                     1007630431 REJECTED with exactly E103, one APPERROR row, counted skipped, one VALIDATED application);
                     AC-4 checked (COMPLETED, error null, errored 3); AC-5 checked (150-byte record 2: FAILED "record 2 is
                     150 bytes, not 200", read 2, errored 1, record 3 not processed); AC-6 built (storage failure path),
                     not live (needs fault injection); AC-7 checked (ADMIN only, unknown 404); AC-8 checked (same file and
                     date again: read 7, written 0, skipped 7, empty error file)
Files created/changed: shared with MAM-56 (the same intake job; this story is its run report): enrollment-service
                   application/IntakeRuns, IntakeLauncher, IntakeLoad; infrastructure/JdbcIntakeRuns, IntakeJobConfig;
                   api/IntakeRunController; db/migration/enrollment/V3__spring_batch.sql
Checks run:        see MAM-56
Not verified:      automated tests (Part D); AC-6 live
Questions for you: none
```

## Lane 3 tests (2026-11-20)

Outcome line unchanged. Tests for every AC of MAM-57 (AC-1 to AC-8), written after the code (the story was built without tests); same lane history, files and runs as MAM-56 (the intake is one job and one set of tests).

- Test files (26, recorded in `MAM-57.tests.sha256`): the same set as MAM-56 (feature `MAM-57.feature` instead of `MAM-56.feature`).
- Tests per AC (JUnit + Cucumber scenarios): AC-1 16+1, AC-2 2+1, AC-3 8+1, AC-4 2+1, AC-5 26+1, AC-6 41+1, AC-7 18+7, AC-8 21+1. AC-6 and AC-8 also have a real failure of the database for every write of a record
  (a trigger that rejects it, a renamed column for the SELECT, a NUL byte the database cannot store, a job that cannot be started) and the re-run after it.
- Counts: read = written + errored + skipped in every scenario; the baseline file gives 8 = 3 + 4 + 1, the scenarios file 8 = 8 + 0 + 0, the bad-length file 3 = 1 + 2 + 0 and ends FAILED naming record 3; a second
  run of the same business date and file skips everything (8 of 8) with an empty error file and no new event; under another business date the valid records are duplicates (E103, skipped).
- Written after the code: the tests of code that works passed at once; the red ones are defects of the code (below).

Runs (2026-11-21, from `modern/`, one Gradle build at a time through the shared lock, `--no-daemon`):
- `./gradlew -I <init script: Spotless on src/test of enrollment-service only> :services:enrollment-service:spotlessApply` before each run (formats the test sources only; it changed no recorded file).
- `./gradlew :services:enrollment-service:check --continue`. Run 1 (00:48) was stopped by this lane after 17 minutes (see "Test corrections" in the red-report of MAM-56, item 1); run 2 (01:35, 5 min 37 s):
  454 tests, 19 failures; run 3 (01:53): 464 tests, 21 failures; **run 4 (02:11-02:19, 6 min 3 s): 467 tests, 21 failures, 0 skipped**. The 21 failures are the same in runs 3 and 4 (no flaky
  test), all of them defects of the code (below); `testSourceCheck` and `versionCheck` passed; ArchUnit (`PlatformChecksTest`) passed; `spotlessJavaCheck` FAILED on `src/main` (defect 8).
- PIT (part of `check`, unit tests of `domain` and `application` only): **109 of 109 mutations killed (100 %)**, test strength 100 %, line coverage of the mutated classes 218/220. The first run
  had 1 survivor (`ListApplications.list` returned null without a test failing); `ListApplicationsTest` (MAM-54) kills it.
- `./gradlew -I <init script: orders the coverage tasks after compileJava> :services:enrollment-service:jacocoTestReport :services:enrollment-service:jacocoTestCoverageVerification -x :services:enrollment-service:test`
  (the report of the same run; with a failing test `check` does not reach the verification): **line coverage 98.6 %** (563 of 571 lines; 99.2 % without `*Application`, as the gate counts), branch 91.2 %,
  gate 85 %: passed. (Before this lane the same module showed 58.84 %: the intake had no tests.)

Defects found (the tests that show them stay red, none was weakened; production code was not touched):
1. `IntakeLoad.java:122-125` - MAM-57 AC-6 (D-024): a record that cannot be stored ends the run with "record n NPI x could not be stored: " + the raw exception text. Expected: a short reason
   (at most 200 characters) without SQL text, the full exception logged. Actual: `PreparedStatementCallback; uncategorized SQLException for SQL [INSERT INTO application (public_id, ...` (300+ characters).
   Tests: `IntakeFailureReasonTest` errorHasNoSqlText and errorIsShort at APPLICATION_INSERT and RUN_KEY.
2. `IntakeLoad.java:109, 130, 139, 141` - MAM-57 AC-6: `alreadyProcessed`, `addErrorRow`, `counts` and `complete` are called outside the try block, so their failures reach the job
   unwrapped: the reason is Spring's text with the SQL statement, longer than 200 characters, and for `alreadyProcessed` it does not name the record. Tests: errorHasNoSqlText/errorIsShort at ERROR_ROW,
   RUN_COUNTS, RUN_COMPLETE, errorHasNoSqlText at ALREADY_PROCESSED_CHECK, `checkFailureNamesTheRecord`, and the two NUL-byte tests (a record the database cannot store ends the run FAILED with
   `PreparedStatementCallback; SQL [SELECT count(*) FROM application a JOIN ...]; ERROR: invalid byte sequence for encoding "UTF8": 0x00`, no record named).
3. `IntakeJobConfig.java:87` and `IntakeRunController.java:118` (also `IntakeJobConfig.java:103`) - MAM-56 AC-1/AC-7, MAM-57 AC-6: a job that cannot be started is reported as
   "job PRVENR01 not started: " + the raw message (SQL text, 300+ characters) in the 202 answer and in `intake_run`. Tests: errorHasNoSqlText and errorIsShort at JOB_LAUNCH.
4. `JdbcIntakeRuns.java:21` - MAM-57 AC-6: `ERROR_MAX = 500`; the stored reason may be 500 characters, the rule is at most 200.
5. `IntakeLoad.java:130` - MAM-56 AC-6, MAM-57 AC-8: the APPERROR row of a rejected application is written after the application's own transaction has committed. When that write fails,
   the REJECTED application stays without its row, and the re-run skips the record (already processed), so the row is never written: the file has no row for a rejected application.
   Tests: `IntakeAtomicityTest` rejectedApplicationAndItsRowAreWrittenTogether (rows 0, stored 1) and rerunLeavesExactlyOneErrorRowForTheRejectedApplication (no row at all).
6. `libs/service-kit/.../OutboxWriter.java:41` (shared kit, lead decision; fixable inside the service in `KitEventOutbox.java:20-22`) - MAM-46 AC-1, lesson f: an X-Correlation-Id that is safe to log but no UUID
   (`trace-42`) goes into the event as `correlationId`; events.yaml says uuid. Test: `ApplicationLessonsTest` eventCorrelationIdIsAlwaysAUuid ("$.correlationId = trace-42 is not a valid uuid").
7. `api/RequestProblems.java:19-24` - MAM-56 AC-7, lesson h: no handler for `MultipartException`. On the real web server a multipart request whose content type has no boundary parameter, or whose
   message is cut off inside the file part, answers 500 with Spring Boot's default JSON (`{"timestamp":...,"status":500,"error":"Internal Server Error",...}`), not a 400 problem+json. Tests:
   `IntakeUploadLimitTest` unreadableMultipartBodyIsA400Problem (2 of 3 cases).
8. Spotless on `src/main`: `IntakeLoad.java` (format hunks at lines 103, 115 and 167: line wrapping) and `IntakeJobConfig.java`. `spotlessApply` on `src/main` is the first developer step.

Lessons of the earlier reviews, as they hold for this service:
- a (short reason, no SQL, full exception logged): `IntakeFailureReasonTest`, 7 failure points of the real database + a NUL byte (28 tests); the full exception is logged (green); reason and length RED (defects 1-4);
  when the database cannot be read, getIntakeRun and getIntakeErrorFile answer 5xx (never 404) with a body of at most 200 characters and no SQL text, and the exception is logged (`IntakeUploadLimitTest`, green).
- b (ISO date only): `IntakeRunStartApiTest` businessDateMustBeAnIsoDate, 13 values incl. `2026-03-31T00:00:00` and `2026-03-31T10:15:30Z`, all 400 problem+json, no run: green.
- c (done-marker in the same transaction): `IntakeAtomicityTest` (18): the run key of a record is in the application's transaction (green: nothing of record 2 remains, a re-run stores it once); the APPERROR row is not (RED, defect 5).
- d (a silent partner): the service calls no other service; `NoOutboundCallsTest` (ArchUnit) fails the day an HTTP client is added: green.
- e (% and _ literal): the intake has no text search; the list filters of MAM-54 are exact values and `%`, `_`, `\` are refused with 400 (`ApplicationLessonsTest`): green.
- f (events conform to events.yaml): `IntakeEventContractTest` (8: baseline, scenarios, absent members omitted, Kafka key) green; the online correlationId is RED (defect 6).
- g (correlation id): wire-possible unsafe ids (space, JSON, 65 characters, path, markup, empty) are replaced in header, log and event (6 + 2 tests, green); a tab in the header is answered consistently (green).
- h (problem+json for 400, 401, 403): all intake endpoints, every role, malformed run id: green; a malformed multipart body is RED (defect 7).

Notes: (1) Q-120 says Tomcat's 2 MB form limit applies. On the standalone service the limit seen by the real web server is Spring's multipart default of 1 MB per file (an upload of 1.5 MB gave 413); 2 MB is Tomcat's
limit in the local run mode, where Spring has no multipart configuration. `IntakeUploadLimitTest` therefore uses 0.9 MB (accepted) and 2.1 MB (refused, no run) which are on the same side of both limits; Q-120's text needs
this correction (option B, an explicit limit, would remove the difference). (2) A header value with a line break cannot reach a service over HTTP; with MockMvc, Spring Security's firewall answers such a request with an empty
400 after the controller has stored the application, so the earlier test (expecting 201) was replaced by wire-possible values (red-reports of MAM-46 and MAM-56).
