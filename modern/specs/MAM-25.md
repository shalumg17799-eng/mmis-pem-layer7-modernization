---
key: MAM-25
localId: L-S032
epic: MAM-5
service: screening-service
type: Story
title: "Decide the screening verdict, store the results per source and publish ScreeningCompleted"
slug: scr-verdict
specVersion: 1
descriptionSha256: 1898c145cfc914e03ac1ec407102c40bf06c3bf4002d35ea1b224a5eb3273f27
jiraUpdated: 2026-11-18T09:14:55.005-0600
rules: [R-PRVSCR01-ADD-02, R-PRVSCR01-cbl-00010, R-PRVSCR01-cbl-00030, R-PRVSCR01-cbl-00031, R-PRVSCR01-cbl-00035, R-PRVSCR01-cbl-00036, R-PRVSCR01-cbl-00037, R-PRVSCR01-cbl-00040, R-PRVSCR01-cbl-00041, R-PRVSCR01-cbl-00042, R-PRVSCR01-cbl-00044, R-PRVSCR01-cbl-00045]
decisions: [D-005, D-018, D-016, D-023]
dependsOn: [MAM-18]
mainJourney: true
labels: [svc-screening-service, kag-L-S032, agentic, decision-D-005, decision-D-018, decision-D-016, decision-D-023, slice-main-journey]
builtCommit: 6e24725
testsSha256: 3be988405ce41878b47264a87d0d91818d4ea4b9806af1b5121429d5b15f1f51
---
## Business requirement
After the four checks the system must decide one overall answer: the provider passes only if every check passed. It must also keep what each source said, with the date and a short explanation, because the programme has to be able to show later why a provider was passed or failed. Today the legacy job keeps only a bare pass for each check and no explanation. After this story the verdict is announced to the rest of the system, the analysts can list and open the screenings, and nothing that was recorded is overwritten.

## Acceptance criteria
### AC-1 All four sources pass: the screening passes
Given a screening of NPI 1003000111 whose four source results are all P
When the last result is recorded
Then GET /api/v1/screenings/{screeningId} returns status COMPLETED, result PASS and a completedAt time
Basis: R-PRVSCR01-cbl-00010, D-005, modern/docs/ux/journeys.md#J1

### AC-2 One failing source fails the screening
Given a screening in which exactly one of the four sources is F (for example OIGLEIE for NPI 1003000444) and the other three are P
When the last result is recorded
Then the screening has status COMPLETED and result FAIL, and the same holds whichever one of the four sources is the F
Basis: R-PRVSCR01-cbl-00010, R-PRVSCR01-cbl-00037, modern/contracts/asyncapi/events.yaml#ScreeningCompleted (any source FAIL means FAIL), modern/docs/ux/journeys.md#J3

### AC-3 One stored result per source
Given a completed screening of NPI 1003000555 whose LICBOARD result is F (E202)
When GET /api/v1/screenings/{screeningId} is called
Then results holds exactly four entries, one each for LICBOARD, OIGLEIE, SAMGOV and SSADMF, each with result P, F or C, a verdictAt time and a non-blank detail, and an errorCode only where one applies (here E202 on LICBOARD; none on the P results)
Basis: R-PRVSCR01-ADD-02, R-PRVSCR01-cbl-00030, R-PRVSCR01-cbl-00031, R-PRVSCR01-cbl-00035, R-PRVSCR01-cbl-00036, R-PRVSCR01-cbl-00040, R-PRVSCR01-cbl-00041, R-PRVSCR01-cbl-00042, R-PRVSCR01-cbl-00044, R-PRVSCR01-cbl-00045, D-005, D-018, D-016, QUESTION C-3, modern/contracts/openapi/screening-service.yaml#SourceResult

### AC-4 Results are kept and never overwritten
Given two screenings of NPI 1003000555 have completed at different times
When GET /api/v1/screenings?npi=1003000555 is called
Then both screenings are returned, each with its own four results, and the earlier one is unchanged
Basis: D-018, R-PRVSCR01-ADD-02, modern/docs/architecture/nfr.md#Audit (screening results are kept), modern/docs/architecture/data-model/screening-service.sql

### AC-5 ScreeningCompleted is published for every completed screening
Given a screening of NPI 1003000111 with taxonomy 207Q00000X completes (PASS or FAIL)
When the result is committed
Then exactly one ScreeningCompleted is published to pem.screening.events with message key 1003000111 and data holding screeningId, npi, reason, result, taxonomy 207Q00000X and sources (source, result and detail of each of the four)
Basis: modern/contracts/asyncapi/events.yaml#ScreeningCompleted, ADR-003, D-005, R-PRVSCR01-cbl-00010, modern/docs/architecture/overview.md#Main flows (2)

### AC-6 Failed screenings can be listed with the failing source
Given NPI 1003000444 failed on OIGLEIE with errorCode E203
When an ANALYST calls GET /api/v1/screenings?result=FAIL
Then the response (items, page, size, total) includes that screening with its per-source results, where OIGLEIE shows F and errorCode E203
Basis: modern/contracts/openapi/screening-service.yaml#listScreenings (result filter), modern/docs/ux/screen-inventory.md#console provider-detail (GET /api/v1/screenings), modern/docs/ux/journeys.md#J3, D-016

### AC-7 One screening can be read, an unknown id is not found
Given a known screeningId and an unknown screeningId
When GET /api/v1/screenings/{screeningId} is called for each
Then the known id returns 200 with the screening and the unknown id returns 404 with a problem+json body
Basis: modern/contracts/openapi/screening-service.yaml#getScreening, .claude/skills/java-microservices/SKILL.md#6 (RFC 7807 errors)

### AC-8 Only staff can read screenings
Given tokens for ANALYST, SUPERVISOR, ADMIN, PROVIDER and SYSTEM, and a request with no token
When each calls GET /api/v1/screenings and GET /api/v1/screenings/{screeningId}
Then ANALYST, SUPERVISOR and ADMIN receive 200, PROVIDER and SYSTEM receive 403, and the request with no token receives 401
Basis: D-023, ADR-007, modern/docs/architecture/nfr.md#Security, modern/contracts/openapi/screening-service.yaml (x-roles)

## Out of scope
- Setting the provider's screen flag (provider-service, prv-screen-verdict)
- Assigning the risk tier (rsk-assign-tier)
- Sending notices (notification-service)
- The legacy SCRNOUT file and the run's failure counter (scr-batch-run)
- Starting a screening on request or for revalidation (scr-rescreen)

## Legacy trace (agent-owned, do not edit)
Rules: R-PRVSCR01-ADD-02, R-PRVSCR01-cbl-00010, R-PRVSCR01-cbl-00030, R-PRVSCR01-cbl-00031, R-PRVSCR01-cbl-00035, R-PRVSCR01-cbl-00036, R-PRVSCR01-cbl-00037, R-PRVSCR01-cbl-00040, R-PRVSCR01-cbl-00041, R-PRVSCR01-cbl-00042, R-PRVSCR01-cbl-00044, R-PRVSCR01-cbl-00045
Programs: source-code/BATCH/PRVSCR01.cbl: SCREEN-ONE-PROVIDER 106-113 (verdict: all four P gives pass, otherwise fail), CHECK-* paragraphs 116-132, WRITE-SCREEN-ROW 146-149 (sets only the NPI and the pass flag). Job step: source-code/JCL/PRVDAY01.jcl STEP030
Decisions: D-005 REPLACE: results carry source, verdict time and detail; D-018 FIX: screening results are a real table that keeps every screening; D-016 FIX: the failing source carries its E2xx code where one applies; D-023 FIX: role-based access on every endpoint

## Technical notes (agent-owned)
Service: screening-service. Endpoints/events: GET /api/v1/screenings, GET /api/v1/screenings/{screeningId}, ScreeningCompleted. Roles: ANALYST, SUPERVISOR, ADMIN.
