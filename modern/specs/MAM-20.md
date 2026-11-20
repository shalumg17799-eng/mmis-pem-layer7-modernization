---
key: MAM-20
localId: L-S030
epic: MAM-5
service: screening-service
type: Story
title: "Handle unknown licences, organisation providers and sources that fail or are slow"
slug: scr-screen-sources-2
specVersion: 1
descriptionSha256: 25a28c6d2c41a06bd07dcc69e0c4cad0defeaabc3f7fe74242d20ff816826819
jiraUpdated: 2026-11-18T09:14:21.585-0600
rules: []
decisions: [D-024, D-016, D-005]
dependsOn: [MAM-18, MAM-25]
mainJourney: false
labels: [svc-screening-service, kag-L-S030, agentic, decision-D-024, decision-D-016, decision-D-005]
builtCommit: 6e24725
testsSha256: b30ae2b98c24cd52be17fa43fe9db31014676e78b43b04f842d504414252607b
---
## Business requirement
Real sources are sometimes down, slow or have no record of a licence, and some providers (groups and facilities) hold no individual licence at all. Without a clear rule a provider could be passed by mistake, or a screening could hang for ever. This story defines what is recorded in these cases so that nobody is approved on an answer that was never received, so that organisations can still be enrolled, and so that an analyst can see why a screening failed and run it again.

## Acceptance criteria
### AC-1 A source that returns an error is recorded as not answered
Given provider 1003000111, for which the LEIE mock's error stub (HTTP 500) applies
When the screening runs
Then the LEIE mock's request log shows the lookup and one retry, the OIGLEIE result is C with no errorCode and a detail stating that the source could not be reached, the other three sources are still recorded, the screening result is FAIL and ScreeningCompleted is published with result FAIL
Basis: QUESTION C-1, R-PRVSCR01-cbl-00010, R-PRVSCR01-ADD-05, D-024, D-005, .claude/skills/synthetic-data/SKILL.md#5 (500-error stub for the retry path), modern/docs/analysis/data-layouts.md#SCRNREC (SCR-COND = C)

### AC-2 A slow source does not hold the screening
Given provider 1003000111, for which the SAM mock's slow stub answers after the 2-second limit
When the screening runs
Then the SAMGOV result is C with no errorCode and a detail stating that the source did not answer in time, and the screening is COMPLETED within 5 seconds of the event
Basis: QUESTION C-1, R-PRVSCR01-cbl-00010, modern/docs/architecture/nfr.md#Performance (screening of one provider under 5 s), .claude/skills/synthetic-data/SKILL.md#5 (slow stub)

### AC-3 A licence the board does not know fails without an error code
Given an individual (IN) provider 1003000666 with licence XX00000 in TX, which the licence-board mock does not know
When the screening completes
Then the LICBOARD result is F with no errorCode and a detail stating that the licence was not found, and the screening result is FAIL
Basis: QUESTION C-3, D-016, R-PRVSCR01-cbl-00010, aws-transform/test-data/providers.txt, aws-transform/test-data/license_board.txt (XX00000 not listed)

### AC-4 Groups and facilities are not checked against a licence
Given a facility (FA) provider 1003000333 whose provider record holds no licence number and no owner NPI
When the screening runs
Then the LICBOARD result is P with a detail stating that the licence check is not applicable, the licence-board mock's request log shows no lookup for it, and the OIGLEIE, SAMGOV and SSADMF sources are looked up as usual
Basis: QUESTION C-3, D-008, R-PRVSCR01-cbl-00032, modern/docs/analysis/data-layouts.md#PRVRECRD (the organisation view has no licence fields), modern/contracts/openapi/provider-service.yaml (licenseNo only under the individual data)

### AC-5 A provider that cannot be read is not screened on made-up data
Given ProviderRegistered for NPI 1003000777, and provider-service GET /api/v1/providers/1003000777 answers 5xx (or does not answer) until it is restored
When the event is processed while provider-service is failing, and provider-service is then restored
Then while provider-service fails, no screening of 1003000777 is COMPLETED and no ScreeningCompleted is published for it; after it is restored, the event is processed again and exactly one COMPLETED screening and one ScreeningCompleted exist for 1003000777
Basis: D-024, R-PRVSCR01-ADD-05, ADR-003 (at-least-once delivery, idempotent consumers), QUESTION C-2

## Out of scope
- Starting a new screening after a source has recovered (scr-rescreen)
- The analyst screen that shows why a screening failed (ui-console-detail, ui-console-queues)
- Changing a provider's licence data
- Any retry policy other than the default applied through question C-1 (one retry, 2-second limit, configurable)

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Related rules: R-PRVSCR01-ADD-05, R-PRVSCR01-cbl-00010, R-PRVSCR01-cbl-00032
Programs: No direct legacy equivalent: PRVSCR01.cbl never handled a failing source because all four checks were stubs returning P (118-131) and no status was tested (R-PRVSCR01-ADD-05, lines 114-149)
Decisions: D-024 FIX: explicit error handling, failures are surfaced and never silent; D-016 FIX: error codes only where the legacy defines one; no code is invented for 'licence not found' or 'source unavailable'; D-005 REPLACE: real screening against mocked sources

## Technical notes (agent-owned)
Service: screening-service. Endpoints/events: GET /api/v1/screenings, ProviderRegistered, ScreeningCompleted. Roles: ANALYST, SUPERVISOR, ADMIN, SYSTEM.
