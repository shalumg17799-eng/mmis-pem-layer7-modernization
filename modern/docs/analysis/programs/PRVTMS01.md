# PRVTMS01

Source: `source-code/BATCH/PRVTMS01.cbl` (117 lines). Run by: PRVMTH01 STEP020 (seq 2).
Rules: 55 AWS rules (MISMATCH 2, VERIFIED 53) and 7 added; see `../rule-verification-PRVTMS01.csv`.

## Purpose

Monthly T-MSIS provider extract. Writes one 120-byte row (record type PRV01, Medicaid ID, NPI, status A, period YYDDD) for every ACTIVE provider.

## Inputs and outputs

| File | DD | Open mode | Dataset (job step) | FD declares | Layout length |
|---|---|---|---|---|---|
| PRV-MASTER-FILE | PRVMSTR | INPUT | MMPRV.PROVIDER.MASTER.KSDS (PRVMTH01 STEP020) | 121 | 130 (COPY PRVRECRD) |
| TMSIS-OUT-FILE | TMSISOUT | OUTPUT | MMPRV.TMSIS.PROVIDER.D&DATE (PRVMTH01 STEP020) | 120 | 120 |

## Main flow

1. Take today's Julian date YYDDD and split it (80-82).
2. Open the master INPUT and TMSISOUT OUTPUT (83-84).
3. Read each record (91-100); active gives WRITE-TMSIS-ROW (102-117): future-enrolment warning, map the fields, write, count.
4. End: close and display the count (87-88).

## Decision points

- End of file (94).
- Status ACTIVE (97).
- 2-digit enrolment year > 2-digit current year gives a warning only (107).

## File-status handling

Only end of file is tested; the WRITE status is never tested.

## What happens on errors

None handled; RETURN-CODE is never set. Line 114 crosses column 72, so the program probably does not compile as shipped (INFERRED).

## Anti-patterns found

- AP-07 YYDDD Julian dates: 80, 107.

## Paragraphs

| Paragraph | Lines |
|---|---|
| MAIN-CONTROL | 78-89 |
| EXTRACT-NEXT | 91-100 |
| WRITE-TMSIS-ROW | 102-117 |

## Rules AWS missed

| ID | Lines | Finding |
|---|---|---|
| R-PRVTMS01-ADD-01 | 72-114 | The target WS-T-PERIOD-YYDDD ends in column 73 of line 114, and a COBOL compiler ignores columns 73-80. It would read the undefined name WS-T-PERIOD-YYDD, so the program likely does not compile as shipped (INFERRED, no compiler here). The only code line in the estate that crosses column 72. (D-021) |
| R-PRVTMS01-ADD-02 | 104-110 | Comparing 2-digit years flags every pre-2000 enrolment (YY 98 or 99 is greater than 26) as a future enrolment and could never flag a real future date after 2099. AP-07, D-015. |
| R-PRVTMS01-ADD-03 | 62-82 | WS-TODAY-DDD, WS-ENROLL-JUL and WS-AGE-DAYS are declared but never used: the 'enrolment age' described in the README is not implemented. (Q-007) |
| R-PRVTMS01-ADD-04 | 97-113 | Only ACTIVE providers are reported and the status is hard-coded A, so terminated, suspended and denied providers never reach T-MSIS. Business question (Q-007). |
| R-PRVTMS01-ADD-05 | 67-115 | The 120-byte row carries only 30 meaningful bytes (5+9+10+1+5); the remaining 90 are spaces. Whether this matches the real CMS provider layout is not known (Q-007). |
| R-PRVTMS01-ADD-06 | 92-116 | READ and WRITE statuses are not tested and RETURN-CODE is never set. (D-024) |
| R-PRVTMS01-ADD-07 | 80-114 | The reporting period is the run date's Julian day, so the same month yields different values depending on the run date. (Q-007) |

Anything marked INFERRED was read from the code and not executed (no compiler or emulator was used).
