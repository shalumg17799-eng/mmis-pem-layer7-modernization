---
key: MAM-56
localId: L-S027
epic: MAM-4
service: enrollment-service
type: Story
title: "Run the legacy APPINPUT intake as a batch job with one error row per rejected application"
slug: enr-intake-run
specVersion: 1
descriptionSha256: af750c771e40d02d881c6d4ce76a9336b2ebbd6a3ca4c55558a1cd4749540878
jiraUpdated: 2026-11-18T09:18:28.098-0600
rules: [R-PRVENR01-ADD-01, R-PRVENR01-ADD-06, R-PRVENR01-cbl-00001, R-PRVENR01-cbl-00002, R-PRVENR01-cbl-00006, R-PRVENR01-cbl-00010, R-PRVENR01-cbl-00012, R-PRVENR01-cbl-00014, R-PRVENR01-cbl-00015, R-PRVENR01-cbl-00016, R-PRVENR01-cbl-00017, R-PRVENR01-cbl-00018, R-PRVENR01-cbl-00019, R-PRVENR01-cbl-00020, R-PRVENR01-cbl-00022, R-PRVENR01-cbl-00027, R-PRVENR01-cbl-00033, R-PRVENR01-cbl-00037, R-PRVENR01-cbl-00038, R-PRVENR01-cbl-00043, R-PRVENR01-cbl-00044, R-PRVENR01-cbl-00045, R-PRVENR01-cbl-00046, R-PRVENR01-cbl-00047, R-PRVENR01-cbl-00048]
decisions: [D-001, D-007, D-016, D-017, D-020, D-023, D-025]
dependsOn: [MAM-46, MAM-42]
mainJourney: false
labels: [svc-enrollment-service, kag-L-S027, agentic, decision-D-001, decision-D-007, decision-D-016, decision-D-017, decision-D-020, decision-D-023, decision-D-025]
builtCommit:
testsSha256:
---
## Business requirement
Some applications still arrive in the old fixed-width daily application file. An administrator or a scheduler can load such a file for a stated business date and batch identifier. Each application in the file is checked exactly like an online one, a bad application never stops the rest of the file, and every rejected application is listed once in an error file in the old layout, so that the run can later be compared with what the legacy job produced.

## Acceptance criteria
### AC-1 An intake run starts with an explicit business date and batch id
Given An ADMIN token and an APPINPUT file of three valid 200-byte records
When POST /api/v1/batch/intake-runs is called with the file, businessDate 2026-03-31 and batchId B0331001
Then 202 with a BatchRun that has a runId, a job, businessDate 2026-03-31 and status RUNNING or COMPLETED; GET /api/v1/batch/intake-runs/{runId} returns the same businessDate; the service's JSON log contains the line 'PRVENR01: STARTING ENROLLMENT LOAD - BATCH B0331001'
Basis: R-PRVENR01-cbl-00001, R-PRVENR01-cbl-00010, R-PRVENR01-cbl-00015, R-PRVENR01-cbl-00017, R-PRVENR01-cbl-00018, D-017, D-020, Q-B8, modern/contracts/openapi/enrollment-service.yaml#startIntakeRun

### AC-2 A missing batch id defaults to DEFBATCH
Given An ADMIN token and the same APPINPUT file
When POST /api/v1/batch/intake-runs is called with businessDate 2026-03-31 and no batchId (or a batchId of blanks)
Then 202, and the service's JSON log contains the line 'PRVENR01: STARTING ENROLLMENT LOAD - BATCH DEFBATCH'
Basis: R-PRVENR01-cbl-00001, R-PRVENR01-cbl-00016, D-017, Q-B8

### AC-3 Each record is read as the 130-byte PRVRECRD in bytes 1-130
Given An APPINPUT file with two 200-byte records. Record 1: NPI 1003000111 (bytes 1-10), Medicaid ID M00000111 (11-19), type IN (20-21), taxonomy 207Q00000X (22-31), risk tier H, status A, screen flag P and two dates in bytes 32-50, last name RIVERA (51-80), first name MARIA (81-100), licence number MD12345 (101-115), licence state TX (116-117) and non-blank text in bytes 131-200. Record 2: NPI 1003000444, type GR, taxonomy 207Q00000X, legal name EXCLUDED CLINIC LLC (51-90), DBA name blank (91-120) and owner NPI 1990000111 (121-130)
When The run is started and completes
Then GET /api/v1/applications?npi=1003000111 as ANALYST shows one VALIDATED application with source LEGACY_FILE and exactly those individual values; the application for 1003000444 shows legalName EXCLUDED CLINIC LLC and ownerNpi 1990000111 (so bytes 121-130 are read); neither the applications nor their ApplicationValidated events carry the text of bytes 131-200 or the risk tier, status, screen flag or dates of bytes 32-50
Basis: R-PRVENR01-ADD-01, R-PRVENR01-cbl-00012, R-PRVENR01-cbl-00019, R-PRVENR01-cbl-00007, D-025, D-007, Q-009, modern/docs/analysis/data-layouts.md (PRVRECRD)

### AC-4 Each record gets the same validation as an online application
Given A file of four records, each otherwise valid: record 1 is a valid application for NPI 1003000111, record 2 has a blank NPI, record 3 is for NPI 1003000222 with type XX, record 4 is for NPI 1003000333 with an empty taxonomy
When The run completes
Then GET /api/v1/applications as ANALYST shows record 1 VALIDATED with one ApplicationValidated event, and records 2, 3 and 4 REJECTED with source LEGACY_FILE and exactly one error each (E101 on npi, E102 on providerType, E104 on taxonomy); ApplicationRejected events are published for 1003000222 and 1003000333 only (record 2 has no NPI to use as the event subject)
Basis: R-PRVENR01-cbl-00002, R-PRVENR01-cbl-00022, R-PRVENR01-cbl-00003, R-PRVENR01-cbl-00004, R-PRVENR01-cbl-00005, R-PRVENR01-cbl-00024, D-016, D-001, Q-B6

### AC-5 One rejected application does not stop the load
Given A file of three records: a valid application for NPI 1003000111, an application for 1003000222 with an empty taxonomy, and a valid application for 1003000333
When The run completes
Then All three records are processed in file order: 1003000111 and 1003000333 are VALIDATED, 1003000222 is REJECTED with one error E104, and the run's counts.read is 3; the rejected application produces no ApplicationValidated event
Basis: R-PRVENR01-cbl-00038, R-PRVENR01-cbl-00048, R-PRVENR01-cbl-00020, R-PRVENR01-cbl-00002, D-001

### AC-6 The APPERROR file has exactly one row per rejected application
Given A file with a valid record and three rejected ones: a blank NPI with type IN, NPI 1003000222 with type XX and an empty taxonomy (two failed checks), and NPI 1003000333 with type IN and an empty taxonomy
When The run completes and an ADMIN calls GET /api/v1/batch/intake-runs/{runId}/error-file
Then 200 with exactly three rows, in input order, one per rejected application and none for the valid one; each row is 250 characters: bytes 1-10 the NPI as read (blanks for a blank NPI), bytes 11-16 ' TYPE:', bytes 17-18 the type code as read, bytes 19-40 ' ERR:VALIDATION-FAILED' and bytes 41-250 spaces (for example the second row starts '1003000222 TYPE:XX ERR:VALIDATION-FAILED'); for a run with no rejected application the call returns 200 with an empty file
Basis: R-PRVENR01-cbl-00006, R-PRVENR01-cbl-00014, R-PRVENR01-cbl-00027, R-PRVENR01-cbl-00033, R-PRVENR01-cbl-00037, R-PRVENR01-cbl-00043, R-PRVENR01-cbl-00044, R-PRVENR01-cbl-00045, R-PRVENR01-cbl-00046, R-PRVENR01-cbl-00047, R-PRVENR01-ADD-06, D-001, D-016, Q-B4, ADR-005

### AC-7 Only ADMIN and SYSTEM may start a run, only ADMIN may download the error file, and the run parameters are required
Given Tokens for ADMIN, SYSTEM, PROVIDER, ANALYST and SUPERVISOR, a request without a token, and the runId 00000000-0000-0000-0000-000000000000 that no run has
When POST /api/v1/batch/intake-runs is called with a file and businessDate, and GET /api/v1/batch/intake-runs/{runId}/error-file is called
Then POST returns 202 for ADMIN and SYSTEM, 403 problem+json for the others and 401 without a token; the error file returns 200 for ADMIN, 403 for every other role and 401 without a token, and 404 problem+json for the unknown runId; a POST without businessDate or without a file returns 400 problem+json and no run is created
Basis: D-023, D-020, ADR-007, modern/docs/architecture/nfr.md, modern/contracts/openapi/enrollment-service.yaml#startIntakeRun, modern/contracts/openapi/enrollment-service.yaml#getIntakeErrorFile

## Out of scope
- Counts, completion status, failure handling and re-runs of the run (enr-intake-run-2)
- The legacy SORT step of PRVDAY01 (D-009, DROP) and the screening and risk steps that follow STEP010 (scr-batch-run, rsk-assign-tier)
- A cron-scheduled start of the intake: the file arrives through the endpoint (Q-B9)
- The console batch-jobs screen (ui-console-batch)
- The fixed-width codec library itself (dat-codec) and the byte-level comparison with legacy output (par-harness)

## Legacy trace (agent-owned, do not edit)
Rules: R-PRVENR01-ADD-01, R-PRVENR01-ADD-06, R-PRVENR01-cbl-00001, R-PRVENR01-cbl-00002, R-PRVENR01-cbl-00006, R-PRVENR01-cbl-00010, R-PRVENR01-cbl-00012, R-PRVENR01-cbl-00014, R-PRVENR01-cbl-00015, R-PRVENR01-cbl-00016, R-PRVENR01-cbl-00017, R-PRVENR01-cbl-00018, R-PRVENR01-cbl-00019, R-PRVENR01-cbl-00020, R-PRVENR01-cbl-00022, R-PRVENR01-cbl-00027, R-PRVENR01-cbl-00033, R-PRVENR01-cbl-00037, R-PRVENR01-cbl-00038, R-PRVENR01-cbl-00043, R-PRVENR01-cbl-00044, R-PRVENR01-cbl-00045, R-PRVENR01-cbl-00046, R-PRVENR01-cbl-00047, R-PRVENR01-cbl-00048
Related rules: R-PRVENR01-cbl-00003, R-PRVENR01-cbl-00004, R-PRVENR01-cbl-00005, R-PRVENR01-cbl-00007, R-PRVENR01-cbl-00024
Programs: PRVENR01 INITIALIZE-PROGRAM 85-100, PROCESS-APPLICATIONS 102-116, VALIDATE-APPLICATION 128-145, INVALID-APP 155-159, WRITE-ERROR-RECORD 173-179, data division 55-103 (APP-INPUT-RECORD 200 bytes read INTO the 130-byte PRVRECRD, APP-ERROR-RECORD 250 bytes); job PRVDAY01 STEP010 (APPINPUT, APPERROR, PARM BATCH_ID at JCL/PRVDAY01.jcl:20)
Decisions: D-001 FIX: every record is processed; each rejected application gets exactly one APPERROR row and is never registered; D-007 FIX: the provider record is 130 bytes, so the owner NPI in bytes 121-130 is read; D-016 FIX: validation results use E101-E104; APPERROR bytes 41-250 are defined (spaces); D-017 FIX: the batch id is a real job parameter (default DEFBATCH); D-020 FIX: the business date is an explicit job parameter; D-023 FIX: role check on every run endpoint (POST ADMIN, SYSTEM; error file ADMIN); D-025 PRESERVE: intake = PRVRECRD layout in bytes 1-130, bytes 131-200 reserved and ignored (Q-009)

## Technical notes (agent-owned)
Service: enrollment-service. Endpoints/events: POST /api/v1/batch/intake-runs, GET /api/v1/batch/intake-runs/{runId}/error-file, ApplicationValidated, ApplicationRejected. Roles: ADMIN, SYSTEM.
