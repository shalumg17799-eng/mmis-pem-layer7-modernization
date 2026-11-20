---
key: MAM-65
localId: L-S048
epic: MAM-10
service: directory-service
type: Story
title: "FHIR R4 Practitioner, Organization and PractitionerRole (read-only, public)"
slug: dir-fhir
specVersion: 1
descriptionSha256: 3d86a73eafd702839b35d916e65c1498ffe9d51dd1d3a374ea717e162cb1ce0e
jiraUpdated: 2026-11-18T09:20:21.621-0600
rules: [R-PRVDIR01-ADD-04]
decisions: [D-028, D-008, D-006]
dependsOn: [MAM-61]
mainJourney: false
labels: [svc-directory-service, kag-L-S048, agentic, decision-D-028, decision-D-008, decision-D-006]
builtCommit: 2e20ead
testsSha256: 23e33a04b5cd4e20b8816e1ba374ee7110920a1b2e4a15647710c71c58997992
---
## Business requirement
Besides the simple directory search, the directory offers its active providers in the standard healthcare data format (FHIR) so that other systems can look them up without signing in. Individual providers are offered as practitioners, groups and facilities as organisations, and each individual's specialty (their taxonomy code) is offered as a practitioner role. Only facts the legacy provider record already holds are offered: identifiers, name, provider type, specialty and licence. Addresses, phone numbers and networks are not offered because the business has not provided them. The data can be read but not changed.

## Acceptance criteria
### AC-1 Practitioner search by NPI
Given NPI 1003000111 is an ACTIVE individual provider (type IN)
When anyone, with no token, calls GET /fhir/Practitioner with identifier=http://hl7.org/fhir/sid/us-npi|1003000111
Then the response is 200 application/fhir+json: a Bundle of type searchset with total 1 whose one entry is a Practitioner carrying the NPI identifier 1003000111.
Basis: ADR-006, modern/contracts/openapi/directory-service.yaml#searchPractitioners (identifier: NPI as system|value; FhirBundle searchset), pem-domain skill section 7, OQ-D5

### AC-2 Practitioner carries only legacy facts
Given the ACTIVE individual NPI 1003000111 (Medicaid ID M00000111) with last name RIVERA, first name MARIA, licence number MD12345 and licence state TX
When the Practitioner is returned by GET /fhir/Practitioner with name=RIVERA
Then it holds the NPI and the Medicaid ID as identifiers, a name with family RIVERA and given MARIA, and the licence MD12345 (state TX) as a qualification, and it holds no address, telecom or network element.
Basis: Q-008, ADR-006, R-PRVDIR01-ADD-02, D-028, OQ-D1, OQ-D5

### AC-3 Organization search for groups and facilities
Given NPI 1003000333 is an ACTIVE organisation of type FA named SUNSET HOSPICE INC (Medicaid ID M00000333) and NPI 1003000111 is an ACTIVE individual
When anyone calls GET /fhir/Organization with name=HOSPICE, and then with identifier=http://hl7.org/fhir/sid/us-npi|1003000333
Then both calls return a searchset Bundle with total 1 whose entry is an Organization with the NPI identifier 1003000333, the Medicaid ID M00000333, the name 'SUNSET HOSPICE INC' and the legacy provider type code FA; the individual 1003000111 never appears among Organizations.
Basis: ADR-006, Q-008, D-008, D-028, modern/contracts/openapi/directory-service.yaml#searchOrganizations, OQ-D5

### AC-4 PractitionerRole carries the taxonomy as specialty
Given NPI 1003000111 is ACTIVE (type IN) with taxonomy 207Q00000X and NPI 1003000555 is ACTIVE (type IN) with taxonomy 225100000X
When anyone calls GET /fhir/PractitionerRole with specialty=207Q00000X
Then the Bundle has total 1 whose entry is a PractitionerRole that references the Practitioner for NPI 1003000111 and whose specialty code is 207Q00000X; NPI 1003000555 is not returned.
Basis: R-PRVDIR01-ADD-04, Q-008, ADR-006, modern/contracts/openapi/directory-service.yaml#searchPractitionerRoles (specialty = taxonomy code), OQ-D5

### AC-5 Only ACTIVE providers appear and reinstatement brings them back
Given NPI 1003000333 (type FA) is suspended (S) and NPI 1003000111 (type IN) is pending (P)
When anyone searches /fhir/Practitioner, /fhir/Organization and /fhir/PractitionerRole for them, and the suspended provider is later reinstated (ProviderStatusChanged from S to A)
Then before the reinstatement every Bundle has total 0 for them (no error); after it, NPI 1003000333 is returned by GET /fhir/Organization and NPI 1003000111, still pending, is not returned by any call.
Basis: R-PRVDIR01-ADD-01, R-PRVDIR01-cbl-00004, R-PRVDIR01-cbl-00018, D-006, Q-008, modern/docs/ux/journeys.md#J5

### AC-6 The FHIR API is read-only
Given an ACTIVE Practitioner for NPI 1003000111 exists
When a caller sends POST, PUT or DELETE to /fhir/Practitioner
Then each call is rejected with a 4xx status (never 2xx), and a following GET /fhir/Practitioner with identifier=http://hl7.org/fhir/sid/us-npi|1003000111 returns the unchanged resource.
Basis: ADR-006 (read-only), modern/contracts/openapi/directory-service.yaml (only GET is defined under /fhir)

### AC-7 Resources are valid FHIR R4
Given ACTIVE providers of type IN (NPI 1003000111), GR (NPI 1003000444) and FA (NPI 1003000333) exist
When the Practitioner, Organization and PractitionerRole searches are called and every returned Bundle is checked with the HAPI FHIR validator against FHIR R4
Then the validator reports no errors for any Bundle or resource.
Basis: pem-domain skill section 7 (validate with the HAPI FHIR validator), ADR-006

### AC-8 A malformed identifier search is rejected
Given any ACTIVE provider exists
When anyone calls GET /fhir/Practitioner with identifier=http://hl7.org/fhir/sid/us-npi|ABC (not a 10-digit NPI)
Then the response is 400 application/problem+json.
Basis: modern/contracts/openapi/directory-service.yaml#searchPractitioners (response 400), modern/contracts/openapi/directory-service.yaml#/components/schemas/DirectoryEntry (NPI pattern of 10 digits), OQ-D12

## Out of scope
- FHIR Location resources and any address, phone or network data: not held by the legacy master (Q-008, ADR-006)
- A PractitionerRole link to an employing organisation: the legacy master holds none (OQ-D5)
- Write operations, FHIR paging links and search parameters beyond those in the contract
- The simple directory search (dir-search) and the legacy DIROUT file (dir-extract)

## Legacy trace (agent-owned, do not edit)
Rules: R-PRVDIR01-ADD-04
Related rules: R-PRVDIR01-ADD-01, R-PRVDIR01-ADD-02, R-PRVDIR01-cbl-00004, R-PRVDIR01-cbl-00018, R-PRVDIR01-cbl-00019, R-PRVDIR01-cbl-00020, R-PRVDIR01-cbl-00021
Programs: PRVDIR01: DIR-OUT-RECORD 47-53 and WRITE-DIR-ROW 83-90 (the legacy row has no taxonomy, address, phone or network data, so it cannot feed a PractitionerRole as it is); ACTIVE filter 78-79. FHIR is new (ADR-006). No JCL job runs PRVDIR01.
Decisions: D-028 FIX: compose the name from the name fields (individual: first + last; organisation: legal name); D-008 PRESERVE: keep IN/GR/FA plus a mapping table; D-006 A new SUSPENDED status (suspended providers are not offered)

## Technical notes (agent-owned)
Service: directory-service. Endpoints/events: GET /fhir/Practitioner, GET /fhir/Organization, GET /fhir/PractitionerRole. Roles: PUBLIC.
