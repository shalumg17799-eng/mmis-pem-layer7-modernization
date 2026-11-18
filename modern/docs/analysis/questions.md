# Open business questions

## Q1. Does "migrate as is" include reproducing the legacy defects? (raised 2026-11-18, status: ANSWERED 2026-11-18, REVISED)

**Final answer (user, 2026-11-18): Q1 is NOT bug-for-bug.** The earlier "yes" (recorded as option A) is **superseded**.
Every item D-001 to D-020 in `.claude/skills/pem-legacy-estate/SKILL.md` section 4 takes its **recommended default**
(FIX, DROP, REPLACE or PRESERVE exactly as listed there). The four open items are decided as:
D-006 = a new SUSPENDED status; D-007 = the record is 130 bytes; D-008 = keep IN/GR/FA plus a mapping table;
D-019 = an analyst approval sets a provider to ACTIVE. Each is logged as "accepted recommended default, user, 2026-11-18".
The COBOL itself is never edited (`source-code/` stays read-only). The options table below is kept as history.

The user asked for the application to be migrated "as it is, no changes in the COBOL". The COBOL stays untouched
(`source-code/` is read-only). What is not yet decided is the behaviour of the new Java services where the legacy
code is defective (see `CLAUDE.md` sections 6 and 7; 12 seeded anti-patterns, several with compliance impact:
invalid applications reaching the master, 2-digit-year revalidation dates after 2065, stubbed screening).

| Option | Meaning | Effect |
|---|---|---|
| A (recommended default) | Bug-for-bug: reproduce legacy behaviour exactly, including defects. Each defect is logged in `defect-register.md` as "preserved by user decision". | Phase 9 parity against the Hercules outputs can pass; defects stay visible and can be fixed later by an explicit decision. |
| B | Same business behaviour, but fix the defects now. | Parity will show intended differences; each fix needs its own approved decision. |
| C | Bug-for-bug by default, with a per-defect switch to the corrected behaviour. | Parity passes; fixes can be turned on without a rewrite; more code and tests. |

Decide per defect in Phase 2 (the legacy-analyst lists them with line evidence). A single answer for all is fine too.

## Q2. Which Neo4j database holds the PEM graph? (raised 2026-11-18, status: ANSWERED 2026-11-18: option A, `medicaid-kag`; `medicade-kag` was a typo)

You gave `NEO4J_DATABASE=medicade-kag`. That database does not exist on the KAG instance. An **empty** database
`medicaid-kag` (online, read-write, 0 nodes, no constraints, created 2026-11-18 12:03) does, and looks like the one made for
this project. The other similar names on the instance (`nfcukag`, `rain-kag`) belong to other work and are not touched.

| Option | Effect |
|---|---|
| **A (recommended)**: use `medicaid-kag` | No new database; fix `NEO4J_DATABASE=medicaid-kag` in `.env`. |
| B: create a new database literally named `medicade-kag` | Leaves `medicaid-kag` unused. |

**Answer (user, 2026-11-18): use `medicaid-kag`; `medicade-kag` was a typo.** Fix `NEO4J_DATABASE` in `.env`.
The graph is rebuilt entirely by scripts, so switching later is a re-run of `load_all.sh`.

## Q3. Small additions to the graph ontology (raised 2026-11-18, status: DEFAULT APPLIED - revisit)

The kag-ontology skill has no home for four things the load needed. Applied by default, all additive (no label or key changed):

1. The AWS test plan: `TestCase` with `level='aws-plan'` and a `COVERS` relationship to the `Job`.
2. A COPY inside a copybook (`WRKSTORE` includes `SCRNREC` and `ERRCODES`): `INCLUDES` also from a `Copybook`.
3. `HAS_ANTIPATTERN` also from a `Copybook`, `Job` or `Dataset` (AP-09, AP-10, AP-11, AP-12 are not programs).
4. `Job.role` (`pipeline`, `vsam-definition`, `proc`), so the skill's "2 jobs plus the proc" check still holds.

**Recommended answer: accept all four.** The alternative is to drop them and leave those facts out of the graph.

## Q4. Where does per-story working state live? (raised 2026-11-18, status: DEFAULT APPLIED - revisit)

You asked for no separate folder for temporary files. The kit kept per-story state in a hidden `.work/<KEY>/` folder
(`red-report.md`, `red-commit`, `trace.json`). Applied by default: those files are audit records, so they live in
`modern/docs/reports/stories/<KEY>/`, committed with the story. Only the path text changed in 8 files under `.claude/`.

**Recommended answer: accept.** The alternative is to restore `.work/` (git-ignored) and keep them out of the repo.
