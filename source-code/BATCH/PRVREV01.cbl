      *================================================================*
      * PROGRAM:  PRVREV01                                             *
      * PURPOSE:  REVALIDATION SWEEP (PEM-4). FINDS PROVIDERS WHOSE    *
      *           REVALIDATION DUE DATE HAS PASSED (42 CFR 455.414,    *
      *           <= 5 YEARS) AND QUEUES THEM FOR RE-SCREENING.        *
      * CALLED BY: PRVMTH01 JOB (STEP010)                            *
      *                                                                *
      * NOTE: This is point-in-time, month-end only. It inherits the    *
      *   2-digit-year due-date defect from PRVRSK01 (a provider whose  *
      *   due year wrapped past 2070 mis-sorts here too). The modern     *
      *   revalidation service replaces this with CONTINUOUS monitoring  *
      *   (re-screen the day a new LEIE/licence event arrives, not at    *
      *   the next month-end batch).                                    *
      *================================================================*
       IDENTIFICATION DIVISION.
       PROGRAM-ID. PRVREV01.
       AUTHOR. MMIS-PROVIDER-TEAM.
       DATE-WRITTEN. 2000-06-15.

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

           SELECT REVAL-OUT-FILE
               ASSIGN TO REVALQ
               ORGANIZATION IS SEQUENTIAL
               FILE STATUS IS WS-REV-STATUS.

       DATA DIVISION.
       FILE SECTION.
       FD  PRV-MASTER-FILE
           RECORD CONTAINS 121 CHARACTERS.
       01  PRV-MASTER-RECORD.
           COPY PRVRECRD.

       FD  REVAL-OUT-FILE
           RECORDING MODE IS F
           RECORD CONTAINS 80 CHARACTERS.
       01  REVAL-OUT-RECORD.
           05  REV-NPI                 PIC X(10).
           05  REV-DUE-DATE            PIC 9(08).
           05  REV-ACTION              PIC X(20).
           05  FILLER                  PIC X(42).

       WORKING-STORAGE SECTION.
           COPY WRKSTORE.
       01  WS-PRV-STATUS               PIC X(2).
       01  WS-REV-STATUS               PIC X(2).
       01  WS-TODAY                    PIC 9(08).
       01  WS-REVAL-COUNT              PIC 9(07) VALUE ZERO.

       PROCEDURE DIVISION.
       MAIN-CONTROL.
           MOVE 'PRVREV01' TO WS-PROGRAM-ID
           ACCEPT WS-TODAY FROM DATE YYYYMMDD
           OPEN INPUT  PRV-MASTER-FILE
           OPEN OUTPUT REVAL-OUT-FILE
           MOVE 'N' TO WS-END-OF-FILE-FLAG
           PERFORM CHECK-NEXT UNTIL WS-END-OF-FILE
           CLOSE PRV-MASTER-FILE REVAL-OUT-FILE
           DISPLAY 'PRVREV01: PROVIDERS QUEUED FOR REVAL='
                   WS-REVAL-COUNT
           STOP RUN.

       CHECK-NEXT.
           READ PRV-MASTER-FILE NEXT RECORD
           MOVE WS-PRV-STATUS TO WS-FILE-STATUS
           IF WS-FS-EOF
               SET WS-END-OF-FILE TO TRUE
           ELSE
               IF PRV-ACTIVE OF PRV-MASTER-RECORD
                  AND PRV-REVAL-DUE-DATE OF PRV-MASTER-RECORD
                                                  <= WS-TODAY
                   PERFORM QUEUE-FOR-REVAL
               END-IF
           END-IF.

       QUEUE-FOR-REVAL.
           MOVE PRV-NPI OF PRV-MASTER-RECORD          TO REV-NPI
           MOVE PRV-REVAL-DUE-DATE OF PRV-MASTER-RECORD
                                                      TO REV-DUE-DATE
           MOVE 'RESCREEN-REQUIRED'                   TO REV-ACTION
           WRITE REVAL-OUT-RECORD
           ADD 1 TO WS-REVAL-COUNT.
