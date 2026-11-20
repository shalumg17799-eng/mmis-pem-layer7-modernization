---
key: MAM-21
localId: L-S017
epic: MAM-3
service: provider-service
type: Story
title: "Record the screening verdict (screen flag) on the provider"
slug: prv-screen-verdict
specVersion: 1
descriptionSha256: 1ae7ec4074bab1542c6b2462dadf6ba4770a73cc492721029a460c6ab4e8710e
jiraUpdated: 2026-11-18T09:14:24.248-0600
rules: [R-PRVSCR01-ADD-03, R-PRVSCR01-cbl-00011, R-PRVSCR01-cbl-00046, R-PRVSCR01-cbl-00047, R-PRVSCR01-cbl-00049, R-PRVSCR01-cbl-00050, R-PRVSCR01-cbl-00051, R-PRVSCR01-cbl-00052]
decisions: [D-005, D-010, D-014, D-019]
dependsOn: [MAM-17]
mainJourney: true
labels: [svc-provider-service, kag-L-S017, agentic, decision-D-005, decision-D-010, decision-D-014, decision-D-019, slice-main-journey]
builtCommit: pending-user-commit
testsSha256: 5cd733c56a413d12efd7298270dd91f94e8a4efefec5159d15392b2c2b698b6f
---
## Business requirement
The result of screening a provider (cleared or not cleared, taking every source into account) must be recorded on the provider's record, so that analysts can see who is cleared and who is not. Recording a result never makes a provider active: only an analyst's decision does. Later re-screenings of an enrolled provider record their result in the same way and never change the enrolment status.

## Acceptance criteria
### AC-1 A passed screening sets the screen flag to P
Given provider 1003000111 is PENDING with screenFlag U
When provider-service consumes a ScreeningCompleted event for 1003000111 with reason INITIAL and result PASS (all four sources passed)
Then GET /api/v1/providers/1003000111 returns screenFlag P and enrollStatus still P
Basis: R-PRVSCR01-cbl-00046, R-PRVSCR01-cbl-00050, D-005, modern/contracts/asyncapi/events.yaml (ScreeningCompleted)

### AC-2 A failed screening sets the screen flag to F
Given provider 1003000444 is PENDING with screenFlag U and screening-service found its owner 1990000111 on the (mocked) OIG LEIE
When provider-service consumes a ScreeningCompleted event for 1003000444 with reason INITIAL and result FAIL
Then GET /api/v1/providers/1003000444 returns screenFlag F and enrollStatus P, and GET /api/v1/providers?status=P&screenFlag=F lists 1003000444
Basis: R-PRVSCR01-cbl-00047, R-PRVSCR01-cbl-00051, D-005, modern/docs/ux/journeys.md#J3

### AC-3 Screening never activates a provider
Given provider 1003000111 is PENDING with screenFlag U
When a ScreeningCompleted event with result PASS is consumed
Then enrollStatus is still P (not A) and no ProviderStatusChanged event is published for 1003000111; only the analyst approval (prv-approve) can set A
Basis: R-PRVSCR01-ADD-03, D-019

### AC-4 Every verdict is saved
Given provider 1003000111 has screenFlag U and version V
When the ScreeningCompleted PASS event is consumed
Then a later GET /api/v1/providers/1003000111 returns screenFlag P and version V+1 (the verdict was written back to the provider record, not only held in memory)
Basis: R-PRVSCR01-cbl-00011, R-PRVSCR01-cbl-00049, R-PRVSCR01-cbl-00052

### AC-5 A re-screening records its verdict the same way and never changes the status
Given provider 1003000111 is ACTIVE with screenFlag P
When a ScreeningCompleted event with reason REVALIDATION (or MANUAL) and result FAIL is consumed
Then GET /api/v1/providers/1003000111 returns screenFlag F and enrollStatus A; a later ScreeningCompleted PASS sets screenFlag P again
Basis: R-PRVSCR01-ADD-03, Q-A4 (a re-screening changes only the screen flag, plus the due date on a revalidation PASS), modern/contracts/asyncapi/events.yaml (ScreeningCompleted.reason INITIAL, REVALIDATION, MANUAL)

### AC-6 A redelivered verdict is applied once
Given the ScreeningCompleted event with id E2 was already processed for provider 1003000111
When E2 is delivered again
Then GET /api/v1/providers/1003000111 shows the version did not increase a second time and the screen flag is unchanged
Basis: ADR-003, .claude/skills/java-microservices/SKILL.md section 7

## Out of scope
- Running the checks against the licence board, OIG LEIE, SAM and SSA DMF and deciding the verdict (scr-screen-sources, scr-owner, scr-verdict).
- One screening row per source with date and detail (screening-service, D-018).
- The Medicare reciprocity check (D-010 DROP): not built.
- Setting the revalidation due date after a completed revalidation: prv-approve-2.
- The legacy SCRNOUT file: scr-batch-run.

## Legacy trace (agent-owned, do not edit)
Rules: R-PRVSCR01-ADD-03, R-PRVSCR01-cbl-00011, R-PRVSCR01-cbl-00046, R-PRVSCR01-cbl-00047, R-PRVSCR01-cbl-00049, R-PRVSCR01-cbl-00050, R-PRVSCR01-cbl-00051, R-PRVSCR01-cbl-00052
Programs: PRVSCR01: source-code/BATCH/PRVSCR01.cbl:90-114 (SCREEN-NEXT-PROVIDER / SCREEN-ONE-PROVIDER: verdict 106-112, REWRITE 114). Job step: PRVDAY01 STEP030 (source-code/JCL/PRVDAY01.jcl; proc PRVSCRPR is never called).
Decisions: D-005 REPLACE: the verdict comes from the real screening-service over mocked sources; provider-service only records it; D-010 DROP: Medicare reciprocity check, nothing built; D-014 FIX: provider-service is the only writer of the screen flag; D-019 FIX: screening never sets ACTIVE; an analyst approval does

## Technical notes (agent-owned)
Service: provider-service. Endpoints/events: ScreeningCompleted. Roles: none.
