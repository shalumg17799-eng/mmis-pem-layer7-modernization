---
key: MAM-80
localId: L-S060
epic: MAM-13
service: web-ui
type: Story
title: "Console: provider detail with every record field and the status shown as text and code"
slug: ui-console-detail
specVersion: 1
descriptionSha256: 21b348257531b93110f2016ef390b1e4b6bb5ebf69c5a86940c3cfccb933bf52
jiraUpdated: 2026-11-18T09:22:08.374-0600
rules: [R-PRVCICS1-ADD-01, R-PRVCICS1-ADD-06, R-PRVCICS1-cbl-00005, R-PRVCICS1-cbl-00006, R-PRVCICS1-cbl-00007, R-PRVCICS1-cbl-00008, R-PRVCICS1-cbl-00009, R-PRVCICS1-cbl-00010, R-PRVCICS1-cbl-00026, R-PRVCICS1-cbl-00027, R-PRVCICS1-cbl-00028, R-PRVCICS1-cbl-00029, R-PRVCICS1-cbl-00030, R-PRVCICS1-cbl-00031, R-PRVCICS1-cbl-00032, R-PRVCICS1-cbl-00033, R-PRVCICS1-cbl-00034, R-PRVCICS1-cbl-00035, R-PRVCICS1-cbl-00036, R-PRVCICS1-cbl-00037, R-PRVCICS1-cbl-00038, R-PRVCICS1-cbl-00039, R-PRVCICS1-cbl-00040, R-PRVCICS1-cbl-00041]
decisions: [D-022, D-006, D-007, D-023, D-024]
dependsOn: [MAM-77]
mainJourney: false
labels: [svc-web-ui, kag-L-S060, agentic, decision-D-022, decision-D-006, decision-D-007, decision-D-023, decision-D-024]
builtCommit:
testsSha256:
---
## Business requirement
When an analyst opens a provider, the page shows everything the provider record holds, each field with its meaning, and states the enrolment status in words and as the stored code. The old inquiry screen looked the provider up but never actually showed the status message; the new page always does. Active, terminated, pending, denied and suspended each have their own wording, an unrecognised status is not hidden, and an unknown provider is reported plainly.

## Acceptance criteria
### AC-1 An individual record is shown in full, with meanings (journey J4)
Given Provider 1003000111: Medicaid ID TX0000111, type IN, taxonomy 207Q00000X, risk tier L, status A, screen flag P, effective 2026-03-02, revalidation due 2031-03-02, last name RIVERA, first name MARIA, licence MD12345, state TX
When analyst1 opens /console/providers/1003000111 from the search results
Then The page calls GET /api/v1/providers/1003000111 and shows the NPI in full in the heading and a table 'Provider record' with NPI 1003000111; Medicaid ID TX0000111; Provider type Individual (IN); Taxonomy 207Q00000X; Enrolment status Active (A); Screen flag Pass (P); Risk tier Limited (L); Effective date 03/02/2026; Revalidation due 03/02/2031; Last name RIVERA; First name MARIA; Licence number MD12345; Licence state TX, so that the provider data and the status message are shown together
Basis: R-PRVCICS1-cbl-00010, R-PRVCICS1-cbl-00041, modern/docs/ux/journeys.md#J4 (step 2), modern/docs/ux/wireframes/console-provider-detail.html, modern/docs/ux/screen-inventory.md (CICS field table), .claude/skills/frontend-stack/SKILL.md section 6, D-022, ADR-009

### AC-2 A group or facility shows its organisation fields
Given Provider 1003000444 is a group (GR) with legal name EXCLUDED CLINIC LLC and owner NPI 1990000111
When analyst1 opens /console/providers/1003000444
Then Provider type reads 'Group (GR)' and the table shows Legal name, DBA name and Owner NPI 1990000111 in full; last name, first name, licence number and licence state are not shown
Basis: modern/docs/ux/screen-inventory.md (CICS field table: organisation view), D-007, aws-transform/test-data/providers.txt (1003000444, owner 1990000111), modern/contracts/openapi/provider-service.yaml#Organization

### AC-3 The status banner states the status in words and code
Given Providers whose enrolment status is A, T, P, D and S
When analyst1 opens the detail page of each
Then A banner at the top reads exactly 'Status: Active (A)', 'Status: Terminated (T)', 'Status: Pending (P)', 'Status: Denied (D)' or 'Status: Suspended (S)'; exactly one label is shown for a provider; the same text is the value of the Enrolment status row; the banner is text that a screen reader reads, and colour or an icon may add to it but never replaces it
Basis: R-PRVCICS1-ADD-01, R-PRVCICS1-cbl-00005, R-PRVCICS1-cbl-00006, R-PRVCICS1-cbl-00007, R-PRVCICS1-cbl-00008, R-PRVCICS1-cbl-00026, R-PRVCICS1-cbl-00027, R-PRVCICS1-cbl-00028, R-PRVCICS1-cbl-00030, R-PRVCICS1-cbl-00031, R-PRVCICS1-cbl-00032, R-PRVCICS1-cbl-00033, R-PRVCICS1-cbl-00034, R-PRVCICS1-cbl-00037, R-PRVCICS1-cbl-00038, R-PRVCICS1-cbl-00039, D-022, modern/docs/ux/wireframes/console-provider-detail.html (Status: Active (A), text and code, never colour alone)

### AC-4 Denied is shown for a denied provider, with the code
Given A provider whose status is D, for example 1003000444 after the analyst denied it
When analyst1 opens its detail page
Then The banner reads 'Status: Denied (D)' and the Enrolment status row reads 'Denied (D)'
Basis: R-PRVCICS1-cbl-00009, R-PRVCICS1-cbl-00035, R-PRVCICS1-cbl-00040, modern/docs/ux/journeys.md#J3 (step 4)

### AC-5 Suspended has its own wording (journey J5)
Given Provider 1003000111 was suspended by a sanction (status S)
When analyst1 opens its detail page
Then The banner and the Enrolment status row read 'Suspended (S)', not 'Denied (D)'
Basis: R-PRVCICS1-ADD-06, D-006, modern/docs/ux/journeys.md#J5 (step 2), modern/docs/ux/screen-inventory.md (PRV-ENROLL-STATUS: Suspended (S))

### AC-6 An unrecognised status keeps the legacy catch-all label and shows the stored code
Given A record whose status value is not P, A, T, D or S (a blank value, or X, served by a test double of the API, since the 1.0.0 contract allows only P, A, T, D and S)
When analyst1 opens its detail page
Then The banner reads 'Status: Denied (X)' for X and 'Status: Denied (blank)' for an empty value, so the legacy catch-all label is kept and the real stored value is still visible to the analyst
Basis: R-PRVCICS1-cbl-00009, R-PRVCICS1-cbl-00029, R-PRVCICS1-cbl-00035, QE-3

### AC-7 Unknown providers and failures are told apart
Given NPI 1003000999 has no provider record; and separate API failures 401, 403 and 5xx
When analyst1 opens /console/providers/1003000999, and then the page fails to load with each failure
Then For the unknown NPI the API answers 404 and the page shows 'No provider found' with a link 'Back to provider search' and no table; a 401 sends the analyst to sign in, a 403 shows 'You are not allowed to see this provider', and a 5xx or network failure shows 'The provider could not be loaded. Try again.'; none of these is shown as a status
Basis: R-PRVCICS1-cbl-00036, R-PRVCICS1-ADD-04, modern/docs/ux/journeys.md#J4 (step 3), D-022, D-023, D-024, modern/contracts/openapi/provider-service.yaml#getProvider (401, 403, 404)

### AC-8 The detail page has zero axe violations
Given The Playwright test opens an individual record, an organisation record, one record per status and the not-found state
When It runs an axe scan on each and reads the page with the keyboard only
Then Every scan reports zero violations; the page has one h1 and landmarks; the record table has header cells; the status banner is never colour alone; the focus order follows the visual order
Basis: .claude/skills/frontend-stack/SKILL.md section 5, modern/docs/architecture/nfr.md (Accessibility), modern/docs/ux/journeys.md#J4

## Out of scope
- Screening, sanctions, revalidation, notices and audit panels (ui-console-detail-2)
- The Approve or deny and Record sanction actions (ui-console-approval, ui-console-sanction)
- Editing provider data (no such function)
- The VSAM READ ERROR text of the legacy program, replaced by the generic failure message of AC-7

## Legacy trace (agent-owned, do not edit)
Rules: R-PRVCICS1-ADD-01, R-PRVCICS1-ADD-06, R-PRVCICS1-cbl-00005, R-PRVCICS1-cbl-00006, R-PRVCICS1-cbl-00007, R-PRVCICS1-cbl-00008, R-PRVCICS1-cbl-00009, R-PRVCICS1-cbl-00010, R-PRVCICS1-cbl-00026, R-PRVCICS1-cbl-00027, R-PRVCICS1-cbl-00028, R-PRVCICS1-cbl-00029, R-PRVCICS1-cbl-00030, R-PRVCICS1-cbl-00031, R-PRVCICS1-cbl-00032, R-PRVCICS1-cbl-00033, R-PRVCICS1-cbl-00034, R-PRVCICS1-cbl-00035, R-PRVCICS1-cbl-00036, R-PRVCICS1-cbl-00037, R-PRVCICS1-cbl-00038, R-PRVCICS1-cbl-00039, R-PRVCICS1-cbl-00040, R-PRVCICS1-cbl-00041
Related rules: R-PRVCICS1-ADD-04, R-PRVCICS1-ADD-05, R-PRVCICS1-cbl-00023
Programs: PRVCICS1.cbl:53-56 (SEND MAP FROM WS-PROVIDER-AREA only: WS-MSG never sent), 63-73 (FORMAT-PROVIDER: EVALUATE active, terminated, pending, otherwise DENIED; labels at :65-72), 47-52 (NOTFND and other RESP messages). CICS transaction PINQ; no job step.
Decisions: D-022 REPLACE (accepted, Q-010): analyst-console inquiry that shows the status; D-006 A new SUSPENDED status with its own label; D-007 The record is 130 bytes: owner NPI is part of the record shown; D-023 FIX (accepted, Q-010): role-based access on every screen; D-024 FIX (accepted, Q-010): failures surfaced

## Technical notes (agent-owned)
Service: web-ui. Endpoints/events: GET /api/v1/providers/{npi}. Roles: ANALYST, SUPERVISOR, ADMIN.
