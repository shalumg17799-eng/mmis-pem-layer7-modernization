      *================================================================*
      * PROGRAM:  PRVTMS01                                             *
      * PURPOSE:  T-MSIS PROVIDER FILE EXTRACT (PEM-10). EXTRACTS       *
      *           ACTIVE PROVIDERS INTO THE MONTHLY CMS T-MSIS          *
      *           PROVIDER SUBMISSION FILE.                            *
      * CALLED BY: PRVMTH01 JOB                                       *
      *                                                                *
      * ANTI-PATTERN: YYDDD JULIAN DATE ARITHMETIC                    *
      *   Enrollment age and submission period use packed YYDDD Julian  *
      *   dates with a 2-digit year. Cross-year comparisons (e.g. a     *
      *   provider enrolled 99365 vs reporting period 00001) sort wrong *
      *   because 99 > 00 numerically. T-MSIS period math breaks across *
      *   the century and any year boundary. A known source of T-MSIS   *
      *   data-quality rejects.                                         *
      *================================================================*
       IDENTIFICATION DIVISION.
       PROGRAM-ID. PRVTMS01.
       AUTHOR. MMIS-PROVIDER-TEAM.
       DATE-WRITTEN. 2014-09-01.

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

           SELECT TMSIS-OUT-FILE
               ASSIGN TO TMSISOUT
               ORGANIZATION IS SEQUENTIAL
               FILE STATUS IS WS-TMS-STATUS.

       DATA DIVISION.
       FILE SECTION.
       FD  PRV-MASTER-FILE
           RECORD CONTAINS 121 CHARACTERS.
       01  PRV-MASTER-RECORD.
           COPY PRVRECRD.

       FD  TMSIS-OUT-FILE
           RECORDING MODE IS F
           RECORD CONTAINS 120 CHARACTERS.
       01  TMSIS-OUT-RECORD            PIC X(120).

       WORKING-STORAGE SECTION.
           COPY WRKSTORE.

       01  WS-PRV-STATUS               PIC X(2).
       01  WS-TMS-STATUS               PIC X(2).

      *    *** YYDDD 2-digit-year Julian dates (the anti-pattern) ***
       01  WS-JULIAN-WORK.
           05  WS-TODAY-JUL            PIC 9(05).
           05  WS-TODAY-YY             PIC 9(02).
           05  WS-TODAY-DDD            PIC 9(03).
           05  WS-ENROLL-JUL           PIC 9(05).
           05  WS-ENROLL-YY            PIC 9(02).
           05  WS-AGE-DAYS             PIC S9(05).

       01  WS-TMSIS-DETAIL.
           05  FILLER                  PIC X(05) VALUE 'PRV01'.
           05  WS-T-PROVID             PIC X(09).
           05  WS-T-NPI                PIC X(10).
           05  WS-T-STATUS             PIC X(01).
           05  WS-T-PERIOD-YYDDD       PIC 9(05).
           05  FILLER                  PIC X(90) VALUE SPACES.

       01  WS-OUT-COUNT                PIC 9(07) VALUE ZERO.

       PROCEDURE DIVISION.
       MAIN-CONTROL.
           MOVE 'PRVTMS01' TO WS-PROGRAM-ID
           ACCEPT WS-TODAY-JUL FROM DAY
           MOVE WS-TODAY-JUL (1:2) TO WS-TODAY-YY
           MOVE WS-TODAY-JUL (3:3) TO WS-TODAY-DDD
           OPEN INPUT  PRV-MASTER-FILE
           OPEN OUTPUT TMSIS-OUT-FILE
           MOVE 'N' TO WS-END-OF-FILE-FLAG
           PERFORM EXTRACT-NEXT UNTIL WS-END-OF-FILE
           CLOSE PRV-MASTER-FILE TMSIS-OUT-FILE
           DISPLAY 'PRVTMS01: T-MSIS PROVIDER ROWS=' WS-OUT-COUNT
           STOP RUN.

       EXTRACT-NEXT.
           READ PRV-MASTER-FILE NEXT RECORD
           MOVE WS-PRV-STATUS TO WS-FILE-STATUS
           IF WS-FS-EOF
               SET WS-END-OF-FILE TO TRUE
           ELSE
               IF PRV-ACTIVE OF PRV-MASTER-RECORD
                   PERFORM WRITE-TMSIS-ROW
               END-IF
           END-IF.

       WRITE-TMSIS-ROW.
      *    Derive enrollment Julian YY from CCYYMMDD effective date.
           MOVE PRV-EFFECTIVE-DATE OF PRV-MASTER-RECORD (3:2)
                                              TO WS-ENROLL-YY
      *    *** Broken: compares 2-digit YY directly across century ***
           IF WS-ENROLL-YY > WS-TODAY-YY
               DISPLAY 'PRVTMS01: FUTURE ENROLLMENT? NPI '
                       PRV-NPI OF PRV-MASTER-RECORD
           END-IF
           MOVE PRV-MEDICAID-ID OF PRV-MASTER-RECORD TO WS-T-PROVID
           MOVE PRV-NPI OF PRV-MASTER-RECORD         TO WS-T-NPI
           MOVE 'A'                                  TO WS-T-STATUS
           MOVE WS-TODAY-JUL                         TO WS-T-PERIOD-YYDDD
           MOVE WS-TMSIS-DETAIL                      TO TMSIS-OUT-RECORD
           WRITE TMSIS-OUT-RECORD
           ADD 1 TO WS-OUT-COUNT.
