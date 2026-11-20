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
