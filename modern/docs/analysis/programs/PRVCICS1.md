# PRVCICS1

Source: `source-code/ONLINE/PRVCICS1.cbl` (73 lines). Run by: no job in this estate.
Rules: 45 AWS rules (MISMATCH 14, NOT_FOUND 2, VERIFIED 29) and 7 added; see `../rule-verification-PRVCICS1.csv`.

## Purpose

CICS transaction PINQ: a state analyst looks up a provider by NPI and sees the enrolment status.

## Inputs and outputs

CICS: transaction `PINQ`, map `PRVMAP1` / mapset `PRVSET1`, file `PRVPROVN` (the provider master via its CICS name). The CSD and the BMS map are not in the estate.

## Main flow

1. A COMMAREA, if present, becomes WS-NPI-IN (29-31).
2. RECEIVE MAP PRVMAP1 / PRVSET1 (33-35).
3. READ FILE PRVPROVN by WS-NPI-IN into WS-PROVIDER-AREA (37-42).
4. EVALUATE the RESP (44-51): NORMAL gives FORMAT-PROVIDER (63-73), NOTFND and others set a message.
5. SEND MAP from WS-PROVIDER-AREA (53-56).
6. RETURN TRANSID PINQ with the NPI (58-61).

## Decision points

- EIBCALEN > 0 (29).
- READ RESP: NORMAL, NOTFND, other (44-50).
- Status A, T, P, otherwise DENIED (64-72).

## File-status handling

Only the READ RESP is tested; the RECEIVE, SEND and RETURN RESP are never tested (and RECEIVE's is overwritten).

## What happens on errors

Messages are set in WS-MSG but never sent; the typed NPI is never moved to the key. INFERRED: the BMS map and the CSD are not provided.

## Anti-patterns found

- No seeded anti-pattern; see the added rules for the inquiry defects (D-022, D-023).

## Paragraphs

| Paragraph | Lines |
|---|---|
| MAIN-LOGIC | 28-61 |
| FORMAT-PROVIDER | 63-73 |

## Rules AWS missed

| ID | Lines | Finding |
|---|---|---|
| R-PRVCICS1-ADD-01 | 47-72 | WS-MSG is set in every branch but SEND MAP passes only FROM(WS-PROVIDER-AREA). The status or error text never reaches the screen. INFERRED: the BMS map PRVSET1 is not provided, so it cannot be proven which fields the map shows. (D-022) |
| R-PRVCICS1-ADD-02 | 28-42 | The NPI typed on the screen is never moved to WS-NPI-IN (RECEIVE MAP has no INTO). The key is the previous NPI from the COMMAREA, or blank on a first call. INFERRED: the symbolic map copybook is not provided. (D-022) |
| R-PRVCICS1-ADD-03 | 33-41 | The RESP of RECEIVE MAP is overwritten by the READ's RESP in the same field and never tested; a MAPFAIL on first entry is ignored. The RESP of SEND and RETURN is not tested either. (D-022) |
| R-PRVCICS1-ADD-04 | 44-50 | Any RESP other than NORMAL or NOTFND (for example IOERR or NOTAUTH) gives 'VSAM READ ERROR', with no log and no abend. (D-022) |
| R-PRVCICS1-ADD-05 | 28-61 | There is no authorisation check (no ASSIGN USERID and no role test): any user of the transaction can read any provider. Security requirement for the new portal and console (RBAC). (D-023) |
| R-PRVCICS1-ADD-06 | 63-73 | DENIED is the catch-all label; the new SUSPENDED status (D-006) needs its own label. |
| R-PRVCICS1-ADD-07 | 25-61 | The COMMAREA is 10 bytes (the NPI). The inquiry is by NPI only; the Medicaid ID alternate index is not used by this transaction. (D-022) |

Anything marked INFERRED was read from the code and not executed (no compiler or emulator was used).
