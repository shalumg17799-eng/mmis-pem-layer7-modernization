---
key: MAM-38
localId: L-S036
epic: MAM-6
service: risk-service
type: Story
title: "View and change the taxonomy-prefix risk table (admin)"
slug: rsk-rules-admin
specVersion: 1
descriptionSha256: b0d5a2908690743d6c22cd1c3673650b03b776c424f2a780600a9b71382ffb26
jiraUpdated: 2026-11-18T09:16:12.461-0600
rules: []
decisions: [Q-005, D-023]
dependsOn: [MAM-35]
mainJourney: false
labels: [svc-risk-service, kag-L-S036, agentic, decision-Q-005, decision-D-023]
builtCommit:
testsSha256:
---
## Business requirement
Which specialty codes are high, moderate or limited risk is business knowledge that will change over time, and the business must be able to see the table and change it without a software release. Staff can read the current table, an administrator can save a changed table as a new version, and each assignment records which version it used. At the start the table holds exactly the values the legacy job used.

## Acceptance criteria
### AC-1 Staff can read the current table
Given the initial rule set
When an ANALYST, SUPERVISOR or ADMIN calls GET /api/v1/risk-rules
Then the response is 200 with version 1, defaultTier L and exactly the rules 332 H, 251 H, 341 M, 291 M and 225 M
Basis: Q-005, R-PRVRSK01-cbl-00004, R-PRVRSK01-cbl-00005, R-PRVRSK01-cbl-00006, .claude/skills/pem-domain/SKILL.md#3, modern/contracts/openapi/risk-service.yaml#RiskRuleSet, modern/docs/ux/wireframes/console-risk-rules.html

### AC-2 An administrator saves a changed table as a new version
Given the current version is 1 and an ADMIN token
When the ADMIN calls PUT /api/v1/risk-rules with version 1, defaultTier L and the legacy rules plus prefix 207 as M
Then the response is 200 with version 2, GET /api/v1/risk-rules then returns version 2 including prefix 207, and the next ScreeningCompleted PASS for taxonomy 207Q00000X produces a RiskTierAssigned with tier M and ruleSetVersion 2
Basis: QUESTION C-9, modern/contracts/openapi/risk-service.yaml#replaceRiskRules, modern/docs/ux/wireframes/console-risk-rules.html (Save as new version), .claude/skills/pem-domain/SKILL.md#3 (data-driven table the business can change)

### AC-3 A stale or inconsistent table is refused
Given the current version is 2
When an ADMIN calls PUT /api/v1/risk-rules with version 1, and then with version 2 and a table that lists prefix 332 twice
Then both calls return 422 with a problem+json body and GET /api/v1/risk-rules still returns version 2 unchanged
Basis: QUESTION C-9, modern/contracts/openapi/risk-service.yaml#replaceRiskRules (422 business validation), modern/docs/architecture/data-model/risk-service.sql (a prefix is unique within a rule set)

### AC-4 Malformed rules are rejected
Given an ADMIN token
When PUT /api/v1/risk-rules is called with a 2-character prefix, a lower-case prefix, a tier X, or no defaultTier
Then each call returns 400 with a problem+json body and GET /api/v1/risk-rules returns the table unchanged
Basis: modern/contracts/openapi/risk-service.yaml#RiskRule (taxonomyPrefix pattern ^[0-9A-Z]{3}$, tier L, M or H), modern/contracts/openapi/risk-service.yaml#RiskRuleSet (required version, defaultTier, rules)

### AC-5 Saving a new version re-tiers nobody
Given NPI 1003000111 was assigned tier L under version 1
When an ADMIN saves version 2 in which prefix 207 is M
Then no RiskTierAssigned is published because of the change, so the tier announced for 1003000111 stays L until a new passed screening of 1003000111 is assessed
Basis: QUESTION C-9, modern/docs/architecture/data-model/risk-service.sql (each assessment keeps its rule_set_version)

### AC-6 Only an administrator can change the table
Given tokens for ADMIN, SUPERVISOR, ANALYST and PROVIDER, and a request with no token
When each calls PUT /api/v1/risk-rules, and then GET /api/v1/risk-rules
Then PUT is accepted only for ADMIN (SUPERVISOR, ANALYST and PROVIDER receive 403); GET is accepted for ANALYST, SUPERVISOR and ADMIN (PROVIDER receives 403); no token receives 401 on both
Basis: modern/docs/architecture/nfr.md#Security, D-023, ADR-007, modern/docs/ux/screen-inventory.md#console risk-rules (ADMIN), modern/contracts/openapi/risk-service.yaml (x-roles)

## Out of scope
- The console page (ui-console-risk-rules)
- Who changed the table and when (the data model has no audit field for rule changes)
- Meanings or labels for the prefixes (Q-005: none attached)
- Re-tiering existing providers after a change
- A second-person approval of a rule change
- A future effectiveFrom date (question C-9: a saved version applies from the moment it is saved)

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Related rules: R-PRVRSK01-cbl-00004, R-PRVRSK01-cbl-00005, R-PRVRSK01-cbl-00006
Programs: source-code/BATCH/PRVRSK01.cbl: CLASSIFY-ONE EVALUATE 83-93 (prefix lists 332/251, 341/291/225, WHEN OTHER); the lists become data, not code
Decisions: Q-005 Option A: the legacy prefix table is kept exactly, as data, with no meanings attached; D-023 FIX: role-based access; the table is changed by ADMIN only

## Technical notes (agent-owned)
Service: risk-service. Endpoints/events: GET /api/v1/risk-rules, PUT /api/v1/risk-rules, RiskTierAssigned. Roles: ADMIN, ANALYST, SUPERVISOR.
