package pem.build

import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult

/**
 * MAM-16 AC-7 (tdd-workflow 4: "a test cannot be skipped to get to green"), first layer: the run-time listener.
 *
 * Collects every test whose result is SKIPPED, which is how JUnit reports a method or class annotated @Disabled, a failed
 * assumption and an unmet enabled-if or disabled-if condition. tools/checks/demo-build-gates.sh has shown the JUnit Jupiter
 * forms: a @Disabled class, a @Disabled method, a failed assumption, and a disabled parameterized test, repeated test or test
 * factory. Not shown by any run: a skipped Cucumber scenario. Its engine reports a scenario as a test result of the suite
 * class (class = the suite class, name = the scenario), so it is expected to be collected like any other test.
 *
 * Two shapes reach the listener differently. A plain test (or a class holding plain tests) is reported through [afterTest].
 * A test template (@ParameterizedTest, @RepeatedTest, @TestFactory) has no test of its own until it runs, so when it is
 * disabled, or sits in a disabled class that holds nothing else, Gradle reports only a SKIPPED suite with zero tests through
 * [afterSuite]; [afterSuite] collects those suites too. A skipped suite that does hold tests is not collected again, its
 * tests are already named by [afterTest].
 *
 * What this listener cannot see (second review, 2026-11-20): a disabled or aborted class template (annotated with
 * ParameterizedClass or ClassTemplate, JUnit 5.13 and later), which Gradle completes as a successful suite without tests, and a JUnit 4 test,
 * which no engine discovers, so it never runs and is never reported. The second layer, the root task testSourceCheck
 * (TestSourceCheck.kt, wired like versionCheck), reads the test sources and fails on the source constructs that cause these
 * cases and on every other skip mechanism. A Cucumber tag filter drops scenarios without any skip being reported; the source
 * check rejects the filter keys too.
 *
 * The listener only observes: it never changes a result. After the run [failIfAny] fails the test task and names each
 * skipped test. When tests also failed, Gradle fails the task itself with its normal output; [afterSuite] then logs the
 * skipped tests as well so that both problems are visible in one run.
 */
class SkippedTestGuard(private val taskPath: String, private val logger: Logger) : TestListener {
    private val skipped = mutableListOf<String>()

    override fun beforeSuite(suite: TestDescriptor) = Unit

    override fun beforeTest(testDescriptor: TestDescriptor) = Unit

    override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
        if (result.resultType == TestResult.ResultType.SKIPPED) {
            synchronized(skipped) { skipped += label(testDescriptor) }
        }
    }

    override fun afterSuite(suite: TestDescriptor, result: TestResult) {
        // A skipped suite that ran no test is a disabled test template (or a disabled class made only of templates). The
        // root suite (parent == null) is excluded, and a skipped suite with tests is already covered test by test in afterTest.
        if (suite.parent != null && result.resultType == TestResult.ResultType.SKIPPED && result.testCount == 0L) {
            synchronized(skipped) { skipped += label(suite) }
        }
        // The root suite is the last event of the run. Test failures make Gradle fail the task before doLast runs,
        // so in that case the skipped tests are reported here (the failure output itself stays as Gradle prints it).
        if (suite.parent == null && result.resultType == TestResult.ResultType.FAILURE) {
            message()?.let { logger.error(it) }
        }
    }

    /** Throws when at least one test was skipped; a run without skipped tests does nothing. */
    fun failIfAny() {
        message()?.let { throw GradleException(it) }
    }

    private fun message(): String? {
        val names = synchronized(skipped) { skipped.sorted() }
        if (names.isEmpty()) return null
        return buildString {
            append("${names.size} test(s) skipped in $taskPath. A skipped or @Disabled test cannot reach green (tdd-workflow 4); ")
            append("run it or delete it:")
            names.forEach { append("\n  - ").append(it) }
        }
    }

    private fun label(d: TestDescriptor): String = "${d.className ?: "(no class)"} > ${d.name}"
}
