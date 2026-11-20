# Developer guide: how a story is built here

For the build agents (test-writer, java-developer, ui-developer, code-reviewer) and for people. Read it with
`.claude/skills/tdd-workflow/SKILL.md` and `.claude/skills/java-microservices/SKILL.md`. The walking skeleton
(MAM-34, provider-service) and the gateway (MAM-29) are the worked examples: copy their patterns.

## 1. Machine rules (small shared machine)

- **One Gradle build at a time**, always `./gradlew --no-daemon ...` from `modern/` (a lingering daemon holds about 1 GB).
- Never start, stop or change Docker containers that are not in compose project `pem`. Never prune images or volumes.
- The local stack (`make up`) may be running; integration tests use their own Testcontainers (Postgres
  `postgres:16.4-alpine`, Kafka `apache/kafka-native:4.3.2`) through `gov.medicaid.pem.testsupport.PemContainers`.
- No git commits (the user commits). No secrets in files: passwords live only in `modern/infra/.env.local`.

## 2. Story keys and the spec check

- Stories are Jira MAM issues. The spec is `modern/specs/<KEY>.md` (frontmatter + the description as written to Jira).
- Before building, compare the Jira description with the spec body (read Jira through the Atlassian MCP, cloudId
  `radiantexp.atlassian.net`). Normalise both: drop backslash escapes, turn `* ` list markers into `- `, collapse all
  whitespace. If they still differ, **stop the story** and report the differences (MODERNIZATION.md section 8).
- Jira statuses: To Do, In Progress, Done. "In review" = In Progress + label `in-review` (Q-013). Never move to Done.

## 3. Red: tests first (test-writer)

- One Cucumber feature per story: `services/<svc>/src/test/resources/features/<KEY>.feature`, tagged `@<KEY>`, one
  scenario per AC tagged `@AC-<n>`. Steps in `gov.medicaid.pem.<svc>.acceptance` (one `CucumberSuiteTest` and one
  `CucumberSpringConfig` per service; create them with the first story of the service, as in provider-service).
- Display names start with the key and AC: `@DisplayName("[MAM-46 AC-3] ...")`.
- Unit tests for domain rules live in the `domain` / `application` packages (PIT mutates only those and runs only those
  tests). Integration tests extend the service's `IntegrationTest` base (`@SpringBootTest(properties =
  {"spring.config.name=<svc>-service", "PEM_LOG_FORMAT="})`, `@AutoConfigureMockMvc`,
  `@ImportTestcontainers(PemContainers.class)`), so all of them share one Spring context.
- Helpers in `libs/test-support`: `PemJwt` (signed-in users by role, `PemJwt.provider(npi)`), `OpenApiContract.of(
  "<svc>-service").assertResponse(operationId, status, body)` (contract test), `EventProbe` (await or send events on the
  Testcontainers Kafka; topics `pem.<svc>.events`), `PemArchitecture.check(basePackage)` (ArchUnit layering).
- Use a fixed `Clock` for business dates (2026-01-15 unless the AC says otherwise); synthetic data only (the NPIs in the
  ACs, `aws-transform/test-data/`).
- Run the tests: every new one must fail for the expected reason (missing class or endpoint, or an assertion). Write
  `modern/docs/reports/stories/<KEY>/red-report.md`.
- **Format before hashing:** `./gradlew --no-daemon :services:<svc>:spotlessApply`, then record:
  `python modern/tools/checks/test_protection.py record <KEY> <every test file of the story>` (Python:
  `modern/kag/.venv/Scripts/python.exe`).

## 4. Green (java-developer)

- Production code only, under `services/<svc>/src/main/**`. Never touch `src/test/**`, features, contracts, other
  services, or the hash files. At most 3 attempts; after each run `test_protection.py check <KEY>` (the orchestrator
  also runs it).
- Layers (ArchUnit enforces): `domain` (records, enums, rules; no Spring, no JPA), `application` (use cases, ports,
  `@Service`, transactions), `infrastructure` (JPA entities and repositories, Kafka listeners, HTTP clients),
  `api` (controllers and DTOs shaped exactly like the OpenAPI schema; `@PreAuthorize` with the contract's x-roles),
  `adapter/legacy` (fixed-width files through `libs/fixed-width`).
- From `libs/service-kit` (auto-configured, see ADR-012):
  - `OutboxWriter.enqueue(type, npi, data)` inside the transaction that changes state (never send to Kafka directly).
  - `ProcessedEvents.firstTime(eventId)` at the start of every consumer transaction (idempotent consumers).
  - `EventJson.parse` / `EventJson.data` to read envelopes; listen with
    `@KafkaListener(topics = "pem.<producer>.events")` and filter on `envelope.type()`; wrap the work in
    `CorrelationId.with(envelope.correlationId(), ...)`.
  - `CurrentUser.current()` (roles, `npi` claim; `requireAccessToNpi`) and `ApiException` (problem details with the
    legacy `code` and field errors) for errors.
  - `ServiceClients.create(baseUrl)` for calls to another service with a SYSTEM token.
  - The injected `Clock`; never `LocalDate.now()`; `BigDecimal` for amounts.
- Database: Flyway files in `src/main/resources/db/migration/<svc>/`. V1 is applied on the local stack: **never edit
  it**; add `V2__<what>.sql` and so on. Spring Batch services add the Batch 5.2 schema as a migration when their first
  batch story needs it.
- Configuration: only in `<svc>-service.yml` with service-specific environment variable names; shared settings come
  from `pem-common.yml` (do not repeat its keys).
- Then `./gradlew --no-daemon :services:<svc>:check` (tests, JaCoCo 85% lines, PIT 70% on domain + application,
  Spotless, `versionCheck`). A skipped test (`@Disabled`, a failed assumption, an unmet condition) fails the test task and
  is named in the message; every version in `gradle/libs.versions.toml` and in the build files must be exact (no `+`,
  `latest` or range) and recorded in ADR-010 (MAM-16; `tools/checks/demo-build-gates.sh` shows both gates failing and
  passing). Write `modern/docs/reports/stories/<KEY>/trace.json`
  (`{"story", "acs", "tests", "codeUnits", "rules"}`).

## 5. Review and reporting

- code-reviewer reads the story's test and production files, runs the protection check and `check`, and answers
  APPROVE or MUST-FIX with file:line items (tdd-workflow section 5).
- Story report `modern/docs/reports/stories/<KEY>.md` in the phase-report format; the same text as a Jira comment
  starting `[agent:<role>]`; label `in-review` when approved.
- Update `modern/specs/<KEY>.md`: `builtCommit: pending-user-commit`, `testsSha256: <sha256 of the hash file>`.

## 6. Running things

| What | Command (from `modern/`) |
|---|---|
| Whole stack (local run mode, ADR-011) | `make up` (or `./pem.sh up`), then `make seed` |
| One service's checks | `./gradlew --no-daemon :services:<svc>:check` |
| Web UI | `cd web-ui && npm run lint && npm test && npm run e2e` (e2e needs the stack) |
| Web UI coverage (figure only, no threshold) | `cd web-ui && npm run coverage` (report in `web-ui/coverage/`; last figures in `docs/reports/ui-coverage.md`) |
| Traceability after a story | `python modern/tools/trace/build_rule_trace.py` (updates `modern/docs/traceability/rule-trace.csv`; Neo4j holds the legacy context only) |
