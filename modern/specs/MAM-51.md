---
key: MAM-51
localId: L-S014
epic: MAM-2
service: testdata
type: Task
title: "Seed data for the local stack: screening results, sanction history and read models in each service"
slug: dat-seed-2
specVersion: 1
descriptionSha256: 5a646535cf1817a8cc6677bb735b41872b300dd6609f7af9539a627ff81a9333
jiraUpdated: 2026-11-18T09:17:36.106-0600
rules: []
decisions: [D-018, D-028, D-029]
dependsOn: [MAM-50, MAM-37, MAM-25, MAM-52, MAM-61, MAM-63, MAM-45, MAM-48]
mainJourney: false
labels: [svc-testdata, kag-L-S014, agentic, decision-D-018, decision-D-028, decision-D-029]
builtCommit:
testsSha256:
---
## Business requirement
The seeded providers must look the same in every part of the system: their screening results, their sanction history, the public directory and the revalidation tracking must match the provider seed, so that the sanction, revalidation and directory journeys can start from the seed without replaying history.

## Acceptance criteria
### AC-1 The provider list matches the seed file
Given the seeded stack and an ANALYST token
When GET /api/v1/providers is called, after one make seed and again after a second make seed
Then both times the total equals the number of records in fixed-width/provider-master-seed.dat and the listed NPIs are exactly those of the file
Basis: .claude/skills/synthetic-data/SKILL.md section 4, QF-6 (default applied), D-014

### AC-2 Screened providers have one result per source
Given the seeded stack and an ANALYST token
When GET /api/v1/screenings?npi=<npi> is called for every seeded provider with screen flag P or F
Then each has a completed screening with one result for each of LICBOARD, OIGLEIE, SAMGOV and SSADMF; a provider with flag P has only P results and a provider with flag F has at least one F result with its error code
Basis: D-018, D-005, modern/docs/architecture/overview.md (main flow 2: four mocked sources, one result per source), modern/contracts/openapi/screening-service.yaml (SourceResult)

### AC-3 Terminated and suspended providers have a sanction history
Given the seeded stack and an ANALYST token
When GET /api/v1/sanctions?npi=<npi> is called for every seeded provider with status T or S
Then each has a sanction row whose action matches its status (T for TERMINATED, S for SUSPENDED) with a reason and an effective date
Basis: D-018, D-006, modern/contracts/openapi/sanctions-service.yaml (Sanction)

### AC-4 The directory lists exactly the active providers with composed names
Given the seeded stack and a caller with no token
When GET /api/v1/directory/providers is called page by page
Then it lists exactly the seeded providers with status A, an individual's displayName is the first name then the last name (TESTPROV ALPHA) and an organisation's displayName is its legal name
Basis: D-028, Q-008, modern/docs/architecture/overview.md (main flow 6: read model of active providers)

### AC-5 The revalidation read model is seeded, not replayed
Given the seeded stack, no event replayed since make seed
When an ADMIN runs POST /api/v1/batch/revalidation-sweeps with businessDate 2026-03-31 and GET /api/v1/revalidations?status=DUE is called with an ANALYST token
Then there is exactly one DUE case for each seeded ACTIVE provider whose due date is on or before 2026-03-31 and none for any other provider
Basis: D-029, D-020, modern/docs/architecture/overview.md (revalidation-service keeps its own read model, difference 4), modern/docs/ux/journeys.md#J6 steps 1-2

## Out of scope
- The provider seed itself (dat-seed)
- Seeded notices (notification-service starts empty)
- Seeded T-MSIS archive generations (tmsis-service starts empty)

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: VSAM/PRVVSM02.jcl:15-27 (SCREEN.RESULTS and SANCTION.MASTER never used, D-018); BATCH/PRVDIR01.cbl:88 (raw-byte name, D-028); BATCH/PRVREV01.cbl:81-94 (no queue state, D-029)
Decisions: D-018 FIX: seeded screening results and sanction history live in their own services' tables; D-028 FIX: seeded directory names are composed from the name fields; D-029 FIX: one revalidation case per due provider, from a seeded read model

## Technical notes (agent-owned)
Service: testdata. Endpoints/events: GET /api/v1/providers, GET /api/v1/screenings, GET /api/v1/sanctions, GET /api/v1/directory/providers, POST /api/v1/batch/revalidation-sweeps, GET /api/v1/revalidations. Roles: ANALYST, ADMIN, PUBLIC.
