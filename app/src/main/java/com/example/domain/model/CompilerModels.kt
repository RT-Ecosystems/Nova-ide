package com.example.domain.model

data class ToolchainStatus(
    val isAndroidJarReady: Boolean = false,
    val filePath: String = "",
    val fileSizeBytes: Long = 0L,
    val lastUpdatedMillis: Long = 0L,
    val isAapt2Ready: Boolean = false,
    val aapt2Path: String? = null,
    val isWorking: Boolean = false,
    val downloadProgress: Float? = null,
    val statusMessage: String? = null,
    val isError: Boolean = false
)

enum class CompilerLogLevel(val badge: String) {
    INFO("INFO"),
    WARNING("WARN"),
    ERROR("ERROR")
}

data class CompilerLogEntry(
    val level: CompilerLogLevel,
    val message: String,
    val filePath: String? = null,
    val lineNumber: Int? = null
)

data class JavaProjectOption(
    val projectName: String,
    val projectPath: String,
    val javaFileCount: Int,
    val hasManifest: Boolean
)

enum class BuildStageStatus {
    IDLE,
    RUNNING,
    SUCCESS,
    FAILED,
    SKIPPED
}

data class NativeBuildArtifact(
    val label: String,
    val fileName: String,
    val relativePath: String,
    val absolutePath: String,
    val sizeBytes: Long
)

data class KeystoreInfo(
    val isCustom: Boolean = false,
    val alias: String = "novadebug",
    val commonName: String = "Nova IDE Debug",
    val organization: String = "Nova IDE",
    val fileName: String = "nova_debug.p12",
    val filePath: String = "",
    val exists: Boolean = false,
    val isWorking: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false
)

data class JavaCompileState(
    val isCompiling: Boolean = false,
    val isBuildingApk: Boolean = false,
    val selectedProjectPath: String? = null,
    val ecjStatus: BuildStageStatus = BuildStageStatus.IDLE,
    val dexStatus: BuildStageStatus = BuildStageStatus.IDLE,
    val packageStatus: BuildStageStatus = BuildStageStatus.IDLE,
    val signStatus: BuildStageStatus = BuildStageStatus.IDLE,
    val compiledClassFiles: List<String> = emptyList(),
    val dexArtifact: NativeBuildArtifact? = null,
    val unsignedApkArtifact: NativeBuildArtifact? = null,
    val signedApkArtifact: NativeBuildArtifact? = null,
    val usedAapt2: Boolean = false,
    val logs: List<CompilerLogEntry> = emptyList(),
    val rawErrorOutput: String = "",
    val durationMs: Long? = null,
    val success: Boolean? = null,
    val errorCount: Int = 0,
    val warningCount: Int = 0,
    val isAutoFixingJava: Boolean = false
) {
    val isBusy: Boolean
        get() = isCompiling || isBuildingApk || isAutoFixingJava
}
