```
Phase / item:      MAM-51 Seed data for the local stack: screening results, sanction history and read models in each service
Outcome:           BUILT except AC-1 (build-phase: tests in Part D), batch A2; AC-5 conflict resolved by Q-103 answer A
What was done:     - Spec check: modern/specs/MAM-51.md read (5 ACs); Jira not re-read in this batch
                   - testdata/generate_seed.py extended (same seed 20260115, deterministic uuid5 ids, fixed times); the
                     provider seed it writes is byte-identical. New generated files, each one transaction that restores
                     exactly the seed state (TRUNCATE then INSERT; processed_event kept):
                     seed/directory_db/02-directory.sql (read model of all 16 seeded providers, display names per D-028;
                     the public list shows status A only), seed/screening_db/02-screenings.sql (one COMPLETED INITIAL
                     screening with 4 results for every provider with flag P or F; ECHO fails LICBOARD E202, LAB fails
                     OIGLEIE E201), seed/sanctions_db/01-sanctions.sql (T for GOLF and HOSPICE 02, S for FOXTROT and
                     THERAPY GROUP; ALPHA none; also empties sanction_intake_run and the outbox), seed/revalidation_db/
                     01-revalidation.sql (tracked_provider = status and due date of every seeded provider; no case, no
                     run; replaces stale read-model rows such as 1003000111)
                   - AC status: AC-1 NOT BUILT: needs the list of all providers (GET /api/v1/providers without a filter,
                     MAM-37, batch A3) and the file fixed-width/provider-master-seed.dat (MAM-44, not built); AC-2 built,
                     checked live (ALPHA 1 PASS screening, ECHO FAIL with LICBOARD F E202, LAB FAIL, DELTA none); AC-3
                     built, checked live (the 4 T/S providers have 1 matching row with reason and date; ALPHA total 0);
                     AC-4 built, checked live (no token: exactly the 8 ACTIVE NPIs, total 8; name=ALPHA lists 1005940865
                     as TESTPROV ALPHA); AC-5 built (read model seeded, not replayed), checked live: the 2026-03-31 sweep
                     queued exactly the 4 seeded ACTIVE providers due on or before that date (BRAVO, CHARLIE, HOSPICE 01,
                     AMBULANCE 01; REVALQ 4 records in NPI order) and none other; BUT see the conflict
                   - The live check ran the whole chain: RevalidationDue -> REVALIDATION screening PASS -> cases
                     COMPLETED (MAM-48 AC-4) and provider due dates moved to 2031-11-19 (MAM-30). ./pem.sh seed was run
                     again afterwards: the stack is back at the seed state (BRAVO due 2026-01-15, no case, ALPHA no
                     sanction)
Files created/changed: modern/testdata/generate_seed.py; modern/testdata/seed/directory_db/02-directory.sql,
                     screening_db/02-screenings.sql, sanctions_db/01-sanctions.sql, revalidation_db/01-revalidation.sql
Checks run:        python modern/testdata/generate_seed.py (provider seed unchanged: cmp OK); ./pem.sh seed (10 files);
                   live checks above; no service code changed, so no Gradle run, no jar, no redeploy
Not verified:      automated tests (Part D); AC-1 (not built); the README table of testdata is not yet updated
Questions for you: none. Q-103 answered A (user, 2026-11-19): AC-5 reads "exactly one case per seeded due ACTIVE
                   provider, in any status, and none for others"; the live sweep of 2026-03-31 met it (one case each for
                   BRAVO, CHARLIE, HOSPICE 01 and AMBULANCE 01, IN_SCREENING then COMPLETED; none for any other provider)
```

AC-1 checked (lead, 2026-11-19 21:05): two make seed runs, after each GET /api/v1/providers (ANALYST): total 16 = 16 records of testdata/fixed-width/provider-master-seed.dat (MAM-44), and the listed NPIs are exactly those of the file, both times. AC-2..AC-4 live-checked in batch A2, AC-5 per Q-103 A (J6 PASSED 3/3 today). All ACs met; automated tests remain a Part D item.
