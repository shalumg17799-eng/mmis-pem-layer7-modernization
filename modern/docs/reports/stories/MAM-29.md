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
Questions for you: Q-121 (gateway exposes only /actuator/health; DEFAULT APPLIED - revisit);
                   Q-157 (Apache HttpClient 5 in the gateway, ADR-013, and its raise to 5.6.5 / 5.4.4: DEFAULT APPLIED - revisit; on
                   2026-11-24 the user named the three versions and asked for review, commit and deploy: see "Review fix 3")
Next step:         Jira Done (user approval 2026-11-19 20:34)
```

## Review fix (2026-11-21, review-fix step of the lead's plan)

(Superseded in one point: the request factory `simple` named below was replaced by Apache HttpClient 5 in the section "Review fix 2" at the end; its trade-offs (a) and (b) of item 3 no longer apply.)

The code review of api-gateway (2026-11-20, about 20:00) rated two points MUST-FIX and story MAM-29 was reopened for them (AC-6, D-024, lessons c and f):
(1) the calls the gateway forwards had no connect or read limit, so a service that accepts a call and never answers held the caller for ever; (2) the route's
error handler answered 503 but dropped the exception and logged nothing.

- Tests first (test-writer, 06:13 to 06:15): 28 new tests in 5 files, all tagged `[MAM-29 AC-6]` (`GatewayProxyTimeoutTest`, `GatewayProxyTimeoutConfigTest`,
  `GatewayFailureLoggingTest`, `UpstreamFailureTestBase`, `SlowServiceStub`); 26 red for the intended reasons, 2 guards green. Details: red-report.md, section
  "Test correction under Q-097".
- Fix (java-developer, one production file and one config file; no test, contract, kit or other service touched):
  - `api-gateway.yml`: `spring.http.client.factory: simple`, `connect-timeout: 2s`, `read-timeout: ${PEM_GATEWAY_READ_TIMEOUT:30s}`. A silent service now ends
    in the existing 503 problem (same status, title and detail, correlation id, no host or port) after the read limit.
  - `PemRoutes.java`: a logger and, in the `onError` handler, `LOG.warn("{}-service call failed: {}", service, req.method(), ex)` with the exception last, so the
    stack trace shows the cause (`ConnectException`, `SocketTimeoutException`); the correlation id is on the line through the MDC; no header, token or body is
    logged. The answer to the caller is unchanged.
- Check: `./gradlew --no-daemon :services:api-gateway:check --continue`, one run (07:06 to 07:08, 1 min 38 s after a 502 s wait for the shared lock): BUILD SUCCESSFUL,
  76 tests (48 recorded + 28 new), 0 failures, 0 skipped, 0 errors; JaCoCo line 92.75 % (64 of 69), branch 6 of 8, gate 85 % met; `testSourceCheck`, `versionCheck`,
  `spotlessCheck` pass. PIT is not configured for this module (it uses `pem.java-conventions`, not `pem.spring-service`; the gateway has no domain or application
  package). `test_protection.py check MAM-29`: OK, 11 files unchanged. Not run on the live stack (no deploy by this step).

Decisions and findings (please read; each is also a question or default in the result of the step):

1. **The default read limit is 30 s, set by `PEM_GATEWAY_READ_TIMEOUT` (a duration such as `45s`).** Reason: it is six times the 5 s limit of a service's own calls
   (`ServiceClients.DEFAULT_READ_TIMEOUT`), so a service can still answer with its own timeout problem first, and it is inside the window the tests fix (above 5 s, at
   most 5 min). Every batch run in the contracts answers 202 at once, so the slowest synchronous calls are the whole master export
   (`GET /api/v1/legacy/provider-master`, 130 bytes per provider) and the file downloads. NOT MEASURED: the MAM-88 smoke with 10,000 providers has not run; if the export
   needs more than 30 s before its first byte, raise the variable (the 5 min test bound is the number to revisit).
2. **What the limit means.** With the simple factory the read limit is a socket limit per read: the longest wait for the response headers and then for each next piece
   of the body. A download that keeps flowing is never cut off, however long it takes (guard `flowingDownloadIsNotCutOff`); one that stalls ends after the limit
   (`stalledDownloadIsEndedWithinABoundedTime`). The review's one-line fix (`spring.http.client.read-timeout` alone) would have used the JDK factory, whose limit in Spring
   6.2.19 covers the WHOLE exchange (`JdkClientHttpRequest$TimeoutHandler`): it cuts a long export and the caller gets a bare 500. That is why `factory: simple` is set.
3. **Trade-offs of the simple (HttpURLConnection) factory, checked in the Spring 6.2.19 and Boot 3.5.16 bytecode.** (a) The request body is streamed (fixed length when
   Content-Length is known, else chunked), so HttpURLConnection never repeats a POST on a dead keep-alive connection; the earlier worry (buffering, `retryPost`) does not
   apply to this version. A POST on a connection the service has closed fails once and is answered 503 instead of being repeated. (b) HTTP PATCH is not supported: a PATCH
   through the gateway is expected to be answered 503 and logged (ProtocolException) instead of the service's 405 (read from the bytecode, not run); the contracts define no PATCH and no screen sends one. (c) HttpURLConnection
   does not forward the restricted request headers (Origin, Host, Connection, Via, Keep-Alive, Upgrade, Access-Control-Request-*); the services decide on the bearer token and
   the correlation id, and CORS is the gateway's own filter. (d) Redirects are still not followed (Boot passes `DONT_FOLLOW` to the simple factory).
4. **The `onError` predicate was not narrowed.** The review says the IOException is wrapped in an UncheckedIOException. In this version the default proxy exchange is the
   RestClient one, which wraps it in `ResourceAccessException`; narrowing to IOException would have turned every unavailable service into a 500. A narrowing to
   `ResourceAccessException` is possible but cannot be tested (a defect inside the route cannot be provoked), so it was left. Every failure, a gateway defect included, now
   leaves a WARN line with its stack trace.
5. **Log content.** The stack trace holds the message of `ResourceAccessException`, which names the request method and the URL (path and query) of the call to the service. No
   header, token or body is in it (tested: `logHoldsNoTokenAndNoBody`). The answer to the caller still holds no host, port or exception text (tested).

Not fixed (new behaviour, needs a question first; default proposed: answer the same 503 problem while the response is not yet committed): when the body copy fails after the
response already carries the service's Content-Type (`text/plain` for the export and the file downloads), `GatewayProblems` cannot write its problem
(`HttpMessageNotWritableException`, no converter for `text/plain`) and the caller gets an empty 500. No test exists for it.
Not verified: the connect limit as behaviour (no reliable unroutable address); the limits on the live stack; the read time of the real master export.

## Review fix 2 (2026-11-21, second code review: a service's 401 to a write was lost)

The second review (about 07:20) rated one point MUST-FIX: with `spring.http.client.factory: simple`, a service's 401 to a POST, PUT or DELETE reached the caller with its status line and
headers and NO body (the review: `HttpURLConnection` throws `HttpRetryException` on a 401 when the body is streamed, and Spring 6.2.19 streams every write). Lesson h and D-024 were broken
for writes: a web UI call with an expired token would not get the 401 it needs to show the login. The cause is the review's; the tests do not depend on it (they assert what reaches the caller).

- Tests first (test-writer, 07:35 to 08:07): 129 test executions in 25 methods in 5 files (`AnsweringServiceStub`, `ServiceAnswerTestBase`, `GatewayRelaysServiceErrorTest`,
  `GatewayRelaysWritesTest`, `GatewayWriteTimeoutTest`); 22 red (20 = the 401 of the four writes, 2 = PATCH), 107 guards. Details: red-report.md, section "Test correction under Q-097 (second code review)".
- Fix (java-developer, edits from about 08:15, check run 08:33 to 08:35; no test, contract, kit or other service touched):
  - `build.gradle.kts`: `implementation("org.apache.httpcomponents.client5:httpclient5")` (version 5.5.2 from the Spring Boot BOM; `httpcore5` and `httpcore5-h2` 5.3.6 come with it **[superseded on 2026-11-21 / 2026-11-24: raised to 5.6.5 / 5.4.4 / 5.4.4, see "Review fix 3"]**; all three were already in the
    Gradle cache). A new third-party library: **ADR-013** (written first) and **Q-157** (default applied: A, Apache HttpClient 5; B, an own factory on the JDK client, was not chosen).
  - `api-gateway.yml`: `spring.http.client.factory: http-components` (was `simple`). The Boot keys `connect-timeout: 2s` and `read-timeout: ${PEM_GATEWAY_READ_TIMEOUT:30s}` are unchanged; Boot maps the read
    limit to the SOCKET timeout of the connection, a limit per read, so a download that keeps flowing is still not cut off (`flowingDownloadIsNotCutOff`) and one that stalls still ends (`stalledDownloadIsEndedWithinABoundedTime`).
  - `GatewayHttpClient.java` (new): a `ClientHttpRequestFactoryBuilderCustomizer` that sets the client up as a plain relay: no automatic retry, no content compression, no cookie handling, a pool of 200
    connections in all and per service. `PemRoutes.java` is unchanged.
- Check: `./gradlew --no-daemon :services:api-gateway:check --continue`, one run (08:33:02 to 08:35:34, 2 min 28 s; the lock was free after 20 s, the memory check waited 360 s for 1 GB; 1,770 MB free at the start):
  **BUILD SUCCESSFUL, 205 tests (48 recorded + 28 + 129), 0 failures, 0 skipped, 0 errors**; JaCoCo line 93.59 % (73 of 78; the gate excludes the `*Application` class, 72 of 73 = 98.6 %), branch 6 of 8, gate 85 % met;
  `spotlessCheck` (main and test sources), `testSourceCheck` (401 test files) and `versionCheck` pass. PIT is not configured for this module (no domain or application package). The result files on disk
  (`build/test-results/test/*.xml`, 08:35:28) and the JaCoCo report (08:35:32) are newer than every source of the module. `test_protection.py check MAM-29`: OK, 16 files unchanged.
  The two PATCH tests (the test-writer asked the lead to keep or drop them) are green with the new client; they were left as written.

Decisions and findings:

1. **What was proved, and how, where no test exists.** Retries, compression, cookies and the pool size have no test of their own that fails without the setting, so they were checked in a scratch program outside the repository
   (a JDK `HttpServer` and the same Apache 5.5.2 jars **[not repeated with 5.6.5, see "Review fix 3"]**; run on 2026-11-21 between the check run and this report, not part of the build). Default Apache client: a PUT, DELETE and GET to a service that closes the connection without an answer reaches it TWICE
   (a POST once); a 503 is asked twice for every method, POST included; it adds `Accept-Encoding: gzip, x-gzip, deflate`; it sends back the cookie a service set; its pool is 25 in all and 5 per service. With the three
   settings of `GatewayHttpClient` every call reaches the server once, there is no `Accept-Encoding` and no cookie; the pool setters give 200 and 200. That the customizer is really applied to the client of the gateway: JaCoCo shows
   the lines of `relayOnly` and `sizePool` executed in the module tests. So the guards `writeIsNotRepeatedAfterAClosedConnection` and `serviceAnswerIsNotRetried` do fail without `disableAutomaticRetries` (inferred from the scratch
   result; the gateway itself was not run without the setting).
2. **The read limit stays 30 s by default** (`PEM_GATEWAY_READ_TIMEOUT`), for the reasons of the section above; NOT MEASURED, the MAM-88 smoke has not run. The pool size 200 and the connect limit 2 s are defaults of the same kind.
3. **Pool: why 200.** The requests are served by virtual threads (`pem-common.yml`), so nothing limits the calls in flight; the old clients did not limit them either. Apache's default (5 per service, 25 in all) would make
   six concurrent long downloads from one service queue every other call to it, up to Apache's 3 minute wait for a connection. 200 is the Tomcat default thread count; it is a guess to be revisited with MAM-88.
4. **Headers.** `Host` is removed by Spring Cloud Gateway (read from the bytecode of `ProxyExchangeHandlerFunction`; Apache sets the service's own); `Origin` and `Access-Control-Request-*` are now forwarded, which
   `HttpURLConnection` did not do. No service has a CORS configuration (search of `services/*/src/main` and `libs/service-kit`), so nothing reacts to them. Not run live.
5. **One write on a connection the service has just closed** is answered 503 and not repeated (the pool checks a connection idle for more than 2 s before it reuses it, so the window is small); this follows the default
   "a write is never repeated" of the test-writer.
6. **Other services:** in the local run mode the library is on the shared class path, but every other service names its HTTP client explicitly (`ServiceClients`, `HttpScreeningSources` use `JdkClientHttpRequestFactory`), so
   nothing changes for them.

Not fixed (as before): when a body copy fails after the response carries the service's Content-Type, the caller gets an empty 500 (see above; new behaviour, needs a question first).
Not verified: the live stack (nothing deployed by this step); a 401 that a service sends before it has read the request body (Spring Security does that; the stub reads first); load (MAM-88); the dependency scan
(`tools/checks/scan_dependencies.py` was not run at this point **[superseded: it ran on 2026-11-24 on the jar with the raised versions, see "Review fix 3"]**); HTTP/2, TLS and compression between gateway and services.

## Review fix 3 (dependency raise: httpclient5 5.6.5, httpcore5 and httpcore5-h2 5.4.4; written 2026-11-24)

What this is: the Spring Boot BOM would put httpclient5 5.5.2 and httpcore5 / httpcore5-h2 5.3.6 into the gateway (ADR-013). The OSV database lists
GHSA-hjcp-jmpx-g3qm (MODERATE, httpclient5, fixed in 5.6.3), GHSA-hf6x-8p5f-cgmf (HIGH, httpcore5) and GHSA-v3jc-474w-2wm6 (HIGH, httpcore5-h2), both fixed in
5.4.3; the 5.5.x and 5.3.x lines have no fix. The lead raised the three versions on 2026-11-21 (default A of Q-122, written as an addition to Q-157). On 2026-11-24 the
user named the three versions in the instruction "review the gateway library upgrade, commit, deploy with J1 + J2, dependency scan clean, move to Done".

- **Files changed** (build files only; no source, test or contract file for the raise): `gradle/libs.versions.toml` (versions `httpclient5` 5.6.5 and `httpcore5` 5.4.4, three library aliases),
  `buildSrc/src/main/kotlin/pem.java-conventions.gradle.kts` (the three aliases added to the constraint list), `services/api-gateway/build.gradle.kts` (`implementation` of httpclient5 without a version).
- **Resolved** (`./gradlew :services:api-gateway:dependencies --configuration runtimeClasspath`, 2026-11-24): httpclient5 5.5.2 -> 5.6.5, httpcore5 5.3.6 -> 5.4.4, httpcore5-h2 5.3.6 -> 5.4.4. The rebuilt
  deployable `pem-local-runtime.jar` (13:12) holds `BOOT-INF/lib/httpclient5-5.6.5.jar`, `httpcore5-5.4.4.jar` and `httpcore5-h2-5.4.4.jar` and no other version of them.
- **Check with the raised versions:** `:services:api-gateway:check`, 205 tests, 0 failures, 0 errors, 0 skipped, JaCoCo line 93.59 %; the result files are of 2026-11-21 09:06, after the build-file edit of 09:01, and name
  only the 5.6.5 and 5.4.4 jars; the lead's run of 2026-11-24 12:57 to 12:58 (BUILD SUCCESSFUL, 1 min 27 s) found every task up to date. `test_protection.py check MAM-29`: OK, 16 test files unchanged; the 16 hashes equal the
  recorded blobs (reviewer, `git hash-object`).
- **Dependency scan** (`tools/checks/scan_dependencies.py`, 2026-11-24 13:12, on the jar above): 110 Java libraries, 322 npm packages; no advisory on httpclient5, httpcore5 or httpcore5-h2; the findings are
  the same two CRITICAL ones of spring-webmvc 6.2.19 as before (fixed only in Spring Framework 7), each with its written justification: **0 HIGH, CRITICAL or unscored findings without justification**.
- **Independent review** (code-reviewer, 2026-11-24): MUST-FIX 2, both about documents (this report still named the BOM versions; ADR-010 had no record of the raise and ADR-013 cited a section that did not exist).
  Both are corrected: the three statements above are marked superseded, ADR-010 has a section "Library raise of 2026-11-21" and ADR-013 cites it. No code or test had to change.
- **Not repeated with 5.6.5:** the scratch proof of finding 1 (retries, compression, cookies, pool size) was made with the 5.5.2 jars. The settings are explicit in `GatewayHttpClient`, JaCoCo shows `relayOnly` and `sizePool` executed,
  and the guards `writeIsNotRepeatedAfterAClosedConnection` and `serviceAnswerIsNotRetried` pass with 5.6.5; that they fail without `disableAutomaticRetries` on 5.6.5 was not run.
- **Accepted notes of the review (SHOULD-FIX, not done in this step):** (1) no wait limit for a connection from the pool, so Apache's default of 3 minutes applies and 200 concurrent calls to one silent service
  (each up to the 30 s read limit) would hold the whole pool; the pool size and the wait limit are guesses that are re-examined with the performance smoke (MAM-88), as finding 3 above already says;
  (2) the pool Javadoc of `GatewayHttpClient` is loose about "as many as calls in flight"; (3) the `because(...)` text of the constraint list cites ADR-010 and the scan only. All three are listed in `docs/reports/review-should-fix.md`.
- **Not verified:** the live stack with this jar (deploy follows the green CI run); that the gateway behaves the same under load.
