# PEM modernization progress

Updated by Claude at the end of every phase. Status values: NOT STARTED, IN PROGRESS,
AWAITING APPROVAL, APPROVED.

## Working mode (standing instruction from the user, 2026-11-18)

Replaces "stop at the end of every phase" in MODERNIZATION.md section 4.

1. Work without stopping until blocked: Phase 1, then 2, then 3, back to back. A phase whose exit checks **all** pass is
   recorded as `APPROVED (standing instruction, <date>)` and the next phase starts. A phase with a check that cannot be
   run yet (for example a graph check while Neo4j is unreachable) is **not** approved; it stays open and is listed below.
2. Stop and ask only when: (a) a credential or connection is missing, (b) an exit check still fails after 2 fix attempts,
   (c) a business question has no recommended default, (d) before creating anything in Jira (Phase 4).
3. Questions go to `modern/docs/analysis/questions.md`, numbered, each with a recommended answer, and are asked together,
   once, at the end of a phase. A question with a sensible default gets the default, marked `DEFAULT APPLIED - revisit`.
4. Neo4j is not connected yet: do all offline work, keep "Pending graph loads" below, and when the four `NEO4J_*` variables
   are visible (`printenv`, never print values) run every pending load, the validation, and a second load that must change nothing.
5. Visible progress: tick the checklist below, add one line to the Activity log after every step, run subagents one at a time
   and wait for each, then write 2-3 lines about its result; commit after each step as `phase<n>.<step>: <summary>`.

## Activity log (newest first)

| Time | Step | What was done | Files |
|---|---|---|---|
| 2026-11-18 18:20 | phase1.5a | kag-curator stopped (it had written nothing); parsers written by the orchestrator per the curator's file; 34 parser unit tests pass | `modern/kag/loaders/parsers.py`, `modern/kag/tests/*`, `constraints.cypher`, `requirements.txt` |
| 2026-11-18 17:58 | phase1.2 | Neo4j MCP: project venv, `mcp-neo4j-cypher==0.6.0`, read-only, env references only | `.mcp.json`, `modern/kag/.venv` (git-ignored) |
| 2026-11-18 17:58 | phase1.0 | Standing working mode recorded; Q1 revised (not bug-for-bug); decisions D-001 to D-020 recorded | `PROGRESS.md`, `questions.md`, `MODERNIZATION.md` |

## Current phase checklist: Phase 1 (knowledge graph)

- [x] 1 Connection details: local Neo4j Desktop, instance `KAG`, database `medicade-kag`
- [x] 2 MCP configuration (`.mcp.json`, project venv, read-only)
- [ ] 3 Restart Claude Code with the `NEO4J_*` variables loaded (**user action, pending**)
- [ ] 4 Verify the connection (needs step 3)
- [ ] 5a kag-curator writes schema, loaders 01-08, parser tests, `--dry-run` (**running**)
- [ ] 5b Orchestrator checks the dry-run counts against the source files
- [ ] 5c Run loaders twice plus validation; second run must change nothing (needs Neo4j)
- [ ] 6 Spot check 3 rule IDs and 1 dataset (needs Neo4j)
- [ ] 7 Commit

## Pending graph loads (run all of them as soon as `NEO4J_*` is visible)

- Phase 1: `constraints.cypher`, loaders `load_01` to `load_08`, `validate.py`, then the second load.
- Phase 2: rule-verification, defect and decision loader (added when Phase 2 finishes).
- Phase 3: services, contracts, tables and mappings loader (added when Phase 3 finishes).

## Phases

| Phase | Status | Approved on | Notes |
|---|---|---|---|
| 0 Setup | APPROVED | 2026-11-18 | Git stays local; push only when the user asks. `uv` is not used (see Decisions). |
| 1 Knowledge graph | IN PROGRESS | | Phase 1: waiting for Neo4j restart (MCP configured with a project venv; env vars not yet visible to Claude Code) |
| 2 Legacy analysis | NOT STARTED | | |
| 3 Architecture and UX | NOT STARTED | | |
| 4 Backlog | NOT STARTED | | |
| 5 Platform | NOT STARTED | | |
| 6 Services (one row per service below) | NOT STARTED | | |
| 7 UI (portal, console) | NOT STARTED | | |
| 8 E2E and hardening | NOT STARTED | | |
| 9 Parity (deferred) | NOT STARTED | | Legacy outputs will come from the local `mmis-hercules` container (see Decisions) |

## Services (Phase 6)

| Order | Service | Status | Approved on | Report |
|---|---|---|---|---|

## UI areas (Phase 7)

| Area | Status | Approved on | Report |
|---|---|---|---|
| portal | NOT STARTED | | |
| console | NOT STARTED | | |

## Decisions (newest first)

| Date | Decision | Made by | Where recorded |
|---|---|---|---|
| 2026-11-18 | **Q1 REVISED: not bug-for-bug.** Supersedes the earlier "yes" (option A), which is withdrawn. Business decisions D-001 to D-020 below take the recommended defaults. | user | `questions.md` Q1, this file |
| 2026-11-18 | D-020: FIX. The business date is an explicit job parameter (`&DATE`, `&BATCH_DATE` were never defined). Accepted recommended default. | user | this file |
| 2026-11-18 | D-019: an analyst approval sets a provider to ACTIVE after a Pass screening. Accepted recommended default. Phase 2 still confirms or refutes, with evidence, that no legacy program sets ACTIVE except sanction reinstate (`R`). | user | this file |
| 2026-11-18 | D-018: FIX. SCREEN.RESULTS and SANCTION.MASTER become real tables in their own services. Accepted recommended default. | user | this file |
| 2026-11-18 | D-017: FIX. BATCH_ID becomes a real job parameter. Accepted recommended default. | user | this file |
| 2026-11-18 | D-016: FIX. Implement error codes E101 to E205 as validation / screening results. Accepted recommended default. | user | this file |
| 2026-11-18 | D-015: PRESERVE the YYDDD period in the legacy-format T-MSIS extract; ISO dates internally. Accepted recommended default. | user | this file |
| 2026-11-18 | D-014: FIX. provider-service is the single owner of provider identity (no DB2 + VSAM duplicate). Accepted recommended default. | user | this file |
| 2026-11-18 | D-013: FIX. sanctions-service calls provider-service's API instead of rewriting its master. Accepted recommended default. | user | this file |
| 2026-11-18 | D-012: REPLACE the dynamic CALL to the missing PRVNT* programs with a notification-service. Accepted recommended default. | user | this file |
| 2026-11-18 | D-011: DROP PRVDEAD1 (orphan, discontinued 2019). Accepted recommended default. | user | this file |
| 2026-11-18 | D-010: DROP the Medicare-reciprocity check in PRVSCR01 (flag never set). Accepted recommended default. | user | this file |
| 2026-11-18 | D-009: DROP the PRVDAY01 SORT step (wrong offset, output never used). Accepted recommended default. | user | this file |
| 2026-11-18 | D-008: keep provider type codes IN/GR/FA plus a mapping table to the feed codes. Accepted recommended default. | user | this file |
| 2026-11-18 | D-007: the provider record is 130 bytes (the copybook is right; the 121 in the FDs and IDCAMS loses OWNER-NPI). Accepted recommended default. | user | this file |
| 2026-11-18 | D-006: sanction action `S` sets a new SUSPENDED status (not DENIED). Accepted recommended default. | user | this file |
| 2026-11-18 | D-005: REPLACE the four stub screening checks with a real screening service using mocked external sources. Accepted recommended default. | user | this file |
| 2026-11-18 | D-004: FIX. 4-digit years everywhere (no 2-digit pivot). Accepted recommended default. | user | this file |
| 2026-11-18 | D-003: FIX. Revalidation due date is set at enrolment approval and at each completed revalidation (not reset to today + 5 years on every run). Accepted recommended default. | user | this file |
| 2026-11-18 | D-002: FIX. The PRVRSK01 risk tier is saved (REWRITE reached). Accepted recommended default. | user | this file |
| 2026-11-18 | D-001: FIX. Process all applications; reject each invalid one with exactly one error row. Accepted recommended default. | user | this file |
| 2026-11-18 | **Standing working mode** replaces "stop at the end of every phase" (MODERNIZATION.md section 4): see "Working mode" at the top of this file. | user | this file, `MODERNIZATION.md` section 4 |
| 2026-11-18 | Database `medicade-kag` is dedicated to the PEM knowledge graph and lives only in the `KAG` Neo4j instance. Nothing from this project goes into any other database or instance. | user | this file |
| 2026-11-18 | Phase 1 runs **without `uv`**. The Neo4j MCP server `mcp-neo4j-cypher==0.6.0` is installed in the project-local venv `modern/kag/.venv` (git-ignored; no system software installed) and `.mcp.json` points at it. The connection stays **local** (Neo4j Desktop). `NEO4J_READ_ONLY=true` is set on the MCP server so only the loader scripts write to the graph (MODERNIZATION.md rule 7); this adds one key to the kit's MCP snippet. | user (no uv, local), Claude (venv, read-only) | this file, `.mcp.json` |
| 2026-11-18 | Neo4j target for Phase 1: Neo4j Desktop instance `KAG`, database `medicade-kag` (spelled as given; Phase 1 verifies it exists, and asks before creating anything). URI and username still to be confirmed. The password goes only in `.env`. | user | this file |
| 2026-11-18 | No changes to the COBOL (`source-code/` stays read-only). Whether the new services reproduce the legacy defects was settled by the revised Q1: **no**, per D-001 to D-020. | user | this file, `questions.md` Q1 |
| 2026-11-18 | Git stays local. Nothing is pushed to GitHub until the user asks; then push as the user directs. | user | this file |
| 2026-11-18 | Phase 1 uses the user's **local Neo4j Desktop** (Bolt `127.0.0.1:7687`), not a Docker Neo4j. Its discovery endpoint reports version `2026.03.1`, edition `enterprise` (the kit text assumes 5.x: driver/MCP compatibility is checked in Phase 1). Recommended in Phase 1: a dedicated database for PEM (Enterprise allows several) so constraints and nodes do not mix with the user's other graphs. | user (choice), Claude (version check) | this file |
| 2026-11-18 | Phase 9 legacy outputs are produced by running the legacy jobs in the local Docker container `mmis-hercules` (MVS TK5 on Hercules). Container state and the `*-LAB.jcl` stubs are checked when Phase 9 starts. | user | this file |
| 2026-11-18 | Git remote is `shalumg17799-eng/mmis-pem-layer7-modernization` (public, empty when checked). Baseline is on `main`, work on `modernization`. Repo-local `core.autocrlf=false` so legacy files keep their exact bytes. Nothing pushed until the user confirms. | user (remote), Claude (autocrlf) | this file |
| 2026-11-18 | The duplicate extract of the kit under `_archive/` was removed by the user; `_archive/pem-agent-kit.zip` stays as the backup. | user | this file |
| 2026-11-18 | Docker is used as a separate stack for this application. Other projects' containers on the machine are not reused or modified. | user | this file |

## Open questions

See `modern/docs/analysis/questions.md`. Count open: 0

## Connections configured (names only, never values)

| Connection | Configured in phase | How |
|---|---|---|
| Git remote `origin` (GitHub) | 0 | `git remote`, HTTPS, no credentials stored by Claude; not pushed yet |
