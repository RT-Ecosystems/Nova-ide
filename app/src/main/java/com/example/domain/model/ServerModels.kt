package com.example.domain.model

data class ServerState(
    val isRunning: Boolean = false,
    val activeProjectName: String? = null,
    val activeProjectPath: String? = null,
    val port: Int = 8080,
    val bindAddress: String = "0.0.0.0",
    val localUrl: String = "",
    val lanUrl: String? = null,
    val reloadNonce: Long = 0L,
    val errorMessage: String? = null
)

data class PreviewProjectOption(
    val projectName: String,
    val projectPath: String,
    val entryHtmlRelativePath: String,
    val htmlCount: Int
)

enum class ConsoleLogLevel(val label: String) {
    LOG("LOG"),
    INFO("INFO"),
    WARN("WARN"),
    ERROR("ERROR")
}

data class ConsoleLogEntry(
    val id: Long,
    val level: ConsoleLogLevel,
    val message: String,
    val source: String,
    val lineNumber: Int,
    val timestampMillis: Long
)
