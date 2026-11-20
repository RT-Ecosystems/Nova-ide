package com.example.domain.model

data class WorkspaceItem(
    val name: String,
    val absolutePath: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val extension: String,
    val isGitRepo: Boolean = false,
    val childCount: Int = 0
) {
    val isCodeEditable: Boolean
        get() = !isDirectory && (extension.lowercase() in SUPPORTED_CODE_EXTENSIONS || name.startsWith("."))

    companion object {
        val SUPPORTED_CODE_EXTENSIONS = setOf(
            "html", "htm", "css", "scss", "js", "mjs", "cjs", "jsx",
            "ts", "tsx", "kt", "kts", "java", "py", "json", "md",
            "markdown", "xml", "yaml", "yml", "txt", "sh", "gradle",
            "properties", "toml", "sql", "env", "c", "cpp", "h", "rs", "go"
        )
    }
}

enum class ProjectTemplate(
    val displayName: String,
    val description: String,
    val badge: String
) {
    WEB_STARTER("Web App", "HTML5 + CSS3 + ES6 starter", "HTML/JS"),
    ANDROID_JAVA_APP("Android Java App", "Manifest + MainActivity.java + strings.xml", "JAVA/APK"),
    KOTLIN_STARTER("Kotlin Module", "Main.kt + README.md", "KT"),
    PYTHON_STARTER("Python Script", "main.py + requirements.txt", "PY"),
    EMPTY("Empty Project", "Blank workspace directory", "EMPTY")
}

data class BreadcrumbSegment(
    val label: String,
    val relativePath: String
)
