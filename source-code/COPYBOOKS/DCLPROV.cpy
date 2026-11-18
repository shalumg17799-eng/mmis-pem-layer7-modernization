      *================================================================*
      * COPYBOOK: DCLPROV - DB2 DCLGEN FOR THE PROVIDER TABLE          *
      *   Host-variable structure for embedded SQL against MMPRV.PROVIDER.
      *   (Reference only on TK5 — DB2 not installed; see DB2/PRVDDL01.sql)
      *================================================================*
           EXEC SQL DECLARE MMPRV.PROVIDER TABLE
           ( NPI              CHAR(10)    NOT NULL,
             MEDICAID_ID      CHAR(9)     NOT NULL,
             PROVIDER_TYPE    CHAR(2)     NOT NULL,
             TAXONOMY_CODE    CHAR(10),
             RISK_TIER        CHAR(1),
             ENROLL_STATUS    CHAR(1)     NOT NULL,
             EFFECTIVE_DATE   DATE        NOT NULL,
             REVAL_DUE_DATE   DATE,
             LEGAL_NAME       VARCHAR(60)
           ) END-EXEC.

       01  DCLPROVIDER.
           10  PRVT-NPI             PIC X(10).
           10  PRVT-MEDICAID-ID     PIC X(09).
           10  PRVT-PROVIDER-TYPE   PIC X(02).
           10  PRVT-TAXONOMY-CODE   PIC X(10).
           10  PRVT-RISK-TIER       PIC X(01).
           10  PRVT-ENROLL-STATUS   PIC X(01).
           10  PRVT-EFFECTIVE-DATE  PIC X(10).
           10  PRVT-REVAL-DUE-DATE  PIC X(10).
           10  PRVT-LEGAL-NAME.
               49 PRVT-LEGAL-NAME-LEN  PIC S9(4) COMP.
               49 PRVT-LEGAL-NAME-TEXT PIC X(60).
