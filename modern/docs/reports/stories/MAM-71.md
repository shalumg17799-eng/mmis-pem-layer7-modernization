```
Phase / item:      MAM-71 Monthly T-MSIS provider extract in the legacy 120-byte layout (YYDDD period)
Outcome:           BUILT (build-phase: tests in Part D), batch A4
What was done:     - Spec check: modern/specs/MAM-71.md (6 ACs); Jira not re-read in this batch
                   - tmsis-service built from its skeleton, as a Spring Batch job (ADR-004) named PRVTMS01 with job
                     parameters runId and businessDate: step "extract" reads provider-service GET
                     /api/v1/providers?status=A page after page of 200 with the SYSTEM token (ProviderPagesReader,
                     ProviderServiceClient), keeps only status A (TmsisRecord.of, R-PRVTMS01-ADD-04) and stores the
                     records in extract_row (chunks of 200); step "archive" writes the file in ascending NPI order and
                     archives it (MAM-75)
                   - Layout through the MAM-42 codec (adapter/legacy/TmsisOutFile, layout TMSISOUT): bytes 1-5 PRV01,
                     6-14 Medicaid ID, 15-24 NPI, 25 'A', 26-30 YYDDD period of the business date
                     (domain/ReportingPeriod, D-015/D-020), 31-120 spaces; no header or trailer; 120-byte records side by
                     side with no line ends (as REVALQ)
                   - Spring Batch tables: new Flyway V2__spring_batch.sql in tmsis_db (the spring-batch-core 5.2.6
                     Postgres schema, unchanged); initialize-schema stays never
                   - AC status: AC-1 built, checked live on the seed (8 ACTIVE providers -> exactly 8 records in
                     ascending NPI order, no P/D/S/T provider); AC-2 built, checked live (every record 120 bytes, PRV01,
                     Medicaid ID and NPI unchanged, 'A', 90 trailing spaces); AC-3 built, checked live (2026-03-31 ->
                     26090 on every record, run started on 2026-11-19); AC-4 built, checked live (2026-01-05 -> 26005,
                     2026-03-30 -> 26089, 2028-12-31 -> 28366); AC-5 built (page loop until the last page), not checked
                     live with 450 providers (the seed has 16); AC-6 built (only the provider API is used; tmsis has no
                     access to provider_db)
Files created/changed: modern/services/tmsis-service/src/main/java/gov/medicaid/pem/tmsis/ domain/ReportingPeriod,
                     TmsisRecord; application/ActiveProviders, ProviderRow, ExtractRun, ExtractRuns, TmsisFile,
                     ExtractJobLauncher, StartTmsisExtract, ArchiveExtract; adapter/legacy/TmsisOutFile;
                     infrastructure/ProviderServiceClient, ProviderPagesReader, JdbcExtractRuns, TmsisExtractJobConfig,
                     SpringBatchExtractLauncher, TmsisSchedule; api/TmsisExtractController, TmsisProblems;
                     resources/db/migration/tmsis/V2__spring_batch.sql; resources/tmsis-service.yml (provider-url, cron)
Checks run:        compile; tmsis-service has no existing tests; bootJar; pem-apps restarted (the first deploy failed
                   to start tmsis-service: a bean-name clash between the configuration class and the job bean; fixed by
                   renaming the class to TmsisExtractJobConfig, redeployed; all ten applications started, healthy);
                   live checks above
Not verified:      automated tests (Part D); AC-5 with 450 providers; the Spring Batch tables were not inspected directly
                   (no database access from here; the runs and counts were checked through the API)
Questions for you: none
```
