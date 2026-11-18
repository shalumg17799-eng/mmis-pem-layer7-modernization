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
