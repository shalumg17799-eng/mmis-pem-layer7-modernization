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

```
Phase / item:      MAM-39 CI pipeline: switched on, first run (2026-11-20)
Outcome:           IN PROGRESS (the first run is red; two causes found and fixed locally, to be proven by the next push)
What was done:     User decision 19:32: the workflow was moved from modern/.github/workflows/ci.yml (inert, AC-6) to the
                   repository root .github/workflows/ci.yml in commit f72d4e5 and pushed (branch modernization).
Checks run:        GitHub Actions run 44750730590 (pem-ci, commit f72d4e5, 2026-11-20 19:39-19:41 IST): FAILURE.
                     Contract lint                 success (1m19s: Redocly on the OpenAPI files, AsyncAPI validate, check_phase3.py)
                     Gradle build and full check   failure at "Build and check" after 2m01s
                     Web UI tests                  failure at "Install and test" after 18s
                     Compose smoke test            skipped (it needs the Gradle job)
                   The job logs cannot be read without a GitHub sign-in (gh is not installed; the public API gives only the
                   steps and "Process completed with exit code 1"). Causes found by local analysis:
                   1. Gradle job: spotlessCheck. 622 Java files are CRLF in git (written on Windows, no .gitattributes);
                      Spotless expects the platform's line endings, so on Linux every one of them is a format violation.
                      Fix: lineEndings = LineEnding.PRESERVE in buildSrc pem.java-conventions (each file keeps its own
                      endings). Verified locally: buildSrc compiles; spotlessCheck of fixed-width, service-kit, risk,
                      notification, tmsis, provider and versionCheck BUILD SUCCESSFUL.
                   2. Web UI job: the workflow pinned Node 20.19.2, but 12 packages of the lock file need Node 22
                      (vitest ^22.12.0, jsdom ^22.22.2, undici >=22.19.0, @axe-core/playwright >=22.12.0, ...).
                      Fix: node-version 22.22.2 for the web UI job and the compose smoke job (contract lint stays on
                      20.19.2, it passed).
                   Also checked: no relative import of the web UI resolves only without case (583 imports); the lock file
                   holds the Linux native packages (rolldown, lightningcss); no source file is hidden by .gitignore any more.
Not verified:      That these are the ONLY causes: both jobs stopped at their first failure. The fixes are not pushed yet
                   (commits only on the user's word; the next one is at the end of the plan). Known to be red in the next
                   run until the lanes finish: the unit tests of the services that are still open.
Next step:         push with the final commit and read the second run
```

## Local reproduction on Linux (2026-11-20 22:23 to 22:54, lead; nothing pushed)

The two failed jobs of the first run were repeated in throwaway Linux containers on the development machine (official
images, memory-capped, removed after each run; the repository was mounted read-only and copied inside the container).

| Job | Image and tree | Commands | Result |
|---|---|---|---|
| Web UI tests | node:20.19.2-bookworm-slim, the committed tree (f72d4e5) | `npm ci --no-audit --no-fund`, `npm run -s lint` | both pass (284 packages; engine warnings only) |
| Web UI tests | the same | `npx vitest run tests/console/provider-detail.test.tsx` | fails at start-up after 0.5 s: 1 error while loading the test environment (undici), no test runs. This is the failure of the first CI run: the install and the lint pass and `npm test` stops at once |
| Web UI tests | node:22.22.2-bookworm-slim, the working tree of 22:31 | `npm ci --no-audit --no-fund && npm run -s lint && npm test` (the job's own commands) | **passes**: 292 packages, lint clean, 43 test files, 1092 of 1092 tests, no skipped test, 463 s, exit 0 |
| Gradle build and full check (formatting only) | eclipse-temurin:21-jdk (21.0.13; the workflow pins 21.0.7), the working tree of 22:42 | `./gradlew --no-daemon spotlessCheck versionCheck` | `spotlessCheck` **passes** for infra:local-runtime, libs:fixed-width, libs:service-kit, libs:test-support, api-gateway and directory-service: sources with CRLF line endings are accepted on Linux with `lineEndings = PRESERVE`. The run then stopped at enrollment-service on real format violations (line wrapping in `IntakeLoad.java` and others: unfinished work of the enrollment lane, not line endings) |

Not reached in the Gradle run (it stops at the first failure; to be repeated with `--continue` when the service lanes are
done): notification, provider, revalidation, risk, sanctions, screening, tmsis and `versionCheck`. Not reproduced at all:
the tests of the Gradle job (they start database and broker containers) and the compose smoke job.

Also changed locally for the next run: the Gradle job's time limit is 150 minutes (was 90: the build is sequential and
runs PIT for nine services); the pinned Node versions were checked against the Node release index (20.19.2 and 22.22.2
exist).

## Second run (2026-11-20 23:57, commit 14e0707, run 44792814928): red, but far further

| Job | Result | Time on the runner |
|---|---|---|
| Contract lint | success | 1m18s |
| Web UI tests | failure in "Install and test" | 1m08s |
| Gradle build and full check | failure in "Build and check" | 45m02s (the first run failed after 2m01s) |
| Compose smoke test | skipped (it needs the Gradle job) | |

What this shows: the line-ending fix works on GitHub (the Gradle job got past the format check of the first modules and
ran builds and tests for 45 minutes before its first failure; without `--continue` it stops there). The commit was a
snapshot taken while three lanes were still working, so a red run was expected: screening-service has failing tests,
enrollment-service and revalidation-service have unfinished sources with format violations. The web UI job ended after
68 seconds, too early for a whole test run: at commit time an accessibility spec was being edited and a first attempt of
a UI fix (taken out again at 23:58, question Q-137) broke one recorded test, so either the lint or that test failed. The
job logs cannot be read without a sign-in, so the exact line is not known.

Changed for the next run (local at 00:45, in the next push): the Gradle job runs with `--continue` and, on a failure,
writes the failed tasks, the first lines of each "What went wrong" and the failed tests as error annotations; the web UI
job writes the lint output or the failed tests the same way. Annotations can be read through the public API without a
sign-in (`<check_run_url>/annotations`), so every later run can be diagnosed from here.

## Third run (2026-11-21 00:48, commit c606832, run 44800779162): a FALSE green in the Gradle job, found and corrected

| Job | Result as GitHub shows it | What it really means |
|---|---|---|
| Contract lint | success | passed |
| Web UI tests | failure | 4 of 1132 tests not passed: the new unit test of ScrollableTable that was being written when the commit was taken (read from the new annotations) |
| Gradle build and full check | success after 31 s | **not a pass.** The step was `gradlew ... 2>&1 | tee file`; GitHub's default shell is `bash -e` without `pipefail`, so the step reported the exit code of `tee`. A real build takes more than 45 minutes; what the build did in those seconds is not known |
| Compose smoke test | success after 3m32s | **a real pass of this job** (it ran only because the Gradle job looked green, but it does its own work): `make up` built the jar and the web UI on the runner and started the stack with every container healthy (3m05s), the gateway health check and the provider lookup through Keycloak and the gateway passed, the stack was stopped. First proof that the stack builds and starts on a clean Linux machine (AC-4) |

Cause: the lead's own change of c606832 (the `tee` for the failure annotations). Correction (commit after c606832):
`defaults.run.shell: bash`, which GitHub runs as `bash --noprofile --norc -eo pipefail`. Checked locally: `false | tee`
ends with 0 under `bash -e` and with 1 under `bash -eo pipefail`. Run 3 must not be counted as a green Gradle run.
The annotations themselves work: the web UI failure above was read through the public API without a sign-in.
