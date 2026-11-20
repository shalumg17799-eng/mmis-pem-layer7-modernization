---
key: MAM-29
localId: L-S005
epic: MAM-1
service: api-gateway
type: Task
title: "API gateway: routing to every service, CORS for the web UI, token relay"
slug: plt-gateway
specVersion: 1
descriptionSha256: bd7e37570657df7c8a2287996cfe327799585b94868c60eed7a846e3575d4a3c
jiraUpdated: 2026-11-18T09:15:14.403-0600
rules: []
decisions: [D-023, D-024]
dependsOn: [MAM-26]
mainJourney: true
labels: [svc-api-gateway, kag-L-S005, agentic, decision-D-023, decision-D-024, slice-main-journey]
builtCommit: 2e20ead
testsSha256: 7df9a78ddad2c8a19fc1559dac0a9e7fff1ffbb5e4bf959812dd894c372888ff
---
## Business requirement
Screens and other callers need one front door to the whole system. It forwards every request to the service that owns it, passes on the identity of the signed-in user, lets the web application call it from the browser, and answers clearly when something is wrong.

## Acceptance criteria
### AC-1 Every contract path is routed to its owning service
Given the gateway at http://localhost:8080, the nine service skeletons running, and the nine OpenAPI contracts (1.0.0)
When a request with an X-Correlation-Id is sent through the gateway to every path of those contracts, including /api/v1/batch/..., /api/v1/legacy/... and /fhir/...
Then each request is forwarded to the service that owns it (provider 8081, enrollment 8082, screening 8083, risk 8084, revalidation 8085, sanctions 8086, notification 8087, directory 8088, tmsis 8089), shown by its correlation id in that service's log, and no request gets the gateway's own 404 'no route' answer
Basis: .claude/agents/devops-engineer.md deliverable 4, modern/docs/architecture/overview.md (api-gateway row), modern/contracts/openapi/*.yaml (version 1.0.0), .claude/skills/java-microservices/SKILL.md section 1

### AC-2 Directory and FHIR need no token, everything else does
Given a caller with no token
When GET /api/v1/directory/providers, GET /fhir/Practitioner, GET /fhir/Organization and GET /fhir/PractitionerRole are called through the gateway, and then GET /api/v1/providers
Then the four public calls are forwarded to directory-service without a token and are never answered 401, and GET /api/v1/providers answers 401
Basis: modern/docs/adr/ADR-006-fhir-scope.md, modern/docs/adr/ADR-007-authentication-and-roles.md, modern/docs/architecture/nfr.md (Security), D-023

### AC-3 The token is relayed unchanged
Given an ANALYST token and a PROVIDER token
When a protected path is called through the gateway with each
Then the owning service receives exactly the Authorization header that was sent, so the role decision is made by the service itself, and the gateway adds no role, claim or identity header of its own
Basis: modern/docs/adr/ADR-007-authentication-and-roles.md, .claude/skills/java-microservices/SKILL.md section 9, .claude/agents/devops-engineer.md deliverable 4, D-023

### AC-4 CORS allows the web UI only
Given the web UI origin http://localhost:5173 and another origin
When a preflight OPTIONS request is sent from each
Then the answer for http://localhost:5173 allows the methods and the headers Authorization, Content-Type and X-Correlation-Id, and the other origin gets no CORS allow header
Basis: .claude/agents/devops-engineer.md deliverable 4, .claude/skills/frontend-stack/SKILL.md section 2

### AC-5 A correlation id is accepted or generated
Given one request with an X-Correlation-Id header and one without
When both are sent through the gateway
Then the first keeps its id and the second gets a generated one, the service receives the id on both, and both responses return it in the X-Correlation-Id header
Basis: modern/docs/architecture/overview.md (api-gateway: correlation id), .claude/skills/java-microservices/SKILL.md section 11, modern/docs/architecture/nfr.md (Audit)

### AC-6 Clear errors for unknown routes and unavailable services
Given an unknown path, and a service that is stopped
When each is called through the gateway
Then the unknown path answers 404 and the stopped service answers 503, both as RFC 7807 problem details that carry the correlation id and no stack trace or internal host name
Basis: .claude/skills/java-microservices/SKILL.md section 6, D-024

## Out of scope
- Role checks inside the services and the 200 answers of each endpoint (each service story, nfr-security)
- Rate limiting and caching
- Production TLS

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: ONLINE/PRVCICS1.cbl:28-61 (CICS transaction PINQ as the old entry point, replaced by gateway and console; D-022, D-023)
Decisions: D-023 FIX: tokens are relayed so every service enforces roles; protected routes answer 401 without a token; D-024 FIX: explicit error handling; failures are surfaced as problem details with a correlation id

## Technical notes (agent-owned)
Service: api-gateway. Endpoints/events: GET /api/v1/directory/providers, GET /fhir/Practitioner, GET /fhir/Organization, GET /fhir/PractitionerRole, GET /api/v1/providers. Roles: PUBLIC, PROVIDER, ANALYST, SUPERVISOR, ADMIN, SYSTEM.
