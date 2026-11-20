---
key: MAM-41
localId: L-S037
epic: MAM-6
service: risk-service
type: Story
title: "Assess a risk tier on request (risk assessment API)"
slug: rsk-assess-api
specVersion: 1
descriptionSha256: 097f1033dca0e6b1f6f620d0be8fd471b245decab9ea943df7b7b4f4cf4b2562
jiraUpdated: 2026-11-18T09:16:27.001-0600
rules: [R-PRVRSK01-ADD-03, R-PRVRSK01-cbl-00003, R-PRVRSK01-cbl-00018, R-PRVRSK01-cbl-00019, R-PRVRSK01-cbl-00020, R-PRVRSK01-cbl-00021]
decisions: [Q-005, D-002]
dependsOn: [MAM-35, MAM-38]
mainJourney: false
labels: [svc-risk-service, kag-L-S037, agentic, decision-Q-005, decision-D-002]
builtCommit: 2e20ead
testsSha256: 8ec462629094dbc22b647dbe8e3168dbb13ec67fbfda002c7b936f016594ee2d
---
## Business requirement
Analysts and other services sometimes need to know which risk level a specialty code would get without changing anything, for example to explain a level to a provider or to check a code. This story provides that question-and-answer service. It uses the same table as the real assignment, looks only at the first three characters of the code, and never records or announces anything.

## Acceptance criteria
### AC-1 Prefixes in the table are assessed with their tier
Given rule set version 1 is in force and an ANALYST token
When POST /api/v1/risk-assessments is called with taxonomy 332B00000X, 251E00000X, 341600000X, 291U00000X, 225100000X and 207Q00000X
Then each response is 200 with the taxonomy, ruleSetVersion 1 and tier H, H, M, M, M and L in that order
Basis: R-PRVRSK01-cbl-00004, R-PRVRSK01-cbl-00005, R-PRVRSK01-cbl-00006, Q-005, modern/contracts/openapi/risk-service.yaml#assessRisk

### AC-2 Only the first three characters count
Given rule set version 1 is in force
When POST /api/v1/risk-assessments is called with taxonomy 3320000000 and with 332ZZZZZZZ
Then both responses return tier H, because only the first three characters are used and the rest is ignored
Basis: R-PRVRSK01-cbl-00003, R-PRVRSK01-cbl-00018, R-PRVRSK01-cbl-00019, R-PRVRSK01-cbl-00020, R-PRVRSK01-cbl-00021

### AC-3 A blank or too-short taxonomy gets the default tier, as in the legacy
Given rule set version 1 is in force
When POST /api/v1/risk-assessments is called with taxonomy blank, and with taxonomy 33
Then each response is 200 with tier L (the default tier), not an error
Basis: R-PRVRSK01-ADD-03, R-PRVRSK01-cbl-00006 (any other prefix, including blank, is Limited), Q-005

### AC-4 A missing or too-long taxonomy is a bad request
Given an ANALYST token
When POST /api/v1/risk-assessments is called with no taxonomy, and with a taxonomy of 11 characters
Then each call returns 400 with a problem+json body
Basis: modern/contracts/openapi/risk-service.yaml#RiskAssessmentRequest (taxonomy required, maxLength 10)

### AC-5 The assessment has no side effect
Given provider 1003000111 with riskTier L in provider-service
When POST /api/v1/risk-assessments is called with taxonomy 332B00000X, once without and once with npi 1003000111
Then each response carries tier H, but no RiskTierAssigned is published and GET /api/v1/providers/1003000111 still shows riskTier L
Basis: modern/contracts/openapi/risk-service.yaml#assessRisk (compute the tier, no side effect), D-002, ADR-002

### AC-6 The answer uses the table in force
Given rule set version 2 is in force, with prefix 207 as M
When POST /api/v1/risk-assessments is called with taxonomy 207Q00000X
Then the response returns tier M and ruleSetVersion 2
Basis: modern/contracts/openapi/risk-service.yaml#RiskAssessment (ruleSetVersion), QUESTION C-9, modern/docs/architecture/data-model/risk-service.sql

### AC-7 Only analysts, supervisors and the system can ask
Given tokens for ANALYST, SUPERVISOR, SYSTEM, PROVIDER and ADMIN, and a request with no token
When each calls POST /api/v1/risk-assessments with taxonomy 207Q00000X
Then ANALYST, SUPERVISOR and SYSTEM receive 200, PROVIDER and ADMIN receive 403, and the request with no token receives 401
Basis: modern/docs/architecture/nfr.md#Security, D-023, ADR-007, modern/contracts/openapi/risk-service.yaml#assessRisk (x-roles)

## Out of scope
- Storing or announcing a tier (rsk-assign-tier)
- Changing the table (rsk-rules-admin)
- Validating that a taxonomy exists in a national code list (the legacy does not; R-PRVRSK01-ADD-03)

## Legacy trace (agent-owned, do not edit)
Rules: R-PRVRSK01-ADD-03, R-PRVRSK01-cbl-00003, R-PRVRSK01-cbl-00018, R-PRVRSK01-cbl-00019, R-PRVRSK01-cbl-00020, R-PRVRSK01-cbl-00021
Related rules: R-PRVRSK01-cbl-00004, R-PRVRSK01-cbl-00005, R-PRVRSK01-cbl-00006
Programs: source-code/BATCH/PRVRSK01.cbl: CLASSIFY-ONE 78-93 (taxonomy group is the first 3 characters of PRV-TAXONOMY, lines 79-80; EVALUATE 83-93)
Decisions: Q-005 Option A: the legacy prefix table is kept exactly, as data, with no meanings attached; D-002 FIX: the tier is produced by the event path (rsk-assign-tier); this endpoint never saves or announces

## Technical notes (agent-owned)
Service: risk-service. Endpoints/events: POST /api/v1/risk-assessments. Roles: ANALYST, SUPERVISOR, SYSTEM.
