---
description: Phase 6. Build one microservice story by story with strict TDD, review and traceability, then verify the whole service. Run once per service, in the build order.
argument-hint: <service-name, e.g. provider-service>
---

# Phase 6: Build service `$ARGUMENTS`

**Goal:** every story of this service's epic is built test-first, reviewed and traced,
and the service works inside the full stack.

## Preconditions
- `$ARGUMENTS` is one of the services in `.claude/skills/java-microservices/SKILL.md`.
  If it is empty or unknown, list the valid names and stop.
- Phase 5 is `APPROVED`.
- Every service earlier in the PROGRESS build order is `APPROVED`. If not, tell the user
  which ones, and ask whether to continue anyway. Record the answer.

## Steps

1. **Story list.** Get the stories of this service's epic from Jira (or
   `backlog-draft.md` if Jira is not connected). Order them by their "blocks" links.
   Skip stories already Done. Show the list and continue.
2. **For each story, in order** (one at a time; do not run stories of the same service in parallel):
   1. **Spec check:** compare the Jira description with `modern/specs/<KEY>.md` (sha256
      of the normalised text). If they differ, **stop this story** and show the user the
      differences (MODERNIZATION.md section 8). Continue with the next independent story
      only if the user says so.
   2. Move the Jira story to its in-progress status.
   3. Run the TDD loop exactly as the tdd-workflow skill describes:
      - test-writer (red, then the test hashes in `modern/docs/reports/stories/<KEY>.tests.sha256`; no commit)
      - java-developer (green, ≤ 3 attempts)
      - the test-protection check (you run it)
      - java-developer refactors, then runs `check`
      - code-reviewer gives APPROVE or MUST-FIX. On MUST-FIX, go back to java-developer;
        this counts as an attempt.
   4. Refresh `modern/docs/traceability/rule-trace.csv` (`python modern/tools/trace/build_rule_trace.py`).
   5. Update `modern/specs/<KEY>.md`: set `builtCommit: pending-user-commit` and `testsSha256`.
   6. Write `modern/docs/reports/stories/<KEY>.md`. Comment the same report on the Jira
      story and move it to its in-review status. **Never to Done.**
   7. If the story is BLOCKED after 3 attempts, record it and go on with the next story
      that does not depend on it.
3. **Service verification (you run these):**
   - `./gradlew :services:$ARGUMENTS:check`: full output summary, coverage and mutation
     score.
   - `make up`, then call each of the service's endpoints through the gateway with the
     right role token (a small script in `modern/tools/checks/smoke-$ARGUMENTS.sh`).
   - Events: start the service with its neighbours and confirm that each published event
     is consumed (integration test, or a check of the consumer's state).
   - Legacy-format adapter tests for this service pass (if it has any).
   - `rule-trace.csv` shows each story's rules, tests (passing) and code.
4. Write `modern/docs/reports/service-$ARGUMENTS.md`:
   - the stories (done / blocked) with links
   - test counts per level, coverage, mutation score
   - the decisions applied (D-0xx)
   - the open items
5. No commit: the user commits only when they say "commit" (rule of 2026-11-18).

## Gate (human in the loop)
Show the service report and ask for `approve service $ARGUMENTS`, or the changes needed.
On approval:
- move this service's reviewed stories to Done in Jira
- set the PROGRESS service row to `APPROVED`
- set the overall Phase 6 row to `APPROVED` when all services are approved

## Finish
Give the phase report. Next: `/phase-6-build-service <next service>`. When all services
are approved: `/phase-7-build-ui portal`. **Stop.**
