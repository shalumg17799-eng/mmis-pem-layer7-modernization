```
Phase / item:      MAM-32 Run screening as a batch job and produce the legacy SCRNOUT file
Outcome:           BUILT (build-phase: tests in Part D), batch A5; the job itself not run live (see question)
What was done:     - Spring Batch job PRVSCR01 (screening_db Flyway V2 Spring Batch tables, approved): POST
                     /api/v1/batch/screening-runs (ADMIN, SYSTEM) -> 202 RUNNING, job on one background thread; step
                     "screen" reads every provider of GET /api/v1/providers page by page (200) until the last page and
                     screens (reason INITIAL, ScreenProvider, each screening committing on its own) a provider whose screen
                     flag is U and who has no INITIAL screening yet (AC-7), linking it to the run; step "results" writes
                     SCRNOUT (4 rows of 67 bytes per screened provider, NPI order, LICBOARD/OIGLEIE/SAMGOV/SSADMF, verdict
                     date = business date, detail cut to 40) and completes the run (read, screened, skipped = read -
                     screened, failed = screened providers with a result other than P); provider-service 5xx ends it
                     FAILED, no endless retry. GET .../{runId} (ADMIN, contract 1.1.0 per the user), GET
                     .../{runId}/results-file (ADMIN)
                   - AC status: AC-1 checked live for the refusals (ANALYST/SUPERVISOR/PROVIDER 403, no token 401, {} 400);
                     the ADMIN/SYSTEM start not run live; AC-2..AC-7 built, not run live; AC-8 checked in part (results
                     file SYSTEM/ANALYST 403, no token 401, unknown 404)
Files created/changed: screening-service application/ScreeningRuns, ProviderListing, ScrnoutFile, RunScreeningBatch,
                     StartScreeningBatch, ScreeningBatchLauncher; adapter/legacy/ScrnoutWriter; infrastructure/
                     JdbcScreeningRuns, ScreeningBatchJobConfig, ProviderServiceClient (provider pages);
                     api/ScreeningRunController; db/migration/screening/V2__spring_batch.sql
Checks run:        screening-service:test 49/49; bootJar; pem-apps healthy; live refusals above
Not verified:      the job live: on the current data it would screen the seeded DELTA and HHA (flag U, no INITIAL
                   screening) and change their screen flags; not done without a seed
Questions for you: 1. Run MAM-32 live now (changes DELTA and HHA in the shared data), or right after your next seed?
                      Recommended: right after your next seed
```
