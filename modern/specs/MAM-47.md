---
key: MAM-47
localId: L-S012
epic: MAM-2
service: infra/wiremock
type: Task
title: "WireMock stubs for the external screening sources (NPPES, LEIE, SAM, licence board, SSA DMF): hit, clean, error, slow"
slug: dat-wiremock
specVersion: 1
descriptionSha256: 3d1640608674a80a48948976b6b82cf6b24a76bc8b3adf444cda7bc2aab82c6d
jiraUpdated: 2026-11-18T09:17:04.905-0600
rules: []
decisions: [D-005, D-016]
dependsOn: [MAM-24, MAM-44]
mainJourney: true
labels: [svc-infra/wiremock, kag-L-S012, agentic, decision-D-005, decision-D-016, slice-main-journey]
builtCommit: pending-user-commit
testsSha256: 133f192801710cefe96c79edae34ce820f654c5d8d2bbeeb1080c9b4000925bf
---
## Business requirement
Screening checks a provider against outside lists (a federal exclusion list, a debarment list, a state licence board, death records and the national provider registry). Those outside services are not available locally, so look-alike stand-ins are needed that can answer found, not found, error and slow, so that screening and the user journeys can be run safely with made-up data.

## Acceptance criteria
### AC-1 Five sources with four answers each
Given infra/wiremock and the stack is up
When the WireMock admin mappings are listed
Then there is a folder and mappings for each of leie, sam, nppes, license-board and ssa-dmf, and each source has a hit stub, a clean stub, a 500-error stub and a slow-response stub
Basis: .claude/skills/synthetic-data/SKILL.md section 5, .claude/agents/data-engineer.md deliverable 4, D-005

### AC-2 The journey J1 applicant is clean on every source
Given provider 1003000111 with licence MD12345 / TX
When each source is asked about that NPI and licence
Then every source answers clean (not excluded, licence ACTIVE with expiry 2028-09, not debarred, not deceased)
Basis: modern/docs/ux/journeys.md#J1 step 4, aws-transform/test-data/license_board.txt, aws-transform/test-data/providers.txt, D-005

### AC-3 LEIE excludes NPI 1990000111
Given the LEIE stub
When it is asked about NPI 1990000111 (the owner of 1003000444) and about any other NPI
Then 1990000111 answers excluded with exclusion type 1128(a)(1) PROGRAM-RELATED FRAUD and date 2023-01-15, and every other NPI answers clean
Basis: aws-transform/test-data/leie.txt, modern/docs/ux/journeys.md#J3 step 1, .claude/skills/synthetic-data/SKILL.md section 4, D-016

### AC-4 The licence board knows the sample and seed licences
Given the licence-board stub
When it is asked about each licence in aws-transform/test-data/license_board.txt, about each licence of a seeded individual, and about XX00000 / TX
Then PT99900 / TX answers EXPIRED (expiry 2024-01), the other six sample licences answer ACTIVE with the expiry in the file, every licence of a seeded individual answers ACTIVE, and XX00000 / TX and any other licence answer not found
Basis: aws-transform/test-data/license_board.txt, aws-transform/test-data/providers.txt, .claude/skills/synthetic-data/SKILL.md section 4 (licence PT99900 expired), D-016 (E202), modern/docs/ux/journeys.md#J6 step 3 (seeded providers pass re-screening)

### AC-5 SAM is empty by design, with one documented hit
Given the SAM stub
When it is asked about any entity, and about the one fabricated debarred entity named in the stub README
Then every entity answers clean except the documented one, which answers debarred
Basis: aws-transform/test-data/sam.txt, .claude/skills/synthetic-data/SKILL.md sections 4 and 5, D-016 (E205)

### AC-6 SSA DMF has one documented deceased hit
Given the SSA DMF stub
When it is asked about any NPI, and about the one fabricated NPI named in the stub README
Then every NPI answers not deceased except the documented one, which answers deceased
Basis: .claude/skills/synthetic-data/SKILL.md section 5, D-016 (E204)

### AC-7 Error and slow answers for every source
Given the error and slow stubs of each source and the way the README says to select them
When each is called
Then the error stub answers HTTP 500 and the slow stub answers only after its documented delay, which is longer than 5 seconds, so that retry and manual-review paths can be tested
Basis: .claude/skills/synthetic-data/SKILL.md section 5, modern/docs/architecture/nfr.md (Performance: screening of one provider under 5 s with mocked sources)

### AC-8 The stubs are documented and tested
Given infra/wiremock/README.md and the running stack
When a test calls every stub described in the README
Then the README documents the simple JSON request and response of each source and how to select each answer, and every documented call returns exactly the documented answer
Basis: .claude/skills/synthetic-data/SKILL.md section 5, .claude/agents/data-engineer.md deliverable 4

## Out of scope
- How screening calls the sources, retries, timeouts and how a 'not found' licence is classified (scr-screen-sources, scr-verdict)
- Whether screening calls NPPES (the overview lists four sources; the stub exists for the skill's five)
- Real external APIs (a later release)

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: BATCH/PRVSCR01.cbl:116-132,146-149 (the four hard-coded Pass checks replaced by real calls, D-005)
Decisions: D-005 REPLACE: real screening against mocked external sources; these stubs are the mocked sources; D-016 FIX: E201-E205 outcomes can be produced from the stubs

## Technical notes (agent-owned)
Service: infra/wiremock. Endpoints/events: none. Roles: none.
