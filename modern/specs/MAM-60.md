---
key: MAM-60
localId: L-S045
epic: MAM-9
service: notification-service
type: Story
title: "List the notices sent and the notice templates"
slug: ntf-list
specVersion: 1
descriptionSha256: b2b13a622fb5c241e11f229b13e545c70cb6a120400812c1485f0eb8f7c77e0e
jiraUpdated: 2026-11-18T09:19:23.286-0600
rules: []
decisions: [D-012, D-023, D-024]
dependsOn: [MAM-58, MAM-59]
mainJourney: true
labels: [svc-notification-service, kag-L-S045, agentic, decision-D-012, decision-D-023, decision-D-024, slice-main-journey]
builtCommit: pending-user-commit
testsSha256: f945420de833b29e4bf10e9ede1666ea25ecccaf418e132f735ce3b8fa4b18ca
---
## Business requirement
Providers must be able to see the notices that were sent to them, staff must be able to see the notices sent to any provider, and administrators must be able to see the notice templates in use. A provider never sees another provider's notices, and a notice that could not be sent shows as failed.

## Acceptance criteria
### AC-1 Staff list the notices of a provider
Given notices exist for NPI 1003000111 (APPROVED, SENT) and for NPI 1003000444 (DENIED, SENT)
When an ANALYST sends GET /api/v1/notices?npi=1003000111
Then the response is 200 with items containing only the notices of 1003000111, each with noticeId, npi, template, channel EMAIL, status and sentAt, plus page 0, size 20 and total
Basis: modern/contracts/openapi/notification-service.yaml (listNotices, Notice, NoticePage), D-012

### AC-2 Invalid filters are refused
Given the notice log has entries
When an ANALYST sends GET /api/v1/notices?npi=ABC and GET /api/v1/notices?size=500
Then each response is 400 problem+json
Basis: modern/contracts/openapi/notification-service.yaml (npi pattern, size maximum 200, 400 Invalid request)

### AC-3 A provider sees only their own notices
Given provider1 is a PROVIDER whose token carries the npi claim 1003000111, and notices exist for 1003000111 and 1003000444
When provider1 sends GET /api/v1/notices, then GET /api/v1/notices?npi=1003000444
Then the first response lists only notices of 1003000111; the second is 403
Basis: modern/docs/architecture/nfr.md (a PROVIDER only ever sees their own notices), D-023, modern/contracts/openapi/notification-service.yaml (listNotices: a PROVIDER sees only their own), .claude/commands/phase-5-platform.md (by analogy: a PROVIDER token for another NPI gives 403)

### AC-4 The provider sees the approval notice
Given the ProviderStatusChanged P to A event for 1003000111 was processed (journey J1)
When provider1 sends GET /api/v1/notices
Then the items include a notice with npi 1003000111, template APPROVED, channel EMAIL and status SENT
Basis: modern/docs/ux/journeys.md#J1, modern/docs/ux/screen-inventory.md (portal notices)

### AC-5 A failed notice shows as FAILED
Given a notice for NPI 1003000111 could not be sent because the mail server was down
When an ANALYST sends GET /api/v1/notices?npi=1003000111
Then the notice is listed with status FAILED
Basis: D-024, R-PRVSAN01-cbl-00009, modern/contracts/openapi/notification-service.yaml (Notice.status SENT, FAILED)

### AC-6 Who may list notices
Given notices exist for 1003000111 and 1003000444
When GET /api/v1/notices is called with ANALYST, SUPERVISOR, ADMIN and PROVIDER tokens, with a SYSTEM token and with no token
Then ANALYST, SUPERVISOR and ADMIN get 200 with the notices of both providers; PROVIDER gets 200 with only its own; SYSTEM gets 403; no token gets 401
Basis: D-023, ADR-007, modern/docs/architecture/nfr.md (roles per endpoint), modern/contracts/openapi/notification-service.yaml (listNotices x-roles PROVIDER, ANALYST, SUPERVISOR, ADMIN)

### AC-7 An administrator lists the templates
Given the seeded templates exist
When an ADMIN sends GET /api/v1/notice-templates
Then the response is 200 with an array of the nine templates TRM, SUS, RST, GEN, APPROVED, DENIED, REJECTED, REVALIDATION_DUE and EXTRACT_READY, each with code, subject and body
Basis: modern/contracts/openapi/notification-service.yaml (listNoticeTemplates, NoticeTemplate, Notice.template values), D-012, Q-A8

### AC-8 Only ADMIN can list templates
Given the seeded templates exist
When GET /api/v1/notice-templates is called with ANALYST, SUPERVISOR, PROVIDER and SYSTEM tokens and with no token
Then ANALYST, SUPERVISOR, PROVIDER and SYSTEM get 403; no token gets 401
Basis: D-023, ADR-007, modern/docs/architecture/nfr.md (roles per endpoint), modern/contracts/openapi/notification-service.yaml (listNoticeTemplates x-roles ADMIN)

## Out of scope
- Editing templates through the API or the UI: no endpoint exists in the contract.
- Showing the notices in the portal and in the console detail page (ui-portal-notices, ui-console-detail).
- Resending a notice: no rule or decision exists.

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: No legacy program: the notice programs PRVNTTRM, PRVNTSUS, PRVNTRST and PRVNTGEN are missing from the estate (PRVSAN01.cbl:124-138; D-012).
Decisions: D-012 REPLACE: a notice log and templates in notification-service; D-023 FIX: role-based access; a provider sees only their own notices; D-024 FIX: failures are surfaced, not silent

## Technical notes (agent-owned)
Service: notification-service. Endpoints/events: GET /api/v1/notices, GET /api/v1/notice-templates. Roles: PROVIDER, ANALYST, SUPERVISOR, ADMIN.
