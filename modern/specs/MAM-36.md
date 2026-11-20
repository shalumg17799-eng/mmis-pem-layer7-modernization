---
key: MAM-36
localId: L-S008
epic: MAM-1
service: platform
type: Story
title: "Health, structured logs and correlation IDs across services (NFR)"
slug: plt-observability
specVersion: 1
descriptionSha256: b2e89cfe7ebb230ea1a8d2ce115015199fce15f30ca3d0a0259d82250b2fcce6
jiraUpdated: 2026-11-18T09:15:59.294-0600
rules: []
decisions: [D-024]
dependsOn: [MAM-31, MAM-24, MAM-29, MAM-46, MAM-17]
mainJourney: false
labels: [svc-platform, kag-L-S008, agentic, decision-D-024]
builtCommit:
testsSha256:
---
## Business requirement
Operations staff and developers must be able to tell at a glance whether each part of the system is healthy, and to follow one request or business change across every service when something goes wrong.

## Acceptance criteria
### AC-1 Every service reports its health
Given a started service
When GET /actuator/health is called without a token, with its database stopped, and again after it is restarted
Then it answers 200 with status UP when the database (and Kafka, where used) is reachable and 503 with status DOWN while the database is stopped
Basis: .claude/skills/java-microservices/SKILL.md section 11, .claude/agents/devops-engineer.md deliverable 2, QF-7 (default applied), D-024

### AC-2 Info and metrics are exposed but protected
Given a started service
When GET /actuator/info and GET /actuator/metrics are called without a token and with an ADMIN token
Then without a token they answer 401 and with an ADMIN token they answer 200
Basis: .claude/skills/java-microservices/SKILL.md section 11, QF-7 (default applied)

### AC-3 Logs are JSON with the correlation id
Given a request with an X-Correlation-Id header
When the service's log output is read
Then every line is a JSON object with timestamp, level, service name, message and that correlationId
Basis: modern/docs/architecture/nfr.md (Audit: logs are JSON), .claude/skills/java-microservices/SKILL.md section 11

### AC-4 The id is generated when missing and follows the events
Given a POST /api/v1/applications for a valid synthetic application sent through the gateway without X-Correlation-Id
When enrollment-service and provider-service have handled it and the logs of the gateway and both services are read
Then a generated id appears in the response header, in the log lines of the gateway, enrollment-service and provider-service, and in the envelopes of ApplicationValidated and ProviderRegistered
Basis: .claude/skills/java-microservices/SKILL.md section 11, modern/docs/adr/ADR-003-events-and-outbox.md, modern/docs/architecture/nfr.md (Audit), modern/docs/architecture/overview.md (main flow 1)

### AC-5 Compose health uses the real health endpoint
Given the stack from make up
When one service's database is stopped
Then docker compose ps shows that service as unhealthy within the health-check interval and healthy again after the database returns
Basis: .claude/agents/devops-engineer.md deliverables 2 and 3, modern/docs/architecture/nfr.md (Availability)

## Out of scope
- Dashboards, alerting and tracing back ends
- Log retention (Q-012: nothing is deleted until the business sets a policy)
- Business audit trail of provider changes (prv-audit)

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: BATCH/PRVENR01.cbl:102-116,187-189; PRVSCR01.cbl:85-93,114,149; PRVRSK01.cbl:70-76,97; PRVREV01.cbl:76-80,93; PRVTMS01.cbl:92-100,116; PRVSAN01.cbl:90-101,115; PRVDIR01.cbl:64-65,73-81,89 (file statuses untested, return codes never set; D-024)
Decisions: D-024 FIX: failures are visible through health, JSON logs and correlation ids instead of being ignored

## Technical notes (agent-owned)
Service: platform. Endpoints/events: POST /api/v1/applications, ApplicationValidated, ProviderRegistered. Roles: ADMIN, PROVIDER.
