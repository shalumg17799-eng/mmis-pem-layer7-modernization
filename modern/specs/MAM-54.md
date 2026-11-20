---
key: MAM-54
localId: L-S026
epic: MAM-4
service: enrollment-service
type: Story
title: "Read application status (a provider sees their own; an analyst sees all)"
slug: enr-status
specVersion: 1
descriptionSha256: 21b8fdfec25f8460a5fda135a8c98aa42e3b43807a3598563bba9bef60aa6867
jiraUpdated: 2026-11-18T09:17:56.533-0600
rules: []
decisions: [D-001, D-016, D-023]
dependsOn: [MAM-46]
mainJourney: true
labels: [svc-enrollment-service, kag-L-S026, agentic, decision-D-001, decision-D-016, decision-D-023, slice-main-journey]
builtCommit: pending-user-commit
testsSha256: f79e23bd03a599b931e9f29ff54dbe0c214076b77398dee3b5e731e63d11c5ef
---
## Business requirement
Providers need to see what happened to the applications they sent, and analysts need to see every application, including the rejected ones, so that they can follow up. A provider sees only their own applications and, for a rejected one, every reason it was rejected. An analyst or supervisor sees all applications and can narrow the list by outcome or by identification number.

## Acceptance criteria
### AC-1 A provider lists only their own applications
Given Two PROVIDER users: user A has submitted an application for NPI 1003000111 (VALIDATED) and user B one for NPI 1003000222 (VALIDATED)
When User A calls GET /api/v1/applications, and then GET /api/v1/applications?npi=1003000222
Then The first call returns 200 with items containing only the application for 1003000111 and total 1; the second returns 200 with no items and total 0; the application for 1003000222 is never shown to user A
Basis: modern/contracts/openapi/enrollment-service.yaml#listApplications, modern/docs/architecture/nfr.md, modern/docs/architecture/data-model/enrollment-service.sql (submitted_by), Q-B2, D-023

### AC-2 A provider cannot open someone else's application
Given User B's application id, and user A signed in as a PROVIDER
When User A calls GET /api/v1/applications/{applicationId} with B's id
Then 403 problem+json, and none of B's data is in the response
Basis: modern/docs/architecture/nfr.md, D-023, .claude/commands/phase-5-platform.md, modern/docs/ux/journeys.md#J4, Q-B2

### AC-3 A validated application shows its status
Given User A's application for NPI 1003000111 was accepted (journey J1)
When User A calls GET /api/v1/applications/{applicationId}
Then 200 with status VALIDATED, no errors, source PORTAL, npi 1003000111, medicaidId M00000111, providerType IN, taxonomy 207Q00000X, the individual details and submittedAt
Basis: modern/docs/ux/journeys.md#J1, modern/contracts/openapi/enrollment-service.yaml#getApplication

### AC-4 A rejected application shows exactly one error per failed check
Given User A's application was rejected for an empty taxonomy (journey J2), and a second one was rejected for providerType XX with an empty taxonomy
When User A calls GET /api/v1/applications/{applicationId} for each
Then The first has status REJECTED and exactly one error with code E104, field taxonomy and a message; the second has status REJECTED and exactly two errors, E102 on providerType and E104 on taxonomy, each listed once
Basis: modern/docs/ux/journeys.md#J2, R-PRVENR01-cbl-00003, R-PRVENR01-cbl-00004, R-PRVENR01-cbl-00005, D-001, D-016

### AC-5 An analyst sees every application and can filter
Given Applications of several providers with statuses VALIDATED and REJECTED, some loaded from a legacy file and some submitted in the portal
When An ANALYST, and then a SUPERVISOR, calls GET /api/v1/applications?status=REJECTED and GET /api/v1/applications?npi=1003000111
Then The first returns every REJECTED application of every provider (source PORTAL and LEGACY_FILE); the second returns only the applications for NPI 1003000111; each response has items, page, size and total, defaulting to page 0 and size 20
Basis: modern/docs/ux/screen-inventory.md (console work-queues: rejected applications), modern/contracts/openapi/enrollment-service.yaml#listApplications, D-023

### AC-6 Paging and filter values are checked
Given An ANALYST is signed in
When GET /api/v1/applications is called with size=201, with status=FOO, and with npi=123
Then Each call returns 400 problem+json; a call with size=200 returns 200
Basis: modern/contracts/openapi/enrollment-service.yaml#listApplications

### AC-7 An unknown application id is not found
Given No application has the id 00000000-0000-0000-0000-000000000000
When An ANALYST calls GET /api/v1/applications/00000000-0000-0000-0000-000000000000
Then 404 problem+json
Basis: modern/contracts/openapi/enrollment-service.yaml#getApplication

### AC-8 Only PROVIDER, ANALYST and SUPERVISOR may read applications
Given Tokens for PROVIDER, ANALYST, SUPERVISOR, ADMIN and SYSTEM, and a request without a token
When GET /api/v1/applications and GET /api/v1/applications/{applicationId} are called
Then PROVIDER (own applications only), ANALYST and SUPERVISOR get 200; ADMIN and SYSTEM get 403 problem+json; a call without a token gets 401
Basis: D-023, ADR-007, modern/docs/architecture/nfr.md, modern/contracts/openapi/enrollment-service.yaml

## Out of scope
- The portal status page and the console work-queue screens (ui-portal-status, ui-console-queues)
- The provider's enrolment status after registration or approval (prv-search-read)
- Notices about rejected applications (ntf-list)
- Changing or withdrawing an application (no such function exists in the legacy estate or the contract)

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Related rules: R-PRVENR01-cbl-00003, R-PRVENR01-cbl-00004, R-PRVENR01-cbl-00005, R-PRVENR01-cbl-00046
Programs: No legacy program reads applications back: this story is new. Related: PRVENR01 WRITE-ERROR-RECORD 173-179 (the APPERROR rows that were reviewed; contract x-legacy 'APPERROR review')
Decisions: D-001 FIX: a rejected application and every one of its errors stay readable, once each; D-016 FIX: errors are shown with their E101-E104 codes; D-023 FIX: role-based access; a PROVIDER sees only their own applications

## Technical notes (agent-owned)
Service: enrollment-service. Endpoints/events: GET /api/v1/applications, GET /api/v1/applications/{applicationId}. Roles: PROVIDER, ANALYST, SUPERVISOR.
