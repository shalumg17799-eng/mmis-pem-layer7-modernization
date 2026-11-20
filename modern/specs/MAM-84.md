---
key: MAM-84
localId: L-S074
epic: MAM-14
service: e2e
type: Story
title: "Security: role tests for every endpoint, dependency check, no secrets (NFR)"
slug: nfr-security
specVersion: 1
descriptionSha256: 6bd5369cb8dfbcde9775bf29d1a099aeca678eeee042b124163b1846f7589eee
jiraUpdated: 2026-11-18T09:22:46.264-0600
rules: []
decisions: [D-023]
dependsOn: [MAM-29, MAM-26, MAM-36, MAM-50]
mainJourney: false
labels: [svc-e2e, kag-L-S074, agentic, decision-D-023]
builtCommit:
testsSha256:
---
## Business requirement
Only the right people may do the right things. Every screen and every service call must be restricted by role, a provider must never see another provider's data, no secret may be stored in the code, and known vulnerable libraries must not be shipped. This story proves it with tests rather than by inspection.

## Acceptance criteria
### AC-1 Every endpoint answers correctly for every role
Given the 39 endpoints in the Security table of nfr.md, tokens for PROVIDER (NPI 1003000111), ANALYST, SUPERVISOR, ADMIN and SYSTEM, and no token
When each endpoint is called with each token and without a token through the gateway
Then every role listed for the endpoint gets an answer that is not 401 or 403 (a PROVIDER on its own NPI), every other role gets 403, a call with no token to a protected endpoint gets 401, and the four PUBLIC endpoints answer 200 without a token
Basis: modern/docs/architecture/nfr.md (Security), modern/docs/adr/ADR-007-authentication-and-roles.md, D-023, .claude/commands/phase-8-e2e-hardening.md step 3, .claude/skills/java-microservices/SKILL.md section 9

### AC-2 Contracts and the role table do not drift
Given the x-roles of every operation in the nine OpenAPI contracts and the table in nfr.md
When a drift test compares them
Then the test passes only when every operation has the same roles in both and fails naming the operation otherwise
Basis: modern/docs/adr/ADR-007-authentication-and-roles.md (Consequences), modern/docs/architecture/nfr.md (Security)

### AC-3 A provider only sees their own data
Given provider1 (NPI 1003000111) and data of other providers
When provider1 calls GET /api/v1/providers/{npi} for another NPI, and GET /api/v1/applications and GET /api/v1/notices with and without an npi filter for another provider
Then the first call returns 403 and the lists contain only provider1's own items
Basis: modern/docs/architecture/nfr.md (Security: a PROVIDER only sees their own data), .claude/skills/frontend-stack/SKILL.md section 6, D-023, QF-1 (default applied)

### AC-4 Forged and expired tokens are refused
Given a token signed by another key, an expired token and a token for another realm
When each is sent through the gateway to a protected endpoint
Then each is refused with 401
Basis: modern/docs/adr/ADR-007-authentication-and-roles.md, .claude/skills/java-microservices/SKILL.md section 9

### AC-5 No high or critical vulnerable dependency
Given the Gradle build and the web UI
When the OWASP dependency check and npm audit run
Then a report in modern/docs/reports lists the result and has no high or critical finding, or each one has a written justification
Basis: .claude/commands/phase-8-e2e-hardening.md step 3, .claude/skills/java-microservices/SKILL.md section 12

### AC-6 No secret in any file
Given the repository
When git grep --untracked and git check-ignore modern/infra/.env.local are run
Then no password, token or key is found in any tracked or untracked file, and .env.local is ignored
Basis: .claude/commands/phase-5-platform.md step 5, MODERNIZATION.md sections 5 and 6, modern/docs/PROGRESS.md (Decisions 2026-11-18)

### AC-7 Only health is open on the actuator
Given every service
When /actuator/info and /actuator/metrics are called without a token
Then both answer 401 while /actuator/health answers 200
Basis: QF-7 (default applied), .claude/skills/java-microservices/SKILL.md section 11

### AC-8 An optional ZAP baseline scan of the gateway
Given the user has agreed to the optional scan
When the pinned zaproxy/zap-stable image scans http://localhost:8080
Then the report lists no high alert, or each one has a written justification; if the user has not agreed, the report states that the scan was not run
Basis: .claude/commands/phase-8-e2e-hardening.md step 3

## Out of scope
- The role tests inside each service's own story (Phase 6)
- Penetration testing
- Production hardening and TLS

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: ONLINE/PRVCICS1.cbl:28-61 (any PINQ user could read any provider, D-023)
Decisions: D-023 FIX: role-based access (Keycloak roles) on every endpoint and screen, proved by tests

## Technical notes (agent-owned)
Service: e2e. Endpoints/events: GET /api/v1/providers, GET /api/v1/providers/{npi}, GET /api/v1/applications, GET /api/v1/notices, GET /api/v1/directory/providers, GET /fhir/Practitioner, GET /fhir/Organization, GET /fhir/PractitionerRole. Roles: PROVIDER, ANALYST, SUPERVISOR, ADMIN, SYSTEM, PUBLIC.
