package com.example.core.compiler

import com.android.dx.command.dexer.DxContext
import com.android.dx.command.dexer.Main
import com.example.core.builder.WebToApkBuilder
import com.example.domain.model.CompilerLogEntry
import com.example.domain.model.CompilerLogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets

data class DexCompileOutput(
    val success: Boolean,
    val dexFile: File?,
    val dexSizeBytes: Long,
    val logs: List<CompilerLogEntry>,
    val durationMs: Long
)

/**
 * Phase 7 On-Device DEX Compiler Engine powered by dalvik-dx.
 * Converts compiled Java 8 `.class` bytecode in `<project>/build/classes/`
 * into Dalvik executable `<project>/build/dex/classes.dex` on Dispatchers.IO.
 */
class DexCompilerEngine {

    suspend fun compileClassesToDex(
        projectDir: File,
        onLog: (CompilerLogEntry) -> Unit = {}
    ): DexCompileOutput = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val logs = mutableListOf<CompilerLogEntry>()

        fun emit(entry: CompilerLogEntry) {
            logs.add(entry)
            onLog(entry)
        }

        val classesDir = File(projectDir, "build/classes")
        if (!classesDir.exists() || !classesDir.isDirectory) {
            val msg = "No compiled classes found in build/classes/. Run ECJ Java compilation first."
            emit(CompilerLogEntry(CompilerLogLevel.ERROR, msg))
            return@withContext failResult(logs, startedAt)
        }

        val classFiles = classesDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("class", ignoreCase = true) }
            .sortedBy { it.canonicalPath }
            .toList()

        if (classFiles.isEmpty()) {
            val msg = "build/classes/ contains 0 .class files. Nothing to convert to DEX."
            emit(CompilerLogEntry(CompilerLogLevel.ERROR, msg))
            return@withContext failResult(logs, startedAt)
        }

        emit(
            CompilerLogEntry(
                CompilerLogLevel.INFO,
                "DEX Stage: Converting ${classFiles.size} .class ${if (classFiles.size == 1) "file" else "files"} → classes.dex..."
            )
        )

        val dexDir = File(projectDir, "build/dex")
        if (dexDir.exists()) {
            dexDir.deleteRecursively()
        }
        if (!dexDir.mkdirs()) {
            emit(CompilerLogEntry(CompilerLogLevel.ERROR, "Unable to create build/dex/ output directory."))
            return@withContext failResult(logs, startedAt)
        }

        val dexOutputFile = File(dexDir, "classes.dex")
        val outBuffer = ByteArrayOutputStream()
        val errBuffer = ByteArrayOutputStream()

        val exitCode = runCatching {
            val dxContext = DxContext(outBuffer, errBuffer)
            val arguments = Main.Arguments(dxContext).apply {
                minSdkVersion = 26
                strictNameCheck = false
                outName = dexOutputFile.canonicalPath
                fileNames = arrayOf(classesDir.canonicalPath)
                makeOptionsObjects()
            }
            Main.run(arguments)
        }.getOrElse { throwable ->
            val errDetail = errBuffer.toString(StandardCharsets.UTF_8.name()).trim()
            val msg = buildString {
                append("DEX conversion crashed: ${throwable.message ?: throwable.javaClass.simpleName}")
                if (errDetail.isNotBlank()) {
                    append("\n$errDetail")
                }
            }
            emit(CompilerLogEntry(CompilerLogLevel.ERROR, msg))
            return@withContext failResult(logs, startedAt)
        }

        val stdoutText = outBuffer.toString(StandardCharsets.UTF_8.name()).trim()
        val stderrText = errBuffer.toString(StandardCharsets.UTF_8.name()).trim()

        if (stdoutText.isNotBlank()) {
            stdoutText.lines().filter { it.isNotBlank() }.forEach { line ->
                emit(CompilerLogEntry(CompilerLogLevel.INFO, line))
            }
        }
        if (stderrText.isNotBlank()) {
            stderrText.lines().filter { it.isNotBlank() }.forEach { line ->
                val level = if (exitCode == 0 && !line.contains("error", ignoreCase = true)) {
                    CompilerLogLevel.WARNING
                } else {
                    CompilerLogLevel.ERROR
                }
                emit(CompilerLogEntry(level, line))
            }
        }

        val durationMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)

        if (exitCode != 0 || !dexOutputFile.exists() || dexOutputFile.length() == 0L) {
            emit(
                CompilerLogEntry(
                    CompilerLogLevel.ERROR,
                    "DEX FAILED (exit code $exitCode) in ${durationMs}ms."
                )
            )
            return@withContext DexCompileOutput(
                success = false,
                dexFile = null,
                dexSizeBytes = 0L,
                logs = logs.toList(),
                durationMs = durationMs
            )
        }

        val dexSize = dexOutputFile.length()
        emit(
            CompilerLogEntry(
                CompilerLogLevel.INFO,
                "DEX SUCCESSFUL: build/dex/classes.dex (${WebToApkBuilder.formatSize(dexSize)}) in ${durationMs}ms"
            )
        )

        DexCompileOutput(
            success = true,
            dexFile = dexOutputFile,
            dexSizeBytes = dexSize,
            logs = logs.toList(),
            durationMs = durationMs
        )
    }

    private fun failResult(
        logs: List<CompilerLogEntry>,
        startedAt: Long
    ): DexCompileOutput = DexCompileOutput(
        success = false,
        dexFile = null,
        dexSizeBytes = 0L,
        logs = logs.toList(),
        durationMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
    )
}
