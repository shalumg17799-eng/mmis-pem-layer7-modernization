---
key: MAM-77
localId: L-S059
epic: MAM-13
service: web-ui
type: Story
title: "Console: provider search by NPI, Medicaid ID or name (replaces CICS PINQ)"
slug: ui-console-search
specVersion: 1
descriptionSha256: c32ce8e15eb1111ee7b478138668529fe3bd6318f9dc153b9800bc76e077862d
jiraUpdated: 2026-11-18T09:21:43.426-0600
rules: [R-PRVCICS1-ADD-02, R-PRVCICS1-ADD-03, R-PRVCICS1-cbl-00001, R-PRVCICS1-cbl-00002, R-PRVCICS1-cbl-00004, R-PRVCICS1-cbl-00011, R-PRVCICS1-cbl-00012, R-PRVCICS1-cbl-00013, R-PRVCICS1-cbl-00014, R-PRVCICS1-cbl-00016, R-PRVCICS1-cbl-00017, R-PRVCICS1-cbl-00018, R-PRVCICS1-cbl-00020, R-PRVCICS1-cbl-00024, R-PRVCICS1-cbl-00025, R-PRVCICS1-cbl-00042, R-PRVCICS1-cbl-00043, R-PRVCICS1-cbl-00044, R-PRVCICS1-cbl-00045]
decisions: [D-022, D-023, D-024]
dependsOn: [MAM-62, MAM-37]
mainJourney: false
labels: [svc-web-ui, kag-L-S059, agentic, decision-D-022, decision-D-023, decision-D-024]
builtCommit: 2e20ead
testsSha256: 346ccf2bbe28503c36dc72b3b29fc435c9056140dfd56c78ed5fe37802cc8a62
---
## Business requirement
A state analyst can look up a provider by identification number, Medicaid ID or name. The number the analyst types is the one searched, a blank or malformed entry is sent back for correction, an unknown provider is reported plainly as 'No provider found', and the last number used is remembered so the analyst can continue without retyping. This replaces the old inquiry transaction, whose typed number was never used and whose messages never reached the screen. Only staff roles can use it.

## Acceptance criteria
### AC-1 The inquiry screen opens ready for input
Given analyst1 is signed in and has not searched in this browser tab
When /console/providers opens
Then The page shows the h1 'Provider search' with labelled inputs NPI, Medicaid ID and Name contains and a Search button; the NPI input is empty; no results table and no message are shown; the page has made no search call
Basis: R-PRVCICS1-cbl-00016, R-PRVCICS1-cbl-00014, D-022, modern/docs/ux/wireframes/console-provider-search.html, modern/docs/ux/screen-inventory.md (console provider-search)

### AC-2 The typed NPI is the search key
Given The provider with NPI 1003000111 exists (RIVERA MARIA, Individual, Active, Limited)
When analyst1 types 1003000111 into NPI and chooses Search
Then The page calls GET /api/v1/providers?npi=1003000111 with exactly the typed value and shows a results table (NPI, Name, Type, Status, Risk) with one row: 1003000111 | RIVERA MARIA | Individual (IN) | Active (A) | Limited (L), whose NPI links to /console/providers/1003000111
Basis: R-PRVCICS1-ADD-02, R-PRVCICS1-cbl-00002, R-PRVCICS1-cbl-00018, R-PRVCICS1-cbl-00003, D-022, modern/docs/ux/journeys.md#J4 (step 1), modern/docs/ux/wireframes/console-provider-search.html

### AC-3 Search by Medicaid ID or by name
Given The same provider, with Medicaid ID TX0000111
When analyst1 clears NPI and enters Medicaid ID TX0000111, and then clears it and enters the name 'RIVERA'
Then The page calls GET /api/v1/providers?medicaidId=TX0000111 and then GET /api/v1/providers?name=RIVERA, sending only the filled inputs, and each search shows the row for 1003000111
Basis: modern/docs/ux/journeys.md#J4 (step 1: then by Medicaid ID), R-PRVCICS1-ADD-07, modern/docs/ux/screen-inventory.md (new Medicaid ID and name inputs), modern/contracts/openapi/provider-service.yaml#searchProviders, D-022, QE-11

### AC-4 A blank or malformed entry is not searched
Given The inquiry screen
When analyst1 chooses Search with all three inputs empty, and then with NPI 12345
Then No search call is made; an error summary at the top links to the NPI field ('Enter an NPI, a Medicaid ID or a name' for the empty search, 'NPI must be 10 digits' for 12345), the same text appears next to the field, the focus moves to the summary and what was typed stays, so the analyst can correct it and search again
Basis: R-PRVCICS1-cbl-00020, D-022, modern/contracts/openapi/provider-service.yaml#searchProviders (npi pattern ^[0-9]{10}$), .claude/skills/frontend-stack/SKILL.md section 5

### AC-5 An unknown provider shows 'No provider found' (journey J4)
Given No provider has NPI 1003000999 (an NPI that is in none of the sample feeds)
When analyst1 searches NPI 1003000999
Then The API answers 200 with no items (total 0) and the page shows the message 'No provider found' in a status area, with no results table; the message that PINQ set as PROVIDER NOT FOUND and never sent now reaches the screen, and the page is neither blank nor an error page
Basis: R-PRVCICS1-cbl-00004, R-PRVCICS1-cbl-00024, R-PRVCICS1-cbl-00025, R-PRVCICS1-ADD-01, R-PRVCICS1-cbl-00036, D-022, modern/docs/ux/journeys.md#J4 (step 3), modern/contracts/openapi/provider-service.yaml#ProviderPage

### AC-6 The last NPI is kept for the next inquiry
Given analyst1 searched NPI 1003000111 and opened its detail page
When They return to /console/providers in the same browser tab, then search NPI 1003000222, and finally open the page in a new tab
Then The NPI input is pre-filled with 1003000111 on the return visit (and nothing is searched until they choose Search); after the search for 1003000222 that NPI is the one kept; the new tab shows an empty NPI input, as on a first visit
Basis: R-PRVCICS1-cbl-00001, R-PRVCICS1-cbl-00011, R-PRVCICS1-cbl-00012, R-PRVCICS1-cbl-00013, R-PRVCICS1-cbl-00014, R-PRVCICS1-cbl-00017, R-PRVCICS1-cbl-00042, R-PRVCICS1-cbl-00043, R-PRVCICS1-cbl-00044, R-PRVCICS1-cbl-00045, modern/docs/ux/screen-inventory.md (NPI kept as the last search, replaces the COMMAREA), QE-10, D-022

### AC-7 Failures are shown, never swallowed
Given The inquiry screen
When The API answers 401, 403, 400 or 5xx, or the network call fails
Then The page shows a message that says which: 401 sends the analyst to sign in again, 403 reads 'You are not allowed to search providers', 400 shows the detail the API gives, and 5xx or a network failure reads 'The search could not be completed. Try again.'; none of them is shown as 'No provider found', and no failure leaves a blank table
Basis: R-PRVCICS1-ADD-03, R-PRVCICS1-ADD-04, D-022, D-023, D-024, modern/contracts/openapi/provider-service.yaml#searchProviders (400, 401, 403)

### AC-8 The search screen has zero axe violations
Given The Playwright test opens the empty screen, a results page, the 'No provider found' state and a validation-error state
When It runs an axe scan on each state and uses the screen with the keyboard only
Then Every scan reports zero violations; Enter in any input starts the search; the results table has header cells; status and risk are written as text with their code; the focus is always visible
Basis: .claude/skills/frontend-stack/SKILL.md section 5, modern/docs/architecture/nfr.md (Accessibility), modern/docs/ux/journeys.md#J4

## Out of scope
- The provider detail page (ui-console-detail and ui-console-detail-2)
- The search back end (prv-search-read)
- Role checks on the API (nfr-security); the route guard is in ui-shell-login
- Advanced filters by status or screen flag (those are used by the work queues)
- Storing the last search on the server (QE-10)

## Legacy trace (agent-owned, do not edit)
Rules: R-PRVCICS1-ADD-02, R-PRVCICS1-ADD-03, R-PRVCICS1-cbl-00001, R-PRVCICS1-cbl-00002, R-PRVCICS1-cbl-00004, R-PRVCICS1-cbl-00011, R-PRVCICS1-cbl-00012, R-PRVCICS1-cbl-00013, R-PRVCICS1-cbl-00014, R-PRVCICS1-cbl-00016, R-PRVCICS1-cbl-00017, R-PRVCICS1-cbl-00018, R-PRVCICS1-cbl-00020, R-PRVCICS1-cbl-00024, R-PRVCICS1-cbl-00025, R-PRVCICS1-cbl-00042, R-PRVCICS1-cbl-00043, R-PRVCICS1-cbl-00044, R-PRVCICS1-cbl-00045
Related rules: R-PRVCICS1-ADD-01, R-PRVCICS1-ADD-04, R-PRVCICS1-ADD-05, R-PRVCICS1-ADD-07, R-PRVCICS1-cbl-00003, R-PRVCICS1-cbl-00015, R-PRVCICS1-cbl-00019, R-PRVCICS1-cbl-00021, R-PRVCICS1-cbl-00022, R-PRVCICS1-cbl-00036
Programs: PRVCICS1.cbl:28-42 (MAIN-LOGIC: COMMAREA moved to the key at :29-31, RECEIVE MAP :33-35 with no INTO), 44-51 (RESP evaluation), 47-56 (NOTFND message set but not sent, SEND MAP), 58-61 (RETURN TRANSID PINQ with COMMAREA). CICS transaction PINQ; no job step.
Decisions: D-022 REPLACE (accepted, Q-010): analyst-console inquiry by NPI or Medicaid ID that shows the status; D-023 FIX (accepted, Q-010): role-based access on every endpoint and screen; D-024 FIX (accepted, Q-010): explicit error handling, failures surfaced

## Technical notes (agent-owned)
Service: web-ui. Endpoints/events: GET /api/v1/providers. Roles: ANALYST, SUPERVISOR, ADMIN.
