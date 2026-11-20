# PRVRSK01

Source: `source-code/BATCH/PRVRSK01.cbl` (137 lines). Run by: PRVDAY01 STEP040 (seq 4, IF STEP010.RC = 0).
Rules: 48 AWS rules (VERIFIED 48) and 7 added; see `../rule-verification-PRVRSK01.csv`.

## Purpose

Risk tiering and revalidation date. For every master record, sets the risk tier from the first three characters of the taxonomy and sets the revalidation due date to today + 5 years.

## Inputs and outputs

| File | DD | Open mode | Dataset (job step) | FD declares | Layout length |
|---|---|---|---|---|---|
| PRV-MASTER-FILE | PRVMSTR | I-O | MMPRV.PROVIDER.MASTER.KSDS (PRVDAY01 STEP040) | 121 | 130 (COPY PRVRECRD) |

## Main flow

1. Open the master I-O (63).
2. Read the next record (69-76).
3. CLASSIFY-ONE: take the taxonomy prefix (79-80); ALTER APPLY-TIER-PARA to HIGH, MODERATE or LIMITED (83-93); PERFORM APPLY-TIER-PARA (95); PERFORM SET-REVALIDATION-DATE (96); REWRITE (97).
4. End: close (66).

## Decision points

- End of file (72).
- Prefix 332 or 251 is High, 341, 291 or 225 is Moderate, anything else Limited (83-93).
- Due year below 70 is 20xx, otherwise 19xx (131-135).

## File-status handling

Only end of file is tested on the read; the REWRITE status is never tested.

## What happens on errors

None handled, no counters, no summary, no RETURN-CODE. INFERRED (not executed): the ALTERed GO TO leaves the PERFORM range, so control may fall from TIER-DONE into SET-REVALIDATION-DATE and off the end of the program, never reaching the REWRITE at 97 (D-002).

## Anti-patterns found

- AP-03 ALTER: 86, 90, 92; APPLY-TIER-PARA at 103.
- AP-04 2-digit year: SET-REVALIDATION-DATE 126-137, pivot at 131.

## Paragraphs

| Paragraph | Lines |
|---|---|
| MAIN-CONTROL | 61-67 |
| CLASSIFY-NEXT | 69-76 |
| CLASSIFY-ONE | 78-97 |
| APPLY-TIER-PARA | 103-104 |
| LIMITED-TIER | 106-108 |
| MODERATE-TIER | 109-111 |
| HIGH-TIER | 112-114 |
| TIER-DONE | 115-116 |
| SET-REVALIDATION-DATE | 126-137 |

## Rules AWS missed

| ID | Lines | Finding |
|---|---|---|
| R-PRVRSK01-ADD-01 | 70-97 | There is no status filter: every provider (active, terminated, denied) is re-tiered on every run, and the due date is reset to today + 5 years each time. D-003 FIX. |
| R-PRVRSK01-ADD-02 | 129-136 | The month and day are copied unchanged, so a run on Feb 29 produces an impossible due date such as 20290229 (no leap-year handling). D-004 area. |
| R-PRVRSK01-ADD-03 | 83-92 | There is no validation of the taxonomy: a blank or invalid value silently becomes LIMITED. (Q-005) |
| R-PRVRSK01-ADD-04 | 95-116 | AP-03. APPLY-TIER-PARA ends in an ALTERed GO TO; TIER-DONE EXIT (115-116) is outside the PERFORM range, so control INFERRED (not executed) continues into SET-REVALIDATION-DATE and off the end of the program: the REWRITE at 97 is likely never reached and only the first record is touched. D-002 FIX. |
| R-PRVRSK01-ADD-05 | 70-76 | READ NEXT tests only for end of file; any other status is not handled (INFERRED effect). (D-024) |
| R-PRVRSK01-ADD-06 | 97-97 | The REWRITE status is never tested. (D-024) |
| R-PRVRSK01-ADD-07 | 61-67 | No counters, no summary DISPLAY and no RETURN-CODE: the job cannot tell how many providers were classified. (D-024) |

Anything marked INFERRED was read from the code and not executed (no compiler or emulator was used).
