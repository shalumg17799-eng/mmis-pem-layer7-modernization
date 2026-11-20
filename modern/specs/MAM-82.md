---
key: MAM-82
localId: L-S061
epic: MAM-13
service: web-ui
type: Story
title: "Console: provider detail panels for screening, sanctions, revalidation, notices and audit"
slug: ui-console-detail-2
specVersion: 1
descriptionSha256: 1e9d1431346cc1347c5d725fda6f238a0975cd82481f4bf9fb646c730e084df8
jiraUpdated: 2026-11-18T09:22:30.471-0600
rules: []
decisions: [D-005, D-016, D-018, D-029, D-012, D-023, D-024]
dependsOn: [MAM-80, MAM-25, MAM-52, MAM-48, MAM-60, MAM-40]
mainJourney: false
labels: [svc-web-ui, kag-L-S061, agentic, decision-D-005, decision-D-016, decision-D-018, decision-D-029, decision-D-012, decision-D-023, decision-D-024]
builtCommit: 2e20ead
testsSha256: 789cdfbb99f8549c847c1aef8fd8b178c3173d79009589db7d4788ee12ae1b08
---
## Business requirement
Below the provider record, an analyst, supervisor or administrator sees what has happened to the provider: the screening results source by source, the sanctions history, the revalidation cases, the notices sent, and the audit trail of changes. Each person sees the panels their role is allowed to read, and one panel failing to load never hides the others.

## Acceptance criteria
### AC-1 Screening results per source (journey J3)
Given Provider 1003000444, whose owner 1990000111 is on the mocked OIG LEIE, was screened with overall result FAIL; provider 1003000111 was screened with PASS on LICBOARD, OIGLEIE, SAMGOV and SSADMF
When analyst1 opens /console/providers/1003000444 and then /console/providers/1003000111
Then The page calls GET /api/v1/screenings?npi=<npi> and the panel 'Screening' shows each screening (with its reason, date and overall result) and a table Source | Result | Verdict | Detail | Error code with one row per source returned; for 1003000444 the OIGLEIE row reads 'Fail (F)' with its detail and 'E203 owner excluded'; for 1003000111 the four rows read 'Pass (P)'; a result C reads 'Conditional (C)'; a provider with no screening shows 'Not screened yet'
Basis: modern/docs/ux/journeys.md#J3 (step 3), modern/docs/ux/wireframes/console-provider-detail.html, D-005, D-016, D-018, .claude/skills/pem-domain/SKILL.md section 4 (E201-E205 meanings), modern/docs/analysis/data-layouts.md (SCR-RESULT P, F, C=SCR-COND), QE-12, modern/contracts/openapi/screening-service.yaml#SourceResult

### AC-2 The sanctions history (journey J5)
Given Provider 1003000111 was suspended and then reinstated by supervisor1
When An analyst, supervisor or administrator opens its detail page
Then The page calls GET /api/v1/sanctions?npi=1003000111 and the panel 'Sanctions' shows the rows Effective date | Action | Reason | By, with 'Suspend (S)' and 'Reinstate (R)' (and 'Terminate (T)' when it applies) and the reasons; a provider with no sanction shows 'None recorded'
Basis: modern/docs/ux/journeys.md#J5 (step 4), modern/docs/ux/wireframes/console-provider-detail.html, D-018, D-006, modern/contracts/openapi/sanctions-service.yaml#Sanction

### AC-3 Revalidation cases, one per provider and period
Given Provider 1003000222 has a revalidation case DUE for 2026-02-28 and an older case COMPLETED
When An analyst or supervisor opens its detail page
Then The page calls GET /api/v1/revalidations?npi=1003000222 and the panel 'Revalidation' shows the provider's revalidation due date and a table Due date | Case | Opened | Closed with the case states written as 'Due', 'In screening', 'Completed' or 'Cancelled'; a provider with no case shows 'No revalidation case'
Basis: D-029, D-003, modern/contracts/openapi/revalidation-service.yaml#RevalidationCase, modern/docs/ux/screen-inventory.md (console provider-detail), R-PRVREV01-ADD-02

### AC-4 Notices sent to the provider
Given Provider 1003000111 received the notices SUS and RST after a suspension and a reinstatement
When The detail page opens
Then The page calls GET /api/v1/notices?npi=1003000111 and the panel 'Notices' lists Sent | Notice | Status with 'Suspension notice (SUS)' and 'Reinstatement notice (RST)' and their state 'Sent' or 'Failed' in words
Basis: modern/docs/ux/journeys.md#J5 (steps 2 and 4), D-012, QE-2, modern/contracts/openapi/notification-service.yaml#listNotices, R-PRVSAN01-cbl-00008

### AC-5 The audit trail (supervisor and administrator)
Given Provider 1003000111 was approved by analyst1 on 03/02/2026
When supervisor1 or admin1 opens its detail page
Then The page calls GET /api/v1/providers/1003000111/audit and the panel 'Audit' shows When | Who | Action rows from the API, for example 03/02/2026 | analyst1 | the approval action
Basis: modern/docs/ux/wireframes/console-provider-detail.html, modern/docs/ux/journeys.md#J4 (step 2), modern/docs/architecture/nfr.md (Audit), modern/contracts/openapi/provider-service.yaml#getProviderAudit

### AC-6 Each role sees only the panels it may read
Given analyst1 (ANALYST), supervisor1 (SUPERVISOR) and admin1 (ADMIN) open the same provider
When The detail page loads for each
Then analyst1 sees Screening, Sanctions, Revalidation and Notices and no Audit panel and no audit request; supervisor1 sees all five panels; admin1 sees Screening, Sanctions, Notices and Audit and no Revalidation panel and no revalidation request; a panel that is not shown is not requested, so no 403 is raised
Basis: QE-4, D-023, modern/docs/architecture/nfr.md (roles per endpoint), modern/contracts/openapi/provider-service.yaml#getProviderAudit (SUPERVISOR, ADMIN), modern/contracts/openapi/revalidation-service.yaml#listRevalidationCases (ANALYST, SUPERVISOR)

### AC-7 One failing panel does not hide the others
Given The sanctions call answers 5xx while the other calls succeed
When The detail page loads
Then The record, banner, screening, revalidation and notices panels are shown normally and the Sanctions panel shows 'The sanctions could not be loaded' with a Retry button; the panel is not shown as 'None recorded'
Basis: D-024, D-018, modern/docs/ux/screen-inventory.md (console provider-detail calls six APIs)

### AC-8 The panels have zero axe violations
Given The Playwright test opens a fully populated detail page for each role and a page with a failed panel
When It runs an axe scan on each page and reads the panels with the keyboard only
Then Every scan reports zero violations; each panel has a heading and a table with header cells; results and statuses are text plus code, never colour alone
Basis: .claude/skills/frontend-stack/SKILL.md section 5, modern/docs/architecture/nfr.md (Accessibility), modern/docs/ux/journeys.md#J3, modern/docs/ux/journeys.md#J4

## Out of scope
- The provider record and the status banner (ui-console-detail)
- Starting a screening or completing a revalidation case from the page (no screen in the inventory)
- Showing the before and after values of an audit entry (not in the wireframe)
- Back-end behaviour of screening, sanctions, revalidation, notification and audit (their own stories)

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Related rules: R-PRVREV01-ADD-02, R-PRVSAN01-cbl-00008
Programs: PRVSCR01.cbl:95-114 (SCREEN-ONE-PROVIDER), 116-132 (CHECK-LICENSE, CHECK-LEIE-EXCLUSION, CHECK-SAM-DEBARMENT, CHECK-SSA-DMF: stubs returning P), 146-149 (WRITE-SCREEN-ROW, SCRNOUT 67 bytes); PRVSAN01.cbl:104-116 (APPLY-SANCTION, no history kept), 124-138 (GENERATE-NOTICE); PRVREV01.cbl:75-94 (CHECK-NEXT, QUEUE-FOR-REVAL: no case state). Job steps PRVDAY01.jcl STEP030, PRVMTH01.jcl STEP010.
Decisions: D-005 REPLACE: real screening with per-source results, shown in the Screening panel; D-016 FIX: screening error codes E201-E205 shown with their meaning; D-018 FIX: screening results and sanction history are real data and are shown; D-029 FIX (accepted, Q-010): one revalidation case per provider with its own state; D-012 REPLACE: notices come from notification-service; D-023 FIX: each role sees only what it may read; D-024 FIX: failures are surfaced

## Technical notes (agent-owned)
Service: web-ui. Endpoints/events: GET /api/v1/screenings, GET /api/v1/sanctions, GET /api/v1/revalidations, GET /api/v1/notices, GET /api/v1/providers/{npi}/audit. Roles: ANALYST, SUPERVISOR, ADMIN.
