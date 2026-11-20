package pem.build

/** A TOML value that is not a quoted string, an array or a table (a number or a boolean), kept as written. */
internal class Raw(val text: String) {
    override fun toString() = text
}

internal class TomlException(message: String) : Exception(message)

/** One `key = value` line of a table. [value] is a String, a Raw, a List or a Map (dotted keys become nested maps). */
internal class TomlEntry(val table: String, val key: String, val line: Int, val value: Any)

/**
 * The part of TOML that a Gradle version catalog uses: `[table]` headers, `key = value` with basic or literal strings,
 * inline tables (`{ module = "g:a", version.ref = "x" }`), arrays and bare values, comments, and values that span lines.
 * Multi-line strings and arrays of tables are rejected with a message instead of being guessed at.
 */
internal object Toml {
    private const val NONE = '\u0000'
    private val header = Regex("""^\[\s*([^\[\]]+?)\s*]$""")

    fun parse(text: String): List<TomlEntry> {
        val out = mutableListOf<TomlEntry>()
        val lines = text.lines()
        var table = ""
        var i = 0
        while (i < lines.size) {
            val first = i + 1
            var stmt = stripComment(lines[i]).trim()
            i++
            if (stmt.isEmpty()) continue
            while (openDepth(stmt, first) > 0 && i < lines.size) {
                stmt += " " + stripComment(lines[i]).trim()
                i++
            }
            if (openDepth(stmt, first) != 0) throw TomlException("line $first: brackets are not closed")
            if (stmt.startsWith("[")) {
                val m = header.matchEntire(stmt) ?: throw TomlException("line $first: unsupported table header '$stmt'")
                table = m.groupValues[1].trim()
                continue
            }
            val eq = indexOfOutsideStrings(stmt, '=')
            if (eq < 0) throw TomlException("line $first: expected key = value, found '$stmt'")
            val key = splitKey(stmt.substring(0, eq), first).joinToString(".")
            val parser = ValueParser(stmt.substring(eq + 1), first)
            val value = parser.parseValue()
            parser.expectEnd()
            out += TomlEntry(table, key, first, value)
        }
        return out
    }

    /** Removes a trailing `# comment` that is not inside a string. */
    private fun stripComment(line: String): String {
        var quote = NONE
        var k = 0
        while (k < line.length) {
            val c = line[k]
            when {
                quote != NONE -> {
                    if (quote == '"' && c == '\\') k++ else if (c == quote) quote = NONE
                }
                c == '"' || c == '\'' -> quote = c
                c == '#' -> return line.substring(0, k)
            }
            k++
        }
        return line
    }

    /** Number of open `{` and `[` outside strings; a string that is not closed counts as open. */
    private fun openDepth(stmt: String, line: Int): Int {
        if (stmt.contains("\"\"\"") || stmt.contains("'''")) throw TomlException("line $line: multi-line strings are not supported")
        var depth = 0
        var quote = NONE
        var k = 0
        while (k < stmt.length) {
            val c = stmt[k]
            if (quote != NONE) {
                if (quote == '"' && c == '\\') k++ else if (c == quote) quote = NONE
            } else when (c) {
                '"', '\'' -> quote = c
                '{', '[' -> depth++
                '}', ']' -> depth--
            }
            k++
        }
        if (quote != NONE) throw TomlException("line $line: a string is not closed")
        // a table header is balanced by itself; a value that starts with [ or { must close on the same statement
        return depth
    }

    private fun indexOfOutsideStrings(s: String, target: Char): Int {
        var quote = NONE
        var k = 0
        while (k < s.length) {
            val c = s[k]
            if (quote != NONE) {
                if (quote == '"' && c == '\\') k++ else if (c == quote) quote = NONE
            } else if (c == '"' || c == '\'') quote = c else if (c == target) return k
            k++
        }
        return -1
    }

    /** `a.b."c d"` into its segments. */
    fun splitKey(raw: String, line: Int): List<String> {
        val parts = mutableListOf<String>()
        val sb = StringBuilder()
        var quote = NONE
        for (c in raw.trim()) {
            if (quote != NONE) {
                if (c == quote) quote = NONE else sb.append(c)
            } else when (c) {
                '"', '\'' -> quote = c
                '.' -> { parts += sb.toString().trim(); sb.setLength(0) }
                else -> sb.append(c)
            }
        }
        parts += sb.toString().trim()
        if (parts.any { it.isEmpty() }) throw TomlException("line $line: empty key in '${raw.trim()}'")
        return parts
    }

    private class ValueParser(private val s: String, private val line: Int) {
        private var i = 0

        fun parseValue(): Any {
            skipSpace()
            if (i >= s.length) fail("a value is missing")
            return when (s[i]) {
                '"' -> basicString()
                '\'' -> literalString()
                '{' -> inlineTable()
                '[' -> array()
                else -> bare()
            }
        }

        fun expectEnd() {
            skipSpace()
            if (i < s.length) fail("unexpected text after the value: '${s.substring(i)}'")
        }

        private fun fail(msg: String): Nothing = throw TomlException("line $line: $msg")

        private fun skipSpace() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun basicString(): String {
            val sb = StringBuilder()
            i++ // opening quote
            while (i < s.length) {
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) fail("a string is not closed")
                        when (val e = s[i++]) {
                            'n' -> sb.append('\n')
                            't' -> sb.append('\t')
                            'r' -> sb.append('\r')
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            'u' -> {
                                if (i + 4 > s.length) fail("bad \\u escape")
                                sb.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> fail("unsupported escape \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
            fail("a string is not closed")
        }

        private fun literalString(): String {
            val end = s.indexOf('\'', i + 1)
            if (end < 0) fail("a string is not closed")
            val v = s.substring(i + 1, end)
            i = end + 1
            return v
        }

        private fun bare(): Raw {
            val start = i
            while (i < s.length && s[i] !in ",}] \t") i++
            if (i == start) fail("a value is missing")
            return Raw(s.substring(start, i))
        }

        private fun array(): List<Any> {
            val items = mutableListOf<Any>()
            i++ // [
            while (true) {
                skipSpace()
                if (i >= s.length) fail("an array is not closed")
                if (s[i] == ']') { i++; return items }
                items += parseValue()
                skipSpace()
                if (i < s.length && s[i] == ',') i++
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun child(map: LinkedHashMap<String, Any>, seg: String, path: List<String>): LinkedHashMap<String, Any> =
            map.getOrPut(seg) { linkedMapOf<String, Any>() } as? LinkedHashMap<String, Any>
                ?: fail("key '${path.joinToString(".")}' collides with a value")

        private fun inlineTable(): Map<String, Any> {
            val map = linkedMapOf<String, Any>()
            i++ // {
            while (true) {
                skipSpace()
                if (i >= s.length) fail("an inline table is not closed")
                if (s[i] == '}') { i++; return map }
                val keyStart = i
                var quote = NONE
                while (i < s.length && (quote != NONE || s[i] != '=')) {
                    val c = s[i]
                    if (quote != NONE) { if (c == quote) quote = NONE } else if (c == '"' || c == '\'') quote = c
                    i++
                }
                if (i >= s.length) fail("expected = in an inline table")
                val path = splitKey(s.substring(keyStart, i), line)
                i++ // =
                val value = parseValue()
                var target = map
                for (seg in path.dropLast(1)) target = child(target, seg, path)
                target[path.last()] = value
                skipSpace()
                if (i < s.length && s[i] == ',') i++
            }
        }
    }
}
