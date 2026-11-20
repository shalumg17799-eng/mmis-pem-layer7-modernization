---
key: MAM-27
localId: L-S019
epic: MAM-3
service: provider-service
type: Story
title: "Analyst approves or denies an enrolment; approval sets ACTIVE and the revalidation due date"
slug: prv-approve
specVersion: 1
descriptionSha256: 10b18122f466a9bf5e1495652a1661049ca59c5f2992e4c8549ab2fd7b239758
jiraUpdated: 2026-11-18T09:15:00.054-0600
rules: [R-PRVRSK01-ADD-01, R-PRVRSK01-cbl-00009, R-PRVRSK01-cbl-00047]
decisions: [D-003, D-014, D-019, D-023, D-024]
dependsOn: [MAM-21, MAM-23]
mainJourney: true
labels: [svc-provider-service, kag-L-S019, agentic, decision-D-003, decision-D-014, decision-D-019, decision-D-023, decision-D-024, slice-main-journey]
builtCommit: pending-user-commit
testsSha256: 74effd01f2b9bd2919f1cc392231d15db69a1f36a8d7411afeb45cc0db403afe
---
## Business requirement
An analyst reviews a provider whose application is pending and either approves or denies the enrolment. Approval is the only way a new provider becomes active; it is allowed only after screening passed, and it starts the five-year revalidation clock. A denial closes the enrolment. Both decisions are announced so that the directory, the provider and the revalidation process are informed, and a decision cannot be taken twice.

## Acceptance criteria
### AC-1 Approval sets ACTIVE and the revalidation due date
Given provider 1003000111 is PENDING, screenFlag P, riskTier L, and the service clock date is 2026-11-18
When an ANALYST (analyst1) sends POST /api/v1/providers/1003000111/approval with decision APPROVE
Then the response is 200 with enrollStatus A and revalidationDueDate 2031-11-18 (approval date + 5 years); a later GET /api/v1/providers/1003000111 returns the same values
Basis: D-019, D-003, R-PRVRSK01-cbl-00009, R-PRVRSK01-cbl-00047, modern/docs/ux/journeys.md#J1, modern/contracts/openapi/provider-service.yaml (decideProvider)

### AC-2 Approval publishes ProviderStatusChanged
Given the approval in AC-1 has been saved
When the outbox poller publishes the event
Then exactly one ProviderStatusChanged event on pem.provider.events with key 1003000111 has from P, to A, actor analyst1, revalidationDueDate 2031-11-18, providerType IN, displayName 'MARIA RIVERA', taxonomy 207Q00000X and medicaidId MCD000111
Basis: modern/contracts/asyncapi/events.yaml (ProviderStatusChanged), D-019, D-028, modern/docs/ux/journeys.md#J1

### AC-3 Denial sets DENIED and leaves no due date
Given provider 1003000444 is PENDING with screenFlag F
When an ANALYST sends POST /api/v1/providers/1003000444/approval with decision DENY and reason 'Owner 1990000111 is on the OIG LEIE'
Then the response is 200 with enrollStatus D and revalidationDueDate null, and a ProviderStatusChanged event with from P, to D and that reason is published
Basis: D-019, Q-A2 (DENY allowed when the screen flag is P or F), modern/docs/ux/journeys.md#J3

### AC-4 Approval needs a passed screening; denial needs a finished one
Given provider 1003000444 is PENDING with screenFlag F and provider 1003000222 is PENDING with screenFlag U
When an ANALYST sends decision APPROVE for 1003000444, APPROVE for 1003000222 and DENY for 1003000222
Then each response is 422 problem+json, both providers are unchanged (enrollStatus P, revalidationDueDate null) and no ProviderStatusChanged event is published
Basis: D-019 (approval follows a Pass screening), Q-A2, modern/contracts/openapi/provider-service.yaml (422 Business validation failed)

### AC-5 A decision cannot be taken twice and never resets the due date
Given provider 1003000111 is ACTIVE with revalidationDueDate 2031-11-18
When an ANALYST sends decision APPROVE, and then DENY, for it again
Then each response is 409 problem+json, enrollStatus stays A, revalidationDueDate stays 2031-11-18 and no event is published
Basis: R-PRVRSK01-ADD-01, D-003, Q-A2 (a decision needs status PENDING), modern/contracts/openapi/provider-service.yaml (409 Conflict with the current state)

### AC-6 An unknown provider gives 404
Given no provider with NPI 1003000999 exists
When an ANALYST sends POST /api/v1/providers/1003000999/approval with decision APPROVE
Then the response is 404 problem+json and nothing is created
Basis: modern/contracts/openapi/provider-service.yaml (404 Not found), D-024

### AC-7 Only ANALYST and SUPERVISOR can decide
Given providers 1003000111 and 1003000333 are PENDING with screenFlag P
When POST /api/v1/providers/1003000111/approval with decision APPROVE is called with a PROVIDER token (provider1), an ADMIN token, a SYSTEM token and no token; then an ANALYST approves 1003000111 and a SUPERVISOR approves 1003000333
Then PROVIDER, ADMIN and SYSTEM get 403 and no token gets 401, with 1003000111 still P after those calls; the ANALYST and the SUPERVISOR each get 200
Basis: D-023, ADR-007, modern/docs/architecture/nfr.md (roles per endpoint), modern/contracts/openapi/provider-service.yaml (decideProvider x-roles ANALYST, SUPERVISOR)

### AC-8 Approval does not wait for the risk tier
Given provider 1003000111 is PENDING, screenFlag P and riskTier null
When an ANALYST approves it
Then the response is 200 with enrollStatus A and riskTier null; the tier is saved later when RiskTierAssigned arrives (prv-risk-tier)
Basis: Q-A5 (no risk tier needed to approve), modern/contracts/openapi/provider-service.yaml (decideProvider)

## Out of scope
- Date arithmetic edge cases (century, leap day) and the due date after a completed revalidation: prv-approve-2.
- Console screens for approving and denying and the work queues: ui-console-approval, ui-console-queues.
- The notices for approval and denial: ntf-send-2. The directory update: dir-projection. The revalidation case: rev-sweep.
- Automatic approval: none exists; D-019 requires a human analyst decision.

## Legacy trace (agent-owned, do not edit)
Rules: R-PRVRSK01-ADD-01, R-PRVRSK01-cbl-00009, R-PRVRSK01-cbl-00047
Programs: PRVRSK01: source-code/BATCH/PRVRSK01.cbl:70-97 (no status filter, due date reset each run, ADD-01), 137 (MOVE WS-DUE-DATE TO PRV-REVAL-DUE-DATE). Job step: PRVDAY01 STEP040 (source-code/JCL/PRVDAY01.jcl). Approval itself has no legacy program: nothing in the estate sets ACTIVE except the sanction reinstate (PRVSAN01.cbl:112; D-019).
Decisions: D-003 FIX: the due date is set at approval (and at each completed revalidation), never reset on every run; D-014 FIX: provider-service is the only writer of status and due date; D-019 FIX: an analyst approval sets ACTIVE after a Pass screening; D-023 FIX: role-based access on every endpoint; D-024 FIX: explicit errors (404, 409, 422)

## Technical notes (agent-owned)
Service: provider-service. Endpoints/events: POST /api/v1/providers/{npi}/approval, ProviderStatusChanged. Roles: ANALYST, SUPERVISOR.
