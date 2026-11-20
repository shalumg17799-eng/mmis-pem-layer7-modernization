---
key: MAM-64
localId: L-S054
epic: MAM-12
service: web-ui
type: Story
title: "Portal: apply for enrolment online, with field errors E101 to E104"
slug: ui-portal-apply
specVersion: 1
descriptionSha256: 8d1c83b3a5ad7dfbfd2c9f4921a7864fa11d1de9050a00bb5e6d88a32b651937
jiraUpdated: 2026-11-18T09:20:17.474-0600
rules: []
decisions: [D-001, D-016, D-008, D-024]
dependsOn: [MAM-62, MAM-46]
mainJourney: true
labels: [svc-web-ui, kag-L-S054, agentic, decision-D-001, decision-D-016, decision-D-008, decision-D-024, slice-main-journey]
builtCommit: pending-user-commit
testsSha256: d2eff3026bb22b60fee69ed60bee1b98cfd74d1f2ffb003ab590b2483fb4c3db
---
## Business requirement
A provider applies for Medicaid enrolment by filling in one online form: the provider's identification number, the state-issued Medicaid ID, the provider type (individual, group or facility), the specialty (taxonomy) code, and either the person's name and licence or the organisation's legal name, trade name and owner. When something is missing or wrong, the form says exactly what and where, once per problem and with the same error codes as the old system, and nothing is registered. A good application is accepted and the provider can follow its progress.

## Acceptance criteria
### AC-1 The form shows every field with a visible label
Given provider1 is signed in
When /portal/apply opens
Then The form has labelled inputs NPI (10 digits), Medicaid ID (at most 9 characters), Provider type (a list that starts with 'Choose...' and offers exactly Individual (IN), Group (GR) and Facility (FA)), Taxonomy code (10 characters) and, for Individual, Last name (30), First name (20), Licence number (15) and Licence state (2); choosing Group or Facility replaces the individual inputs by Legal name, DBA name and Owner NPI; a 'Submit application' button ends the form
Basis: modern/docs/ux/wireframes/portal-apply.html, modern/contracts/openapi/enrollment-service.yaml#ApplicationRequest, modern/contracts/openapi/enrollment-service.yaml#Individual, modern/contracts/openapi/enrollment-service.yaml#Organization, D-008, .claude/skills/frontend-stack/SKILL.md section 5, modern/docs/ux/screen-inventory.md (portal apply)

### AC-2 A valid application is accepted (journey J1)
Given provider1 is signed in and no application exists for NPI 1003000111
When They enter NPI 1003000111, Medicaid ID TX0000111, type Individual (IN), taxonomy 207Q00000X, last name RIVERA, first name MARIA, licence number MD12345 and licence state TX and choose Submit application
Then The page sends POST /api/v1/applications with providerType IN and the individual details, receives 201 with status VALIDATED and opens /portal/applications/:applicationId, which shows 'Validated'
Basis: modern/docs/ux/journeys.md#J1 (steps 2 and 3), modern/contracts/openapi/enrollment-service.yaml#submitApplication, aws-transform/test-data/providers.txt (1003000111 RIVERA MARIA MD, MD12345, TX), D-001, modern/docs/ux/wireframes/portal-home.html (Medicaid ID TX0000111), QE-11

### AC-3 An empty taxonomy gives one error E104 beside the field and in the summary (journey J2)
Given provider1 fills the form for NPI 1003000111 with every field except the taxonomy code, which stays empty
When They choose Submit application
Then The request is sent with the empty taxonomy (the browser does not block the submit for a missing NPI, type or taxonomy, so that the service stores the rejection, journey J2 steps 3 and 4); the API answers 422 with exactly one error, E104 on taxonomy; the page stays on /portal/apply, shows an error summary at the top ('1 error in the application') with a link 'E104: ...' that moves the focus to the Taxonomy code field, shows the same code and message next to that field, keeps everything that was typed, and shows no success page
Basis: modern/docs/ux/journeys.md#J2 (steps 1 and 2), D-001, D-016, R-PRVENR01-cbl-00005, .claude/skills/frontend-stack/SKILL.md section 5, modern/docs/ux/wireframes/portal-apply.html, modern/contracts/openapi/enrollment-service.yaml#FieldError

### AC-4 E101, E102 and E103 appear at their own field, once per failed check
Given provider1 submits, in turn, (a) a blank NPI, (b) no provider type chosen, (c) NPI 1003000111 for which an application is already VALIDATED, and (d) no provider type and an empty taxonomy together
When The API answers 422 for each submission
Then (a) E101 is shown next to NPI, (b) E102 next to Provider type, (c) E103 next to NPI, and (d) two errors, E102 next to Provider type and E104 next to Taxonomy code, each listed once in the summary and once at its field, with the summary heading '2 errors in the application'; every code is shown in full (E101, not E1) followed by the message returned by the API
Basis: D-001, D-016, R-PRVENR01-cbl-00003, R-PRVENR01-cbl-00004, R-PRVENR01-ADD-04, R-PRVENR01-ADD-05, modern/docs/ux/wireframes/portal-apply.html, modern/contracts/openapi/enrollment-service.yaml#submitApplication

### AC-5 A rejected application can be found afterwards and nothing is registered
Given The submission of AC-3 stored an application with status REJECTED
When provider1 chooses the link 'My applications' that the page offers after the errors, or opens /portal, and then opens My provider record
Then The portal home lists that application with status 'Rejected' and a View link to /portal/applications/:applicationId; opening it shows the rejected application (ui-portal-status); My provider record (GET /api/v1/providers/1003000111 answers 404) says that no record exists yet
Basis: QE-1, modern/docs/ux/journeys.md#J2 (steps 2 and 3), modern/docs/ux/screen-inventory.md (portal home calls GET /api/v1/applications), D-001

### AC-6 Other failures are shown, never swallowed
Given provider1 has filled a complete form
When The API answers 400, 401, 403 or 5xx, or the network call fails
Then The page shows an error summary 'The application could not be submitted' with a reason that matches the failure (401: sign in again; 403: you are not allowed to apply; 400 and 5xx: try again, with the detail of a 400), keeps what was typed, shows no success page and no partial status, and lets the user submit again
Basis: D-024, modern/contracts/openapi/enrollment-service.yaml#submitApplication (400, 401, 403 responses), .claude/skills/frontend-stack/SKILL.md section 5

### AC-7 The form has zero axe violations in every state
Given The Playwright test opens the empty form, the form after the one-error rejection of AC-3 and the form after the two-error rejection of AC-4
When It runs an axe scan in each state and completes and submits the form with the keyboard only
Then Every scan reports zero violations; every input has a visible label; after a failed submit the focus moves to the error summary; each error is written as code plus text next to its field, never by colour alone; the whole form can be completed and sent with Tab, Space and Enter
Basis: .claude/skills/frontend-stack/SKILL.md section 5, modern/docs/architecture/nfr.md (Accessibility), modern/docs/ux/journeys.md#J2 (step 2)

## Out of scope
- Validating the application (enrollment-service, enr-submit); the page only shows the answer
- Checks on names, licence or owner at apply time (the legacy checks only NPI, type and taxonomy; screening checks the rest)
- The application status page (ui-portal-status) and the portal home table (ui-shell-login)
- Changing or withdrawing an application after it is sent (no such function exists)
- Loading applications from the legacy APPINPUT file (enr-intake-run, console batch page)
- The 'Application rejected' notice (ntf-send-2; shown by ui-portal-notices)

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Related rules: R-PRVENR01-ADD-04, R-PRVENR01-ADD-05, R-PRVENR01-cbl-00003, R-PRVENR01-cbl-00004, R-PRVENR01-cbl-00005
Programs: PRVENR01.cbl:127-148 (VALIDATE-APPLICATION: NPI, type and taxonomy checks), 155 (INVALID-APP, no exit: fall-through, D-001), 173-179 (WRITE-ERROR-RECORD); job PRVDAY01.jcl STEP010 (PRVENR01).
Decisions: D-001 FIX: every application is processed and each invalid one is rejected with exactly one error per failed check; a rejected application is never registered; D-016 FIX: error codes E101-E205 are real validation and screening results; D-008 PRESERVE: provider types IN, GR, FA (feed codes are mapped by the service); D-024 FIX: errors are handled explicitly and surfaced

## Technical notes (agent-owned)
Service: web-ui. Endpoints/events: POST /api/v1/applications. Roles: PROVIDER.
