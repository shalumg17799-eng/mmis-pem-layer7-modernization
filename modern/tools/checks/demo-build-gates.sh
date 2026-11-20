#!/usr/bin/env bash
# MAM-16 demonstration of the two build gates that are not plain configuration (run it from modern/, or anywhere).
#   AC-8  versionCheck fails on a dynamic version and names the alias; it passes on the real catalog.
#   AC-7  a @Disabled test (method, class, @ParameterizedTest, @RepeatedTest, @TestFactory) and a test skipped at run time fail
#         :libs:fixed-width:test and are named; without them it passes.
#   AC-7, second layer (source check): the root task testSourceCheck reads the test sources and fails, naming file, line and
#         construct, on every skip mechanism (@Disabled, @Ignore, conditional execution, assumptions, JUnit 3 and 4, Cucumber
#         filters), also those the run-time listener cannot see; the same words in comments, in string literals and outside
#         src/test do not fail it; the real tree passes; the task is in the plan of every module check and of checkAll once.
# It prints PASS or FAIL per step and exits 1 when any step failed. It leaves no file behind: the temporary sources
# (TempSkippedDemo* in libs/fixed-width/src/test/java/.../fixedwidth, in libs/fixed-width and in the source folder of
# libs/test-support) and its scratch folder are removed on exit, also on error.
# The temporary sources of the source-check block are text for the scanner only: they do NOT compile, on purpose, and only the
# task testSourceCheck (it reads text, it compiles nothing) runs while one exists.
#
# Low-memory rules (docs/PROGRESS.md): one Gradle build at a time, always --no-daemon, and before every Gradle call the free
# memory is read; under 1024 MB (PEM_MIN_FREE_MB) the script waits 60 s and checks again, it never gives up for memory.
# Environment (all optional):
#   PEM_SCRATCH       folder for the scratch directory (default: the system temp folder)
#   PEM_GRADLE_LOCK   a lock folder; when set, the script creates it (with owner.txt) around every Gradle call, removes a stale
#                     one (no java.exe of this repository running) and waits for a live one
#   PEM_MIN_FREE_MB   free-memory threshold, default 1024
#   DEMO_TIMEOUT      seconds allowed per Gradle call, default 1500
#   DEMO_ONLY         run one block only: ac8, ac7-runtime or ac7-source (default: all blocks, then the clean-up check)
set -u
cd "$(dirname "$0")/../.." || exit 2
MODERN="$(pwd)"
TEST_DIR="libs/fixed-width/src/test/java/gov/medicaid/pem/fixedwidth"
SUPPORT_DIR="libs/test-support/src/main/java/gov/medicaid/pem/testsupport"
TEMP_DIRS=("$MODERN/$TEST_DIR" "$MODERN/libs/fixed-width" "$MODERN/$SUPPORT_DIR")   # where TempSkippedDemo* files are written
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

remove_temp() {
  local d
  for d in "${TEMP_DIRS[@]}"; do rm -f "$d"/TempSkippedDemo*; done
}

temp_left() {   # lists every TempSkippedDemo file that is still there
  local d
  for d in "${TEMP_DIRS[@]}"; do ls "$d"/TempSkippedDemo* 2>/dev/null; done
}

cleanup() {
  remove_temp
  rm -rf "$WORK"
}
trap cleanup EXIT

skip_block() { [ -n "${DEMO_ONLY:-}" ] && [ "$DEMO_ONLY" != "$1" ]; }   # true when DEMO_ONLY names another block
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
if ! skip_block ac8; then
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
fi

# ---------------------------------------------------------------- AC-7: skipped tests
if ! skip_block ac7-runtime; then
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
fi

# ---------------------------------------------------------------- AC-7, second layer: skip mechanisms in the test sources
# The run-time listener cannot see a disabled class template (@ParameterizedClass, @ClassTemplate) or a JUnit 4 test (no engine
# discovers it), so the root task testSourceCheck reads the test sources. The temporary sources of this block are text for that
# scanner and nothing else: they do NOT compile, on purpose (the check reads sources, it does not compile them). For that reason
# scan_only is the only Gradle call made while one of them exists, and every call is followed by remove_temp.
if ! skip_block ac7-source; then
echo "-- AC-7 (second layer): a skip mechanism in a test source fails testSourceCheck"

scan_only() { run_gradle "$1" testSourceCheck; }   # scan_only <log>: the source check and nothing else (nothing is compiled)

# mk_java <dir> <class name> <the one source line>: a seven-line file, the construct on line 6
mk_java() {
  mkdir -p "$1"
  printf '%s\n' 'package gov.medicaid.pem.fixedwidth;' '' \
    '// MAM-16 demonstration only: written and removed by tools/checks/demo-build-gates.sh. It does not compile, on purpose.' \
    "class $2 {" '  // the construct is on the next line (line 6)' "  $3" '}' > "$1/$2.java"
}

# named <log> <label> <file> <line> <construct>: PASS when the message of the failed task names exactly that file, line and construct
NOT_NAMED=0
named() {
  local hit
  hit="$(grep -m1 -F -- "$3:$4: $5 (" "$1" | sed 's#^[^:]*/##' | cut -c1-235)"
  if [ -n "$hit" ]; then pass "AC-7 source $2: $hit"; else fail "AC-7 source $2: '$3:$4: $5 (' is not in the message"; NOT_NAMED=1; fi
}

# expected_sources: what the check must read, counted without Gradle: .java .kt .properties under src/test of every module and
# .java .kt under src/main of libs/test-support
expected_sources() {
  {
    find libs services infra -path '*/src/test/*' -type f \( -name '*.java' -o -name '*.kt' -o -name '*.properties' \) -not -path '*/build/*'
    find libs/test-support/src/main -type f \( -name '*.java' -o -name '*.kt' \) -not -path '*/build/*'
  } | wc -l | tr -d ' '
}

# name | the source line (line 6 of the file) | the construct the message must name. File: TempSkippedDemoSrc<name>.java
SRC_CASES=(
  'Disabled|@Disabled("MAM-16 demo")|@Disabled'
  'DisabledQualified|@org.junit.jupiter.api.Disabled|@Disabled'
  'Ignore|@Ignore|@Ignore'
  'DisabledWithoutDocker|@Testcontainers(disabledWithoutDocker = true)|disabledWithoutDocker'
  'ExecutionCondition|class DemoCondition implements ExecutionCondition {}|ExecutionCondition'
  'AssumptionsType|Assumptions.assumeTrue(false);|Assumptions'
  'AssumeTrue|assumeTrue(false);|assumeTrue('
  'AssumeFalse|assumeFalse(true);|assumeFalse('
  'AssumeThat|assumeThat(1).isEqualTo(1);|assumeThat('
  'AssumingThat|assumingThat(false, () -> {});|assumingThat('
  'AssumeThatThrownBy|assumeThatThrownBy(() -> {});|assumeThatThrownBy('
  'TestAborted|throw new TestAbortedException("MAM-16 demo");|TestAbortedException'
  'Junit4Test|import org.junit.Test;|org.junit.Test'
  'Junit4Ignore|import org.junit.Ignore;|org.junit.Ignore'
  'Junit4Before|import org.junit.Before;|org.junit.Before'
  'Junit4After|import org.junit.After;|org.junit.After'
  'Junit4Assert|import org.junit.Assert;|org.junit.Assert'
  'Junit4Runner|import org.junit.runner.RunWith;|org.junit.runner'
  'Junit4Rule|import org.junit.Rule;|org.junit.Rule'
  'Junit4Qualified|org.junit.Assert.assertTrue(true);|org.junit.Assert'
  'Junit3|class DemoCase extends junit.framework.TestCase {}|junit.framework.TestCase'
  'CucumberKeyTags|@ConfigurationParameter(key = "cucumber.filter.tags", value = "not @slow")|"cucumber.filter.tags"'
  'CucumberKeyName|System.setProperty("cucumber.filter.name", "slow");|"cucumber.filter.name"'
  'CucumberConstTags|@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@fast")|FILTER_TAGS_PROPERTY_NAME'
  'CucumberConstName|@ConfigurationParameter(key = FILTER_NAME_PROPERTY_NAME, value = "fast")|FILTER_NAME_PROPERTY_NAME'
  'ExcludeTags|@ExcludeTags("slow")|@ExcludeTags'
  'IncludeTags|@IncludeTags("fast")|@IncludeTags'
)
# the 14 conditional-execution annotations of org.junit.jupiter.api.condition
for a in EnabledIf DisabledIf EnabledOnOs DisabledOnOs EnabledOnJre DisabledOnJre EnabledForJreRange DisabledForJreRange \
         EnabledIfSystemProperty DisabledIfSystemProperty EnabledIfEnvironmentVariable DisabledIfEnvironmentVariable \
         EnabledInNativeImage DisabledInNativeImage; do
  SRC_CASES+=("$a|@$a(\"demo\")|@$a")
done

# --- a: one construct alone fails the check (exit 1) and the message names file, line and construct
remove_temp
mk_java "$MODERN/$TEST_DIR" TempSkippedDemoSrcAlone '@Disabled("MAM-16 demo: the only construct")'
scan_only "$WORK/s1.log"; rc=$?
remove_temp
if [ $rc -eq 1 ] && grep -qF 'TempSkippedDemoSrcAlone.java:6: @Disabled (' "$WORK/s1.log" && grep -qF 'Test source check failed: 1 skip mechanism(s)' "$WORK/s1.log"; then
  pass "AC-7 source: one temporary test source with a single @Disabled makes testSourceCheck fail (exit $rc), the message names file, line and construct"
  excerpt "$WORK/s1.log" 'TempSkippedDemoSrcAlone.java:6' 1
  excerpt "$WORK/s1.log" 'cannot be skipped to get to green' 1
else
  fail "AC-7 source: one @Disabled alone (exit $rc; expected exit 1 and 'TempSkippedDemoSrcAlone.java:6: @Disabled (')" "$WORK/s1.log"
fi

# --- b: every construct, one temporary file each, in one run; each must be named with its own file, line 6 and construct
for spec in "${SRC_CASES[@]}"; do
  IFS='|' read -r name code construct <<< "$spec"
  mk_java "$MODERN/$TEST_DIR" "TempSkippedDemoSrc$name" "$code"
done
mk_java "$MODERN/$SUPPORT_DIR" TempSkippedDemoSrcSupport '@Disabled("MAM-16 demo: a file under src/main of libs/test-support is shared test support")'
printf '%s\n' 'package gov.medicaid.pem.fixedwidth' '' \
  '/* Kotlin block comments nest: /* inner */ @EnabledOnOs is still inside the outer comment */' \
  'class TempSkippedDemoSrcKotlin {' '  // the construct is on the next line (line 6)' '  @Disabled' '}' > "$MODERN/$TEST_DIR/TempSkippedDemoSrcKotlin.kt"
printf '%s\n' '# MAM-16 demonstration only: written and removed by tools/checks/demo-build-gates.sh.' '#' '#' '#' \
  '# the construct is on the next line (line 6)' 'cucumber.filter.tags=@MAM-37' > "$MODERN/$TEST_DIR/TempSkippedDemoSrcProperties.properties"
total=$(( ${#SRC_CASES[@]} + 3 ))
scan_only "$WORK/s2.log"; rc=$?
remove_temp
if [ $rc -eq 1 ] && grep -qF "Test source check failed: $total skip mechanism(s)" "$WORK/s2.log"; then
  pass "AC-7 source: $total temporary sources together make testSourceCheck fail (exit $rc) and the count is $total, one finding per source: nothing is counted twice and nothing else is flagged"
else
  fail "AC-7 source: $total temporary sources together (exit $rc; expected exit 1 and 'Test source check failed: $total skip mechanism(s)')" "$WORK/s2.log"
fi
NOT_NAMED=0
for spec in "${SRC_CASES[@]}"; do
  IFS='|' read -r name code construct <<< "$spec"
  named "$WORK/s2.log" "$construct" "TempSkippedDemoSrc$name.java" 6 "$construct"
done
named "$WORK/s2.log" "@Disabled under src/main of libs/test-support" TempSkippedDemoSrcSupport.java 6 '@Disabled'
named "$WORK/s2.log" "@Disabled in a Kotlin file" TempSkippedDemoSrcKotlin.kt 6 '@Disabled'
named "$WORK/s2.log" "cucumber.filter.tags in a properties file" TempSkippedDemoSrcProperties.properties 6 'cucumber.filter.tags'
# the negative step counts only when the Kotlin file was read (line 6 is named): a check that reads nothing must not pass it
if grep -qF 'TempSkippedDemoSrcKotlin.kt:3:' "$WORK/s2.log"; then
  fail "AC-7 source: the Kotlin nested comment on line 3 was flagged (a Kotlin block comment nests, so @EnabledOnOs there is a comment)"; NOT_NAMED=1
elif ! grep -qF 'TempSkippedDemoSrcKotlin.kt:6:' "$WORK/s2.log"; then
  fail "AC-7 source: the Kotlin file was not read at all (line 6 is not named), so line 3 cannot be judged"; NOT_NAMED=1
else
  pass "AC-7 source: the words inside a Kotlin nested block comment (line 3) are not flagged, while line 6 of the same file is"
fi
[ "$NOT_NAMED" -eq 0 ] || tail -n 40 "$WORK/s2.log" | sed 's/^/    | /'

# --- c: the same words in comments, in string literals and in files outside src/test do NOT fail the check
before="$(expected_sources)"   # the real tree, counted before any temporary source is written
cat > "$MODERN/$TEST_DIR/TempSkippedDemoQuietTest.java" <<'JAVA'
package gov.medicaid.pem.fixedwidth;

/**
 * MAM-16 demonstration only (does not compile, on purpose). Javadoc: {@code @Disabled}, assumeTrue(false), org.junit.Test,
 * cucumber.filter.tags, @EnabledOnOs, ExecutionCondition.
 */
// A line comment: @Disabled @Ignore Assumptions.assumeTrue( import org.junit.Assert; FILTER_TAGS_PROPERTY_NAME
/* A block comment
   over two lines: @ExcludeTags("slow") disabledWithoutDocker TestAbortedException */
import org.junit.jupiter.api.Test;
import org.junit.platform.suite.api.Suite;

class TempSkippedDemoQuietTest {
  // the apostrophe in this comment (don't) does not open a char literal
  String text = "@Disabled assumeTrue( org.junit.Test @EnabledIf Assumptions @Ignore";
  String block = """
      @Disabled
      assumingThat( TestAbortedException org.junit.Rule
      """;
  char quote = '"'; // a char literal that holds a double quote
  String escaped = "a \" quote and @Disabled still inside the string";
  String note = "the key cucumber.filter.tags is read from the properties file";
  @JsonIgnore String a;
  @EnableScheduling int b;
  @org.junit.jupiter.api.Test void c() {}
  boolean assumed() { return true; }
  void assume() {}
}
JAVA
cat > "$MODERN/$TEST_DIR/TempSkippedDemoQuietKotlin.kt" <<'KOTLIN'
package gov.medicaid.pem.fixedwidth

/* a /* b */ @Disabled is still inside the outer comment */
class TempSkippedDemoQuietKotlin {
  // @EnabledIf assumeTrue( org.junit.Test
  val text = "@Ignore Assumptions"
  val raw = """@EnabledIf assumeTrue( org.junit.Test"""
}
KOTLIN
printf '%s\n' '# MAM-16 demonstration only.' '# cucumber.filter.tags=@slow' '! cucumber.filter.name=slow' 'cucumber.publish.quiet=true' \
  'cucumber.junit-platform.naming-strategy.short.example-name=pickle' 'junit.jupiter.execution.timeout.testable.method.default = 6 m' \
  > "$MODERN/$TEST_DIR/TempSkippedDemoQuiet.properties"
# not read at all: a feature file (its tags are no filters) and a file outside src/test
printf '%s\n' '@MAM-37 @AC-1 @ignore @wip' 'Feature: demonstration only' '  @MAM-37 @AC-2 @disabled' '  Scenario: tags are no filter' \
  > "$MODERN/$TEST_DIR/TempSkippedDemoQuiet.feature"
printf '%s\n' 'package gov.medicaid.pem.fixedwidth;' 'import org.junit.Test;' '@Disabled class TempSkippedDemoOutside { void a() { assumeTrue(false); } String k = "cucumber.filter.tags"; }' \
  > "$MODERN/libs/fixed-width/TempSkippedDemoOutside.java"
# the Java, Kotlin and properties files above are read (3 more than the real tree); the .feature file and the one outside src/test are not
scan_only "$WORK/s3.log"; rc=$?
remove_temp
read_n="$(sed -n 's/.*testSourceCheck: read \([0-9]*\) test source files.*/\1/p' "$WORK/s3.log" | head -n 1)"
if [ $rc -eq 0 ] && [ "$read_n" = "$((before + 3))" ]; then
  pass "AC-7 source: the same words in comments, in string literals (also a text block and a char literal), in a feature file and in a file outside src/test do not fail (exit 0; read $read_n files = $before of the real tree + 3 temporary)"
  excerpt "$WORK/s3.log" 'testSourceCheck:' 1
else
  fail "AC-7 source: no false alarm (exit $rc; read '${read_n}', expected $((before + 3)))" "$WORK/s3.log"
fi

# --- d: the real tree passes and the output says how many files were read
if [ -n "$(temp_left)" ]; then
  fail "AC-7 source: a temporary source is still there before the run on the real tree: $(temp_left | head -n 3 | tr '\n' ' ')"
else
  scan_only "$WORK/s4.log"; rc=$?
  expected="$(expected_sources)"
  read_n="$(sed -n 's/.*testSourceCheck: read \([0-9]*\) test source files.*/\1/p' "$WORK/s4.log" | head -n 1)"
  if [ $rc -eq 0 ] && [ "$read_n" = "$expected" ] && [ "$read_n" -gt 0 ]; then
    pass "AC-7 source: the real tree passes testSourceCheck (exit 0) and the output says $read_n test source files were read (find counts $expected)"
    excerpt "$WORK/s4.log" 'testSourceCheck:' 1
  else
    fail "AC-7 source: the real tree (exit $rc; read '${read_n}', find counts $expected)" "$WORK/s4.log"
  fi

  # --- e: the task is in the plan of a module's check and of checkAll exactly once
  run_gradle "$WORK/s5.log" :libs:fixed-width:check --dry-run; rc=$?
  c1="$(grep -c '^:testSourceCheck ' "$WORK/s5.log")"; v1="$(grep -c '^:versionCheck ' "$WORK/s5.log")"
  if [ $rc -eq 0 ] && [ "$c1" = "1" ] && [ "$v1" = "1" ]; then
    pass "AC-7 source: :libs:fixed-width:check --dry-run plans :testSourceCheck once (and :versionCheck once)"
    excerpt "$WORK/s5.log" ':testSourceCheck ' 1
  else
    fail "AC-7 source: dry run of :libs:fixed-width:check (exit $rc; :testSourceCheck x$c1, :versionCheck x$v1; expected 1 each)" "$WORK/s5.log"
  fi
  run_gradle "$WORK/s6.log" checkAll --dry-run; rc=$?
  c2="$(grep -c '^:testSourceCheck ' "$WORK/s6.log")"; v2="$(grep -c '^:versionCheck ' "$WORK/s6.log")"
  checks="$(grep -cE '^:(libs|services|infra):[A-Za-z0-9._-]+:check ' "$WORK/s6.log")"
  modules="$(ls libs/*/build.gradle.kts services/*/build.gradle.kts infra/*/build.gradle.kts 2>/dev/null | wc -l | tr -d ' ')"
  if [ $rc -eq 0 ] && [ "$c2" = "1" ] && [ "$v2" = "1" ] && [ "$checks" = "$modules" ] && [ "$modules" -gt 0 ]; then
    pass "AC-7 source: checkAll --dry-run plans :testSourceCheck once, :versionCheck once and $checks module check tasks (modules with a build file: $modules)"
    excerpt "$WORK/s6.log" ':testSourceCheck ' 1
  else
    fail "AC-7 source: dry run of checkAll (exit $rc; :testSourceCheck x$c2, :versionCheck x$v2, module checks $checks of $modules)" "$WORK/s6.log"
  fi
fi
fi

# ---------------------------------------------------------------- nothing left behind
echo "-- clean-up"
PY="kag/.venv/Scripts/python.exe"
[ -x "$PY" ] || PY="$(command -v python3 || command -v python)"
if [ -n "$(temp_left)" ]; then
  fail "no TempSkippedDemo file may remain: $(temp_left | head -n 3 | tr '\n' ' ')"
elif [ -n "$PY" ]; then
  out="$("$PY" tools/checks/test_protection.py check MAM-42 2>&1)"
  case "$out" in OK*) pass "no file left behind: test_protection.py check MAM-42 -> $out" ;; *) fail "test_protection.py check MAM-42 -> $out" ;; esac
else
  pass "no TempSkippedDemo file remains (no Python found for test_protection.py)"
fi

echo "== waited ${WAITED} s for free memory; $FAILS step(s) failed =="
[ "$FAILS" -eq 0 ]
