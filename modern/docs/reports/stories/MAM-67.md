```
Phase / item:      MAM-67 Directory extract as a batch job in the legacy DIROUT format
Outcome:           BUILT (build-phase: tests in Part D), batch A5
What was done:     - Spring Batch job PRVDIR01 (ADR-004; directory_db Flyway V2 Spring Batch tables, approved): POST
                     /api/v1/batch/directory-extracts (ADMIN, SYSTEM) records a RUNNING run and runs the job on the
                     caller's thread, so the 202 carries the finished run (COMPLETED or FAILED, AC-1/AC-7); step
                     "extract" writes every ACTIVE entry in ascending NPI order as a 100-byte DIROUT record through the
                     MAM-42 codec (Medicaid ID, NPI, type, tier, composed name D-028, 18 spaces; no line ends) and
                     completes the run in its transaction; a failure marks the run FAILED with no file. GET .../{runId}
                     (ADMIN, contract 1.1.0 per the user) and GET .../{runId}/file (ADMIN, 404 unless COMPLETED);
                     missing or non-ISO businessDate -> 400 problem+json, no run
                   - AC status: AC-1 checked live (202 COMPLETED, job PRVDIR01, counts.rows 9 = 9 records); AC-2 checked
                     live ({} and 31/03/2026 -> 400 problem+json); AC-3 checked live (only the 9 ACTIVE entries, NPI order,
                     100 bytes each; the empty-directory case not live); AC-4/AC-5 checked live (record 1:
                     TX0000111 1003000111 IN L MARIA RIVERA + spaces); AC-6 checked live (new runId, byte-identical file,
                     first file still downloadable); AC-7 built (FAILED path), not live; AC-8 checked live (ADMIN/SYSTEM
                     202, SYSTEM 403 on the file, ANALYST/SUPERVISOR/PROVIDER 403, no token 401)
Files created/changed: directory-service application/DirectoryExtracts, DirectoryFile, DirectoryExtractJobLauncher,
                     RunDirectoryExtract, StartDirectoryExtract; adapter/legacy/DiroutFile; infrastructure/
                     JdbcDirectoryExtracts, DirectoryExtractJobConfig; api/DirectoryExtractController, DirectoryProblems;
                     db/migration/directory/V2__spring_batch.sql
Checks run:        directory-service:test 29/29; bootJar; pem-apps healthy; live checks above (no seed)
Not verified:      automated tests (Part D); AC-7 and the empty-directory case live
Questions for you: none
```

Lane 3 tests (2026-11-19)
```
Item:              MAM-67 Directory extract as a batch job in the legacy DIROUT format (test-writer, Lane 3)
Files (all under modern/services/directory-service/src/test; hashes in modern/docs/reports/stories/MAM-67.tests.sha256):
                   resources/features/MAM-67.feature; java/gov/medicaid/pem/directory/: acceptance/DirectoryExtractSteps,
                   adapter/legacy/DiroutFileTest, domain/DirectoryNameCompositionTest, application/
                   RunDirectoryExtractTest, StartDirectoryExtractTest, DirectoryPageTest, api/DirectoryExtractApiTest,
                   DirectoryExtractRolesTest, DirectoryExtractContractTest, infrastructure/JdbcDirectoryExtractsTest,
                   DirectoryExtractJobTest; shared with MAM-65: DirectoryFixtures, ContractFile; own helper: Dirout
Counts:            190 tests = 14 Cucumber scenarios (8 scenarios + the AC-8 outline of 6 roles) + 80 unit + 88
                   integration (Testcontainers Postgres, Kafka for the name composition) + 8 contract; 188 pass,
                   2 fail (below)
Command / result:  ./gradlew --no-daemon :services:directory-service:check --continue -> FAILED: 5 tests fail (2 of
                   MAM-67, 3 of MAM-65) and spotlessJavaCheck fails on 4 production files; of 391 tests 386 pass.
                   jacocoTestCoverageVerification (run with -x test) passed: line coverage 99.39 % (488/491);
                   pitest passed: 97 % (33/34, equivalent boundary mutant DirectoryRules.java:22). Reports:
                   build/reports/jacoco/test/html/index.html and build/reports/pitest/index.html
AC -> test:        AC-1 feature @AC-1; DirectoryExtractApiTest (adminRuns, timesSet, rowsEqualRecords, dateEchoed,
                   statusGet, unknownRun, notAUuid); StartDirectoryExtractTest; DirectoryExtractJobTest (job PRVDIR01,
                   parameters runId + businessDate, step "extract" write count); JdbcDirectoryExtractsTest;
                   DirectoryExtractContractTest (BatchRun, media types)
                   AC-2 feature @AC-2; DirectoryExtractApiTest (businessDateRequired x8, noBody, problemNamesTheDate,
                   dateTimeIsNotADate x2); DirectoryExtractContractTest (Problem)
                   AC-3 feature @AC-3 (2 scenarios, the second is the empty directory); DirectoryExtractApiTest
                   (activeOnlyInNpiOrder, noActiveEntry for P, S, T, D, emptyDirectory, moreThanOnePage 250 entries);
                   RunDirectoryExtractTest (paging 1 to 450 entries, store calls); DiroutFileTest (order, no framing,
                   empty); DirectoryNameCompositionTest (only status A)
                   AC-4 feature @AC-4; DirectoryExtractApiTest (recordLayout, facilityLayout, noTier); DiroutFileTest
                   (every field byte range, missing and empty values, cut on the right, round trip through the
                   codec, the DIROUT layout is the documented one)
                   AC-5 feature @AC-5 (end to end: provider-service WireMock + ProviderStatusChanged events, then the
                   run); DirectoryNameCompositionTest (27 cases: padding, DBA, licence, 60-character boundary,
                   missing parts); DirectoryExtractApiTest.names; DiroutFileTest.namePadded
                   AC-6 feature @AC-6; DirectoryExtractApiTest (rerunIdentical, earlierFileKept, fileFrozenAtTheRun,
                   runsAccumulate); DirectoryExtractJobTest.rerunIsANewJob; RunDirectoryExtractTest.openTwice
                   AC-7 feature @AC-7 (a trigger in the test Postgres rejects the COMPLETED update: a real database
                   failure); DirectoryExtractJobTest (FAILED run answer, reason, no file, status GET, FAILED job
                   execution, next run works); RunDirectoryExtractTest.writerFails/failed; StartDirectoryExtractTest
                   (launcher failure, no message); JdbcDirectoryExtractsTest (fail guards, 500-character cut, 'failed')
                   AC-8 feature @AC-8 outline (ADMIN, SYSTEM, ANALYST, SUPERVISOR, PROVIDER, no token);
                   DirectoryExtractRolesTest (the AC as written, 403 creates no run, 403 is problem+json, and the roles
                   of all three operations compared with the contract's x-roles)
Failing tests (kept, not weakened):
                   DirectoryExtractApiTest.dateTimeIsNotADate x2 (businessDate 2026-03-31T00:00:00 and
                   2026-03-31T00:00:00Z): expected 400, was 202 and a COMPLETED run. DEFECT (low): Jackson's default
                   LocalDate reader takes a date-time string as a date. DirectoryExtractController.java:40
                   (record BatchRunRequest) and :71-76 (startDirectoryExtract); the contract says format date
                   (BatchRunRequest.businessDate), ADR-009 ISO dates. The two inputs the AC names (missing, and
                   31/03/2026) are rejected correctly and pass. Fix: read the field strictly as yyyy-MM-dd.
Findings:          (1) The spec's "Out of scope" still says there is no run-status call, but contract 1.1.0 and the
                   code have GET /api/v1/batch/directory-extracts/{runId} (ADMIN): its tests are labelled AC-1 and
                   AC-8; the spec needs an AC for it (backlog-planner). (2) Same Spotless production failures as
                   MAM-65 (DirectoryExtractJobConfig.java, DiroutFile.java, FhirController.java, FhirDirectory.java).
                   (3) Not tested, no AC: non-ASCII characters in a name: the file is stored as US-ASCII text
                   (JdbcDirectoryExtracts.java:44 and :81), so a Latin-1 byte would come back as '?'; ask the
                   business what the legacy file holds. (4) Hashes recorded for the 15 files; test_protection check
                   OK for MAM-67, MAM-65, MAM-61 and MAM-63.
```
