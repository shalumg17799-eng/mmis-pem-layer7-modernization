---
key: MAM-50
localId: L-S013
epic: MAM-2
service: testdata
type: Task
title: "Seed data for the local stack (make seed): the provider seed"
slug: dat-seed
specVersion: 1
descriptionSha256: 6c01526b4485d690fa0caeb860981ee6970f1aacb317ef8715780dad0ccc26bc
jiraUpdated: 2026-11-18T09:17:21.166-0600
rules: []
decisions: [D-006, D-007, D-014]
dependsOn: [MAM-24, MAM-44]
mainJourney: true
labels: [svc-testdata, kag-L-S013, agentic, decision-D-006, decision-D-007, decision-D-014, slice-main-journey]
builtCommit: pending-user-commit
testsSha256: 9f2c1def4b3e259a53d8feaa017fd73aa41e8c7a5f9249df64f1bfeb9919f4a7
---
## Business requirement
To demonstrate and test the system, a freshly started local stack needs a realistic set of fictitious providers in every state, loaded with one command and safe to repeat, so that every journey can start from the same known state.

## Acceptance criteria
### AC-1 make seed loads the provider seed as stored in the legacy file
Given the stack is up and an ANALYST token
When make seed is run and GET /api/v1/providers/{npi} is called for every NPI of fixed-width/provider-master-seed.dat
Then each call returns 200 with the same Medicaid ID, type, taxonomy, risk tier, enrolment status, screen flag, effective date, revalidation due date and name or organisation fields (including the owner NPI) as the 130-byte record, with dates in 4-digit-year form
Basis: .claude/agents/devops-engineer.md deliverable 5 (make seed), .claude/skills/synthetic-data/SKILL.md section 4 (sql/<service>-seed.sql: the same providers), D-007, D-014, D-004

### AC-2 The provider seed covers every state
Given the seeded stack
When the seeded providers are read through GET /api/v1/providers/{npi}
Then together they hold every enrolment status P, A, T, D and S, every screen flag U, P and F, every risk tier L, M and H and the taxonomy prefixes 332, 251, 341, 291, 225 and one other
Basis: .claude/skills/synthetic-data/SKILL.md section 4, D-006, .claude/skills/pem-domain/SKILL.md sections 2 and 3

### AC-3 The seed supports the journeys
Given the seeded stack and the journey NPIs listed in modern/testdata/README.md
When those providers are read through GET /api/v1/providers/{npi}
Then there is an ACTIVE individual with first name TESTPROV and last name ALPHA, ACTIVE providers with a revalidation due date on or before 2026-03-31 and at least one with a later due date, one ACTIVE and one TERMINATED provider whose names contain HOSPICE, and every licence of a seeded individual is one the licence-board stub answers ACTIVE
Basis: modern/docs/ux/journeys.md#J5 step 1, modern/docs/ux/journeys.md#J6 steps 1-3, modern/docs/ux/journeys.md#J7 step 3, .claude/skills/synthetic-data/SKILL.md section 1

### AC-4 Journey applicants are never pre-registered
Given the seeded stack and an ANALYST token
When GET /api/v1/providers/{npi} is called for 1003000111, 1003000222, 1003000333, 1003000444, 1003000555, 1003000666, 1003000777, 1003000888 and 1990000111
Then each call returns 404, so applying with them is never a duplicate (E103), and no seeded NPI lies in 1003000000-1003000999 or 1990000000-1990000999
Basis: modern/docs/ux/journeys.md#J1 step 2, modern/docs/ux/journeys.md#J3 step 1, aws-transform/test-data/providers.txt, D-016 (E103), QF-12 (default applied)

### AC-5 make seed restores exactly the seed state
Given a seeded stack in which the test set-up has added one extra provider row
When make seed is run again, and then a third time
Then after each run GET /api/v1/providers/{npi} returns 404 for the extra provider and the seeded providers are unchanged, so each journey can start from the same state
Basis: QF-6 (default applied), .claude/commands/phase-8-e2e-hardening.md step 2 (journeys run 3 times in a row), .claude/agents/devops-engineer.md deliverable 5

### AC-6 A seed failure is explicit
Given one service's database is not reachable
When make seed is run
Then it stops with a non-zero exit and a message naming that service, and running make seed again once the database is back gives the full seed state
Basis: D-024

### AC-7 Seed data is synthetic
Given all SQL seed files
When a scan runs over them
Then it finds only obviously fake names (such as TESTPROV ALPHA, SAMPLE CLINIC 01) and NPIs with a valid check digit outside the reserved ranges, and fails on anything else
Basis: .claude/skills/synthetic-data/SKILL.md section 1, MODERNIZATION.md section 6 rule 4, QF-12 (default applied)

## Out of scope
- Seeded screening results, sanction history, directory entries and revalidation read model, checked through their own APIs (dat-seed-2)
- Keycloak users (plt-keycloak imports the realm)
- Notice template text (notification-service)
- The scenario applications of the journeys (dat-synthetic scenarios)
- The 10,000-provider set (nfr-performance)

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: COPYBOOKS/PRVRECRD.cpy:9-44 (provider record); VSAM/PRVVSM01.jcl:14-17 and DB2/PRVDDL01.sql:19-33 (the two legacy stores replaced by one, D-014)
Decisions: D-006 FIX: seeded providers include SUSPENDED; D-007 FIX: seed provider records hold every 130-byte field including the owner NPI; D-014 FIX: provider-service holds the one seeded provider record

## Technical notes (agent-owned)
Service: testdata. Endpoints/events: GET /api/v1/providers/{npi}. Roles: ANALYST.
