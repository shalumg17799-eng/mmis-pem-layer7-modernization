---
key: MAM-53
localId: L-S015
epic: MAM-2
service: tools/data
type: Task
title: "EBCDIC to ASCII converter for the Phase 9 parity run"
slug: dat-ebcdic
specVersion: 1
descriptionSha256: 4dc103c9aa2c98f7f020b03cc6af364aef3e20612c2b156a4ed56e69a1f6e61a
jiraUpdated: 2026-11-18T09:17:48.710-0600
rules: []
decisions: [D-024]
dependsOn: []
mainJourney: false
labels: [svc-tools/data, kag-L-S015, agentic, decision-D-024]
builtCommit: pending-user-commit
testsSha256: 5e40c8bf6ff64b380a490b7fff132523776e0322f6de22ad58cebb03ce06689a
---
## Business requirement
When the old batch jobs are run on a mainframe emulator, their output files come back in mainframe character encoding. The team needs a small, reliable tool that converts them to readable text without changing record lengths, so the old and new outputs can be compared.

## Acceptance criteria
### AC-1 A command-line tool converts a file
Given a file in EBCDIC code page 037 or 1047
When tools/data/ebcdic_to_ascii.py is run with the input path, the output path and the code page as a parameter
Then the output file has the same number of bytes as the input, one ASCII byte per EBCDIC byte, with no line terminators added
Basis: .claude/agents/data-engineer.md deliverable 5, .claude/skills/parity-testing/SKILL.md sections 2 and 4

### AC-2 The code page is required and checked
Given a run with no code page, or with an unsupported one
When the tool is started
Then it exits with a non-zero code and a message that lists the supported code pages 037 and 1047, and writes no output file
Basis: .claude/agents/data-engineer.md deliverable 5, .claude/skills/parity-testing/SKILL.md section 2, D-024

### AC-3 Known text converts as expected under both code pages
Given a fixture holding letters, digits, space and the punctuation that the legacy layouts use, encoded in 037 and in 1047
When each is converted with its own code page
Then both give the same expected ASCII text recorded in the test, and converting a 037 file with 1047 gives a different result for the bytes where the two pages differ
Basis: .claude/skills/parity-testing/SKILL.md sections 2 and 4

### AC-4 Round trip
Given an ASCII fixed-width fixture
When it is encoded with a code page in the test and converted back with the same page
Then the result equals the original bytes
Basis: .claude/skills/parity-testing/SKILL.md section 4

### AC-5 Record length is verified and non-text bytes are reported
Given a file whose size is not a multiple of the record length given with --lrecl, or a byte that converts to a character outside printable ASCII
When the tool runs
Then it exits non-zero with a message naming the file size and record length, or the byte offset and value of the offending byte, and never writes a replacement character
Basis: D-024, .claude/skills/parity-testing/SKILL.md section 2 (exact record lengths)

### AC-6 The original is never touched
Given an input path and an output path that is the same file
When the tool runs
Then it refuses with a message, and in every run the input file's sha256 is unchanged
Basis: .claude/skills/parity-testing/SKILL.md section 4 step 1 (keep the originals)

## Out of scope
- Capturing the legacy outputs (done in the other environment)
- Packed-decimal or binary fields (the legacy layouts hold display fields only)
- Comparison of the converted files (par-harness)

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: none (supports the Phase 9 run of JCL/PRVDAY01.jcl and JCL/PRVMTH01.jcl; capture scaffolding in aws-transform/07-test-data-collection)
Decisions: D-024 FIX: explicit errors for bad code pages, sizes and bytes

## Technical notes (agent-owned)
Service: tools/data. Endpoints/events: none. Roles: none.
