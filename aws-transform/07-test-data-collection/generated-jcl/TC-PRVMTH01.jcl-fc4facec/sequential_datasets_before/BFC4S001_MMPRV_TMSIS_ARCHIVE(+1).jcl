//USERIDX JOB ACTINFO1,'SEQCOPY',CLASS=A,MSGCLASS=H,MSGLEVEL=(1,1), 
//             TIME=1440,NOTIFY=&SYSUID
//         EXPORT SYMLIST=(*)   //allows var usage instream 
//*
//* Instream procedure for transferring sequential files to s3
//*
//SEQCOPY PROC
//************************************************************
//S3COPY EXEC PGM=BPXBATCH
//************************************************************
//STDOUT  DD SYSOUT=*
//STDERR  DD SYSOUT=*
//STDIN   DD DUMMY
//STDENV  DD *,SYMBOLS=EXECSYS
SEQ_FILE=&FILENAME
S3_URI=&DESTPATH
PROFILE=&PROFILE
/*
//STDPARM DD *
SH
set -o errexit ;
mkdir -p $HOME/tmp;

cp -F bin -v "//'$SEQ_FILE'"
   $HOME/tmp/$SEQ_FILE ;

aws s3
  mv $HOME/tmp/$SEQ_FILE
  $S3_URI --profile $PROFILE ;

/*
//         PEND
//*
//STEP01   EXEC SEQCOPY,
//         FILENAME='MMPRV.TMSIS.ARCHIVE(+1)', 
//         DESTPATH='s3://DOC-EXAMPLE-BUCKET',
//         PROFILE='default'
//* ************************************************************
//* Sequential File to S3 Copy Procedure
//* Copies sequential dataset to S3 bucket
//*
//* FILENAME  - Sequential dataset name derived from the test plan
//* DESTPATH  - S3 destination URI
//* PROFILE   - AWS profile for authentication
//* ************************************************************
//
