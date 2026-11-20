# ADR-011: Local run mode with the services in one JVM
Status: ACCEPTED (user, 2026-11-18) as a local run mode only

## Context
The development machine shares Docker with about 28 other projects' containers: about 2.5 GB of the 8 GB Docker
memory limit is free, and about 2 GB of host memory. Ten Spring Boot JVMs (nine services and the gateway) need about
2 to 2.5 GB on their own, on top of Postgres, Kafka, Keycloak, WireMock and Mailpit. The full stack does not fit with one
container per service.

## Decision
Every service stays its own Spring Boot application: its own main class, configuration file (`<service>.yml`),
Dockerfile, port, database and database user, Kafka consumer group and topic, Flyway migrations
(`db/migration/<service>`), and it runs on its own (`java -jar services/<service>/build/libs/<service>.jar`, or one
container per service in the compose `split` profile). This is the deployment model and the microservices requirement.

For local runs on small machines, `infra/local-runtime` starts all ten applications in one JVM, side by side, each in
its own Spring application context with its own port, database, consumer group and settings (compose profile `apps`,
container `pem-apps`; `make up`). Classes and libraries are shared in memory; nothing else is. Services still talk only
through REST (over their ports) and Kafka, never in-process calls.

## Consequences
- The local stack fits in about 1.2 GB for the applications instead of about 2.5 GB.
- `docker compose ps` shows one `pem-apps` container; its health check passes only when every service's readiness
  endpoint is UP. Per-service health stays visible at each service's `/actuator/health`.
- Configuration names must not collide: each service uses its own config file name and service-specific environment
  variable names (`PG_<SERVICE>_PASSWORD`, `PEM_<SERVICE>_PORT`).
- An ArchUnit-style check and the `split` profile keep the standalone mode working; CI builds one image per service.
