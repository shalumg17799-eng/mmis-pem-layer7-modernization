---
key: MAM-81
localId: L-S072
epic: MAM-14
service: e2e
type: Story
title: "Journey J6 end to end: monthly revalidation"
slug: e2e-j6
specVersion: 1
descriptionSha256: c8260ace43be7cc9d803c8c00676efae9a91133c0051f172181fbaa83872a761
jiraUpdated: 2026-11-18T09:22:17.117-0600
rules: []
decisions: [D-003, D-029, D-020, D-024]
dependsOn: [MAM-48, MAM-45, MAM-28, MAM-93, MAM-85, MAM-50, MAM-51]
mainJourney: false
labels: [svc-e2e, kag-L-S072, agentic, decision-D-003, decision-D-029, decision-D-020, decision-D-024]
builtCommit:
testsSha256:
---
## Business requirement
Every month the administrator must be able to find the providers whose revalidation is due, see each of them once as a case, have them checked again automatically, and receive the legacy queue file. This story proves that monthly cycle works for real in the running system.

## Acceptance criteria
### AC-1 The administrator runs the sweep for a business date
Given admin1 is on /console/admin/jobs at the seed state
When admin1 runs the revalidation sweep for the business date 03/31/2026
Then POST /api/v1/batch/revalidation-sweeps with businessDate 2026-03-31 returns a run that ends COMPLETED, shows a queued count equal to the number of ACTIVE providers whose due date is on or before 2026-03-31, and shows no failure reason
Basis: modern/docs/ux/journeys.md#J6 step 1, D-020, D-024, R-PRVREV01-cbl-00003, R-PRVREV01-cbl-00004

### AC-2 Each due provider has exactly one case
Given the completed sweep
When analyst1 opens /console/queues and GET /api/v1/revalidations?status=DUE is called
Then each due provider appears once under Revalidation due, and no provider that is not due or not ACTIVE has a case
Basis: modern/docs/ux/journeys.md#J6 step 2, D-029

### AC-3 Running the sweep again does not duplicate cases
Given the same sweep was already run for 2026-03-31
When admin1 runs it again for the same date
Then the second run completes and the number of cases per provider is still one
Basis: D-029, modern/docs/architecture/nfr.md (Availability: batch jobs are idempotent), modern/docs/adr/ADR-004-batch-and-scheduling.md

### AC-4 Re-screening completes the case and sets the next due date
Given the cases are DUE
When RevalidationDue events are handled and re-screening passes
Then pem.screening.events carries ScreeningCompleted with reason REVALIDATION and result PASS for each due provider, each case becomes COMPLETED, and GET /api/v1/providers/{npi} shows a new revalidation due date later than 2026-03-31
Basis: modern/docs/ux/journeys.md#J6 step 3, D-003, modern/docs/architecture/overview.md (main flow 4), modern/contracts/asyncapi/events.yaml (RevalidationDue, ScreeningCompleted), modern/contracts/openapi/revalidation-service.yaml (RevalidationCase status)

### AC-5 The REVALQ file can be downloaded
Given the completed sweep run
When admin1 downloads GET /api/v1/batch/revalidation-sweeps/{runId}/queue-file
Then every record is 80 bytes with NPI in bytes 1-10, the due date in bytes 11-18 as CCYYMMDD, the text RESCREEN-REQUIRED starting at byte 19 and spaces to byte 80, and the number of records equals the queued count
Basis: modern/docs/ux/journeys.md#J6 step 4, modern/docs/analysis/data-layouts.md (REVAL-OUT-RECORD), R-PRVREV01-cbl-00005, D-004, modern/docs/adr/ADR-005-legacy-format-adapters.md

### AC-6 Repeatable and accessible
Given the journey as one Playwright test with an axe scan on every page it visits
When it is run three times in a row, each time from the seed state
Then all three runs pass and every axe scan reports zero violations
Basis: .claude/commands/phase-8-e2e-hardening.md steps 2 and 4, modern/docs/architecture/nfr.md (Accessibility), QF-6 (default applied)

## Out of scope
- Failed re-screening and its follow-up
- Scheduled (cron) runs of the sweep
- Revalidation notices (not part of the journey)

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: BATCH/PRVREV01.cbl:64-94 (sweep of ACTIVE providers due today or earlier, no state kept; D-029); BATCH/PRVRSK01.cbl:70-76,126-137 (due date reset on every run, D-003); JCL/PRVMTH01.jcl:14-22 (STEP010)
Decisions: D-003 FIX: the next due date is set when a revalidation is completed; D-029 FIX: one revalidation case per provider with its own state; D-020 FIX: the business date 2026-03-31 is an explicit job parameter; D-024 FIX: the run result shows its status, counts and failure reason

## Technical notes (agent-owned)
Service: e2e. Endpoints/events: POST /api/v1/batch/revalidation-sweeps, GET /api/v1/revalidations, GET /api/v1/batch/revalidation-sweeps/{runId}/queue-file, GET /api/v1/providers/{npi}, RevalidationDue, ScreeningCompleted, ProviderStatusChanged. Roles: ADMIN, ANALYST, SYSTEM.
