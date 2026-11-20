---
key: MAM-67
localId: L-S049
epic: MAM-10
service: directory-service
type: Story
title: "Directory extract as a batch job in the legacy DIROUT format"
slug: dir-extract
specVersion: 1
descriptionSha256: efc759268906fa491040e96eb6cbe1d820a2afa14fe2ff6247cc1a73ed1eeda5
jiraUpdated: 2026-11-18T09:20:46.282-0600
rules: [R-PRVDIR01-ADD-03, R-PRVDIR01-cbl-00001, R-PRVDIR01-cbl-00002, R-PRVDIR01-cbl-00003, R-PRVDIR01-cbl-00007, R-PRVDIR01-cbl-00008, R-PRVDIR01-cbl-00009, R-PRVDIR01-cbl-00010, R-PRVDIR01-cbl-00011, R-PRVDIR01-cbl-00012, R-PRVDIR01-cbl-00013, R-PRVDIR01-cbl-00014, R-PRVDIR01-cbl-00015, R-PRVDIR01-cbl-00016, R-PRVDIR01-cbl-00017, R-PRVDIR01-cbl-00024, R-PRVDIR01-cbl-00025, R-PRVDIR01-cbl-00026, R-PRVDIR01-cbl-00027, R-PRVDIR01-cbl-00028, R-PRVDIR01-cbl-00029]
decisions: [D-028, D-020, D-024, D-023, D-013]
dependsOn: [MAM-61, MAM-42]
mainJourney: false
labels: [svc-directory-service, kag-L-S049, agentic, decision-D-028, decision-D-020, decision-D-024, decision-D-023, decision-D-013]
builtCommit:
testsSha256:
---
## Business requirement
Managed care organisations received a directory file listing every active provider, one fixed-width record per provider. The new system must still produce that file in exactly the legacy layout, so it can be compared byte for byte with the old one, but with a properly composed provider name. An administrator can run the job for a chosen business date and download the file. The run reports how many providers it wrote, and a failed run is visible instead of silently producing nothing.

## Acceptance criteria
### AC-1 An administrator runs the extract and sees the result
Given an ADMIN token and a directory holding two ACTIVE entries (NPI 1003000111 and NPI 1003000333)
When the ADMIN calls POST /api/v1/batch/directory-extracts with businessDate 2026-03-31
Then the response is 202 with the finished BatchRun: a runId, job PRVDIR01 (the legacy program id), businessDate 2026-03-31, status COMPLETED, endedAt set and counts.rows 2, equal to the number of records in the file.
Basis: R-PRVDIR01-cbl-00001, R-PRVDIR01-cbl-00010, R-PRVDIR01-cbl-00013, R-PRVDIR01-cbl-00014, R-PRVDIR01-cbl-00008, R-PRVDIR01-cbl-00027, R-PRVDIR01-cbl-00028, R-PRVDIR01-cbl-00009, R-PRVDIR01-cbl-00029, D-024, D-020, ADR-004, modern/contracts/openapi/directory-service.yaml#startDirectoryExtract (202, BatchRun), OQ-D7, OQ-D10

### AC-2 The business date is an explicit parameter
Given an ADMIN token
When the ADMIN calls POST /api/v1/batch/directory-extracts without businessDate, and then with businessDate 31/03/2026 (not an ISO date)
Then both calls are rejected with 400 application/problem+json and no run is created.
Basis: D-020, ADR-004, ADR-009 (ISO dates in APIs), modern/contracts/openapi/directory-service.yaml#/components/schemas/BatchRunRequest (businessDate required, format date)

### AC-3 Only ACTIVE entries are written, one record each, in NPI order
Given the directory holds ACTIVE entries for NPI 1003000333 and NPI 1003000111, a suspended entry for NPI 1003000777, a denied one for NPI 1003000444 and a pending one for NPI 1003000555
When a run for 2026-03-31 completes and the ADMIN calls GET /api/v1/batch/directory-extracts/{runId}/file
Then the file has exactly two records, NPI 1003000111 first and NPI 1003000333 second (ascending NPI, the key order in which the legacy read its master), each 100 bytes, with no header, trailer or record for the other providers; when the directory holds no ACTIVE entry, the run is COMPLETED with counts.rows 0 and the file is empty.
Basis: R-PRVDIR01-cbl-00002, R-PRVDIR01-cbl-00003, R-PRVDIR01-cbl-00004, R-PRVDIR01-cbl-00015, R-PRVDIR01-cbl-00016, R-PRVDIR01-cbl-00017, R-PRVDIR01-cbl-00018, R-PRVDIR01-cbl-00007, R-PRVDIR01-cbl-00026, R-PRVDIR01-ADD-01, D-006, D-013, ADR-005, modern/docs/analysis/data-layouts.md (PRVDIR01:DIR-OUT-RECORD), OQ-D4

### AC-4 The record has the legacy DIROUT layout
Given a completed run whose file holds a record for NPI 1003000111 (Medicaid ID M00000111, type IN, risk tier L)
When the ADMIN downloads the file and reads that record
Then the record is 100 bytes: bytes 1-9 'M00000111', bytes 10-19 '1003000111', bytes 20-21 'IN', byte 22 'L', bytes 23-82 the name (60 bytes) and bytes 83-100 spaces.
Basis: R-PRVDIR01-cbl-00024, R-PRVDIR01-cbl-00019, R-PRVDIR01-cbl-00020, R-PRVDIR01-cbl-00021, R-PRVDIR01-cbl-00022, R-PRVDIR01-cbl-00005, ADR-005, modern/docs/analysis/data-layouts.md (PRVDIR01:DIR-OUT-RECORD, 100 bytes), modern/docs/architecture/field-to-column.csv, OQ-D4

### AC-5 The name field holds the composed name
Given NPI 1003000111 (last name RIVERA, first name MARIA, licence number MD12345) and NPI 1003000333 (type FA, legal name SUNSET HOSPICE INC, DBA name SUNSET HOSPICE CARE) are in a completed run
When the ADMIN reads bytes 23-82 of each record
Then the name of 1003000111 is 'MARIA RIVERA' followed by spaces to 60 bytes and the name of 1003000333 is 'SUNSET HOSPICE INC' followed by spaces to 60 bytes; no record contains the licence number, the DBA name or the padding of the separate last-name and first-name fields.
Basis: D-028, R-PRVDIR01-ADD-02, R-PRVDIR01-cbl-00006, R-PRVDIR01-cbl-00023, R-PRVDIR01-cbl-00025, modern/docs/architecture/field-to-column.csv (DIR-NAME composed from the name fields), OQ-D2

### AC-6 Running again for the same date creates a new run
Given a completed run A for business date 2026-03-31 and no change to the directory since
When the ADMIN calls POST /api/v1/batch/directory-extracts again for 2026-03-31
Then the response carries a different runId, and the file of the new run is byte-identical to the file of run A, which can still be downloaded.
Basis: ADR-004 (jobs can be re-run for a date), modern/docs/architecture/nfr.md (batch jobs can be re-run and are idempotent), Q-012, OQ-D6

### AC-7 A failed run is visible and produces no file
Given the directory cannot store the extract file (for example the database rejects the write)
When the ADMIN calls POST /api/v1/batch/directory-extracts for 2026-03-31
Then the BatchRun in the response has status FAILED, a non-empty error text and endedAt set, and GET /api/v1/batch/directory-extracts/{runId}/file for that run returns 404 (no partial file).
Basis: D-024, R-PRVDIR01-ADD-03, R-PRVDIR01-cbl-00011, R-PRVDIR01-cbl-00012, modern/contracts/openapi/directory-service.yaml#/components/schemas/BatchRun (error: why the run failed), OQ-D10

### AC-8 Only the right roles can run and download
Given tokens for ADMIN, SYSTEM, ANALYST, SUPERVISOR and PROVIDER, and a call with no token
When each calls POST /api/v1/batch/directory-extracts and GET /api/v1/batch/directory-extracts/{runId}/file
Then ADMIN and SYSTEM may start a run (202); only ADMIN may download the file (200, SYSTEM gets 403); ANALYST, SUPERVISOR and PROVIDER get 403 on both; no token gets 401.
Basis: modern/docs/architecture/nfr.md (security table), modern/contracts/openapi/directory-service.yaml (x-roles), ADR-007, D-023

## Out of scope
- Sending the file to the managed care organisations: no delivery mechanism exists in the estate; the file is downloaded by an ADMIN
- A call to read a run's status later: the contract has only the start call and the file download (OQ-D10)
- ACs for the scheduled run (cron in configuration, ADR-004): it is configured, but no call in the contract lists directory runs, so its result cannot be observed (OQ-D10)
- Taxonomy, address, phone or network columns in DIROUT: the legacy record has none and must stay 100 bytes (R-PRVDIR01-ADD-04, Q-008)
- The public search (dir-search) and FHIR (dir-fhir)
- Reading the legacy provider master: the file is built from the directory's own data (D-013)

## Legacy trace (agent-owned, do not edit)
Rules: R-PRVDIR01-ADD-03, R-PRVDIR01-cbl-00001, R-PRVDIR01-cbl-00002, R-PRVDIR01-cbl-00003, R-PRVDIR01-cbl-00007, R-PRVDIR01-cbl-00008, R-PRVDIR01-cbl-00009, R-PRVDIR01-cbl-00010, R-PRVDIR01-cbl-00011, R-PRVDIR01-cbl-00012, R-PRVDIR01-cbl-00013, R-PRVDIR01-cbl-00014, R-PRVDIR01-cbl-00015, R-PRVDIR01-cbl-00016, R-PRVDIR01-cbl-00017, R-PRVDIR01-cbl-00024, R-PRVDIR01-cbl-00025, R-PRVDIR01-cbl-00026, R-PRVDIR01-cbl-00027, R-PRVDIR01-cbl-00028, R-PRVDIR01-cbl-00029
Related rules: R-PRVDIR01-ADD-01, R-PRVDIR01-ADD-02, R-PRVDIR01-ADD-05, R-PRVDIR01-cbl-00004, R-PRVDIR01-cbl-00005, R-PRVDIR01-cbl-00006, R-PRVDIR01-cbl-00018, R-PRVDIR01-cbl-00019, R-PRVDIR01-cbl-00020, R-PRVDIR01-cbl-00021, R-PRVDIR01-cbl-00022, R-PRVDIR01-cbl-00023
Programs: PRVDIR01: MAIN-CONTROL 62-70 (program id 63, open 64-65, end-of-file flag 66, loop 67, close and count display 68-69), EXTRACT-NEXT 72-81 (read 73, end of file 75-76, ACTIVE filter 78-79), WRITE-DIR-ROW 83-90 (mapping 84-88, write 89, count 90), DIR-OUT-RECORD 47-53. No JCL job runs PRVDIR01 (job-flow.md); the DD DIROUT has no dataset definition.
Decisions: D-028 FIX: compose the name from the name fields (individual: first + last; organisation: legal name); D-020 FIX: the business date is an explicit job parameter; D-024 FIX: explicit error handling, a job result per run, failures surfaced; D-023 FIX: role-based access (Keycloak roles) on every endpoint and screen; D-013 FIX: sanctions-service and directory-service call provider-service's API (here: the file is built from the directory's own read model, never from the provider master)

## Technical notes (agent-owned)
Service: directory-service. Endpoints/events: POST /api/v1/batch/directory-extracts, GET /api/v1/batch/directory-extracts/{runId}/file. Roles: ADMIN, SYSTEM.
