/* REXX -------------------------------------------------------------*/
/* PRVAUTOM - SDSF AUTOMATION FOR THE PROVIDER ENROLLMENT PIPELINE    */
/*                                                                    */
/* Submits the daily enrollment job, watches JES2 via SDSF, and       */
/* alerts the operator if any step ends with a non-zero return code.  */
/* Runs the legacy "ops automation" the modern event-bus replaces.    */
/*-------------------------------------------------------------------*/
say 'PRVAUTOM: provider enrollment pipeline monitor starting'

jobname = 'PRVDAY01'
maxwait = 30
checks  = 0

/* Submit the daily enrollment job */
"SUBMIT 'MMPRV.PROD.JCL(PRVDAY01)'"
if rc <> 0 then do
   say 'PRVAUTOM: SUBMIT failed rc=' rc
   exit 12
end

/* Poll SDSF for completion */
rc = isfcalls('ON')
do checks = 1 to maxwait
   isfprefix = jobname
   "ISFEXEC ST"
   if rc <> 0 then leave
   do ix = 1 to isfrows
      if owner.ix = 'MMPRV' then do
         say 'PRVAUTOM:' jobname 'status' status.ix 'rc' retcode.ix
         if retcode.ix <> '' & retcode.ix <> 'CC 0000' then
            call alert_operator jobname retcode.ix
      end
   end
end
rc = isfcalls('OFF')

say 'PRVAUTOM: monitor complete after' checks 'checks'
exit 0

alert_operator:
   parse arg jn cc
   say 'PRVAUTOM: *** ALERT *** job' jn 'ended' cc
   /* Real estate routes to NetView / OPS-MVS; stubbed here */
return
