---
key: MAM-86
localId: L-S075
epic: MAM-14
service: e2e
type: Story
title: "Accessibility: WCAG 2.1 AA on every page, zero axe violations, keyboard-only walk-through (NFR)"
slug: nfr-accessibility
specVersion: 1
descriptionSha256: 77af35e9535c40b424f172e657f389e7854183ebeebf0dc5f4955ab3b3761935
jiraUpdated: 2026-11-18T09:22:59.390-0600
rules: []
decisions: [D-006]
dependsOn: [MAM-62, MAM-64, MAM-66, MAM-68, MAM-70, MAM-73, MAM-77, MAM-80, MAM-85, MAM-87, MAM-90, MAM-92, MAM-93]
mainJourney: false
labels: [svc-e2e, kag-L-S075, agentic, decision-D-006]
builtCommit:
testsSha256:
---
## Business requirement
Every person must be able to use the portal, the console and the public directory, including people who use only a keyboard or a screen reader. This story proves that every page meets the agreed accessibility standard.

## Acceptance criteria
### AC-1 Zero axe violations on every page
Given the 13 screens of modern/docs/ux/screen-inventory.md (5 portal, 7 console, 1 public)
When each is opened in its main state in Playwright and scanned with axe for WCAG 2.0 and 2.1 level A and AA
Then every scan reports zero violations
Basis: modern/docs/architecture/nfr.md (Accessibility), .claude/skills/frontend-stack/SKILL.md section 5, .claude/commands/phase-8-e2e-hardening.md step 4

### AC-2 Error, empty and refused states are scanned too
Given the apply form with errors, the provider search with No provider found, and a console route refused with 403
When each is scanned with axe
Then every scan reports zero violations and form errors appear next to their fields and in a summary linked to the fields
Basis: .claude/skills/frontend-stack/SKILL.md section 5, modern/docs/ux/journeys.md#J2 and #J4

### AC-3 A keyboard-only pass is written up per journey
Given journeys J1 to J7
When each journey's main steps are completed using only the keyboard in Playwright
Then every journey completes, and a written report in modern/docs/reports lists the steps and the verdict for each of the seven journeys
Basis: .claude/commands/phase-8-e2e-hardening.md step 4, modern/docs/ux/journeys.md

### AC-4 Focus is visible and the tab order is logical
Given every page
When the Tab key is pressed through the page
Then each interactive element shows a visible focus indicator and the focus order follows the visual order
Basis: .claude/skills/frontend-stack/SKILL.md section 5

### AC-5 Contrast and status are not left to colour
Given every page
When the axe colour-contrast rule runs and the status badges are read
Then text contrast is at least 4.5:1, and every status shows text with its code, for example Suspended (S), never colour alone
Basis: .claude/skills/frontend-stack/SKILL.md sections 5 and 6, D-006

### AC-6 Page structure
Given every page
When its structure is read
Then it has exactly one h1 and landmarks, every table has headers and every input has a visible label
Basis: .claude/skills/frontend-stack/SKILL.md section 5

### AC-7 A violation breaks the run
Given a page with one axe violation
When make e2e is run
Then the run fails naming the page and the rule, and no violation is suppressed without a written justification in the repository
Basis: modern/docs/architecture/nfr.md (Accessibility), .claude/commands/phase-8-e2e-hardening.md (Exit checks)

## Out of scope
- The Keycloak sign-in page itself
- Screen reader testing with real assistive technology
- Translations

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: ONLINE/PRVCICS1.cbl:28-72 (green-screen inquiry replaced by the console)
Decisions: D-006 FIX: status is always shown as text with its code, including Suspended (S)

## Technical notes (agent-owned)
Service: e2e. Endpoints/events: none. Roles: PROVIDER, ANALYST, SUPERVISOR, ADMIN, PUBLIC.
