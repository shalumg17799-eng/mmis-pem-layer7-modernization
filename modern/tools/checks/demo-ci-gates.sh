#!/usr/bin/env bash
# MAM-39 demonstration that a deliberate fault turns the commands of three CI gates red (run it from modern/, or anywhere).
#
# WHAT THIS IS AND WHAT IT IS NOT. The demonstration PROVES that each gate command fails on its fault and names it: the Redocly lint
# and the AsyncAPI validation of the contracts, the two commands of the "test" script of the web UI, and the Gradle command with its
# JaCoCo rule each exit non-zero on one deliberate fault, print that fault, and exit 0 on the same input without it. It runs the
# commands that .github/workflows/ci.yml contains, on copies of the inputs, on this machine; it is not a red run on GitHub. The static
# workflow step CHECKS a stated list of switches in the workflow file and is NOT a proof that no gate can be switched off: a switch
# that is not on the list is not seen. The list: (1) on: has push and pull_request; (2) the jobs build-and-check, contract-lint and
# web-ui and the four steps that carry the Gradle line, the two npx lines and "npm test" have no "if:" and no "continue-on-error"
# other than a literal false; (3) those steps run with shell: bash (GitHub runs it with -eo pipefail) in the working directory this
# script assumes; (4) none of those three jobs has a "needs" whose needed job, followed through its own needs, has an "if:" or a
# "continue-on-error" other than a literal false; (5) no line of those four steps contains "||" or a "set +" (set +e, set +o pipefail,
# set +eo pipefail, set +ex, set +o errexit, ...); (6) each command is found in the job that owns it. NOT SEEN: a condition is not
# evaluated (if: true is reported like if: false); a switch outside the workflow file (a repository ruleset, a required-checks or
# branch-protection setting, a disabled workflow); the branch and path filters of the triggers (printed, not judged); an "exit 0"
# line before the command in the same step; a failure swallowed inside a script that a step calls; a shell option switched off by
# other means than a "set +" line of the step (an env: entry such as BASH_ENV, a trap, a "set" continued with a backslash); the other
# jobs and steps (annotations, compose smoke test, lint step, check_phase3.py); the runs-on label, timeout-minutes, strategy,
# concurrency and permissions of the jobs.
#
# Per block this script runs the commands of the workflow (read from .github/workflows/ci.yml at the repository root), first on a
# clean input (the negative control: it must pass) and then on an input with ONE deliberate fault (it must fail, and the output must
# name the fault). The differences to the workflow's own command are stated per block.
#   Block ac2  AC-2 contract lint: a copy of contracts/. Clean copy: redocly lint of the OpenAPI files and asyncapi validate of
#              events.yaml pass. Then (a) a $ref to a schema that does not exist in one OpenAPI file, (a2) the same fault in each
#              of the nine files, (b) a $ref to a message that does not exist in events.yaml: the command exits non-zero and
#              names the file and the fault.
#   Block ac3  AC-3 web UI: a tiny temporary Vitest file in a git-ignored folder (web-ui/test-results/demo-ci-gates-<pid>/), run
#              through a temporary Vitest configuration that imports the project's own and changes three things: the list of test files,
#              Vite's cacheDir and Vitest's fsModuleCachePath (Vitest keeps its results cache under the first and its transformed-module
#              cache under the second; both point into the same temporary folder, so the entries of the temporary files do not reach
#              the project's caches in web-ui/node_modules/.vite and web-ui/node_modules/.vitest-cache). The commands are those of the
#              "test" script of package.json: vitest run (the JSON report) && node scripts/no-skipped.mjs <report>, with the report
#              path replaced and --config and a file filter added. it.skip -> red and named, the same file without the skip -> green,
#              describe.skip / it.skipIf / it.todo -> red and named, a failing test -> red.
#   Block ac1  AC-1 coverage: a copy of the build (git archive of HEAD: build logic, libs/fixed-width, libs/test-support and the
#              one document the library's tests read, docs/analysis/data-layouts.md) in the
#              scratch folder. The workflow's own Gradle command (with --no-build-cache added: a clean runner has no build cache)
#              runs on it unchanged (passes), then all test classes but one are deleted IN THE COPY and it runs again: it must fail
#              in jacocoTestCoverageVerification with the rule's message.
#   Block wf   the negative controls of the static step: fourteen copies of the workflow in the scratch folder, each with ONE
#              switch of the list above; the check must report each by name, and an unchanged copy must have no problem. The
#              copies: continue-on-error or if: on a job or on a step (four), shell: bash removed, the pull_request trigger removed,
#              '|| true' on the Gradle line, on 'npm test' (four more), 'set +o pipefail' before the Gradle command (its one-line run:
#              becomes a run: | block, one line replaced by three), 'set +eo pipefail', 'set +ex' and 'set +o errexit' in the web-ui step
#              (three), a 'needs' on job compose-smoke that has 'if: false' (two lines added), and a 'needs' that reaches
#              build-and-check, which has continue-on-error, through compose-smoke (two lines added). The controls show that the
#              check reports these fourteen forms; they say nothing about other forms.
# The static step (Python and PyYAML) runs before the blocks, also with DEMO_ONLY, and prints the interpreter and the PyYAML
# version that read the workflow. The script prints PASS or FAIL per step and exits 1 when any step failed.
# Clean-up: the script itself writes only into the scratch folder and into web-ui/test-results/demo-ci-gates-<pid>/ (git-ignored; it
# holds the temporary tests and the Vitest caches of those runs: Vite's cacheDir and Vitest's fsModuleCachePath), and removes both on
# exit, also on error. The tools it starts keep files of their own outside these folders, which the script neither removes nor
# compares: a daemon log of each Gradle call in the Gradle user home (~/.gradle/daemon/<version>/), the two CLIs in npm's cache, and a
# bundle of the temporary configuration that Vite writes into web-ui/node_modules/.vite-temp/ and deletes after loading it.
# Its last steps compare before and after, for BOTH Vitest caches of the project, the number of entries for "demo-ci-gates-" paths
# (each count must not grow): in the results cache (web-ui/node_modules/.vite/vitest/*/results.json) the entries of its files, and in
# the module cache (web-ui/node_modules/.vitest-cache) the entry files whose content names "demo-ci-gates-" and the manifest keys with
# "demo-ci-gates-" in the path. It also compares the sha256 of the files it must never touch (modern/contracts, libs/fixed-width,
# build logic, web-ui scripts, the workflow file) and the real web-ui/test-results/vitest.json. Other files are not compared.
#
# Run: bash tools/checks/demo-ci-gates.sh   (from modern/; "bash" so that it works whatever the file mode of a checkout is)
#
# Also not shown: a red run on GitHub. That a non-zero exit turns a GitHub job red is GitHub's rule for shell: bash; it is not
# shown again here.
#
# Low-memory rules (docs/PROGRESS.md): one Gradle build at a time, always --no-daemon (it is in the workflow's command), and
# before each Gradle, Vitest and npx call the free memory is read; under 1024 MB (PEM_MIN_FREE_MB) the script waits 60 s and
# checks again, it never gives up for memory.
# Vitest on Windows is started from PowerShell with a lowercase drive path (from Git Bash it can fail with "Vitest failed to find
# the runner"); elsewhere it is started directly. Paths without spaces are assumed on the PowerShell route.
# Environment (all optional):
#   PEM_SCRATCH       folder for the scratch directory (default: the system temp folder)
#   PEM_GRADLE_LOCK   a lock folder; when set, the script creates it (with owner.txt) around each Gradle call and, when it
#                     exists, waits and retries every PEM_LOCK_RETRY seconds (default 20). It never removes a lock it does not own.
#   PEM_PYTHON        the Python for the static workflow step (needs PyYAML); default: the first of modern/kag/.venv, python3, python
#                     that can import yaml
#   PEM_MIN_FREE_MB   free-memory threshold, default 1024
#   DEMO_TIMEOUT      seconds allowed per Gradle call, default 1500; DEMO_NPX_TIMEOUT per npx call, default 900
#   DEMO_ONLY         run one block only: ac1, ac2, ac3 or wf (default: all blocks, then the clean-up check); the static step
#                     of the workflow always runs
#   DEMO_OPENAPI_FILE the OpenAPI file that gets the single fault of step (a), default risk-service.yaml
#   DEMO_KEEP_TESTS   the one test class kept in the copy of block ac1, default RoundTripTest.java
set -u
cd "$(dirname "$0")/../.." || exit 2
MODERN="$(pwd)"
REPO="$(cd .. && pwd)"
WORKFLOW="$REPO/.github/workflows/ci.yml"
[ -f "$WORKFLOW" ] || { echo "workflow file not found: $WORKFLOW" >&2; exit 2; }
WORK="$(mktemp -d "${PEM_SCRATCH:-${TMPDIR:-/tmp}}/demo-ci-gates.XXXXXX")" || exit 2
TMP_NAME="demo-ci-gates-$$"
TMP_WEB="$MODERN/web-ui/test-results/$TMP_NAME"   # git-ignored (web-ui/.gitignore: test-results/)
FAILS=0
WAITED_MEM=0
WAITED_LOCK=0
GRADLE_RUNS=0
HAVE_LOCK=0

native() { if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else printf '%s' "$1"; fi; }

release_lock() {
  [ "$HAVE_LOCK" = "1" ] || return 0
  rm -f "$PEM_GRADLE_LOCK/owner.txt" 2>/dev/null
  rmdir "$PEM_GRADLE_LOCK" 2>/dev/null
  HAVE_LOCK=0
  return 0
}

cleanup() {
  release_lock
  rm -rf "$TMP_WEB"
  local i
  for i in 1 2 3; do   # on Windows a just-finished Gradle may still hold a file for a moment
    rm -rf "$WORK" 2>/dev/null
    [ -d "$WORK" ] || break
    sleep 5
  done
  [ -d "$WORK" ] && echo "WARNING: the scratch folder could not be removed: $WORK" >&2
  return 0
}
trap cleanup EXIT
trap 'exit 130' INT TERM

skip_block() { [ -n "${DEMO_ONLY:-}" ] && [ "$DEMO_ONLY" != "$1" ]; }   # true when DEMO_ONLY names another block

free_mb() {
  if command -v powershell >/dev/null 2>&1; then
    powershell -NoProfile -Command "[int]((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory/1024)" 2>/dev/null | tr -d '\r'
  elif [ -r /proc/meminfo ]; then
    awk '/MemAvailable/ {print int($2/1024)}' /proc/meminfo
  fi
}

wait_memory() {
  local min="${PEM_MIN_FREE_MB:-1024}" free
  while :; do
    free="$(free_mb)"
    case "$free" in ''|*[!0-9]*) return 0 ;; esac   # cannot be read: go on
    [ "$free" -ge "$min" ] && return 0
    echo "  waiting 60 s: free memory ${free} MB is under ${min} MB"
    sleep 60
    WAITED_MEM=$((WAITED_MEM + 60))
  done
}

acquire_lock() {
  [ -n "${PEM_GRADLE_LOCK:-}" ] || return 0
  local n=0 retry="${PEM_LOCK_RETRY:-20}" owner
  while ! mkdir "$PEM_GRADLE_LOCK" 2>/dev/null; do
    if [ $((n % 3)) -eq 0 ]; then   # one line per minute is enough
      owner="$(cat "$PEM_GRADLE_LOCK/owner.txt" 2>/dev/null)"
      echo "  gradle lock exists (owner: ${owner:-unknown}); retrying every ${retry} s"
    fi
    sleep "$retry"
    n=$((n + 1))
    WAITED_LOCK=$((WAITED_LOCK + retry))
  done
  HAVE_LOCK=1
  echo "devops-engineer demo-ci-gates.sh $(date '+%F %T')" > "$PEM_GRADLE_LOCK/owner.txt"
}

pass() { echo "PASS $1"; }
fail() { echo "FAIL $1"; FAILS=$((FAILS + 1)); [ -n "${2:-}" ] && tail -n 25 "$2" | sed 's/^/    | /'; }
excerpt() { grep -m "${3:-4}" -F -- "$2" "$1" | cut -c1-230 | sed 's/^/    | /'; }   # excerpt <log> <text> [max lines]
excerpt_re() { grep -m "${3:-4}" -E -- "$2" "$1" | cut -c1-230 | sed 's/^/    | /'; }   # excerpt_re <log> <regex> [max lines]

# strip_log <log>: writes <log>.txt without colour codes, carriage returns and backslashes (Windows paths), for matching
strip_log() { sed -e 's/\x1b\[[0-9;]*m//g' -e 's/\r$//' -e 's#\\#/#g' "$1" > "$1.txt"; }

# err_blocks <stripped Redocly log> <missing name>: one line "file:line" for every block of the output that is an ERROR of the rule
# no-unresolved-refs and shows the missing name. Redocly prints warnings in the same "[n] file:line:col" format, and a broken $ref
# also leaves its schema "never used": a warning in the same file, which must not count as "the fault is named".
err_blocks() {
  awk -v missing="$2" '
    function flush() { if (blk && hasref && haserr) print loc; blk = 0 }
    /^\[[0-9]+\] contracts\/openapi\/[^ ]+:[0-9]+:[0-9]+/ { flush(); blk = 1; hasref = 0; haserr = 0; loc = $2; sub(/:[0-9]+$/, "", loc); next }
    /^validating / || / validated in / || /Validation failed/ { flush(); next }
    blk && index($0, missing) { hasref = 1 }
    blk && /^Error was generated by the no-unresolved-refs rule\./ { haserr = 1 }
    END { flush() }
  ' "$1"
}

# ---- the workflow: the source of the commands, and a static check of a stated list of switches (see the header)
# find_python: a Python that can import yaml (PEM_PYTHON, the kag virtual environment, python3, python)
find_python() {
  local c
  for c in "${PEM_PYTHON:-}" "$MODERN/kag/.venv/Scripts/python.exe" "$MODERN/kag/.venv/bin/python" python3 python; do
    [ -n "$c" ] || continue
    command -v "$c" >/dev/null 2>&1 || [ -x "$c" ] || continue
    if "$c" -c 'import yaml' >/dev/null 2>&1; then printf '%s' "$c"; return 0; fi
  done
  return 1
}

WF_GRADLE=""; WF_REDOCLY=""; WF_ASYNCAPI=""; WF_NPMTEST=""

# write_wiring_py: the static look at a workflow file (python, PyYAML), written to the scratch folder
write_wiring_py() {
  cat > "$WORK/wf_wiring.py" <<'PY'
# Static look at the workflow: prints "env", "ok", "bad", "note" and "cmd" lines (tab separated) for the shell script to read.
import platform
import re
import sys

import yaml

with open(sys.argv[1], encoding="utf-8") as fh:
    wf = yaml.safe_load(fh)


def emit(kind, *parts):
    print("\t".join([kind] + [str(p) for p in parts]))


# which interpreter and which PyYAML read the workflow (the shell script prints it)
emit("env", sys.executable, "Python %s, PyYAML %s" % (platform.python_version(), yaml.__version__))


def run_default(node, key):
    return ((((node or {}).get("defaults") or {}).get("run")) or {}).get(key)


def norm(path):
    path = (path or ".").replace("\\", "/")
    while path.startswith("./"):
        path = path[2:]
    return path.rstrip("/") or "."


# the triggers (PyYAML reads the key "on" as the boolean True: YAML 1.1)
on = wf.get("on", wf.get(True))
if on is None:
    names = []
elif isinstance(on, dict):
    names = list(on)
elif isinstance(on, str):
    names = [on]
else:
    names = list(on)
missing = [t for t in ("push", "pull_request") if t not in names]
if missing:
    emit("bad", "the workflow does not run on %s (on: has %s)" % (" and ".join(missing), ", ".join(map(str, names)) or "nothing"))
else:
    emit("ok", "triggers: the workflow runs on push and on pull_request")
if isinstance(on, dict):
    for t in ("push", "pull_request"):
        filters = on.get(t)
        if isinstance(filters, dict) and filters:
            emit("note", "trigger %s has filters (%s): a push or pull request outside them does not run the gates" % (t, ", ".join(filters)))

# (job, regex of the command line inside a run: block, key for the shell script, working directory the script assumes)
GATES = [
    ("build-and-check", r"^\./gradlew .*checkAll", "gradle", "modern"),
    ("contract-lint", r"^npx --yes @redocly/cli@\S+ lint ", "redocly", "modern"),
    ("contract-lint", r"^npx --yes @asyncapi/cli@\S+ validate ", "asyncapi", "modern"),
    ("web-ui", r"^npm test$", "npmtest", "modern/web-ui"),
]
jobs = wf.get("jobs") or {}


def needs_of(job):
    needs = (job or {}).get("needs") if isinstance(job, dict) else None
    if needs is None:
        return []
    return [str(needs)] if isinstance(needs, str) else [str(n) for n in needs]


def needs_problems(job_id):
    """Every job that job_id needs, directly or through the needs of those jobs, that has an if: or a continue-on-error
    other than a literal false: GitHub skips a job whose needed job was skipped, and a skipped job is not red."""
    found = []
    seen = set()

    def walk(jid, chain):
        for dep in needs_of(jobs.get(jid)):
            if dep in seen:
                continue
            seen.add(dep)
            path = " -> ".join(chain + [dep])
            dep_job = jobs.get(dep)
            if not isinstance(dep_job, dict):
                found.append("job %s needs %s, which is not a job of the workflow" % (job_id, path))
                continue
            if "if" in dep_job:
                found.append("job %s needs %s, which has an if: condition (%s): GitHub skips %s when a job it needs is skipped, and a skipped job is not red" % (job_id, path, dep_job["if"], job_id))
            if dep_job.get("continue-on-error", False) is not False:
                found.append("job %s needs %s, which has continue-on-error: %s" % (job_id, path, dep_job["continue-on-error"]))
            walk(dep, chain + [dep])

    walk(job_id, [])
    return found


for job_id, pattern, key, workdir in GATES:
    job = jobs.get(job_id)
    if not isinstance(job, dict):
        emit("bad", "%s: the workflow has no job %s" % (key, job_id))
        continue
    found = None
    for step in job.get("steps") or []:
        run = step.get("run")
        if not isinstance(run, str):
            continue
        for line in run.splitlines():
            if re.match(pattern, line.strip()):
                found = (step, run, line.strip())
                break
        if found:
            break
    if not found:
        emit("bad", "%s: job %s has no step with a run: line matching %s" % (key, job_id, pattern))
        continue
    step, run, line = found
    label = step.get("name") or line
    problems = []
    if "if" in job:
        problems.append("job %s has an if: condition (%s)" % (job_id, job["if"]))
    if job.get("continue-on-error", False) is not False:
        problems.append("job %s has continue-on-error: %s" % (job_id, job["continue-on-error"]))
    problems.extend(needs_problems(job_id))
    if "if" in step:
        problems.append("step '%s' has an if: condition (%s)" % (label, step["if"]))
    if step.get("continue-on-error", False) is not False:
        problems.append("step '%s' has continue-on-error: %s" % (label, step["continue-on-error"]))
    shell = step.get("shell") or run_default(job, "shell") or run_default(wf, "shell")
    if shell != "bash":
        problems.append("step '%s' runs with shell %s, not bash: GitHub's default is bash -e without pipefail, and this script runs the command with bash -eo pipefail" % (label, shell or "none set"))
    wd = norm(step.get("working-directory") or run_default(job, "working-directory") or run_default(wf, "working-directory"))
    if wd != workdir:
        problems.append("step '%s' runs in %s; this script assumes %s" % (label, wd, workdir))
    for text in run.splitlines():
        text = text.strip()
        if text.startswith("#"):
            continue
        if "||" in text:
            problems.append("step '%s' has a line that can swallow a failure: %s" % (label, text))
        if re.search(r"\bset\s+\+", text):   # set +e, set +o pipefail, set +eo pipefail, set +ex, set +o errexit, ...
            problems.append("step '%s' has a line that switches a shell option off: %s" % (label, text))
    for p in problems:
        emit("bad", "%s: %s" % (key, p))
    if not problems:
        emit("ok", "%s: job %s, step '%s': no if:, no continue-on-error, shell bash, working directory %s, no needed job with if: or continue-on-error, no '||' and no 'set +' line" % (key, job_id, label, wd))
    emit("cmd", key, line)
PY
}

# wiring_raw <workflow file>: the lines (env, ok, bad, note, cmd; tab separated) of the static look; returns 1 when there is no Python
wiring_raw() {
  local py
  py="$(find_python)" || return 1
  [ -f "$WORK/wf_wiring.py" ] || write_wiring_py
  "$py" -I "$(native "$WORK/wf_wiring.py")" "$(native "$1")" 2>&1 | tr -d '\r'
}

# wiring_check: reads the workflow, sets WF_GRADLE, WF_REDOCLY, WF_ASYNCAPI (the command lines of the jobs that own them) and
# WF_NPMTEST, and prints one PASS, or one FAIL with every violation, for the question "is one of the switches on the checked list
# (see the header) set in the workflow?". It prints the interpreter and the PyYAML version that did the reading.
wiring_check() {
  local out kind a b line pyinfo="not printed"
  local oks=() bads=() notes=()
  if ! out="$(wiring_raw "$WORKFLOW")"; then
    fail "Workflow wiring: no Python with PyYAML found (set PEM_PYTHON, or run: python -m pip install pyyaml); the commands cannot be read from the workflow and the switches cannot be checked"
    return 0
  fi
  while IFS=$'\t' read -r kind a b; do
    case "$kind" in
      '') ;;
      env) pyinfo="$a ($b)" ;;
      ok) oks+=("$a") ;;
      note) notes+=("$a") ;;
      bad) bads+=("$a") ;;
      cmd)
        case "$a" in
          gradle) WF_GRADLE="$b" ;;
          redocly) WF_REDOCLY="$b" ;;
          asyncapi) WF_ASYNCAPI="$b" ;;
          npmtest) WF_NPMTEST="$b" ;;
        esac ;;
      *) bads+=("unexpected output of the wiring check: $kind $a $b") ;;
    esac
  done <<< "$out"
  if [ ${#bads[@]} -eq 0 ] && [ ${#oks[@]} -ge 5 ]; then
    pass "Workflow wiring: none of the switches on the checked list (script header, block wf) is set in ${WORKFLOW#"$REPO"/}: ${#oks[@]} checks of the triggers, the three gate jobs and the four steps that carry the commands; this is not a proof that no gate can be switched off"
    echo "    | read with: $pyinfo"
    for line in ${oks[@]+"${oks[@]}"} ${notes[@]+"${notes[@]}"}; do echo "    | $line"; done
  else
    fail "Workflow wiring: ${WORKFLOW#"$REPO"/} has a switch of the checked list set, or a command is missing or not what this script assumes (${#bads[@]} problem(s); the blocks below still run with the commands that were found, but their PASS does not hold for the workflow)"
    echo "    | read with: $pyinfo"
    for line in ${bads[@]+"${bads[@]}"} ${notes[@]+"${notes[@]}"}; do echo "    | $line"; done
    [ ${#bads[@]} -eq 0 ] && echo "    | only ${#oks[@]} of 5 expected checks passed"
  fi
  return 0
}

# run_in <dir> <log> <command>: the command as the workflow runs it (bash -eo pipefail), with a timeout; returns its exit code
run_in() {
  local dir="$1" log="$2" cmd="$3" rc
  wait_memory
  (cd "$dir" && timeout "${DEMO_NPX_TIMEOUT:-900}" bash -eo pipefail -c "$cmd") > "$log" 2>&1
  rc=$?
  strip_log "$log"
  return $rc
}

# run_gradle_cmd <dir> <log> <runner-temp> <command>: memory check, lock, ONE Gradle build as the workflow runs it, unlock
run_gradle_cmd() {
  local dir="$1" log="$2" rt="$3" cmd="$4" rc
  mkdir -p "$rt"
  wait_memory
  acquire_lock
  wait_memory
  GRADLE_RUNS=$((GRADLE_RUNS + 1))
  (cd "$dir" && RUNNER_TEMP="$(native "$rt")" timeout "${DEMO_TIMEOUT:-1500}" bash -eo pipefail -c "$cmd") > "$log" 2>&1
  rc=$?
  release_lock
  strip_log "$log"
  return $rc
}

# snapshot <outfile>: sha256 of the files this script must never change (it only ever works on copies)
snapshot() {
  (cd "$MODERN" && {
    find contracts libs/fixed-width buildSrc gradle web-ui/scripts -type f -not -path 'libs/fixed-width/build/*' -not -path 'buildSrc/build/*' -not -path 'buildSrc/.gradle/*' -not -path 'buildSrc/.kotlin/*'
    ls web-ui/vite.config.ts web-ui/package.json build.gradle.kts settings.gradle.kts gradle.properties ../.github/workflows/ci.yml
  } | LC_ALL=C sort | xargs sha256sum) > "$1" 2>&1
}

# break_ref <file> <kind> <missing name>: the target of the FIRST $ref of that kind (schemas | messages) becomes a name that does
# not exist; prints the number of the changed line, returns 1 when the file has no such $ref
break_ref() {
  local file="$1" kind="$2" missing="$3" ln
  ln="$(grep -n -m1 "\$ref: '#/components/$kind/" "$file" | cut -d: -f1)"
  [ -n "$ln" ] || return 1
  sed -i "${ln}s|\(\$ref: '#/components/$kind/\)[A-Za-z0-9_.-]*'|\1$missing'|" "$file"
  printf '%s' "$ln"
}

# changed_lines <original> <changed>: how many lines differ (a deliberate fault must be exactly one line)
changed_lines() { diff "$1" "$2" | grep -c '^>'; }

snapshot "$WORK/before.sha256"
ls -A "$MODERN/web-ui/test-results" 2>/dev/null | LC_ALL=C sort > "$WORK/before-test-results.txt"
report_hash() { if [ -f "$MODERN/web-ui/test-results/vitest.json" ]; then sha256sum < "$MODERN/web-ui/test-results/vitest.json"; else echo absent; fi; }   # the real report of npm test
report_hash > "$WORK/before-report.txt"
# project_cache_entries: how many entries for a path with "demo-ci-gates-" (the temporary test files of this script) the project's two
# Vitest caches hold, as three numbers "R F K": R = entries in the results cache (web-ui/node_modules/.vite/vitest/*/results.json),
# F = entry files of the module cache (web-ui/node_modules/.vitest-cache; the files named by 40 hex digits) whose content names
# "demo-ci-gates-", K = keys of the module cache's manifests (_manifest-*.json) with "demo-ci-gates-" in the path. "?" when it cannot
# be read (three tries: another lane's Vitest run may be writing the files)
project_cache_entries() {
  local n i
  for i in 1 2 3; do
    n="$(cd "$MODERN/web-ui" && node -e '
const fs = require("fs");
const T = "demo-ci-gates-";
let r = 0, f = 0, k = 0;
const base = "node_modules/.vite/vitest";
if (fs.existsSync(base)) {
  for (const d of fs.readdirSync(base)) {
    const p = base + "/" + d + "/results.json";
    if (!fs.existsSync(p)) continue;
    const j = JSON.parse(fs.readFileSync(p, "utf8"));
    for (const pr of Object.values(j.projects || {})) r += Object.keys(pr.files || {}).filter((x) => x.includes(T)).length;
  }
}
const mod = "node_modules/.vitest-cache";
if (fs.existsSync(mod)) {
  for (const name of fs.readdirSync(mod)) {
    if (/^[0-9a-f]{40}$/.test(name)) {
      try { if (fs.readFileSync(mod + "/" + name, "latin1").includes(T)) f++; } catch (e) { if (e.code !== "ENOENT") throw e; }
    } else if (/^_manifest-.*\.json$/.test(name)) {
      const m = JSON.parse(fs.readFileSync(mod + "/" + name, "utf8"));
      for (const sec of ["modules", "externalized"]) for (const o of Object.values(m[sec] || {})) k += Object.keys(o).filter((x) => x.includes(T)).length;
    }
  }
}
console.log(r + " " + f + " " + k);
' 2>/dev/null | tr -d '\r')"
    [[ "$n" =~ ^[0-9]+\ [0-9]+\ [0-9]+$ ]] && { echo "$n"; return 0; }
    sleep 2
  done
  echo "?"
}
CACHE_BEFORE="$(project_cache_entries)"
TMP_CACHE_TEXT="block ac3 did not run in this run: no temporary Vitest run"

echo "== MAM-39 demonstration that deliberate faults turn the CI gates red ($(date '+%F %T'); scratch: $WORK) =="
echo "   workflow: $WORKFLOW"
echo "   head: $(git -C "$REPO" rev-parse --short HEAD 2>/dev/null)"
echo "   demo-ci-gates- entries in the project's Vitest caches before the run (results cache; module cache files; module cache manifest keys): $CACHE_BEFORE"

# ---------------------------------------------------------------- the workflow itself (always, also with DEMO_ONLY)
echo "-- Workflow: the commands come from the job that owns them, and none of the checked switches is set"
wiring_check

# ---------------------------------------------------------------- the static check itself: it must report each switch of its list
if ! skip_block wf; then
echo "-- Workflow wiring: the static check itself reports each switch of its list (copies of the workflow in the scratch folder)"
if ! wiring_raw "$WORKFLOW" > /dev/null; then
  fail "Workflow wiring (negative controls) setup: no Python with PyYAML found"
else
  yml_after() { awk -v a="$2" -v t="$3" '{ print } $0 == a { print t }' "$1"; }   # yml_after <file> <line> <new line>: a copy with the new line after the line
  # wf_fault <label> <text the report must contain> <copy> [max lines added] [max lines removed]: the copy differs from the workflow in
  # one switch (one line; the two optional numbers allow a switch that needs more lines, and the label says so), and the check
  # reports it by name
  wf_fault() {
    local label="$1" want="$2" copy="$3" max_add="${4:-1}" max_rem="${5:-1}" added removed bad
    added="$(diff "$WORKFLOW" "$copy" | grep -c '^>')"; removed="$(diff "$WORKFLOW" "$copy" | grep -c '^<')"
    bad="$(wiring_raw "$copy" | grep '^bad')"
    if [ "$added" -le "$max_add" ] && [ "$removed" -le "$max_rem" ] && [ $((added + removed)) -ge 1 ] && printf '%s\n' "$bad" | grep -qF "$want"; then
      pass "wiring fault: $label: the static check reports it"
      printf '%s\n' "$bad" | grep -F -m1 "$want" | cut -f2 | cut -c1-230 | sed 's/^/    | /'
    else
      fail "wiring fault: $label (lines added $added, removed $removed; expected the report to contain '$want'; report: $(printf '%s' "$bad" | tr '\n\t' '  ' | cut -c1-300))"
    fi
  }
  cp "$WORKFLOW" "$WORK/wf0.yml"
  nbad="$(wiring_raw "$WORK/wf0.yml" | grep -c '^bad')"
  if [ "$nbad" = "0" ] && [ -z "$(diff "$WORKFLOW" "$WORK/wf0.yml")" ]; then
    pass "wiring control: an unchanged copy of the workflow has no problem reported"
  else
    fail "wiring control: an unchanged copy of the workflow has $nbad problem(s) reported"
  fi
  yml_after "$WORKFLOW" "    name: Gradle build and full check" "    continue-on-error: true" > "$WORK/wf1.yml"
  wf_fault "job build-and-check gets 'continue-on-error: true'" "job build-and-check has continue-on-error" "$WORK/wf1.yml"
  yml_after "$WORKFLOW" "      - name: Lint OpenAPI" "        if: false" > "$WORK/wf2.yml"
  wf_fault "step 'Lint OpenAPI' gets 'if: false'" "step 'Lint OpenAPI' has an if: condition" "$WORK/wf2.yml"
  yml_after "$WORKFLOW" "      - name: Validate AsyncAPI" "        continue-on-error: true" > "$WORK/wf3.yml"
  wf_fault "step 'Validate AsyncAPI' gets 'continue-on-error: true'" "step 'Validate AsyncAPI' has continue-on-error" "$WORK/wf3.yml"
  yml_after "$WORKFLOW" "    name: Web UI tests" "    if: false" > "$WORK/wf4.yml"
  wf_fault "job web-ui gets 'if: false'" "job web-ui has an if: condition" "$WORK/wf4.yml"
  grep -vxF "    shell: bash" "$WORKFLOW" > "$WORK/wf5.yml"
  wf_fault "the 'shell: bash' default is removed (a pipe to tee would hide the exit code of Gradle)" "not bash" "$WORK/wf5.yml"
  grep -vxF "  pull_request:" "$WORKFLOW" > "$WORK/wf6.yml"
  wf_fault "the pull_request trigger is removed" "does not run on pull_request" "$WORK/wf6.yml"
  sed 's/gradle\.log"$/gradle.log" || true/' "$WORKFLOW" > "$WORK/wf7.yml"
  wf_fault "the Gradle command gets '|| true'" "can swallow a failure" "$WORK/wf7.yml"
  sed 's/^\( *\)npm test$/\1npm test || true/' "$WORKFLOW" > "$WORK/wf8.yml"
  wf_fault "'npm test' becomes 'npm test || true'" "job web-ui has no step with a run: line matching" "$WORK/wf8.yml"
  # a line that switches a shell option off in a gate step. The Gradle step is a one-line "run:": to put a line before the command it
  # becomes a "run: |" block, one line replaced by three (the command line itself is unchanged). This is the failure of run 3 (pipefail off)
  awk '{ if ($0 ~ /^        run: \.\/gradlew /) { print "        run: |"; print "          set +o pipefail"; print "          " substr($0, 14) } else print }' "$WORKFLOW" > "$WORK/wf9.yml"
  wf_fault "a line 'set +o pipefail' before the Gradle command (one line replaced by three; the exit code of tee would hide Gradle's)" "gradle: step 'Build and check' has a line that switches a shell option off: set +o pipefail" "$WORK/wf9.yml" 3 1
  # the spellings that a pattern for "set +e" alone does not see, one added line each in the 'Install and test' block of job web-ui
  for form in 'set +eo pipefail' 'set +ex' 'set +o errexit'; do
    yml_after "$WORKFLOW" "          npm ci --no-audit --no-fund" "          $form" > "$WORK/wf-set.yml"
    wf_fault "a line '$form' in the gate step 'Install and test'" "npmtest: step 'Install and test' has a line that switches a shell option off: $form" "$WORK/wf-set.yml"
  done
  # a needs on a job that is skipped. Job compose-smoke exists already (it needs build-and-check): it gets 'if: false' and job web-ui
  # needs it (two lines added: the if and the needs)
  yml_after "$WORKFLOW" "    name: Compose smoke test" "    if: false" > "$WORK/wf10a.yml"
  yml_after "$WORK/wf10a.yml" "    name: Web UI tests" "    needs: compose-smoke" > "$WORK/wf10.yml"
  wf_fault "job web-ui needs compose-smoke (two lines added) and compose-smoke has 'if: false': GitHub skips web-ui, a skipped job is not red" "npmtest: job web-ui needs compose-smoke, which has an if: condition" "$WORK/wf10.yml" 2 0
  # the same through the needs of the needed job: web-ui needs compose-smoke, which needs build-and-check, which gets continue-on-error
  yml_after "$WORKFLOW" "    name: Gradle build and full check" "    continue-on-error: true" > "$WORK/wf11a.yml"
  yml_after "$WORK/wf11a.yml" "    name: Web UI tests" "    needs: [compose-smoke]" > "$WORK/wf11.yml"
  wf_fault "job web-ui needs [compose-smoke] (two lines added) and build-and-check, which compose-smoke needs, has 'continue-on-error: true'" "npmtest: job web-ui needs compose-smoke -> build-and-check, which has continue-on-error" "$WORK/wf11.yml" 2 0
fi
fi

# ---------------------------------------------------------------- AC-2: contract lint
if ! skip_block ac2; then
echo "-- AC-2: a deliberate error in a contract file fails the contract lint"
REDOCLY_CMD="$WF_REDOCLY"
ASYNCAPI_CMD="$WF_ASYNCAPI"
if [ -z "$REDOCLY_CMD" ] || [ -z "$ASYNCAPI_CMD" ]; then
  fail "AC-2 setup: the lint commands were not found in the workflow (redocly: '${REDOCLY_CMD}', asyncapi: '${ASYNCAPI_CMD}')"
else
  echo "   workflow command 1: $REDOCLY_CMD"
  echo "   workflow command 2: $ASYNCAPI_CMD"
  C0="$WORK/contracts-clean"; mkdir -p "$C0"; cp -r "$MODERN/contracts" "$C0/contracts"
  n_files="$(ls "$C0"/contracts/openapi/*.yaml | wc -l | tr -d ' ')"
  F="${DEMO_OPENAPI_FILE:-risk-service.yaml}"
  [ -f "$C0/contracts/openapi/$F" ] || F="$(basename "$(ls "$C0"/contracts/openapi/*.yaml | head -n 1)")"

  # negative control: the clean copy passes
  run_in "$C0" "$WORK/c1.log" "$REDOCLY_CMD"; rc=$?
  validated="$(grep -c 'validated in' "$WORK/c1.log.txt")"
  if [ $rc -eq 0 ] && [ "$validated" = "$n_files" ] && ! grep -q "Can't resolve" "$WORK/c1.log.txt"; then
    pass "AC-2 control: the clean copy of contracts/ passes the Redocly lint (exit 0, $validated of $n_files OpenAPI files validated)"
    excerpt "$WORK/c1.log.txt" 'API descriptions are valid' 1
  else
    fail "AC-2 control: Redocly lint on the clean copy (exit $rc; validated $validated of $n_files)" "$WORK/c1.log.txt"
  fi
  run_in "$C0" "$WORK/c2.log" "$ASYNCAPI_CMD"; rc=$?
  if [ $rc -eq 0 ] && grep -q 'is valid' "$WORK/c2.log.txt"; then
    pass "AC-2 control: the clean copy of events.yaml passes asyncapi validate (exit 0)"
    excerpt "$WORK/c2.log.txt" 'is valid' 1
  else
    fail "AC-2 control: asyncapi validate on the clean copy (exit $rc)" "$WORK/c2.log.txt"
  fi

  # (a) one deliberate error in one OpenAPI file
  CA="$WORK/contracts-a"; mkdir -p "$CA"; cp -r "$C0/contracts" "$CA/contracts"
  ln="$(break_ref "$CA/contracts/openapi/$F" schemas DemoMissingSchema)"
  if [ -z "$ln" ] || [ "$(changed_lines "$C0/contracts/openapi/$F" "$CA/contracts/openapi/$F")" != "1" ]; then
    fail "AC-2 (a) setup: could not put exactly one deliberate error into the copy of $F"
  else
    run_in "$CA" "$WORK/ca.log" "$REDOCLY_CMD"; rc=$?
    errs="$(err_blocks "$WORK/ca.log.txt" DemoMissingSchema)"
    if [ $rc -ne 0 ] && [ "$errs" = "contracts/openapi/$F:$ln" ] && grep -qE 'Validation failed with 1 error([^s]|$)' "$WORK/ca.log.txt"; then
      pass "AC-2 (a) one \$ref to a schema that does not exist in the copy of $F (line $ln): the Redocly lint exits $rc, and its one error (rule no-unresolved-refs, in the same block as DemoMissingSchema) is at $F:$ln"
      excerpt "$WORK/ca.log.txt" "contracts/openapi/$F:$ln" 1
      excerpt "$WORK/ca.log.txt" "Can't resolve" 1
      excerpt "$WORK/ca.log.txt" 'DemoMissingSchema' 1
      excerpt "$WORK/ca.log.txt" 'no-unresolved-refs' 1
      excerpt_re "$WORK/ca.log.txt" 'Validation failed' 1
    else
      fail "AC-2 (a) the lint of the copy with a fault in $F (exit $rc; expected non-zero, exactly one no-unresolved-refs error block with DemoMissingSchema at $F:$ln, and 'Validation failed with 1 error'; error blocks seen: '$(printf '%s' "$errs" | tr '\n' ' ')')" "$WORK/ca.log.txt"
    fi
  fi

  # (a2) the same fault in each of the OpenAPI files: the lint reaches every one of them
  CB="$WORK/contracts-all"; mkdir -p "$CB"; cp -r "$C0/contracts" "$CB/contracts"
  broken=0
  for f in "$CB"/contracts/openapi/*.yaml; do
    break_ref "$f" schemas DemoMissingSchema >/dev/null && broken=$((broken + 1))
  done
  run_in "$CB" "$WORK/cb.log" "$REDOCLY_CMD"; rc=$?
  # a file counts as named only when an ERROR block of rule no-unresolved-refs with the missing name is in it (not a warning), and the
  # summary line must count exactly one error per file
  errs="$(err_blocks "$WORK/cb.log.txt" DemoMissingSchema)"
  named=0
  for f in "$CB"/contracts/openapi/*.yaml; do
    printf '%s\n' "$errs" | grep -q "^contracts/openapi/$(basename "$f"):[0-9]*$" && named=$((named + 1))
  done
  n_err_blocks="$(printf '%s\n' "$errs" | grep -c .)"
  if [ $rc -ne 0 ] && [ "$broken" = "$n_files" ] && [ "$named" = "$n_files" ] && [ "$n_err_blocks" = "$n_files" ] \
     && grep -qE "Validation failed with $n_files errors?([^a-z]|\$)" "$WORK/cb.log.txt"; then
    pass "AC-2 (a2) the same fault in each of the $n_files OpenAPI files: the lint exits $rc, $named of $n_files files have a no-unresolved-refs error block with DemoMissingSchema, and the summary counts $n_files errors"
    printf '%s\n' "$errs" | sed 's/^/    | /'
    excerpt_re "$WORK/cb.log.txt" 'Validation failed' 1
  else
    fail "AC-2 (a2) a fault in every OpenAPI file (exit $rc; faults written $broken of $n_files; files with an error block $named; error blocks $n_err_blocks)" "$WORK/cb.log.txt"
  fi

  # (b) one deliberate error in events.yaml
  CE="$WORK/contracts-events"; mkdir -p "$CE"; cp -r "$C0/contracts" "$CE/contracts"
  ln="$(break_ref "$CE/contracts/asyncapi/events.yaml" messages DemoMissingMessage)"
  if [ -z "$ln" ] || [ "$(changed_lines "$C0/contracts/asyncapi/events.yaml" "$CE/contracts/asyncapi/events.yaml")" != "1" ]; then
    fail "AC-2 (b) setup: could not put exactly one deliberate error into the copy of events.yaml"
  else
    run_in "$CE" "$WORK/ce.log" "$ASYNCAPI_CMD"; rc=$?
    # one line must carry the severity, the rule and the missing name together (a warning plus an unrelated error would not do)
    if [ $rc -ne 0 ] && grep -qF 'contracts/asyncapi/events.yaml' "$WORK/ce.log.txt" \
       && grep -qE '[0-9]+:[0-9]+[[:space:]]+error[[:space:]]+invalid-ref[[:space:]].*DemoMissingMessage' "$WORK/ce.log.txt"; then
      pass "AC-2 (b) one \$ref to a message that does not exist in the copy of events.yaml (line $ln): asyncapi validate exits $rc and names the file and, on one line, the error, the rule invalid-ref and the missing message"
      excerpt "$WORK/ce.log.txt" 'governance issues' 1
      excerpt_re "$WORK/ce.log.txt" '^contracts/asyncapi/events.yaml$' 1
      excerpt "$WORK/ce.log.txt" 'DemoMissingMessage' 1
    else
      fail "AC-2 (b) asyncapi validate of the copy with a fault (exit $rc; expected non-zero, the file name, and one line '<line>:<col> error invalid-ref ... DemoMissingMessage')" "$WORK/ce.log.txt"
    fi
  fi
fi
fi

# ---------------------------------------------------------------- AC-3: web UI, a skipped test
if ! skip_block ac3; then
echo "-- AC-3: a skipped or failing web UI test fails the step"
WEB="$MODERN/web-ui"
TEST_SCRIPT="$(cd "$WEB" && node -p "JSON.parse(require('fs').readFileSync('package.json','utf8')).scripts.test" 2>/dev/null | tr -d '\r')"
VITEST_PART="${TEST_SCRIPT%% && *}"
SKIP_PART="${TEST_SCRIPT#* && }"
if [ -z "$WF_NPMTEST" ]; then
  fail "AC-3 wiring: job web-ui of the workflow has no step that runs 'npm test' (see the workflow check above)"
elif [ "$VITEST_PART" = "$TEST_SCRIPT" ] || [ "${VITEST_PART#vitest run }" = "$VITEST_PART" ] || [ "${SKIP_PART#node scripts/no-skipped.mjs }" = "$SKIP_PART" ] \
     || [ "${SKIP_PART#*&&}" != "$SKIP_PART" ] || [ "${TEST_SCRIPT#*||}" != "$TEST_SCRIPT" ] || [ "${TEST_SCRIPT#*;}" != "$TEST_SCRIPT" ]; then
  # exactly "A && B": this script computes the exit code of the chain itself, with the semantics of &&
  fail "AC-3 wiring: the test script of package.json is not exactly 'vitest run ... && node scripts/no-skipped.mjs <report>' (it is: '$TEST_SCRIPT')"
elif [ ! -d "$WEB/node_modules/vitest" ]; then
  fail "AC-3 setup: web-ui/node_modules has no vitest (run: cd web-ui && npm ci --no-audit --no-fund)"
else
  pass "AC-3 wiring: the workflow's web-ui job runs '$WF_NPMTEST' and the test script is: $TEST_SCRIPT"

  # run_vitest <log> <arguments of vitest>: from PowerShell with a lowercase drive path on Windows, directly elsewhere
  run_vitest() {
    local log="$1" win
    shift
    wait_memory
    if command -v powershell >/dev/null 2>&1 && command -v cygpath >/dev/null 2>&1; then
      win="$(cygpath -w "$WEB")"
      win="$(printf '%s' "${win:0:1}" | tr 'A-Z' 'a-z')${win:1}"
      cat > "$WORK/run-vitest.ps1" <<'PS'
param([string]$Dir, [string]$VitestArgs)
Set-Location $Dir
Invoke-Expression ".\node_modules\.bin\vitest.cmd $VitestArgs"
exit $LASTEXITCODE
PS
      timeout "${DEMO_TIMEOUT:-1500}" powershell -NoProfile -ExecutionPolicy Bypass -File "$(cygpath -w "$WORK/run-vitest.ps1")" -Dir "$win" -VitestArgs "$*" > "$log" 2>&1
    else
      (cd "$WEB" && timeout "${DEMO_TIMEOUT:-1500}" ./node_modules/.bin/vitest "$@") > "$log" 2>&1
    fi
    local rc=$?
    strip_log "$log"
    return $rc
  }

  # the temporary tests and the project's Vitest configuration with only the list of test files narrowed to them
  mkdir -p "$TMP_WEB"
  cat > "$TMP_WEB/vitest.demo.config.ts" <<TS
// MAM-39 demonstration only (tools/checks/demo-ci-gates.sh): the project's own Vitest configuration with three changes: the test file
// list is narrowed to this temporary, git-ignored folder; Vite's cacheDir (where Vitest keeps its results cache, under
// <cacheDir>/vitest) and Vitest's fsModuleCachePath (where it keeps its transformed-module cache; its default is
// node_modules/.vitest-cache, which cacheDir does not move) are folders inside this same folder, so that the entries of these
// temporary files never reach the project's caches in node_modules/.vite and node_modules/.vitest-cache. The folder is removed when
// the script ends.
import { fileURLToPath } from 'node:url';
import base from '../../vite.config.ts';

export default {
  ...base,
  cacheDir: fileURLToPath(new URL('./.vite', import.meta.url)),
  test: {
    ...base.test,
    include: ['test-results/$TMP_NAME/*.test.ts'],
    fsModuleCachePath: fileURLToPath(new URL('./.vitest-cache', import.meta.url)),
  },
};
TS
  cat > "$TMP_WEB/demo-skip.test.ts" <<'TS'
import { describe, expect, it } from 'vitest';

// MAM-39 demonstration only: written and removed by tools/checks/demo-ci-gates.sh.
describe('MAM-39 demo', () => {
  it('this test passes', () => {
    expect(1 + 1).toBe(2);
  });
  it.skip('this test is skipped on purpose', () => {
    expect(1).toBe(1);
  });
});
TS
  sed 's/it\.skip(/it(/' "$TMP_WEB/demo-skip.test.ts" > "$TMP_WEB/demo-clean.test.ts"
  cat > "$TMP_WEB/demo-other.test.ts" <<'TS'
import { describe, expect, it } from 'vitest';

// MAM-39 demonstration only: written and removed by tools/checks/demo-ci-gates.sh.
describe.skip('MAM-39 demo skipped group', () => {
  it('inside a skipped group', () => {
    expect(1).toBe(1);
  });
});
describe('MAM-39 demo other forms', () => {
  it('this test passes', () => {
    expect(1 + 1).toBe(2);
  });
  it.skipIf(true)('skipped by skipIf', () => {
    expect(1).toBe(1);
  });
  it.todo('a todo test');
});
TS
  cat > "$TMP_WEB/demo-failing.test.ts" <<'TS'
import { describe, expect, it } from 'vitest';

// MAM-39 demonstration only: written and removed by tools/checks/demo-ci-gates.sh.
describe('MAM-39 demo failing', () => {
  it('this test fails on purpose', () => {
    expect(1).toBe(2);
  });
});
TS
  if [ "$(grep -c 'it\.skip(' "$TMP_WEB/demo-clean.test.ts")" != "0" ] || [ "$(changed_lines "$TMP_WEB/demo-skip.test.ts" "$TMP_WEB/demo-clean.test.ts")" != "1" ]; then
    fail "AC-3 setup: the control file must differ from the skip file by exactly the one it.skip"
  fi

  # vt_case <name> <file filter>: the two commands of the test script on one temporary file, joined as the script joins them (&&).
  # Sets VT_RC (vitest), SK_RC (no-skipped.mjs; 99 when it was not reached), CHAIN_RC (what npm test would exit with)
  vt_case() {
    local name="$1" filter="$2" report vargs skip_cmd
    report="$WORK/vt-$name.json"
    vargs="$(printf '%s' "${VITEST_PART#vitest }" | sed "s#--outputFile=test-results/vitest.json#--outputFile=$(native "$report")#")"
    skip_cmd="$(printf '%s' "$SKIP_PART" | sed "s#test-results/vitest.json#$(native "$report")#")"
    case "$vargs $skip_cmd" in *test-results/vitest.json*) echo "  (the report path of package.json could not be replaced: the real report would be overwritten)"; VT_RC=98; SK_RC=98; CHAIN_RC=98; return ;; esac
    run_vitest "$WORK/vt-$name.log" $vargs --config "test-results/$TMP_NAME/vitest.demo.config.ts" "$filter"; VT_RC=$?
    SK_RC=99
    if [ $VT_RC -eq 0 ] && [ -f "$report" ]; then
      (cd "$WEB" && bash -c "$skip_cmd") > "$WORK/sk-$name.log" 2>&1; SK_RC=$?
      strip_log "$WORK/sk-$name.log"
    else
      : > "$WORK/sk-$name.log"; strip_log "$WORK/sk-$name.log"
    fi
    CHAIN_RC=$VT_RC; [ $VT_RC -eq 0 ] && CHAIN_RC=$SK_RC
  }

  # (a) one it.skip
  vt_case skip demo-skip
  if [ $VT_RC -eq 0 ] && [ $SK_RC -eq 1 ] && [ $CHAIN_RC -ne 0 ] && grep -qF 'this test is skipped on purpose' "$WORK/sk-skip.log.txt" && grep -qF 'demo-skip.test.ts' "$WORK/sk-skip.log.txt"; then
    pass "AC-3 (a) a temporary test file with one it.skip: Vitest alone exits $VT_RC (it does not fail a skipped test), scripts/no-skipped.mjs exits $SK_RC and names the test: the step fails (exit $CHAIN_RC)"
    excerpt_re "$WORK/vt-skip.log.txt" 'Tests .*skipped' 1
    excerpt "$WORK/sk-skip.log.txt" 'SKIPPED:' 1
    excerpt "$WORK/sk-skip.log.txt" 'a test cannot be skipped' 1
  else
    fail "AC-3 (a) one it.skip (vitest exit $VT_RC, no-skipped exit $SK_RC; expected 0 and 1, and the test named)" "$WORK/sk-skip.log.txt"
  fi
  # (a, control) the same file without the skip
  vt_case clean demo-clean
  if [ $VT_RC -eq 0 ] && [ $SK_RC -eq 0 ] && [ $CHAIN_RC -eq 0 ] && grep -qF 'no skipped tests (2 tests)' "$WORK/sk-clean.log.txt"; then
    pass "AC-3 (a) control: the same file without the skip: Vitest exits 0 and scripts/no-skipped.mjs exits 0, the step is green"
    excerpt "$WORK/sk-clean.log.txt" 'no skipped tests' 1
  else
    fail "AC-3 (a) control: the file without the skip (vitest exit $VT_RC, no-skipped exit $SK_RC)" "$WORK/vt-clean.log.txt"
  fi
  # (b) the other forms of a test that does not run
  vt_case other demo-other
  miss=""
  for n in 'inside a skipped group' 'skipped by skipIf' 'a todo test'; do grep -qF "$n" "$WORK/sk-other.log.txt" || miss="$miss '$n'"; done
  if [ $VT_RC -eq 0 ] && [ $SK_RC -eq 1 ] && [ -z "$miss" ] && grep -qF '3 skipped test(s)' "$WORK/sk-other.log.txt"; then
    pass "AC-3 (b) describe.skip, it.skipIf and it.todo: scripts/no-skipped.mjs exits $SK_RC and names all three (the step fails)"
    excerpt "$WORK/sk-other.log.txt" 'SKIPPED:' 3
  else
    fail "AC-3 (b) other skip forms (vitest exit $VT_RC, no-skipped exit $SK_RC; not named:${miss:- none})" "$WORK/sk-other.log.txt"
  fi
  # (c) a failing test: Vitest exits non-zero, the && stops the chain
  vt_case failing demo-failing
  if [ $VT_RC -ne 0 ] && [ $SK_RC -eq 99 ] && [ $CHAIN_RC -ne 0 ] && grep -qF 'this test fails on purpose' "$WORK/vt-failing.log.txt" \
     && grep -qE 'AssertionError: expected 1 to be 2' "$WORK/vt-failing.log.txt"; then
    pass "AC-3 (c) a failing test: Vitest exits $VT_RC and names the test and its assertion, the && of the test script stops there: the step fails (exit $CHAIN_RC)"
    excerpt "$WORK/vt-failing.log.txt" 'this test fails on purpose' 1
    excerpt_re "$WORK/vt-failing.log.txt" 'AssertionError: expected 1 to be 2' 1
  else
    fail "AC-3 (c) a failing test (vitest exit $VT_RC; expected non-zero, the test named and 'AssertionError: expected 1 to be 2')" "$WORK/vt-failing.log.txt"
  fi
fi
[ -d "$TMP_WEB" ] && TMP_CACHE_TEXT="the temporary Vitest runs wrote $(find "$TMP_WEB/.vite" -name results.json 2>/dev/null | wc -l | tr -d ' ') results.json file(s) and $(ls "$TMP_WEB/.vitest-cache" 2>/dev/null | grep -cE '^[0-9a-f]{40}$') module-cache entry file(s) inside their own temporary folder (removed with it)"
rm -rf "$TMP_WEB"
fi

# ---------------------------------------------------------------- AC-1: coverage below 85 %
if ! skip_block ac1; then
echo "-- AC-1: a change that lowers coverage below 85% fails the Gradle build"
GRADLE_CMD="$WF_GRADLE"
if [ -z "$GRADLE_CMD" ]; then
  fail "AC-1 setup: the Gradle command was not found in job build-and-check of the workflow"
else
  # The one difference to the workflow: --no-build-cache. A developer machine has a build cache that a clean runner does not;
  # without the flag the clean run could be restored from it without running one test.
  GRADLE_RUN="$(printf '%s' "$GRADLE_CMD" | sed 's|\./gradlew |./gradlew --no-build-cache |')"
  echo "   workflow command: $GRADLE_CMD"
  echo "   run in the copy:  $GRADLE_RUN"
  B="$WORK/b"; mkdir -p "$B"
  if ! git -C "$REPO" archive --format=tar HEAD:modern buildSrc gradle gradlew gradlew.bat gradle.properties build.gradle.kts settings.gradle.kts \
       libs/fixed-width libs/test-support docs/analysis/data-layouts.md | tar -x -C "$B"; then   # the doc: DataLayoutsDoc.java reads ../../docs/analysis/data-layouts.md
    fail "AC-1 setup: git archive of HEAD failed"
  else
    # every project that settings.gradle.kts includes needs its folder; the ones not copied stay empty (no build file: no task)
    for p in $(grep -o '"[a-z0-9-]*:[a-z0-9:-]*"' "$B/settings.gradle.kts" | tr -d '"'); do mkdir -p "$B/${p//://}"; done
    chmod +x "$B/gradlew" 2>/dev/null
    RULE="$B/buildSrc/src/main/kotlin/pem.java-conventions.gradle.kts"
    if git -C "$REPO" diff --quiet HEAD -- modern/buildSrc modern/gradle modern/build.gradle.kts modern/settings.gradle.kts modern/gradle.properties \
         modern/libs/fixed-width modern/libs/test-support; then
      echo "   the copy is HEAD $(git -C "$REPO" rev-parse --short HEAD); the working tree has the same build logic and modules"
    else
      echo "   the copy is HEAD $(git -C "$REPO" rev-parse --short HEAD); NOTE: the working tree differs from HEAD in the build logic or in a copied module"
    fi
    echo "   the rule in the copy (unchanged):"; grep -n -E 'counter = "LINE"|value = "COVEREDRATIO"|minimum = ' "$RULE" | sed 's/^/    | /'
    TESTS="$B/libs/fixed-width/src/test"
    n_tests_all="$(find "$TESTS" -name '*Test.java' | wc -l | tr -d ' ')"

    line_ratio() {   # line_ratio <jacoco xml>: "covered/total ratio" of the report's LINE counter, empty when there is none
      local c
      c="$(grep -o '<counter type="LINE" missed="[0-9]*" covered="[0-9]*"/>' "$1" 2>/dev/null | tail -n 1)"
      [ -n "$c" ] || return 0
      echo "$c" | sed 's/.*missed="\([0-9]*\)" covered="\([0-9]*\)".*/\1 \2/' | awk '{ t = $1 + $2; printf "%d/%d %.4f", $2, t, (t ? $2 / t : 0) }'
    }
    XML="$B/libs/fixed-width/build/reports/jacoco/test/jacocoTestReport.xml"

    # negative control: the workflow's command on the unchanged copy passes
    run_gradle_cmd "$B" "$WORK/g1.log" "$WORK/runner-temp-1" "$GRADLE_RUN"; rc=$?
    ratio="$(line_ratio "$XML")"
    if [ $rc -eq 0 ] && grep -q 'BUILD SUCCESSFUL' "$WORK/g1.log.txt" && grep -qE '^> Task :libs:fixed-width:test$' "$WORK/g1.log.txt" \
       && grep -qE '^> Task :libs:fixed-width:jacocoTestCoverageVerification$' "$WORK/g1.log.txt" && ! grep -q ' FAILED$' "$WORK/g1.log.txt"; then
      pass "AC-1 control: the workflow's command on the unchanged copy passes (exit 0; test and jacocoTestCoverageVerification of libs:fixed-width ran; $n_tests_all test classes; lines covered ${ratio:-unknown})"
      excerpt_re "$WORK/g1.log.txt" '^> Task :libs:fixed-width:jacocoTestCoverageVerification' 1
      excerpt "$WORK/g1.log.txt" 'BUILD SUCCESSFUL' 1
    else
      fail "AC-1 control: the workflow's command on the unchanged copy (exit $rc; failed: $(grep -E ' FAILED$' "$WORK/g1.log.txt" | head -n 5 | tr '\n' ';'))" "$WORK/g1.log.txt"
    fi

    # the fault: every test class but one is deleted IN THE COPY (the repository is not touched)
    KEEP="${DEMO_KEEP_TESTS:-RoundTripTest.java}"
    if [ -z "$(find "$TESTS" -name "$KEEP")" ]; then
      fail "AC-1 fault setup: the test class to keep ($KEEP) is not in libs/fixed-width/src/test"
    else
      find "$TESTS" -name '*Test.java' ! -name "$KEEP" -delete
      left="$(find "$TESTS" -name '*Test.java' | wc -l | tr -d ' ')"
      echo "   in the copy: $((n_tests_all - left)) of $n_tests_all test classes deleted, $KEEP kept (the helpers Samples and DataLayoutsDoc stay)"
      run_gradle_cmd "$B" "$WORK/g2.log" "$WORK/runner-temp-2" "$GRADLE_RUN"; rc=$?
      msg="$(grep -m1 -E 'lines covered ratio is' "$WORK/g2.log.txt")"
      got="$(printf '%s' "$msg" | sed -n 's/.*lines covered ratio is \([0-9.]*\), but expected minimum is \([0-9.]*\).*/\1 \2/p')"
      below=no; [ -n "$got" ] && [ "$(echo "$got" | awk '{ print ($1 < $2) ? 1 : 0 }')" = "1" ] && below=yes
      failed_tasks="$(grep -cE '^> Task .* FAILED$' "$WORK/g2.log.txt")"
      ratio2="$(line_ratio "$XML")"
      if [ $rc -ne 0 ] && [ "$left" -ge 1 ] && grep -qE '^> Task :libs:fixed-width:jacocoTestCoverageVerification FAILED$' "$WORK/g2.log.txt" \
         && grep -qF 'expected minimum is 0.85' "$WORK/g2.log.txt" && [ "$below" = "yes" ] && [ "$failed_tasks" = "1" ] && grep -q 'BUILD FAILED' "$WORK/g2.log.txt"; then
        pass "AC-1 with $left of $n_tests_all test classes left in the copy the workflow's command fails (exit $rc) in jacocoTestCoverageVerification, the only failed task, with the rule's message (ratio ${got%% *}, minimum ${got##* }; report: lines covered ${ratio2:-unknown})"
        excerpt "$WORK/g2.log.txt" 'jacocoTestCoverageVerification FAILED' 1
        excerpt "$WORK/g2.log.txt" 'lines covered ratio is' 1
        excerpt "$WORK/g2.log.txt" 'BUILD FAILED' 1
      else
        fail "AC-1 with tests deleted (exit $rc; failed tasks $failed_tasks; message '${msg}'; ratio below minimum: $below)" "$WORK/g2.log.txt"
      fi
    fi
  fi
fi
fi

# ---------------------------------------------------------------- clean-up, and what is compared before and after
echo "-- clean-up"
rm -rf "$TMP_WEB"
CACHE_AFTER="$(project_cache_entries)"
if [ "$CACHE_BEFORE" = "?" ] || [ "$CACHE_AFTER" = "?" ]; then
  fail "the Vitest caches of the project could not be read (demo-ci-gates- entries: results cache; module cache files; module cache manifest keys; before: $CACHE_BEFORE, after: $CACHE_AFTER); the comparison cannot be made, so this step fails"
else
  read -r RES0 FIL0 KEY0 <<< "$CACHE_BEFORE"
  read -r RES1 FIL1 KEY1 <<< "$CACHE_AFTER"
  if [ "$RES1" -gt "$RES0" ] || [ "$FIL1" -gt "$FIL0" ] || [ "$KEY1" -gt "$KEY0" ]; then
    fail "the Vitest caches of the project gained entries for temporary test files of this script: results cache (web-ui/node_modules/.vite/vitest) $RES0 before, $RES1 after; module cache (web-ui/node_modules/.vitest-cache) entry files $FIL0 before, $FIL1 after, manifest keys $KEY0 before, $KEY1 after: the cacheDir or the fsModuleCachePath of the temporary configuration did not take effect"
  else
    pass "Vitest caches: for demo-ci-gates- paths the project's results cache (web-ui/node_modules/.vite/vitest) had $RES0 entries before the run and has $RES1 after, its module cache (web-ui/node_modules/.vitest-cache) had $FIL0 entry files and $KEY0 manifest keys before and has $FIL1 and $KEY1 after: none was added; $TMP_CACHE_TEXT"
  fi
fi
snapshot "$WORK/after.sha256"
ls -A "$MODERN/web-ui/test-results" 2>/dev/null | LC_ALL=C sort > "$WORK/after-test-results.txt"
if [ -n "$(ls -d "$MODERN"/web-ui/test-results/demo-ci-gates-* 2>/dev/null)" ]; then
  fail "a temporary folder of this script is still in web-ui/test-results: $(ls -d "$MODERN"/web-ui/test-results/demo-ci-gates-* | head -n 3 | tr '\n' ' ') (left by an earlier run that was killed?)"
elif [ "$(report_hash)" != "$(cat "$WORK/before-report.txt")" ]; then
  fail "web-ui/test-results/vitest.json (the real report of npm test) changed during the run"
elif diff -q "$WORK/before.sha256" "$WORK/after.sha256" >/dev/null; then
  pass "the compared files are unchanged: $(wc -l < "$WORK/after.sha256" | tr -d ' ') files (contracts/, libs/fixed-width, the build logic, the web-ui scripts and the workflow) have the same sha256 as before; no demo-ci-gates-* entry is left in web-ui/test-results and its real report vitest.json is as before (other files are not compared)"
  # other processes (a Playwright run of another lane) also write to that git-ignored folder: reported, not counted
  diff -q "$WORK/before-test-results.txt" "$WORK/after-test-results.txt" >/dev/null || echo "    | note: other entries of web-ui/test-results changed during the run (not this script): $(diff "$WORK/before-test-results.txt" "$WORK/after-test-results.txt" | grep '^[<>]' | tr '\n' ' ')"
else
  fail "files that the script must never touch changed during the run (by this script, or by another lane at the same time): $(diff "$WORK/before.sha256" "$WORK/after.sha256" | grep '^[<>]' | awk '{ print $3 }' | sort -u | head -n 5 | tr '\n' ' ')"
fi

echo "== $(date '+%F %T'): $GRADLE_RUNS Gradle run(s); waited ${WAITED_MEM} s for free memory and ${WAITED_LOCK} s for the Gradle lock; $FAILS step(s) failed =="
[ "$FAILS" -eq 0 ]
