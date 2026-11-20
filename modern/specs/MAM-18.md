---
key: MAM-18
localId: L-S029
epic: MAM-5
service: screening-service
type: Story
title: "Screen a newly registered provider against the licence board, OIG LEIE, SAM and SSA DMF (mocked sources)"
slug: scr-screen-sources
specVersion: 1
descriptionSha256: 3c7d9303966a28114e8dc69bcd0d111b79aad305263db4b9864300c6c1f03986
jiraUpdated: 2026-11-18T09:14:06.965-0600
rules: [R-PRVSCR01-ADD-01, R-PRVSCR01-cbl-00006, R-PRVSCR01-cbl-00007, R-PRVSCR01-cbl-00008, R-PRVSCR01-cbl-00009, R-PRVSCR01-cbl-00020, R-PRVSCR01-cbl-00021, R-PRVSCR01-cbl-00029, R-PRVSCR01-cbl-00032, R-PRVSCR01-cbl-00034, R-PRVSCR01-cbl-00038, R-PRVSCR01-cbl-00039, R-PRVSCR01-cbl-00043]
decisions: [D-005, D-016, D-010, D-014]
dependsOn: [MAM-17, MAM-37, MAM-47]
mainJourney: true
labels: [svc-screening-service, kag-L-S029, agentic, decision-D-005, decision-D-016, decision-D-010, decision-D-014, slice-main-journey]
builtCommit: 6e24725
testsSha256: 0657a95cd567cd8c9836339937f30fc53f80aa0525c4a3d72f8b7f77b20ddb30
---
## Business requirement
Before anyone approves an enrolment, the state must check that the provider holds a valid licence, is not on the federal list of excluded providers, is not barred from federal programmes and is not recorded as deceased. Today these four checks do nothing and every provider passes. After this story each check asks its source and records what the source answered, so a provider with an expired licence, an exclusion, a debarment or a death record is caught. The sources are simulated for now; the real services will be connected in a later release.

## Acceptance criteria
### AC-1 Screening starts when a provider is registered
Given provider-service has published ProviderRegistered for NPI 1003000111 (type IN, taxonomy 207Q00000X, enrollStatus P, screenFlag U) and provider-service GET /api/v1/providers/1003000111 returns licence MD12345 in TX
When screening-service receives the event
Then GET /api/v1/screenings?npi=1003000111 (ANALYST token) returns one screening with reason INITIAL that holds one result for each of LICBOARD, OIGLEIE, SAMGOV and SSADMF, and the licence-board mock's request log shows the lookup for licence MD12345 in TX (the values read from provider-service)
Basis: R-PRVSCR01-cbl-00020, R-PRVSCR01-cbl-00021, R-PRVSCR01-cbl-00032, D-005, D-014, ADR-002, QUESTION C-2, QUESTION C-4, modern/contracts/asyncapi/events.yaml#ProviderRegistered, modern/docs/architecture/overview.md#Main flows (2), modern/docs/ux/journeys.md#J1

### AC-2 A clean provider passes all four sources
Given NPI 1003000111 with licence MD12345 in TX, which the licence-board mock reports ACTIVE, and the LEIE, SAM and SSA DMF mocks return their clean answer for it
When the screening completes
Then the LICBOARD, OIGLEIE, SAMGOV and SSADMF results are all P, each with a verdictAt time and a non-blank detail, and the request log of each of the four mocks shows exactly one lookup for this provider (the checks really ask the sources)
Basis: R-PRVSCR01-ADD-01, R-PRVSCR01-cbl-00006, R-PRVSCR01-cbl-00029, R-PRVSCR01-cbl-00034, R-PRVSCR01-cbl-00039, R-PRVSCR01-cbl-00043, D-005, aws-transform/test-data/license_board.txt, modern/docs/ux/journeys.md#J1

### AC-3 An expired licence fails the licence source (E202)
Given an individual (IN) provider 1003000555 with licence PT99900 in TX, which the licence-board mock reports as EXPIRED
When the screening completes
Then the LICBOARD result is F with errorCode E202 and a detail stating that the licence is expired
Basis: R-PRVSCR01-cbl-00006, R-PRVSCR01-cbl-00029, D-016, D-005, aws-transform/test-data/license_board.txt, .claude/skills/pem-domain/SKILL.md#4 (E202 licence expired)

### AC-4 A provider on the OIG LEIE fails (E201)
Given an individual (IN) provider with NPI 1990000111 is registered, and the LEIE mock lists 1990000111 (as in the sample LEIE feed)
When the screening completes
Then the OIGLEIE result is F with errorCode E201 and a detail stating that the provider is excluded
Basis: R-PRVSCR01-cbl-00007, R-PRVSCR01-cbl-00034, D-016, D-005, aws-transform/test-data/leie.txt, .claude/skills/pem-domain/SKILL.md#4 (E201 excluded)

### AC-5 A debarred provider fails SAM (E205)
Given provider 1003000111, for whose name the SAM mock's hit stub returns status DEBARRED
When the screening completes
Then the SAMGOV result is F with errorCode E205 and a detail stating that the provider is debarred
Basis: R-PRVSCR01-cbl-00008, R-PRVSCR01-cbl-00038, R-PRVSCR01-cbl-00039, D-016, D-005, QUESTION C-2, aws-transform/test-data/sam.txt (DEBARRED in column 2 triggers a fail), .claude/skills/pem-domain/SKILL.md#4 (E205 debarred)

### AC-6 A deceased provider fails SSA DMF (E204)
Given provider 1003000111, which the SSA DMF mock's hit stub reports as deceased
When the screening completes
Then the SSADMF result is F with errorCode E204 and a detail stating that the provider is deceased
Basis: R-PRVSCR01-cbl-00009, R-PRVSCR01-cbl-00043, D-016, D-005, .claude/skills/pem-domain/SKILL.md#4 (E204 deceased), .claude/skills/synthetic-data/SKILL.md#5 (hit stub)

### AC-7 The same registration never creates two screenings
Given ProviderRegistered for NPI 1003000111 is delivered twice with the same event id
When both deliveries are processed
Then GET /api/v1/screenings?npi=1003000111 returns exactly one screening and each mock's request log shows one lookup for the provider
Basis: ADR-003, R-PRVSCR01-cbl-00021, .claude/skills/java-microservices/SKILL.md#7 (idempotent consumers)

## Out of scope
- Medicare reciprocity / PECOS check (dropped, D-010): PECOS is not a screening source
- An NPPES lookup (not one of the four legacy checks; question C-4)
- Overall verdict, per-source storage and the ScreeningCompleted event (scr-verdict)
- Owner screening (scr-owner); unknown licences, organisation providers and sources that fail or are slow (scr-screen-sources-2)
- Setting the provider's screen flag (provider-service, prv-screen-verdict)
- The batch run and the SCRNOUT file (scr-batch-run)
- Connecting the real licence boards, LEIE, SAM, NPPES or SSA DMF services

## Legacy trace (agent-owned, do not edit)
Rules: R-PRVSCR01-ADD-01, R-PRVSCR01-cbl-00006, R-PRVSCR01-cbl-00007, R-PRVSCR01-cbl-00008, R-PRVSCR01-cbl-00009, R-PRVSCR01-cbl-00020, R-PRVSCR01-cbl-00021, R-PRVSCR01-cbl-00029, R-PRVSCR01-cbl-00032, R-PRVSCR01-cbl-00034, R-PRVSCR01-cbl-00038, R-PRVSCR01-cbl-00039, R-PRVSCR01-cbl-00043
Programs: source-code/BATCH/PRVSCR01.cbl: SCREEN-NEXT-PROVIDER 90-91, SCREEN-ONE-PROVIDER 101-104, CHECK-LICENSE 116-119, CHECK-LEIE-EXCLUSION 121-124, CHECK-SAM-DEBARMENT 126-128, CHECK-SSA-DMF 130-132 (all four hard-code P at 118, 123, 127, 131). Job step: source-code/JCL/PRVDAY01.jcl STEP030
Decisions: D-005 REPLACE: the four stubs become real lookups against mocked sources; D-016 FIX: E201, E202, E204 and E205 are the error codes of the failing source; D-010 DROP: the Medicare reciprocity check is not built; D-014 FIX: provider-service is the single owner of provider data; screening-service reads licence and name through provider-service's API (ADR-002, question C-2)

## Technical notes (agent-owned)
Service: screening-service. Endpoints/events: GET /api/v1/screenings, ProviderRegistered. Roles: ANALYST, SUPERVISOR, ADMIN, SYSTEM.
