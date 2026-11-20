---
key: MAM-69
localId: L-S067
epic: MAM-14
service: e2e
type: Story
title: "Journey J1 end to end: a provider applies and is enrolled (main journey)"
slug: e2e-j1
specVersion: 1
descriptionSha256: 8456d9697fcea0e63f169b54341ee190725d13cdd1d27399c1c7dd5b9f358ad7
jiraUpdated: 2026-11-18T09:21:03.467-0600
rules: []
decisions: [D-001, D-005, D-002, D-019, D-003, D-012, D-028]
dependsOn: [MAM-62, MAM-87, MAM-70, MAM-73, MAM-64, MAM-66, MAM-68, MAM-85, MAM-47, MAM-50, MAM-46, MAM-54, MAM-17, MAM-21, MAM-23, MAM-18, MAM-25, MAM-35, MAM-27, MAM-61, MAM-63, MAM-58]
mainJourney: true
labels: [svc-e2e, kag-L-S067, agentic, decision-D-001, decision-D-005, decision-D-002, decision-D-019, decision-D-003, decision-D-012, decision-D-028, slice-main-journey]
builtCommit: pending-user-commit
testsSha256: 9305c7f9938a31c1c1f9a04315d8edd8e25a595f9232de8e81c947e4f0cf5cbd
---
## Business requirement
A new provider must be able to apply online and, once the automatic checks and an analyst's decision are complete, be enrolled, appear in the public directory and be told so. This story proves the whole path works for real, from the portal to the notice, in the running system.

## Acceptance criteria
### AC-1 The provider signs in, applies and sees Validated
Given the stack is up at the seed state and provider1 (PROVIDER, NPI 1003000111) is signed in at the portal home
When provider1 chooses Apply and submits NPI 1003000111, the Medicaid ID that the dat-synthetic scenario assigns to it, type Individual (IN), taxonomy 207Q00000X, last name RIVERA, first name MARIA and licence MD12345 / TX
Then GET /api/v1/applications/{applicationId} returns status VALIDATED with no errors and the page /portal/applications/:applicationId shows the status Validated
Basis: modern/docs/ux/journeys.md#J1 steps 1-3, D-001, modern/contracts/openapi/enrollment-service.yaml (Application status), aws-transform/test-data/providers.txt, QF-1 (default applied)

### AC-2 Registered Pending and Unscreened, then screened on all four sources
Given the application is VALIDATED
When the services have processed ApplicationValidated and ProviderRegistered
Then pem.provider.events carries ProviderRegistered for subject 1003000111 with enrollStatus P and screenFlag U, pem.screening.events carries ScreeningCompleted with reason INITIAL, result PASS and source results LICBOARD, OIGLEIE, SAMGOV and SSADMF each P, GET /api/v1/screenings?npi=1003000111 lists those four results, and GET /api/v1/providers/1003000111 shows screen flag P
Basis: modern/docs/ux/journeys.md#J1 step 4, D-005, modern/contracts/asyncapi/events.yaml (ProviderRegistered, ScreeningCompleted), modern/contracts/openapi/screening-service.yaml (SourceResult), modern/docs/architecture/overview.md (main flows 1 and 2)

### AC-3 Risk tier Limited is assigned and saved
Given screening passed for taxonomy 207Q00000X
When risk-service has handled ScreeningCompleted
Then pem.risk.events carries RiskTierAssigned with tier L for 1003000111 and GET /api/v1/providers/1003000111 shows risk tier L (Limited)
Basis: modern/docs/ux/journeys.md#J1 step 4, D-002, Q-005, modern/contracts/asyncapi/events.yaml (RiskTierAssigned)

### AC-4 The analyst sees it under Pending approval
Given the provider is Pending with screen flag P and tier L
When analyst1 opens the console work queues at /console/queues
Then 1003000111 is listed under Pending approval and not under Failed screening
Basis: modern/docs/ux/journeys.md#J1 step 5, modern/docs/ux/screen-inventory.md (console work-queues), D-019

### AC-5 Approval sets Active and the revalidation due date
Given analyst1 is on /console/providers/1003000111/approval
When analyst1 approves
Then POST /api/v1/providers/1003000111/approval with decision APPROVE returns the provider with status A and a revalidation due date exactly five years after the approval date, and pem.provider.events carries ProviderStatusChanged from P to A with that revalidationDueDate
Basis: modern/docs/ux/journeys.md#J1 step 6, D-019, D-003, modern/contracts/openapi/provider-service.yaml (decideProvider), modern/contracts/asyncapi/events.yaml (ProviderStatusChanged)

### AC-6 The provider appears in the public directory
Given the provider is Active
When anyone without a token calls GET /api/v1/directory/providers?name=RIVERA and searches RIVERA on /directory
Then 1003000111 is listed in both with the name MARIA RIVERA
Basis: modern/docs/PROGRESS.md (Current run checklist item 4: main journey slice includes the directory), modern/docs/architecture/overview.md (main flows 3 and 6), modern/docs/adr/ADR-006-fhir-scope.md, modern/docs/adr/ADR-007-authentication-and-roles.md, D-028, Q-008

### AC-7 The notice is visible in Mailpit and in the portal
Given ProviderStatusChanged from P to A
When notification-service has handled it
Then pem.notification.events carries NoticeSent with template APPROVED for 1003000111, the email Enrolment approved addressed to 1003000111@example.test is visible in Mailpit at http://localhost:8025, /portal/notices shows Enrolment approved to provider1, and /portal/provider shows Active (A)
Basis: modern/docs/ux/journeys.md#J1 step 7, modern/docs/PROGRESS.md (Current run checklist item 4: Mailpit notice), D-012, .claude/skills/java-microservices/SKILL.md section 3 (mailpit), modern/contracts/asyncapi/events.yaml (NoticeSent), QF-3 (default applied)

### AC-8 Repeatable and accessible
Given the journey as one Playwright test in modern/web-ui/e2e/journeys/ with an axe scan on every page it visits
When it is run three times in a row, each time from the seed state
Then all three runs pass and every axe scan reports zero violations
Basis: .claude/commands/phase-8-e2e-hardening.md steps 2 and 4, modern/docs/ux/journeys.md (introduction), modern/docs/architecture/nfr.md (Accessibility), QF-6 (default applied)

## Out of scope
- Rejection and failure paths (J2, J3)
- Sanctions, revalidation and extracts (J5 to J7)
- Security and performance checks (nfr stories)

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: BATCH/PRVENR01.cbl:128-159 (enrolment validation, D-001); BATCH/PRVSCR01.cbl:116-132,146-149 (stub checks, D-005); BATCH/PRVRSK01.cbl:70-76,86-97,103-116,126-137 (risk tier and due date, D-002, D-003); BATCH/PRVSAN01.cbl:112 (the only legacy statement that sets ACTIVE, D-019); JCL/PRVDAY01.jcl STEP010, STEP030, STEP040
Decisions: D-001 FIX: every application is processed; a valid one is registered Pending and Unscreened; D-005 REPLACE: screening runs against the mocked sources; D-002 FIX: the risk tier is saved; D-019 FIX: an analyst approval sets ACTIVE after a Pass screening; D-003 FIX: the due date is set at approval (approval + 5 years); D-012 REPLACE: the notice is sent by notification-service; D-028 FIX: the directory shows the composed name

## Technical notes (agent-owned)
Service: e2e. Endpoints/events: POST /api/v1/applications, GET /api/v1/applications/{applicationId}, GET /api/v1/providers, GET /api/v1/providers/{npi}, GET /api/v1/screenings, POST /api/v1/providers/{npi}/approval, GET /api/v1/directory/providers, GET /api/v1/notices, ApplicationValidated, ProviderRegistered, ScreeningCompleted, RiskTierAssigned, ProviderStatusChanged, NoticeSent. Roles: PROVIDER, ANALYST, PUBLIC.
