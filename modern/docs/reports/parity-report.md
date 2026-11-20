# Parity report: legacy PEM jobs on Hercules against the modern system (MAM-91)

Date: 2026-11-20. Author: parity-checker. Business date of both sides: **2026-11-20** (not the frozen 2026-01-15, see section 7).
Status: **FINAL for this run (lead, 2026-11-20 18:20; user deadline 20:00: no further re-run).** Every difference in the captured files is classified below as EXPECTED with its decision number (D-001 to D-029) or as UNEXPECTED. Nothing was marked EXPECTED without an approved decision. The UNEXPECTED groups U-1 to U-10 are recorded as questions Q-124 to Q-130 in `modern/docs/analysis/questions.md` with the default applied for each (section 8); they stay UNEXPECTED here until the user decides. Story MAM-91 therefore stays In Progress (AC-7 open).

**Revision after the independent review (2026-11-20), two corrections.** (1) The screen flag F of application 1006133288 was proposed as EXPECTED under D-005. D-005 only replaces the four stubs and does not say what an unknown licence gives, so it is now UNEXPECTED, group U-10 (test data / mock: SD20001 is not in the licence-board stub; the Q-041 default is not an approved decision). (2) The master input is not shared by the two sides: the legacy run loaded the `.dat`, the modern stack was seeded from SQL and differs in 4 due dates (U-1). The three 'input byte-identical' master checks passed only because `run_modern.py` copies the golden file; they are now information only (213 ok, 5 information only). Units are unchanged (MATCH 128, EXPECTED 41, UNEXPECTED 67); rows are now 63 EXPECTED and 86 UNEXPECTED.

Inputs: `modern/parity/golden/**` (stage 1, legacy run, report `modern/docs/reports/parity-legacy-run.md`), `modern/parity/modern/**` (stage 2, modern run), `modern/docs/analysis/defect-register.md` (D-001 to D-029), `modern/docs/analysis/questions.md`, the layouts exported from the graph (`modern/libs/fixed-width/src/main/resources/layouts/*.json`). Comparison tool: `modern/tools/parity/compare_parity.py` (Python, standard library only); full machine-readable result: `modern/parity/compare-result.json`.

## 0. Result per file (final)

| File | Records legacy / modern | Result |
|---|---|---|
| REVALQ (PRVMTH01) | 4 / 4 | **MATCH**, byte-identical |
| TMSISOUT (PRVMTH01) | 8 / 8 | **MATCH**, byte-identical (period 26324, D-015 preserved). The GDG archive generation could not be captured: BLOCKED (U-8, Q-129) |
| DIROUT (PRVDIR01) | 8 / 8 | **EXPECTED**: all 8 records differ only in the name area, bytes 23-82 (D-028, section 5.1); every other byte is identical |
| APPERROR (PRVDAY01) | baseline 2 / 5; valid-only 0 / 0 | **EXPECTED** (D-001: the legacy stops at the first invalid application and writes its error row twice; the modern run writes one row per invalid application); the valid-only run is identical (both empty) |
| SCRNOUT (PRVDAY01) | valid-only 20 / 8; baseline absent / 8 | 8 rows **EXPECTED** (D-005: the stub rows have no source, date or detail); 12 rows and 1 file **UNEXPECTED** (U-5, U-6: new providers are screened at registration, and the legacy never produced the baseline file; Q-126) |
| Provider master (13 comparisons in 5 runs) | 205 / 204 | 115 **MATCH**; 20 **EXPECTED** (D-001, D-003, D-006); 53 **UNEXPECTED**: 46 rows are the "no due date" convention (U-1, Q-124), the rest are the effective date (U-2, Q-125), the event chain (U-3, U-6, U-7, Q-126), the tier rule (U-4, Q-127) and the licence stub gap (U-10, Q-130) |
| Sanction notices (supplementary) | 5 / 5 | 4 MATCH, 1 EXPECTED (D-026), 1 UNEXPECTED (U-9, Q-128) |

## 1. Result at a glance

- Six file types in five runs, 21 file comparisons (a file on one side only counts as a comparison). Units are records, or one unit per file-level difference: **MATCH 128, EXPECTED 41, UNEXPECTED 67**. Difference rows behind them: 63 EXPECTED, 86 UNEXPECTED (one row per field, record or file; the notices are included).
- Supplementary check of the PRVSAN01 notices (not one of the six files): MATCH 4, EXPECTED 1, UNEXPECTED 1.
- **Byte-identical, no difference at all:** REVALQ (4 records) and TMSISOUT (8 records, period 26324, D-015 preserved); the empty APPERROR of the valid-only run.
- **Explained by an approved decision (EXPECTED):** D-001 (APPERROR and the bad record on the master), D-003 (due dates), D-005 (screening rows), D-006 (sanction S), D-026 (no general notice), D-028 (directory name). Section 5.
- **UNEXPECTED, grouped U-1 to U-10 (section 6).** The largest by far is U-1: two conventions for "no due date" (99991231 in the test data and the legacy run, 00000000 in the modern export, from an SQL seed that holds NULL). It is a data-convention conflict between Q-106 and MAM-43 AC-6, and it also means the modern BEFORE master is not byte-equal to the legacy BEFORE master (4 of 16 records, bytes 43-50), so MAM-50 AC-1 ("seed equals the .dat") does not hold byte for byte.
- The other UNEXPECTED groups are design differences that no D decision states (effective date at registration, event chain instead of step sequence, tier only after PASS, new providers screened at registration, no JCL gate on the intake return code, an extra APPROVED notice), two capture gaps (a lab-only snapshot, the blocked GDG generation) and one test-data / mock gap (U-10: the licence-board stub does not know the licence SD20001 of an intake application, so the modern screening fails it while the legacy stubs pass it). I found no difference that looks like a coding error of the modern system; two items point at test or mock data (U-1 and U-10, section 8). The user decides each one (section 8 has the options and a recommended default).
- Premise checks: 213 ok, 0 failed, 5 information only (of 218): every manifest size and sha256, every golden `.ascii` re-converted with the recorded code page, the APPINPUT and SANCIN inputs byte-identical on both sides (the same bytes were uploaded), both sides the same business date.
- **The master input is not shared by the two sides.** The legacy run loaded the 16-record `.dat`; the modern stack is seeded from SQL (`infra/scripts/seed.sh`), and its exported BEFORE master differs from the `.dat` in 4 of 16 records, in the due date (bytes 43-50, U-1). `run_modern.py` (`copy_input`) only copies the golden `.dat` into the modern run folder, so a check that the two copies are equal passes by construction. Those checks (PRVMTH01, PRVDIR01 and PRVSAN01; in the two PRVDAY01 runs nothing is copied) count as information only, and the real comparison of the two master inputs is the `master-before` row of every run (section 4).
- Nothing was normalised: no manifest lists a run-dependent field.

## 2. How each side was run (short; details in `parity-legacy-run.md`)

**Legacy.** Container `mmis-hercules` (TK5 / MVS 3.8j), the PEM lab decks reconstructed from the JES2 listings of the lab jobs 154-183 (the `*-LAB.jcl` stubs are not in the container or the repository), one anonymous job per run with temporary datasets, MVS date 2026-11-20, code page 1047, output captured with IDCAMS PRINT DUMP and rebuilt to binary, then converted with `tools/data/ebcdic_to_ascii.py`. The programs are the lab's V2 port (`parity/golden/lab-cobol-diff/`); the COBOL under `source-code/` was not changed (git status shows no change there).

**Modern.** The running PEM stack (compose project `pem`) driven through its HTTP APIs by `modern/parity/modern/run_modern.py`: a fresh `infra/scripts/seed.sh` (16 providers) before every run, the business date 2026-11-20 as the explicit job parameter (D-020), the legacy-format adapters for APPERROR, SCRNOUT, REVALQ, TMSISOUT, DIROUT and the 130-byte master export. This stage only read the stored files: it did not start a Hercules job, did not touch the stack, and so took no stack lock and waited 0 s for memory.

| Run | Legacy (MVS job, result) | Modern (run ids, result) | Inputs |
|---|---|---|---|
| PRVDAY01 baseline | 193; STEP010 PRVENR01 RC=4 (stops at the first invalid application); STEP015-STEP040 bypassed by COND | intake 5cc34e6e COMPLETED read 8, written 3, errored 4, skipped 1; screening d2a18fea COMPLETED screened 2, skipped 17 | APPINPUT `appinput-baseline.dat` (8): same bytes on both sides. Master: legacy `.dat` (16), modern SQL seed (16; 4 due dates differ, U-1) |
| PRVDAY01 valid-only | 194; all steps RC=0 | intake f5f6d755 COMPLETED read 3, written 3, errored 0; screening b274024e COMPLETED screened 2, skipped 17 | APPINPUT first 3 applications: same bytes on both sides. Master: as above |
| PRVMTH01 seed | 195; PRVREV01, PRVTMS01, IEBGENER RC=0; GDG generation BLOCKED | sweep b95a2698 COMPLETED queued 4; T-MSIS c39fa96e COMPLETED rows 8, archive generation 16 | master only: legacy `.dat`, modern SQL seed (4 due dates differ, U-1) |
| PRVDIR01 seed | 196 (ad-hoc lab deck); RC=0 | extract d119ac41 COMPLETED rows 8 | master only: as above |
| PRVSAN01 sancin-baseline | 197 (ad-hoc lab deck); RC=0 | ad8fc151 COMPLETED read 6, applied 4, notFound 1, rejected 1, terminated 1 | SANCIN `sancin-baseline.dat` (6): same bytes on both sides. Master: as above |

## 3. Method

1. **Layouts.** Both sides are split into records and fields with the same layout JSON files the codec library reads (PRVRECRD 130, APPERROR 250, SCRNREC 67, REVALQ 80, TMSISOUT 120, DIROUT 100). The Java codec itself was not called (no Gradle in this lane); the layout files are the codec's own resources. The REDEFINES view of the master (IN, or GR/FA) is chosen from the type code; when the two records select different views the 80-byte variant is compared as one field.
2. **Matching.** By NPI (master, REVALQ, TMSISOUT with its NPI at bytes 15-24, DIROUT). APPERROR and SCRNOUT can repeat an NPI, so the key is the NPI plus its n-th occurrence (the legacy SCRNOUT has a blank source, so the source cannot be part of the key; the four rows of an NPI are paired in file order).
3. **Byte by byte and field by field.** A file pair is first compared as bytes (identical, first differing offset); then every matched record field by field. A record on one side only, a file on one side only, a size that is not a whole number of records and a different record order are differences too.
4. **Normalisation.** None. The modern files are the manifests' `.ascii` record streams (the API's line feeds between records removed when the stream was written; no field changed).
5. **Classification.** A rule table in the script. EXPECTED only with an approved decision D-001 to D-029; otherwise UNEXPECTED with the side that looks wrong and the likely cause. Where unsure, UNEXPECTED. The rules are in `RULES` in `compare_parity.py` and in `compare-result.json` (`rules`).
6. **Premises.** Before comparing, the script checks every manifest size and sha256, that every golden `.ascii` is its `.ebcdic` converted again, that APPINPUT and SANCIN are byte-identical on both sides and that both sides name the same business date. It does not claim that the master input is shared: the modern side is seeded from SQL, so the master input is compared as data (the `master-before` pair) and the copy-equality checks are information only.
7. **Repeat:** `python modern/tools/parity/compare_parity.py` (from the repository root); `--quiet` prints only the per-file counts. Exit code 1 only when a premise check fails.

## 4. Summary per file

Units: MATCH = identical record; EXPECTED or UNEXPECTED = a differing record (UNEXPECTED if any of its fields is), a record on one side only, or a file-level difference. "Fields E/U" counts differing fields classified EXPECTED / UNEXPECTED.

| Run | File | Legacy records | Modern records | Bytes identical | MATCH | EXPECTED | UNEXPECTED | Fields E/U |
|---|---|---|---|---|---|---|---|---|
| PRVDAY01 baseline | provider master BEFORE (seed) | 16 | 16 | no | 12 | 0 | 4 | 0 / 4 |
| PRVDAY01 baseline | APPERROR | 2 | 5 | no | 1 | 5 | 0 | 0 / 0 |
| PRVDAY01 baseline | provider master after STEP010 | 20 | 19 | no | 12 | 1 | 7 | 0 / 15 |
| PRVDAY01 baseline | SCRNOUT | absent | 8 | n/a | 0 | 0 | 1 | 0 / 0 |
| PRVDAY01 baseline | provider master after STEP040 (final) | absent | 19 | n/a | 0 | 0 | 1 | 0 / 0 |
| PRVDAY01 valid-only | provider master BEFORE (seed) | 16 | 16 | no | 12 | 0 | 4 | 0 / 4 |
| PRVDAY01 valid-only | APPERROR | 0 | 0 | yes (both empty) | 0 | 0 | 0 | 0 / 0 |
| PRVDAY01 valid-only | provider master after STEP010 | 19 | 19 | no | 12 | 0 | 7 | 0 / 15 |
| PRVDAY01 valid-only | SCRNOUT | 20 | 8 | no | 0 | 8 | 12 | 24 / 0 |
| PRVDAY01 valid-only | provider master after STEP030 (lab capture) | 19 | absent | n/a | 0 | 0 | 1 | 0 / 0 |
| PRVDAY01 valid-only | provider master after STEP040 (final) | 19 | 19 | no | 0 | 14 | 5 | 19 / 7 |
| PRVMTH01 seed | provider master BEFORE (seed) | 16 | 16 | no | 12 | 0 | 4 | 0 / 4 |
| PRVMTH01 seed | REVALQ | 4 | 4 | yes | 4 | 0 | 0 | 0 / 0 |
| PRVMTH01 seed | TMSISOUT | 8 | 8 | yes | 8 | 0 | 0 | 0 / 0 |
| PRVMTH01 seed | T-MSIS archive copy (GDG) | 8 | absent | n/a | 0 | 0 | 1 | 0 / 0 |
| PRVMTH01 seed | provider master after | 16 | 16 | no | 8 | 4 | 4 | 4 / 4 |
| PRVDIR01 seed | provider master BEFORE (seed) | 16 | 16 | no | 12 | 0 | 4 | 0 / 4 |
| PRVDIR01 seed | DIROUT | 8 | 8 | no | 0 | 8 | 0 | 8 / 0 |
| PRVDIR01 seed | provider master after | 16 | 16 | no | 12 | 0 | 4 | 0 / 4 |
| PRVSAN01 sancin-baseline | provider master BEFORE (seed) | 16 | 16 | no | 12 | 0 | 4 | 0 / 4 |
| PRVSAN01 sancin-baseline | provider master after | 16 | 16 | no | 11 | 1 | 4 | 1 / 4 |
| PRVSAN01 sancin-baseline | sanction notices (supplementary) | 5 notices | 5 notices | n/a | 4 | 1 | 1 | n/a |

Per file type, all runs together (six files; the supplementary notices are left out):

| File type | Comparisons | Legacy records | Modern records | MATCH | EXPECTED | UNEXPECTED |
|---|---|---|---|---|---|---|
| provider master | 13 | 205 | 204 | 115 | 20 | 53 |
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
| E-2 | D-003 | PRVDAY01 valid-only: provider master after STEP040 (final) | 19 rows | PRVRSK01 resets the due date of every provider to today + 5 years on every daily run (20311120 here); the modern system sets a due date only at approval or a completed revalidation (D-003), so it leaves the date as it was (or unset) in this run | `1005912765 PRV-REVAL-DUE-DATE: 20311120 -> 20280401` |
| E-2 | D-003 | PRVMTH01 seed: provider master after | 4 rows | the month-end sweep queued this provider in both systems (REVALQ is identical); the legacy PRVREV01 only queues it and leaves the master unchanged, while in the modern system the re-screening (reason REVALIDATION) runs by events, passes and completes the revalidation, and D-003 sets the due date at each completed revalidation (business date + 5 years = 20311120); D-029 gives the case its own state | `1005923986 PRV-REVAL-DUE-DATE: 20260331 -> 20311120` |
| E-5 | D-028 | PRVDIR01 seed: DIROUT | 8 rows | the legacy name is the first 60 bytes of PRV-VARIANT (an individual: last name, first name and the start of the licence number; an organisation: legal name and the start of the DBA name); the modern name is composed (first + last, or the legal name) | `1005912765 DIR-NAME: SAMPLE CLINIC 01 S... -> SAMPLE CLINIC 01` |
| E-4 | D-006 | PRVSAN01 sancin-baseline: provider master after | 1 row | sanction S gives DENIED in the legacy (PRVSAN01.cbl:109-110) and the new SUSPENDED status in the modern system | `1005992312 PRV-ENROLL-STATUS: D -> S` |
| E-6 | D-026 | PRVSAN01 sancin-baseline: sanction notices (supplementary) | 1 row | an unknown action code X is rejected by the modern run (no change, no notice); the legacy applies nothing but still queues the general notice PRVNTGEN (D-012: notification-service replaces the CALL) | `1005912765: GEN 1005912765 -> -` |

### 5.1 The DIROUT name area (bytes 23-82), decision D-028

DIROUT is 100 bytes: Medicaid ID 1-9, NPI 10-19, type 20-21, risk tier 22, **name 23-82** (60 bytes), filler 83-100. In all
8 records every byte outside the name area is identical on both sides; only the name area differs, and it differs in all 8.

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
| U-1 | SPEC-CONFLICT | 46 | test data (Q-106) against MAM-43 AC-6 | 10 master files in all five runs (list in 6.2) | two conventions for 'no due date': the test-data files, and so the legacy run, carry 99991231 (Q-106 default A, DEFAULT APPLIED, because the codec rejects zeros), while the modern export writes 00000000 as MAM-43 AC-6 specifies (D-029: zero means unset) from an SQL seed that holds NULL; MAM-50 AC-1 'seed equals the .dat' therefore does not hold byte for byte; the same value pair appears for the new providers after STEP010 (the intake record carries 99991231, the modern provider has no due date) | Decide the convention (section 8, question 1). Recommended default A: the export writes 99991231 for an unset due date (Q-106 A), tests first. |
| U-2 | NEEDS-DECISION | 9 | modern | PRVDAY01 baseline: provider master after STEP010; PRVDAY01 valid-only: provider master after STEP010; PRVDAY01 valid-only: provider master after STEP040 (final) | effective date of a new provider: the legacy keeps the effective date of the intake record (20260115, D-025); the modern system sets the registration date (20261120), Q-015/Q-A1 default A, status DEFAULT APPLIED - revisit, not an approved D decision | Confirm Q-A1 default A as a new decision, or carry the application's date (needs a contract change). Recommended: confirm A. |
| U-3 | NEEDS-DECISION | 10 | neither (design difference) | PRVDAY01 baseline: provider master after STEP010; PRVDAY01 valid-only: provider master after STEP010 | snapshot timing: the legacy file is the master right after STEP010 (flag U, no tier); the modern system registers, screens and tiers a new provider by events within seconds, so the modern snapshot already holds the result of STEP030/STEP040 (no stop between them is possible); no D decision covers the event chain replacing the step sequence | Record a new decision: the event chain replaces the step sequence, compare final states only. Recommended: accept. |
| U-4 | NEEDS-DECISION | 3 | neither (design difference) | PRVDAY01 valid-only: provider master after STEP040 (final) | the legacy PRVRSK01 sets a tier on every record of the file, whatever its status and screen flag; the modern risk-service assigns a tier only after a PASS screening (MAM-35 AC-6: a failed screening gets no tier), so denied or failed providers keep a blank tier; MAM-35 is an approved story, but no D decision states the rule | Record a new decision: a tier is assigned only after a PASS screening (MAM-35 AC-6). Recommended: accept. |
| U-5 | NEEDS-DECISION | 12 | neither (design difference) | PRVDAY01 valid-only: SCRNOUT | the legacy PRVSCR01 screens every provider with flag U in one pass, new providers included; in the modern system a new provider gets its INITIAL screening at registration (event), so its rows are not in the screening run's file; they exist in the screening service (logs/screenings-of-new-providers.json) | Record a new decision: new providers are screened at registration, outside the run's file. Recommended: accept. |
| U-6 | NEEDS-DECISION | 2 | neither (design difference) | PRVDAY01 baseline: SCRNOUT; PRVDAY01 baseline: provider master after STEP040 (final) | the legacy job never produced this file: STEP010 ended with RC=4 (the first invalid application) and the JCL bypassed STEP015-STEP040 (COND); the modern run is not gated on the intake result and went on to screening and tiering; D-001 and D-024 are the nearest decisions but neither states that gate | Record a new decision: the modern jobs are not gated on the intake return code. Recommended: accept. |
| U-7 | NOT-COMPARABLE | 1 | neither (design difference) | PRVDAY01 valid-only: provider master after STEP030 (lab capture) | the legacy file is a lab capture between two steps (CAP030, after PRVSCR01); the modern system has no step between screening and tiering (events), so no such state can be exported; it is not a job output | Accept as not comparable (a lab capture between two steps, not a job output). |
| U-8 | CAPTURE-GAP | 1 | neither (design difference) | PRVMTH01 seed: T-MSIS archive copy (GDG) | the legacy GDG generation MMPRV.TMSIS.ARCHIVE(+1) could not be created (BLOCKED, IEF197I in the anonymous job); only a temporary IEBGENER copy exists (byte-identical to TMSISOUT); the modern archive generation (16) is recorded in tmsis-extract-list.json but its content was not exported | Open. Needs the lab account (password in `.env`, not in chat) to capture the GDG generation, and an export of the modern archive content. |
| U-9 | NEEDS-DECISION | 1 | neither (design difference) | PRVSAN01 sancin-baseline: sanction notices (supplementary) | reinstating (R) a PENDING provider makes it ACTIVE (D-027 preserves the legacy rule); the modern notification-service then also sends an APPROVED notice besides the RST notice; the legacy sends only the reinstatement notice; no D decision covers the extra notice | Business question: should reinstating a pending provider also send an APPROVED notice? Recommended: accept (D-027 makes the provider ACTIVE). |
| U-10 | TEST-DATA-GAP | 1 | test data / mock (SD20001 missing from the licence-board stub) | PRVDAY01 valid-only: provider master after STEP040 (final) | application 1006133288 (an individual, licence SD20001, called 'valid IN' in `tools/data/generate.py:161`) fails the modern screening: the licence-board stub (WireMock, `infra/wiremock/mappings/license-board/`) knows SD10001-SD10007 and the six sample licences, so LICBOARD answers not found (detail LICENCE NOT FOUND) and the unknown-licence default Q-041/C-3 A (DEFAULT APPLIED - revisit, not an approved decision) gives F; the legacy stubs pass everybody (P). D-005 only replaces the four stubs by a real screening service and does not say what an unknown licence gives, so it does not explain this difference. The same cause shows in the STEP010 snapshot (F, counted under U-3) and in the blank tier (counted under U-4) | Decide (section 8, question 7). Recommended A: add SD20001 / TX ACTIVE to the licence-board stub, tests first; confirm Q-041 default A as its own decision. |

### 6.2 Every UNEXPECTED difference

One row per file, group, field and value pair; identical differences on several keys share a row (all keys are listed). The full list, one entry per field, is `compare-result.json`.

| # | Group | Run / file | Key(s) | Field | Legacy value | Modern value | Likely cause |
|---|---|---|---|---|---|---|---|
| 1 | U-1 | PRVDAY01 baseline: provider master BEFORE (seed) | 1005914035, 1005925213, 1005933068, 1005960046 | PRV-REVAL-DUE-DATE | `99991231` | `00000000` | no-date convention conflict (Q-106 vs MAM-43 AC-6) |
| 2 | U-1 | PRVDAY01 baseline: provider master after STEP010 | 1005914035, 1005925213, 1005933068, 1005960046, 1006133288, 1006144350, 1006153716 | PRV-REVAL-DUE-DATE | `99991231` | `00000000` | no-date convention conflict (Q-106 vs MAM-43 AC-6) |
| 3 | U-3 | PRVDAY01 baseline: provider master after STEP010 | 1006133288 | PRV-SCREEN-FLAG | `U` | `F` | snapshot taken after the event chain ran |
| 4 | U-2 | PRVDAY01 baseline: provider master after STEP010 | 1006133288, 1006144350, 1006153716 | PRV-EFFECTIVE-DATE | `20260115` | `20261120` | Q-A1 default: registration date instead of the intake date |
| 5 | U-3 | PRVDAY01 baseline: provider master after STEP010 | 1006144350, 1006153716 | PRV-RISK-TIER | `(blank)` | `L` | snapshot taken after the event chain ran |
| 6 | U-3 | PRVDAY01 baseline: provider master after STEP010 | 1006144350, 1006153716 | PRV-SCREEN-FLAG | `U` | `P` | snapshot taken after the event chain ran |
| 7 | U-6 | PRVDAY01 baseline: SCRNOUT | (file) | (file only on the modern side) | `absent` | `8 records` | legacy steps bypassed by COND after RC=4; modern not gated |
| 8 | U-6 | PRVDAY01 baseline: provider master after STEP040 (final) | (file) | (file only on the modern side) | `absent` | `19 records` | legacy steps bypassed by COND after RC=4; modern not gated |
| 9 | U-1 | PRVDAY01 valid-only: provider master BEFORE (seed) | 1005914035, 1005925213, 1005933068, 1005960046 | PRV-REVAL-DUE-DATE | `99991231` | `00000000` | no-date convention conflict (Q-106 vs MAM-43 AC-6) |
| 10 | U-1 | PRVDAY01 valid-only: provider master after STEP010 | 1005914035, 1005925213, 1005933068, 1005960046, 1006133288, 1006144350, 1006153716 | PRV-REVAL-DUE-DATE | `99991231` | `00000000` | no-date convention conflict (Q-106 vs MAM-43 AC-6) |
| 11 | U-3 | PRVDAY01 valid-only: provider master after STEP010 | 1006133288 | PRV-SCREEN-FLAG | `U` | `F` | snapshot taken after the event chain ran |
| 12 | U-2 | PRVDAY01 valid-only: provider master after STEP010 | 1006133288, 1006144350, 1006153716 | PRV-EFFECTIVE-DATE | `20260115` | `20261120` | Q-A1 default: registration date instead of the intake date |
| 13 | U-3 | PRVDAY01 valid-only: provider master after STEP010 | 1006144350, 1006153716 | PRV-RISK-TIER | `(blank)` | `L` | snapshot taken after the event chain ran |
| 14 | U-3 | PRVDAY01 valid-only: provider master after STEP010 | 1006144350, 1006153716 | PRV-SCREEN-FLAG | `U` | `P` | snapshot taken after the event chain ran |
| 15 | U-5 | PRVDAY01 valid-only: SCRNOUT | 1006133288 x4 | (record only in the legacy file) | `1006133288        P` | absent | new providers screened at registration, not in the run file |
| 16 | U-5 | PRVDAY01 valid-only: SCRNOUT | 1006144350 x4 | (record only in the legacy file) | `1006144350        P` | absent | new providers screened at registration, not in the run file |
| 17 | U-5 | PRVDAY01 valid-only: SCRNOUT | 1006153716 x4 | (record only in the legacy file) | `1006153716        P` | absent | new providers screened at registration, not in the run file |
| 18 | U-7 | PRVDAY01 valid-only: provider master after STEP030 (lab capture) | (file) | (file only on the legacy side) | `19 records` | `absent` | lab capture between two steps; no modern equivalent |
| 19 | U-4 | PRVDAY01 valid-only: provider master after STEP040 (final) | 1005914035, 1005960046 | PRV-RISK-TIER | `M` | `(blank)` | legacy tiers every record; modern only after PASS |
| 20 | U-4 | PRVDAY01 valid-only: provider master after STEP040 (final) | 1006133288 | PRV-RISK-TIER | `L` | `(blank)` | legacy tiers every record; modern only after PASS |
| 21 | U-2 | PRVDAY01 valid-only: provider master after STEP040 (final) | 1006133288, 1006144350, 1006153716 | PRV-EFFECTIVE-DATE | `20260115` | `20261120` | Q-A1 default: registration date instead of the intake date |
| 22 | U-10 | PRVDAY01 valid-only: provider master after STEP040 (final) | 1006133288 | PRV-SCREEN-FLAG | `P` | `F` | SD20001 is not in the licence-board stub; Q-041 default for an unknown licence (D-005 does not cover it) |
| 23 | U-1 | PRVMTH01 seed: provider master BEFORE (seed) | 1005914035, 1005925213, 1005933068, 1005960046 | PRV-REVAL-DUE-DATE | `99991231` | `00000000` | no-date convention conflict (Q-106 vs MAM-43 AC-6) |
| 24 | U-8 | PRVMTH01 seed: T-MSIS archive copy (GDG) | (file) | (file only on the legacy side) | `8 records` | `absent` | GDG generation blocked; modern archive content not exported |
| 25 | U-1 | PRVMTH01 seed: provider master after | 1005914035, 1005925213, 1005933068, 1005960046 | PRV-REVAL-DUE-DATE | `99991231` | `00000000` | no-date convention conflict (Q-106 vs MAM-43 AC-6) |
| 26 | U-1 | PRVDIR01 seed: provider master BEFORE (seed) | 1005914035, 1005925213, 1005933068, 1005960046 | PRV-REVAL-DUE-DATE | `99991231` | `00000000` | no-date convention conflict (Q-106 vs MAM-43 AC-6) |
| 27 | U-1 | PRVDIR01 seed: provider master after | 1005914035, 1005925213, 1005933068, 1005960046 | PRV-REVAL-DUE-DATE | `99991231` | `00000000` | no-date convention conflict (Q-106 vs MAM-43 AC-6) |
| 28 | U-1 | PRVSAN01 sancin-baseline: provider master BEFORE (seed) | 1005914035, 1005925213, 1005933068, 1005960046 | PRV-REVAL-DUE-DATE | `99991231` | `00000000` | no-date convention conflict (Q-106 vs MAM-43 AC-6) |
| 29 | U-1 | PRVSAN01 sancin-baseline: provider master after | 1005914035, 1005925213, 1005933068, 1005960046 | PRV-REVAL-DUE-DATE | `99991231` | `00000000` | no-date convention conflict (Q-106 vs MAM-43 AC-6) |
| 30 | U-9 | PRVSAN01 sancin-baseline: sanction notices (supplementary) | 1005933068 | notice APPROVED | absent | `APPROVED 1005933068` | extra APPROVED notice after reinstating a pending provider |

(30 rows stand for 86 difference entries.)

## 7. BLOCKED, with the exact error

| What | Exact error | Effect on this report |
|---|---|---|
| GDG generation of PRVMTH01 STEP030 (`MMPRV.TMSIS.ARCHIVE(+1)`) | `IEF197I SYSTEM ERROR DURING ALLOCATION`, then `IEF453I PARDG1 - JOB FAILED - JCL ERROR` (jobs 187, 189, 190, 191; an anonymous job cannot create a permanent dataset) | Only a temporary IEBGENER copy exists (byte-identical to TMSISOUT). U-8. |
| Run under the lab account HERC01 | `IEF722I PARPRB01 - FAILED - INVALID PASSWORD GIVEN` (job 185); the password is not in the repository and no other was tried | All runs anonymous with temporary datasets; the lab's own `MMPRV.*` datasets were not touched. |
| Business date 2026-01-15 | the lab programs read the MVS system date, and changing it is forbidden | Both sides were run for 2026-11-20 (AC-2, AC-4 deviation). |
| The `*-LAB.jcl` stubs | not in `/app/mvs/jcl` (only `dummy`) and not in the repository | Decks reconstructed from the JES2 listings of lab jobs 154-183 (2026-11-19). |
| Modern `GET /api/v1/applications` | HTTP 403 for ADMIN (the endpoint is ANALYST only; the body is in `logs/applications.json` of the two PRVDAY01 runs) | No application list on the modern side; the error codes E101-E104 behind each APPERROR row are not captured. |

No legacy job needed a second attempt (stage 1). On the modern side, earlier attempts were runner defects only and were overwritten by the five final runs (stage 2).

## 8. Decisions needed from the user

Each is a proposal. The lead recorded them on 2026-11-20 17:55 as questions in `modern/docs/analysis/questions.md` and
applied the defaults below (the user's standing rule: apply the recommended default, mark it DEFAULT APPLIED - revisit):

| Item | Group | Question | Default applied |
|---|---|---|---|
| 1 | U-1 | Q-124 | **Nothing changed.** Every option changes an approved criterion (MAM-43 AC-6), an approved library rule or needs a new decision, so it is left to the user. Recommended: A. |
| 2 | U-2 | Q-125 | A: behaviour unchanged (registration date); needs the user's confirmation to become a decision. |
| 3 | U-3, U-5, U-6, U-7 | Q-126 | A: no code change; one new decision proposed (compare final states only). |
| 4 | U-4 | Q-127 | A: accept (MAM-35 AC-6). |
| 5 | U-9 | Q-128 | A: accept; business to confirm. |
| 6 | U-8 | Q-129 | Open, BLOCKED (GDG generation and the lab account; no password was guessed). |
| 7 | U-10 | Q-130 | A: the stub fix is prepared and tested offline (new mapping `infra/wiremock/mappings/license-board/clean-SD20001.json`, new test `tools/checks/tests/test_intake_licences_known_to_stub.py`, 21 passed). It is **not loaded into the running WireMock and the two PRVDAY01 modern runs were not repeated** (user deadline 20:00: no further re-run), so U-10 stays UNEXPECTED in this report. Expected after a reload and a re-run: flag P and tier L for 1006133288, as in the legacy files (not verified). |

None of these defaults turns an UNEXPECTED row into EXPECTED: that needs an approved decision with a D number.

1. **No due date (U-1, 46 rows).** The test data and the legacy run write 99991231; the modern export writes 00000000 (MAM-43 AC-6, D-029) and the codec rejects zeros (D-024); the SQL seed holds NULL.  
   Options: A) the export writes 99991231 for an unset due date (Q-106 A), MAM-43 AC-6 changed, tests first; B) keep zeros in the export and re-run the legacy with zeros in the four seed records (the lab loader should take them; not tested); C) accept both conventions as a new decision D-030 and compare the two values as equal. **Recommended: A** (it is the default already applied in Q-106, and the codec could then read the export back).
2. **Effective date of a new provider (U-2, 9 rows).** Legacy keeps the intake record's date (20260115); modern sets the registration date (20261120).  
   Options: A) confirm Q-A1 default A as a decision; B) carry the application's date (contract change). **Recommended: A.**
3. **Event chain instead of steps (U-3, U-5, U-6, U-7).** The modern system registers, screens and tiers by events; the intermediate snapshot, the run file of screening and the JCL gate do not exist.  
   Options: A) one new decision covering all four (compare final states, screening rows of new providers live in the screening service, intake errors do not block later work); B) decide each separately; C) rebuild step semantics (not recommended). **Recommended: A.**
4. **Tier only after PASS (U-4, 3 rows).** Options: A) accept (MAM-35 AC-6); B) also tier denied and failed providers. **Recommended: A.**
5. **Extra APPROVED notice (U-9).** Options: A) accept; B) suppress it when the cause is a sanction reinstatement. **Recommended: A**, business to confirm.
6. **Capture gaps (U-8).** Provide the lab account password in `.env` (not in chat) to capture the GDG generation, and decide whether the modern archive content should be exported for comparison. **Recommended:** leave open.
7. **Licence SD20001 of application 1006133288 (U-10, 1 row).** `tools/data/generate.py:161` creates this application as a valid individual with licence SD20001, but the licence-board stub knows only SD10001-SD10007 and the six sample licences. The modern screening therefore answers LICBOARD F (LICENCE NOT FOUND; Q-041 default A, DEFAULT APPLIED - revisit) and the provider gets flag F and no tier, while the legacy stubs pass it (flag P, tier L). D-005 does not explain it: it only replaces the four stubs and does not say what an unknown licence gives.  
   Options: A) add SD20001 / TX ACTIVE to the licence-board stub (`clean-seeded.json` matches `^SD1000[1-7]$`), tests first, so the intake application passes as its generator comment says; the legacy side stays as it is, and the rows of this provider for the flag, the tier and the STEP010 snapshot are then expected to change (not verified); B) change the licence of this application in the test data to one the stub knows and run the legacy intake again (needs Hercules; the golden inputs change); C) keep the failure as a deliberate scenario, rename it in the generator and record Q-041 default A as a decision (the P to F can then be EXPECTED under that new D number). **Recommended: A**, and confirm Q-041 default A as its own decision.

Observations that are not differences in the six files but matter:

- **The modern PRVMTH01 run changes the master**: the four providers the sweep queued (1005923986, 1005947811, 1005951276, 1005989276; the same four as the legacy REVALQ) get due date 20311120. The code (`CompleteRevalidation`, `VerdictEventsListener`) sets the due date at a passed REVALIDATION re-screening, so I classified it D-003 (with D-029). The re-screenings themselves were not captured, so this is inferred from the code and from the four NPIs, not observed. The legacy PRVREV01 only queues them and leaves the master unchanged.
- **D-002 (INFERRED: PRVRSK01 never reaches its REWRITE)** is not confirmed by the lab: the lab port reaches the REWRITE and tiers and re-dates every record. The lab port is not the unmodified source, so this does not prove the source behaves so.
- **D-007 and D-021** are not observable: the lab runs 130-byte records and a patched PRVTMS01 line; the legacy as shipped (121 bytes, unpatched) was not run. D-004 (2-digit year) gives the same result as the 4-digit rule at this date (2026 + 5 = 2031).

## 9. What was not compared, and why

- **Job logs and return codes** as data: legacy RC=4 / `*FLUSH*` against the modern COMPLETED runs with error counts are described in section 2 and in group U-6, not compared byte for byte (D-024 replaces return codes by run results).
- **Intermediate master snapshots**: the legacy STEP030 capture has no modern counterpart (U-7). The modern "after STEP010" and "after STEP040" are taken after the events settled (the manifest says so), so they are not the same point in the workflow as the legacy files (U-3).
- **Baseline run, STEP030/STEP040 files**: the legacy never produced SCRNOUT or the final master (COND bypass after RC=4); the modern files exist and are listed as U-6. Their legacy counterpart is the valid-only run, which uses only the first three applications.
- **The GDG archive generation**: blocked on the legacy side; the legacy temporary copy equals the legacy TMSISOUT, which equals the modern TMSISOUT, but the modern archive content (generation 16) was not exported (U-8).
- **Screening results of the new providers** (API JSON): only used as context for U-5; the modern INITIAL screenings are in `parity/modern/PRVDAY01/*/logs/screenings-of-new-providers.json`.
- **Sanction notices and the sanction history**: only the notice lines were checked, as a supplementary comparison (lab stubs that DISPLAY a line against notification-service); the history and the decision D-018 are not observable in the legacy files.
- **PRVDAY01 STEP020 SORT output** (D-009 DROP, never read), **the Medicare reciprocity check** (D-010 DROP), **PRVDEAD1** (D-011 DROP): dropped by decision.
- **Decisions not observable in these files:** D-004, D-007, D-012, D-013, D-014, D-016 (error codes), D-017, D-018, D-019 (approval), D-020, D-021, D-022, D-023, D-024, D-029.
- **The modern master input as a file**: the modern system does not load the `.dat`; it is seeded from SQL (section 1, U-1). The copies in `modern/parity/modern/<job>/<run-id>/inputs/provider-master-seed-sorted.dat` were made by `run_modern.py` for reference, and their description in the modern manifests ('input as sent to the legacy run (same bytes)') is true for `appinput.dat` and `sancin.dat` (those bytes were uploaded) but not for the master copy. The runner and the manifests are not changed in this revision (it needs a stack run); open.
- **The legacy as shipped**: the programs under `source-code/` do not compile unmodified; the comparison is against the lab port (`parity/golden/lab-cobol-diff/`, `labDifferences` in every golden manifest).

## 10. Status of the acceptance criteria of MAM-91

| AC | Status | Why |
|---|---|---|
| AC-1 | not exercised | Golden files exist, so the "no golden files: print the checklist and stop" path did not run; showing it would need the golden folder removed, which I did not do. |
| AC-2 | partly met | Every run has `inputs`, `before`, `after`, `logs` and a manifest with job, code page 1047, file names, record lengths and sha256 (all verified: 213 premise checks ok, 5 information only). The business date in the manifests is 2026-11-20, not 2026-01-15: the MVS date could not be changed. |
| AC-3 | met | Each golden `.ebcdic` has its `.ebcdic.ascii` next to it; the originals still equal their manifest sha256; each `.ascii` equals its `.ebcdic` converted again with code page 1047 by `tools/data/ebcdic_to_ascii.py` (22 files); sizes equal records x record length. The re-conversion shows consistency with the converter, not that 1047 is the right table: that proof is in `README-hercules-lab.md` section 5 and in the BEFORE files equalling the loaded inputs (8 checks). |
| AC-4 | met, with the date deviation | The modern system ran the same inputs on 2026-11-20 and produced APPERROR, SCRNOUT, REVALQ, TMSISOUT, DIROUT and the 130-byte master export; both sides parsed with the codec's layout files. The Java codec itself was not called. |
| AC-5 | met | Every difference is MATCH, EXPECTED (with a D number) or UNEXPECTED; no manifest lists a run-dependent field, so nothing was normalised and no business field was. The report uses EXPECTED/UNEXPECTED for the skill's INTENDED/UNEXPLAINED. |
| AC-6 | met in substance | One report for all runs (as the lead asked) instead of one file per job and run: summary per file (section 4), table of EXPECTED groups with D numbers including D-006 (section 5), table of UNEXPECTED differences with file, key, field, values and cause (section 6). |
| AC-7 | not met yet | 67 UNEXPECTED units (86 rows) wait for the user: questions Q-124 to Q-130 with the defaults of section 8. No difference was marked EXPECTED without a decision number, no Defect story was created, and the comparison must be run again (the script is repeatable) once the decisions are made and the stub fix of Q-130 is loaded. |
| AC-8 | met | PRVTMS01 line 114 (identifier past column 72) runs as the lab port's re-spaced statement, `&DATE` and `&BATCH_DATE` have explicit values (PARM `BATCH_ID=20261120`, temporary dataset names), `source-code/` and `aws-transform/` are unchanged (git status empty), and each patch is recorded in the manifests (`labDifferences`) and in `parity/golden/lab-cobol-diff/`. |

## 11. Files

- `modern/tools/parity/compare_parity.py` (new): the repeatable comparison and the classification rules.
- `modern/parity/compare-result.json` (new, generated): per file the counts, every difference with its class, decision, group and cause; the premise checks; the rule table.
- `modern/docs/reports/parity-report.md`: this report.
- Not changed: `modern/parity/golden/**`, `modern/parity/modern/**`, `source-code/`, `aws-transform/`, no commit.

