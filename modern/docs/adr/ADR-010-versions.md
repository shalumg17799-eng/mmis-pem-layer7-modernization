# ADR-010: Versions

Status: ACCEPTED (user, 2026-11-18)

## Context

Versions must be pinned and recorded.

## Decision

Java 21; the latest Spring Boot 3.x GA and its Spring Cloud train, pinned in `modern/gradle/libs.versions.toml` in Phase 5; Postgres 16; Kafka KRaft; OpenAPI 3.1; AsyncAPI 3.1; USWDS 3.x.

## Consequences

Exact versions are recorded in Phase 5 when the build is created.

## Exact versions (recorded in Phase 5, 2026-11-18)
| Area | Version | Where pinned |
|---|---|---|
| Java | 21 (Temurin/Oracle 21.0.x on the build machine; images eclipse-temurin 21) | `gradle/libs.versions.toml` (`java`) |
| Gradle | 8.14.7 (wrapper; the 8.x line for plugin compatibility with Spring Boot 3.5) | `gradle/wrapper/gradle-wrapper.properties` |
| Spring Boot | 3.5.16 (latest 3.x GA on 2026-11-18); manages Kafka client 3.9.2, Spring Kafka 3.3.16, Spring Batch 5.2.6, Flyway 11.7.2, Hibernate 6.6, Testcontainers 1.21.4, JUnit 5.12 | `libs.versions.toml` |
| Spring Cloud | 2025.0.3 (train for Boot 3.5; Gateway Server Web MVC) | `libs.versions.toml` |
| springdoc-openapi | 2.8.17 | `libs.versions.toml` |
| HAPI FHIR (directory-service, **test scope only**: the R4 validator for MAM-65 AC-7) | 8.12.1 (latest 8.x on 2026-11-19; 9.0.0 exists and is not used): `hapi-fhir-structures-r4`, `hapi-fhir-validation`, `hapi-fhir-validation-resources-r4`, `hapi-fhir-caching-caffeine`, all the same version | `libs.versions.toml` (`hapi-fhir`), `services/directory-service/build.gradle.kts` |
| Cucumber / ArchUnit / PIT / JaCoCo / Spotless | 7.34.9 / 1.5.1 / 1.20.4 (gradle plugin 1.15.0, junit5 plugin 1.2.3) / 0.8.13 / 7.2.1 (google-java-format as bundled) | `libs.versions.toml` |
| Postgres | 16.4-alpine (digest-pinned) | `infra/docker-compose.yml`, `PemContainers` |
| Kafka | apache/kafka-native 4.3.2, KRaft (digest-pinned) | same |
| Keycloak / WireMock / Mailpit | 26.8.2 / 3.13.2 / v1.34.0 (digest-pinned) | `infra/docker-compose.yml` |
| Web UI | React 19.3.1, TypeScript 5.9.3 (7.x not yet supported by openapi-typescript), Vite 8.3.8, React Router 7.18.5, TanStack Query 5.108.0, **USWDS 3.14.0 via @trussworks/react-uswds 13.0.1**, oidc-client-ts 3.5.0, Vitest 5.1.1, Playwright 1.65.1, @axe-core/playwright 4.14.0, openapi-typescript 7.13.0, ESLint 9.39.5 | `web-ui/package.json` (exact, `save-exact`) |
