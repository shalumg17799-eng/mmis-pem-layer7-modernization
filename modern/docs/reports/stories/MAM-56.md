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
