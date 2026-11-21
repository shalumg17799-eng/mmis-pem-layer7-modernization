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

## Review fix (2026-11-21, review-fix step of the lead's plan)

The code review of 2026-11-20 rated two points MUST-FIX for tmsis-service; both are fixed, tests first, in two rounds.

1. **Paging (MAM-71 AC-5, the user's decision Q-132).** The extract read the providers by page number and never noticed a
   change between two pages. It now walks by key (`afterNpi`, provider-service 1.2.0): the first request has no key, each
   next one is after the last NPI of the answer before. The walk ends only on an answer shorter than 200 (the empty one
   included); the total never ends it. The second review found that the first version of the fix still stopped when the
   rows read reached the total, so a termination before the key and an approval after it could drop the last provider of
   a COMPLETED run; that stop is gone (25 tests red before, among them the reviewer's case). Safety net as the user asked:
   a total that differs from the first answer, an answer out of order or one that ignores the key ends the run FAILED with
   a short reason ("provider data changed during the extract; run it again"); see question Q-156 for what that means.
2. **Interrupted runs (MAM-75 AC-6).** A run that was RUNNING when the service stopped stayed RUNNING for ever. At start-up
   every RUNNING run is now ended as FAILED with "interrupted: tmsis-service stopped during the run", and its job execution
   is ended too (9 tests). It assumes one instance of the service per database (stated in the code).
- Recorded tests that changed are listed with their reasons in `MAM-71/red-report.md` (both sections), `MAM-75/red-report.md`
  and `MAM-78/red-report.md`.
- Whole check of the module (08:07 to 08:13): 585 tests, 0 failures, 0 skipped, JaCoCo 98.34 % of lines, PIT 100 % (27 of 27;
  PIT covers the domain and application packages only, so the two infrastructure classes of these fixes have no mutation
  score).
- Reviews: second review MUST-FIX (1 item, above), third review APPROVE. Notes in `modern/docs/reports/review-should-fix.md`.
- Not verified: against a real provider-service in a test (a fake pinned to contract 1.2.0 was used); a restart in the
  middle of a run on the running stack.
