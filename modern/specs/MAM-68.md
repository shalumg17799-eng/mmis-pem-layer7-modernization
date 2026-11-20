---
key: MAM-68
localId: L-S056
epic: MAM-12
service: web-ui
type: Story
title: "Portal: see my provider record"
slug: ui-portal-record
specVersion: 1
descriptionSha256: 2b8c263dd617ca3c3b53e815c83762f28b8cc54c7e57ab161928f63e7bc925ea
jiraUpdated: 2026-11-18T09:20:49.200-0600
rules: []
decisions: [D-006, D-007, D-002, D-003, D-004, D-023]
dependsOn: [MAM-62, MAM-37]
mainJourney: true
labels: [svc-web-ui, kag-L-S056, agentic, decision-D-006, decision-D-007, decision-D-002, decision-D-003, decision-D-004, decision-D-023, slice-main-journey]
builtCommit:
testsSha256:
---
## Business requirement
A provider can see their own provider record: their identifiers, type, specialty, enrolment status (including pending, active, terminated, denied and suspended), screening result, risk level, effective date and next revalidation date, plus either their personal details and licence or their organisation's details. Every code is shown with its meaning, dates are shown with four-digit years, and a provider can never see someone else's record.

## Acceptance criteria
### AC-1 The individual record is shown in full (journey J1)
Given provider1's provider is Active, screen flag Pass, risk tier Limited, effective 2026-03-02, revalidation due 2031-03-02, last name RIVERA, first name MARIA, licence MD12345, state TX
When /portal/provider opens
Then The page calls GET /api/v1/providers/1003000111 and shows, as a table with header cells: NPI 1003000111; Medicaid ID TX0000111; Provider type Individual (IN); Taxonomy 207Q00000X; Enrolment status Active (A); Screen flag Pass (P); Risk tier Limited (L); Effective date 03/02/2026; Revalidation due 03/02/2031; Last name RIVERA; First name MARIA; Licence number MD12345; Licence state TX
Basis: modern/docs/ux/wireframes/portal-provider-record.html, modern/docs/ux/journeys.md#J1 (step 7), .claude/skills/frontend-stack/SKILL.md section 6, ADR-009, D-002, D-003, D-004

### AC-2 A group or facility shows its organisation details
Given A PROVIDER user whose provider is a group (GR) or facility (FA) with a legal name, a DBA name and an owner NPI
When /portal/provider opens
Then Provider type reads 'Group (GR)' or 'Facility (FA)' and the table shows Legal name, DBA name and Owner NPI (10 digits, in full) instead of last name, first name, licence number and licence state
Basis: modern/docs/ux/screen-inventory.md (CICS field table: organisation view), D-007, modern/contracts/openapi/provider-service.yaml#Organization, D-008

### AC-3 Every status, screen flag and risk tier has its own wording
Given Providers with enrolment status P, A, T, D and S, screen flag U, P and F, risk tier L, M, H and no tier yet, and no revalidation date yet
When Their records are shown on /portal/provider
Then The status reads Pending (P), Active (A), Terminated (T), Denied (D) and Suspended (S), so Suspended is never shown as Denied; the screen flag reads Unscreened (U), Pass (P) or Fail (F); the tier reads Limited (L), Moderate (M) or High (H), or 'Not assigned' when the API gives none; a missing revalidation date reads 'Not set'
Basis: D-006, R-PRVCICS1-ADD-06, .claude/skills/frontend-stack/SKILL.md section 6, modern/docs/ux/screen-inventory.md (CICS field table), modern/contracts/openapi/provider-service.yaml#Provider (riskTier and revalidationDueDate nullable)

### AC-4 Only my own record is ever requested (journey J4)
Given provider1 is signed in with the claim npi = 1003000111
When /portal/provider opens, and, in a second check, the API answers 403
Then The only provider request the page makes is GET /api/v1/providers/1003000111, the page has no NPI input and no NPI in its address, and after a 403 it shows an access-denied message with no provider data
Basis: modern/docs/ux/journeys.md#J4 (step 4), modern/docs/architecture/nfr.md (a PROVIDER only sees their own data), D-023, modern/infra/keycloak/realm-pem.json (npi claim), .claude/skills/frontend-stack/SKILL.md section 6

### AC-5 No record yet is explained
Given provider1 has only a rejected application, or an accepted one that has not been registered yet
When /portal/provider opens and the API answers 404
Then The page says that there is no provider record yet, that a record is created when an application is accepted, and links to My enrolment and to Apply for enrolment; it shows no table and no error code
Basis: modern/contracts/openapi/provider-service.yaml#getProvider (404), D-001, modern/docs/ux/journeys.md#J2 (step 2: nothing is registered)

### AC-6 Dates keep four-digit years
Given A provider whose revalidation due date is 2070-03-02 and one whose effective date is 2026-03-02
When Their records are shown
Then The dates read 03/02/2070 and 03/02/2026; no date is shown with a two-digit year and none is shifted by a century
Basis: D-004, ADR-009

### AC-7 The record page has zero axe violations
Given The Playwright test opens an individual record, an organisation record and the no-record page
When It runs an axe scan on each and reads the page with the keyboard only
Then Every scan reports zero violations; the page has one h1 and landmarks; the record table has header cells; status is text plus code, never colour alone
Basis: .claude/skills/frontend-stack/SKILL.md section 5, modern/docs/architecture/nfr.md (Accessibility), modern/docs/ux/wireframes/portal-provider-record.html

## Out of scope
- Editing the record (no function exists; changes go through applications, approval and sanctions)
- Another provider's record, which is never shown
- Screening results per source, sanctions and audit history (console only)
- Notices (ui-portal-notices)

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Related rules: R-PRVCICS1-ADD-06
Programs: PRVCICS1.cbl:63-73 (FORMAT-PROVIDER status labels A, T, P, otherwise DENIED); COPYBOOKS/PRVRECRD.cpy:9-44 (record fields, 130 bytes, D-007); PRVRSK01.cbl:126-137 (SET-REVALIDATION-DATE, two-digit year pivot at :131, D-004). No job step.
Decisions: D-006 A new SUSPENDED status with its own label; D-007 The record is 130 bytes, so the owner NPI is kept and shown; D-002 FIX: the risk tier is saved and therefore shown; D-003 FIX: due date set at approval and at each completed revalidation, shown here; D-004 FIX: 4-digit years everywhere; D-023 FIX: a provider sees only their own record

## Technical notes (agent-owned)
Service: web-ui. Endpoints/events: GET /api/v1/providers/{npi}. Roles: PROVIDER.
