---
key: MAM-79
localId: L-S071
epic: MAM-14
service: e2e
type: Story
title: "Journey J5 end to end: a supervisor suspends and reinstates a provider"
slug: e2e-j5
specVersion: 1
descriptionSha256: 54c4a3a162cdca591ba2d076d83b5917ddaa14842a8dcacc595de985835c0a82
jiraUpdated: 2026-11-18T09:22:02.778-0600
rules: []
decisions: [D-006, D-018, D-026, D-012, D-013, D-027]
dependsOn: [MAM-90, MAM-52, MAM-33, MAM-58, MAM-61, MAM-73, MAM-50, MAM-51]
mainJourney: false
labels: [svc-e2e, kag-L-S071, agentic, decision-D-006, decision-D-018, decision-D-026, decision-D-012, decision-D-013, decision-D-027]
builtCommit: pending-user-commit
testsSha256: 566b313480a143c31e27c4ec0ef63ae65a6c900d79c74a94d9052f8e50b47452
---
## Business requirement
A supervisor must be able to suspend a provider and later reinstate them, with the provider told each time, the public directory following the status, and the full history kept. This story proves that path works for real in the running system.

## Acceptance criteria
### AC-1 The supervisor opens Record sanction from an active provider
Given supervisor1 is signed in and the seeded ACTIVE individual TESTPROV ALPHA exists
When supervisor1 opens the provider detail and chooses Record sanction
Then the page /console/providers/:npi/sanctions/new is shown with the action, reason and effective date fields
Basis: modern/docs/ux/journeys.md#J5 step 1, modern/docs/ux/screen-inventory.md (console record-sanction)

### AC-2 Suspend sets Suspended and sends notice SUS
Given the Record sanction page
When supervisor1 chooses Suspend (S), enters a reason and an effective date, and submits
Then POST /api/v1/sanctions returns 201, GET /api/v1/providers/{npi} shows status S (Suspended), pem.provider.events carries ProviderStatusChanged from A to S published by provider-service, pem.sanctions.events carries SanctionApplied with action S, and a notice with template SUS is listed by GET /api/v1/notices and visible in Mailpit
Basis: modern/docs/ux/journeys.md#J5 step 2, D-006, D-013, D-012, D-018, modern/contracts/asyncapi/events.yaml (SanctionApplied)

### AC-3 The suspended provider leaves the public directory
Given the provider is Suspended
When anyone without a token searches GET /api/v1/directory/providers and /directory for ALPHA
Then the provider is not listed
Basis: modern/docs/ux/journeys.md#J5 step 3, modern/docs/architecture/overview.md (main flow 6: active providers only), D-006

### AC-4 Reinstate sets Active and sends notice RST
Given the provider is Suspended
When supervisor1 records action Reinstate (R)
Then GET /api/v1/providers/{npi} shows status A (Active), SanctionApplied carries action R, and a notice with template RST is listed and visible in Mailpit
Basis: modern/docs/ux/journeys.md#J5 step 4, D-006, D-012, D-027

### AC-5 The history shows both actions
Given both sanctions were recorded
When GET /api/v1/sanctions?npi=<npi> is called and the detail sanctions panel is opened
Then both the Suspend and the Reinstate are listed, in order, with their reasons and effective dates
Basis: modern/docs/ux/journeys.md#J5 step 4, D-018

### AC-6 An unknown action code is rejected
Given the provider is Active
When POST /api/v1/sanctions is called with action X
Then it answers 422 with a problem detail that names the field action, no sanction is stored, and the provider's status is unchanged
Basis: modern/docs/ux/journeys.md#J5 step 5, D-026, modern/contracts/openapi/sanctions-service.yaml (recordSanction 422; SanctionRequest action)

### AC-7 Repeatable and accessible
Given the journey as one Playwright test with an axe scan on every page it visits
When it is run three times in a row, each time from the seed state
Then all three runs pass and every axe scan reports zero violations
Basis: .claude/commands/phase-8-e2e-hardening.md steps 2 and 4, modern/docs/architecture/nfr.md (Accessibility), QF-6 (default applied)

## Out of scope
- Termination (T) paths and the SANCIN batch file (san-intake-run)
- Sanction rules that depend on the current status (Q-006: not applied)
- Role tests for the sanctions endpoints (nfr-security)

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: BATCH/PRVSAN01.cbl:104-116 (apply T, S, R; unknown codes accepted, D-026; no status check, D-027); :109-110 (S sets DENIED, D-006); :115 (REWRITE of the master, D-013); :124-138 (dynamic CALL of PRVNT*, D-012)
Decisions: D-006 FIX: Suspend sets the new SUSPENDED status S; D-018 FIX: sanction history is kept with reason and effective date; D-026 FIX: unknown action codes are rejected with an error; D-012 REPLACE: notices SUS and RST are sent by notification-service; D-013 FIX: sanctions-service changes the status through provider-service's API; D-027 PRESERVE: a reinstate applies whatever the current status (Q-006)

## Technical notes (agent-owned)
Service: e2e. Endpoints/events: POST /api/v1/sanctions, GET /api/v1/sanctions, GET /api/v1/providers/{npi}, GET /api/v1/directory/providers, GET /api/v1/notices, SanctionApplied, ProviderStatusChanged, NoticeSent. Roles: SUPERVISOR, PUBLIC.
