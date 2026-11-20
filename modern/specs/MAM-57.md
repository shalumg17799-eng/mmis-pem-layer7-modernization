---
key: MAM-57
localId: L-S028
epic: MAM-4
service: enrollment-service
type: Story
title: "Report the result of an intake run: exclusive counts, completion, failures and safe re-runs"
slug: enr-intake-run-2
specVersion: 1
descriptionSha256: 6666e4533cb4ca76442aac12bc6effbdd2b728698da8e2d772b0bc26e3be5c10
jiraUpdated: 2026-11-18T09:18:51.462-0600
rules: [R-PRVENR01-ADD-03, R-PRVENR01-ADD-07, R-PRVENR01-ADD-08, R-PRVENR01-cbl-00008, R-PRVENR01-cbl-00009, R-PRVENR01-cbl-00011, R-PRVENR01-cbl-00013, R-PRVENR01-cbl-00021, R-PRVENR01-cbl-00023, R-PRVENR01-cbl-00026, R-PRVENR01-cbl-00031, R-PRVENR01-cbl-00036, R-PRVENR01-cbl-00057, R-PRVENR01-cbl-00059, R-PRVENR01-cbl-00060, R-PRVENR01-cbl-00061, R-PRVENR01-cbl-00062, R-PRVENR01-cbl-00063, R-PRVENR01-cbl-00064, R-PRVENR01-cbl-00065, R-PRVENR01-cbl-00066, R-PRVENR01-cbl-00067, R-PRVENR01-cbl-00068]
decisions: [D-001, D-016, D-023, D-024]
dependsOn: [MAM-56]
mainJourney: false
labels: [svc-enrollment-service, kag-L-S028, agentic, decision-D-001, decision-D-016, decision-D-023, decision-D-024]
builtCommit:
testsSha256:
---
## Business requirement
After an intake run, operations need an honest result: how many applications were read, accepted, rejected and skipped, and whether the run finished cleanly, finished with rejected applications, or failed and why. The old job counted one application several times, hid a hard error behind a warning code and could loop forever on a bad record. The new run reports counts that add up and an explicit result, and a run can be repeated for the same business date without loading anything twice.

## Acceptance criteria
### AC-1 A clean run reports its counts, and each run starts from zero
Given A first run for business date 2026-03-31 of a file with three valid applications (NPIs 1003000111, 1003000222, 1003000333), followed by a run for business date 2026-04-01 of a file with one valid application for NPI 1003000777
When Both runs complete and an ADMIN calls GET /api/v1/batch/intake-runs/{runId} for each
Then The first shows status COMPLETED, endedAt set, error null and counts read 3, written 3, errored 0, skipped 0; the second shows read 1, written 1, errored 0, skipped 0, so nothing is carried over from the first run
Basis: R-PRVENR01-cbl-00009, R-PRVENR01-cbl-00011, R-PRVENR01-cbl-00021, R-PRVENR01-cbl-00057, R-PRVENR01-cbl-00061, R-PRVENR01-cbl-00062, R-PRVENR01-cbl-00067, D-024, modern/contracts/openapi/enrollment-service.yaml#getIntakeRun

### AC-2 Every record is counted exactly once
Given A file of four records: valid applications for NPIs 1003000111 and 1003000777, one with a blank NPI, and one for NPI 1003000222 with type XX and an empty taxonomy (two failed checks)
When The run completes and an ADMIN reads the run and downloads its error file
Then counts are read 4, written 2, errored 2, skipped 0, so that read equals written plus errored plus skipped; the record with two failed checks is counted once, not twice; the downloaded error file is complete and holds exactly 2 rows
Basis: R-PRVENR01-cbl-00026, R-PRVENR01-cbl-00031, R-PRVENR01-cbl-00036, R-PRVENR01-cbl-00060, R-PRVENR01-cbl-00063, R-PRVENR01-ADD-08, D-024, D-001

### AC-3 A duplicate NPI is counted as skipped
Given A file holding NPI 1003000111 (no earlier application), the same NPI 1003000111 again, and NPI 1003000222
When The run completes
Then The first 1003000111 record and 1003000222 are VALIDATED; the second 1003000111 record is REJECTED with exactly one error E103 and has one APPERROR row; counts are read 3, written 2, errored 0, skipped 1; the first application is unchanged and exactly one ApplicationValidated event exists for 1003000111
Basis: R-PRVENR01-ADD-04, R-PRVENR01-cbl-00008, R-PRVENR01-cbl-00013, R-PRVENR01-cbl-00064, D-016, Q-B3

### AC-4 A run with rejected applications completes, not as a failure
Given A file with two valid applications and one rejected application
When The run completes
Then GET /api/v1/batch/intake-runs/{runId} shows status COMPLETED (not FAILED), error null and counts.errored 1, the modern form of the legacy return code 4 'completed with warnings'; the two valid applications keep their VALIDATED status
Basis: R-PRVENR01-cbl-00065, R-PRVENR01-cbl-00066, R-PRVENR01-cbl-00068, D-024, Q-B8

### AC-5 An unreadable record ends the run with a stated failure
Given A file of four records whose third record is only 150 bytes long (the file is cut short)
When The run is started
Then The run does not stay RUNNING and never loops: GET /api/v1/batch/intake-runs/{runId} shows status FAILED, an error text naming record 3, endedAt set; records 1 and 2 keep their results and record 4 is not processed; counts are read 3, written 2, errored 1, skipped 0
Basis: R-PRVENR01-cbl-00023, R-PRVENR01-ADD-03, R-PRVENR01-ADD-08, D-024, Q-B8, Q-B14

### AC-6 A hard failure is never shown as a warning
Given A file of three records: an application for NPI 1003000222 with an empty taxonomy, then valid applications for NPIs 1003000111 and 1003000333, and an unexpected failure injected while the application for 1003000111 is being stored
When The run is started
Then The run's status is FAILED (not COMPLETED, although rejected applications alone would give COMPLETED) with an error text naming record 2 and NPI 1003000111; counts are read 2, written 0, errored 2, skipped 0; the REJECTED application for 1003000222 and its APPERROR row remain; no ApplicationValidated event is published for 1003000111 and record 3 is not processed
Basis: R-PRVENR01-cbl-00059, R-PRVENR01-ADD-07, D-024, Q-B8, Q-B14

### AC-7 Run results are for ADMIN only and unknown runs are not found
Given Tokens for ADMIN, SYSTEM, PROVIDER, ANALYST and SUPERVISOR, and a request without a token
When GET /api/v1/batch/intake-runs/{runId} is called, and again with the unknown runId 00000000-0000-0000-0000-000000000000
Then ADMIN gets 200; SYSTEM, PROVIDER, ANALYST and SUPERVISOR get 403 problem+json; a call without a token gets 401; the unknown runId returns 404 problem+json
Basis: D-023, ADR-007, modern/docs/architecture/nfr.md, modern/contracts/openapi/enrollment-service.yaml#getIntakeRun

### AC-8 Re-running a business date loads nothing twice
Given A run for business date 2026-03-31 of a file of four records (valid 1003000111, valid 1003000333, 1003000222 with an empty taxonomy, valid 1003000777) ended FAILED because of a storage failure injected at record 4, after records 1 to 3 were processed
When An ADMIN starts a new run for business date 2026-03-31 with the same file, and then a third run for the same date and file
Then The second run shows status COMPLETED with counts read 4, written 1, errored 0, skipped 3: only 1003000777 is newly VALIDATED with one ApplicationValidated event, the three records already processed on 2026-03-31 get no new application, no APPERROR row and no event, and its error file is empty; the third run shows read 4, written 0, errored 0, skipped 4 and publishes no event
Basis: modern/docs/architecture/nfr.md (Availability: batch jobs can be re-run for a business date and are idempotent), ADR-004, D-024, Q-B11, Q-B14, modern/docs/architecture/data-model/enrollment-service.sql (application.raw_record, intake_run_id)

## Out of scope
- Starting the run, reading the file and writing the APPERROR rows (enr-intake-run)
- Gating the later daily steps on the intake return code: the legacy skipped screening and risk when STEP010 ended with RC 4; here screening starts per provider from ProviderRegistered, so a run with rejections does not block it (overview.md main flows 1 and 2)
- Showing run failures in the console (ui-console-batch)
- A second load of the same file under a different business date: its valid applications are then duplicates (E103, skipped), as in AC-3

## Legacy trace (agent-owned, do not edit)
Rules: R-PRVENR01-ADD-03, R-PRVENR01-ADD-07, R-PRVENR01-ADD-08, R-PRVENR01-cbl-00008, R-PRVENR01-cbl-00009, R-PRVENR01-cbl-00011, R-PRVENR01-cbl-00013, R-PRVENR01-cbl-00021, R-PRVENR01-cbl-00023, R-PRVENR01-cbl-00026, R-PRVENR01-cbl-00031, R-PRVENR01-cbl-00036, R-PRVENR01-cbl-00057, R-PRVENR01-cbl-00059, R-PRVENR01-cbl-00060, R-PRVENR01-cbl-00061, R-PRVENR01-cbl-00062, R-PRVENR01-cbl-00063, R-PRVENR01-cbl-00064, R-PRVENR01-cbl-00065, R-PRVENR01-cbl-00066, R-PRVENR01-cbl-00067, R-PRVENR01-cbl-00068
Related rules: R-PRVENR01-ADD-04, R-PRVENR01-cbl-00051, R-PRVENR01-cbl-00058
Programs: PRVENR01 PROCESS-APPLICATIONS 102-116 (read error 111-114), WRITE-PROVIDER-RECORD 157-171 (status 00 written, 22 duplicate skipped, other error RC 8), TERMINATE-PROGRAM 181-189 (four counts, RC 4 when errored > 0); job PRVDAY01 STEP010 and the gate IF (STEP010.RC = 0) at JCL/PRVDAY01.jcl:38
Decisions: D-001 FIX: a rejected application is counted once and the run goes on; D-016 FIX: a duplicate NPI is the validation result E103; D-023 FIX: role check on the run result endpoint (ADMIN); D-024 FIX: exclusive counts, an explicit result per run (COMPLETED or FAILED with a reason), no endless loop, a hard error is never downgraded to a warning, a re-run for a business date is safe

## Technical notes (agent-owned)
Service: enrollment-service. Endpoints/events: GET /api/v1/batch/intake-runs/{runId}, ApplicationValidated, ApplicationRejected. Roles: ADMIN.
