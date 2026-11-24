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

## Library upgrade of 2026-11-20 (MAM-84 AC-5, Q-122)
The OSV scan of the deployable (`docs/reports/dependency-scan.md`) found 33 advisories in run-time libraries. The Spring Boot 3.5.16 BOM, Kafka and `spring-cloud-starter` bring these libraries in older patch or minor versions of the same line, so the libraries below are raised above them. Spring Boot (3.5.16) and Spring Cloud (2025.0.3) are unchanged and no library moves to a new major version. Each version is a key in `libs.versions.toml`; `pem.java-conventions` applies them to every module as dependency constraints (the Jackson modules through the `jackson-bom` platform), so a service, a library and `:infra:local-runtime` resolve the same version and the higher version always wins if a BOM moves later.

| Library | Before -> after | Advisories fixed | Where it comes from |
|---|---|---|---|
| tomcat-embed-core, tomcat-embed-el, tomcat-embed-websocket (`org.apache.tomcat.embed`) | 10.1.55 -> 10.1.59 | 7 on tomcat-embed-core: 5 CRITICAL (GHSA-9xv2-5v5q-p794, GHSA-f525-44xv-f2qj, GHSA-gcx9-497g-6cp6, GHSA-h3x4-894j-xpx5, GHSA-mqg3-r7h5-24x4), 1 HIGH (GHSA-h792-v28v-ppgr), 1 MODERATE (GHSA-5v3h-fjv2-54fg); the highest fix is 10.1.59 | Spring Boot BOM (`tomcat.version`) |
| Jackson: jackson-core, jackson-databind, dataformat-toml/yaml, datatype-jdk8/jsr310, module-parameter-names (`jackson-bom`) | 2.21.4 -> 2.21.7 | jackson-core 2 HIGH (GHSA-7hhh-6rmp-j9qf, GHSA-p6pp-m3f8-5c89); jackson-databind 3 HIGH (GHSA-cxp5-3px4-pw24, GHSA-q4xh-88c3-wmh7, GHSA-wv8q-qhhj-9h54) and 6 MODERATE | Spring Boot BOM (`jackson-bom.version`) |
| jackson-annotations | stays 2.21 | none (no advisory); since Jackson 2.20 this artifact has no patch version, 2.21.7 does not exist and the `jackson-bom` 2.21.7 manages it as 2.21 | `jackson-bom` |
| postgresql (`org.postgresql`) | 42.7.11 -> 42.7.12 | GHSA-j92g-9f8w-j867 (HIGH, channel-binding downgrade) | Spring Boot BOM (`postgresql.version`) |
| snappy-java (`org.xerial.snappy`) | 1.1.10.5 -> 1.1.10.9 | 2 HIGH (GHSA-gwq9-9ffj-mvrv, GHSA-q233-g92q-qxp6), 1 MODERATE | `kafka-clients` 3.9.2 |
| lz4-java (`at.yawk.lz4`) | 1.10.1 -> 1.11.4 | 1 HIGH (GHSA-mcr4-qmvw-px4g), 4 MODERATE, 1 LOW | `kafka-clients` 3.9.2 |
| bcprov-jdk18on (`org.bouncycastle`) | 1.80.2 -> 1.85 | 1 CRITICAL (GHSA-9pwp-9qqc-pr26), 1 HIGH (GHSA-qp49-qgx5-5m26) | `spring-cloud-starter` 4.3.3 (compile scope) through `spring-cloud-starter-gateway-server-webmvc` of the api-gateway; a production dependency, not a test library, so it is raised and not removed |
| log4j-api, log4j-to-slf4j (`org.apache.logging.log4j`) | 2.24.3 -> 2.25.5 | GHSA-qv9r-c865-cp47 (MODERATE) | Spring Boot BOM (`log4j2.version`), `spring-boot-starter-logging` |

Every target version exists on Maven Central; none was replaced by a lower patch. Not changed: spring-webmvc 6.2.19 (2 CRITICAL advisories, fixed only in Spring Framework 7 / Spring Boot 4; justified in `tools/checks/dependency-justifications.json`). The pins live in `gradle/libs.versions.toml` (keys `tomcat`, `jackson`, `postgresql-jdbc`, `snappy-java`, `lz4-java`, `bouncycastle`, `log4j`, and since 2026-11-21 `httpclient5` and `httpcore5`) and `buildSrc/src/main/kotlin/pem.java-conventions.gradle.kts`.

## Library raise of 2026-11-21 (api-gateway HTTP client, ADR-013, Q-122 option A, Q-157)
The gateway uses Apache HttpClient 5 (ADR-013). The Spring Boot 3.5.16 BOM would bring it in with the versions below; the OSV database (2026-11-21) lists advisories for them and the 5.5.x and 5.3.x lines have no fix, so the three are raised by the same constraint mechanism as the table above. Checked again on 2026-11-24: the scan of the deployable jar (13:12) shows no advisory on any of the three.

| Library | Before -> after | Advisories fixed | Where it comes from |
|---|---|---|---|
| httpclient5 (`org.apache.httpcomponents.client5`) | 5.5.2 -> 5.6.5 | GHSA-hjcp-jmpx-g3qm (MODERATE, fixed in 5.6.3) | Spring Boot BOM; declared (without a version) by `services/api-gateway/build.gradle.kts` only (ADR-013); in the local run mode it is on the shared class path of `pem-local-runtime.jar` because that jar contains the gateway |
| httpcore5, httpcore5-h2 (`org.apache.httpcomponents.core5`) | 5.3.6 -> 5.4.4 | GHSA-hf6x-8p5f-cgmf (HIGH, httpcore5), GHSA-v3jc-474w-2wm6 (HIGH, httpcore5-h2), both fixed in 5.4.3 | transitive of httpclient5; 5.4.4 is the httpcore line that httpclient5 5.6.5 itself names; both artifacts stay on one version (key `httpcore5`) |

httpcore5 5.5 was published on 2026-11-15 and is not used. A Gradle constraint only raises a module that is already in the dependency graph; it does not add one, so no other service gets httpclient5 from this raise (read of all build files and of the cached POMs in the review of 2026-11-24).
