# PRVSCR01

Source: `source-code/BATCH/PRVSCR01.cbl` (149 lines). Run by: PRVDAY01 STEP030 (seq 3, IF STEP010.RC = 0), PRVSCRPR SCREEN (seq 1).
Rules: 62 AWS rules (DROPPED 7, MISMATCH 6, VERIFIED 49) and 6 added; see `../rule-verification-PRVSCR01.csv`.

## Purpose

Screening. For every master record whose screen flag is U, runs four checks (licence, OIG LEIE, SAM, SSA DMF), writes one row per check to SCRNOUT, then sets the screen flag to P or F and rewrites the master.

## Inputs and outputs

| File | DD | Open mode | Dataset (job step) | FD declares | Layout length |
|---|---|---|---|---|---|
| PRV-MASTER-FILE | PRVMSTR | I-O | &HLQ..PROVIDER.MASTER.KSDS (PRVSCRPR SCREEN) | 121 | 130 (COPY PRVRECRD) |
| SCREEN-OUT-FILE | SCRNOUT | OUTPUT | &HLQ..SCREEN.RESULTS.D&DTE (PRVSCRPR SCREEN) | 67 | 67 (COPY SCRNREC) |

## Main flow

1. Open the master I-O and SCRNOUT for output (76-77).
2. Read the next master record (84-93); only unscreened records are screened.
3. SCREEN-ONE-PROVIDER (95-114): the reciprocity branch (dead), the four checks (each writes a row), the verdict, REWRITE.
4. End: close, display the failure count (80-81).

## Decision points

- End of file (87).
- Screen flag U (90).
- Reciprocity flag (97): never true, the flag is hard-coded N.
- All four results P gives pass, otherwise fail (106-111).

## File-status handling

Only end of file is tested on the read. The REWRITE and the screening-row WRITE statuses are never tested.

## What happens on errors

None are handled and RETURN-CODE is never set. All four checks are stubs that return P, so the fail branch cannot run (D-005).

## Anti-patterns found

- AP-02 dead code: flag at 61, guard at 97, CHECK-MEDICARE-RECIPROCITY at 140.

## Paragraphs

| Paragraph | Lines |
|---|---|
| MAIN-CONTROL | 74-82 |
| SCREEN-NEXT-PROVIDER | 84-93 |
| SCREEN-ONE-PROVIDER | 95-114 |
| CHECK-LICENSE | 116-119 |
| CHECK-LEIE-EXCLUSION | 121-124 |
| CHECK-SAM-DEBARMENT | 126-128 |
| CHECK-SSA-DMF | 130-132 |
| CHECK-MEDICARE-RECIPROCITY | 140-144 |
| WRITE-SCREEN-ROW | 146-149 |

## Rules AWS missed

| ID | Lines | Finding |
|---|---|---|
| R-PRVSCR01-ADD-01 | 118-131 | All four checks hard-code P: nothing is looked up (no CALL or MQ), so the fail branch (110-112) is unreachable. D-005. |
| R-PRVSCR01-ADD-02 | 147-149 | Each screening row sets only SCR-NPI and SCR-PASS. SCR-SOURCE, SCR-VERDICT-DATE and SCR-DETAIL are never set, so the four rows of a provider look identical and the 42 CFR 455 audit trail is empty. D-005. |
| R-PRVSCR01-ADD-03 | 90-111 | The program sets only the screen flag; it never sets the enrolment status to ACTIVE. Nothing in this estate does (D-019). |
| R-PRVSCR01-ADD-04 | 85-93 | READ NEXT only tests for end of file. Any other read status is ignored and the previous record area is screened again (INFERRED, not executed). (D-024) |
| R-PRVSCR01-ADD-05 | 114-149 | The status of REWRITE (114) and of the screening WRITE (149) is never tested, so failures are silent. (D-024) |
| R-PRVSCR01-ADD-06 | 75-82 | RETURN-CODE is never set, even when providers fail screening: the job cannot see failures. (D-024) |

Anything marked INFERRED was read from the code and not executed (no compiler or emulator was used).
