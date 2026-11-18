//PRVVSM01 JOB (MMIS,VSAM),'DEFINE PROV KSDS',
//             CLASS=A,MSGCLASS=X,NOTIFY=&SYSUID
//*
//* DEFINE THE PROVIDER MASTER KSDS (OWNED BY PEMAPP1) PLUS AN
//* ALTERNATE INDEX ON MEDICAID PROVIDER ID. THE BASE CLUSTER NPI KEY
//* IS THE PRIMARY ENTERPRISE PROVIDER KEY.
//*
//DEFKSDS  EXEC PGM=IDCAMS
//SYSPRINT DD SYSOUT=*
//SYSIN    DD *
  DELETE MMPRV.PROVIDER.MASTER.KSDS CLUSTER PURGE
  SET MAXCC = 0

  DEFINE CLUSTER ( NAME(MMPRV.PROVIDER.MASTER.KSDS) -
                   INDEXED                          -
                   KEYS(10 0)                       -
                   RECORDSIZE(121 121)              -
                   SHAREOPTIONS(2 3)                -
                   CYLINDERS(10 5) )                -
         DATA    ( NAME(MMPRV.PROVIDER.MASTER.DATA) ) -
         INDEX   ( NAME(MMPRV.PROVIDER.MASTER.INDEX) )

  DEFINE ALTERNATEINDEX ( NAME(MMPRV.PROVIDER.MASTER.AIX) -
                   RELATE(MMPRV.PROVIDER.MASTER.KSDS)      -
                   KEYS(9 10)                              -
                   NONUNIQUEKEY                            -
                   RECORDSIZE(30 60)                       -
                   CYLINDERS(2 1) )                        -
         DATA    ( NAME(MMPRV.PROVIDER.MASTER.AIX.DATA) )  -
         INDEX   ( NAME(MMPRV.PROVIDER.MASTER.AIX.INDEX) )

  DEFINE PATH ( NAME(MMPRV.PROVIDER.MASTER.PATH) -
                PATHENTRY(MMPRV.PROVIDER.MASTER.AIX) )
/*
