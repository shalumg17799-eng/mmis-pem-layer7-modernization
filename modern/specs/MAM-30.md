---
key: MAM-30
localId: L-S020
epic: MAM-3
service: provider-service
type: Story
title: "Revalidation due date: five years, four-digit years, leap day and each completed revalidation"
slug: prv-approve-2
specVersion: 1
descriptionSha256: 737b72e814644281a696358bc7f9f7248819a223440f7d7fd6b35ad0a89f0448
jiraUpdated: 2026-11-18T09:15:20.628-0600
rules: [R-PRVRSK01-ADD-02, R-PRVRSK01-cbl-00008, R-PRVRSK01-cbl-00039, R-PRVRSK01-cbl-00040, R-PRVRSK01-cbl-00041, R-PRVRSK01-cbl-00042, R-PRVRSK01-cbl-00043, R-PRVRSK01-cbl-00044, R-PRVRSK01-cbl-00045, R-PRVRSK01-cbl-00046]
decisions: [D-003, D-004]
dependsOn: [MAM-27]
mainJourney: false
labels: [svc-provider-service, kag-L-S020, agentic, decision-D-003, decision-D-004]
builtCommit: pending-user-commit
testsSha256: 0b6fd18c059b3c0f7d05875ce28cb092518588550dfa680e5e5e55d0ff8a4991
---
## Business requirement
The date by which an active provider must be revalidated is exactly five years after the approval, or after the last completed revalidation, on the same day and month. It is always a real calendar date with a four-digit year, also after the year 2065 and also for approvals on 29 February. Only an approval or a passed revalidation moves the date; screening, risk assessment and a failed re-screening never do. Other services that track due dates are told when the date changes.

## Acceptance criteria
### AC-1 The due date keeps the day and month and adds five years
Given the service clock date is 2029-03-15 and provider 1003000111 is PENDING with screenFlag P
When an ANALYST approves it
Then revalidationDueDate is 2034-03-15 (same month and day, year + 5, 4-digit year), taken from the service clock and not from the request
Basis: R-PRVRSK01-cbl-00008, R-PRVRSK01-cbl-00039, R-PRVRSK01-cbl-00041, R-PRVRSK01-cbl-00045, R-PRVRSK01-cbl-00046, D-004, 42 CFR 455.414 (five-year revalidation; summary from .claude/skills/pem-domain/SKILL.md section 5, UNVERIFIED against eCFR)

### AC-2 The century is never guessed
Given the service clock date is 2065-06-30 for one approval and 2097-04-01 for another
When each provider is approved
Then revalidationDueDate is 2070-06-30 and 2102-04-01 (the legacy 2-digit year with the pivot at 70 would have produced 19700630 and 20020401)
Basis: R-PRVRSK01-cbl-00040, R-PRVRSK01-cbl-00042, R-PRVRSK01-cbl-00043, R-PRVRSK01-cbl-00044, D-004 (pivot at 70 gives 19xx from 2065)

### AC-3 An approval on 29 February gives a real date
Given the service clock date is 2028-02-29
When provider 1003000111 is approved
Then revalidationDueDate is 2033-02-28, never the impossible date 2033-02-29
Basis: R-PRVRSK01-ADD-02, D-004, ADR-009, Q-A3 (last day of February)

### AC-4 A passed revalidation sets the next due date
Given provider 1003000111 is ACTIVE with revalidationDueDate 2026-03-31 and the service clock date is 2026-04-02
When provider-service consumes a ScreeningCompleted event for 1003000111 with reason REVALIDATION and result PASS
Then GET /api/v1/providers/1003000111 returns revalidationDueDate 2031-04-02 (the previous value is overwritten), enrollStatus A and screenFlag P
Basis: D-003, Q-A4, R-PRVRSK01-cbl-00047, modern/docs/architecture/overview.md (main flow 4), modern/docs/ux/journeys.md#J6

### AC-5 The new due date is announced
Given the revalidation in AC-4 has been saved
When the outbox poller publishes the event
Then one ProviderStatusChanged event with key 1003000111 has from A, to A and revalidationDueDate 2031-04-02, so revalidation-service can update its own read model
Basis: Q-A4, modern/docs/architecture/overview.md (difference 4: revalidation-service keeps a read model from ProviderStatusChanged), modern/contracts/asyncapi/events.yaml (ProviderStatusChanged.revalidationDueDate)

### AC-6 A failed re-screening leaves the due date and status alone
Given provider 1003000111 is ACTIVE with revalidationDueDate 2026-03-31
When a ScreeningCompleted event with reason REVALIDATION and result FAIL is consumed
Then GET /api/v1/providers/1003000111 still returns revalidationDueDate 2026-03-31 and enrollStatus A, and no ProviderStatusChanged event is published
Basis: Q-A4, R-PRVSCR01-ADD-03

### AC-7 Only approval and a passed revalidation move the date
Given provider 1003000111 is PENDING with revalidationDueDate null, and provider 1003000333 is ACTIVE with revalidationDueDate 2031-11-18
When ScreeningCompleted PASS with reason INITIAL is consumed for the first and reason MANUAL for the second
Then GET /api/v1/providers/{npi} still returns revalidationDueDate null for 1003000111 and 2031-11-18 for 1003000333
Basis: D-003, Q-A4 (a MANUAL result changes only the screen flag), R-PRVRSK01-ADD-01

### AC-8 A redelivered revalidation result is applied once
Given the ScreeningCompleted REVALIDATION PASS event with id E4 was already processed
When E4 is delivered again (on a later clock date)
Then revalidationDueDate keeps its value and no second ProviderStatusChanged event is published
Basis: ADR-003, .claude/skills/java-microservices/SKILL.md section 7

## Out of scope
- Opening revalidation cases and the monthly sweep: rev-sweep, rev-cases (D-029).
- The re-screening itself: scr-rescreen.
- What a failed revalidation leads to (suspension or termination): a supervisor sanction (prv-status-change); no automatic action (Q-A4).
- The legacy 2-digit-year logic, the pivot at 70 and the daily reset (D-003, D-004): not reproduced.

## Legacy trace (agent-owned, do not edit)
Rules: R-PRVRSK01-ADD-02, R-PRVRSK01-cbl-00008, R-PRVRSK01-cbl-00039, R-PRVRSK01-cbl-00040, R-PRVRSK01-cbl-00041, R-PRVRSK01-cbl-00042, R-PRVRSK01-cbl-00043, R-PRVRSK01-cbl-00044, R-PRVRSK01-cbl-00045, R-PRVRSK01-cbl-00046
Related rules: R-PRVRSK01-cbl-00047
Programs: PRVRSK01: source-code/BATCH/PRVRSK01.cbl:127-137 (SET-REVALIDATION-DATE: ACCEPT date 127, YY 128, MMDD 129, MOD(YY+5,100) 130, pivot 131-134, date 136, MOVE 137); 129-136 (Feb 29, ADD-02). Job step: PRVDAY01 STEP040 (source-code/JCL/PRVDAY01.jcl).
Decisions: D-003 FIX: the due date is set at approval and at each completed revalidation; D-004 FIX: 4-digit-year dates everywhere; no 2-digit year or century pivot is built

## Technical notes (agent-owned)
Service: provider-service. Endpoints/events: ScreeningCompleted, ProviderStatusChanged. Roles: none.
