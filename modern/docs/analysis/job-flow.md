# Job flow

How the legacy jobs run, step by step. Paths are relative to `source-code/`. Nothing here was executed; "INFERRED" marks
conclusions read from the code. Scheduler definitions are not in the estate (AWS's scheduler-knowledge step processed 0 files).

## PRVDAY01: daily enrolment (`JCL/PRVDAY01.jcl`)

Submitted by the REXX monitor `REXX/PRVAUTOM.rexx` (line 15), which polls SDSF up to 30 times (line 23, with no wait between polls) and only
writes an operator message when a step's return code is not `CC 0000` (alert routing is stubbed, line 40). `JOBLIB` is `MMPRV.PROD.LOADLIB`.

| Seq | Step | Program | Reads | Writes | Runs when | What happens on failure |
|---|---|---|---|---|---|---|
| 1 | STEP010 | PRVENR01 | `APPINPUT` = MMPRV.PROV.APPS.D&DATE (OLD,KEEP,KEEP, 200 bytes); `PRVMSTR` master KSDS | `PRVMSTR` (new PENDING records); `APPERROR` = MMPRV.PROV.ERRORS.D&DATE (NEW,CATLG, LRECL 250) | always | RC 4 if any application failed validation; RC 8 on a read or write error, but RC 4 at the end overwrites it (D-024). INFERRED: the run stops at the first invalid application (D-001). |
| - | IF (STEP010.RC = 0), line 38 | | | | | **Any non-zero RC from STEP010 skips steps 020 to 040.** So a single invalid application (RC 4) means no screening and no risk tiering that day. |
| 2 | STEP020 | SORT | `SORTIN` = master KSDS | `SORTOUT` = &&PRVSORT (temporary) | STEP010 RC = 0 | `INCLUDE COND=(23,1,CH,EQ,C'U')` (line 50) tests byte 23, inside the taxonomy, not the screen flag at byte 34; the output is never read. D-009 DROP. |
| 3 | STEP030 | PRVSCR01 | master KSDS (I-O) | master (screen flag); `SCRNOUT` = MMPRV.SCREEN.RESULTS.D&DATE (LRECL 67) | STEP010 RC = 0 | No return code is ever set; statuses untested (D-024). |
| 4 | STEP040 | PRVRSK01 | master KSDS (I-O) | master (risk tier, revalidation due date) | STEP010 RC = 0 | No return code. INFERRED: the REWRITE may never run (D-002). |
| 5 | STEP099 | IEFBR14 | none | none | `COND=(0,LT)`: bypassed if any earlier step ended with RC > 0 | Does nothing; a marker step. |

## PRVMTH01: month end (`JCL/PRVMTH01.jcl`)

No `COND` and no `IF`: every step runs even when an earlier one fails.

| Seq | Step | Program | Reads | Writes | What happens on failure |
|---|---|---|---|---|---|
| 1 | STEP010 | PRVREV01 | master KSDS | `REVALQ` = MMPRV.REVAL.QUEUE.D&DATE (LRECL 80) | No return code is set. The due dates it compares are reset daily by PRVRSK01 (D-003). |
| 2 | STEP020 | PRVTMS01 | master KSDS | `TMSISOUT` = MMPRV.TMSIS.PROVIDER.D&DATE (LRECL 120) | No return code is set. INFERRED: the program does not compile as shipped (D-021). |
| 3 | STEP030 | IEBGENER | `SYSUT1` = the T-MSIS file | `SYSUT2` = MMPRV.TMSIS.ARCHIVE(+1), a new GDG generation on tape | Runs even if STEP020 failed, archiving whatever is there. |

## One-time definition jobs (`VSAM/`)

| Job | Defines |
|---|---|
| PRVVSM01 | `MMPRV.PROVIDER.MASTER.KSDS` (key NPI, 10 bytes at offset 0, RECORDSIZE 121, SHAREOPTIONS(2 3)), an alternate index on the Medicaid ID (9 bytes at offset 10, non-unique) and its PATH. Deletes the cluster first (`SET MAXCC = 0`). |
| PRVVSM02 | `MMPRV.SCREEN.RESULTS.KSDS` (key NPI + source, 18 bytes, record 67) and `MMPRV.SANCTION.MASTER.KSDS` (key NPI, record 80). **No program uses either cluster** (D-018). |

## Cataloged procedure PRVSCRPR (`JCL/PROCS/PRVSCRPR.jcl`)

Runs PRVSCR01 with dataset names built from `&HLQ` (default MMPRV) and `&DTE` (default 00000000). **Nothing in the estate calls it**
(AP-11); PRVDAY01 runs PRVSCR01 directly.

## Programs that no job runs

| Program | Why it matters |
|---|---|
| PRVSAN01 (sanctions, PEMAPP2) | Rewrites the provider master. The estate README lists it as step 5 of the run order, but no JCL exists for it; it needs a `SANCIN` dataset. |
| PRVDIR01 (directory, PEMAPP2) | Produces the MCO directory file; needs a `DIROUT` dataset; no JCL. |
| PRVCICS1 (PINQ) | Online CICS transaction; the CSD and BMS map are not in the estate. |
| PRVDEAD1 | Orphan, dropped (D-011). |

## Order between jobs (implicit)

PRVDAY01 must run before PRVMTH01 (PRVREV01 reads the due dates PRVRSK01 writes). PRVSAN01 changes the status that PRVREV01, PRVTMS01
and PRVDIR01 filter on, so it must run before them; no schedule in the estate states this. `&DATE` and `&BATCH_DATE` are defined
nowhere (D-020), so the business date has to become an explicit parameter.
