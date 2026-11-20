---
key: MAM-26
localId: L-S004
epic: MAM-1
service: platform
type: Task
title: "Keycloak realm pem with the PROVIDER, ANALYST, SUPERVISOR, ADMIN roles, one test user per role and a SYSTEM service account"
slug: plt-keycloak
specVersion: 1
descriptionSha256: ba16b678d45fafc0e5ae7cb9ea62fc06721d10b257e34a3a47a4311b25f62cde
jiraUpdated: 2026-11-18T09:14:57.946-0600
rules: []
decisions: [D-023]
dependsOn: [MAM-24]
mainJourney: true
labels: [svc-platform, kag-L-S004, agentic, decision-D-023, slice-main-journey]
builtCommit: pending-user-commit
testsSha256: 9fc4035a1c3c0485e416e4234b97ee4f39d05e21d0e860c278ab5586cb6ef688
---
## Business requirement
People must sign in with their own account and see only what their role allows. The identity service has to be set up with the four user roles, one test user for each role, and a separate service role for system-to-system calls, so that every screen and every service call can enforce access.

## Acceptance criteria
### AC-1 Realm pem with five roles
Given the stack is up
When http://localhost:8180/realms/pem/.well-known/openid-configuration is read and the realm roles are listed with the Keycloak admin
Then the realm answers with an issuer ending in /realms/pem and the roles PROVIDER, ANALYST, SUPERVISOR, ADMIN and SYSTEM exist, imported from infra/keycloak/realm-pem.json
Basis: modern/docs/adr/ADR-007-authentication-and-roles.md, modern/docs/architecture/nfr.md (Security), .claude/skills/java-microservices/SKILL.md section 3, .claude/agents/devops-engineer.md deliverable 3

### AC-2 One test user per role
Given the users provider1, analyst1, supervisor1 and admin1
When each signs in through the web UI client
Then each access token carries exactly its own role of the PEM set: PROVIDER, ANALYST, SUPERVISOR and ADMIN respectively
Basis: .claude/commands/phase-5-platform.md step 1 (user decision 2026-11-18: test users provider1, analyst1, supervisor1, admin1), .claude/agents/devops-engineer.md deliverable 3, D-023

### AC-3 No password is committed
Given the committed realm file and compose files
When git grep --untracked is run and a sign-in is tried with a wrong password
Then no password or secret value is found, the sign-in fails, and the four test-user passwords and the Keycloak admin password are read from KC_PROVIDER_PASSWORD, KC_ANALYST_PASSWORD, KC_SUPERVISOR_PASSWORD, KC_ADMIN_PASSWORD and KC_BOOTSTRAP_ADMIN_PASSWORD in modern/infra/.env.local
Basis: .claude/commands/phase-5-platform.md steps 1 and 5, MODERNIZATION.md section 5, modern/docs/PROGRESS.md (Decisions 2026-11-18)

### AC-4 Web UI client uses authorization code with PKCE
Given the realm
When the client of the web UI is inspected
Then it is a public client that uses the authorization code flow with PKCE, its redirect address and web origin are http://localhost:5173 only, and it does not allow the password grant
Basis: modern/docs/adr/ADR-007-authentication-and-roles.md, .claude/skills/frontend-stack/SKILL.md section 2, .claude/agents/devops-engineer.md deliverable 4

### AC-5 A service account carries the SYSTEM role
Given a confidential service-account client for system calls (sanctions-service calling provider-service, schedulers starting batch jobs)
When it requests a token with client credentials
Then the token carries the role SYSTEM and no user role, and the client secret is read from modern/infra/.env.local
Basis: modern/docs/adr/ADR-007-authentication-and-roles.md, modern/docs/architecture/overview.md (difference 1), D-013, QF-8 (default applied)

### AC-6 The PROVIDER user is tied to an NPI
Given provider1 signed in
When its access token is read
Then it carries the claim npi with the value 1003000111, and the tokens of analyst1, supervisor1 and admin1 carry no npi claim
Basis: QF-1 (default applied), modern/docs/ux/journeys.md#J1 step 2, modern/docs/ux/journeys.md#J4 step 4, modern/docs/architecture/nfr.md (a PROVIDER only sees their own data)

## Out of scope
- Sign-in screens and role-based navigation (ui-shell-login)
- Endpoint role checks (each service story and nfr-security)
- Real production identity provider

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: ONLINE/PRVCICS1.cbl:28-61 (the PINQ inquiry had no authorisation, D-023)
Decisions: D-023 FIX: role-based access with Keycloak roles on every endpoint and screen; this story provides the roles and users

## Technical notes (agent-owned)
Service: platform. Endpoints/events: none. Roles: PROVIDER, ANALYST, SUPERVISOR, ADMIN, SYSTEM.
