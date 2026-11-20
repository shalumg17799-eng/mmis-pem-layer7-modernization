```
Phase / item:      MAM-93 Console: run the batch jobs and download their legacy files (admin)
Outcome:           DONE (fast-mode, gates postponed); AC-1..AC-8 built, tests 57/57 green
What was done:     - Red: batch-jobs.test.tsx (50), admin-jobs.spec.ts (7)
                   - Green: /console/admin/jobs (ADMIN only) with the Job list (Enrolment intake (APPINPUT), Screening run, Revalidation
                     sweep, Sanction intake (SANCIN), Directory extract (DIROUT), T-MSIS extract), an empty "Business date (MM/DD/YYYY)",
                     a required file input and an optional Batch id (8) for the two intakes, field errors and a linked summary; the JSON
                     jobs POST {businessDate: 2026-03-31} to /api/v1/batch/{revalidation-sweeps, screening-runs, directory-extracts,
                     tmsis-extracts}; the intakes POST multipart (file, businessDate, batchId) and are followed with GET {runId} until
                     Completed or Failed; the table "Recent runs" (Job, Business date, Status in words, Counts, Error, File) holds the
                     runs started here plus the T-MSIS runs of GET /api/v1/batch/tmsis-extracts; a failed run shows its error;
                     400 / 422 show the detail in a summary, 403 "You are not allowed to run jobs"; Download (Completed runs of five jobs)
                     fetches the legacy file with the token and saves the bytes as received (Blob, SCRNOUT.D20260331 ...);
                     404 "The file is not available (yet)". The client gained postForm (multipart) and download (Blob).
Files created/changed: web-ui/src/features/console/admin/jobs/{JobsPage.tsx,jobs.ts}, src/shared/api/client.ts,
                   src/shared/format/dates.ts, src/features/console/sanction/sanctionForm.ts (the date helper moved), src/app/screens.tsx;
                   tests/admin/batch-jobs.test.tsx, tests/support/batch.ts, e2e/admin-jobs.spec.ts
Checks run:        vitest batch-jobs + sanction -> 85/85 (PowerShell, default pool); playwright admin-jobs + console-sanction -> 16/16
                   (axe on the empty page, an intake selected with field errors, runs, a failed run and a 422; keyboard-only run;
                   byte-for-byte download with the token; multipart intake followed to Completed); lint, tsc -b, build clean
Not verified:      against the live batch endpoints (none of the batch back ends exists yet; every answer is stubbed from the contracts);
                   the list of runs after a page reload (only T-MSIS has a list endpoint); code review
Questions for you: 1. After a reload the table holds only the T-MSIS runs. Acceptable, or should the services get a run list (contract change)?
Next step:         code-reviewer; journeys J6 and J7 when the back ends exist
```
