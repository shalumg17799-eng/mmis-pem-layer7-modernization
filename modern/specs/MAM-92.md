---
key: MAM-92
localId: L-S065
epic: MAM-13
service: web-ui
type: Story
title: "Console: view and change the risk rules (admin)"
slug: ui-console-risk-rules
specVersion: 1
descriptionSha256: 723074e333caee4d7a61a2fd74b12a780651c7709f753de73b7bb60698c829fd
jiraUpdated: 2026-11-18T09:23:42.981-0600
rules: []
decisions: [D-002, D-023, D-024]
dependsOn: [MAM-62, MAM-38]
mainJourney: false
labels: [svc-web-ui, kag-L-S065, agentic, decision-D-002, decision-D-023, decision-D-024]
builtCommit:
testsSha256:
---
## Business requirement
The table that turns a provider's specialty prefix into a risk level (limited, moderate or high) is kept as business data, not buried in a program. An administrator can see the current table with its version, add, change or remove rows, and save the result as a new version. The screen starts with the legacy values and refuses entries that are not valid. Nobody else can change the table.

## Acceptance criteria
### AC-1 The current table is shown with the legacy values
Given The initial rule set (version 1, default tier Limited)
When admin1 opens /console/admin/risk-rules
Then The page calls GET /api/v1/risk-rules and shows 'Version 1 (default tier: Limited (L))' and a table Taxonomy prefix | Tier with 332 High (H), 251 High (H), 341 Moderate (M), 291 Moderate (M) and 225 Moderate (M); no meaning (such as a specialty name) is shown next to a prefix
Basis: modern/docs/ux/wireframes/console-risk-rules.html, Q-005, .claude/skills/pem-domain/SKILL.md section 3, modern/contracts/openapi/risk-service.yaml#RiskRuleSet, R-PRVRSK01-cbl-00004, R-PRVRSK01-cbl-00005, R-PRVRSK01-cbl-00006

### AC-2 A changed table is saved as a new version
Given admin1 is on the page showing version 1
When They add the row 207 with tier Moderate (M) and choose 'Save as new version'
Then The page sends PUT /api/v1/risk-rules with the version it loaded (1), defaultTier L and the six rules, receives 200 and shows 'Version 2' with the six rows returned by the API
Basis: modern/contracts/openapi/risk-service.yaml#replaceRiskRules (replace the table with a new version), modern/docs/ux/wireframes/console-risk-rules.html (Save as new version), Q-005, D-002

### AC-3 Rows and the default tier can be edited
Given The table of version 1
When admin1 changes the tier of 225 to High (H), removes the row 291 and changes the default tier to Moderate (M) before saving
Then The page sends exactly those rows and that default tier in the PUT request body, and the table shown after saving matches what was sent; tiers are chosen from High (H), Moderate (M) and Limited (L) only
Basis: modern/contracts/openapi/risk-service.yaml#RiskRuleSet, modern/contracts/openapi/risk-service.yaml#RiskRule (tier L, M, H), .claude/skills/pem-domain/SKILL.md section 3 (data-driven table), Q-005

### AC-4 Invalid prefixes are refused at the field
Given admin1 is editing the table
When They enter the prefixes '20' (too short), '2o7' (a lower-case letter) or leave a prefix empty and choose 'Save as new version'
Then Nothing is sent; an error summary links to the cell and a message next to it says that a prefix is exactly 3 upper-case letters or digits
Basis: modern/contracts/openapi/risk-service.yaml#RiskRule (taxonomyPrefix pattern ^[0-9A-Z]{3}$), .claude/skills/frontend-stack/SKILL.md section 5, Q-005

### AC-5 Refusals from the service are shown and the table is reloaded
Given Another administrator saved version 2 while admin1 was editing version 1, or the table lists a prefix twice
When admin1 chooses 'Save as new version'
Then The API answers 422 and the page shows the problem detail, reloads and shows the table that the service holds now with its version, and does not show the rejected table as saved; a 400 (a malformed table) is shown the same way
Basis: modern/contracts/openapi/risk-service.yaml#replaceRiskRules (400, 422), D-024

### AC-6 Only administrators open the screen
Given admin1, supervisor1, analyst1 and provider1 are signed in
When Each tries to open /console/admin/risk-rules
Then admin1 sees the table and the Save button; supervisor1, analyst1 and provider1 see no menu entry and the access-denied page when they type the address; if the API answers 403 to a save the page shows 'You are not allowed to change the risk rules'
Basis: D-023, QE-8, modern/docs/ux/screen-inventory.md (console risk-rules, ADMIN), modern/docs/architecture/nfr.md (PUT /api/v1/risk-rules: ADMIN), modern/contracts/openapi/risk-service.yaml#replaceRiskRules (ADMIN)

### AC-7 The risk-rules screen has zero axe violations
Given The Playwright test opens the table, the table with field errors and the page after a refusal
When It runs an axe scan on each and edits and saves the table with the keyboard only
Then Every scan reports zero violations; each cell input has a label that names its row and column; the Add and Remove buttons have names that say which row they act on; errors are linked from the summary; tiers are written as text plus code
Basis: .claude/skills/frontend-stack/SKILL.md section 5, modern/docs/architecture/nfr.md (Accessibility), modern/docs/ux/wireframes/console-risk-rules.html

## Out of scope
- Computing the risk tier and publishing RiskTierAssigned (risk-service)
- Re-tiering providers that already have a tier (a saved table applies to later assessments only, rsk-rules-admin)
- Names or meanings for prefixes (Q-005: none attached)
- A future effective date or a second-person approval of a change
- Who changed the table and when (no audit field in the data model)

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Related rules: R-PRVRSK01-cbl-00004, R-PRVRSK01-cbl-00005, R-PRVRSK01-cbl-00006
Programs: PRVRSK01.cbl:78-97 (CLASSIFY-ONE: taxonomy prefix 332, 251 High; 341, 291, 225 Moderate; else Limited), 103-116 (APPLY-TIER-PARA, LIMITED-TIER, MODERATE-TIER, HIGH-TIER; ALTER at :86 :90 :92, D-002). Job step PRVDAY01.jcl STEP040 (PRVRSK01).
Decisions: D-002 FIX: the risk tier is saved; the table that drives it is now data the administrator can change; D-023 FIX: only ADMIN changes the table; D-024 FIX: refusals are surfaced

## Technical notes (agent-owned)
Service: web-ui. Endpoints/events: GET /api/v1/risk-rules, PUT /api/v1/risk-rules. Roles: ADMIN.
