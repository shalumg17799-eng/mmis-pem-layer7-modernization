# PRVENR01

Source: `source-code/BATCH/PRVENR01.cbl` (189 lines). Run by: PRVDAY01 STEP010 (seq 1).
Rules: 68 AWS rules (MISMATCH 17, VERIFIED 51) and 8 added; see `../rule-verification-PRVENR01.csv`.

## Purpose

Daily enrolment loader. Reads provider applications from APPINPUT, checks NPI, provider type (IN/GR/FA) and taxonomy, writes accepted applications to the provider master as PENDING / UNSCREENED, and writes rejected ones to APPERROR.

## Inputs and outputs

| File | DD | Open mode | Dataset (job step) | FD declares | Layout length |
|---|---|---|---|---|---|
| APP-INPUT-FILE | APPINPUT | INPUT | MMPRV.PROV.APPS.D&DATE (PRVDAY01 STEP010) | 200 | 200 |
| PRV-MASTER-FILE | PRVMSTR | I-O | MMPRV.PROVIDER.MASTER.KSDS (PRVDAY01 STEP010) | 121 | 130 (COPY PRVRECRD) |
| APP-ERROR-FILE | APPERROR | OUTPUT | MMPRV.PROV.ERRORS.D&DATE (PRVDAY01 STEP010) | 250 | 250 |

## Main flow

1. Initialise counters, open the three files, take the batch id from the environment variable BATCH_ID (default DEFBATCH), display the start message (85-100).
2. Read the next application INTO the 130-byte PRVRECRD work area (102-116).
3. Validate: NPI present (128), type IN/GR/FA (134-136), taxonomy present (141).
4. Valid: set PENDING and UNSCREENED (146-147) and write the master record (157-171).
5. Invalid: count it, write one error row (173-179), GO TO INVALID-APP, which falls through into the master write (AP-01).
6. End: close the files, display read/written/errors/skipped, RC 4 if any error (181-189).

## Decision points

- Read status: end of file, OK, or anything else (105-115).
- NPI blank (128).
- Type not IN, GR or FA (134-136).
- Taxonomy blank (141).
- Write status 00 written, 22 duplicate skipped, other error with RC 8 (160-171).
- Errors > 0 gives RC 4 (187).

## File-status handling

Input read status: 10 ends the loop, 00 continues, anything else is an error (RC 8) and the loop reads again. Master write: 00, 22 or other. OPEN statuses and the error-file write status are never tested.

## What happens on errors

A read error sets RC 8 but never ends the loop (possible endless loop, INFERRED). An invalid application is logged and then still written to the master (D-001). RC 4 at the end overwrites an earlier RC 8.

## Anti-patterns found

- AP-01 paragraph fall-through: GO TO INVALID-APP at 132, 139, 144; empty label at 155.

## Paragraphs

| Paragraph | Lines |
|---|---|
| MAIN-CONTROL | 79-83 |
| INITIALIZE-PROGRAM | 85-100 |
| PROCESS-APPLICATIONS | 102-116 |
| VALIDATE-APPLICATION | 127-148 |
| INVALID-APP | 155-155 |
| WRITE-PROVIDER-RECORD | 157-171 |
| WRITE-ERROR-RECORD | 173-179 |
| TERMINATE-PROGRAM | 181-189 |

## Rules AWS missed

| ID | Lines | Finding |
|---|---|---|
| R-PRVENR01-ADD-01 | 55-103 | The intake record is 200 bytes (APP-INPUT-RECORD X(200)) but is read INTO the 130-byte PRVRECRD work area, so bytes 131-200 of each intake record are ignored. The real intake layout is not defined anywhere in the estate. (D-025, Q-009) |
| R-PRVENR01-ADD-02 | 57-159 | The master FD declares 121 bytes but PRVRECRD is 130, so PRV-OWNER-NPI (bytes 121-130) would be lost on a 121-byte file (INFERRED effect). D-007: 130 is correct. |
| R-PRVENR01-ADD-03 | 81-116 | A persistent read error (status other than 00 or 10) never sets the end-of-file flag, so the PERFORM UNTIL loop would repeat forever (INFERRED, not executed). (D-024) |
| R-PRVENR01-ADD-04 | 160-166 | Duplicate detection happens only at WRITE (status 22). EC-DUP-NPI (E103) is never used (D-016). |
| R-PRVENR01-ADD-05 | 129-145 | Only the missing-NPI branch moves an error code (cut to 2 bytes); E102 and E104 are never assigned (D-016). |
| R-PRVENR01-ADD-06 | 75-179 | WS-ERR-DETAIL (X(250)) has no VALUE and STRING does not clear it, so bytes 41-250 of every error row are undefined (INFERRED). (D-016) |
| R-PRVENR01-ADD-07 | 114-189 | RC 4 at termination overwrites an earlier RC 8, so a hard error is reported to the job as a warning (D-024). |
| R-PRVENR01-ADD-08 | 109-166 | Counters are not exclusive: an invalid application is counted as read, as errored and then as written or skipped. (D-024) |

Anything marked INFERRED was read from the code and not executed (no compiler or emulator was used).
