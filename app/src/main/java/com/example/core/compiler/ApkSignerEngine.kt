package com.example.core.compiler

import com.android.apksig.ApkSigner
import com.example.core.builder.WebToApkBuilder
import com.example.domain.model.CompilerLogEntry
import com.example.domain.model.CompilerLogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ApkSignOutput(
    val success: Boolean,
    val signedApkFile: File?,
    val exportedApkFile: File?,
    val apkSizeBytes: Long,
    val signerAlias: String,
    val signerCommonName: String,
    val isCustomKeystore: Boolean,
    val logs: List<CompilerLogEntry>,
    val durationMs: Long
)

/**
 * Phase 8 On-Device APK Signer Engine.
 * Signs `<project>/build/apk/<projectName>-unsigned.apk` using Google's official `apksig`
 * library with APK Signature Scheme v1 (JAR) + v2 (APK Signature Scheme v2).
 * Writes the signed APK both inside `<project>/build/apk/<projectName>-signed.apk`
 * and into shared `/exports/<projectName>-signed.apk` for Recent Exports & FileProvider sharing.
 */
class ApkSignerEngine(
    private val keystoreManager: KeystoreManager,
    private val webToApkBuilder: WebToApkBuilder
) {

    suspend fun signUnsignedApk(
        projectDir: File,
        unsignedApkFile: File,
        onLog: (CompilerLogEntry) -> Unit = {}
    ): ApkSignOutput = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val logs = mutableListOf<CompilerLogEntry>()

        fun emit(entry: CompilerLogEntry) {
            logs.add(entry)
            onLog(entry)
        }

        if (!unsignedApkFile.exists() || !unsignedApkFile.isFile || unsignedApkFile.length() == 0L) {
            emit(
                CompilerLogEntry(
                    CompilerLogLevel.ERROR,
                    "Unsigned APK missing (${unsignedApkFile.name}). Run Package stage first."
                )
            )
            return@withContext failResult(logs, startedAt)
        }

        emit(CompilerLogEntry(CompilerLogLevel.INFO, "Sign Stage: Loading active PKCS12 signing keystore..."))

        val credentialsResult = keystoreManager.ensureActiveSigningKey()
        val credentials = credentialsResult.getOrElse { err ->
            emit(
                CompilerLogEntry(
                    CompilerLogLevel.ERROR,
                    "Keystore error: ${err.message ?: err.javaClass.simpleName}"
                )
            )
            return@withContext failResult(logs, startedAt)
        }

        val keystoreKind = if (credentials.isCustom) "Custom Release" else "Auto-Debug"
        emit(
            CompilerLogEntry(
                CompilerLogLevel.INFO,
                "Loaded $keystoreKind key • alias=${credentials.alias} • CN=${credentials.commonName} (${credentials.keystoreFile.name})"
            )
        )

        val safeName = projectDir.name.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "app" }
        val apkDir = File(projectDir, "build/apk").apply { mkdirs() }
        val signedApkFile = File(apkDir, "$safeName-signed.apk")
        if (signedApkFile.exists()) signedApkFile.delete()

        emit(
            CompilerLogEntry(
                CompilerLogLevel.INFO,
                "Signing ${unsignedApkFile.name} with apksig (v1 JAR + v2 APK Signature Scheme, minSdk=26)..."
            )
        )

        val signOutcome = runCatching {
            val signerConfig = ApkSigner.SignerConfig.Builder(
                credentials.alias,
                credentials.privateKey,
                credentials.certificates
            ).build()

            val apkSigner = ApkSigner.Builder(listOf(signerConfig))
                .setInputApk(unsignedApkFile)
                .setOutputApk(signedApkFile)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setMinSdkVersion(26)
                .setCreatedBy("Nova IDE")
                .build()

            apkSigner.sign()
        }

        if (signOutcome.isFailure || !signedApkFile.exists() || signedApkFile.length() == 0L) {
            val err = signOutcome.exceptionOrNull()
            emit(
                CompilerLogEntry(
                    CompilerLogLevel.ERROR,
                    "apksig signing failed: ${err?.message ?: err?.javaClass?.simpleName ?: "Unknown signing error"}"
                )
            )
            return@withContext failResult(logs, startedAt)
        }

        // Also copy into shared /exports/ so it appears in Recent Exports & is FileProvider-shareable
        val exportsDir = webToApkBuilder.ensureExportsDir()
        val exportedApkFile = File(exportsDir, signedApkFile.name)
        val copyResult = runCatching {
            signedApkFile.copyTo(exportedApkFile, overwrite = true)
            exportedApkFile
        }.getOrNull()

        val durationMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
        val finalSize = signedApkFile.length()

        emit(
            CompilerLogEntry(
                CompilerLogLevel.INFO,
                "SIGN SUCCESSFUL (v1 + v2): build/apk/${signedApkFile.name} (${WebToApkBuilder.formatSize(finalSize)}) in ${durationMs}ms"
            )
        )
        if (copyResult != null) {
            emit(
                CompilerLogEntry(
                    CompilerLogLevel.INFO,
                    "Exported to /exports/${copyResult.name} — ready to Share / Save APK via FileProvider."
                )
            )
        }

        ApkSignOutput(
            success = true,
            signedApkFile = signedApkFile,
            exportedApkFile = copyResult,
            apkSizeBytes = finalSize,
            signerAlias = credentials.alias,
            signerCommonName = credentials.commonName,
            isCustomKeystore = credentials.isCustom,
            logs = logs.toList(),
            durationMs = durationMs
        )
    }

    private fun failResult(
        logs: List<CompilerLogEntry>,
        startedAt: Long
    ): ApkSignOutput = ApkSignOutput(
        success = false,
        signedApkFile = null,
        exportedApkFile = null,
        apkSizeBytes = 0L,
        signerAlias = "",
        signerCommonName = "",
        isCustomKeystore = false,
        logs = logs.toList(),
        durationMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
    )
}
