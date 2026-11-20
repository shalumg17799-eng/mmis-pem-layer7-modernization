---
description: Phase 9 (deferred). Compare legacy job outputs captured in another environment with the modern system's outputs, and resolve every difference.
argument-hint: [job name, e.g. PRVDAY01; empty = all jobs with golden files]
---

# Phase 9: Parity with legacy outputs

**Goal:** prove the modern system reproduces legacy results, except for differences that
an approved decision explains.

## Preconditions
- Phase 8 is `APPROVED`, or the user explicitly asks to run parity for specific
  services earlier.
- Golden files exist in `modern/parity/golden/`. If they do not:
  1. print the capture checklist from `.claude/skills/parity-testing/SKILL.md`, section 2
  2. state which synthetic input files to send to the legacy environment (paths under
     `modern/testdata/fixed-width/`)
  3. set phase 9 to `WAITING FOR LEGACY OUTPUTS` in PROGRESS
  4. **stop**

## Steps

1. For each job with golden files (or `$ARGUMENTS` only), delegate to parity-checker.
2. Show the user the summary per job and per file, and every UNEXPLAINED difference.
3. **Human decision per UNEXPLAINED difference:**
   - **Modern is wrong:** backlog-planner creates a Defect story, and it is fixed through
     the Phase 6 loop.
   - **Legacy is wrong and we keep the modern behaviour:** record a new decision D-0xx in
     the defect register; kag-curator reloads; the difference becomes INTENDED.
4. Re-run parity until there are zero UNEXPLAINED differences.
5. No commit: the user commits only when they say "commit" (rule of 2026-11-18).

## Exit checks
- [ ] Zero UNEXPLAINED differences for every captured job.
- [ ] Every INTENDED difference cites an approved decision.

## Finish
PROGRESS: phase 9 → `AWAITING APPROVAL`. Give the phase report. **Stop.**
