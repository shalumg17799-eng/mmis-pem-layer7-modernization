---
key: MAM-23
localId: L-S018
epic: MAM-3
service: provider-service
type: Story
title: "Record the assigned risk tier on the provider"
slug: prv-risk-tier
specVersion: 1
descriptionSha256: b2eb24b828448a14abbd864b28aa96c7a8f04ab590b188b4525b21d694ad0441
jiraUpdated: 2026-11-18T09:14:41.115-0600
rules: [R-PRVRSK01-cbl-00010, R-PRVRSK01-cbl-00048]
decisions: [D-002, D-003, D-014]
dependsOn: [MAM-17]
mainJourney: true
labels: [svc-provider-service, kag-L-S018, agentic, decision-D-002, decision-D-003, decision-D-014, slice-main-journey]
builtCommit: pending-user-commit
testsSha256: 845d7b375bb1f59d64e92cbc674f273ee61063e035d158469c0ff9b60c22e806
---
## Business requirement
The risk level assigned to a provider (limited, moderate or high) must be saved on the provider's record for every provider, and replaced when a new level is assigned, so that analysts and the directory see it. Assigning a level changes nothing else on the record; in particular it does not move the date by which the provider must be revalidated.

## Acceptance criteria
### AC-1 The assigned tier is saved on the provider
Given provider 1003000111 is registered, screenFlag P, riskTier null
When provider-service consumes a RiskTierAssigned event for 1003000111 with tier L (taxonomy 207Q00000X, ruleSetVersion 1)
Then GET /api/v1/providers/1003000111 returns riskTier L
Basis: R-PRVRSK01-cbl-00010, R-PRVRSK01-cbl-00048, D-002, modern/docs/ux/journeys.md#J1

### AC-2 The tier is saved for every provider, not only the first
Given providers 1003000111 (taxonomy 207Q00000X), 1003000222 (taxonomy 332B00000X) and 1003000888 (taxonomy 341600000X) are registered with riskTier null
When RiskTierAssigned events arrive for them with tiers L, H and M
Then GET /api/v1/providers/{npi} returns riskTier L for 1003000111, H for 1003000222 and M for 1003000888 (provider-service stores the tier it receives and does not recompute it)
Basis: D-002, R-PRVRSK01-cbl-00010, R-PRVRSK01-cbl-00048, .claude/skills/pem-domain/SKILL.md sections 2 and 3 (tiers L, M, H; prefixes 332 High, 341 Moderate)

### AC-3 A new tier replaces the old one
Given provider 1003000111 has riskTier L
When a RiskTierAssigned event with tier M arrives
Then GET /api/v1/providers/1003000111 returns riskTier M
Basis: R-PRVRSK01-cbl-00048, R-PRVRSK01-cbl-00010

### AC-4 Assigning a tier changes nothing else, in particular not the due date
Given provider 1003000111 is ACTIVE, screenFlag P, effectiveDate 2026-11-18 and revalidationDueDate 2031-11-18
When a RiskTierAssigned event with tier M arrives
Then GET /api/v1/providers/1003000111 returns riskTier M while enrollStatus A, screenFlag P, effectiveDate 2026-11-18 and revalidationDueDate 2031-11-18 are unchanged, and no ProviderStatusChanged event is published
Basis: R-PRVRSK01-ADD-01, D-003 (the due date is set at approval and at completed revalidations, not by the risk step), modern/docs/architecture/overview.md (difference 5), R-PRVRSK01-cbl-00010

### AC-5 Arrival order does not matter
Given provider 1003000111 is registered and its screenFlag is still U (RiskTierAssigned and ScreeningCompleted travel on different topics)
When a RiskTierAssigned event with tier L arrives before the ScreeningCompleted event
Then GET /api/v1/providers/1003000111 returns riskTier L and screenFlag U, and the later ScreeningCompleted PASS then sets screenFlag P without removing the tier
Basis: modern/contracts/asyncapi/events.yaml (pem.screening.events and pem.risk.events are separate topics), ADR-003

### AC-6 A redelivered tier event is applied once
Given the RiskTierAssigned event with id E3 was already processed
When E3 is delivered again
Then GET /api/v1/providers/1003000111 shows the version did not increase a second time and the tier is unchanged
Basis: ADR-003, .claude/skills/java-microservices/SKILL.md section 7

## Out of scope
- Choosing the tier from the taxonomy prefix and keeping the prefix table as data (risk-service: rsk-assign-tier, rsk-rules-admin; Q-005).
- Setting or resetting the revalidation due date: moved to approval and completed revalidation (prv-approve, prv-approve-2; D-003).
- The legacy rule that re-tiered every provider on every daily run (R-PRVRSK01-ADD-01): not reproduced.

## Legacy trace (agent-owned, do not edit)
Rules: R-PRVRSK01-cbl-00010, R-PRVRSK01-cbl-00048
Related rules: R-PRVRSK01-ADD-01
Programs: PRVRSK01: source-code/BATCH/PRVRSK01.cbl:95-97 (CLASSIFY-ONE: PERFORM APPLY-TIER-PARA, SET-REVALIDATION-DATE, REWRITE at 97); 70-97 (no status filter, ADD-01). Job step: PRVDAY01 STEP040 (source-code/JCL/PRVDAY01.jcl).
Decisions: D-002 FIX: the risk tier is saved for every provider; D-003 FIX: the due date is not set by the risk step; D-014 FIX: provider-service is the only writer of the risk tier

## Technical notes (agent-owned)
Service: provider-service. Endpoints/events: RiskTierAssigned. Roles: none.
