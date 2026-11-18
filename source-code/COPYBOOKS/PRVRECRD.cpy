      *================================================================*
      * COPYBOOK: PRVRECRD - PROVIDER MASTER RECORD                    *
      * ANTI-PATTERN: REDEFINES ACROSS 3 PROVIDER TYPES               *
      *   Individual / Group / Facility records redefine the same      *
      *   80-byte PRV-VARIANT area. 6+ programs share this structure;  *
      *   changing one variant silently affects the others. CAST flags  *
      *   the object-level coupling across all PRVRECRD users.          *
      *================================================================*
       01  PROVIDER-RECORD.
           05  PRV-NPI                 PIC X(10).
           05  PRV-MEDICAID-ID         PIC X(09).
           05  PRV-TYPE-CODE           PIC X(02).
               88  PRV-INDIVIDUAL      VALUE 'IN'.
               88  PRV-GROUP           VALUE 'GR'.
               88  PRV-FACILITY        VALUE 'FA'.
           05  PRV-TAXONOMY            PIC X(10).
           05  PRV-RISK-TIER           PIC X(01).
               88  PRV-RISK-LIMITED    VALUE 'L'.
               88  PRV-RISK-MODERATE   VALUE 'M'.
               88  PRV-RISK-HIGH       VALUE 'H'.
           05  PRV-ENROLL-STATUS       PIC X(01).
               88  PRV-PENDING         VALUE 'P'.
               88  PRV-ACTIVE          VALUE 'A'.
               88  PRV-TERMINATED      VALUE 'T'.
               88  PRV-DENIED          VALUE 'D'.
           05  PRV-SCREEN-FLAG         PIC X(01).
               88  PRV-UNSCREENED      VALUE 'U'.
               88  PRV-SCREEN-PASS     VALUE 'P'.
               88  PRV-SCREEN-FAIL     VALUE 'F'.
           05  PRV-EFFECTIVE-DATE      PIC 9(08).
           05  PRV-REVAL-DUE-DATE      PIC 9(08).
           05  PRV-VARIANT             PIC X(80).
      *    --- Individual provider view of PRV-VARIANT ---
           05  PRV-INDIVIDUAL-DATA REDEFINES PRV-VARIANT.
               10  PRV-LAST-NAME       PIC X(30).
               10  PRV-FIRST-NAME      PIC X(20).
               10  PRV-LICENSE-NO      PIC X(15).
               10  PRV-LICENSE-STATE   PIC X(02).
               10  FILLER              PIC X(13).
      *    --- Group / Facility view of the SAME 80 bytes ---
           05  PRV-ORG-DATA REDEFINES PRV-VARIANT.
               10  PRV-LEGAL-NAME      PIC X(40).
               10  PRV-DBA-NAME        PIC X(30).
               10  PRV-OWNER-NPI       PIC X(10).
