# The Hercules / TK5 lab: what is there and how the legacy runs were made

Written by the parity checker on 2026-11-20 (stage 1: legacy runs and golden files). Everything below was observed in the
container `mmis-hercules` through `docker exec` and `docker cp`, or measured by the runs. Nothing under `source-code/` or
`aws-transform/` was changed, and no other container or Docker project was touched.

## 1. The lab

| Item | Observed |
|---|---|
| Container | `mmis-hercules`, Up (the lead's look at 15:16 IST: image `mhardingdk/mvs:tk5`, MVS 3.8j TK5, up 38 h). Never started, stopped, restarted or reconfigured by this lane. |
| Clock | The container runs on UTC (`date -u`). MVS follows it (`TZOFFSET +0000` in `/app/mvs/conf/tk5.cnf`). The jobs of this lane ran on **20 Nov 2026**. |
| Config | `/app/mvs/conf/tk5.cnf`: CPU model 3033, 16 MB main storage, HTTP console on 8038 (published as 8040, not used by this lane). |
| Card reader | `000C 3505 3505 sockdev ascii trunc eof`: a TCP socket (published as `localhost:3507`). Every line sent is a card (ASCII translated to EBCDIC, longer than 80 columns is truncated); closing the connection is end of file, so one connection is one job stream. |
| Printer | `000E 1403 prt/prt00e.txt`: JES2 class A output (MSGCLASS=A and SYSOUT=A) lands there as ASCII text. Output of a job starts with four `START JOB nnn` banner lines and ends with `END JOB nnn`. |
| Punch | `000D 3525 pch/pch00d.txt` (empty), second reader `010C` on `jcl/dummy` (empty). |
| Tape | `0480 3420 *` is an empty tape drive; not used (an anonymous job can print, see section 4). |
| Logs | `/app/mvs/log/3033.log` (Hercules console: `$HASP100 name ON READER1` ... `$HASP373 STARTED` ... `$HASP395 ENDED` ... `$HASP150 ON PRINTER1` ... `$HASP250 IS PURGED`) and `hardcopy.log`. The runner waits for `$HASP250 <name> IS PURGED`. |
| DASD | `/app/mvs/dasd/*`: the volume images are compressed (a 3390 volume is 53 MB), so a plain string search of the images finds no known EBCDIC text (`PARMLIB` is not found either). The lab content can only be seen from inside MVS or from its printed output. |
| `/app/mvs/jcl` | contains only an empty file `dummy`. **There are no `PRV*-LAB.jcl` files in the container**, and none in this repository. |

## 2. What the lab already contained (history in the spool)

The printer spool holds the output of 30 jobs of **2026-11-19 07:02-07:32 UTC** (job numbers 154-183), run for another purpose by the owner of
the container: `PRVBLD01` (compiles and links 16 programs with `IKFCBL00`, OS ANS COBOL V2, into `MMPRV.PROD.LOADLIB`), `PRVSRC01`,
`PRVSMK00`, `PRVVSM01/02` (VSAM definitions), `PRVDDL01`, `PRVGDG01` (GDG base `MMPRV.TMSIS.ARCHIVE` and model `MMPRV.TMSIS.MODEL`),
`PRVLOAD1/2` (load the master and the inputs), `PRVDAY01`, `PRVMTH01`, `PRVSAN01`, `PRVDIR01`, `PRVSCRRR`, `PRVJCL01`, `PRVAUTOM`,
`PRVDEAD1`, `PRVEXP01` (IDCAMS PRINT of the datasets) and `PRVDUMP`. Their JCL listings are the only copy of the lab decks that this lane can read,
and the decks of this lane were **reconstructed from them** (`parity/legacy/build_decks.py`); the lab JCL comments list the lab's differences
from `source-code/JCL` (copied into every manifest under `labDifferences.jcl`).

What the lab is:
- The master is an **ISAM** dataset (the 1972 compiler has no VSAM): 130-byte fixed records, 10-byte key at offset 0. The record is 130 bytes, not 121 (D-007).
- Helper programs that are not in the legacy estate: `PRVSEED1` (loads the master from cards, **two cards per record: 80 + 50 bytes**), `PRVLDAP1`
  (the same for the 200-byte application records, bytes 131-200 blank), `PRVUNLD1` (unloads the ISAM master to a flat 130-byte file; IDCAMS and the OS/360 SORT cannot open ISAM),
  `PRVDUMP1`, and four notice stubs `PRVNTTRM/SUS/RST/GEN` that `PRVSAN01` calls.
- The lab programs are a port of `source-code/BATCH/*.cbl` for the V2 compiler. Differences are in `lab-cobol-diff/` (raw diffs per program,
  copybooks are flattened in the lab source, so the diffs are large) and `labDifferences.cobol` in every manifest.
- The lab has jobs for all four jobs of the checklist: `PRVDAY01`, `PRVMTH01`, `PRVSAN01`, `PRVDIR01` (the last two have no JCL in the legacy estate).

## 3. The business date

The lab COBOL takes the date from the MVS `CURRENT-DATE` register (`MM/DD/YY`; the original `ACCEPT ... FROM DATE YYYYMMDD` and `FROM DAY` do not exist in
V2). There is no PARM or control card that sets it. The only way to run on 2026-01-15 would be to change the MVS system date, which this lane may not do.
**The legacy jobs therefore ran on the real date, 2026-11-20** (printed by JES2 as `20 NOV 26 JOB EXECUTION DATE` in each job), and the modern run must use
the same date. `PRVENR01` also receives `PARM='BATCH_ID=20261120'` (D-017 / D-020: the lab reads it from the PARM); that value is a label, not a business date.

## 4. What could not be done as written, and why

- **Lab account.** The lab decks name `USER=HERC01` with a password that the JES2 listing prints blank, and it is not documented in this repository.
  A deck with `USER=HERC01,PASSWORD=` was rejected (`IEF722I PARPRB01 - FAILED - INVALID PASSWORD GIVEN`, job 185). No password was guessed.
- **Anonymous jobs** (no `USER`) are accepted and run programs, read `MMPRV.PROD.LOADLIB` and use temporary datasets, but **cannot create a named permanent dataset**:
  `IEF197I SYSTEM ERROR DURING ALLOCATION` then `IEF453I JOB FAILED - JCL ERROR` for `NEW,CATLG` (job 187), `NEW,KEEP` (189) and the same with `VOL=SER=TSO003` / `WORK04` (190, 191).
- So **every run is one job with temporary datasets**: the inputs are in-stream cards, the lab steps keep their names, programs, DD names and DCBs, and the datasets are
  printed in the same job. The lab's own `MMPRV.*` datasets were neither read nor changed, and the lab was left as found: no permanent dataset of this lane exists
  (the only named allocation that succeeded, `MMPRV.PARITY.PROBE` as `MOD,DELETE` in job 186, was scratched in the same step).
- The consequence for the checklist: **the GDG generation of `PRVMTH01` STEP030 cannot be created** (BLOCKED, the exact error is above). STEP030 ran and wrote a temporary
  copy, `after/tmsis-archive-copy.ebcdic`, identical to `tmsisout.ebcdic`.
- If the owner of the lab provides the account, the same decks can be rerun with permanent datasets; the program results do not depend on the user.

## 4a. Probe jobs (my own work jobs, not legacy jobs)

| Job | Number | Result |
|---|---|---|
| `PARPRB01` | 184 | JCL error: job card past column 71 (my mistake; `herc_lib.check_jcl_columns` now refuses such cards) |
| `PARPRB01` | 185 | `IEF722I INVALID PASSWORD GIVEN` (the blank-password attempt above) |
| `PARPRB01` | 186 | anonymous; STEP010 `IEF197I` |
| `PARDG1/2/3` | 187-189 | `NEW,CATLG` fails, temporary `&&T` works, `NEW,KEEP` fails |
| `PARDG4/5` | 190, 191 | same failure with a pinned volume |
| `PARPRB02` | 192 | all steps RC 0: library readable, temporary ISAM load/unload, `PRINT DUMP`, code page probe |

## 5. The code page: 1047 (the Hercules table), proved

A card with the characters on which 037 and 1047 differ was sent through the reader, copied with IEBGENER and printed with `IDCAMS PRINT DUMP` (job 192):

| Character | Came back as | 037 would be | 1047 is |
|---|---|---|---|
| `[` | `AD` | `BA` | `AD` |
| `]` | `BD` | `BB` | `BD` |
| `^` | `5F` | `B0` | `5F` |
| `!` | `5A` | `5A` | `5A` |
| `|` | `6A` | `4F` | `4F` |

So the lab uses **1047**; the one deviation is `|`, which the Hercules table sends to `6A`. The characters of the synthetic data are only digits, upper-case letters,
blank and `:` (the same in both pages and in the Hercules table), so `ebcdic_to_ascii.py --codepage 1047` is exact for every captured file. The proof is repeated on the data itself:
for every run the captured BEFORE files (master, applications, SANCIN) are **equal to the loaded inputs encoded with `encode(..., '1047')`** (`equalsLoadedInput: true` in the manifests).

## 6. How the files were captured, and the proof

- Inputs: `PRVSEED1` / `PRVLDAP1` read two 80-column cards per record. The master is loaded **in ascending NPI order** (an ISAM load needs it), so
  `inputs/provider-master-seed-sorted.dat` is `provider-master-seed.dat` sorted by NPI (same 16 records). `appinput.dat` is the baseline file (or its first 3 records).
- Capture: `IDCAMS PRINT INFILE(DDnn) DUMP` prints every record as hex (32 bytes per line, hex offsets). The hex digits are the EBCDIC bytes; `parse_dump.py` rebuilds the bytes,
  and checks contiguous offsets, record numbers and the `IDC0005I` record count. No code-page conversion is involved in the capture.
- The master is captured through `PRVUNLD1` (flat file, 130 bytes), before the job and after each step.
- Proof on the BEFORE master: the bytes rebuilt from the dump equal the loaded bytes, for all 16 records, in all five runs (and in the probe job 192).
- Converted copies: `<name>.ebcdic.ascii` next to every `.ebcdic` file, made with `tools/data/ebcdic_to_ascii.py INPUT OUTPUT --codepage 1047 --lrecl n`; the originals are unchanged and every
  file size is records x record length (checked against the manifests).

## 7. Layout

```
golden/<JOB>/<run-id>/{inputs,before,after,logs}/  manifest.json
golden/lab-cobol-diff/                             per program diff and summary.json
```

| Job | Run id | MVS job | What it is |
|---|---|---|---|
| PRVDAY01 | `20261120-baseline` | 193 | the baseline applications (8 records): STEP010 ends RC=4, STEPs 015-040 are bypassed |
| PRVDAY01 | `20261120-valid-only` | 194 | records 1-3 of the baseline (the valid ones) so that STEP030 and STEP040 run |
| PRVMTH01 | `20261120-seed` | 195 | seed master: REVALQ, TMSISOUT, the STEP030 copy |
| PRVDIR01 | `20261120-seed` | 196 | seed master: DIROUT |
| PRVSAN01 | `20261120-sancin-baseline` | 197 | seed master and the 6 SANCIN records |

Reproduce: from `modern/`, `kag/.venv/Scripts/python.exe parity/legacy/run_legacy.py <day-baseline|day-valid|mth|dir|san> --attempt 1 --scratch <dir>`
(`build_decks.py` builds the deck, `herc_lib.py` talks to the container, `parse_dump.py` rebuilds the bytes, `lab_cobol_diff.py` makes the COBOL diffs).
