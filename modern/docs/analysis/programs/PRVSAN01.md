# PRVSAN01

Source: `source-code/BATCH/PRVSAN01.cbl` (138 lines). Run by: no job in this estate.
Rules: 58 AWS rules (NOT_FOUND 1, VERIFIED 57) and 7 added; see `../rule-verification-PRVSAN01.csv`.

## Purpose

Sanctions (PEMAPP2). Applies terminate / suspend / reinstate actions from SANCIN to the provider master that PEMAPP1 owns, rewrites it, and calls a notice program per action.

## Inputs and outputs

| File | DD | Open mode | Dataset (job step) | FD declares | Layout length |
|---|---|---|---|---|---|
| PRV-MASTER-FILE | PRVMSTR | I-O | no JCL defines this DD | 121 | 130 (COPY PRVRECRD) |
| SANCTION-IN-FILE | SANCIN | INPUT | no JCL defines this DD | 80 | 80 |

## Main flow

1. Open the master I-O and SANCIN INPUT (81-82).
2. Read each sanction (89-102) and read the master by NPI.
3. Found: APPLY-SANCTION (104-116): T gives TERMINATED (+count), S gives DENIED, R gives ACTIVE; REWRITE; GENERATE-NOTICE (124-138) builds PRVNT+TRM/SUS/RST/GEN and CALLs it dynamically.
4. Not found: display 'NPI NOT FOUND' (100).
5. End: close and display the terminated count (85-86).

## Decision points

- End of file (92).
- Master read status 00 (97).
- Action T, S or R (105-113); any other code changes nothing but is still rewritten.
- Notice suffix (125-130).
- CALL ON EXCEPTION (135).

## File-status handling

Sanction read: end of file only. Master read: 00 or anything else (reported as not found). REWRITE status never tested.

## What happens on errors

A missing notice program is caught by ON EXCEPTION and processing continues; RETURN-CODE is never set.

## Anti-patterns found

- AP-05 dynamic CALL: 134.
- AP-06 cross-application VSAM coupling: ASSIGN at 34, REWRITE at 115.

## Paragraphs

| Paragraph | Lines |
|---|---|
| MAIN-CONTROL | 79-87 |
| PROCESS-SANCTION | 89-102 |
| APPLY-SANCTION | 104-116 |
| GENERATE-NOTICE | 124-138 |

## Rules AWS missed

| ID | Lines | Finding |
|---|---|---|
| R-PRVSAN01-ADD-01 | 90-101 | Any READ status other than 00 on the master (including I/O errors) prints 'NPI NOT FOUND', and any sanction-file read status other than EOF is not handled. (D-024) |
| R-PRVSAN01-ADD-02 | 104-129 | An action code that is not T, S or R is not rejected: no status changes, but the master is REWRITTEN and PRVNTGEN is called. (D-026) |
| R-PRVSAN01-ADD-03 | 57-116 | SAN-REASON and SAN-EFF-DATE are never used: no sanction history is kept anywhere (the SANCTION KSDS and the DB2 table are unused). D-018 FIX. |
| R-PRVSAN01-ADD-04 | 107-112 | A terminate or reinstate is applied whatever the current status or screening result is: a reinstate can make a DENIED or PENDING provider ACTIVE. Business question (Q-006). (D-027) |
| R-PRVSAN01-ADD-05 | 115-115 | The REWRITE status is not tested. (D-024) |
| R-PRVSAN01-ADD-06 | 80-87 | Not-found NPIs are not counted and RETURN-CODE is never set. (D-024) |
| R-PRVSAN01-ADD-07 | 81-115 | AP-06: PEMAPP2 opens PEMAPP1's master I-O and rewrites it with no API. D-013 FIX. |

Anything marked INFERRED was read from the code and not executed (no compiler or emulator was used).
