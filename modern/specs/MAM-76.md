---
key: MAM-76
localId: L-S070
epic: MAM-14
service: e2e
type: Story
title: "Journey J4 end to end: analyst inquiry (PINQ replacement)"
slug: e2e-j4
specVersion: 1
descriptionSha256: b477ff3138bbef58703bbb14dfdbaa2b1f8c24f04c2b97ed8e9ee1a4ef55b685
jiraUpdated: 2026-11-18T09:21:40.553-0600
rules: []
decisions: [D-022, D-023, D-006]
dependsOn: [MAM-80, MAM-77, MAM-62, MAM-37, MAM-40, MAM-69, MAM-50]
mainJourney: false
labels: [svc-e2e, kag-L-S070, agentic, decision-D-022, decision-D-023, decision-D-006]
builtCommit: pending-user-commit
testsSha256: 2a5e144e1797dd8e31996da35d73e21410a9c21b742c621240de624dfc7fe9ab
---
## Business requirement
Analysts must be able to look up any provider by NPI or Medicaid ID and see everything the old inquiry screen held plus the status message it never showed, while providers can see only their own record. This story proves the inquiry and its access control work for real in the running system.

## Acceptance criteria
### AC-1 Search by NPI
Given provider 1003000111 is enrolled (the test set-up replays the J1 API steps) and analyst1 is on /console/providers
When analyst1 types NPI 1003000111 and searches
Then GET /api/v1/providers?npi=1003000111 is called and the provider is shown
Basis: modern/docs/ux/journeys.md#J4 step 1, D-022, modern/docs/ux/screen-inventory.md (console provider-search), QF-6 (default applied)

### AC-2 Search by Medicaid ID
Given the same provider and its Medicaid ID
When analyst1 searches by that Medicaid ID
Then GET /api/v1/providers?medicaidId=<id> is called and the same provider is shown
Basis: modern/docs/ux/journeys.md#J4 step 1, modern/docs/ux/screen-inventory.md (R-PRVCICS1-ADD-07 extra search inputs), D-022

### AC-3 The detail shows every field with its meaning
Given analyst1 opens /console/providers/1003000111
When the page is read
Then it shows the NPI in full, Medicaid ID, type Individual (IN), taxonomy 207Q00000X, risk tier Limited (L), status Active (A) as text and code, screen flag Pass (P), effective and revalidation due dates as MM/DD/YYYY, last name, first name, licence number and state, and panels for screening per source, sanctions, revalidation and notices
Basis: modern/docs/ux/journeys.md#J4 step 2, modern/docs/ux/screen-inventory.md (PINQ field map), .claude/skills/frontend-stack/SKILL.md section 6, D-022, D-006

### AC-4 Audit history is shown to a supervisor
Given supervisor1 opens the same provider detail
When the page is read
Then it also shows the audit history from GET /api/v1/providers/1003000111/audit, while analyst1's page shows no audit panel and makes no call to it
Basis: modern/docs/architecture/nfr.md (Security: audit is SUPERVISOR and ADMIN), modern/contracts/openapi/provider-service.yaml (getProviderAudit x-roles), QF-11 (default applied), modern/docs/ux/journeys.md#J4 step 2

### AC-5 An unknown NPI says No provider found
Given analyst1 is on /console/providers
When analyst1 searches for an NPI that no provider has (1003000999)
Then the page shows No provider found, and the API returns an empty page rather than an error
Basis: modern/docs/ux/journeys.md#J4 step 3, D-022, modern/docs/ux/screen-inventory.md (WS-MSG PROVIDER NOT FOUND)

### AC-6 A provider cannot use the console and sees only their own record
Given provider1 is signed in
When provider1 opens /console/providers, calls GET /api/v1/providers and GET /api/v1/providers/{npi} for another NPI, and opens /portal/provider
Then the console route shows the 403 refused page, both API calls return 403, and the portal shows only provider1's own record
Basis: modern/docs/ux/journeys.md#J4 step 4, D-023, modern/docs/architecture/nfr.md (Security), .claude/skills/frontend-stack/SKILL.md section 6

### AC-7 Repeatable and accessible
Given the journey as one Playwright test with an axe scan on every page it visits
When it is run three times in a row, each time from the seed state
Then all three runs pass and every axe scan reports zero violations
Basis: .claude/commands/phase-8-e2e-hardening.md steps 2 and 4, modern/docs/architecture/nfr.md (Accessibility), QF-6 (default applied)

## Out of scope
- Changing a provider from the inquiry screen
- Role tests for every endpoint (nfr-security)
- Admin view of revalidation cases (the revalidations endpoint allows ANALYST and SUPERVISOR only, QF-11)

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: ONLINE/PRVCICS1.cbl:28-61 (NPI typed on the screen never reaches the key, no status message sent, no authorisation; D-022, D-023); ONLINE/PRVCICS1.cbl:47-72 (WS-MSG built but not sent)
Decisions: D-022 REPLACE: console inquiry by NPI or Medicaid ID that shows the status; D-023 FIX: role-based access; a PROVIDER cannot open console routes or other providers' records; D-006 FIX: status is shown as text and code (Suspended (S) included)

## Technical notes (agent-owned)
Service: e2e. Endpoints/events: GET /api/v1/providers, GET /api/v1/providers/{npi}, GET /api/v1/providers/{npi}/audit. Roles: ANALYST, SUPERVISOR, PROVIDER.
