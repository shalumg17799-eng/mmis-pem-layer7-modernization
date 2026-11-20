```
Phase / item:      MAM-29 API gateway: routes, public directory paths, token relay, CORS and correlation ID
Outcome:           DONE (Phase 5 platform task; tests recorded in MAM-29.tests.sha256); AC-1..AC-6 checked 2026-11-20
What was done:     The gateway was built in Phase 5 (services/api-gateway, port 8080) and has carried every journey since.
                   On 2026-11-20 one defect was found by the live security suite and fixed test-first: /actuator/info and
                   /actuator/metrics answered without a token (Q-121, default A applied: the gateway exposes only health);
                   fix = management.endpoints.web.exposure.exclude: info,metrics in api-gateway.yml, in deploy 1.
                   This report was written by the lead from the recorded tests, today's gate run and read-only live checks.
Files created/changed: services/api-gateway/src/main/** (Phase 5), services/api-gateway/src/main/resources/api-gateway.yml
                   (actuator exposure, 2026-11-20), tools/checks/live/test_security.py (the 3 actuator tests);
                   tests: MAM-29.tests.sha256 (6 files; GatewayRoutingTest holds the six criteria)
Checks run:        Recorded tests, :services:api-gateway:check on 2026-11-20 (before deploy 1): 48 tests, 0 failures,
                        0 skipped, JaCoCo line 92.31 %; test results on disk of 17:30 (run by the library-upgrade build):
                        48 tests, 0 failures, 0 skipped, 0 errors. test_protection check MAM-29: OK.
                   AC-1 every contract path is routed to its owning service:
                        GatewayRoutingTest "[MAM-29 AC-1]" (one case per contract path, stub services record the call).
                        Live 17:50: one GET per contract through http://127.0.0.1:8080, each with its own X-Correlation-Id:
                        directory, enrollment, notification, provider, revalidation, risk, sanctions, screening, tmsis all
                        answered 200 with their own data and echoed the id; none got the gateway's "no route" 404.
                        Service logs: since the restart of 16:59 the apps log holds lines that carry a correlationId
                        together with pemService = the owning service, for each of the nine services (4 to 10 lines each).
                        The live security suite after deploy 1 called all 42 operations through the gateway: 278 passed.
                   AC-2 directory and FHIR need no token, everything else does:
                        GatewayRoutingTest "[MAM-29 AC-2]". Live 17:50, no token: GET /api/v1/directory/providers 200,
                        /fhir/Practitioner 200, /fhir/Organization 200, /fhir/PractitionerRole 200; GET /api/v1/providers 401.
                   AC-3 the token is relayed unchanged: GatewayRoutingTest relaysTheToken (the stub receives exactly the
                        Authorization header that was sent; no role, claim or identity header is added). Live: the role
                        decisions of all 42 operations are made by the services (tools/checks/live/test_security.py, role
                        table of docs/architecture/nfr.md: 278 passed after deploy 1).
                   AC-4 CORS allows the web UI only: GatewayRoutingTest "[MAM-29 AC-4]". Live 17:50, preflight OPTIONS:
                        Origin http://localhost:5173 -> 200, allow-origin http://localhost:5173, allow-headers
                        authorization, content-type, x-correlation-id; Origin http://evil.example -> 403, no allow header.
                   AC-5 a correlation id is accepted or generated: GatewayRoutingTest "[MAM-29 AC-5]". Live 17:50: a sent id
                        came back unchanged; a request without one got a generated id (36 characters) in the response.
                   AC-6 clear errors for unknown routes and unavailable services: GatewayRoutingTest "[MAM-29 AC-6]" (unknown
                        path 404, stopped service 503, both problem details with the correlation id, no internal host name).
                        Live 17:50: GET /api/v1/no-such-thing -> 404 application/problem+json with the correlation id,
                        no stack trace, no internal host name.
                   Deploy 1 (2026-11-20 17:07): J1 passed, J2 passed, live security suite 278 passed (the three actuator
                        tests that failed before the fix are green).
Not verified:      AC-6 "a stopped service answers 503" against the live stack (no service was stopped: other work shares the
                   stack); it is covered by the recorded test. AC-1 "shown in that service's log" for the nine GETs of 17:50
                   themselves: the services log nothing for a plain successful read at INFO level, so the log evidence is the
                   lines of other requests (see AC-1).
Questions for you: Q-121 (gateway exposes only /actuator/health; DEFAULT APPLIED - revisit)
Next step:         Jira Done (user approval 2026-11-19 20:34)
```
