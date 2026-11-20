package pem.build

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * MAM-16 AC-8: every version of the build is pinned exactly. Fails, naming the alias (or file and line) and the value, on
 *  - a version that contains "+", the word "latest", or a range ("[", "]", "(", ")" or a comma),
 *  - an empty version,
 *  - a version.ref that does not exist in [versions],
 *  - a plugin of the catalog without a version,
 *  - the same kinds of dynamic version written inline in a build file (buildSrc, root, settings, every module).
 * A library of the catalog with no version at all is deliberately managed by a BOM and is allowed (and listed).
 * The catalog is gradle/libs.versions.toml; -PpemVersionCatalog=<path> points the check at another file (the demonstration).
 */
abstract class VersionCheck : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val catalog: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val buildFiles: ConfigurableFileCollection

    @get:Internal
    abstract val baseDirectory: DirectoryProperty

    @TaskAction
    fun verify() {
        val base = baseDirectory.get().asFile
        // a path relative to modern/ for files of the build; the full path for a catalog given with -PpemVersionCatalog elsewhere
        fun shown(f: File) = (if (f.startsWith(base)) f.relativeTo(base) else f).path.replace('\\', '/')

        val catalogFile = catalog.get().asFile
        val report = CatalogVersions.check(catalogFile.readText())
        val problems = report.problems.map { "${shown(catalogFile)}: $it" }.toMutableList()
        val files = buildFiles.files.filter { it.isFile }.sortedBy { it.path }
        files.forEach { f -> InlineVersions.scan(f.readText()).mapTo(problems) { "${shown(f)}:$it" } }

        if (problems.isNotEmpty()) {
            throw GradleException(
                buildString {
                    append("Version check failed: ${problems.size} dynamic, empty or missing version(s). ")
                    append("Every version must be exact (MAM-16 AC-8); pin it in gradle/libs.versions.toml and record it in ")
                    append("docs/adr/ADR-010-versions.md.")
                    problems.forEach { append("\n  - ").append(it) }
                }
            )
        }
        logger.lifecycle(
            "versionCheck: ${report.versions} versions, ${report.libraries} libraries, ${report.plugins} plugins in " +
                "${shown(catalogFile)} and ${files.size} build files: every version is exact; " +
                "${report.bomManaged.size} libraries have no version (managed by a BOM)."
        )
    }
}

/** What counts as a dynamic version. */
internal object Dynamic {
    fun reasons(version: String): List<String> {
        if (version.isBlank()) return listOf("is empty")
        val out = mutableListOf<String>()
        if ('+' in version) out += "contains '+' (a dynamic version)"
        if (version.contains("latest", ignoreCase = true)) out += "contains 'latest' (a dynamic version)"
        if (version.any { it in "[](),"}) out += "is a version range"
        return out
    }
}

internal class CatalogReport(
    val problems: List<String>,
    val versions: Int,
    val libraries: Int,
    val plugins: Int,
    val bomManaged: List<String>,
)

/** Checks the text of a version catalog (the [versions], [libraries] and [plugins] tables). */
internal object CatalogVersions {
    private val richKeys = listOf("require", "strictly", "prefer")

    fun check(text: String): CatalogReport {
        val entries = try {
            Toml.parse(text)
        } catch (e: TomlException) {
            return CatalogReport(listOf("cannot read the catalog: ${e.message}"), 0, 0, 0, emptyList())
        }
        val problems = mutableListOf<String>()
        val versions = entries.filter { it.table == "versions" }
        val libraries = entries.filter { it.table == "libraries" }
        val plugins = entries.filter { it.table == "plugins" }
        val declared = versions.map { it.key }.toSet()
        val bomManaged = mutableListOf<String>()

        fun add(e: TomlEntry, what: String, shown: String?, reason: String) {
            problems += "[${e.table}] ${e.key}${if (shown != null) " $what $shown" else ""} (line ${e.line}): $reason"
        }

        fun pinned(e: TomlEntry, what: String, value: Any) {
            when (value) {
                is String -> Dynamic.reasons(value).forEach { add(e, what, "\"$value\"", it) }
                is Map<*, *> -> {
                    val pins = richKeys.mapNotNull { k -> value[k]?.let { k to it } }
                    if (pins.isEmpty()) add(e, what, null, "has no exact version (needs require, strictly or prefer)")
                    for ((k, v) in pins) {
                        if (v is String) Dynamic.reasons(v).forEach { add(e, "$what $k", "\"$v\"", it) }
                        else add(e, "$what $k", null, "must be a quoted string")
                    }
                }
                else -> add(e, what, null, "must be a quoted string, found '$value'")
            }
        }

        fun versionOf(e: TomlEntry, value: Any?, missing: () -> Unit) {
            if (value == null) { missing(); return }
            if (value is Map<*, *> && value.containsKey("ref")) {
                val ref = value["ref"]
                when {
                    ref !is String -> add(e, "version.ref", null, "must be a quoted string")
                    ref.isBlank() -> add(e, "version.ref", "\"$ref\"", "is empty")
                    ref !in declared -> add(e, "version.ref", "\"$ref\"", "does not exist in [versions]")
                }
            } else {
                pinned(e, "version", value)
            }
        }

        versions.forEach { pinned(it, "=", it.value) }

        for (e in libraries) {
            when (val v = e.value) {
                is String -> { // "group:name" or "group:name:version"
                    val parts = v.split(":")
                    when {
                        parts.size < 2 -> add(e, "=", "\"$v\"", "must be group:name or group:name:version")
                        parts.size == 2 -> bomManaged += e.key
                        else -> Dynamic.reasons(parts[2]).forEach { add(e, "version", "\"${parts[2]}\"", it) }
                    }
                }
                is Map<*, *> -> versionOf(e, v["version"]) { bomManaged += e.key }
                else -> add(e, "=", null, "must be a string or an inline table")
            }
        }

        for (e in plugins) {
            when (val v = e.value) {
                is String -> { // "id:version"
                    val parts = v.split(":")
                    if (parts.size < 2) add(e, "=", "\"$v\"", "has no version") else Dynamic.reasons(parts[1]).forEach { add(e, "version", "\"${parts[1]}\"", it) }
                }
                is Map<*, *> -> versionOf(e, v["version"]) { add(e, "version", null, "a plugin has no version") }
                else -> add(e, "=", null, "must be a string or an inline table")
            }
        }
        return CatalogReport(problems, versions.size, libraries.size, plugins.size, bomManaged)
    }
}

/** Looks for dynamic versions written inline in Kotlin DSL build files. */
internal object InlineVersions {
    // "group:artifact:version" (optionally :classifier and @ext), the dependency string notation
    private val coordinate = Regex("\"([A-Za-z0-9_.\\-]+):([A-Za-z0-9_.\\-]+):([^\"\\s:@]*)(?::[A-Za-z0-9_.\\-]+)?(?:@[A-Za-z0-9]+)?\"")

    // version = "x", toolVersion = "x", id("p") version "x", version("x"), strictly("x"), require("x"), prefer("x")
    private val assignment = Regex("(?:\\b[A-Za-z0-9_]*[Vv]ersion\\s*=\\s*|\\bversion\\s+|\\bversion\\s*\\(\\s*|\\b(?:strictly|require|prefer)\\s*\\(\\s*)\"([^\"]*)\"")

    /** One message "<line>: ..." per dynamic version. */
    fun scan(text: String): List<String> {
        val out = mutableListOf<String>()
        text.lines().forEachIndexed { index, raw ->
            val line = withoutComment(raw)
            for (m in coordinate.findAll(line)) {
                val version = m.groupValues[3]
                if (version.startsWith("$")) continue // interpolated from a variable: the catalog check covers the value
                Dynamic.reasons(version).forEach { out += "${index + 1}: inline version \"$version\" in \"${m.value.trim('"')}\" $it" }
            }
            for (m in assignment.findAll(line)) {
                val version = m.groupValues[1]
                if (version.contains("$")) continue
                Dynamic.reasons(version).forEach { out += "${index + 1}: inline version \"$version\" in ${m.value.trim()} $it" }
            }
        }
        return out
    }

    /** The line without a trailing // comment (a // inside a string, as in a URL, is kept). */
    private fun withoutComment(line: String): String {
        var inString = false
        var k = 0
        while (k < line.length) {
            val c = line[k]
            if (c == '\\' && inString) k++
            else if (c == '"') inString = !inString
            else if (!inString && c == '/' && k + 1 < line.length && line[k + 1] == '/') return line.substring(0, k)
            k++
        }
        return line
    }
}
