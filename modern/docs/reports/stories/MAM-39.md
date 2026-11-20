```
Phase / item:      MAM-39 CI pipeline: build, check, contract lint, web-ui tests, compose smoke test
Outcome:           BUILT, not run (build-phase: tests in Part D), batch A6
What was done:     - Spec check: modern/specs/MAM-39.md (6 ACs); Jira not re-read in this batch
                   - modern/.github/workflows/ci.yml (inert there, AC-6): jobs build-and-check (./gradlew build checkAll:
                     tests, ArchUnit, JaCoCo 85%, PIT), contract-lint (redocly lint of every OpenAPI file, asyncapi
                     validate, tools/checks/check_phase3.py), web-ui (npm ci, lint, npm test, which fails on skipped tests
                     through scripts/no-skipped.mjs), compose-smoke (make up waits for healthy containers, gateway health,
                     seed, analyst1 token, GET /api/v1/providers/1005940865, logs of every unhealthy container on failure,
                     make down always)
                   - Every action and tool pinned to an exact version (no latest): ubuntu-24.04, checkout 4.2.2,
                     setup-java 4.7.1 (Temurin 21.0.7), setup-node 4.4.0 (Node 20.19.2), setup-python 5.6.0 (3.12.10),
                     @redocly/cli 1.34.3, @asyncapi/cli 2.14.0, PyYAML 6.0.2
                   - modern/docs/runbooks/ci.md: the same four steps with exact commands, the versions, and that the file
                     must be copied to the repository root .github/workflows to run
                   - AC status: AC-1..AC-6 written; none run (nothing pushed, no GitHub runner here)
Files created/changed: modern/.github/workflows/ci.yml (new), modern/docs/runbooks/ci.md (new)
Checks run:        none (the workflow cannot run locally)
Not verified:      that the pinned versions exist and work together; AC-1 coverage gate, AC-2 deliberate error,
                   AC-3 skipped-test failure and AC-4 smoke on a clean runner
Questions for you: 1. Copy the workflow to the repository root and push? Options: A) not yet (default, AC-6); B) yes
```
