---
description: Phase 2. Verify every legacy rule against the COBOL, document job flow and layouts, build the defect register, and get the user's business decisions.
---

# Phase 2: Legacy analysis

**Goal:** a verified specification. Every rule has line evidence, every defect has a
decision, and every business question is answered or explicitly deferred.

## Preconditions
Phase 1 is `APPROVED`.

## Steps

1. **Per-program verification.** Delegate to legacy-analyst, one program per run, at most
   3 in parallel, in this order: PRVENR01, PRVSCR01, PRVRSK01, PRVREV01, PRVTMS01,
   PRVSAN01, PRVDIR01, PRVCICS1, PRVDEAD1.
   - Each run writes its own `rule-verification-<PROGRAM>.csv`.
   - When all 9 are done, merge them into `rule-verification.csv` (header once, sorted by
     rule_id).
2. **Cross-program analysis.** One legacy-analyst run for: `job-flow.md`,
   `data-layouts.md`, `defect-register.md`, `questions.md`. It must confirm or refute
   D-019 (who sets ACTIVE) with evidence.
3. **Your checks (orchestrator):**
   - `rule-verification.csv` has one row for each of the 403 AWS rules, plus any
     `-ADD-` rows. No row is `UNVERIFIED`.
   - Each `MISMATCH` and `NOT_FOUND` row has a note, and has a defect or a question.
   - The anti-pattern self-check found 12 of 12.
   - Every defect whose decision is `OPEN` has a matching question.
4. **Load.** kag-curator runs the Phase 2 loader. Re-run validation.
5. **Business decisions (human in the loop).** Show the user:
   - a summary (counts by status; new rules found; new defects)
   - the defect table: ID, finding, recommended decision
   - the open questions: each with its options and recommended default

   Ask: "Reply with `accept all defaults`, or give decisions per ID (e.g.
   `D-006: SUSPENDED, D-007: 130`). Anything you want to leave open, say `defer D-0xx`:
   we will build with the default and flag it."
6. **Record the answers.**
   - In `defect-register.md`: Decision, Decided by = `user`, Date.
   - In `questions.md`: status `ANSWERED` with the answer, or `DEFERRED` (default used).
   - Add a row to the PROGRESS decisions log for each decision.
   - kag-curator re-runs the Phase 2 loader.
7. Commit: `docs(phase-2): verified rules, defect register, decisions`.

## Exit checks
- [ ] 403 rules verified, each with line evidence (or Dropped by decision).
- [ ] 12 of 12 anti-patterns found.
- [ ] No defect is `OPEN`. Each is decided, or DEFERRED with its default recorded.
- [ ] The graph shows the verification status and decisions (spot-check 3 rules).

## Finish
PROGRESS: phase 2 → `AWAITING APPROVAL`. Give the phase report. Next:
`/phase-3-architecture`. **Stop.**
