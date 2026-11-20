# MAM-56 red report: APPINPUT intake run (PRVENR01 as a Spring Batch job)

The intake was built before its tests (batch A5, "tests in Part D"), so these tests were written after the code. An earlier lane wrote most of them on 2026-11-20 (17:13-18:28), recorded their hashes
and was stopped under a deadline without ever running the whole suite. This lane (2026-11-21, test-writer, enrollment-service) ran it for the first time, corrected three faults in the tests, added tests and
recorded the hashes again. A test of code that already works is green at once; a test that fails because the code does not meet the AC or a lesson of the earlier reviews is kept red and reported (below).

## Test corrections (Q-097 C: no assertion weakened or removed, every change listed)

1. `api/IntakeUploadLimitTest` (run 1, 00:48-01:05, aborted by this lane): the class has a Spring context of its own (real web server) and carried `@ImportTestcontainers(PemContainers.class)` with
   `@DirtiesContext(AFTER_CLASS)`. A context stops the container beans it owns when it closes, so closing it stopped the shared Postgres and Kafka containers of every other test class: from the next
   database test on, every test waited 30 s for a connection ("HikariPool-2 - Connection is not available", `Connection refused`) and failed. Seen after 17 minutes (only the Ryuk container was left);
   the test JVM and the Gradle daemon of this lane were stopped, the lock released. Fix: the context takes the containers through `@DynamicPropertySource` (started there if this class runs first), no
   `@ImportTestcontainers`; after it 467 tests ran with no connection failure.
2. `api/IntakeUploadLimitTest`, the file under the limit: 1.5 MB gave 413. The real web server of the standalone service refuses a file part over Spring's multipart default of 1 MB (Q-120 speaks of Tomcat's
   2 MB, which applies only in the local run mode). The test now sends 0.9 MB (accepted) and keeps 2.1 MB (refused with a client error, no run): both sizes are on the same side of both limits. Assertions unchanged.
3. `api/IntakeRunDatasetsTest` scenariosIndividual: `SELECT trim(last_name), trim(first_name), ...` gave seven columns all named `trim`, which `queryForList` collapses into one key (the list held 2 values, not 8).
   The columns are aliased; the eight expected values are unchanged.
4. `api/ApplicationLessonsTest` (also recorded for MAM-46 and MAM-54) unsafeCorrelationIdIsReplaced: the test sent `abc\nFORGED LOG LINE` as X-Correlation-Id and expected 201. A header value cannot hold a line break
   on the wire; with MockMvc the request reaches the controller, which stores the application, and Spring Security's firewall (`StrictHttpFirewall`, header values without control characters) then answers 400 with an empty body
   when the response is written. The premise of the test is not a case of the real system. It is now a parameterized test over six unsafe values that can travel in a header (a forged log line after a space, a forged
   JSON log line, 65 characters, a path with a quote, markup, an empty string) with the same assertions (201, the echoed header is a safe id and not the raw value, no log line and no event carries it, the event
   correlationId is a UUID). Added: `tabInTheCorrelationIdIsAnsweredConsistently` (a tab is legal in HTTP and refused by the firewall: either 201 with one application or 400 with none; it passes).
5. Added, nothing existing changed: `application/IntakeRecordsEdgeTest` (8, how the file is cut into records), `infrastructure/NoOutboundCallsTest` (1, lesson d), `application/ListApplicationsTest` (6, MAM-54;
   kills the one PIT survivor of run 2), in `api/IntakeUploadLimitTest` 3 malformed-multipart cases (lesson h) and 2 read-failure cases (lesson a).
6. Formatting: `spotlessApply` on `src/test` only (init script in the session scratchpad) before each run changed no recorded file. Hashes recorded again on 2026-11-21: MAM-46 (12 files), MAM-54 (7), MAM-56 (26), MAM-57 (26);
   Q-095 unchanged (4); `test_protection.py check` prints OK for all five.

## Red tests at hand-over (21 of 467; the other 446 pass; 0 skipped)

| Test (class) | AC | Expected | Actual | Defect |
|---|---|---|---|---|
| `IntakeFailureReasonTest` errorHasNoSqlText at APPLICATION_INSERT, RUN_KEY, ERROR_ROW, ALREADY_PROCESSED_CHECK, RUN_COUNTS, RUN_COMPLETE, JOB_LAUNCH (7) | MAM-57 AC-6, MAM-56 AC-7 | the error of the run and the stored reason hold none of select, insert, update, sql | `PreparedStatementCallback; uncategorized SQLException for SQL [INSERT INTO application ...]; SQL state [P0001] ...` | 1, 2, 3 |
| `IntakeFailureReasonTest` errorIsShort at the same points, except ALREADY_PROCESSED_CHECK (6) | MAM-57 AC-6 | at most 200 characters | more than 200 characters (the whole Spring text with the SQL statement, up to the 500 of `ERROR_MAX`) | 1, 2, 3, 4 |
| `IntakeFailureReasonTest` checkFailureNamesTheRecord | MAM-57 AC-6 | reason starts "record 1" | `PreparedStatementCallback; bad SQL grammar [SELECT count(*) ...]` | 2 |
| `IntakeFailureReasonTest` nulByteEndsTheRunFailed, nulByteReasonIsClean (2) | MAM-57 AC-6 | FAILED, reason starts "record 2 NPI 1003000333", short, no SQL | reason `PreparedStatementCallback; SQL [SELECT count(*) FROM application a JOIN intake_run r ...]; ERROR: invalid byte sequence for encoding "UTF8": 0x00` | 2 |
| `IntakeAtomicityTest` rejectedApplicationAndItsRowAreWrittenTogether | MAM-56 AC-6 | APPERROR rows of the run = applications stored for record 2 | rows 0, stored 1 | 5 |
| `IntakeAtomicityTest` rerunLeavesExactlyOneErrorRowForTheRejectedApplication | MAM-56 AC-6, MAM-57 AC-8 | one APPERROR row for 1003000333 in all | no row (the re-run skips the record) | 5 |
| `IntakeUploadLimitTest` unreadableMultipartBodyIsA400Problem, no boundary parameter and cut-off file part (2) | MAM-56 AC-7 | 400 application/problem+json, no run | 500 `{"timestamp":...,"status":500,"error":"Internal Server Error",...}` | 7 |
| `ApplicationLessonsTest` eventCorrelationIdIsAlwaysAUuid | MAM-46 AC-1 | no violation of ApplicationValidatedEnvelope | `$.correlationId = trace-42 is not a valid uuid` | 6 |

Defect numbers are those of the "Lane 3 tests" section of MAM-56.md (file:line there). 16 + 2 + 2 + 1 = 21.

## AC to test mapping (run 4 of 2026-11-21)

| AC | Scenarios (feature) | JUnit classes |
|---|---|---|
| AC-1 start with business date and batch id | 1 | IntakeRunStartApiTest, IntakeRunContractTest, IntakeJobTest, IntakeLoadTest (open), MultipartSupportTest, IntakeUploadLimitTest (Q-120), NoOutboundCallsTest |
| AC-2 DEFBATCH | 2 | IntakeRunStartApiTest (null, blanks, trimmed, 8 and 9 characters), IntakeLoadTest |
| AC-3 PRVRECRD bytes 1-130 | 1 | IntakeLoadTest, IntakeRecordsEdgeTest, IntakeRunLoadTest (LF, CR LF, blocks), IntakeRunDatasetsTest (scenarios file), IntakeEventContractTest, SubmitFromFileTest |
| AC-4 same validation as online | 1 | IntakeLoadTest, SubmitFromFileTest, ErrorCodeTest, IntakeRunLoadTest, IntakeRunDatasetsTest (baseline file), IntakeEventContractTest |
| AC-5 a rejection does not stop the load | 1 | IntakeLoadTest |
| AC-6 APPERROR, one row per rejected application | 2 | IntakeLoadTest, JdbcIntakeRunsTest, IntakeRunLoadTest (non-ASCII), IntakeRunDatasetsTest, IntakeAtomicityTest, IntakeRunContractTest |
| AC-7 roles and run parameters | 15 | IntakeRunRolesTest (all roles, contract x-roles), IntakeRunStartApiTest (13 date values, leap day, no file, no date), IntakeRunContractTest (problem+json), IntakeUploadLimitTest (multipart bodies) |
