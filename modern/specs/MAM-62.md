---
key: MAM-62
localId: L-S053
epic: MAM-12
service: web-ui
type: Story
title: "Web UI shell: sign in with Keycloak, role-based navigation, portal home"
slug: ui-shell-login
specVersion: 1
descriptionSha256: 454d2cb9dd5965b33b4fe908254fe6e20b1936eb2408e6961de067c251e2c7fa
jiraUpdated: 2026-11-18T09:19:49.610-0600
rules: []
decisions: [D-023, D-006, D-019, D-004, D-024]
dependsOn: [MAM-34, MAM-26, MAM-54, MAM-37]
mainJourney: true
labels: [svc-web-ui, kag-L-S053, agentic, decision-D-023, decision-D-006, decision-D-019, decision-D-004, decision-D-024, slice-main-journey]
builtCommit: pending-user-commit
testsSha256: 8f222e01e2716853d38bc23db8aa36bf2cb6461c3a392ded4e0d78c07958afb8
---
## Business requirement
People use one web application to work with the enrolment system. Everyone signs in with their own account, and after signing in each person sees only the areas their role allows: providers see their own enrolment pages, analysts and supervisors see the working screens for provider checks and approvals, and administrators see the set-up and batch-job screens. Someone who is not allowed to open a page is told so clearly instead of being shown another person's data. A provider's start page lists their applications and summarises their own record, with an obvious way to apply for enrolment.

## Acceptance criteria
### AC-1 Sign-in is required and uses the identity service
Given A visitor who is not signed in
When They open /portal, /console/providers or any other /portal or /console route
Then The browser is sent to the sign-in page of the Keycloak realm pem (client pem-web) using the authorization code flow with PKCE (the authorize request carries an S256 code challenge and the web UI never handles a password); after a successful sign-in the visitor lands on the route that was asked for, and every API call carries the access token as a bearer token to the gateway at http://localhost:8080
Basis: ADR-007, ADR-008, .claude/skills/frontend-stack/SKILL.md section 2, modern/infra/keycloak/realm-pem.json (client pem-web, pkce.code.challenge.method S256), D-023, modern/docs/ux/journeys.md#J1 (step 1)

### AC-2 The menu shows only what the role allows
Given The test users provider1 (PROVIDER), analyst1 (ANALYST), supervisor1 (SUPERVISOR) and admin1 (ADMIN) defined in the Keycloak realm file
When Each user signs in
Then provider1 sees the menu entries My enrolment (/portal), Apply for enrolment (/portal/apply), My provider record (/portal/provider) and My notices (/portal/notices) and no console entry; analyst1 and supervisor1 see Provider search (/console/providers) and Work queues (/console/queues); admin1 sees Provider search, Risk-tier rules (/console/admin/risk-rules) and Batch jobs (/console/admin/jobs)
Basis: modern/docs/ux/screen-inventory.md (roles column), modern/docs/ux/wireframes/index.html, D-023, .claude/commands/phase-5-platform.md (test users), modern/infra/keycloak/realm-pem.json

### AC-3 A role cannot open the areas of other roles
Given provider1, analyst1 and admin1 are each signed in
When provider1 types /console/providers in the address bar, analyst1 types /portal/apply, and admin1 types /console/queues
Then Each sees an access-denied page (heading 'Access denied', the text 403 and a link to their own start page) and the browser makes no API call for the data of the page it was refused; the same happens for every route outside the user's role in the screen inventory
Basis: D-023, R-PRVCICS1-ADD-05, modern/docs/ux/journeys.md#J4 (step 4), modern/docs/ux/screen-inventory.md, modern/docs/architecture/nfr.md (Security: roles per endpoint)

### AC-4 Portal home lists my applications and leads to the form
Given provider1 (NPI 1003000111) is signed in and has one application, submitted on 03/01/2026, that is Validated
When /portal opens
Then The page calls GET /api/v1/applications and shows a table with the columns Submitted (03/01/2026), NPI (1003000111 in full), Status ('Validated') and a View link to /portal/applications/:applicationId; a status is always written as text ('Submitted', 'Validated' or 'Rejected'), never by colour alone; a button 'Apply for enrolment' leads to /portal/apply; with no applications the table is replaced by the text 'No applications yet'
Basis: modern/docs/ux/wireframes/portal-home.html, modern/docs/ux/screen-inventory.md (portal home), modern/docs/ux/journeys.md#J1 (step 1), ADR-009, D-004, .claude/skills/frontend-stack/SKILL.md section 6, modern/contracts/openapi/enrollment-service.yaml#listApplications

### AC-5 Portal home summarises my own provider record
Given provider1's access token carries the claim npi = 1003000111 and that provider is Active (A) with screen flag Pass (P)
When /portal opens
Then The page calls GET /api/v1/providers/1003000111 (the NPI comes from the token, never from something typed) and shows NPI, Medicaid ID, Provider type 'Individual (IN)', Taxonomy, 'Enrolment status' 'Active (A)' and 'Screen flag' 'Pass (P)' with a link to My provider record; when the provider does not exist yet (404) the summary says that no provider record exists yet and the applications table is still shown
Basis: modern/docs/ux/wireframes/portal-home.html, modern/infra/keycloak/realm-pem.json (npi claim), D-006, D-019, D-023, .claude/skills/frontend-stack/SKILL.md section 6, modern/contracts/openapi/provider-service.yaml#getProvider

### AC-6 Sign-out and ended sessions
Given provider1 is signed in
When They choose Sign out, or the API answers a call with 401 because the token expired
Then After Sign out the Keycloak session is ended and the browser shows a signed-out page, and opening /portal asks for sign-in again; after a 401 the user is sent to sign in again and no data of the earlier page stays on the screen, instead of an empty or half-filled page
Basis: ADR-007, modern/infra/keycloak/realm-pem.json (post.logout.redirect.uris), D-024, modern/contracts/openapi/provider-service.yaml (401 Not authenticated), .claude/skills/frontend-stack/SKILL.md section 2

### AC-7 The shell and portal home have zero axe violations
Given The Playwright test visits the portal home (with applications, without applications), the signed-out page and the access-denied page
When It runs an axe scan on each page and walks the menu with the keyboard only
Then Every scan reports zero violations; each page has one h1 and the landmarks header, nav and main; a 'Skip to main content' link is the first focus stop; menu entries, View links and 'Apply for enrolment' can be reached and used with Tab and Enter and the focus is always visible
Basis: .claude/skills/frontend-stack/SKILL.md section 5, modern/docs/architecture/nfr.md (Accessibility), modern/docs/ux/wireframes/portal-home.html, modern/docs/ux/journeys.md (every journey runs an axe scan)

## Out of scope
- Creating or managing user accounts and passwords (Keycloak administration, plt-keycloak)
- The contents of the other portal and console pages (their own stories)
- A production identity provider
- Self-service account pages, password reset and multi-factor sign-in
- Showing or listing another provider's data to a provider, which is never allowed

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Related rules: R-PRVCICS1-ADD-05
Programs: PRVCICS1.cbl:28-61 (MAIN-LOGIC: no authorisation check, any user of PINQ reads any provider; R-PRVCICS1-ADD-05, D-023). No job step; the CICS transaction PINQ has no sign-on in the estate.
Decisions: D-023 FIX (accepted, Q-010): role-based access (Keycloak roles) on every endpoint and screen; D-006 A new SUSPENDED status with its own label; D-019 Analyst approval sets ACTIVE; the provider's status is shown after approval; D-004 FIX: 4-digit years in every date, shown as MM/DD/YYYY; D-024 FIX: an ended session is surfaced, never shown as an empty page

## Technical notes (agent-owned)
Service: web-ui. Endpoints/events: GET /api/v1/applications, GET /api/v1/providers/{npi}. Roles: PROVIDER, ANALYST, SUPERVISOR, ADMIN.
