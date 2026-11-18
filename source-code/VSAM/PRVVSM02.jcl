//PRVVSM02 JOB (MMIS,VSAM),'DEFINE SCRN KSDS',
//             CLASS=A,MSGCLASS=X,NOTIFY=&SYSUID
//*
//* DEFINE THE SCREENING-RESULTS KSDS AND THE SANCTION KSDS.
//* SCREENING RESULTS ARE KEYED NPI + SOURCE SO MULTIPLE SOURCE
//* VERDICTS PER PROVIDER ARE RETAINED FOR THE AUDIT TRAIL.
//*
//DEFKSDS  EXEC PGM=IDCAMS
//SYSPRINT DD SYSOUT=*
//SYSIN    DD *
  DELETE MMPRV.SCREEN.RESULTS.KSDS CLUSTER PURGE
  DELETE MMPRV.SANCTION.MASTER.KSDS CLUSTER PURGE
  SET MAXCC = 0

  DEFINE CLUSTER ( NAME(MMPRV.SCREEN.RESULTS.KSDS) -
                   INDEXED                         -
                   KEYS(18 0)                      -
                   RECORDSIZE(67 67)               -
                   SHAREOPTIONS(2 3)               -
                   CYLINDERS(10 5) )               -
         DATA    ( NAME(MMPRV.SCREEN.RESULTS.DATA) ) -
         INDEX   ( NAME(MMPRV.SCREEN.RESULTS.INDEX) )

  DEFINE CLUSTER ( NAME(MMPRV.SANCTION.MASTER.KSDS) -
                   INDEXED                          -
                   KEYS(10 0)                       -
                   RECORDSIZE(80 80)                -
                   SHAREOPTIONS(2 3)                -
                   CYLINDERS(5 2) )                 -
         DATA    ( NAME(MMPRV.SANCTION.MASTER.DATA) ) -
         INDEX   ( NAME(MMPRV.SANCTION.MASTER.INDEX) )
/*
