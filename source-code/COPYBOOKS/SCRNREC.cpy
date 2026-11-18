      *================================================================*
      * COPYBOOK: SCRNREC - SCREENING / VERIFICATION RESULT RECORD     *
      *   One row per external source queried during screening, with   *
      *   a timestamped verdict (the audit trail required by           *
      *   42 CFR 455 Subpart E).                                       *
      *================================================================*
       01  SCREENING-RECORD.
           05  SCR-NPI                 PIC X(10).
           05  SCR-SOURCE              PIC X(08).
               88  SCR-NPPES           VALUE 'NPPES'.
               88  SCR-LEIE            VALUE 'OIGLEIE'.
               88  SCR-SAM             VALUE 'SAMGOV'.
               88  SCR-LICENSE         VALUE 'LICBOARD'.
               88  SCR-DMF             VALUE 'SSADMF'.
               88  SCR-PECOS           VALUE 'PECOS'.
           05  SCR-RESULT              PIC X(01).
               88  SCR-PASS            VALUE 'P'.
               88  SCR-FAIL            VALUE 'F'.
               88  SCR-COND            VALUE 'C'.
           05  SCR-VERDICT-DATE        PIC 9(08).
           05  SCR-DETAIL              PIC X(40).
