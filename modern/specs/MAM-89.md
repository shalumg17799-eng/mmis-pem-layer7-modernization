---
key: MAM-89
localId: L-S077
epic: MAM-14
service: e2e
type: Story
title: "Traceability report (rule -> story -> AC -> test -> code) and runbooks"
slug: nfr-traceability
specVersion: 1
descriptionSha256: 07ad0d06059db7ab130b1926bc6f267f8238ac14501883834f10d325c7bbd04a
jiraUpdated: 2026-11-18T09:23:22.947-0600
rules: []
decisions: []
dependsOn: [MAM-69, MAM-72, MAM-74, MAM-76, MAM-79, MAM-81, MAM-83, MAM-84, MAM-86, MAM-88]
mainJourney: false
labels: [svc-e2e, kag-L-S077, agentic]
builtCommit:
testsSha256:
---
## Business requirement
For compliance and for confidence, every legacy business rule that was kept must be traceable to the work item that implements it, the acceptance criterion that states it, the automated test that proves it and the code that carries it, with a plain list of anything not yet proven. Operations staff also need written runbooks for starting, running and fixing the system.

## Acceptance criteria
### AC-1 A rule-to-code trace for every kept rule
Given the knowledge graph and the latest test results
When the traceability report is generated
Then modern/docs/reports/traceability.md has one row for each of the 433 non-DROPPED rules of rule-to-service.csv with its story, AC, test names, code classes and the test result
Basis: .claude/commands/phase-8-e2e-hardening.md step 6, modern/docs/architecture/rule-to-service.csv, QF-9 (default applied)

### AC-2 Rules without a passing test are listed
Given the report
When its summary is read
Then it states the number of rules without a passing test, whose target is zero, and lists every exception with its reason for the user to accept
Basis: .claude/commands/phase-8-e2e-hardening.md (step 6 and Exit checks)

### AC-3 Dropped rules are shown with their decision
Given the 22 DROPPED rules of rule-to-service.csv
When the report's dropped section is read
Then each is listed with its decision (15 by D-011 PRVDEAD1, 7 by D-010 Medicare reciprocity) and no story or test is expected for them
Basis: modern/docs/analysis/defect-register.md (D-010, D-011), modern/docs/architecture/rule-to-service.csv

### AC-4 Every FIX or REPLACE decision traces to a passing AC test
Given the 29 decisions D-001 to D-029
When the decision section is read
Then every FIX or REPLACE decision lists at least one story, AC and passing test, except D-021, which is listed as an exception because its only AC is in the deferred parity story
Basis: .claude/skills/jira-backlog/SKILL.md section 2, modern/docs/analysis/defect-register.md, D-021

### AC-5 The report is generated, not written by hand
Given the generator and the test result files
When it is run twice with no change
Then both runs give the same report apart from the timestamp
Basis: .claude/commands/phase-8-e2e-hardening.md step 6, MODERNIZATION.md section 6 rule 8

### AC-6 The operations runbook covers every job
Given modern/docs/runbooks/operations.md
When it is read
Then it lists the six batch jobs (intake, screening, revalidation sweep, sanction intake, directory extract, T-MSIS extract) with their configured schedule, their manual-trigger endpoint under /api/v1/batch, the business-date parameter, where to read the run result, and how to read logs and health
Basis: .claude/commands/phase-8-e2e-hardening.md step 7, modern/docs/adr/ADR-004-batch-and-scheduling.md, D-020, D-024

### AC-7 The troubleshooting runbook covers the known failure modes
Given modern/docs/runbooks/troubleshooting.md
When it is read
Then it has a section for an unhealthy container, a busy host port, a failed sign-in, events not flowing (including dead-lettered events), and a batch run in status FAILED, each with the commands to look and to fix
Basis: .claude/commands/phase-8-e2e-hardening.md step 7, D-024, QF-13 (default applied)

### AC-8 The local-run runbook was tested
Given modern/docs/runbooks/local-run.md
When its steps are executed from a clean clone
Then the result is recorded in the file, and the file states honestly if it was not executed
Basis: .claude/commands/phase-5-platform.md (Exit checks), .claude/commands/phase-8-e2e-hardening.md step 7, MODERNIZATION.md section 6 rule 8

## Out of scope
- Loading Epic, Story and AC nodes into the graph (Phase 4 kag-curator)
- Code review of the whole system
- Parity with legacy output (par-harness)

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: none (cross-cutting report over all legacy programs)
Decisions: none

## Technical notes (agent-owned)
Service: e2e. Endpoints/events: none. Roles: none.
