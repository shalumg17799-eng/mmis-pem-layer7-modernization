---
key: MAM-70
localId: L-S057
epic: MAM-12
service: web-ui
type: Story
title: "Portal: see my notices"
slug: ui-portal-notices
specVersion: 1
descriptionSha256: a9e2b03c3e478c18ef0c2030778024dd7683a53794c13bb68693a776eb6b8f66
jiraUpdated: 2026-11-18T09:21:05.055-0600
rules: []
decisions: [D-012, D-023, D-024]
dependsOn: [MAM-62, MAM-60]
mainJourney: true
labels: [svc-web-ui, kag-L-S057, agentic, decision-D-012, decision-D-023, decision-D-024, slice-main-journey]
builtCommit: 2e20ead
testsSha256: e8d6c32b098c614074a4d239992a8455f4575d51f460e323950983a446441ab5
---
## Business requirement
A provider can see every notice the programme has sent to them, such as approval, denial, rejection, suspension, reinstatement, termination and revalidation notices, with the date, a plain name for the notice, how it was sent and whether it was delivered. A provider sees only their own notices.

## Acceptance criteria
### AC-1 The approval notice is listed (journey J1)
Given provider1 was approved and the notice with template APPROVED was sent on 03/05/2026 by email
When /portal/notices opens
Then The page calls GET /api/v1/notices (with no npi parameter) and shows a table with the columns Sent, Notice, Channel and Status holding the row 03/05/2026, 'Enrolment approved (APPROVED)', 'Email', 'Sent'
Basis: modern/docs/ux/journeys.md#J1 (step 7), modern/docs/ux/wireframes/portal-notices.html, modern/contracts/openapi/notification-service.yaml#Notice, D-012, QE-2

### AC-2 The rejection notice is listed (journey J2)
Given provider1's application was rejected and the notice with template REJECTED was sent
When /portal/notices opens
Then The table shows a row whose Notice reads 'Application rejected (REJECTED)'
Basis: modern/docs/ux/journeys.md#J2 (step 4), modern/contracts/openapi/notification-service.yaml#Notice, D-012, QE-2

### AC-3 Every other template has a plain name and its code
Given Notices with the templates DENIED, TRM, SUS, RST, GEN and REVALIDATION_DUE, and one with a template code the page does not know
When /portal/notices opens
Then The Notice column reads 'Enrolment denied (DENIED)', 'Termination notice (TRM)', 'Suspension notice (SUS)', 'Reinstatement notice (RST)', 'General notice (GEN)' and 'Revalidation due (REVALIDATION_DUE)', and for the unknown template just the code itself
Basis: QE-2, modern/contracts/openapi/notification-service.yaml#Notice (template enum), D-012, R-PRVSAN01-cbl-00045, R-PRVSAN01-cbl-00046, R-PRVSAN01-cbl-00047, R-PRVSAN01-cbl-00048, .claude/skills/frontend-stack/SKILL.md section 6, modern/docs/ux/journeys.md#J5 (notices SUS and RST)

### AC-4 Delivery state is written as text
Given One notice with status SENT and one with status FAILED
When /portal/notices opens
Then The Status column reads 'Sent' and 'Failed' as words, never by colour alone, so a notice that was not delivered is visible to the provider
Basis: modern/contracts/openapi/notification-service.yaml#Notice (status SENT, FAILED), D-024, .claude/skills/frontend-stack/SKILL.md section 5

### AC-5 Only my own notices are shown
Given Notices exist for NPI 1003000111 (provider1) and for NPI 1003000222
When provider1 opens /portal/notices
Then Only the notices of 1003000111 are shown and the page has no input or address parameter that could ask for another NPI
Basis: modern/docs/architecture/nfr.md (a PROVIDER only sees their own notices), modern/docs/ux/journeys.md#J4 (step 4), D-023, .claude/skills/frontend-stack/SKILL.md section 6, modern/contracts/openapi/notification-service.yaml#listNotices

### AC-6 Empty, long and failed lists are handled
Given provider1 with no notices, then with more than 20 notices, then an API failure
When /portal/notices opens each time
Then With none the page says 'No notices yet'; with more than 20 it shows paging controls (page 0, size 20 by default, total shown); on a 401 or 5xx or a network failure it shows 'The notices could not be loaded. Try again.' and never the text 'No notices yet'
Basis: modern/contracts/openapi/notification-service.yaml#listNotices (page, size), D-024

### AC-7 The notices page has zero axe violations
Given The Playwright test opens the page with notices, with none and with the load failure
When It runs an axe scan on each state
Then Every scan reports zero violations; the table has header cells; the page has one h1 and landmarks; paging controls work with the keyboard only
Basis: .claude/skills/frontend-stack/SKILL.md section 5, modern/docs/architecture/nfr.md (Accessibility), modern/docs/ux/wireframes/portal-notices.html

## Out of scope
- Reading the full text of a notice (the Notice contract carries no subject or body)
- Sending notices (notification-service, ntf-send, ntf-send-2) and viewing the email itself (Mailpit)
- Notices of other providers, which are never shown
- The wireframe row 'Application received', which has no template in the notice contract
- GEN for an unknown sanction action: D-026 rejects unknown actions, so no GEN notice is produced that way (ntf-send AC-4); the label exists only because GEN is in the template enum

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Related rules: R-PRVSAN01-cbl-00008, R-PRVSAN01-cbl-00045, R-PRVSAN01-cbl-00046, R-PRVSAN01-cbl-00047, R-PRVSAN01-cbl-00048
Programs: PRVSAN01.cbl:124-138 (GENERATE-NOTICE: dynamic CALL of PRVNTTRM, PRVNTSUS, PRVNTRST or PRVNTGEN at :134; the programs are absent from the estate, AP-05, D-012). No job step.
Decisions: D-012 REPLACE: notification-service sends the TRM, SUS, RST and GEN notices and the new APPROVED, DENIED, REJECTED and REVALIDATION_DUE notices; D-023 FIX: a provider sees only their own notices; D-024 FIX: failures are surfaced

## Technical notes (agent-owned)
Service: web-ui. Endpoints/events: GET /api/v1/notices. Roles: PROVIDER.
