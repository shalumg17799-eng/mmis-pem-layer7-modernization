---
key: MAM-32
localId: L-S034
epic: MAM-5
service: screening-service
type: Story
title: "Run screening as a batch job and produce the legacy SCRNOUT file"
slug: scr-batch-run
specVersion: 1
descriptionSha256: 19761ca24514305d36d23cd196c1bb731aff60a550e7b584b2268aa2b7ac66fd
jiraUpdated: 2026-11-18T09:15:36.765-0600
rules: [R-PRVSCR01-ADD-04, R-PRVSCR01-ADD-05, R-PRVSCR01-ADD-06, R-PRVSCR01-cbl-00001, R-PRVSCR01-cbl-00002, R-PRVSCR01-cbl-00003, R-PRVSCR01-cbl-00012, R-PRVSCR01-cbl-00013, R-PRVSCR01-cbl-00014, R-PRVSCR01-cbl-00015, R-PRVSCR01-cbl-00016, R-PRVSCR01-cbl-00017, R-PRVSCR01-cbl-00018, R-PRVSCR01-cbl-00019, R-PRVSCR01-cbl-00022, R-PRVSCR01-cbl-00023, R-PRVSCR01-cbl-00048, R-PRVSCR01-cbl-00053, R-PRVSCR01-cbl-00054, R-PRVSCR01-cbl-00055, R-PRVSCR01-cbl-00056, R-PRVSCR01-cbl-00057, R-PRVSCR01-cbl-00058, R-PRVSCR01-cbl-00059, R-PRVSCR01-cbl-00060, R-PRVSCR01-cbl-00061, R-PRVSCR01-cbl-00062]
decisions: [D-020, D-024, D-005, D-004, D-009]
dependsOn: [MAM-25, MAM-42, MAM-37]
mainJourney: false
labels: [svc-screening-service, kag-L-S034, agentic, decision-D-020, decision-D-024, decision-D-005, decision-D-004, decision-D-009]
builtCommit: 6e24725
testsSha256: 46dc0c687ad4cf4acb083e477c4f2116c60c73cdb50bfa72b3554c47f8ec657c
---
## Business requirement
The daily enrolment job used to screen every provider that was still unscreened and write one line per check into a fixed-format file that other programmes and, later, the parity comparison depend on. The new system must still be able to do the same on demand for a business date: screen every provider that is still unscreened, produce the same file, and tell the administrator how many providers were read, screened and failed. Unlike the legacy job it must say clearly when something went wrong instead of finishing silently.

## Acceptance criteria
### AC-1 An administrator or the scheduler starts screening for a business date
Given tokens for ADMIN, SYSTEM, ANALYST, SUPERVISOR and PROVIDER, and a request with no token
When each calls POST /api/v1/batch/screening-runs with businessDate 2026-03-31, and ADMIN also calls it with a body without businessDate
Then ADMIN and SYSTEM receive 202 with a BatchRun (runId, job naming the screening job, businessDate 2026-03-31, status RUNNING); ANALYST, SUPERVISOR and PROVIDER receive 403; no token receives 401; the body without businessDate returns 400 with a problem+json body
Basis: R-PRVSCR01-cbl-00001, R-PRVSCR01-cbl-00013, R-PRVSCR01-cbl-00014, R-PRVSCR01-cbl-00015, R-PRVSCR01-cbl-00016, R-PRVSCR01-cbl-00017, D-020, D-023, ADR-004, modern/contracts/openapi/screening-service.yaml#startScreeningRun (x-roles ADMIN, SYSTEM; businessDate required)

### AC-2 Only unscreened providers are screened
Given provider-service holds four providers, 1003000111 (screenFlag U), 1003000555 (U), 1003000222 (P) and 1003000444 (F), and none has a screening yet
When the run for 2026-03-31 executes
Then GET /api/v1/screenings shows one new screening with reason INITIAL for 1003000111 and one for 1003000555, and none for 1003000222 or 1003000444; every provider returned by provider-service GET /api/v1/providers (all pages) is read exactly once and the run stops when no page remains
Basis: R-PRVSCR01-cbl-00002, R-PRVSCR01-cbl-00003, R-PRVSCR01-cbl-00018, R-PRVSCR01-cbl-00019, R-PRVSCR01-cbl-00022, R-PRVSCR01-cbl-00023, D-014, ADR-002, modern/contracts/openapi/provider-service.yaml#searchProviders (SYSTEM may read)

### AC-3 The SCRNOUT file has four 67-byte rows per screened provider
Given the run of criterion 2 has completed
When an ADMIN calls GET /api/v1/batch/screening-runs/{runId}/results-file
Then the body has 8 records of 67 bytes, four per screened provider in ascending NPI order (1003000111 then 1003000555) and, for each provider, in the order LICBOARD, OIGLEIE, SAMGOV, SSADMF; each record holds NPI (bytes 1-10), SOURCE (11-18, left-justified, space-padded), RESULT (19, P, F or C), VERDICT-DATE (20-27, CCYYMMDD, here 20260331) and DETAIL (28-67, left-justified, cut to 40 characters)
Basis: R-PRVSCR01-cbl-00053, R-PRVSCR01-cbl-00054, R-PRVSCR01-cbl-00055, R-PRVSCR01-cbl-00056, R-PRVSCR01-cbl-00057, R-PRVSCR01-cbl-00058, R-PRVSCR01-cbl-00059, R-PRVSCR01-cbl-00060, R-PRVSCR01-ADD-02, D-005, D-004, ADR-005, QUESTION C-7, modern/docs/analysis/data-layouts.md#SCRNREC, .claude/skills/cobol-to-java/SKILL.md (padding and truncation only in legacy-format adapters)

### AC-4 A failed provider's rows show the failing source
Given provider 1003000555 (licence PT99900, EXPIRED on the licence-board mock) is screened by the run
When the SCRNOUT file of the run is downloaded
Then its LICBOARD row has RESULT F and its OIGLEIE, SAMGOV and SSADMF rows have RESULT P (the legacy wrote P on every row)
Basis: R-PRVSCR01-ADD-01, R-PRVSCR01-cbl-00010, D-005, aws-transform/test-data/license_board.txt

### AC-5 The run records how many providers it read, screened, skipped and failed
Given the run of criterion 2 (4 providers read, 2 screened, 2 skipped, 1 failed)
When an ADMIN reads the run through GET /api/v1/batch/screening-runs/{runId} (run-status call proposed by question C-8; not in contract 1.0.0)
Then the BatchRun has status COMPLETED, an endedAt time and counts read 4, screened 2, skipped 2 and failed 1, where failed is the number of providers whose screening result is FAIL
Basis: R-PRVSCR01-cbl-00012, R-PRVSCR01-cbl-00048, R-PRVSCR01-cbl-00061, R-PRVSCR01-cbl-00062, D-024, ADR-004 (every run records its counts and failure reason), QUESTION C-8, modern/docs/analysis/data-layouts.md#WRKSTORE (read, written, skipped, errored counters)

### AC-6 A failure is reported, never silent
Given a run during which provider-service answers 5xx, and another run whose SCRNOUT file cannot be written
When each run executes and an ADMIN reads it through the run-status call of question C-8
Then each run ends with status FAILED and the reason in error, it stops reading providers instead of retrying for ever, and it is never reported as COMPLETED
Basis: R-PRVSCR01-ADD-04, R-PRVSCR01-ADD-05, R-PRVSCR01-ADD-06, D-024, ADR-004, QUESTION C-8, modern/contracts/openapi/screening-service.yaml#BatchRun (status FAILED, error)

### AC-7 Running again does not screen anyone twice
Given the run for 2026-03-31 has completed and an INITIAL screening (RUNNING or COMPLETED) exists for 1003000111 and 1003000555, whose screen flag is still U in provider-service
When another run is started for 2026-03-31
Then no further screening and no further ScreeningCompleted is created for 1003000111 or 1003000555, and the second run's SCRNOUT file has no rows for them
Basis: modern/docs/architecture/nfr.md#Availability (batch jobs can be re-run for a business date and are idempotent), ADR-004, R-PRVSCR01-cbl-00022

### AC-8 Only an administrator can download the SCRNOUT file
Given a completed run id, an unknown run id, and tokens for ADMIN, SYSTEM, ANALYST and PROVIDER
When each calls GET /api/v1/batch/screening-runs/{runId}/results-file, and ADMIN also calls it with the unknown run id
Then ADMIN receives 200 with the file, SYSTEM, ANALYST and PROVIDER receive 403, and the unknown run id returns 404 with a problem+json body
Basis: modern/docs/architecture/nfr.md#Security, D-023, modern/contracts/openapi/screening-service.yaml#getScreeningResultsFile (x-roles ADMIN, 404)

## Out of scope
- PRVDAY01 STEP020 SORT (dropped, D-009)
- The cron expression of the daily schedule (configuration, ADR-004)
- The console page that starts the run (ui-console-batch)
- Comparing the file with a legacy run (par-harness)

## Legacy trace (agent-owned, do not edit)
Rules: R-PRVSCR01-ADD-04, R-PRVSCR01-ADD-05, R-PRVSCR01-ADD-06, R-PRVSCR01-cbl-00001, R-PRVSCR01-cbl-00002, R-PRVSCR01-cbl-00003, R-PRVSCR01-cbl-00012, R-PRVSCR01-cbl-00013, R-PRVSCR01-cbl-00014, R-PRVSCR01-cbl-00015, R-PRVSCR01-cbl-00016, R-PRVSCR01-cbl-00017, R-PRVSCR01-cbl-00018, R-PRVSCR01-cbl-00019, R-PRVSCR01-cbl-00022, R-PRVSCR01-cbl-00023, R-PRVSCR01-cbl-00048, R-PRVSCR01-cbl-00053, R-PRVSCR01-cbl-00054, R-PRVSCR01-cbl-00055, R-PRVSCR01-cbl-00056, R-PRVSCR01-cbl-00057, R-PRVSCR01-cbl-00058, R-PRVSCR01-cbl-00059, R-PRVSCR01-cbl-00060, R-PRVSCR01-cbl-00061, R-PRVSCR01-cbl-00062
Related rules: R-PRVSCR01-ADD-01, R-PRVSCR01-ADD-02, R-PRVSCR01-cbl-00010
Programs: source-code/BATCH/PRVSCR01.cbl: MAIN-CONTROL 74-82 (open 76-77, loop 79, close 80, failure count display 81), SCREEN-NEXT-PROVIDER 84-93, SCREEN-ONE-PROVIDER 95-114 (counter at 112, REWRITE at 114), WRITE-SCREEN-ROW 146-149. Job step: source-code/JCL/PRVDAY01.jcl STEP030 (DD SCRNOUT); STEP020 SORT dropped
Decisions: D-020 FIX: the business date is an explicit job parameter; D-024 FIX: a job result per run, failures surfaced, no endless loop; D-005 REPLACE: SCRNOUT rows now carry source, date and detail; D-004 FIX: 4-digit-year dates (CCYYMMDD in the file); D-009 DROP: the SORT step is not built

## Technical notes (agent-owned)
Service: screening-service. Endpoints/events: POST /api/v1/batch/screening-runs, GET /api/v1/batch/screening-runs/{runId}/results-file, GET /api/v1/screenings, ScreeningCompleted. Roles: ADMIN, SYSTEM.
