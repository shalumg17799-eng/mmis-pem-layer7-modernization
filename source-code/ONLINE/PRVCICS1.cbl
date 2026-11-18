      *================================================================*
      * PROGRAM:  PRVCICS1   (CICS TRANSACTION: PINQ)                  *
      * PURPOSE:  ONLINE PROVIDER INQUIRY. STATE ANALYST KEYS AN NPI   *
      *           ON THE PINQ SCREEN; THIS READS THE PROVIDER MASTER   *
      *           VSAM FILE AND RETURNS ENROLLMENT STATUS / RISK TIER. *
      *           REFERENCE ONLY ON TK5 (NO CICS TS).                  *
      *                                                                *
      * NOTE: This is the "Oregon Trail" green-screen the modern        *
      *       provider self-service portal + analyst console replace.   *
      *================================================================*
       IDENTIFICATION DIVISION.
       PROGRAM-ID. PRVCICS1.
       AUTHOR. MMIS-ONLINE-TEAM.

       ENVIRONMENT DIVISION.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01  WS-RESP                     PIC S9(8) COMP.
       01  WS-NPI-IN                   PIC X(10).
       01  WS-MSG                      PIC X(40).
       01  WS-PROVIDER-AREA.
           COPY PRVRECRD.

       LINKAGE SECTION.
       01  DFHCOMMAREA                 PIC X(10).

       PROCEDURE DIVISION.
       MAIN-LOGIC.
           IF EIBCALEN > 0
               MOVE DFHCOMMAREA TO WS-NPI-IN
           END-IF

           EXEC CICS RECEIVE MAP('PRVMAP1') MAPSET('PRVSET1')
                RESP(WS-RESP)
           END-EXEC

           EXEC CICS READ
                FILE('PRVPROVN')
                INTO(WS-PROVIDER-AREA)
                RIDFLD(WS-NPI-IN)
                RESP(WS-RESP)
           END-EXEC

           EVALUATE WS-RESP
               WHEN DFHRESP(NORMAL)
                   PERFORM FORMAT-PROVIDER
               WHEN DFHRESP(NOTFND)
                   MOVE 'PROVIDER NOT FOUND' TO WS-MSG
               WHEN OTHER
                   MOVE 'VSAM READ ERROR'    TO WS-MSG
           END-EVALUATE

           EXEC CICS SEND MAP('PRVMAP1') MAPSET('PRVSET1')
                FROM(WS-PROVIDER-AREA)
                RESP(WS-RESP)
           END-EXEC

           EXEC CICS RETURN
                TRANSID('PINQ')
                COMMAREA(WS-NPI-IN)
           END-EXEC.

       FORMAT-PROVIDER.
           EVALUATE TRUE
               WHEN PRV-ACTIVE     OF WS-PROVIDER-AREA
                   MOVE 'ACTIVE'      TO WS-MSG
               WHEN PRV-TERMINATED OF WS-PROVIDER-AREA
                   MOVE 'TERMINATED'  TO WS-MSG
               WHEN PRV-PENDING    OF WS-PROVIDER-AREA
                   MOVE 'PENDING'     TO WS-MSG
               WHEN OTHER
                   MOVE 'DENIED'      TO WS-MSG
           END-EVALUATE.
