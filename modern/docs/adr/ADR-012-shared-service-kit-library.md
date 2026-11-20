# ADR-012: Shared library libs/service-kit
Status: ACCEPTED (default applied by the architect role, 2026-11-18; Q-014 - revisit)

## Context
Nine services need the same event envelope, transactional outbox, idempotent consumer, dead-letter handling,
correlation ids, RFC 7807 problem details, Keycloak role mapping and SYSTEM-token client (ADR-003, ADR-007,
java-microservices 7, 9 and 11). Copying that code nine times would break "no duplication"; the skill's layout names
only `libs/fixed-width` and `libs/test-support`, and the plt-events story asked for a module `libs:events`.

## Decision
One production library, `libs/service-kit` (package `gov.medicaid.pem.kit`), auto-configured by Spring Boot. Each part
switches on only where its classes and beans exist (the gateway gets correlation ids only). It holds no business rule.
`libs/events` is not created separately: events are one package of service-kit (`gov.medicaid.pem.kit.events`).
Shared test code stays in `libs/test-support`.

## Consequences
One place to fix cross-cutting behaviour; every service depends on the library, so a change to it is tested with
every service's suite (`./gradlew checkAll`). No third-party library is added.
