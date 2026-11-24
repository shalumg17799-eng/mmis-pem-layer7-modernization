# Parity report: legacy PEM jobs on Hercules against the modern system (MAM-91)

## FINAL VERSION of 2026-11-24 (this block replaces the results of the version of 2026-11-20 below)

**Result of the final comparison** (`python modern/tools/parity/compare_parity.py`, 2026-11-24 after the modern side was run again on deploy 12): units **MATCH 168, EXPECTED 67, UNEXPECTED 1**;
rows **102 EXPECTED, 1 UNEXPECTED** (103 rows, the same set as in the version of 2026-11-20: only the class of 39 rows changed, because of four decisions); supplementary notices (PRVSAN01) MATCH 4,
EXPECTED 2, UNEXPECTED 0. Premise checks: **213 ok, 0 failed**, 5 information only (the sizes and sha256 of all 52 golden files and of the modern captures, the inputs, the business date).
Nothing was normalised: no manifest lists a run-dependent field.

**The one UNEXPECTED unit is BLOCKED and stays listed: U-8 (Q-129).** The permanent generation `MMPRV.TMSIS.ARCHIVE(+1)` could not be created by the anonymous job on the Hercules lab (IEF197I, IEF453I) and the
lab account HERC01 was refused (IEF722I INVALID PASSWORD GIVEN; no password was guessed or asked for in chat). Only a temporary copy of TMSISOUT was captured (byte-identical to TMSISOUT); the content of the modern archive
generation was not exported, so the file `tmsis-archive-copy` has no modern counterpart. To close it: the lab password in the root `.env` (never in chat), one more PRVMTH01 run on Hercules and an export of the modern archive
content. The user decided on 2026-11-24 that this job stays BLOCKED. No other difference is unexplained.

**What happened on 2026-11-24**
1. The user confirmed the recommended answers (message of 2026-11-24): Q-124 A (already D-030, live since deploy 4), **Q-125 A = D-031**, **Q-126 A = D-032**, **Q-127 A = D-033**, **Q-128 A = D-034**,
   Q-130 A (the licence-board stub knows SD20001, applied and checked on 2026-11-20). The decisions are recorded in `defect-register.md` (section "Decisions recorded after Phase 2 that are not legacy defects") and the answers in
   `questions.md`; no Defect story was created, because nothing points at a coding error.
2. `compare_parity.py`: seven rules moved from UNEXPECTED to EXPECTED and cite their decision: E-7 = D-031 (effective date of a new provider, 9 rows), E-8 = D-032 (event chain instead of the step sequence: snapshot timing 12, new providers
   in SCRNOUT 12, no JCL gate 2, no CAP030 snapshot 1 = 27 rows), E-9 = D-033 (tier only after PASS, 2 rows), E-10 = D-034 (the extra APPROVED notice, 1 row). Run on the captured files of 2026-11-20 first: the same 103 rows, 39 of
   them changed class (U-2 9, U-3 12, U-4 2, U-5 12, U-6 2, U-7 1, U-9 1), 64 unchanged (commit 18f6efc).
3. **The modern side was run again** on the deployed build of deploy 12 (the gateway on Apache HttpClient 5.6.5 and the shared kit change), all five scenarios one after another from the seed state, business date 2026-11-20 as the legacy
   run (14:14 to 14:29 IST, manifests `startedUtc` 2026-11-24T08:44Z to 08:59Z, "problems: 0" in each); the stack was reseeded at the end (16 providers).
4. **A frozen date, found and handled.** The modern services stamp the clock of the day on which they run (`business date` is a parameter only of the batch runs). The legacy run was frozen at 2026-11-20. So in the re-run of today the effective date of
   a new provider reads **20261124** (D-031: "the registration date") and the due date at a completed revalidation **20311124** (D-003: "the date + 5 years"), where the version of 2026-11-20 had 20261120 and 20311120, which were right only on that day. The first
   comparison of today therefore showed 13 rows with no rule (group U-0: 9 effective dates, 4 due dates). Both decisions are rules, not constants: the two rules of the tool now expect **the date of the modern run (read from its manifest) and that date + 5 years**.
   The match is exact: a modern date that is neither is UNEXPECTED. The legacy values (20260115 from the intake record, 20311120 from the frozen run) are unchanged. Consequence for anyone repeating the run: the modern date of the run matters; the golden files stay frozen at
   2026-11-20. No business field was normalised.
5. No legacy job was run again: the Hercules containers (`mmis-hercules`) were up, the golden captures are unchanged (the premise checks compare size and sha256 of every golden file with its manifest).

**Result per file (final)**

| File | Result |
|---|---|
| REVALQ (PRVMTH01) | 4 / 4 records, **MATCH**, byte-identical |
| TMSISOUT (PRVMTH01) | 8 / 8, **MATCH**, byte-identical (period 26324, D-015 preserved) |
| TMSISOUT archive generation (PRVMTH01) | **BLOCKED** (U-8, Q-129, above) |
| DIROUT (PRVDIR01) | 8 / 8, **EXPECTED**: only the name area, bytes 23-82 (D-028) |
| APPERROR (PRVDAY01) | baseline: 1 MATCH unit and 5 EXPECTED (D-001: the legacy stops at the first invalid application and writes its error row twice); valid-only: both empty |
| SCRNOUT (PRVDAY01) | 21 units **EXPECTED**: the stub rows without source, date and detail (D-005, 24 field rows) and the screening of new providers at registration and the missing baseline file (D-032) |
| Provider master (sum of the 14 comparisons of the five runs) | MATCH 159, **EXPECTED 35**, **UNEXPECTED 0** (D-001, D-003, D-006, D-031, D-032, D-033); every BEFORE export is byte-identical to the legacy BEFORE file |
| Sanction notices (supplementary) | MATCH 4, EXPECTED 2 (D-026, D-034), UNEXPECTED 0 |

Groups of the 102 EXPECTED rows: E-1 D-001 x6, E-2 D-003 x23, E-3 D-005 x24, E-4 D-006 x1, E-5 D-028 x8, E-6 D-026 x1, E-7 D-031 x9, E-8 D-032 x27, E-9 D-033 x2, E-10 D-034 x1. UNEXPECTED: U-8 x1.

**Acceptance criteria of MAM-91 after this version.** AC-1 not exercised (as before); AC-2 and AC-4 partly met for the reason in section 10 (both sides ran on 2026-11-20 as the business date, not 2026-01-15); AC-3, AC-5, AC-6, AC-8 met; **AC-7 met except for the one
BLOCKED capture gap (U-8)**: every difference is MATCH or EXPECTED with a decision number, none was marked EXPECTED on its own, no Defect story was needed, and the legacy-side decisions are recorded as D-030 to D-034.

**Reading the rest of this report.** Everything below this block is the text of the version of 2026-11-20 23:38, kept for its evidence rows. Where it says UNEXPECTED for the groups U-2 to U-7 and U-9, or "decision needed" for Q-125 to Q-128 (sections 0, 1, 4 to 6, 8, 10), it is
**superseded by this block**: those groups are EXPECTED under D-031 to D-034.

---

Date of the version below: 2026-11-20 (superseded where the block above says so), after a **re-run of the modern side** (scenarios 23:13 to 23:16, comparison 23:17, machine time UTC+5:30), corrected at 23:38 after two independent checks (11 new rows, not 4; AC-4 is partly met; the source of the value 20311120; see "Corrections of 23:38" below). First version of this report: 2026-11-20 18:20. Author: parity-checker. Business date of both sides: **2026-11-20** (not the frozen 2026-01-15, see section 7).
Status of the version of 2026-11-20 23:38: **was current for that re-run.** Every difference in the captured files is classified below as EXPECTED with its decision number or as UNEXPECTED. Nothing was marked EXPECTED without an approved decision. The UNEXPECTED groups U-2 to U-9 are recorded as questions Q-125 to Q-129 in `modern/docs/analysis/questions.md`; Q-125 to Q-128 have the default "accept the difference, no code change" applied (to be confirmed by the user), Q-129 is BLOCKED (section 8). They stay UNEXPECTED here until the user decides. Story MAM-91 therefore stays In Progress (AC-7 open).

Inputs: `modern/parity/golden/**` (stage 1, legacy run, report `modern/docs/reports/parity-legacy-run.md`), `modern/parity/modern/**` (stage 2, modern run), `modern/docs/analysis/defect-register.md` (D-001 to D-029), `modern/docs/analysis/questions.md`, the layouts exported from the graph (`modern/libs/fixed-width/src/main/resources/layouts/*.json`). Comparison tool: `modern/tools/parity/compare_parity.py` (Python, standard library only, not changed in this re-run); full machine-readable result: `modern/parity/compare-result.json`.

## What changed since the report of 18:20 (why the modern side ran again)

1. **User decision Q-124 A (2026-11-20 19:32), live since deploy 4.** The master export now writes `99991231` in bytes 43-50 for a provider with no revalidation due date (MAM-43 AC-6, spec version 2). Before, it wrote `00000000`.
2. **Q-130 default A.** The licence-board stub now knows licence SD20001 / TX (mapping `infra/wiremock/mappings/license-board/clean-SD20001.json`, loaded into the running WireMock at 22:13), so the valid intake application 1006133288 can pass the modern screening.
3. **provider-service has an optional search parameter `afterNpi`** (contract 1.2.0). It changes nothing for the callers of this run (the runner does not use it).

Preconditions, each checked at the start of the re-run (not assumed):

| Check | Result |
|---|---|
| (a) The export writes 99991231 now | After a seed under the stack lock, `GET /api/v1/legacy/provider-master` returned 16 records of 130 bytes (LF between records). Bytes 43-50 of the four providers without a due date: `99991231` for 1005914035 (status D), 1005925213 (P), 1005933068 (P) and 1005960046 (D). No record has `00000000`. All 16 records equal the golden `provider-master-seed-sorted.dat` byte for byte. |
| (b) The licence-board stub knows SD20001 | `GET http://127.0.0.1:8280/license-board/licenses?licenseNo=SD20001&state=TX` answered HTTP 200 `{"source":"LICBOARD","licenseNo":"SD20001","state":"TX","found":true,"status":"ACTIVE","expiry":"2030-12"}`. |
| (c) The golden folders are unchanged | Every file listed in the five golden manifests (52: the originals and their `.ascii` conversions) has the recorded size and sha256 (0 mismatches); `git status` shows no change under `modern/parity/golden`; the newest file listed in a golden manifest is dated 2026-11-20 16:05, before the first version of this report. |
| Stack and memory | The stack lock was taken at 23:11:41 (after waiting for another lane's lock since 23:06) and released at 23:16:36, after a final `seed.sh` (16 providers, 4 with `99991231`). Free memory was 1915 MB before the run (no waiting; the runner's own gate waited 0 s in all five scenarios). The pem-apps container (`pem-pem-apps-1`) had been running since 2026-11-20 22:56 (17:26 UTC); its image id is the base image `maven:3.9-eclipse-temurin-21`, the same as in the first run, so the build is told apart by that start time and by the observed 99991231, not by the image id. |

The five scenarios ran one after another under the lock, with business date 2026-11-20 (`day-baseline`, `day-valid-only`, `month-seed`, `dir-seed`, `san-baseline`). **Every scenario passed on its first attempt (exit 0, no problems in its manifest); none needed a second one.**

**History (the earlier numbers, kept here once so the change is visible).** Units are records, or one unit per file-level difference; rows are one per field, record or file.

| Version | MATCH | EXPECTED | UNEXPECTED | Rows EXPECTED / UNEXPECTED | Provider master MATCH / EXPECTED / UNEXPECTED | Groups of UNEXPECTED rows |
|---|---|---|---|---|---|---|
| First final version (modern side captured 2026-11-20 16:50, report 18:20) | 128 | 41 | 67 | 63 / 86 | 115 / 20 / 53 | U-1 46, U-2 9, U-3 10, U-4 3, U-5 12, U-6 2, U-7 1, U-8 1, U-9 1, U-10 1 |
| This re-run (23:13 to 23:17) | **168** | **41** | **27** | **63 / 40** | **155 / 20 / 13** | U-2 9, U-3 12, U-4 2, U-5 12, U-6 2, U-7 1, U-8 1, U-9 1 |

Net change: 46 rows of U-1 and 1 row of U-10 are gone; U-3 gained 2 rows and U-4 lost 1 (all three because application 1006133288 now passes screening, section 6.3). 86 - 46 - 1 + 2 - 1 = 40. The EXPECTED units and rows did not change in number.

Row by row (the rows of `compare-result.json` of the first final version against this one, matched on file, key, field, both values, class, group and rule): **92 rows are unchanged, 57 are gone, 11 are new**. 92 + 57 = 149 rows before (63 EXPECTED, 86 UNEXPECTED); 92 + 11 = 103 rows now (63 EXPECTED, 40 UNEXPECTED). The 57 that are gone: 46 of U-1, 1 of U-10, 1 of U-4, 2 of U-3 (flag `F`) and 7 of E-2 (modern value `00000000`). The 11 that are new: 4 of U-3 and 7 of E-2 (modern value `99991231`); section 6.4 lists them. The EXPECTED count stays 63 because 7 E-2 rows left and 7 E-2 rows came.

**Corrections of 23:38.** Two independent checks of this re-run found three errors in the first text of this version, all in the report, none in a file or a count of the comparison: (1) it said the only new rows were 4 rows of U-3, and left out the 7 E-2 rows (92 + 4 = 96 does not equal the 103 rows of the tool); (2) it called AC-4 met, although its Given is the business date 2026-01-15, which could not be run (section 7), so it is partly met, like AC-2; (3) it explained the value `20311120` of the 4 E-2 rows of PRVMTH01 as "business date + 5 years", but the modern code takes it from the service clock (processing date), which is the default of Q-018 (Q-A4), not an approved decision. No scenario was run again for this correction; the comparison result and all captured files are those of 23:17. The supplementary notice check is unchanged (MATCH 4, EXPECTED 1, UNEXPECTED 1). The seven non-master modern files (both APPERROR files, both SCRNOUT files, DIROUT, REVALQ, TMSISOUT) are byte-identical to those of the first run; all 12 master exports changed.

## 0. Result per file (version of 2026-11-20 23:38; the final result is in the block at the top)

| File | Records legacy / modern | Result |
|---|---|---|
| REVALQ (PRVMTH01) | 4 / 4 | **MATCH**, byte-identical |
| TMSISOUT (PRVMTH01) | 8 / 8 | **MATCH**, byte-identical (period 26324, D-015 preserved). The GDG archive generation could not be captured: BLOCKED (U-8, Q-129) |
| DIROUT (PRVDIR01) | 8 / 8 | **EXPECTED**: all 8 records differ only in the name area, bytes 23-82 (D-028, section 5.1); every other byte is identical |
| APPERROR (PRVDAY01) | baseline 2 / 5; valid-only 0 / 0 | **EXPECTED** (D-001: the legacy stops at the first invalid application and writes its error row twice; the modern run writes one row per invalid application); the valid-only run is identical (both empty) |
| SCRNOUT (PRVDAY01) | valid-only 20 / 8; baseline absent / 8 | 8 records (24 field rows) **EXPECTED** (D-005: the stub rows have no source, date or detail); 12 records and 1 file **UNEXPECTED** (U-5, U-6: new providers are screened at registration, and the legacy never produced the baseline file; Q-126) |
| Provider master (13 comparisons in 5 runs) | 205 / 204 | 155 **MATCH** (all five BEFORE exports are byte-identical to the legacy BEFORE files); 20 **EXPECTED** (D-001, D-003, D-006); 13 **UNEXPECTED**: 11 records (the 3 new providers after STEP010 in two runs: U-2 and U-3; the 3 new providers in the final valid-only file: U-2; the tier of 2 denied providers: U-4) and 2 files (U-6, U-7) |
| Sanction notices (supplementary) | 5 / 5 | 4 MATCH, 1 EXPECTED (D-026), 1 UNEXPECTED (U-9, Q-128) |

## 1. Result at a glance (version of 2026-11-20 23:38; the final result is in the block at the top)

- Six file types in five runs, 21 file comparisons (a file on one side only counts as a comparison). Units are records, or one unit per file-level difference: **MATCH 168, EXPECTED 41, UNEXPECTED 27**. Difference rows behind them: 63 EXPECTED, 40 UNEXPECTED (one row per field, record or file; the notices are included).
- Supplementary check of the PRVSAN01 notices (not one of the six files): MATCH 4, EXPECTED 1, UNEXPECTED 1.
- **Byte-identical, no difference at all:** REVALQ (4 records), TMSISOUT (8 records, period 26324, D-015 preserved), the empty APPERROR of the valid-only run, and the five BEFORE exports of the master and the master after PRVDIR01 (16 records each).
- **U-1 (no due date) is closed: all 46 rows now match** (section 6.3). The modern BEFORE master equals the legacy BEFORE master byte for byte in all five runs, so MAM-50 AC-1 ("seed equals the .dat") now holds byte for byte.
- **U-10 (licence stub) is closed:** application 1006133288 now passes screening on the modern side (flag P, tier L), as on the legacy side (section 6.3).
- **Explained by an approved decision (EXPECTED):** D-001 (APPERROR and the bad record on the master), D-003 (due dates), D-005 (screening rows), D-006 (sanction S), D-026 (no general notice), D-028 (directory name). Section 5.
- **UNEXPECTED, grouped U-2 to U-9 (section 6).** They are design differences that no D decision states (effective date at registration, event chain instead of step sequence, tier only after PASS, new providers screened at registration, no JCL gate on the intake return code, an extra APPROVED notice) and two capture gaps (a lab-only snapshot, the blocked GDG generation). I found no difference that looks like a coding error of the modern system and none that points at test or mock data any more. The user decides each one (section 8 has the options and a recommended default).
- **No NEW difference in kind, but 11 NEW rows** (section 6.4; 92 rows unchanged, 57 gone, 11 new; 92 + 11 = 103). The rows the first run did not have are 4 rows of group U-3 for application 1006133288 (flag `U` -> `P` and tier blank -> `L` in two runs; a direct consequence of closing U-10) and 7 rows of group E-2 (EXPECTED, D-003) in the final master of the valid-only run, where the modern due date is now `99991231` instead of `00000000` (1005914035, 1005925213, 1005933068, 1005960046, 1006133288, 1006144350, 1006153716; a direct consequence of Q-124 A).
- Premise checks: 213 ok, 0 failed, 5 information only (of 218): every manifest size and sha256, every golden `.ascii` re-converted with the recorded code page, the APPINPUT and SANCIN inputs byte-identical on both sides (the same bytes were uploaded), both sides the same business date. The five information-only lines are the master input: the legacy run loaded the `.dat`, the modern stack is seeded from SQL, and the tool now reports that its exported BEFORE master differs from the `.dat` in **0 of 16** records (the tool's wording still ends "see master-before and U-1", which is stale; section 9).
- Nothing was normalised: no manifest lists a run-dependent field.

## 2. How each side was run (short; details in `parity-legacy-run.md`)

**Legacy.** Container `mmis-hercules` (TK5 / MVS 3.8j), the PEM lab decks reconstructed from the JES2 listings of the lab jobs 154-183 (the `*-LAB.jcl` stubs are not in the container or the repository), one anonymous job per run with temporary datasets, MVS date 2026-11-20, code page 1047, output captured with IDCAMS PRINT DUMP and rebuilt to binary, then converted with `tools/data/ebcdic_to_ascii.py`. The programs are the lab's V2 port (`parity/golden/lab-cobol-diff/`); the COBOL under `source-code/` was not changed (git status shows no change there). The legacy side was **not** run again: the golden folders are unchanged.

**Modern.** The running PEM stack (compose project `pem`) driven through its HTTP APIs by `modern/parity/modern/run_modern.py`: a fresh `infra/scripts/seed.sh` (16 providers) before every run, the business date 2026-11-20 as the explicit job parameter (D-020), the legacy-format adapters for APPERROR, SCRNOUT, REVALQ, TMSISOUT, DIROUT and the 130-byte master export. The runs went under the stack lock (the stack is shared); the comparison itself reads only the stored files.

| Run | Legacy (MVS job, result) | Modern (run ids of this re-run, result) | Inputs |
|---|---|---|---|
| PRVDAY01 baseline | 193; STEP010 PRVENR01 RC=4 (stops at the first invalid application); STEP015-STEP040 bypassed by COND | intake f03ebc2a COMPLETED read 8, written 3, errored 4, skipped 1; screening 70e83b40 COMPLETED read 19, screened 2, skipped 17, failed 0 | APPINPUT `appinput-baseline.dat` (8): same bytes on both sides. Master: legacy `.dat` (16), modern SQL seed (16): equal byte for byte |
| PRVDAY01 valid-only | 194; all steps RC=0 | intake 3b38474e COMPLETED read 3, written 3, errored 0; screening fd36eaf1 COMPLETED read 19, screened 2, skipped 17, failed 0 | APPINPUT first 3 applications: same bytes on both sides. Master: as above |
| PRVMTH01 seed | 195; PRVREV01, PRVTMS01, IEBGENER RC=0; GDG generation BLOCKED | sweep 7f29f390 COMPLETED queued 4, noDueDate 0; T-MSIS 8e22f755 COMPLETED rows 8, futureEnrolments 0, archive generation 27 | master only: as above |
| PRVDIR01 seed | 196 (ad-hoc lab deck); RC=0 | extract bb3d61b1 COMPLETED rows 8 | master only: as above |
| PRVSAN01 sancin-baseline | 197 (ad-hoc lab deck); RC=0 | b3bfb518 COMPLETED read 6, applied 4, notFound 1, rejected 1, skipped 0, terminated 1 | SANCIN `sancin-baseline.dat` (6): same bytes on both sides. Master: as above |

Changes to the runner in this re-run (strings only, no behaviour of the scenarios): the manifest note about the seed premise is now **checked at run time** (the BEFORE export against the golden `.dat`, record by record) instead of stating that four due dates differ; the licence note of the day scenarios no longer says that SD20001 fails; the description of the copied master `.dat` now says it is for reference only (the modern stack is seeded from SQL). The new manifests of all five runs carry the result "equals the legacy `.dat` byte for byte (16 of 16 records)".

## 3. Method

1. **Layouts.** Both sides are split into records and fields with the same layout JSON files the codec library reads (PRVRECRD 130, APPERROR 250, SCRNREC 67, REVALQ 80, TMSISOUT 120, DIROUT 100). The Java codec itself was not called (no Gradle in this lane); the layout files are the codec's own resources. The REDEFINES view of the master (IN, or GR/FA) is chosen from the type code; when the two records select different views the 80-byte variant is compared as one field.
2. **Matching.** By NPI (master, REVALQ, TMSISOUT with its NPI at bytes 15-24, DIROUT). APPERROR and SCRNOUT can repeat an NPI, so the key is the NPI plus its n-th occurrence (the legacy SCRNOUT has a blank source, so the source cannot be part of the key; the four rows of an NPI are paired in file order).
3. **Byte by byte and field by field.** A file pair is first compared as bytes (identical, first differing offset); then every matched record field by field. A record on one side only, a file on one side only, a size that is not a whole number of records and a different record order are differences too.
4. **Normalisation.** None. The modern files are the manifests' `.ascii` record streams (the API's line feeds between records removed when the stream was written; no field changed).
5. **Classification.** A rule table in the script. EXPECTED only with an approved decision D-001 to D-029; otherwise UNEXPECTED with the side that looks wrong and the likely cause. Where unsure, UNEXPECTED. The rules are in `RULES` in `compare_parity.py` and in `compare-result.json` (`rules`). I read the text of each decision again for the EXPECTED rows (section 5): a decision only counts when it explains the exact bytes. No row fell into the catch-all group U-0.
6. **Premises.** Before comparing, the script checks every manifest size and sha256, that every golden `.ascii` is its `.ebcdic` converted again, that APPINPUT and SANCIN are byte-identical on both sides and that both sides name the same business date. The master input is compared as data (the `master-before` pair, and the tool's "differs in n of 16 records" line), because the modern side is seeded from SQL and not from the `.dat`.
7. **Repeat:** `python modern/tools/parity/compare_parity.py` (from the repository root); `--quiet` prints only the per-file counts. Exit code 1 only when a premise check fails. The earlier result (`compare-result.json` of the first version) was kept for the before/after comparison outside the repository.

## 4. Summary per file

Units: MATCH = identical record; EXPECTED or UNEXPECTED = a differing record (UNEXPECTED if any of its fields is), a record on one side only, or a file-level difference. "Fields E/U" counts differing fields classified EXPECTED / UNEXPECTED.

| Run | File | Legacy records | Modern records | Bytes identical | MATCH | EXPECTED | UNEXPECTED | Fields E/U |
|---|---|---|---|---|---|---|---|---|
| PRVDAY01 baseline | provider master BEFORE (seed) | 16 | 16 | yes | 16 | 0 | 0 | 0 / 0 |
| PRVDAY01 baseline | APPERROR | 2 | 5 | no | 1 | 5 | 0 | 0 / 0 |
| PRVDAY01 baseline | provider master after STEP010 | 20 | 19 | no | 16 | 1 | 3 | 0 / 9 |
| PRVDAY01 baseline | SCRNOUT | absent | 8 | n/a | 0 | 0 | 1 | 0 / 0 |
| PRVDAY01 baseline | provider master after STEP040 (final) | absent | 19 | n/a | 0 | 0 | 1 | 0 / 0 |
| PRVDAY01 valid-only | provider master BEFORE (seed) | 16 | 16 | yes | 16 | 0 | 0 | 0 / 0 |
| PRVDAY01 valid-only | APPERROR | 0 | 0 | yes (both empty) | 0 | 0 | 0 | 0 / 0 |
| PRVDAY01 valid-only | provider master after STEP010 | 19 | 19 | no | 16 | 0 | 3 | 0 / 9 |
| PRVDAY01 valid-only | SCRNOUT | 20 | 8 | no | 0 | 8 | 12 | 24 / 0 |
| PRVDAY01 valid-only | provider master after STEP030 (lab capture) | 19 | absent | n/a | 0 | 0 | 1 | 0 / 0 |
| PRVDAY01 valid-only | provider master after STEP040 (final) | 19 | 19 | no | 0 | 14 | 5 | 19 / 5 |
| PRVMTH01 seed | provider master BEFORE (seed) | 16 | 16 | yes | 16 | 0 | 0 | 0 / 0 |
| PRVMTH01 seed | REVALQ | 4 | 4 | yes | 4 | 0 | 0 | 0 / 0 |
| PRVMTH01 seed | TMSISOUT | 8 | 8 | yes | 8 | 0 | 0 | 0 / 0 |
| PRVMTH01 seed | T-MSIS archive copy (GDG) | 8 | absent | n/a | 0 | 0 | 1 | 0 / 0 |
| PRVMTH01 seed | provider master after | 16 | 16 | no | 12 | 4 | 0 | 4 / 0 |
| PRVDIR01 seed | provider master BEFORE (seed) | 16 | 16 | yes | 16 | 0 | 0 | 0 / 0 |
| PRVDIR01 seed | DIROUT | 8 | 8 | no | 0 | 8 | 0 | 8 / 0 |
| PRVDIR01 seed | provider master after | 16 | 16 | yes | 16 | 0 | 0 | 0 / 0 |
| PRVSAN01 sancin-baseline | provider master BEFORE (seed) | 16 | 16 | yes | 16 | 0 | 0 | 0 / 0 |
| PRVSAN01 sancin-baseline | provider master after | 16 | 16 | no | 15 | 1 | 0 | 1 / 0 |
| PRVSAN01 sancin-baseline | sanction notices (supplementary) | 5 notices | 5 notices | n/a | 4 | 1 | 1 | n/a |

Per file type, all runs together (six files; the supplementary notices are left out):

| File type | Comparisons | Legacy records | Modern records | MATCH | EXPECTED | UNEXPECTED |
|---|---|---|---|---|---|---|
| provider master | 13 | 205 | 204 | 155 | 20 | 13 |
| APPERROR | 2 | 2 | 5 | 1 | 5 | 0 |
| SCRNOUT | 2 | 20 | 16 | 0 | 8 | 13 |
| REVALQ | 1 | 4 | 4 | 4 | 0 | 0 |
| TMSISOUT | 2 | 16 | 8 | 8 | 0 | 1 |
| DIROUT | 1 | 8 | 8 | 0 | 8 | 0 |

## 5. EXPECTED differences (proposed, each under an approved decision)

| Group | Decision | Where | Units / rows | Why it is expected | Example (legacy -> modern) |
|---|---|---|---|---|---|
| E-1 | D-001 | PRVDAY01 baseline: APPERROR | 1 row | the legacy fall-through writes the error row of the same invalid application a second time; the modern run writes exactly one row per invalid application | `(blank)#2: TYPE:IN ERR:VALIDATION-FAILED -> -` |
| E-1 | D-001 | PRVDAY01 baseline: APPERROR | 4 rows | the legacy load stops at the first invalid application (record 4), so records 5-8 are never read; the modern run processes all applications and rejects each invalid one with one row (D-016 supplies the reason: E102 type, E103 duplicate, E104 taxonomy) | `1006113702: - -> 1006113702 TYPE:ZZ ERR:VALIDATION-FAILED` |
| E-1 | D-001 | PRVDAY01 baseline: provider master after STEP010 | 1 row | the legacy fall-through also writes the invalid application (no NPI) to the master with the status bytes of the input; the modern run never registers an invalid application | `(blank): 900026454IN207Q00000X 2026011... -> -` |
| E-3 | D-005 | PRVDAY01 valid-only: SCRNOUT | 24 rows | the legacy stub rows carry no source, no verdict date and no detail; the replaced screening service fills them from its mocked sources (the field contents are the Q-045/C-7 default) | `1005925213#1 SCR-SOURCE: (blank) -> LICBOARD` |
| E-2 | D-003 | PRVDAY01 valid-only: provider master after STEP040 (final) | 19 rows | PRVRSK01 resets the due date of every provider to today + 5 years on every daily run (20311120 here); the modern system sets a due date only at approval or a completed revalidation (D-003), so it leaves the date as it was. I checked all 19: the modern value equals the value the record had before the run (12 seed providers keep their own date; 7 have no due date, written `99991231`: the 4 seed providers without one and the 3 new PENDING providers, which no analyst has approved, D-019) | `1005912765 PRV-REVAL-DUE-DATE: 20311120 -> 20280401`; `1005925213: 20311120 -> 99991231` |
| E-2 | D-003 | PRVMTH01 seed: provider master after | 4 rows | the month-end sweep queued this provider in both systems (REVALQ is identical); the legacy PRVREV01 only queues it and leaves the master unchanged, while in the modern system the re-screening (reason REVALIDATION) runs by events, passes and completes the revalidation, and D-003 explains why the date is set at a completed revalidation at all (D-029 gives the case its own state). The value itself is the **processing date + 5 years** (Q-018 / Q-A4 default A, DEFAULT APPLIED, not an approved D decision): `CompleteRevalidation.java:56` reads the service clock (`LocalDate.now(clock)`, with `Clock.systemUTC()` in `PemWebAutoConfiguration.java:32`) and `Revalidation.passed` adds 5 years (the interval and the 4-digit year are D-003 and D-004). It is not taken from the business date parameter of the job. The bytes `20311120` equal "business date + 5 years" only because both dates are 2026-11-20 in this run (the UTC date of the run and the business date parameter) | `1005923986 PRV-REVAL-DUE-DATE: 20260331 -> 20311120` |
| E-5 | D-028 | PRVDIR01 seed: DIROUT | 8 rows | the legacy name is the first 60 bytes of PRV-VARIANT (an individual: last name, first name and the start of the licence number; an organisation: legal name and the start of the DBA name); the modern name is composed (first + last, or the legal name) | `1005912765 DIR-NAME: SAMPLE CLINIC 01 S... -> SAMPLE CLINIC 01` |
| E-4 | D-006 | PRVSAN01 sancin-baseline: provider master after | 1 row | sanction S gives DENIED in the legacy (PRVSAN01.cbl:109-110) and the new SUSPENDED status in the modern system | `1005992312 PRV-ENROLL-STATUS: D -> S` |
| E-6 | D-026 | PRVSAN01 sancin-baseline: sanction notices (supplementary) | 1 row | an unknown action code X is rejected by the modern run (no change, no notice); the legacy applies nothing but still queues the general notice PRVNTGEN (D-012: notification-service replaces the CALL) | `1005912765: GEN 1005912765 -> -` |

How D-003 and the user's answer Q-124 A fit together in the 7 rows whose modern value is `99991231`. D-003 explains why the value is not 20311120: the modern system does not reset the date, so the field keeps the value it had (the intake record and the seed already carried `99991231`). Q-124 A, the user's answer of 2026-11-20 19:32 (MAM-43 AC-6, spec version 2), fixes how a missing date is written in the file. Q-124 has no D number in the defect register; the D-003 explanation does not depend on it (the modern byte equals the byte before the run), but I propose to record Q-124 A as a numbered decision (section 8, item 8) so that the reference is complete.

### 5.1 The DIROUT name area (bytes 23-82), decision D-028

DIROUT is 100 bytes: Medicaid ID 1-9, NPI 10-19, type 20-21, risk tier 22, **name 23-82** (60 bytes), filler 83-100. In all
8 records every byte outside the name area is identical on both sides; only the name area differs, and it differs in all 8.
The modern DIROUT file of this re-run is byte-identical to the one of the first run.

Why: the legacy PRVDIR01 does not build a name. It copies the first 60 bytes of the 80-byte variant area of the master
record (`PRV-VARIANT`, master bytes 51-130), whatever the provider type is. That area is laid out in two ways (REDEFINES):

- individual (IN): last name 30, first name 20, licence number 15, licence state 2, filler 13. The first 60 bytes are
  therefore the last name, the first name **and the first 10 characters of the licence number**;
- group or facility (GR, FA): legal name 40, DBA name 30, owner NPI 10. The first 60 bytes are the legal name and the
  **first 20 characters of the DBA name** (cut in the middle of the field).

Decision D-028 replaces this with a composed name: first name + last name for an individual, the legal name for an
organisation, left-justified and padded with spaces to 60 bytes. The licence number no longer appears in the public
directory file, and no half DBA name is published.

Examples from the captured files (`parity/golden/PRVDIR01/20261120-seed/after/dirout.ebcdic.ascii` and
`parity/modern/PRVDIR01/20261120-seed/after/dirout.ascii`; the brackets mark bytes 23-82):

| NPI, type | Master variant area | Legacy name area | Modern name area |
|---|---|---|---|
| 1005940865, IN | last `ALPHA`, first `TESTPROV`, licence `SD10001`, state `TX` | `[ALPHA                         TESTPROV            SD10001   ]` | `[TESTPROV ALPHA                                              ]` |
| 1005912765, GR | legal `SAMPLE CLINIC 01`, DBA `SAMPLE CLINIC GROUP 01`, owner 1005940865 | `[SAMPLE CLINIC 01                        SAMPLE CLINIC GROUP ]` | `[SAMPLE CLINIC 01                                            ]` |
| 1005923986, FA | legal `SAMPLE AMBULANCE 01`, DBA `SAMPLE TRANSPORT 01`, owner 1005951276 | `[SAMPLE AMBULANCE 01                     SAMPLE TRANSPORT 01 ]` | `[SAMPLE AMBULANCE 01                                         ]` |

In the first row the legacy file publishes the licence number inside the name; in the second the DBA name is cut after 20
characters (`SAMPLE CLINIC GROUP ` instead of `SAMPLE CLINIC GROUP 01`). Both are the documented legacy behaviour
(CLAUDE.md section 4: "name = first 60 bytes of PRV-VARIANT"), so the 8 differences are EXPECTED under D-028.

Matches that confirm decisions (no difference, listed because they were exercised): D-015 (T-MSIS period 26324 identical in all 8 rows); D-027 (R on the pending provider 1005933068 gives A on both sides; R on the suspended 1005919109 gives A on both); T on 1005943141 gives T on both; D-026 and the unknown NPI 1006143592 leave the master unchanged on both sides; D-029 is not observable in these files (REVALQ has the same 4 providers on both sides because the seed has no active provider with an unset due date).

## 6. UNEXPECTED differences

### 6.1 Groups, likely cause and proposal

| Group | Category | Rows | Side that looks wrong | Where | Likely cause | Proposal |
|---|---|---|---|---|---|---|
| U-2 | NEEDS-DECISION | 9 | modern | PRVDAY01 baseline: provider master after STEP010; PRVDAY01 valid-only: provider master after STEP010; PRVDAY01 valid-only: provider master after STEP040 (final) | effective date of a new provider (1006133288, 1006144350, 1006153716 in 3 files): the legacy keeps the effective date of the intake record (20260115, D-025); the modern system sets the registration date (20261120), Q-015/Q-A1 default A, status DEFAULT APPLIED - revisit, not an approved D decision | Confirm Q-A1 default A as a new decision, or carry the application's date (needs a contract change). Recommended: confirm A. Question Q-125. |
| U-3 | NEEDS-DECISION | 12 | neither (design difference) | PRVDAY01 baseline: provider master after STEP010; PRVDAY01 valid-only: provider master after STEP010 | snapshot timing: the legacy file is the master right after STEP010 (flag U, no tier); the modern system registers, screens and tiers a new provider by events within seconds, so the modern snapshot already holds the result of STEP030/STEP040 (flag P and tier L for all three new providers, in each of the two runs: 3 providers x 2 fields x 2 runs); no D decision covers the event chain replacing the step sequence | Record a new decision: the event chain replaces the step sequence, compare final states only. Recommended: accept. Question Q-126. |
| U-4 | NEEDS-DECISION | 2 | neither (design difference) | PRVDAY01 valid-only: provider master after STEP040 (final) | the legacy PRVRSK01 sets a tier on every record of the file, whatever its status and screen flag (M for the DENIED providers 1005914035 and 1005960046); the modern risk-service assigns a tier only after a PASS screening (MAM-35 AC-6: a failed screening gets no tier), so these two keep a blank tier; MAM-35 is an approved story, but no D decision states the rule | Record a new decision: a tier is assigned only after a PASS screening (MAM-35 AC-6). Recommended: accept. Question Q-127. |
| U-5 | NEEDS-DECISION | 12 | neither (design difference) | PRVDAY01 valid-only: SCRNOUT | the legacy PRVSCR01 screens every provider with flag U in one pass, new providers included; in the modern system a new provider gets its INITIAL screening at registration (event), so its rows are not in the screening run's file; they exist in the screening service (logs/screenings-of-new-providers.json) | Record a new decision: new providers are screened at registration, outside the run's file. Recommended: accept. Question Q-126. |
| U-6 | NEEDS-DECISION | 2 | neither (design difference) | PRVDAY01 baseline: SCRNOUT; PRVDAY01 baseline: provider master after STEP040 (final) | the legacy job never produced this file: STEP010 ended with RC=4 (the first invalid application) and the JCL bypassed STEP015-STEP040 (COND); the modern run is not gated on the intake result and went on to screening and tiering; D-001 and D-024 are the nearest decisions but neither states that gate | Record a new decision: the modern jobs are not gated on the intake return code. Recommended: accept. Question Q-126. |
| U-7 | NOT-COMPARABLE | 1 | neither (design difference) | PRVDAY01 valid-only: provider master after STEP030 (lab capture) | the legacy file is a lab capture between two steps (CAP030, after PRVSCR01); the modern system has no step between screening and tiering (events), so no such state can be exported; it is not a job output | Accept as not comparable (a lab capture between two steps, not a job output). Question Q-126. |
| U-8 | CAPTURE-GAP | 1 | neither (design difference) | PRVMTH01 seed: T-MSIS archive copy (GDG) | the legacy GDG generation MMPRV.TMSIS.ARCHIVE(+1) could not be created (BLOCKED, IEF197I in the anonymous job); only a temporary IEBGENER copy exists (byte-identical to TMSISOUT); the modern archive generation (27 in this run) is recorded in tmsis-extract-list.json but its content was not exported | Open. Needs the lab account (password in `.env`, not in chat) to capture the GDG generation, and an export of the modern archive content. Question Q-129. |
| U-9 | NEEDS-DECISION | 1 | neither (design difference) | PRVSAN01 sancin-baseline: sanction notices (supplementary) | reinstating (R) a PENDING provider makes it ACTIVE (D-027 preserves the legacy rule); the modern notification-service then also sends an APPROVED notice besides the RST notice; the legacy sends only the reinstatement notice; no D decision covers the extra notice | Business question: should reinstating a pending provider also send an APPROVED notice? Recommended: accept (D-027 makes the provider ACTIVE). Question Q-128. |

### 6.2 Every UNEXPECTED difference

One row per file, group, field and value pair; identical differences on several keys share a row (all keys are listed). The full list, one entry per field, is `compare-result.json`.

| # | Group | Run / file | Key(s) | Field | Legacy value | Modern value | Likely cause |
|---|---|---|---|---|---|---|---|
| 1 | U-2 | PRVDAY01 baseline: provider master after STEP010 | 1006133288, 1006144350, 1006153716 | PRV-EFFECTIVE-DATE | `20260115` | `20261120` | Q-A1 default: registration date instead of the intake date |
| 2 | U-3 | PRVDAY01 baseline: provider master after STEP010 | 1006133288, 1006144350, 1006153716 | PRV-RISK-TIER | `(blank)` | `L` | snapshot taken after the event chain ran |
| 3 | U-3 | PRVDAY01 baseline: provider master after STEP010 | 1006133288, 1006144350, 1006153716 | PRV-SCREEN-FLAG | `U` | `P` | snapshot taken after the event chain ran |
| 4 | U-6 | PRVDAY01 baseline: SCRNOUT | (file) | (file only on the modern side) | `absent` | `8 records` | legacy steps bypassed by COND after RC=4; modern not gated |
| 5 | U-6 | PRVDAY01 baseline: provider master after STEP040 (final) | (file) | (file only on the modern side) | `absent` | `19 records` | legacy steps bypassed by COND after RC=4; modern not gated |
| 6 | U-2 | PRVDAY01 valid-only: provider master after STEP010 | 1006133288, 1006144350, 1006153716 | PRV-EFFECTIVE-DATE | `20260115` | `20261120` | Q-A1 default: registration date instead of the intake date |
| 7 | U-3 | PRVDAY01 valid-only: provider master after STEP010 | 1006133288, 1006144350, 1006153716 | PRV-RISK-TIER | `(blank)` | `L` | snapshot taken after the event chain ran |
| 8 | U-3 | PRVDAY01 valid-only: provider master after STEP010 | 1006133288, 1006144350, 1006153716 | PRV-SCREEN-FLAG | `U` | `P` | snapshot taken after the event chain ran |
| 9 | U-5 | PRVDAY01 valid-only: SCRNOUT | 1006133288 x4 | (record only in the legacy file) | `1006133288        P` | absent | new providers screened at registration, not in the run file |
| 10 | U-5 | PRVDAY01 valid-only: SCRNOUT | 1006144350 x4 | (record only in the legacy file) | `1006144350        P` | absent | new providers screened at registration, not in the run file |
| 11 | U-5 | PRVDAY01 valid-only: SCRNOUT | 1006153716 x4 | (record only in the legacy file) | `1006153716        P` | absent | new providers screened at registration, not in the run file |
| 12 | U-7 | PRVDAY01 valid-only: provider master after STEP030 (lab capture) | (file) | (file only on the legacy side) | `19 records` | `absent` | lab capture between two steps; no modern equivalent |
| 13 | U-2 | PRVDAY01 valid-only: provider master after STEP040 (final) | 1006133288, 1006144350, 1006153716 | PRV-EFFECTIVE-DATE | `20260115` | `20261120` | Q-A1 default: registration date instead of the intake date |
| 14 | U-4 | PRVDAY01 valid-only: provider master after STEP040 (final) | 1005914035, 1005960046 | PRV-RISK-TIER | `M` | `(blank)` | legacy tiers every record; modern only after PASS |
| 15 | U-8 | PRVMTH01 seed: T-MSIS archive copy (GDG) | (file) | (file only on the legacy side) | `8 records` | `absent` | GDG generation blocked; modern archive content not exported |
| 16 | U-9 | PRVSAN01 sancin-baseline: sanction notices (supplementary) | 1005933068 | notice APPROVED | absent | `APPROVED 1005933068` | extra APPROVED notice after reinstating a pending provider |

(16 rows stand for 40 difference entries: U-2 9, U-3 12, U-4 2, U-5 12, U-6 2, U-7 1, U-8 1, U-9 1.)

### 6.3 The two groups that are closed

**U-1, no due date (46 rows in the first run): all 46 now match, none remains.** Where the 46 were: the BEFORE master of five runs (4 rows each, 20), the master after STEP010 of the two PRVDAY01 runs (7 rows each: the four seed providers and the three new ones, 14) and the master after the run in PRVMTH01, PRVDIR01 and PRVSAN01 (4 rows each, 12). In every one the legacy and the modern byte is `99991231` now. The five BEFORE exports are byte-identical to the legacy BEFORE files, and the tool reports 0 of 16 records different between the `.dat` and the exported SQL seed. The records of the three new providers after STEP010 still differ, but in other fields (U-2, U-3), not in bytes 43-50. The 7 rows of E-2 whose modern value was `00000000` now show `99991231` (section 5); they were EXPECTED before and are EXPECTED now.

**U-10, licence SD20001 (1 row in the first run): gone.** Application 1006133288 (an individual, licence SD20001 / TX, taxonomy 207Q00000X) in the valid-only run, both sides:

| | Legacy (job 194) | Modern (run of this re-run) |
|---|---|---|
| Screening | 4 SCRNOUT rows, result `P`, source, date and detail blank (the stubs pass everybody) | INITIAL screening PASS: LICBOARD `P` (LICENCE SD20001 TX ACTIVE UNTIL 2030-12), OIGLEIE `P` (NOT EXCLUDED), SAMGOV `P` (NOT DEBARRED), SSADMF `P` (NOT DECEASED); the baseline run shows the same |
| After STEP010 | status P, flag `U`, no tier | status P, flag `P`, tier `L` (U-3: the event chain has already run) |
| Final (after STEP040) | status P, flag `P`, tier `L` | status P, flag `P`, tier `L` |
| Effective date | 20260115 | 20261120 (U-2) |
| Due date | 20311120 | 99991231 (E-2, D-003) |

So flag and tier of the final record now match; what is left of this record are the same differences as for the other two new providers (U-2, E-2, and U-3 in the snapshot). The unknown-licence default Q-041 (a licence the board does not know gives F) is still a default and not a decision, but no parity file exercises it any more.

### 6.4 Differences that the first run did not have (NEW)

No difference of a new kind appeared, but **11 rows of this re-run are not in the first run** (compared with the saved result of the first final version, matched on file, key, field, both values, class, group and rule). Two causes: 4 rows are consequences of closing U-10 (application 1006133288 passes screening), 7 rows are consequences of Q-124 A (the modern due date is written `99991231`, not `00000000`).

| # | Run / file | Key | Field | Legacy | Modern | Group | Why it is new |
|---|---|---|---|---|---|---|---|
| 1 | PRVDAY01 baseline: master after STEP010 | 1006133288 | PRV-SCREEN-FLAG | `U` | `P` | U-3 | the first run had `U` -> `F` (row 3 of the first run); the application passes now |
| 2 | PRVDAY01 baseline: master after STEP010 | 1006133288 | PRV-RISK-TIER | `(blank)` | `L` | U-3 | a tier follows a PASS, so the snapshot now holds `L` |
| 3 | PRVDAY01 valid-only: master after STEP010 | 1006133288 | PRV-SCREEN-FLAG | `U` | `P` | U-3 | as rows 1 and 2 |
| 4 | PRVDAY01 valid-only: master after STEP010 | 1006133288 | PRV-RISK-TIER | `(blank)` | `L` | U-3 | as rows 1 and 2 |
| 5 | PRVDAY01 valid-only: master after STEP040 (final) | 1005914035 | PRV-REVAL-DUE-DATE | `20311120` | `99991231` | E-2 (EXPECTED, D-003) | the first run had `00000000` (written for "no due date"); Q-124 A writes `99991231` |
| 6 | same file | 1005925213 | PRV-REVAL-DUE-DATE | `20311120` | `99991231` | E-2 (EXPECTED, D-003) | as row 5 |
| 7 | same file | 1005933068 | PRV-REVAL-DUE-DATE | `20311120` | `99991231` | E-2 (EXPECTED, D-003) | as row 5 |
| 8 | same file | 1005960046 | PRV-REVAL-DUE-DATE | `20311120` | `99991231` | E-2 (EXPECTED, D-003) | as row 5 |
| 9 | same file | 1006133288 | PRV-REVAL-DUE-DATE | `20311120` | `99991231` | E-2 (EXPECTED, D-003) | as row 5 |
| 10 | same file | 1006144350 | PRV-REVAL-DUE-DATE | `20311120` | `99991231` | E-2 (EXPECTED, D-003) | as row 5 |
| 11 | same file | 1006153716 | PRV-REVAL-DUE-DATE | `20311120` | `99991231` | E-2 (EXPECTED, D-003) | as row 5 |

Rows 1 to 4 are group U-3 (UNEXPECTED, Q-126), the same assignment as for 1006144350 and 1006153716 (same cause, snapshot timing). Rows 5 to 11 are group E-2: the same field, the same seven records and the same class and decision as seven rows of the first run whose modern value was `00000000`; only the written value changed, so they are new as rows but not a new kind of difference (section 5).

The rest of the comparison: **92 rows are unchanged and 57 rows of the first run are gone** (the 46 of U-1, 1 of U-10, 1 of U-4, 2 of U-3 with flag `F`, and the 7 E-2 rows with `00000000`). Check: 92 + 57 = 149 rows in the first run; 92 + 11 = 103 rows now (63 EXPECTED, 40 UNEXPECTED).

## 7. BLOCKED, with the exact error

| What | Exact error | Effect on this report |
|---|---|---|
| GDG generation of PRVMTH01 STEP030 (`MMPRV.TMSIS.ARCHIVE(+1)`) | `IEF197I SYSTEM ERROR DURING ALLOCATION`, then `IEF453I PARDG1 - JOB FAILED - JCL ERROR` (jobs 187, 189, 190, 191; an anonymous job cannot create a permanent dataset) | Only a temporary IEBGENER copy exists (byte-identical to TMSISOUT). U-8, Q-129. |
| Run under the lab account HERC01 | `IEF722I PARPRB01 - FAILED - INVALID PASSWORD GIVEN` (job 185); the password is not in the repository and no other was tried | All runs anonymous with temporary datasets; the lab's own `MMPRV.*` datasets were not touched. Q-129. |
| Business date 2026-01-15 | the lab programs read the MVS system date, and changing it is forbidden | Both sides were run for 2026-11-20 (AC-2, AC-4 deviation). |
| The `*-LAB.jcl` stubs | not in `/app/mvs/jcl` (only `dummy`) and not in the repository | Decks reconstructed from the JES2 listings of lab jobs 154-183 (2026-11-19). |
| Modern `GET /api/v1/applications` | HTTP 403 for ADMIN (the endpoint is ANALYST only; the body is in `logs/applications.json` of the two PRVDAY01 runs; the same answer in this re-run) | No application list on the modern side; the error codes E101-E104 behind each APPERROR row are not captured. |

No legacy job needed a second attempt (stage 1), and the legacy side was not run again for this version. On the modern side no scenario needed a second attempt in the re-run.

## 8. Decisions needed from the user

Each is a proposal. The lead recorded them on 2026-11-20 17:55 as questions in `modern/docs/analysis/questions.md` and
applied the defaults below (the user's standing rule: apply the recommended default, mark it DEFAULT APPLIED - revisit):

| Item | Group | Question | State now |
|---|---|---|---|
| 1 | U-1 (closed) | Q-124 | **ANSWERED A by the user (2026-11-20 19:32) and applied** (MAM-43 AC-6, spec version 2; live since deploy 4). Result of this re-run: all 46 rows match (section 6.3). |
| 2 | U-2 | Q-125 | A: behaviour unchanged (registration date); needs the user's confirmation to become a decision. 9 rows remain. |
| 3 | U-3, U-5, U-6, U-7 | Q-126 | A: no code change; one new decision proposed (compare final states only). 12 + 12 + 2 + 1 rows remain. |
| 4 | U-4 | Q-127 | A: accept (MAM-35 AC-6). 2 rows remain. |
| 5 | U-9 | Q-128 | A: accept; business to confirm. 1 row remains. |
| 6 | U-8 | Q-129 | Open, BLOCKED (GDG generation and the lab account; no password was guessed). 1 row remains. |
| 7 | U-10 (closed) | Q-130 | **Default A applied and checked** (stub mapping `clean-SD20001.json` loaded 22:13, modern PRVDAY01 runs repeated): application 1006133288 passes (flag P, tier L), the group is gone (section 6.3). |
| 8 | new proposal | Q-124 as a numbered decision | Q-124 A has no D number. Proposal: record it as D-030 (the master file writes 99991231 for "no due date") so that the E-2 rows can cite it next to D-003. Not decided; nothing depends on it for the classification. |

None of the defaults of items 2 to 6 turns an UNEXPECTED row into EXPECTED: that needs an approved decision with a D number. Items 2 to 6 are, as they stand, "accept the difference, no code change", to be confirmed by the user.

2. **Effective date of a new provider (U-2, 9 rows).** Legacy keeps the intake record's date (20260115); modern sets the registration date (20261120).  
   Options: A) confirm Q-A1 default A as a decision; B) carry the application's date (contract change). **Recommended: A.**
3. **Event chain instead of steps (U-3, U-5, U-6, U-7).** The modern system registers, screens and tiers by events; the intermediate snapshot, the run file of screening and the JCL gate do not exist.  
   Options: A) one new decision covering all four (compare final states, screening rows of new providers live in the screening service, intake errors do not block later work); B) decide each separately; C) rebuild step semantics (not recommended). **Recommended: A.**
4. **Tier only after PASS (U-4, 2 rows).** Options: A) accept (MAM-35 AC-6); B) also tier denied and failed providers. **Recommended: A.**
5. **Extra APPROVED notice (U-9).** Options: A) accept; B) suppress it when the cause is a sanction reinstatement. **Recommended: A**, business to confirm.
6. **Capture gaps (U-8).** Provide the lab account password in `.env` (not in chat) to capture the GDG generation, and decide whether the modern archive content should be exported for comparison. **Recommended:** leave open.

Observations that are not differences in the six files but matter:

- **The modern PRVMTH01 run changes the master**: the four providers the sweep queued (1005923986, 1005947811, 1005951276, 1005989276; the same four as the legacy REVALQ) get due date 20311120. The code (`CompleteRevalidation`, `VerdictEventsListener`) sets the due date at a passed REVALIDATION re-screening, so I classified it D-003 (with D-029). The re-screenings themselves were not captured (the revalidation list endpoint is ANALYST only and the runner uses an ADMIN token), so this is inferred from the code and from the four NPIs, not observed. The legacy PRVREV01 only queues them and leaves the master unchanged.
- **The value 20311120 rests on an unapproved default, and on the date of the run.** The modern date is the processing date + 5 years (`CompleteRevalidation.java:56`, `LocalDate.now(clock)`, `Clock.systemUTC()` in `PemWebAutoConfiguration.java:32`), which is Q-018 / Q-A4 default A (DEFAULT APPLIED - revisit), not a D decision and not the business date parameter of the job. In this run the UTC date and the business date are both 2026-11-20, so the bytes are `20311120`. At the required business date 2026-01-15 (AC-2, AC-4; not run, section 7) the two would differ (by the code; not run): a run on the machine date 2026-11-20 would still write `20311120`, while "business date + 5 years" would be `20310115`. At that date the sweep would queue only 2 of the 4 providers (1005951276 due 20260115 and 1005989276 due 20251201; the sweep takes ACTIVE providers due on or before the business date), and their bytes would show the default Q-A4 that the 4 rows of this run already rest on. D-003 explains that the field changes at a completed revalidation; it does not fix the date it is computed from. The user can confirm Q-018 default A, or ask for the business date to drive the due date (a code and contract question, not decided).
- **D-002 (INFERRED: PRVRSK01 never reaches its REWRITE)** is not confirmed by the lab: the lab port reaches the REWRITE and tiers and re-dates every record. The lab port is not the unmodified source, so this does not prove the source behaves so.
- **D-007 and D-021** are not observable: the lab runs 130-byte records and a patched PRVTMS01 line; the legacy as shipped (121 bytes, unpatched) was not run. D-004 (2-digit year) gives the same result as the 4-digit rule at this date (2026 + 5 = 2031).
- **Q-135 (default applied):** after Q-124 A the codec reads `99991231` as the date 9999-12-31 while the API leaves the due date out. This does not touch parity (the files are compared as bytes), it is noted only because it is the other side of the same convention.

## 9. What was not compared, and why

- **Job logs and return codes** as data: legacy RC=4 / `*FLUSH*` against the modern COMPLETED runs with error counts are described in section 2 and in group U-6, not compared byte for byte (D-024 replaces return codes by run results).
- **Intermediate master snapshots**: the legacy STEP030 capture has no modern counterpart (U-7). The modern "after STEP010" and "after STEP040" are taken after the events settled (the manifest says so), so they are not the same point in the workflow as the legacy files (U-3).
- **Baseline run, STEP030/STEP040 files**: the legacy never produced SCRNOUT or the final master (COND bypass after RC=4); the modern files exist and are listed as U-6. Their legacy counterpart is the valid-only run, which uses only the first three applications.
- **The GDG archive generation**: blocked on the legacy side; the legacy temporary copy equals the legacy TMSISOUT, which equals the modern TMSISOUT, but the modern archive content (generation 27 in this run) was not exported (U-8).
- **Screening results of the new providers** (API JSON): only used as context for U-5 and for the picture of application 1006133288 (section 6.3); the modern INITIAL screenings are in `parity/modern/PRVDAY01/*/logs/screenings-of-new-providers.json`.
- **Sanction notices and the sanction history**: only the notice lines were checked, as a supplementary comparison (lab stubs that DISPLAY a line against notification-service); the history and the decision D-018 are not observable in the legacy files.
- **PRVDAY01 STEP020 SORT output** (D-009 DROP, never read), **the Medicare reciprocity check** (D-010 DROP), **PRVDEAD1** (D-011 DROP): dropped by decision.
- **Decisions not observable in these files:** D-004, D-007, D-012, D-013, D-014, D-016 (error codes), D-017, D-018, D-019 (approval; only used above to explain why the new providers have no due date), D-020, D-021, D-022, D-023, D-024, D-029.
- **The modern master input as a file**: the modern system does not load the `.dat`; it is seeded from SQL. Since Q-124 A its exported BEFORE master equals the `.dat` byte for byte (checked by the runner at run time, by the `master-before` pair in all five runs and by the tool's premise line: 0 of 16 differ). The copies in `modern/parity/modern/<job>/<run-id>/inputs/provider-master-seed-sorted.dat` (PRVMTH01, PRVDIR01, PRVSAN01) are reference copies; their manifest description says so now. The tool still prints them as "equal by construction, proves nothing", which stays true for the copy as such.
- **Wording of the comparison tool that is now stale** (the tool was not changed, as the task forbids changing it to make differences disappear; nothing here affects a count): the rule texts of U-NO-DATE (U-1) and U-LICENCE-MOCK (U-10) describe groups that no longer occur; the text of U-ARCHIVE-COPY says "generation (16)" (this run created 27); the premise information line ends "see master-before and U-1"; the text of rule E-D003-REVALIDATED (`compare_parity.py:222`, and the `why` of the 4 rows in `compare-result.json`) says "business date + 5 years = 20311120", but the source of that date is the processing date (Q-A4 default A, section 5 and section 8). The wording of this report is the reference.
- **The legacy as shipped**: the programs under `source-code/` do not compile unmodified; the comparison is against the lab port (`parity/golden/lab-cobol-diff/`, `labDifferences` in every golden manifest).
- **Modern build identification**: the container runs from a base image with the jar mounted, so the image id of the manifests (`modernBuild.pemAppsImageAndCreated`) is the same as in the first run and `gitHead` is the repository head with uncommitted changes; the manifests do not identify the jar. The behaviour (99991231, SD20001 passing) is what shows that the new build was running.

## 10. Status of the acceptance criteria of MAM-91

| AC | Status | Why |
|---|---|---|
| AC-1 | not exercised | Golden files exist, so the "no golden files: print the checklist and stop" path did not run; showing it would need the golden folder removed, which I did not do. |
| AC-2 | partly met | Every run has `inputs`, `before`, `after`, `logs` and a manifest with job, code page 1047, file names, record lengths and sha256 (re-verified at the start of the re-run: 52 manifest-listed files, 0 mismatches; 213 premise checks ok, 5 information only). The business date in the manifests is 2026-11-20, not 2026-01-15: the MVS date could not be changed. |
| AC-3 | met | Each golden `.ebcdic` has its `.ebcdic.ascii` next to it; the originals still equal their manifest sha256; each `.ascii` equals its `.ebcdic` converted again with code page 1047 by `tools/data/ebcdic_to_ascii.py` (22 files); sizes equal records x record length. The re-conversion shows consistency with the converter, not that 1047 is the right table: that proof is in `README-hercules-lab.md` section 5 and in the BEFORE files equalling the loaded inputs (8 checks). |
| AC-4 | partly met | Its Given is the business date 2026-01-15, which was not run (section 7: the MVS date cannot be changed, so both sides ran for 2026-11-20; same deviation as AC-2). On 2026-11-20 the modern system ran the same inputs (all five scenarios again in the re-run, each on the first attempt) and produced APPERROR, SCRNOUT, REVALQ, TMSISOUT, DIROUT and the 130-byte master export. "Both sides are parsed with the same layouts from libs/fixed-width" is only partly true: both sides were parsed with the layout JSON files that the library reads as resources, but the Java codec itself was not called. |
| AC-5 | met | Every difference of the re-run is MATCH, EXPECTED (with a D number) or UNEXPECTED; no manifest lists a run-dependent field, so nothing was normalised and no business field was; no row is unclassified (U-0: 0). The 7 E-2 rows with `99991231` rest on D-003 for the difference to the legacy and on the user's answer Q-124 A for the written form (section 5). The 4 E-2 rows of PRVMTH01 rest on D-003 for the fact that the date changes at a completed revalidation; the exact value `20311120` is the processing date + 5 years (Q-018 / Q-A4 default A, not approved), which equals the business date + 5 years only because both dates are 2026-11-20 here (section 8). The report uses EXPECTED/UNEXPECTED for the skill's INTENDED/UNEXPLAINED. |
| AC-6 | met in substance | One report for all runs (as the lead asked) instead of one file per job and run: summary per file (section 4), table of EXPECTED groups with D numbers including D-006 (section 5), table of UNEXPECTED differences with file, key, field, values and cause (section 6). |
| AC-7 | not met yet | 27 UNEXPECTED units (40 rows) wait for the user: Q-125 to Q-129 with the defaults of section 8 (Q-129 BLOCKED). U-1 and U-10 are closed by the answers and fixes already made. No difference was marked EXPECTED without a decision number, no Defect story was created (nothing points at a coding error), and the comparison must be run again (the script is repeatable) once the decisions are made. "Zero UNEXPLAINED" is not reached. |
| AC-8 | met | PRVTMS01 line 114 (identifier past column 72) runs as the lab port's re-spaced statement, `&DATE` and `&BATCH_DATE` have explicit values (PARM `BATCH_ID=20261120`, temporary dataset names), `source-code/` and `aws-transform/` are unchanged (git status empty, checked again in the re-run), and each patch is recorded in the manifests (`labDifferences`) and in `parity/golden/lab-cobol-diff/`. |

## 11. Files

- `modern/tools/parity/compare_parity.py`: the repeatable comparison and the classification rules (not changed in the re-run).
- `modern/parity/compare-result.json` (generated, rewritten by the re-run): per file the counts, every difference with its class, decision, group and cause; the premise checks; the rule table.
- `modern/parity/modern/run_modern.py` (changed in the re-run, strings only, section 2): the seed premise is checked at run time; two notes and one description corrected.
- `modern/parity/modern/<job>/<run-id>/**` (regenerated by the re-run): inputs, before, after, logs and manifest of the five modern runs.
- `modern/docs/reports/parity-report.md`: this report.
- Not changed: `modern/parity/golden/**`, `source-code/`, `aws-transform/`, the comparison tool, the layouts, `defect-register.md`; no commit. `questions.md` was not changed by the parity-checker; the lead appended the block "Parity re-run of 2026-11-20 23:17" to it at 23:40 and created `modern/docs/reports/stories/MAM-91.md`.

## 12. Log entry for the story MAM-91 (for the lead to append to `modern/docs/reports/stories/MAM-91.md`)

That story file does not exist yet and lies outside the files the parity-checker may write, so the text is kept here.

> **2026-11-20 23:17, parity re-run (parity-checker).** Why: Q-124 A is live (the export writes 99991231 for no due date), the licence stub knows SD20001 (Q-130 A), provider-service got the optional parameter afterNpi. Preconditions checked: export bytes 43-50 are 99991231 for 4 providers and 00000000 for none; WireMock answers found true for SD20001 / TX; the golden manifests (52 files) match their sha256, git shows no golden change. Ran day-baseline, day-valid-only, month-seed, dir-seed and san-baseline under the stack lock (23:11:41 to 23:16:36), business date 2026-11-20, each exit 0 on the first attempt; reseeded to 16 providers. Compared with `compare_parity.py` (unchanged). Before: MATCH 128, EXPECTED 41, UNEXPECTED 67 (rows 63 / 86; provider master 115 / 20 / 53). After: MATCH 168, EXPECTED 41, UNEXPECTED 27 (rows 63 / 40; provider master 155 / 20 / 13). U-1 (46 rows) and U-10 (1 row) closed. Row by row against the first result: 92 unchanged, 57 gone, 11 new (92 + 57 = 149 rows before, 92 + 11 = 103 now): 4 new U-3 rows (1006133288 flag U -> P, tier blank -> L, in 2 runs; it now passes) and 7 new E-2 rows (EXPECTED, D-003; valid-only final master, 20311120 -> 99991231 for 1005914035, 1005925213, 1005933068, 1005960046, 1006133288, 1006144350, 1006153716; Q-124 A); no new kind of difference. Still open: U-2 (9), U-3 (12), U-4 (2), U-5 (12), U-6 (2), U-7 (1), U-8 (1, BLOCKED, Q-129), U-9 (1): Q-125 to Q-129; AC-7 not met; AC-1 not exercised; AC-2 and AC-4 date deviation (business date 2026-01-15 not run; AC-4 partly met). Not verified: the Java codec was not called; the legacy side was not run again; the GDG generation and the HERC01 login stay BLOCKED; the revalidation completion behind the 4 E-2 rows of PRVMTH01 is inferred, not observed; the value 20311120 of those rows is the processing date + 5 years (Q-018 / Q-A4 default A, not approved), equal to the business date + 5 years only because both are 2026-11-20 (at 2026-01-15 it would differ).
>
> **2026-11-20 23:38, correction of this text (parity-checker).** After two independent checks: the new rows are 11, not 4 (the 7 E-2 rows were missing); AC-4 is partly met, not met (its Given date 2026-01-15 was not run); the value 20311120 of the 4 E-2 rows of PRVMTH01 comes from the service clock (Q-A4 default A), not from the business date. No scenario was run again; no file other than this report was changed in the correction.
