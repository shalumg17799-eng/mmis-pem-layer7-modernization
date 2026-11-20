---
key: MAM-90
localId: L-S064
epic: MAM-13
service: web-ui
type: Story
title: "Console: record a sanction (supervisor)"
slug: ui-console-sanction
specVersion: 1
descriptionSha256: a501fc809319cdbaa55dfe0cdc94430f50a5aa07b2907ad132ec7ed706000bc1
jiraUpdated: 2026-11-18T09:23:26.637-0600
rules: []
decisions: [D-006, D-018, D-026, D-027, D-023, D-024, D-004]
dependsOn: [MAM-80, MAM-52]
mainJourney: false
labels: [svc-web-ui, kag-L-S064, agentic, decision-D-006, decision-D-018, decision-D-026, decision-D-027, decision-D-023, decision-D-024, decision-D-004]
builtCommit:
testsSha256:
---
## Business requirement
A supervisor can terminate, suspend or reinstate a provider by choosing the action, giving a reason and an effective date. The provider's status changes at once, the history keeps every action, and a suspension is a status of its own, no longer confused with a denial. The form never invents rules about which status may be sanctioned, and an unknown action is refused with a message at the field.

## Acceptance criteria
### AC-1 Only supervisors reach the sanction form
Given supervisor1, analyst1, admin1 and provider1 are signed in and an Active provider 1003000111 exists
When Each opens /console/providers/1003000111
Then Only supervisor1 sees the action 'Record sanction', which opens /console/providers/1003000111/sanctions/new; typing that address as analyst1, admin1 or provider1 shows the access-denied page
Basis: D-023, modern/docs/ux/screen-inventory.md (console record-sanction, SUPERVISOR), modern/docs/ux/journeys.md#J5 (step 1), modern/contracts/openapi/sanctions-service.yaml#recordSanction (SUPERVISOR)

### AC-2 The form and its required values
Given supervisor1 is on the sanction form of 1003000111
When The page opens and supervisor1 chooses 'Record sanction' with no action and no date
Then The form shows the NPI (read-only), an Action list (Terminate (T), Suspend (S), Reinstate (R)), a Reason (at most 60 characters) and an Effective date (MM/DD/YYYY); with no action or an empty or impossible date nothing is sent and field errors with an error summary appear; a valid date is sent in the ISO form (for example 03/31/2026 as 2026-03-31)
Basis: modern/docs/ux/wireframes/console-record-sanction.html, modern/contracts/openapi/sanctions-service.yaml#SanctionRequest (npi, action and effectiveDate required; reason maxLength 60), D-004, ADR-009, .claude/skills/frontend-stack/SKILL.md sections 5 and 6

### AC-3 Each action sets its own status
Given Active providers 1003000333 and 1003000777
When supervisor1 records Suspend (S) for 1003000333, Terminate (T) for 1003000777 and then Reinstate (R) for 1003000333, each with a reason and the effective date 03/31/2026
Then Each time the page sends POST /api/v1/sanctions with npi, action, reason and effectiveDate 2026-03-31, receives 201 with resultingStatus S, T and A, and confirms 'Suspended (S)', 'Terminated (T)' and 'Active (A)' respectively, so a suspension is never shown as Denied
Basis: D-006, R-PRVSAN01-cbl-00014, R-PRVSAN01-cbl-00005, R-PRVSAN01-cbl-00015, R-PRVSAN01-cbl-00016, modern/contracts/openapi/sanctions-service.yaml#Sanction (resultingStatus T, S, A), modern/docs/ux/journeys.md#J5 (steps 2 and 4)

### AC-4 Suspend and reinstate keep a history (journey J5)
Given Provider 1003000111 is Active
When supervisor1 records Suspend (S) with a reason and an effective date, and later Reinstate (R)
Then After the first action the page confirms 'Suspended (S)' and after the second 'Active (A)'; the sanction history on the page (GET /api/v1/sanctions?npi=1003000111) lists both rows with Effective date, Action, Reason and By
Basis: modern/docs/ux/journeys.md#J5 (steps 2 and 4), D-018, R-PRVSAN01-ADD-03, modern/docs/ux/wireframes/console-record-sanction.html (Sanction history: Effective, Action, Reason, By), modern/contracts/openapi/sanctions-service.yaml#listSanctions

### AC-5 An unknown action code is rejected at the field (journey J5)
Given A request that carries an action other than T, S or R (the form only offers T, S and R, so the test sends one through a test double of the API)
When The service answers 422
Then The message of the answer (which names the action field and the allowed values T, S and R) is shown next to the Action field and in the error summary; the status and history shown on the page are unchanged
Basis: D-026, modern/docs/ux/journeys.md#J5 (step 5), R-PRVSAN01-ADD-02, modern/contracts/openapi/sanctions-service.yaml#recordSanction (422)

### AC-6 No status is blocked by the page
Given Providers that are Pending (P), Denied (D), Terminated (T), Suspended (S) and Active (A)
When supervisor1 opens the sanction form of each
Then All three actions are offered whatever the status; for example Reinstate (R) is offered for a Denied provider and the page sends it, and the page shows whatever status the service returns
Basis: D-027, Q-006, R-PRVSAN01-ADD-04, modern/docs/ux/screen-inventory.md (console record-sanction: D-027)

### AC-7 Unknown providers and failures are shown, nothing is claimed that did not happen
Given The form for NPI 1003000999 (no provider), and then API answers 403, 5xx and a network failure
When supervisor1 records a sanction
Then A 404 shows 'No provider found' and records nothing; a 403 shows 'You are not allowed to record sanctions'; a 5xx or network failure shows 'The sanction could not be applied. Nothing was recorded.' and the history and status shown stay as they were
Basis: D-024, D-023, modern/contracts/openapi/sanctions-service.yaml#recordSanction (403, 404, 422), R-PRVSAN01-ADD-01

### AC-8 The sanction form has zero axe violations
Given The Playwright test opens the empty form, the form with field errors, the form after a successful action and the failure state
When It runs an axe scan on each and records a sanction with the keyboard only
Then Every scan reports zero violations; every input has a visible label; errors are shown next to the field and in a linked summary; the history table has header cells
Basis: .claude/skills/frontend-stack/SKILL.md section 5, modern/docs/architecture/nfr.md (Accessibility), modern/docs/ux/journeys.md#J5

## Out of scope
- Applying the status and sending the notice (sanctions-service, provider-service, notification-service)
- The SUS and RST notices: sent by ntf-send, shown in the Notices panel of ui-console-detail-2 and checked end to end by e2e-j5
- The public directory no longer showing a suspended provider (ui-public-directory and dir-projection)
- Loading sanctions from the legacy SANCIN file (ui-console-batch)
- Restricting actions by the current status (D-027 preserves the legacy rule, pending the business answer)

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Related rules: R-PRVSAN01-ADD-01, R-PRVSAN01-ADD-02, R-PRVSAN01-ADD-03, R-PRVSAN01-ADD-04, R-PRVSAN01-cbl-00005, R-PRVSAN01-cbl-00014, R-PRVSAN01-cbl-00015, R-PRVSAN01-cbl-00016
Programs: PRVSAN01.cbl:104-116 (APPLY-SANCTION: T gives TERMINATED, S gives DENIED at :109-110, R gives ACTIVE at :112; any other code is accepted), 124-138 (GENERATE-NOTICE, CALL at :134). No job step runs PRVSAN01.
Decisions: D-006 A new SUSPENDED status; a suspension is no longer a denial; D-018 FIX: sanction history is kept and shown; D-026 FIX (accepted, Q-010): unknown action codes are rejected with an error; D-027 PRESERVE (Q-006): sanctions apply whatever the current status; D-023 FIX: only SUPERVISOR records sanctions; D-024 FIX: failures are surfaced; D-004 FIX: 4-digit years; the date is sent as ISO

## Technical notes (agent-owned)
Service: web-ui. Endpoints/events: POST /api/v1/sanctions, GET /api/v1/sanctions, GET /api/v1/providers/{npi}. Roles: SUPERVISOR.
