# PRVDEAD1

Source: `source-code/BATCH/PRVDEAD1.cbl` (51 lines). Run by: no job in this estate.
Rules: 14 AWS rules (DROPPED 14) and 1 added; see `../rule-verification-PRVDEAD1.csv`.

## Purpose

Historical paper-fingerprint intake for high-risk providers, superseded by LiveScan in 2019. Dropped (D-011).

## Inputs and outputs

No files.

## Main flow

1. Display the banner (35).
2. Test the flag (37): it is hard-coded N, so only the 'disabled' message runs (40).
3. PROCESS-PAPER-CARD (48-51) is unreachable.

## Decision points

- WS-PAPER-FP-ON (37): never true.

## File-status handling

No files.

## What happens on errors

None.

## Anti-patterns found

- AP-08 orphan and dead flag: flag at 29, guard at 37; no JCL runs the program.

## Paragraphs

| Paragraph | Lines |
|---|---|
| MAIN-CONTROL | 34-42 |
| PROCESS-PAPER-CARD | 48-51 |

## Rules AWS missed

| ID | Lines | Finding |
|---|---|---|
| R-PRVDEAD1-ADD-01 | 29-42 | AP-08: the flag WS-PAPER-FP-ENABLED is hard-coded N and never set, no JCL in the estate runs the program, and it only DISPLAYs. Nothing to migrate (D-011 DROP). |

Anything marked INFERRED was read from the code and not executed (no compiler or emulator was used).
