---
key: MAM-33
localId: L-S021
epic: MAM-3
service: provider-service
type: Story
title: "Apply a sanction status change to the provider (TERMINATED, SUSPENDED, ACTIVE)"
slug: prv-status-change
specVersion: 1
descriptionSha256: 73754afbd97da981bc34a2b97d8f8e1e75fc1e924910c50a135900c600c77a5e
jiraUpdated: 2026-11-18T09:15:41.147-0600
rules: [R-PRVSAN01-ADD-04, R-PRVSAN01-ADD-07, R-PRVSAN01-cbl-00004, R-PRVSAN01-cbl-00005, R-PRVSAN01-cbl-00006, R-PRVSAN01-cbl-00007, R-PRVSAN01-cbl-00014, R-PRVSAN01-cbl-00015, R-PRVSAN01-cbl-00016, R-PRVSAN01-cbl-00026, R-PRVSAN01-cbl-00028, R-PRVSAN01-cbl-00030, R-PRVSAN01-cbl-00031, R-PRVSAN01-cbl-00034, R-PRVSAN01-cbl-00035, R-PRVSAN01-cbl-00040, R-PRVSAN01-cbl-00041, R-PRVSAN01-cbl-00042, R-PRVSAN01-cbl-00043]
decisions: [D-006, D-013, D-014, D-023, D-024, D-026, D-027]
dependsOn: [MAM-17]
mainJourney: false
labels: [svc-provider-service, kag-L-S021, agentic, decision-D-006, decision-D-013, decision-D-014, decision-D-023, decision-D-024, decision-D-026, decision-D-027]
builtCommit:
testsSha256: 97d8c78ce8cddafc2b9f3d81e5802c068376c10f4622a43cf046baca58d0b164
---
## Business requirement
When a sanction is applied to an enrolled provider, the provider's enrolment status must change accordingly: a termination ends the enrolment, a suspension pauses it and is shown separately from a denial, and a reinstatement makes the provider active again. The change can only be requested through the provider service, so that no other system rewrites the provider record. It is applied whatever the provider's current status is, it is announced to the other services, and an unknown action or an incomplete request is refused.

## Acceptance criteria
### AC-1 Terminate sets TERMINATED and counts it
Given provider 1003000111 is ACTIVE
When a SYSTEM caller sends POST /api/v1/providers/1003000111/status-changes with action TERMINATE, reason 'LEIE exclusion' and effectiveDate 2026-03-31
Then the response is 200 with enrollStatus T, and the terminations counter on /actuator/metrics rises by 1
Basis: R-PRVSAN01-cbl-00004, R-PRVSAN01-cbl-00014, R-PRVSAN01-cbl-00026, R-PRVSAN01-cbl-00040, D-013

### AC-2 Suspend sets SUSPENDED, not DENIED
Given provider 1003000111 is ACTIVE
When a SYSTEM caller sends action SUSPEND, reason 'LEIE exclusion' and effectiveDate 2026-03-31
Then the response is 200 with enrollStatus S (not D) and the terminations counter does not change
Basis: R-PRVSAN01-cbl-00005, R-PRVSAN01-cbl-00015, R-PRVSAN01-cbl-00030, R-PRVSAN01-cbl-00041, D-006, modern/docs/ux/journeys.md#J5

### AC-3 Reinstate sets ACTIVE
Given provider 1003000111 is SUSPENDED (status S)
When a SYSTEM caller sends action REINSTATE with effectiveDate 2026-04-30
Then the response is 200 with enrollStatus A and the terminations counter does not change
Basis: R-PRVSAN01-cbl-00006, R-PRVSAN01-cbl-00016, R-PRVSAN01-cbl-00034, R-PRVSAN01-cbl-00042, modern/docs/ux/journeys.md#J5

### AC-4 The change applies whatever the current status is
Given provider 1003000444 is in status D (denied) and provider 1003000222 is in status P (pending)
When REINSTATE is sent for 1003000444 and TERMINATE for 1003000222
Then GET /api/v1/providers/1003000444 returns enrollStatus A and GET /api/v1/providers/1003000222 returns T (the legacy rule applied as written)
Basis: R-PRVSAN01-ADD-04, D-027, Q-006 (answered: apply as written)

### AC-5 The new status is saved and announced
Given provider 1003000111 is ACTIVE
When a SYSTEM caller (service-account-pem-services) applies SUSPEND with reason 'LEIE exclusion'
Then a later GET /api/v1/providers/1003000111 returns enrollStatus S, and one ProviderStatusChanged event with key 1003000111 has from A, to S, reason 'LEIE exclusion' and actor service-account-pem-services
Basis: R-PRVSAN01-cbl-00007, R-PRVSAN01-cbl-00028, R-PRVSAN01-cbl-00031, R-PRVSAN01-cbl-00035, R-PRVSAN01-cbl-00043, modern/contracts/asyncapi/events.yaml (ProviderStatusChanged), D-013

### AC-6 An unknown action or an incomplete request is refused
Given provider 1003000111 is ACTIVE
When a SYSTEM caller sends action PURGE with effectiveDate 2026-03-31, and then action SUSPEND with no effectiveDate
Then each response is 422 problem+json, the provider is unchanged (status A, same version) and no ProviderStatusChanged event is published (the legacy program would have rewritten the record and called the general notice)
Basis: D-026, D-024, modern/contracts/openapi/provider-service.yaml (StatusChangeRequest: action enum TERMINATE, SUSPEND, REINSTATE; effectiveDate required; 422 is the only error listed for an invalid body)

### AC-7 An unknown provider gives 404 and changes nothing
Given no provider with NPI 1003000999 exists
When a SYSTEM caller sends action TERMINATE with effectiveDate 2026-03-31 for 1003000999
Then the response is 404 problem+json, no provider is created and no event is published
Basis: modern/contracts/openapi/provider-service.yaml (404 Not found), D-024

### AC-8 Only SYSTEM and SUPERVISOR can change the status
Given providers 1003000111 and 1003000333 are ACTIVE
When POST /api/v1/providers/1003000111/status-changes with action SUSPEND is called with an ANALYST token, a PROVIDER token (provider1), an ADMIN token and no token; then a SYSTEM caller suspends 1003000111 and a SUPERVISOR (supervisor1) suspends 1003000333
Then ANALYST, PROVIDER and ADMIN get 403 and no token gets 401, with 1003000111 still A after those calls; SYSTEM and SUPERVISOR each get 200
Basis: R-PRVSAN01-ADD-07, D-013, D-023, ADR-002, ADR-007, modern/docs/architecture/nfr.md (roles per endpoint)

## Out of scope
- The sanction history with reason and effective date, and validating T/S/R input (sanctions-service: san-record, san-intake-run; D-018).
- The TRM, SUS and RST notices: ntf-send.
- A business rule limiting which statuses a sanction may start from (Q-006 answered: not applied).
- Maintaining the provider end date from the sanction effective date (Q-A6: not done).
- Idempotency of a repeated call with the same sanctionId: not specified in any rule or decision.

## Legacy trace (agent-owned, do not edit)
Rules: R-PRVSAN01-ADD-04, R-PRVSAN01-ADD-07, R-PRVSAN01-cbl-00004, R-PRVSAN01-cbl-00005, R-PRVSAN01-cbl-00006, R-PRVSAN01-cbl-00007, R-PRVSAN01-cbl-00014, R-PRVSAN01-cbl-00015, R-PRVSAN01-cbl-00016, R-PRVSAN01-cbl-00026, R-PRVSAN01-cbl-00028, R-PRVSAN01-cbl-00030, R-PRVSAN01-cbl-00031, R-PRVSAN01-cbl-00034, R-PRVSAN01-cbl-00035, R-PRVSAN01-cbl-00040, R-PRVSAN01-cbl-00041, R-PRVSAN01-cbl-00042, R-PRVSAN01-cbl-00043
Programs: PRVSAN01: source-code/BATCH/PRVSAN01.cbl:104-116 (APPLY-SANCTION: T 106-108, S 109-110, R 111-112, REWRITE 115), 107-112 (status applied whatever the current status), 81-115 (cross-application REWRITE of the PEMAPP1 master, AP-06). No job runs PRVSAN01 in this estate.
Decisions: D-006 FIX: suspension is a new SUSPENDED status (S), not DENIED; D-013 FIX: sanctions reach the provider only through this status-change API; D-014 FIX: provider-service is the single owner of the status; D-023 FIX: role-based access; D-024 FIX: explicit errors (404, 422) instead of silent skips; D-026 FIX: unknown action codes are rejected; D-027 PRESERVE: the change applies whatever the current status (Q-006)

## Technical notes (agent-owned)
Service: provider-service. Endpoints/events: POST /api/v1/providers/{npi}/status-changes, ProviderStatusChanged. Roles: SYSTEM, SUPERVISOR.
