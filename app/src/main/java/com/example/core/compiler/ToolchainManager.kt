package com.example.core.compiler

import android.content.Context
import android.net.Uri
import com.example.domain.model.ToolchainStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

/**
 * Manages the on-device Android SDK compile stub (`android.jar`) inside app-private
 * `/toolchain/android.jar`, supporting device import (SAF OpenDocument) and OkHttp URL download.
 */
class ToolchainManager(
    context: Context
) {
    private val appContext = context.applicationContext

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val toolchainDir: File
        get() = File(appContext.filesDir, TOOLCHAIN_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }

    fun getAndroidJarFile(): File = File(toolchainDir, ANDROID_JAR_FILE_NAME)

    fun findAapt2Executable(): File? {
        val nativeLibDir = appContext.applicationInfo.nativeLibraryDir
        if (!nativeLibDir.isNullOrBlank()) {
            val bundledSo = File(nativeLibDir, "libaapt2.so")
            if (bundledSo.exists() && bundledSo.isFile) {
                return bundledSo
            }
        }
        val customBin = File(toolchainDir, AAPT2_FILE_NAME)
        if (customBin.exists() && customBin.isFile) {
            return customBin
        }
        return null
    }

    private val _status = MutableStateFlow(buildSnapshotStatus())
    val status: StateFlow<ToolchainStatus> = _status.asStateFlow()

    fun refreshStatus(): ToolchainStatus {
        val snapshot = buildSnapshotStatus(
            statusMessage = _status.value.statusMessage,
            isError = _status.value.isError
        )
        _status.value = snapshot
        return snapshot
    }

    suspend fun importAndroidJarFromUri(uri: Uri): Result<ToolchainStatus> = withContext(Dispatchers.IO) {
        _status.update {
            it.copy(
                isWorking = true,
                downloadProgress = null,
                statusMessage = "Importing android.jar from device...",
                isError = false
            )
        }
        runCatching {
            val targetDir = toolchainDir
            val tempFile = File(targetDir, "android.jar.importing")
            if (tempFile.exists()) tempFile.delete()

            val inputStream = appContext.contentResolver.openInputStream(uri)
                ?: error("Unable to open the selected file.")

            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            }

            validateJarArchive(tempFile)

            val dest = getAndroidJarFile()
            if (dest.exists()) dest.delete()
            check(tempFile.renameTo(dest)) { "Failed to finalize /toolchain/android.jar." }

            val ready = buildSnapshotStatus(
                statusMessage = "Imported android.jar successfully.",
                isError = false
            )
            _status.value = ready
            ready
        }.onFailure { err ->
            val tempFile = File(toolchainDir, "android.jar.importing")
            if (tempFile.exists()) tempFile.delete()
            _status.value = buildSnapshotStatus(
                statusMessage = err.message ?: "Failed to import android.jar.",
                isError = true
            )
        }
    }

    suspend fun downloadAndroidJarFromUrl(rawUrl: String): Result<ToolchainStatus> = withContext(Dispatchers.IO) {
        val url = rawUrl.trim()
        if (!url.startsWith("https://", ignoreCase = true) && !url.startsWith("http://", ignoreCase = true)) {
            val failed = buildSnapshotStatus(
                statusMessage = "Enter a valid http:// or https:// direct link to android.jar.",
                isError = true
            )
            _status.value = failed
            return@withContext Result.failure(IllegalArgumentException(failed.statusMessage))
        }

        _status.update {
            it.copy(
                isWorking = true,
                downloadProgress = 0f,
                statusMessage = "Connecting to download server...",
                isError = false
            )
        }

        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "NovaIDE-Android/1.0")
                .build()

            val tempFile = File(toolchainDir, "android.jar.downloading")
            if (tempFile.exists()) tempFile.delete()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Download failed (HTTP ${response.code}). Check the URL and retry.")
                }
                val body = response.body ?: error("Empty response from server.")
                val totalBytes = body.contentLength()
                var downloaded = 0L
                var lastEmit = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloaded += read

                            val now = System.currentTimeMillis()
                            if (now - lastEmit >= 120L) {
                                lastEmit = now
                                val progress = if (totalBytes > 0L) {
                                    (downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                } else null
                                _status.update { current ->
                                    current.copy(
                                        isWorking = true,
                                        downloadProgress = progress,
                                        statusMessage = if (totalBytes > 0L) {
                                            "Downloading android.jar... ${((progress ?: 0f) * 100).toInt()}%"
                                        } else {
                                            "Downloading android.jar... (${downloaded / 1024} KB)"
                                        },
                                        isError = false
                                    )
                                }
                            }
                        }
                        output.flush()
                    }
                }
            }

            validateJarArchive(tempFile)

            val dest = getAndroidJarFile()
            if (dest.exists()) dest.delete()
            check(tempFile.renameTo(dest)) { "Could not save downloaded android.jar." }

            val ready = buildSnapshotStatus(
                statusMessage = "Downloaded android.jar successfully.",
                isError = false
            )
            _status.value = ready
            ready
        }.onFailure { err ->
            val tempFile = File(toolchainDir, "android.jar.downloading")
            if (tempFile.exists()) tempFile.delete()
            _status.value = buildSnapshotStatus(
                statusMessage = err.message ?: "Download failed.",
                isError = true
            )
        }
    }

    suspend fun deleteAndroidJar(): Result<ToolchainStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val file = getAndroidJarFile()
            if (file.exists()) {
                check(file.delete()) { "Could not delete android.jar." }
            }
            val updated = buildSnapshotStatus(
                statusMessage = "Toolchain reset. Import or download android.jar to compile Java.",
                isError = false
            )
            _status.value = updated
            updated
        }
    }

    private fun validateJarArchive(file: File) {
        if (!file.exists() || file.length() < 1024L) {
            error("Selected file is too small to be a valid android.jar archive.")
        }
        val header = ByteArray(4)
        RandomAccessFile(file, "r").use { raf ->
            raf.readFully(header)
        }
        // Standard ZIP / JAR magic signature starts with "PK" (0x50, 0x4B)
        if (header[0] != 0x50.toByte() || header[1] != 0x4B.toByte()) {
            error("Invalid file format: expected a valid ZIP/JAR archive (android.jar).")
        }
    }

    private fun buildSnapshotStatus(
        statusMessage: String? = null,
        isError: Boolean = false
    ): ToolchainStatus {
        val file = getAndroidJarFile()
        val ready = file.exists() && file.isFile && file.length() >= 1024L
        val aapt2File = findAapt2Executable()
        return ToolchainStatus(
            isAndroidJarReady = ready,
            filePath = "/$TOOLCHAIN_DIR_NAME/$ANDROID_JAR_FILE_NAME",
            fileSizeBytes = if (ready) file.length() else 0L,
            lastUpdatedMillis = if (ready) file.lastModified() else 0L,
            isAapt2Ready = aapt2File != null,
            aapt2Path = aapt2File?.canonicalPath,
            isWorking = false,
            downloadProgress = null,
            statusMessage = statusMessage,
            isError = isError
        )
    }

    companion object {
        const val TOOLCHAIN_DIR_NAME = "toolchain"
        const val ANDROID_JAR_FILE_NAME = "android.jar"
        const val AAPT2_FILE_NAME = "aapt2"
    }
}
