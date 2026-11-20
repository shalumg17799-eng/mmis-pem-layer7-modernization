---
key: MAM-28
localId: L-S033
epic: MAM-5
service: screening-service
type: Story
title: "Rescreen a provider on request or when revalidation is due"
slug: scr-rescreen
specVersion: 1
descriptionSha256: dae5819d32a9c207fbe80f469ff390087ee2cb7ad00dfe2fbfd1a7f0b4631655
jiraUpdated: 2026-11-18T09:15:13.920-0600
rules: []
decisions: [D-005, D-029, D-018, D-023]
dependsOn: [MAM-25]
mainJourney: false
labels: [svc-screening-service, kag-L-S033, agentic, decision-D-005, decision-D-029, decision-D-018, decision-D-023]
builtCommit: 6e24725
testsSha256: 06d7f272b55d75e45c95e18c2e8d8c0b5216204b180cd20b4a4252a33b3a2df0
---
## Business requirement
A provider's situation changes over time: a licence lapses, a name appears on an exclusion list, a source that was down comes back, and every enrolled provider must be revalidated about every five years. The state therefore needs to be able to screen a provider again without waiting for a new application. An analyst can ask for a fresh screening, and the monthly revalidation process triggers one automatically. Earlier results are kept so that the history is complete.

## Acceptance criteria
### AC-1 An analyst starts a screening on request
Given provider 1003000555 exists in provider-service and has one earlier screening, and an ANALYST token
When the ANALYST calls POST /api/v1/screenings with npi 1003000555
Then the response is 202 with a Screening of reason MANUAL and status RUNNING, and when it completes GET /api/v1/screenings/{screeningId} shows four fresh results while GET /api/v1/screenings?npi=1003000555 still returns the earlier screening unchanged
Basis: modern/contracts/openapi/screening-service.yaml#requestScreening, D-005, D-018

### AC-2 A malformed or unknown NPI is refused
Given an ANALYST token
When POST /api/v1/screenings is called with npi 123, and then with npi 1003000999, which provider-service does not know
Then the first call returns 400 and the second returns 404, both with a problem+json body, and no screening is created
Basis: modern/contracts/openapi/screening-service.yaml#requestScreening (npi pattern, 400, 404), D-024, .claude/skills/java-microservices/SKILL.md#6 (RFC 7807 errors)

### AC-3 Revalidation due starts a re-screening
Given revalidation-service has published RevalidationDue for a case of NPI 1003000222 with dueDate 2026-02-28
When screening-service receives the event
Then a screening with reason REVALIDATION is started for 1003000222 and, when it completes, a ScreeningCompleted with reason REVALIDATION is published for it
Basis: modern/contracts/asyncapi/events.yaml#RevalidationDue, modern/contracts/asyncapi/events.yaml#ScreeningCompleted, modern/docs/architecture/overview.md#Main flows (4), D-029

### AC-4 One re-screening per revalidation case
Given the RevalidationDue of criterion 3 is delivered twice with the same event id
When both deliveries are processed
Then exactly one REVALIDATION screening exists for 1003000222 and exactly one ScreeningCompleted is published for it
Basis: ADR-003, D-029

### AC-5 A re-screening uses the provider's current data
Given provider 1003000111 passed its first screening with licence MD12345, and provider-service GET /api/v1/providers/1003000111 now returns licence PT99900 in TX (EXPIRED on the licence-board mock)
When a MANUAL screening of 1003000111 runs
Then the licence-board mock's request log shows a lookup for PT99900, not for MD12345, and the new screening's LICBOARD result is F with errorCode E202
Basis: QUESTION C-2, D-014, ADR-002, D-016, aws-transform/test-data/license_board.txt

### AC-6 Only analysts, supervisors and the system can start a screening
Given tokens for ANALYST, SUPERVISOR, SYSTEM, PROVIDER and ADMIN, and a request with no token
When each calls POST /api/v1/screenings with npi 1003000111
Then ANALYST, SUPERVISOR and SYSTEM are accepted with 202, PROVIDER and ADMIN receive 403 and the request with no token receives 401
Basis: D-023, ADR-007, modern/docs/architecture/nfr.md#Security, modern/contracts/openapi/screening-service.yaml#requestScreening (x-roles)

## Out of scope
- Closing the revalidation case (rev-cases)
- The unscreened-providers batch run (scr-batch-run)
- What provider-service does with a failed manual re-screening of an ACTIVE provider (no automatic status change is defined; see question C-12 for revalidation)
- The console button that starts a screening (ui-console-detail)

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: No legacy equivalent: PRVSCR01.cbl (lines 90-92) screens only records whose screen flag is U, and PRVREV01.cbl (88-94) only wrote a queue row; nothing ever screened a provider again
Decisions: D-005 REPLACE: the same real checks are used for every screening reason; D-029 FIX: one revalidation case per provider; its due notice starts exactly one re-screening; D-018 FIX: results of every screening are kept; D-023 FIX: role-based access on every endpoint

## Technical notes (agent-owned)
Service: screening-service. Endpoints/events: POST /api/v1/screenings, GET /api/v1/screenings, GET /api/v1/screenings/{screeningId}, RevalidationDue, ScreeningCompleted. Roles: ANALYST, SUPERVISOR, SYSTEM.
