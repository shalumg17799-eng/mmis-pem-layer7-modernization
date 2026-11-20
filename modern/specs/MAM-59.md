---
key: MAM-59
localId: L-S044
epic: MAM-9
service: notification-service
type: Story
title: "Send notices by email for approvals, denials, rejections and revalidation"
slug: ntf-send-2
specVersion: 1
descriptionSha256: 03f5c6df0a35f9fb0c2c21f9b487eff4f27546e117672449c6ab20d38929a552
jiraUpdated: 2026-11-18T09:19:09.401-0600
rules: []
decisions: [D-001, D-012, D-016, D-019]
dependsOn: [MAM-58]
mainJourney: true
labels: [svc-notification-service, kag-L-S044, agentic, decision-D-001, decision-D-012, decision-D-016, decision-D-019, slice-main-journey]
builtCommit: pending-user-commit
testsSha256: 848b4d31ff89799e8055d9b3aa656716ced0da8ff8f573a1a782115de4e963d8
---
## Business requirement
Providers must also be told by email about decisions on their application: approved, denied, or rejected after the checks, and when their revalidation falls due. The emails can be seen by the person demonstrating the system (in Mailpit) and, through the notice list, by the provider. A provider receives one notice per event, and a change caused by a sanction is not announced a second time.

## Acceptance criteria
### AC-1 An approval sends the 'Enrolment approved' notice
Given Mailpit is empty and provider 1003000111 was approved
When notification-service consumes a ProviderStatusChanged event for 1003000111 with from P and to A
Then exactly one email arrives in Mailpit addressed to 1003000111@providers.pem.local with the subject 'Enrolment approved', and one NoticeSent event with template APPROVED is published
Basis: modern/docs/ux/journeys.md#J1, D-019, Q-A7, Q-A8, modern/contracts/asyncapi/events.yaml (ProviderStatusChanged, NoticeSent)

### AC-2 A denial sends the DENIED notice
Given Mailpit is empty and provider 1003000444 was denied by an analyst
When notification-service consumes a ProviderStatusChanged event for 1003000444 with from P and to D
Then exactly one email arrives in Mailpit addressed to 1003000444@providers.pem.local with the subject of the DENIED template, and one NoticeSent event with template DENIED is published
Basis: modern/docs/ux/journeys.md#J3 (a notice is sent), Q-A7, Q-A8

### AC-3 A sanction-driven status change sends no second notice
Given the SUS notice for NPI 1003000111 was already sent from a SanctionApplied event and Mailpit holds only that email
When notification-service consumes the ProviderStatusChanged events A to S, S to A, A to T and A to A for 1003000111
Then no further email arrives in Mailpit and no further NoticeSent event is published
Basis: Q-A8 (only P to A and P to D produce a notice from ProviderStatusChanged), modern/docs/ux/journeys.md#J5, Q-A4 (a revalidation announces A to A)

### AC-4 A rejected application sends the 'Application rejected' notice
Given Mailpit is empty and the application for NPI 1003000222 was rejected with error E104
When notification-service consumes an ApplicationRejected event for NPI 1003000222 with one error (code E104, field taxonomy)
Then exactly one email arrives in Mailpit addressed to 1003000222@providers.pem.local with the subject 'Application rejected', and one NoticeSent event with template REJECTED is published
Basis: modern/docs/ux/journeys.md#J2, D-001, D-016, Q-A7, Q-A8

### AC-5 A rejected application without an NPI gets no notice
Given Mailpit is empty
When it consumes an ApplicationRejected event with no NPI (error E101, field npi)
Then no email arrives, no NoticeSent event is published, an information log line names the event id, and the event is acknowledged without error
Basis: Q-A7 (nobody to address without an NPI), modern/contracts/openapi/notification-service.yaml (Notice.npi is required), modern/contracts/asyncapi/events.yaml (ApplicationRejectedData.npi is optional)

### AC-6 A revalidation due sends the REVALIDATION_DUE notice
Given Mailpit is empty
When it consumes a RevalidationDue event with a caseId, npi 1003000111 and dueDate 2026-03-31
Then exactly one email arrives in Mailpit addressed to 1003000111@providers.pem.local with the subject of the REVALIDATION_DUE template, and one NoticeSent event with template REVALIDATION_DUE is published
Basis: modern/contracts/asyncapi/events.yaml (RevalidationDue consumed by notification-service), modern/contracts/openapi/notification-service.yaml (template REVALIDATION_DUE), modern/docs/ux/screen-inventory.md (portal notices: approval, denial, sanctions, revalidation), Q-A7, Q-A8

### AC-7 An extract-produced event is consumed without a notice
Given Mailpit is empty
When it consumes an ExtractProduced event (runId, businessDate 2026-03-31, rowCount 3)
Then no email arrives, no NoticeSent event is published and the event is acknowledged without error
Basis: Q-A9 (no NPI and no recipient defined for EXTRACT_READY), modern/contracts/asyncapi/events.yaml (ExtractProduced consumed by notification-service)

### AC-8 A redelivered approval event sends one notice
Given the ProviderStatusChanged event with id E7 (1003000111, P to A) was already processed and its APPROVED email sent
When E7 is delivered again
Then Mailpit still holds exactly one 'Enrolment approved' email for 1003000111 and only one NoticeSent event with template APPROVED exists for it
Basis: ADR-003, .claude/skills/java-microservices/SKILL.md section 7 (idempotent consumers)

## Out of scope
- Sanction notices TRM, SUS and RST, unknown sanction actions and the failed-send and missing-template handling: ntf-send (the same handling applies to these notice types).
- Listing notices and templates: ntf-list.
- Sending an EXTRACT_READY notice to an operations mailbox (Q-A9: not built until the business names a recipient).
- Business-approved wording of subjects and bodies other than 'Enrolment approved' and 'Application rejected' (Q-A8).
- Adding a contact email to the application or provider (Q-A7).

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: No legacy program sends these notices (the PRVNT* programs are missing; PRVSAN01.cbl:124-138 only covers sanction notices). New behaviour from journeys J1, J2, J3 and the approved event contract.
Decisions: D-001 FIX: a rejected application is rejected once and the applicant is told; D-012 REPLACE: notification-service replaces the missing notice programs; D-016 FIX: the rejection carries the validation error codes; D-019 FIX: the approval by an analyst is what the provider is told about

## Technical notes (agent-owned)
Service: notification-service. Endpoints/events: ProviderStatusChanged, ApplicationRejected, RevalidationDue, ExtractProduced, NoticeSent. Roles: none.
