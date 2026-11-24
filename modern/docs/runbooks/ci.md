# CI runbook (MAM-39)

The CI workflow is `.github/workflows/ci.yml` at the repository root. It was kept inert under `modern/.github/workflows/`
until the user decided to switch it on (2026-11-20 19:32); it now runs on every push and pull request. Its run results
are recorded in `modern/docs/reports/stories/MAM-39.md`. Every action and tool in it has an exact version; change a
version only on purpose.

Anyone without the workflow runs the same four steps by hand from `modern/` (Git Bash on Windows, any shell elsewhere):

| Step | What it proves | Commands |
|---|---|---|
| 1. Build and full check | Gradle build; unit, acceptance, integration, contract and ArchUnit tests; JaCoCo (85% per service) and PIT; red on any failure | `./gradlew --no-daemon --continue build checkAll` (`--continue`: every module is checked even when one fails; the job is still red) |
| 2. Contract lint | All OpenAPI files lint; the AsyncAPI file validates; roles, events and rule/field coverage are consistent | `npx --yes @redocly/cli@1.34.3 lint contracts/openapi/*.yaml`<br>`npx --yes @asyncapi/cli@2.14.0 validate contracts/asyncapi/events.yaml`<br>`python tools/checks/check_phase3.py` (from the repository root: `python modern/tools/checks/check_phase3.py`) |
| 3. Web UI tests | Lint and unit tests; a failing or skipped test fails | `cd web-ui && npm ci --no-audit --no-fund && npm run -s lint && npm test` |
| 4. Compose smoke test | The stack starts healthy, the gateway answers and the walking-skeleton provider lookup works | `make up` (or `./pem.sh up`; waits for every container to be healthy)<br>`curl -sf http://localhost:8080/actuator/health`<br>`./pem.sh seed`, then GET `http://localhost:8080/api/v1/providers/1005940865` with an analyst1 token (password from `infra/.env.local`, never printed)<br>`make down` |

Tool versions used by the workflow: Ubuntu runner 24.04, Temurin Java 21.0.7, Node 20.19.2 for the contract lint and
Node 22.22.2 for the web UI tests and the compose smoke test (the web UI test libraries need Node 22), Python 3.12.10,
actions/checkout 4.2.2, actions/setup-java 4.7.1, actions/setup-node 4.4.0, actions/setup-python 5.6.0,
@redocly/cli 1.34.3, @asyncapi/cli 2.14.0, PyYAML 6.0.2.

If step 4 fails, print the logs of every pem container that is not healthy:
`docker ps -a --filter label=com.docker.compose.project=pem` then `docker logs --tail 200 <name>`.

State on 2026-11-24: the workflow has run on every push since 2026-11-20. The first runs were red and were fixed one cause at a time
(MAM-39.md); runs 8 to 16 (2026-11-21) were green; runs 17 (a60dcd3), 18 (3222fbe) and 19 (ee34503) were red: run 19 failed in the
warm-up step with `HTTP 429 Too Many Requests` from repo.maven.apache.org (Maven Central limits the shared addresses of the runners),
run 18 with 16 timeouts in one revalidation-service test class that passes locally, run 17 before any task. The workflow has two
measures against the first kind of failure (below) and its failure summary now prints the first two failures in detail. The record of
every run is in `modern/docs/reports/stories/MAM-39.md`. Time limits: 150 minutes for the Gradle job (the build is sequential and runs PIT for nine
services), 15 for the contract lint, 30 for the web UI tests, 45 for the compose smoke test.

## When the Gradle job fails before any test (downloads)

Read the annotations of the run (they need no sign-in; the job log does): `GET https://api.github.com/repos/<owner>/<repo>/check-runs/<job id>/annotations`
or the run page. "What went wrong 1" shows the first lines of Gradle's own message, for example `Received status code 429 from
server: Too Many Requests` (run 19, 2026-11-24: Maven Central rate-limits the shared addresses of GitHub's runners).

The job has three steps before the gate for this: **Restore the downloaded Gradle libraries** (`actions/cache/restore` v5.0.5: the
folders `~/.gradle/caches/modules-2` and `~/.gradle/wrapper/dists` of an earlier run; the key holds the hash of the build files and
the run id, the newest cache of the same build files is taken), **Resolve build logic and plugins (up to five attempts)** (runs
`./gradlew --no-daemon -q help`, which downloads the build logic's libraries; waits 60, 120, 180 and 240 seconds between attempts;
its output goes to `$RUNNER_TEMP/warmup.log`, which the failure summary reads when the gate step never started), and, at the end,
**Save the downloaded Gradle libraries** (only after a successful warm-up, also when a test failed). The Gradle build cache and the
test results are NOT cached, so the gate (**Build and check**, one run, no repeat, no `if:`) always starts from scratch. A failing
test, a compile error of the modules' code and a coverage violation are never repeated. The first run after a change of the build
files has no cache and downloads everything; that run can still meet a 429.

The **Failure summary** step prints, besides the failed tasks and the failed tests, the first two failures in detail (the message up
to 700 characters, 14 lines of the stack trace, up to 25 warning and error lines of the output of the test class). Annotations are
public on a public repository and nothing filters payloads: the tests must keep using synthetic data only.

## Deliberate faults turn the gate commands red (demonstration, 2026-11-21)

What it is. `tools/checks/demo-ci-gates.sh` (MAM-39; run it from `modern/` in Git Bash) PROVES, on one machine and on copies of
the inputs, that each gate command of the workflow fails on its fault and names it: the Redocly lint and the AsyncAPI validation
of the contracts, the two commands of the `test` script of `web-ui/package.json` (Vitest, then `scripts/no-skipped.mjs`) and the
Gradle command with its JaCoCo rule each exit non-zero on one deliberate fault, print it, and exit 0 on the same input without
it. It does not show a red run on GitHub. Its static workflow step CHECKS a stated list of switches in
`.github/workflows/ci.yml` and is NOT a proof that no gate can be switched off: a switch that is not on the list is not seen. The
list: `on:` has `push` and `pull_request`; the jobs `build-and-check`, `contract-lint` and `web-ui` and the four steps that carry
the Gradle line, the two `npx` lines and `npm test` have no `if:` and no `continue-on-error` other than a literal `false`; those
steps run with `shell: bash` (GitHub runs it with `-eo pipefail`; the default is `bash -e` without `pipefail`, which hides the
exit code of `gradlew ... | tee`, as run 3 showed) in the working directory the script assumes (`modern`, `modern/web-ui`); none
of the three jobs has a `needs` whose needed job, followed through its own `needs`, has an `if:` or a `continue-on-error` other
than a literal `false` (GitHub skips a job whose needed job was skipped, and a skipped job is not red); no line of those four
steps contains `||` or a `set +` (`set +e`, `set +o pipefail`, `set +eo pipefail`, `set +ex`, `set +o errexit`, ...); each command
is found in the job that owns it. Not seen: a condition is not evaluated (`if: true` is reported like `if: false`); a switch
outside the workflow file (a repository ruleset, a required-checks or branch-protection setting, a disabled workflow); the
branch and path filters of the triggers (printed as a note, not judged); an `exit 0` line before the command in the same step; a
failure swallowed inside a script that a step calls; a shell option switched off by other means than a `set +` line of the step
(an `env:` entry such as `BASH_ENV`, a `trap`, a `set` continued with a backslash); the other jobs and steps (annotations,
compose smoke test, the lint step, `check_phase3.py`); the `runs-on` label, `timeout-minutes`, `strategy`, `concurrency` and
`permissions` of the jobs.

How it works. Before its blocks the script reads the workflow (Python and PyYAML; it prints which interpreter and which PyYAML
version it used; the static step always runs, also with `DEMO_ONLY`) and takes the Gradle line, the two pinned `npx` lines
(`@redocly/cli`, `@asyncapi/cli`) and `npm test` from the job that owns each of them (`build-and-check`, `contract-lint`,
`web-ui`); the two commands of the `test` script come from `web-ui/package.json`, which must be exactly `A && B` (no `||`, no
`;`), because the script computes the exit code of the chain itself. Each block runs first on a clean input (the negative
control: it must pass) and then on an input with ONE deliberate fault (it must fail, and the output must name the fault). It
prints PASS or FAIL per step and exits 1 when any step failed. The differences to the workflow's own commands: Gradle runs with
`--no-build-cache`; the Vitest command gets another report path, `--config` and a file filter. Copies live in a scratch folder,
the temporary Vitest files and the Vitest caches of those runs (Vite's `cacheDir` and Vitest's `fsModuleCachePath`) in the
git-ignored `web-ui/test-results/demo-ci-gates-<pid>/`; both are removed on exit, also on error. The tools it starts keep files of
their own outside these folders, which the script neither removes nor compares: a daemon log of each Gradle call in the Gradle
user home (`~/.gradle/daemon/<version>/`), the two CLIs in npm's cache, and a bundle of the temporary configuration that Vite
writes into `web-ui/node_modules/.vite-temp/` and deletes after loading it. The last steps compare before and after, for both
Vitest caches of the project, the number of entries for `demo-ci-gates-` paths (each count must not grow): in the results cache
(`web-ui/node_modules/.vite/vitest/*/results.json`) the entries of its files, and in the module cache
(`web-ui/node_modules/.vitest-cache`) the entry files whose content names `demo-ci-gates-` and the manifest keys with
`demo-ci-gates-` in the path; they also compare the sha256 of `contracts/`, `libs/fixed-width`, the build logic, the web UI
scripts and the workflow file, and the real `test-results/vitest.json`; other files are not compared.

| Block | Criterion | Clean input (must pass) | Deliberate fault (must fail, and be named) |
|---|---|---|---|
| `wf` | the static step itself | The unchanged workflow: no problem is reported (this is also the first PASS line of each run) | Fourteen copies of the workflow in the scratch folder, one switch of the list in each: `continue-on-error: true` on job `build-and-check` or on step `Validate AsyncAPI`; `if: false` on step `Lint OpenAPI` or on job `web-ui`; the `shell: bash` default removed; the `pull_request` trigger removed; `\|\| true` added to the Gradle line; `npm test` changed to `npm test \|\| true` (these eight change one line each); a `set +o pipefail` line before the Gradle command (its one-line `run:` becomes a `run: \|` block: one line replaced by three); a `set +eo pipefail`, a `set +ex` and a `set +o errexit` line in the web UI step (one line each); `needs: compose-smoke` on job `web-ui` while `compose-smoke` has `if: false` (two lines added); `needs: [compose-smoke]` on `web-ui` while `build-and-check`, which `compose-smoke` needs, has `continue-on-error: true` (two lines added). The check reports each of the fourteen by name; this is not shown for other forms |
| `ac2` | AC-2 contract lint | A copy of `contracts/`: `npx --yes @redocly/cli@1.34.3 lint contracts/openapi/*.yaml` validates all nine OpenAPI files, `npx --yes @asyncapi/cli@2.14.0 validate contracts/asyncapi/events.yaml` is valid | (a) one `$ref` to a schema that does not exist in one OpenAPI file (default `risk-service.yaml`): Redocly exits 1, and one error block (rule `no-unresolved-refs`, with the missing name) is at that file and line. (a2) the same fault in each of the nine files: every file has its own error block, and the summary counts nine errors (the warnings that Redocly prints in the same format, such as `no-unused-components` for the schema that lost its only reference, do not count as naming the file). (b) one `$ref` to a message that does not exist in `events.yaml`: `asyncapi validate` exits 1 and one line carries `error`, `invalid-ref` and the missing name |
| `ac3` | AC-3 web UI tests | A tiny temporary Vitest file, run through a temporary configuration that imports the project's Vitest configuration and changes only `test.include` (narrowed to the temporary folder), `cacheDir` (Vitest's results cache goes into the temporary folder) and `fsModuleCachePath` (so does its module cache), then `node scripts/no-skipped.mjs <report>` on its JSON report, exactly as the `test` script chains them (`vitest run ... && node scripts/no-skipped.mjs ...`) | (a) the file with one `it.skip`: Vitest alone exits 0 (it does not fail a skipped test), `no-skipped.mjs` exits 1 and prints `SKIPPED: <test name>`; the same file without the skip exits 0. (b) `describe.skip`, `it.skipIf` and `it.todo` are all named. (c) a failing test: Vitest exits 1 and prints the assertion (`AssertionError: expected 1 to be 2`), and the `&&` stops the chain |
| `ac1` | AC-1 coverage gate | A copy of `HEAD` made with `git archive` (build logic, `libs/fixed-width`, `libs/test-support`, the one document the library's tests read): the workflow's Gradle command (`./gradlew --no-daemon --continue build checkAll 2>&1 \| tee ...`, run under `bash -eo pipefail` as GitHub does) passes, and `jacocoTestCoverageVerification` of `libs:fixed-width` ran | In the copy, all test classes but `RoundTripTest` are deleted: the same command exits 1, `jacocoTestCoverageVerification` is the only failed task, and the rule's own message appears: `lines covered ratio is 0.33, but expected minimum is 0.85` |

Run it (with `bash`, so that it works whatever the file mode of a checkout is):

```bash
cd modern
bash tools/checks/demo-ci-gates.sh                   # the static workflow step, all blocks, then the clean-up step
DEMO_ONLY=ac2 bash tools/checks/demo-ci-gates.sh     # one block: ac1, ac2, ac3 or wf (the static workflow step always runs)
PEM_SCRATCH=<folder> PEM_GRADLE_LOCK=<folder>/gradle.lock bash tools/checks/demo-ci-gates.sh   # on a shared machine
```

- About 6 to 15 minutes. The recorded run (2026-11-24 13:52:38 to 13:57:55, 5 minutes 17 seconds, no waiting for free memory and none for
  the Gradle lock; the two Gradle runs took 1 min 40 s and 33 s; the rest is the `npx` calls of block `ac2` and the Vitest runs of
  block `ac3`). Earlier runs on a busier machine took 7 min 28 s (2026-11-21, 2 min 4 s and 40 s of Gradle) and 14 min 45 s (of which
  300 seconds waiting for free memory and 100 seconds for a Gradle lock that another lane held). The first `npx` call of each pinned CLI
  downloads it: add a few minutes on a machine that has not run it before (not timed by the script). It needs Python with PyYAML for
  the static workflow step (`PEM_PYTHON`, or `modern/kag/.venv`, `python3`, `python`, whichever can `import yaml` first),
  `web-ui/node_modules` (`npm ci`) for block `ac3`, Node, Java 21 and network access to npm and Maven Central.
- The recorded run: exit 0, 30 steps PASS, 0 FAIL, 106 log lines, HEAD b82359a (the build copy of block `ac1` is `git archive` of
  HEAD; the log says the working tree has the same build logic and modules), workflow `.github/workflows/ci.yml` of HEAD (sha256
  `5d41e1c0cc35e90d...`), script sha256 `38d13696bb59ddb986a8ff40b9675bb9b3d97b85cc1f8f4f850217b00060f6d4`, 941 lines, mode 100755.
  Summary line `== 2026-11-24 13:57:55: 2 Gradle run(s); waited 0 s for free memory and 0 s for the Gradle lock; 0 step(s) failed ==`.
  Entries for `demo-ci-gates-` paths in the project's Vitest caches (results cache, module cache entry files, module cache manifest
  keys): 0 0 0 before and 0 0 0 after. The compared files: 55 (contracts, `libs/fixed-width`, the build logic, the web-ui scripts and
  the workflow); the generated folder `buildSrc/.kotlin` is not compared (a Gradle build in the real tree rewrites it) and a cache
  count that cannot be read is a FAIL. Details: `modern/docs/reports/stories/MAM-39.md`, last section.
- Memory and Gradle: before each Gradle, Vitest and `npx` call it reads the free memory and waits 60 seconds while it is under
  1024 MB (`PEM_MIN_FREE_MB`). With `PEM_GRADLE_LOCK` set it creates that lock folder (with `owner.txt`) around each Gradle
  call, retries every 20 seconds (`PEM_LOCK_RETRY`) when it exists, and never removes a lock it does not own. Timeouts:
  `DEMO_TIMEOUT` (Gradle and Vitest, default 1500 s) and `DEMO_NPX_TIMEOUT` (default 900 s).
- Vitest on Windows is started from PowerShell with a lowercase drive path (from Git Bash it can fail with "Vitest failed to
  find the runner"); on other systems it is started directly. The temporary tests cannot go into `web-ui/tests` (a tracked
  folder, and the project's `include` is `tests/**`), so the script writes a temporary config that imports the project's
  `vite.config.ts` and replaces only `include`, `cacheDir` and `fsModuleCachePath`: plugins, jsdom environment and `tests/setup.ts`
  stay the project's.
- The two Vitest caches: Vitest 5.1.1 keeps two caches in the project's `node_modules` (both git-ignored). The results cache is
  under Vite's `cacheDir` (by default `web-ui/node_modules/.vite/vitest/<hash>/results.json`). The transformed-module cache
  (`fsModuleCache`, on by default) is at `fsModuleCachePath`, by default `web-ui/node_modules/.vitest-cache` (one file per
  transformed module and a `_manifest-web-ui-<hash>.json`); setting `cacheDir` does not move it. An earlier version of this script
  left four entries (`test-results/demo-ci-gates-<pid>/...`) in the results cache per run; the first correction pointed `cacheDir`
  into the temporary folder, and that moved only the results cache: the module cache in `node_modules/.vitest-cache` kept getting
  four entries per run (one entry file and one manifest key per temporary test file; 36 entries from nine runs, including the
  recorded run of that version, pid 152648, and one entry of a prototype: 37 entry files and 37 manifest keys in all). They did not
  change a test result, but the cache grew with each run. The temporary configuration now sets `fsModuleCachePath` into the
  temporary folder too, and the clean-up step counts the entries of both caches before and after the run and fails when a count
  grows. The old entries were removed once, on 2026-11-21, exactly, with one-time scripts in the session scratch folder (not in the
  repository): the 25 entries of the results cache, and the 37 entry files and 37 manifest keys of the module cache (that script
  ran as a dry run first, wrote only because the manifest serializes back to the same bytes and the 37 keys and 37 files matched one
  to one with no other key using such a file, and kept a copy of what it removed). The folder `.vitest-cache` itself was not
  deleted. The numbers are in the report.
- Other options: `PEM_PYTHON` (the Python for the static step), `DEMO_OPENAPI_FILE` (the OpenAPI file of fault (a)) and
  `DEMO_KEEP_TESTS` (the one test class kept in block `ac1`).
- If a run is killed, a folder `web-ui/test-results/demo-ci-gates-<pid>/` can stay behind (it is git-ignored); the next run's
  clean-up step reports it, and it can be deleted by hand. Other entries of that folder (a Playwright run of another lane writes
  there too) are printed as a note and do not fail the run; a change to the real `test-results/vitest.json` does.

What it shows and what it does not: with the workflow's own commands (the same pinned tool versions, the `test` script of
`package.json`, the Gradle command and the JaCoCo rule of `buildSrc`), a deliberate fault in each of the three places makes the
command exit non-zero and the output names the fault, while the clean input exits 0. It is a local run on copies, not a red run
on GitHub; the one difference to the workflow's Gradle command is `--no-build-cache` (a clean runner has no build cache). The
static step is a check of the stated list above and not a proof: at the time of the run it found none of the switches of its list
set in `.github/workflows/ci.yml`, and block `wf` shows that it reports each of fourteen forms of them. A switch that is not on
the list (see "Not seen" above) is not seen; a ruleset or required-checks setting on GitHub, a condition that is evaluated at run
time and a failure swallowed inside a script that a step calls are outside it. The recorded run, its log lines and its limits are
in `modern/docs/reports/stories/MAM-39.md`, section "Deliberate faults turn the gate commands red (2026-11-21)".
