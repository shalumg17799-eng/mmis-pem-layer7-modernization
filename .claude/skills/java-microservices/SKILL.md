---
name: java-microservices
description: The target architecture and Java coding standards for the PEM microservices. Covers the service list, ports, data ownership, events, project layout, versions, REST/event/database/security conventions, batch and observability. Use when designing, building, reviewing or running any service.
---

# PEM Java microservices

## 1. Services (default design; Phase 3 may refine it with an ADR)

| Service | Port | Database | Replaces | Owns | Publishes |
|---|---|---|---|---|---|
| api-gateway | 8080 | none | (entry point) | routing, token relay | none |
| provider-service | 8081 | `provider_db` | VSAM master, DB2 PROVIDER/OWNER | the provider golden record, owners, status, screen flag, risk tier, revalidation due date | ProviderRegistered, ProviderStatusChanged |
| enrollment-service | 8082 | `enrollment_db` | PRVENR01, PRVDAY01 step 010 | applications, validation errors, the legacy APPINPUT file intake | ApplicationValidated, ApplicationRejected |
| screening-service | 8083 | `screening_db` | PRVSCR01 | screening runs and results per source | ScreeningCompleted |
| risk-service | 8084 | `risk_db` | PRVRSK01 | the risk-tier rule table | RiskTierAssigned |
| revalidation-service | 8085 | `revalidation_db` | PRVREV01, PRVMTH01 step 010 | the revalidation queue, the scheduled sweep | RevalidationDue |
| sanctions-service | 8086 | `sanctions_db` | PRVSAN01 | sanction history, the legacy SANCIN intake | SanctionApplied |
| notification-service | 8087 | `notification_db` | PRVNT* (missing) | notice templates and notice log | NoticeSent |
| directory-service | 8088 | `directory_db` | PRVDIR01 | the directory read model, FHIR API, the legacy DIROUT extract | none |
| tmsis-service | 8089 | `tmsis_db` | PRVTMS01, PRVMTH01 steps 020–030 | extract runs, archive, the legacy TMSISOUT extract | ExtractProduced |
| web-ui | 5173 | none | CICS PINQ + new portal | portal (providers) and console (analysts) | none |

**Data ownership rule:** only provider-service writes provider status, screen flag, risk
tier and revalidation date. Other services send events or call its API (fixes AP-06 and
AP-12). No service reads another service's database.

## 2. Main flow (events)

```
enrollment  --ApplicationValidated-->  provider (registers PENDING/UNSCREENED)  --ProviderRegistered-->
screening   --ScreeningCompleted-->    provider (sets screen flag)  and  risk (on PASS)
risk        --RiskTierAssigned-->      provider (sets tier)
analyst approves in console (REST to provider) --ProviderStatusChanged--> directory, notification
revalidation (scheduled) --RevalidationDue--> screening (re-screen), notification
sanctions   --REST call to provider status API--> provider --ProviderStatusChanged--> directory, notification
tmsis (scheduled) reads provider-service API, writes the extract
```

## 3. Infrastructure (docker compose, `modern/infra/`)

| Container | Port | Purpose |
|---|---|---|
| postgres (16) | 5432 | one database per service, created by `infra/postgres/init.sql` |
| kafka (KRaft, single node) | 9092 | events |
| keycloak | 8180 | OIDC; realm `pem`, imported from `infra/keycloak/realm-pem.json` |
| wiremock | 8280 | mocks of LEIE, SAM, NPPES, licence board, SSA DMF |
| mailpit | 8025 (UI), 1025 (SMTP) | captures notices |
| neo4j (optional) | 7474, 7687 | only if the user has no Neo4j of their own |

## 4. Versions

- Java 21. Spring Boot: the latest 3.x GA release at Phase 5. Spring Cloud: its matching
  release train. Pin every version in `modern/gradle/libs.versions.toml` and record the
  choice in ADR "Versions".
- Build: Gradle (Kotlin DSL) multi-project with a wrapper; shared conventions in `modern/buildSrc`.
- Allowed libraries: Spring Web, Validation, Data JPA, Actuator, Security OAuth2 Resource
  Server, Spring Kafka, Spring Batch, Flyway, PostgreSQL driver, springdoc-openapi, HAPI
  FHIR (directory and provider only), MapStruct, Testcontainers, JUnit 5, AssertJ,
  Mockito, Cucumber-JVM, ArchUnit, JaCoCo, PIT. Anything else needs an ADR.

## 5. Project layout

```
modern/
  settings.gradle.kts  build.gradle.kts  gradle/libs.versions.toml  buildSrc/
  libs/fixed-width/          copybook-driven fixed-width codec (data-engineer)
  libs/test-support/         shared test fixtures and builders (no production code)
  services/<service>/
    src/main/java/gov/medicaid/pem/<svc>/
      api/            REST controllers, request/response DTOs (generated from OpenAPI where possible)
      application/    use cases (one class per use case), ports
      domain/         entities, value objects, domain rules: no Spring imports
      infrastructure/ JPA repositories, Kafka producers/consumers, HTTP clients, outbox
      adapter/legacy/ fixed-width readers and writers (only where a legacy file exists)
    src/main/resources/application.yml, db/migration/V1__init.sql …
    src/test/java/...        unit, integration (Testcontainers), contract
    src/test/resources/features/*.feature   Cucumber acceptance tests
  contracts/openapi/<service>.yaml  contracts/asyncapi/events.yaml
```
Here `<svc>` is the service name without `-service`, e.g. `risk`. ArchUnit enforces:
`domain` depends on nothing else; `api` never touches `infrastructure` directly.

## 6. REST conventions

- Base path `/api/v1/<resource>`. JSON. Plural nouns. NPI is the provider path key.
- Errors use RFC 7807 problem details, with `code` = a legacy error code (E101…) where
  one applies.
- Pagination: `page`, `size`; response has `items`, `page`, `size`, `total`.
- OpenAPI 3.1 contract first (in `contracts/openapi`), then code. A contract change needs
  the user's approval.

## 7. Event conventions

- Topic per producer: `pem.<service>.events` (e.g. `pem.risk.events`).
- Envelope: `{ "id": uuid, "type": "RiskTierAssigned", "source": "risk-service",
  "time": ISO-8601, "subject": "<npi>", "correlationId": uuid, "data": {…} }`.
  Message key = NPI.
- Producers use a **transactional outbox** table (`outbox_event`) and a poller.
- Consumers are **idempotent**: a `processed_event` table keyed by event id.
- Schemas live in `contracts/asyncapi/events.yaml`.

## 8. Database conventions

- One database per service; Flyway migrations `V<n>__<description>.sql`. Never edit an
  applied migration; always add a new one.
- Every table has `id`, `created_at`, `updated_at`, and `version` (optimistic locking)
  where rows are updated.
- Audit: every state change of a provider writes `audit_log` (who, when, action, before
  JSON, after JSON) in provider-service.

## 9. Security

- Keycloak realm `pem`. Roles: `PROVIDER`, `ANALYST`, `SUPERVISOR`, `ADMIN`.
- Every service is an OAuth2 resource server; the gateway relays tokens.
- Method-level checks with `@PreAuthorize`; a test per role for every endpoint.
- No secrets in code or YAML; only environment variables.

## 10. Batch and scheduling

- Revalidation sweep and T-MSIS extract are Spring Batch jobs with a cron set in
  configuration, plus a manual-trigger endpoint for ADMIN.
- The business date is a job parameter, defaulting to the injected `Clock`'s date.

## 11. Observability

- Actuator `health`, `info`, `metrics` exposed. JSON logs.
- An `X-Correlation-Id` header is accepted or generated, passed through REST and events,
  and logged on every line.

## 12. Service quality bar (CI fails below it)

- JaCoCo line coverage ≥ 85% per service; PIT mutation score ≥ 70% on `domain` and
  `application`.
- ArchUnit rules green; OpenAPI contract tests green; no high-severity dependency
  vulnerabilities.
