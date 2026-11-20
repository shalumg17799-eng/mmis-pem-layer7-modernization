---
key: MAM-72
localId: L-S068
epic: MAM-14
service: e2e
type: Story
title: "Journey J2 end to end: an application is rejected"
slug: e2e-j2
specVersion: 1
descriptionSha256: 8df99f0a65b183d2948b025f21956c82ccaf6af20eafa9e2ccc113c35ab60aa3
jiraUpdated: 2026-11-18T09:21:14.649-0600
rules: []
decisions: [D-001, D-016, D-012]
dependsOn: [MAM-64, MAM-66, MAM-70, MAM-46, MAM-54, MAM-58, MAM-50]
mainJourney: false
labels: [svc-e2e, kag-L-S068, agentic, decision-D-001, decision-D-016, decision-D-012]
builtCommit:
testsSha256: 0fe218ad62aee5682207818ff25e47fdfb51c5881991ead7cbb734904b682f3a
---
## Business requirement
When an application is incomplete, the provider must be told exactly which field is wrong, nothing must be enrolled, and a notice must record the rejection. This story proves that the rejection path works for real in the running system.

## Acceptance criteria
### AC-1 The error summary links to the field
Given the stack is at the seed state and provider1 is signed in at /portal/apply
When provider1 enters the J1 values for NPI 1003000111 but leaves the taxonomy empty, and submits
Then an error summary at the top of the form lists E104 and links to the taxonomy field, E104 is shown next to the taxonomy field, and no other field shows an error
Basis: modern/docs/ux/journeys.md#J2 steps 1-2, D-016, .claude/skills/frontend-stack/SKILL.md section 5, QF-6 (default applied)

### AC-2 Nothing is registered and the rejection is published
Given the rejected submission
When the services have handled it
Then GET /api/v1/providers/1003000111 with an ANALYST token returns 404, no ProviderRegistered event exists for 1003000111, and pem.enrollment.events carries ApplicationRejected whose errors list contains only E104
Basis: modern/docs/ux/journeys.md#J2 step 2, D-001, modern/contracts/asyncapi/events.yaml (ApplicationRejected)

### AC-3 The application shows Rejected with exactly one error row
Given the rejected application
When provider1 opens /portal/applications/:applicationId
Then the status is Rejected and the error table has exactly one row, code E104 for the taxonomy field
Basis: modern/docs/ux/journeys.md#J2 step 3, D-001, D-016, modern/docs/ux/screen-inventory.md (portal application-status)

### AC-4 The provider is told the application was rejected
Given ApplicationRejected was published
When provider1 opens /portal/notices
Then the notice Application rejected is listed, and GET /api/v1/notices shows template REJECTED for 1003000111
Basis: modern/docs/ux/journeys.md#J2 step 4, D-012, modern/docs/architecture/overview.md (notification-service consumes ApplicationRejected), modern/contracts/openapi/notification-service.yaml (Notice template)

### AC-5 Repeatable and accessible
Given the journey as one Playwright test with an axe scan on every page it visits
When it is run three times in a row, each time from the seed state
Then all three runs pass and every axe scan reports zero violations
Basis: .claude/commands/phase-8-e2e-hardening.md steps 2 and 4, modern/docs/architecture/nfr.md (Accessibility), QF-6 (default applied)

## Out of scope
- Rejection through the legacy intake file (enr-intake-run)
- Several failed checks on one application
- Screening failures (J3)

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: BATCH/PRVENR01.cbl:128-159 (GO TO INVALID-APP at 132, 139, 144; only EC-MISSING-NPI is ever used at 129; D-001, D-016); JCL/PRVDAY01.jcl STEP010
Decisions: D-001 FIX: an invalid application is rejected with exactly one error row per failed check and never registered; D-016 FIX: E101-E104 are real validation results, here E104; D-012 REPLACE: the rejection notice is sent by notification-service

## Technical notes (agent-owned)
Service: e2e. Endpoints/events: POST /api/v1/applications, GET /api/v1/applications/{applicationId}, GET /api/v1/providers/{npi}, GET /api/v1/notices, ApplicationRejected, NoticeSent. Roles: PROVIDER, ANALYST.
