      *================================================================*
      * PROGRAM:  PRVSCR01                                             *
      * PURPOSE:  SCREENING & VERIFICATION ENGINE (PEM-2). FOR EACH    *
      *           PENDING PROVIDER, QUERIES EXTERNAL SOURCES (LICENSE, *
      *           OIG LEIE, SAM, SSA-DMF) AND POSTS A SCREENING VERDICT.*
      * CALLED BY: PRVDAY01 JOB (STEP030)                             *
      *                                                                *
      * ANTI-PATTERN: DEAD CODE (FLAG NEVER SET)                      *
      *   WS-RECIPROCITY-ENABLED is initialized to 'N' and never        *
      *   changed anywhere in the estate. The CHECK-MEDICARE-RECIPROCITY*
      *   paragraph - which would let the state rely on PECOS screening *
      *   (42 CFR 455.410) and skip duplicate work - is therefore NEVER *
      *   reached. The reciprocity feature was specified in 2014 but     *
      *   the enabling flag was never wired up. CAST flags the dead path.*
      *================================================================*
       IDENTIFICATION DIVISION.
       PROGRAM-ID. PRVSCR01.
       AUTHOR. MMIS-PROVIDER-TEAM.
       DATE-WRITTEN. 2003-02-10.

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

           SELECT SCREEN-OUT-FILE
               ASSIGN TO SCRNOUT
               ORGANIZATION IS SEQUENTIAL
               ACCESS MODE IS SEQUENTIAL
               FILE STATUS IS WS-SCR-STATUS.

       DATA DIVISION.
       FILE SECTION.
       FD  PRV-MASTER-FILE
           RECORD CONTAINS 121 CHARACTERS.
       01  PRV-MASTER-RECORD.
           COPY PRVRECRD.

       FD  SCREEN-OUT-FILE
           RECORDING MODE IS F
           RECORD CONTAINS 67 CHARACTERS.
       01  SCREEN-OUT-RECORD.
           COPY SCRNREC.

       WORKING-STORAGE SECTION.
           COPY WRKSTORE.

       01  WS-PRV-STATUS               PIC X(2).
       01  WS-SCR-STATUS               PIC X(2).

       01  WS-CONTROL-FLAGS.
           05  WS-RECIPROCITY-ENABLED  PIC X(01) VALUE 'N'.
               88  WS-RECIPROCITY-ON   VALUE 'Y'.
           05  WS-FAIL-COUNT           PIC 9(03) VALUE ZERO.

      *    Simulated external-source verdicts (real estate calls
      *    licensure board, OIG LEIE, SAM, SSA-DMF via MQ / FTP).
       01  WS-EXT-RESULTS.
           05  WS-LICENSE-RESULT       PIC X(01).
           05  WS-LEIE-RESULT          PIC X(01).
           05  WS-SAM-RESULT           PIC X(01).
           05  WS-DMF-RESULT           PIC X(01).

       PROCEDURE DIVISION.
       MAIN-CONTROL.
           MOVE 'PRVSCR01' TO WS-PROGRAM-ID
           OPEN I-O    PRV-MASTER-FILE
           OPEN OUTPUT SCREEN-OUT-FILE
           MOVE 'N' TO WS-END-OF-FILE-FLAG
           PERFORM SCREEN-NEXT-PROVIDER UNTIL WS-END-OF-FILE
           CLOSE PRV-MASTER-FILE SCREEN-OUT-FILE
           DISPLAY 'PRVSCR01: SCREENING FAILURES=' WS-FAIL-COUNT
           STOP RUN.

       SCREEN-NEXT-PROVIDER.
           READ PRV-MASTER-FILE NEXT RECORD
           MOVE WS-PRV-STATUS TO WS-FILE-STATUS
           IF WS-FS-EOF
               SET WS-END-OF-FILE TO TRUE
           ELSE
               IF PRV-UNSCREENED OF PRV-MASTER-RECORD
                   PERFORM SCREEN-ONE-PROVIDER
               END-IF
           END-IF.

       SCREEN-ONE-PROVIDER.
      *    *** DEAD CODE: this guard is always false (flag hardcoded N) ***
           IF WS-RECIPROCITY-ON
               PERFORM CHECK-MEDICARE-RECIPROCITY
           END-IF

           PERFORM CHECK-LICENSE
           PERFORM CHECK-LEIE-EXCLUSION
           PERFORM CHECK-SAM-DEBARMENT
           PERFORM CHECK-SSA-DMF

           IF WS-LICENSE-RESULT = 'P' AND WS-LEIE-RESULT = 'P'
                              AND WS-SAM-RESULT = 'P'
                              AND WS-DMF-RESULT = 'P'
               SET PRV-SCREEN-PASS OF PRV-MASTER-RECORD TO TRUE
           ELSE
               SET PRV-SCREEN-FAIL OF PRV-MASTER-RECORD TO TRUE
               ADD 1 TO WS-FAIL-COUNT
           END-IF
           REWRITE PRV-MASTER-RECORD.

       CHECK-LICENSE.
      *    Real program issues a CALL/MQ to the state license board.
           MOVE 'P' TO WS-LICENSE-RESULT
           PERFORM WRITE-SCREEN-ROW.

       CHECK-LEIE-EXCLUSION.
      *    Real program matches NPI + 5%+ owners against OIG LEIE.
           MOVE 'P' TO WS-LEIE-RESULT
           PERFORM WRITE-SCREEN-ROW.

       CHECK-SAM-DEBARMENT.
           MOVE 'P' TO WS-SAM-RESULT
           PERFORM WRITE-SCREEN-ROW.

       CHECK-SSA-DMF.
           MOVE 'P' TO WS-DMF-RESULT
           PERFORM WRITE-SCREEN-ROW.

      *----------------------------------------------------------------*
      * CHECK-MEDICARE-RECIPROCITY - DEAD CODE                        *
      *   Never reached: WS-RECIPROCITY-ENABLED is permanently 'N'.    *
      *   Would query PECOS to accept Medicare's screening and skip     *
      *   duplicate state checks. 10+ years of dead investment.         *
      *----------------------------------------------------------------*
       CHECK-MEDICARE-RECIPROCITY.
           DISPLAY 'PRVSCR01: RELYING ON MEDICARE/PECOS SCREENING'
           SET SCR-PECOS OF SCREEN-OUT-RECORD TO TRUE
           MOVE 'P' TO WS-LICENSE-RESULT WS-LEIE-RESULT
                       WS-SAM-RESULT WS-DMF-RESULT.

       WRITE-SCREEN-ROW.
           MOVE PRV-NPI OF PRV-MASTER-RECORD TO SCR-NPI
           SET  SCR-PASS TO TRUE
           WRITE SCREEN-OUT-RECORD.
