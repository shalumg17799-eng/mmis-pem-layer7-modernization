---
key: MAM-31
localId: L-S006
epic: MAM-1
service: platform
type: Task
title: "Event backbone: CloudEvents-style envelope, transactional outbox, idempotent consumers"
slug: plt-events
specVersion: 1
descriptionSha256: e8041c4a47ac5359d137d7fdaa380578a3fd10ef0e72511bfadf68906bc5d096
jiraUpdated: 2026-11-18T09:15:32.452-0600
rules: []
decisions: [D-024, D-012]
dependsOn: [MAM-16, MAM-24]
mainJourney: true
labels: [svc-platform, kag-L-S006, agentic, decision-D-024, decision-D-012, slice-main-journey]
builtCommit: 2e20ead
testsSha256: 5af3536ddd3ad62e64e903722fbdeddd473f9df2e1930f38c3763a3791d25093
---
## Business requirement
Services must tell each other about business changes reliably, so that an approved provider reaches the directory and triggers a notice even if a service restarts or the same message arrives twice. This story gives every service the same, tested way to publish and to receive such messages.

## Acceptance criteria
### AC-1 Every event has the agreed envelope, topic and key
Given any service publishing an event
When the message on its Kafka topic is read
Then it is JSON with id (uuid), type, source, time (ISO-8601), subject (the NPI), correlationId (uuid) and data, it is on topic pem.<service>.events with the NPI as message key, and a contract test fails when a message does not match modern/contracts/asyncapi/events.yaml
Basis: .claude/skills/java-microservices/SKILL.md section 7, modern/docs/adr/ADR-003-events-and-outbox.md, modern/contracts/asyncapi/events.yaml (Envelope)

### AC-2 The event is written in the same transaction as the change
Given a service that changes its state and publishes an event
When the transaction commits, and in a second case rolls back
Then after the commit the event appears once on the topic, and after the rollback no event appears
Basis: modern/docs/adr/ADR-003-events-and-outbox.md, .claude/skills/java-microservices/SKILL.md section 7

### AC-3 No event is lost when Kafka is unavailable
Given Kafka is stopped while a service commits a change
When Kafka is started again
Then the event is published after the restart (at least once, none lost) and the outbox row is marked published
Basis: modern/docs/adr/ADR-003-events-and-outbox.md (at-least-once delivery)

### AC-4 A repeated event is applied once
Given a consumer that has handled event id X
When the same event id X is delivered again
Then the second delivery has no effect on the consumer's data and the consumer's processed_event record holds X once
Basis: modern/docs/adr/ADR-003-events-and-outbox.md, .claude/skills/java-microservices/SKILL.md section 7, modern/docs/architecture/nfr.md (Availability: Kafka consumers are idempotent)

### AC-5 The correlation id travels with the event
Given a request that carries X-Correlation-Id
When it causes an event that another service consumes
Then the envelope correlationId equals that id, and the consumer's log lines and any event it publishes next carry the same id
Basis: .claude/skills/java-microservices/SKILL.md section 11, modern/docs/architecture/nfr.md (Audit), modern/docs/adr/ADR-003-events-and-outbox.md

### AC-6 The eight topics exist before services start
Given make up
When the Kafka topic list is read
Then pem.enrollment.events, pem.provider.events, pem.screening.events, pem.risk.events, pem.revalidation.events, pem.sanctions.events, pem.notification.events and pem.tmsis.events exist
Basis: modern/contracts/asyncapi/events.yaml (channels), .claude/skills/java-microservices/SKILL.md section 7

### AC-7 A message that cannot be processed is parked and surfaced, not dropped
Given a consumer that fails on one message every time
When the message is delivered and later messages arrive on the same topic
Then the message is retried a configured number of times, then written to the dead-letter topic <topic>.DLT with its original envelope, an error log line names its event id and correlation id, and the later messages are still processed
Basis: QF-13 (default applied), D-024, modern/docs/adr/ADR-003-events-and-outbox.md (at-least-once, none lost)

### AC-8 One shared module for envelope, outbox and idempotency
Given the Gradle build
When ./gradlew projects is run and the service build files are read
Then the module libs:service-kit holds the envelope type, the outbox writer and poller, the idempotent-consumer helper and the dead-letter handler (package gov.medicaid.pem.kit.events), every service that publishes or consumes events depends on it, and ADR-012 records the module
Basis: QF-4 (default applied), .claude/skills/java-microservices/SKILL.md sections 5 and 7, modern/docs/adr/ADR-003-events-and-outbox.md, modern/docs/adr/ADR-012-shared-service-kit-library.md

## Out of scope
- Business content of each event (each service story)
- Replay tooling for dead-lettered events
- Event schema changes (contract changes need the user's approval)

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: REXX/PRVAUTOM.rexx:15,23,40 (job submit, SDSF polling, alert routing stubbed; replaced by the event bus, CLAUDE.md section 10); BATCH/PRVSAN01.cbl:124-138 (dynamic CALL of PRVNT*, replaced by events and notification-service, D-012)
Decisions: D-024 FIX: explicit error handling; a message that cannot be processed is retried, dead-lettered and logged with its ids, never silent; D-012 REPLACE: the dynamic CALL of missing notice programs becomes events consumed by notification-service

## Technical notes (agent-owned)
Service: platform. Endpoints/events: ApplicationValidated, ApplicationRejected, ProviderRegistered, ProviderStatusChanged, ScreeningCompleted, RiskTierAssigned, RevalidationDue, SanctionApplied, NoticeSent, ExtractProduced. Roles: SYSTEM.
