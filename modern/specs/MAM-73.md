---
key: MAM-73
localId: L-S058
epic: MAM-12
service: web-ui
type: Story
title: "Public directory page (no sign-in)"
slug: ui-public-directory
specVersion: 1
descriptionSha256: ba353c62f1a67ee37619020673e1fe21eb40ac74c6473deba3c29bab362d8a51
jiraUpdated: 2026-11-18T09:21:21.414-0600
rules: []
decisions: [D-028, D-006, D-008, D-024]
dependsOn: [MAM-63, MAM-34]
mainJourney: true
labels: [svc-web-ui, kag-L-S058, agentic, decision-D-028, decision-D-006, decision-D-008, decision-D-024, slice-main-journey]
builtCommit: 2e20ead
testsSha256: 50c8f4f6396827729d5ca39ed9ebcfa90c58d9cf1c82c1913ef1f58594fd6599
---
## Business requirement
Anyone, without signing in, can look up Medicaid providers who are currently enrolled and active. A visitor searches by name and optionally by provider type and sees each provider's name, identification number, type and specialty. Providers who are pending, denied, terminated or suspended never appear, and names are shown as proper display names.

## Acceptance criteria
### AC-1 The page opens without signing in
Given A visitor who is not signed in
When They open /directory
Then The page loads without any redirect to Keycloak, shows the h1 'Find a Medicaid provider', an input 'Name', a list 'Provider type' (Any, Individual (IN), Group (GR), Facility (FA)) and a Search button, and none of its requests carries an Authorization header
Basis: modern/docs/ux/screen-inventory.md (public directory, PUBLIC), modern/docs/ux/wireframes/public-directory.html, ADR-006, ADR-007, modern/contracts/openapi/directory-service.yaml#searchDirectory (x-roles PUBLIC)

### AC-2 Search by name (journey J7)
Given Active providers MARIA RIVERA (NPI 1003000111, Individual, taxonomy 207Q00000X) and SUNSET HOSPICE INC (NPI 1003000333, Facility, taxonomy 251G00000X)
When A visitor types 'HOSPICE' and chooses Search, and then types 'RIVERA' and chooses Search
Then The page calls GET /api/v1/directory/providers?name=HOSPICE and the table 'Active providers' (columns Name, NPI, Type, Specialty (taxonomy)) shows the row SUNSET HOSPICE INC | 1003000333 | Facility (FA) | 251G00000X; the second search calls GET /api/v1/directory/providers?name=RIVERA and shows MARIA RIVERA | 1003000111 | Individual (IN) | 207Q00000X
Basis: modern/docs/ux/journeys.md#J7 (step 3), modern/docs/ux/wireframes/public-directory.html, modern/contracts/openapi/directory-service.yaml#DirectoryEntry, D-028, Q-008

### AC-3 Filter by provider type
Given Active providers of all three types
When A visitor chooses Facility (FA) and searches, and then chooses Any and searches
Then The first call carries providerType=FA and lists only facilities; the second call carries no providerType parameter and lists all types
Basis: modern/contracts/openapi/directory-service.yaml#searchDirectory (providerType), D-008, modern/docs/ux/wireframes/public-directory.html

### AC-4 Names are composed, not raw bytes
Given An individual provider with first name MARIA, last name RIVERA and licence number MD12345, and a facility with legal name SUNSET HOSPICE INC
When They appear in the results
Then The Name column shows 'MARIA RIVERA' and 'SUNSET HOSPICE INC' exactly as returned in displayName; the licence number or other bytes of the record never appear in a name, and the page does not cut a name in the middle of a word
Basis: D-028, R-PRVDIR01-ADD-02, modern/contracts/openapi/directory-service.yaml#DirectoryEntry (displayName composed from the name fields)

### AC-5 A suspended provider does not appear (journey J5)
Given An Active provider was suspended (status S) by a supervisor, and a Pending, a Denied and a Terminated provider exist
When A visitor searches for the suspended provider's name and for the others
Then None of them is listed; the page only shows what the API returns for active providers and never adds or fills in a provider itself
Basis: modern/docs/ux/journeys.md#J5 (step 3), R-PRVDIR01-ADD-01, R-PRVDIR01-cbl-00018, D-006, Q-008

### AC-6 Only public columns are shown
Given The API also returns Medicaid ID and risk tier for each entry
When The results are shown
Then The table has exactly the columns Name, NPI (10 digits in full), Type and Specialty (taxonomy); Medicaid ID and risk tier are not displayed, and no address, phone or network column exists
Basis: modern/docs/ux/wireframes/public-directory.html, ADR-006, Q-008, R-PRVDIR01-ADD-04

### AC-7 Empty, long, refused and failed searches are handled
Given A search with no match, a search with more than 20 matches, a request the API refuses with 400, and an API failure
When The page shows each outcome
Then No match shows 'No providers found'; many matches show the total and paging controls (page 0, size 20 by default); a 400 shows the problem detail next to the search form; a 5xx or network failure shows 'The directory is not available right now. Try again.' and never 'No providers found'
Basis: modern/contracts/openapi/directory-service.yaml#searchDirectory (page, size, 400), D-024

### AC-8 The directory page has zero axe violations
Given The Playwright test opens the empty page, a results page, the no-match state and the failure state with no user signed in
When It runs an axe scan on each state and uses the page with the keyboard only
Then Every scan reports zero violations; the page has one h1 and the landmarks header and main with a 'Skip to main content' link; inputs have visible labels; the table has header cells
Basis: .claude/skills/frontend-stack/SKILL.md section 5, modern/docs/architecture/nfr.md (Accessibility), modern/docs/ux/wireframes/public-directory.html

## Out of scope
- The FHIR Practitioner, Organization and PractitionerRole API (dir-fhir); the page does not browse FHIR
- A taxonomy (specialty) search box, which the wireframe does not show
- Addresses, phones and networks (Q-008: not held by the legacy master)
- The nightly DIROUT file (ui-console-batch)
- Keeping the directory up to date from provider events (dir-projection)

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Related rules: R-PRVDIR01-ADD-01, R-PRVDIR01-ADD-02, R-PRVDIR01-ADD-04, R-PRVDIR01-cbl-00004, R-PRVDIR01-cbl-00018
Programs: PRVDIR01.cbl:72-81 (EXTRACT-NEXT: active filter), 83-90 (WRITE-DIR-ROW: name is the first 60 bytes of PRV-VARIANT at :88, D-028). No job step; PRVDIR01 is run by no JCL.
Decisions: D-028 FIX (accepted, Q-010): the name is composed from the name fields (individual: first + last; organisation: legal name); D-006 A suspended provider has its own status and is not listed; D-008 PRESERVE: provider types IN, GR, FA; D-024 FIX: failures are surfaced

## Technical notes (agent-owned)
Service: web-ui. Endpoints/events: GET /api/v1/directory/providers. Roles: PUBLIC.
