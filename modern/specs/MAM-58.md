---
key: MAM-58
localId: L-S043
epic: MAM-9
service: notification-service
type: Story
title: "Send sanction notices by email (TRM, SUS, RST), reject unknown actions and handle failures"
slug: ntf-send
specVersion: 1
descriptionSha256: 657c87e3eac579688b037373802262766587ed3b12b87270f6ef46b2019658ec
jiraUpdated: 2026-11-18T09:18:53.309-0600
rules: [R-PRVSAN01-cbl-00008, R-PRVSAN01-cbl-00009, R-PRVSAN01-cbl-00032, R-PRVSAN01-cbl-00036, R-PRVSAN01-cbl-00037, R-PRVSAN01-cbl-00038, R-PRVSAN01-cbl-00039, R-PRVSAN01-cbl-00044, R-PRVSAN01-cbl-00045, R-PRVSAN01-cbl-00046, R-PRVSAN01-cbl-00047, R-PRVSAN01-cbl-00048, R-PRVSAN01-cbl-00049, R-PRVSAN01-cbl-00050, R-PRVSAN01-cbl-00051, R-PRVSAN01-cbl-00052, R-PRVSAN01-cbl-00053]
decisions: [D-012, D-024, D-026]
dependsOn: [MAM-31, MAM-24]
mainJourney: true
labels: [svc-notification-service, kag-L-S043, agentic, decision-D-012, decision-D-024, decision-D-026, slice-main-journey]
builtCommit:
testsSha256: 9044ccf84146b7a21c24f6545ffba9af498d34ad0e508cb695876dff0955647a
---
## Business requirement
When a sanction is recorded against a provider (termination, suspension or reinstatement), the provider must be told by email with the matching notice. This replaces the legacy notice programs that were never delivered. A sanction with an action that is not recognised is refused and reported instead of being sent as a general notice. A notice that cannot be sent, or a missing template, must never stop the processing and never undo the status change; it is recorded as failed and made visible. Each notice is sent once even if the triggering event arrives twice, and every sent notice is announced so that it appears in the provider's history.

## Acceptance criteria
### AC-1 A termination sends the TRM notice
Given notification-service is running with the seeded templates and Mailpit is empty
When it consumes a SanctionApplied event for NPI 1003000111 with action T, reason 'LEIE exclusion' and effectiveDate 2026-03-31
Then exactly one email arrives in Mailpit addressed to 1003000111@providers.pem.local with the subject of the TRM template and the TRM body filled in with NPI 1003000111
Basis: R-PRVSAN01-cbl-00044, R-PRVSAN01-cbl-00045, R-PRVSAN01-cbl-00046, R-PRVSAN01-cbl-00050, R-PRVSAN01-cbl-00051, R-PRVSAN01-cbl-00008, D-012, Q-A7, Q-A8

### AC-2 A suspension sends the SUS notice
Given Mailpit is empty
When it consumes a SanctionApplied event for NPI 1003000111 with action S, reason 'LEIE exclusion' and effectiveDate 2026-03-31
Then exactly one email arrives in Mailpit addressed to 1003000111@providers.pem.local with the subject of the SUS template and the SUS body filled in with NPI 1003000111
Basis: R-PRVSAN01-cbl-00045, R-PRVSAN01-cbl-00047, R-PRVSAN01-cbl-00032, D-012, Q-A7, Q-A8, modern/docs/ux/journeys.md#J5

### AC-3 A reinstatement sends the RST notice
Given Mailpit is empty
When it consumes a SanctionApplied event for NPI 1003000111 with action R, reason 'Exclusion lifted' and effectiveDate 2026-04-30
Then exactly one email arrives in Mailpit addressed to 1003000111@providers.pem.local with the subject of the RST template and the RST body filled in with NPI 1003000111
Basis: R-PRVSAN01-cbl-00045, R-PRVSAN01-cbl-00048, R-PRVSAN01-cbl-00036, R-PRVSAN01-cbl-00037, R-PRVSAN01-cbl-00039 (no legacy code sends it: built new), D-012, Q-A7, Q-A8, modern/docs/ux/journeys.md#J5

### AC-4 An action outside T, S and R is refused, not sent as a general notice
Given Mailpit is empty
When it consumes a SanctionApplied event for NPI 1003000111 whose action is X (a value the contract does not allow), followed by a valid one with action S for NPI 1003000444
Then no email and no NoticeSent event are produced for the first event, an error log line names its event id and the action X, the event is acknowledged without stopping the consumer, and the SUS email for 1003000444 arrives
Basis: R-PRVSAN01-cbl-00049 (legacy GEN fallback, superseded), D-026 (unknown action codes are rejected; the decision wins, Q-001), D-024, modern/contracts/asyncapi/events.yaml (SanctionApplied.action enum T, S, R), Q-A10

### AC-5 Every sent notice is announced
Given the TRM notice of AC-1 was sent by email
When the outbox poller publishes the event
Then exactly one NoticeSent event is on topic pem.notification.events with key 1003000111 and data noticeId, npi 1003000111 and template TRM
Basis: R-PRVSAN01-cbl-00052, modern/contracts/asyncapi/events.yaml (NoticeSent), D-012, ADR-003

### AC-6 A failed send is logged, surfaced and does not stop the processing
Given the provider status of 1003000111 was already changed to S and the mail server (Mailpit) is stopped
When a SanctionApplied event for NPI 1003000111 (action S) and then one for NPI 1003000444 (action T) are consumed
Then for each, a warning log line names the template (SUS, then TRM), the NPI, status FAILED and the cause; no NoticeSent event is published for either; the second event is processed although the first failed; and GET /api/v1/providers/1003000111 still returns enrollStatus S (nothing is rolled back)
Basis: R-PRVSAN01-cbl-00009, R-PRVSAN01-cbl-00038, R-PRVSAN01-cbl-00053, D-024, D-012

### AC-7 A missing template is a named failure, not a crash
Given the TRM template row has been removed and Mailpit is empty
When a SanctionApplied event with action T for NPI 1003000111 is consumed, followed by one with action S for NPI 1003000444
Then for the first, the log contains 'NOTICE TEMPLATE NOT FOUND TRM' (the legacy 'NOTICE PGM NOT FOUND' with the template code) and status FAILED, and no email and no NoticeSent are produced; the SUS email for 1003000444 still arrives
Basis: R-PRVSAN01-cbl-00009, R-PRVSAN01-cbl-00038, R-PRVSAN01-cbl-00053, R-PRVSAN01-cbl-00050 (the notice is found by code, not built from a program name), D-012, D-024

### AC-8 A redelivered event sends one notice
Given the SanctionApplied event with id E6 for NPI 1003000111 (action S) was already processed
When E6 is delivered again
Then Mailpit still holds exactly one SUS email for NPI 1003000111 and only one NoticeSent event exists for it
Basis: ADR-003, .claude/skills/java-microservices/SKILL.md section 7 (idempotent consumers)

## Out of scope
- Notices for approval, denial, rejection and revalidation: ntf-send-2.
- Listing notices (including the FAILED ones) and templates: ntf-list.
- Notifying the downstream systems Claims and MCO named in the legacy program header (no interface exists in the estate; Q-A7).
- The general notice GEN: seeded and listed, but not sent for any event (Q-A10).
- Resending a FAILED notice: no rule and no decision exist for it.
- Final notice wording, including which sanction details (reason, effective date) appear in the body: placeholder text is seeded as data for the business to replace (Q-A8).
- A contact email on the provider record: not held by the legacy master (Q-A7).

## Legacy trace (agent-owned, do not edit)
Rules: R-PRVSAN01-cbl-00008, R-PRVSAN01-cbl-00009, R-PRVSAN01-cbl-00032, R-PRVSAN01-cbl-00036, R-PRVSAN01-cbl-00037, R-PRVSAN01-cbl-00038, R-PRVSAN01-cbl-00039, R-PRVSAN01-cbl-00044, R-PRVSAN01-cbl-00045, R-PRVSAN01-cbl-00046, R-PRVSAN01-cbl-00047, R-PRVSAN01-cbl-00048, R-PRVSAN01-cbl-00049, R-PRVSAN01-cbl-00050, R-PRVSAN01-cbl-00051, R-PRVSAN01-cbl-00052, R-PRVSAN01-cbl-00053
Programs: PRVSAN01: source-code/BATCH/PRVSAN01.cbl:124-138 (GENERATE-NOTICE: suffix TRM 126, SUS 127, RST 128, GEN 129, STRING 131-133, dynamic CALL 134, ON EXCEPTION 135-137), 116 (PERFORM GENERATE-NOTICE after the REWRITE). The PRVNTTRM, PRVNTSUS, PRVNTRST and PRVNTGEN programs are missing from the estate (R-PRVSAN01-cbl-00039 NOT_FOUND). No job runs PRVSAN01.
Decisions: D-012 REPLACE: notification-service sends the TRM, SUS and RST notices by email and logs them, instead of the dynamic CALL to missing programs; D-024 FIX: a failed notice is recorded and logged; processing continues; D-026 FIX: an unknown action code is rejected (no GEN notice is sent for it; Q-A10)

## Technical notes (agent-owned)
Service: notification-service. Endpoints/events: SanctionApplied, NoticeSent. Roles: none.
