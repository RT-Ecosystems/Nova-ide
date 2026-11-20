package com.example.core.compiler

import com.example.core.builder.WebToApkBuilder
import com.example.domain.model.CompilerLogEntry
import com.example.domain.model.CompilerLogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jdt.internal.compiler.batch.Main
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

data class JavaCompileOutput(
    val success: Boolean,
    val compiledClassFiles: List<String>,
    val logs: List<CompilerLogEntry>,
    val rawErrorOutput: String,
    val durationMs: Long,
    val errorCount: Int,
    val warningCount: Int
)

/**
 * On-device Eclipse ECJ Java 8 compiler engine.
 * Compiles `.java` sources inside `<project>/src/` against `/toolchain/android.jar`
 * into `<project>/build/classes/` on Dispatchers.IO.
 */
class JavaCompilerEngine(
    private val toolchainManager: ToolchainManager
) {

    suspend fun compileProject(
        projectDir: File,
        onLog: (CompilerLogEntry) -> Unit = {}
    ): JavaCompileOutput = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val logs = mutableListOf<CompilerLogEntry>()

        fun emit(entry: CompilerLogEntry) {
            logs.add(entry)
            onLog(entry)
        }

        if (!projectDir.exists() || !projectDir.isDirectory) {
            val err = CompilerLogEntry(
                level = CompilerLogLevel.ERROR,
                message = "Selected project folder does not exist."
            )
            emit(err)
            return@withContext failResult(logs, err.message, startedAt)
        }

        emit(CompilerLogEntry(CompilerLogLevel.INFO, "Checking workspace project: /projects/${projectDir.name}"))

        // 1. Enforce 100 MB safety guard
        val projectSize = computeProjectSourceSizeBytes(projectDir)
        if (projectSize > WebToApkBuilder.MAX_PROJECT_SIZE_BYTES) {
            val msg = "100 MB Safety Guard blocked compilation (${WebToApkBuilder.formatSize(projectSize)}). Remove large assets and retry."
            emit(CompilerLogEntry(CompilerLogLevel.ERROR, msg))
            return@withContext failResult(logs, msg, startedAt)
        }
        emit(
            CompilerLogEntry(
                CompilerLogLevel.INFO,
                "Project size verified: ${WebToApkBuilder.formatSize(projectSize)} (limit: 100 MB)"
            )
        )

        // 2. Verify /toolchain/android.jar readiness
        val androidJar = toolchainManager.getAndroidJarFile()
        if (!androidJar.exists() || !androidJar.isFile || androidJar.length() < 1024L) {
            val msg = "Missing /toolchain/android.jar. Open the SDK Toolchain Manager (Tools tab or banner above) to import or download android.jar first."
            emit(CompilerLogEntry(CompilerLogLevel.ERROR, msg))
            return@withContext failResult(logs, msg, startedAt)
        }
        emit(
            CompilerLogEntry(
                CompilerLogLevel.INFO,
                "Using android.jar (${WebToApkBuilder.formatSize(androidJar.length())})"
            )
        )

        // 3. Discover .java source files inside src/ (with project root fallback)
        val srcDir = File(projectDir, "src")
        val searchRoot = if (srcDir.exists() && srcDir.isDirectory) srcDir else projectDir
        val javaFiles = searchRoot.walkTopDown()
            .onEnter { it.name != ".git" && it.name != "build" && it.name != "node_modules" }
            .filter { it.isFile && it.extension.equals("java", ignoreCase = true) }
            .sortedBy { it.canonicalPath }
            .toList()

        if (javaFiles.isEmpty()) {
            val msg = "No .java source files found in /projects/${projectDir.name}/src/."
            emit(CompilerLogEntry(CompilerLogLevel.ERROR, msg))
            return@withContext failResult(logs, msg, startedAt)
        }

        emit(
            CompilerLogEntry(
                CompilerLogLevel.INFO,
                "Found ${javaFiles.size} Java source ${if (javaFiles.size == 1) "file" else "files"}:"
            )
        )
        javaFiles.forEach { file ->
            val rel = file.relativeTo(projectDir).path.replace('\\', '/')
            emit(CompilerLogEntry(CompilerLogLevel.INFO, "  • $rel"))
        }

        // 4. Clean and prepare build/classes/ output directory
        val classesOutputDir = File(projectDir, "build/classes")
        if (classesOutputDir.exists()) {
            classesOutputDir.deleteRecursively()
        }
        if (!classesOutputDir.mkdirs()) {
            val msg = "Unable to create output directory build/classes/."
            emit(CompilerLogEntry(CompilerLogLevel.ERROR, msg))
            return@withContext failResult(logs, msg, startedAt)
        }

        // 5. Invoke Eclipse ECJ Batch Compiler
        emit(CompilerLogEntry(CompilerLogLevel.INFO, "Running Eclipse ECJ (-source 1.8 -target 1.8)..."))

        val args = buildList {
            add("-source")
            add("1.8")
            add("-target")
            add("1.8")
            add("-encoding")
            add("UTF-8")
            add("-proc:none")
            add("-bootclasspath")
            add(androidJar.canonicalPath)
            add("-classpath")
            add(androidJar.canonicalPath)
            add("-d")
            add(classesOutputDir.canonicalPath)
            javaFiles.forEach { add(it.canonicalPath) }
        }.toTypedArray()

        val stdoutWriter = StringWriter()
        val stderrWriter = StringWriter()

        val compileSucceeded = runCatching {
            Main.compile(
                args,
                PrintWriter(stdoutWriter),
                PrintWriter(stderrWriter),
                null
            )
        }.getOrElse { throwable ->
            val msg = "ECJ internal error: ${throwable.message ?: throwable.javaClass.simpleName}"
            emit(CompilerLogEntry(CompilerLogLevel.ERROR, msg))
            return@withContext failResult(logs, msg, startedAt)
        }

        val stdoutText = stdoutWriter.toString().trim()
        val stderrText = stderrWriter.toString().trim()
        val combinedRaw = listOf(stderrText, stdoutText).filter { it.isNotBlank() }.joinToString("\n")

        // 6. Parse ECJ diagnostics into structured INFO / WARNING / ERROR entries
        val parsedDiagnostics = parseEcjDiagnostics(combinedRaw, projectDir)
        parsedDiagnostics.forEach { emit(it) }

        val classFiles = classesOutputDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("class", ignoreCase = true) }
            .map { it.relativeTo(classesOutputDir).path.replace('\\', '/') }
            .sorted()
            .toList()

        val durationMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
        val errorCount = parsedDiagnostics.count { it.level == CompilerLogLevel.ERROR }
        val warningCount = parsedDiagnostics.count { it.level == CompilerLogLevel.WARNING }

        if (compileSucceeded) {
            emit(
                CompilerLogEntry(
                    CompilerLogLevel.INFO,
                    "BUILD SUCCESSFUL: Compiled ${classFiles.size} .class ${if (classFiles.size == 1) "file" else "files"} in ${durationMs}ms → build/classes/"
                )
            )
        } else {
            val effectiveErrors = if (errorCount > 0) errorCount else 1
            emit(
                CompilerLogEntry(
                    CompilerLogLevel.ERROR,
                    "BUILD FAILED with $effectiveErrors ${if (effectiveErrors == 1) "error" else "errors"}${if (warningCount > 0) ", $warningCount warning(s)" else ""} in ${durationMs}ms."
                )
            )
        }

        JavaCompileOutput(
            success = compileSucceeded,
            compiledClassFiles = classFiles,
            logs = logs.toList(),
            rawErrorOutput = if (compileSucceeded) "" else combinedRaw,
            durationMs = durationMs,
            errorCount = if (compileSucceeded) 0 else errorCount.coerceAtLeast(1),
            warningCount = warningCount
        )
    }

    private fun parseEcjDiagnostics(rawOutput: String, projectDir: File): List<CompilerLogEntry> {
        if (rawOutput.isBlank()) return emptyList()
        val results = mutableListOf<CompilerLogEntry>()
        val projectCanonical = projectDir.canonicalPath
        val headerRegex = Regex("""^\d+\.\s+(ERROR|WARNING)\s+in\s+(.+?)\s+\(at line\s+(\d+)\)""")

        val blocks = rawOutput.split("----------")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        for (block in blocks) {
            val lines = block.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
            if (lines.isEmpty()) continue

            val firstLine = lines.first()
            val match = headerRegex.find(firstLine)
            if (match != null) {
                val severity = match.groupValues[1]
                val fullFilePath = match.groupValues[2].trim()
                val lineNumber = match.groupValues[3].toIntOrNull()
                val relFile = fullFilePath
                    .removePrefix(projectCanonical)
                    .trimStart(File.separatorChar, '/')
                    .replace('\\', '/')

                val detailLines = lines.drop(1).joinToString("\n").trim()
                val level = if (severity.equals("ERROR", ignoreCase = true)) {
                    CompilerLogLevel.ERROR
                } else {
                    CompilerLogLevel.WARNING
                }
                results.add(
                    CompilerLogEntry(
                        level = level,
                        message = if (detailLines.isNotBlank()) detailLines else firstLine,
                        filePath = relFile,
                        lineNumber = lineNumber
                    )
                )
            } else {
                val summaryLine = lines.joinToString("\n")
                val level = when {
                    summaryLine.contains("error", ignoreCase = true) && !summaryLine.contains("0 errors", ignoreCase = true) ->
                        CompilerLogLevel.ERROR
                    summaryLine.contains("warning", ignoreCase = true) ->
                        CompilerLogLevel.WARNING
                    else -> CompilerLogLevel.INFO
                }
                results.add(CompilerLogEntry(level = level, message = summaryLine))
            }
        }
        return results
    }

    private fun computeProjectSourceSizeBytes(dir: File): Long {
        fun walk(f: File): Long {
            if (f.name == ".git" || f.name == "node_modules" || f.name == "build") return 0L
            if (f.isFile) return f.length()
            var sum = 0L
            f.listFiles()?.forEach { sum += walk(it) }
            return sum
        }
        return walk(dir)
    }

    private fun failResult(
        logs: List<CompilerLogEntry>,
        rawError: String,
        startedAt: Long
    ): JavaCompileOutput = JavaCompileOutput(
        success = false,
        compiledClassFiles = emptyList(),
        logs = logs.toList(),
        rawErrorOutput = rawError,
        durationMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L),
        errorCount = 1,
        warningCount = 0
    )
}
