---
key: MAM-78
localId: L-S052
epic: MAM-11
service: tmsis-service
type: Story
title: "Warn about providers whose enrolment year is later than the business year"
slug: tms-future-enrolment
specVersion: 1
descriptionSha256: 102945b67d6536353b54c2308c6f9d3b818fb8c217236751870c29e0e142e9f0
jiraUpdated: 2026-11-18T09:21:43.966-0600
rules: [R-PRVTMS01-ADD-02, R-PRVTMS01-ADD-03, R-PRVTMS01-cbl-00005, R-PRVTMS01-cbl-00006, R-PRVTMS01-cbl-00033, R-PRVTMS01-cbl-00034, R-PRVTMS01-cbl-00035, R-PRVTMS01-cbl-00036, R-PRVTMS01-cbl-00037]
decisions: [D-004, D-020, D-015]
dependsOn: [MAM-71]
mainJourney: false
labels: [svc-tmsis-service, kag-L-S052, agentic, decision-D-004, decision-D-020, decision-D-015]
builtCommit: 7aa7820
testsSha256: 5c4419f69bc2ff7bf8d0b2a8bb9a2ba85f2957a3f9d7819bd5dac253785fc9be
---
## Business requirement
While preparing the monthly T-MSIS file, the system checks the enrolment start date of each active provider. When the year of that date is later than the year of the run's business date, the date is probably a data-entry mistake, so the system raises a warning that names the provider. The warning never stops the provider from being reported. The check must work for any year: the legacy check compared only two-digit years, so it wrongly warned about every provider enrolled before the year 2000 and could never warn about a date after 2099.

## Acceptance criteria
### AC-1 A later enrolment year raises a warning that names the provider
Given provider-service returns NPI 1003000777 as ACTIVE with effectiveDate 2027-01-15
When the ADMIN runs POST /api/v1/batch/tmsis-extracts for businessDate 2026-03-31
Then the run's JSON log holds exactly one line 'PRVTMS01: FUTURE ENROLLMENT? NPI 1003000777' and the run in GET /api/v1/batch/tmsis-extracts shows counts.futureEnrolments 1.
Basis: R-PRVTMS01-cbl-00005, R-PRVTMS01-cbl-00006, R-PRVTMS01-cbl-00035, R-PRVTMS01-cbl-00036, R-PRVTMS01-cbl-00037, D-004, D-020, OQ-D7, OQ-D9

### AC-2 The flagged provider is still reported unchanged
Given the run of AC-1 for businessDate 2026-03-31
When the ADMIN downloads GET /api/v1/batch/tmsis-extracts/{runId}/file
Then the file still holds the record for NPI 1003000777 with status A and period 26090, counted in counts.rows: the warning excludes nothing and alters nothing.
Basis: R-PRVTMS01-cbl-00006, R-PRVTMS01-cbl-00036, R-PRVTMS01-cbl-00037, D-015

### AC-3 Only a year strictly later than the business year is flagged
Given ACTIVE providers NPI 1003000111 effective 2026-03-31, NPI 1003000333 effective 2026-12-31 and NPI 1003000777 effective 2027-01-01
When a run for businessDate 2026-03-31 completes
Then only NPI 1003000777 (year 2027) is flagged and counts.futureEnrolments is 1; NPI 1003000333 is not flagged although its date is later in the same year 2026, and NPI 1003000111 is not flagged.
Basis: R-PRVTMS01-cbl-00005, R-PRVTMS01-cbl-00035, D-004, OQ-D9

### AC-4 Enrolments before the year 2000 are not flagged
Given NPI 1003000333 is ACTIVE with effectiveDate 1998-05-01
When a run for businessDate 2026-03-31 completes
Then no warning line names NPI 1003000333 and counts.futureEnrolments does not count it (the legacy two-digit test, 98 greater than 26, flagged it); its record is in the file.
Basis: D-004, R-PRVTMS01-ADD-02, R-PRVTMS01-cbl-00005, R-PRVTMS01-cbl-00033

### AC-5 Years after 2099 are flagged
Given NPI 1003000888 is ACTIVE with effectiveDate 2101-01-01
When a run for businessDate 2026-03-31 completes
Then the run's log holds 'PRVTMS01: FUTURE ENROLLMENT? NPI 1003000888' and counts.futureEnrolments counts it (the legacy two-digit test, 01 not greater than 26, could never flag it).
Basis: D-004, R-PRVTMS01-ADD-02, R-PRVTMS01-cbl-00034, R-PRVTMS01-cbl-00035

### AC-6 Only ACTIVE providers are checked
Given NPI 1003000555 is pending (P) with effectiveDate 2027-01-01
When a run for businessDate 2026-03-31 completes
Then NPI 1003000555 has no record in the file, no warning line and no count in counts.futureEnrolments: only reported (ACTIVE) providers are evaluated.
Basis: R-PRVTMS01-cbl-00004, R-PRVTMS01-ADD-04, Q-007

## Out of scope
- Excluding the flagged provider from the file or changing its record: the legacy only warns (R-PRVTMS01-cbl-00006)
- A month or day comparison of the enrolment date: the legacy compares years only, and D-004 changes only the year width (OQ-D9)
- An enrolment age in days: the legacy fields WS-ENROLL-JUL and WS-AGE-DAYS are declared but never used (R-PRVTMS01-ADD-03, Q-007)
- The two-digit year taken from positions 3-4 of the date (R-PRVTMS01-cbl-00033, -00034): replaced by 4-digit years (D-004)
- Sending a notice or correcting the date: no notice exists for this warning in the estate
- The file layout, period and archive (tms-extract, tms-run-archive)

## Legacy trace (agent-owned, do not edit)
Rules: R-PRVTMS01-ADD-02, R-PRVTMS01-ADD-03, R-PRVTMS01-cbl-00005, R-PRVTMS01-cbl-00006, R-PRVTMS01-cbl-00033, R-PRVTMS01-cbl-00034, R-PRVTMS01-cbl-00035, R-PRVTMS01-cbl-00036, R-PRVTMS01-cbl-00037
Related rules: R-PRVTMS01-cbl-00004, R-PRVTMS01-cbl-00020, R-PRVTMS01-cbl-00021, R-PRVTMS01-cbl-00023
Programs: PRVTMS01: WRITE-TMSIS-ROW 102-110 (enrolment year 104-105, comparison 107, warning 108-109), MAIN-CONTROL 80-82 (current year), WS-JULIAN-WORK 59-65 (WS-ENROLL-JUL and WS-AGE-DAYS never used). Job: PRVMTH01 STEP020.
Decisions: D-004 FIX: 4-digit dates everywhere (the year comparison is correct for every century); D-020 FIX: the business date is an explicit job parameter (its year is the reference year); D-015 PRESERVE in the legacy-format extract; ISO dates internally

## Technical notes (agent-owned)
Service: tmsis-service. Endpoints/events: POST /api/v1/batch/tmsis-extracts, GET /api/v1/batch/tmsis-extracts, GET /api/v1/batch/tmsis-extracts/{runId}/file. Roles: ADMIN, SYSTEM.
