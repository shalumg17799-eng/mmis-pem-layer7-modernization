//PRVMTH01 JOB (MMIS,PROVIDER),'MONTH-END PROVIDER',
//             CLASS=A,MSGCLASS=X,MSGLEVEL=(1,1),
//             NOTIFY=&SYSUID,REGION=0M
//*
//* JOB:     PRVMTH01
//* PURPOSE: MONTH-END PROVIDER PROCESSING
//*          STEP010 - REVALIDATION SWEEP (PRVREV01) - 42 CFR 455.414
//*          STEP020 - T-MSIS PROVIDER FILE EXTRACT  (PRVTMS01)
//*          STEP030 - ARCHIVE T-MSIS FILE TO GDG GENERATION
//*
//JOBLIB   DD DSN=MMPRV.PROD.LOADLIB,DISP=SHR
//*
//*=================================================================*
//* STEP010 - REVALIDATION SWEEP                                    *
//*=================================================================*
//STEP010  EXEC PGM=PRVREV01
//PRVMSTR  DD DSN=MMPRV.PROVIDER.MASTER.KSDS,DISP=SHR
//REVALQ   DD DSN=MMPRV.REVAL.QUEUE.D&DATE,
//            DISP=(NEW,CATLG,DELETE),
//            UNIT=SYSDA,SPACE=(CYL,(5,2)),
//            DCB=(RECFM=FB,LRECL=80,BLKSIZE=8000)
//SYSOUT   DD SYSOUT=*
//*
//*=================================================================*
//* STEP020 - T-MSIS PROVIDER FILE EXTRACT                         *
//*=================================================================*
//STEP020  EXEC PGM=PRVTMS01
//PRVMSTR  DD DSN=MMPRV.PROVIDER.MASTER.KSDS,DISP=SHR
//TMSISOUT DD DSN=MMPRV.TMSIS.PROVIDER.D&DATE,
//            DISP=(NEW,CATLG,DELETE),
//            UNIT=SYSDA,SPACE=(CYL,(20,5)),
//            DCB=(RECFM=FB,LRECL=120,BLKSIZE=12000)
//SYSOUT   DD SYSOUT=*
//*
//*=================================================================*
//* STEP030 - ARCHIVE T-MSIS FILE TO GDG (+1 = NEW GENERATION)     *
//*=================================================================*
//STEP030  EXEC PGM=IEBGENER
//SYSUT1   DD DSN=MMPRV.TMSIS.PROVIDER.D&DATE,DISP=SHR
//SYSUT2   DD DSN=MMPRV.TMSIS.ARCHIVE(+1),
//            DISP=(NEW,CATLG,DELETE),
//            UNIT=TAPE,LABEL=(1,SL)
//SYSIN    DD DUMMY
//SYSPRINT DD SYSOUT=*
//SYSOUT   DD SYSOUT=*
