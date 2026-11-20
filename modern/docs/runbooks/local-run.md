# Local run: from a clean clone to a running system

Status of this runbook: the commands below were run on the development machine on 2026-11-18/19 against an existing
checkout (not a fresh clone); a run from a truly clean clone has **not** been done yet.

## Prerequisites
- Docker Desktop with Compose v2 (this stack needs about 1.6 GB of Docker memory: ADR-011 local run mode).
- Java 21 (for the Gradle wrapper; Gradle itself is downloaded by `./gradlew`).
- Node 20 or later with npm (web UI build).
- Bash (Git Bash on Windows). `make` is optional: `./pem.sh <target>` does the same.
- Free host ports: 5433 (Postgres), 9092, 8180, 8280, 8025, 1025, 8080, 18081, 8082-8089, 5173. Change the Postgres and
  provider ports with `PEM_PG_HOST_PORT` and `PEM_PROVIDER_HOST_PORT` if they collide.

## Start
```bash
cd modern
make up        # or ./pem.sh up: creates infra/.env.local (random local passwords), builds, starts, waits for health
make seed      # loads testdata/seed into each service database
```
`make up` stops before starting anything if a port is taken by another program, and touches only the compose project
`pem`. The application container `pem-apps` runs the gateway and the nine services in one JVM (ADR-011); each service
still has its own port, database, Kafka group and Swagger UI.

## Addresses
| What | URL |
|---|---|
| Web UI (portal, console, public directory) | http://localhost:5173 |
| API gateway | http://localhost:8080 |
| Swagger UI | http://localhost:18081/swagger-ui.html (provider), http://localhost:8082 ... 8089/swagger-ui.html |
| Keycloak (realm pem) | http://localhost:8180 (admin console user `admin`) |
| Mailpit (notices) | http://localhost:8025 |
| WireMock (mocked screening sources) | http://localhost:8280/__admin/mappings |
| Postgres | localhost:5433 (one database and user per service) |

Test users: `provider1`, `analyst1`, `supervisor1`, `admin1`. Their passwords and every other local secret are in
`modern/infra/.env.local` (generated, git-ignored; this file never shows the values).

## Other targets
`make ps`, `make logs`, `make down` (keeps data), `make build`, `make test`, `make e2e` (Playwright, needs the stack),
`make reset` (asks for "yes", then deletes the pem volumes).

## Checks
- `tools/checks/smoke-walking-skeleton.sh` checks the walking skeleton through the gateway with real tokens.
- `./gradlew --no-daemon :services:<service>:check` runs a service's tests and quality gates.
