---
description: Phase 1. Connect Neo4j, build the knowledge graph from the legacy code and AWS Transform output, and validate it.
---

# Phase 1: Knowledge graph

**Goal:** every legacy fact and every AWS finding is in Neo4j, loaded by repeatable
scripts, with counts that match the sources exactly.

## Preconditions
Phase 0 is `APPROVED` in `modern/docs/PROGRESS.md`. If not, stop.

## Steps

1. **Connection details (ask now).** Ask the user:
   - "Do you already have a Neo4j 5.x database for this work, or should I start a local
     one in Docker?"
   - **Existing:** ask in chat for the URI (e.g. `neo4j+s://…` or `bolt://host:7687`),
     the username and the database name. Ask them to put all four values in `.env`
     (`NEO4J_URI`, `NEO4J_USERNAME`, `NEO4J_PASSWORD`, `NEO4J_DATABASE`). **Never ask for
     the password in chat.**
   - **Local:** ask them to choose a password and write it in `.env` as `NEO4J_PASSWORD`,
     with `NEO4J_URI=bolt://localhost:7687`, `NEO4J_USERNAME=neo4j`,
     `NEO4J_DATABASE=neo4j`. Then give them this command to run themselves (it reads the
     password from their shell):
     `docker run -d --name pem-neo4j -p 7474:7474 -p 7687:7687 -e NEO4J_AUTH=neo4j/$NEO4J_PASSWORD neo4j:5.26-community`
     Use the latest 5.x tag available at the time, and pin it.
2. **MCP configuration.** Create or merge `.mcp.json` at the root with this server.
   Values are environment references only:
   ```json
   { "mcpServers": { "neo4j": { "command": "uvx", "args": ["mcp-neo4j-cypher"],
       "env": { "NEO4J_URI": "${NEO4J_URI}", "NEO4J_USERNAME": "${NEO4J_USERNAME}",
                "NEO4J_PASSWORD": "${NEO4J_PASSWORD}", "NEO4J_DATABASE": "${NEO4J_DATABASE}" } } } }
   ```
   Pin the package version you verify works (`mcp-neo4j-cypher==<version>`) and record it.
3. **Restart.** Tell the user: "Restart Claude Code with the environment loaded
   (HOW-TO-RUN section 3), approve the `neo4j` MCP server when asked, then run
   `/phase-1-knowledge-graph` again." Write `Phase 1: waiting for Neo4j restart` in the
   PROGRESS notes and **stop**. On the next run, continue from step 4 if that note is
   present.
4. **Verify the connection** without printing secrets:
   `for v in NEO4J_URI NEO4J_USERNAME NEO4J_PASSWORD NEO4J_DATABASE; do if printenv "$v" >/dev/null; then echo "$v set"; else echo "$v MISSING"; fi; done`
   Then call the Neo4j MCP schema tool. Record in PROGRESS the MCP tool names you see
   (for example the read, write and schema tools). If it fails, show the error and stop.
5. **Delegate to kag-curator** (Phase 1 loaders in its file):
   - `constraints.cypher` and loaders `load_01_source.py` … `load_08_missing.py`
     covering: programs, paragraphs, copybooks, records, fields, jobs, steps,
     transactions; the dependency graph; lineage; the data dictionary; the BRE rules and
     key functionalities; the AWS test plan; the 12 anti-patterns; the missing items.
   - Parser unit tests (pytest) must pass.
   - Run `load_all.sh` **twice**. The second run must not change any count (idempotency).
   - Run validation and write `modern/docs/analysis/graph-report.md` with a table of
     check, expected, actual, OK.
6. **Spot check (you, the orchestrator).** Pick 3 random rule IDs. Compare the graph
   (MCP read) with the BRE JSON: name, Given, When and Then must match exactly. Pick 1
   dataset and confirm its readers/writers match `program_to_dsn.csv`.
7. Commit: `feat(phase-1): knowledge graph loaders and validation`.

## Exit checks
- [ ] Every count in kag-ontology section 6 matches, or the difference is explained.
- [ ] The second load changed nothing.
- [ ] The spot checks match.
- [ ] No secret appears in any committed file (`git grep -n "NEO4J_PASSWORD="` finds only `.env.example`).

## Finish
PROGRESS: phase 1 → `AWAITING APPROVAL`; add `Neo4j | 1 | MCP neo4j + loaders via env` to
Connections. Give the phase report. Next: `/phase-2-legacy-analysis`. **Stop.**
