---
key: MAM-19
localId: L-S002
epic: MAM-1
service: platform
type: Task
title: "Web UI toolchain scaffold: React, TypeScript, Vite, USWDS, Vitest, Playwright with axe, clients generated from the contracts"
slug: plt-build-2
specVersion: 1
descriptionSha256: 435dd6ddfca6fca7548eaccf1c261e2f029f2674b11ae763f982a863c231cec7
jiraUpdated: 2026-11-18T09:14:16.171-0600
rules: []
decisions: []
dependsOn: [MAM-16]
mainJourney: true
labels: [svc-platform, kag-L-S002, agentic, slice-main-journey]
builtCommit: pending-user-commit
testsSha256: 780e3589e2d353ebbb46fc5ced98f5ff2bea402f46e7074ab158e8613291b283
---
## Business requirement
The provider portal and the analyst console are built as one web application. Its tooling has to be set up once so that every screen is built, tested and checked for accessibility the same way, and so that the application talks to the services only through typed clients generated from the agreed service contracts.

## Acceptance criteria
### AC-1 The web UI builds
Given modern/web-ui with its committed package.json and lock file
When npm ci and then npm run build are run
Then both exit with code 0 and a production bundle is produced in modern/web-ui/dist, which git check-ignore reports as ignored
Basis: .claude/skills/frontend-stack/SKILL.md section 2, .claude/agents/devops-engineer.md ('You may write only': modern/web-ui/ scaffold files), .gitignore (modern/web-ui/dist/)

### AC-2 Stack and folder structure
Given the scaffold
When its configuration and folders are inspected
Then it is React with TypeScript in strict mode on Vite, with React Router and TanStack Query, and has src/app, src/features/portal, src/features/console, src/shared, src/theme, tests/ and e2e/
Basis: .claude/skills/frontend-stack/SKILL.md sections 2 and 3, modern/docs/adr/ADR-008-ui-stack-and-design-system.md

### AC-3 USWDS is the only component library
Given package.json
When its dependencies are read
Then @trussworks/react-uswds is present at an exact version, no other component library is present, and the version is recorded in the ADR Versions (USWDS 3.x)
Basis: .claude/skills/frontend-stack/SKILL.md section 2, modern/docs/adr/ADR-010-versions.md, modern/docs/adr/ADR-008-ui-stack-and-design-system.md

### AC-4 Typed API clients generated from the contracts
Given the nine OpenAPI files in modern/contracts/openapi (version 1.0.0)
When the client generation script (openapi-typescript) is run twice
Then a typed client exists for every operation of every contract, the second run changes no file, and a check that regenerates and compares fails when a generated file was edited by hand
Basis: .claude/skills/frontend-stack/SKILL.md section 2 (never hand-write API types), modern/docs/adr/ADR-008-ui-stack-and-design-system.md

### AC-5 Unit and component tests run
Given a sample component with a Vitest and Testing Library test
When npm test is run
Then it exits 0, and a skipped test makes it fail
Basis: .claude/skills/frontend-stack/SKILL.md section 2, .claude/skills/tdd-workflow/SKILL.md section 4

### AC-6 Playwright runs an accessibility scan and fails on violations
Given a sample page with an input that has no label
When npm run e2e is run with the axe scan
Then the run fails and names the axe violation; the same page with a label passes
Basis: .claude/skills/frontend-stack/SKILL.md section 5, modern/docs/architecture/nfr.md (Accessibility)

### AC-7 Local addresses come from configuration
Given the dev server
When it is started
Then it serves on port 5173 and reads the gateway address (http://localhost:8080) and the Keycloak address (http://localhost:8180, realm pem) from configuration, with no secret in any committed file
Basis: .claude/skills/java-microservices/SKILL.md sections 1 and 3, .claude/skills/frontend-stack/SKILL.md section 2, .claude/agents/devops-engineer.md deliverable 4

## Out of scope
- Screens of the portal and console (ui-* stories)
- Sign-in and role-based navigation (ui-shell-login)
- Wireframes (already in modern/docs/ux/wireframes)
- The walking-skeleton page (plt-walking-skeleton)

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: none (platform enabler)
Decisions: none

## Technical notes (agent-owned)
Service: platform. Endpoints/events: none. Roles: none.
