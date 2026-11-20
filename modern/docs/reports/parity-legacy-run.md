# Parity, stage 1: the legacy PEM jobs run on the Hercules / TK5 lab

Date of the work: 2026-11-20. Author: parity checker. Scope of this stage: run the legacy jobs, capture their files as golden files. The comparison with the
modern system is a later stage. Details of the lab and of the method: `modern/parity/golden/README-hercules-lab.md`. Tools: `modern/parity/legacy/`.

## 1. Result in one table

| Job | Run id | MVS job | Attempts | Result | Step return codes |
|---|---|---|---|---|---|
| PRVDAY01 | `20261120-baseline` | 193 | 1 | ran; STEP010 RC=4 (first bad application), STEPs 015-040 bypassed by COND | LOAD010 PRVSEED1 RC= 0000; LOAD020 PRVLDAP1 RC= 0000; LOAD030 PRVUNLD1 RC= 0000; LOAD040 IDCAMS RC= 0000; STEP010 PRVENR01 RC= 0004; CAP010 PRVUNLD1 RC= 0000; CAP011 IDCAMS RC= 0000; STEP015 PRVUNLD1 *FLUSH*; STEP020 SORT *FLUSH*; STEP030 PRVSCR01 *FLUSH*; CAP030 PRVUNLD1 *FLUSH*; STEP040 PRVRSK01 *FLUSH*; CAP040 PRVUNLD1 *FLUSH*; STEP099 IEFBR14 *FLUSH*; CAP099 IDCAMS *FLUSH* |
| PRVDAY01 | `20261120-valid-only` | 194 | 1 | ran; all steps RC=0 | LOAD010 PRVSEED1 RC= 0000; LOAD020 PRVLDAP1 RC= 0000; LOAD030 PRVUNLD1 RC= 0000; LOAD040 IDCAMS RC= 0000; STEP010 PRVENR01 RC= 0000; CAP010 PRVUNLD1 RC= 0000; CAP011 IDCAMS RC= 0000; STEP015 PRVUNLD1 RC= 0000; STEP020 SORT RC= 0000; STEP030 PRVSCR01 RC= 0000; CAP030 PRVUNLD1 RC= 0000; STEP040 PRVRSK01 RC= 0000; CAP040 PRVUNLD1 RC= 0000; STEP099 IEFBR14 RC= 0000; CAP099 IDCAMS RC= 0000 |
| PRVMTH01 | `20261120-seed` | 195 | 1 | ran; all steps RC=0; **GDG generation BLOCKED** (section 5) | LOAD010 PRVSEED1 RC= 0000; LOAD030 PRVUNLD1 RC= 0000; LOAD040 IDCAMS RC= 0000; STEP010 PRVREV01 RC= 0000; STEP020 PRVTMS01 RC= 0000; STEP030 IEBGENER RC= 0000; CAP030 PRVUNLD1 RC= 0000; CAP099 IDCAMS RC= 0000 |
| PRVDIR01 | `20261120-seed` | 196 | 1 | ran; all steps RC=0 | LOAD010 PRVSEED1 RC= 0000; LOAD030 PRVUNLD1 RC= 0000; LOAD040 IDCAMS RC= 0000; STEP010 PRVDIR01 RC= 0000; CAP030 PRVUNLD1 RC= 0000; CAP099 IDCAMS RC= 0000 |
| PRVSAN01 | `20261120-sancin-baseline` | 197 | 1 | ran; all steps RC=0 | LOAD010 PRVSEED1 RC= 0000; LOAD020 IEBGENER RC= 0000; LOAD030 PRVUNLD1 RC= 0000; LOAD040 IDCAMS RC= 0000; STEP010 PRVSAN01 RC= 0000; CAP030 PRVUNLD1 RC= 0000; CAP099 IDCAMS RC= 0000 |

All five were accepted on the first attempt; no job needed a second one. Nine work jobs of my own (184-192) were run before them (section 5).
Every run is **one anonymous job with temporary datasets** (the lab account password is not available, an anonymous job cannot create a permanent dataset).

## 2. Business date, batch id, code page

- **Business date really used: 2026-11-20** (`20 NOV 26 JOB EXECUTION DATE` in each job; submitted between 10:27 and 10:34 UTC). The lab COBOL reads the MVS `CURRENT-DATE` register
  and has no PARM or control card for the date; 2026-01-15 would need a change of the MVS system date, which is forbidden. The modern run must use **2026-11-20**.
- `PRVENR01` PARM `BATCH_ID=20261120` (a label; the program only displays it).
- The T-MSIS period written by `PRVTMS01` is therefore `26324` (day 324 of 2026) and `PRVRSK01` sets due dates to `20311120`.
- **Code page 1047** (the Hercules table: `[`=AD, `]`=BD, `^`=5F; 037 would give BA, BB, B0; `|` comes back as 6A, which does not occur in the data). Proof in the lab README, section 5, and
  on the data: the BEFORE captures equal the loaded inputs encoded with 1047 in every run.
- Memory: the lane paused **360 s** in total because free memory fell under 1024 MB (60 s before job 186, 60 s before job 193, 60 s before job 194, 180 s before job 197); it never failed for memory.

## 3. Files captured (record length as written, sha256 of the binary file)

### PRVDAY01 `20261120-baseline` (MVS job 193)

| File | Records x bytes | Total bytes | sha256 | Equals loaded input | `.ascii` sha256 |
|---|---|---|---|---|---|
| `inputs/provider-master-seed-sorted.dat` | 16 x 130 | 2096 | `3febd267db27375183df9859f57776ed0d9c986b7a9fe08c3480dcefe741563c` | n/a | n/a |
| `inputs/appinput.dat` | 8 x 200 | 1608 | `bd9bd0a834d86743844514d704122bf5688737b9eb84456126245a6fdbf5b420` | n/a | n/a |
| `before/appinput.ebcdic` | 8 x 200 | 1600 | `76237019f64a8ff6b2adea7df996cf25201c8cd4b771b4d8976c01991bec6c59` | yes | `95b7be1ca2d69132b45e3043a100d41548fe89a1bc6b57776ff08f3f5a2c28f2` |
| `before/provider-master.ebcdic` | 16 x 130 | 2080 | `f87732f7a20e0b89f7aa9cc33a6c351fac1dd67f02029c59310e2c530c4ee90a` | yes | `2062175f6cd1aaa1860b5ad2bdf717e191fb046d4445b4499503c9f1888fce00` |
| `after/apperror.ebcdic` | 2 x 250 | 500 | `7e90d7b82064a418d4695d899f14cf83a66223e95ffe646f9581094282665dbd` | n/a | `c3fe943bf62c8bfaf88927e57eb36230b5e3f6be426b2d6d6b7c1648c44da6aa` |
| `after/provider-master-after-step010.ebcdic` | 20 x 130 | 2600 | `0b850051c7e8b21579bafd0deddc97509f795501e88577941607ea93cadb2f29` | n/a | `4c5d299a36cf8c930b860464db83198bf529106751a4a4e27aec07a626050afb` |
| `scrnout.ebcdic` | not produced | | | | step STEP030 was bypassed (COND=(0,LT,STEP010), STEP010 ended with RC=4): the legacy job never created it |
| `provider-master-after-step030.ebcdic` | not produced | | | | step CAP030 was bypassed (COND=(0,LT,STEP010), STEP010 ended with RC=4): the legacy job never created it |
| `provider-master-after-step040.ebcdic` | not produced | | | | step CAP040 was bypassed (COND=(0,LT,STEP010), STEP010 ended with RC=4): the legacy job never created it |

Lab JCL used: `logs/submitted-PRVDAY01.jcl`, sha256 `76e8d4410e427a349b6927c7c65d4a8de77e5c58ed01cacc5148c5768177048e` (JES2 JCL listing of the lab job of 2026-11-19 in the printer spool (the *-LAB.jcl stubs are not in the container or the repository))

### PRVDAY01 `20261120-valid-only` (MVS job 194)

| File | Records x bytes | Total bytes | sha256 | Equals loaded input | `.ascii` sha256 |
|---|---|---|---|---|---|
| `inputs/provider-master-seed-sorted.dat` | 16 x 130 | 2096 | `3febd267db27375183df9859f57776ed0d9c986b7a9fe08c3480dcefe741563c` | n/a | n/a |
| `inputs/appinput.dat` | 3 x 200 | 603 | `f42a9af137205bde766bc977922e2af75606145a44391228ed432bc348086617` | n/a | n/a |
| `before/appinput.ebcdic` | 3 x 200 | 600 | `4daf893fb71dcee7a1531898d5f87f510ccf1bfad0f1f0a39029ef3031f00518` | yes | `2f4dc25a833f195b374bc935b2808a766a6ef9a2f88b40c44870f604ef017399` |
| `before/provider-master.ebcdic` | 16 x 130 | 2080 | `f87732f7a20e0b89f7aa9cc33a6c351fac1dd67f02029c59310e2c530c4ee90a` | yes | `2062175f6cd1aaa1860b5ad2bdf717e191fb046d4445b4499503c9f1888fce00` |
| `after/apperror.ebcdic` | 0 x 250 | 0 | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` | n/a | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |
| `after/provider-master-after-step010.ebcdic` | 19 x 130 | 2470 | `5f14fc59826e696aa7e0ffe6b71dda16a952d4eafc8e479dd9a11606471f5013` | n/a | `50a996ac763df05f4108d755829767fc4b14c090bb72c383316144ecfa6497b3` |
| `after/scrnout.ebcdic` | 20 x 67 | 1340 | `19e1522a615ba7b61c8b058a93ca079013b9d1aaeb8e761bad437a3cede4d537` | n/a | `451873019ac1f2f7a56597243719b7e65d23a22b27f917a8e1f25cc06174a7d1` |
| `after/provider-master-after-step030.ebcdic` | 19 x 130 | 2470 | `bd7dba3360f68c17da4d2d81837cb96e3b928111ffe6b0685c0667d81d311471` | n/a | `0e3e18e3ac186c07d44ffa42338979df38b2470a5a3e0b69c9fa7ea5be3bdf43` |
| `after/provider-master-after-step040.ebcdic` | 19 x 130 | 2470 | `91315ea6044d2cf9458fe453cfcf344b3d6bdda0e280eb56b94c243da9c84810` | n/a | `9a8367877fa9c34d37a15515fe78d76cd83a2281bac1c47cc40877f05d7fca18` |

Lab JCL used: `logs/submitted-PRVDAY01.jcl`, sha256 `99e0994c9fd1b26b7486b98f41237b2d5d96eb98d8eedb5f3a8dd6e329cb7cba` (JES2 JCL listing of the lab job of 2026-11-19 in the printer spool (the *-LAB.jcl stubs are not in the container or the repository))

### PRVMTH01 `20261120-seed` (MVS job 195)

| File | Records x bytes | Total bytes | sha256 | Equals loaded input | `.ascii` sha256 |
|---|---|---|---|---|---|
| `inputs/provider-master-seed-sorted.dat` | 16 x 130 | 2096 | `3febd267db27375183df9859f57776ed0d9c986b7a9fe08c3480dcefe741563c` | n/a | n/a |
| `before/provider-master.ebcdic` | 16 x 130 | 2080 | `f87732f7a20e0b89f7aa9cc33a6c351fac1dd67f02029c59310e2c530c4ee90a` | yes | `2062175f6cd1aaa1860b5ad2bdf717e191fb046d4445b4499503c9f1888fce00` |
| `after/revalq.ebcdic` | 4 x 80 | 320 | `8ad08fed0dcfe28932d7145ec87464c1a3904e8f0675a8ea94126bd184cf788d` | n/a | `cdb95c53868afbeb8094d66cffd70345c32899f375f4bd06f81ee7a5232af8ff` |
| `after/tmsisout.ebcdic` | 8 x 120 | 960 | `7116ba03d5fd4d8af2aaa5ceb8dcdf2b79c252cf882935efe14916c54cb535f1` | n/a | `ff1b0d0a5e87e7d9caf1a1edc3b7ab7719cc64fd46899745828aa6a429fd0858` |
| `after/tmsis-archive-copy.ebcdic` | 8 x 120 | 960 | `7116ba03d5fd4d8af2aaa5ceb8dcdf2b79c252cf882935efe14916c54cb535f1` | n/a | `ff1b0d0a5e87e7d9caf1a1edc3b7ab7719cc64fd46899745828aa6a429fd0858` |
| `after/provider-master-after.ebcdic` | 16 x 130 | 2080 | `f87732f7a20e0b89f7aa9cc33a6c351fac1dd67f02029c59310e2c530c4ee90a` | n/a | `2062175f6cd1aaa1860b5ad2bdf717e191fb046d4445b4499503c9f1888fce00` |

Lab JCL used: `logs/submitted-PRVMTH01.jcl`, sha256 `d7796979231282cd6052adbe00773e6b3ca52c4c72d05bc6066957baf45fbfc0` (JES2 JCL listing of the lab job of 2026-11-19 in the printer spool (the *-LAB.jcl stubs are not in the container or the repository))

### PRVDIR01 `20261120-seed` (MVS job 196)

| File | Records x bytes | Total bytes | sha256 | Equals loaded input | `.ascii` sha256 |
|---|---|---|---|---|---|
| `inputs/provider-master-seed-sorted.dat` | 16 x 130 | 2096 | `3febd267db27375183df9859f57776ed0d9c986b7a9fe08c3480dcefe741563c` | n/a | n/a |
| `before/provider-master.ebcdic` | 16 x 130 | 2080 | `f87732f7a20e0b89f7aa9cc33a6c351fac1dd67f02029c59310e2c530c4ee90a` | yes | `2062175f6cd1aaa1860b5ad2bdf717e191fb046d4445b4499503c9f1888fce00` |
| `after/dirout.ebcdic` | 8 x 100 | 800 | `b26c8d6bb6823d21084a597a88e8031e1f42085cbc274312f67130d949c124d7` | n/a | `03c980698c4617ecffb5c81175e884f282787e40ba2e2fcf9185c7b6df502d44` |
| `after/provider-master-after.ebcdic` | 16 x 130 | 2080 | `f87732f7a20e0b89f7aa9cc33a6c351fac1dd67f02029c59310e2c530c4ee90a` | n/a | `2062175f6cd1aaa1860b5ad2bdf717e191fb046d4445b4499503c9f1888fce00` |

Lab JCL used: `logs/submitted-PRVDIR01.jcl`, sha256 `fd7247fc52a9166e32087fba5bedf03809e36d2ba9ccac3c255923b121e0fd2a` (JES2 JCL listing of the lab job of 2026-11-19 in the printer spool (the *-LAB.jcl stubs are not in the container or the repository))

### PRVSAN01 `20261120-sancin-baseline` (MVS job 197)

| File | Records x bytes | Total bytes | sha256 | Equals loaded input | `.ascii` sha256 |
|---|---|---|---|---|---|
| `inputs/provider-master-seed-sorted.dat` | 16 x 130 | 2096 | `3febd267db27375183df9859f57776ed0d9c986b7a9fe08c3480dcefe741563c` | n/a | n/a |
| `inputs/sancin.dat` | 6 x 80 | 486 | `caa42d0042c3aa1070cc8177c66549ba557c848e895114b5134e8be288a1a37a` | n/a | n/a |
| `before/sancin.ebcdic` | 6 x 80 | 480 | `cdf0d2d73b6813c1bfaec8282c73e7e4d4e5ab73c5469ff0cdb9a2f6f096b6a2` | yes | `595972013aeb651f19959d1dd4296302a9149b799fa6b6d8ca337cf4df54fc14` |
| `before/provider-master.ebcdic` | 16 x 130 | 2080 | `f87732f7a20e0b89f7aa9cc33a6c351fac1dd67f02029c59310e2c530c4ee90a` | yes | `2062175f6cd1aaa1860b5ad2bdf717e191fb046d4445b4499503c9f1888fce00` |
| `after/provider-master-after.ebcdic` | 16 x 130 | 2080 | `8f594bb31634af1dac3b53d9220f1c6f0f93cc526137e613a70f6b06c2e7fafe` | n/a | `c6f0ea98bec5e27d453947485bb3e799c2e97e4aedca3740c250e354062db849` |

Lab JCL used: `logs/submitted-PRVSAN01.jcl`, sha256 `882697bcd2f9575693fd876f585f5672c786c66e15b95b98f10b3697e5e3d322` (JES2 JCL listing of the lab job of 2026-11-19 in the printer spool (the *-LAB.jcl stubs are not in the container or the repository))

Input files: `provider-master-seed-sorted.dat` is `testdata/fixed-width/provider-master-seed.dat` sorted by NPI (the ISAM load needs ascending keys); `appinput.dat` is
`appinput-baseline.dat` (8 records) or its first three records; `sancin.dat` is `sancin-baseline.dat`. All three are byte-identical to the repository files (checked), with LF line ends.
Each ASCII input line is one record; the `.ebcdic` files have no line terminators (records back to back).

Integrity check (run after the last job): every file hash equals its manifest, every size is records x record length, every `.ascii` file has the size of its `.ebcdic` file.

## 4. What the legacy jobs did (observations for the comparison stage; not classified here)

These are facts read from the golden files. Whether each is intended is for the comparison and the user (decisions D-001 to D-029).

- **PRVENR01 stops at the first bad application.** With the baseline file (record 4 has no NPI) it reads 4, writes 4, counts 1 error and ends with RC=4; records 5-8 are never read.
  The error file has **2 records** for that one application (`<11 blanks>TYPE:IN ERR:VALIDATION-FAILED` twice, 250 bytes each) and the master gets **a fourth new record, with a blank NPI**
  (status, flag and tier bytes blank), so the master has 20 records instead of 16 + 3. Steps 015-040 are bypassed, so there is no SCRNOUT and no risk tiering for this run.
  (This matches the code-reading finding behind D-001.)
- **Valid applications only** (records 1-3): PRVENR01 reads 3, writes 3, 0 errors; the three new providers are `P`ending, flag `U`. **PRVSCR01** screens the five unscreened providers (the three new and the two seed `PU`
  providers) and writes **20 SCRNOUT records, 4 per provider**, each 67 bytes with only the NPI and the result `P` filled (source, date and detail are blank); all become flag `P`. **PRVRSK01 does reach its REWRITE** in the lab port:
  tiers are saved (`L`/`M`/`H`; e.g. `1005933068` taxonomy 251E gets `H`, three new providers get `L`) and **the revalidation due date of every provider, including terminated and denied ones, becomes `20311120`** (today + 5 years).
- **PRVMTH01**: PRVREV01 queues **4** providers (due dates 20251201, 20260115, 20260215, 20260331, all `RESCREEN-REQUIRED`); PRVTMS01 writes **8** rows with period `26324`; the master is unchanged
  (its hash equals the BEFORE hash). The STEP030 copy equals TMSISOUT.
- **PRVDIR01**: 8 rows; master unchanged.
- **PRVSAN01** (six SANCIN records): `T` on 1005943141 gives `T`; **`S` on 1005992312 gives `D` (DENIED)**; `R` on 1005919109 (suspended) gives `A`; `R` on the pending 1005933068 gives `A` (D-027); the unknown NPI 1006143592 is reported
  `NPI NOT FOUND`; the invalid action `X` on 1005912765 leaves the record unchanged and queues a general notice (`PRVNTGEN`); the run counts `PROVIDERS TERMINATED=00001`. The four notice stubs only display a line.

## 5. What could not be done (exact errors)

| What | Exact error | Effect |
|---|---|---|
| Run as the lab account | `IEF722I PARPRB01 - FAILED - INVALID PASSWORD GIVEN` (job 185, deck with `USER=HERC01,PASSWORD=`; the password is printed blank in the spool and is not documented in the repository). No other password was tried. | Runs are anonymous. |
| Permanent datasets | `IEF197I SYSTEM ERROR DURING ALLOCATION`, then `IEF453I PARDG1 - JOB FAILED - JCL ERROR` (job 187 `NEW,CATLG`), job 189 `NEW,KEEP`, jobs 190 and 191 with `VOL=SER=TSO003` and `WORK04` | Every dataset is temporary; the lab's own `MMPRV.*` datasets are untouched. |
| **GDG generation of PRVMTH01 STEP030** (`MMPRV.TMSIS.ARCHIVE(+1)`) | same `IEF197I` (a cataloged generation is a permanent dataset) | **BLOCKED.** STEP030 ran and wrote the temporary copy `after/tmsis-archive-copy.ebcdic` (same sha256 as `tmsisout.ebcdic`); only the catalog generation is missing. |
| Business date 2026-01-15 | the lab programs read the MVS system date; changing it is forbidden | The legacy side is dated 2026-11-20; the modern run uses the same date. |
| PRVDAY01 STEP010 with the baseline file continues after the first bad record | not a failure: legacy behaviour | A second run with the valid records exercises STEP030 and STEP040. |
| The `*-LAB.jcl` stubs | not in the container (`/app/mvs/jcl` holds only `dummy`) or the repository | Decks reconstructed from the JES2 listings of the lab jobs 165 and 169 and others (job 154-183, 2026-11-19). |

My own work jobs before the runs: 184 (JCL error, job card past column 71, my mistake, the runner now refuses such cards), 185 (password), 186 (anonymous, `IEF197I`), 187-191 (allocation diagnostics), 192 (probe: library readable,
temporary ISAM, DUMP format, code page). They are not legacy jobs.

## 6. Differences between the lab stubs and `source-code/` (AC-8)

Recorded in every manifest under `labDifferences` (`jcl`, `cobol`, `parityRunAdditions`) and in `parity/golden/lab-cobol-diff/` (`summary.json` and one `.diff` per program, taken from the SOURCE listing of the lab build job 154).
In short:

- JCL: `IF/THEN` replaced by `COND`, `&DATE` and `&BATCH_DATE` given values (D-020), VSAM replaced by ISAM with 130-byte records (D-007), STEP005 (rerun scratch) and STEP015 `PRVUNLD1` added by the lab, the SORT `INCLUDE`
  card removed and its output still unused (D-009), `UNIT=TAPE,LABEL=(1,SL)` replaced by disk and a GDG model (PRVMTH01), no JCL for PRVSAN01 / PRVDIR01 in the legacy estate.
- COBOL: V2 port (VSAM to ISAM, `ACCEPT FROM DATE` to `CURRENT-DATE`, `EVALUATE`/`FUNCTION MOD`/reference modification rewritten, `BATCH_ID` from the PARM, dynamic `CALL` dispatched to four literal calls with notice stubs).
  The control flow, the fall-through of PRVENR01, the `ALTER` of PRVRSK01 and the 2-digit-year arithmetic are stated to be unchanged by the lab's own headers.
- **`PRVTMS01.cbl:114`** (`MOVE WS-TODAY-JUL ... TO WS-T-PERIOD-YYDDD`, 73 columns, D-021): the lab port re-spaces the statement so that the identifier ends inside column 72, so the compiled lab program is the "patched copy"
  the story asks for; `source-code/` is unchanged (`git status` shows no change under `source-code/` or `aws-transform/`).
- This run only: no `USER`/`PASSWORD`, temporary datasets, `LOADnnn` and `CAPnnn` read-only steps added, STEP005 dropped, master loaded in ascending NPI order.

## 7. Open points for the next stages

1. The modern run must use the date **2026-11-20**, the sorted seed and the same applications (`inputs/` of each run).
2. If the lab account is provided (not in chat; the project rule is the `.env` file), the decks can be rerun with permanent datasets and the GDG generation captured. Results of the programs do not depend on the user.
3. Differences expected from the observations above (to be proposed, not decided here): D-001 (stop at first error, double error row, bad record on the master), D-003 (due date reset to today + 5 years for everyone),
   D-004 (2-digit year), D-005 (screening stubs), D-006 (`S` gives DENIED), D-015 (YYDDD period), D-027 (`R` on a pending provider).

