#!/usr/bin/env bash
# MAM-16 demonstration of the two build gates that are not plain configuration (run it from modern/, or anywhere).
#   AC-8  versionCheck fails on a dynamic version and names the alias; it passes on the real catalog.
#   AC-7  a @Disabled test (method, class, @ParameterizedTest, @RepeatedTest, @TestFactory) and a test skipped at run time fail
#         :libs:fixed-width:test and are named; without them it passes.
# It prints PASS or FAIL per step and exits 1 when any step failed. It leaves no file behind: the temporary test classes
# (libs/fixed-width/src/test/java/.../TempSkippedDemo*.java) and its scratch folder are removed on exit, also on error.
#
# Low-memory rules (docs/PROGRESS.md): one Gradle build at a time, always --no-daemon, and before every Gradle call the free
# memory is read; under 1024 MB (PEM_MIN_FREE_MB) the script waits 60 s and checks again, it never gives up for memory.
# Environment (all optional):
#   PEM_SCRATCH       folder for the scratch directory (default: the system temp folder)
#   PEM_GRADLE_LOCK   a lock folder; when set, the script creates it (with owner.txt) around every Gradle call, removes a stale
#                     one (no java.exe of this repository running) and waits for a live one
#   PEM_MIN_FREE_MB   free-memory threshold, default 1024
#   DEMO_TIMEOUT      seconds allowed per Gradle call, default 1500
set -u
cd "$(dirname "$0")/../.." || exit 2
MODERN="$(pwd)"
TEST_DIR="libs/fixed-width/src/test/java/gov/medicaid/pem/fixedwidth"
CATALOG="gradle/libs.versions.toml"
ADR="docs/adr/ADR-010-versions.md"
WORK="$(mktemp -d "${PEM_SCRATCH:-${TMPDIR:-/tmp}}/demo-build-gates.XXXXXX")" || exit 2
FAILS=0
WAITED=0

native() { if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else printf '%s' "$1"; fi; }

release_lock() {
  [ -n "${PEM_GRADLE_LOCK:-}" ] || return 0
  rm -f "$PEM_GRADLE_LOCK/owner.txt" 2>/dev/null
  rmdir "$PEM_GRADLE_LOCK" 2>/dev/null
  return 0
}

cleanup() {
  rm -f "$MODERN/$TEST_DIR"/TempSkippedDemo*.java
  rm -rf "$WORK"
}
trap cleanup EXIT
trap 'exit 130' INT TERM

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
    WAITED=$((WAITED + 60))
  done
}

java_of_repo_running() {   # read-only: how many java.exe have this repository in their command line
  command -v powershell >/dev/null 2>&1 || { echo 0; return; }
  cat > "$WORK/javacount.ps1" <<'PS'
@(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -match 'Medco-Transform' }).Count
PS
  powershell -NoProfile -ExecutionPolicy Bypass -File "$(native "$WORK/javacount.ps1")" 2>/dev/null | tr -d '\r'
}

acquire_lock() {
  [ -n "${PEM_GRADLE_LOCK:-}" ] || return 0
  while ! mkdir "$PEM_GRADLE_LOCK" 2>/dev/null; do
    echo "  gradle lock exists (owner: $(cat "$PEM_GRADLE_LOCK/owner.txt" 2>/dev/null))"
    if [ "$(java_of_repo_running)" = "0" ]; then
      echo "  no java.exe of this repository is running: the lock is stale, removing it"
      rm -f "$PEM_GRADLE_LOCK/owner.txt"; rmdir "$PEM_GRADLE_LOCK" 2>/dev/null
      continue
    fi
    sleep 60
  done
  echo "devops-engineer demo-build-gates.sh $(date '+%F %T')" > "$PEM_GRADLE_LOCK/owner.txt"
}

# run_gradle <log> <gradle args...>: memory check, lock, one build, unlock; returns Gradle's exit code
run_gradle() {
  local log="$1" rc; shift
  wait_memory
  acquire_lock
  timeout "${DEMO_TIMEOUT:-1500}" ./gradlew --no-daemon --console=plain "$@" > "$log" 2>&1
  rc=$?
  release_lock
  return $rc
}

pass() { echo "PASS $1"; }
fail() { echo "FAIL $1"; FAILS=$((FAILS + 1)); [ -n "${2:-}" ] && tail -n 30 "$2" | sed 's/^/    | /'; }
excerpt() { grep -m "${3:-4}" -F -- "$2" "$1" | sed 's/^/    | /'; }   # excerpt <log> <text> [max lines]

echo "== MAM-16 demonstration of the build gates (scratch: $WORK) =="

# ---------------------------------------------------------------- AC-8: versionCheck
echo "-- AC-8: the version check"
cp "$CATALOG" "$WORK/one-dynamic.toml"
sed -i 's/^springdoc = "[^"]*"/springdoc = "2.+"/' "$WORK/one-dynamic.toml"
if ! grep -q '^springdoc = "2\.+"' "$WORK/one-dynamic.toml"; then
  fail "AC-8 setup: could not make springdoc dynamic in the copy of the catalog"
else
  run_gradle "$WORK/a1.log" versionCheck -PpemVersionCatalog="$(native "$WORK/one-dynamic.toml")"; rc=$?
  if [ $rc -ne 0 ] && grep -q 'Version check failed' "$WORK/a1.log" && grep -q 'springdoc' "$WORK/a1.log" && grep -q '2\.+' "$WORK/a1.log"; then
    pass "AC-8 a catalog with springdoc = \"2.+\" makes versionCheck fail (exit $rc) and the message names the alias and the value"
    excerpt "$WORK/a1.log" 'springdoc' 2
  else
    fail "AC-8 versionCheck on a catalog with one dynamic version (exit $rc; expected a failure that names springdoc)" "$WORK/a1.log"
  fi
fi

cp "$CATALOG" "$WORK/all-kinds.toml"
sed -i -e 's/^archunit = "[^"]*"/archunit = "latest.release"/' \
       -e 's/^wiremock = "[^"]*"/wiremock = "[3.0,4.0)"/' \
       -e 's/^pitest-junit5 = "[^"]*"/pitest-junit5 = ""/' "$WORK/all-kinds.toml"
printf '%s\n' 'bad-ref = { module = "org.example:bad-ref", version.ref = "no-such-version" }' >> "$WORK/all-kinds.toml"
run_gradle "$WORK/a2.log" versionCheck -PpemVersionCatalog="$(native "$WORK/all-kinds.toml")"; rc=$?
missing=""
for needle in archunit latest.release wiremock '[3.0,4.0)' pitest-junit5 'is empty' bad-ref no-such-version; do
  grep -qF -- "$needle" "$WORK/a2.log" || missing="$missing $needle"
done
if [ $rc -ne 0 ] && [ -z "$missing" ]; then
  pass "AC-8 latest, a range, an empty version and a missing version.ref are each named (exit $rc)"
  excerpt "$WORK/a2.log" '(line' 5
else
  fail "AC-8 all kinds of bad version (exit $rc; not named:${missing:- none})" "$WORK/a2.log"
fi

run_gradle "$WORK/a3.log" versionCheck; rc=$?
if [ $rc -eq 0 ] && grep -q 'versionCheck:' "$WORK/a3.log"; then
  pass "AC-8 versionCheck on the real catalog succeeds (exit 0)"
  excerpt "$WORK/a3.log" 'versionCheck:' 1
else
  fail "AC-8 versionCheck on the real catalog (exit $rc)" "$WORK/a3.log"
fi

# The versions of the catalog are recorded in ADR-010 (Spring Boot and Spring Cloud by name, every other version by text).
sb="$(sed -n 's/^spring-boot = "\([^"]*\)".*/\1/p' "$CATALOG")"
sc="$(sed -n 's/^spring-cloud = "\([^"]*\)".*/\1/p' "$CATALOG")"
unrecorded=""
while read -r alias value; do
  grep -qF -- "$value" "$ADR" || unrecorded="$unrecorded $alias=$value"
done < <(sed -n '/^\[versions\]/,/^\[libraries\]/p' "$CATALOG" | sed -n -E 's/^([A-Za-z0-9_.-]+)[[:space:]]*=[[:space:]]*"([^"]*)".*/\1 \2/p')
if [ -n "$sb" ] && [ -n "$sc" ] && grep -qE "^\| Spring Boot \| ${sb//./\\.}[ (]" "$ADR" && grep -qE "^\| Spring Cloud \| ${sc//./\\.}[ (]" "$ADR" && [ -z "$unrecorded" ]; then
  pass "AC-8 ADR-010 records Spring Boot $sb, Spring Cloud $sc and every other version of the catalog"
else
  fail "AC-8 ADR-010 does not record: Spring Boot '${sb}', Spring Cloud '${sc}', others:${unrecorded:- none}"
fi

# ---------------------------------------------------------------- AC-7: skipped tests
echo "-- AC-7: a skipped test cannot reach green"
mkdir -p "$MODERN/$TEST_DIR"
cat > "$MODERN/$TEST_DIR/TempSkippedDemoTest.java" <<'JAVA'
package gov.medicaid.pem.fixedwidth;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** MAM-16 demonstration only: created and removed by tools/checks/demo-build-gates.sh. */
@Disabled("MAM-16 demo: the whole class is disabled")
class TempSkippedDemoTest {
  @Test
  void insideDisabledClass() {}
}
JAVA
cat > "$MODERN/$TEST_DIR/TempSkippedDemoRuntimeTest.java" <<'JAVA'
package gov.medicaid.pem.fixedwidth;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** MAM-16 demonstration only: created and removed by tools/checks/demo-build-gates.sh. */
class TempSkippedDemoRuntimeTest {
  @Test
  @Disabled("MAM-16 demo: one method is disabled")
  void disabledByAnnotation() {}

  @Test
  void skippedAtRunTime() {
    assumeTrue(false, "MAM-16 demo: skipped by a failed assumption");
  }
}
JAVA
# Test templates (@ParameterizedTest, @RepeatedTest, @TestFactory) have no test of their own until they run, so Gradle reports a
# disabled one only as a skipped suite with zero tests. They are the reason SkippedTestGuard also reads afterSuite.
cat > "$MODERN/$TEST_DIR/TempSkippedDemoTemplateTest.java" <<'JAVA'
package gov.medicaid.pem.fixedwidth;

import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** MAM-16 demonstration only: created and removed by tools/checks/demo-build-gates.sh. */
class TempSkippedDemoTemplateTest {
  @Disabled("MAM-16 demo: a parameterized test is disabled")
  @ParameterizedTest
  @ValueSource(ints = {1, 2})
  void disabledParameterized(int value) {}

  @Disabled("MAM-16 demo: a repeated test is disabled")
  @RepeatedTest(2)
  void disabledRepeated() {}

  @Disabled("MAM-16 demo: a test factory is disabled")
  @TestFactory
  Stream<DynamicTest> disabledFactory() {
    return Stream.empty();
  }
}
JAVA
cat > "$MODERN/$TEST_DIR/TempSkippedDemoTemplateClassTest.java" <<'JAVA'
package gov.medicaid.pem.fixedwidth;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** MAM-16 demonstration only: created and removed by tools/checks/demo-build-gates.sh. */
@Disabled("MAM-16 demo: a class that holds only a parameterized test is disabled")
class TempSkippedDemoTemplateClassTest {
  @ParameterizedTest
  @ValueSource(strings = {"a", "b"})
  void parameterizedInsideDisabledClass(String value) {}
}
JAVA
run_gradle "$WORK/b1.log" :libs:fixed-width:test --rerun; rc=$?
rm -f "$MODERN/$TEST_DIR"/TempSkippedDemo*.java
missing=""
for needle in 'skipped in :libs:fixed-width:test' TempSkippedDemoTest insideDisabledClass TempSkippedDemoRuntimeTest disabledByAnnotation skippedAtRunTime \
              TempSkippedDemoTemplateTest 'disabledParameterized(int)' 'disabledRepeated()' 'disabledFactory()' \
              TempSkippedDemoTemplateClassTest; do
  grep -qF -- "$needle" "$WORK/b1.log" || missing="$missing $needle"
done
if [ $rc -ne 0 ] && [ -z "$missing" ]; then
  pass "AC-7 with a @Disabled class, a @Disabled method, a failed assumption and a @Disabled @ParameterizedTest, @RepeatedTest, @TestFactory and parameterized-only class :libs:fixed-width:test fails (exit $rc) and names each"
  excerpt "$WORK/b1.log" 'skipped in :libs:fixed-width:test' 1
  excerpt "$WORK/b1.log" ' - gov.medicaid.pem.fixedwidth.TempSkippedDemo' 12
else
  fail "AC-7 skipped tests (exit $rc; not named:${missing:- none})" "$WORK/b1.log"
fi

if ls "$MODERN/$TEST_DIR"/TempSkippedDemo* >/dev/null 2>&1; then
  fail "AC-7 the temporary test classes are still there"
else
  run_gradle "$WORK/b2.log" :libs:fixed-width:test --rerun; rc=$?
  tests=0; skipped=0; failures=0
  for x in libs/fixed-width/build/test-results/test/TEST-*.xml; do
    [ -f "$x" ] || continue
    t="$(sed -n '2s/.* tests="\([0-9]*\)".*/\1/p' "$x")"; s="$(sed -n '2s/.* skipped="\([0-9]*\)".*/\1/p' "$x")"
    f="$(sed -n '2s/.* failures="\([0-9]*\)".*/\1/p' "$x")"
    tests=$((tests + ${t:-0})); skipped=$((skipped + ${s:-0})); failures=$((failures + ${f:-0}))
  done
  if [ $rc -eq 0 ] && grep -q 'BUILD SUCCESSFUL' "$WORK/b2.log" && [ "$skipped" -eq 0 ] && [ "$tests" -gt 0 ]; then
    pass "AC-7 after the temporary classes are removed :libs:fixed-width:test passes again (exit 0; $tests tests, $failures failures, $skipped skipped)"
  else
    fail "AC-7 test task after removing the temporary classes (exit $rc; $tests tests, $skipped skipped)" "$WORK/b2.log"
  fi
fi

# ---------------------------------------------------------------- nothing left behind
echo "-- clean-up"
PY="kag/.venv/Scripts/python.exe"
[ -x "$PY" ] || PY="$(command -v python3 || command -v python)"
if ls "$MODERN/$TEST_DIR"/TempSkippedDemo* >/dev/null 2>&1; then
  fail "no TempSkippedDemo file may remain"
elif [ -n "$PY" ]; then
  out="$("$PY" tools/checks/test_protection.py check MAM-42 2>&1)"
  case "$out" in OK*) pass "no file left behind: test_protection.py check MAM-42 -> $out" ;; *) fail "test_protection.py check MAM-42 -> $out" ;; esac
else
  pass "no TempSkippedDemo file remains (no Python found for test_protection.py)"
fi

echo "== waited ${WAITED} s for free memory; $FAILS step(s) failed =="
[ "$FAILS" -eq 0 ]
