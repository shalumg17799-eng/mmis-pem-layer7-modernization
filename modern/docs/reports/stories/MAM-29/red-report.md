# MAM-29 red report

The kit tests recorded under MAM-29 before this section are listed in `MAM-29.tests.sha256` (4 files: `HelpersTest`,
`PemKitAutoConfigurationTest`, `ProblemHandlerTest`, and `GatewayRoutingTest` of api-gateway); the key switch is in
`key-switch.md`. This file did not exist before; it starts with the section below.

## Kit tests added 2026-11-20 (Q-097 additive)

Scope: `ProblemHandler.handleUnreadable` of the shared library `libs/service-kit` (a request body that cannot be read). Additive
only (Q-097 C: nothing weakened, removed or edited; new test classes). **No production file was touched**: the sha256 of all
24 files under `libs/service-kit/src/main` is identical before and after.

**These tests were written after the code, so they were green at once: there is no red phase.** To show that they are not
vacuous, they were run against deliberately broken copies of the production code (section "Mutation check").

### Which AC, and why

**MAM-29 AC-6** ("clear errors ... as RFC 7807 problem details that carry ... no stack trace or internal host name"). The 400 for an
unreadable body is the same family as the 404 and 503 of AC-6 and is the problem-details rule of the java-microservices skill,
section 6; `ProblemHandlerTest` and `HelpersTest` already record the other problem shapes of the kit under `[MAM-29 AC-6]`.
(The story is the gateway; no AC names the 400 of the services' bodies, so this is the nearest one.)

### New files (blob ids are `git hash-object`; they are also in `red-blobs.txt`)

| File (under `modern/libs/service-kit/src/test/java/gov/medicaid/pem/kit/web/`) | Blob id | Tests |
|---|---|---|
| `ProblemHandlerUnreadableTest.java` | `29ae61591562b37aa32ee75a9102618b5eaf95b6` | 4 |
| `UnreadableBodyWebTest.java` | `92b15550078dba0eaf1f70885eb59bcb4d26b913` | 22 |

26 new tests. Re-recorded: `MAM-29.tests.sha256` now lists 6 files (the 4 recorded files unchanged, 2 new). The new sha256 of the hash
file is `7df9a78ddad2c8a19fc1559dac0a9e7fff1ffbb5e4bf959812dd894c372888ff` (`testsSha256` in `specs/MAM-29.md` is still empty; the lead
sets it).

### Tests

`ProblemHandlerUnreadableTest` (unit): status 400; title Bad Request; a short detail with none of the parser words (Jackson, "JSON
parse error", "Unrecognized token", "Cannot deserialize", class names, "line:", "column:" ...); no legacy code and no field errors.

`UnreadableBodyWebTest` (a real request through `WebApplicationContextRunner` + MockMvc, with the auto-configured `ProblemHandler`
of a service and a probe endpoint that reads a JSON object); six bodies: not JSON, wrong type (`"count":"many"`), a JSON array,
truncated JSON, a JSON string, no body at all:
- each gets 400 as `application/problem+json`
- each has title "Bad Request", status 400 and a detail
- none of the answers says anything about the parser, and none echoes a piece of the submitted body
- with `Accept: application/json` the problem is still `application/problem+json`
- control: a readable JSON object is accepted (200)
- a service's own advice for `HttpMessageNotReadableException` (the shape of `DirectoryProblems`: `@Order(LOWEST_PRECEDENCE)`, its own
  detail) still wins; without it the kit's detail is used, so the test tells the two apart

### Run

`./gradlew --no-daemon :libs:service-kit:check --continue` (2026-11-20, one build at a time): **BUILD SUCCESSFUL**. Tests of the module:
110 (41 existing + 69 new of MAM-26 and MAM-29), 0 failed, 0 skipped; Spotless check passed; JaCoCo line coverage of the kit **91.85 %**
(338 of 368; gate 85 %). Details of the run and the mutation method are in `modern/docs/reports/stories/MAM-26/red-report.md`.

### Mutation check (throw-away copy of the kit in the session scratchpad)

| Mutant | Failed tests |
|---|---|
| M4 the detail of `handleUnreadable` is "JSON parse error: Unrecognized token (Jackson)" | unit `detailSaysNothingAboutTheParser`; web `unreadableBodyLeaksNothing` for all 6 bodies |
| M5 `ProblemHandler` gets `@Order(HIGHEST_PRECEDENCE)` (the kit beats the service) | web `ownAdviceWins` (the kit's detail instead of the service's) |

### Not verified / not covered

- A malformed body of other media types (XML, form) and other methods than POST: only JSON bodies of a POST were tried.
- The other handlers of `ProblemHandler` that no test reaches yet (7 uncovered lines: `handleInvalid` for `MethodArgumentNotValidException` and
  `handleTypeMismatch`); they are outside this task and are the next candidates if the kit's coverage is to be raised.

## Test correction under Q-097 (code review 2026-11-20, additive)

Cause: the code review of api-gateway (verdict MUST-FIX, 2 items, 2026-11-20 about 20:00) found two defects the recorded tests cannot catch. The standing rule **Q-097 C** applies: additive, no
assertion weakened, removed or relaxed, documented here and re-recorded; the code-reviewer confirms. Story MAM-29 was reopened in Jira for these two items.

| Review item | AC | What the recorded tests miss | Evidence in the code (before the fix) |
|---|---|---|---|
| MUST-FIX 1: no connect or read timeout on forwarded calls | AC-6 (D-024; lesson f: every outgoing call has a timeout) | `GatewayRoutingTest` has services that answer at once and one port with nothing listening; no service that accepts the call and never answers. So "the caller never gets a problem answer" is invisible. | `api-gateway.yml` has no `spring.http.client.*` and no `spring.cloud.gateway.*.http-client.*`; `PemRoutes.java:65` `.route(paths, http())` |
| MUST-FIX 2: the failed forward is answered 503 but never logged | AC-6 (D-024 "failures surfaced"; lesson c: short reason in the answer, full exception in the log) | `stoppedService` checks only the answer, never the log. | `PemRoutes.java:68` `.onError(ex -> true, (ex, req) -> unavailable(service))` drops the exception; the class has no logger |

**Additive.** No recorded file was edited: `GatewayRoutingTest.java` and the 5 kit files of MAM-29 are byte-identical (sha256 equal before and after). 5 new files are added to the hash file
(6 -> 11 files). **No production file was touched**: the sha256 over the 5 files of `services/api-gateway/src/main` is `0e8adb665f6c...` before the first and after the last Gradle run. No Cucumber
scenario was added: AC-6 has no scenario in this story (the gateway tests are JUnit, as recorded). The hash file before this correction is kept in the scratchpad
(`MAM-29.tests.sha256.before-gw-review`, 6 files, sha256 `7df9a78ddad2c8a19fc1559dac0a9e7fff1ffbb5e4bf959812dd894c372888ff`).

### Files (all under `modern/services/api-gateway/src/test/java/gov/medicaid/pem/apigateway/`; CRLF kept, formatted by Spotless on test sources only)

| File | Blob id (recorded) | Tests |
|---|---|---|
| `SlowServiceStub.java` (JDK HttpServer: quick path, flowing download, stalling download, and for every other path "accepts the call and never answers"; the gateway's counterpart of the kit's `SilentServer`) | `6c0846091c15b913619dda28735e0bc383c076c1` | 0 |
| `UpstreamFailureTestBase.java` (abstract: one gateway context for the two classes below; `tmsis` stopped, the other 8 services point to the stub; `PEM_GATEWAY_READ_TIMEOUT=1s`; every call has a 6 s client limit and fails with a message, never hangs) | `fd87dc28eed6429f7ecdb253a3dbab4b3d9b0315` | 0 |
| `GatewayProxyTimeoutTest.java` | `40ac4704d5840b03d8bca9b0558c6b34cc67af3b` | 15 (8 parameterised + 7) |
| `GatewayFailureLoggingTest.java` | `0f77ad8f8be77f0ac982e54fb6936ed041d9ec99` | 7 |
| `GatewayProxyTimeoutConfigTest.java` (no Spring context: reads the shipped `api-gateway.yml`) | `db5fc1c15e359f4e2b2a0bf79beb8db2f6f0b5ce` | 6 |

28 new tests. New hash file `modern/docs/reports/stories/MAM-29.tests.sha256` = 11 files, sha256 `e3143a60d770caf4d0dc5f6e453e59fce467fc8297b5f2a9c94ea0eee7745a40`
(`testsSha256` in `specs/MAM-29.md` still holds the old value `7df9a78d...`; the lead sets it). `test_protection.py check MAM-29`: OK (11 files unchanged, no unrecorded test file).
`trace.json` of MAM-29 does not list the 5 new files yet (it is written at story close; the review also asks to correct its `codeUnits`).

### The tests (display names start `[MAM-29 AC-6]`)

`GatewayProxyTimeoutTest` (MUST-FIX 1; the stub never answers; the gateway's read limit is 1 s; each test has `@Timeout(30)` in its own thread and each call a 6 s limit):

| Test | What it asserts (one focus per test) |
|---|---|
| `silentServiceIsAnswered503Problem` x8 (provider, enrollment, screening, risk, revalidation, sanctions, notification, directory; the first GET path of each contract) | status 503 and `application/problem+json` |
| `answerCarriesTheCorrelationId` | the problem's `correlationId` and the `X-Correlation-Id` header equal the id sent (`gw-timeout-1`) |
| `answerHasNoInternalDetail` | the body has status 503 and holds no `127.0.0.1`, no port of the stub, no `Exception`, no `timed out` |
| `answerIsTheUsualUnavailableProblem` | status 503, title `Service Unavailable`, detail `The risk service is not available. Try again later.` (the existing problem; a 504 or other value would be new behaviour and needs a question first) |
| `endsWithinABoundedTime` | the call ends in under 5 s |
| `isEndedByTheReadLimit` | the stub received the call and the answer came no earlier than the limit less 200 ms (so it is not an early error) |
| `flowingDownloadIsNotCutOff` (guard, green today) | the download of `GET /api/v1/legacy/provider-master` (headers and first part at once, 5 parts 0.5 s apart, about 2 s in all against a 1 s limit, no pause as long as the limit) arrives whole: 200, the full body, and it took at least 1.8 s |
| `stalledDownloadIsEndedWithinABoundedTime` | a download that sends its first bytes and then stalls (revalidation queue file) ends in under 5 s, whatever way (an error answer or a cut connection) |

`GatewayFailureLoggingTest` (MUST-FIX 2; `tmsis` is stopped; the log is read as console text through `OutputCaptureExtension` and as log events through an appender on the root logger):

| Test | What it asserts |
|---|---|
| `stoppedServiceIsLoggedOnce` | exactly one console line with the level WARN or ERROR names `tmsis` |
| `stoppedServiceLogShowsTheCause` | the console output holds `ConnectException` (the exception was passed to the logger, so its stack trace is written) |
| `logHoldsNoTokenAndNoBody` | after a POST with a bearer token and a body marker the failure is logged (so the test is not vacuous), and neither the token nor the body marker is in the output |
| `logLineCarriesTheCorrelationId` | the one WARN/ERROR event naming `tmsis` has `correlationId` = `gw-503` in its MDC |
| `logEventHoldsTheException` | that event's cause chain holds a class that ends in `ConnectException` |
| `timedOutCallIsLogged` | a call to the silent `risk` service is logged once at WARN/ERROR naming `risk`, and the cause chain holds a class whose name contains `Timeout` |
| `answerStaysThePlainProblem` (guard, green today) | 503 problem+json with `gw-503`, and no host, port, `Exception`, `Connect` or stack trace line in the answer: logging must not change what the caller gets |

`GatewayProxyTimeoutConfigTest` (MUST-FIX 1, the SHIPPED defaults; no Spring context, no network; it loads `api-gateway.yml` with the Boot YAML loader and binds the properties with the Boot binder,
placeholders resolved against the test's own overrides only):

| Test | What it asserts |
|---|---|
| `readLimitIsConfigured` | one of `spring.http.client.read-timeout`, `spring.cloud.gateway.server.webmvc.http-client.read-timeout`, `spring.cloud.gateway.mvc.http-client.read-timeout` is set and positive |
| `readLimitExceedsTheServicesOwnLimit` | it is longer than `ServiceClients.DEFAULT_READ_TIMEOUT` (5 s), so a service can still answer with its own timeout problem before the gateway gives up |
| `readLimitIsBounded` | it is at most 5 minutes |
| `connectLimitIsConfigured` | one of the three `...connect-timeout` names is set and positive |
| `connectLimitIsShort` | it is at most 10 s |
| `readLimitFollowsTheEnvironmentVariable` | with `PEM_GATEWAY_READ_TIMEOUT=7s` the read limit is 7 s |

### Decisions the tests contain (defaults; please confirm)

1. **The environment variable is `PEM_GATEWAY_READ_TIMEOUT`**: the name of the review's own example (`${PEM_GATEWAY_READ_TIMEOUT:30s}`); the lead's instruction only says "an environment variable with a
   default". The tests set it (like `PEM_LOG_FORMAT`) to `1s`; a different name makes `GatewayProxyTimeoutTest` and `readLimitFollowsTheEnvironmentVariable` fail. The default value itself is left to the developer.
2. **The window of the read limit is the test writer's reasoning, not a business rule**: above 5 s (the kit's limit for a service's own calls) and at most 5 minutes; connect at most 10 s. The review's example
   (2 s and 30 s) is inside it. If the MAM-88 smoke needs a longer limit, the upper bound is the one number to revisit.
3. **Any of the three property names the jar's configuration metadata defines is accepted** in the config test; that the proxy really honours the one that is set is proved by the behaviour tests, as the review asks.
4. **The test client's limits** (6 s per call, 5 s expected bound, `@Timeout(30)`) make a missing limit FAIL with a message instead of hanging the build.

### Red run

`./gradlew --no-daemon -I gw-spotless-tests-only.gradle :services:api-gateway:spotlessApply :services:api-gateway:test --tests ...GatewayProxyTimeoutTest --tests ...GatewayFailureLoggingTest --tests ...GatewayProxyTimeoutConfigTest`
(2026-11-21 06:13 to 06:15, 2 min 34 s, the lock waited for 869 s; Spotless applied to the test sources only). **28 tests, 26 failed, 2 passed, 0 skipped.** Every failure is the intended one:

- **`GatewayProxyTimeoutTest`, 14 failed: `The gateway gave no answer within 6 s to GET <path> although its read limit is 1 s: a call to a service that never answers has no limit`** (cause: the test client's
  `HttpTimeoutException`). The silent service holds the call for ever: the 8 parameterised cases, the five single-call tests and the stalled download. Without the 6 s limit these would hang.
- **`GatewayProxyTimeoutConfigTest`, 6 failed: `api-gateway.yml sets none of [spring.http.client.read-timeout, ...]` (and the same for the connect names).**
- **`GatewayFailureLoggingTest`, 6 failed.** `stoppedServiceIsLoggedOnce`, `logLineCarriesTheCorrelationId`, `logEventHoldsTheException`: `Expected size: 1 but was: 0` (nothing is logged). `stoppedServiceLogShowsTheCause`:
  the console output holds only start-up lines and does not contain `ConnectException`. `logHoldsNoTokenAndNoBody`: `[the failure is logged at all] Expecting actual not to be empty`. `timedOutCallIsLogged`: no answer within 6 s.
- **2 guards pass today, by design**: `flowingDownloadIsNotCutOff` (2.0 s) and `answerStaysThePlainProblem`.

### Finding: the review's fix, as written, cuts off a download that keeps flowing (proved in a scratch copy)

The review says of the read limit: "it is the time until response headers arrive, so streamed downloads are not cut off". To check that the tests can be satisfied, the review's fix was applied to a THROW-AWAY COPY of the
module (`scratchpad/gw-fixcheck`; the repository was not touched): `spring.http.client.connect-timeout: 2s`, `spring.http.client.read-timeout: ${PEM_GATEWAY_READ_TIMEOUT:30s}` in `api-gateway.yml`, and a `log.warn("{}-service call
failed: {}", service, req.method(), ex)` in the `onError` handler of `PemRoutes`. Results (Gradle runs under the shared lock):

| Run | Change in the copy | Result |
|---|---|---|
| A (first version of the tests, 27 new tests) | the review's fix as written (JDK request factory, Spring 6.2.19) | 26 of the 27 new tests green: **`spring.http.client.*` is the property the proxy honours**, the 503 problem and the log lines are as the tests expect. The 27th (the first download guard) red, see below. One more error in `GatewayRoutingTest`: a scratch artefact (the copy had no `contracts` folder). |
| B (first version) | A + `spring.http.client.factory: simple`, contracts added | 75 tests, 1 red: the same guard, whose 1.6 s silent gap exceeds the limit |
| C (final tests, as recorded) | A + `spring.http.client.factory: simple` | **`:services:api-gateway:check` BUILD SUCCESSFUL: 76 tests (48 existing + 28 new), 0 failed, 0 skipped, JaCoCo line 92.75 % (64 of 69), Spotless, `testSourceCheck` and `versionCheck` pass** |

What run A showed: with the JDK request factory the read limit covers the WHOLE exchange. A download whose body arrives slowly is cut when the limit is up: `java.io.IOException: closed` in
`AbstractProxyExchange.copyResponseBody` while the proxy copies the body, and the caller gets a bare **500** (not even the 503 problem). The first version of the guard (one silent gap of 1.6 s in the body) failed that way; it
also fails under a socket (idle) limit, which is how run B showed `SocketTimeoutException: Read timed out`. A silent gap longer than the limit is a stall, not a flowing download, and a limit that tolerated it would leave
a stalled body unbounded: the very defect of MUST-FIX 1. So the guard was replaced (before it reached the developer) by the pair that states the requirement: a download that **keeps flowing** is not cut off, however long it
takes in total; a download that **stalls** is ended in a bounded time. Both hold with `spring.http.client.factory: simple` (JDK `HttpURLConnection`: the read limit is a socket limit per read). Blobs of the first version (in
git's object store, unreferenced): `GatewayProxyTimeoutTest` `ca7ef4b22d47bbc455e1e5d35865ac6d70197474`, `SlowServiceStub` `a0efd75cf038d7a814a0091c1a2dd3d18ed10565`.

**The consequence for the developer:** the review's one-line fix makes 27 of the 28 new tests green and leaves `flowingDownloadIsNotCutOff` red. `spring.http.client.factory: simple` in `api-gateway.yml` (config only) makes
all 28 green (run C). It has trade-offs the developer must check and report: `HttpURLConnection` buffers request bodies, supports no HTTP PATCH (the contracts have none), and **retries a POST once when a reused keep-alive
connection turns out dead** unless the JVM runs with `-Dsun.net.http.retryPost=false`. Another way is a small custom `ClientHttpRequestFactory` bean. If the lead decides that a limit on the whole exchange is acceptable
(the limit sized to the slowest export), `flowingDownloadIsNotCutOff` has to be replaced, which needs the lead's decision under Q-097 C; it is left as written (never weakened to make it pass).

**A second defect, not tested (outside the two review items):** when the body copy fails after the response already carries the service's `Content-Type` (every file download and the master export are `text/plain` or
octet-stream), `GatewayProblems#handleException` cannot write its problem: `HttpMessageNotWritableException: No converter for [class org.springframework.http.ProblemDetail] with preset Content-Type 'text/plain'`, and the caller
gets an empty 500. What the answer should be then (reset and answer the same 503 problem while nothing is sent yet, or cut the connection) is new behaviour and needs a question first (default: the same 503 problem while the
response is not committed).

### Not tested, and why

- **The connect limit as behaviour**: it needs an address that neither answers nor refuses; no machine gives that reliably. Only the shipped value is tested (`connectLimitIsConfigured`, `connectLimitIsShort`).
- **The optional narrowing of the `onError` predicate** (a gateway defect ending in the 500 path instead of a misleading 503): a defect inside the route cannot be provoked without changing production code or a flaky trick.
- **The production log format** (ECS JSON): the tests use the plain Boot format like the other gateway tests (`PEM_LOG_FORMAT=`); the MDC test covers that the correlation id is on the log event, which ECS writes as a field.
- **A 504 for the timeout** and any other new status: the review says it needs a question first; the tests keep the existing 503 problem.

### Hand-over to the java-developer

Make the 26 red tests green and keep the 2 guards green, without touching any test file: (1) `api-gateway.yml`: a connect limit and a read limit `${PEM_GATEWAY_READ_TIMEOUT:<default>}` for the proxy, and a way for `flowingDownloadIsNotCutOff` to pass (see the
finding: `spring.http.client.factory: simple`, or a custom factory); say in the story report which default you chose and why (slowest calls: the whole master export, the batch file downloads; MAM-88 has not run). (2) `PemRoutes`: a
logger, and in the `onError` handler `log.warn("{}-service call failed: {}", service, req.method(), ex)` with the exception LAST (no header, token or body). Run
`./gradlew --no-daemon :services:api-gateway:check` under the lock.

## Test correction under Q-097 (second code review 2026-11-21, additive)

Cause: the second code review of api-gateway (2026-11-21, about 07:20, after the review fix of the section above) returned MUST-FIX with one item: with `spring.http.client.factory: simple` a service's 401 to a POST, PUT or DELETE does not reach
the caller whole (the review: `HttpURLConnection` throws `HttpRetryException` on a 401 when the request body is streamed, JDK 21 source lines 1809-1813; Spring 6.2.19 streams every write; lesson h and D-024 are broken for writes). Requested
fix: "a per-read client without this rule", Apache HttpClient 5 (`http-components`, needs an ADR first) or a gateway-owned JDK-HttpClient factory; the 28 tests of the first round stay green. The standing rule **Q-097 C** applies: additive, nothing
weakened, removed or edited, documented here and re-recorded; the code-reviewer confirms.

| Review item | AC | What the recorded tests miss |
|---|---|---|
| MUST-FIX: a service's 401 to POST, PUT, DELETE is lost | AC-2 (protected routes answer 401, D-023) and AC-6 (problem details and a correlation id, D-024) | `GatewayRoutingTest` only has stubs that answer 200; `SlowServiceStub` never answers with an error; `GatewayProxyTimeoutTest` sends GET only. No test sees what a write gets back from a service that says 401. |

**Additive.** No recorded file was edited: the blob ids of all 11 files recorded before (5 kit files, `GatewayRoutingTest`, `SlowServiceStub`, `UpstreamFailureTestBase`, `GatewayProxyTimeoutTest`, `GatewayProxyTimeoutConfigTest`,
`GatewayFailureLoggingTest`) are identical before and after. 5 new files are added to the hash file (11 -> 16 files). **No production file was touched:** the sha256 over the 6 production files of the module (`src/main/java/**`, `api-gateway.yml`,
`build.gradle.kts`) is `4d72388d829e...` before the first and after the last Gradle run (they were last modified at 06:55 today). The hash file before this correction is kept in the scratchpad (`MAM-29.tests.sha256.before-gw401`, 11 files).
No Cucumber scenario was added (AC-1..AC-6 of this story have none; the gateway tests are JUnit, as recorded).

### Files (all under `modern/services/api-gateway/src/test/java/gov/medicaid/pem/apigateway/`; CRLF kept, formatted by Spotless on test sources only)

| File | Blob id (recorded) | Executions |
|---|---|---|
| `AnsweringServiceStub.java` (JDK HttpServer that always answers as a protected service does: problem+json for an error, `WWW-Authenticate: Bearer` for a 401, `Location` for a 202, a download of a given size with a `Content-Disposition`, or closes the connection without a word; records every call; steered by request headers `X-Stub-*`) | `4463335d6add6d398345dd0fb6b86f273278aa74` | 0 |
| `ServiceAnswerTestBase.java` (abstract: one gateway context with the shipped `api-gateway.yml` and all nine services pointing to the stub; a request builder; every call has a limit for the WHOLE answer) | `9e3c6fa105a44c41621a0d21d961907be5e439aa` | 0 |
| `GatewayRelaysServiceErrorTest.java` | `3243de8b41ffdf4676224cc57d2513099db95540` | 86 (8 methods) |
| `GatewayRelaysWritesTest.java` | `45fb7e85f4b388cf455e5de7b494911c1d1b41b7` | 34 (14 methods) |
| `GatewayWriteTimeoutTest.java` (extends the recorded `UpstreamFailureTestBase`: read limit 1 s, silent stub) | `96b8b4b35d54739d55cd5ddbcd5b5b106565e8e1` | 9 (3 methods) |

129 new test executions in 25 test methods. New hash file `modern/docs/reports/stories/MAM-29.tests.sha256` = 16 files, sha256 `74bacd0f5f85815053e303fc0dad90da1185d8df0e1202c8a5fb82c5e9281793`.
`test_protection.py check MAM-29`: OK (16 files unchanged, no unrecorded test file). `trace.json` of MAM-29 does not list the 5 new files yet (it is written at story close). `testsSha256` in `specs/MAM-29.md` is set by the lead.

### The tests (display names start `[MAM-29 AC-n]`; every test has `@Timeout(30)` in its own thread, every call a limit of 5 s for the whole answer)

`GatewayRelaysServiceErrorTest`, group 1 (the review item; the stub is told to answer 401, the call carries no token; five calls: POST `/api/v1/applications` with a JSON body, PUT `/api/v1/risk-rules` with a JSON body,
DELETE `/api/v1/providers/1003000111`, POST `/api/v1/batch/revalidation-sweeps` without a body, and GET `/api/v1/providers` as control). Tag `[MAM-29 AC-2/AC-6]`. One property per test, so a failure names what is lost:

| Test | What it asserts |
|---|---|
| `unauthorizedStatusIsRelayed` | the caller gets status 401 |
| `unauthorizedIsProblemJson` | the content type is `application/problem+json` |
| `unauthorizedBodyIsIntact` | the body equals, byte for byte, the problem the service wrote |
| `unauthorizedKeepsTheBearerChallenge` | `WWW-Authenticate: Bearer` arrives |
| `unauthorizedCarriesTheCorrelationId` | the `X-Correlation-Id` of the answer is the one sent |
| `unauthorizedCallIsNotRepeated` (guard) | the service was asked exactly once (the lost answer is not judged here, see the comment in the test) |

`GatewayRelaysServiceErrorTest`, group 2 (guards, tag `[MAM-29 AC-6]`): `errorStatusIsRelayed` and `errorBodyIsRelayed`, for GET, POST, PUT and DELETE and the service statuses 400, 403, 404, 409, 422, 500, 503: the status and the
problem body arrive as the service wrote them (a client that retries or rewrites an answer would show here).

`GatewayRelaysWritesTest` (what a replacement client must keep; tags as named):

| Test | Tag | What it asserts |
|---|---|---|
| `bodyReachesTheServiceUnchanged` (POST, PUT, multipart POST) | AC-1 | the service receives the body byte for byte (JSON with non-ASCII letters; a multipart upload) |
| `contentTypeReachesTheService` (same three) | AC-1 | the service receives the same media type, and the same multipart boundary |
| `tokenReachesTheServiceOnWrites` (POST, PUT, DELETE) | AC-3 | the service receives exactly the `Authorization` header that was sent |
| `correlationIdReachesTheServiceOnWrites` (POST, PUT, DELETE) | AC-5 | the service receives the `X-Correlation-Id` that was sent |
| `postWithoutBodyReachesTheServiceEmpty` | AC-1 | a run start (POST without body) arrives as a POST with an empty body |
| `largeUploadReachesTheServiceWhole` | AC-1 | a multipart upload of 13,000 records of 80 bytes (1.04 MB) arrives whole (sha-256) |
| `largeDownloadArrivesWhole` (with and without a content length) | AC-1 | a download of 2,000,000 bytes arrives whole (sha-256) |
| `downloadKeepsItsFileName` | AC-1 | the `Content-Disposition` of a file download arrives |
| `acceptedAnswerKeepsItsLocation` | AC-1 | the `Location` of a 202 arrives as written |
| `patchReachesTheService`, `patchAnswerReachesTheCaller` | AC-1 | a PATCH reaches the service with its body, and the service's 200 reaches the caller |
| `closedConnectionIsAnswered503` (POST, PUT, DELETE) | AC-6 | a service that closes the connection without an answer is answered with the 503 problem |
| `writeIsNotRepeatedAfterAClosedConnection` (POST, PUT, DELETE) | AC-6 | the write reached the service exactly once |
| `serviceAnswerIsNotRetried` (GET, POST, PUT, DELETE x 429, 503) | AC-6 | a call that the service answers 503 or 429 reached the service exactly once (Apache's default retry strategy repeats both) |

`GatewayWriteTimeoutTest` (guards, tag `[MAM-29 AC-6]`; the stub never answers; POST `/api/v1/risk-assessments`, PUT `/api/v1/risk-rules`, DELETE `/api/v1/risk-rules/legacy-1`): `silentServiceWriteIsAnswered503Problem` (503
and problem+json), `silentServiceWriteIsEndedByTheReadLimit` (between the 1 s limit less 200 ms and 5 s), `silentServiceWriteIsNotRepeated` (the stub saw the write once). The first round tested the read limit with GET only.

### Decisions the tests contain (defaults; please confirm)

1. **A write is never repeated by the gateway, and a service's own 503 or 429 is relayed once** (`writeIsNotRepeatedAfterAClosedConnection`, `serviceAnswerIsNotRetried`, `silentServiceWriteIsNotRepeated`, `unauthorizedCallIsNotRepeated`). No
   legacy rule or ADR says it; the basis is the first review fix's own note ("a POST is never repeated") and the fact that a repeated PUT of the risk rules would create a second version. Default: keep. Retries of idempotent calls would be new behaviour and need a question first.
2. **DELETE is tested although no contract has a DELETE operation**: the review names it and the route table forwards every method of a path.
3. **PATCH (2 tests, red)** is not part of the review's item. It is a regression guard for a method the gateway's own CORS answer allows and the route table forwards, which `HttpURLConnection` cannot send (the first review fix's developer note (b);
   seen in the red run: `ProtocolException: Invalid HTTP method: PATCH`, answered 503). No contract has a PATCH. **Keeping or dropping the two tests is the lead's decision** (they are new, nothing recorded depends on them).
4. **Test client:** the first version bounded only the arrival of the response headers (`HttpRequest.timeout`), and the 401 calls then hung for the 30 s guard each (run 1 below). The final client bounds the WHOLE answer (5 s) and names the part that
   arrived. Tag of the 401 tests is `[MAM-29 AC-2/AC-6]` (the story's AC-2 says protected routes answer 401; AC-6 asks for problem details with a correlation id).
5. **Media type, not byte-equal content type** in `contentTypeReachesTheService`: see finding 3.

### Red runs (2026-11-21; shared Gradle lock; free memory 1.7 to 1.8 GB at the start of each run)

Command (the same for the three runs): `./gradlew --no-daemon -I gw-spotless-tests-only.gradle :services:api-gateway:spotlessApply :services:api-gateway:test --tests ...GatewayRelaysServiceErrorTest --tests ...GatewayRelaysWritesTest --tests ...GatewayWriteTimeoutTest`
(Spotless applied to the test sources only).

| Run | Time | Result | Remark |
|---|---|---|---|
| 1 (first version) | 07:35 to 07:48, 13 min 14 s, lock waited 3 s | 129 executions, 29 failed | 24 = the 401 calls of the four writes, each ended only by the 30 s guard (the client limit covered the headers only); 2 = PATCH; 3 = `contentTypeReachesTheService`, a wrong assumption of the test (finding 3), corrected before recording |
| 2 | 07:54 to 07:57, 3 min 16 s, lock waited 3 s | 129, 22 failed | the client now bounds the whole answer; the content type test compares media types; `unauthorizedCallIsNotRepeated` no longer fails on the lost answer |
| 3 (the recorded files) | 08:02 to 08:07, 4 min 31 s, lock waited 187 s (held by another lane; free memory fell to 910 MB for a short time) | **129 executions, 22 failed, 107 passed, 0 skipped**, `BUILD FAILED` as intended | Javadoc corrections only since run 2; Spotless reformatted two files, which were re-recorded |

**The 22 red tests and why they fail:**

- **20 x `GatewayRelaysServiceErrorTest` (the five properties of group 1, each for POST with body, PUT, DELETE, POST without body):** `AssertionError: The gateway did not complete its answer (status, headers and body) within 5 s to POST /api/v1/applications: the status
  line and headers arrived (401 {content-length=[137], content-type=[application/problem+json], www-authenticate=[Bearer], x-correlation-id=[gw-r58], ...}), the body did not complete`. The same for PUT (content-length 135), DELETE (145) and POST without a body.
  The GET control passes for all five properties (the failure is the write).
- **2 x `GatewayRelaysWritesTest`:** `patchReachesTheService`: `Expected size: 1 but was: 0` (the stub never saw the PATCH); `patchAnswerReachesTheCaller`: `expected: 200 but was: 503`.
- **107 pass:** the six GET controls, `unauthorizedCallIsNotRepeated` for the four writes, the 56 error-status executions, 32 of `GatewayRelaysWritesTest` (bodies, media types, tokens, correlation ids, 1 MB upload, 2 MB downloads, headers, closed connection, no retry) and all 9 of `GatewayWriteTimeoutTest`. They are guards, green with the current factory, so a replacement that breaks one is caught at once.

### Findings

1. **The failure is worse than "an empty body": the gateway sends the status line and all headers of the 401 (with the service's `Content-Length`) and then NO body, and leaves the connection open.** A client waits for bytes that never come
   (observed: the 10 s `HttpRequest.timeout` of the first test client, which ends at the headers, never fired; only the 30 s guard ended the call; a caller without a limit of its own would wait as long). A web UI call with an expired token (POST, PUT, DELETE) therefore does not get the 401 it needs to show the login. The gateway logs
   nothing about it (no WARN line in the run output; the `HttpRetryException` of the review is not in the log), so it is not the `onError` path; the cause is not traced here. The second defect named in the story report (a body copy that fails after the
   response carries the service's `Content-Type`; `GatewayProblems` cannot write its problem) may be the same area; it is still untested (new behaviour needs a question first).
2. **The review's cause is not proven by these tests, and they do not depend on it.** They assert what reaches the caller, so they hold for any request factory.
3. **`Content-Type` of a request arrives with `;charset=UTF-8` appended** (`application/json;charset=UTF-8`; multipart: `multipart/form-data;boundary=...;charset=UTF-8`, the space removed). This is not a defect of the client: the gateway's servlet layer
   adds it before the proxy forwards (probably Spring's `ServletServerHttpRequest`, which copies the request encoding into the header; inference, not traced), so every client would show it. It is the same media type; the first version of the test (byte-equal) was wrong and is
   corrected; the corrected test compares type, subtype and boundary.
4. **Origin and Host:** the review fix's note asks whether anything relies on these headers (`HttpURLConnection` does not forward them). A search of `services/*/src/main` and the kit finds a CORS configuration only in the gateway (`PemRoutes`); no service reads Origin. Nothing to test.

### Not tested, and why

- **The ADR for Apache HttpClient 5** (a new dependency): a document, not behaviour. The developer must write it before the code if that option is chosen.
- **The wire-level cause** and a 401 that a service sends before it has read the request body (Spring Security does that): the stub reads the body first, as the review's own repro does. A body larger than the socket buffers could behave differently; not covered.
- **HTTP/2, TLS, response compression and redirects:** not used between gateway and services today.
- **The second defect** (empty 500 when the body copy fails after the Content-Type is set): new behaviour, needs a question first (default proposed by the first review fix: the same 503 problem while the response is not committed).

### Hand-over to the java-developer

Make the 20 red 401 tests (and, if the lead keeps them, the 2 PATCH tests) green, keep the 107 green tests, the 76 tests of the first round (48 recorded + 28) and the 129 here green, and change no test file. Run `./gradlew --no-daemon :services:api-gateway:check` under the lock. Points the tests will catch:

- **Apache HttpClient 5** (`spring.http.client.factory: http-components`): write the ADR first. Check that Boot's read timeout is a per-read (socket) limit: `flowingDownloadIsNotCutOff` and `stalledDownloadIsEndedWithinABoundedTime` decide. Automatic retries must be OFF: Apache's default strategy retries an idempotent call (GET, PUT, DELETE) once
  after an I/O error and retries a 429 or 503 answer; `writeIsNotRepeatedAfterAClosedConnection` and `serviceAnswerIsNotRetried` fail otherwise.
- **A gateway-owned JDK-HttpClient factory:** the JDK factory of Spring 6.2.19 limits the WHOLE exchange (`flowingDownloadIsNotCutOff` goes red), so the per-read limit has to be built into the factory.
- **Keep the Boot property names** `spring.http.client.read-timeout` and `connect-timeout` in `api-gateway.yml` (`GatewayProxyTimeoutConfigTest` reads them, with `PEM_GATEWAY_READ_TIMEOUT`).
