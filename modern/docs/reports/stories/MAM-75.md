```
Phase / item:      MAM-75 T-MSIS extract run: archive every file, report counts, publish ExtractProduced
Outcome:           BUILT (build-phase: tests in Part D), batch A4; AC-5 built after Q-061 answer B
What was done:     - Spec check: modern/specs/MAM-75.md (8 ACs); Jira not re-read in this batch
                   - POST /api/v1/batch/tmsis-extracts (ADMIN, SYSTEM): businessDate required (400 problem+json when it
                     is missing or not a date); records a RUNNING extract_run and launches the PRVTMS01 job on its own
                     thread (TaskExecutorJobLauncher), answers 202 with BatchRun {runId, job PRVTMS01, businessDate,
                     status, counts.rows, startedAt}
                   - GET /api/v1/batch/tmsis-extracts (ADMIN): runs newest first, paged; counts.rows and
                     counts.archiveGeneration (Q-060 default A); error and endedAt
                   - GET /api/v1/batch/tmsis-extracts/{runId}/file (ADMIN): the archived file of a COMPLETED run, else 404
                   - Archive: step "archive" (ArchiveExtract) writes the file, adds the next generation in
                     extract_archive under a table lock (never overwritten or deleted, Q-012) and completes the run, in
                     one transaction; a job that does not complete marks its run FAILED with the reason (job listener),
                     so a failed run has no file and no generation (D-024)
                   - Schedule: TmsisSchedule, cron pem.tmsis.cron (default "0 0 22 L * *", month end, UTC;
                     PEM_TMSIS_CRON overrides, "-" turns it off); the business date is the injected Clock's date
                   - AC status: AC-1 built, checked live (202 RUNNING with job PRVTMS01; the list then shows the run);
                     AC-2 built, checked live for 8 rows (counts.rows = records in the file, endedAt set); the 0-row case
                     not checked live; AC-3 built, checked live (businessDate 2026-03-31 -> 26090, run on 2026-11-19);
                     AC-4 built, checked live (generations 1..5 for five runs; the first 2026-03-31 file is still
                     downloadable and byte-identical to the later 2026-03-31 run's); AC-5 built after Q-061 answer B (user,
                     2026-11-19): ArchiveExtract puts ExtractProduced {runId, businessDate, periodYyddd, rowCount} in the
                     outbox in the same transaction that archives the file and COMPLETEs the run, subject and Kafka key =
                     the run id; events.yaml 1.1.0 (ExtractProduced subject/key = run id, Envelope.subject NPI or uuid,
                     changelog in info.description); tmsis Flyway V3 widens outbox_event.subject to VARCHAR(36) (no
                     other service's outbox changed); the service-kit envelope parser, ProcessedEvents and outbox relay
                     have no NPI-only check (only their javadoc says NPI); notification-service handles ExtractProduced
                     explicitly (logs the run id, sends nothing: EXTRACT_READY listed but not sent, Q-A9); checked live:
                     one ExtractProduced for run 17ea6843-..., consumed by notification-service ("for extract run
                     17ea6843-...: no notice"), no ERROR or DLT in notification-service;
                     AC-6 built, not checked live (provider-service cannot be made to fail); AC-7 built, not checked live
                     (month-end cron); AC-8 built, checked live (ADMIN and SYSTEM 202; SYSTEM 403 on list and file;
                     ANALYST, SUPERVISOR, PROVIDER 403 on all three; no token 401; no businessDate 400
                     application/problem+json; unknown run file 404)
Files created/changed: see MAM-71 (same service, same build); plus tmsis application/EventOutbox, ExtractProducedData,
                     ArchiveExtract (publishes), infrastructure/TmsisAdapters, db/migration/tmsis/V3__outbox_subject_run_id.sql;
                     modern/contracts/asyncapi/events.yaml (1.1.0); modern/docs/architecture/data-model/tmsis-service.sql
                     (subject column); notification-service infrastructure/NoticeEventsListener (ExtractProduced case)
Checks run:        compile; no existing tests in tmsis-service; notification-service:test 64/64;
                   tools/checks/check_phase3.py 7/7 (contracts); bootJar; pem-apps healthy; live checks above
Not verified:      automated tests (Part D); AC-2 zero-row run, AC-6, AC-7 live. The live runs stay in tmsis_db (the
                   seed has no tmsis_db reset), so the archive now holds generations 1-5
Questions for you: none (Q-061 answered B)
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
