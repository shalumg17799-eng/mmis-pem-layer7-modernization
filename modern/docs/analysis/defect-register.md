# Defect register

Every defect found in the legacy estate, the evidence, and the decision for the new system. Paths are relative to
`source-code/`. "INFERRED" means read from the code but not executed (no compiler or emulator was used).

- **D-001 to D-020:** decided by the user on 2026-11-18 (recommended defaults accepted; D-006, D-007, D-008 and D-019 as stated).
- **D-021 to D-029:** new findings from the Phase 2 verification. Their recommended defaults were applied and then
  accepted by the user on 2026-11-18 (answers to Q-006, Q-009 and Q-010).

| ID | Finding | Evidence (file:lines) | Legacy behaviour | Recommended decision | Decision | Decided by | Date |
|---|---|---|---|---|---|---|---|
| D-001 | PRVENR01 paragraph fall-through (AP-01) | BATCH/PRVENR01.cbl:128-159 | An invalid application is logged and then still written to the master, carrying the status bytes from the input. INFERRED: the run then falls through to TERMINATE-PROGRAM, so the load stops at the first invalid application and its error row is written twice. | FIX: process all applications; reject each invalid one with exactly one error row | FIX | user | 2026-11-18 |
| D-002 | PRVRSK01 ALTER path (AP-03) | BATCH/PRVRSK01.cbl:86-97,103-116 | INFERRED: the ALTERed GO TO leaves the PERFORM range, so the REWRITE at 97 is likely never reached and only the first record is touched. | FIX: the risk tier is saved | FIX | user | 2026-11-18 |
| D-003 | Revalidation date reset on every run | BATCH/PRVRSK01.cbl:70-76,126-137; BATCH/PRVREV01.cbl:81-84 | Every provider's due date becomes today + 5 years on every daily run, so the month-end sweep rarely finds anyone due. | FIX: set the due date at enrolment approval and at each completed revalidation | FIX | user | 2026-11-18 |
| D-004 | 2-digit-year arithmetic (AP-04) | BATCH/PRVRSK01.cbl:128-136; BATCH/PRVTMS01.cbl:104-110 | Pivot at 70 gives 19xx from 2065; a Feb 29 run gives an impossible due date; the T-MSIS future-enrolment test flags every pre-2000 enrolment. | FIX: 4-digit dates everywhere | FIX | user | 2026-11-18 |
| D-005 | Screening checks are stubs | BATCH/PRVSCR01.cbl:116-132,146-149 | All four checks return Pass, so every provider passes; screening rows carry no source, date or detail. | REPLACE: real screening service using mocked external sources | REPLACE | user | 2026-11-18 |
| D-006 | Sanction `S` sets DENIED | BATCH/PRVSAN01.cbl:109-110 | A suspension cannot be told apart from a denial. | A new SUSPENDED status | SUSPENDED status | user | 2026-11-18 |
| D-007 | Record length 130 vs 121 | COPYBOOKS/PRVRECRD.cpy:9-44; `RECORD CONTAINS 121` in all 7 batch FDs; VSAM/PRVVSM01.jcl:17 | At 121 bytes, PRV-OWNER-NPI (bytes 121-130) would be lost. | The record is 130 bytes | 130 bytes | user | 2026-11-18 |
| D-008 | Provider type codes differ | COPYBOOKS/PRVRECRD.cpy (88-levels IN/GR/FA); aws-transform/test-data/providers.txt | Feed codes IND/GRP/HOSP/AMB/HOS/LAB/PT/DME/HHA cannot be loaded as they are. | Keep IN/GR/FA plus a mapping table | IN/GR/FA + mapping table | user | 2026-11-18 |
| D-009 | PRVDAY01 SORT step | JCL/PRVDAY01.jcl:43-50 | Tests byte 23 (inside the taxonomy) instead of the screen flag at 34; its output &&PRVSORT is never read. | DROP | DROP | user | 2026-11-18 |
| D-010 | Medicare reciprocity check (AP-02) | BATCH/PRVSCR01.cbl:61,97-99,140-144 | The flag is never set, so the check never runs. | DROP | DROP | user | 2026-11-18 |
| D-011 | PRVDEAD1 orphan (AP-08) | BATCH/PRVDEAD1.cbl:29,37 | No job runs it and its only branch is disabled. | DROP | DROP | user | 2026-11-18 |
| D-012 | Dynamic CALL to missing programs (AP-05) | BATCH/PRVSAN01.cbl:124-138 | PRVNTTRM/SUS/RST/GEN are not in the estate; no notice is ever sent here. | REPLACE: notification-service | REPLACE | user | 2026-11-18 |
| D-013 | Cross-application coupling (AP-06) | BATCH/PRVSAN01.cbl:34,115; BATCH/PRVDIR01.cbl:64,73 | PEMAPP2 reads and rewrites PEMAPP1's master with no API. | FIX: sanctions-service and directory-service call provider-service's API | FIX | user | 2026-11-18 |
| D-014 | Dual system of record (AP-12) | DB2/PRVDDL01.sql:19-33; VSAM/PRVVSM01.jcl:14-17 | Provider identity is defined in DB2 and in the VSAM master (no program uses the DB2 tables). | FIX: provider-service is the single owner | FIX | user | 2026-11-18 |
| D-015 | T-MSIS YYDDD period (AP-07) | BATCH/PRVTMS01.cbl:60-62,80,114 | The period is a 2-digit-year Julian date. | PRESERVE in the legacy-format extract; ISO dates internally | PRESERVE | user | 2026-11-18 |
| D-016 | Error codes mostly unused | BATCH/PRVENR01.cbl:129; COPYBOOKS/ERRCODES.cpy | Only E101 is ever moved (cut to 2 bytes) and never output; E102-E205 unused. | FIX: implement E101-E205 as validation and screening results | FIX | user | 2026-11-18 |
| D-017 | BATCH_ID ignored | JCL/PRVDAY01.jcl:20; BATCH/PRVENR01.cbl:95-98 | The JCL passes PARM, the program reads an environment variable, so the id is (INFERRED) always DEFBATCH. | FIX: a real job parameter | FIX | user | 2026-11-18 |
| D-018 | Unused clusters, no history | VSAM/PRVVSM02.jcl:15-27; BATCH/PRVSAN01.cbl:57-64 | SCREEN.RESULTS and SANCTION.MASTER KSDS are never opened; sanction reason and effective date are never stored. | FIX: real tables in their services (screening results, sanction history) | FIX | user | 2026-11-18 |
| D-019 | Nothing activates a provider | BATCH/PRVSAN01.cbl:112 (only SET PRV-ACTIVE); BATCH/PRVENR01.cbl:146 (PENDING) | **Confirmed in Phase 2:** the only statement that sets ACTIVE is the sanction reinstate. The one other route is the D-001 fall-through, which writes whatever status bytes the intake record held (PRVENR01.cbl:103,158). | An analyst approval step sets ACTIVE after a Pass screening | Analyst approval sets ACTIVE | user | 2026-11-18 |
| D-020 | Undefined symbolics | JCL/PRVDAY01.jcl:20,22,27,59; JCL/PRVMTH01.jcl:18,29,39 | `&DATE` and `&BATCH_DATE` are defined nowhere, so the dataset names cannot resolve. | FIX: the business date is an explicit job parameter | FIX | user | 2026-11-18 |
| D-021 | Identifier past column 72 | BATCH/PRVTMS01.cbl:114 | The last `D` of WS-T-PERIOD-YYDDD is in column 73, which the compiler ignores; INFERRED: the program does not compile as shipped. | FIX: not applicable to the new system (the field is explicit); the Phase 9 Hercules run must patch the line | FIX (accepted, Q-010) | Claude (default), accepted by the user | 2026-11-18 |
| D-022 | Provider inquiry does not work as intended | ONLINE/PRVCICS1.cbl:28-61 | INFERRED (map not provided): the typed NPI is never moved to the key, the status message is never sent, no initial map is sent, RESP is not tested. | REPLACE: analyst-console inquiry by NPI or Medicaid ID that shows the status | REPLACE (accepted, Q-010) | Claude (default), accepted by the user | 2026-11-18 |
| D-023 | No authorisation on the inquiry | ONLINE/PRVCICS1.cbl:28-61 | Any user of PINQ can read any provider. | FIX: role-based access (Keycloak roles) on every endpoint and screen | FIX (accepted, Q-010) | Claude (default), accepted by the user | 2026-11-18 |
| D-024 | Errors are not handled | BATCH/PRVENR01.cbl:102-116,187-189; PRVSCR01.cbl:85-93,114,149; PRVRSK01.cbl:70-76,97; PRVREV01.cbl:76-80,93; PRVTMS01.cbl:92-100,116; PRVSAN01.cbl:90-101,115; PRVDIR01.cbl:64-65,73-81,89 | File statuses are untested, most programs never set a return code, RC 4 overwrites RC 8, and a persistent read error could loop forever (INFERRED). | FIX: explicit error handling, a job result per run, failures surfaced | FIX (accepted, Q-010) | Claude (default), accepted by the user | 2026-11-18 |
| D-025 | Intake layout undefined | BATCH/PRVENR01.cbl:52-55,73-74,103 | The 200-byte intake record is read INTO the 130-byte PRVRECRD; bytes 131-200 are ignored. | Intake = PRVRECRD layout in bytes 1-130, bytes 131-200 reserved (exactly how the legacy reads it) | Q-009 (accepted, Q-010) | Claude (default), accepted by the user | 2026-11-18 |
| D-026 | Unknown sanction codes accepted | BATCH/PRVSAN01.cbl:104-116,129 | A code other than T, S, R changes nothing but the record is rewritten and PRVNTGEN is called. | FIX: reject unknown action codes with an error | FIX (accepted, Q-010) | Claude (default), accepted by the user | 2026-11-18 |
| D-027 | Sanctions ignore the current status | BATCH/PRVSAN01.cbl:104-113 | T, S and R apply whatever the status is; R can make a DENIED or PENDING provider ACTIVE. | PRESERVE the legacy rule (apply as written), pending the business answer | Q-006 (accepted, Q-010) | Claude (default), accepted by the user | 2026-11-18 |
| D-028 | Directory name is raw bytes | BATCH/PRVDIR01.cbl:88; COPYBOOKS/PRVRECRD.cpy:32-44 | The name is the first 60 bytes of PRV-VARIANT: for an individual that includes the first 10 characters of the licence number. | FIX: compose the name from the name fields (individual: first + last; organisation: legal name) | FIX (accepted, Q-010) | Claude (default), accepted by the user | 2026-11-18 |
| D-029 | Revalidation queue has no state | BATCH/PRVREV01.cbl:81-94 | A provider that stays overdue is queued again every month; an active provider with a zero due date is queued too. | FIX: one revalidation case per provider with its own state in revalidation-service | FIX (accepted, Q-010) | Claude (default), accepted by the user | 2026-11-18 |

No defect is OPEN. The anti-pattern self-check found **12 of 12**: AP-01 (D-001), AP-02 (D-010), AP-03 (D-002), AP-04 (D-004),
AP-05 (D-012), AP-06 (D-013), AP-07 (D-015), AP-08 (D-011), AP-09 (WRKSTORE nests SCRNREC and ERRCODES, COPYBOOKS/WRKSTORE.cpy:22-23:
a build concern only, solved by per-service models), AP-10 (PRVRECRD REDEFINES, COPYBOOKS/PRVRECRD.cpy:34,41: solved by D-007 and the
type-specific model), AP-11 (PRVSCRPR symbolic override, JCL/PROCS/PRVSCRPR.jcl: the proc is never called), AP-12 (D-014).

## Decisions recorded after Phase 2 that are not legacy defects

Kept outside the table above on purpose: the table lists defects of the legacy estate (the knowledge graph holds one
Defect node per table row), and the entries below are conventions of the new system that the user decided.

- **D-030: "no revalidation due date" is written as 99991231 in the 130-byte master** (decided by the user on
  2026-11-20, answer A to Q-124 in `questions.md`; recorded here on 2026-11-21). The master export of provider-service
  writes 99991231 in bytes 43-50 for a provider with no revalidation due date. That is the value the synthetic test data
  uses, and so the captured legacy run (Q-106, default A); the export no longer writes 00000000. A reader of the file
  takes 99991231 as "no due date" (Q-135, default A: the API leaves the member out for such a provider). Stated in
  MAM-43 AC-6 (spec version 2); live since deploy 4 (2026-11-20 23:02); in the parity re-run of 2026-11-20 23:17 the 46
  rows concerned compare byte for byte. D-029 is unchanged: the legacy sweep itself treats a zero due date as due.
