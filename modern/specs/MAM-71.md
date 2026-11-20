---
key: MAM-71
localId: L-S050
epic: MAM-11
service: tmsis-service
type: Story
title: "Monthly T-MSIS provider extract in the legacy 120-byte layout (YYDDD period)"
slug: tms-extract
specVersion: 1
descriptionSha256: 9f0eba88bc67b7b609d7948dcf8fd6e923e5bc93dcede16a840c2698ba6030ee
jiraUpdated: 2026-11-18T09:21:08.447-0600
rules: [R-PRVTMS01-ADD-01, R-PRVTMS01-ADD-04, R-PRVTMS01-ADD-05, R-PRVTMS01-ADD-07, R-PRVTMS01-cbl-00002, R-PRVTMS01-cbl-00003, R-PRVTMS01-cbl-00004, R-PRVTMS01-cbl-00007, R-PRVTMS01-cbl-00008, R-PRVTMS01-cbl-00009, R-PRVTMS01-cbl-00010, R-PRVTMS01-cbl-00011, R-PRVTMS01-cbl-00012, R-PRVTMS01-cbl-00020, R-PRVTMS01-cbl-00021, R-PRVTMS01-cbl-00022, R-PRVTMS01-cbl-00023, R-PRVTMS01-cbl-00024, R-PRVTMS01-cbl-00025, R-PRVTMS01-cbl-00026, R-PRVTMS01-cbl-00027, R-PRVTMS01-cbl-00028, R-PRVTMS01-cbl-00029, R-PRVTMS01-cbl-00030, R-PRVTMS01-cbl-00031, R-PRVTMS01-cbl-00032, R-PRVTMS01-cbl-00038, R-PRVTMS01-cbl-00039, R-PRVTMS01-cbl-00040, R-PRVTMS01-cbl-00041, R-PRVTMS01-cbl-00042, R-PRVTMS01-cbl-00043, R-PRVTMS01-cbl-00044, R-PRVTMS01-cbl-00045, R-PRVTMS01-cbl-00046, R-PRVTMS01-cbl-00047, R-PRVTMS01-cbl-00048, R-PRVTMS01-cbl-00049, R-PRVTMS01-cbl-00050, R-PRVTMS01-cbl-00051]
decisions: [D-015, D-020, D-021, D-004, D-006, D-014]
dependsOn: [MAM-43, MAM-42, MAM-37]
mainJourney: false
labels: [svc-tmsis-service, kag-L-S050, agentic, decision-D-015, decision-D-020, decision-D-021, decision-D-004, decision-D-006, decision-D-014]
builtCommit:
testsSha256:
---
## Business requirement
Each month the state reports its enrolled providers to the federal Medicaid statistical data programme (T-MSIS). The monthly provider file must list every active provider, one fixed-width record per provider, in exactly the layout the legacy job produced, so the new file can be compared byte for byte with the old one. Each record carries the Medicaid ID, the NPI, an active status and a reporting period written the legacy way, as a two-digit year followed by the day of the year, taken from the business date of the run. Providers who are pending, denied, suspended or terminated are not reported.

## Acceptance criteria
### AC-1 Only ACTIVE providers are reported
Given provider-service holds ACTIVE providers NPI 1003000333 and NPI 1003000111, and non-active providers NPI 1003000444 (D denied), 1003000555 (P pending), 1003000777 (S suspended) and 1003000888 (T terminated)
When an ADMIN runs POST /api/v1/batch/tmsis-extracts for businessDate 2026-03-31 and downloads GET /api/v1/batch/tmsis-extracts/{runId}/file
Then the file has exactly two records, NPI 1003000111 first and NPI 1003000333 second (ascending NPI, the key order in which the legacy read its master), no record for the four non-active providers and no header or trailer record.
Basis: R-PRVTMS01-cbl-00004, R-PRVTMS01-cbl-00030, R-PRVTMS01-cbl-00031, R-PRVTMS01-cbl-00032, R-PRVTMS01-ADD-04, R-PRVTMS01-cbl-00003, R-PRVTMS01-cbl-00026, Q-007, D-006, modern/docs/analysis/job-flow.md (PRVMTH01 STEP020), OQ-D4

### AC-2 The record has the legacy 120-byte layout
Given a completed run whose file holds a record for NPI 1003000111 (Medicaid ID M00000111)
When the ADMIN downloads the file and reads that record
Then the record is 120 bytes: bytes 1-5 'PRV01', bytes 6-14 'M00000111' copied unchanged, bytes 15-24 '1003000111' copied unchanged, byte 25 'A' (also for every other record), bytes 26-30 the period and bytes 31-120 spaces.
Basis: R-PRVTMS01-cbl-00007, R-PRVTMS01-cbl-00008, R-PRVTMS01-cbl-00009, R-PRVTMS01-cbl-00011, R-PRVTMS01-cbl-00012, R-PRVTMS01-cbl-00038, R-PRVTMS01-cbl-00039, R-PRVTMS01-cbl-00040, R-PRVTMS01-cbl-00044, R-PRVTMS01-cbl-00045, R-PRVTMS01-cbl-00046, R-PRVTMS01-cbl-00047, R-PRVTMS01-cbl-00049, R-PRVTMS01-cbl-00050, R-PRVTMS01-cbl-00051, R-PRVTMS01-ADD-05, Q-007, ADR-005, modern/docs/analysis/data-layouts.md (PRVTMS01:WS-TMSIS-DETAIL), OQ-D4

### AC-3 The period is the business date as an explicit YYDDD field
Given a run for businessDate 2026-03-31 started on another calendar day
When the ADMIN reads bytes 26-30 of every record in the run's file
Then every record holds '26090' (year 26, day of the year 090), the same for the whole run, taken from the business date and not from the system date.
Basis: D-015, D-020, D-021, R-PRVTMS01-ADD-01, R-PRVTMS01-cbl-00010, R-PRVTMS01-cbl-00041, R-PRVTMS01-cbl-00042, R-PRVTMS01-cbl-00048, R-PRVTMS01-cbl-00002, R-PRVTMS01-cbl-00020, R-PRVTMS01-cbl-00021, R-PRVTMS01-cbl-00022, Q-007, ADR-009 (YYDDD only inside the legacy T-MSIS extract)

### AC-4 The period names a day, not a month, and handles edge dates
Given runs for businessDate 2026-01-05, 2026-03-30 and 2028-12-31
When the ADMIN reads the period bytes (26-30) of a record in each file
Then they are '26005' (day number padded to three digits), '26089' (different from the '26090' of 2026-03-31 although the month is the same) and '28366' (2028 is a leap year with 366 days).
Basis: D-015, D-004, R-PRVTMS01-ADD-07, R-PRVTMS01-cbl-00043, R-PRVTMS01-cbl-00018, R-PRVTMS01-cbl-00024, Q-007

### AC-5 Every ACTIVE provider is read, however many there are
Given provider-service holds 450 synthetic ACTIVE providers (NPI 1003001001 to 1003001450), more than two pages of 200
When a run for 2026-03-31 completes and the ADMIN downloads its file
Then the file has 450 records, each NPI exactly once, in ascending NPI order; no provider is missed or repeated.
Basis: R-PRVTMS01-cbl-00003, R-PRVTMS01-cbl-00026, R-PRVTMS01-cbl-00027, R-PRVTMS01-cbl-00028, R-PRVTMS01-cbl-00029, R-PRVTMS01-cbl-00025, R-PRVTMS01-cbl-00023, modern/contracts/openapi/provider-service.yaml#searchProviders (page size at most 200)

### AC-6 Providers are read through provider-service's API
Given tmsis-service owns only its own database and a SYSTEM service account
When a run for 2026-03-31 starts
Then provider-service receives GET /api/v1/providers with status=A, page after page, authenticated with the SYSTEM service token; tmsis-service holds only extract data of its own and never reads the provider database.
Basis: D-014, ADR-002, ADR-007, modern/docs/architecture/overview.md (flow 7), modern/docs/architecture/nfr.md (GET /api/v1/providers allows SYSTEM)

## Out of scope
- The real CMS T-MSIS provider file format and a reporting month: the legacy extract is kept as it is until the business supplies the CMS specification (Q-007 option B)
- Reporting providers that are not ACTIVE or any status other than A (Q-007, R-PRVTMS01-ADD-04)
- The run result, archive generations and the ExtractProduced event (tms-run-archive)
- The future-enrolment warning (tms-future-enrolment)
- The legacy compile defect of the period field name past column 72: it only concerns the Phase 9 Hercules run (D-021)
- Two-digit years from the year 2100 on: YYDDD is kept only because the legacy file has it (D-015)

## Legacy trace (agent-owned, do not edit)
Rules: R-PRVTMS01-ADD-01, R-PRVTMS01-ADD-04, R-PRVTMS01-ADD-05, R-PRVTMS01-ADD-07, R-PRVTMS01-cbl-00002, R-PRVTMS01-cbl-00003, R-PRVTMS01-cbl-00004, R-PRVTMS01-cbl-00007, R-PRVTMS01-cbl-00008, R-PRVTMS01-cbl-00009, R-PRVTMS01-cbl-00010, R-PRVTMS01-cbl-00011, R-PRVTMS01-cbl-00012, R-PRVTMS01-cbl-00020, R-PRVTMS01-cbl-00021, R-PRVTMS01-cbl-00022, R-PRVTMS01-cbl-00023, R-PRVTMS01-cbl-00024, R-PRVTMS01-cbl-00025, R-PRVTMS01-cbl-00026, R-PRVTMS01-cbl-00027, R-PRVTMS01-cbl-00028, R-PRVTMS01-cbl-00029, R-PRVTMS01-cbl-00030, R-PRVTMS01-cbl-00031, R-PRVTMS01-cbl-00032, R-PRVTMS01-cbl-00038, R-PRVTMS01-cbl-00039, R-PRVTMS01-cbl-00040, R-PRVTMS01-cbl-00041, R-PRVTMS01-cbl-00042, R-PRVTMS01-cbl-00043, R-PRVTMS01-cbl-00044, R-PRVTMS01-cbl-00045, R-PRVTMS01-cbl-00046, R-PRVTMS01-cbl-00047, R-PRVTMS01-cbl-00048, R-PRVTMS01-cbl-00049, R-PRVTMS01-cbl-00050, R-PRVTMS01-cbl-00051
Related rules: R-PRVTMS01-cbl-00001, R-PRVTMS01-cbl-00018, R-PRVTMS01-cbl-00019
Programs: PRVTMS01 (source-code/BATCH/PRVTMS01.cbl): EXTRACT-NEXT 91-100 (read 92-95, ACTIVE filter 97-99), WRITE-TMSIS-ROW 102-117 (field moves 111-114, status A 113, period 114, assemble 115, write 116), MAIN-CONTROL 80-82 (Julian date and its split), WS-TMSIS-DETAIL 67-73 (120-byte layout), WS-JULIAN-WORK 59-65. Job: PRVMTH01 STEP020 (seq 2), DD TMSISOUT = MMPRV.TMSIS.PROVIDER.D&DATE.
Decisions: D-015 PRESERVE in the legacy-format extract; ISO dates internally; D-020 FIX: the business date is an explicit job parameter; D-021 FIX: not applicable to the new system (the field is explicit); the Phase 9 Hercules run must patch the line; D-004 FIX: 4-digit dates everywhere (the two-digit year exists only inside the legacy file); D-006 A new SUSPENDED status (suspended providers are not reported); D-014 FIX: provider-service is the single owner (tmsis-service reads it through its API)

## Technical notes (agent-owned)
Service: tmsis-service. Endpoints/events: POST /api/v1/batch/tmsis-extracts, GET /api/v1/batch/tmsis-extracts/{runId}/file, GET /api/v1/providers. Roles: ADMIN, SYSTEM.
