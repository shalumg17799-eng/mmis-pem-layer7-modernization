---
key: MAM-85
localId: L-S062
epic: MAM-13
service: web-ui
type: Story
title: "Console: work queues (pending approval, failed screening, revalidation due, rejected applications)"
slug: ui-console-queues
specVersion: 1
descriptionSha256: 26734f2ff23358f1999fcac9076bc720aebaca371812b2137e570d94f38c83b0
jiraUpdated: 2026-11-18T09:22:49.353-0600
rules: []
decisions: [D-019, D-029, D-005, D-016, D-001, D-023, D-024]
dependsOn: [MAM-62, MAM-37, MAM-54, MAM-25, MAM-48]
mainJourney: true
labels: [svc-web-ui, kag-L-S062, agentic, decision-D-019, decision-D-029, decision-D-005, decision-D-016, decision-D-001, decision-D-023, decision-D-024, slice-main-journey]
builtCommit:
testsSha256:
---
## Business requirement
Analysts and supervisors work from four lists: providers waiting for approval after a passed screening, providers whose screening failed, providers whose revalidation is due, and applications that were rejected. Each list names the provider, why it is there and how to open it, so nothing waits unseen. After a decision or a new screening the lists change accordingly.

## Acceptance criteria
### AC-1 Pending approval lists providers with a passed screening (journey J1)
Given Provider 1003000111 is Pending (P) with screen flag Pass (P), and provider 1003000444 is Pending (P) with screen flag Fail (F)
When analyst1 opens /console/queues
Then The page calls GET /api/v1/providers?status=P&screenFlag=P and the section 'Pending approval' lists 1003000111 (columns NPI, Name, Screened 'Pass (P)', and a 'Review' link to /console/providers/1003000111/approval) and does not list 1003000444
Basis: modern/docs/ux/journeys.md#J1 (step 5), D-019, modern/docs/ux/wireframes/console-work-queues.html, modern/docs/ux/screen-inventory.md (console work-queues), modern/contracts/openapi/provider-service.yaml#searchProviders (status, screenFlag), R-PRVSCR01-ADD-03

### AC-2 Failed screening shows the failing source (journey J3)
Given Provider 1003000444 (owner 1990000111 on the mocked OIG LEIE) is Pending (P) with screen flag Fail (F), failed on OIGLEIE with E203
When analyst1 opens /console/queues
Then The page calls GET /api/v1/providers?status=P&screenFlag=F and, for each listed provider, GET /api/v1/screenings?npi=<npi>&result=FAIL; the section 'Failed screening' lists 1003000444 EXCLUDED CLINIC LLC with the failing source 'OIGLEIE (E203 owner excluded)' and the NPI links to /console/providers/1003000444; the provider is not listed under Pending approval
Basis: modern/docs/ux/journeys.md#J3 (steps 1 to 3), D-005, D-016, QE-9, .claude/skills/pem-domain/SKILL.md section 4 (E203 owner excluded), modern/docs/ux/wireframes/console-work-queues.html, modern/contracts/openapi/screening-service.yaml#listScreenings

### AC-3 Revalidation due lists open cases once each (journey J6)
Given The revalidation sweep opened one case for provider 1003000222 with due date 2026-02-28; its status is DUE or already IN_SCREENING; an older case of another provider is COMPLETED
When analyst1 opens /console/queues
Then The page calls GET /api/v1/revalidations?status=DUE and GET /api/v1/revalidations?status=IN_SCREENING and the section 'Revalidation due' lists the case of 1003000222 once, with the columns NPI, Due (02/28/2026) and Case ('Due' or 'In screening'); completed and cancelled cases are not listed
Basis: modern/docs/ux/journeys.md#J6 (step 2), D-029, D-003, QE-5, modern/docs/ux/wireframes/console-work-queues.html, modern/contracts/openapi/revalidation-service.yaml#listRevalidationCases, R-PRVREV01-ADD-02

### AC-4 Rejected applications are listed for follow-up
Given Two applications were rejected: one for NPI 1003000111 with E104, and one with a blank NPI with E101
When analyst1 opens /console/queues
Then The page calls GET /api/v1/applications?status=REJECTED and the section 'Rejected applications' lists each with NPI (or '(no NPI)' when blank), Submitted date as MM/DD/YYYY, the error codes found (E104; E101) and the source ('Portal' or 'Legacy file')
Basis: modern/docs/ux/screen-inventory.md (console work-queues: rejected applications), D-001, D-016, D-004, R-PRVENR01-ADD-05, modern/contracts/openapi/enrollment-service.yaml#listApplications, modern/contracts/openapi/enrollment-service.yaml#Application (source PORTAL, LEGACY_FILE)

### AC-5 Counts, empty lists and paging
Given The four sections in turn have 1 entry, no entry and more than 20 entries
When /console/queues is shown
Then Each heading carries its count ('Pending approval (1)'); an empty section says 'Nothing pending approval', 'No failed screenings', 'No revalidations due' or 'No rejected applications'; a section with more than 20 entries shows paging controls (page 0, size 20 by default)
Basis: modern/contracts/openapi/provider-service.yaml#searchProviders (page, size), modern/contracts/openapi/enrollment-service.yaml#listApplications (page, size), modern/contracts/openapi/revalidation-service.yaml#listRevalidationCases (page, size), modern/docs/ux/wireframes/console-work-queues.html

### AC-6 One failing queue does not hide the others
Given The revalidations call answers 5xx while the other calls succeed
When /console/queues loads
Then The other three sections are shown normally and 'Revalidation due' shows 'The list could not be loaded' with a Retry button, never 'No revalidations due'
Basis: D-024, modern/docs/ux/screen-inventory.md (console work-queues calls three APIs)

### AC-7 Only analysts and supervisors open the queues
Given analyst1, supervisor1, admin1 and provider1 are signed in
When Each opens /console/queues
Then analyst1 and supervisor1 see the four sections; admin1 and provider1 see the access-denied page and no queue request is made (the applications and revalidations endpoints do not allow ADMIN)
Basis: D-023, modern/docs/ux/screen-inventory.md (roles ANALYST, SUPERVISOR), modern/docs/architecture/nfr.md (roles per endpoint)

### AC-8 The queues page has zero axe violations
Given The Playwright test opens the page with all four sections filled, with all empty and with one section failed
When It runs an axe scan on each and uses the page with the keyboard only
Then Every scan reports zero violations; each section has a heading and a table with header cells; the Review and NPI links can be reached with Tab and opened with Enter
Basis: .claude/skills/frontend-stack/SKILL.md section 5, modern/docs/architecture/nfr.md (Accessibility), modern/docs/ux/journeys.md#J1

## Out of scope
- Approving or denying (ui-console-approval)
- Screening, risk and revalidation processing (their services)
- Completing a revalidation case from the queue (no screen in the inventory)
- Reassigning or claiming work items (no such function exists)

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Related rules: R-PRVENR01-ADD-05, R-PRVREV01-ADD-02, R-PRVSCR01-ADD-03
Programs: PRVREV01.cbl:75-94 (CHECK-NEXT, QUEUE-FOR-REVAL: REVALQ rows with no case state, D-029); PRVSCR01.cbl:90-111 (SCREEN-ONE-PROVIDER sets the screen flag only, R-PRVSCR01-ADD-03); PRVENR01.cbl:173-179 (WRITE-ERROR-RECORD, APPERROR rows). Job steps PRVMTH01.jcl STEP010 (PRVREV01), PRVDAY01.jcl STEP030 and STEP010.
Decisions: D-019 FIX: an analyst approval step sets ACTIVE after a Pass screening, so a Pending-approval queue is needed; D-029 FIX (accepted, Q-010): one revalidation case per provider with its own state; D-005 REPLACE: screening results per source decide the failed-screening queue; D-016 FIX: error codes shown with the failing source; D-001 FIX: rejected applications are kept and listed; D-023 FIX: only analysts and supervisors; D-024 FIX: failures are surfaced

## Technical notes (agent-owned)
Service: web-ui. Endpoints/events: GET /api/v1/providers, GET /api/v1/screenings, GET /api/v1/revalidations, GET /api/v1/applications. Roles: ANALYST, SUPERVISOR.
