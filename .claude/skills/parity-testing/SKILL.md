---
name: parity-testing
description: Comparing legacy job outputs with the modern system's outputs, used in Phase 9 once legacy jobs have been run in another environment. Covers what to capture from the legacy run, the frozen date, normalisation, record-level comparison, classification and the report. Use only when legacy outputs are available.
---

# Parity testing (deferred until legacy outputs exist)

## 1. When it applies

Only after the user provides legacy job outputs captured in another environment
(mainframe, GnuCOBOL, or a replatform runtime). Until then, keep the legacy-format
adapters working and tested (cobol-to-java section 7), so parity can start immediately.

## 2. Capture checklist (give this to whoever runs the legacy jobs)

Run every job with **the same synthetic inputs** from `modern/testdata/fixed-width/`
and **a fixed business date of 2026-01-15** (set the system date, or the date the
program accepts). For each run, capture the files **before and after**, byte for byte,
in binary mode (no code-page conversion), plus the job log and return codes.

| Job / program | Inputs to load | Outputs to capture |
|---|---|---|
| PRVDAY01 (PRVENR01, SORT, PRVSCR01, PRVRSK01) | APPINPUT; provider master (seed) | Provider master after each step; APPERROR; SCRNOUT; step return codes |
| PRVMTH01 (PRVREV01, PRVTMS01, IEBGENER) | Provider master (seed) | REVALQ; TMSISOUT; the GDG archive generation |
| PRVSAN01 (no JCL exists; run with ad-hoc JCL) | SANCIN; provider master | Provider master after; job log (notice CALL failures expected) |
| PRVDIR01 (no JCL exists; run with ad-hoc JCL) | Provider master | DIROUT |

Also record the code page (EBCDIC 037 or 1047) and the exact record lengths of each file
as written. The AWS-generated JCLs in `aws-transform/07-test-data-collection/` can do
the before/after capture; replace their placeholder bucket and profile first.

## 3. Where the files go

`modern/parity/golden/<job>/<run-id>/{inputs,before,after,logs}/` plus a `manifest.json`
holding the job, business date, code page, file names, record lengths and sha256 of
each file.

## 4. Comparison procedure

1. Convert EBCDIC to ASCII with the recorded code page (the data-engineer's converter);
   keep the originals.
2. Run the modern system on the same inputs, with the same business date, using the
   legacy-format adapters to produce the same files.
3. Parse both sides with the same layouts from `libs/fixed-width`.
4. Match records by key (NPI, or record position for files without a key).
5. Compare field by field. Normalise only what the manifest lists as run-dependent
   (e.g. a run timestamp). Never normalise business fields.
6. Classify each difference:
   - `MATCH`
   - `INTENDED`: explained by an approved decision D-0xx (cite it)
   - `UNEXPLAINED`: anything else
7. Write `modern/docs/reports/parity-<job>-<run-id>.md` (section 5).

## 5. Report format

- Summary: records compared; MATCH, INTENDED and UNEXPLAINED counts per file.
- A table of every INTENDED difference, with its decision ID.
- A table of every UNEXPLAINED difference: file, key, field, legacy value, modern value,
  and the likely cause.
- **Any UNEXPLAINED difference is a defect.** The parity-checker never marks a
  difference INTENDED on its own; it proposes, and the user decides.
