      *================================================================*
      * PROGRAM:  PRVRSK01                                             *
      * PURPOSE:  RISK-BASED SCREENING CLASSIFIER (PEM-1/PEM-2).       *
      *           ASSIGNS EACH PROVIDER A SCREENING RISK TIER          *
      *           (LIMITED / MODERATE / HIGH) PER 42 CFR 455.450 AND   *
      *           SETS THE NEXT REVALIDATION DUE DATE (42 CFR 455.414).*
      * CALLED BY: PRVDAY01 JOB (STEP040)                             *
      *                                                                *
      * ANTI-PATTERN 1: ALTER STATEMENT                              *
      *   APPLY-TIER-PARA's GO TO target is rewritten at runtime via    *
      *   ALTER (limited vs high-risk routing). The branch destination  *
      *   is invisible to static analysis - CAST cannot resolve it.     *
      * ANTI-PATTERN 2: 2-DIGIT YEAR (YY) ARITHMETIC                 *
      *   Revalidation due date is computed as WS-YY + 5 with a < 70    *
      *   pivot. A provider revalidated in 2068 gets a due year of 73   *
      *   which the pivot reads as 1973 - the record sorts as decades   *
      *   overdue. Fails for any 5-year window crossing 2070.           *
      *================================================================*
       IDENTIFICATION DIVISION.
       PROGRAM-ID. PRVRSK01.
       AUTHOR. MMIS-PROVIDER-TEAM.
       DATE-WRITTEN. 1999-11-03.

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

       DATA DIVISION.
       FILE SECTION.
       FD  PRV-MASTER-FILE
           RECORD CONTAINS 121 CHARACTERS.
       01  PRV-MASTER-RECORD.
           COPY PRVRECRD.

       WORKING-STORAGE SECTION.
           COPY WRKSTORE.

       01  WS-PRV-STATUS               PIC X(2).

       01  WS-DATE-WORK.
           05  WS-CURR-DATE            PIC 9(08).
           05  WS-CURR-YY              PIC 9(02).
           05  WS-CURR-MMDD            PIC 9(04).
           05  WS-DUE-YY               PIC 9(02).
           05  WS-DUE-CCYY             PIC 9(04).
           05  WS-DUE-DATE             PIC 9(08).

       01  WS-TAXONOMY-GROUP           PIC X(03).

       PROCEDURE DIVISION.
       MAIN-CONTROL.
           MOVE 'PRVRSK01' TO WS-PROGRAM-ID
           OPEN I-O PRV-MASTER-FILE
           MOVE 'N' TO WS-END-OF-FILE-FLAG
           PERFORM CLASSIFY-NEXT UNTIL WS-END-OF-FILE
           CLOSE PRV-MASTER-FILE
           STOP RUN.

       CLASSIFY-NEXT.
           READ PRV-MASTER-FILE NEXT RECORD
           MOVE WS-PRV-STATUS TO WS-FILE-STATUS
           IF WS-FS-EOF
               SET WS-END-OF-FILE TO TRUE
           ELSE
               PERFORM CLASSIFY-ONE
           END-IF.

       CLASSIFY-ONE.
           MOVE PRV-TAXONOMY OF PRV-MASTER-RECORD (1:3)
                                              TO WS-TAXONOMY-GROUP

      *    *** ALTER: choose the tier-routing target at runtime ***
           EVALUATE WS-TAXONOMY-GROUP
               WHEN '332'
               WHEN '251'
                   ALTER APPLY-TIER-PARA TO PROCEED TO HIGH-TIER
               WHEN '341'
               WHEN '291'
               WHEN '225'
                   ALTER APPLY-TIER-PARA TO PROCEED TO MODERATE-TIER
               WHEN OTHER
                   ALTER APPLY-TIER-PARA TO PROCEED TO LIMITED-TIER
           END-EVALUATE

           PERFORM APPLY-TIER-PARA
           PERFORM SET-REVALIDATION-DATE
           REWRITE PRV-MASTER-RECORD.

      *----------------------------------------------------------------*
      * APPLY-TIER-PARA - ALTER target rewritten above (the anti-     *
      * pattern). Static analysis sees GO TO LIMITED-TIER only.       *
      *----------------------------------------------------------------*
       APPLY-TIER-PARA.
           GO TO LIMITED-TIER.

       LIMITED-TIER.
           SET PRV-RISK-LIMITED OF PRV-MASTER-RECORD TO TRUE
           GO TO TIER-DONE.
       MODERATE-TIER.
           SET PRV-RISK-MODERATE OF PRV-MASTER-RECORD TO TRUE
           GO TO TIER-DONE.
       HIGH-TIER.
           SET PRV-RISK-HIGH OF PRV-MASTER-RECORD TO TRUE
           GO TO TIER-DONE.
       TIER-DONE.
           EXIT.

      *----------------------------------------------------------------*
      * SET-REVALIDATION-DATE                                         *
      * ANTI-PATTERN: 2-DIGIT YEAR ARITHMETIC                        *
      *   Adds 5 years using a 2-digit year and a < 70 century pivot.  *
      *   Correct until 2065+; a 2068 enrollment yields due-year 73,   *
      *   pivoted to 1973 - the provider looks 90+ years overdue and    *
      *   the revalidation sweep mis-sorts it.                          *
      *----------------------------------------------------------------*
       SET-REVALIDATION-DATE.
           ACCEPT WS-CURR-DATE FROM DATE YYYYMMDD
           MOVE WS-CURR-DATE (3:2) TO WS-CURR-YY
           MOVE WS-CURR-DATE (5:4) TO WS-CURR-MMDD
           COMPUTE WS-DUE-YY = FUNCTION MOD (WS-CURR-YY + 5, 100)
           IF WS-DUE-YY < 70
               COMPUTE WS-DUE-CCYY = 2000 + WS-DUE-YY
           ELSE
               COMPUTE WS-DUE-CCYY = 1900 + WS-DUE-YY
           END-IF
           COMPUTE WS-DUE-DATE = (WS-DUE-CCYY * 10000) + WS-CURR-MMDD
           MOVE WS-DUE-DATE TO PRV-REVAL-DUE-DATE OF PRV-MASTER-RECORD.
