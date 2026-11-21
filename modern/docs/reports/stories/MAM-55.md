```
Phase / item:      MAM-55 Run the legacy SANCIN sanction intake as a batch job
Outcome:           BUILT (build-phase: tests in Part D), batch A5
What was done:     - Spring Batch job PRVSAN01 (sanctions_db Flyway V2 Spring Batch tables, approved): POST
                     /api/v1/batch/sanction-intake-runs multipart (file, businessDate) ADMIN/SYSTEM -> 202 RUNNING; GET
                     .../{runId} (ADMIN). The job reads every 80-byte record (per line, or 80-byte blocks) through the
                     MAM-42 codec and applies it with RecordSanction (provider-service status change first, then history
                     with sourceFeed SANCIN and recordedBy = the caller's login, and SanctionApplied), each record on its
                     own; unknown NPI -> notFound + 'PRVSAN01: NPI NOT FOUND <npi>'; unknown action or impossible date ->
                     rejected; a record an earlier run of the same business date already applied -> skipped; an
                     unreadable record or a provider-service 5xx ends the run FAILED naming the record and NPI (that
                     record is not counted); counts read, applied, notFound, rejected, skipped, terminated (applied T of
                     the run); 'PRVSAN01: PROVIDERS TERMINATED=nnnnn' at the end; sanction.intake_run_id links the history
                   - AC status (live, synthetic NPIs registered by the MAM-56 run): AC-1 checked (202 RUNNING; no file or
                     no date 400); AC-2 checked (reason 'SYNTHETIC TEST REASON', effectiveDate 2026-03-31); AC-3 checked
                     (S, T, R applied: providers S, T and A; history sourceFeed SANCIN, recordedBy admin1); AC-4 checked
                     (read 6, applied 3, notFound 1, rejected 2, COMPLETED; NOT FOUND line logged); AC-5 checked in part
                     (terminated 1, 'PROVIDERS TERMINATED=00001'); AC-6 checked for the short record (FAILED naming record
                     2, record 1 applied); the 5xx case not live; AC-7 checked (same file again: applied 0, skipped 3);
                     AC-8 checked (ADMIN only on GET, unknown 404)
Files created/changed: sanctions-service application/SanctionIntakeRuns, SanctionIntake, SanctionIntakeLauncher;
                     infrastructure/JdbcSanctionIntakeRuns, SanctionIntakeJobConfig, MultipartSupport;
                     api/SanctionIntakeController; db/migration/sanctions/V2__spring_batch.sql
Checks run:        sanctions-service:test 21/21; bootJar; live checks above
Not verified:      automated tests (Part D); AC-6 5xx case live
Questions for you: none
```

Lane 3 tests (2026-11-19)
```
Item:              MAM-55 Run the legacy SANCIN sanction intake as a batch job (test-writer, Lane 3). The code was built
                   without tests, so there was no red phase: the tests were written against the built code and all pass.
Files (all under modern/services/sanctions-service/src/test; 20 files, hashes in
                   modern/docs/reports/stories/MAM-55.tests.sha256):
                   resources/features/MAM-55.feature; java/gov/medicaid/pem/sanctions/: IntegrationTest (base: Postgres,
                   Kafka, WireMock as provider-service; waits for background jobs after each test), IntakeApi, SancinFile
                   (synthetic 80-byte records), ContractFile (checks answers against contract 1.0.1), PlatformChecksTest
                   (ArchUnit layering + SANCIN layout), acceptance/IntakeSteps, application/SanctionIntakeRecordsTest,
                   SanctionIntakeTest, RecordSanctionRulesTest, ListSanctionsTest, api/SanctionIntakeApiTest,
                   SanctionIntakeRolesTest, SanctionIntakeContractTest, SanctionIntakeDatasetsTest (the MAM-44 files
                   testdata/fixed-width/sancin-baseline.dat and negative/sancin-malformed.dat),
                   SanctionIntakeControllerTest, SanctionListApiTest, infrastructure/JdbcSanctionIntakeRunsTest,
                   SanctionIntakeJobTest, MultipartSupportTest. The provider-service steps of MAM-52 are reused.
Counts:            266 new tests = 23 Cucumber scenarios (MAM-55.feature: AC-1 3, AC-2 3, AC-3 2, AC-4 1, AC-5 2, AC-6 3,
                   AC-7 1, AC-8 8) + 129 unit (no Spring context) + 103 integration (Testcontainers Postgres and Kafka,
                   WireMock) + 11 contract. The module now has 287 tests (the 21 of MAM-52 are untouched): 287 pass,
                   0 fail, 0 skipped. 243 of the 266 are JUnit tests named "[MAM-55 AC-n]" (198) or "[MAM-52 AC-n]" (45:
                   field limits of RecordSanction, ListSanctions, the list endpoint that shows the intake's history).
Commands / results: ./gradlew --no-daemon :services:sanctions-service:check --continue (run 22:07 and 22:31) -> BUILD FAILED
                   only because spotlessJavaCheck fails on 4 PRODUCTION files (defect 1). Everything else passed:
                   test 287/287; jacocoTestCoverageVerification passed; pitest passed.
                   JaCoCo line coverage 97.9 % (412/421; the gate's classes, *Application excluded; 96.9 % = 413/426 with
                   SanctionsServiceApplication). Not covered: SanctionIntake.java:117-124 (unreachable, defect 2),
                   SanctionIntakeJobConfig.java:55 (file of the run gone), :104 (failure without an exception) and
                   :114-115 (destroy), SanctionController.java:120 (not a JWT login, MAM-52 code), JdbcSanctionHistory.java:65.
                   PIT 97 % (72 of 74 mutants killed, gate 70 %): the two others are NO_COVERAGE in the unreachable block
                   SanctionIntake.java:120 and :122. Reports (module build folder): reports/jacoco/test/html/index.html and
                   jacocoTestReport.xml, reports/pitest/index.html and mutations.xml, test-results/test/*.xml.
                   Test sources were formatted with Spotless through an init script that limits it to src/test; src/main was
                   not touched (no file under src/main changed). ArchUnit (PemArchitecture) passes.
                   test_protection: record MAM-55 (20 files); check OK for MAM-55, MAM-52, MAM-42 and MAM-44.
AC -> test:        AC-1 feature @AC-1 (3: start with a date, no date 400, no file 400, each with no run created);
                   SanctionIntakeApiTest (202 answer, six count names, same date on GET, explicit date, 12 refused inputs
                   incl. 31/03/2026, 2026-02-30, blank), SanctionIntakeControllerTest (answer, launch arguments, refused
                   request starts nothing, unreadable upload 400), SanctionIntakeContractTest (multipart request, BatchRun,
                   Problem 400, documented statuses), SanctionIntakeJobTest (job PRVSAN01, parameters runId +
                   businessDate + recordedBy, step "apply"), MultipartSupportTest (Tomcat parses the parts),
                   SanctionIntakeTest.open, JdbcSanctionIntakeRunsTest.created
                   AC-2 feature @AC-2 outline (LF, CRLF and no line ends, reason and date read back from the history);
                   SanctionIntakeRecordsTest (record splitting: LF, CRLF, 80-byte blocks, last line, empty file, bytes
                   kept); SanctionIntakeTest (bytes 1-10, 11, 12-51 and 52-59, 40-byte reason without trailing spaces,
                   blank reason, 5 dates incl. 20240229 and 19991231, file order, counts after every record, empty file,
                   blocks); PlatformChecksTest (SANCIN layout and fixture positions); SanctionIntakeDatasetsTest (baseline
                   file fields); SanctionIntakeApiTest (empty file, read = number of records)
                   AC-3 feature @AC-3 outline (starter ADMIN and SYSTEM: statuses T, S and A through POST status-changes
                   with the SYSTEM token, history sourceFeed SANCIN and recordedBy, one event per record, event sanctionId
                   = history row, event on the Kafka topic); SanctionIntakeTest (TERMINATE, SUSPEND, REINSTATE, history
                   rows, events, same sanctionId, link to the run, no status check, D-027); SanctionIntakeApiTest and
                   SanctionIntakeControllerTest (recordedBy: preferred_username, principal name); SanctionIntakeDatasetsTest
                   (reinstating a PENDING provider); RecordSanctionRulesTest (order: provider-service first, history and
                   event in one transaction)
                   AC-4 feature @AC-4 (the five records: read 5, applied 2, notFound 1, rejected 2, COMPLETED, endedAt,
                   no error, history and events only for 1003000111 and 1003000222, provider 1003000333 unchanged,
                   'PRVSAN01: NPI NOT FOUND 1003000999' in the service log); SanctionIntakeTest (the same five records with
                   the counts after each record, 7 action codes, 8 invalid dates, 4 invalid NPIs, rejected-record log
                   lines, summary line); SanctionIntakeDatasetsTest (baseline file read 6, applied 4, notFound 1,
                   rejected 1); JdbcSanctionIntakeRunsTest (counts, complete); SanctionIntakeContractTest (COMPLETED run)
                   AC-5 feature @AC-5 (4 records: applied 4, terminated 2, 'PRVSAN01: PROVIDERS TERMINATED=00002'; and
                   S only: 00000); SanctionIntakeTest (00002, 00000, 00012, skipped T not counted, one line);
                   JdbcSanctionIntakeRunsTest (terminated = T of this run only)
                   AC-6 feature @AC-6 (record of 60 bytes: FAILED naming record 2; provider-service server error and
                   unreachable: FAILED naming record 2 and its NPI; record 1 kept with history and event, applied 1,
                   record 3 not processed, no event for the failing sanction); SanctionIntakeTest (60 bytes and 5 other
                   lengths, first record, provider failure, history failure, failure on the first record, FAILED mark and
                   log line); SanctionIntakeJobTest (job and step FAILED, launcher refuses a second start);
                   SanctionIntakeControllerTest (launch failure leaves a FAILED run); SanctionIntakeDatasetsTest (malformed
                   file: record 2 rejected, record 3 of 79 bytes fails the run); JdbcSanctionIntakeRunsTest (fail guards)
                   AC-7 feature @AC-7 (same file, same date: read 2, applied 0, skipped 2, no new history, no new event, no
                   second call to provider-service, statuses T and S kept); SanctionIntakeTest (skip, skipped count, key,
                   partial re-run, different record not skipped); JdbcSanctionIntakeRunsTest (alreadyApplied for the
                   same date, same run, other date, other NPI/action/reason/date, no reason, a FAILED run's records);
                   SanctionIntakeJobTest (re-run is its own job execution, corrected file after a FAILED run, other date
                   applies again); SanctionIntakeDatasetsTest (baseline file twice)
                   AC-8 feature @AC-8 (ADMIN and SYSTEM 202; ANALYST, SUPERVISOR, PROVIDER 403 problem+json with no run;
                   no token 401; GET: ADMIN 200, SYSTEM, ANALYST, SUPERVISOR, PROVIDER 403, no token 401; unknown run id
                   404 problem+json); SanctionIntakeRolesTest (24: the AC as written, 403 and 404 are problem+json, roles
                   before lookup, and every role against the contract's x-roles); SanctionIntakeContractTest (403 and
                   404 are Problem, 401 documented)
Defects found:     (1) GATE: spotlessJavaCheck fails on production code written without a Spotless run: SanctionIntake.java
                   (:18-27 class Javadoc, :53 Javadoc line over 100 columns, :106 and :133 lines over 100 columns),
                   SanctionIntakeRuns.java (:43-44 alreadyApplied parameters), JdbcSanctionIntakeRuns.java (:54, :106),
                   SanctionIntakeJobConfig.java (:55). Not fixed here (src/main is not the test-writer's). Fix for the
                   java-developer: ./gradlew :services:sanctions-service:spotlessApply, then re-run check.
                   (2) LOW, dead code: SanctionIntake.java:117-124, the try/catch around r.getDate("SAN-EFF-DATE") can never
                   run, because FixedWidthCodec.read (:102-112) already rejects an impossible or non-numeric date and the
                   record is counted rejected there. Behaviour is right (AC-4); the block is unreachable (JaCoCo lines
                   119-123, PIT NO_COVERAGE at :120 and :122). Suggest removing it.
                   No test fails: no AC was found unmet by the code.
Findings:          (3) Contract: the Problem schema has no "instance" member, but Spring adds it to problem answers;
                   ContractFile.problemViolations tolerates it (same gap as in directory-service, Lane 3). (4) The run
                   goes on in a background thread: the 202 answer shows RUNNING or COMPLETED, and the job repository's
                   status is written a moment after the run row (the test base class and the step "the run has ended"
                   wait for both). (5) The AC-5 log line is written after the run is COMPLETED, so a reader that sees
                   COMPLETED may not see the line yet; the scenarios wait for it. (6) Two Spring contexts run in the test
                   JVM (Cucumber's and the JUnit base class), as in directory-service. (7) Gaps of MAM-52 found while
                   reading the code are covered here under "[MAM-52 AC-n]": RecordSanction field limits (reason 60,
                   sourceFeed 20, NPI, date), ListSanctions, GET /api/v1/sanctions paging and its 400s.
Not verified:      the log lines are checked as the formatted message of the log events (Logback), not in the ECS JSON
                   console output; the multipart upload is checked through MockMvc and on Tomcat's own factory
                   (MultipartSupportTest), not by a real HTTP request to a running service (the live check is in the report
                   above); the SanctionApplied event is checked on the Kafka topic for one NPI only; the 5xx case is now
                   tested (provider-service WireMock) but not against a live provider-service.
```

## Review fix (2026-11-21, review-fix step of the lead's plan)

Found by the enrollment lane (same upload pattern) and confirmed by the lead on the running stack on 2026-11-21 at 02:27:
a multipart request that cannot be read was answered 500 with the default error body, not a 400 problem (question Q-143).
The story and epic MAM-8 were reopened for it.

- Tests first (test-writer, 05:38 to 05:57): `SanctionIntakeUploadTest`, 44 tests against the real web server: four kinds of
  malformed body (no boundary, a file part that is cut off, cut-off part headers, a cut-off businessDate part), each with nine
  checks (400, problem+json also for a JSON client, a detail that says the upload could not be read, no SQL or class name,
  the correlation id, no run started, nothing applied), and 8 guards (a missing part stays the controller's own 400, a file
  over the limit stays 413, the service goes on). 36 were red before the fix, all with "expected 400 but was 500".
- Fix (java-developer, 05:57 to 06:50): `api/RequestProblems.java`, the service's own advice: a multipart error is a 400
  problem with the detail "The upload could not be read: send the file and the businessDate as a complete
  multipart/form-data message."; a file over the limit keeps 413, now as a problem too. The shared kit is untouched.
- Whole check of the module (06:44 to 06:48): 370 tests, 0 failures, 0 skipped, JaCoCo 98.4 % of lines, PIT 86 % (75 of 87).
  A first run (06:19 to 06:26) had failed 33 Cucumber scenarios because the Kafka test container did not start in time on
  the loaded machine; the second run with the same sources passed. Only that one green full run exists since.
- Second review (07:00): APPROVE, no MUST-FIX. Its notes are in `modern/docs/reports/review-should-fix.md` (among them: the
  wording of the detail differs from enrollment-service; the role check runs after the body is parsed, so a caller without the
  role gets 400 for a malformed body, as in enrollment-service; a wrong content type gets Spring's 415 in the default format).
