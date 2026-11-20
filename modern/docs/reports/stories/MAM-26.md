```
Phase / item:      MAM-26 Keycloak realm pem with the PROVIDER, ANALYST, SUPERVISOR, ADMIN roles, one test user per role and a SYSTEM service account
Outcome:           DONE (Phase 5 platform task; tests recorded in MAM-26.tests.sha256); AC-1..AC-6 checked live 2026-11-19 20:52
What was done:     Realm infra/keycloak/realm-pem.json imported by the pem stack (Phase 5); service-kit security and token helpers
                   (tests in libs/service-kit); this report written by the lead after the live AC check below
Files created/changed: infra/keycloak/realm-pem.json, libs/service-kit/src/main/java/gov/medicaid/pem/kit/security/*,
                   libs/service-kit/src/main/java/gov/medicaid/pem/kit/client/ServiceTokenProvider.java (tests: MAM-26.tests.sha256)
Checks run:        live, read-only (lead, 2026-11-19 20:52; passwords read from infra/.env.local, never printed):
                   AC-1 issuer http://localhost:8180/realms/pem; realm roles ADMIN, ANALYST, PROVIDER, SUPERVISOR, SYSTEM (admin API)
                   AC-2 tokens: provider1 [PROVIDER], analyst1 [ANALYST], supervisor1 [SUPERVISOR], admin1 [ADMIN] (PEM roles only)
                   AC-3 wrong password -> 400 (refused); no value of infra/.env.local in any tracked or untracked, non-ignored
                        file of the repo (in-memory scan, 0 files); git check-ignore confirms infra/.env.local is ignored
                   AC-4 client pem-web: public, standard flow, PKCE S256, redirect http://localhost:5173/*, web origin
                        http://localhost:5173, direct access (password) grant off
                   AC-5 client pem-services (client credentials, secret PEM_SERVICES_CLIENT_SECRET): token roles [SYSTEM], no npi
                   AC-6 provider1 token npi = 1003000111; analyst1, supervisor1, admin1 tokens carry no npi claim
                   recorded tests: libs/service-kit check 41/41 (A5 build, 2026-11-19); re-run with the service gates in lane 3
Not verified:      the AC-2 sign-in through the web UI client itself is shown by journeys J1-J7 (all PASSED 2026-11-19 20:10-20:24),
                   not by a separate token read of pem-web; the test client pem-test (password grant, used only by tests) exists
                   besides pem-web
Questions for you: none
Next step:         Jira Done (user approval 2026-11-19 20:34)
```
