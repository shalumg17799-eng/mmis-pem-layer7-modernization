```
Phase / item:      MAM-56 Run the legacy APPINPUT intake as a batch job with one error row per rejected application
Outcome:           BUILT (build-phase: tests in Part D), batch A5
What was done:     - Spring Batch job PRVENR01 (enrollment_db Flyway V3 Spring Batch tables, approved): POST
                     /api/v1/batch/intake-runs multipart (file, businessDate, batchId) ADMIN/SYSTEM -> 202 RUNNING; the job
                     (one background thread, file handed over in memory) reads one record per line, or 200-byte blocks
                     when the file has no line end; bytes 1-130 as PRVRECRD by position (bytes 131-200 and the
                     tier/status/flag/dates of 32-50 are not taken over), the view of the type code (IN individual, GR/FA
                     organisation with owner NPI 121-130); each record goes through SubmitApplication.fromFile (same
                     validation, storage and events as online, source LEGACY_FILE, intake_run_id and raw_record set in the
                     same transaction), each on its own; a rejected application appends one 250-byte APPERROR row (NPI and
                     type as read, ' ERR:VALIDATION-FAILED', spaces; no line ends); 'PRVENR01: STARTING ENROLLMENT LOAD -
                     BATCH <id>' logged, DEFBATCH when blank; GET .../{runId} and .../{runId}/error-file (ADMIN)
                   - Multipart upload: the services' Tomcat had no multipart configuration in the local run mode (first
                     live upload: 500 "Unable to process parts as no multi-part configuration has been provided"); fixed
                     with infrastructure/MultipartSupport in enrollment and sanctions
                   - service-kit defect found and fixed: CorrelationIdFilter put the MDC key "service", which collides with
                     the ECS "service" object, so every log line written on a request thread failed to append ("The name
                     'service' has already been written"); the key is now "pemService"
                   - AC status (live, synthetic NPIs 1007..., no seed): AC-1 202 RUNNING then COMPLETED with the business
                     date; AC-2 202 without batchId; AC-1/AC-2 log lines: see the batch line; AC-3 checked (1007630431
                     VALIDATED LEGACY_FILE with exactly RIVERA/MARIA/MD12345/TX; 1007936531 legal name and owner
                     1990000111); AC-4 checked (blank NPI, XX and empty-taxonomy records REJECTED); AC-5 checked (a
                     rejection does not stop the load, read 7); AC-6 checked (4 APPERROR rows of 250 bytes in input order,
                     blank NPI as spaces, none for valid ones); AC-7 checked (GET and file ADMIN only, SYSTEM/ANALYST/
                     SUPERVISOR/PROVIDER 403, no token 401, unknown 404, no file or date 400)
Files created/changed: enrollment-service application/IntakeRuns, IntakeLauncher, IntakeLoad, SubmitApplication
                     (fromFile); infrastructure/JdbcIntakeRuns, IntakeJobConfig, MultipartSupport; api/IntakeRunController;
                     db/migration/enrollment/V3__spring_batch.sql; libs/service-kit web/CorrelationIdFilter (MDC key)
Checks run:        enrollment-service:test 90/90; service-kit:test; bootJar; live checks above
Not verified:      automated tests (Part D); the ApplicationValidated/Rejected events per record were not read from Kafka
Questions for you: none
```

## Lane 3 tests (2026-11-20)

Outcome line unchanged. Tests for every AC of MAM-56 (AC-1 to AC-7), written after the code (the story was built without tests). The earlier lane wrote and recorded most of them on 2026-11-20 (17:13-18:28) and never ran
the whole suite; this lane ran it for the first time on 2026-11-21, corrected three test-side faults (red-report) and added tests.

- Test files (26, recorded in `MAM-56.tests.sha256`): `features/MAM-56.feature`, `IntakeRunSteps`; support `AppInputFile`, `ContractFile`, `EventsContractFile`, `DbRejections`, `IntakeApi`, `IntakeIntegrationTest`;
  `IntakeRunStartApiTest` 34, `IntakeRunRolesTest` 40, `IntakeRunLoadTest` 25, `IntakeRunDatasetsTest` 16, `IntakeRunContractTest` 15, `IntakeUploadLimitTest` 8, `IntakeLoadTest` 61, `IntakeRecordsEdgeTest` 8,
  `SubmitFromFileTest` 12, `ErrorCodeTest` 7, `IntakeAtomicityTest` 18, `IntakeEventContractTest` 8, `IntakeFailureReasonTest` 28, `IntakeJobTest` 6, `JdbcIntakeRunsTest` 17, `MultipartSupportTest` 2,
  `NoOutboundCallsTest` 1 (the classes are shared with MAM-57; the counts are the whole class).
- Tests per AC (JUnit + Cucumber scenarios; an outline counts one per example): AC-1 24+1, AC-2 13+2, AC-3 33+1, AC-4 29+1, AC-5 2+1, AC-6 12+2, AC-7 54+15. Also tagged: Q-095 (1: a staff-run intake is not bound to
  an own NPI), Q-100 (1), Q-120 (3: the upload limit on the real web server).
- Byte level: the APPERROR rows are compared as 250-character strings (NPI 1-10, ` TYPE:` 11-16, type 17-18, ` ERR:VALIDATION-FAILED` 19-40, 210 spaces), a blank NPI as ten spaces, non-ASCII input as printable ASCII;
  the files of MAM-44 (`appinput-baseline.dat`, `appinput-scenarios.dat`, `appinput-bad-length.dat`) are run through the endpoint; LF, CR LF and block files (no line ends) are read.
- Written after the code: the tests of code that works passed at once; 21 tests of the module are red by defect (below); the Cucumber suite (74 scenarios of the four stories) passed in every run.

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
