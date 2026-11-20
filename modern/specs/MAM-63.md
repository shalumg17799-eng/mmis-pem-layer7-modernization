---
key: MAM-63
localId: L-S047
epic: MAM-10
service: directory-service
type: Story
title: "Public provider directory search"
slug: dir-search
specVersion: 1
descriptionSha256: b64f5d517c122817b0cbd76cf66ddcfb6468e86f6ad8aca4a400bd0f1f25640f
jiraUpdated: 2026-11-18T09:20:02.834-0600
rules: []
decisions: [D-028, D-006, D-008]
dependsOn: [MAM-61]
mainJourney: true
labels: [svc-directory-service, kag-L-S047, agentic, decision-D-028, decision-D-006, decision-D-008, slice-main-journey]
builtCommit:
testsSha256: 3b945c4506963f5b470de2db6242c5491031ed2f68fda7cc72c8b3d43ba331f7
---
## Business requirement
Anyone, including people who are not signed in, can search the public directory of enrolled providers by name, provider type or specialty code and page through the results. Only active providers are listed. Each entry shows the few facts the legacy directory already held for managed care organisations (Medicaid ID, NPI, provider type, risk level and a readable name) plus the specialty code; no addresses, phone numbers, screening results, sanctions or licence numbers are shown.

## Acceptance criteria
### AC-1 Anyone can search without signing in
Given the directory holds ACTIVE entries for NPI 1003000111 (type IN, displayName 'MARIA RIVERA') and NPI 1003000333 (type FA, displayName 'SUNSET HOSPICE INC')
When a caller with no Authorization header calls GET /api/v1/directory/providers
Then the response is 200 application/json with a page object: items holding both entries, page 0, size 20 (the defaults) and total 2.
Basis: ADR-007, ADR-006, modern/docs/architecture/nfr.md (GET /api/v1/directory/providers: PUBLIC), modern/contracts/openapi/directory-service.yaml#searchDirectory (security none; page default 0, size default 20; DirectoryPage), Q-008

### AC-2 Only ACTIVE providers are listed
Given the directory knows NPI 1003000111 (A active), 1003000333 (S suspended), 1003000444 (D denied) and 1003000555 (P pending)
When anyone calls GET /api/v1/directory/providers with no filters
Then items contain only NPI 1003000111 and total is 1.
Basis: R-PRVDIR01-ADD-01, R-PRVDIR01-cbl-00004, R-PRVDIR01-cbl-00018, D-006, Q-008, modern/docs/ux/journeys.md#J5, modern/docs/ux/journeys.md#J7

### AC-3 Search by name finds the composed name
Given the directory holds ACTIVE entries 'MARIA RIVERA' (NPI 1003000111) and 'SUNSET HOSPICE INC' (NPI 1003000333)
When anyone calls GET /api/v1/directory/providers with name=HOSPICE, then with name=rivera, then with name=ZZZZ
Then the first call returns only NPI 1003000333, the second only NPI 1003000111 (matching ignores letter case), and the third returns 200 with no items and total 0 (not an error).
Basis: D-028, R-PRVDIR01-ADD-02, modern/docs/ux/journeys.md#J7, OQ-D3, modern/contracts/openapi/directory-service.yaml#searchDirectory (name: Name contains)

### AC-4 Filter by provider type
Given ACTIVE entries for NPI 1003000111 (type IN) and NPI 1003000333 (type FA)
When anyone calls GET /api/v1/directory/providers with providerType=IN, then with providerType=FA, and then with providerType=XX
Then the first call returns only NPI 1003000111, the second only NPI 1003000333; the third is rejected with 400 application/problem+json because XX is not IN, GR or FA.
Basis: D-008, modern/contracts/openapi/directory-service.yaml#searchDirectory (providerType enum IN, GR, FA; response 400)

### AC-5 Filter by taxonomy and combine filters
Given ACTIVE entries for NPI 1003000111 (type IN, taxonomy 207Q00000X) and NPI 1003000333 (type FA, taxonomy 251G00000X)
When anyone calls GET /api/v1/directory/providers with taxonomy=207Q00000X, and then with taxonomy=207Q00000X and providerType=FA
Then the first call returns only NPI 1003000111; the second returns 200 with no items and total 0 because every filter given must match.
Basis: modern/contracts/openapi/directory-service.yaml#searchDirectory (taxonomy), Q-008, OQ-D3

### AC-6 Results are paged in a stable order
Given 25 synthetic ACTIVE entries in the directory, NPI 1003001001 to 1003001025
When anyone calls GET /api/v1/directory/providers with page=0 and size=10, then page=2 and size=10, then size=201
Then page 0 returns NPI 1003001001 to 1003001010 in ascending NPI order with page 0, size 10 and total 25; page 2 returns NPI 1003001021 to 1003001025; size=201 (and likewise size=0 or page=-1) is rejected with 400 application/problem+json.
Basis: modern/contracts/openapi/directory-service.yaml#searchDirectory (page minimum 0; size 1 to 200, default 20; response 400), java-microservices skill section 6 (page, size, items, total), OQ-D3

### AC-7 Only the published facts are returned
Given the ACTIVE provider NPI 1003000111, whose provider record also holds licence MD12345, screen flag P, a revalidation due date and a sanction history
When anyone calls GET /api/v1/directory/providers with name=RIVERA
Then the item contains only npi, medicaidId, providerType, riskTier, displayName and taxonomy: no licence number, screen flag, status, due date, sanction or address data.
Basis: Q-008, ADR-006, modern/contracts/openapi/directory-service.yaml#/components/schemas/DirectoryEntry

## Out of scope
- The public directory web page (ui-public-directory)
- FHIR search (dir-fhir) and the legacy DIROUT file (dir-extract)
- Search by NPI or Medicaid ID on the REST directory: the contract has no such parameter (the FHIR identifier search covers NPI)
- Addresses, phones and networks (Q-008, ADR-006)
- Response-time targets and the security test of every role (nfr-performance, nfr-security)

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Related rules: R-PRVDIR01-ADD-01, R-PRVDIR01-ADD-02, R-PRVDIR01-cbl-00004, R-PRVDIR01-cbl-00018
Programs: PRVDIR01: EXTRACT-NEXT 78-79 (ACTIVE filter). The public search has no legacy equivalent: the legacy only produced a flat file for managed care organisations (PRVDIR01 WRITE-DIR-ROW 89). No JCL job runs PRVDIR01.
Decisions: D-028 FIX: compose the name from the name fields (the name searched and shown); D-006 A new SUSPENDED status (suspended providers are not listed); D-008 PRESERVE: keep IN/GR/FA plus a mapping table

## Technical notes (agent-owned)
Service: directory-service. Endpoints/events: GET /api/v1/directory/providers. Roles: PUBLIC.
