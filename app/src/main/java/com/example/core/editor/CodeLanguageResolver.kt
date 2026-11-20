package com.example.core.editor

import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.analysis.SimpleAnalyzeManager
import io.github.rosemoe.sora.lang.styling.MappedSpans
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.langs.java.JavaLanguage
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

object CodeLanguageResolver {

    fun languageLabelFor(extension: String): String = when (extension.lowercase()) {
        "kt", "kts" -> "Kotlin"
        "java" -> "Java"
        "html", "htm" -> "HTML"
        "css", "scss" -> "CSS"
        "js", "mjs", "cjs", "jsx" -> "JavaScript"
        "ts", "tsx" -> "TypeScript"
        "py" -> "Python"
        "json" -> "JSON"
        "md", "markdown" -> "Markdown"
        "xml" -> "XML"
        "yml", "yaml" -> "YAML"
        "sh" -> "Shell"
        "sql" -> "SQL"
        "gradle" -> "Gradle"
        else -> "Plain Text"
    }

    fun resolveLanguage(extension: String): Language {
        return when (extension.lowercase()) {
            "java" -> JavaLanguage()
            "kt", "kts" -> TokenHighlightLanguage(SyntaxProfile.KOTLIN)
            "js", "mjs", "cjs", "jsx", "ts", "tsx" -> TokenHighlightLanguage(SyntaxProfile.JAVASCRIPT)
            "py" -> TokenHighlightLanguage(SyntaxProfile.PYTHON)
            "html", "htm", "xml" -> TokenHighlightLanguage(SyntaxProfile.MARKUP)
            "css", "scss" -> TokenHighlightLanguage(SyntaxProfile.CSS)
            "json" -> TokenHighlightLanguage(SyntaxProfile.JSON)
            "md", "markdown" -> TokenHighlightLanguage(SyntaxProfile.MARKDOWN)
            else -> EmptyLanguage()
        }
    }
}

private enum class SyntaxProfile(
    val keywords: Set<String>,
    val lineCommentPrefix: String?
) {
    KOTLIN(
        keywords = setOf(
            "package", "import", "class", "interface", "object", "fun", "val", "var",
            "if", "else", "when", "for", "while", "do", "return", "break", "continue",
            "try", "catch", "finally", "throw", "in", "is", "as", "data", "sealed",
            "enum", "override", "private", "public", "protected", "internal", "open",
            "suspend", "companion", "true", "false", "null", "this", "super"
        ),
        lineCommentPrefix = "//"
    ),
    JAVASCRIPT(
        keywords = setOf(
            "const", "let", "var", "function", "return", "if", "else", "switch", "case",
            "for", "while", "do", "break", "continue", "class", "extends", "new", "try",
            "catch", "finally", "throw", "async", "await", "import", "export", "from",
            "default", "typeof", "instanceof", "true", "false", "null", "undefined", "this",
            "interface", "type"
        ),
        lineCommentPrefix = "//"
    ),
    PYTHON(
        keywords = setOf(
            "def", "class", "return", "if", "elif", "else", "for", "while", "break",
            "continue", "pass", "import", "from", "as", "try", "except", "finally",
            "raise", "with", "yield", "lambda", "and", "or", "not", "in", "is",
            "True", "False", "None", "async", "await", "global", "nonlocal"
        ),
        lineCommentPrefix = "#"
    ),
    MARKUP(
        keywords = emptySet(),
        lineCommentPrefix = null
    ),
    CSS(
        keywords = setOf("important", "root", "media", "keyframes", "from", "to", "var", "calc"),
        lineCommentPrefix = "//"
    ),
    JSON(
        keywords = setOf("true", "false", "null"),
        lineCommentPrefix = null
    ),
    MARKDOWN(
        keywords = emptySet(),
        lineCommentPrefix = null
    )
}

private class TokenHighlightLanguage(
    private val profile: SyntaxProfile
) : EmptyLanguage() {

    private val analyzer = RegexSyntaxAnalyzeManager(profile)

    override fun getAnalyzeManager(): AnalyzeManager = analyzer
}

private class RegexSyntaxAnalyzeManager(
    private val profile: SyntaxProfile
) : SimpleAnalyzeManager<Unit>() {

    override fun analyze(text: StringBuilder, delegate: Delegate<Unit>): Styles {
        val styles = Styles()
        val raw = text.toString()
        val lines = raw.split('\n')
        val builder = MappedSpans.Builder(lines.size.coerceAtLeast(1))

        val normalStyle = TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)
        val keywordStyle = TextStyle.makeStyle(EditorColorScheme.KEYWORD, 0, true, false, false)
        val stringStyle = TextStyle.makeStyle(EditorColorScheme.LITERAL)
        val commentStyle = TextStyle.makeStyle(EditorColorScheme.COMMENT, 0, false, true, false)
        val numberStyle = TextStyle.makeStyle(EditorColorScheme.ANNOTATION)
        val varStyle = TextStyle.makeStyle(EditorColorScheme.IDENTIFIER_VAR)
        val funcStyle = TextStyle.makeStyle(EditorColorScheme.FUNCTION_NAME)

        for (lineIdx in lines.indices) {
            if (delegate.isCancelled) break
            val line = lines[lineIdx]
            if (line.isEmpty()) {
                builder.addNormalIfNull()
                continue
            }

            when (profile) {
                SyntaxProfile.MARKDOWN -> highlightMarkdownLine(lineIdx, line, builder, normalStyle, keywordStyle, stringStyle, commentStyle)
                SyntaxProfile.MARKUP -> highlightMarkupLine(lineIdx, line, builder, normalStyle, keywordStyle, stringStyle, commentStyle, varStyle)
                SyntaxProfile.JSON -> highlightJsonLine(lineIdx, line, builder, normalStyle, keywordStyle, stringStyle, numberStyle, varStyle)
                SyntaxProfile.CSS -> highlightCssLine(lineIdx, line, builder, normalStyle, keywordStyle, stringStyle, commentStyle, numberStyle, varStyle)
                else -> highlightCodeLine(lineIdx, line, builder, normalStyle, keywordStyle, stringStyle, commentStyle, numberStyle, varStyle, funcStyle)
            }
            builder.determine(lineIdx)
        }

        builder.addNormalIfNull()
        styles.spans = builder.build()
        return styles
    }

    private fun highlightCodeLine(
        lineIdx: Int,
        line: String,
        builder: MappedSpans.Builder,
        normal: Long,
        keyword: Long,
        string: Long,
        comment: Long,
        number: Long,
        variable: Long,
        func: Long
    ) {
        val len = line.length
        var i = 0
        val commentPrefix = profile.lineCommentPrefix

        while (i < len) {
            if (commentPrefix != null && line.startsWith(commentPrefix, i)) {
                builder.addIfNeeded(lineIdx, i, comment)
                return
            }

            val c = line[i]
            when {
                c == '"' || c == '\'' || c == '`' -> {
                    builder.addIfNeeded(lineIdx, i, string)
                    val quote = c
                    i++
                    while (i < len) {
                        if (line[i] == '\\' && i + 1 < len) {
                            i += 2
                        } else if (line[i] == quote) {
                            i++
                            break
                        } else {
                            i++
                        }
                    }
                    if (i < len) builder.addIfNeeded(lineIdx, i, normal)
                }

                c == '@' -> {
                    builder.addIfNeeded(lineIdx, i, number)
                    i++
                    while (i < len && (line[i].isLetterOrDigit() || line[i] == '_')) i++
                    if (i < len) builder.addIfNeeded(lineIdx, i, normal)
                }

                c.isDigit() -> {
                    builder.addIfNeeded(lineIdx, i, number)
                    while (i < len && (line[i].isLetterOrDigit() || line[i] == '.' || line[i] == '_')) i++
                    if (i < len) builder.addIfNeeded(lineIdx, i, normal)
                }

                c.isLetter() || c == '_' -> {
                    val start = i
                    while (i < len && (line[i].isLetterOrDigit() || line[i] == '_')) i++
                    val word = line.substring(start, i)
                    val style = when {
                        word in profile.keywords -> keyword
                        i < len && line[i] == '(' -> func
                        word.first().isUpperCase() -> variable
                        else -> normal
                    }
                    builder.addIfNeeded(lineIdx, start, style)
                    if (style != normal && i < len) {
                        builder.addIfNeeded(lineIdx, i, normal)
                    }
                }

                else -> {
                    builder.addIfNeeded(lineIdx, i, normal)
                    i++
                }
            }
        }
    }

    private fun highlightMarkupLine(
        lineIdx: Int,
        line: String,
        builder: MappedSpans.Builder,
        normal: Long,
        keyword: Long,
        string: Long,
        comment: Long,
        attr: Long
    ) {
        val len = line.length
        var i = 0
        while (i < len) {
            if (line.startsWith("<!--", i)) {
                builder.addIfNeeded(lineIdx, i, comment)
                val end = line.indexOf("-->", i + 4)
                if (end == -1) return
                i = end + 3
                if (i < len) builder.addIfNeeded(lineIdx, i, normal)
                continue
            }
            if (line[i] == '<') {
                builder.addIfNeeded(lineIdx, i, keyword)
                i++
                if (i < len && (line[i] == '/' || line[i] == '!')) i++
                while (i < len && (line[i].isLetterOrDigit() || line[i] == '-' || line[i] == ':')) i++
                while (i < len && line[i] != '>') {
                    when {
                        line[i] == '"' || line[i] == '\'' -> {
                            val q = line[i]
                            builder.addIfNeeded(lineIdx, i, string)
                            i++
                            while (i < len && line[i] != q) i++
                            if (i < len) i++
                            if (i < len) builder.addIfNeeded(lineIdx, i, normal)
                        }
                        line[i].isLetter() -> {
                            builder.addIfNeeded(lineIdx, i, attr)
                            while (i < len && (line[i].isLetterOrDigit() || line[i] == '-' || line[i] == ':')) i++
                            if (i < len) builder.addIfNeeded(lineIdx, i, normal)
                        }
                        else -> {
                            builder.addIfNeeded(lineIdx, i, normal)
                            i++
                        }
                    }
                }
                if (i < len && line[i] == '>') {
                    builder.addIfNeeded(lineIdx, i, keyword)
                    i++
                    if (i < len) builder.addIfNeeded(lineIdx, i, normal)
                }
            } else {
                builder.addIfNeeded(lineIdx, i, normal)
                i++
            }
        }
    }

    private fun highlightCssLine(
        lineIdx: Int,
        line: String,
        builder: MappedSpans.Builder,
        normal: Long,
        keyword: Long,
        string: Long,
        comment: Long,
        number: Long,
        prop: Long
    ) {
        val trimmed = line.trimStart()
        if (trimmed.startsWith("/*") || trimmed.startsWith("*")) {
            builder.addIfNeeded(lineIdx, 0, comment)
            return
        }
        val colonIdx = line.indexOf(':')
        if (colonIdx > 0 && !trimmed.startsWith("@") && !line.contains('{')) {
            builder.addIfNeeded(lineIdx, 0, prop)
            builder.addIfNeeded(lineIdx, colonIdx, normal)
            builder.addIfNeeded(lineIdx, colonIdx + 1, string)
        } else if (line.contains('{') || line.contains('}')) {
            builder.addIfNeeded(lineIdx, 0, keyword)
        } else if (trimmed.startsWith("@")) {
            builder.addIfNeeded(lineIdx, 0, number)
        } else {
            builder.addIfNeeded(lineIdx, 0, normal)
        }
    }

    private fun highlightJsonLine(
        lineIdx: Int,
        line: String,
        builder: MappedSpans.Builder,
        normal: Long,
        keyword: Long,
        string: Long,
        number: Long,
        keyStyle: Long
    ) {
        val len = line.length
        var i = 0
        while (i < len) {
            val c = line[i]
            when {
                c == '"' -> {
                    val start = i
                    i++
                    while (i < len) {
                        if (line[i] == '\\' && i + 1 < len) i += 2
                        else if (line[i] == '"') {
                            i++
                            break
                        } else i++
                    }
                    val lookahead = line.substring(i).trimStart()
                    val style = if (lookahead.startsWith(":")) keyStyle else string
                    builder.addIfNeeded(lineIdx, start, style)
                    if (i < len) builder.addIfNeeded(lineIdx, i, normal)
                }
                c.isDigit() || c == '-' -> {
                    builder.addIfNeeded(lineIdx, i, number)
                    while (i < len && (line[i].isDigit() || line[i] == '.' || line[i] == '-' || line[i] == 'e' || line[i] == 'E')) i++
                    if (i < len) builder.addIfNeeded(lineIdx, i, normal)
                }
                c.isLetter() -> {
                    val start = i
                    while (i < len && line[i].isLetter()) i++
                    val word = line.substring(start, i)
                    val style = if (word in profile.keywords) keyword else normal
                    builder.addIfNeeded(lineIdx, start, style)
                    if (i < len) builder.addIfNeeded(lineIdx, i, normal)
                }
                else -> {
                    builder.addIfNeeded(lineIdx, i, normal)
                    i++
                }
            }
        }
    }

    private fun highlightMarkdownLine(
        lineIdx: Int,
        line: String,
        builder: MappedSpans.Builder,
        normal: Long,
        keyword: Long,
        string: Long,
        comment: Long
    ) {
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("#") -> builder.addIfNeeded(lineIdx, 0, keyword)
            trimmed.startsWith("```") -> builder.addIfNeeded(lineIdx, 0, string)
            trimmed.startsWith(">") -> builder.addIfNeeded(lineIdx, 0, comment)
            else -> builder.addIfNeeded(lineIdx, 0, normal)
        }
    }
}
