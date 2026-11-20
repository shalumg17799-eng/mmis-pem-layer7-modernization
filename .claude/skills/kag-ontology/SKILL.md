---
name: kag-ontology
description: The Neo4j knowledge graph for the PEM modernization: legacy code and AWS Transform output only. Covers node labels, relationships, required properties, loader conventions, validation queries and standard read queries. Use whenever reading from or writing to the graph.
---

# PEM knowledge graph (KAG)

**Scope (user decision 2026-11-19): Neo4j is the context engine for the legacy code and the AWS Transform output ONLY.** The labels below are the only ones in the graph. Design, backlog, tests and code are never loaded: traceability lives in `modern/docs/traceability/rule-trace.csv` (built by `modern/tools/trace/build_rule_trace.py`) and in each Jira story's "Legacy trace" section. The earlier delivery-layer data is archived in `modern/docs/traceability/graph-export/`.

## 1. Who writes and who reads

- **Writes:** only the `kag-curator` agent, and only by running Python loader scripts in
  `modern/kag/loaders/`. Graph writes are code: reviewable, repeatable, in git.
- **Reads:** any agent, through the Neo4j MCP read tool (or a read-only Python query).
- Never write the graph by hand through MCP. If something must change, change a loader
  or its input file and re-run it.

## 2. Three layers (never mix them on one node)

### Legacy fact layer (from `source-code/` and `aws-transform/`; never edited after load)

| Label | Key property | Other required properties |
|---|---|---|
| Program | `name` (e.g. PRVENR01) | `path`, `kind` (batch, cics, rexx), `app` (PEMAPP1, PEMAPP2), `missing` (bool), `loc` |
| Paragraph | `id` = `<program>:<paragraph>` | `program`, `startLine`, `endLine` |
| Copybook | `name` | `path` |
| Record | `id` = `<copybook or program>:<01-name>` | `length` |
| Field | `id` = `<record id>:<field name>` | `name`, `pic`, `start`, `length`, `level`, `redefines`, `values` (88-levels), `definition` |
| Dataset | `dsn` | `type` (VSAM KSDS, SEQ, GDG), `recordLength`, `ddName` |
| Job | `name` | `path` |
| Step | `id` = `<job>:<step>` | `seq`, `program`, `condition` |
| Transaction | `id` (PINQ) | `program`, `map`, `mapset` |
| AntiPattern | `id` (AP-01 … AP-12) | `name`, `program`, `lines` |

### Knowledge layer (analysis results)

| Label | Key property | Other required properties |
|---|---|---|
| BusinessRule | `ruleId` (e.g. R-PRVRSK01-cbl-00012) | `program`, `type`, `name`, `description`, `given`, `when`, `then`, `verification` (UNVERIFIED, VERIFIED, MISMATCH, NOT_FOUND, DROPPED), `evidence` (`file:start-end`) |
| KeyFunctionality | `id` = `<program>:<aws id>` | `title`, `level` |
| Defect | `id` (D-001 …) | `title`, `legacyBehaviour`, `decision` (FIX, PRESERVE, DROP, REPLACE, OPEN), `decidedBy`, `decidedOn` |
| Question | `id` (Q-001 …) | `text`, `options`, `recommended`, `status` (OPEN, ANSWERED), `answer` |
| Regulation | `id` (e.g. 42CFR455.450) | `title` |

### Delivery layer: not in the graph

Service, ApiEndpoint, Event, Table, Column, UiScreen, Epic, Story, AcceptanceCriterion, TestCase, CodeUnit and Decision are
not graph labels any more (removed 2026-11-19; exported to `modern/docs/traceability/graph-export/`). The AWS test plan
stays in `aws-transform/06-test-plan/test-plan.json`.

## 3. Relationships

| From | Relationship | To | Meaning |
|---|---|---|---|
| Job | HAS_STEP | Step | Step order in `seq` |
| Step | RUNS | Program | |
| Program | HAS_PARAGRAPH | Paragraph | |
| Program | INCLUDES | Copybook | COPY statement |
| Program | CALLS | Program | Static or dynamic (`dynamic:true`) |
| Program / Step | READS, WRITES, REWRITES | Dataset | From the lineage files |
| Copybook / Program | DEFINES | Record | |
| Record | HAS_FIELD | Field | |
| Field | REDEFINES | Field | |
| Dataset | USES_LAYOUT | Record | |
| Transaction | HANDLED_BY | Program | |
| Program | HAS_ANTIPATTERN | AntiPattern | |
| BusinessRule | IMPLEMENTED_IN | Program | `lines` property when verified |
| BusinessRule | PART_OF | KeyFunctionality | |
| BusinessRule | GOVERNED_BY | Regulation | |
| Defect | AFFECTS | BusinessRule or Program | |
| Question | ABOUT | any node | |

## 4. Required provenance on every node and relationship you create

- `source`: one of `source-code`, `aws-transform`, `legacy-analyst`, `architect`,
  `backlog-planner`, `build`, `human`.
- `loadedAt`: ISO timestamp.
- `loader`: loader script file name.

## 5. Loader conventions

- Python 3.11 with the `neo4j` driver. Put dependencies in `modern/kag/requirements.txt`
  and use a virtualenv at `modern/kag/.venv` (gitignored).
- Connection only from the environment variables `NEO4J_URI`, `NEO4J_USERNAME`,
  `NEO4J_PASSWORD` and `NEO4J_DATABASE`. Never print them.
- Create the constraints first (`modern/kag/constraints.cypher`): one uniqueness
  constraint per label on its key property.
- Always `MERGE` on the key property, then `SET` the other properties. Use `UNWIND`
  batches of 500. Re-running a loader must change nothing.
- One loader per input type, named `load_<nn>_<what>.py`, run in number order by
  `modern/kag/load_all.sh`.
- Path mapping: any path in AWS output that starts with `MMIS-PROVIDER-DEMO-TIER4/`
  refers to `source-code/`.

## 6. Validation (`modern/kag/validate.cypher`, run after every load)

Expected counts after Phase 1:

| Check | Expected |
|---|---|
| Program nodes with `missing:false` and kind batch or cics | 9 |
| Copybook nodes | 5 |
| Job nodes | 2 (PRVDAY01, PRVMTH01), plus proc PRVSCRPR as Job with `proc:true` |
| BusinessRule nodes | 403 |
| BusinessRule per program | PRVENR01 68, PRVSCR01 62, PRVSAN01 58, PRVTMS01 55, PRVRSK01 48, PRVCICS1 45, PRVDIR01 29, PRVREV01 24, PRVDEAD1 14 |
| BusinessRule without IMPLEMENTED_IN | 0 |
| Program nodes with `missing:true` | PRVNTGEN, PRVNTRST, PRVNTSUS, PRVNTTRM, plus the missing CICS/BMS items as Transaction/Map properties |
| AntiPattern nodes | 12 |

If a count differs, **do not adjust the expectation.** Investigate and report the reason.

## 7. Standard read queries

```cypher
// Rules for a program, with their verification status
MATCH (r:BusinessRule)-[:IMPLEMENTED_IN]->(p:Program {name:$program})
RETURN r.ruleId, r.name, r.verification, r.evidence ORDER BY r.ruleId;

// Layout of the record a dataset uses
MATCH (d:Dataset {dsn:$dsn})-[:USES_LAYOUT]->(rec)-[:HAS_FIELD]->(f)
RETURN f.name, f.pic, f.start, f.length, f.redefines ORDER BY f.start;

// Who reads and writes a dataset
MATCH (p)-[rel:READS|WRITES|REWRITES]->(d:Dataset {dsn:$dsn})
RETURN p.name, type(rel);

// Traceability (rule -> story -> AC -> test -> code): modern/docs/traceability/rule-trace.csv, not the graph.
```
