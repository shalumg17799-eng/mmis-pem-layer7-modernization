---
key: MAM-17
localId: L-S016
epic: MAM-3
service: provider-service
type: Story
title: "Register a validated application as a PENDING, UNSCREENED provider"
slug: prv-register
specVersion: 1
descriptionSha256: 4d80fe3be7b4d564f12e234aa02982645cad1166b47d93fc852130232bc37589
jiraUpdated: 2026-11-18T09:14:04.494-0600
rules: [R-PRVENR01-ADD-02, R-PRVENR01-cbl-00007, R-PRVENR01-cbl-00040, R-PRVENR01-cbl-00041, R-PRVENR01-cbl-00042, R-PRVENR01-cbl-00051, R-PRVENR01-cbl-00056, R-PRVENR01-cbl-00058]
decisions: [D-001, D-003, D-004, D-007, D-008, D-014, D-016, D-019, D-028]
dependsOn: [MAM-31, MAM-34]
mainJourney: true
labels: [svc-provider-service, kag-L-S016, agentic, decision-D-001, decision-D-003, decision-D-004, decision-D-007, decision-D-008, decision-D-014, decision-D-016, decision-D-019, decision-D-028, slice-main-journey]
builtCommit: pending-user-commit
testsSha256: 4ed41804960c8a6d60abe95aab8b60865638e63b5fbc84af7525a8207ef92771
---
## Business requirement
When an enrolment application has passed its checks, the state must record the applicant as a provider in one single provider record that every other part of the system relies on. A newly recorded provider is always waiting for a decision and has not yet been screened. Every detail on the application is kept, including the owner of a group or a facility. An application that failed its checks never becomes a provider, and an application for a provider who is already on file never overwrites the existing record.

## Acceptance criteria
### AC-1 A validated individual application becomes a PENDING, UNSCREENED provider
Given provider-service is running and no provider with NPI 1003000111 exists
When it consumes an ApplicationValidated event for NPI 1003000111 (Medicaid ID MCD000111, provider type IN, taxonomy 207Q00000X, individual: last name RIVERA, first name MARIA, licence MD12345, licence state TX)
Then GET /api/v1/providers/1003000111 as SYSTEM returns 200 with enrollStatus P, screenFlag U, riskTier null, revalidationDueDate null and the NPI, Medicaid ID, provider type, taxonomy and individual fields exactly as supplied
Basis: R-PRVENR01-cbl-00007, R-PRVENR01-cbl-00040, R-PRVENR01-cbl-00041, R-PRVENR01-cbl-00042, R-PRVENR01-cbl-00056, D-001 (the status is always set by the service, never taken from the input), D-003 (no due date before approval), D-008, D-014, D-019

### AC-2 Registration publishes ProviderRegistered
Given the registration of NPI 1003000111 in AC-1 has been saved
When the outbox poller publishes the event
Then exactly one ProviderRegistered event is on topic pem.provider.events with message key 1003000111 and data npi 1003000111, medicaidId MCD000111, providerType IN, taxonomy 207Q00000X, enrollStatus P, screenFlag U and displayName 'MARIA RIVERA' (first name + last name)
Basis: modern/contracts/asyncapi/events.yaml (ProviderRegistered, channel pem.provider.events), ADR-003, R-PRVENR01-cbl-00042, D-028 (name composition: individual first + last, organisation legal name)

### AC-3 A group keeps every field, including the owner NPI
Given no provider with NPI 1003000444 exists
When provider-service consumes an ApplicationValidated event for NPI 1003000444 (Medicaid ID MCD000444, provider type GR, taxonomy 251E00000X, organization: legal name EXCLUDED CLINIC LLC, DBA name EXCLUDED CLINIC, owner NPI 1990000111)
Then GET /api/v1/providers/1003000444 returns organization.legalName EXCLUDED CLINIC LLC, organization.dbaName EXCLUDED CLINIC and organization.ownerNpi 1990000111 (the owner NPI is not lost), no individual block, enrollStatus P and screenFlag U; the ProviderRegistered event carries displayName 'EXCLUDED CLINIC LLC'
Basis: R-PRVENR01-ADD-02, D-007, D-008, D-028, modern/docs/analysis/data-layouts.md (PRV-ORG-DATA, PRV-OWNER-NPI bytes 121-130)

### AC-4 A duplicate NPI never overwrites the existing provider
Given provider 1003000111 is registered and its status is A
When a second ApplicationValidated event (a different event id) for NPI 1003000111 arrives
Then GET /api/v1/providers/1003000111 still returns the existing record unchanged (status A, same version); no second ProviderRegistered event is published; the service log contains 'DUPLICATE NPI 1003000111'; and the skipped-registrations counter on /actuator/metrics rises by 1
Basis: R-PRVENR01-cbl-00058, R-PRVENR01-cbl-00051, D-001, D-016 (E103 is raised at submission by enrollment-service; this is the second line of defence), .claude/skills/java-microservices/SKILL.md section 11 (Actuator metrics, JSON logs)

### AC-5 A rejected application never creates a provider
Given enrollment-service rejected the application for NPI 1003000222 (submitted with an empty taxonomy, E104) and published only ApplicationRejected for it, and published ApplicationValidated for NPI 1003000111
When provider-service has processed every event on pem.enrollment.events and an ANALYST looks for NPI 1003000222
Then GET /api/v1/providers/1003000222 returns 404, GET /api/v1/providers?npi=1003000222 returns total 0, no ProviderRegistered event exists for 1003000222, and NPI 1003000111 is registered normally (P, U)
Basis: D-001, R-PRVENR01-cbl-00051 (routing an invalid record to the write step is the defect to correct), modern/docs/architecture/overview.md (main flow 1: an invalid application is never registered), modern/docs/ux/journeys.md#J2

### AC-6 A redelivered event registers once
Given the ApplicationValidated event with id E1 for NPI 1003000111 was already processed
When the same event E1 is delivered again
Then there is still exactly one provider 1003000111, exactly one ProviderRegistered event exists, and the skipped-registrations counter is unchanged (a replay is ignored, not counted as a duplicate)
Basis: ADR-003, .claude/skills/java-microservices/SKILL.md section 7 (idempotent consumers, processed_event)

### AC-7 The effective date is the registration date, in ISO form
Given the service clock date is 2026-11-18
When provider 1003000111 is registered
Then GET /api/v1/providers/1003000111 returns effectiveDate 2026-11-18 (a 4-digit-year ISO date) and revalidationDueDate null
Basis: Q-A1 (effective date = registration date), D-004, ADR-009

## Out of scope
- Validation of the application (E101 missing NPI, E102 invalid type, E103 duplicate NPI at submission, E104 missing taxonomy) and the rejection notice: enrollment-service and notification-service (ntf-send-2).
- Screening, risk tier, approval and sanctions: prv-screen-verdict, prv-risk-tier, prv-approve, prv-status-change.
- Reading and searching the record: prv-search-read. Exporting the 130-byte master: prv-legacy-master.
- Owner disclosure rows (provider_owner: owner name, ownership percentage, disclosure date): no legacy program or application field feeds them.
- The 200-byte APPINPUT intake layout (bytes 131-200 reserved, D-025): enrollment-service.

## Legacy trace (agent-owned, do not edit)
Rules: R-PRVENR01-ADD-02, R-PRVENR01-cbl-00007, R-PRVENR01-cbl-00040, R-PRVENR01-cbl-00041, R-PRVENR01-cbl-00042, R-PRVENR01-cbl-00051, R-PRVENR01-cbl-00056, R-PRVENR01-cbl-00058
Programs: PRVENR01: source-code/BATCH/PRVENR01.cbl:57-159 (master record, WRITE-PROVIDER-RECORD), 146-148 (VALIDATE-APPLICATION: SET PRV-PENDING, SET PRV-UNSCREENED, PERFORM WRITE-PROVIDER-RECORD), 157-171 (WRITE-PROVIDER-RECORD, duplicate key 163-166). Job step: PRVDAY01 STEP010 (source-code/JCL/PRVDAY01.jcl).
Decisions: D-001 FIX: every application is processed; an invalid one is rejected once and never reaches the master; D-003 FIX: no due date at registration; it is set at approval; D-004 FIX: 4-digit-year ISO dates; D-007 FIX: the record is 130 bytes; the owner NPI (bytes 121-130) is kept; D-008 PRESERVE: provider types IN, GR, FA; D-014 FIX: provider-service is the single owner of provider identity; D-016 FIX: E103 duplicate NPI is raised at submission by enrollment-service; here the existing record is kept and the skip counted; D-019 FIX: registration always yields PENDING; only an analyst approval sets ACTIVE; D-028 FIX: the display name is composed from the name fields

## Technical notes (agent-owned)
Service: provider-service. Endpoints/events: ApplicationValidated, ProviderRegistered. Roles: none.
