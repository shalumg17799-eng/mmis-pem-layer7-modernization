# MAM-57 red report: result of an intake run (counts, completion, failures, re-runs)

The intake was built before its tests (batch A5, "tests in Part D"), so these tests were written after the code. An earlier lane wrote most of them on 2026-11-20 (17:13-18:28), recorded their hashes
and was stopped under a deadline without ever running the whole suite. This lane (2026-11-21, test-writer, enrollment-service) ran it for the first time, corrected three faults in the tests, added tests and
recorded the hashes again. A test of code that already works is green at once; a test that fails because the code does not meet the AC or a lesson of the earlier reviews is kept red and reported (below).

The intake is one job: the corrections and the red tests are the same as in MAM-56/red-report.md and are not repeated in full. Test corrections 1 to 6 apply to this story's file set too (26 files, the same as MAM-56).

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
| AC-1 clean run, counts from zero | 1 | JdbcIntakeRunsTest, IntakeRunLoadTest, IntakeRunDatasetsTest (scenarios file), IntakeRunContractTest, IntakeJobTest, IntakeLoadTest |
| AC-2 every record counted once | 1 | IntakeLoadTest, IntakeRunDatasetsTest (baseline: 8 = 3 + 4 + 1) |
| AC-3 duplicate NPI is skipped | 1 | IntakeLoadTest, SubmitFromFileTest, ErrorCodeTest, IntakeRunLoadTest, IntakeRunDatasetsTest |
| AC-4 rejections complete the run | 1 | IntakeLoadTest, JdbcIntakeRunsTest |
| AC-5 unreadable record ends the run FAILED | 1 | IntakeLoadTest, IntakeRecordsEdgeTest, IntakeRunLoadTest (0, 130, 199, 201 bytes), IntakeRunDatasetsTest (bad-length file), IntakeJobTest, IntakeRunContractTest |
| AC-6 a hard failure is never a warning | 1 | IntakeFailureReasonTest (RED, see above), IntakeAtomicityTest, IntakeLoadTest, JdbcIntakeRunsTest |
| AC-7 ADMIN only, 404 | 7 | IntakeRunRolesTest, IntakeRunContractTest, IntakeUploadLimitTest (read failure) |
| AC-8 re-run loads nothing twice | 1 | IntakeLoadTest, IntakeAtomicityTest, IntakeJobTest, IntakeRunDatasetsTest (baseline twice, other date), JdbcIntakeRunsTest, SubmitFromFileTest |
