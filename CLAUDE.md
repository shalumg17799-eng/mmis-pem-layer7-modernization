@MODERNIZATION.md

# CLAUDE.md

Project guide for AI coding assistants: read this once and you know the whole codebase.
Verified against the files on 2026-11-17. Anything marked **(inferred)** comes from reading control flow
and was **not executed**: there is no COBOL compiler or mainframe emulator in this environment.

---

## 1. What this is

- **Domain:** Medicaid MMIS, Layer 7: **Provider Enrollment & Management (PEM)**. Enroll healthcare providers,
  screen them against federal/state exclusion lists, assign a risk tier, revalidate, apply sanctions,
  publish a provider directory, report to CMS **T-MSIS**. Regulatory anchor: **42 CFR 455**.
- **`source-code/`**: a small legacy IBM mainframe estate (9 COBOL programs, ~1,050 lines; plus CICS, JCL, VSAM, DB2, REXX).
  Built by Radiant Digital as a deliberately aged "before" system with **seeded anti-patterns** for modernization demos.
- **`aws-transform/`**: output of an **AWS Transform for mainframe** job (`01d3a9dd-03e6-4e0c-9877-1e05d09ff111`, run 2026-11-17)
  that analysed that source: dependency graph, data lineage, docs, business rules, test plan.
- **Project goal:** modernize to cloud microservices + FHIR (see section 10).
- **Not present:** no build system, tests, compiler, or git history; nothing has ever been compiled or run here.
  The README references things that are *not* in this folder: `../../modern/`, `../../USE-CASE-PEM.md`, and the Hercules `*-LAB.jcl` stubs.

## 2. Layout

```
Medco-Transform/
|-- CLAUDE.md
|-- source-code/            the mainframe estate (22 files)  <-- the thing to read/edit
|   |-- README.md           estate README: anti-pattern index, app boundaries, run order, target design
|   |-- BATCH/              8 batch COBOL programs (PRV*.cbl)
|   |-- ONLINE/             PRVCICS1.cbl  (CICS transaction PINQ)
|   |-- COPYBOOKS/          5 copybooks (.cpy)
|   |-- JCL/                PRVDAY01.jcl, PRVMTH01.jcl, PROCS/PRVSCRPR.jcl
|   |-- VSAM/               PRVVSM01.jcl, PRVVSM02.jcl  (IDCAMS cluster definitions)
|   |-- DB2/                PRVDDL01.sql
|   `-- REXX/               PRVAUTOM.rexx
|-- aws-transform/          everything AWS-Transform-related, kept together in one folder
|   |-- 01-code-analysis/   02-data-analysis/   03-decomposition/   04-documentation/
|   |-- 05-business-documentation/   06-test-plan/   07-test-data-collection/   08-smf-analysis/
|   |   (01-08 are GENERATED output: treat as read-only reference)
|   `-- test-data/          4 pipe-delimited feed samples (hand-authored input, see section 9)
`-- _archive/               the 3 original zips, untouched backup (not needed day to day)
```

- **Path mapping:** every path *inside* `aws-transform/` JSON/CSV begins `MMIS-PROVIDER-DEMO-TIER4/`. That is `source-code/`.
- Inside `source-code/`, directory names (BATCH, JCL, ...) mirror z/OS library types and member names equal
  PROGRAM-ID / job name. **Keep them**; AWS outputs and the README reference them.

## 3. The estate in one picture

Two logical applications share one data store:

| App | Programs | Owns |
|---|---|---|
| **PEMAPP1** Enrollment & Screening | PRVENR01, PRVSCR01, PRVRSK01, PRVREV01, PRVTMS01, PRVDEAD1, CICS PRVCICS1 (txn `PINQ`) | `MMPRV.PROVIDER.MASTER.KSDS` (DD `PRVMSTR`, CICS file `PRVPROVN`) |
| **PEMAPP2** Sanctions & Directory | PRVSAN01, PRVDIR01 | `MMPRV.SANCTION.MASTER.KSDS` (defined, never used) |

**The key modernization blocker:** PEMAPP2 opens and **REWRITEs PEMAPP1's master KSDS directly** (PRVSAN01) and reads it (PRVDIR01), with no API boundary.
Seven batch programs and the CICS transaction touch the master (all but PRVDEAD1).

```
DAILY  PRVDAY01.jcl                                      MONTH-END  PRVMTH01.jcl
 APPINPUT -> STEP010 PRVENR01 --> PRVMSTR (KSDS) <--      STEP010 PRVREV01 -> REVALQ
                |-> APPERROR                               STEP020 PRVTMS01 -> TMSISOUT
                v  (steps 020-040 only if STEP010 RC=0)    STEP030 IEBGENER -> GDG MMPRV.TMSIS.ARCHIVE(+1)
 STEP020 SORT (output &&PRVSORT is never used)
 STEP030 PRVSCR01 -> REWRITE PRVMSTR, writes SCRNOUT
 STEP040 PRVRSK01 -> REWRITE PRVMSTR (risk tier + revalidation date)

NOT scheduled by any JCL here:  PRVSAN01 (SANCIN -> REWRITE PRVMSTR -> CALL PRVNT***)   PRVDIR01 (-> DIROUT)
                                PRVDEAD1 (orphan)   PRVCICS1 (online, CICS)   PRVAUTOM.rexx (submits PRVDAY01)
```

## 4. Programs

| Program | App | Role | Reads | Writes | Note |
|---|---|---|---|---|---|
| PRVENR01 | 1 | Enrollment loader: validates NPI present, type IN/GR/FA, taxonomy present; writes master | `APPINPUT` seq 200B | `PRVMSTR` WRITE, `APPERROR` 250B | fall-through bug |
| PRVSCR01 | 1 | Screening: for master records with screen-flag `U` run license / LEIE / SAM / SSA-DMF checks, set `P`/`F` | `PRVMSTR` | `PRVMSTR` REWRITE, `SCRNOUT` 67B | all 4 checks are **stubs returning `P`** |
| PRVRSK01 | 1 | Risk tier by taxonomy prefix (`332`,`251` -> High; `341`,`291`,`225` -> Moderate; else Limited) + revalidation due date = today + 5y | `PRVMSTR` | `PRVMSTR` REWRITE | ALTER, 2-digit year |
| PRVREV01 | 1 | Revalidation sweep: `ACTIVE` and due date <= today -> `RESCREEN-REQUIRED` | `PRVMSTR` | `REVALQ` 80B | inherits YY defect |
| PRVTMS01 | 1 | T-MSIS extract of `ACTIVE` providers | `PRVMSTR` | `TMSISOUT` 120B | YYDDD Julian dates |
| PRVDEAD1 | 1 | Paper fingerprint intake (discontinued 2019) | - | - | orphan + flag `N` |
| PRVSAN01 | 2 | Apply sanctions: `T`->TERMINATED, `S`->**DENIED**, `R`->ACTIVE; then `CALL PRVNT{TRM,SUS,RST,GEN}` | `SANCIN` 80B, `PRVMSTR` | `PRVMSTR` REWRITE | dynamic CALL, targets absent |
| PRVDIR01 | 2 | Directory extract of `ACTIVE` providers | `PRVMSTR` | `DIROUT` 100B | name = first 60 bytes of `PRV-VARIANT` |
| PRVCICS1 | 1 | CICS `PINQ`: receive map `PRVMAP1`/`PRVSET1`, READ `PRVPROVN` by NPI, show status | `PRVPROVN` | map | needs BMS + CSD (not provided) |
| PRVAUTOM.rexx | ops | SUBMIT `MMPRV.PROD.JCL(PRVDAY01)`, poll SDSF, alert on non-zero RC | | | replaced by event bus in target |

All batch programs open the master through `ASSIGN TO PRVMSTR`, ORGANIZATION INDEXED, ACCESS DYNAMIC, key `PRV-NPI`.
DD names in JCL match the `ASSIGN` names for every job that exists.

## 5. Data

### 5.1 Provider master record: `COPYBOOKS/PRVRECRD.cpy`

| Field | Bytes | Pos | Values |
|---|---|---|---|
| PRV-NPI (KEY) | 10 | 1-10 | |
| PRV-MEDICAID-ID | 9 | 11-19 | alternate index key |
| PRV-TYPE-CODE | 2 | 20-21 | `IN` individual, `GR` group, `FA` facility |
| PRV-TAXONOMY | 10 | 22-31 | first 3 chars drive risk tier |
| PRV-RISK-TIER | 1 | 32 | `L` `M` `H` |
| PRV-ENROLL-STATUS | 1 | 33 | `P` pending, `A` active, `T` terminated, `D` denied |
| PRV-SCREEN-FLAG | 1 | 34 | `U` unscreened, `P` pass, `F` fail |
| PRV-EFFECTIVE-DATE | 8 | 35-42 | CCYYMMDD |
| PRV-REVAL-DUE-DATE | 8 | 43-50 | CCYYMMDD |
| PRV-VARIANT | 80 | 51-130 | **REDEFINED** two ways (below) |

- Individual view: LAST-NAME 30, FIRST-NAME 20, LICENSE-NO 15, LICENSE-STATE 2, filler 13.
- Group/Facility view: LEGAL-NAME 40, DBA-NAME 30, OWNER-NPI 10.
- **Total = 130 bytes, but every FD says `RECORD CONTAINS 121` and `VSAM/PRVVSM01.jcl` says `RECORDSIZE(121 121)`.**
  (AWS's data dictionary also computes 130.)

### 5.2 Other layouts

| Record | Bytes | Layout |
|---|---|---|
| `SCRNREC.cpy` screening result | 67 | NPI 10, SOURCE 8 (`NPPES` `OIGLEIE` `SAMGOV` `LICBOARD` `SSADMF` `PECOS`), RESULT 1 (`P` `F` `C`), VERDICT-DATE 8, DETAIL 40 |
| Sanction input (`SANCIN`) | 80 | NPI 10, ACTION 1 (`T` `S` `R`), REASON 40, EFF-DATE 8, filler 21 |
| Revalidation queue (`REVALQ`) | 80 | NPI 10, DUE-DATE 8, ACTION 20, filler 42 |
| T-MSIS (`TMSISOUT`) | 120 | `PRV01` 5, PROVID 9, NPI 10, STATUS 1, PERIOD-YYDDD 5, filler 90 |
| Directory (`DIROUT`) | 100 | MEDICAID-ID 9, NPI 10, TYPE 2, RISK 1, NAME 60, filler 18 |
| Application error (`APPERROR`) | 250 | free text `<NPI> TYPE:<t> ERR:VALIDATION-FAILED` |

- `WRKSTORE.cpy`: standard work area (end-of-file flag, file-status 88s, read/written/skipped/errored counters); **nests `SCRNREC` and `ERRCODES`**.
- `ERRCODES.cpy`: `E101` missing NPI, `E102` invalid type, `E103` dup NPI, `E104` missing taxonomy, `E201` excluded, `E202` license expired,
  `E203` owner excluded, `E204` deceased, `E205` debarred. **Only `EC-MISSING-NPI` is referenced** (PRVENR01.cbl:129).

### 5.3 Stores

- **VSAM** (`VSAM/`): `PROVIDER.MASTER.KSDS` key(10 0) + AIX on Medicaid ID `KEYS(9 10)` NONUNIQUE + PATH;
  `SCREEN.RESULTS.KSDS` key(18)=NPI+SOURCE rec 67; `SANCTION.MASTER.KSDS` key(10) rec 80. All SHAREOPTIONS(2 3).
  **The last two are defined but no program or JCL opens them** (screening writes a *sequential* dataset instead).
- **DB2** (`DB2/PRVDDL01.sql`): DB `MMPRVDB`; tables `PROVIDER`, `PROVIDER_OWNER`, `SCREENING`, `SANCTION`; indexes on `MEDICAID_ID`, `REVAL_DUE_DATE`.
  Reference only: no program uses `EXEC SQL`, and `DCLPROV.cpy` is COPY'd by nothing and omits `SCREEN_FLAG` / `END_DATE` (drift vs the DDL).
  Provider identity lives in **both** DB2 and the VSAM master (the "dual system of record" anti-pattern).
- **Sequential datasets:** HLQ `MMPRV`; daily/monthly names end `.D&DATE`: `PROV.APPS`, `PROV.ERRORS`, `SCREEN.RESULTS`, `REVAL.QUEUE`, `TMSIS.PROVIDER`; GDG `TMSIS.ARCHIVE`; loadlib `MMPRV.PROD.LOADLIB`.

## 6. Seeded anti-patterns (intentional: do NOT "fix" unless asked)

The modernization demo depends on these existing. Catalogued in `source-code/README.md`; locations verified:

| # | Pattern | Where |
|---|---|---|
| 1 | Paragraph fall-through | `PRVENR01.cbl` `GO TO INVALID-APP` at :132 :139 :144; label at :155 has no exit |
| 2 | Dead code, flag never set | `PRVSCR01.cbl` flag :61, guard :97, `CHECK-MEDICARE-RECIPROCITY` :140 |
| 3 | `ALTER` (runtime GO TO) | `PRVRSK01.cbl` :86 :90 :92; `APPLY-TIER-PARA` :103 |
| 4 | 2-digit-year arithmetic | `PRVRSK01.cbl` `SET-REVALIDATION-DATE` :126-137, pivot `< 70` at :131 |
| 5 | Dynamic CALL | `PRVSAN01.cbl:134` `CALL WS-NOTICE-PGM` |
| 6 | Cross-app VSAM coupling | `PRVSAN01.cbl` :34 (assign), :115 (REWRITE); `PRVDIR01.cbl` (read) |
| 7 | YYDDD Julian dates | `PRVTMS01.cbl` :80 :107 |
| 8 | Orphan + dead flag | `PRVDEAD1.cbl` :29 :37 |
| 9 | Nested copybook | `WRKSTORE.cpy` :22-23 |
| 10 | REDEFINES across 3 types | `PRVRECRD.cpy` :34 :41 |
| 11 | Symbolic-parameter override | `JCL/PROCS/PRVSCRPR.jcl` (`&HLQ`, `&DTE`) |
| 12 | Dual system of record | `DB2/PRVDDL01.sql` vs VSAM master |

## 7. Findings beyond the README

**Verified (by grep / arithmetic):**
- **Record length:** 130 bytes in the copybook vs 121 in all 7 FDs and the IDCAMS definition (section 5.1).
- **`PRVDAY01` STEP020 SORT is wrong twice:** `INCLUDE COND=(23,1,CH,EQ,C'U')` is commented "PRV-SCREEN-FLAG" but offset 23 is inside
  `PRV-TAXONOMY` (22-31); the flag is at 34. And `SORTOUT=&&PRVSORT` is never read by STEP030/040.
- **Orphans:** no JCL runs `PRVSAN01` or `PRVDIR01` (README's run order lists `PRVSAN01` as step 5; no such job exists). `PRVSCRPR` proc has no caller
  (JCL runs `PGM=PRVSCR01` directly). `&DATE` and `&BATCH_DATE` are not defined anywhere.
- **`BATCH_ID`:** JCL passes `PARM='BATCH_ID=&BATCH_DATE'` but PRVENR01 has no LINKAGE; it reads `ACCEPT ... FROM ENVIRONMENT 'BATCH_ID'` and defaults to `DEFBATCH`.
- **Screening is fully stubbed:** the four checks hard-code `P`; `SCR-SOURCE`, `SCR-VERDICT-DATE`, `SCR-DETAIL` are never populated. **No program reads `aws-transform/test-data/`.**
- **Provider-type codes disagree:** COBOL uses `IN/GR/FA`; the test feed uses `IND/GRP/HOSP/AMB/HOS/LAB/PT/DME/HHA`.

**(inferred) from control flow, not executed (high confidence on COBOL semantics):**
- **PRVENR01 is probably worse than documented.** `GO TO INVALID-APP` leaves the PERFORM range of `VALIDATE-APPLICATION`, so control runs
  `INVALID-APP` -> `WRITE-PROVIDER-RECORD` -> `WRITE-ERROR-RECORD` -> `TERMINATE-PROGRAM` and then off the end of the program. Likely effects:
  the load **stops at the first bad application**, the error row is written twice, and (since the `SET PRV-PENDING/UNSCREENED` lines are skipped)
  the bad record is written with whatever status bytes came in on input. The README and AWS docs describe only "enrolled as PENDING".
- **PRVRSK01 likely never reaches its `REWRITE` (:97):** the `ALTER`ed GO TO leaves the PERFORM range of `APPLY-TIER-PARA` (called at :95); `TIER-DONE` falls into
  `SET-REVALIDATION-DATE` and then off the end of the program.
- **Revalidation date logic:** PRVRSK01 would set `due = today + 5 years` for *every* provider on *every* daily run, so due dates keep sliding forward and PRVREV01 would rarely queue anyone.
- **Won't compile unmodified:** `01 PRV-MASTER-RECORD. COPY PRVRECRD.` nests an `01` under an `01` and duplicates names (e.g. `PRV-NPI` in the FD and in working storage).
  JCL `IF/THEN/ENDIF`, `FUNCTION MOD` and `ACCEPT ... YYYYMMDD` are also unavailable on MVS 3.8j/TK5, which fits the README's note that Hercules runs use separate `*-LAB.jcl` stubs.

## 8. AWS Transform outputs (`aws-transform/`)

| Folder | Contents |
|---|---|
| `01-code-analysis/` | `classification.json`, `dependencies.json`, `assets.csv` (LOC + cyclomatic complexity), `missing-dependencies.csv`, `code-issues.csv`, `codebase-issues.json`, `generic-analysis-{1,2}.json`, `logs/` |
| `02-data-analysis/` | `data-lineage/` (`jcl_to_dsn`, `program_to_dsn`, `dsn_to_file`, csv + json); `data-dictionary/` (cbl / cpy / ddl aggregates + `per-file/`) |
| `03-decomposition/` | `dependency-graph.json` (68 entries, including "Missing ..." placeholders), `decomposition.csv`, `domains.json` |
| `04-documentation/` | PDF + JSON per member for 13 members (BATCH 8, ONLINE 1, JCL 2, VSAM 2), `cover-page.pdf`, `summary.json`. No docs for copybooks / DB2 / REXX / proc |
| `05-business-documentation/` | Business-rule extraction (BRE): `index.html`, application-level spec, 4 entry-point specs (PRVDAY01, PRVMTH01, PRVVSM01, PRVVSM02), per-member html/json. **Tree kept exactly as generated: the HTML cross-links are relative.** Styling loads Cloudscape CSS from `unpkg.com`, so it needs internet |
| `06-test-plan/` | `test-plan.json`: 4 test cases (PRVDAY01, PRVMTH01, PRVVSM01, PRVVSM02) with complexity scores; `businessRuleCount` is 0 |
| `07-test-data-collection/` | **JCL scaffolding, not data:** 17 generated JCLs (IDCAMS REPRO for VSAM, copy-to-S3 for sequential) that would capture before/after datasets, all with placeholder `s3://DOC-EXAMPLE-BUCKET` + profile `default`; plus `datasets.json`, `dataname-mapping.json`, `tokenizer-map.json`, `metrics.json`, `job-lists/`, `logs/`. See "Why there is no real data" below |
| `08-smf-analysis/` | `NO_RECORDS`: no SMF S3 URI was supplied. See "Why there is no real data" below |

**What the run found:** 22 files (cob 9, cpy 5, jcl 4, proc 1, REXX 1, sql 1, unknown 1 = README); graph of 42 nodes / 102 dependencies / 8 entry points
(4 JCL jobs + PRVSAN01, PRVDIR01, PRVCICS1, PRVDEAD1). **Issues (all ERROR):** missing LISTCAT, missing CSD, missing BMS `PRVSET1`, missing CICS defs
`PRVPROVN` + `PINQ`, missing dynamic-call targets `PRVNTGEN/RST/SUS/TRM`, README unclassified.
**Decomposition produced no domain split:** everything is `Unassigned`; the four programs no JCL reaches are unassigned in the BRE as well.
AWS's per-program docs *do* describe the seeded defects (they read the source comments).

**Why there is no real data in `07`, `08` or `test-data/`** (confirmed from the AWS logs)
- **07:** the tool only did template substitution (3 templates: `REPRO.jcl`, `DBUNLOAD.jcl`, `FTPBIN.jcl`; 17 tasks in 0.08 s). It has no connection to a mainframe, and real
  before/after data exists only by **running the actual jobs and capturing their output datasets**. The DB unload template was unused because no DB2 tables are in the test plan.
  Before running the generated JCLs, replace the placeholder bucket/profile.
- **08:** no SMF records S3 URI was given when the job was submitted. The analyzer fell back to scanning the 22 uploaded *source files* as if they were SMF, found 0 records in each,
  and noted "No scrt/ folder found, MSU calculations will be skipped" (SCRT reports are a second optional input). Provide the SMF S3 URI to re-run it.
- **`test-data/`:** not AWS output and never seen by AWS (its `inputs/` held only the 22 source files). These are tiny synthetic samples (fabricated NPIs, `sam.txt` empty by design)
  and, per section 7, don't match the COBOL's record formats anyway.
- **To get real data:** run `PRVDAY01` / `PRVMTH01` (e.g. on the Hercules/TK5 lab the README mentions, whose `*-LAB.jcl` stubs are not in this folder) and capture the datasets
  using the 07 scaffolding; or hand-build fixed-width inputs that match `PRVRECRD` (section 5).

**Gotchas**
- In `07-test-data-collection/generated-jcl/`, the `TC-<job>-<hash>/` folder names and the `&DATE` / `(+1)` filenames are IDs referenced by
  `test-plan.json`, `datasets.json` and `dataname-mapping.json`. They were kept verbatim. **Quote them in shells:** `"B3F1S000_MMPRV_PROV_APPS_D&DATE.jcl"`.
- The unzip intentionally dropped byte-identical duplicates (AWS `inputs/` == `source-code/`, nested copies of the docs and generated JCL) and
  truncated mid-run log snapshots. Everything is still in `_archive/aws-transform-artifacts.zip`.
- Timestamps were stripped from file names; all outputs come from one run on 2026-11-17.

## 9. Test data (`aws-transform/test-data/`)

Pipe-delimited, `#` comment lines. `providers.txt` (8 applications: `npi|name|type|license_no|license_state|owner_npi`),
`leie.txt` (1 exclusion: NPI `1990000111`), `license_board.txt` (7 licenses; `PT99900` is `EXPIRED`), `sam.txt` (empty).
**(inferred) intended scenarios:** `1003000444` has owner `1990000111` who is on LEIE (-> `E203`); `1003000555` has an expired license (-> `E202`);
`1003000666` has no name and an unknown license; the other five should pass. These feeds are shaped for the modern screening engine, not the COBOL (section 7).

## 10. Modernization target (from the README)

| Legacy | Target |
|---|---|
| CICS PRVCICS1 (`PINQ`) | provider self-service portal + analyst console |
| PRVENR01 + PRVDAY01 | enrollment-orchestrator (workflow / BPMN) |
| PRVSCR01 | screening-engine (parallel source calls) |
| PRVRSK01 | rules-risk-engine (Limited / Moderate / High) |
| PRVREV01 | revalidation + continuous-monitoring service |
| PRVTMS01 | T-MSIS extract (EDW / data lake) |
| DB2 PROVIDER + VSAM master | provider-mdm golden record (Aurora) + FHIR API |
| PRVSAN01 | sanctions / exclusion service |
| PRVDIR01 | provider-directory service (FHIR `PractitionerRole`) |
| PRVAUTOM.rexx | event bus |

Migration sequence: provider-mdm -> screening-engine -> enrollment-orchestrator -> rules-risk-engine -> sanctions/directory -> decommission the VSAM master.
The README expects the rebuild in a `modern/` tree (does not exist yet; create it at the project root, not inside `source-code/`).

## 11. Conventions when editing

- **Fixed-format COBOL:** cols 1-6 sequence, col 7 `*` = comment, Area A = cols 8-11 (divisions, sections, paragraph names, `01`), Area B = cols 12-72.
  Text past col 72 is ignored by the compiler. **Files use CRLF line endings: preserve them.**
- Uppercase, member name == PROGRAM-ID / job name, 8 characters max.
- JCL statements start `//`, keep within col 71; DSN symbolics (`&DATE`, `&HLQ.`) are part of the anti-pattern story.
- Don't remove or "tidy" seeded anti-patterns; add new behaviour beside them or mark it clearly.
- Don't hand-edit the generated folders `aws-transform/01-*` to `08-*`; regenerate them. `aws-transform/test-data/` is hand-authored and fine to edit.
  `_archive/` is a backup, so leave it alone.

## 12. Where to look

| Question | Open |
|---|---|
| What does program X do? | `source-code/BATCH/X.cbl` header, then `aws-transform/04-documentation/BATCH/X-cbl.json`, then `05-business-documentation/MMIS-PROVIDER-DEMO-TIER4/BATCH/X-cbl.html` |
| Who reads/writes dataset D? | `02-data-analysis/data-lineage/*.csv` |
| What does a field mean? | `02-data-analysis/data-dictionary/data_dictionary_cpy.csv` |
| Call / dependency graph | `03-decomposition/dependency-graph.json` |
| What's missing from the analysis? | `01-code-analysis/missing-dependencies.csv`, `code-issues.csv` |
| Most complex programs (cyclomatic) | `01-code-analysis/assets.csv`: ENR01 18, SCR01 17, RSK01 16, SAN01 13, CICS1 8, TMS01 6, REV01 5, DIR01 5, DEAD1 3 |
