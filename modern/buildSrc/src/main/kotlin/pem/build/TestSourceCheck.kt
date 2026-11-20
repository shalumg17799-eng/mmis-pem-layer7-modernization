package pem.build

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * MAM-16 AC-7, second layer (tdd-workflow 4: "a test cannot be skipped to get to green"). The listener [SkippedTestGuard]
 * names a test that Gradle reports as skipped at run time. This task reads the test SOURCES and fails, naming file:line and the
 * construct, on a skip mechanism or on a test that would never run, among them the cases the listener cannot see: a disabled
 * or aborted class template (Gradle reports it as a successful suite without tests) and a JUnit 4 test (no engine for it is on
 * the class path, so it is never discovered).
 *
 * Files read: every .java, .kt and .properties file under src/test of every module, and every .java and .kt file under src/main
 * of libs/test-support (shared test support, which tools/checks/test_protection.py also treats as test code). No file outside
 * these folders is read, and no allow-list exists: a finding is fixed by running the test or deleting it.
 *
 * What counts as code (one predictable rule). In a .java or .kt file the scanner first blanks, keeping every line break,
 *  - line comments and block comments (Kotlin block comments nest, Java ones do not),
 *  - the content of string literals, text blocks and char literals. A string or char literal that is still open at the end of
 *    its line ends there, so a lexer mistake can hide at most one line.
 * What is left is matched line by line, so a construct must sit on one line (google-java-format keeps it so). The same words
 * in a comment or in a string literal therefore never fail the check. One exception, because they exist only as text: a string
 * literal that is exactly a Cucumber filter key ("cucumber.filter.tags" or "cucumber.filter.name") fails, wherever it is used.
 * In a .properties file a line fails when its key is one of those two keys and it is not a comment line (# or !).
 *
 * Fails on (the construct is named in the message):
 *  - the annotations @Disabled and JUnit 4 @Ignore, and every annotation whose simple name starts with Enabled or Disabled
 *    (the JUnit conditions such as EnabledOnOs or DisabledIfSystemProperty), qualified or not,
 *  - disabledWithoutDocker (Testcontainers) and the type ExecutionCondition,
 *  - the types Assumptions (JUnit and AssertJ), TestAbortedException and AssumptionViolatedException, and a call of a method
 *    whose name is assume or assuming followed by a capital letter (assumeTrue, assumeThat, assumingThat, and the like),
 *  - JUnit 4 and 3: any org.junit name that is not org.junit.jupiter or org.junit.platform, and junit.framework; a class,
 *    a package or the wildcard import of an IDE (import org.junit.*, import junit.framework.*) all count,
 *  - Cucumber and suite filters that drop tests without a skip being reported: the keys cucumber.filter.tags and
 *    cucumber.filter.name, the constants FILTER_TAGS_PROPERTY_NAME and FILTER_NAME_PROPERTY_NAME, @IncludeTags and @ExcludeTags.
 *    The tags that feature files carry (for example the story and AC tags) are not filters and are not read.
 * Not covered: a filter or exclusion written in a build file, in gradle.properties, in an environment variable or on the
 * command line (Gradle includeTags, excludeTestsMatching, cucumber.filter.tags given with -D).
 */
abstract class TestSourceCheck : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val testSources: ConfigurableFileCollection

    @get:Internal
    abstract val baseDirectory: DirectoryProperty

    @TaskAction
    fun verify() {
        val base = baseDirectory.get().asFile
        fun shown(f: File) = (if (f.startsWith(base)) f.relativeTo(base) else f).path.replace('\\', '/')

        val files = testSources.files.filter { it.isFile }.sortedBy { shown(it) }
        // A check that reads nothing would pass for the wrong reason (a wrong path or an empty collection).
        if (files.isEmpty()) {
            throw GradleException("Test source check failed: no test source file was found, so nothing was checked.")
        }

        val problems = mutableListOf<String>()
        val modules = mutableSetOf<String>()
        var javaFiles = 0
        var kotlinFiles = 0
        var propertyFiles = 0
        for (f in files) {
            val name = shown(f)
            val text = f.readText()
            val found = when {
                name.endsWith(".properties") -> { propertyFiles++; TestSourceScan.scanProperties(text) }
                name.endsWith(".kt") -> { kotlinFiles++; TestSourceScan.scanSource(text, isKotlin = true) }
                else -> { javaFiles++; TestSourceScan.scanSource(text, isKotlin = false) }
            }
            modules += name.substringBefore("/src/")
            found.mapTo(problems) { "$name:${it.line}: ${it.construct} (${it.reason}): ${it.source}" }
        }

        if (problems.isNotEmpty()) {
            throw GradleException(
                buildString {
                    append("Test source check failed: ${problems.size} skip mechanism(s) in the test sources. ")
                    append("A test cannot be skipped to get to green (tdd-workflow 4, MAM-16 AC-7): run the test or delete it; ")
                    append("do not disable it, make it conditional, abort it with an assumption, write it for JUnit 4 or filter it out.")
                    problems.forEach { append("\n  - ").append(it) }
                }
            )
        }
        logger.lifecycle(
            "testSourceCheck: read ${files.size} test source files ($javaFiles Java, $kotlinFiles Kotlin, $propertyFiles properties) in " +
                "${modules.size} modules: no @Disabled, @Ignore, conditional execution, assumption, JUnit 3 or 4 class, or " +
                "Cucumber tag filter."
        )
    }
}

/** One skip mechanism found in a file: the line (from 1), the construct, why it is rejected, and the source line. */
internal class SkipFinding(val line: Int, val construct: String, val reason: String, val source: String)

/** The text of a source file with comments and the content of literals blanked, and the string literals themselves. */
internal class Lexed(val code: List<String>, val literals: List<Literal>)

internal class Literal(val line: Int, val value: String)

internal object SourceLexer {
    /** Blanks comments and literal content (line breaks and offsets are kept) and collects the string literals. */
    fun lex(text: String, isKotlin: Boolean): Lexed {
        val n = text.length
        val out = StringBuilder(n)
        val literals = mutableListOf<Literal>()
        var i = 0
        var line = 1

        fun at(k: Int): Char = if (k < n) text[k] else '\u0000'

        // consumes one character and writes a blank in its place (a line break stays a line break); returns the character
        fun blank(): Char {
            val c = text[i]
            if (c == '\n') line++
            out.append(if (c == '\n') '\n' else ' ')
            i++
            return c
        }

        while (i < n) {
            val c = text[i]
            when {
                c == '/' && at(i + 1) == '/' -> while (i < n && text[i] != '\n') blank()
                c == '/' && at(i + 1) == '*' -> {
                    blank(); blank()
                    var depth = 1
                    while (i < n && depth > 0) {
                        when {
                            text[i] == '*' && at(i + 1) == '/' -> { blank(); blank(); depth-- }
                            isKotlin && text[i] == '/' && at(i + 1) == '*' -> { blank(); blank(); depth++ }
                            else -> blank()
                        }
                    }
                }
                c == '"' && at(i + 1) == '"' && at(i + 2) == '"' -> { // text block (Java) or raw string (Kotlin)
                    val startLine = line
                    val value = StringBuilder()
                    out.append("\"\"\""); i += 3
                    while (i < n && !(text[i] == '"' && at(i + 1) == '"' && at(i + 2) == '"')) {
                        if (!isKotlin && text[i] == '\\' && i + 1 < n) value.append(blank()) // a Java escape: the next character is content
                        value.append(blank())
                    }
                    if (i < n) { out.append("\"\"\""); i += 3 }
                    literals += Literal(startLine, value.toString())
                }
                c == '"' || c == '\'' -> { // string or char literal: ends at its closing quote or at the end of the line
                    val startLine = line
                    val value = StringBuilder()
                    out.append(c); i++
                    while (i < n && text[i] != '\n' && text[i] != c) {
                        if (text[i] == '\\' && i + 1 < n && text[i + 1] != '\n') value.append(blank())
                        value.append(blank())
                    }
                    if (i < n && text[i] == c) { out.append(c); i++ }
                    if (c == '"') literals += Literal(startLine, value.toString())
                }
                else -> { out.append(c); i++; if (c == '\n') line++ }
            }
        }
        return Lexed(out.toString().split('\n'), literals)
    }
}

internal object TestSourceScan {
    private const val FILTER_KEYS_REASON = "a Cucumber filter drops scenarios without any being reported as skipped"
    private val filterKeys = setOf("cucumber.filter.tags", "cucumber.filter.name")

    private class Hit(val construct: String, val reason: String)

    private class Rule(pattern: String, val hit: (MatchResult) -> Hit) {
        val regex = Regex(pattern)
    }

    // an annotation, qualified or not: @Name, @a.b.Name
    private const val ANNOTATION = """@\s*(?:[A-Za-z_]\w*\s*\.\s*)*"""

    private val rules = listOf(
        Rule(ANNOTATION + """((?:Enabled|Disabled)\w*)""") { m ->
            val name = m.groupValues[1]
            if (name == "Disabled") Hit("@Disabled", "the test is switched off")
            else Hit("@$name", "conditional execution: the test is skipped when its condition is not met")
        },
        Rule(ANNOTATION + """Ignore\b""") { Hit("@Ignore", "JUnit 4 @Ignore: the test is switched off") },
        Rule("""\bdisabledWithoutDocker\b""") {
            Hit("disabledWithoutDocker", "the Testcontainers tests are skipped when Docker is missing")
        },
        Rule("""\bExecutionCondition\b""") { Hit("ExecutionCondition", "a custom execution condition can skip any test") },
        Rule("""\b(Assumptions|TestAbortedException|AssumptionViolatedException)\b|\b(assum(?:e|ing)[A-Z]\w*)\s*\(""") { m ->
            val type = m.groups[1]?.value
            Hit(type ?: (m.groups[2]!!.value + "("), "an assumption or an abort reports the test as skipped")
        },
        Rule("""(?<![\w.])org\.junit\.(?!jupiter\b|platform\b)(\w+|\*)""") { m ->
            Hit("org.junit." + m.groupValues[1], "JUnit 4: no engine for it is on the class path, so the test is never discovered or run")
        },
        Rule("""(?<![\w.])junit\.framework\.(\w+|\*)""") { m ->
            Hit("junit.framework." + m.groupValues[1], "JUnit 3: no engine for it is on the class path, so the test is never discovered or run")
        },
        Rule("""(@?)\b(FILTER_TAGS_PROPERTY_NAME|FILTER_NAME_PROPERTY_NAME|IncludeTags|ExcludeTags)\b""") { m ->
            Hit(m.groupValues[1] + m.groupValues[2], "a tag or name filter drops tests without any being reported as skipped")
        },
    )

    /** The skip mechanisms of one .java or .kt file, in line order. */
    fun scanSource(text: String, isKotlin: Boolean): List<SkipFinding> {
        val lexed = SourceLexer.lex(text, isKotlin)
        val original = text.split('\n')
        val found = mutableListOf<SkipFinding>()

        fun add(line: Int, hit: Hit) {
            found += SkipFinding(line, hit.construct, hit.reason, original.getOrElse(line - 1) { "" }.trim().take(110))
        }

        lexed.code.forEachIndexed { index, codeLine ->
            for (rule in rules) rule.regex.find(codeLine)?.let { add(index + 1, rule.hit(it)) }
        }
        for (literal in lexed.literals) {
            if (literal.value in filterKeys) add(literal.line, Hit("\"${literal.value}\"", FILTER_KEYS_REASON))
        }
        return found.sortedBy { it.line }
    }

    /** The skip mechanisms of one .properties file: a Cucumber filter key on a line that is not a comment. */
    fun scanProperties(text: String): List<SkipFinding> {
        val found = mutableListOf<SkipFinding>()
        text.split('\n').forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) return@forEachIndexed
            val key = line.takeWhile { it != '=' && it != ':' && !it.isWhitespace() }
            if (key in filterKeys) found += SkipFinding(index + 1, key, FILTER_KEYS_REASON, line.take(110))
        }
        return found
    }
}
