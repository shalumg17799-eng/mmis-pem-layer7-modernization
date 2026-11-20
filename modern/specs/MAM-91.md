---
key: MAM-91
localId: L-S078
epic: MAM-15
service: parity
type: Story
title: "Parity: run the legacy jobs on Hercules and compare their outputs with the modern system (deferred)"
slug: par-harness
specVersion: 1
descriptionSha256: 8056733d567cd7928a2caf44f104918fd6fb167e06721bbd202ede470b24579b
jiraUpdated: 2026-11-18T09:23:37.560-0600
rules: []
decisions: [D-021, D-020, D-006]
dependsOn: [MAM-53, MAM-42, MAM-44, MAM-56, MAM-32, MAM-45, MAM-55, MAM-67, MAM-75, MAM-43]
mainJourney: false
labels: [svc-parity, kag-L-S078, agentic, decision-D-021, decision-D-020, decision-D-006]
builtCommit:
testsSha256:
---
## Business requirement
To prove that the new system reproduces the old one, the old batch jobs will be run on a mainframe emulator with the same fictitious inputs, their output files captured, and compared record by record with the new system's output. Every difference must be explained by an approved decision or become a defect. This work waits until the legacy outputs exist.

## Acceptance criteria
### AC-1 Without golden files the run stops and says what to capture
Given no files in modern/parity/golden
When the parity phase is started
Then it prints the capture checklist, lists the synthetic input files under modern/testdata/fixed-width to send to the legacy environment, sets phase 9 to WAITING FOR LEGACY OUTPUTS in PROGRESS.md and stops
Basis: .claude/commands/phase-9-parity.md (Preconditions), .claude/skills/parity-testing/SKILL.md section 2

### AC-2 Golden files are stored with a manifest
Given captured runs of PRVDAY01, PRVMTH01, PRVSAN01 and PRVDIR01 (the last two with ad-hoc JCL)
When they are placed under modern/parity/golden/<job>/<run-id>
Then each run has inputs, before, after and logs folders and a manifest.json with the job, the business date 2026-01-15, the code page, file names, record lengths and sha256 of each file
Basis: .claude/skills/parity-testing/SKILL.md sections 2 and 3

### AC-3 Legacy files are converted with the recorded code page
Given golden files captured in binary mode
When the converter runs with the code page from the manifest
Then converted files exist next to the originals, which are unchanged, and record lengths match the manifest
Basis: .claude/skills/parity-testing/SKILL.md section 4 step 1, .claude/agents/data-engineer.md deliverable 5

### AC-4 The modern system runs the same inputs on the same date
Given the same synthetic inputs and the business date 2026-01-15
When the modern jobs are run through their legacy-format adapters
Then they produce APPERROR, SCRNOUT, REVALQ, TMSISOUT, DIROUT and the 130-byte master export, and both sides are parsed with the same layouts from libs/fixed-width
Basis: .claude/skills/parity-testing/SKILL.md section 4 steps 2 and 3, modern/docs/adr/ADR-005-legacy-format-adapters.md, D-007, D-020

### AC-5 Every difference is classified
Given both sides parsed
When records are matched by key (NPI, or position where there is no key) and compared field by field
Then each difference is MATCH, INTENDED (naming an approved decision D-0xx) or UNEXPLAINED, only run-dependent fields listed in the manifest are normalised, and no business field is
Basis: .claude/skills/parity-testing/SKILL.md section 4 steps 4 to 6

### AC-6 The report lists intended and unexplained differences
Given a finished comparison
When modern/docs/reports/parity-<job>-<run-id>.md is read
Then it has a summary per file with MATCH, INTENDED and UNEXPLAINED counts, a table of INTENDED differences with their decision IDs (for example D-006 for sanction S giving DENIED in the legacy and SUSPENDED in the modern system) and a table of UNEXPLAINED differences with file, key, field, legacy value, modern value and likely cause
Basis: .claude/skills/parity-testing/SKILL.md section 5, D-006

### AC-7 A human decides every unexplained difference
Given an UNEXPLAINED difference
When it is shown to the user
Then the parity checker has not marked it INTENDED on its own; if the modern system is wrong a Defect story is created, and if the legacy is wrong and the modern behaviour stays a new decision D-0xx is recorded; the comparison is re-run until there are zero UNEXPLAINED differences
Basis: .claude/commands/phase-9-parity.md steps 3 and 4, .claude/skills/parity-testing/SKILL.md section 5

### AC-8 The legacy run is patched outside the source folder
Given the Hercules run of PRVMTH01 and PRVDAY01
When the jobs are prepared
Then PRVTMS01 runs from a patched copy in which the identifier past column 72 (BATCH/PRVTMS01.cbl:114) is fixed, the undefined &DATE and &BATCH_DATE are given explicit values, source-code/ is unchanged, and the manifest records each patch
Basis: D-021, D-020, CLAUDE.md sections 7 and 11 (source-code is read-only), modern/docs/PROGRESS.md (Decisions 2026-11-18: Phase 9 legacy outputs from the local mmis-hercules container)

## Out of scope
- Running the legacy jobs (done by the user in the Hercules container)
- PRVDAY01 STEP020 SORT, whose output is never read (D-009 DROP)
- The Medicare reciprocity check (D-010 DROP) and PRVDEAD1 (D-011 DROP)
- Fixing differences (Defect stories through the normal build loop)

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: JCL/PRVDAY01.jcl (STEP010, STEP030, STEP040; &DATE and &BATCH_DATE undefined); JCL/PRVMTH01.jcl (STEP010-STEP030); BATCH/PRVSAN01.cbl and BATCH/PRVDIR01.cbl (no JCL exists; ad-hoc JCL needed); BATCH/PRVTMS01.cbl:114 (identifier past column 72, D-021)
Decisions: D-021 FIX: not applicable to the new system; the Phase 9 Hercules run patches the PRVTMS01 line in a copy; D-020 FIX: the business date is an explicit parameter, also for the legacy run; D-006 FIX: sanction S gives SUSPENDED, so the legacy DENIED is a proposed INTENDED difference

## Technical notes (agent-owned)
Service: parity. Endpoints/events: none. Roles: none.
