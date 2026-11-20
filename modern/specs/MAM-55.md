---
key: MAM-55
localId: L-S042
epic: MAM-8
service: sanctions-service
type: Story
title: "Run the legacy SANCIN sanction intake as a batch job"
slug: san-intake-run
specVersion: 1
descriptionSha256: 6c6bfcc1e12c1e225ab0b814922fe87fe54dcb95098b2101982413346a1bb3f6
jiraUpdated: 2026-11-18T09:18:03.559-0600
rules: [R-PRVSAN01-ADD-06, R-PRVSAN01-cbl-00001, R-PRVSAN01-cbl-00010, R-PRVSAN01-cbl-00011, R-PRVSAN01-cbl-00012, R-PRVSAN01-cbl-00013, R-PRVSAN01-cbl-00017, R-PRVSAN01-cbl-00027, R-PRVSAN01-cbl-00054, R-PRVSAN01-cbl-00055, R-PRVSAN01-cbl-00056, R-PRVSAN01-cbl-00057, R-PRVSAN01-cbl-00058]
decisions: [D-004, D-006, D-013, D-018, D-020, D-023, D-024, D-026, D-027]
dependsOn: [MAM-52, MAM-42]
mainJourney: false
labels: [svc-sanctions-service, kag-L-S042, agentic, decision-D-004, decision-D-006, decision-D-013, decision-D-018, decision-D-020, decision-D-023, decision-D-024, decision-D-026, decision-D-027]
builtCommit:
testsSha256:
---
## Business requirement
Sanction actions also arrive in the old fixed-width sanction file. An administrator or a scheduler can load such a file for a stated business date. Every record is applied like a sanction entered by a supervisor. Records for unknown providers, with unknown actions or with an invalid date are counted and skipped without stopping the run, the number of terminations is reported as before, the run ends with an explicit result that says whether it completed or failed and why, and running the same business date again applies nothing twice.

## Acceptance criteria
### AC-1 A sanction intake run starts with an explicit business date
Given An ADMIN token and a SANCIN file of 80-byte records
When POST /api/v1/batch/sanction-intake-runs is called with the file and businessDate 2026-03-31
Then 202 with a BatchRun that has a runId, a job, businessDate 2026-03-31 and status RUNNING or COMPLETED; GET /api/v1/batch/sanction-intake-runs/{runId} returns the same businessDate; a call without businessDate or without the file returns 400 problem+json and creates no run
Basis: R-PRVSAN01-cbl-00011, D-020, modern/contracts/openapi/sanctions-service.yaml#startSanctionIntakeRun

### AC-2 Each 80-byte record is read from its fixed positions until end of file
Given A SANCIN file whose records hold the NPI in bytes 1-10, the action in byte 11, the reason in bytes 12-51, the effective date as CCYYMMDD in bytes 52-59 and filler in bytes 60-80, for example NPI 1003000111, action T, reason 'SYNTHETIC TEST REASON', date 20260331
When The run completes
Then Every record is read in file order through the last one and counts.read equals the number of records; GET /api/v1/sanctions?npi=1003000111 shows effectiveDate 2026-03-31 (the 4-digit-year date stored as an ISO date) and reason 'SYNTHETIC TEST REASON' (bytes 12-51 without trailing spaces)
Basis: R-PRVSAN01-cbl-00001, R-PRVSAN01-cbl-00011, R-PRVSAN01-cbl-00012, R-PRVSAN01-cbl-00013, R-PRVSAN01-cbl-00056, R-PRVSAN01-cbl-00057, D-004, modern/docs/analysis/data-layouts.md (PRVSAN01:SANCTION-IN-RECORD)

### AC-3 Each record is applied through provider-service and kept in the history
Given ACTIVE provider 1003000111, ACTIVE provider 1003000333 and suspended provider 1003000222, and a file with T for 1003000111, S for 1003000333 and R for 1003000222
When The run completes
Then The providers show status T, S and A respectively, set only through provider-service's POST /api/v1/providers/{npi}/status-changes; GET /api/v1/sanctions shows one history row per record with sourceFeed SANCIN and recordedBy the account that started the run; one SanctionApplied event is published per record
Basis: R-PRVSAN01-cbl-00017, D-013, D-018, D-006, D-027, modern/docs/analysis/programs/PRVSAN01.md (DD SANCIN)

### AC-4 Records that cannot be applied are counted, the run goes on and completes
Given A file of five records: a valid T for 1003000111, a T for NPI 1003000999 that no provider has, action X for 1003000333, a valid action for 1003000333 with effective date 20261331 (not a valid date), and a valid T for 1003000222
When The run ends
Then GET /api/v1/batch/sanction-intake-runs/{runId} shows status COMPLETED, endedAt set, error null and counts read 5, applied 2, notFound 1, rejected 2; history rows and SanctionApplied events exist only for the two applied records (1003000111 and 1003000222); provider 1003000333 is unchanged; the service's JSON log contains the line 'PRVSAN01: NPI NOT FOUND 1003000999'; the records after each skipped one are still processed
Basis: R-PRVSAN01-ADD-06, R-PRVSAN01-ADD-02, R-PRVSAN01-ADD-01, R-PRVSAN01-cbl-00003, R-PRVSAN01-cbl-00012, R-PRVSAN01-cbl-00021, R-PRVSAN01-cbl-00023, R-PRVSAN01-cbl-00024, D-024, D-026, Q-B12

### AC-5 Only terminations are counted as terminated
Given All providers exist, and a file with T for 1003000111, S for 1003000333, R for 1003000222 and T for 1003000555
When The run completes
Then counts.applied is 4 and counts.terminated is 2 (S and R do not count); the service's JSON log contains the line 'PRVSAN01: PROVIDERS TERMINATED=00002' (the legacy 5-digit counter)
Basis: R-PRVSAN01-cbl-00027, R-PRVSAN01-cbl-00054, R-PRVSAN01-cbl-00055, R-PRVSAN01-cbl-00010, R-PRVSAN01-cbl-00058, Q-B8

### AC-6 An unreadable record or a failure to apply ends the run with a stated failure
Given A file of three valid records whose second record is only 60 bytes long, and in another run a file of three valid records where provider-service answers with a server error for the NPI of record 2 (fault injected)
When Each run is started
Then Each run does not stay RUNNING: status FAILED, endedAt set and an error text naming record 2 (and its NPI in the second case); record 1 of each run keeps its result (history row and event) and counts.applied is 1; record 3 is not processed; the failing sanction is not recorded as applied and has no SanctionApplied event
Basis: R-PRVSAN01-ADD-01, R-PRVSAN01-ADD-05, D-024, Q-B14

### AC-7 Re-running a business date applies nothing twice
Given A run for business date 2026-03-31 of a file with T for 1003000111 and S for 1003000333 completed, and the same file is loaded again for business date 2026-03-31
When The second run completes
Then Status COMPLETED with counts read 2, applied 0, skipped 2; no new history row and no SanctionApplied event is created, so no second notice is triggered; the providers keep status T and S
Basis: modern/docs/architecture/nfr.md (Availability: batch jobs can be re-run for a business date and are idempotent), ADR-004, D-024, D-018, Q-B11

### AC-8 Only ADMIN and SYSTEM may start a run and only ADMIN may read its result
Given Tokens for ADMIN, SYSTEM, ANALYST, SUPERVISOR and PROVIDER, and a request without a token
When POST /api/v1/batch/sanction-intake-runs and GET /api/v1/batch/sanction-intake-runs/{runId} are called, and GET with the unknown runId 00000000-0000-0000-0000-000000000000
Then POST returns 202 for ADMIN and SYSTEM, 403 problem+json for the others, 401 without a token; GET returns 200 for ADMIN, 403 for every other role and 401 without a token; the unknown runId returns 404 problem+json
Basis: D-023, ADR-007, modern/docs/architecture/nfr.md, modern/contracts/openapi/sanctions-service.yaml#getSanctionIntakeRun

## Out of scope
- Applying one sanction: the rules for T, S and R, history and the apply call are in san-record and are reused here
- Any behaviour for batchId: the legacy PRVSAN01 has no batch id; the field exists only because the contract shares one request shape with the intake run
- A cron-scheduled start: no file location is defined for it (Q-B9)
- Run order against the directory and T-MSIS jobs, which the legacy never scheduled (job-flow.md, implicit order)
- The console batch-jobs screen (ui-console-batch)

## Legacy trace (agent-owned, do not edit)
Rules: R-PRVSAN01-ADD-06, R-PRVSAN01-cbl-00001, R-PRVSAN01-cbl-00010, R-PRVSAN01-cbl-00011, R-PRVSAN01-cbl-00012, R-PRVSAN01-cbl-00013, R-PRVSAN01-cbl-00017, R-PRVSAN01-cbl-00027, R-PRVSAN01-cbl-00054, R-PRVSAN01-cbl-00055, R-PRVSAN01-cbl-00056, R-PRVSAN01-cbl-00057, R-PRVSAN01-cbl-00058
Related rules: R-PRVSAN01-ADD-01, R-PRVSAN01-ADD-02, R-PRVSAN01-ADD-05, R-PRVSAN01-cbl-00003, R-PRVSAN01-cbl-00021, R-PRVSAN01-cbl-00023, R-PRVSAN01-cbl-00024, R-PRVSAN01-cbl-00025, R-PRVSAN01-cbl-00029, R-PRVSAN01-cbl-00033
Programs: PRVSAN01 MAIN-CONTROL 79-87 (loop and final count 85-86, WS-TERM-COUNT PIC 9(05) at 76), PROCESS-SANCTION 89-102, APPLY-SANCTION 104-116 (termination count 107-108); input SANCIN 80 bytes (SANCTION-IN-RECORD); no JCL job runs PRVSAN01 and no JCL defines the SANCIN DD
Decisions: D-004 FIX: the CCYYMMDD effective date is stored as a 4-digit-year ISO date; D-006 FIX: S sets SUSPENDED; D-013 FIX: the status is changed only through provider-service's API; D-018 FIX: every record applied is kept in the sanction history, once; D-020 FIX: the business date is an explicit job parameter; D-023 FIX: role check on the run endpoints (POST ADMIN, SYSTEM; GET ADMIN); D-024 FIX: not-found and rejected records are counted, the run has an explicit result, a failure is surfaced with a reason, a re-run for a business date is safe; D-026 FIX: unknown action codes are rejected and counted; D-027 PRESERVE: each action is applied whatever the current status

## Technical notes (agent-owned)
Service: sanctions-service. Endpoints/events: POST /api/v1/batch/sanction-intake-runs, GET /api/v1/batch/sanction-intake-runs/{runId}, SanctionApplied. Roles: ADMIN, SYSTEM.
