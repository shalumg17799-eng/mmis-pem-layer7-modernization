---
key: MAM-75
localId: L-S051
epic: MAM-11
service: tmsis-service
type: Story
title: "T-MSIS extract run: archive every file, report counts, publish ExtractProduced"
slug: tms-run-archive
specVersion: 1
descriptionSha256: 1ea21a3dd6dadfb0b7daab12e4624bf705ed263eed083e0e236da57c44eaf467
jiraUpdated: 2026-11-18T09:21:28.172-0600
rules: [R-PRVTMS01-ADD-06, R-PRVTMS01-cbl-00001, R-PRVTMS01-cbl-00013, R-PRVTMS01-cbl-00014, R-PRVTMS01-cbl-00015, R-PRVTMS01-cbl-00016, R-PRVTMS01-cbl-00017, R-PRVTMS01-cbl-00018, R-PRVTMS01-cbl-00019, R-PRVTMS01-cbl-00052, R-PRVTMS01-cbl-00053, R-PRVTMS01-cbl-00054, R-PRVTMS01-cbl-00055]
decisions: [D-015, D-020, D-024, D-023]
dependsOn: [MAM-71, MAM-31]
mainJourney: false
labels: [svc-tmsis-service, kag-L-S051, agentic, decision-D-015, decision-D-020, decision-D-024, decision-D-023]
builtCommit: pending-user-commit
testsSha256: 31518f57b3165dacefdcae54ba6bd421e83cea475208800dc5ffcf83237f4b96
---
## Business requirement
The monthly T-MSIS extract is run by an administrator for a chosen business date, or automatically each month. Each run reports how many providers it wrote, keeps a permanent copy of its file as the next archive generation so that earlier months can still be retrieved, and tells the rest of the system that the extract is ready. If a run fails, the failure is visible, nothing is archived and nobody is told the extract is ready.

## Acceptance criteria
### AC-1 An administrator starts an extract for a business date
Given an ADMIN token and ACTIVE providers in provider-service
When the ADMIN calls POST /api/v1/batch/tmsis-extracts with businessDate 2026-03-31
Then the response is 202 with a BatchRun carrying a runId, job PRVTMS01 (the legacy program id), businessDate 2026-03-31 and status RUNNING or COMPLETED (the run may finish before the answer is sent), and GET /api/v1/batch/tmsis-extracts then lists that runId.
Basis: R-PRVTMS01-cbl-00016, D-020, ADR-004, modern/contracts/openapi/tmsis-service.yaml#startTmsisExtract (202, BatchRun), modern/contracts/openapi/tmsis-service.yaml#listTmsisExtracts

### AC-2 The run reports its result and row count
Given a run for 2026-03-31 over 2 ACTIVE providers, and another over none
When each run ends and the ADMIN calls GET /api/v1/batch/tmsis-extracts
Then the list shows each run as COMPLETED with endedAt set; the first has counts.rows 2, equal to the number of records in its file, the second counts.rows 0 with an empty file.
Basis: R-PRVTMS01-cbl-00013, R-PRVTMS01-cbl-00014, R-PRVTMS01-cbl-00015, R-PRVTMS01-cbl-00052, R-PRVTMS01-cbl-00053, R-PRVTMS01-cbl-00054, R-PRVTMS01-cbl-00055, D-024, OQ-D7

### AC-3 The run uses the business date, not the system date
Given the system clock shows 2026-05-10
When the ADMIN starts a run with businessDate 2026-03-31 and the run completes
Then the BatchRun shows businessDate 2026-03-31 and bytes 26-30 of every record of the run's file hold '26090', not '26130' (the day of the system date).
Basis: D-020, D-015, R-PRVTMS01-cbl-00001, R-PRVTMS01-cbl-00017, R-PRVTMS01-cbl-00018, R-PRVTMS01-cbl-00019, ADR-004

### AC-4 Every run's file is archived as a new generation
Given an empty archive, then completed runs for 2026-02-28 and 2026-03-31, and then a second run for 2026-03-31 with no data change
When the ADMIN calls GET /api/v1/batch/tmsis-extracts and downloads each run's file
Then the three runs show counts.archiveGeneration 1, 2 and 3; every earlier run's file can still be downloaded byte for byte unchanged after the later runs, and the third file is byte-identical to the second (nothing is overwritten or deleted).
Basis: modern/docs/analysis/job-flow.md (PRVMTH01 STEP030: IEBGENER to MMPRV.TMSIS.ARCHIVE(+1)), modern/docs/architecture/data-model/tmsis-service.sql (extract_archive.generation), modern/contracts/openapi/tmsis-service.yaml#listTmsisExtracts (runs and archive generations), Q-012, OQ-D6, OQ-D7

### AC-5 ExtractProduced is published when the extract is archived
Given a run for 2026-03-31 that wrote 2 rows
When the run is archived and completes
Then exactly one ExtractProduced event is published on pem.tmsis.events with source tmsis-service, data.runId equal to the run's runId, data.businessDate 2026-03-31, data.periodYyddd '26090' and data.rowCount 2, and it is published only after the run is COMPLETED and archived.
Basis: modern/contracts/asyncapi/events.yaml (ExtractProduced), ADR-003, D-015, modern/docs/architecture/overview.md (flow 7), OQ-D8

### AC-6 A failed run is visible and leaves nothing behind
Given provider-service answers with an error (or is not reachable) while the run reads the providers
When the ADMIN starts a run for 2026-03-31
Then the list shows the run as FAILED with a non-empty error text and endedAt set; GET /api/v1/batch/tmsis-extracts/{runId}/file returns 404; no archive generation is added and no ExtractProduced event is published (the legacy archived whatever file was there even when the extract step failed).
Basis: D-024, R-PRVTMS01-ADD-06, modern/docs/analysis/job-flow.md (PRVMTH01: STEP030 runs even if STEP020 failed), modern/contracts/openapi/tmsis-service.yaml (BatchRun.error; 404 on the file)

### AC-7 The monthly schedule uses the Clock's date
Given the monthly schedule is configured and fires with the injected Clock at 2026-04-30 and no explicit business date
When the scheduled run completes and the ADMIN calls GET /api/v1/batch/tmsis-extracts
Then the list shows a COMPLETED run with businessDate 2026-04-30, whose file records hold period '26120'.
Basis: ADR-004 (cron in configuration; business date defaults to the injected Clock), java-microservices skill section 10, D-020, D-015

### AC-8 Access and input checks
Given tokens for ADMIN, SYSTEM, ANALYST, SUPERVISOR and PROVIDER, and a call with no token
When each calls POST /api/v1/batch/tmsis-extracts, GET /api/v1/batch/tmsis-extracts and GET /api/v1/batch/tmsis-extracts/{runId}/file, and the ADMIN also posts without businessDate
Then ADMIN and SYSTEM may start a run (202); only ADMIN may list runs and download files (SYSTEM gets 403 on both); ANALYST, SUPERVISOR and PROVIDER get 403 everywhere; no token gets 401; a start without businessDate is rejected with 400 application/problem+json.
Basis: modern/docs/architecture/nfr.md (security table), modern/contracts/openapi/tmsis-service.yaml (x-roles), ADR-007, D-023, D-020, modern/contracts/openapi/tmsis-service.yaml#/components/schemas/BatchRunRequest

## Out of scope
- What the records contain and how they are read from provider-service (tms-extract)
- The future-enrolment warning (tms-future-enrolment)
- The notice that notification-service sends when it consumes ExtractProduced (ntf-send)
- Deleting or aging out old archive generations: nothing is deleted until the business sets a retention policy (Q-012)
- Delivery of the file to CMS: no delivery mechanism exists in the estate
- The console batch-jobs screen (ui-console-batch)

## Legacy trace (agent-owned, do not edit)
Rules: R-PRVTMS01-ADD-06, R-PRVTMS01-cbl-00001, R-PRVTMS01-cbl-00013, R-PRVTMS01-cbl-00014, R-PRVTMS01-cbl-00015, R-PRVTMS01-cbl-00016, R-PRVTMS01-cbl-00017, R-PRVTMS01-cbl-00018, R-PRVTMS01-cbl-00019, R-PRVTMS01-cbl-00052, R-PRVTMS01-cbl-00053, R-PRVTMS01-cbl-00054, R-PRVTMS01-cbl-00055
Related rules: R-PRVTMS01-cbl-00010, R-PRVTMS01-cbl-00012, R-PRVTMS01-cbl-00041, R-PRVTMS01-cbl-00042
Programs: PRVTMS01: MAIN-CONTROL 78-89 (program id 79, date 80, end-of-file flag 85, close 87, count display 88), WRITE-TMSIS-ROW 117 (count). Jobs: PRVMTH01 STEP020 (PRVTMS01) and STEP030 (IEBGENER copy of the T-MSIS file to MMPRV.TMSIS.ARCHIVE(+1), which runs even if STEP020 failed).
Decisions: D-015 PRESERVE in the legacy-format extract; ISO dates internally; D-020 FIX: the business date is an explicit job parameter; D-024 FIX: explicit error handling, a job result per run, failures surfaced; D-023 FIX: role-based access (Keycloak roles) on every endpoint and screen

## Technical notes (agent-owned)
Service: tmsis-service. Endpoints/events: POST /api/v1/batch/tmsis-extracts, GET /api/v1/batch/tmsis-extracts, GET /api/v1/batch/tmsis-extracts/{runId}/file, ExtractProduced. Roles: ADMIN, SYSTEM.
