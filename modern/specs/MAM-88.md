---
key: MAM-88
localId: L-S076
epic: MAM-14
service: e2e
type: Story
title: "Performance smoke: 10,000 synthetic providers, batch jobs and search against the NFR targets (NFR)"
slug: nfr-performance
specVersion: 1
descriptionSha256: 93ca6dadb8d3a5e88898c54bdb616d03a668c21aa198835fa37108841c4730fa
jiraUpdated: 2026-11-18T09:23:10.765-0600
rules: []
decisions: []
dependsOn: [MAM-44, MAM-50, MAM-45, MAM-75, MAM-37, MAM-46, MAM-18]
mainJourney: false
labels: [svc-e2e, kag-L-S076, agentic]
builtCommit:
testsSha256:
---
## Business requirement
The system must stay responsive and finish its monthly jobs in a reasonable time when it holds a realistic number of providers. This story fills the system with 10,000 fictitious providers and measures it against the agreed targets, saying plainly which targets are still only placeholders.

## Acceptance criteria
### AC-1 A larger synthetic set is generated and loaded
Given the deterministic generator with its recorded seed
When it generates 10,000 providers and they are loaded into the stack
Then the 130-byte provider file holds 10,000 records, GET /api/v1/providers reports a total of at least 10,000, and every NPI and name passes the synthetic-data rules
Basis: .claude/commands/phase-8-e2e-hardening.md step 5, .claude/skills/synthetic-data/SKILL.md section 1, D-007

### AC-2 Search and detail respond within the target
Given 20 concurrent users with k6 (pinned Docker image)
When they call GET /api/v1/providers and GET /api/v1/providers/{npi} through the gateway
Then the k6 summary shows a p95 under 500 ms for each
Basis: modern/docs/architecture/nfr.md (Performance: search and detail), .claude/commands/phase-8-e2e-hardening.md step 5

### AC-3 Submit and screening respond within the target
Given the 10,000-provider stack
When applications are submitted with POST /api/v1/applications and single providers are screened against the mocked sources
Then the p95 of a submit is under 1 s and a screening of one provider takes under 5 s
Basis: modern/docs/architecture/nfr.md (Performance)

### AC-4 The monthly jobs finish within the proportional target
Given the 10,000-provider stack
When the revalidation sweep and the T-MSIS extract are run
Then each completes within 90 seconds (the 15-minute target for 100,000 providers scaled to 10,000), and the report says this is a projection
Basis: modern/docs/architecture/nfr.md (Performance: 100,000 providers in under 15 minutes), QF-10 (default applied), .claude/commands/phase-8-e2e-hardening.md step 5

### AC-5 The report states every result and which targets are DEFAULT
Given the measurements
When the report in modern/docs/reports is read
Then it lists each target with the measured value and met or not met, marks every target still marked DEFAULT in nfr.md as DEFAULT, and lists any target not met without tuning it away
Basis: .claude/commands/phase-8-e2e-hardening.md step 5, modern/docs/architecture/nfr.md (Performance: DEFAULT until the user gives volumes), MODERNIZATION.md section 6 rule 8

## Out of scope
- A 100,000-provider load (QF-10 option B)
- Tuning and fixes for any missed target (separate Defect stories after the user agrees)
- Availability and failover testing

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: JCL/PRVMTH01.jcl:14-45 (monthly sweep and extract jobs); JCL/PRVDAY01.jcl:55-63 (STEP030 screening run)
Decisions: none

## Technical notes (agent-owned)
Service: e2e. Endpoints/events: GET /api/v1/providers, GET /api/v1/providers/{npi}, POST /api/v1/applications, POST /api/v1/screenings, POST /api/v1/batch/revalidation-sweeps, POST /api/v1/batch/tmsis-extracts. Roles: ANALYST, ADMIN, PROVIDER.
