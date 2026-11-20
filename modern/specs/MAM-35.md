---
key: MAM-35
localId: L-S035
epic: MAM-6
service: risk-service
type: Story
title: "Assign the risk tier from the taxonomy prefix after a passed screening"
slug: rsk-assign-tier
specVersion: 1
descriptionSha256: 696ec61b1f00fd27c3b72ddc386eb06e0bbd010706c053b7dee951a111425cf8
jiraUpdated: 2026-11-18T09:15:58.129-0600
rules: [R-PRVRSK01-ADD-04, R-PRVRSK01-ADD-05, R-PRVRSK01-ADD-06, R-PRVRSK01-ADD-07, R-PRVRSK01-cbl-00001, R-PRVRSK01-cbl-00002, R-PRVRSK01-cbl-00004, R-PRVRSK01-cbl-00005, R-PRVRSK01-cbl-00006, R-PRVRSK01-cbl-00007, R-PRVRSK01-cbl-00011, R-PRVRSK01-cbl-00012, R-PRVRSK01-cbl-00013, R-PRVRSK01-cbl-00014, R-PRVRSK01-cbl-00015, R-PRVRSK01-cbl-00016, R-PRVRSK01-cbl-00017, R-PRVRSK01-cbl-00022, R-PRVRSK01-cbl-00023, R-PRVRSK01-cbl-00024, R-PRVRSK01-cbl-00025, R-PRVRSK01-cbl-00026, R-PRVRSK01-cbl-00027, R-PRVRSK01-cbl-00028, R-PRVRSK01-cbl-00029, R-PRVRSK01-cbl-00030, R-PRVRSK01-cbl-00031, R-PRVRSK01-cbl-00032, R-PRVRSK01-cbl-00033, R-PRVRSK01-cbl-00034, R-PRVRSK01-cbl-00035, R-PRVRSK01-cbl-00036, R-PRVRSK01-cbl-00037, R-PRVRSK01-cbl-00038]
decisions: [D-002, Q-005, D-024]
dependsOn: [MAM-25]
mainJourney: true
labels: [svc-risk-service, kag-L-S035, agentic, decision-D-002, decision-Q-005, decision-D-024, slice-main-journey]
builtCommit: pending-user-commit
testsSha256: c52765b53bc6c703ba4e17932c36a5cc427bc7e435786174c16dbda487828f99
---
## Business requirement
Every enrolled provider has a risk level (limited, moderate or high) that decides how closely the state watches it. Once a provider has passed screening, the system must work out that level from the first three characters of the provider's specialty code, using one table, and announce it so that the provider record keeps it, the analyst sees it before approving and the directory can show it. The legacy job probably never saved the level it worked out, so this story makes sure a level is always produced and announced, and always from the same table. A provider that failed screening gets no level because it will not be approved.

## Acceptance criteria
### AC-1 A passed screening gets a risk tier, which is announced for saving
Given ScreeningCompleted with result PASS for NPI 1003000111 and taxonomy 207Q00000X, and the rule set in force is version 1 (the legacy table)
When risk-service processes the event
Then exactly one RiskTierAssigned is published to pem.risk.events with message key 1003000111 and data npi 1003000111, taxonomy 207Q00000X, tier L and ruleSetVersion 1
Basis: D-002, R-PRVRSK01-ADD-04, R-PRVRSK01-cbl-00006, R-PRVRSK01-cbl-00037, modern/contracts/asyncapi/events.yaml#RiskTierAssigned, modern/docs/architecture/overview.md#Main flows (2: provider-service saves the tier), modern/docs/ux/journeys.md#J1

### AC-2 Prefixes 332 and 251 are High
Given ScreeningCompleted PASS for a provider whose taxonomy starts with 332 (for example 332B00000X), and another whose taxonomy starts with 251 (for example 251E00000X)
When risk-service processes the events
Then each RiskTierAssigned carries tier H
Basis: R-PRVRSK01-cbl-00004, R-PRVRSK01-cbl-00022, R-PRVRSK01-cbl-00023, R-PRVRSK01-cbl-00024, R-PRVRSK01-cbl-00035, Q-005, .claude/skills/pem-domain/SKILL.md#3, 42 CFR 455.450 (summary, unverified)

### AC-3 Prefixes 341, 291 and 225 are Moderate
Given ScreeningCompleted PASS for taxonomies starting with 341 (for example 341600000X), 291 (291U00000X) and 225 (225100000X)
When risk-service processes the events
Then each RiskTierAssigned carries tier M
Basis: R-PRVRSK01-cbl-00005, R-PRVRSK01-cbl-00026, R-PRVRSK01-cbl-00027, R-PRVRSK01-cbl-00028, R-PRVRSK01-cbl-00029, R-PRVRSK01-cbl-00030, R-PRVRSK01-cbl-00036, Q-005, .claude/skills/pem-domain/SKILL.md#3, 42 CFR 455.450 (summary, unverified)

### AC-4 Every other prefix is Limited
Given ScreeningCompleted PASS for taxonomy 207Q00000X, and for taxonomy 999Z00000X, whose first three characters are in no rule
When risk-service processes the events
Then each RiskTierAssigned carries the rule set's default tier L
Basis: R-PRVRSK01-cbl-00006, R-PRVRSK01-cbl-00025, R-PRVRSK01-cbl-00033, R-PRVRSK01-cbl-00034, R-PRVRSK01-cbl-00037, R-PRVRSK01-ADD-03, Q-005, modern/docs/ux/journeys.md#J1

### AC-5 Each assessment carries exactly one tier, and a later passed screening is assessed again
Given NPI 1003000111 (taxonomy 207Q00000X) already received RiskTierAssigned tier L from its INITIAL screening, and a ScreeningCompleted PASS with reason REVALIDATION then arrives for it
When risk-service processes the event
Then a second RiskTierAssigned is published for 1003000111 carrying exactly one tier (L) and the ruleSetVersion in force at that moment; no event ever carries more than one tier
Basis: R-PRVRSK01-cbl-00007, R-PRVRSK01-cbl-00038, R-PRVRSK01-cbl-00031, R-PRVRSK01-cbl-00032, D-002, QUESTION C-15, modern/docs/architecture/overview.md#Main flows (2: on PASS), modern/docs/architecture/data-model/risk-service.sql (risk_assessment keeps every tier assigned)

### AC-6 A failed screening gets no tier
Given ScreeningCompleted with result FAIL for NPI 1003000444
When risk-service processes the event
Then no RiskTierAssigned is published for 1003000444
Basis: modern/docs/architecture/overview.md#Main flows (2: on PASS), .claude/skills/pem-domain/SKILL.md#6 (risk tier assigned on PASS), modern/contracts/asyncapi/events.yaml#RiskTierAssigned (after a PASS screening)

### AC-7 Every passed screening is classified exactly once
Given ScreeningCompleted PASS events for 1003000111, 1003000222 and 1003000333, the one for 1003000222 delivered twice with the same event id
When all are processed
Then exactly one RiskTierAssigned is published for each of the three NPIs: none is skipped and the duplicate delivery publishes nothing more
Basis: R-PRVRSK01-cbl-00001, R-PRVRSK01-cbl-00002, R-PRVRSK01-cbl-00011, R-PRVRSK01-cbl-00012, R-PRVRSK01-cbl-00013, R-PRVRSK01-cbl-00014, R-PRVRSK01-cbl-00015, R-PRVRSK01-cbl-00016, R-PRVRSK01-cbl-00017, D-002, ADR-003

### AC-8 A failure to save is not silent, and assignments are counted
Given a database error occurs while storing the assessment for a ScreeningCompleted PASS event of NPI 1003000111
When the event is processed, and is later delivered again after the database is back
Then the failed attempt is written to the JSON log with the event's correlation id, no RiskTierAssigned is published for the failed attempt, exactly one RiskTierAssigned is published after the redelivery, and the Actuator metrics endpoint shows the count of tiers assigned increased by one
Basis: R-PRVRSK01-ADD-05, R-PRVRSK01-ADD-06, R-PRVRSK01-ADD-07, D-024, ADR-003, .claude/skills/java-microservices/SKILL.md#11 (Actuator metrics, JSON logs, correlation id)

## Out of scope
- Saving the tier on the provider record (provider-service, prv-risk-tier)
- The revalidation due date that PRVRSK01 also set (provider-service owns it, D-003)
- Attaching meanings to the prefixes (question Q-005: none attached)
- Changing the rule table (rsk-rules-admin) and the on-request assessment (rsk-assess-api)
- Assigning a tier to every provider on every daily run (legacy behaviour replaced: a tier is assigned on a PASS screening, overview flow 2; D-002, D-003)

## Legacy trace (agent-owned, do not edit)
Rules: R-PRVRSK01-ADD-04, R-PRVRSK01-ADD-05, R-PRVRSK01-ADD-06, R-PRVRSK01-ADD-07, R-PRVRSK01-cbl-00001, R-PRVRSK01-cbl-00002, R-PRVRSK01-cbl-00004, R-PRVRSK01-cbl-00005, R-PRVRSK01-cbl-00006, R-PRVRSK01-cbl-00007, R-PRVRSK01-cbl-00011, R-PRVRSK01-cbl-00012, R-PRVRSK01-cbl-00013, R-PRVRSK01-cbl-00014, R-PRVRSK01-cbl-00015, R-PRVRSK01-cbl-00016, R-PRVRSK01-cbl-00017, R-PRVRSK01-cbl-00022, R-PRVRSK01-cbl-00023, R-PRVRSK01-cbl-00024, R-PRVRSK01-cbl-00025, R-PRVRSK01-cbl-00026, R-PRVRSK01-cbl-00027, R-PRVRSK01-cbl-00028, R-PRVRSK01-cbl-00029, R-PRVRSK01-cbl-00030, R-PRVRSK01-cbl-00031, R-PRVRSK01-cbl-00032, R-PRVRSK01-cbl-00033, R-PRVRSK01-cbl-00034, R-PRVRSK01-cbl-00035, R-PRVRSK01-cbl-00036, R-PRVRSK01-cbl-00037, R-PRVRSK01-cbl-00038
Related rules: R-PRVRSK01-ADD-03
Programs: source-code/BATCH/PRVRSK01.cbl: MAIN-CONTROL 61-67, CLASSIFY-NEXT 69-76, CLASSIFY-ONE 78-97 (EVALUATE 83-93, ALTER at 86, 90, 92, REWRITE at 97), APPLY-TIER-PARA 103-104, LIMITED-TIER 106-108, MODERATE-TIER 109-111, HIGH-TIER 112-114, TIER-DONE 115-116. Job step: source-code/JCL/PRVDAY01.jcl STEP040, tier part only
Decisions: D-002 FIX: the risk tier is saved: every passed screening yields exactly one tier (no ALTER path), published in RiskTierAssigned for provider-service to save (overview flow 2); Q-005 Option A: the legacy prefix table is kept exactly, as data, with no meanings attached; D-024 FIX: explicit error handling; failures logged, retried and counted

## Technical notes (agent-owned)
Service: risk-service. Endpoints/events: ScreeningCompleted, RiskTierAssigned. Roles: SYSTEM.
