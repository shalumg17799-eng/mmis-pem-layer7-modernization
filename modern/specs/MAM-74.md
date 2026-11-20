---
key: MAM-74
localId: L-S069
epic: MAM-14
service: e2e
type: Story
title: "Journey J3 end to end: screening fails and the analyst denies"
slug: e2e-j3
specVersion: 1
descriptionSha256: 1a4391347bd4b5beceab822c6ebeed37f4ba1c0854a79ea40c22c3ac1c7f0c4d
jiraUpdated: 2026-11-18T09:21:27.541-0600
rules: []
decisions: [D-005, D-019, D-016, D-012]
dependsOn: [MAM-87, MAM-22, MAM-85, MAM-80, MAM-47, MAM-58, MAM-50]
mainJourney: false
labels: [svc-e2e, kag-L-S069, agentic, decision-D-005, decision-D-019, decision-D-016, decision-D-012]
builtCommit: pending-user-commit
testsSha256: 2253a0ec976c4a17eacadf6a75501075e9c4bd767d5022cc1c9645f3a676a31e
---
## Business requirement
When the automatic checks find a problem, such as an owner on a federal exclusion list, the analyst must see the failure with its reason and be able to deny the enrolment, and the provider must be told. This story proves that path works for real in the running system.

## Acceptance criteria
### AC-1 Screening fails with E203 owner excluded
Given the application for NPI 1003000444 (type GR mapped from GRP, legal name EXCLUDED CLINIC LLC, owner NPI 1990000111, which is on the mocked LEIE, with the Medicaid ID and taxonomy of the dat-synthetic scenario) submitted by the test set-up as analyst1
When the provider is registered and screened
Then ScreeningCompleted for 1003000444 has result FAIL, the OIGLEIE source result is F with errorCode E203 (owner excluded), and GET /api/v1/providers/1003000444 shows screen flag F and status P
Basis: modern/docs/ux/journeys.md#J3 step 1, D-005, D-016, aws-transform/test-data/leie.txt, aws-transform/test-data/providers.txt, QF-2 (default applied)

### AC-2 The analyst sees it under Failed screening
Given the failed screening
When analyst1 opens /console/queues
Then 1003000444 is listed under Failed screening and not under Pending approval
Basis: modern/docs/ux/journeys.md#J3 step 2, modern/docs/ux/screen-inventory.md (console work-queues)

### AC-3 The detail shows the failing source
Given analyst1 opens /console/providers/1003000444
When the screening table is read
Then it shows source OIGLEIE with result Fail (F), its detail text, and the error E203 owner excluded
Basis: modern/docs/ux/journeys.md#J3 step 3, modern/docs/ux/screen-inventory.md (console provider-detail), D-005

### AC-4 The analyst denies with a reason
Given analyst1 is on /console/providers/1003000444/approval
When analyst1 chooses Deny and enters a reason
Then POST /api/v1/providers/1003000444/approval with decision DENY returns the provider with status D, and pem.provider.events carries ProviderStatusChanged from P to D
Basis: modern/docs/ux/journeys.md#J3 step 4, D-019, modern/contracts/openapi/provider-service.yaml (decideProvider, ApprovalRequest)

### AC-5 A denial notice is sent
Given ProviderStatusChanged from P to D
When notification-service has handled it
Then GET /api/v1/notices lists a notice with template DENIED for 1003000444 and the email addressed to 1003000444@example.test is visible in Mailpit
Basis: modern/docs/ux/journeys.md#J3 step 4, D-012, modern/contracts/openapi/notification-service.yaml (Notice template), QF-3 (default applied)

### AC-6 Repeatable and accessible
Given the journey as one Playwright test with an axe scan on every page it visits
When it is run three times in a row, each time from the seed state
Then all three runs pass and every axe scan reports zero violations
Basis: .claude/commands/phase-8-e2e-hardening.md steps 2 and 4, modern/docs/architecture/nfr.md (Accessibility), QF-6 (default applied)

## Out of scope
- Failures from the licence board, SAM or SSA DMF (screening stories)
- Approval of a provider whose screening passed (J1)
- Rescreening (J6)

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: BATCH/PRVSCR01.cbl:85-93,114-132,146-149 (stub checks, D-005; screening results never populated); BATCH/PRVSAN01.cbl:112 (only statement that sets ACTIVE, D-019); JCL/PRVDAY01.jcl STEP030
Decisions: D-005 REPLACE: real screening against mocked sources produces the failure; D-019 FIX: the analyst's decision sets the status; here DENIED; D-016 FIX: E203 owner excluded is a real screening result; D-012 REPLACE: the denial notice is sent by notification-service

## Technical notes (agent-owned)
Service: e2e. Endpoints/events: POST /api/v1/applications, GET /api/v1/providers/{npi}, GET /api/v1/screenings, POST /api/v1/providers/{npi}/approval, GET /api/v1/notices, ProviderRegistered, ScreeningCompleted, ProviderStatusChanged, NoticeSent. Roles: ANALYST.
