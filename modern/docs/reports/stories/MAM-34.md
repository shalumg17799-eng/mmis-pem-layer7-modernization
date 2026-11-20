```
Phase / item:      MAM-34 (local L-S007) Walking skeleton: provider lookup by NPI
Outcome:           DONE (AC-1..AC-3), In Review: AC-3 console Provider search page built 2026-11-19 01:40 (npm test 150/150, Playwright 30/30 live; code-reviewer APPROVE)
What was done:     - Red: 16 test files (Cucumber feature MAM-34 for AC-1/AC-2, per-role API tests, unit tests for
                     the status/flag/tier/type codes and the find use case, contract check against getProvider,
                     platform checks); failed only because the classes did not exist; hashes recorded
                   - Green (attempt 1): provider-service GET /api/v1/providers/{npi} with role checks
                     (PROVIDER own NPI only from the npi claim, ANALYST/SUPERVISOR/ADMIN/SYSTEM any), 404/400/401/403
                     as problem details; domain codes; JPA adapter on Flyway V1 (the Phase 3 data model)
                   - Check (attempt 2): PIT first scored 68% (< 70% gate, survivors only reachable by integration
                     tests); production refactor only (smaller builder, codes defined once); now 31/31 mutants killed
Files created/changed: modern/services/provider-service/** (main and test), modern/libs/test-support/**,
                   modern/libs/service-kit/** (shared kit, tested under MAM-26/29/31/36)
Checks run:        ./gradlew :services:provider-service:check -> BUILD SUCCESSFUL: 37 tests, 0 failures,
                   0 skipped; JaCoCo line coverage 125/130 (96%); PIT 31/31 killed (100%)
                   python modern/tools/checks/test_protection.py check MAM-34 -> OK (16 files unchanged)
Not verified:      AC-1/AC-2 through the gateway with real Keycloak tokens (needs the stack: make up && make seed,
                   then tools/checks/smoke-walking-skeleton.sh); AC-3 console page "Provider lookup" (component and
                   Playwright tests not written yet); code-reviewer review (runs when AC-3 is done)
Questions for you: none new (the PROVIDER-to-NPI link via the npi claim is Q-015 / QF-1, default applied)
Next step:         AC-3 page and the end-to-end check, then code review, then In Review
```

Live check (lead, 2026-11-19 21:00, stack at the seed state): tools/checks/smoke-walking-skeleton.sh -> PASS AC-1 analyst reads
seeded provider 1005940865 through the gateway (200, body is that provider); PASS AC-2 no token (401); PASS AC-2 provider1
(npi 1003000111) reads another NPI (403). AC-3 console page: built, tested and reviewed earlier (see Outcome). The 'Not verified'
items above are now verified, except the separate 'Provider lookup' page wording (the console search page replaced it, MAM-37).
