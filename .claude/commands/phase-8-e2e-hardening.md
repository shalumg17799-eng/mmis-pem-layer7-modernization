---
description: Phase 8. Prove the whole system end to end. Covers the user journeys, security, accessibility, performance smoke, the traceability report and runbooks.
---

# Phase 8: End to end and hardening

**Goal:** the whole system runs from a clean start, the user journeys pass, the
non-functional checks meet their targets, and every legacy rule traces to a passing test.

## Preconditions
Phase 6 and Phase 7 are `APPROVED`.

## Steps

1. **Clean start.** Ask the user before running `make reset` (it deletes local data).
   Then `make up && make seed`. Show `docker compose ps`.
2. **Journeys.** test-writer writes (or completes) the Playwright tests for every
   journey in `modern/docs/ux/journeys.md`, in `modern/web-ui/e2e/journeys/`. Run them
   3 times in a row. Every failure becomes a Defect story (backlog-planner, after the
   user agrees) and is fixed through the Phase 6 loop. **Never by editing the test.**
3. **Security:**
   - OWASP dependency check across the Gradle build: no high or critical findings, or
     each one has a written justification
   - the role tests for every endpoint exist and pass
   - an optional OWASP ZAP baseline scan of the gateway (pinned `zaproxy/zap-stable`
     image), if the user agrees
4. **Accessibility:** run axe on every page; zero violations. Write up the keyboard-only
   pass per journey.
5. **Performance smoke:**
   - data-engineer generates a larger synthetic set (default 10,000 providers)
   - run the revalidation and T-MSIS jobs, and k6 (pinned Docker image) against search
     and provider detail
   - compare with `modern/docs/architecture/nfr.md`; anything still marked DEFAULT is
     reported as such
6. **Traceability.** The orchestrator produces `modern/docs/reports/traceability.md` from
   `modern/docs/traceability/rule-trace.csv` (never from Neo4j): for
   every non-dropped rule, its story, AC, test(s) and code class(es), plus the test
   result. **Target: zero rules without a passing test.** List any exceptions.
7. **Runbooks:** `local-run.md`, `operations.md` (jobs, schedules, manual triggers,
   logs, health), `troubleshooting.md`.
8. **Whole-system review.** code-reviewer reviews cross-cutting concerns: security
   config, error handling consistency, logging and correlation IDs, outbox and
   idempotency, configuration and secrets.
9. No commit: the user commits only when they say "commit" (rule of 2026-11-18).

## Exit checks
- [ ] All journeys pass 3 runs in a row.
- [ ] No high or critical security findings without justification. Zero axe violations.
- [ ] Performance results recorded against the targets.
- [ ] The traceability report has zero untested rules (or exceptions the user accepts).

## Finish
PROGRESS: phase 8 → `AWAITING APPROVAL`. Give the phase report. Next: Phase 9 when the
legacy outputs are available; until then, the system is complete for demo and review.
**Stop.**
