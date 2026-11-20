---
key: MAM-46
localId: L-S025
epic: MAM-4
service: enrollment-service
type: Story
title: "Submit an enrolment application online and validate it (E101 to E104)"
slug: enr-submit
specVersion: 1
descriptionSha256: eef31894f77bc9a37756ff52eb32c7fb9a6171b213349951ccb5f3fbd7eecb2d
jiraUpdated: 2026-11-18T09:17:04.330-0600
rules: [R-PRVENR01-ADD-04, R-PRVENR01-ADD-05, R-PRVENR01-cbl-00003, R-PRVENR01-cbl-00004, R-PRVENR01-cbl-00005, R-PRVENR01-cbl-00024, R-PRVENR01-cbl-00025, R-PRVENR01-cbl-00028, R-PRVENR01-cbl-00029, R-PRVENR01-cbl-00030, R-PRVENR01-cbl-00032, R-PRVENR01-cbl-00034, R-PRVENR01-cbl-00035, R-PRVENR01-cbl-00039, R-PRVENR01-cbl-00049, R-PRVENR01-cbl-00050, R-PRVENR01-cbl-00052, R-PRVENR01-cbl-00053, R-PRVENR01-cbl-00054, R-PRVENR01-cbl-00055]
decisions: [D-001, D-008, D-016, D-019, D-023]
dependsOn: [MAM-31]
mainJourney: true
labels: [svc-enrollment-service, kag-L-S025, agentic, decision-D-001, decision-D-008, decision-D-016, decision-D-019, decision-D-023, slice-main-journey]
builtCommit: pending-user-commit
testsSha256: 1239d8e44331c8c7dfac82b43b7cab6b3e0832be22e95a9f20b47b8b05bea157
---
## Business requirement
A provider, or an analyst acting for a provider, can apply for Medicaid enrolment online. Before anything else happens the system checks that the application names the provider's identification number, that the provider type is individual, group or facility, that a specialty (taxonomy) code is given, and that no application for the same identification number has already been accepted. Every failed check is reported to the applicant once, with its legacy error code. A rejected application is kept for review but never becomes a provider. An accepted application is passed on so that the provider can be registered as pending and then screened. Codes used by the old provider feeds are translated to the three official types.

## Acceptance criteria
### AC-1 A valid application is accepted
Given A PROVIDER user is signed in, no application exists yet for NPI 1003000111, and the user sends an application with NPI 1003000111, the synthetic Medicaid ID M00000111, providerType IN, taxonomy 207Q00000X and individual details (lastName RIVERA, firstName MARIA, licenseNo MD12345, licenseState TX)
When POST /api/v1/applications is called
Then 201 with an Application whose status is VALIDATED, source is PORTAL, errors are empty and providerType is IN; exactly one ApplicationValidated event is published with subject 1003000111 carrying applicationId, npi 1003000111, medicaidId M00000111, providerType IN, taxonomy 207Q00000X and the individual details; no ApplicationRejected event is published
Basis: R-PRVENR01-cbl-00024, R-PRVENR01-cbl-00030, R-PRVENR01-cbl-00039, R-PRVENR01-cbl-00007, D-001, D-019, modern/docs/ux/journeys.md#J1, modern/contracts/asyncapi/events.yaml#ApplicationValidated

### AC-2 A missing or malformed NPI fails with E101
Given A PROVIDER user is signed in and sends an otherwise valid application whose npi is an empty string, and a second one whose npi is 12345 (not 10 digits)
When POST /api/v1/applications is called with each request
Then Each call returns 422 problem+json whose errors list has exactly one entry with code E101 and field npi (the full four-character code, not cut to E1); the application is stored with status REJECTED and that single error, and GET /api/v1/applications?status=REJECTED shows it to the same user; no ApplicationValidated event and no ApplicationRejected event is published, because an event needs a 10-digit NPI as its subject and key
Basis: R-PRVENR01-cbl-00003, R-PRVENR01-cbl-00024, R-PRVENR01-cbl-00025, R-PRVENR01-cbl-00054, R-PRVENR01-ADD-05, D-001, D-016, Q-B1, Q-B5, Q-B6

### AC-3 An invalid provider type fails with E102
Given A PROVIDER user sends an otherwise valid application for NPI 1003000222 with providerType XX, a code that is not IN, GR or FA and is not in the provider-type mapping table
When POST /api/v1/applications is called
Then 422 problem+json with exactly one error, code E102 on field providerType; the application is stored as REJECTED; exactly one ApplicationRejected event is published with subject 1003000222 and errors [E102 on providerType]; no ApplicationValidated event is published. Separate otherwise valid applications for NPI 1003000111 with providerType IN, NPI 1003000444 with providerType GR and NPI 1003000777 with providerType FA (organisation details given for GR and FA) each return 201 with status VALIDATED
Basis: R-PRVENR01-cbl-00004, R-PRVENR01-cbl-00029, R-PRVENR01-cbl-00030, R-PRVENR01-cbl-00053, R-PRVENR01-ADD-05, D-001, D-016, Q-B1

### AC-4 A missing taxonomy fails with E104
Given No application exists yet for NPI 1003000111, and a PROVIDER user leaves the taxonomy empty in an otherwise valid application for that NPI (journey J2)
When POST /api/v1/applications is called
Then 422 problem+json with exactly one error, code E104 on field taxonomy; the application is stored as REJECTED with that one error; exactly one ApplicationRejected event is published with subject 1003000111 and errors [E104 on taxonomy]; no ApplicationValidated event is published, so nothing is registered
Basis: R-PRVENR01-cbl-00005, R-PRVENR01-cbl-00035, R-PRVENR01-cbl-00055, R-PRVENR01-ADD-05, D-001, D-016, modern/docs/ux/journeys.md#J2, Q-B1

### AC-5 A duplicate NPI fails with E103
Given An application for NPI 1003000111 is already VALIDATED and a PROVIDER user submits another otherwise valid application for the same NPI
When POST /api/v1/applications is called
Then 422 problem+json with exactly one error, code E103 on field npi; the new application is stored as REJECTED and exactly one ApplicationRejected event is published for it; the first application is still VALIDATED and only one ApplicationValidated event exists for that NPI. A new application for an NPI whose earlier application was only REJECTED is accepted (VALIDATED) and does not fail E103
Basis: R-PRVENR01-ADD-04, R-PRVENR01-cbl-00008, R-PRVENR01-cbl-00051, R-PRVENR01-cbl-00058, D-016, D-001, Q-B3, modern/docs/architecture/overview.md (main flow 1)

### AC-6 Several failed checks give one error each, and a rejected application is never registered
Given A PROVIDER user sends an application with a valid NPI 1003000333 (no earlier application), providerType XX and an empty taxonomy
When POST /api/v1/applications is called
Then 422 problem+json with exactly two errors, one E102 on providerType and one E104 on taxonomy, each listed once; exactly one REJECTED application is stored (not two); exactly one ApplicationRejected event is published carrying both errors; no ApplicationValidated event is published, so the provider is never registered and can never reach approval
Basis: R-PRVENR01-cbl-00028, R-PRVENR01-cbl-00032, R-PRVENR01-cbl-00034, R-PRVENR01-cbl-00049, R-PRVENR01-cbl-00050, R-PRVENR01-cbl-00052, D-001, D-016, D-019, Q-B1, modern/contracts/openapi/enrollment-service.yaml#Application (errors: exactly one error per failed check)

### AC-7 Feed provider-type codes are mapped to IN, GR or FA
Given The provider-type mapping table holds IND to IN, GRP to GR and HOSP to FA, and a PROVIDER user sends otherwise valid applications for NPI 1003000111 with providerType IND, NPI 1003000444 with providerType GRP (organisation details given) and NPI 1003000777 with providerType HOSP, and one for NPI 1003000888 with providerType ZZZ, which is neither IN, GR or FA nor in the table
When POST /api/v1/applications is called for each
Then The first three return 201 with status VALIDATED, and both the Application and its ApplicationValidated event show providerType IN, GR and FA respectively; the ZZZ application returns 422 with exactly one error, code E102 on providerType
Basis: D-008, Q-B7, aws-transform/test-data/providers.txt, modern/contracts/openapi/enrollment-service.yaml#ApplicationRequest, modern/docs/architecture/data-model/enrollment-service.sql (provider_type_mapping)

### AC-8 Only PROVIDER and ANALYST may submit
Given Tokens for PROVIDER, ANALYST, SUPERVISOR, ADMIN and SYSTEM, and a request without a token
When POST /api/v1/applications is called with a valid application
Then PROVIDER and ANALYST get 201; SUPERVISOR, ADMIN and SYSTEM get 403 problem+json; the call without a token gets 401; a body that is not valid JSON or lacks a required property gets 400 problem+json and nothing is stored
Basis: D-023, ADR-007, modern/docs/architecture/nfr.md, modern/contracts/openapi/enrollment-service.yaml#submitApplication

## Out of scope
- Registering the provider as PENDING / UNSCREENED and emitting ProviderRegistered (prv-register)
- Screening, risk tier and approval (scr-*, rsk-*, prv-approve)
- The portal form and its linked error summary (ui-portal-apply)
- The 'Application rejected' notice (ntf-send consumes ApplicationRejected)
- Checks on names, licence or owner: the legacy checks only NPI, type and taxonomy; licences and owners are screened later
- The legacy APPINPUT file load (enr-intake-run)
- Submit response-time target (nfr-performance)

## Legacy trace (agent-owned, do not edit)
Rules: R-PRVENR01-ADD-04, R-PRVENR01-ADD-05, R-PRVENR01-cbl-00003, R-PRVENR01-cbl-00004, R-PRVENR01-cbl-00005, R-PRVENR01-cbl-00024, R-PRVENR01-cbl-00025, R-PRVENR01-cbl-00028, R-PRVENR01-cbl-00029, R-PRVENR01-cbl-00030, R-PRVENR01-cbl-00032, R-PRVENR01-cbl-00034, R-PRVENR01-cbl-00035, R-PRVENR01-cbl-00039, R-PRVENR01-cbl-00049, R-PRVENR01-cbl-00050, R-PRVENR01-cbl-00052, R-PRVENR01-cbl-00053, R-PRVENR01-cbl-00054, R-PRVENR01-cbl-00055
Related rules: R-PRVENR01-cbl-00007, R-PRVENR01-cbl-00008, R-PRVENR01-cbl-00051, R-PRVENR01-cbl-00058
Programs: PRVENR01 VALIDATE-APPLICATION 127-148 (NPI check 128-133, type check 134-139, taxonomy check 141-145, error code move 129), INVALID-APP 155-159, WRITE-PROVIDER-RECORD duplicate branch 160-166; copybooks PRVRECRD (type codes IN/GR/FA) and ERRCODES (E101-E104); job PRVDAY01 STEP010
Decisions: D-001 FIX: each invalid application is rejected with one error per failed check, stored once, announced once and never registered; D-008 PRESERVE: IN/GR/FA kept; feed codes translated through a mapping table; D-016 FIX: E101, E102, E103 and E104 are real validation results with full four-character codes; D-019 Analyst approval sets ACTIVE: a validated application only leads to a PENDING provider and a rejected one is never registered; D-023 FIX: role check on the endpoint (PROVIDER, ANALYST)

## Technical notes (agent-owned)
Service: enrollment-service. Endpoints/events: POST /api/v1/applications, ApplicationValidated, ApplicationRejected. Roles: PROVIDER, ANALYST.
