//PRVSCRPR PROC HLQ=MMPRV,
//             DTE=00000000,
//             RGN=0M
//*
//* CATALOGED PROCEDURE: PRVSCRPR - SCREENING RUN
//* ANTI-PATTERN: SYMBOLIC PARAMETER OVERRIDE
//*   The &HLQ / &DTE symbolics are overridden at three call sites
//*   (PRVDAY01, PRVMTH01, ad-hoc reruns) with different values. The
//*   effective dataset names differ per caller and are hard to trace
//*   statically - CAST shows the resolved DSN as parameterized.
//*
//SCREEN   EXEC PGM=PRVSCR01,REGION=&RGN
//STEPLIB  DD DSN=&HLQ..PROD.LOADLIB,DISP=SHR
//PRVMSTR  DD DSN=&HLQ..PROVIDER.MASTER.KSDS,DISP=SHR
//SCRNOUT  DD DSN=&HLQ..SCREEN.RESULTS.D&DTE,
//            DISP=(NEW,CATLG,DELETE),
//            UNIT=SYSDA,SPACE=(CYL,(10,5)),
//            DCB=(RECFM=FB,LRECL=67,BLKSIZE=6700)
//SYSOUT   DD SYSOUT=*
//         PEND
