---
key: MAM-66
localId: L-S055
epic: MAM-12
service: web-ui
type: Story
title: "Portal: see my application status"
slug: ui-portal-status
specVersion: 1
descriptionSha256: bd2f5ff7774e8cbdf3d16c18a35d907dce425411844f3731899e223d2fa5fff7
jiraUpdated: 2026-11-18T09:20:32.692-0600
rules: []
decisions: [D-001, D-016, D-023, D-024, D-004]
dependsOn: [MAM-64, MAM-54]
mainJourney: true
labels: [svc-web-ui, kag-L-S055, agentic, decision-D-001, decision-D-016, decision-D-023, decision-D-024, decision-D-004, slice-main-journey]
builtCommit:
testsSha256:
---
## Business requirement
A provider can open any of their applications to see what happened to it: when it was sent, whether it was accepted or rejected and, for a rejected one, every reason it was rejected, each with its error code. A provider never sees another provider's application.

## Acceptance criteria
### AC-1 A validated application shows its status and details (journey J1)
Given provider1's application for NPI 1003000111 was accepted (status VALIDATED)
When /portal/applications/:applicationId opens
Then The page calls GET /api/v1/applications/{applicationId} and shows the application id, Status 'Validated', the submitted date as MM/DD/YYYY, NPI 1003000111 in full, Medicaid ID, Provider type 'Individual (IN)', Taxonomy 207Q00000X, the individual details and the text 'No validation errors'
Basis: modern/docs/ux/journeys.md#J1 (step 3), modern/docs/ux/wireframes/portal-application-status.html, modern/contracts/openapi/enrollment-service.yaml#getApplication, ADR-009, D-004, .claude/skills/frontend-stack/SKILL.md section 6

### AC-2 A rejected application shows exactly one error row (journey J2)
Given provider1's application was rejected because the taxonomy was empty
When The status page of that application opens
Then Status shows 'Rejected' and a table 'Validation errors' with the columns Code, Field and Message holds exactly one row: E104, taxonomy and the message returned by the API (for example 'Taxonomy code is required')
Basis: modern/docs/ux/journeys.md#J2 (step 3), modern/docs/ux/wireframes/portal-application-status.html, D-001, D-016, R-PRVENR01-cbl-00005, R-PRVENR01-cbl-00045

### AC-3 Several failed checks give several rows, each once
Given A second application of provider1 was rejected for provider type XX and an empty taxonomy
When Its status page opens
Then The table lists exactly two rows, E102 on providerType and E104 on taxonomy, each once, in the order returned by the API; no error is repeated and no error is missing
Basis: D-001, D-016, R-PRVENR01-cbl-00004, R-PRVENR01-cbl-00044, modern/contracts/openapi/enrollment-service.yaml#Application (exactly one error per failed check)

### AC-4 Someone else's or an unknown application is never shown
Given provider1 is signed in; another user's application has a known id; no application has the id 00000000-0000-0000-0000-000000000000
When provider1 opens /portal/applications/ with the other user's id, and then with the unknown id
Then For the other user's id the API answers 403 and the page shows an access-denied message with none of that application's data (no NPI, no errors); for the unknown id the API answers 404 and the page shows 'Application not found' with a link to My enrolment
Basis: modern/docs/ux/journeys.md#J4 (step 4), modern/docs/architecture/nfr.md (a PROVIDER only sees their own data), .claude/skills/frontend-stack/SKILL.md section 6, D-023, modern/contracts/openapi/enrollment-service.yaml#getApplication (403, 404)

### AC-5 Loading failures are shown, not an empty page
Given provider1 opens a status page
When The API answers 401 or 5xx or the network call fails
Then The page shows a message that matches the failure (sign in again, or 'The application could not be loaded. Try again.') and no status, so a failed load is never mistaken for 'Validated' or 'Rejected'
Basis: D-024, modern/contracts/openapi/enrollment-service.yaml#getApplication (401)

### AC-6 The status page has zero axe violations
Given The Playwright test opens a validated application and a rejected application with two errors
When It runs an axe scan on each page
Then Both scans report zero violations; the page has one h1 and the landmarks header, nav and main; the error table has header cells; the status is text, not colour alone; the page can be used with the keyboard only
Basis: .claude/skills/frontend-stack/SKILL.md section 5, modern/docs/architecture/nfr.md (Accessibility), modern/docs/ux/wireframes/portal-application-status.html

## Out of scope
- Reading the status of other users' applications (console work queues show rejected applications to analysts)
- Notices about the application (ui-portal-notices)
- The provider's enrolment status after registration or approval (ui-portal-record)
- Withdrawing or editing an application (no such function exists)

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Related rules: R-PRVENR01-cbl-00003, R-PRVENR01-cbl-00004, R-PRVENR01-cbl-00005, R-PRVENR01-cbl-00006, R-PRVENR01-cbl-00043, R-PRVENR01-cbl-00044, R-PRVENR01-cbl-00045
Programs: PRVENR01.cbl:127-148 (VALIDATE-APPLICATION), 155 (INVALID-APP), 173-179 (WRITE-ERROR-RECORD: one APPERROR row per rejected application after D-001); job PRVDAY01.jcl STEP010 (PRVENR01, APPERROR file).
Decisions: D-001 FIX: exactly one error per failed check; a rejected application is kept for review and never registered; D-016 FIX: error codes E101-E205 shown as stable identifiers; D-023 FIX: a provider sees only their own applications; D-024 FIX: failures are surfaced; D-004 FIX: dates shown with 4-digit years as MM/DD/YYYY

## Technical notes (agent-owned)
Service: web-ui. Endpoints/events: GET /api/v1/applications/{applicationId}. Roles: PROVIDER.
