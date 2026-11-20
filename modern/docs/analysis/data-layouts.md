# Data layouts

Every record layout in the legacy estate, with byte offsets computed by the parser in `modern/kag/loaders/parsers.py`
(tested in `modern/kag/tests/`) and meanings from AWS's data dictionary where it has one. Start is 1-based. Generated from the
source; do not edit by hand.

## The 121 / 130 issue (D-007: 130 is correct)

The provider master copybook `PRVRECRD` adds up to **130 bytes**: 10+9+2+10+1+1+1+8+8+80. Every batch FD that uses it declares
`RECORD CONTAINS 121 CHARACTERS` and the cluster is defined with `RECORDSIZE(121 121)` (`VSAM/PRVVSM01.jcl:17`). At 121 bytes the last
9 bytes of PRV-VARIANT would be lost, which for a group or facility is most of **PRV-OWNER-NPI (bytes 121-130)**. AWS's dictionary
also computes 130. The new provider-service stores every field; the legacy-format reader and writer use 130.

| FD record (program) | FD declares | Layout |
|---|---|---|
| `PRVDIR01:PRV-MASTER-RECORD` | 121 | COPY PRVRECRD = 130 bytes |
| `PRVENR01:PRV-MASTER-RECORD` | 121 | COPY PRVRECRD = 130 bytes |
| `PRVREV01:PRV-MASTER-RECORD` | 121 | COPY PRVRECRD = 130 bytes |
| `PRVRSK01:PRV-MASTER-RECORD` | 121 | COPY PRVRECRD = 130 bytes |
| `PRVSAN01:PRV-MASTER-RECORD` | 121 | COPY PRVRECRD = 130 bytes |
| `PRVSCR01:PRV-MASTER-RECORD` | 121 | COPY PRVRECRD = 130 bytes |
| `PRVSCR01:SCREEN-OUT-RECORD` | 67 | COPY SCRNREC = 67 bytes |
| `PRVTMS01:PRV-MASTER-RECORD` | 121 | COPY PRVRECRD = 130 bytes |

## Copybooks

### `DCLPROV:DCLPROVIDER` (115 bytes)

| Field | Level | PIC / type | Start | Length | Meaning |
|---|---|---|---|---|---|
| &nbsp;&nbsp;PRVT-NPI | 10 | X(10) | 1 | 10 | National Provider Identifier (NPI) for the provider (PIC X(10)) |
| &nbsp;&nbsp;PRVT-MEDICAID-ID | 10 | X(09) | 11 | 9 | Medicaid provider identification number (PIC X(09)) |
| &nbsp;&nbsp;PRVT-PROVIDER-TYPE | 10 | X(02) | 20 | 2 | Provider type classification code (PIC X(02)) |
| &nbsp;&nbsp;PRVT-TAXONOMY-CODE | 10 | X(10) | 22 | 10 | Healthcare provider taxonomy classification code (PIC X(10)) |
| &nbsp;&nbsp;PRVT-RISK-TIER | 10 | X(01) | 32 | 1 | Risk tier assignment level for the provider (PIC X(01)) |
| &nbsp;&nbsp;PRVT-ENROLL-STATUS | 10 | X(01) | 33 | 1 | Provider enrollment status indicator (PIC X(01)) |
| &nbsp;&nbsp;PRVT-EFFECTIVE-DATE | 10 | X(10) | 34 | 10 | Date when provider information becomes effective (PIC X(10)) |
| &nbsp;&nbsp;PRVT-REVAL-DUE-DATE | 10 | X(10) | 44 | 10 | Date when provider revaluation is due (PIC X(10)) |
| &nbsp;&nbsp;PRVT-LEGAL-NAME | 10 | (group) | 54 | 62 | Provider legal name group containing length and text fields |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;PRVT-LEGAL-NAME-LEN | 49 | S9(4) | 54 | 2 | Length of provider legal name text in bytes (PIC S9(4) COMP) |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;PRVT-LEGAL-NAME-TEXT | 49 | X(60) | 56 | 60 | Provider legal name text value (PIC X(60)) |

### `ERRCODES:ERROR-CODES` (40 bytes)

| Field | Level | PIC / type | Start | Length | Meaning |
|---|---|---|---|---|---|
| EC-OK | 5 | X(04) | 1 | 4 | Success status code indicating no errors (VALUE '0000') |
| EC-MISSING-NPI | 5 | X(04) | 5 | 4 | Error code for missing National Provider Identifier (VALUE 'E101') |
| EC-INVALID-TYPE | 5 | X(04) | 9 | 4 | Error code for invalid provider type (VALUE 'E102') |
| EC-DUP-NPI | 5 | X(04) | 13 | 4 | Error code for duplicate National Provider Identifier (VALUE 'E103') |
| EC-MISSING-TAXONOMY | 5 | X(04) | 17 | 4 | Error code for missing provider taxonomy code (VALUE 'E104') |
| EC-EXCLUDED | 5 | X(04) | 21 | 4 | Error code for excluded provider status (VALUE 'E201') |
| EC-LICENSE-EXPIRED | 5 | X(04) | 25 | 4 | Error code for expired professional license (VALUE 'E202') |
| EC-OWNER-EXCLUDED | 5 | X(04) | 29 | 4 | Error code for excluded provider owner (VALUE 'E203') |
| EC-DECEASED | 5 | X(04) | 33 | 4 | Error code for deceased provider (VALUE 'E204') |
| EC-DEBARRED | 5 | X(04) | 37 | 4 | Error code for debarred provider status (VALUE 'E205') |

### `PRVRECRD:PROVIDER-RECORD` (130 bytes) : REDEFINES across 3 provider types (AP-10)

PRV-VARIANT (bytes 51-130) has two views: individual (last name, first name, licence number, licence state) and
group / facility (legal name, DBA name, owner NPI). Which view applies depends on PRV-TYPE-CODE.

| Field | Level | PIC / type | Start | Length | Meaning |
|---|---|---|---|---|---|
| PRV-NPI | 5 | X(10) | 1 | 10 | National Provider Identifier - unique 10-character alphanumeric code assigned to healthcare providers |
| PRV-MEDICAID-ID | 5 | X(09) | 11 | 9 | State Medicaid provider identification number - 9-character alphanumeric identifier |
| PRV-TYPE-CODE | 5 | X(02) | 20 | 2 | Provider classification code - 2-character alphanumeric (X(02)) Values: PRV-INDIVIDUAL=IN, PRV-GROUP=GR, PRV-FACILITY=FA. |
| PRV-TAXONOMY | 5 | X(10) | 22 | 10 | Healthcare provider taxonomy code - 10-character alphanumeric classification code |
| PRV-RISK-TIER | 5 | X(01) | 32 | 1 | Risk assessment tier classification - 1-character alphanumeric (X(01)) Values: PRV-RISK-LIMITED=L, PRV-RISK-MODERATE=M, PRV-RISK-HIGH=H. |
| PRV-ENROLL-STATUS | 5 | X(01) | 33 | 1 | Provider enrollment status code - 1-character alphanumeric (X(01)) Values: PRV-PENDING=P, PRV-ACTIVE=A, PRV-TERMINATED=T, PRV-DENIED=D. |
| PRV-SCREEN-FLAG | 5 | X(01) | 34 | 1 | Provider screening result flag - 1-character alphanumeric (X(01)) Values: PRV-UNSCREENED=U, PRV-SCREEN-PASS=P, PRV-SCREEN-FAIL=F. |
| PRV-EFFECTIVE-DATE | 5 | 9(08) | 35 | 8 | Provider enrollment effective date - 8-digit numeric date (YYYYMMDD format) |
| PRV-REVAL-DUE-DATE | 5 | 9(08) | 43 | 8 | Provider re-evaluation due date - 8-digit numeric date (YYYYMMDD format) |
| PRV-VARIANT | 5 | X(80) | 51 | 80 | Variant data field containing provider-specific information - 80-character alphanumeric (REDEFINES PRV-VARIANT) |
| PRV-INDIVIDUAL-DATA | 5 | (group) | 51 | 80 | REDEFINES PRV-VARIANT. Individual provider demographic data group (REDEFINES PRV-VARIANT) |
| &nbsp;&nbsp;PRV-LAST-NAME | 10 | X(30) | 51 | 30 | Individual provider last name - 30-character alphanumeric |
| &nbsp;&nbsp;PRV-FIRST-NAME | 10 | X(20) | 81 | 20 | Individual provider first name - 20-character alphanumeric |
| &nbsp;&nbsp;PRV-LICENSE-NO | 10 | X(15) | 101 | 15 | Individual provider license number - 15-character alphanumeric |
| &nbsp;&nbsp;PRV-LICENSE-STATE | 10 | X(02) | 116 | 2 | Individual provider license issuing state code - 2-character alphanumeric |
| &nbsp;&nbsp;FILLER | 10 | X(13) | 118 | 13 |  |
| PRV-ORG-DATA | 5 | (group) | 51 | 80 | REDEFINES PRV-VARIANT. Organization provider data group (REDEFINES PRV-VARIANT) |
| &nbsp;&nbsp;PRV-LEGAL-NAME | 10 | X(40) | 51 | 40 | Organization legal business name - 40-character alphanumeric |
| &nbsp;&nbsp;PRV-DBA-NAME | 10 | X(30) | 91 | 30 | Organization doing business as name - 30-character alphanumeric |
| &nbsp;&nbsp;PRV-OWNER-NPI | 10 | X(10) | 121 | 10 | Organization owner National Provider Identifier - 10-character alphanumeric |

### `SCRNREC:SCREENING-RECORD` (67 bytes)

| Field | Level | PIC / type | Start | Length | Meaning |
|---|---|---|---|---|---|
| SCR-NPI | 5 | X(10) | 1 | 10 | National Provider Identifier number (10 characters) |
| SCR-SOURCE | 5 | X(08) | 11 | 8 | Source system code for screening data (8 characters) Values: SCR-NPPES=NPPES, SCR-LEIE=OIGLEIE, SCR-SAM=SAMGOV, SCR-LICENSE=LICBOARD, SCR-DMF=SSADMF, SCR-PECOS=PECOS. |
| SCR-RESULT | 5 | X(01) | 19 | 1 | Screening result code (1 character) Values: SCR-PASS=P, SCR-FAIL=F, SCR-COND=C. |
| SCR-VERDICT-DATE | 5 | 9(08) | 20 | 8 | Date of screening verdict in YYYYMMDD format (8 digits) |
| SCR-DETAIL | 5 | X(40) | 28 | 40 | Detailed screening result message or notes (40 characters) |

### `WRKSTORE:WS-STANDARD-WORK` (39 bytes)

Nests COPY SCRNREC, ERRCODES (AP-09).

| Field | Level | PIC / type | Start | Length | Meaning |
|---|---|---|---|---|---|
| WS-PROGRAM-ID | 5 | X(08) | 1 | 8 | Program identifier field (8 characters) |
| WS-END-OF-FILE-FLAG | 5 | X(01) | 9 | 1 | End-of-file indicator flag (VALUE 'N') Values: WS-END-OF-FILE=Y. |
| WS-FILE-STATUS | 5 | X(02) | 10 | 2 | File I/O status code (VALUE '00') Values: WS-FS-OK=00, WS-FS-EOF=10, WS-FS-DUP=22. |
| WS-RECORDS-READ | 5 | 9(07) | 12 | 7 | Count of records read from input file (7-digit numeric, VALUE ZERO) |
| WS-RECORDS-WRITTEN | 5 | 9(07) | 19 | 7 | Count of records written to output file (7-digit numeric, VALUE ZERO) |
| WS-RECORDS-SKIPPED | 5 | 9(07) | 26 | 7 | Count of records skipped during processing (7-digit numeric, VALUE ZERO) |
| WS-RECORDS-ERRORED | 5 | 9(07) | 33 | 7 | Count of records with processing errors (7-digit numeric, VALUE ZERO) |

## File records defined inside programs

APPINPUT (`PRVENR01:APP-INPUT-RECORD`) is a flat `PIC X(200)`: the program reads it INTO the 130-byte PRVRECRD layout, so bytes
1-130 are interpreted as a provider record and bytes 131-200 are ignored. No other intake layout exists in the estate (D-025, Q-009).

### `PRVDIR01:DIR-OUT-RECORD` (100 bytes, FD declares 100)

| Field | Level | PIC / type | Start | Length | Meaning |
|---|---|---|---|---|---|
| DIR-MEDICAID-ID | 5 | X(09) | 1 | 9 | Medicaid provider identifier field (9 characters) |
| DIR-NPI | 5 | X(10) | 10 | 10 | National Provider Identifier field (10 characters) |
| DIR-TYPE | 5 | X(02) | 20 | 2 | Provider type code field (2 characters) |
| DIR-RISK-TIER | 5 | X(01) | 22 | 1 | Risk tier classification code field (1 character) |
| DIR-NAME | 5 | X(60) | 23 | 60 | Provider name field (60 characters) |
| FILLER | 5 | X(18) | 83 | 18 |  |

### `PRVENR01:APP-ERROR-RECORD` (250 bytes, FD declares 250)

Elementary record: 250 bytes with no sub-fields.

### `PRVENR01:APP-INPUT-RECORD` (200 bytes, FD declares 200)

Elementary record: 200 bytes with no sub-fields.

### `PRVREV01:REVAL-OUT-RECORD` (80 bytes, FD declares 80)

| Field | Level | PIC / type | Start | Length | Meaning |
|---|---|---|---|---|---|
| REV-NPI | 5 | X(10) | 1 | 10 | National Provider Identifier number (10 characters) |
| REV-DUE-DATE | 5 | 9(08) | 11 | 8 | Revaluation due date in CCYYMMDD format (8 digits) |
| REV-ACTION | 5 | X(20) | 19 | 20 | Revaluation action code or description (20 characters) |
| FILLER | 5 | X(42) | 39 | 42 |  |

### `PRVSAN01:SANCTION-IN-RECORD` (80 bytes, FD declares 80)

| Field | Level | PIC / type | Start | Length | Meaning |
|---|---|---|---|---|---|
| SAN-NPI | 5 | X(10) | 1 | 10 | National Provider Identifier for sanctioned provider (10 characters) |
| SAN-ACTION | 5 | X(01) | 11 | 1 | Sanction action code indicating type of provider sanction (1 character) Values: SAN-TERMINATE=T, SAN-SUSPEND=S, SAN-REINSTATE=R. |
| SAN-REASON | 5 | X(40) | 12 | 40 | Reason description for sanction action (40 characters) |
| SAN-EFF-DATE | 5 | 9(08) | 52 | 8 | Effective date of sanction action in YYYYMMDD format (8 digits) |
| FILLER | 5 | X(21) | 60 | 21 |  |

### `PRVTMS01:TMSIS-OUT-RECORD` (120 bytes, FD declares 120)

Elementary record: 120 bytes with no sub-fields.

### `PRVTMS01:WS-TMSIS-DETAIL` (the T-MSIS row, moved to the 120-byte TMSIS-OUT-RECORD)

| Field | Level | PIC / type | Start | Length | Meaning |
|---|---|---|---|---|---|
| FILLER | 5 | X(05) | 1 | 5 |  |
| WS-T-PROVID | 5 | X(09) | 6 | 9 | Provider identifier code in TMSIS detail record (PIC X(09)) |
| WS-T-NPI | 5 | X(10) | 15 | 10 | National Provider Identifier in TMSIS detail record (PIC X(10)) |
| WS-T-STATUS | 5 | X(01) | 25 | 1 | Provider status indicator in TMSIS detail record (PIC X(01)) |
| WS-T-PERIOD-YYDDD | 5 | 9(05) | 26 | 5 | Period date in Julian YYDDD format in TMSIS detail record (PIC 9(05)) |
| FILLER | 5 | X(90) | 31 | 90 |  |

## DB2 tables (reference only: no program uses EXEC SQL; D-014)

`DCLPROV.cpy` (the DCLGEN, 115 bytes) omits SCREEN_FLAG and END_DATE, so it has drifted from the DDL. AWS's dictionary lists 22 of the
26 columns (it has only 1 of the 5 SANCTION columns).

### `MMPRV.PROVIDER`

| Field | Level | PIC / type | Start | Length | Meaning |
|---|---|---|---|---|---|
| NPI |  | CHAR(10) | col 1 | 10 | National Provider Identifier that uniquely identifies a healthcare provider |
| MEDICAID_ID |  | CHAR(9) | col 2 | 9 | Medicaid identifier assigned to the provider |
| PROVIDER_TYPE |  | CHAR(2) | col 3 | 2 | Classification of the provider's type of healthcare service |
| TAXONOMY_CODE |  | CHAR(10) | col 4 | 10 | Healthcare provider taxonomy code indicating provider specialization |
| RISK_TIER |  | CHAR(1) | col 5 | 1 | Risk classification tier assigned to the provider |
| ENROLL_STATUS |  | CHAR(1) | col 6 | 1 | Current enrollment status of the provider |
| SCREEN_FLAG |  | CHAR(1) | col 7 | 1 | Flag indicating whether the provider has been screened |
| EFFECTIVE_DATE |  | DATE | col 8 |  | Date when the provider record becomes effective |
| END_DATE |  | DATE | col 9 |  | Date when the provider record ends or is terminated |
| REVAL_DUE_DATE |  | DATE | col 10 |  | Date when the provider's re-evaluation is due |
| LEGAL_NAME |  | VARCHAR(60) | col 11 | 60 | Legal name of the provider |

### `MMPRV.PROVIDER_OWNER`

| Field | Level | PIC / type | Start | Length | Meaning |
|---|---|---|---|---|---|
| NPI |  | CHAR(10) | col 1 | 10 | National Provider Identifier linking to the provider record |
| OWNER_NPI |  | CHAR(10) | col 2 | 10 | National Provider Identifier of the owner entity |
| OWNER_NAME |  | VARCHAR(60) | col 3 | 60 | Legal name of the owner entity |
| OWNER_PCT |  | DECIMAL(5,2) | col 4 |  | Percentage ownership held by the owner entity |
| DISCLOSURE_DATE |  | DATE | col 5 |  | Date when the ownership disclosure was made |

### `MMPRV.SANCTION`

| Field | Level | PIC / type | Start | Length | Meaning |
|---|---|---|---|---|---|
| NPI |  | CHAR(10) | col 1 | 10 | National Provider Identifier linking to the provider record |
| ACTION |  | CHAR(1) | col 2 | 1 |  |
| REASON |  | VARCHAR(60) | col 3 | 60 |  |
| EFFECTIVE_DATE |  | DATE | col 4 |  |  |
| SOURCE_FEED |  | VARCHAR(20) | col 5 | 20 |  |

### `MMPRV.SCREENING`

| Field | Level | PIC / type | Start | Length | Meaning |
|---|---|---|---|---|---|
| NPI |  | CHAR(10) | col 1 | 10 | National Provider Identifier linking to the provider record |
| SOURCE |  | CHAR(8) | col 2 | 8 | Source system or organization that performed the screening |
| RESULT |  | CHAR(1) | col 3 | 1 | Result or outcome of the screening process |
| VERDICT_TS |  | TIMESTAMP | col 4 |  | Timestamp when the screening verdict was determined |
| DETAIL |  | VARCHAR(60) | col 5 | 60 | Detailed information or notes regarding the screening result |

