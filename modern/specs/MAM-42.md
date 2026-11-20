---
key: MAM-42
localId: L-S010
epic: MAM-2
service: libs/fixed-width
type: Task
title: "Fixed-width codec with layouts exported from the graph (PRVRECRD 130 bytes and every legacy file)"
slug: dat-codec
specVersion: 1
descriptionSha256: d4859335cded48636dc58ede5de2efb6232c128b419e091a8901fce87e8ac2aa
jiraUpdated: 2026-11-18T09:16:30.353-0600
rules: []
decisions: [D-007, D-004, D-015, D-025, D-024]
dependsOn: [MAM-16]
mainJourney: true
labels: [svc-libs/fixed-width, kag-L-S010, agentic, decision-D-007, decision-D-004, decision-D-015, decision-D-025, decision-D-024, slice-main-journey]
builtCommit: pending-user-commit
testsSha256: c30b3743bf16b0edefefd7cd1e3986aead8c6250766901f9900ec1cec369f067
---
## Business requirement
The new system must read and write the old fixed-width files byte for byte, so that its output can later be compared with the old mainframe's output. One shared, tested library handles every legacy file layout, and the layouts are taken from the analysed legacy definitions rather than typed by hand.

## Acceptance criteria
### AC-1 Layouts are exported, not typed
Given the graph export of the legacy records
When the layout files under libs/fixed-width/src/main/resources/layouts are read
Then there is one JSON layout for each of PRVRECRD (130), SCRNREC (67), APPINPUT (200), APPERROR (250), SANCIN (80), REVALQ (80), TMSISOUT (120) and DIROUT (100), each names the graph read query that produced it, and every field position equals the position in modern/docs/analysis/data-layouts.md
Basis: .claude/skills/synthetic-data/SKILL.md sections 2 and 3, .claude/agents/data-engineer.md (deliverable 1 and Rules), modern/docs/analysis/data-layouts.md

### AC-2 The provider master is 130 bytes and the intake record keeps bytes 131-200 reserved
Given the PRVRECRD layout
When a record is written and read
Then NPI is bytes 1-10, Medicaid ID 11-19, type 20-21, taxonomy 22-31, risk tier 32, enrolment status 33, screen flag 34, effective date 35-42, revalidation due date 43-50 and the variant 51-130, so the owner NPI at bytes 121-130 survives; a 121-byte master record is rejected; and APPINPUT is 200 bytes with bytes 1-130 as PRVRECRD and 131-200 reserved
Basis: D-007, D-025, Q-009, modern/docs/analysis/data-layouts.md (The 121 / 130 issue; File records defined inside programs)

### AC-3 The variant is read by provider type
Given a PRVRECRD record
When it is read with type IN, and with type GR or FA
Then type IN gives last name (30, byte 51), first name (20, byte 81), licence number (15, byte 101), licence state (2, byte 116) and 13 bytes of filler; type GR or FA gives legal name (40, byte 51), DBA name (30, byte 91) and owner NPI (10, byte 121)
Basis: modern/docs/analysis/data-layouts.md (PRVRECRD), .claude/skills/synthetic-data/SKILL.md section 2 (REDEFINES discriminator), D-008

### AC-4 Field types and dates
Given the supported field types
When values are written
Then alphanumeric fields are right-padded with spaces, numeric display fields are left zero-filled, the date 2026-01-15 is written as 20260115 in a CCYYMMDD field and as 26015 in a YYDDD field, and reading a YYDDD value requires the caller to give the century (no fixed pivot year)
Basis: D-004, D-015, .claude/skills/synthetic-data/SKILL.md section 2, modern/docs/adr/ADR-009-dates-codes-and-money.md

### AC-5 Parse then write gives identical bytes
Given a sample record for every layout, including an IN record and a GR record
When each is parsed and written again
Then the written bytes are identical to the input bytes
Basis: .claude/skills/synthetic-data/SKILL.md section 2 (round-trip test), .claude/agents/data-engineer.md deliverable 1

### AC-6 Bad input is rejected with an explicit error
Given a record of the wrong length, a non-numeric character in a numeric field, or an impossible date such as 20260230
When it is read
Then reading fails with an error naming the layout, record number and field, and nothing is silently truncated, padded or defaulted
Basis: D-024, D-007, D-004

### AC-7 The library check passes
Given libs/fixed-width
When ./gradlew :libs:fixed-width:check is run
Then it exits 0 with the round-trip tests and the quality gates passing
Basis: .claude/agents/data-engineer.md deliverable 1, .claude/commands/phase-5-platform.md (Exit checks)

## Out of scope
- Business validation of field values (each service)
- The services' own readers and writers (adapter/legacy in each service)
- EBCDIC conversion (dat-ebcdic)
- DB2 DCLGEN layout (not used by any program, D-014)

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: COPYBOOKS/PRVRECRD.cpy:9-44; COPYBOOKS/SCRNREC.cpy:7-21; BATCH/PRVENR01.cbl:52-55 (APPINPUT) and 62-65 (APPERROR); BATCH/PRVSAN01.cbl:53-65 (SANCIN); BATCH/PRVREV01.cbl:46-54 (REVALQ); BATCH/PRVTMS01.cbl:47-50 and 60-82,114 (TMSISOUT, YYDDD); BATCH/PRVDIR01.cbl:44-53 (DIROUT)
Decisions: D-007 FIX: the provider master record is 130 bytes; D-004 FIX: 4-digit dates everywhere internally; D-015 PRESERVE: YYDDD only inside the legacy T-MSIS layout; D-025 PRESERVE: intake = PRVRECRD in bytes 1-130, bytes 131-200 reserved; D-024 FIX: explicit errors instead of silent handling

## Technical notes (agent-owned)
Service: libs/fixed-width. Endpoints/events: none. Roles: none.
