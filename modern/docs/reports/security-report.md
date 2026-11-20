# Security report (MAM-84)

Written by the lead on 2026-11-20 from the runs named below. Every check is repeatable with the command shown; none was
typed by hand. Stack: Docker Compose project `pem` on this machine, synthetic data only.

## Summary

| AC | What | Result |
|---|---|---|
| AC-1 | Every endpoint answers correctly for every role | PASS: 42 operations x 5 roles + no token, through the gateway |
| AC-2 | Contracts and the role table do not drift | PASS (after the table in `docs/architecture/nfr.md` got the 3 run-status calls of Q-105) |
| AC-3 | A provider only sees their own data | PASS |
| AC-4 | Forged and expired tokens are refused | PASS: 6 of 6 |
| AC-5 | No high or critical vulnerable dependency | PASS after deploy 2 (2026-11-20 18:46): the scan of the deployed jar shows 2 findings, both spring-webmvc with a written justification (Q-122); 0 HIGH or CRITICAL findings without a justification (before the upgrade: 33 findings, 19 HIGH or CRITICAL) |
| AC-6 | No secret in any file | PASS: clean |
| AC-7 | Only health is open on the actuator | PASS after deploy 1 (2026-11-20 17:07): the defect found on the gateway (`/actuator/info` and `/actuator/metrics` open without a token) is fixed, the gateway and the nine services expose only health (Q-121, default A) |
| AC-8 | Optional ZAP baseline scan | NOT RUN: the user has not agreed to the optional scan |

## AC-1, AC-2, AC-3, AC-7: live checks through the gateway

Command (from `modern/`, stack up and seeded):
`kag/.venv/Scripts/python.exe -m pytest tools/checks/live/test_security.py -q`

- The operations and their roles are read from the nine OpenAPI contracts (`x-roles`): 42 operations, 4 of them PUBLIC
  (the directory search and the three FHIR searches).
- **AC-2:** the drift test compares the contracts with the Security table of `nfr.md` and names every operation that
  differs. First run: 3 operations were only in the contracts (the run-status calls added under Q-105); the table was
  brought up to date; now 42 = 42 with equal roles. A control test proves a changed role is detected.
- **AC-1:** no token on each of the 38 protected operations: 401. Each role that is not listed for an operation: 403
  (a complete request body is sent where needed, so a validation error cannot hide the 403). Each listed role: never
  401 or 403 (a PROVIDER on its own NPI). The 4 public operations: 200 without a token.
  What "listed role" proves here: the role gets through authorisation with a harmless request (a read, or a body the
  service refuses after authorising the caller). That each listed role can really perform each write is proven by the
  journeys J1 to J7.
- **AC-3:** provider1 asking for another provider's NPI gets 403; its lists of applications and notices hold only its own
  items while another provider's items really exist; a filter for another NPI is refused or shows nothing foreign.
- **AC-7:** each of the nine services answers 200 for `/actuator/health` and 401 for `/actuator/info` and
  `/actuator/metrics` without a token (200 with an ADMIN token).
  **Defect found:** the api-gateway answered 200 without a token for `/actuator/info`, `/actuator/metrics` and
  `/actuator/metrics/{name}`. The gateway has no token check of its own (it relays the token). Fix (Q-121, default A):
  the gateway no longer exposes info and metrics at all (`management.endpoints.web.exposure.exclude` in
  `services/api-gateway/src/main/resources/api-gateway.yml`); they answer 404 there, which is stricter than the 401 the
  AC names. The three failing tests were written first (run of 2026-11-20 15:59: 275 passed, 3 failed); the gateway
  module check passes with the fix (48 tests); live result after the deploy: see "Runs".
- Known behaviour, not a defect of the AC: on a few write operations a role that is not allowed gets 400 instead of 403
  when it sends an invalid body (validation runs before the role check). With a valid body it gets 403.

## AC-4: tokens

Command: `kag/.venv/Scripts/python.exe -m pytest tools/checks/live/test_security_tokens.py -q` (about 16 minutes: it waits
for a real token to expire). Run of 2026-11-20 16:00-16:16: 6 passed.

| Token | Answer |
|---|---|
| real ANALYST token (control) | 200 |
| same header and claims, signed with another key | 401 |
| real signature under changed claims (ADMIN added) | 401 |
| unsigned (`alg: none`) | 401 |
| really issued by Keycloak, but for the realm `master` | 401 |
| the real token after its expiry time (lifespan 15 minutes, plus 60 s of allowed clock skew) | 401 |

## AC-5: vulnerable dependencies

Command: `kag/.venv/Scripts/python.exe tools/checks/scan_dependencies.py` -> `docs/reports/dependency-scan.md`.
The AC names the OWASP dependency check and npm audit; neither is installed on this machine (the OWASP plugin needs the
NVD download and an API key). The scan uses the public OSV database instead, with the exact versions that run: the
third-party jars inside the deployable and every package of `web-ui/package-lock.json`. Only open-source package names
and versions are sent.

Scan of 2026-11-20 16:03 (deployable built 2026-11-19 19:01): 107 Java libraries, 314 npm packages. 33 advisories, all in
Java libraries (web UI: none): 8 CRITICAL, 11 HIGH, 13 MODERATE, 1 LOW.

| Library | In use | Fixed in | Action (Q-122, default A) |
|---|---|---|---|
| tomcat-embed-core | 10.1.55 | 10.1.56 to 10.1.59 | raise to 10.1.59 |
| jackson-core, jackson-databind | 2.21.4 | 2.21.7 | raise to 2.21.7 |
| postgresql (JDBC driver) | 42.7.11 | 42.7.12 | raise |
| snappy-java | 1.1.10.5 | 1.1.10.9 | raise |
| lz4-java | 1.10.1 | 1.11.4 | raise |
| bcprov-jdk18on | 1.80.2 | 1.85 | raise (or remove if only a test library brings it) |
| log4j-api (MODERATE) | 2.24.3 | 2.25.5 | raise |
| spring-webmvc (2 CRITICAL) | 6.2.19 | only 7.0.9 (Spring Boot 4) | justified: needs Server-Sent Events with view fragments, or XsltView; neither is used (`tools/checks/dependency-justifications.json`) |

The upgrade is a build change with a regression run before it is deployed; the scan is repeated on the new deployable
(see "Runs").

### Library upgrade of 2026-11-20 (Q-122)

Default A of Q-122 applied in the build (Spring Boot 3.5.16 and Spring Cloud 2025.0.3 unchanged, no new major version): the versions are keys in `gradle/libs.versions.toml`, applied to every module as constraints in `buildSrc/.../pem.java-conventions.gradle.kts` (ADR-010). Resolved on the run-time class path of `:infra:local-runtime` (all ten services):

| Library | Before | After |
|---|---|---|
| tomcat-embed-core, -el, -websocket | 10.1.55 | 10.1.59 |
| jackson-core, jackson-databind and the other Jackson modules (jackson-annotations stays 2.21: it has no patch version) | 2.21.4 | 2.21.7 |
| postgresql (JDBC driver) | 42.7.11 | 42.7.12 |
| snappy-java | 1.1.10.5 | 1.1.10.9 |
| lz4-java | 1.10.1 | 1.11.4 |
| bcprov-jdk18on (brought by spring-cloud-starter, compile scope; not a test library) | 1.80.2 | 1.85 |
| log4j-api, log4j-to-slf4j | 2.24.3 | 2.25.5 |

Regression of the modules that are already closed, run on the new versions (`gradlew :libs:fixed-width:test :libs:service-kit:check :services:api-gateway:check :services:risk-service:test :services:sanctions-service:test :services:directory-service:test`, 2026-11-20 17:24 to 17:51, BUILD SUCCESSFUL): fixed-width 257, service-kit 110 (check: JaCoCo, Spotless, versionCheck), api-gateway 48 (check), risk 351, sanctions 326, directory 433 tests; 0 failed, 0 skipped. `help` and `versionCheck` pass.
The 33 advisories are closed in the scan only when the deployable is rebuilt and the scan is repeated; that is the lead's step with the deploy.

## AC-6: secrets

Command: `kag/.venv/Scripts/python.exe tools/checks/scan_secrets.py`. Run of 2026-11-20 16:01: CLEAN.
- `modern/infra/.env.local` (the generated local passwords) is git-ignored.
- None of its 16 values appears in any of the 1,695 tracked or untracked, non-ignored files.
- No private key block, cloud or VCS token, signed JWT or literal credential in any file. One test constant named
  `SECRET` (a made-up internal detail that the test proves is not leaked) is allow-listed with its reason.

## AC-8: ZAP baseline scan

Not run. The AC makes the scan optional and asks for the user's agreement first; the user has not been asked to agree
and no scanner image was pulled.

## Runs

| When | What | Result |
|---|---|---|
| 2026-11-20 15:59 | test_security.py against the deployable of 2026-11-19 19:01 | 275 passed, 3 failed (gateway actuator: the defect above) |
| 2026-11-20 16:00-16:16 | test_security_tokens.py | 6 passed |
| 2026-11-20 16:01 | scan_secrets.py | CLEAN |
| 2026-11-20 16:03 | scan_dependencies.py | 33 advisories, 19 HIGH or CRITICAL, 2 justified |
| 2026-11-20 17:06 | test_security.py against deploy 1 (the gated risk, sanctions and directory code plus the gateway actuator fix) | **278 passed**, 0 failed (78 s); J1 and J2 passed before it |
| 2026-11-20 17:27 | secrets scan of the content staged for the checkpoint commit 2e20ead (1,716 files; same patterns and the 16 values of `.env.local`) | CLEAN |
| 2026-11-20 18:42 | scan_dependencies.py on the jar built 18:36 with the upgraded libraries (107 Java libraries, 314 npm packages) | 2 findings (CRITICAL 2, both spring-webmvc 6.2.19, justified); HIGH or CRITICAL without justification: **0**; exit code 0 |
| 2026-11-20 18:45 | test_security.py against deploy 2 (the upgraded libraries; J1 and J2 passed before it) | **278 passed**, 0 failed (42 s) |
