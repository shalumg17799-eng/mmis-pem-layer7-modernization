# Knowledge graph validation report

Mode: **online** (Cypher run against the dedicated database)
Generated: 2026-11-18T13:33:52+00:00

| Check | Expected | Actual | OK |
|---|---|---|---|
| Program nodes, missing:false, kind batch or cics | 9 | 9 | OK |
| Copybook nodes | 5 | 5 | OK |
| Job nodes with role 'pipeline' (PRVDAY01, PRVMTH01) | 2 | 2 | OK |
| Job nodes that are a cataloged proc (PRVSCRPR) | 1 | 1 | OK |
| Job nodes in total [explained: 2 pipeline + 1 proc + the 2 VSAM-definition jobs PRVVSM01/02, which the skill's '2 plus proc' leaves out] | 5 | 5 | OK |
| BusinessRule nodes from AWS (ruleId contains -cbl-) | 403 | 403 | OK |
| AWS BusinessRule nodes for PRVENR01 | 68 | 68 | OK |
| AWS BusinessRule nodes for PRVSCR01 | 62 | 62 | OK |
| AWS BusinessRule nodes for PRVSAN01 | 58 | 58 | OK |
| AWS BusinessRule nodes for PRVTMS01 | 55 | 55 | OK |
| AWS BusinessRule nodes for PRVRSK01 | 48 | 48 | OK |
| AWS BusinessRule nodes for PRVCICS1 | 45 | 45 | OK |
| AWS BusinessRule nodes for PRVDIR01 | 29 | 29 | OK |
| AWS BusinessRule nodes for PRVREV01 | 24 | 24 | OK |
| AWS BusinessRule nodes for PRVDEAD1 | 14 | 14 | OK |
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
| AWS BusinessRule nodes without PART_OF | 88 | 88 | OK |
| TestCase nodes from the AWS plan | 4 | 4 | OK |
| DB2 columns as Field nodes (the DDL defines 26; AWS's dictionary lists 22) | 26 | 26 | OK |
| Programs that have paragraphs (the 9 COBOL programs) | 9 | 9 | OK |
| Transaction PINQ is flagged as missing its CICS definition | True | True | OK |
| AWS rules with verification VERIFIED (as in rule-verification.csv) | 339 | 339 | OK |
| AWS rules with verification MISMATCH (as in rule-verification.csv) | 40 | 40 | OK |
| AWS rules with verification NOT_FOUND (as in rule-verification.csv) | 3 | 3 | OK |
| AWS rules with verification DROPPED (as in rule-verification.csv) | 21 | 21 | OK |
| Rules still UNVERIFIED | 0 | 0 | OK |
| Added rules R-<PROGRAM>-ADD-nn (as in rule-verification.csv) | 52 | 52 | OK |
| VERIFIED or MISMATCH rules without evidence | 0 | 0 | OK |
| Defect nodes (as in defect-register.md) | 29 | 29 | OK |
| Defects still OPEN | 0 | 0 | OK |
| Defects that AFFECT nothing | 0 | 0 | OK |
| MISMATCH or NOT_FOUND rules with no defect and no question | 0 | 0 | OK |
| Question nodes (as in questions.md) | 12 | 12 | OK |
| Regulations cited by no rule | 0 | 0 | OK |
| Service nodes (as in the overview service table) | 11 | 11 | OK |
| ApiEndpoint nodes (as in the OpenAPI contracts) | 39 | 39 | OK |
| ApiEndpoints without roles | 0 | 0 | OK |
| Events without a consumer | 0 | 0 | OK |
| Table nodes (as in the data-model drafts) | 42 | 42 | OK |
| Rules OWNED_BY a service (as in rule-to-service.csv, DROPPED excluded) | 433 | 433 | OK |
| Rules neither OWNED_BY a service nor dropped | 0 | 0 | OK |
| Legacy fields neither MAPS_TO a column nor dropped | 0 | 0 | OK |
| Decision (ADR) nodes | 10 | 10 | OK |
| UiScreen nodes (as in screen-inventory.md) | 13 | 13 | OK |
| Screen CALLS to endpoints (as in screen-inventory.md) | 31 | 31 | OK |

## Totals, idempotency and spot checks

- **Database:** the dedicated `medicaid-kag` on the local KAG instance (Neo4j 2026.03.1 Enterprise, driver 6.4.1). It was empty before the load.
- **Size:** after Phase 1, 792 nodes and 1,060 relationships; after Phase 2, 886 nodes and 1,342 relationships; after Phase 3, 1,363 nodes and 2,350 relationships.
- **Idempotency:** `validate.py --idempotency` re-runs every loader and reported **no change in any count** after Phase 1, after Phase 2 and after Phase 3.
  The comparison was itself tested: against a deliberately altered snapshot it reports `CHANGED` and exits 1.
- **Spot checks (seed 20261118):** rules `R-PRVSCR01-cbl-00050`, `R-PRVENR01-cbl-00017`, `R-PRVREV01-cbl-00020` match the AWS JSON exactly on name, Given, When and Then.
  Dataset `MMPRV.PROV.APPS.D&DATE` (random) and `MMPRV.PROVIDER.MASTER.KSDS` (9 program/access pairs) match `program_to_dsn.csv` exactly.
- **Tests:** 81 pytest tests pass (parsers, loaders, validation); `git grep` finds the password variable only in `.env.example`.

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

## Phase 2 (legacy analysis) in the graph

- `load_09_analysis` sets `verification`, `evidence` (`file:start-end`), `paragraph` and `verificationNote` on all 403 AWS rules and adds
  the 52 rules AWS missed (`R-<PROGRAM>-ADD-nn`); `IMPLEMENTED_IN` carries the `lines`.
- 29 Defect nodes (decisions: none OPEN) with 174 `AFFECTS` links to programs, jobs, copybooks, datasets and rules; 10 Question nodes with
  37 `ABOUT` links; 3 Regulation nodes (the 42 CFR 455 sections the AWS rule texts cite) with 19 `GOVERNED_BY` links.
- Spot check (seed 20261118): `R-PRVCICS1-cbl-00041` (MISMATCH, D-022 REPLACE), `R-PRVRSK01-cbl-00044` (VERIFIED, D-004 FIX) and
  `R-PRVCICS1-ADD-05` (added, D-023 FIX) show exactly the status, evidence lines and defect decision in `rule-verification.csv`.
- Explained: the 3 NOT_FOUND rules have no code to cite by definition; each is covered by a REPLACE decision (D-012, D-022).

## Phase 3 (architecture and UX) in the graph

- `load_10_architecture`: 11 Service, 39 ApiEndpoint (each with roles), 10 Event, 42 Table, 350 Column, 10 Decision (ADRs, PROPOSED), 13 UiScreen;
  433 OWNED_BY (every rule not dropped has exactly one owning service; 22 dropped rules carry `owner = DROPPED`), 86 MAPS_TO plus 40 dropped fields
  (every legacy field accounted for), 31 screen CALLS, 17 CONSUMES and 10 PUBLISHES.
- Trace check: `R-PRVENR01-cbl-00003` (MISMATCH) -> D-001 FIX -> enrollment-service -> portal.apply; `R-PRVSAN01-cbl-00005` -> D-006 FIX -> provider-service;
  `PRV-OWNER-NPI` -> `provider-service:provider:owner_npi` (D-007).
