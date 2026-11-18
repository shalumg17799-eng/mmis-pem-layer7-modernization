# Knowledge graph validation report

Mode: **online** (Cypher run against the dedicated database)
Generated: 2026-11-18T12:49:24+00:00

| Check | Expected | Actual | OK |
|---|---|---|---|
| Program nodes, missing:false, kind batch or cics | 9 | 9 | OK |
| Copybook nodes | 5 | 5 | OK |
| Job nodes with role 'pipeline' (PRVDAY01, PRVMTH01) | 2 | 2 | OK |
| Job nodes that are a cataloged proc (PRVSCRPR) | 1 | 1 | OK |
| Job nodes in total [explained: 2 pipeline + 1 proc + the 2 VSAM-definition jobs PRVVSM01/02, which the skill's '2 plus proc' leaves out] | 5 | 5 | OK |
| BusinessRule nodes | 403 | 403 | OK |
| BusinessRule nodes for PRVENR01 | 68 | 68 | OK |
| BusinessRule nodes for PRVSCR01 | 62 | 62 | OK |
| BusinessRule nodes for PRVSAN01 | 58 | 58 | OK |
| BusinessRule nodes for PRVTMS01 | 55 | 55 | OK |
| BusinessRule nodes for PRVRSK01 | 48 | 48 | OK |
| BusinessRule nodes for PRVCICS1 | 45 | 45 | OK |
| BusinessRule nodes for PRVDIR01 | 29 | 29 | OK |
| BusinessRule nodes for PRVREV01 | 24 | 24 | OK |
| BusinessRule nodes for PRVDEAD1 | 14 | 14 | OK |
| BusinessRule nodes without IMPLEMENTED_IN | 0 | 0 | OK |
| Program nodes with missing:true | 4 | 4 | OK |
| Names of the missing programs | PRVNTGEN,PRVNTRST,PRVNTSUS,PRVNTTRM | PRVNTGEN,PRVNTRST,PRVNTSUS,PRVNTTRM | OK |
| AntiPattern nodes | 12 | 12 | OK |
| PRVRECRD record length in bytes (copybook; FDs and IDCAMS say 121) | 130 | 130 | OK |
| Records whose FD length differs from their layout (7 batch FDs: 121 vs 130) | 7 | 7 | OK |
| Step nodes (PRVDAY01 5, PRVMTH01 3, PRVVSM01 1, PRVVSM02 1, proc PRVSCRPR 1) | 11 | 11 | OK |
| RUNS relationships (AWS lists 11 'Exec program' edges) | 11 | 11 | OK |
| INCLUDES relationships (AWS lists 16 + 2 COPY edges) | 18 | 18 | OK |
| REDEFINES relationships (PRVRECRD) | 2 | 2 | OK |
| KeyFunctionality nodes [explained: AWS repeats the id PRVTMS01:t, so 85 become 84] | 84 | 84 | OK |
| PART_OF relationships [explained: 88 of 403 rules sit in no key functionality in the AWS output] | 315 | 315 | OK |
| BusinessRule nodes without PART_OF | 88 | 88 | OK |
| TestCase nodes from the AWS plan | 4 | 4 | OK |
| DB2 columns as Field nodes (the DDL defines 26; AWS's dictionary lists 22) | 26 | 26 | OK |
| Programs that have paragraphs (the 9 COBOL programs) | 9 | 9 | OK |
| Transaction PINQ is flagged as missing its CICS definition | True | True | OK |

## Totals, idempotency and spot checks

- **Database:** the dedicated `medicaid-kag` on the local KAG instance (Neo4j 2026.03.1 Enterprise, driver 6.4.1). It was empty before the load.
- **Size after the first load:** 792 nodes and 1,060 relationships.
- **Idempotency:** a second run of `load_all.sh` finished without error and `validate.py --compare-counts` reported **no change in any count**.
  The comparison tool was itself tested: against a deliberately altered snapshot it reports `CHANGED` and exits 1.
- **Spot checks (seed 20261118):** rules `R-PRVSCR01-cbl-00050`, `R-PRVENR01-cbl-00017`, `R-PRVREV01-cbl-00020` match the AWS JSON exactly on name, Given, When and Then.
  Dataset `MMPRV.PROV.APPS.D&DATE` (random) and `MMPRV.PROVIDER.MASTER.KSDS` (9 program/access pairs) match `program_to_dsn.csv` exactly.
- **Tests:** 73 pytest tests pass (35 parser, 38 loader and validation tests); `git grep` finds the password variable only in `.env.example`.

## Differences from the ontology's expected counts, all explained (no expectation was changed)

| Item | Skill says | Graph has | Reason |
|---|---|---|---|
| Job nodes | 2 plus the proc | 5 | The two VSAM-definition jobs `PRVVSM01` / `PRVVSM02` are JCL jobs too. They carry `role='vsam-definition'`; the 2 pipeline jobs and 1 proc match. |
| KeyFunctionality | not stated | 84 | AWS lists 85, but repeats the id `PRVTMS01:t`; the key `<program>:<aws id>` merges them. |
| BusinessRule with `PART_OF` | not stated | 315 of 403 | 88 rules appear only in AWS's flat rule list, in no key functionality. |

## New facts the load established (the CLAUDE.md findings are confirmed by the graph)

- 7 FD records declare 121 bytes while the layout is 130 (the `PRVRECRD` copybook), as already reported.
- AWS's data dictionary lists **22 of the 26 DB2 columns** (it has 1 of the 5 `MMPRV.SANCTION` columns). The graph has all 26, parsed from the DDL.
- AWS's dictionary shows length 0 for the 10 records that `COPY` a copybook (it does not expand `COPY`); the graph resolves them to the copybook length (130 for the provider master).
- Where both exist, AWS's field positions equal our parsed offsets: **0 differences**. AWS's "total lines" is always the real line count + 1.
- 204 dictionary rows: 167 matched a parsed field or record, 31 are 88-level conditions (kept as `values` on the parent field), 6 are `FILLER` entries with no stable name.
- All 9 COBOL programs have paragraphs; the 4 missing `PRVNT*` programs are linked by dynamic `CALLS` from `PRVSAN01`.

## Not verified

- The Neo4j **MCP** read path was not exercised: its tools are not available until Claude Code is restarted with the `NEO4J_*` variables loaded. Every check above used the Python driver instead.
- Rule `evidence` and `verification` are all `UNVERIFIED` and `lines` is empty by design; Phase 2 fills them.
- Phase 2 and Phase 3 data (rule verification, defects, services, mappings) are not in the graph yet; their loaders are added in those phases.
