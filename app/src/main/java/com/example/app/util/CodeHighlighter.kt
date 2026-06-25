package com.example.app.util

object CodeHighlighter {
    // VSCode Dark+ color scheme
    private const val KEYWORD = "#569cd6"
    private const val STRING = "#ce9178"
    private const val COMMENT = "#6a9955"
    private const val NUMBER = "#b5cea8"
    private const val FUNCTION = "#dcdcaa"
    private const val TYPE = "#4ec9b0"
    private const val CONSTANT = "#4fc1ff"
    private const val OPERATOR = "#d4d4d4"
    private const val PROPERTY = "#9cdcfe"
    private const val DEFAULT = "#d4d4d4"
    private const val LINENUM = "#858585"
    private const val BG = "#1e1e1e"
    private const val GUTTER_BG = "#1e1e1e"

    private val keywordsByLang = mapOf(
        "py" to setOf(
            "False", "None", "True", "and", "as", "assert", "async", "await",
            "break", "class", "continue", "def", "del", "elif", "else", "except",
            "finally", "for", "from", "global", "if", "import", "in", "is", "lambda",
            "nonlocal", "not", "or", "pass", "raise", "return", "try", "while", "with", "yield"
        ),
        "java" to setOf(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface", "long", "native", "new", "package",
            "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient",
            "try", "void", "volatile", "while", "var", "record", "sealed"
        ),
        "kt" to setOf(
            "abstract", "actual", "annotation", "as", "break", "by", "catch", "class",
            "companion", "const", "constructor", "continue", "crossinline", "data", "do",
            "dynamic", "else", "enum", "expect", "external", "final", "finally", "for",
            "fun", "get", "if", "import", "in", "infix", "init", "inline", "inner",
            "interface", "internal", "is", "lateinit", "noinline", "object", "open",
            "operator", "out", "override", "package", "private", "protected", "public",
            "reified", "return", "sealed", "set", "super", "suspend", "tailrec", "this",
            "throw", "try", "typealias", "val", "var", "vararg", "when", "where", "while"
        ),
        "c" to setOf(
            "auto", "break", "case", "char", "const", "continue", "default", "do",
            "double", "else", "enum", "extern", "float", "for", "goto", "if", "inline",
            "int", "long", "register", "restrict", "return", "short", "signed", "sizeof",
            "static", "struct", "switch", "typedef", "union", "unsigned", "void", "volatile", "while"
        ),
        "cpp" to setOf(
            "alignas", "alignof", "auto", "bool", "break", "case", "catch", "char", "class",
            "const", "constexpr", "continue", "decltype", "default", "delete", "do", "double",
            "else", "enum", "explicit", "export", "extern", "false", "float", "for", "friend",
            "goto", "if", "inline", "int", "long", "mutable", "namespace", "new", "noexcept",
            "nullptr", "operator", "override", "private", "protected", "public", "register",
            "return", "short", "signed", "sizeof", "static", "static_cast", "struct", "switch",
            "template", "this", "throw", "true", "try", "typedef", "typeid", "typename",
            "union", "unsigned", "using", "virtual", "void", "volatile", "while", "concept", "requires"
        ),
        "js" to setOf(
            "async", "await", "break", "case", "catch", "class", "const", "continue",
            "debugger", "default", "delete", "do", "else", "export", "extends", "false",
            "finally", "for", "function", "if", "import", "in", "instanceof", "let", "new",
            "null", "of", "return", "super", "switch", "this", "throw", "true", "try",
            "typeof", "undefined", "var", "void", "while", "with", "yield"
        ),
        "ts" to setOf(
            "abstract", "any", "as", "async", "await", "boolean", "break", "case", "catch",
            "class", "const", "constructor", "continue", "debugger", "declare", "default",
            "delete", "do", "else", "enum", "export", "extends", "false", "finally", "for",
            "from", "function", "if", "implements", "import", "in", "infer", "instanceof",
            "interface", "is", "keyof", "let", "module", "namespace", "never", "new", "null",
            "number", "object", "of", "private", "protected", "public", "readonly", "return",
            "string", "super", "switch", "symbol", "this", "throw", "true", "try", "type",
            "typeof", "undefined", "unique", "unknown", "var", "void", "while", "with", "yield"
        ),
        "go" to setOf(
            "break", "case", "chan", "const", "continue", "default", "defer", "else",
            "fallthrough", "for", "func", "go", "goto", "if", "import", "interface", "map",
            "package", "range", "return", "select", "struct", "switch", "type", "var",
            "bool", "byte", "complex64", "complex128", "error", "float32", "float64",
            "int", "int8", "int16", "int32", "int64", "rune", "string", "uint", "uint8",
            "uint16", "uint32", "uint64", "uintptr", "nil", "true", "false"
        ),
        "rs" to setOf(
            "as", "async", "await", "break", "const", "continue", "crate", "dyn", "else",
            "enum", "extern", "false", "fn", "for", "if", "impl", "in", "let", "loop",
            "match", "mod", "move", "mut", "pub", "ref", "return", "self", "Self", "static",
            "struct", "super", "trait", "true", "type", "union", "unsafe", "use", "where",
            "while", "i8", "i16", "i32", "i64", "u8", "u16", "u32", "u64", "f32", "f64",
            "bool", "char", "str", "String", "Vec", "Option", "Result", "Some", "None", "Ok", "Err"
        ),
        "sh" to setOf(
            "if", "then", "else", "elif", "fi", "for", "while", "do", "done", "in",
            "case", "esac", "function", "local", "export", "return", "source", "alias",
            "break", "continue", "eval", "exec", "exit", "readonly", "shift", "test", "unset"
        ),
        "sql" to setOf(
            "SELECT", "FROM", "WHERE", "INSERT", "UPDATE", "DELETE", "CREATE", "DROP",
            "ALTER", "TABLE", "INDEX", "INTO", "VALUES", "SET", "JOIN", "LEFT", "RIGHT",
            "INNER", "OUTER", "ON", "AND", "OR", "NOT", "NULL", "IS", "IN", "LIKE",
            "BETWEEN", "ORDER", "BY", "GROUP", "HAVING", "LIMIT", "OFFSET", "UNION",
            "ALL", "AS", "DISTINCT", "COUNT", "SUM", "AVG", "MIN", "MAX", "EXISTS",
            "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "CASCADE", "DEFAULT", "CHECK",
            "UNIQUE", "CONSTRAINT", "INTEGER", "VARCHAR", "TEXT", "BOOLEAN", "DATE", "TIMESTAMP"
        ),
        "css" to setOf(
            "@media", "@import", "@keyframes", "@supports", "@font-face",
            "!important", "url", "rgb", "rgba", "hsl", "hsla", "var", "calc"
        ),
        "html" to setOf(
            "DOCTYPE", "html", "head", "body", "meta", "title", "link", "script", "style",
            "div", "span", "p", "a", "img", "ul", "ol", "li", "table", "tr", "td", "th",
            "form", "input", "button", "select", "option", "textarea", "label", "h1", "h2",
            "h3", "h4", "h5", "h6", "header", "footer", "nav", "main", "section", "article",
            "aside", "br", "hr", "strong", "em", "code", "pre", "blockquote", "iframe"
        ),
        "yaml" to emptySet(),
        "json" to setOf("true", "false", "null"),
        "xml" to emptySet(),
        "md" to emptySet(),
        "csv" to emptySet(),
        "gradle" to setOf(
            "plugins", "android", "dependencies", "implementation", "api", "compileOnly",
            "runtimeOnly", "annotationProcessor", "testImplementation", "debugImplementation",
            "buildscript", "repositories", "allprojects", "configurations", "defaultConfig",
            "buildTypes", "compileOptions", "kotlinOptions", "buildFeatures", "packaging"
        ),
        "rb" to setOf(
            "alias", "and", "begin", "break", "case", "class", "def", "defined?", "do",
            "else", "elsif", "end", "ensure", "false", "for", "if", "in", "module", "next",
            "nil", "not", "or", "redo", "rescue", "retry", "return", "self", "super",
            "then", "true", "undef", "unless", "until", "when", "while", "yield"
        ),
        "php" to setOf(
            "abstract", "as", "break", "callable", "case", "catch", "class", "clone",
            "const", "continue", "declare", "default", "do", "echo", "else", "elseif",
            "empty", "enddeclare", "endfor", "endforeach", "endif", "endswitch", "endwhile",
            "extends", "final", "finally", "fn", "for", "foreach", "function", "global",
            "goto", "if", "implements", "include", "instanceof", "interface", "isset",
            "list", "match", "namespace", "new", "print", "private", "protected", "public",
            "readonly", "require", "return", "static", "switch", "throw", "trait", "try",
            "unset", "use", "var", "while", "yield", "true", "false", "null"
        ),
        "swift" to setOf(
            "associatedtype", "async", "await", "break", "case", "catch", "class", "continue",
            "default", "defer", "deinit", "do", "else", "enum", "extension", "fallthrough",
            "false", "fileprivate", "for", "func", "guard", "if", "import", "in", "init",
            "inout", "internal", "is", "let", "mutating", "nil", "open", "operator",
            "optional", "override", "private", "protocol", "public", "repeat", "rethrows",
            "return", "self", "Self", "static", "struct", "subscript", "super", "switch",
            "throw", "throws", "true", "try", "typealias", "var", "where", "while"
        )
    )

    private val builtinTypes = setOf(
        "int", "long", "float", "double", "char", "bool", "void",
        "String", "List", "Map", "Set", "Array", "Int", "Long", "Float", "Double", "Boolean",
        "ByteArray", "ShortArray", "IntArray", "LongArray", "FloatArray", "DoubleArray",
        "Unit", "Nothing", "Any", "Byte", "Short", "Char"
    )

    fun highlight(code: String, language: String): String {
        val keywords = keywordsByLang[language.lowercase()] ?: emptySet()
        val escaped = code
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        val lines = escaped.split("\n")
        val maxLineNum = lines.size.toString().length

        val highlightedLines = lines.mapIndexed { i, line ->
            val num = (i + 1).toString().padStart(maxLineNum)
            val highlighted = highlightLine(line, keywords, language.lowercase())
            """<div class="line"><span class="ln">$num</span><span class="code">$highlighted</span></div>"""
        }

        return buildHtmlDocument(language, highlightedLines.joinToString("\n"))
    }

    private fun highlightLine(line: String, keywords: Set<String>, lang: String): String {
        if (line.isBlank()) return line.ifEmpty { " " }

        val sb = StringBuilder()
        var i = 0

        while (i < line.length) {
            when {
                // Single-line comment: // or # (Python/Shell/Ruby)
                (lang != "py" && lang != "sh" && lang != "rb" && lang != "yaml" && i + 1 < line.length &&
                        line[i] == '/' && line[i + 1] == '/') ||
                (lang == "py" && line[i] == '#') ||
                (lang in listOf("sh", "rb", "yaml") && line[i] == '#') -> {
                    sb.append("""<span class="c">${line.substring(i)}</span>""")
                    return sb.toString()
                }
                // Block comment: /*
                i + 1 < line.length && line[i] == '/' && line[i + 1] == '*' -> {
                    val end = line.indexOf("*/", i + 2)
                    if (end >= 0) {
                        sb.append("""<span class="c">${line.substring(i, end + 2)}</span>""")
                        i = end + 2
                    } else {
                        sb.append("""<span class="c">${line.substring(i)}</span>""")
                        return sb.toString()
                    }
                }
                // Triple-quoted string (Python): """ or '''
                lang == "py" && i + 2 < line.length &&
                ((line[i] == '"' && line[i + 1] == '"' && line[i + 2] == '"') ||
                        (line[i] == '\'' && line[i + 1] == '\'' && line[i + 2] == '\'')) -> {
                    val quote = line.substring(i, i + 3)
                    val end = line.indexOf(quote, i + 3)
                    if (end >= 0) {
                        sb.append("""<span class="s">${line.substring(i, end + 3)}</span>""")
                        i = end + 3
                    } else {
                        sb.append("""<span class="s">${line.substring(i)}</span>""")
                        return sb.toString()
                    }
                }
                // Double-quoted string
                line[i] == '"' -> {
                    val end = findStringEnd(line, i + 1, '"')
                    sb.append("""<span class="s">${line.substring(i, end + 1)}</span>""")
                    i = end + 1
                }
                // Single-quoted string (not in prose languages like plain text)
                line[i] == '\'' && lang !in listOf("txt", "md", "csv", "plain") -> {
                    val end = findStringEnd(line, i + 1, '\'')
                    sb.append("""<span class="s">${line.substring(i, end + 1)}</span>""")
                    i = end + 1
                }
                // Backtick string (Go, JS template, Shell, Markdown)
                (lang in listOf("go", "js", "ts", "sh", "md")) && line[i] == '`' -> {
                    val end = line.indexOf('`', i + 1)
                    if (end >= 0) {
                        sb.append("""<span class="s">${line.substring(i, end + 1)}</span>""")
                        i = end + 1
                    } else {
                        sb.append("""<span class="s">${
                            line.substring(i)
                        }</span>""")
                        return sb.toString()
                    }
                }
                // Number
                line[i].isDigit() && (i == 0 || !line[i - 1].isLetterOrDigit()) -> {
                    val start = i
                    if (i + 1 < line.length && line[i] == '0' && line[i + 1] in "xX") i += 2
                    while (i < line.length && (line[i].isDigit() || line[i] in ".eE_")) i++
                    if (i < line.length && line[i] in "fFlLuU") i++
                    sb.append("""<span class="n">${line.substring(start, i)}</span>""")
                }
                // Word (potential keyword/identifier)
                line[i].isLetter() || line[i] == '_' -> {
                    val start = i
                    while (i < line.length && (line[i].isLetterOrDigit() || line[i] == '_')) i++
                    val word = line.substring(start, i)
                    when {
                        word in keywords -> sb.append("""<span class="k">$word</span>""")
                        word in builtinTypes -> sb.append("""<span class="t">$word</span>""")
                        word == "true" || word == "false" || word == "null" || word == "nil" || word == "None" ->
                            sb.append("""<span class="cn">$word</span>""")
                        // Function call: word followed by (
                        i < line.length && line[i] == '(' ->
                            sb.append("""<span class="f">$word</span>""")
                        // Annotation/decorator
                        word.startsWith("@") ->
                            sb.append("""<span class="t">$word</span>""")
                        else -> sb.append(word)
                    }
                }
                // HTML tags
                lang in listOf("html", "xml") && line[i] == '<' -> {
                    val end = line.indexOf('>', i)
                    if (end >= 0) {
                        sb.append("""<span class="k">${line.substring(i, end + 1)}</span>""")
                        i = end + 1
                    } else {
                        sb.append(line[i])
                        i++
                    }
                }
                // CSS property/value separator
                lang == "css" && line[i] == ':' -> {
                    sb.append("""<span class="k">:</span>""")
                    i++
                }
                else -> {
                    sb.append(line[i])
                    i++
                }
            }
        }
        return sb.toString()
    }

    private fun findStringEnd(line: String, start: Int, quote: Char): Int {
        var i = start
        while (i < line.length) {
            when {
                line[i] == '\\' -> i += 2
                line[i] == quote -> return i
                else -> i++
            }
        }
        // Unterminated string
        return line.length - 1
    }

    private fun buildHtmlDocument(lang: String, body: String): String = """
<!DOCTYPE html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{background:$BG;padding:12px 0;font-family:'Cascadia Code','Fira Code','JetBrains Mono','Consolas','Courier New',monospace;font-size:13px;line-height:1.55;-webkit-text-size-adjust:none}
.line{display:flex;min-height:1.55em}
.ln{color:$LINENUM;text-align:right;min-width:${if (body.lines().size > 99) "52" else "40"}px;padding-right:14px;user-select:none;-webkit-user-select:none;flex-shrink:0}
.code{flex:1;white-space:pre-wrap;word-break:break-all;color:$DEFAULT}
.k{color:$KEYWORD;font-weight:500}
.s{color:$STRING}
.c{color:$COMMENT;font-style:italic}
.n{color:$NUMBER}
.f{color:$FUNCTION}
.t{color:$TYPE}
.cn{color:$CONSTANT}
</style></head>
<body>$body</body></html>
""".trimIndent()

    fun detectLanguage(filename: String): String {
        val ext = filename.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "py" -> "py"
            "java" -> "java"
            "kt", "kts" -> "kt"
            "c", "h" -> "c"
            "cpp", "cxx", "cc", "hpp" -> "cpp"
            "js", "jsx", "mjs" -> "js"
            "ts", "tsx" -> "ts"
            "go" -> "go"
            "rs" -> "rs"
            "sh", "bash", "zsh" -> "sh"
            "sql" -> "sql"
            "css", "scss", "less" -> "css"
            "html", "htm" -> "html"
            "xml", "svg" -> "xml"
            "json" -> "json"
            "yaml", "yml" -> "yaml"
            "md" -> "md"
            "csv" -> "csv"
            "rb" -> "rb"
            "php" -> "php"
            "swift" -> "swift"
            "gradle" -> "gradle"
            "properties", "ini", "cfg" -> "properties"
            else -> "plain"
        }
    }
}
