---
key: MAM-34
localId: L-S007
epic: MAM-1
service: provider-service
type: Task
title: "Walking skeleton: provider lookup by NPI (console -> gateway -> provider-service -> Postgres, with login)"
slug: plt-walking-skeleton
specVersion: 1
descriptionSha256: 7aac2b33ccc6cd05f573b002bfc8fb178c100c69f65e603689635c8d213bd67b
jiraUpdated: 2026-11-18T09:15:45.482-0600
rules: []
decisions: [D-014, D-022, D-023]
dependsOn: [MAM-29, MAM-50, MAM-19]
mainJourney: true
labels: [svc-provider-service, kag-L-S007, agentic, decision-D-014, decision-D-022, decision-D-023, slice-main-journey]
builtCommit: pending-user-commit
testsSha256: edab0548bab6d3c63c2527b31e5707efa8fb2439503d9e16715a98677a6f54dc
---
## Business requirement
Before the real features are built, one thin feature must work from the analyst's screen all the way to the database and back, with sign-in and access control in place. Looking up a provider by NPI proves that the platform, the gateway, the identity service, the first service and the web application are wired together correctly.

## Acceptance criteria
### AC-1 An analyst reads a seeded provider through the gateway
Given the stack is up and seeded, and an ANALYST token
When GET /api/v1/providers/{npi} is called through the gateway for a seeded NPI
Then it returns that seeded provider
Basis: .claude/commands/phase-5-platform.md step 4 (AC-1), D-014

### AC-2 No token gives 401, another provider's NPI gives 403
Given the stack is up and seeded
When GET /api/v1/providers/{npi} is called through the gateway without a token, and then with a PROVIDER token for another NPI
Then the call without a token returns 401 and the call with the PROVIDER token for another NPI returns 403
Basis: .claude/commands/phase-5-platform.md step 4 (AC-2), D-023, QF-1 (default applied)

### AC-3 The console page Provider lookup shows the provider
Given an analyst is signed in to the console
When they look up a seeded NPI on the console page Provider lookup
Then the page shows the provider's name, status and risk tier
Basis: .claude/commands/phase-5-platform.md step 4 (AC-3), D-022, modern/docs/ux/screen-inventory.md (console provider-search)

## Out of scope
- Search by Medicaid ID or name (ui-console-search, prv-search-read)
- The full provider detail page (ui-console-detail)
- Role-based navigation and portal home (ui-shell-login)
- Writing or changing providers

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: ONLINE/PRVCICS1.cbl:28-61 (PINQ inquiry replaced, D-022; no authorisation, D-023); VSAM/PRVVSM01.jcl:14-17 and DB2/PRVDDL01.sql:19-33 (dual system of record, D-014)
Decisions: D-014 FIX: provider-service is the single owner of the provider record that the lookup reads; D-022 REPLACE: console inquiry by NPI that shows the status; D-023 FIX: role-based access, 401 without a token and 403 for another provider's record

## Technical notes (agent-owned)
Service: provider-service. Endpoints/events: GET /api/v1/providers/{npi}. Roles: ANALYST, PROVIDER.
