```
Phase / item:      MAM-45 Monthly revalidation sweep: open a case for every ACTIVE provider that is due, with the legacy REVALQ file
Outcome:           BUILT (build-phase: tests in Part D), batch A1
What was done:     - Spec check: Jira MAM-45 description = modern/specs/MAM-45.md (same 8 ACs)
                   - Built in revalidation-service: read model tracked_provider from ProviderStatusChanged (status +
                     revalidationDueDate); POST /api/v1/batch/revalidation-sweeps (ADMIN, SYSTEM; businessDate required,
                     400 otherwise) -> 202 BatchRun RUNNING, run executed in the background: SweepRule (only ACTIVE,
                     due <= business date, 4-digit dates; no due date -> counted noDueDate, Q-049 A), one DUE case per
                     provider without an open case + RevalidationDue in one transaction, then COMPLETED with counts
                     queued/noDueDate and the REVALQ file, or FAILED with the reason; GET .../{runId} (ADMIN, Q-046 A);
                     GET .../{runId}/queue-file (ADMIN, text/plain, 80-byte records via the MAM-42 codec layout REVALQ,
                     ascending NPI, no line ends; 404 unknown run); GET /api/v1/revalidations (ANALYST, SUPERVISOR);
                     Flyway V2 adds sweep_run.no_due_date_count
                   - AC status: AC-1 built, checked live (ADMIN 202 RUNNING, ANALYST/PROVIDER 403, no token 401, {} 400);
                     AC-2 built, checked live (2032-01-01 sweep opened 1 case with the provider's own due date);
                     AC-3 built (rule NOT_DUE/NOT_ACTIVE; a moved date is followed from ProviderStatusChanged), not
                     checked live (no such tracked provider in the stack); AC-4 built (RevalidationDue caseId/npi/dueDate,
                     key NPI), seen live indirectly (the case moved to IN_SCREENING when its event was consumed);
                     AC-5 built (open case = no new case; unique index), not checked live; AC-6 built (counts.noDueDate),
                     live run showed noDueDate 0; AC-7 built, checked live (1 record: 1003000111|20311119|
                     RESCREEN-REQUIRED + 42 spaces; ANALYST 403; unknown run 404; a run with no case gives an empty
                     body); AC-8 built (COMPLETED with counts checked live; FAILED path not exercised live)
Files created/changed: modern/services/revalidation-service/src/main/java/gov/medicaid/pem/revalidation/ domain/
                     (TrackedProvider, SweepDecision, SweepRule, CaseStatus, RevalidationCase), application/ (RunSweep,
                     SweepRun, SweepRuns, CaseStore, CasePage, QueueFile, EventOutbox, RevalidationDueData, ListCases),
                     infrastructure/ (JdbcRevalidationStore, ProviderEventsListener, RevalidationAdapters),
                     adapter/legacy/RevalqFile, api/ (SweepController, RevalidationController);
                     resources/db/migration/revalidation/V2__sweep_counts.sql
                   test drafts for Part D (not run): modern/docs/reports/stories/MAM-45/part-d-drafts/
Checks run:        ./gradlew --no-daemon :services:revalidation-service:compileJava (+ spotlessApply) -> OK (the service
                   has no tests yet); local-runtime bootJar; pem-apps restarted, healthy; live checks above
Not verified:      no automated tests yet (Part D); the run executes on an executor, not as a Spring Batch job as ADR-004
                   says (no service has batch tables yet): needs a decision or a later refactor; the monthly cron is out
                   of scope; FAILED path and AC-3/AC-5 not seen live
Questions for you: 1. ADR-004 says Spring Batch for the sweep; built as an executor-run job with its own run record
                      (counts, failure reason). Options: A) keep (recommended for now) and move to Spring Batch when the
                      batch tables are introduced; B) switch now
Update 2026-11-19 (Q-102 answer A, user): the sweep now runs as a Spring Batch job (ADR-004). RunSweep records the
                   RUNNING run and starts job 'revalidation-sweep' (parameters runId, businessDate) on one background thread
                   (SpringBatchSweepLauncher: runs never overlap, as before); its step 'sweep' (SweepJobConfig) calls
                   ExecuteSweep, which evaluates every tracked provider and records the cases, the RevalidationDue events,
                   the counts and the REVALQ file in the step's one transaction; Spring Batch keeps read/write/filter
                   counts; a job that does not complete marks the run FAILED with the first failure's message (as before).
                   Flyway V3__spring_batch.sql adds the Spring Batch tables to revalidation_db; the sweepExecutor bean is
                   gone. API, roles, status codes, REVALQ bytes, counts and events unchanged. Checked live (J6 path by
                   hand): seed; sweep 2026-03-31 -> 202 RUNNING, COMPLETED queued 4 noDueDate 0; REVALQ 320 bytes, 4
                   records in NPI order (1005923986 20260331, 1005947811 20260215, 1005951276 20260115, 1005989276
                   20251201, RESCREEN-REQUIRED + 42 spaces); the 4 cases went DUE -> IN_SCREENING -> COMPLETED within
                   about 2 s (IN_SCREENING was too short to be seen by the first poll); the 4 due dates are now 2031-11-19;
                   ANALYST/PROVIDER 403, no token 401, {} 400, unknown run 404, SYSTEM GET 403; log shows SimpleJob
                   revalidation-sweep launched/completed and step sweep. Reseeded afterwards. The Part D draft
                   RunSweepTest.java (part-d-drafts) targets the old executor constructor and is obsolete.
```
