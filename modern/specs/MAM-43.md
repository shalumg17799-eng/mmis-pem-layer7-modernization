---
key: MAM-43
localId: L-S024
epic: MAM-3
service: provider-service
type: Story
title: "Export the provider master in the legacy 130-byte PRVRECRD layout"
slug: prv-legacy-master
specVersion: 2  # 2026-11-20: AC-6 now exports an unset revalidation due date as 99991231 (v1: 00000000); Q-124 A, user, 2026-11-20 19:32; tests and code follow
descriptionSha256: f32eb3400d75188f7c2c4584b25e9ffe6b84d6661a1403581d5a088452eb4780
jiraUpdated: 2026-11-20T08:14:51.916-0600
rules: []
decisions: [D-004, D-006, D-007, D-023]
dependsOn: [MAM-17, MAM-42]
mainJourney: false
labels: [svc-provider-service, kag-L-S024, agentic, decision-D-004, decision-D-006, decision-D-007, decision-D-023]
builtCommit: pending-user-commit
testsSha256: 6e7fd54f211c9aaf8173ef3efacdc778473fb005ed2b7361abdc90285cb94380
---
## Business requirement
For the planned side-by-side comparison with the old system, an administrator must be able to export all providers in the old fixed 130-byte master record layout, so that the result can be compared byte for byte with the file the old system produced. Every field is included, among them the owner of a group or facility.

## Acceptance criteria
### AC-1 Every record is exactly 130 bytes
Given providers 1003000111 (IN), 1003000444 (GR) and 1003000777 (FA) exist
When an ADMIN sends GET /api/v1/legacy/provider-master
Then the response is 200 text/plain with one line per provider and every line exactly 130 characters long
Basis: D-007, ADR-005, modern/contracts/openapi/provider-service.yaml (exportProviderMaster), modern/docs/analysis/data-layouts.md (130 bytes)

### AC-2 Records are in NPI order with no header or trailer
Given the three providers of AC-1
When the ADMIN exports the master
Then the lines are in ascending NPI order (1003000111, 1003000444, 1003000777) and there is no header or trailer line
Basis: CLAUDE.md section 5.3 (the master is a KSDS keyed by PRV-NPI), ADR-005

### AC-3 An individual record has the PRVRECRD field positions
Given provider 1003000111: Medicaid ID MCD000111, type IN, taxonomy 207Q00000X, riskTier L, status A, screenFlag P, effectiveDate 2026-11-18, revalidationDueDate 2031-11-18, RIVERA, MARIA, licence MD12345, state TX
When the ADMIN exports the master
Then its line has the NPI in bytes 1-10, 'MCD000111' in 11-19, 'IN' in 20-21, the taxonomy in 22-31, 'L' in byte 32, 'A' in byte 33, 'P' in byte 34, '20261118' in 35-42, '20311118' in 43-50, 'RIVERA' space-padded in 51-80, 'MARIA' space-padded in 81-100, 'MD12345' space-padded in 101-115, 'TX' in 116-117 and spaces in 118-130
Basis: modern/docs/analysis/data-layouts.md (PRVRECRD positions), D-004 (CCYYMMDD, 4-digit year), .claude/skills/synthetic-data/SKILL.md section 2 (alphanumeric right-padded with spaces)

### AC-4 A group record keeps the owner NPI in bytes 121-130
Given provider 1003000444: type GR, legal name EXCLUDED CLINIC LLC, DBA name EXCLUDED CLINIC, owner NPI 1990000111
When the ADMIN exports the master
Then its line has the legal name space-padded in bytes 51-90, the DBA name space-padded in 91-120 and '1990000111' in bytes 121-130 (the owner NPI is not cut off); a legal name longer than 40 characters is cut to its first 40 characters
Basis: D-007, R-PRVENR01-ADD-02, modern/docs/analysis/data-layouts.md (PRV-ORG-DATA: PRV-LEGAL-NAME X(40), PRV-DBA-NAME X(30), PRV-OWNER-NPI X(10)), .claude/skills/cobol-to-java/SKILL.md section 3 (alphanumeric MOVE truncates on the right, only in legacy-format writers)

### AC-5 A suspended provider is exported with status S
Given provider 1003000888 has status S
When the ADMIN exports the master
Then byte 33 of its line is 'S'
Basis: D-006, modern/docs/analysis/data-layouts.md (PRV-ENROLL-STATUS byte 33)

### AC-6 Unset values use the legacy padding
Given provider 1003000222 is PENDING with riskTier null, screenFlag U and revalidationDueDate null
When the ADMIN exports the master
Then byte 32 is a space, byte 34 is 'U' and bytes 43-50 are '99991231' (the value for no revalidation due date)
Basis: modern/docs/analysis/data-layouts.md (PRV-RISK-TIER X(01), PRV-REVAL-DUE-DATE 9(08)), .claude/skills/synthetic-data/SKILL.md section 2 (alphanumeric right-padded with spaces, numeric display left zero-filled), the unset due date follows the test-data convention of Q-106 (99991231) and the user's decision Q-124 A of 2026-11-20 (the dat-codec can then read the export back), D-029 (still describes only what the legacy did with a zero due date)

### AC-7 The export decodes to the same values as the API
Given the export of AC-1
When each line is decoded with the fixed-width codec (dat-codec) using the PRVRECRD layout
Then the decoded fields equal the fields returned by GET /api/v1/providers/{npi} for that NPI
Basis: ADR-005, D-007

### AC-8 Only ADMIN can export
Given the providers of AC-1
When GET /api/v1/legacy/provider-master is called with ADMIN, ANALYST, SUPERVISOR, PROVIDER and SYSTEM tokens and with no token
Then ADMIN gets 200; ANALYST, SUPERVISOR, PROVIDER and SYSTEM get 403; no token gets 401
Basis: D-023, ADR-007, modern/docs/architecture/nfr.md (roles per endpoint)

## Out of scope
- Loading a legacy master file into provider-service: no such load is planned.
- The other legacy files (APPINPUT, SCRNOUT, REVALQ, DIROUT, TMSISOUT): their own services.
- Running the legacy jobs on Hercules and comparing outputs: par-harness. EBCDIC conversion: dat-ebcdic.

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Related rules: R-PRVENR01-ADD-02
Programs: No legacy program: replaces reading the VSAM master MMPRV.PROVIDER.MASTER.KSDS (source-code/VSAM/PRVVSM01.jcl:14-17, RECORDSIZE 121 vs the 130-byte copybook source-code/COPYBOOKS/PRVRECRD.cpy:9-44; PRVENR01.cbl:57-159).
Decisions: D-004 FIX: dates are written as CCYYMMDD with a 4-digit year; D-006 FIX: status S is exported for a suspended provider; D-007 FIX: the record is 130 bytes, the owner NPI is kept; D-023 FIX: ADMIN only

## Technical notes (agent-owned)
Service: provider-service. Endpoints/events: GET /api/v1/legacy/provider-master. Roles: ADMIN.
