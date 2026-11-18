---
description: Phase 5. Build the platform (Gradle build, service skeletons, docker compose, Keycloak, gateway, CI), the fixed-width library and synthetic data, and prove it end to end with a walking skeleton.
---

# Phase 5: Platform and walking skeleton

**Goal:** `make up` starts every container and service healthy. One thin feature works
end to end: console UI → gateway → provider-service → Postgres, with login. The test
data and legacy-format library exist.

## Preconditions
Phase 4 is `APPROVED`.

## Steps

1. **Ask the user now:**
   - "Postgres: use a local Docker container (recommended), or your own server?" If
     theirs: host, port and admin username in chat; the password goes in `.env` as
     `PG_ADMIN_PASSWORD`. Restart Claude Code if `.env` changed.
   - "Do you use GitHub for this repo (for CI workflows)? yes/no"
   - "Passwords for the 4 Keycloak test users": put them in `.env` as
     `KC_PROVIDER_PASSWORD`, `KC_ANALYST_PASSWORD`, `KC_SUPERVISOR_PASSWORD` and
     `KC_ADMIN_PASSWORD`, plus `KC_BOOTSTRAP_ADMIN_PASSWORD` for the Keycloak admin.
2. **Platform.** Delegate to devops-engineer: all of its Phase 5 deliverables.
   - Gradle wrapper: if Gradle is not installed locally, generate the wrapper with a
     pinned Docker image, e.g.
     `docker run --rm -v "$PWD/modern":/w -w /w gradle:<pinned>-jdk21 gradle wrapper`.
   - Pin versions in `libs.versions.toml`.
   - Delegate the ADR "Versions" update to architect.
3. **Data.** Delegate to data-engineer: all of its Phase 5 deliverables. This needs the
   build from step 2.
4. **Walking skeleton.** Use a story from Phase 4 tagged as the walking skeleton. If
   there is none, ask backlog-planner to add Task "Walking skeleton: provider lookup by
   NPI" under Platform and CI. It has these ACs:
   - AC-1: `GET /api/v1/providers/{npi}` through the gateway, with an ANALYST token,
     returns a seeded provider.
   - AC-2: without a token it returns 401; with a PROVIDER token for another NPI, 403.
   - AC-3: the console page "Provider lookup" shows the provider's name, status and
     risk tier.

   Run it with the full TDD loop (tdd-workflow skill): test-writer → java-developer /
   ui-developer → test-protection check → code-reviewer.
5. **Your checks (orchestrator):**
   - `cd modern && make build && make test`
   - `make up`: every container is healthy (show `docker compose ps`)
   - `make seed`
   - the walking-skeleton Playwright test passes
   - The JaCoCo and PIT gates are active. Prove it: temporarily lower a threshold check,
     or show the build configuration lines.
   - `git grep` finds no committed secrets.
6. Commit: `feat(phase-5): platform, data, walking skeleton`.

## Exit checks
- [ ] `make up` is healthy, and `make seed` loads the data.
- [ ] The walking skeleton passes end to end.
- [ ] The fixed-width round-trip tests pass, and test-data coverage is ≥ 95% (or the gaps are listed).
- [ ] The runbook `docs/runbooks/local-run.md` works from a clean clone (state whether you actually tested it).

## Finish
PROGRESS: phase 5 → `AWAITING APPROVAL`; add `Postgres | 5 | <local or external>`. Give
the phase report. Next: `/phase-6-build-service <first service in the build order>`.
**Stop.**
