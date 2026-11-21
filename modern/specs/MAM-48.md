---
key: MAM-48
localId: L-S039
epic: MAM-7
service: revalidation-service
type: Story
title: "Revalidation cases: list them and complete one"
slug: rev-cases
specVersion: 1
descriptionSha256: db743a5de438a0335160ad560b2f4f3a32ee94aae49dae1c9e1a0b9ced293d16
jiraUpdated: 2026-11-18T09:17:07.035-0600
rules: [R-PRVREV01-ADD-01]
decisions: [D-003, D-029, D-023]
dependsOn: [MAM-45]
mainJourney: false
labels: [svc-revalidation-service, kag-L-S039, agentic, decision-D-003, decision-D-029, decision-D-023]
builtCommit: a529d4f
testsSha256: f130f9ef4bc754b77327e169727e0a92dba4108473ccc27745a8fdab5959c582
---
## Business requirement
Once a revalidation case has been opened, staff must be able to see it, and the case must be closed when the provider's re-screening is done, so that the provider is not chased again and, after a pass, gets a new five-year date. Today the legacy queue only listed the provider and nothing recorded what happened afterwards. After this story analysts see the open and closed cases, a re-screening result closes the case by itself, an analyst can close it by hand, and the provider's next due date comes from the provider record, not from this service.

## Acceptance criteria
### AC-1 Analysts list revalidation cases
Given cases exist in several statuses, including one DUE case for NPI 1003000222
When an ANALYST or SUPERVISOR calls GET /api/v1/revalidations?status=DUE&page=0&size=20, then ?npi=1003000222, then ?status=OPEN
Then the first two responses are 200 with items (caseId, npi, dueDate, status, openedAt, closedAt), page, size and total holding only the matching cases; the invalid status value OPEN returns 400 with a problem+json body
Basis: modern/contracts/openapi/revalidation-service.yaml#listRevalidationCases (status enum, 400), D-029, modern/docs/ux/screen-inventory.md#console work-queues (GET /api/v1/revalidations), modern/docs/ux/journeys.md#J6

### AC-2 An analyst or the system completes a case with a screening result
Given an open case for NPI 1003000222 and a completed REVALIDATION screening of 1003000222 with result PASS
When an ANALYST (or SYSTEM) calls POST /api/v1/revalidations/{caseId}/completion with that screeningId and result PASS
Then the response is 200 with the case in status COMPLETED and closedAt set, and GET /api/v1/revalidations?status=DUE and ?status=IN_SCREENING no longer list it
Basis: modern/contracts/openapi/revalidation-service.yaml#completeRevalidation, D-003, D-029

### AC-3 Unknown and closed cases are refused
Given an unknown caseId, and a case that is already COMPLETED or CANCELLED
When POST /api/v1/revalidations/{caseId}/completion is called for each
Then the unknown case returns 404 and the closed case returns 409, both with a problem+json body, and the closed case is unchanged
Basis: modern/contracts/openapi/revalidation-service.yaml#completeRevalidation (404, 409), D-029

### AC-4 A passed re-screening completes the case by itself
Given an open case for 1003000222, and screening-service publishes ScreeningCompleted with reason REVALIDATION and result PASS for 1003000222
When revalidation-service processes the event
Then GET /api/v1/revalidations?npi=1003000222 shows the case COMPLETED with closedAt set, without any REST call
Basis: modern/contracts/asyncapi/events.yaml#ScreeningCompleted, modern/docs/architecture/overview.md#Main flows (4), modern/docs/ux/journeys.md#J6 (on PASS the case completes), D-003, D-029

### AC-5 A failed re-screening closes the case but does not move the due date
Given an open case for 1003000222 (due 2026-02-28) and ScreeningCompleted with reason REVALIDATION and result FAIL for 1003000222
When revalidation-service processes the event
Then the case becomes COMPLETED, the due date tracked for 1003000222 stays 2026-02-28 (no new date reaches revalidation-service), revalidation-service publishes nothing for it, and the screening is listed by GET /api/v1/screenings?result=FAIL
Basis: QUESTION C-12, modern/contracts/openapi/revalidation-service.yaml#CompletionRequest (result PASS or FAIL), D-003

### AC-6 Only revalidation screenings close cases
Given an open case for 1003000222, a ScreeningCompleted with reason INITIAL or MANUAL for 1003000222, and a ScreeningCompleted with reason REVALIDATION for NPI 1003000999, which has no open case
When revalidation-service processes the events
Then the case for 1003000222 keeps its status, and the event for 1003000999 is ignored without error and opens no case
Basis: modern/docs/architecture/overview.md#Main flows (4: on ScreeningCompleted REVALIDATION), modern/contracts/asyncapi/events.yaml#ScreeningCompleted (reason), D-029

### AC-7 The next due date comes from provider-service and is followed here
Given the case for 1003000222 was completed with PASS, and provider-service then publishes ProviderStatusChanged for 1003000222 (from A to A) with revalidationDueDate 2031-03-31
When the sweep runs for 2026-04-30
Then no new case is opened for 1003000222 and its REVALQ file has no row for it, because the tracked due date is now 2031-03-31
Basis: R-PRVREV01-ADD-01, D-003, ADR-002, QUESTION C-10, modern/docs/architecture/overview.md#Where this design differs (5), modern/contracts/asyncapi/events.yaml#ProviderStatusChanged

### AC-8 Only the right roles can list and complete cases
Given tokens for ANALYST, SUPERVISOR, SYSTEM, ADMIN and PROVIDER, and a request with no token
When each calls GET /api/v1/revalidations and POST /api/v1/revalidations/{caseId}/completion
Then GET is accepted for ANALYST and SUPERVISOR; POST is accepted for SYSTEM and ANALYST; every other role receives 403 and no token receives 401
Basis: modern/docs/architecture/nfr.md#Security, D-023, ADR-007, modern/contracts/openapi/revalidation-service.yaml (x-roles)

## Out of scope
- Opening cases (rev-sweep)
- The states IN_SCREENING and CANCELLED (rev-cases-2)
- Computing and storing the next due date (provider-service; question C-10)
- Any automatic suspension or termination after a failed revalidation (question C-12: not done)
- The console work queue (ui-console-queues)

## Legacy trace (agent-owned, do not edit)
Rules: R-PRVREV01-ADD-01
Programs: No direct legacy equivalent: source-code/BATCH/PRVREV01.cbl QUEUE-FOR-REVAL 88-94 wrote the queue row and nothing tracked it afterwards (D-029). The due date was reset daily by source-code/BATCH/PRVRSK01.cbl SET-REVALIDATION-DATE 126-137 (R-PRVREV01-ADD-01, D-003)
Decisions: D-003 FIX: the due date moves only at approval and at a completed revalidation, never daily; it is set by provider-service; D-029 FIX: one revalidation case per provider with its own state; D-023 FIX: role-based access on every endpoint

## Technical notes (agent-owned)
Service: revalidation-service. Endpoints/events: GET /api/v1/revalidations, POST /api/v1/revalidations/{caseId}/completion, ScreeningCompleted, ProviderStatusChanged. Roles: ANALYST, SUPERVISOR, SYSTEM.
