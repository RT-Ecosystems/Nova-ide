package com.example.core.compiler

import com.example.core.builder.WebToApkBuilder
import com.example.domain.model.CompilerLogEntry
import com.example.domain.model.CompilerLogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class ApkPackageOutput(
    val success: Boolean,
    val unsignedApkFile: File?,
    val apkSizeBytes: Long,
    val usedAapt2: Boolean,
    val logs: List<CompilerLogEntry>,
    val durationMs: Long
)

/**
 * Phase 7 Resource & Unsigned APK Packager.
 * Checks for `libaapt2.so` (nativeLibraryDir) or `/toolchain/aapt2`:
 * - If found: runs `aapt2 compile` + `aapt2 link` with `android.jar` & `AndroidManifest.xml`,
 *   then merges `classes.dex` into `<project>/build/apk/<projectName>-unsigned.apk`.
 * - Fallback (no `aapt2` bundled): packages `classes.dex`, `AndroidManifest.xml`, `res/`, and
 *   `assets/` cleanly via ZipOutputStream and logs an explicit INFO/WARNING notice.
 */
class ApkPackagerEngine(
    private val toolchainManager: ToolchainManager
) {

    suspend fun packageUnsignedApk(
        projectDir: File,
        onLog: (CompilerLogEntry) -> Unit = {}
    ): ApkPackageOutput = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val logs = mutableListOf<CompilerLogEntry>()

        fun emit(entry: CompilerLogEntry) {
            logs.add(entry)
            onLog(entry)
        }

        val dexFile = File(projectDir, "build/dex/classes.dex")
        if (!dexFile.exists() || !dexFile.isFile || dexFile.length() == 0L) {
            emit(
                CompilerLogEntry(
                    CompilerLogLevel.ERROR,
                    "Missing build/dex/classes.dex. Run DEX compilation before packaging APK."
                )
            )
            return@withContext failResult(logs, startedAt)
        }

        val apkDir = File(projectDir, "build/apk")
        if (!apkDir.exists() && !apkDir.mkdirs()) {
            emit(CompilerLogEntry(CompilerLogLevel.ERROR, "Unable to create build/apk/ output directory."))
            return@withContext failResult(logs, startedAt)
        }

        val safeName = projectDir.name.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "app" }
        val unsignedApk = File(apkDir, "$safeName-unsigned.apk")
        if (unsignedApk.exists()) unsignedApk.delete()

        val manifestFile = File(projectDir, "AndroidManifest.xml")
        val resDir = File(projectDir, "res")
        val assetsDir = File(projectDir, "assets")
        val androidJar = toolchainManager.getAndroidJarFile()
        val aapt2Binary = toolchainManager.findAapt2Executable()

        emit(CompilerLogEntry(CompilerLogLevel.INFO, "Package Stage: Assembling ${unsignedApk.name}..."))

        var usedAapt2 = false

        if (aapt2Binary != null && manifestFile.exists() && androidJar.exists()) {
            emit(
                CompilerLogEntry(
                    CompilerLogLevel.INFO,
                    "Found aapt2 binary (${aapt2Binary.name}) — compiling & linking binary Android resources..."
                )
            )
            val aapt2Tmp = File(projectDir, "build/aapt2_tmp").apply {
                if (exists()) deleteRecursively()
                mkdirs()
            }
            val resourcesAp = File(aapt2Tmp, "resources.ap_")

            val aapt2Ok = runAapt2LinkPipeline(
                aapt2Binary = aapt2Binary,
                androidJar = androidJar,
                manifestFile = manifestFile,
                resDir = resDir,
                assetsDir = assetsDir,
                tempDir = aapt2Tmp,
                outputApFile = resourcesAp,
                emit = ::emit
            )

            if (aapt2Ok && resourcesAp.exists() && resourcesAp.length() > 0L) {
                val merged = runCatching {
                    mergeResourcesApAndDex(resourcesAp, dexFile, unsignedApk)
                }.onFailure { err ->
                    emit(
                        CompilerLogEntry(
                            CompilerLogLevel.ERROR,
                            "Failed merging resources.ap_ with classes.dex: ${err.message}"
                        )
                    )
                }.isSuccess

                aapt2Tmp.deleteRecursively()

                if (merged) {
                    usedAapt2 = true
                } else {
                    return@withContext failResult(logs, startedAt)
                }
            } else {
                aapt2Tmp.deleteRecursively()
                emit(
                    CompilerLogEntry(
                        CompilerLogLevel.WARNING,
                        "aapt2 execution did not produce resources.ap_. Falling back to ZipOutputStream APK assembler."
                    )
                )
            }
        } else {
            emit(
                CompilerLogEntry(
                    CompilerLogLevel.WARNING,
                    "aapt2 binary not bundled (libaapt2.so / /toolchain/aapt2 not found) — binary resource linking skipped (ready for libaapt2.so drop-in). Assembling unsigned APK via ZipOutputStream fallback."
                )
            )
        }

        if (!usedAapt2) {
            val packedCount = runCatching {
                assembleFallbackUnsignedApk(
                    unsignedApk = unsignedApk,
                    dexFile = dexFile,
                    manifestFile = manifestFile,
                    resDir = resDir,
                    assetsDir = assetsDir
                )
            }.getOrElse { err ->
                emit(
                    CompilerLogEntry(
                        CompilerLogLevel.ERROR,
                        "Fallback APK packaging failed: ${err.message ?: err.javaClass.simpleName}"
                    )
                )
                return@withContext failResult(logs, startedAt)
            }

            emit(
                CompilerLogEntry(
                    CompilerLogLevel.INFO,
                    "Packed $packedCount archive ${if (packedCount == 1) "entry" else "entries"} (classes.dex, manifest & resources) into ${unsignedApk.name}"
                )
            )
        }

        val durationMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
        val apkSize = unsignedApk.length()
        emit(
            CompilerLogEntry(
                CompilerLogLevel.INFO,
                "PACKAGE SUCCESSFUL: build/apk/${unsignedApk.name} (${WebToApkBuilder.formatSize(apkSize)}) in ${durationMs}ms"
            )
        )

        ApkPackageOutput(
            success = true,
            unsignedApkFile = unsignedApk,
            apkSizeBytes = apkSize,
            usedAapt2 = usedAapt2,
            logs = logs.toList(),
            durationMs = durationMs
        )
    }

    private fun runAapt2LinkPipeline(
        aapt2Binary: File,
        androidJar: File,
        manifestFile: File,
        resDir: File,
        assetsDir: File,
        tempDir: File,
        outputApFile: File,
        emit: (CompilerLogEntry) -> Unit
    ): Boolean {
        val compiledResZip = File(tempDir, "compiled_res.zip")
        val hasResFiles = resDir.exists() && resDir.isDirectory &&
            resDir.walkTopDown().any { it.isFile }

        if (hasResFiles) {
            val compileCmd = listOf(
                aapt2Binary.canonicalPath,
                "compile",
                "--dir",
                resDir.canonicalPath,
                "-o",
                compiledResZip.canonicalPath
            )
            val compileExit = runProcess(compileCmd, emit)
            if (compileExit != 0 || !compiledResZip.exists()) {
                emit(CompilerLogEntry(CompilerLogLevel.WARNING, "aapt2 compile exited with code $compileExit"))
                return false
            }
        }

        val linkCmd = mutableListOf(
            aapt2Binary.canonicalPath,
            "link",
            "-I",
            androidJar.canonicalPath,
            "--manifest",
            manifestFile.canonicalPath,
            "-o",
            outputApFile.canonicalPath,
            "--auto-add-overlay"
        )
        if (assetsDir.exists() && assetsDir.isDirectory) {
            linkCmd.add("-A")
            linkCmd.add(assetsDir.canonicalPath)
        }
        if (compiledResZip.exists()) {
            linkCmd.add(compiledResZip.canonicalPath)
        }

        val linkExit = runProcess(linkCmd, emit)
        return linkExit == 0 && outputApFile.exists()
    }

    private fun runProcess(
        command: List<String>,
        emit: (CompilerLogEntry) -> Unit
    ): Int {
        return runCatching {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val code = process.waitFor()
            if (output.isNotBlank()) {
                output.lines().filter { it.isNotBlank() }.forEach { line ->
                    emit(
                        CompilerLogEntry(
                            level = if (code == 0) CompilerLogLevel.INFO else CompilerLogLevel.WARNING,
                            message = "[aapt2] $line"
                        )
                    )
                }
            }
            code
        }.getOrElse { err ->
            emit(
                CompilerLogEntry(
                    CompilerLogLevel.WARNING,
                    "[aapt2] Unable to execute binary: ${err.message ?: err.javaClass.simpleName}"
                )
            )
            -1
        }
    }

    private fun mergeResourcesApAndDex(
        resourcesAp: File,
        dexFile: File,
        outputApk: File
    ) {
        ZipFile(resourcesAp).use { sourceZip ->
            ZipOutputStream(FileOutputStream(outputApk)).use { zos ->
                val entries = sourceZip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name == "classes.dex") continue
                    val outEntry = ZipEntry(entry.name)
                    zos.putNextEntry(outEntry)
                    if (!entry.isDirectory) {
                        sourceZip.getInputStream(entry).use { input ->
                            input.copyTo(zos)
                        }
                    }
                    zos.closeEntry()
                }
                writeFileEntry(zos, "classes.dex", dexFile)
            }
        }
    }

    private fun assembleFallbackUnsignedApk(
        unsignedApk: File,
        dexFile: File,
        manifestFile: File,
        resDir: File,
        assetsDir: File
    ): Int {
        var count = 0
        ZipOutputStream(FileOutputStream(unsignedApk)).use { zos ->
            writeFileEntry(zos, "classes.dex", dexFile)
            count++

            if (manifestFile.exists() && manifestFile.isFile) {
                writeFileEntry(zos, "AndroidManifest.xml", manifestFile)
                count++
            }

            if (resDir.exists() && resDir.isDirectory) {
                resDir.walkTopDown()
                    .filter { it.isFile }
                    .sortedBy { it.canonicalPath }
                    .forEach { file ->
                        val rel = file.relativeTo(resDir).path.replace('\\', '/')
                        writeFileEntry(zos, "res/$rel", file)
                        count++
                    }
            }

            if (assetsDir.exists() && assetsDir.isDirectory) {
                assetsDir.walkTopDown()
                    .filter { it.isFile }
                    .sortedBy { it.canonicalPath }
                    .forEach { file ->
                        val rel = file.relativeTo(assetsDir).path.replace('\\', '/')
                        writeFileEntry(zos, "assets/$rel", file)
                        count++
                    }
            }
        }
        return count
    }

    private fun writeFileEntry(zos: ZipOutputStream, entryName: String, file: File) {
        zos.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                zos.write(buffer, 0, read)
            }
        }
        zos.closeEntry()
    }

    private fun failResult(
        logs: List<CompilerLogEntry>,
        startedAt: Long
    ): ApkPackageOutput = ApkPackageOutput(
        success = false,
        unsignedApkFile = null,
        apkSizeBytes = 0L,
        usedAapt2 = false,
        logs = logs.toList(),
        durationMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
    )
}
