      *================================================================*
      * PROGRAM:  PRVDEAD1                                             *
      * PURPOSE:  (HISTORICAL) PAPER FINGERPRINT-CARD INTAKE FOR       *
      *           HIGH-RISK PROVIDERS. SUPERSEDED BY ELECTRONIC        *
      *           FINGERPRINTING (LIVESCAN VENDOR) IN 2019.            *
      *                                                                *
      * ANTI-PATTERN: DEAD CODE (ORPHANED + FLAG NEVER SET)          *
      *   1. No JCL in this estate references PRVDEAD1 - it is an        *
      *      orphaned node in the CAST call graph (nothing schedules it).*
      *   2. Even if run, WS-PAPER-FP-ENABLED is hardcoded 'N', so the   *
      *      PROCESS-PAPER-CARD path is doubly dead. The paper-card      *
      *      workflow was discontinued when the state moved to LiveScan. *
      *   It survives only because no one dared delete it. CAST flags    *
      *   both the orphan and the unreachable paragraph.                *
      *================================================================*
       IDENTIFICATION DIVISION.
       PROGRAM-ID. PRVDEAD1.
       AUTHOR. MMIS-PROVIDER-TEAM.
       DATE-WRITTEN. 2011-04-12.

       ENVIRONMENT DIVISION.
       CONFIGURATION SECTION.
       SOURCE-COMPUTER. IBM-3090.
       OBJECT-COMPUTER. IBM-3090.

       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01  WS-CONTROL.
           05  WS-PAPER-FP-ENABLED     PIC X(01) VALUE 'N'.
               88  WS-PAPER-FP-ON      VALUE 'Y'.
           05  WS-CARD-COUNT           PIC 9(05) VALUE ZERO.

       PROCEDURE DIVISION.
       MAIN-CONTROL.
           DISPLAY 'PRVDEAD1: PAPER FINGERPRINT INTAKE (LEGACY)'
      *    *** Always false - WS-PAPER-FP-ENABLED is permanently 'N' ***
           IF WS-PAPER-FP-ON
               PERFORM PROCESS-PAPER-CARD
           ELSE
               DISPLAY 'PRVDEAD1: PAPER PROCESS DISABLED - USE LIVESCAN'
           END-IF
           STOP RUN.

      *----------------------------------------------------------------*
      * PROCESS-PAPER-CARD - UNREACHABLE                             *
      *   Discontinued 2019. Never reached since the flag is 'N'.      *
      *----------------------------------------------------------------*
       PROCESS-PAPER-CARD.
           DISPLAY 'PRVDEAD1: LOGGING PAPER FP CARD TO MAILROOM QUEUE'
           ADD 1 TO WS-CARD-COUNT
           DISPLAY 'PRVDEAD1: CARDS QUEUED=' WS-CARD-COUNT.
