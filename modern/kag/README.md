# modern/kag: PEM knowledge graph (Neo4j)

Everything the legacy estate and the AWS Transform output say, loaded into one dedicated Neo4j database by repeatable scripts.
Ontology: `.claude/skills/kag-ontology/SKILL.md`. The graph is only ever written by these loaders; the MCP server is read-only.

1. Python venv (git-ignored): `python -m venv .venv` then `.venv/Scripts/python.exe -m pip install -r requirements.txt`.
2. Put `NEO4J_URI`, `NEO4J_USERNAME`, `NEO4J_PASSWORD`, `NEO4J_DATABASE` in the root `.env` (never in chat or in a file you commit),
   then start Claude Code / a shell with them loaded (HOW-TO-RUN.md section 3). The loaders refuse the default `neo4j` and `system` databases.
3. `./load_all.sh` writes constraints then `loaders/load_01_*.py` to `load_08_*.py` in order. Re-running changes nothing.
4. `./load_all.sh --dry-run` parses and counts without a database (output saved in `dry-run-counts.txt`).
5. `python validate.py` runs the checks in `validate.cypher` against the database; `python validate.py --offline` computes them in memory.
6. Idempotency proof: `python validate.py --save-counts F`, run `./load_all.sh` again, then `python validate.py --compare-counts F`.
7. Tests: `.venv/Scripts/python.exe -m pytest tests` (parsers, loaders, validation; no database needed).

| Loader | Loads |
|---|---|
| `load_00_constraints` | uniqueness constraints and lookup indexes |
| `load_01_source` | programs, paragraphs, copybooks, records, fields with byte offsets, jobs, steps, VSAM definitions, DB2 tables and columns (parsed from `source-code/`) |
| `load_02_dependencies` | cross-check of our COPY edges against the AWS dependency graph |
| `load_03_lineage` | READS / WRITES / REWRITES between steps, programs and datasets; dataset record layouts |
| `load_04_data_dictionary` | business definitions on fields and records (update-only) |
| `load_05_business_rules` | the 403 AWS rules (UNVERIFIED) and 84 key functionalities |
| `load_06_test_plan` | the 4 AWS test cases (ontology extension: `level='aws-plan'`, `COVERS` to the job) |
| `load_07_anti_patterns` | the 12 seeded anti-patterns, line numbers found by regex in the source |
| `load_08_missing` | missing programs (with the dynamic CALLS), CICS / BMS / dataset gaps |

Ontology extensions (additive): `INCLUDES` also from a Copybook to a Copybook; `HAS_ANTIPATTERN` also from a Copybook, Job or Dataset;
Job has `role` (pipeline, vsam-definition, proc); `TestCase.level='aws-plan'` with `COVERS`. Provenance (`source`, `loader`, `loadedAt`) is set on create only.
