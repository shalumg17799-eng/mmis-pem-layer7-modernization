# ADR-013: Apache HttpClient 5 behind the gateway's proxy routes
Status: ACCEPTED (user, 2026-11-24, answer to Q-157: Apache HttpClient 5, with the version managed by Spring Boot and raised to 5.6.5 / httpcore5 5.4.4 by the constraints of ADR-010, is approved for the gateway). It was written by the java-developer in the review fix of MAM-29 on 2026-11-21 and applied as a default. The numbers (pool size, wait for a connection) are looked at again in the story that adds the pool limits (local id L-S079, drafted in `modern/docs/backlog/backlog-draft.md`, to be created in Jira)

## Context
The gateway (Spring Cloud Gateway Server MVC 4.3.5, Spring Framework 6.2.19, Spring Boot 3.5.16) forwards every call with the
`RestClient` of Spring Boot. Three request factories were tried against the same tests (`GatewayProxyTimeoutTest`,
`GatewayRelaysServiceErrorTest`, `GatewayRelaysWritesTest`, `GatewayWriteTimeoutTest`, all recorded under MAM-29):

| Factory | What went wrong |
|---|---|
| JDK `HttpClient` (the default of Spring Cloud Gateway) | The read limit covers the WHOLE exchange (Spring 6.2.19, `JdkClientHttpRequest$TimeoutHandler`), so a long master export or file download is cut when the limit is up and the caller gets a bare 500 (guard `flowingDownloadIsNotCutOff`). |
| `simple` (`HttpURLConnection`, the first review fix) | A service's 401 to a POST, PUT or DELETE reaches the caller with its status line and headers and NO body: `HttpURLConnection` throws `HttpRetryException` on a 401 when the request body is streamed (JDK 21, `HttpURLConnection` lines 1809-1813), and Spring 6.2.19 always streams a write. The web UI would not get the 401 it needs to show the login (D-023, D-024). It also knows no HTTP PATCH (the gateway's CORS answer allows PATCH). |
| A gateway-owned factory on the JDK client | The JDK client has no read limit per read; one would have to be built with a watchdog around the response stream. Own code that has to be written, tested and kept; not chosen. |

## Decision
`spring.http.client.factory: http-components` in `api-gateway.yml`: **Apache HttpClient 5** (`org.apache.httpcomponents.client5:httpclient5`,
version 5.6.5 with `httpcore5` and `httpcore5-h2` 5.4.4; Apache License 2.0). The Spring Boot BOM would manage 5.5.2 and 5.3.6; both have security advisories
(OSV, 2026-11-21: httpclient5 5.5.2 GHSA-hjcp-jmpx-g3qm MODERATE; httpcore5 5.3.6 GHSA-hf6x-8p5f-cgmf HIGH; httpcore5-h2 5.3.6 GHSA-v3jc-474w-2wm6 HIGH) and
the 5.5.x and 5.3.x lines have no fix (5.3.6 is the last 5.3 release), so the three are raised above the BOM by the constraints of `pem.java-conventions`
(`gradle/libs.versions.toml`, ADR-010 section "Library raise of 2026-11-21", Q-122 option A). 5.4.4 is the httpcore line that httpclient5 5.6.5 itself names; httpcore5 5.5
(published 2026-11-15) is only days old and is not used. The limits stay the Boot properties
`spring.http.client.connect-timeout` and `read-timeout` (`PEM_GATEWAY_READ_TIMEOUT`); Boot maps them to the connect timeout and the SOCKET timeout of the
connection, which is a limit per read: a download that keeps flowing is never cut off, a stalled one ends after the limit, a silent service is answered with the
503 problem.

`GatewayHttpClient` (a `ClientHttpRequestFactoryBuilderCustomizer`) sets the client up as a plain relay:

- **no automatic retry.** Apache's default strategy repeats a PUT or DELETE after an I/O error and any call answered 429 or 503. The gateway never repeats a call
  (a repeated PUT of the risk rules would create a second version) and relays a service's own 429 or 503 once;
- **no content compression** (Apache would add `Accept-Encoding` and unpack the answer, so the caller would not get the body and headers the service sent) and **no cookie
  handling** (a cookie of one service must never reach another);
- **a pool of 200** connections in all and per service. Apache's default of 5 per service and 25 in all would let a few long downloads hold up every other call; the
  requests are served by virtual threads (`pem-common.yml`), so no thread limit bounds the calls in flight. Before this change nothing bounded them either.

## Consequences
- A new third-party library (the java-microservices skill requires an ADR for it). It reaches the shared class path of the local run mode (ADR-011). The other services
  build their own clients with explicit factories (`ServiceClients`, `HttpScreeningSources` use `JdkClientHttpRequestFactory`), so nothing changes for them; only a
  builder that Boot detects by itself would now find Apache first. The next dependency scan (`tools/checks/scan_dependencies.py`) will list the three jars; it was not run
  in this change.
- A write that meets a connection the service has just closed fails once and is answered with the 503 problem; it is not repeated (the pool also checks a connection that
  was idle for more than 2 s before it reuses it). A caller may repeat a write himself when it is safe for that write.
- Not forwarded as before: `Host` (Spring Cloud Gateway removes it unless the route preserves it, so Apache sets the service's own). Forwarded now, unlike with
  `HttpURLConnection`: `Origin` and `Access-Control-Request-*`. No service has a CORS configuration (search of `services/*/src/main` and `libs/service-kit`, 2026-11-21), so
  they ignore them; CORS is the gateway's own filter.
- HTTP PATCH works through the gateway.
- Redirects are still not followed (Spring Cloud Gateway sets `spring.http.client.redirects` to `dont_follow`).
- The numbers (read limit 30 s, connect limit 2 s, pool 200) are defaults that the MAM-88 smoke with 10,000 providers has not tested yet.
