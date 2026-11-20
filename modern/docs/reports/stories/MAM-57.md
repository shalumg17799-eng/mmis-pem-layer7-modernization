```
Phase / item:      MAM-57 Report the result of an intake run: exclusive counts, completion, failures and safe re-runs
Outcome:           BUILT (build-phase: tests in Part D), batch A5 (with MAM-56)
What was done:     - Counts kept after every record in intake_run (each record exactly once): written = VALIDATED; skipped =
                     a duplicate NPI rejected with E103 only, or a record an earlier run of the same business date already
                     processed (no application, no row, no event); errored = any other rejection, an unreadable record or
                     a record that could not be stored. Rejections alone give COMPLETED (legacy RC 4); an unreadable record
                     (length not 200) or a storage failure ends the run FAILED naming the record (and NPI); later records
                     are not processed, earlier results are kept
                   - AC status (live, synthetic NPIs): AC-1 built (fresh counts per run); AC-2 checked (read 7 = written 3
                     + errored 3 + skipped 1; the record with two failed checks counted once); AC-3 checked (second
                     1007630431 REJECTED with exactly E103, one APPERROR row, counted skipped, one VALIDATED application);
                     AC-4 checked (COMPLETED, error null, errored 3); AC-5 checked (150-byte record 2: FAILED "record 2 is
                     150 bytes, not 200", read 2, errored 1, record 3 not processed); AC-6 built (storage failure path),
                     not live (needs fault injection); AC-7 checked (ADMIN only, unknown 404); AC-8 checked (same file and
                     date again: read 7, written 0, skipped 7, empty error file)
Files created/changed: shared with MAM-56 (the same intake job; this story is its run report): enrollment-service
                   application/IntakeRuns, IntakeLauncher, IntakeLoad; infrastructure/JdbcIntakeRuns, IntakeJobConfig;
                   api/IntakeRunController; db/migration/enrollment/V3__spring_batch.sql
Checks run:        see MAM-56
Not verified:      automated tests (Part D); AC-6 live
Questions for you: none
```
