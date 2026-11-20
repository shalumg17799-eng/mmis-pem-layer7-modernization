```
Phase / item:      MAM-47 (local L-S012) WireMock stubs for the external screening sources
Outcome:           DONE (AC-1..AC-8)
What was done:     - Red: tools/checks/tests/test_wiremock_stubs.py, 42 tests against the running WireMock (localhost:8280),
                     one group per AC; all failed (no mappings, no README); hash recorded (MAM-47.tests.sha256)
                   - Green (attempt 1): 27 mappings in infra/wiremock/mappings/{leie,sam,nppes,license-board,ssa-dmf}/,
                     each with metadata.source and metadata.answer (hit, clean, error, slow; licence board also
                     not-found); our own simple JSON (D-005): GET /leie/exclusions?npi=, /sam/debarments?npi=,
                     /nppes/providers?npi=, /ssa-dmf/deaths?npi=, /license-board/licenses?licenseNo=&state=
                   - Hits: LEIE 1990000111 (1128(a)(1) PROGRAM-RELATED FRAUD, 2023-01-15); licence PT99900 EXPIRED
                     2024-01; fabricated SAM 1005999010, SSA DMF 1005999028, NPPES 1005999036 (valid check digit,
                     never seeded); the six sample licences ACTIVE with their file expiry; seeded SD10001-SD10007 ACTIVE
                   - Error / slow selected by header X-Pem-Stub: error (500) or slow (clean answer after 6000 ms)
                   - infra/wiremock/README.md: requests, answers, selection, and the 33 documented calls the test runs
Files created/changed: modern/infra/wiremock/README.md, modern/infra/wiremock/mappings/**/*.json (27)
                   test: modern/tools/checks/tests/test_wiremock_stubs.py
Checks run:        pytest test_wiremock_stubs.py -> 42 passed (62 s, live stack, mappings reloaded via
                   POST /__admin/mappings/reset, no container restart)
                   test_protection.py check MAM-47 -> OK
Not verified:      how screening-service calls the sources (MAM-18/25/21); code-reviewer pass not run for this
                   stub-only story
Questions for you: none
Next step:         MAM-46 submit and validate an application
```
