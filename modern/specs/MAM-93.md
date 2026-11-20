---
key: MAM-93
localId: L-S066
epic: MAM-13
service: web-ui
type: Story
title: "Console: run the batch jobs and download their legacy files (admin)"
slug: ui-console-batch
specVersion: 1
descriptionSha256: 846ff59e81b28d8aa17e761743a07836e4c8145c16a8eda6fae1825c112a09dc
jiraUpdated: 2026-11-18T09:24:05.776-0600
rules: []
decisions: [D-020, D-017, D-015, D-025, D-024, D-009, D-023, D-004]
dependsOn: [MAM-62, MAM-56, MAM-32, MAM-45, MAM-55, MAM-67, MAM-71, MAM-75]
mainJourney: false
labels: [svc-web-ui, kag-L-S066, agentic, decision-D-020, decision-D-017, decision-D-015, decision-D-025, decision-D-024, decision-D-009, decision-D-023, decision-D-004]
builtCommit: 2e20ead
testsSha256: 4842945aceae2e0c88db2e102549a964aebebc013610ddfe5f78ba2d230ea5e5
---
## Business requirement
The scheduled jobs of the old system (enrolment intake, screening, revalidation sweep, sanction intake, directory extract and the monthly extract for the federal reporting feed) can be run on demand by an administrator for a business date that the administrator chooses. The page shows each run with its date, status and counts, shows why a run failed, and lets the administrator download the legacy-format files that the runs produce, so they can be compared with the old system.

## Acceptance criteria
### AC-1 The form asks for an explicit business date and, where needed, a file
Given admin1 is signed in
When /console/admin/jobs opens and admin1 picks each job in turn
Then The page shows a 'Business date (MM/DD/YYYY)' input that is empty and is not filled in by the page, a Job list with Enrolment intake (APPINPUT), Screening run, Revalidation sweep, Sanction intake (SANCIN), Directory extract (DIROUT) and T-MSIS extract, a 'Run job' button and a 'Recent runs' table; picking Enrolment intake or Sanction intake adds a required file input and an optional 'Batch id' of at most 8 characters; 'Run job' with no valid date, or with no file for an intake job, sends nothing and shows field errors
Basis: modern/docs/ux/wireframes/console-batch-jobs.html, modern/docs/ux/screen-inventory.md (console batch-jobs), D-020, D-017, modern/contracts/openapi/enrollment-service.yaml#FileRunRequest, modern/contracts/openapi/sanctions-service.yaml#FileRunRequest

### AC-2 Run the revalidation sweep (journey J6)
Given admin1 is on the page
When They choose Revalidation sweep, enter 03/31/2026 and choose 'Run job'
Then The page sends POST /api/v1/batch/revalidation-sweeps with businessDate 2026-03-31, receives 202 and adds a row to 'Recent runs' with Job 'Revalidation sweep', Business date 03/31/2026, the Status in words (Running, Completed or Failed) and the Counts
Basis: modern/docs/ux/journeys.md#J6 (step 1), D-020, D-004, modern/contracts/openapi/revalidation-service.yaml#startRevalidationSweep, D-029

### AC-3 Run the screening, directory and T-MSIS jobs (journey J7)
Given admin1 is on the page
When They run Screening run, Directory extract and T-MSIS extract, each for 03/31/2026
Then The page sends POST /api/v1/batch/screening-runs, POST /api/v1/batch/directory-extracts and POST /api/v1/batch/tmsis-extracts with businessDate 2026-03-31 and adds a row for each; the T-MSIS runs are also listed from GET /api/v1/batch/tmsis-extracts, and each new T-MSIS run adds a new row there (a new archive generation)
Basis: modern/docs/ux/journeys.md#J7 (steps 1 and 2), D-015, D-020, modern/contracts/openapi/tmsis-service.yaml#listTmsisExtracts, modern/contracts/openapi/screening-service.yaml#startScreeningRun, modern/contracts/openapi/directory-service.yaml#startDirectoryExtract, modern/docs/ux/wireframes/console-batch-jobs.html

### AC-4 File intake runs upload the file and follow the run
Given admin1 picks Enrolment intake and then Sanction intake, each with a legacy file, business date 03/31/2026 and the batch id 'B0331'
When They choose 'Run job'
Then The page sends a multipart POST /api/v1/batch/intake-runs and a multipart POST /api/v1/batch/sanction-intake-runs with the file, businessDate 2026-03-31 and batchId B0331, and then refreshes the run from GET /api/v1/batch/intake-runs/{runId} and GET /api/v1/batch/sanction-intake-runs/{runId} until its status is Completed or Failed, showing the counts the service returns
Basis: D-017, D-020, D-025, modern/contracts/openapi/enrollment-service.yaml#startIntakeRun, modern/contracts/openapi/enrollment-service.yaml#getIntakeRun, modern/contracts/openapi/sanctions-service.yaml#getSanctionIntakeRun, modern/docs/analysis/job-flow.md (PRVDAY01 STEP010, PRVSAN01 SANCIN)

### AC-5 Failed runs and refusals are shown
Given A run ends with status FAILED and an error text, and other requests are answered with 400, 403 or 422
When The page shows them
Then The failed run's row shows the status 'Failed' and the error text returned for it; a 400 or 422 shows the problem detail in an error summary at the form; a 403 shows 'You are not allowed to run jobs'; no failure is shown as Completed or silently dropped
Basis: D-024, modern/contracts/openapi/enrollment-service.yaml#BatchRun (error), modern/contracts/openapi/enrollment-service.yaml#startIntakeRun (400, 403, 422), modern/contracts/openapi/tmsis-service.yaml#startTmsisExtract (400, 403)

### AC-6 The legacy files can be downloaded (journeys J6 and J7)
Given Completed runs of the T-MSIS extract, the directory extract, the revalidation sweep, the screening run and the enrolment intake
When admin1 chooses 'Download' on each run
Then The page requests GET /api/v1/batch/tmsis-extracts/{runId}/file (TMSISOUT, 120-byte records), GET /api/v1/batch/directory-extracts/{runId}/file (DIROUT, 100), GET /api/v1/batch/revalidation-sweeps/{runId}/queue-file (REVALQ, 80), GET /api/v1/batch/screening-runs/{runId}/results-file (SCRNOUT, 67) and GET /api/v1/batch/intake-runs/{runId}/error-file (APPERROR, 250) with the administrator's token and saves each file exactly as received, byte for byte; the sanction intake run has no file; a 404 shows 'The file is not available (yet)'
Basis: modern/docs/ux/journeys.md#J6 (step 4), modern/docs/ux/journeys.md#J7 (steps 1 and 2), ADR-005, MODERNIZATION.md section 6 rule 3 (legacy-format adapters), D-015, QE-7, modern/contracts/openapi/tmsis-service.yaml#getTmsisExtractFile, modern/contracts/openapi/directory-service.yaml#getDirectoryExtractFile, modern/contracts/openapi/revalidation-service.yaml#getRevalidationQueueFile, modern/contracts/openapi/screening-service.yaml#getScreeningResultsFile, modern/contracts/openapi/enrollment-service.yaml#getIntakeErrorFile

### AC-7 Only administrators open the page
Given admin1, analyst1, supervisor1 and provider1 are signed in
When Each opens /console/admin/jobs
Then admin1 sees the page; the others see no menu entry and the access-denied page when they type the address; every batch request is made with the administrator's token
Basis: D-023, modern/docs/ux/screen-inventory.md (console batch-jobs, ADMIN), modern/docs/architecture/nfr.md (batch endpoints: ADMIN, SYSTEM)

### AC-8 The batch page has zero axe violations
Given The Playwright test opens the empty page, the page with an intake job selected, the page with runs and the page with a failed run
When It runs an axe scan on each state and runs a job with the keyboard only
Then Every scan reports zero violations; every input has a visible label; the runs table has header cells; run status is text, never colour alone; the Download buttons name the job and date they belong to
Basis: .claude/skills/frontend-stack/SKILL.md section 5, modern/docs/architecture/nfr.md (Accessibility), modern/docs/ux/journeys.md#J6, modern/docs/ux/journeys.md#J7

## Out of scope
- The jobs themselves and their schedules (the services, ADR-004)
- The PRVDAY01 SORT step (D-009, dropped)
- The export of the provider master in the 130-byte layout (GET /api/v1/legacy/provider-master has no screen in the inventory)
- Comparing the legacy files with the legacy system's own output (Phase 9 parity)
- Status polling for the screening run, revalidation sweep and directory extract beyond the 202 answer (QE-7)

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: JCL/PRVDAY01.jcl (STEP010 PRVENR01, STEP030 PRVSCR01, STEP040 PRVRSK01; IF at :38; SORT :43-50 dropped by D-009), JCL/PRVMTH01.jcl (STEP010 PRVREV01, STEP020 PRVTMS01, STEP030 IEBGENER archive), PRVSAN01.cbl (SANCIN intake, run by no JCL), PRVDIR01.cbl (DIROUT, run by no JCL), REXX/PRVAUTOM.rexx:15 (SUBMIT of PRVDAY01, replaced by schedules and this page).
Decisions: D-020 FIX: the business date is an explicit job parameter (the legacy &DATE and &BATCH_DATE are defined nowhere); D-017 FIX: the batch id is a real job parameter; D-015 PRESERVE: the T-MSIS legacy-format file with YYDDD period stays; ISO dates internally; D-025 PRESERVE (Q-009): the intake file is PRVRECRD in bytes 1-130, bytes 131-200 reserved; D-024 FIX: a result per run and failures surfaced; D-009 DROP: the SORT step has no job and no AC; D-023 FIX: ADMIN only; D-004 FIX: the business date is entered as MM/DD/YYYY and sent as ISO

## Technical notes (agent-owned)
Service: web-ui. Endpoints/events: POST /api/v1/batch/intake-runs, GET /api/v1/batch/intake-runs/{runId}, GET /api/v1/batch/intake-runs/{runId}/error-file, POST /api/v1/batch/screening-runs, GET /api/v1/batch/screening-runs/{runId}/results-file, POST /api/v1/batch/revalidation-sweeps, GET /api/v1/batch/revalidation-sweeps/{runId}/queue-file, POST /api/v1/batch/sanction-intake-runs, GET /api/v1/batch/sanction-intake-runs/{runId}, POST /api/v1/batch/directory-extracts, GET /api/v1/batch/directory-extracts/{runId}/file, POST /api/v1/batch/tmsis-extracts, GET /api/v1/batch/tmsis-extracts, GET /api/v1/batch/tmsis-extracts/{runId}/file. Roles: ADMIN.
