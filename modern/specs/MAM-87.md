---
key: MAM-87
localId: L-S063
epic: MAM-13
service: web-ui
type: Story
title: "Console: approve or deny an enrolment"
slug: ui-console-approval
specVersion: 1
descriptionSha256: 02b35d17c64f7dac3e15c60078130d2f01e8e2a8f9c99a0a0dc2f90e766ad1a4
jiraUpdated: 2026-11-18T09:23:07.675-0600
rules: []
decisions: [D-019, D-003, D-004, D-023, D-024]
dependsOn: [MAM-85, MAM-27, MAM-25]
mainJourney: true
labels: [svc-web-ui, kag-L-S063, agentic, decision-D-019, decision-D-003, decision-D-004, decision-D-023, decision-D-024, slice-main-journey]
builtCommit: pending-user-commit
testsSha256: a0c51ff161945645f7635eb979c0096133bd8f306ba4481d25d21f864c27886f
---
## Business requirement
Nothing in the old system ever made a provider active. An analyst or supervisor now reviews a pending provider and its screening results and either approves the enrolment, which makes the provider active with a revalidation date five years ahead, or denies it, optionally with a reason. Approval is only offered after a passed screening, and if someone else has already decided, the analyst is told and shown the current status.

## Acceptance criteria
### AC-1 The approval page and how to reach it
Given Provider 1003000111 is Pending (P) with screen flag Pass (P) and has a passed screening
When analyst1 chooses 'Review' in the Pending approval queue, or opens the provider's detail page and chooses 'Approve or deny'
Then /console/providers/1003000111/approval opens and shows NPI, Name, Status 'Pending (P)', Screen flag 'Pass (P)' and the per-source screening results, from GET /api/v1/providers/1003000111 and GET /api/v1/screenings?npi=1003000111; on the detail page the 'Approve or deny' action is offered only to ANALYST and SUPERVISOR and only when the status is Pending (P) and the screen flag is Pass (P) or Fail (F)
Basis: modern/docs/ux/journeys.md#J1 (step 6), modern/docs/ux/journeys.md#J3 (step 4), D-019, QE-6, modern/docs/ux/wireframes/console-approval.html, modern/docs/ux/screen-inventory.md (console approval)

### AC-2 Approve makes the provider Active (journey J1)
Given The approval page of 1003000111 (Pending, Pass)
When analyst1 selects 'Approve (becomes Active)' and chooses 'Record decision'
Then The page sends POST /api/v1/providers/1003000111/approval with decision APPROVE, receives 200 with the provider in status A and a revalidation due date exactly five years after the approval date, and confirms 'Active (A)' with the due date returned by the service as MM/DD/YYYY (approval on 03/05/2026 shows 03/05/2031); the provider is no longer listed under Pending approval
Basis: D-019, D-003, D-004, modern/docs/ux/journeys.md#J1 (step 6), modern/contracts/openapi/provider-service.yaml#decideProvider, modern/docs/ux/wireframes/console-approval.html, R-PRVSCR01-ADD-03

### AC-3 Deny with a reason (journey J3)
Given The approval page of 1003000444 (Pending, screen flag Fail, failed on OIGLEIE with E203)
When analyst1 selects 'Deny (becomes Denied)', types the reason 'Owner is on the OIG exclusion list' (at most 200 characters, with a character counter) and chooses 'Record decision'
Then The page sends POST /api/v1/providers/1003000444/approval with decision DENY and the reason, receives 200 with status D and confirms 'Denied (D)'; the provider is no longer listed under Failed screening; a longer reason is not sent
Basis: modern/docs/ux/journeys.md#J3 (step 4), D-019, QE-6, modern/contracts/openapi/provider-service.yaml#ApprovalRequest (reason maxLength 200), modern/docs/ux/wireframes/console-approval.html

### AC-4 Approval needs a passed screening
Given A Pending provider whose screen flag is Fail (F), and another whose screen flag is Unscreened (U)
When analyst1 opens the approval page of each
Then For the Fail provider the Approve option is disabled with the text 'Approval needs a passed screening' and only Deny can be recorded; for the Unscreened provider neither decision is offered and the page says that screening has not finished; nothing is sent to the API in either case until a permitted decision is recorded
Basis: D-019, QE-6, modern/contracts/openapi/provider-service.yaml#decideProvider (a pending provider whose screening passed), R-PRVSCR01-ADD-03

### AC-5 Refusals from the service are shown and the page is refreshed
Given Another analyst has already approved 1003000111 while analyst1's approval page was open
When analyst1 records a decision
Then The API answers 409 and the page shows the problem detail, reloads the provider so that it reads 'Active (A)' and offers no further decision; an answer of 422 shows its detail at the decision form and keeps what was typed; a 404 shows 'No provider found'; a 5xx or network failure shows 'The decision could not be recorded. Try again.' and the page makes no claim that a decision was recorded
Basis: modern/contracts/openapi/provider-service.yaml#decideProvider (404, 409, 422), D-024, D-019

### AC-6 Only analysts and supervisors decide
Given analyst1, supervisor1, admin1 and provider1 are signed in
When Each opens /console/providers/1003000111/approval
Then analyst1 and supervisor1 see the decision form; admin1 and provider1 see the access-denied page; if the API answers 403 to a decision the page shows 'You are not allowed to record this decision'
Basis: D-023, modern/docs/ux/screen-inventory.md (roles ANALYST, SUPERVISOR), modern/docs/architecture/nfr.md (roles per endpoint), modern/contracts/openapi/provider-service.yaml#decideProvider (x-roles ANALYST, SUPERVISOR)

### AC-7 The approval page has zero axe violations
Given The Playwright test opens the page for a Pass provider, a Fail provider, an Unscreened provider and after a refusal
When It runs an axe scan on each state and records a decision with the keyboard only
Then Every scan reports zero violations; the decision is a labelled set of radio buttons and the Reason has a visible label; the status and screen flag are text plus code, never colour alone
Basis: .claude/skills/frontend-stack/SKILL.md section 5, modern/docs/architecture/nfr.md (Accessibility), modern/docs/ux/journeys.md#J1, modern/docs/ux/journeys.md#J3

## Out of scope
- Setting the status and the due date (provider-service, prv-approve); the page shows what the service returns
- The notice sent after approval or denial (notification-service) and its display (ui-portal-notices, ui-console-detail-2)
- Waiting for the risk tier before approval: the page shows no risk tier; the tier is saved by provider-service when it is assigned
- Making the reason mandatory (the contract makes it optional)
- Reversing a decision (a sanction is the only way to change an Active provider)

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Related rules: R-PRVSCR01-ADD-03
Programs: PRVSAN01.cbl:112 (the only statement in the estate that sets ACTIVE is the sanction reinstate, D-019); PRVSCR01.cbl:90-111 (SCREEN-ONE-PROVIDER sets only the screen flag, R-PRVSCR01-ADD-03); PRVENR01.cbl:146 (status PENDING). No job step approves a provider.
Decisions: D-019 FIX: an analyst approval step sets ACTIVE after a Pass screening; D-003 FIX: the revalidation due date is set at approval (approval date + 5 years); D-004 FIX: 4-digit years in all dates; D-023 FIX: only ANALYST and SUPERVISOR decide; D-024 FIX: refusals and failures are surfaced

## Technical notes (agent-owned)
Service: web-ui. Endpoints/events: GET /api/v1/providers/{npi}, GET /api/v1/screenings, POST /api/v1/providers/{npi}/approval. Roles: ANALYST, SUPERVISOR.
