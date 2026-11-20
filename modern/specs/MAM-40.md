---
key: MAM-40
localId: L-S023
epic: MAM-3
service: provider-service
type: Story
title: "Provider history: every change and every notice sent is audited and readable"
slug: prv-audit
specVersion: 1
descriptionSha256: c8c878f381394fc176d4123bae8bb69c6aa87ae73f80718079c44ddad1661ea1
jiraUpdated: 2026-11-18T09:16:20.248-0600
rules: []
decisions: [D-012, D-018, D-023]
dependsOn: [MAM-17, MAM-21, MAM-23, MAM-27, MAM-33]
mainJourney: false
labels: [svc-provider-service, kag-L-S023, agentic, decision-D-012, decision-D-018, decision-D-023]
builtCommit:
testsSha256:
---
## Business requirement
Every change to a provider's record, and every notice sent about the provider, must be kept in a history that supervisors and administrators can read: who or what made the change, when, what the record looked like before and after, and which request it belongs to. The history can be read page by page, and nobody can edit or delete it.

## Acceptance criteria
### AC-1 Every change writes one history entry, oldest first
Given provider 1003000111 was registered, got ScreeningCompleted PASS, got RiskTierAssigned L, was approved by an ANALYST and then suspended by a SYSTEM caller, and no NoticeSent event has been consumed for it
When a SUPERVISOR sends GET /api/v1/providers/1003000111/audit
Then the items contain exactly five entries (registration, screening verdict, risk tier, approval, suspension) ordered by time, oldest first
Basis: modern/docs/architecture/nfr.md (Audit), .claude/skills/java-microservices/SKILL.md section 8, modern/docs/architecture/data-model/provider-service.sql (audit_log)

### AC-2 An entry shows before, after and the request it belongs to
Given the history of AC-1, where the approval was sent with X-Correlation-Id 5a1e7b20-3c4d-4e5f-9a6b-000000000456
When the SUPERVISOR reads the approval entry
Then it has at, actor, an action that names the approval, before with enrollStatus P, after with enrollStatus A and revalidationDueDate 2031-11-18, and correlationId 5a1e7b20-3c4d-4e5f-9a6b-000000000456; the registration entry has before null
Basis: modern/contracts/openapi/provider-service.yaml (AuditEntry; correlationId is a uuid), modern/docs/architecture/nfr.md (Audit: who, when, action, before, after, correlation id)

### AC-3 The actor is the user or the producing service
Given changes made by analyst1 (approval), by the SYSTEM service account service-account-pem-services (suspension) and by events from enrollment-service, screening-service and risk-service
When the SUPERVISOR reads the history
Then the approval entry's actor is analyst1, the suspension entry's actor is service-account-pem-services, and the registration, screening and tier entries have the producing service (enrollment-service, screening-service, risk-service) as actor
Basis: modern/docs/architecture/nfr.md (Audit: who), modern/contracts/asyncapi/events.yaml (envelope source), modern/infra/keycloak/realm-pem.json (users analyst1, service-account-pem-services)

### AC-4 A notice sent is recorded once
Given a NoticeSent event with id E5, noticeId N1, npi 1003000111 and template APPROVED
When provider-service consumes it, and then E5 is delivered a second time
Then GET /api/v1/providers/1003000111/audit has exactly one entry referring to noticeId N1 and template APPROVED, and the provider's own fields and version are unchanged
Basis: modern/contracts/asyncapi/events.yaml (NoticeSent consumed by provider-service), modern/docs/architecture/overview.md (difference 3), D-012, ADR-003

### AC-5 A status change entry carries reason, effective date and sanction id
Given a SYSTEM caller applied SUSPEND with reason 'LEIE exclusion', effectiveDate 2026-03-31 and sanctionId S1
When the SUPERVISOR reads the suspension entry
Then it has before enrollStatus A, after enrollStatus S and carries the reason 'LEIE exclusion', the effective date 2026-03-31 and sanctionId S1; GET /api/v1/providers/1003000111 shows endDate unchanged
Basis: Q-A6, D-018, modern/contracts/openapi/provider-service.yaml (StatusChangeRequest)

### AC-6 The history is paged
Given provider 1003000111 has 25 history entries
When a SUPERVISOR sends GET /api/v1/providers/1003000111/audit?page=1&size=20
Then the response has 5 items, page 1, size 20 and total 25
Basis: modern/contracts/openapi/provider-service.yaml (AuditPage, page and size), .claude/skills/java-microservices/SKILL.md section 6 (pagination)

### AC-7 Only SUPERVISOR and ADMIN can read it; an unknown provider gives 404
Given provider 1003000111 exists and 1003000999 does not
When GET /api/v1/providers/1003000111/audit is called with SUPERVISOR, ADMIN, ANALYST, PROVIDER (provider1) and SYSTEM tokens and with no token, and GET /api/v1/providers/1003000999/audit with a SUPERVISOR token
Then SUPERVISOR and ADMIN get 200; ANALYST, PROVIDER and SYSTEM get 403; no token gets 401; the unknown NPI gets 404
Basis: D-023, ADR-007, modern/docs/architecture/nfr.md (roles per endpoint), modern/contracts/openapi/provider-service.yaml (getProviderAudit x-roles, 404)

### AC-8 The history is append-only
Given provider 1003000111 has a history
When a later change is made, and a SUPERVISOR tries PUT, PATCH and DELETE on /api/v1/providers/1003000111/audit
Then the earlier entries are returned unchanged, the new entry is added at the end, and PUT, PATCH and DELETE each get 405 problem+json
Basis: Q-012 (nothing is deleted until a retention policy is set), modern/docs/architecture/nfr.md (Data retention), modern/contracts/openapi/provider-service.yaml (only GET is defined on the audit path)

## Out of scope
- Screening result rows (screening-service), sanction history (sanctions-service) and the notice log (notification-service): this history only holds the provider's own changes and references to notices.
- Deleting or archiving history (Q-012: nothing is deleted).
- Showing the history in the console (ui-console-detail). Note: the contract and NFR allow only SUPERVISOR and ADMIN, so the console must hide the history from ANALYST.

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: No legacy program: the legacy master kept no history of changes (D-018). Related legacy evidence: PRVSAN01.cbl:57-116 (reason and effective date never stored, R-PRVSAN01-ADD-03).
Decisions: D-012 REPLACE: every notice sent is recorded in the provider history through NoticeSent; D-018 FIX: real history instead of unused clusters; D-023 FIX: role-based access

## Technical notes (agent-owned)
Service: provider-service. Endpoints/events: GET /api/v1/providers/{npi}/audit, NoticeSent. Roles: SUPERVISOR, ADMIN.
