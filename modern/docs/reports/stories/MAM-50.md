```
Phase / item:      MAM-50 (local L-S013) Seed data for the local stack (make seed): the provider seed
Outcome:           DONE for AC-2..AC-7; AC-1 NOT MET (needs the fixed-width file, MAM-42 parked by the user)
What was done:     - Red: ProviderSeedTest (6 tests, AC-2, AC-3 x2, AC-4, AC-5, AC-7: loads the seed SQL into a fresh
                     provider_db and reads every provider back through GET /api/v1/providers/{npi}) and
                     tools/checks/tests/test_seed_script.py (2 tests, AC-6, against the local stack); all failed because
                     testdata/seed did not exist; hashes recorded (MAM-50.tests.sha256)
                   - Green (attempt 1): testdata/generate_seed.py (deterministic, random.Random(20260115)) writes
                     testdata/seed/provider_db/01-providers.sql: 16 synthetic providers (7 TESTPROV individuals,
                     9 SAMPLE organisations), every status P/A/T/D/S, flag U/P/F, tier L/M/H, prefixes 332, 251, 341,
                     291, 225 + 207/193/282, NPIs 10059xxxxx with a valid check digit; TRUNCATE + INSERT restores
                     exactly the seed state; testdata/README.md lists the seeded NPIs, the journey applicants that are
                     never seeded and the licences SD10001-SD10007 the licence-board stub (MAM-47) must answer ACTIVE
                   - infra/scripts/seed.sh: one transaction per file (--single-transaction), failure names the
                     service ("seed FAILED for <svc>-service") and exits 1, SEED_DIR override for the test
Files created/changed: modern/testdata/{README.md, generate_seed.py, seed/provider_db/01-providers.sql}
                   modern/infra/scripts/seed.sh
                   tests: modern/services/provider-service/src/test/java/.../seed/ProviderSeedTest.java,
                   modern/tools/checks/tests/test_seed_script.py
Checks run:        ./gradlew :services:provider-service:check -> BUILD SUCCESSFUL, 43 tests 0 failures (6 new),
                   PIT 31/31 killed (100%), JaCoCo gate passed
                   pytest modern/tools/checks/tests/test_seed_script.py -> 2 passed (live stack)
                   test_protection.py check MAM-50 -> OK (2 files unchanged)
                   tools/checks/smoke-walking-skeleton.sh on the seeded stack -> 4/4 PASS (1005940865)
Not verified:      AC-1 (provider-master-seed.dat, MAM-42 parked); AC-3 "the licence-board stub answers ACTIVE"
                   is checked as "listed in testdata/README.md" until MAM-47 builds the stub; independent
                   code-reviewer pass not run for this data-only story
Questions for you: none
Next step:         MAM-47 WireMock stubs must answer SD10001-SD10007 ACTIVE
```

AC-1 checked (lead, 2026-11-19 21:05): after make seed, GET /api/v1/providers/{npi} for every record of testdata/fixed-width/provider-master-seed.dat (MAM-44) -> 16 of 16 providers equal their 130-byte record field by field (Medicaid ID, type, taxonomy, tier, status, flag, effective and due dates in 4-digit-year form, 99991231 = no date per Q-106, names, licence, organisation fields with owner NPI). AC-3: the licence-board stub (MAM-47) answers ACTIVE for the seeded licences (journeys J1-J7 PASSED today). All ACs met.
