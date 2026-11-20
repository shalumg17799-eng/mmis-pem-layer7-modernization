# Phase report: Phase 2, legacy analysis

```
Phase / item:      Phase 2 (legacy analysis)
Outcome:           DONE (APPROVED under the standing instruction, 2026-11-18)
What was done:     - All 403 AWS rules verified against the COBOL with line evidence: 339 VERIFIED, 40 MISMATCH, 3 NOT_FOUND, 21 DROPPED (D-010, D-011)
                   - 52 rules AWS missed added (R-<PROGRAM>-ADD-nn); every MISMATCH / NOT_FOUND linked to a defect or question
                   - 9 program analyses; job flow; data layouts (generated); defect register (29 defects, none OPEN); D-019 confirmed with evidence
                   - Anti-pattern self-check: 12 of 12
                   - load_09_analysis: verification, added rules, defects, questions, regulations loaded and validated
Files created/changed: modern/docs/analysis/{rule-verification*.csv, programs/*.md, job-flow.md, data-layouts.md, defect-register.md, questions.md}
                   modern/kag/loaders/load_09_analysis.py, validate.py, validate.cypher, tests
Checks run:        orchestrator checks (403 rows, none UNVERIFIED, every MISMATCH/NOT_FOUND linked, 12/12 anti-patterns, no OPEN defect) -> pass ;
                   validate.py (online) -> 45 of 45 ; idempotency -> no change ; spot check 3 rules in the graph -> exact match
Not verified:      Behaviour marked INFERRED (fall-through stops the load, ALTER skips the REWRITE, PRVTMS01 compile failure, PINQ map behaviour)
                   was read from the code, not executed: no compiler or emulator here. Phase 9 (Hercules) can confirm it.
Questions for you: Q-005 to Q-010 (defaults applied) - see questions.md
Next step:         Phase 3 (done in the same run)
```

The 3 NOT_FOUND rules have no code to cite by definition; each is covered by a REPLACE decision (D-012, D-022).
