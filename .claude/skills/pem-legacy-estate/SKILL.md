---
name: pem-legacy-estate
description: Facts about the legacy PEM mainframe estate. Covers where every program, copybook, job and AWS Transform output lives, the seeded anti-patterns, known findings, and the questions that must be confirmed. Use before analysing, designing or building anything that replaces legacy behaviour.
---

# PEM legacy estate

## 1. Read first

The root `CLAUDE.md` is the verified guide to the estate. Read sections 3 to 9 before
anything else. This skill adds only what CLAUDE.md does not cover: the work lists and
the default decisions.

## 2. Inputs and where they are

| What | Path |
|---|---|
| Batch COBOL (8) | `source-code/BATCH/PRV*.cbl` |
| CICS program | `source-code/ONLINE/PRVCICS1.cbl` |
| Copybooks (5) | `source-code/COPYBOOKS/*.cpy` |
| JCL | `source-code/JCL/PRVDAY01.jcl`, `PRVMTH01.jcl`, `PROCS/PRVSCRPR.jcl` |
| VSAM definitions | `source-code/VSAM/PRVVSM01.jcl`, `PRVVSM02.jcl` |
| DB2 DDL | `source-code/DB2/PRVDDL01.sql` |
| REXX | `source-code/REXX/PRVAUTOM.rexx` |
| Business rules, 403 with Given/When/Then | `aws-transform/05-business-documentation/MMIS-PROVIDER-DEMO-TIER4/**/*-cbl.json` (key `all_rules`) |
| Dependency graph (68 entries) | `aws-transform/03-decomposition/dependency-graph.json` |
| Data lineage | `aws-transform/02-data-analysis/data-lineage/*.csv` |
| Data dictionary | `aws-transform/02-data-analysis/data-dictionary/*.csv` (+ `per-file/`) |
| Per-program docs | `aws-transform/04-documentation/**/*.json` |
| AWS test plan | `aws-transform/06-test-plan/test-plan.json` |
| Missing dependencies | `aws-transform/01-code-analysis/missing-dependencies.csv` |
| Sample feeds (not COBOL format) | `aws-transform/test-data/*.txt` |

If a path is not where this table says, search for it, report the real path, and
continue. Never invent content for a missing file.

## 3. Seeded anti-patterns (the analysis must find all 12)

AP-01 fall-through (PRVENR01) · AP-02 dead code and never-set flag (PRVSCR01) ·
AP-03 ALTER (PRVRSK01) · AP-04 2-digit-year arithmetic (PRVRSK01) · AP-05 dynamic CALL
(PRVSAN01) · AP-06 cross-app VSAM coupling (PRVSAN01, PRVDIR01) · AP-07 YYDDD Julian dates
(PRVTMS01) · AP-08 orphan program and dead flag (PRVDEAD1) · AP-09 nested copybook
(WRKSTORE) · AP-10 REDEFINES across 3 types (PRVRECRD) · AP-11 symbolic-parameter override
(PRVSCRPR) · AP-12 dual system of record (DB2 vs VSAM).
Locations: CLAUDE.md section 6.

## 4. Known findings and default decisions

The user confirms each default in Phase 2. Record every outcome in
`modern/docs/analysis/defect-register.md`.

| ID | Finding | Default decision |
|---|---|---|
| D-001 | PRVENR01 fall-through: likely stops at first bad application, writes the error row twice, stores the bad record (inferred) | FIX: process all; reject invalid with one error each |
| D-002 | PRVRSK01 ALTER path: REWRITE likely never reached (inferred) | FIX: risk tier is saved |
| D-003 | Revalidation due date reset to today + 5 years on every run | FIX: set at enrolment approval and at each completed revalidation |
| D-004 | 2-digit-year pivot at 70 | FIX: 4-digit dates everywhere |
| D-005 | Four screening checks are stubs returning Pass | REPLACE: real screening service using mocked external sources |
| D-006 | Sanction action `S` sets DENIED | OPEN: recommend a new SUSPENDED status |
| D-007 | Record length: copybook 130, FDs/IDCAMS 121. At 121 the group OWNER-NPI (bytes 121–130) is lost | OPEN: recommend 130 is correct |
| D-008 | Provider type codes: COBOL IN/GR/FA vs feed IND/GRP/HOSP/AMB/HOS/LAB/PT/DME/HHA | OPEN: recommend keep IN/GR/FA plus a mapping table |
| D-009 | PRVDAY01 SORT: wrong offset (23, not 34); output never used | DROP |
| D-010 | PRVSCR01 Medicare-reciprocity check: flag never set | DROP |
| D-011 | PRVDEAD1 orphan (discontinued 2019) | DROP |
| D-012 | Dynamic CALL to PRVNT* notice programs that do not exist | REPLACE: notification-service |
| D-013 | PRVSAN01 rewrites PEMAPP1's master directly | FIX: sanctions-service calls provider-service's API |
| D-014 | Provider identity in both DB2 and VSAM | FIX: provider-service is the single owner |
| D-015 | T-MSIS YYDDD period | PRESERVE in the legacy-format extract; ISO dates internally |
| D-016 | Only E101 of the error codes is used | FIX: implement E101–E205 as validation/screening results |
| D-017 | BATCH_ID passed by JCL, ignored by the program | FIX: a real job parameter |
| D-018 | SCREEN.RESULTS and SANCTION.MASTER KSDS defined, never used | FIX: real tables in their services |
| D-019 | No program appears to set status ACTIVE except sanction reinstate (`R`). **Verify in Phase 2.** | OPEN: recommend an analyst approval step sets ACTIVE after a Pass screening |
| D-020 | `&DATE` and `&BATCH_DATE` are not defined anywhere | FIX: business date is an explicit job parameter |

## 5. Things that do not exist (do not look for them, do not invent them)

The BMS map PRVSET1, the CSD, the LISTCAT, the CICS definitions PRVPROVN and PINQ, the
programs PRVNTGEN/RST/SUS/TRM, any real data, any compiler output, the `*-LAB.jcl` stubs,
and the `modern/` tree (until Phase 0 creates it).
