---
key: MAM-61
localId: L-S046
epic: MAM-10
service: directory-service
type: Story
title: "Keep the directory of ACTIVE providers up to date from provider events"
slug: dir-projection
specVersion: 1
descriptionSha256: 53260762a2921b7f9154a68a02e119d13fecc1a633f453dc292d96927a71cdc2
jiraUpdated: 2026-11-18T09:19:46.104-0600
rules: [R-PRVDIR01-ADD-01, R-PRVDIR01-ADD-02, R-PRVDIR01-ADD-05, R-PRVDIR01-cbl-00004, R-PRVDIR01-cbl-00005, R-PRVDIR01-cbl-00006, R-PRVDIR01-cbl-00018, R-PRVDIR01-cbl-00019, R-PRVDIR01-cbl-00020, R-PRVDIR01-cbl-00021, R-PRVDIR01-cbl-00022, R-PRVDIR01-cbl-00023]
decisions: [D-028, D-013, D-014, D-006, D-019, D-027, D-008]
dependsOn: [MAM-27, MAM-17, MAM-35, MAM-37, MAM-31]
mainJourney: true
labels: [svc-directory-service, kag-L-S046, agentic, decision-D-028, decision-D-013, decision-D-014, decision-D-006, decision-D-019, decision-D-027, decision-D-008, slice-main-journey]
builtCommit:
testsSha256: dfe68f9e739db1d45bb8e9dc1f6c153d6a57a474d92af89659da49d699ad9d6f
---
## Business requirement
The provider directory must list a provider only while that provider is enrolled and active. The directory keeps its own up-to-date list, built from the changes the provider record reports as providers are registered, given a risk level, approved, suspended, terminated, denied or reinstated, so that searching the directory never depends on the provider record being available. A provider appears in the directory once an analyst has approved it and disappears as soon as it is no longer active. Each provider is shown by a readable name (first and last name for an individual, the legal name for a group or facility) instead of the raw stored name text, which for individuals ran on into the first characters of the licence number.

## Acceptance criteria
### AC-1 A pending provider is not published
Given directory-service has consumed ProviderRegistered for the individual provider NPI 1003000111 (Medicaid ID M00000111, type IN, enrollStatus P, screenFlag U, taxonomy 207Q00000X)
When anyone calls GET /api/v1/directory/providers without a token
Then NPI 1003000111 is not among the returned items and the page total does not count it: a provider that is not ACTIVE is not published.
Basis: R-PRVDIR01-cbl-00004, R-PRVDIR01-cbl-00018, R-PRVDIR01-ADD-01, Q-008, modern/docs/ux/journeys.md#J1, modern/docs/architecture/overview.md (flows 1 and 6), OQ-D11

### AC-2 Approval publishes the provider with the directory fields
Given NPI 1003000111 was registered (Medicaid ID M00000111, type IN, taxonomy 207Q00000X), directory-service consumed RiskTierAssigned with tier L for it, and provider-service answers GET /api/v1/providers/1003000111 with last name RIVERA, first name MARIA, licence MD12345 and licence state TX
When directory-service consumes ProviderStatusChanged for NPI 1003000111 with from P and to A (the analyst approval)
Then GET /api/v1/directory/providers returns exactly one item for NPI 1003000111 with medicaidId M00000111, providerType IN (the legacy code), riskTier L, taxonomy 207Q00000X and displayName 'MARIA RIVERA'; provider-service received GET /api/v1/providers/1003000111 authenticated with the SYSTEM service token, and directory-service read no other service's database.
Basis: R-PRVDIR01-cbl-00005, R-PRVDIR01-cbl-00019, R-PRVDIR01-cbl-00020, R-PRVDIR01-cbl-00021, R-PRVDIR01-cbl-00022, D-019, D-013, D-008, Q-008, ADR-007, modern/docs/ux/journeys.md#J1, modern/docs/architecture/overview.md (flows 3 and 6), modern/contracts/asyncapi/events.yaml (ProviderStatusChanged, RiskTierAssigned), modern/docs/architecture/nfr.md (GET /api/v1/providers/{npi} allows SYSTEM), OQ-D1

### AC-3 The display name is composed from the name fields, never raw bytes
Given provider-service holds NPI 1003000111 (type IN) with last name RIVERA, first name MARIA, licence number MD12345 and licence state TX, and NPI 1003000333 (type FA, Medicaid ID M00000333) with legal name SUNSET HOSPICE INC and DBA name SUNSET HOSPICE CARE, and both become ACTIVE
When anyone calls GET /api/v1/directory/providers
Then displayName for 1003000111 is 'MARIA RIVERA' and for 1003000333 is 'SUNSET HOSPICE INC'; neither contains the licence number, the DBA name, padding spaces or more than 60 characters (the legacy name field held the 30-byte last name, the 20-byte first name and the first 10 characters of the licence number, untrimmed).
Basis: D-028, R-PRVDIR01-ADD-02, R-PRVDIR01-cbl-00006, R-PRVDIR01-cbl-00023, R-PRVDIR01-cbl-00025, Q-008, modern/contracts/openapi/directory-service.yaml#/components/schemas/DirectoryEntry (displayName maxLength 60, composed from the name fields), OQ-D1, OQ-D2, modern/docs/ux/journeys.md#J7

### AC-4 A provider that is no longer ACTIVE is withdrawn
Given NPI 1003000333 (type FA, SUNSET HOSPICE INC) is published as ACTIVE
When directory-service consumes ProviderStatusChanged for it with from A and to S (suspended)
Then GET /api/v1/directory/providers no longer returns NPI 1003000333; the same happens for a change from A to T (terminated), and a provider that was denied (to D), such as NPI 1003000444, is never returned.
Basis: R-PRVDIR01-cbl-00018, R-PRVDIR01-cbl-00004, R-PRVDIR01-ADD-01, D-006, modern/docs/ux/journeys.md#J5

### AC-5 A reinstated provider is published again
Given NPI 1003000333 was suspended and is not returned by GET /api/v1/directory/providers
When directory-service consumes ProviderStatusChanged for it with from S and to A (reinstated)
Then GET /api/v1/directory/providers returns NPI 1003000333 again, exactly once, with the same medicaidId, providerType, riskTier, taxonomy and displayName as before the suspension.
Basis: R-PRVDIR01-cbl-00018, D-006, D-027, modern/docs/ux/journeys.md#J5

### AC-6 Only the enrolment status decides publication
Given NPI 1003000444 (type GR, legal name EXCLUDED CLINIC LLC), whose screening result is FAIL (screen flag F) and for which no RiskTierAssigned was ever received, was set ACTIVE by a reinstatement (ProviderStatusChanged from D to A) whose revalidationDueDate is 2025-01-31, already in the past
When anyone calls GET /api/v1/directory/providers
Then NPI 1003000444 is returned, with riskTier null: a failed screening or an overdue revalidation does not remove an ACTIVE provider from the directory, exactly as the legacy filter (status ACTIVE only).
Basis: R-PRVDIR01-ADD-01, R-PRVDIR01-cbl-00018, Q-008, D-027, modern/contracts/openapi/directory-service.yaml#/components/schemas/DirectoryEntry (riskTier nullable)

### AC-7 A redelivered event changes nothing
Given directory-service already processed the ProviderStatusChanged event with id 7f9c0e2a-0000-4000-8000-000000000001 (NPI 1003000111, from P to A) and recorded that id as processed
When the same event (same id) is delivered again
Then GET /api/v1/directory/providers still returns exactly one item for NPI 1003000111, with unchanged data, and the page total is unchanged.
Basis: ADR-003, java-microservices skill section 7 (processed_event), modern/docs/architecture/data-model/directory-service.sql (processed_event)

### AC-8 Searches are answered from the directory's own data
Given NPI 1003000111 is published and provider-service is not reachable
When anyone calls GET /api/v1/directory/providers
Then the response is 200 and still contains NPI 1003000111: the directory reads neither the legacy provider master nor provider-service when it answers a search.
Basis: D-013, D-014, ADR-002, R-PRVDIR01-ADD-05, modern/docs/architecture/overview.md (no service reads another service's database)

## Out of scope
- Search filters (providerType, taxonomy, name), paging limits and request validation of the public search (dir-search)
- FHIR resources (dir-fhir) and the legacy DIROUT file (dir-extract)
- Addresses, phone numbers and network data: the legacy master does not hold them (Q-008, ADR-006)
- Reading the legacy provider master directly: replaced by events and provider-service's API (D-013; PRVDIR01 read PEMAPP1's master, AP-06)
- Event retry, dead-letter handling and the transactional outbox itself (plt-events)
- Loading providers that existed before the directory service started (dat-seed)

## Legacy trace (agent-owned, do not edit)
Rules: R-PRVDIR01-ADD-01, R-PRVDIR01-ADD-02, R-PRVDIR01-ADD-05, R-PRVDIR01-cbl-00004, R-PRVDIR01-cbl-00005, R-PRVDIR01-cbl-00006, R-PRVDIR01-cbl-00018, R-PRVDIR01-cbl-00019, R-PRVDIR01-cbl-00020, R-PRVDIR01-cbl-00021, R-PRVDIR01-cbl-00022, R-PRVDIR01-cbl-00023
Related rules: R-PRVDIR01-cbl-00025
Programs: PRVDIR01 (source-code/BATCH/PRVDIR01.cbl): EXTRACT-NEXT 72-81 (ACTIVE filter 78-79), WRITE-DIR-ROW 83-90 (field mapping 84-88, raw name 88), MAIN-CONTROL 62-70 and READ 73 (direct read of PEMAPP1's provider master, replaced by events and provider-service's API, D-013). No JCL job runs PRVDIR01.
Decisions: D-028 FIX: compose the name from the name fields (individual: first + last; organisation: legal name); D-013 FIX: sanctions-service and directory-service call provider-service's API; D-014 FIX: provider-service is the single owner (the directory keeps only a read model, ADR-002); D-006 A new SUSPENDED status (a suspended provider is not published); D-019 An analyst approval step sets ACTIVE after a Pass screening (which publishes the provider); D-027 PRESERVE the legacy rule (sanctions apply whatever the status, so a reinstate can make a denied provider ACTIVE); D-008 PRESERVE: keep IN/GR/FA plus a mapping table

## Technical notes (agent-owned)
Service: directory-service. Endpoints/events: GET /api/v1/directory/providers, GET /api/v1/providers/{npi}, ProviderRegistered, RiskTierAssigned, ProviderStatusChanged. Roles: SYSTEM, PUBLIC.
