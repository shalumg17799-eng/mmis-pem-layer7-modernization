---
key: MAM-22
localId: L-S031
epic: MAM-5
service: screening-service
type: Story
title: "Screen the owner of a group or facility (E203 owner excluded)"
slug: scr-owner
specVersion: 1
descriptionSha256: 81fed412b164b6213458c5222d4123ee5a5bffe97667c9c65adf09b3675cea88
jiraUpdated: 2026-11-18T09:14:36.601-0600
rules: [R-PRVSCR01-cbl-00033]
decisions: [D-016, D-005]
dependsOn: [MAM-18, MAM-25]
mainJourney: false
labels: [svc-screening-service, kag-L-S031, agentic, decision-D-016, decision-D-005]
builtCommit: 6e24725
testsSha256: 79458fd0a52187da982d662e798a2057493a35f407d04451de858c6ab0fd0265
---
## Business requirement
A group or facility is only as trustworthy as the person who owns it. When the provider is an organisation, the state must also check its owner against the federal list of excluded providers, and a provider whose owner is excluded must not pass screening. This story adds the owner check to the exclusion source, with its own error so that the analyst can see that the owner, not the provider itself, is the problem.

## Acceptance criteria
### AC-1 An excluded owner fails the group (E203)
Given a group (GR) provider 1003000444 with owner NPI 1990000111, and the LEIE mock lists 1990000111 but not 1003000444
When the screening completes
Then the OIGLEIE result is F with errorCode E203 and a detail naming owner NPI 1990000111, the other three sources are still recorded, the screening result is FAIL, and GET /api/v1/screenings?result=FAIL lists it
Basis: R-PRVSCR01-cbl-00033, D-016, D-005, QUESTION C-5, modern/docs/ux/journeys.md#J3, aws-transform/test-data/leie.txt, aws-transform/test-data/providers.txt

### AC-2 A clean owner passes
Given a group (GR) provider 1003000222 with owner NPI 1990000999 (as in the sample feed), neither of which is on the LEIE mock
When the screening completes
Then the OIGLEIE result is P and the LEIE mock's request log shows two lookups, one for 1003000222 and one for 1990000999
Basis: R-PRVSCR01-cbl-00033, R-PRVSCR01-cbl-00034, QUESTION C-5, aws-transform/test-data/providers.txt

### AC-3 Providers without an owner NPI are checked on their own NPI only
Given the individual (IN) provider 1003000111, and the facility (FA) provider 1003000333 whose owner NPI is blank
When each is screened
Then for each, the LEIE mock's request log shows exactly one lookup (the provider's own NPI) and the OIGLEIE result carries no E203
Basis: R-PRVSCR01-cbl-00033, modern/docs/analysis/data-layouts.md#PRVRECRD (owner NPI exists only in the organisation view), modern/contracts/openapi/provider-service.yaml (ownerNpi only under the organisation data)

### AC-4 A provider and its owner both on the LEIE report E201
Given a group (GR) provider 1003000444 whose own NPI and whose owner NPI 1990000111 are both listed by the LEIE mock
When the screening completes
Then the single OIGLEIE result is F with errorCode E201 and a detail that names both 1003000444 and owner 1990000111
Basis: QUESTION C-6, D-016, R-PRVSCR01-cbl-00033, modern/docs/architecture/data-model/screening-service.sql (one result per source: UNIQUE screening_id, source)

### AC-5 An owner lookup that fails is recorded as not answered
Given a group (GR) provider 1003000222 with owner NPI 1990000999, and the LEIE mock's error stub (HTTP 500) applies to the lookup of 1990000999
When the screening runs
Then after one retry the OIGLEIE result is C with no errorCode and the screening result is FAIL
Basis: QUESTION C-1, R-PRVSCR01-cbl-00010, D-024

## Out of scope
- Owners with 5 percent or more listed in provider-service's ownership table (not exposed by any API in 1.0.0; question C-5)
- Checking owners against SAM, the licence board or SSA DMF (the legacy comment and E203 'owner excluded' concern the LEIE only)
- An ownership percentage threshold (the legacy master holds one owner NPI and no percentage)

## Legacy trace (agent-owned, do not edit)
Rules: R-PRVSCR01-cbl-00033
Related rules: R-PRVSCR01-cbl-00010, R-PRVSCR01-cbl-00034
Programs: source-code/BATCH/PRVSCR01.cbl: CHECK-LEIE-EXCLUSION 121-124 (the comment at 122 says it matches NPI and 5%+ owners; the code only sets P). Owner NPI is PRV-OWNER-NPI, bytes 121-130 of source-code/COPYBOOKS/PRVRECRD.cpy
Decisions: D-016 FIX: E203 owner excluded is implemented as a screening result; D-005 REPLACE: the LEIE stub becomes a real lookup of the provider and its owner

## Technical notes (agent-owned)
Service: screening-service. Endpoints/events: GET /api/v1/screenings, ScreeningCompleted. Roles: ANALYST, SUPERVISOR, ADMIN, SYSTEM.
