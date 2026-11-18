      *================================================================*
      * PROGRAM:  PRVENR01                                             *
      * PURPOSE:  PROVIDER ENROLLMENT LOADER - READS PROVIDER          *
      *           APPLICATIONS FROM A SEQUENTIAL INTAKE FILE,          *
      *           VALIDATES, AND WRITES PENDING PROVIDER RECORDS TO    *
      *           THE PROVIDER MASTER KSDS (PEM-1).                    *
      * CALLED BY: PRVDAY01 JOB (STEP010)                             *
      *                                                                *
      * ANTI-PATTERN: PARAGRAPH FALL-THROUGH                          *
      *   VALIDATE-APPLICATION does NOT have EXIT or STOP RUN.         *
      *   When validation fails, control FALLS THROUGH the INVALID-APP *
      *   label into WRITE-PROVIDER-RECORD - so a provider that FAILED *
      *   validation is still written to the master as PENDING and can *
      *   be picked up by the auto-approve sweep. A provider enrolled  *
      *   despite failing prerequisites is a direct improper-payment   *
      *   exposure. Bug present since 1998.                            *
      *================================================================*
       IDENTIFICATION DIVISION.
       PROGRAM-ID. PRVENR01.
       AUTHOR. MMIS-PROVIDER-TEAM.
       DATE-WRITTEN. 1998-07-22.
       DATE-COMPILED.

       ENVIRONMENT DIVISION.
       CONFIGURATION SECTION.
       SOURCE-COMPUTER. IBM-3090.
       OBJECT-COMPUTER. IBM-3090.

       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT APP-INPUT-FILE
               ASSIGN TO APPINPUT
               ORGANIZATION IS SEQUENTIAL
               ACCESS MODE IS SEQUENTIAL
               FILE STATUS IS WS-APP-IN-STATUS.

           SELECT PRV-MASTER-FILE
               ASSIGN TO PRVMSTR
               ORGANIZATION IS INDEXED
               ACCESS MODE IS DYNAMIC
               RECORD KEY IS PRV-NPI
               FILE STATUS IS WS-PRV-OUT-STATUS.

           SELECT APP-ERROR-FILE
               ASSIGN TO APPERROR
               ORGANIZATION IS SEQUENTIAL
               ACCESS MODE IS SEQUENTIAL
               FILE STATUS IS WS-APP-ERR-STATUS.

       DATA DIVISION.
       FILE SECTION.
       FD  APP-INPUT-FILE
           RECORDING MODE IS F
           RECORD CONTAINS 200 CHARACTERS.
       01  APP-INPUT-RECORD            PIC X(200).

       FD  PRV-MASTER-FILE
           RECORD CONTAINS 121 CHARACTERS.
       01  PRV-MASTER-RECORD.
           COPY PRVRECRD.

       FD  APP-ERROR-FILE
           RECORDING MODE IS F
           RECORD CONTAINS 250 CHARACTERS.
       01  APP-ERROR-RECORD            PIC X(250).

       WORKING-STORAGE SECTION.
           COPY WRKSTORE.

       01  WS-APP-IN-STATUS            PIC X(2).
       01  WS-PRV-OUT-STATUS           PIC X(2).
       01  WS-APP-ERR-STATUS           PIC X(2).
       01  WS-PRV-WORK.
           COPY PRVRECRD.
       01  WS-ERR-DETAIL               PIC X(250).
       01  WS-BATCH-ID                 PIC X(8).

       PROCEDURE DIVISION.
       MAIN-CONTROL.
           PERFORM INITIALIZE-PROGRAM
           PERFORM PROCESS-APPLICATIONS UNTIL WS-END-OF-FILE
           PERFORM TERMINATE-PROGRAM
           STOP RUN.

       INITIALIZE-PROGRAM.
           MOVE 'PRVENR01'     TO WS-PROGRAM-ID
           MOVE 'N'            TO WS-END-OF-FILE-FLAG
           MOVE ZERO           TO WS-RECORDS-READ
                                  WS-RECORDS-WRITTEN
                                  WS-RECORDS-SKIPPED
                                  WS-RECORDS-ERRORED
           OPEN INPUT  APP-INPUT-FILE
           OPEN I-O    PRV-MASTER-FILE
           OPEN OUTPUT APP-ERROR-FILE
           ACCEPT WS-BATCH-ID FROM ENVIRONMENT 'BATCH_ID'
           IF WS-BATCH-ID = SPACES
               MOVE 'DEFBATCH' TO WS-BATCH-ID
           END-IF
           DISPLAY 'PRVENR01: STARTING ENROLLMENT LOAD - BATCH '
                   WS-BATCH-ID.

       PROCESS-APPLICATIONS.
           READ APP-INPUT-FILE INTO WS-PRV-WORK
           MOVE WS-APP-IN-STATUS TO WS-FILE-STATUS
           IF WS-FS-EOF
               SET WS-END-OF-FILE TO TRUE
           ELSE
               IF WS-FS-OK
                   ADD 1 TO WS-RECORDS-READ
                   PERFORM VALIDATE-APPLICATION
               ELSE
                   DISPLAY 'PRVENR01: READ ERROR ' WS-APP-IN-STATUS
                   ADD 1 TO WS-RECORDS-ERRORED
                   MOVE 8 TO RETURN-CODE
               END-IF
           END-IF.

      *----------------------------------------------------------------*
      * VALIDATE-APPLICATION                                           *
      * ANTI-PATTERN: PARAGRAPH FALL-THROUGH                          *
      *   Each failed check writes an error AND branches to INVALID-APP.
      *   But INVALID-APP has NO EXIT - control continues into          *
      *   WRITE-PROVIDER-RECORD, so the failed application is ALSO      *
      *   written to the provider master as PENDING. Two outcomes for   *
      *   one record; the bad provider is now enrollable.               *
      *----------------------------------------------------------------*
       VALIDATE-APPLICATION.
           IF PRV-NPI OF WS-PRV-WORK = SPACES
               MOVE EC-MISSING-NPI TO WS-FILE-STATUS
               ADD 1 TO WS-RECORDS-ERRORED
               PERFORM WRITE-ERROR-RECORD
               GO TO INVALID-APP
           END-IF
           IF NOT PRV-INDIVIDUAL OF WS-PRV-WORK
                              AND NOT PRV-GROUP OF WS-PRV-WORK
                              AND NOT PRV-FACILITY OF WS-PRV-WORK
               ADD 1 TO WS-RECORDS-ERRORED
               PERFORM WRITE-ERROR-RECORD
               GO TO INVALID-APP
           END-IF
           IF PRV-TAXONOMY OF WS-PRV-WORK = SPACES
               ADD 1 TO WS-RECORDS-ERRORED
               PERFORM WRITE-ERROR-RECORD
               GO TO INVALID-APP
           END-IF
           SET PRV-PENDING    OF WS-PRV-WORK TO TRUE
           SET PRV-UNSCREENED OF WS-PRV-WORK TO TRUE
           PERFORM WRITE-PROVIDER-RECORD.

      *----------------------------------------------------------------*
      * INVALID-APP - FALL-THROUGH TARGET (the bug)                   *
      *   *** MISSING: EXIT / GO TO PROCESS-APPLICATIONS ***          *
      *   Program counter continues into WRITE-PROVIDER-RECORD.        *
      *----------------------------------------------------------------*
       INVALID-APP.

       WRITE-PROVIDER-RECORD.
           MOVE WS-PRV-WORK TO PRV-MASTER-RECORD
           WRITE PRV-MASTER-RECORD
           EVALUATE WS-PRV-OUT-STATUS
               WHEN '00'
                   ADD 1 TO WS-RECORDS-WRITTEN
               WHEN '22'
                   DISPLAY 'PRVENR01: DUPLICATE NPI '
                           PRV-NPI OF WS-PRV-WORK
                   ADD 1 TO WS-RECORDS-SKIPPED
               WHEN OTHER
                   DISPLAY 'PRVENR01: WRITE ERROR ' WS-PRV-OUT-STATUS
                   ADD 1 TO WS-RECORDS-ERRORED
                   MOVE 8 TO RETURN-CODE
           END-EVALUATE.

       WRITE-ERROR-RECORD.
           STRING PRV-NPI OF WS-PRV-WORK DELIMITED SIZE
                  ' TYPE:' DELIMITED SIZE
                  PRV-TYPE-CODE OF WS-PRV-WORK DELIMITED SIZE
                  ' ERR:VALIDATION-FAILED' DELIMITED SIZE
               INTO WS-ERR-DETAIL
           WRITE APP-ERROR-RECORD FROM WS-ERR-DETAIL.

       TERMINATE-PROGRAM.
           CLOSE APP-INPUT-FILE PRV-MASTER-FILE APP-ERROR-FILE
           DISPLAY 'PRVENR01: READ='    WS-RECORDS-READ
           DISPLAY 'PRVENR01: WRITTEN=' WS-RECORDS-WRITTEN
           DISPLAY 'PRVENR01: ERRORS='  WS-RECORDS-ERRORED
           DISPLAY 'PRVENR01: SKIPPED=' WS-RECORDS-SKIPPED
           IF WS-RECORDS-ERRORED > ZEROS
               MOVE 4 TO RETURN-CODE
           END-IF.
