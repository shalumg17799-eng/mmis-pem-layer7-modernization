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

## Fourth run (2026-11-21 01:05, commit 00cfc3b, run 44803538965): the first run with real exit codes and readable causes

| Job | Result | Time on the runner |
|---|---|---|
| Contract lint | success | 1m11s |
| Web UI tests | **success** (first green on GitHub: install, lint and the whole Vitest suite on Node 22.22.2) | 1m08s |
| Gradle build and full check | failure, with `--continue` and annotations | 61m24s |
| Compose smoke test | skipped (it needs the Gradle job; it passed on its own in run 3) | |

Read from the annotations through the public API (no sign-in). **Failed Gradle tasks: 6, all in the three services whose
lanes are still open:** `:services:enrollment-service:test` and `:spotlessJavaCheck`, `:services:revalidation-service:test`
and `:spotlessJavaCheck`, `:services:screening-service:test` and `:spotlessJavaCheck`; 169 failed tests in them (for example
enrollment `IntakeAtomicityTest` and `IntakeUploadLimitTest`, tests written for stories that are not built to the end yet),
and format violations in unfinished sources (`IntakeLoad.java`, `ExecuteSweep.java`).

**Every other module passed its whole check on GitHub, on Linux:** libs fixed-width, service-kit and test-support,
api-gateway, directory-service, notification-service, provider-service, risk-service, sanctions-service, tmsis-service and
infra:local-runtime, with tests, JaCoCo, PIT, Spotless (CRLF sources accepted), `versionCheck` and the new
`testSourceCheck`. So what keeps the pipeline red is exactly the open work of the three service lanes, nothing of the
platform.

## Fifth run (2026-11-21 02:00, commit c75af16, run 44811923545)

Contract lint success and Web UI tests success (second green in a row). The Gradle job ended red after 23m49s with the
same 6 failed tasks as run 4 (test and spotlessJavaCheck of enrollment-service, revalidation-service and
screening-service) and 109 failed tests (run 4: 169; the lanes' work in progress moved on between the two snapshots).
Every other module passed again. Run 4 needed 61 minutes because of tests of an unfinished lane that waited for
database connections; a run without such waits takes about 24 minutes on the runner.

## Sixth run (2026-11-21 03:23, commit 6e24725, run 44824148226): one module left

| Job | Result | Duration |
|---|---|---|
| Contract lint | success | 59s |
| Web UI tests | success (third green run in a row) | 1m08s |
| Gradle build and full check | failure, with `--continue` and annotations | 19m29s |
| Compose smoke test | skipped (it needs the Gradle job; it passed on its own in run 3) | |

Read from the annotations through the public API. **Failed Gradle tasks: 2, both in revalidation-service**, the one
service whose lane was still in its test step when the commit was taken; failed tests in the JUnit XML files: 0.

- `:services:revalidation-service:compileTestJava`: two test files of the lane's test-writer were caught by the commit in
  the middle of an edit (a broken literal in `RequestFailureTest.java:173` and in `RevalqGoldenFileTest.java:84`). The
  test-writer corrected both at 03:24 and they compile in the working tree; the next commit carries the corrected files.
  Cause on the lead's side: the commit waits for the build lock, but a test-writer edits files without holding it. For
  the run that is meant to be green the commit is taken only when no lane is editing.
- `:services:revalidation-service:spotlessJavaCheck`: `ExecuteSweep.java` and three more production files (known since
  run 4; the lane's developer step formats them).

**New in this run: enrollment-service and screening-service passed their whole check on GitHub, on Linux** (tests, JaCoCo,
PIT, Spotless, `testSourceCheck`; 467 and 705 tests in the local runs), and every other module passed again. What keeps
the Gradle job red is now this one module, and with it the compose smoke test from starting.

## Seventh run (2026-11-21 04:43, commit 715a8ad, run 44835139578): revalidation-service green on Linux; one compile error, by design

| Job | Result | Duration |
|---|---|---|
| Contract lint | success | 1m08s |
| Web UI tests | success (fourth green run in a row) | 1m01s |
| Gradle build and full check | failure, with `--continue` and annotations | 22m22s |
| Compose smoke test | skipped (it needs the Gradle job) | |

**Failed Gradle tasks: 1**, `:services:notification-service:compileTestJava`; failed tests in the JUnit XML files: 0. The
commit held the new tests for the open review item of notification-service (they call a constructor of `SendNotice` with a
sixth argument) and not yet the fix: tests first, so that module could not compile at this commit. The fix followed in the
next commit (20e0270, 05:05).

**New in this run: revalidation-service passed its whole check on GitHub, on Linux** (529 tests in the local run), with the
two test files of run 6 corrected and the format violations gone. With that, every module has now passed on Linux at least
once; what was red in this run was only the half of a tests-first step.

Lesson applied from run 6: since 05:05 a commit leaves out the folders that a test-writer is editing at that moment, so the
head of the branch always compiles.

## Eighth run (2026-11-21 05:05, commit 20e0270, run 44837841769): GREEN, all four jobs

| Job | Result | Duration |
|---|---|---|
| Contract lint | success | 1m07s |
| Web UI tests | success | 1m04s |
| Gradle build and full check | **success** | 24m11s |
| Compose smoke test | **success** | 3m37s |

Read from the public API at 05:33: the run's conclusion is `success` (workflow pem-ci, run number 8, branch modernization,
event push). This is the first run in which the Gradle job passed with its real exit code: `./gradlew --no-daemon --continue
build checkAll` on Linux for every module (the three libraries, the ten services and the local runtime) with tests, Spotless,
ArchUnit, JaCoCo, PIT, `versionCheck`, `testSourceCheck` and the skipped-test guard; and the compose smoke test then built the
jar and the web UI on the clean runner, started the stack, waited for every container to be healthy and made its calls.

What made the difference between run 1 and run 8, in the order found: sources with CRLF line endings were rejected by Spotless
on Linux (the build now keeps each file's own line endings); the lint of the web UI ran on a Node version that its lockfile did
not allow (Node 22 for the web UI job); the exit code of the Gradle step was hidden by a pipe (every step now runs with
pipefail); the open service lanes left failing tests and format violations, which ended as each lane closed; and two commits
caught files in the middle of a tests-first step (a commit now leaves out the folders that are being edited).

Still to show for this story (not shown by a green run): that a deliberate fault turns the run red for AC-1 (coverage below
85 %), AC-2 (an error in a contract file) and AC-3 (a skipped web UI test). AC-3's failing-test half was seen on GitHub in
run 3, AC-4 in runs 3 and 8.

## Deliberate faults turn the gate commands red (2026-11-21)

What this is, in one paragraph. The demonstration PROVES that each gate command fails on its fault and names it: the Redocly
lint and the AsyncAPI validation of the contracts, the two commands of the `test` script of `web-ui/package.json` (Vitest, then
`scripts/no-skipped.mjs`) and the Gradle command with its JaCoCo rule each exit non-zero on one deliberate fault, print it, and
exit 0 on the same input without it; it runs the commands of `.github/workflows/ci.yml` on copies of the inputs, on this
machine, and is not a red run on GitHub. Its static workflow step CHECKS a stated list of switches in the workflow file and is
NOT a proof that no gate can be switched off; a switch that is not on the list is not seen. The list: `on:` has `push` and
`pull_request`; the jobs `build-and-check`, `contract-lint` and `web-ui` and the four steps that carry the Gradle line, the two
`npx` lines and `npm test` have no `if:` and no `continue-on-error` other than a literal `false`; those steps run with `shell:
bash` (GitHub runs it with `-eo pipefail`) in the working directory the script assumes (`modern`, `modern/web-ui`); none of the
three jobs has a `needs` whose needed job, followed through its own `needs`, has an `if:` or a `continue-on-error` other than a
literal `false`; no line of those four steps contains `||` or a `set +` (`set +e`, `set +o pipefail`, `set +eo pipefail`,
`set +ex`, `set +o errexit`, ...); each command is found in the job that owns it. Not seen: a condition is not evaluated (`if:
true` is reported like `if: false`); a switch outside the workflow file (a repository ruleset, a required-checks or
branch-protection setting, a disabled workflow); the branch and path filters of the triggers (printed, not judged); an `exit 0`
line before the command in the same step; a failure swallowed inside a script that a step calls; a shell option switched off by
other means than a `set +` line of the step (an `env:` entry such as `BASH_ENV`, a `trap`, a `set` continued with a backslash);
the other jobs and steps (annotations, compose smoke test, the lint step, `check_phase3.py`); the `runs-on` label,
`timeout-minutes`, `strategy`, `concurrency` and `permissions` of the jobs. Block `wf` shows that the check reports fourteen
forms of the listed switches (below); it says nothing about other forms.

Why: run 8 is green, and a green run does not show that a fault turns a run red. AC-1 (coverage below 85 %), AC-2 (an error in
a contract file) and AC-3 (a skipped web UI test) each say that a fault fails the run, and none of the three had evidence.
`modern/tools/checks/demo-ci-gates.sh` (new; sha256 0a8a8cec0ebde3784e2b6857808c46fcd300a075a5bb02210ac049944910ab64, 941 lines)
shows it on this machine, in the style of `demo-build-gates.sh` of MAM-16: PASS or FAIL per step, exit 1 when any step failed, the
free-memory rule, optional `PEM_GRADLE_LOCK` and `PEM_SCRATCH`, `DEMO_ONLY=ac1|ac2|ac3|wf` for one block. It does not copy the
commands. Before its blocks it parses `.github/workflows/ci.yml` (Python and PyYAML) and takes the Gradle line, the two pinned
`npx` lines and `npm test` from the job that owns each of them (`build-and-check`, `contract-lint`, `web-ui`); the two commands of
the `test` script come from `web-ui/package.json`. The same static step fails when one of the switches of its list is set in the
workflow (see "The workflow itself" below). Each block then runs first on a clean input (the negative control: it must pass) and
then on an input with ONE deliberate fault (it must fail, and the output must name the fault). How to run it:
`modern/docs/runbooks/ci.md`.

Recorded run (final version of the script, after the correction rounds below): 2026-11-21 08:53:30 to 09:00:58 (7 minutes 28
seconds; no waiting for free memory and none for the Gradle lock, which was free; the two Gradle runs took 2 min 4 s and 40 s), from
`modern/`, the workflow file of the working tree (HEAD a60dcd3 at the start and at the end; `ci.yml` is unchanged for this run, it was changed on 2026-11-24: see the last section), the build copy of
block `ac1` made from HEAD a60dcd3 (the log says the working tree has the same build logic and modules), `PEM_SCRATCH` and
`PEM_GRADLE_LOCK` set to the session scratch folder, exit 0, 30 steps PASS, 0 FAIL, 105 log lines. Summary line:
`== 2026-11-21 09:00:58: 2 Gradle run(s); waited 0 s for free memory and 0 s for the Gradle lock; 0 step(s) failed ==`.
Log: `C:/Users/ks519/AppData/Local/Temp/claude/afae246eb5f474f0/dfe81b9f-23d2-44cd-8efc-409693dc0951/scratchpad/mam39/demo-ci-gates-full.log`
(exit code in `demo-ci-gates-full.exit`; **that session folder no longer exists (checked 2026-11-24), so the figures of this run can no longer be checked against its log; the lines that matter were copied below at the time; the run of 2026-11-24 in the last section supersedes it**). The
script was not edited between the start of this run and the sha256 above (checked before and after: equal). The previous recorded
run (08:01:31 to 08:16:16, sha256 7ef1b86c..., 910 lines, 30 steps PASS, 14 min 45 s) is superseded by this one; its log is kept as
`demo-ci-gates-full-before-fsmodulecache.log` (that version counted only the results cache of Vitest). The one before it (07:19:12
to 07:31:00, sha256 f73500f7..., 779 lines, 23 steps PASS) is kept as `demo-ci-gates-full-before-last-correction.log`.

History, so that nothing is hidden. Run 1 (06:09 to 06:18, `demo-ci-gates-run1-control-failed.log`) failed two steps, both bugs
of the script and not of the workflow: the copy of the build lacked the one document that the library's tests read
(`docs/analysis/data-layouts.md`, read by `DataLayoutsDoc.java`), so the control `:libs:fixed-width:test` failed in the copy; and
the check for the test class to keep looked in the wrong folder. Run 2 (06:20 to 06:36, sha256 9f490525..., 524 lines,
`demo-ci-gates-full-before-review.log`) passed with 0 failed steps; an independent review then found four weaknesses in the
script's assertions (none gave a false PASS on that run, but the script said more than it checked), and this section is the
result of the correction round:

1. Every block could say PASS while the gate was switched off in the workflow (the first version read the first matching `run:`
   line anywhere, and never `continue-on-error`, `if:`, the triggers or the job). Fixed by the static step below, and the
   wording "it follows the workflow when that changes" is replaced by what the step really checks (this report, the runbook).
2. Step AC-2 (a2) counted a file as named by any line `[n] file:line:`, and Redocly prints warnings in the same format (8 of the 9
   files are named twice: the error, and a `no-unused-components` warning). Fixed: a file counts only with an error block of rule
   `no-unresolved-refs` that shows the missing name, and the summary must count exactly nine errors. Step (a) uses the same
   block check (it had three independent greps, which is the same weakness).
3. The runbook called the script directly, but `core.filemode` is false here, so a plain `git add` records the new file as
   100644 (its siblings are 100755): after a checkout on Linux the runbook command would give "Permission denied". Fixed in the
   documents: they call `bash tools/checks/demo-ci-gates.sh`. The mode in the index is for the lead at commit time
   (`git add --chmod=+x modern/tools/checks/demo-ci-gates.sh`); I did not touch the index.
4. Step (b) matched the file, the missing name and ` error ` with three separate greps, and (c) did not check the assertion text.
   Fixed: (b) needs one line with `error`, `invalid-ref` and `DemoMissingMessage`; (c) also needs `AssertionError: expected 1 to be 2`.

Two more changes that the review did not ask for, with the reason: (i) the static step also requires `shell: bash` and the
working directory the script assumes, because the script's own PASS depends on them (see below), and block `wf` showed eight
kinds of switch reported at that time; (ii) the clean-up step no longer fails when another process changes other entries of
the shared, git-ignored folder `web-ui/test-results`: in a trial run it failed because another lane's Playwright run had replaced
`.last-run.json` by `.playwright-artifacts-0` meanwhile, which is not this script's doing. It now fails on any `demo-ci-gates-*`
entry left behind, and on a change to the real `test-results/vitest.json`, and it prints other changes as a note.

Last correction round (2026-11-21 07:51 to 08:16). A second independent check of the corrected version confirmed every block and
left three points; this is what each led to.
1. Two ways of switching a gate off still got a PASS from the static step, and three texts said more than the step checked (the PASS
   line, the runbook sentence "nothing in `ci.yml` switches those gates off", and this report). Changed: the static step now
   reports any `set +` line of a gate step (before: only `set +e`, so `set +o pipefail`, `set +eo pipefail`, `set +ex` and
   `set +o errexit` were missed) and any `needs` of a gate job whose needed job, followed through its own `needs`, has an `if:` or a
   `continue-on-error` other than a literal `false`; block `wf` has six more copies of the workflow (fourteen now, one for each
   form named in "The workflow itself" below); the PASS line, the script header, the runbook and this report now say that the step
   checks a stated list and is not a proof that no gate can be switched off, and list what it does not see (the list is longer
   than before). I reproduced the finding first: the previous check printed 5 ok and 0 bad on a copy with `set +o pipefail` before
   the Gradle command, on a copy with `needs: compose-smoke` and `if: false` on compose-smoke, and on a copy with `set +eo
   pipefail`; the new check prints 4 ok and 1 bad on each.
2. The script did leave something behind: Vitest writes its results cache under Vite's `cacheDir`, and the file in
   `web-ui/node_modules/.vite/vitest/` had 25 entries for temporary files of earlier runs of this script. Changed: the temporary
   Vitest configuration sets `cacheDir` into the temporary folder, a new clean-up step compares the number of such entries in the
   project's cache before and after, and the 25 old entries were removed once, exactly (see "The Vitest caches" under AC-3). This
   moved only the results cache; the next round (below) found that Vitest has a second cache that this change did not move.
3. The report said as a fact which Python the static step had used, and the log did not print it. Changed: the static step prints
   the interpreter and the PyYAML version (log line 6 of the recorded run: `read with:
   C:\Users\ks519\Downloads\Medco-Transform\modern\kag\.venv\Scripts\python.exe (Python 3.11.9, PyYAML 6.0.3)`); the sentence in
   "What this proves" now quotes it.

Correction after a third independent check (2026-11-21 08:48 to 09:04). That check confirmed the static step, the fourteen
controls of block `wf`, the interpreter line, the wording about what the demonstration proves and what the static step is, and the
numbers of the run recorded at 08:16, and left two points.
1. The script still left files in the project's `node_modules`, and they grew with every run. Vitest 5.1.1 has a second cache,
   `fsModuleCache` (a `test` option, on by default), stored at `fsModuleCachePath` (default `web-ui/node_modules/.vitest-cache`),
   which Vite's `cacheDir` does not move. Every run of block `ac3` left four entries there (an entry file and a manifest key for
   each of the four temporary test files), the run recorded at 08:16 included. The clean-up step counted only the results cache, so
   its PASS ("none was added") held for that file only, and the sentences that said that the cache of these runs is removed with
   the folder, and that nothing remained, said more than the script did. I reproduced it before changing anything: the count was 37
   entry files and 37 manifest keys. Changed: the temporary configuration also sets `fsModuleCachePath` (into the temporary
   folder); the clean-up step counts both caches (the entries of the results cache, the entry files of the module cache whose
   content names `demo-ci-gates-`, and the manifest keys with `demo-ci-gates-` in the path) and fails when one of the three counts
   grows; the 37 old entry files and 37 manifest keys were removed once, exactly (numbers under "The Vitest caches" in AC-3); the
   header, the runbook and this report say what the script does.
2. Wording only: besides the Vitest caches, the tools that the script starts keep files of their own outside the script's folders,
   and the header, the runbook and this report said that the script writes only into two folders. Changed: the clean-up
   sentences now say that the script itself writes only into the scratch folder and into `web-ui/test-results/demo-ci-gates-<pid>/`,
   and name what the started tools keep (a daemon log of each Gradle call in `~/.gradle/daemon/<version>/`, the two CLIs in npm's
   cache, Vite's temporary bundle of the configuration in `web-ui/node_modules/.vite-temp/`, which it deletes after loading it);
   the script neither removes nor compares those files (see "Recording" below for what the recorded run left).

### The workflow itself: where the commands come from, and the switches the static step checks

Why this step exists (review of the first version): the first version took each command from the first matching `run:` line
anywhere in `ci.yml`. It never read `continue-on-error`, an `if:` of a step or a job, the triggers, or which job a line sits in, so
every block could say PASS while GitHub showed the job green because someone had switched the gate off in the workflow. The
workflow had none of these switches, so the recorded result of the first version held; but "it follows the workflow when
that changes" said more than the script checked.

What the static step checks (Python and PyYAML, in the script, before the blocks; it runs with every `DEMO_ONLY` value; it
prints the interpreter and the PyYAML version that read the workflow, see the log lines below):
- `on:` must have `push` and `pull_request` (branch or path filters are printed as a note, not judged).
- Jobs `build-and-check`, `contract-lint` and `web-ui`, and the four steps that carry the commands (the Gradle line in
  `build-and-check`, the Redocly line and the AsyncAPI line in `contract-lint`, `npm test` in `web-ui`), must have no `if:` and no
  `continue-on-error` (a literal `false` is accepted).
- Those steps must run with `shell: bash` (step, job or workflow default), because GitHub's default `bash -e` has no `pipefail`
  and `gradlew ... | tee` would hide the exit code of Gradle (this is what run 3 showed), and the script runs the same commands
  under `bash -eo pipefail`. This check goes beyond the four points of the first review; the reason is that the script's own
  PASS depends on it.
- Their working directory must be the one the script assumes (`modern`, and `modern/web-ui` for `npm test`).
- New in the last correction round: none of the three jobs may have a `needs` whose needed job, followed through its own `needs`,
  has an `if:` or a `continue-on-error` other than a literal `false` (GitHub skips a job whose needed job was skipped, and a skipped
  job is not red; this is the reviewer's statement of GitHub's behaviour, I did not see it on a run here; for a needed job
  with `continue-on-error` the check reports it as the review asked, and I did not check what GitHub does with the dependent job).
- Changed in the last correction round: no line of those four steps may contain `||` or a `set +` (before: `set +e` only), so
  `set +o pipefail`, `set +eo pipefail`, `set +ex` and `set +o errexit` are reported.
- Each command is taken from its own job (`npm test` is no longer found in any job).

The third review (the last correction round) found that the version before it gave `5 ok, 0 bad` on two switches that turn a gate
off: a line `set +o pipefail` before the Gradle command (exactly the failure of run 3: the exit code of `tee` hides Gradle's), and
`needs` on a job that has `if: false`. I reproduced this on the previous version of the check (its Python text, taken from
`demo-ci-gates.before-last-correction.sh`, run on copies of the workflow with that one switch; the copies are the ones of block
`wf` below): the previous check printed 5 ok and 0 bad for the `set +o pipefail` copy, for the `needs` copy and for a
`set +eo pipefail` copy; the new check prints 4 ok and 1 bad for each of them.

Block `wf` shows that the check is not vacuous: fourteen copies of the workflow in the scratch folder, each with one switch of the
list (one line added, changed or removed; the three variants that need more lines say so), and the check must report each one by
name; an unchanged copy must have no problem. The control shows it for these fourteen forms only. The repository's workflow is
not touched (its sha256 is part of the clean-up comparison).

| Switch (in a copy of the workflow) | Reported by the static check | Result |
|---|---|---|
| none (unchanged copy) | no problem reported; five checks hold (triggers and the four gate steps) | PASS |
| job `build-and-check` gets `continue-on-error: true` | `gradle: job build-and-check has continue-on-error: True` | PASS |
| step `Lint OpenAPI` gets `if: false` | `redocly: step 'Lint OpenAPI' has an if: condition (False)` | PASS |
| step `Validate AsyncAPI` gets `continue-on-error: true` | `asyncapi: step 'Validate AsyncAPI' has continue-on-error: True` | PASS |
| job `web-ui` gets `if: false` | `npmtest: job web-ui has an if: condition (False)` | PASS |
| the `shell: bash` default is removed | `gradle: step 'Build and check' runs with shell none set, not bash: GitHub's default is bash -e without pipefail, ...` (and the same for the other three steps) | PASS |
| the `pull_request` trigger is removed | `the workflow does not run on pull_request (on: has push)` | PASS |
| the Gradle command gets `\|\| true` | `gradle: step 'Build and check' has a line that can swallow a failure: ./gradlew --no-daemon --continue build checkAll 2>&1 \| tee "$RUNNER_TEMP/gradle.log" \|\| true` | PASS |
| `npm test` becomes `npm test \|\| true` | `npmtest: job web-ui has no step with a run: line matching ^npm test$` (the changed line is no longer the gate) | PASS |
| a line `set +o pipefail` before the Gradle command (the one-line `run:` becomes a `run: \|` block: one line replaced by three, the command line itself unchanged) | `gradle: step 'Build and check' has a line that switches a shell option off: set +o pipefail` | PASS |
| a line `set +eo pipefail` in the step `Install and test` (one line added) | `npmtest: step 'Install and test' has a line that switches a shell option off: set +eo pipefail` | PASS |
| a line `set +ex` in the same step (one line added) | `npmtest: step 'Install and test' has a line that switches a shell option off: set +ex` | PASS |
| a line `set +o errexit` in the same step (one line added) | `npmtest: step 'Install and test' has a line that switches a shell option off: set +o errexit` | PASS |
| job `web-ui` gets `needs: compose-smoke`, and the existing job `compose-smoke` gets `if: false` (two lines added; no extra job is needed) | `npmtest: job web-ui needs compose-smoke, which has an if: condition (False): GitHub skips web-ui when a job it needs is skipped, and a skipped job is not red` | PASS |
| job `web-ui` gets `needs: [compose-smoke]` (list form), and `build-and-check`, which `compose-smoke` needs, gets `continue-on-error: true` (two lines added): the needed job is found through the `needs` of `compose-smoke` | `npmtest: job web-ui needs compose-smoke -> build-and-check, which has continue-on-error: True` | PASS |

What the static step does not see, as said in the header of the script, the runbook and the first paragraph of this section:
conditions are not evaluated (`if: true` is reported like `if: false`); a switch outside the workflow file (a repository ruleset
or required-checks or branch-protection setting on GitHub, a disabled workflow) is not visible; the branch and path filters of
the triggers are printed, not judged; an `exit 0` line placed before the command in the same step, and a `||` or `set +e` hidden
in a script that a step calls, are not detected (the check looks at the lines of those four steps only); a shell option switched
off by other means than a `set +` line of the step (an `env:` entry such as `BASH_ENV`, a `trap`, a `set` whose option is on the
next line after a backslash) is not detected; the other jobs and steps (annotations, compose smoke test, the lint step,
`check_phase3.py`) are not looked at; the `runs-on` label, `timeout-minutes`, `strategy`, `concurrency` and `permissions` of the
jobs are not looked at. I did not try any of these to see what the check does with it; they are listed because the check has no
code for them.

### AC-2: a deliberate error in a contract file fails the lint

Setup: a copy of `modern/contracts` in the scratch folder; the commands run from the copy exactly as the workflow runs them from
`modern/` (shell `bash -eo pipefail`). `modern/contracts` itself is never touched (sha256 of its files before and after: equal).

| Step | Fault | Command | Failure text seen | Result |
|---|---|---|---|---|
| control | none | `npx --yes @redocly/cli@1.34.3 lint contracts/openapi/*.yaml` | exit 0, 9 of 9 OpenAPI files validated: `Woohoo! Your API descriptions are valid.` | PASS |
| control | none | `npx --yes @asyncapi/cli@2.14.0 validate contracts/asyncapi/events.yaml` | exit 0: `File contracts/asyncapi/events.yaml is valid! ... don't have governance issues.` | PASS |
| (a) | `risk-service.yaml` line 39: `$ref: '#/components/schemas/RiskRuleSet'` becomes `.../DemoMissingSchema` (one line changed) | the Redocly command above | exit 1: `[1] contracts/openapi/risk-service.yaml:39:17 at #/paths/~1api~1v1~1risk-rules/get/responses/200/content/application~1json/schema`, `Can't resolve $ref`, `$ref: '#/components/schemas/DemoMissingSchema'`, `Error was generated by the no-unresolved-refs rule.`, `Validation failed with 1 error.`; the script now requires that exactly one error block (rule `no-unresolved-refs`, with the missing name, in the same block) is at `risk-service.yaml:39` | PASS |
| (a2) | the same fault in each of the nine OpenAPI files | the Redocly command above | exit 1: nine error blocks, one per file (`directory-service.yaml:78`, `enrollment-service.yaml:35`, `notification-service.yaml:66`, `provider-service.yaml:141`, `revalidation-service.yaml:77`, `risk-service.yaml:39`, `sanctions-service.yaml:34`, `screening-service.yaml:76`, `tmsis-service.yaml:33`); `Validation failed with 9 errors and 8 warnings.` The eight warnings (`no-unused-components`: the schema whose only reference was broken is now unused) are in the same `[n] file:line` format; the script does not count them as naming a file | PASS |
| (b) | `events.yaml` line 23: the first message `$ref` (`#/components/messages/ApplicationValidated`) becomes `.../DemoMissingMessage` (one line changed) | the AsyncAPI command above | exit 1: `File contracts/asyncapi/events.yaml and/or referenced documents have governance issues.`, and on ONE line `77:13  error  invalid-ref  '#/components/messages/DemoMissingMessage' does not exist  operations.enrollmentService_send_ApplicationValidated.messages[0].$ref` (the script matches severity, rule and missing name on that one line) | PASS |

### AC-3: a skipped or failing web UI test fails the step

Setup: the workflow's web UI step runs `npm test`, and the `test` script of `package.json` is `vitest run --reporter=default
--reporter=json --outputFile=test-results/vitest.json && node scripts/no-skipped.mjs test-results/vitest.json`. The static step of
the script finds `npm test` as a line of the web-ui job's step `Install and test` (not in another job) and checks that this step and
its job have no `if:` and no `continue-on-error`, run with `shell: bash` in `modern/web-ui` and have no `||`; the block checks that
the `test` script is exactly `vitest run ... && node scripts/no-skipped.mjs <report>` (no second `&&`, no `||`, no `;`), takes the
two commands from `package.json` (only the report path is replaced; `--config` and a file filter are added to the Vitest command)
and runs them in that order, computing the exit code of the chain with the semantics of `&&`.

Plainly: a temporary test file cannot be put through the project's Vitest configuration as it is, because that configuration only
includes `tests/**/*.test.{ts,tsx}`, a tracked folder. What the script does instead: it writes the temporary tests and a temporary
config into the git-ignored folder `web-ui/test-results/demo-ci-gates-<pid>/`; the config imports the project's own `vite.config.ts`
and replaces `test.include`, Vite's `cacheDir` and Vitest's `fsModuleCachePath` (see "The Vitest caches" below), so the plugins,
the jsdom environment, `tests/setup.ts` and the other options are the project's.
Vitest is started from PowerShell with a lowercase drive path (`c:\Users\...\modern\web-ui`, `.\node_modules\.bin\vitest.cmd`).
`npm test` itself is not run (it runs the whole suite and writes `test-results/vitest.json`): its two commands are, on the temporary
files. The folder and the reports are removed on exit (checked: no `demo-ci-gates-*` left, and `test-results` has the same entries as
before, only the `.last-run.json` of Playwright).

The Vitest caches (two correction rounds). Vitest 5.1.1 keeps two caches in `web-ui/node_modules` (git-ignored; the Vitest runs of
the web UI tests read and write them too). (1) The results cache, `<cacheDir>/vitest/<hash>/results.json`; for this project
`web-ui/node_modules/.vite/vitest/da39a3ee5e6b4b0d3255bfef95601890afd80709/results.json`. (2) The transformed-module cache
(`fsModuleCache`, a `test` option, on by default), stored at `fsModuleCachePath`, by default
`<workspace root>/node_modules/.vitest-cache`, here `web-ui/node_modules/.vitest-cache`: one file named by 40 hex digits per
transformed module, and `_manifest-web-ui-05901ece.json`, which maps the module paths to those files. Vite's `cacheDir` does not
move it (Vitest's class `FileSystemModuleCache` takes `config.fsModuleCachePath` or that default; read in
`web-ui/node_modules/vitest/dist/chunks/cli-api.D6E8EtSi.js`, not run).

First round (results cache, 07:51 to 08:16). The earlier versions of the script left an entry for each temporary test file of
each run in the results cache (`test-results/demo-ci-gates-<pid>/demo-skip.test.ts` and so on): at 07:51 the file had 74 entries,
25 of them for `demo-ci-gates-` paths. It did not change a test result and is git-ignored, but it grew with each run. What changed
then: (1) the temporary configuration set Vite's `cacheDir` to `<temporary folder>/.vite`; (2) the clean-up step counted the
entries with `demo-ci-gates-` in the path in the results cache before and after the run and failed when the count grew. Shown: a
trial run of block `ac3` only (07:57:19 to 07:58:37, `trial2-ac3.log`), made while the 25 old entries were still in the file,
printed 25 before and 25 after, and the file had the same modification time (07:47) and size (9303 bytes) before and after.
Removal of the 25 old entries (07:59), made once, by a throwaway script in the session scratch folder (`clean-vitest-cache.cjs`,
not in the repository; it first ran as a dry run): it wrote only when (a) the file was byte for byte what `JSON.stringify` writes
for its parsed content, (b) the new text equalled the original text with exactly the 25 fragments of the removed entries (each with
one adjacent comma) cut out, (c) the 49 kept entries were identical in key, order and value and none had `demo-ci-gates-` in its
path, and (d) the file did not change between reading and writing; all four held. Result: 74 entries and 9303 bytes (sha256
660bc3c1...) became 49 entries and 5767 bytes (sha256 5035f9ef...); the 49 kept entries are the project's own test files, unchanged; a
copy of the file before and after is in the scratch folder (`vitest-results.before-cleanup.json`,
`vitest-results.after-cleanup.json`). That round moved only the results cache. The run recorded at 08:16 (sha256 7ef1b86c..., 910
lines) counted only that cache (0 before, 0 after) and left four entries in the module cache, which nothing counted.

Second round (module cache, 08:48 to 09:04). The third independent check found in `web-ui/node_modules/.vitest-cache` 37 entry files
whose content names `demo-ci-gates-` and, in `_manifest-web-ui-05901ece.json`, 37 keys with `demo-ci-gates-` in the path: four per run
of block `ac3` for nine runs (36; among them pid 151961, the trial after the `cacheDir` change, pid 152648, the run recorded at 08:16,
and pid 156930, the reviewer's own run) and one for a prototype (`demo-ci-gates-proto`, 05:47). What changed: (1) the temporary
configuration also sets `fsModuleCachePath` to `<temporary folder>/.vitest-cache`; (2) the count of the script is now three
numbers (entries of the results cache, entry files of the module cache, keys of its manifest) and the clean-up step fails when any
one of them grows. Counted with the new function before any change: 0, 37 and 37. Removal (08:51), made once, by a throwaway script
in the session scratch folder (`mam39/fsmc/clean-module-cache.cjs`, not in the repository), exact, and not the whole folder: no
Vitest or node process was running, and the script first ran as a dry run: the manifest serializes back to exactly its own bytes
(17613 bytes, no trailing newline), the 37 keys named 37 entry files, every one of which names `demo-ci-gates-`, no other entry file
does, and no key outside the 37 uses one of those files. Then it copied the manifest and the 37 files to `mam39/fsmc/backup/`,
removed the 37 keys, wrote the manifest (to a temporary name, then renamed it) and deleted the 37 files. Result: the folder went from
329 files (326 entry files) to 292 (289 entry files); the manifest from 172 to 135 module keys and from 17613 to 13647 bytes
(sha256 f7eb7521... to b29659af...); the 135 remaining keys all point at an existing file; the count is 0, 0 and 0. Not touched:
every entry that does not name `demo-ci-gates-`, and the results cache (`web-ui/node_modules/.vite`: `results.json` sha256
5035f9ef..., the same as after the first round). The reviewer also saw the project's own entry for `tests/setup.ts` rewritten at
08:30:23, one second before the first temporary entry of his run; I did not prove which run wrote it. Shown: a trial run of block
`ac3` only (08:51:52 to 08:52:51, `mam39/fsmc/trial-ac3-fsmc.log`) printed `... had 0 entry files and 0 manifest keys before and has
0 and 0 after`; the temporary runs wrote 1 `results.json` and 5 module-cache entry files inside their own temporary folder; the
manifest of the project's module cache had the same sha256 (b29659af...) before and after the trial. Recorded run (08:53:30 to 09:00:58): the clean-up step printed `Vitest caches: for
demo-ci-gates- paths the project's results cache (web-ui/node_modules/.vite/vitest) had 0 entries before the run and has 0 after, its
module cache (web-ui/node_modules/.vitest-cache) had 0 entry files and 0 manifest keys before and has 0 and 0 after: none was added;
the temporary Vitest runs wrote 1 results.json file(s) and 5 module-cache entry file(s) inside their own temporary folder (removed
with it)`, and the start of the log has the same three numbers (`0 0 0`). By hand after the run (09:01), with the same function: 0, 0
and 0; the module cache still has 289 entry files (292 files), its manifest the same sha256 (b29659af...) as after the removal, and
the newest file in the folder is still from the removal (08:51:40); `results.json` has the same sha256 (5035f9ef...) as before;
`web-ui/node_modules/.vite-temp` is empty. So neither of the two Vitest caches of the project got anything from the run.
Limits: the script counts only entries whose path (results cache, manifest keys) or content (module cache entry files) names
`demo-ci-gates-`, and does not compare anything else in `node_modules/.vite` or `node_modules/.vitest-cache`; another lane's Vitest
run could change these files during a run of this script (see the by-hand check above); the 37 and 25 entries are the only evidence
of what the earlier versions wrote, I did not run an earlier version again to see it.

| Step | Fault | Failure text seen | Result |
|---|---|---|---|
| (a) | a file with one `it.skip` | Vitest alone exits 0 (`Tests  1 passed \| 1 skipped (2)`: it does not fail a skipped test); `node scripts/no-skipped.mjs` exits 1: `SKIPPED: MAM-39 demo this test is skipped on purpose (.../demo-skip.test.ts)`, `1 skipped test(s): a test cannot be skipped to get to green.`; the step fails (exit 1) | PASS |
| (a) control | the same file with `it` instead of `it.skip` (one line differs) | Vitest exits 0, `no-skipped.mjs` exits 0: `no skipped tests (2 tests)`; the step is green | PASS |
| (b) | `describe.skip`, `it.skipIf(true)` and `it.todo` | `no-skipped.mjs` exits 1 and names all three: `SKIPPED: MAM-39 demo skipped group inside a skipped group`, `... skipped by skipIf`, `... a todo test` | PASS |
| (c) | a failing test (`expect(1).toBe(2)`) | Vitest exits 1: `× this test fails on purpose`, `AssertionError: expected 1 to be 2 // Object.is equality`; the script now requires both the test name and the assertion text; the `&&` stops the chain, `no-skipped.mjs` is not reached; the step fails (exit 1). This is the failing-test half that run 3 showed on GitHub | PASS |

### AC-1: a change that lowers coverage below 85 % fails the build

Setup: a copy of the build in the scratch folder made with `git archive` of HEAD (the root build files, `buildSrc`, `gradle`,
`libs/fixed-width`, `libs/test-support` and the one document the library's tests read; the other projects of `settings.gradle.kts`
are empty folders). The JaCoCo rule is the one of `buildSrc` unchanged (`counter = "LINE"`, `value = "COVEREDRATIO"`,
`minimum = "0.85"`, lines 101 to 103 of `pem.java-conventions.gradle.kts`). The command is the workflow's, run under
`bash -eo pipefail` like the step: `./gradlew --no-daemon --continue build checkAll 2>&1 | tee "$RUNNER_TEMP/gradle.log"`; the one
change is `--no-build-cache` (a clean runner has no build cache; a developer machine does, and a restored test result would prove
nothing).

| Step | Input | Failure text seen | Result |
|---|---|---|---|
| control | the unchanged copy, 8 test classes | exit 0, `BUILD SUCCESSFUL in 2m 4s`; `:libs:fixed-width:test` and `:libs:fixed-width:jacocoTestCoverageVerification` executed; JaCoCo report: lines covered 158/182 = 86.81 % | PASS |
| fault | in the copy, 7 of the 8 test classes deleted (`RoundTripTest` kept); the repository's tests are untouched (sha256 of `libs/fixed-width` before and after: equal) | exit 1: `> Task :libs:fixed-width:jacocoTestCoverageVerification FAILED`, `[ant:jacocoReport] Rule violated for bundle fixed-width: lines covered ratio is 0.33, but expected minimum is 0.85`, `BUILD FAILED in 40s`; it is the only failed task (the script counts them); JaCoCo report: 61/182 = 33.52 % | PASS |

### What this proves, and what it does not

It proves that with the workflow's own commands (the same pinned `@redocly/cli` 1.34.3 and `@asyncapi/cli` 2.14.0, the `test`
script of `package.json`, the Gradle command with the `| tee` under `pipefail`, and the JaCoCo rule of `buildSrc`) a deliberate
fault in each of the three places makes the command exit non-zero and the output names the fault, and that the same commands on
the clean input exit 0. For AC-3 it also shows why the guard exists: Vitest alone is green on a skipped test, and only
`scripts/no-skipped.mjs` turns it red. The control of AC-1 is a real pass of the rule (the gate task ran, 1.81 points above the
minimum), and the fault fails in that gate and nowhere else. The static step is a check and not a proof: it found none of the
switches of its list (see the first paragraph of this section) set in `.github/workflows/ci.yml` at the time of the run (log line:
`PASS Workflow wiring: none of the switches on the checked list (script header, block wf) is set in .github/workflows/ci.yml: 5 checks
of the triggers, the three gate jobs and the four steps that carry the commands; this is not a proof that no gate can be switched
off`), and block `wf` shows that it reports each of fourteen forms of those switches. It says nothing about a switch that is not on
its list.

It does not prove:
- That a run on GitHub goes red. This is a local run on Windows 11 in Git Bash (Node 22.19.0, Java 21.0.10: read by hand with `node --version` and `java -version`; the script does not print them); the workflow runs on
  ubuntu-24.04 with Node 20.19.2 (contract lint) and 22.22.2 (web UI) and Temurin 21.0.7. Nothing was pushed, so no red run, no red
  job and no annotation of a faulty push was seen. That a non-zero exit turns the step, and so the job, red is GitHub's rule for
  `shell: bash` (`-eo pipefail`, set in `defaults.run.shell` after run 3, see the third run above); it is not shown again here.
- AC-1 on a service. The rule was run on `libs:fixed-width`, the smallest module with tests; the ten services get the same rule
  from the same convention plugin (read, not run per service). The PIT gate (70 %) is not part of this. The fault is "tests removed
  from a copy", one way to lower coverage; untested production code added to a service would end in the same rule, but was not tried.
- AC-2 beyond one kind of error: an unresolved `$ref` in the OpenAPI files and in `events.yaml`. Syntax errors and other lint rules
  were not tried, and `tools/checks/check_phase3.py` (the third step of the contract lint job) is not part of this.
- AC-3 as `npm test` itself, and the lint step (`npm run -s lint`) of the same job.
- The annotation steps (`if: failure()`) of the workflow: they were not run.
- Anything the static step does not look for. Its list is in the first paragraph of this section, and so is what it does not see
  (conditions are not evaluated; a ruleset, a required-checks or branch-protection setting or a disabled workflow on GitHub; the
  filters of the triggers; an `exit 0` before the command; a script that a step calls; a shell option switched off by other means
  than a `set +` line; the other jobs and steps; `runs-on`, `timeout-minutes`, `strategy`, `concurrency`, `permissions`). I did not
  try any of those. The step needs Python with PyYAML (without one it FAILs and the commands cannot be read); the recorded run
  printed which one it used: `read with: C:\Users\ks519\Downloads\Medco-Transform\modern\kag\.venv\Scripts\python.exe (Python 3.11.9,
  PyYAML 6.0.3)`. The workflow runs `pip install pyyaml==6.0.2` on Python 3.12.10; the step was not run with those versions.
- That GitHub skips a job whose needed job was skipped and does not show it red: this is stated by the reviewer and used in the
  message of the `needs` check; I did not see it on a run. What GitHub does with a dependent job when the needed job has
  `continue-on-error` was not checked either.

Recording: as in MAM-16, whose demonstration script is not in `MAM-16.tests.sha256` (that file lists test files only: two files,
none of them `demo-build-gates.sh`), the script is listed under "Files" here with its sha256 and the run is recorded in this
section. `test_protection.py record MAM-39` was not run, also not after the correction round, because the script was never
recorded that way and MAM-39 has no test files: `check MAM-39` answers `no hash file for MAM-39: run 'record' after the red step`
(exit 2); `check MAM-16` is `OK: 2 test files of MAM-16 unchanged; no unrecorded test files` and `check MAM-42` is `OK: 10 test files
of MAM-42 unchanged; no unrecorded test files` (the tests of `libs/fixed-width`). If the script should be hashed anyway, it is one
`record` command and would create `MAM-39.tests.sha256` and `MAM-39/red-blobs.txt` (`test_protection.py` was not run in the last
round either). What was compared after the recorded run: the script's own last steps compared the sha256 of 55 files
(`contracts/`, `libs/fixed-width`, the build logic, the web UI scripts and `.github/workflows/ci.yml`) before and after: equal; the
number of `demo-ci-gates-` entries in the project's two Vitest caches (results cache; module cache entry files; module cache
manifest keys): 0 0 0 before, 0 0 0 after; and `web-ui/test-results/vitest.json`: as before. By hand after the run (09:01): no
`demo-ci-gates.*` folder in the scratch folder, no `demo-ci-gates-*` entry in `web-ui/test-results` (it holds only
`.last-run.json`), the same files with the same sha256 in `web-ui/node_modules/.vite`, `web-ui/node_modules/.vite-temp` empty, the
module cache as described under AC-3, and `git status` shows for these files only `modern/docs/reports/stories/MAM-39.md` and
`modern/docs/runbooks/ci.md` modified and `modern/tools/checks/demo-ci-gates.sh` untracked (all three were already in that state
before this round, except for the changes described here). The script releases the Gradle lock after each of its two runs (and in
its exit trap); at 09:01 the lock folder does not exist. The tools that the script starts keep files of their own outside the
script's folders, which the script neither removes nor compares. The two Gradle calls of the recorded run each left a daemon log in
~/.gradle/daemon/8.14.7/ (daemon-46836.out.log, daemon-26716.out.log, about 50 KB each; both name the scratch folder
`demo-ci-gates.fjq8bN`); they are Gradle's own files and the script does not remove them. The same holds for the two Gradle calls of
every earlier run of this script (the reviewer found `daemon-44968.out.log` and `daemon-11872.out.log` for the run recorded at 08:16, and
five more from earlier runs, which I did not look at again). The two pinned CLIs stay in npm's cache
(`C:/Users/ks519/AppData/Local/npm-cache/_npx/`: `@redocly/cli` 1.34.3 and `@asyncapi/cli` 2.14.0, as the reviewer found; I did not
look at it again), and Vite writes a bundle of the temporary configuration into `web-ui/node_modules/.vite-temp/` and deletes it after
loading it (read in Vite's source by the reviewer; the folder was empty after the run). `scan_secrets.py`: CLEAN (09:03 to 09:04, after the run and after the edits of the three files; 16 values of .env.local checked in 2177 files). Not compared by
the script, and not looked at by me: any other file of the repository or of `node_modules`.

Files: `modern/tools/checks/demo-ci-gates.sh` (new; sha256 0a8a8cec0ebde3784e2b6857808c46fcd300a075a5bb02210ac049944910ab64, 941 lines;
to be committed with mode 100755 like its siblings), `modern/docs/runbooks/ci.md` (section added), this report (section added).
Not changed by that step (2026-11-21): `.github/workflows/ci.yml`, `modern/buildSrc`, any production code, test or contract file. **The workflow and the script were changed afterwards (2026-11-24): see the last section.**

## Runs 9 to 20, the workflow changes of 2026-11-24, the final check and the closing report (2026-11-24)

**GitHub runs since the eighth** (read from the public API of the repository on 2026-11-24, branch `modernization`; the job logs need a sign-in, the steps and
annotations do not):

| Run | Commit | Result | Cause or note |
|---|---|---|---|
| 9 to 12 | (see PROGRESS.md, 2026-11-21 04:04 to 07:44) | success | five green runs in a row (8 to 12) are recorded there; not re-read from the API today |
| 13, 14, 15, 16 | 51b771f, a6a8814, 7aa7820, a5760fc | success | read from the API today |
| 17 | a60dcd3 (shared kit: uuid correlation id) | **failure after 75 s**, step "Build and check", no task ran | recorded on 2026-11-21 as: buildSrc could not get `kotlin-stdlib 2.0.21` ("Could not find"). The log is not readable; run 19 shows the same artifact failing with HTTP 429, so this was **probably the same cause (not proven)** |
| 18 | 3222fbe (documents only on top of a60dcd3) | **failure after 38 min**, `:services:revalidation-service:test`: 16 tests of `RevalidationCorrelationIdTest` (an event not processed within 45 s: `ConditionTimeoutException`) | the other 14 cases of the class and every other module passed. **Cause not found.** Local reproduction on 2026-11-24 (build with the kit change, the class alone): 30 tests, 0 failures, 0 skipped, about 1 s per case, BUILD SUCCESSFUL in 8 min 34 s; the test sends the event straight to Kafka (`EventProbe`, not through the changed `OutboxWriter`) and the consumer uses `auto-offset-reset: earliest`, so neither the kit change nor a lost first message explains it. A slow or throttled runner is a guess. |
| 19 | ee34503 | **failure after 2 min 31 s**, step "Resolve build logic and plugins (up to three attempts)" (new in this commit) | the failure summary printed the cause: `Received status code 429 from server: Too Many Requests` from repo.maven.apache.org for `kotlin-gradle-plugin-api 2.0.21`; all three attempts (30 s apart) hit it. Contract lint and Web UI tests of the same run were green. |
| 20 | b82359a | started 2026-11-24 13:51 | see the end of this section |

**What was changed on 2026-11-24** (`.github/workflows/ci.yml`; against 3222fbe one line of the old file is gone, inside the failure-summary step; the four gated
steps "Build and check", "Lint OpenAPI", "Validate AsyncAPI" and "Install and test" are untouched, the reviewer's `git diff` of ee34503 and the diff of b82359a show 0
removed lines there):
1. ee34503: a warm-up step before the gate (`./gradlew --no-daemon -q help`, repeated up to three times, output to `warmup.log`); the failure summary prints the first
   two failures in detail (message up to 700 characters, 14 lines of the stack trace, up to 25 warning and error lines of the output of the class) and no longer crashes when
   `gradle.log` does not exist.
2. b82359a (after run 19): `actions/cache/restore` and `/save` v5.0.5 for `~/.gradle/caches/modules-2` and `~/.gradle/wrapper/dists` only (no Gradle build cache, no test
   result: every gate still runs from scratch); the warm-up repeats up to five times with waits of 60, 120, 180 and 240 seconds. The save step runs after a successful warm-up
   even when a test failed (`if: always() && steps.warmup.outcome == 'success'`). The static step of the gate demonstration passes on this file (18 PASS in block `wf`, 13:49).
`modern/docs/runbooks/ci.md` has a new section "When the Gradle job fails before any test (downloads)".

**Re-run of the gate demonstration on 2026-11-24** (script `modern/tools/checks/demo-ci-gates.sh`, sha256 `38d13696bb59ddb986a8ff40b9675bb9b3d97b85cc1f8f4f850217b00060f6d4`, 941
lines, git mode 100755; `PEM_MIN_FREE_MB=250` because Docker Desktop and other projects' containers leave this machine about 300 to 800 MB free: the script's own limit of 1024 MB
would have waited for ever; the machine ran the builds without trouble). Three runs, each exit 0 and 30 PASS, 0 FAIL:
- 13:18:50 to 13:26:39 (7 min 49 s) on the workflow of 3222fbe plus the warm-up of the working tree, script sha256 `0a8a8cec...` (the one of 2026-11-21). Not the final script.
- 13:40:33 to 13:46:25 (5 min 52 s), HEAD ee34503, final script. Gradle 1 min 51 s and 32 s.
- **13:52:38 to 13:57:55 (5 min 17 s), HEAD b82359a, final script and final workflow (sha256 `5d41e1c0cc35e90d...`): exit 0, 30 PASS, 0 FAIL, 106 log lines. Gradle 1 min 40 s
  (the clean copy passes) and 33 s (the copy with the deleted test classes fails in `jacocoTestCoverageVerification`); static step read with Python 3.11.9 and PyYAML 6.0.3 from
  `modern/kag/.venv`; the build copy is `git archive` of HEAD and the log states that the working tree has the same build logic and modules; 55 compared files unchanged (the
  generated `buildSrc/.kotlin` is no longer compared); entries for `demo-ci-gates-` paths in the project's Vitest caches 0 0 0 before and 0 0 0 after; summary line
  `== 2026-11-24 13:57:55: 2 Gradle run(s); waited 0 s for free memory and 0 s for the Gradle lock; 0 step(s) failed ==`.**
  Log: `C:/Users/ks519/AppData/Local/Temp/claude/afae246eb5f474f0/9c3a2f24-f133-402a-87b5-0cf27b3a2c03/scratchpad/demo-ci-gates.log` (a session scratch folder: it can disappear,
  so the figures are copied here). Scratch folder of the run `/tmp/demo-ci-gates.6utSny`; the two Gradle daemon logs are the newest two in `~/.gradle/daemon/8.14.7/`.

**Independent final check** (code-reviewer, read-only, 2026-11-24, on the state of the first run of the day): the script and the workflow change are sound. Open point 1
of 2026-11-21 (the second Vitest cache) is fixed in the script (both caches redirected into the temporary folder, counted before and after, no residue on disk: `test-results`
holds only `.last-run.json`, `.vite-temp` is empty, `.vitest-cache` has no `demo-ci-gates-` entry); open point 2 (a sentence that says more than the log shows) was
the statement "Not changed: ci.yml" and the "7 min 28 s" figures, now qualified or replaced. The warm-up cannot turn a red gate green (it runs before the gate, the gate runs once, the
failure-summary step is `if: failure()`). MUST-FIX 3, all documents, all corrected here: ci.yml statements, the missing record of the 2026-11-24 run, the missing closing report.
SHOULD-FIX done: the `buildSrc/.kotlin` exclusion and "an unreadable cache count is a FAIL" in the script; the comments of ci.yml (a build-logic error is repeated too; the
annotation "no payloads" claim reworded); the guard for a missing `gradle.log` (tested with and without the file); `node --version`/`java -version` marked as read by hand; the
unsupported "about 2 and 4 minutes" npx figure removed from ci.md; ci.md's stale state paragraph, `--continue` in the Gradle command and the warm-up documented. The
executable bit is recorded in git as mode 100755 (`git ls-files -s`), as the reviewer asked.

**Closing report for the story (MODERNIZATION.md section 9)**
```
Phase / item:      MAM-39 CI pipeline
Outcome:           NEEDS DECISION until a green run of the final workflow exists (run 20 in progress); the demonstration part is DONE
What was done:     workflow on GitHub since 2026-11-20 (user decision 19:32); eight fixes to a first green run (2026-11-21 05:05); gate demonstration (AC-1, AC-2, AC-3)
                   with the faults named by the failing commands, run three times on 2026-11-24, final run 30 PASS 0 FAIL; runs 17 to 19 analysed; two workflow changes
                   (warm-up with retry, Gradle library cache, failure annotations in detail)
Checks run:        demo-ci-gates.sh (13:52:38 to 13:57:55, HEAD b82359a): 30 PASS, 0 FAIL; static step on the final workflow; the failure-summary script run against fake
                   results with and without gradle.log; GitHub API reads of runs 13 to 19
AC-1 coverage gate     demonstrated locally (block ac1: the clean copy passes, a copy with deleted test classes fails in jacocoTestCoverageVerification with the rule's message);
                       on GitHub the Gradle job was green in runs 8 to 16 and is red in 17, 18 (revalidation timeouts, cause unknown), 19 (HTTP 429)
AC-2 contract lint     demonstrated locally (block ac2: unresolved $ref in each of nine OpenAPI files and in events.yaml fails and names the file); GitHub job green in runs 18 and 19
AC-3 web UI tests      demonstrated locally (block ac3: it.skip, describe.skip, it.skipIf, it.todo and a failing test are red and named); GitHub job green in runs 18 and 19
AC-4 compose smoke     ran green in the green runs; skipped when the Gradle job is red (it needs it)
AC-5 pinned versions   met; actions/cache v5.0.5 added with an exact version (published 2026-04-13)
AC-6 inert workflow    SUPERSEDED by the user's decision of 2026-11-20 19:32 ("KEEP GOING", answer 1, PROGRESS.md): the workflow is at the repository root and runs. The spec
                       modern/specs/MAM-39.md (specVersion 1) still says it is at modern/.github and nothing is pushed; changing the spec is the user's call (MODERNIZATION.md section 8)
Not verified:      a green run of the final workflow (run 20 is the first); the cause of the 16 timeouts of run 18 (not reproduced; if run 20 shows them again the new annotation
                   prints the first two failures in detail); the "First failure in detail" annotation on GitHub (no test failed in run 19); that the cache is restored and saved on
                   GitHub; that this demonstration says anything about a red run on GitHub (it is a local run on Windows)
Questions for you: 1. Spec MAM-39 AC-6: re-version it to say "the workflow runs from the repository root" (recommended), or leave the Jira text and keep this record? 2. If run 18's
                   timeouts come back: how much time may be spent on them, since no local reproduction exists?
Next step:         read run 20; Jira Done only after a green run of the final workflow
```
Files: `.github/workflows/ci.yml`, `modern/tools/checks/demo-ci-gates.sh` (sha256 above, mode 100755), `modern/docs/runbooks/ci.md`, `.gitignore` (`**/.kotlin/`), this report.
