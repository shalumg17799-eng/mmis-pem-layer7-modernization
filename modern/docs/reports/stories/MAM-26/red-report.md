# MAM-26 red report

The kit tests recorded under MAM-26 before this section are listed in `MAM-26.tests.sha256` (4 files:
`HelpersTest`, `ServiceTokenProviderTest`, `PemKitAutoConfigurationTest`, `SecurityKitTest`); the key switch is in
`key-switch.md`. This file did not exist before; it starts with the section below.

## Kit tests added 2026-11-20 (Q-097 additive)

Scope: the newest code of the shared library `libs/service-kit`, which every service uses and which had almost no tests
of its own. Additive only (Q-097 C: nothing weakened, removed or edited; new test classes). **No production file was
touched**: the sha256 of all 24 files under `libs/service-kit/src/main` is identical before and after.

**These tests were written after the code, so they were green at once: there is no red phase.** To show that they are
not vacuous, they were run against deliberately broken copies of the production code (section "Mutation check").

### Which AC each group belongs to, and why

The MAM-26 story is the Keycloak realm; none of its six ACs is about the body of a 401 or the timeout of a client, so the
nearest AC was chosen (stated here so the reviewer can overrule it):

| Group | AC | Why |
|---|---|---|
| `BearerProblemHandlerTest`, `BearerProblemFilterChainTest` | **MAM-26 AC-2** (each token carries its own role) | the 401 and 403 of the filter chain are the enforcement of those realm roles: no token or an unusable token is 401, a valid token without the role is 403 (`KeycloakJwtConverter` turns the roles into authorities in the same chain) |
| `ServiceClientsTimeoutTest`, `PemClientTimeoutConfigurationTest` | **MAM-26 AC-5** (service account with the SYSTEM role) | the clients and the token client of `ServiceClients` / `ServiceTokenProvider` are the service-to-service calls that AC-5 describes (SYSTEM token by client credentials) |

### New files (blob ids are `git hash-object`; they are also in `red-blobs.txt`)

| File (under `modern/libs/service-kit/src/test/java/gov/medicaid/pem/kit/`) | Blob id | Tests |
|---|---|---|
| `client/ServiceClientsTimeoutTest.java` | `f032ac339a62b118519b16e125ef82f0f614b82d` | 11 |
| `config/PemClientTimeoutConfigurationTest.java` | `944ada212321ecdf002e9ffe894e5dcf3f9aadf6` | 6 |
| `security/BearerProblemHandlerTest.java` | `d075f8068fa78e02d90510ce941039a63df7336a` | 8 |
| `security/BearerProblemFilterChainTest.java` | `6a0102891a5f103dfda162915b912ce8d2613525` | 18 |
| `client/SilentServer.java` (helper: accepts the connection, never answers) | `ba5df9e32e91b6ed278009cfd5325c627d4ab7c2` | - |
| `client/StubHttpServer.java` (helper: `/token`, `/echo`, `/slow` on loopback) | `1ac4f8136816e342c5c512180a02e50a0b696cd2` | - |
| `client/CallProbe.java` (helper: failure and elapsed time of a call) | `35922d1d50d7b4254f8e8af84cb1d3ee0256ca92` | - |

43 new tests. Re-recorded: `MAM-26.tests.sha256` now lists 11 files (the 4 recorded files unchanged, 7 new). The new
sha256 of the hash file is `52feabfc98444c1923c9332e08799a9fc774637343737a9614fea930ec2aa90d` (the `testsSha256` in
`specs/MAM-26.md` is still the old `9fc4035a...`; the lead updates it).

### Tests

`BearerProblemHandlerTest` (unit, `MockHttpServletRequest/Response`):
- no token: 401 and `WWW-Authenticate: Bearer`; body `application/problem+json`, title Unauthorized, status 401, short detail
- invalid token: Spring's own challenge kept (`error="invalid_token"`, equal to Spring's), body does not repeat the reason
- valid token without the role: 403 and Spring's own `insufficient_scope` challenge (equal to `BearerTokenAccessDeniedHandler`'s);
  body title Forbidden, status 403; no bearer principal: 403 with the plain `Bearer` challenge
- 401 and 403 bodies: only the problem fields (title, status, detail required), no exception text, host name or class name

`BearerProblemFilterChainTest` (the real chain of `PemSecurityAutoConfiguration` through MockMvc, real RS256-signed JWTs, a
decoder that trusts a test key, fixed clock 2026-01-15T10:00:00Z):
- no token on a protected path: 401, `Bearer`, problem+json, Unauthorized/401/short detail; problem+json for `Accept`
  `application/json`, `*/*`, `text/html`
- garbage, expired and wrong-signature tokens: 401, `error="invalid_token"`, problem+json; body gives no decoder reason
- PROVIDER, ANALYST, SUPERVISOR and a role-less token on the admin-only `/actuator/info`: 403, `insufficient_scope`,
  problem+json Forbidden/403; no stack trace or internal detail in either body
- controls: ADMIN passes the admin-only path (not 401/403); `/actuator/health` needs no token

`ServiceClientsTimeoutTest` (a loopback server that accepts the connection and never answers; short read limit 600 ms, connect
limit 8 s so a swap would show; every test has a 30 s guard in its own thread, so a missing limit FAILS and never hangs):
- the call ends with `ResourceAccessException` within the read limit; it is a timeout of a held connection, not an earlier error
  (elapsed at least 500 ms, the server did accept)
- `create(url, Duration)` replaces the configured limits; the default (5 s) applies when nothing is configured
- `ServiceClients.builder(...)` ends an unanswered call within its limit; it passes the connect limit to the HTTP client and the
  read limit to the request factory (white box through `ReflectionTestUtils`, the only way to see the connect limit)
- the token client ends an unanswered token request within its limit
- controls over a real connection: a slow answer within the limit is accepted; the SYSTEM token and the correlation id are still sent

`PemClientTimeoutConfigurationTest` (`WebApplicationContextRunner` with `PemWebAutoConfiguration` and `PemClientAutoConfiguration`):
- defaults connect 2 s and read 5 s; `pem.client.connect-timeout` / `read-timeout` (`1500ms`, `PT3S`) override them
- the configured read limit applies to the `ServiceClients` bean and to the `ServiceTokenProvider` bean (unanswered call and unanswered
  token request fail within it); with no limit configured the token client gives up after the default; a call fails within the
  limit when the token endpoint is silent

### Run

`./gradlew --no-daemon :libs:service-kit:check --continue` (2026-11-20, one build at a time; the Gradle lock was free, free memory
1.3 to 2.5 GB, no waiting for memory): **BUILD SUCCESSFUL**. Tests of the module: 110 (41 existing + 69 new of MAM-26 and MAM-29),
0 failed, 0 skipped. Spotless check passed on main and test sources (test sources formatted only, with an init script restricted
to `src/test`). JaCoCo line coverage of the kit **91.85 %** (338 of 368 lines; gate 85 %).

### Mutation check (throw-away copy of the kit under the session scratchpad; the repository was only read)

Two runs of `:libs:service-kit:test` in the copy: run 1 with M1 to M10 together, run 2 with M1, M3b, M9, M11, M13 (so each
mutant that hides behind another in run 1 was also seen alone).

| Mutant (production code of the copy) | Failed tests |
|---|---|
| M1 `BearerProblemHandler.commence` does not write the body | `noTokenBodyIsAProblem`, `invalidTokenKeepsSpringChallengeAndHidesTheReason`, `bodiesHaveOnlyTheProblemFields[401]` |
| M2 `handle` skips Spring's 403 handler | `missingRoleIs403WithSpringChallenge` (status 200), `deniedWithoutTokenPrincipalIs403` |
| M3 `.accessDeniedHandler(problems)` not wired | chain `tokenWithoutTheRoleBodyIsAProblem` |
| M3b `.authenticationEntryPoint(problems)` not wired | chain: no-token body, 3 `Accept` cases, 3 unusable tokens, no-reason, `bodiesHaveOnlyTheProblemFields[401]` |
| M13 body written as `application/json` | content-type tests of 401 and 403 (unit and chain) |
| M6 `factory.setReadTimeout` removed | 6 client tests FAIL at the 30 s guard (no hang), `builderSetsTheReadLimit`, `serviceClientsUseTheConfiguredReadLimit` (guard) |
| M7 connect timeout not set on the HTTP client | `builderSetsTheConnectLimit` |
| M8 token client built without timeouts | `tokenClientUsesTheConfiguredReadLimit`, `tokenClientHasADefaultLimit`, `callFailsWhenTheTokenEndpointIsSilent` (30 s guard) |
| M9 connect and read limits swapped in `PemClientAutoConfiguration` | `serviceClientsUseTheConfiguredReadLimit` (elapsed 8.0 s, bound 4 s) |
| M10 default read limit 50 s | `defaultsComeFromTheKit` (expected 5 s, was 50 s) |
| M11 `create(url, Duration)` ignores its argument | `singleLimitReplacesTheConfiguredLimits` (30 s guard) |

A first version of the two "no leak" tests passed on an empty body (M1, M3); they were strengthened before recording (the title,
status and detail fields must be present, the body must not be blank) and the mutants M1 and M3b run again: now they fail.

### Not verified

- The **connect** limit is checked only through the builder's internals (white box): a black-box test needs a host that holds the
  TCP handshake, which a loopback test cannot do portably.
- The real Keycloak and JWKS download are not used (a decoder with a test key); the default `JwtDecoder` bean is only checked to exist
  (`PemKitAutoConfigurationTest`).
- The correlation id on the 401 and 403 (its filter runs before the chain in a container) is not tested: MockMvc does not run that filter.
- Two default-limit tests wait about 5 s each (the documented default read limit).
