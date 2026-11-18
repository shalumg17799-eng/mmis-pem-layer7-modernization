---
description: Phase 3. Design the microservices (contracts, data model, mappings, NFRs, ADRs) and the UX (screen inventory, wireframes, journeys) for approval.
---

# Phase 3: Architecture and UX

**Goal:** an approved design in which every verified rule has exactly one owning service,
every legacy field has a home or a reason to drop, and every screen knows which API it
calls.

## Preconditions
Phase 2 is `APPROVED`.

## Steps

1. **Architecture.** Delegate to architect: all the Phase 3 deliverables in its agent
   file.
2. **UX.** After step 1, delegate to ui-developer (Phase 3 part): the screen inventory,
   the wireframes and the journeys.
3. **Your checks (orchestrator).** Run these and show the results:
   - **Rule coverage:** every rule in `rule-verification.csv` that is not DROPPED
     appears exactly once in `rule-to-service.csv`. Write a small script in
     `modern/tools/checks/` and run it.
   - **Field coverage:** every field in `data-layouts.md` appears in
     `field-to-column.csv` (as a column or DROP).
   - **Contracts lint clean:** `npx @redocly/cli lint modern/contracts/openapi/*.yaml`.
     For AsyncAPI: `npx @asyncapi/cli validate modern/contracts/asyncapi/events.yaml`.
   - Every endpoint lists roles. Every event has a producer and at least one consumer.
   - Every API call in `screen-inventory.md` exists in a contract.
4. **Load.** kag-curator runs the Phase 3 loader.
5. **Review with the user (human in the loop).** Show:
   - the service table
   - every deviation from the default design, with its reason
   - the ADR list (PROPOSED)
   - the open assumptions
   - the path to `modern/docs/ux/wireframes/index.html`, to open in a browser

   Ask for approval or changes. Apply the changes, re-run the step 3 checks, and show the
   result again.
6. **On approval:** set every ADR's status to `ACCEPTED`, and tag the contracts as
   version `1.0.0` in their `info.version`.
7. Commit: `docs(phase-3): architecture, contracts, ADRs, UX`.

## Exit checks
- [ ] Rule coverage and field coverage show zero gaps.
- [ ] Contracts lint clean. ADRs are ACCEPTED. Wireframes are reviewed by the user.

## Finish
PROGRESS: phase 3 → `AWAITING APPROVAL`, and record the decisions made in this review.
Give the phase report. Next: `/phase-4-backlog`. **Stop.**
