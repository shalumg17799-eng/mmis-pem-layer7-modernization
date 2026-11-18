      *================================================================*
      * COPYBOOK: WRKSTORE - STANDARD WORKING STORAGE                  *
      * ANTI-PATTERN: NESTED COPYBOOK                                  *
      *   WRKSTORE includes BOTH SCRNREC and ERRCODES. Any change to    *
      *   SCRNREC forces a recompile of every program that copies       *
      *   WRKSTORE, even programs that never touch screening records.   *
      *   CAST surfaces this as a hidden transitive dependency.         *
      *================================================================*
       01  WS-STANDARD-WORK.
           05  WS-PROGRAM-ID           PIC X(08).
           05  WS-END-OF-FILE-FLAG     PIC X(01) VALUE 'N'.
               88  WS-END-OF-FILE      VALUE 'Y'.
           05  WS-FILE-STATUS          PIC X(02) VALUE '00'.
               88  WS-FS-OK            VALUE '00'.
               88  WS-FS-EOF           VALUE '10'.
               88  WS-FS-DUP           VALUE '22'.
           05  WS-RECORDS-READ         PIC 9(07) VALUE ZERO.
           05  WS-RECORDS-WRITTEN      PIC 9(07) VALUE ZERO.
           05  WS-RECORDS-SKIPPED      PIC 9(07) VALUE ZERO.
           05  WS-RECORDS-ERRORED      PIC 9(07) VALUE ZERO.
      *    --- NESTED COPYBOOKS (the anti-pattern) ---
           COPY SCRNREC.
           COPY ERRCODES.
