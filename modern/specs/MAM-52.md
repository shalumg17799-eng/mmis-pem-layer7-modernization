---
key: MAM-52
localId: L-S041
epic: MAM-8
service: sanctions-service
type: Story
title: "Record a sanction (terminate, suspend, reinstate) with history and apply it through provider-service"
slug: san-record
specVersion: 1
descriptionSha256: d2c39dc8fa8443de2aa9fe5d188e3e0f12833b00186652329d989534ea5b6932
jiraUpdated: 2026-11-18T09:17:40.844-0600
rules: [R-PRVSAN01-ADD-01, R-PRVSAN01-ADD-02, R-PRVSAN01-ADD-03, R-PRVSAN01-ADD-05, R-PRVSAN01-cbl-00002, R-PRVSAN01-cbl-00003, R-PRVSAN01-cbl-00018, R-PRVSAN01-cbl-00019, R-PRVSAN01-cbl-00020, R-PRVSAN01-cbl-00021, R-PRVSAN01-cbl-00022, R-PRVSAN01-cbl-00023, R-PRVSAN01-cbl-00024, R-PRVSAN01-cbl-00025, R-PRVSAN01-cbl-00029, R-PRVSAN01-cbl-00033]
decisions: [D-006, D-012, D-013, D-018, D-023, D-024, D-026, D-027]
dependsOn: [MAM-33]
mainJourney: false
labels: [svc-sanctions-service, kag-L-S041, agentic, decision-D-006, decision-D-012, decision-D-013, decision-D-018, decision-D-023, decision-D-024, decision-D-026, decision-D-027]
builtCommit:
testsSha256: f8cb75b87a8fb1170196945f5f48d167e5b55d82dc8f473e8aa5df506559c86f
---
## Business requirement
A supervisor can terminate, suspend or reinstate a provider, giving a reason and an effective date. The system checks that the action is one of the three known ones and that the provider exists, changes the provider's status through the provider service, keeps a permanent history of every sanction, and announces the sanction so that a notice can be sent. A suspension is a status of its own and is no longer confused with a denial. As in the old program, a sanction is applied whatever the provider's current status is. Nothing is recorded as applied if the status could not be changed.

## Acceptance criteria
### AC-1 A termination sets the provider to TERMINATED
Given A SUPERVISOR is signed in and provider 1003000111 has status A (ACTIVE) in provider-service
When POST /api/v1/sanctions is called with npi 1003000111, action T, reason 'SYNTHETIC TEST REASON' and effectiveDate 2026-03-31
Then 201 with a Sanction that has a sanctionId, npi 1003000111, action T, resultingStatus T, effectiveDate 2026-03-31, the reason, recordedAt, and recordedBy set to the supervisor; the status was changed only through provider-service's POST /api/v1/providers/1003000111/status-changes with action TERMINATE, effectiveDate 2026-03-31 and the same sanctionId, so GET /api/v1/providers/1003000111 now shows status T (TERMINATED); exactly one SanctionApplied event is published with subject 1003000111, action T, effectiveDate 2026-03-31 and the same sanctionId
Basis: R-PRVSAN01-cbl-00002, R-PRVSAN01-cbl-00018, R-PRVSAN01-cbl-00019, R-PRVSAN01-cbl-00020, R-PRVSAN01-cbl-00025, R-PRVSAN01-cbl-00004, D-012, D-013, D-018, modern/contracts/openapi/provider-service.yaml#changeProviderStatus, modern/contracts/asyncapi/events.yaml#SanctionApplied

### AC-2 A suspension sets the provider to SUSPENDED, not DENIED
Given A SUPERVISOR is signed in and provider 1003000333 has status A (ACTIVE)
When POST /api/v1/sanctions is called with npi 1003000333, action S, a reason and effectiveDate 2026-03-31
Then 201 with resultingStatus S; provider-service received a status change with action SUSPEND; GET /api/v1/providers/1003000333 shows status S (SUSPENDED) and not D (DENIED); exactly one SanctionApplied event with action S is published
Basis: R-PRVSAN01-cbl-00029, R-PRVSAN01-cbl-00005, D-006, modern/docs/ux/journeys.md#J5, modern/contracts/openapi/provider-service.yaml#StatusChangeRequest

### AC-3 A reinstatement sets the provider to ACTIVE
Given A SUPERVISOR is signed in and provider 1003000333 has status S (suspended in AC-2)
When POST /api/v1/sanctions is called with npi 1003000333, action R, a reason and effectiveDate 2026-04-30
Then 201 with resultingStatus A; provider-service received a status change with action REINSTATE; GET /api/v1/providers/1003000333 shows status A (ACTIVE); exactly one SanctionApplied event with action R is published
Basis: R-PRVSAN01-cbl-00033, R-PRVSAN01-cbl-00006, D-006, modern/docs/ux/journeys.md#J5, modern/contracts/openapi/provider-service.yaml#StatusChangeRequest

### AC-4 A sanction is applied whatever the current status
Given A SUPERVISOR is signed in; provider 1003000444 has status D (DENIED), provider 1003000555 has status P (PENDING) and provider 1003000666 has status D (DENIED)
When R is recorded for 1003000444 and for 1003000555, and T is recorded for 1003000666
Then Each call returns 201 with no refusal based on the current status: 1003000444 and 1003000555 become A (ACTIVE) and 1003000666 becomes T (TERMINATED); one SanctionApplied event is published per call
Basis: D-027, Q-006, R-PRVSAN01-ADD-04, R-PRVSAN01-cbl-00026, R-PRVSAN01-cbl-00034

### AC-5 An unknown action code is rejected
Given A SUPERVISOR is signed in and provider 1003000111 is ACTIVE
When POST /api/v1/sanctions is called for npi 1003000111 with action X, and again with an empty action
Then Each call returns 422 problem+json whose detail names the field action and the allowed values T, S and R, with no legacy code (none of E101-E205 applies); no sanction is stored (GET /api/v1/sanctions?npi=1003000111 is unchanged); provider-service receives no status change and the provider's status is unchanged; no SanctionApplied event is published, so no notice is triggered
Basis: R-PRVSAN01-ADD-02, D-026, modern/docs/ux/journeys.md#J5, Q-B10

### AC-6 A sanction for an unknown provider is not found
Given A SUPERVISOR is signed in and no provider has NPI 1003000999 (provider-service answers 404)
When POST /api/v1/sanctions is called with npi 1003000999, action T and effectiveDate 2026-03-31
Then 404 problem+json whose detail names NPI 1003000999; no sanction is stored for it and no SanctionApplied event is published
Basis: R-PRVSAN01-cbl-00003, R-PRVSAN01-cbl-00021, R-PRVSAN01-cbl-00022, R-PRVSAN01-cbl-00023, R-PRVSAN01-cbl-00024, D-013

### AC-7 A failure to apply is never reported as not found or as applied
Given A SUPERVISOR is signed in and provider-service is unreachable, or answers with a server error, for NPI 1003000111 (fault injected)
When POST /api/v1/sanctions is called with action T and effectiveDate 2026-03-31 for 1003000111
Then 503 problem+json whose detail names NPI 1003000111 (not 404 and not 201); no sanction is recorded as applied (GET /api/v1/sanctions?npi=1003000111 has no new row) and no SanctionApplied event is published
Basis: R-PRVSAN01-ADD-01, R-PRVSAN01-ADD-05, D-024, D-013, Q-B13

### AC-8 The sanction history is readable, and roles are enforced
Given Sanctions T and then R were recorded for NPI 1003000111, and tokens for every role
When GET /api/v1/sanctions?npi=1003000111 and POST /api/v1/sanctions are called
Then ANALYST, SUPERVISOR and ADMIN get 200 with items for both rows, each with action, reason, effectiveDate, recordedBy, recordedAt and resultingStatus (T and A), plus page, size and total (defaults page 0, size 20); PROVIDER and SYSTEM get 403 on the GET and a call without a token gets 401; POST is allowed only for SUPERVISOR (ANALYST, ADMIN, SYSTEM and PROVIDER get 403, no token gets 401)
Basis: R-PRVSAN01-ADD-03, D-018, D-023, ADR-007, modern/docs/architecture/nfr.md, modern/docs/ux/journeys.md#J5, modern/contracts/openapi/sanctions-service.yaml#listSanctions

## Out of scope
- Setting the status, the audit entry and ProviderStatusChanged inside provider-service (prv-status-change)
- Sending the TRM, SUS or RST notice from SanctionApplied (ntf-send, D-012)
- Removing a suspended or terminated provider from the public directory (dir-projection)
- The console record-sanction screen (ui-console-sanction)
- Any restriction of sanctions by current status (Q-006: none; D-027 preserves the legacy rule)
- Loading sanctions from a SANCIN file (san-intake-run)

## Legacy trace (agent-owned, do not edit)
Rules: R-PRVSAN01-ADD-01, R-PRVSAN01-ADD-02, R-PRVSAN01-ADD-03, R-PRVSAN01-ADD-05, R-PRVSAN01-cbl-00002, R-PRVSAN01-cbl-00003, R-PRVSAN01-cbl-00018, R-PRVSAN01-cbl-00019, R-PRVSAN01-cbl-00020, R-PRVSAN01-cbl-00021, R-PRVSAN01-cbl-00022, R-PRVSAN01-cbl-00023, R-PRVSAN01-cbl-00024, R-PRVSAN01-cbl-00025, R-PRVSAN01-cbl-00029, R-PRVSAN01-cbl-00033
Related rules: R-PRVSAN01-ADD-04, R-PRVSAN01-ADD-07, R-PRVSAN01-cbl-00004, R-PRVSAN01-cbl-00005, R-PRVSAN01-cbl-00006, R-PRVSAN01-cbl-00007, R-PRVSAN01-cbl-00008, R-PRVSAN01-cbl-00009, R-PRVSAN01-cbl-00014, R-PRVSAN01-cbl-00015, R-PRVSAN01-cbl-00016, R-PRVSAN01-cbl-00026, R-PRVSAN01-cbl-00028, R-PRVSAN01-cbl-00034, R-PRVSAN01-cbl-00035
Programs: PRVSAN01 PROCESS-SANCTION 89-102 (master lookup by NPI 95-101, not-found message 100), APPLY-SANCTION 104-116 (T 106-108, S 109-110, R 111-112, REWRITE 115), GENERATE-NOTICE 124-138; no JCL job runs PRVSAN01 in this estate
Decisions: D-006 FIX: S sets a new SUSPENDED status (S), no longer DENIED; D-012 REPLACE: the dynamic CALL to missing PRVNT programs becomes the SanctionApplied event consumed by notification-service; D-013 FIX: sanctions-service changes the status only through provider-service's API, never the provider's data; D-018 FIX: a real sanction history table (reason, effective date, who, resulting status); D-023 FIX: role check on every endpoint (POST SUPERVISOR; GET ANALYST, SUPERVISOR, ADMIN); D-024 FIX: an apply failure is surfaced (503) and distinguished from not found; nothing is recorded as applied; D-026 FIX: unknown action codes are rejected with an error; D-027 PRESERVE: T, S and R apply whatever the current status is (Q-006)

## Technical notes (agent-owned)
Service: sanctions-service. Endpoints/events: POST /api/v1/sanctions, GET /api/v1/sanctions, SanctionApplied. Roles: SUPERVISOR, ANALYST, ADMIN.
