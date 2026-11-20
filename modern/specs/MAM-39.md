---
key: MAM-39
localId: L-S009
epic: MAM-1
service: platform
type: Task
title: "CI pipeline: build, check, contract lint, web-ui tests, compose smoke test"
slug: plt-ci
specVersion: 1
descriptionSha256: 9c5310ed3a3824f45df92f6ad33dceb43df2d623b09618a348898b587a21a2bf
jiraUpdated: 2026-11-18T09:16:12.617-0600
rules: []
decisions: []
dependsOn: [MAM-16, MAM-19, MAM-24, MAM-34]
mainJourney: false
labels: [svc-platform, kag-L-S009, agentic]
builtCommit:
testsSha256:
---
## Business requirement
Every change should be checked automatically with the same steps the team runs by hand, so that a broken build, a violated contract or a failing screen is noticed before anyone depends on it.

## Acceptance criteria
### AC-1 Build and full check on every change
Given the CI workflow
When a change is pushed or proposed
Then it runs the Gradle build and check (unit, acceptance, integration, contract, ArchUnit, JaCoCo, PIT), and a change that lowers coverage below 85% turns the run red
Basis: .claude/agents/devops-engineer.md deliverable 6, .claude/skills/tdd-workflow/SKILL.md section 2 step 7 and section 4, QF-5 (default applied)

### AC-2 Contracts are linted
Given the OpenAPI and AsyncAPI files in modern/contracts
When the contract lint step runs
Then it lints all nine OpenAPI files and validates the AsyncAPI file, and a deliberate error in one of them fails the run
Basis: .claude/agents/devops-engineer.md deliverable 6, modern/docs/PROGRESS.md (activity phase3.3: Redocly lint clean, AsyncAPI valid)

### AC-3 Web UI tests run
Given modern/web-ui
When the web-ui step runs
Then npm ci and npm test run and a failing or skipped test fails the step
Basis: .claude/agents/devops-engineer.md deliverable 6, .claude/skills/tdd-workflow/SKILL.md section 4

### AC-4 A compose smoke test proves the stack starts
Given a clean CI runner with Docker
When the smoke step runs make up, then calls the gateway health and the provider lookup of the walking skeleton, then make down
Then every container is healthy, both calls answer as expected, and the step fails and prints the logs of any container that is not healthy
Basis: .claude/agents/devops-engineer.md deliverable 6, .claude/commands/phase-5-platform.md (Goal)

### AC-5 Pinned tools and a written fallback
Given the workflow file and modern/docs/runbooks/ci.md
When both are read
Then every action and image in the workflow has an exact version (no latest), and modern/docs/runbooks/ci.md lists the same steps (build and check, contract lint, web-ui tests, compose smoke test) with their exact commands for anyone who does not use the workflow
Basis: .claude/agents/devops-engineer.md (Rules, deliverable 6), QF-5 (default applied)

### AC-6 The workflow is inert until the user decides
Given the repository state
When the workflow file's location is checked
Then it is at modern/.github/workflows/ci.yml, nothing is pushed, and the runbook says it must be copied to the repository root .github/workflows to run on GitHub
Basis: QF-5 (default applied), modern/docs/PROGRESS.md (Phase 0 note: git stays local, push only when the user asks), .claude/agents/devops-engineer.md ('You may write only': modern/.github/**)

## Out of scope
- Security scans such as the dependency check (nfr-security)
- Deployment and release
- Running the Playwright journeys in CI (e2e stories, make e2e)

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: none (platform enabler)
Decisions: none

## Technical notes (agent-owned)
Service: platform. Endpoints/events: GET /api/v1/providers/{npi}. Roles: none.
