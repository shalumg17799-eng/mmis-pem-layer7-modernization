# PRVDIR01

Source: `source-code/BATCH/PRVDIR01.cbl` (90 lines). Run by: no job in this estate.
Rules: 29 AWS rules (VERIFIED 29) and 5 added; see `../rule-verification-PRVDIR01.csv`.

## Purpose

Nightly provider directory (PEMAPP2). Writes one 100-byte row (Medicaid ID, NPI, type, risk tier, 60-byte name) for every ACTIVE provider for the MCOs.

## Inputs and outputs

| File | DD | Open mode | Dataset (job step) | FD declares | Layout length |
|---|---|---|---|---|---|
| PRV-MASTER-FILE | PRVMSTR | INPUT | no JCL defines this DD | 121 | 130 (COPY PRVRECRD) |
| DIR-OUT-FILE | DIROUT | OUTPUT | no JCL defines this DD | 100 | 100 |

## Main flow

1. Open the master INPUT and DIROUT OUTPUT (64-65).
2. Read each record (72-81); active gives WRITE-DIR-ROW (83-90).
3. End: close and display the count (68-69).

## Decision points

- End of file (75).
- Status ACTIVE (78).

## File-status handling

Only end of file is tested; OPEN and WRITE statuses are never tested.

## What happens on errors

None handled; RETURN-CODE is never set.

## Anti-patterns found

- AP-06 cross-application coupling (read side): PEMAPP2 reads PEMAPP1's master.

## Paragraphs

| Paragraph | Lines |
|---|---|
| MAIN-CONTROL | 62-70 |
| EXTRACT-NEXT | 72-81 |
| WRITE-DIR-ROW | 83-90 |

## Rules AWS missed

| ID | Lines | Finding |
|---|---|---|
| R-PRVDIR01-ADD-01 | 78-79 | The filter is only the active status. Screening result and overdue revalidation are not considered, so an active provider that failed screening or is overdue still appears in the directory. (Q-008) |
| R-PRVDIR01-ADD-02 | 88-88 | DIR-NAME is the first 60 bytes of PRV-VARIANT, which is not a display name: for an individual it is last name (30) + first name (20) + the first 10 characters of the licence number; for a group or facility it is the legal name (40) + the first 20 of the DBA name. No trimming or spacing. (D-028) |
| R-PRVDIR01-ADD-03 | 64-89 | OPEN statuses and WRITE status are never tested and RETURN-CODE is never set. (D-024) |
| R-PRVDIR01-ADD-04 | 47-88 | The directory row has no taxonomy or specialty, address, phone or network information, so it cannot feed a FHIR PractitionerRole (specialty = taxonomy) as it is (Q-008). |
| R-PRVDIR01-ADD-05 | 62-73 | AP-06: PEMAPP2 reads PEMAPP1's master directly with no API. D-013 FIX. |

Anything marked INFERRED was read from the code and not executed (no compiler or emulator was used).
