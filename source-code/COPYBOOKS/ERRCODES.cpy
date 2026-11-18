      *================================================================*
      * COPYBOOK: ERRCODES - SHARED ENROLLMENT/SCREENING ERROR CODES   *
      *================================================================*
       01  ERROR-CODES.
           05  EC-OK                   PIC X(04) VALUE '0000'.
           05  EC-MISSING-NPI          PIC X(04) VALUE 'E101'.
           05  EC-INVALID-TYPE         PIC X(04) VALUE 'E102'.
           05  EC-DUP-NPI              PIC X(04) VALUE 'E103'.
           05  EC-MISSING-TAXONOMY     PIC X(04) VALUE 'E104'.
           05  EC-EXCLUDED             PIC X(04) VALUE 'E201'.
           05  EC-LICENSE-EXPIRED      PIC X(04) VALUE 'E202'.
           05  EC-OWNER-EXCLUDED       PIC X(04) VALUE 'E203'.
           05  EC-DECEASED             PIC X(04) VALUE 'E204'.
           05  EC-DEBARRED             PIC X(04) VALUE 'E205'.
