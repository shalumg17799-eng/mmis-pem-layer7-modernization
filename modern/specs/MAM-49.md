---
key: MAM-49
localId: L-S040
epic: MAM-7
service: revalidation-service
type: Story
title: "Revalidation case states: re-screening requested and provider no longer active"
slug: rev-cases-2
specVersion: 1
descriptionSha256: 433df26b9eee14629aeef5c3b0a41dd158a0ab9433a60e39044d6a14033b0119
jiraUpdated: 2026-11-18T09:17:17.945-0600
rules: []
decisions: [D-029, D-006]
dependsOn: [MAM-45, MAM-48]
mainJourney: false
labels: [svc-revalidation-service, kag-L-S040, agentic, decision-D-029, decision-D-006]
builtCommit: a529d4f
testsSha256: a0fc1e7801758133d9af7291f97fc9581557d8fce35f208e702ebe39783e1d43
---
## Business requirement
A revalidation case passes through stages, and staff need to see which stage it is in: waiting to be screened, being screened, finished, or no longer needed because the provider is no longer active. This story defines when a case moves to the screening stage and when it is cancelled, so that the work queue never shows cases that nobody can act on, and so that a provider who is reinstated while still overdue is picked up again at the next sweep.

## Acceptance criteria
### AC-1 A case moves to IN_SCREENING when the re-screening is requested
Given the sweep opened a case for 1003000222 in status DUE
When the RevalidationDue event for that case has been published
Then GET /api/v1/revalidations?npi=1003000222 shows the case with status IN_SCREENING
Basis: QUESTION C-13, modern/contracts/asyncapi/events.yaml#RevalidationDue (re-screening starts), modern/contracts/openapi/revalidation-service.yaml#RevalidationCase (status)

### AC-2 A provider that is no longer ACTIVE cancels its open case
Given an open case (DUE or IN_SCREENING) for 1003000222
When provider-service publishes ProviderStatusChanged for 1003000222 from A to S (and, in separate examples, to T or D)
Then the case becomes CANCELLED with closedAt set and is listed by GET /api/v1/revalidations?status=CANCELLED
Basis: QUESTION C-14, R-PRVREV01-cbl-00003, D-006, D-029

### AC-3 A late screening result does not reopen a cancelled case
Given the case for 1003000222 is CANCELLED
When ScreeningCompleted with reason REVALIDATION arrives for 1003000222
Then GET /api/v1/revalidations?npi=1003000222 still shows the case CANCELLED and no error is raised
Basis: QUESTION C-14, D-029

### AC-4 A reinstated provider that is still overdue gets a new case
Given the case for 1003000222 was CANCELLED, the provider is reinstated (ProviderStatusChanged from S to A) and its due date is still 2026-02-28
When the sweep runs for 2026-03-31
Then a new open case (status DUE or IN_SCREENING) is opened for 1003000222 and the cancelled case remains listed with status CANCELLED
Basis: QUESTION C-14, R-PRVREV01-cbl-00004, D-029, D-006

## Out of scope
- Opening cases and the REVALQ file (rev-sweep)
- Completing cases (rev-cases)
- Sanctions themselves (sanctions-service)

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Related rules: R-PRVREV01-cbl-00003, R-PRVREV01-cbl-00004
Programs: No direct legacy equivalent: PRVREV01.cbl CHECK-NEXT 81-84 only tested that the provider was ACTIVE and due; no stage was kept (D-029)
Decisions: D-029 FIX: one revalidation case per provider with its own state (DUE, IN_SCREENING, COMPLETED, CANCELLED); D-006 FIX: status S (suspended) exists; a provider that leaves ACTIVE cancels its open case

## Technical notes (agent-owned)
Service: revalidation-service. Endpoints/events: GET /api/v1/revalidations, RevalidationDue, ProviderStatusChanged, ScreeningCompleted. Roles: ANALYST, SUPERVISOR, SYSTEM.
