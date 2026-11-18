//PRVDAY01 JOB (MMIS,PROVIDER),'DAILY ENROLLMENT',
//             CLASS=A,MSGCLASS=X,MSGLEVEL=(1,1),
//             NOTIFY=&SYSUID,
//             REGION=0M
//*
//* JOB:     PRVDAY01
//* PURPOSE: DAILY PROVIDER ENROLLMENT PIPELINE
//*          STEP010 - LOAD PROVIDER APPLICATIONS    (PRVENR01)
//*          STEP020 - SORT PENDING PROVIDERS BY NPI
//*          STEP030 - SCREEN PROVIDERS              (PRVSCR01)
//*          STEP040 - RISK-TIER CLASSIFICATION      (PRVRSK01)
//*          CONDITIONAL: STEPS 020-040 ONLY IF STEP010 RC=0
//*
//JOBLIB   DD DSN=MMPRV.PROD.LOADLIB,DISP=SHR
//*
//*=================================================================*
//* STEP010 - ENROLLMENT LOADER                                     *
//*=================================================================*
//STEP010  EXEC PGM=PRVENR01,
//             PARM='BATCH_ID=&BATCH_DATE'
//STEPLIB  DD DSN=MMPRV.PROD.LOADLIB,DISP=SHR
//APPINPUT DD DSN=MMPRV.PROV.APPS.D&DATE,
//            DISP=(OLD,KEEP,KEEP),
//            UNIT=SYSDA
//PRVMSTR  DD DSN=MMPRV.PROVIDER.MASTER.KSDS,
//            DISP=SHR
//APPERROR DD DSN=MMPRV.PROV.ERRORS.D&DATE,
//            DISP=(NEW,CATLG,DELETE),
//            UNIT=SYSDA,
//            SPACE=(CYL,(5,2)),
//            DCB=(RECFM=FB,LRECL=250,BLKSIZE=25000)
//SYSOUT   DD SYSOUT=*
//SYSPRINT DD SYSOUT=*
//*
//*=================================================================*
//* CONDITIONAL: SKIP 020-040 IF STEP010 FAILED                    *
//*=================================================================*
//         IF (STEP010.RC = 0) THEN
//*
//*=================================================================*
//* STEP020 - DFSORT: SORT PENDING PROVIDERS BY NPI                *
//*=================================================================*
//STEP020  EXEC PGM=SORT
//SORTIN   DD DSN=MMPRV.PROVIDER.MASTER.KSDS,DISP=SHR
//SORTOUT  DD DSN=&&PRVSORT,
//            DISP=(NEW,PASS,DELETE),
//            UNIT=SYSDA,SPACE=(CYL,(20,5))
//SYSIN    DD *
  SORT FIELDS=(1,10,CH,A)            COL  1-10  NPI
  INCLUDE COND=(23,1,CH,EQ,C'U')     PRV-SCREEN-FLAG = 'U' (UNSCREENED)
//SYSOUT   DD SYSOUT=*
//SYSPRINT DD SYSOUT=*
//*
//*=================================================================*
//* STEP030 - SCREENING & VERIFICATION ENGINE                      *
//*=================================================================*
//STEP030  EXEC PGM=PRVSCR01
//PRVMSTR  DD DSN=MMPRV.PROVIDER.MASTER.KSDS,DISP=SHR
//SCRNOUT  DD DSN=MMPRV.SCREEN.RESULTS.D&DATE,
//            DISP=(NEW,CATLG,DELETE),
//            UNIT=SYSDA,SPACE=(CYL,(10,5)),
//            DCB=(RECFM=FB,LRECL=67,BLKSIZE=6700)
//SYSOUT   DD SYSOUT=*
//*
//*=================================================================*
//* STEP040 - RISK-TIER CLASSIFICATION + REVALIDATION DUE DATE     *
//*=================================================================*
//STEP040  EXEC PGM=PRVRSK01
//PRVMSTR  DD DSN=MMPRV.PROVIDER.MASTER.KSDS,DISP=SHR
//SYSOUT   DD SYSOUT=*
//*
//         ENDIF
//*
//STEP099  EXEC PGM=IEFBR14,COND=(0,LT)
//SYSOUT   DD SYSOUT=*
