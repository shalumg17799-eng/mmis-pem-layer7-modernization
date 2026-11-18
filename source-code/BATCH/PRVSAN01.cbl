      *================================================================*
      * PROGRAM:  PRVSAN01                                             *
      * PURPOSE:  SANCTIONS / TERMINATION PROCESSOR (PEM-6). APPLIES   *
      *           EXCLUSIONS AND TERMINATIONS TO PROVIDER RECORDS AND   *
      *           NOTIFIES DOWNSTREAM (CLAIMS, MCO).                    *
      * OWNED BY:  PEMAPP2 (Sanctions & Directory application)         *
      *                                                                *
      * ANTI-PATTERN 1: CROSS-APP VSAM COUPLING                      *
      *   PEMAPP2 opens PRVMSTR (PRVPROVN) - the Provider Master KSDS   *
      *   OWNED BY PEMAPP1 - directly via its DD statement and          *
      *   REWRITEs it. There is no API boundary. PEMAPP2 cannot be      *
      *   migrated to a cloud Sanctions service without either exposing *
      *   a Provider API from PEMAPP1 or migrating both at once. This   *
      *   is THE modernization blocker (see estate README).            *
      * ANTI-PATTERN 2: DYNAMIC CALL (TARGET IN A VARIABLE)          *
      *   The notice generator is invoked via CALL WS-NOTICE-PGM where  *
      *   the program name is built at runtime. CAST shows the call      *
      *   target as '?', breaking the call graph.                       *
      *================================================================*
       IDENTIFICATION DIVISION.
       PROGRAM-ID. PRVSAN01.
       AUTHOR. MMIS-SANCTIONS-TEAM.
       DATE-WRITTEN. 2001-05-18.

       ENVIRONMENT DIVISION.
       CONFIGURATION SECTION.
       SOURCE-COMPUTER. IBM-3090.
       OBJECT-COMPUTER. IBM-3090.

       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
      *    *** CROSS-APP: PEMAPP2 reaching into PEMAPP1's master ***
           SELECT PRV-MASTER-FILE
               ASSIGN TO PRVMSTR
               ORGANIZATION IS INDEXED
               ACCESS MODE IS DYNAMIC
               RECORD KEY IS PRV-NPI
               FILE STATUS IS WS-PRV-STATUS.

           SELECT SANCTION-IN-FILE
               ASSIGN TO SANCIN
               ORGANIZATION IS SEQUENTIAL
               ACCESS MODE IS SEQUENTIAL
               FILE STATUS IS WS-SAN-STATUS.

       DATA DIVISION.
       FILE SECTION.
       FD  PRV-MASTER-FILE
           RECORD CONTAINS 121 CHARACTERS.
       01  PRV-MASTER-RECORD.
           COPY PRVRECRD.

       FD  SANCTION-IN-FILE
           RECORDING MODE IS F
           RECORD CONTAINS 80 CHARACTERS.
       01  SANCTION-IN-RECORD.
           05  SAN-NPI                 PIC X(10).
           05  SAN-ACTION              PIC X(01).
               88  SAN-TERMINATE       VALUE 'T'.
               88  SAN-SUSPEND         VALUE 'S'.
               88  SAN-REINSTATE       VALUE 'R'.
           05  SAN-REASON              PIC X(40).
           05  SAN-EFF-DATE            PIC 9(08).
           05  FILLER                  PIC X(21).

       WORKING-STORAGE SECTION.
           COPY WRKSTORE.

       01  WS-PRV-STATUS               PIC X(2).
       01  WS-SAN-STATUS               PIC X(2).

       01  WS-NOTICE-CONTROL.
           05  WS-NOTICE-PREFIX        PIC X(05) VALUE 'PRVNT'.
           05  WS-NOTICE-SUFFIX        PIC X(03).
           05  WS-NOTICE-PGM           PIC X(08).
       01  WS-TERM-COUNT               PIC 9(05) VALUE ZERO.

       PROCEDURE DIVISION.
       MAIN-CONTROL.
           MOVE 'PRVSAN01' TO WS-PROGRAM-ID
           OPEN I-O    PRV-MASTER-FILE
           OPEN INPUT  SANCTION-IN-FILE
           MOVE 'N' TO WS-END-OF-FILE-FLAG
           PERFORM PROCESS-SANCTION UNTIL WS-END-OF-FILE
           CLOSE PRV-MASTER-FILE SANCTION-IN-FILE
           DISPLAY 'PRVSAN01: PROVIDERS TERMINATED=' WS-TERM-COUNT
           STOP RUN.

       PROCESS-SANCTION.
           READ SANCTION-IN-FILE
           MOVE WS-SAN-STATUS TO WS-FILE-STATUS
           IF WS-FS-EOF
               SET WS-END-OF-FILE TO TRUE
           ELSE
               MOVE SAN-NPI TO PRV-NPI OF PRV-MASTER-RECORD
               READ PRV-MASTER-FILE
               IF WS-PRV-STATUS = '00'
                   PERFORM APPLY-SANCTION
               ELSE
                   DISPLAY 'PRVSAN01: NPI NOT FOUND ' SAN-NPI
               END-IF
           END-IF.

       APPLY-SANCTION.
           EVALUATE TRUE
               WHEN SAN-TERMINATE
                   SET PRV-TERMINATED OF PRV-MASTER-RECORD TO TRUE
                   ADD 1 TO WS-TERM-COUNT
               WHEN SAN-SUSPEND
                   SET PRV-DENIED OF PRV-MASTER-RECORD TO TRUE
               WHEN SAN-REINSTATE
                   SET PRV-ACTIVE OF PRV-MASTER-RECORD TO TRUE
           END-EVALUATE
      *    *** cross-app REWRITE of PEMAPP1-owned master ***
           REWRITE PRV-MASTER-RECORD
           PERFORM GENERATE-NOTICE.

      *----------------------------------------------------------------*
      * GENERATE-NOTICE                                               *
      * ANTI-PATTERN: DYNAMIC CALL                                   *
      *   Program name assembled at runtime (PRVNT + action). CAST      *
      *   cannot resolve the CALL target - shows '?'.                   *
      *----------------------------------------------------------------*
       GENERATE-NOTICE.
           EVALUATE TRUE
               WHEN SAN-TERMINATE  MOVE 'TRM' TO WS-NOTICE-SUFFIX
               WHEN SAN-SUSPEND    MOVE 'SUS' TO WS-NOTICE-SUFFIX
               WHEN SAN-REINSTATE  MOVE 'RST' TO WS-NOTICE-SUFFIX
               WHEN OTHER          MOVE 'GEN' TO WS-NOTICE-SUFFIX
           END-EVALUATE
           STRING WS-NOTICE-PREFIX DELIMITED SIZE
                  WS-NOTICE-SUFFIX DELIMITED SIZE
               INTO WS-NOTICE-PGM
           CALL WS-NOTICE-PGM USING PRV-MASTER-RECORD
               ON EXCEPTION
                   DISPLAY 'PRVSAN01: NOTICE PGM NOT FOUND '
                           WS-NOTICE-PGM
           END-CALL.
