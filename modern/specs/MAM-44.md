---
key: MAM-44
localId: L-S011
epic: MAM-2
service: testdata
type: Task
title: "Deterministic synthetic datasets covering at least 95% of the verified rules"
slug: dat-synthetic
specVersion: 1
descriptionSha256: 7c9f18f59823debbca673d76288f1a761330e48d82d347d63336a32004d30865
jiraUpdated: 2026-11-18T09:16:48.457-0600
rules: []
decisions: [D-007, D-008, D-006, D-016, D-026, D-004]
dependsOn: [MAM-42]
mainJourney: true
labels: [svc-testdata, kag-L-S011, agentic, decision-D-007, decision-D-008, decision-D-006, decision-D-016, decision-D-026, decision-D-004, slice-main-journey]
builtCommit: pending-user-commit
testsSha256: 63c9c0b3d99dc1ae3dc377fa3ccb88d6a6ddcbbeca23953c6289b4562d50136e
---
## Business requirement
Tests and later comparisons with the old system need realistic but entirely fictitious data that exercises every business rule outcome, generated the same way every time. No real provider data may ever enter the project.

## Acceptance criteria
### AC-1 The generator is deterministic and documents its seed
Given the generator and the seed recorded in modern/testdata/README.md
When it is run twice
Then every generated file is byte-identical between the two runs (equal sha256), and the README records the seed and the frozen business date 2026-01-15
Basis: .claude/skills/synthetic-data/SKILL.md sections 1 and 4, .claude/agents/data-engineer.md deliverable 2

### AC-2 Enrolment intake covers every validation branch
Given fixed-width/appinput-baseline.dat
When it is read with the APPINPUT layout
Then every record is 200 bytes and the file has at least one valid IN, one valid GR, one valid FA, one record with a missing NPI (E101), one with an invalid type (E102), one with a missing taxonomy (E104) and one with a duplicate NPI (E103)
Basis: .claude/skills/synthetic-data/SKILL.md section 4, D-016, D-025

### AC-3 The provider master seed covers every state
Given fixed-width/provider-master-seed.dat
When it is read with the PRVRECRD layout
Then every record is 130 bytes and together they hold every enrolment status P, A, T, D and S, every screen flag U, P and F, every risk tier L, M and H, the taxonomy prefixes 332, 251, 341, 291, 225 and one other, and revalidation due dates before, equal to and after 2026-01-15, all in 4-digit-year form
Basis: .claude/skills/synthetic-data/SKILL.md section 4, D-006, D-007, D-004, .claude/skills/pem-domain/SKILL.md sections 2 and 3

### AC-4 Sanction input covers every action
Given fixed-width/sancin-baseline.dat
When it is read with the SANCIN layout
Then every record is 80 bytes and the file has T, S and R actions, a record for an unknown NPI and a record with an invalid action code
Basis: .claude/skills/synthetic-data/SKILL.md section 4, D-026

### AC-5 The eight sample applications become legacy intake records
Given the 8 applications in aws-transform/test-data/providers.txt and the type mapping of QF-2
When scenarios/ is generated
Then a 200-byte APPINPUT record exists for each of the NPIs 1003000111, 1003000222, 1003000333, 1003000444, 1003000555, 1003000666, 1003000777 and 1003000888 with the feed type code mapped to IN, GR or FA, with synthetic Medicaid IDs and taxonomies added (the feed has none; 207Q00000X for 1003000111), and an expected-outcome list names 1003000444 as failing with E203, 1003000555 as failing with E202, 1003000111, 1003000222, 1003000333, 1003000777 and 1003000888 as passing, and 1003000666 with no expected outcome yet (QF-14)
Basis: .claude/skills/synthetic-data/SKILL.md section 4, D-008, D-016, CLAUDE.md section 9, QF-2 (default applied), QF-14 (default applied), modern/docs/ux/journeys.md#J1 step 2

### AC-6 Coverage of at least 95% of the rules is measured
Given testdata/coverage.csv with rule_id, fixture_file and record_ref
When it is compared with the 433 non-DROPPED rules of modern/docs/architecture/rule-to-service.csv
Then at least 95% of those rules have a fixture row, and every uncovered rule is listed with a reason
Basis: .claude/agents/data-engineer.md deliverable 3, .claude/skills/synthetic-data/SKILL.md section 4, .claude/commands/phase-5-platform.md (Exit checks), QF-9 (default applied)

### AC-7 No real data
Given all generated files
When a scan runs over them
Then every generated NPI has a valid check digit (Luhn with the 80840 prefix) and lies outside the reserved ranges 1003000000-1003000999 and 1990000000-1990000999, generated names are obviously fake (for example TESTPROV ALPHA, SAMPLE CLINIC 01), the only other NPIs and names are the fabricated values of aws-transform/test-data used verbatim in scenarios/, and the scan fails on anything else
Basis: .claude/skills/synthetic-data/SKILL.md section 1, MODERNIZATION.md section 6 rule 4, QF-12 (default applied)

### AC-8 Every file parses with the shared library
Given every dataset under fixed-width/ and scenarios/
When it is read with libs/fixed-width
Then every record has the exact length of its layout (provider master 130) and the round trip returns identical bytes
Basis: D-007, .claude/skills/synthetic-data/SKILL.md section 2

## Out of scope
- SQL seed files and make seed (dat-seed)
- WireMock stubs (dat-wiremock)
- The 10,000-provider performance set (nfr-performance)
- The screening outcome of 1003000666 (screening stories, QF-14)
- Real data of any kind

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: BATCH/PRVENR01.cbl:128-159 (validation branches, E101); BATCH/PRVSAN01.cbl:104-116 (T, S, R actions); BATCH/PRVRSK01.cbl:70-76,86-97,126-137 (taxonomy prefixes, due dates); COPYBOOKS/PRVRECRD.cpy:9-44
Decisions: D-007 FIX: provider master datasets are 130-byte records; D-008 PRESERVE: IN/GR/FA plus a mapping table; feed codes are mapped in the scenarios; D-006 FIX: the seed includes the new SUSPENDED status S; D-016 FIX: E101-E104 branches and E202/E203 outcomes are covered; D-026 FIX: an invalid sanction action code is included; D-004 FIX: 4-digit dates in every file

## Technical notes (agent-owned)
Service: testdata. Endpoints/events: none. Roles: none.
