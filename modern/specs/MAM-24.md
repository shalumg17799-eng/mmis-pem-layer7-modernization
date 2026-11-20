---
key: MAM-24
localId: L-S003
epic: MAM-1
service: platform
type: Task
title: "One-command local stack: docker compose, health checks, make up/down/logs/seed/reset"
slug: plt-compose
specVersion: 1
descriptionSha256: f8b3f56b7891f7132a44f88d4d97d7ef0c9d3fb6777ffe3ea23e46a545e0b663
jiraUpdated: 2026-11-18T09:14:41.740-0600
rules: []
decisions: [D-012, D-013, D-014]
dependsOn: [MAM-16]
mainJourney: true
labels: [svc-platform, kag-L-S003, agentic, decision-D-012, decision-D-013, decision-D-014, slice-main-journey]
builtCommit: pending-user-commit
testsSha256: bd3df17c326cddc3751cf0a83cfaf6acafd807958795fd22a272686344c9ecc0
---
## Business requirement
A developer or reviewer must be able to start the whole system on one machine with a single command, see that every part is healthy, load demonstration data, and stop or reset it again, without touching anything else that runs on the machine. Notices that the system sends must be captured locally so that they can be looked at.

## Acceptance criteria
### AC-1 make up starts the whole stack healthy
Given a clean clone of the repository with Docker running
When make up is run from modern/
Then docker compose ps lists postgres 16, kafka (single-node KRaft), keycloak, wiremock, mailpit, the application container and web-ui, every one healthy; in the local run mode (ADR-011) the application container hosts api-gateway and the nine business services and is healthy only when every one of them reports ready, and in the split profile each of them is its own healthy container; make up exits 0 only when all of them are healthy
Basis: .claude/commands/phase-5-platform.md (Goal, step 5), .claude/skills/java-microservices/SKILL.md sections 1 and 3, .claude/agents/devops-engineer.md deliverables 2 and 3, modern/docs/adr/ADR-010-versions.md (Postgres 16, Kafka KRaft), modern/docs/adr/ADR-011-local-run-mode-shared-jvm.md (accepted by the user 2026-11-18 as a local run mode)

### AC-2 Pinned images and start order by health
Given modern/infra/docker-compose.yml
When a compose lint test reads it
Then every image has an exact version tag (no latest, no untagged image), every container declares a health check, and each service depends_on Postgres, Kafka and Keycloak with condition service_healthy where it needs them
Basis: .claude/agents/devops-engineer.md (Rules and deliverable 3)

### AC-3 One database and one user per service, schemas migrated at start
Given a started stack
When each service connects with its own database user and the schemas are inspected
Then provider_db, enrollment_db, screening_db, risk_db, revalidation_db, sanctions_db, notification_db, directory_db and tmsis_db exist (created by infra/postgres/init-databases.sh with passwords from modern/infra/.env.local), each service applies its Flyway V1__init.sql (taken from modern/docs/architecture/data-model/<service>.sql) at start-up, and a service user is refused when it connects to another service's database
Basis: .claude/skills/java-microservices/SKILL.md sections 1, 3 and 8, .claude/agents/devops-engineer.md deliverables 2 and 3, modern/docs/adr/ADR-002-data-ownership-single-provider-record.md, D-013, D-014, QF-8 (default applied)

### AC-4 Local-only secrets are generated and never committed
Given a first make up on a clone with no modern/infra/.env.local
When make up runs
Then the file is created with random values for the database users, the Keycloak admin, the four Keycloak test users (provider1, analyst1, supervisor1, admin1) and the SYSTEM client secret, the values are never printed, git check-ignore confirms the file is ignored, and git grep --untracked finds no such value in any other file
Basis: modern/docs/PROGRESS.md (Decisions 2026-11-18: random local-only passwords in modern/infra/.env.local), .claude/commands/phase-5-platform.md steps 1 and 5, MODERNIZATION.md sections 5 and 6, .gitignore (modern/infra/.env.local), QF-8 (default applied)

### AC-5 A separate stack that does not collide with the machine
Given other Docker containers already running on the machine, and host ports 5432 and 8081 already in use
When make up and then make down are run
Then only containers of the compose project named pem are created and stopped, no other project's container is touched, the host ports published for Postgres and provider-service are configurable (variables PEM_PG_HOST_PORT and PEM_PROVIDER_HOST_PORT) and default to ports that are free, and if any published host port is busy make up stops before starting anything and names the port
Basis: modern/docs/PROGRESS.md (Decisions 2026-11-18: Docker is used as a separate stack; other projects' containers are not reused or modified), .claude/commands/phase-5-platform.md step 5

### AC-6 The make targets behave as documented
Given modern/Makefile
When make down, logs, build, test, e2e, seed and reset are run
Then down stops the pem containers and keeps their data volumes, logs follows the pem container logs, build and test run the Gradle build and tests, e2e runs the Playwright journeys, seed loads the seed data, and reset asks for a yes before it deletes the pem volumes and changes nothing on any other answer
Basis: .claude/agents/devops-engineer.md deliverable 5, .claude/commands/phase-8-e2e-hardening.md step 1

### AC-7 Notices and mocked sources are reachable
Given the stack is up
When a test mail is sent to SMTP port 1025 and the WireMock admin API on port 8280 is called
Then the mail appears in the Mailpit web page at http://localhost:8025 and WireMock lists the stub mappings mounted from modern/infra/wiremock
Basis: .claude/skills/java-microservices/SKILL.md section 3, D-012, D-005

### AC-8 A runbook from clean clone to running system
Given modern/docs/runbooks/local-run.md
When its steps are followed on a clean clone
Then it lists the prerequisites, the commands, every URL and port, and the path of .env.local (never its values), and the file records whether the steps were actually executed from a clean clone
Basis: .claude/agents/devops-engineer.md deliverable 7, .claude/commands/phase-5-platform.md (Exit checks)

## Out of scope
- Seed content (dat-seed, dat-seed-2) and stub content (dat-wiremock)
- Keycloak realm content (plt-keycloak)
- Gateway routing (plt-gateway)
- Neo4j (the user's own instance is used, not part of this stack)
- Production deployment

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: none (platform enabler; REXX/PRVAUTOM.rexx job submission is replaced by the event bus and batch endpoints, CLAUDE.md section 10)
Decisions: D-012 REPLACE: notices are sent by email and captured locally in Mailpit; D-013 FIX: other services have no direct access to the provider data store (separate databases and users); D-014 FIX: provider-service is the single owner of the provider record (provider_db reachable only by its own user)

## Technical notes (agent-owned)
Service: platform. Endpoints/events: none. Roles: none.
