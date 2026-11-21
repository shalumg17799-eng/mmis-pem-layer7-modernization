---
key: MAM-45
localId: L-S038
epic: MAM-7
service: revalidation-service
type: Story
title: "Monthly revalidation sweep: open a case for every ACTIVE provider that is due, with the legacy REVALQ file"
slug: rev-sweep
specVersion: 1
descriptionSha256: 8c177cc6abab65f9a0a8cd5f058c6ca5a04f8a04f401d8432d7f3c2ecda1e99d
jiraUpdated: 2026-11-18T09:16:50.507-0600
rules: [R-PRVREV01-ADD-02, R-PRVREV01-ADD-03, R-PRVREV01-ADD-04, R-PRVREV01-cbl-00001, R-PRVREV01-cbl-00002, R-PRVREV01-cbl-00003, R-PRVREV01-cbl-00004, R-PRVREV01-cbl-00005, R-PRVREV01-cbl-00006, R-PRVREV01-cbl-00007, R-PRVREV01-cbl-00008, R-PRVREV01-cbl-00009, R-PRVREV01-cbl-00010, R-PRVREV01-cbl-00011, R-PRVREV01-cbl-00012, R-PRVREV01-cbl-00013, R-PRVREV01-cbl-00014, R-PRVREV01-cbl-00015, R-PRVREV01-cbl-00016, R-PRVREV01-cbl-00017, R-PRVREV01-cbl-00018, R-PRVREV01-cbl-00019, R-PRVREV01-cbl-00020, R-PRVREV01-cbl-00021, R-PRVREV01-cbl-00022, R-PRVREV01-cbl-00023, R-PRVREV01-cbl-00024]
decisions: [D-003, D-029, D-004, D-020, D-024, D-006, D-014]
dependsOn: [MAM-27, MAM-42]
mainJourney: false
labels: [svc-revalidation-service, kag-L-S038, agentic, decision-D-003, decision-D-029, decision-D-004, decision-D-020, decision-D-024, decision-D-006, decision-D-014]
builtCommit: a529d4f
testsSha256: 50cfe8b66add57fe2e801f0526d447eef640fde85b947b4cf05a674d2b90d04e
---
## Business requirement
Providers must be revalidated at least every five years. Once a month the state must find every active provider whose revalidation date has arrived and open one revalidation case for each, so that nobody is forgotten and nobody is queued twice. Today the list is rebuilt from scratch every month with no memory of earlier months, and the dates it relies on are reset every day, so it rarely finds anyone. After this story each due provider has exactly one open case, the administrator can run the sweep for any business date, and the same fixed-format queue file as before can be downloaded.

## Acceptance criteria
### AC-1 An administrator or the scheduler starts the sweep for a business date
Given tokens for ADMIN, SYSTEM, ANALYST and PROVIDER, and a request with no token
When each calls POST /api/v1/batch/revalidation-sweeps with businessDate 2026-03-31, and ADMIN also calls it with a body without businessDate
Then ADMIN and SYSTEM receive 202 with a BatchRun (runId, job naming the revalidation sweep, businessDate 2026-03-31, status RUNNING); ANALYST and PROVIDER receive 403; no token receives 401; the body without businessDate returns 400; every due date is then compared with businessDate 2026-03-31 (4-digit year), not with the clock date
Basis: R-PRVREV01-cbl-00001, R-PRVREV01-cbl-00002, R-PRVREV01-cbl-00008, R-PRVREV01-cbl-00009, R-PRVREV01-cbl-00010, D-020, D-004, D-023, ADR-004, modern/contracts/openapi/revalidation-service.yaml#startRevalidationSweep (x-roles ADMIN, SYSTEM), modern/docs/ux/journeys.md#J6

### AC-2 An ACTIVE provider whose due date has arrived gets one case
Given revalidation-service tracks provider 1003000222 as status A with revalidation due date 2026-02-28 and provider 1003000333 as status A with due date 2026-03-31, both from ProviderStatusChanged events
When the sweep runs for 2026-03-31
Then GET /api/v1/revalidations?npi=1003000222 and ?npi=1003000333 each return exactly one open case (status DUE or IN_SCREENING) whose dueDate is the provider's own due date (2026-02-28 and 2026-03-31)
Basis: R-PRVREV01-cbl-00003, R-PRVREV01-cbl-00004, R-PRVREV01-cbl-00005, R-PRVREV01-cbl-00011, R-PRVREV01-cbl-00012, R-PRVREV01-cbl-00013, R-PRVREV01-cbl-00015, R-PRVREV01-cbl-00016, R-PRVREV01-ADD-01, D-029, D-003, D-014, QUESTION C-13, modern/docs/ux/journeys.md#J6, 42 CFR 455.414 (summary, unverified)

### AC-3 Providers that are not due, not ACTIVE or moved forward get no case
Given tracked providers: 1003000777 status A due 2026-04-01; 1003000888 status S due 2026-02-28; 1003000555 status T due 2026-02-28; and 1003000111 status A due 2026-02-28 that, before the sweep, received a ProviderStatusChanged with revalidationDueDate 2031-03-31
When the sweep runs for 2026-03-31
Then GET /api/v1/revalidations?npi=... returns no case for any of these four providers and the run's REVALQ file has no row for them
Basis: R-PRVREV01-cbl-00003, R-PRVREV01-cbl-00004, R-PRVREV01-cbl-00015, R-PRVREV01-cbl-00016, D-006, D-003, QUESTION C-10, modern/contracts/asyncapi/events.yaml#ProviderStatusChanged (revalidationDueDate)

### AC-4 Opening a case announces it
Given the sweep opens a case for provider 1003000222
When the case is committed
Then exactly one RevalidationDue is published to pem.revalidation.events with message key 1003000222 and data caseId, npi 1003000222 and dueDate 2026-02-28
Basis: modern/contracts/asyncapi/events.yaml#RevalidationDue, R-PRVREV01-cbl-00005, D-029, ADR-003

### AC-5 A provider that already has an open case is not queued again
Given provider 1003000222 has an open case (status DUE or IN_SCREENING) from the sweep for 2026-03-31
When another sweep runs, for 2026-03-31 again and then for 2026-04-30
Then GET /api/v1/revalidations?npi=1003000222 still returns one case, no second RevalidationDue is published for it, and the later runs' REVALQ files have no row for it
Basis: R-PRVREV01-ADD-02, D-029, modern/docs/architecture/data-model/revalidation-service.sql (at most one open case per provider), modern/docs/architecture/nfr.md#Availability (re-runnable, idempotent)

### AC-6 A provider with no due date is not queued, and the gap is visible
Given tracked provider 1003000666 with status A and no revalidation due date (revalidationDueDate null)
When the sweep runs for 2026-03-31 and an ADMIN reads the run through the run-status call of question C-8
Then no case is opened for 1003000666, the REVALQ file has no row for it, and the run's counts show it under noDueDate
Basis: R-PRVREV01-ADD-03, D-029, D-024, QUESTION C-11, QUESTION C-8

### AC-7 The legacy REVALQ file lists the cases the run opened
Given the run of criterion 2, which opened the cases for 1003000222 and 1003000333
When an ADMIN calls GET /api/v1/batch/revalidation-sweeps/{runId}/queue-file
Then the body has 2 records of 80 bytes in ascending NPI order, each holding NPI (bytes 1-10), the case due date as CCYYMMDD (11-18), RESCREEN-REQUIRED left-justified and space-padded to 20 characters (19-38) and 42 spaces (39-80), for example 1003000222 then 20260228; a run that opens no case returns an empty file; SYSTEM, ANALYST and PROVIDER tokens receive 403 and an unknown runId returns 404
Basis: R-PRVREV01-cbl-00005, R-PRVREV01-cbl-00017, R-PRVREV01-cbl-00018, R-PRVREV01-cbl-00019, R-PRVREV01-cbl-00020, ADR-005, D-004, modern/docs/analysis/data-layouts.md#PRVREV01:REVAL-OUT-RECORD, modern/contracts/openapi/revalidation-service.yaml#getRevalidationQueueFile (x-roles ADMIN, 404), modern/docs/ux/journeys.md#J6

### AC-8 The run reports how many cases it queued, and a failure is never silent
Given the run of criterion 2, which evaluated every tracked provider and opened 2 cases, and another run whose database or file write fails
When an ADMIN reads each run through GET /api/v1/batch/revalidation-sweeps/{runId} (run-status call proposed by question C-8; not in contract 1.0.0)
Then the first run has status COMPLETED and counts queued 2, reached only after every tracked provider was evaluated; the failed run has status FAILED with the reason in error and is never reported as COMPLETED
Basis: R-PRVREV01-cbl-00006, R-PRVREV01-cbl-00007, R-PRVREV01-cbl-00014, R-PRVREV01-cbl-00021, R-PRVREV01-cbl-00022, R-PRVREV01-cbl-00023, R-PRVREV01-cbl-00024, R-PRVREV01-ADD-04, D-024, ADR-004 (every run records its counts and failure reason), QUESTION C-8, modern/contracts/openapi/revalidation-service.yaml#BatchRun

## Out of scope
- Re-screening the providers (screening-service, scr-rescreen)
- Completing, listing and cancelling cases (rev-cases, rev-cases-2)
- Setting the due date (provider-service owns it, D-003)
- The cron expression of the monthly schedule (configuration, ADR-004)
- The console page that starts the sweep (ui-console-batch)
- A re-run for the same business date opens no new case, so its REVALQ file is empty (consequence of D-029; the earlier run's file stays available)

## Legacy trace (agent-owned, do not edit)
Rules: R-PRVREV01-ADD-02, R-PRVREV01-ADD-03, R-PRVREV01-ADD-04, R-PRVREV01-cbl-00001, R-PRVREV01-cbl-00002, R-PRVREV01-cbl-00003, R-PRVREV01-cbl-00004, R-PRVREV01-cbl-00005, R-PRVREV01-cbl-00006, R-PRVREV01-cbl-00007, R-PRVREV01-cbl-00008, R-PRVREV01-cbl-00009, R-PRVREV01-cbl-00010, R-PRVREV01-cbl-00011, R-PRVREV01-cbl-00012, R-PRVREV01-cbl-00013, R-PRVREV01-cbl-00014, R-PRVREV01-cbl-00015, R-PRVREV01-cbl-00016, R-PRVREV01-cbl-00017, R-PRVREV01-cbl-00018, R-PRVREV01-cbl-00019, R-PRVREV01-cbl-00020, R-PRVREV01-cbl-00021, R-PRVREV01-cbl-00022, R-PRVREV01-cbl-00023, R-PRVREV01-cbl-00024
Related rules: R-PRVREV01-ADD-01
Programs: source-code/BATCH/PRVREV01.cbl: MAIN-CONTROL 63-73 (program id at 64, date at 65, open 66-67, loop 69, close 70, summary display 71-72), CHECK-NEXT 75-86 (active and due test 81-84), QUEUE-FOR-REVAL 88-94 (queue row 89-93, counter 94). Job step: source-code/JCL/PRVMTH01.jcl STEP010 (DD REVALQ)
Decisions: D-003 FIX: the due date compared is the one set at approval and at each completed revalidation, never reset daily; D-029 FIX: one case with its own state per provider; a provider is not queued again while its case is open; D-004 FIX: 4-digit-year dates (ISO in the API, CCYYMMDD in REVALQ); D-020 FIX: the business date is an explicit job parameter; D-024 FIX: a job result per run, failures surfaced; D-006 FIX: status S (suspended) exists; only ACTIVE (A) providers are swept; D-014 FIX: provider-service is the single owner; revalidation-service keeps only its own read model built from ProviderStatusChanged (overview differences 4)

## Technical notes (agent-owned)
Service: revalidation-service. Endpoints/events: POST /api/v1/batch/revalidation-sweeps, GET /api/v1/batch/revalidation-sweeps/{runId}/queue-file, GET /api/v1/revalidations, ProviderStatusChanged, RevalidationDue. Roles: ADMIN, SYSTEM, ANALYST, SUPERVISOR.
