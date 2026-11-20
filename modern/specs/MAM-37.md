---
key: MAM-37
localId: L-S022
epic: MAM-3
service: provider-service
type: Story
title: "Search and read providers by NPI or Medicaid ID, role-checked (PINQ replacement back end)"
slug: prv-search-read
specVersion: 2  # 2026-11-20: AC-9 added (optional keyset parameter afterNpi on searchProviders; Q-132, user, 2026-11-20 20:22); AC-1 to AC-8 unchanged; tests and code follow
descriptionSha256: 5a99630179c21d80a4846336dc27b72ca9a89c78165052107b9ef7c7766284f9
jiraUpdated: 2026-11-20T10:11:17.348-0600
rules: [R-PRVCICS1-ADD-04, R-PRVCICS1-ADD-05, R-PRVCICS1-ADD-07, R-PRVCICS1-cbl-00003, R-PRVCICS1-cbl-00015, R-PRVCICS1-cbl-00019, R-PRVCICS1-cbl-00021, R-PRVCICS1-cbl-00022, R-PRVCICS1-cbl-00023]
decisions: [D-006, D-022, D-023, D-024]
dependsOn: [MAM-34]
mainJourney: true
labels: [svc-provider-service, kag-L-S022, agentic, decision-D-006, decision-D-022, decision-D-023, decision-D-024, slice-main-journey]
builtCommit: pending-user-commit
testsSha256: 48d4505bd729709abbb978a4e6809e22f2f2ee112b69572bdcd23d7c0f92fd91
---
## Business requirement
Analysts, supervisors, administrators and system accounts must be able to look up a provider by the NPI they type or by Medicaid ID, and to search by name, status and screening result to build work queues, seeing every stored detail and the current status. A provider may only see their own record. The lookup must tell 'no such provider' apart from a technical failure, and nobody without the right role may read provider data.

## Acceptance criteria
### AC-1 Look up a provider by NPI and see every field
Given provider 1003000111 is ACTIVE, riskTier L, screenFlag P, effectiveDate 2026-11-18, revalidationDueDate 2031-11-18
When an ANALYST sends GET /api/v1/providers/1003000111
Then the response is 200 with npi 1003000111, medicaidId MCD000111, providerType IN, taxonomy 207Q00000X, riskTier L, enrollStatus A, screenFlag P, effectiveDate 2026-11-18, revalidationDueDate 2031-11-18 and individual lastName RIVERA, firstName MARIA, licenseNo MD12345, licenseState TX
Basis: R-PRVCICS1-cbl-00003, R-PRVCICS1-cbl-00021, R-PRVCICS1-cbl-00022, D-022, modern/docs/ux/screen-inventory.md (PINQ field map)

### AC-2 An unknown NPI gives 404
Given no provider with NPI 1003000999 exists
When an ANALYST sends GET /api/v1/providers/1003000999
Then the response is 404 problem+json and contains no data of any other provider
Basis: R-PRVCICS1-cbl-00003, D-022, modern/contracts/openapi/provider-service.yaml (404 Not found)

### AC-3 The NPI typed in the request is the key, with no carry-over
Given an ANALYST has just looked up 1003000111 successfully
When the same ANALYST then requests 1003000444, and after that 1003000999 (unknown)
Then the second response is the record of 1003000444 (never 1003000111's) and the third is 404 (never the previous provider)
Basis: R-PRVCICS1-cbl-00019, R-PRVCICS1-cbl-00015, R-PRVCICS1-cbl-00021, D-022 (the legacy COMMAREA carry-over is replaced; the typed NPI is the key)

### AC-4 The status code is returned as stored, including S
Given providers exist with statuses P, A, T, D and S
When an ANALYST reads each of them
Then enrollStatus is exactly P, A, T, D and S respectively; the suspended provider shows S, not D
Basis: R-PRVCICS1-cbl-00023, D-006, D-022

### AC-5 Search by Medicaid ID or NPI
Given provider 1003000111 has Medicaid ID MCD000111
When an ANALYST sends GET /api/v1/providers?medicaidId=MCD000111, then GET /api/v1/providers?npi=1003000111, then GET /api/v1/providers?npi=1003000999
Then the first two return an items array with exactly provider 1003000111 (total 1); the third returns 200 with an empty items array and total 0
Basis: R-PRVCICS1-ADD-07, D-022, modern/docs/ux/journeys.md#J4

### AC-6 Search by name, status and screen flag with paging (work queues)
Given 1003000111 is PENDING with screenFlag P, 1003000444 is PENDING with screenFlag F, 1003000222 is PENDING with screenFlag U, and 25 providers exist in total
When an ANALYST sends GET /api/v1/providers?status=P&screenFlag=P, then ?status=P&screenFlag=F, then ?name=RIVERA, then ?page=1&size=20
Then the first returns only 1003000111, the second only 1003000444, the third only providers whose name contains RIVERA (1003000111), and the fourth returns 5 items with page 1, size 20 and total 25
Basis: modern/contracts/openapi/provider-service.yaml (searchProviders parameters, ProviderPage), modern/docs/ux/screen-inventory.md (console work-queues), modern/docs/ux/journeys.md#J1, modern/docs/ux/journeys.md#J3

### AC-7 Role checks: a provider sees only their own record
Given provider1 is a PROVIDER whose token carries the npi claim 1003000111, and 1003000111 is ACTIVE
When GET /api/v1/providers/1003000111, GET /api/v1/providers/1003000444 and GET /api/v1/providers are each called with provider1's token, with ANALYST, SUPERVISOR, ADMIN and SYSTEM tokens, and with no token
Then provider1 gets 200 with enrollStatus A for 1003000111, 403 for 1003000444 and 403 for the search; ANALYST, SUPERVISOR, ADMIN and SYSTEM get 200 on all three; no token gets 401 on all three
Basis: R-PRVCICS1-ADD-05, D-023, ADR-007, modern/docs/architecture/nfr.md (roles per endpoint), modern/contracts/openapi/provider-service.yaml (getProvider: a PROVIDER sees only their own record), .claude/commands/phase-5-platform.md (PROVIDER token for another NPI gives 403), modern/docs/ux/journeys.md#J1 step 7, modern/docs/ux/journeys.md#J4

### AC-8 A read failure is not reported as 'not found'
Given the provider database is unavailable
When an ANALYST sends GET /api/v1/providers/1003000111 with X-Correlation-Id 7d2f9c4e-1b3a-4c5d-8e6f-000000000123
Then the response is an RFC 7807 problem with a 5xx status (not 404) and the same X-Correlation-Id header, and the failure is logged with that correlation id
Basis: R-PRVCICS1-ADD-04, D-022, D-024, .claude/skills/java-microservices/SKILL.md sections 6 and 11

### AC-9 Walk all providers of a filter by key (afterNpi) in NPI order, none missed or repeated
Given provider-service holds 450 ACTIVE providers NPI 1003001001 to 1003001450 and provider 1003001500, which is PENDING
When an ANALYST sends GET /api/v1/providers?status=A&page=1&size=200 (no afterNpi); then walks all providers with status=A and size=200 using the optional parameter afterNpi (the first page from the start of the NPI order, every further page with afterNpi set to the last NPI of the page before, until a page holds fewer than 200 items), and between the first and the second page of that walk provider 1003001100 (already on the first page) is terminated and provider 1003001500 is approved; then sends GET /api/v1/providers?status=A&afterNpi=ABC and GET /api/v1/providers?status=A&afterNpi=12345
Then the request without afterNpi answers as before the parameter existed (200 items, page 1, size 200, total 450, as in AC-6); the walk returns the providers in ascending NPI order and every provider that matches status=A during the whole walk (1003001001 to 1003001450 except 1003001100) exactly once, none skipped (1003001201 is on the second page) and none twice, while 1003001100 and 1003001500, which changed status during the walk, each appear at most once; the two malformed keys are refused with 400 application/problem+json
Basis: Q-132 (user decision of 2026-11-20), MAM-71 AC-5 (no provider is missed or repeated), R-PRVTMS01-cbl-00003 and R-PRVDIR01-cbl-00002 (the legacy read the master in key order with READ NEXT: PRVTMS01:92-95, PRVDIR01:73), modern/contracts/openapi/provider-service.yaml (searchProviders: the optional parameter afterNpi, added by the minor version bump of Q-132)

## Out of scope
- The console search and detail screens, the 'No provider found' text and the status banner (ui-console-search, ui-console-detail).
- Screening, sanction, revalidation and notice panels of the detail page (their own services).
- The public directory search: dir-search.
- The walking-skeleton lookup (plt-walking-skeleton) is the starting point; this story completes and role-tests it.

## Legacy trace (agent-owned, do not edit)
Rules: R-PRVCICS1-ADD-04, R-PRVCICS1-ADD-05, R-PRVCICS1-ADD-07, R-PRVCICS1-cbl-00003, R-PRVCICS1-cbl-00015, R-PRVCICS1-cbl-00019, R-PRVCICS1-cbl-00021, R-PRVCICS1-cbl-00022, R-PRVCICS1-cbl-00023
Programs: PRVCICS1: source-code/ONLINE/PRVCICS1.cbl:28-61 (MAIN-LOGIC, no authorisation check ADD-05), 25-61 (COMMAREA of 10 bytes, ADD-07), 29-42 (retained NPI, 00015), 33-42 (captured NPI, 00019), 37-42 (READ FILE PRVPROVN, 00003 and 00022), 40 (RIDFLD, 00021), 44-50 (RESP handling, ADD-04), 45-46 (FORMAT-PROVIDER, 00023). CICS transaction PINQ; no JCL job runs it.
Decisions: D-006 FIX: SUSPENDED (S) is shown as its own status; D-022 REPLACE: inquiry by NPI or Medicaid ID; the typed NPI is the key; not-found and read errors are distinguished; D-023 FIX: role-based access on every endpoint; D-024 FIX: explicit error handling and a logged failure

## Technical notes (agent-owned)
Service: provider-service. Endpoints/events: GET /api/v1/providers, GET /api/v1/providers/{npi}. Roles: PROVIDER, ANALYST, SUPERVISOR, ADMIN, SYSTEM.
