      *================================================================*
      * PROGRAM:  PRVDIR01                                             *
      * PURPOSE:  PROVIDER DIRECTORY EXTRACT (PEM-7). READS THE        *
      *           PROVIDER MASTER AND WRITES ACTIVE PROVIDERS TO THE   *
      *           NIGHTLY DIRECTORY FLAT FILE CONSUMED BY MCOs.        *
      * OWNED BY:  PEMAPP2 (Sanctions & Directory application)         *
      *                                                                *
      * ANTI-PATTERN: CROSS-APP VSAM COUPLING (read side)            *
      *   Like PRVSAN01, PEMAPP2 reads PEMAPP1's Provider Master KSDS  *
      *   directly. The legacy directory is a batch flat file - the     *
      *   modern target is a live FHIR PractitionerRole API.           *
      *================================================================*
       IDENTIFICATION DIVISION.
       PROGRAM-ID. PRVDIR01.
       AUTHOR. MMIS-DIRECTORY-TEAM.
       DATE-WRITTEN. 2005-08-30.

       ENVIRONMENT DIVISION.
       CONFIGURATION SECTION.
       SOURCE-COMPUTER. IBM-3090.
       OBJECT-COMPUTER. IBM-3090.

       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT PRV-MASTER-FILE
               ASSIGN TO PRVMSTR
               ORGANIZATION IS INDEXED
               ACCESS MODE IS DYNAMIC
               RECORD KEY IS PRV-NPI
               FILE STATUS IS WS-PRV-STATUS.

           SELECT DIR-OUT-FILE
               ASSIGN TO DIROUT
               ORGANIZATION IS SEQUENTIAL
               FILE STATUS IS WS-DIR-STATUS.

       DATA DIVISION.
       FILE SECTION.
       FD  PRV-MASTER-FILE
           RECORD CONTAINS 121 CHARACTERS.
       01  PRV-MASTER-RECORD.
           COPY PRVRECRD.

       FD  DIR-OUT-FILE
           RECORDING MODE IS F
           RECORD CONTAINS 100 CHARACTERS.
       01  DIR-OUT-RECORD.
           05  DIR-MEDICAID-ID         PIC X(09).
           05  DIR-NPI                 PIC X(10).
           05  DIR-TYPE                PIC X(02).
           05  DIR-RISK-TIER           PIC X(01).
           05  DIR-NAME                PIC X(60).
           05  FILLER                  PIC X(18).

       WORKING-STORAGE SECTION.
           COPY WRKSTORE.
       01  WS-PRV-STATUS               PIC X(2).
       01  WS-DIR-STATUS               PIC X(2).
       01  WS-DIR-COUNT                PIC 9(07) VALUE ZERO.

       PROCEDURE DIVISION.
       MAIN-CONTROL.
           MOVE 'PRVDIR01' TO WS-PROGRAM-ID
           OPEN INPUT  PRV-MASTER-FILE
           OPEN OUTPUT DIR-OUT-FILE
           MOVE 'N' TO WS-END-OF-FILE-FLAG
           PERFORM EXTRACT-NEXT UNTIL WS-END-OF-FILE
           CLOSE PRV-MASTER-FILE DIR-OUT-FILE
           DISPLAY 'PRVDIR01: DIRECTORY ROWS=' WS-DIR-COUNT
           STOP RUN.

       EXTRACT-NEXT.
           READ PRV-MASTER-FILE NEXT RECORD
           MOVE WS-PRV-STATUS TO WS-FILE-STATUS
           IF WS-FS-EOF
               SET WS-END-OF-FILE TO TRUE
           ELSE
               IF PRV-ACTIVE OF PRV-MASTER-RECORD
                   PERFORM WRITE-DIR-ROW
               END-IF
           END-IF.

       WRITE-DIR-ROW.
           MOVE PRV-MEDICAID-ID OF PRV-MASTER-RECORD TO DIR-MEDICAID-ID
           MOVE PRV-NPI         OF PRV-MASTER-RECORD TO DIR-NPI
           MOVE PRV-TYPE-CODE   OF PRV-MASTER-RECORD TO DIR-TYPE
           MOVE PRV-RISK-TIER   OF PRV-MASTER-RECORD TO DIR-RISK-TIER
           MOVE PRV-VARIANT     OF PRV-MASTER-RECORD (1:60) TO DIR-NAME
           WRITE DIR-OUT-RECORD
           ADD 1 TO WS-DIR-COUNT.
