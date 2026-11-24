# MAM-91 Parity: run the legacy jobs on Hercules and compare their outputs with the modern system

Story log (kept by the lead; the full result is `modern/docs/reports/parity-report.md`, the legacy run is described in
`modern/docs/reports/parity-legacy-run.md`). Status in Jira: In Progress, because AC-7 asks for a human decision on every
unexplained difference and the questions Q-125 to Q-128 wait for the user's confirmation (default applied: accept), and
Q-129 is BLOCKED (GDG archive generation, lab account).

## Result per file after the re-run of 2026-11-20 23:17

| File | Result |
|---|---|
| REVALQ (PRVMTH01) | MATCH, 4 of 4 records byte-identical |
| TMSISOUT (PRVMTH01) | MATCH, 8 of 8 records byte-identical; the GDG archive copy could not be compared (Q-129, BLOCKED) |
| DIROUT (PRVDIR01) | EXPECTED, 8 records differ only in the name area, bytes 23-82 (D-028) |
| APPERROR (PRVDAY01) | EXPECTED (D-001): 1 MATCH, 5 EXPECTED; the valid-only run is identical (both empty) |
| SCRNOUT (PRVDAY01) | 8 EXPECTED (D-005), 13 UNEXPECTED (groups U-5 and U-6, question Q-126) |
| Provider master (13 comparisons) | 155 MATCH, 20 EXPECTED (D-001, D-003, D-006), 13 UNEXPECTED (U-2, U-3, U-4, U-6, U-7; Q-125, Q-126, Q-127) |
| Sanction notices (supplementary) | 4 MATCH, 1 EXPECTED (D-026), 1 UNEXPECTED (U-9, Q-128) |

Totals: MATCH 168 (was 128), EXPECTED 41, UNEXPECTED 27 (was 67).

## Log

**2026-11-20 23:17, parity re-run (parity-checker).** Why: Q-124 A is live (the export writes 99991231 for no due date), the licence stub knows SD20001 (Q-130 A), provider-service got the optional parameter afterNpi. Preconditions checked: export bytes 43-50 are 99991231 for 4 providers and 00000000 for none; WireMock answers found true for SD20001 / TX; the golden manifests (52 files) match their sha256, git shows no golden change. Ran day-baseline, day-valid-only, month-seed, dir-seed and san-baseline under the stack lock (23:11:41 to 23:16:36), business date 2026-11-20, each exit 0 on the first attempt; reseeded to 16 providers. Compared with `compare_parity.py` (unchanged). Before: MATCH 128, EXPECTED 41, UNEXPECTED 67 (rows 63 / 86; provider master 115 / 20 / 53). After: MATCH 168, EXPECTED 41, UNEXPECTED 27 (rows 63 / 40; provider master 155 / 20 / 13). U-1 (46 rows) and U-10 (1 row) closed. Row by row against the first result: 92 unchanged, 57 gone, 11 new (92 + 57 = 149 rows before, 92 + 11 = 103 now): 4 new U-3 rows (1006133288 flag U -> P, tier blank -> L, in 2 runs; it now passes) and 7 new E-2 rows (EXPECTED, D-003; valid-only final master, 20311120 -> 99991231 for 1005914035, 1005925213, 1005933068, 1005960046, 1006133288, 1006144350, 1006153716; Q-124 A); no new kind of difference. Still open: U-2 (9), U-3 (12), U-4 (2), U-5 (12), U-6 (2), U-7 (1), U-8 (1, BLOCKED, Q-129), U-9 (1): Q-125 to Q-129; AC-7 not met; AC-1 not exercised; AC-2 and AC-4 date deviation (business date 2026-01-15 not run; AC-4 partly met). Not verified: the Java codec was not called; the legacy side was not run again; the GDG generation and the HERC01 login stay BLOCKED; the revalidation completion behind the 4 E-2 rows of PRVMTH01 is inferred, not observed; the value 20311120 of those rows is the processing date + 5 years (Q-018 / Q-A4 default A, not approved), equal to the business date + 5 years only because both are 2026-11-20 (at 2026-01-15 it would differ).

**2026-11-20 23:38, correction of this text (parity-checker).** After two independent checks: the new rows are 11, not 4 (the 7 E-2 rows were missing); AC-4 is partly met, not met (its Given date 2026-01-15 was not run); the value 20311120 of the 4 E-2 rows of PRVMTH01 comes from the service clock (Q-A4 default A), not from the business date. No scenario was run again; no file other than `modern/docs/reports/parity-report.md` was changed in the correction.

**2026-11-20 23:50, last independent check (code-reviewer).** The three corrections are in every place of the report and all numbers were recomputed from the two result files (149 rows before, 103 now; 92 unchanged, 57 gone, 11 new). One sentence of section 8 was made exact (at business date 2026-01-15 the sweep would queue 2 of the 4 providers) and four wordings were tidied by the lead. Nothing was run again.

**2026-11-24, final parity re-run and closing (lead).** The user confirmed the recommended answers to Q-124 to Q-130 (message of 2026-11-24). Decisions D-031 (effective date of a new provider is the
registration date, Q-125 A), D-032 (the event chain replaces the JCL step sequence; parity compares final states, Q-126 A), D-033 (a tier only after a PASS screening, Q-127 A) and D-034 (the APPROVED
notice after reinstating a PENDING provider, Q-128 A) were recorded in `defect-register.md`; Q-130 A (SD20001 in the licence stub) had been applied on 2026-11-20; Q-129 stays BLOCKED. The seven rules of
those groups moved to EXPECTED in `tools/parity/compare_parity.py` (commit 18f6efc). The modern side was run again on deploy 12 (all five scenarios, 14:14 to 14:29 IST, business date 2026-11-20, each
manifest "problems: 0"; the stack reseeded afterwards). A frozen-date effect was found and handled: the modern services stamp the real date of the run (20261124 and 20311124), so the two date rules now expect the date
of the modern run and that date + 5 years, read from the run manifest (exact match, nothing normalised). **Final result: MATCH 168, EXPECTED 67, UNEXPECTED 1 units; rows 102 EXPECTED, 1 UNEXPECTED; premise checks
213 ok, 0 failed.** The one UNEXPECTED unit is U-8, the permanent generation `MMPRV.TMSIS.ARCHIVE(+1)`, **BLOCKED (Q-129)**: the lab account HERC01 was refused, only a temporary copy was captured; it stays listed
in `modern/docs/reports/parity-report.md` (block "FINAL VERSION of 2026-11-24"). AC-7 is met except for that capture gap; AC-2 and AC-4 stay "partly met" for the date reason of section 10 of the report (both
sides ran on 2026-11-20, not 2026-01-15). No Defect story was created: nothing points at a coding error. Not verified: the content of the modern archive generation against a legacy generation (no legacy generation exists).
Questions for you: 1. Q-129: put the lab password into the root `.env` (never in chat) when you want the archive generation closed; then one more PRVMTH01 run on Hercules and an export of the modern archive content.
Next step: Jira Done for MAM-91 with the capture gap listed (the connection to Jira was not available when this was written; queued in PROGRESS.md).
