---
key: MAM-83
localId: L-S073
epic: MAM-14
service: e2e
type: Story
title: "Journey J7 end to end: month-end extracts and the public directory"
slug: e2e-j7
specVersion: 1
descriptionSha256: 2116225748f565308ebf79ab1bb659bd683eb3682d830169c1c0e323751b9da0
jiraUpdated: 2026-11-18T09:22:31.758-0600
rules: []
decisions: [D-015, D-028, D-020]
dependsOn: [MAM-75, MAM-71, MAM-67, MAM-63, MAM-93, MAM-73, MAM-50, MAM-51]
mainJourney: false
labels: [svc-e2e, kag-L-S073, agentic, decision-D-015, decision-D-028, decision-D-020]
builtCommit: pending-user-commit
testsSha256: 772dfe529540fc201d4d61994c64b54bda49db726962f53fcd5963a176c8b8d4
---
## Business requirement
At month end the administrator must be able to produce the federal reporting file and the managed-care directory file in their legacy formats, with the reporting file archived, and anyone must be able to find active providers in the public directory with properly written names. This story proves that works for real in the running system.

## Acceptance criteria
### AC-1 The T-MSIS extract is produced in the legacy layout
Given admin1 is on /console/admin/jobs at the seed state
When admin1 runs the T-MSIS extract for 03/31/2026 and downloads the TMSISOUT file
Then POST /api/v1/batch/tmsis-extracts with businessDate 2026-03-31 returns a run that ends COMPLETED, and every record of the file is 120 bytes with the record type PRV01 in bytes 1-5, the Medicaid ID in bytes 6-14, the NPI in bytes 15-24, status A in byte 25, the period 26090 in bytes 26-30 and spaces in bytes 31-120
Basis: modern/docs/ux/journeys.md#J7 step 1, D-015, D-020, Q-007, R-PRVTMS01-cbl-00043, modern/docs/analysis/data-layouts.md (WS-TMSIS-DETAIL)

### AC-2 The run is archived and the event is published
Given the completed extract run
When GET /api/v1/batch/tmsis-extracts is called and pem.tmsis.events is read
Then the list shows the run (COMPLETED, business date 2026-03-31) as a new entry next to any earlier runs, and ExtractProduced carries the business date 2026-03-31, periodYyddd 26090 and a rowCount equal to the number of records in the downloaded file
Basis: modern/docs/ux/journeys.md#J7 step 1, modern/contracts/openapi/tmsis-service.yaml (listTmsisExtracts: runs and archive generations, GDG MMPRV.TMSIS.ARCHIVE), modern/contracts/asyncapi/events.yaml (ExtractProduced), D-015

### AC-3 The directory extract is produced in the legacy layout
Given admin1 is on /console/admin/jobs
When admin1 runs the directory extract for 03/31/2026 and downloads the DIROUT file
Then POST /api/v1/batch/directory-extracts with businessDate 2026-03-31 returns a run that ends COMPLETED and every record is 100 bytes with the Medicaid ID in bytes 1-9, the NPI in 10-19, the type in 20-21, the risk tier in 22, the name in 23-82 and spaces in 83-100
Basis: modern/docs/ux/journeys.md#J7 step 2, modern/docs/analysis/data-layouts.md (DIR-OUT-RECORD), Q-008, D-020

### AC-4 Names are composed, not raw bytes
Given the DIROUT record of the seeded individual TESTPROV ALPHA
When its name field is read
Then it contains the first name TESTPROV and then the last name ALPHA and no part of the licence number, and the record of a seeded organisation shows its legal name
Basis: D-028, modern/docs/ux/journeys.md#J7 step 3, R-PRVDIR01-ADD-02

### AC-5 Only ACTIVE providers are in both files
Given the seeded providers in every status
When the NPIs in the TMSISOUT and DIROUT files are compared with GET /api/v1/providers?status=A
Then both files contain exactly the ACTIVE providers and none that is Pending, Terminated, Denied or Suspended
Basis: R-PRVTMS01-cbl-00004, R-PRVDIR01-cbl-00004, modern/docs/architecture/overview.md (main flows 6 and 7), D-006

### AC-6 Anyone can search the public directory for HOSPICE
Given a visitor with no sign-in
When the visitor searches HOSPICE on /directory and GET /api/v1/directory/providers?name=HOSPICE is called with no token
Then only ACTIVE providers whose names contain HOSPICE are listed, each with a properly composed name, and the seeded TERMINATED hospice is not listed
Basis: modern/docs/ux/journeys.md#J7 step 3, D-028, Q-008, modern/docs/adr/ADR-007-authentication-and-roles.md

### AC-7 Repeatable and accessible
Given the journey as one Playwright test with an axe scan on every page it visits
When it is run three times in a row, each time from the seed state
Then all three runs pass and every axe scan reports zero violations
Basis: .claude/commands/phase-8-e2e-hardening.md steps 2 and 4, modern/docs/architecture/nfr.md (Accessibility), QF-6 (default applied)

## Out of scope
- The real CMS T-MSIS file layout (Q-007: later story)
- Address, phone and network data in the directory (Q-008)
- The FHIR resources (dir-fhir)
- The T-MSIS future-enrolment flag (tms-future-enrolment)

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: BATCH/PRVTMS01.cbl:60-62,80,97-117 (ACTIVE only, status A, period YYDDD of the run date; D-015); BATCH/PRVDIR01.cbl:62-90 (ACTIVE only, name = first 60 bytes of the variant at :88; D-028, D-013); JCL/PRVMTH01.jcl:25-45 (STEP020, STEP030)
Decisions: D-015 PRESERVE: the legacy T-MSIS extract keeps the YYDDD period (26090 for business date 2026-03-31); D-028 FIX: directory names are composed from the name fields; D-020 FIX: the business date is an explicit job parameter

## Technical notes (agent-owned)
Service: e2e. Endpoints/events: POST /api/v1/batch/tmsis-extracts, GET /api/v1/batch/tmsis-extracts, GET /api/v1/batch/tmsis-extracts/{runId}/file, POST /api/v1/batch/directory-extracts, GET /api/v1/batch/directory-extracts/{runId}/file, GET /api/v1/directory/providers, GET /api/v1/providers, ExtractProduced. Roles: ADMIN, PUBLIC.
