//USERIDV JOB 'VSAM REPRO WITH DYNAMIC LRECL',CLASS=A,MSGCLASS=0,
//         TIME=1440,NOTIFY=&SYSUID
//         EXPORT SYMLIST=(*)       //ALLOWS INSTREAM VAR USAGE
//* INSTREAM PROCEDURE FOR VSAM REPRO WITH DYNAMIC LRECL DETECTION
//*
//VSAMPROC PROC 
//* *******************************************************************
//* Dynamic VSAM to PS repro with automatic LRECL detection
//* *******************************************************************
//STEP1    EXEC PGM=IEBGENER
//SYSPRINT DD SYSOUT=*
//SYSUT1   DD *,DLM=@@,SYMBOLS=EXECSYS
/* REXX - Dynamic LRECL Detection and Flat File Allocation */
/* This REXX procedure automatically detects VSAM LRECL and */
/* allocates the output flat file with matching attributes  */
parse source . . . . . . . env .
if env = 'TSO' then do
  address TSO
  'PROFILE NOPREFIX'
end
/* Set dataset names from JCL parameters */
dsname = '&VSAMNAME'
flatfile = '&VSAMNAME..PS'
/* Capture LISTCAT output for DATA component only */
x = OUTTRAP('OUT.')
"LISTCAT ENTRIES('"dsname"') DATA ALL"
listcat_rc = rc
x = OUTTRAP('OFF')
if listcat_rc <> 0 then do
  say 'LISTCAT failed for' dsname 'RC=' listcat_rc
  exit listcat_rc
end
/* Parse LISTCAT output for MAXLRECL, AVGLRECL and SPACE */
avglrecl = 0
maxlrecl = 0
space_type = ''
space_pri = 0
space_sec = 0
do i = 1 to out.0
  if pos('AVGLRECL',out.i) > 0 then do
    pos1 = pos('AVGLRECL',out.i) + 8
    temp = substr(out.i,pos1)
    parse var temp '-' avglrecl ' ' .
    avglrecl = strip(avglrecl,'B','-')
    say 'DETECTED AVGLRECL:' avglrecl
  end
  if pos('MAXLRECL',out.i) > 0 then do
    pos1 = pos('MAXLRECL',out.i) + 8
    temp = substr(out.i,pos1)
    parse var temp '-' maxlrecl ' ' .
    maxlrecl = strip(maxlrecl,'B','-')
    say 'DETECTED MAXLRECL:' maxlrecl
  end
  if pos('SPACE-TYPE',out.i) > 0 then do
    pos1 = pos('SPACE-TYPE',out.i) + 10
    temp = substr(out.i,pos1)
    parse var temp '-' space_type ' ' .
    space_type = strip(space_type,'B','-')
    say 'DETECTED SPACE-TYPE:' space_type
  end
  if pos('SPACE-PRI',out.i) > 0 then do
    pos1 = pos('SPACE-PRI',out.i) + 9
    temp = substr(out.i,pos1)
    parse var temp '-' space_pri ' ' .
    space_pri = strip(space_pri,'B','-')
    say 'DETECTED SPACE-PRI:' space_pri
  end
  if pos('SPACE-SEC',out.i) > 0 then do
    pos1 = pos('SPACE-SEC',out.i) + 9
    temp = substr(out.i,pos1)
    parse var temp '-' space_sec ' ' .
    space_sec = strip(space_sec,'B','-')
    say 'DETECTED SPACE-SEC:' space_sec
  end
end
/* Determine record format and allocate */
if maxlrecl > 0 then do
  if avglrecl = maxlrecl then do
    recfm = 'F,B'
    lrecl = maxlrecl
  end
  else do
    recfm = 'V,B'
    lrecl = maxlrecl + 4  /* Add 4 bytes for RDW */
  end
  say 'USING LRECL:' lrecl 'RECFM:' recfm
  call allocate_flat_file flatfile, lrecl, recfm, space_type,,
                          space_pri, space_sec
end
exit rc
/* Subroutine to allocate flat file with detected params */
allocate_flat_file:
  parse arg dsn, lrecl, recfm, stype, spri, ssec
  /* Check if output dataset already exists */
  "LISTCAT ENTRIES('"dsn"')"
  if rc = 0 then do
    say 'Output dataset exists, deleting...'
    "DELETE '"dsn"'"
    if rc <> 0 then do
      say 'Delete failed, RC=' rc
      exit rc
    end
  end
  /* Build space allocation based on detected values */
  if stype = 'CYLINDER' & spri > 0 then do
    space_parm = "SPACE("spri","ssec") CYLINDERS"
  end
  else do
    space_parm = "SPACE(50,10) TRACKS"
  end
  /* Allocate new flat file with detected parameters */
  "ALLOC DA('"dsn"') NEW CATALOG",
  space_parm" LRECL("lrecl") RECFM("recfm")"
  if rc = 0 then
    say 'Flat file allocated successfully with LRECL=' lrecl,
        'RECFM=' recfm 'SPACE=' space_parm
  else do
    say 'Allocation failed, RC=' rc
    exit rc
  end
return
@@
//* Create temporary PDS member for REXX procedure
//SYSUT2   DD DSN=&&TEMP(PROCVSAM),DISP=(NEW,PASS),
//         UNIT=SYSDA,SPACE=(TRK,(1,1,1)),
//         DCB=RECFM=FB
//SYSIN    DD DSN=NULLFILE
//* STEP 2: Execute REXX procedure for LRECL detection
//STEP2    EXEC PGM=IKJEFT01
//SYSPROC  DD DSN=&&TEMP,DISP=(OLD,DELETE)
//SYSTSPRT DD SYSOUT=*
//SYSTSIN  DD *
%PROCVSAM
/*
//* STEP 3: REPRO VSAM dataset to flat file
//STEP3    EXEC PGM=IDCAMS
//SYSPRINT DD SYSOUT=*
//INVS     DD DISP=SHR,
//         DSN=&VSAMNAME
//OUTFL    DD DISP=SHR,
//         DSN=&VSAMNAME..PS
//SYSIN    DD *
  REPRO INFILE(INVS) OUTFILE(OUTFL)
/*
//************************************************************
//S3COPY EXEC PGM=BPXBATCH
//************************************************************
//STDOUT  DD SYSOUT=*
//STDERR  DD SYSOUT=*
//STDIN   DD DUMMY
//STDENV  DD *,SYMBOLS=EXECSYS
REPRO_DS=&VSAMNAME..PS
VSAM_NAME=&VSAMNAME
S3_URI=&DESTPATH
PROFILE=&PROFILE
/*
//STDPARM DD *
SH
set -o errexit ;
mkdir -p $HOME/tmp;

cp -F bin -v "//'$REPRO_DS'"
   $HOME/tmp/$REPRO_DS ;

aws s3
  mv $HOME/tmp/$REPRO_DS
  $S3_URI$VSAM_NAME --profile $PROFILE ;

/*
//         PEND
//*
//* PROCEDURE CALL WITH PARAMETERS
//*
//EXECUTE  EXEC VSAMPROC,
//         VSAMNAME='MMPRV.PROVIDER.MASTER.KSDS', 
//         DESTPATH='s3://DOC-EXAMPLE-BUCKET',
//         PROFILE='default'
//* ************************************************************
//* VSAM to S3 Copy Procedure
//* Converts VSAM dataset to PS and uploads to S3
//*
//* VSAMNAME  - VSAM dataset name derived from the test plan
//* DESTPATH  - S3 destination URI
//* PROFILE   - AWS profile for authentication
//* ************************************************************
//
