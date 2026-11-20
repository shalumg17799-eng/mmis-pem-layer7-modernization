# PRVREV01

Source: `source-code/BATCH/PRVREV01.cbl` (94 lines). Run by: PRVMTH01 STEP010 (seq 1).
Rules: 24 AWS rules (MISMATCH 1, VERIFIED 23) and 4 added; see `../rule-verification-PRVREV01.csv`.

## Purpose

Month-end revalidation sweep. Writes one REVALQ row ('RESCREEN-REQUIRED') for every ACTIVE provider whose revalidation due date is today or earlier.

## Inputs and outputs

| File | DD | Open mode | Dataset (job step) | FD declares | Layout length |
|---|---|---|---|---|---|
| PRV-MASTER-FILE | PRVMSTR | INPUT | MMPRV.PROVIDER.MASTER.KSDS (PRVMTH01 STEP010) | 121 | 130 (COPY PRVRECRD) |
| REVAL-OUT-FILE | REVALQ | OUTPUT | MMPRV.REVAL.QUEUE.D&DATE (PRVMTH01 STEP010) | 80 | 80 |

## Main flow

1. Take today's date (65).
2. Open the master INPUT and REVALQ OUTPUT (66-67).
3. Read each record (75-86); active and due gives QUEUE-FOR-REVAL (88-94).
4. End: close and display the count (70-72).

## Decision points

- End of file (78).
- Status ACTIVE and due date <= today (81-83).

## File-status handling

Only end of file is tested; the WRITE status is never tested.

## What happens on errors

None handled; RETURN-CODE is never set.

## Anti-patterns found

- No seeded anti-pattern in the code, but it consumes the due dates PRVRSK01 resets on every run (AP-04, D-003).

## Paragraphs

| Paragraph | Lines |
|---|---|
| MAIN-CONTROL | 63-73 |
| CHECK-NEXT | 75-86 |
| QUEUE-FOR-REVAL | 88-94 |

## Rules AWS missed

| ID | Lines | Finding |
|---|---|---|
| R-PRVREV01-ADD-01 | 81-84 | The due date compared here is the one PRVRSK01 resets to today + 5 years on every daily run, so no provider processed by PRVRSK01 can ever be due: the queue stays (almost) empty. INFERRED combination (not executed). D-003 FIX. |
| R-PRVREV01-ADD-02 | 81-94 | No state is kept: a provider that stays overdue is queued again at every month end, and the queue row has no sweep date. (D-029) |
| R-PRVREV01-ADD-03 | 81-84 | An active provider whose due date is zero (never classified by PRVRSK01) is <= today and is queued. (D-029) |
| R-PRVREV01-ADD-04 | 76-93 | READ and WRITE statuses are not tested beyond end of file, and RETURN-CODE is never set. (D-024) |

Anything marked INFERRED was read from the code and not executed (no compiler or emulator was used).
