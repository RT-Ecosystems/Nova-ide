package com.example.core.compiler

import android.content.Context
import com.example.core.security.SecretCrypto
import com.example.domain.model.KeystoreInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class SigningCredentials(
    val privateKey: PrivateKey,
    val certificates: List<X509Certificate>,
    val alias: String,
    val commonName: String,
    val organization: String,
    val isCustom: Boolean,
    val keystoreFile: File
)

/**
 * Phase 8 Keystore Manager.
 * Stores PKCS12 (.p12) keystores inside app-private `/toolchain/keystores/`.
 * - Auto-generates `nova_debug.p12` (RSA-2048, 25-year self-signed X.509) on first use
 *   using BouncyCastle directly (no `keytool` required, Android 8.0+ compatible).
 * - Supports creating a custom release keystore with user-supplied Alias, CN, Org, Password
 *   (password encrypted via Android Keystore AES-256-GCM SecretCrypto) or resetting to debug.
 */
class KeystoreManager(context: Context) {

    private val appContext = context.applicationContext
    private val keystoresDir: File
        get() = File(File(appContext.filesDir, TOOLCHAIN_DIR), KEYSTORES_DIR).apply {
            if (!exists()) mkdirs()
        }

    private val configFile: File
        get() = File(keystoresDir, ACTIVE_CONFIG_FILE)

    private val debugKeystoreFile: File
        get() = File(keystoresDir, DEBUG_KEYSTORE_FILE)

    private val _keystoreInfo = MutableStateFlow(readCurrentInfo())
    val keystoreInfo: StateFlow<KeystoreInfo> = _keystoreInfo.asStateFlow()

    fun refreshInfo(): KeystoreInfo {
        val current = readCurrentInfo()
        _keystoreInfo.value = current
        return current
    }

    /**
     * Loads the active keystore's PrivateKey and X509Certificate chain.
     * Automatically generates `nova_debug.p12` if no keystore exists yet.
     */
    suspend fun ensureActiveSigningKey(): Result<SigningCredentials> = withContext(Dispatchers.IO) {
        runCatching {
            val activeMeta = readActiveConfigOrNull()

            if (activeMeta != null && activeMeta.isCustom) {
                val customFile = File(keystoresDir, activeMeta.fileName)
                if (customFile.exists() && customFile.length() > 0L && activeMeta.password.isNotEmpty()) {
                    return@runCatching loadCredentialsFromPkcs12(
                        keystoreFile = customFile,
                        alias = activeMeta.alias,
                        password = activeMeta.password,
                        commonName = activeMeta.commonName,
                        organization = activeMeta.organization,
                        isCustom = true
                    )
                }
            }

            // Default auto-debug keystore path
            val debugFile = debugKeystoreFile
            if (!debugFile.exists() || debugFile.length() == 0L) {
                generatePkcs12Keystore(
                    outputFile = debugFile,
                    alias = DEBUG_ALIAS,
                    password = DEBUG_PASSWORD,
                    commonName = DEBUG_CN,
                    organization = DEBUG_ORG,
                    validityYears = 25
                )
            }

            saveActiveConfig(
                StoredKeystoreMeta(
                    isCustom = false,
                    alias = DEBUG_ALIAS,
                    commonName = DEBUG_CN,
                    organization = DEBUG_ORG,
                    fileName = DEBUG_KEYSTORE_FILE,
                    password = DEBUG_PASSWORD
                )
            )
            refreshInfo()

            loadCredentialsFromPkcs12(
                keystoreFile = debugFile,
                alias = DEBUG_ALIAS,
                password = DEBUG_PASSWORD,
                commonName = DEBUG_CN,
                organization = DEBUG_ORG,
                isCustom = false
            )
        }
    }

    suspend fun createCustomKeystore(
        alias: String,
        commonName: String,
        organization: String,
        password: String
    ): Result<KeystoreInfo> = withContext(Dispatchers.IO) {
        val cleanAlias = alias.trim().replace(Regex("[^a-zA-Z0-9._-]"), "")
        val cleanCn = commonName.trim()
        val cleanOrg = organization.trim().ifBlank { "Nova IDE Developer" }
        val cleanPassword = password.trim()

        if (cleanAlias.length < 2) {
            return@withContext Result.failure(IllegalArgumentException("Alias must be at least 2 alphanumeric characters."))
        }
        if (cleanCn.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Common Name (CN) cannot be empty."))
        }
        if (cleanPassword.length < 6) {
            return@withContext Result.failure(IllegalArgumentException("Keystore password must be at least 6 characters."))
        }

        _keystoreInfo.value = _keystoreInfo.value.copy(
            isWorking = true,
            statusMessage = "Generating RSA-2048 X.509 certificate...",
            isError = false
        )

        runCatching {
            val slug = cleanAlias.lowercase(Locale.US).take(24).ifBlank { "custom" }
            val fileName = "${slug}_release.p12"
            val outputFile = File(keystoresDir, fileName)

            generatePkcs12Keystore(
                outputFile = outputFile,
                alias = cleanAlias,
                password = cleanPassword,
                commonName = cleanCn,
                organization = cleanOrg,
                validityYears = 25
            )

            saveActiveConfig(
                StoredKeystoreMeta(
                    isCustom = true,
                    alias = cleanAlias,
                    commonName = cleanCn,
                    organization = cleanOrg,
                    fileName = fileName,
                    password = cleanPassword
                )
            )

            val updated = readCurrentInfo(
                statusMessage = "Custom keystore ($fileName) generated & active.",
                isError = false
            )
            _keystoreInfo.value = updated
            updated
        }.onFailure { err ->
            _keystoreInfo.value = readCurrentInfo(
                statusMessage = err.message ?: "Failed to create custom keystore.",
                isError = true
            )
        }
    }

    suspend fun resetToDebugKeystore(): Result<KeystoreInfo> = withContext(Dispatchers.IO) {
        _keystoreInfo.value = _keystoreInfo.value.copy(
            isWorking = true,
            statusMessage = "Switching to Nova Debug keystore...",
            isError = false
        )

        runCatching {
            val debugFile = debugKeystoreFile
            if (!debugFile.exists() || debugFile.length() == 0L) {
                generatePkcs12Keystore(
                    outputFile = debugFile,
                    alias = DEBUG_ALIAS,
                    password = DEBUG_PASSWORD,
                    commonName = DEBUG_CN,
                    organization = DEBUG_ORG,
                    validityYears = 25
                )
            }
            saveActiveConfig(
                StoredKeystoreMeta(
                    isCustom = false,
                    alias = DEBUG_ALIAS,
                    commonName = DEBUG_CN,
                    organization = DEBUG_ORG,
                    fileName = DEBUG_KEYSTORE_FILE,
                    password = DEBUG_PASSWORD
                )
            )
            val updated = readCurrentInfo(
                statusMessage = "Active keystore reset to Nova Debug (nova_debug.p12).",
                isError = false
            )
            _keystoreInfo.value = updated
            updated
        }.onFailure { err ->
            _keystoreInfo.value = readCurrentInfo(
                statusMessage = err.message ?: "Unable to reset keystore.",
                isError = true
            )
        }
    }

    private fun generatePkcs12Keystore(
        outputFile: File,
        alias: String,
        password: String,
        commonName: String,
        organization: String,
        validityYears: Int
    ) {
        outputFile.parentFile?.mkdirs()

        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048, SecureRandom())
        val keyPair = keyPairGenerator.generateKeyPair()

        val subject = X500NameBuilder(BCStyle.INSTANCE)
            .addRDN(BCStyle.CN, commonName)
            .addRDN(BCStyle.O, organization)
            .build()

        val now = Date()
        val notAfter = Calendar.getInstance().apply {
            time = now
            add(Calendar.YEAR, validityYears)
        }.time

        val serialNumber = BigInteger(128, SecureRandom()).abs().max(BigInteger.ONE)

        // Instantiate BouncyCastleProvider directly to avoid conflicts with Android's built-in BC provider
        val bcProvider = BouncyCastleProvider()
        val contentSigner = JcaContentSignerBuilder("SHA256withRSA")
            .setProvider(bcProvider)
            .build(keyPair.private)

        val certHolder = JcaX509v3CertificateBuilder(
            subject,
            serialNumber,
            now,
            notAfter,
            subject,
            keyPair.public
        ).build(contentSigner)

        val certificate: X509Certificate = JcaX509CertificateConverter()
            .setProvider(bcProvider)
            .getCertificate(certHolder)

        val keyStore = KeyStore.getInstance("PKCS12")
        keyStore.load(null, null)
        keyStore.setKeyEntry(
            alias,
            keyPair.private,
            password.toCharArray(),
            arrayOf(certificate)
        )

        val tmpFile = File(outputFile.parentFile, "${outputFile.name}.tmp")
        FileOutputStream(tmpFile).use { fos ->
            keyStore.store(fos, password.toCharArray())
        }
        if (outputFile.exists()) outputFile.delete()
        if (!tmpFile.renameTo(outputFile)) {
            tmpFile.copyTo(outputFile, overwrite = true)
            tmpFile.delete()
        }
    }

    private fun loadCredentialsFromPkcs12(
        keystoreFile: File,
        alias: String,
        password: String,
        commonName: String,
        organization: String,
        isCustom: Boolean
    ): SigningCredentials {
        val passChars = password.toCharArray()
        val keyStore = KeyStore.getInstance("PKCS12")
        FileInputStream(keystoreFile).use { fis ->
            keyStore.load(fis, passChars)
        }

        val resolvedAlias = when {
            keyStore.containsAlias(alias) -> alias
            keyStore.aliases().hasMoreElements() -> keyStore.aliases().nextElement()
            else -> error("Keystore ${keystoreFile.name} contains no signing keys.")
        }

        val privateKey = keyStore.getKey(resolvedAlias, passChars) as? PrivateKey
            ?: error("Unable to load private key for alias '$resolvedAlias'.")

        val chain = keyStore.getCertificateChain(resolvedAlias)
            ?.mapNotNull { it as? X509Certificate }
            ?.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(keyStore.getCertificate(resolvedAlias) as? X509Certificate)

        if (chain.isEmpty()) {
            error("Keystore ${keystoreFile.name} has no X.509 certificate for alias '$resolvedAlias'.")
        }

        return SigningCredentials(
            privateKey = privateKey,
            certificates = chain,
            alias = resolvedAlias,
            commonName = commonName,
            organization = organization,
            isCustom = isCustom,
            keystoreFile = keystoreFile
        )
    }

    private fun readCurrentInfo(
        statusMessage: String? = null,
        isError: Boolean = false
    ): KeystoreInfo {
        val meta = readActiveConfigOrNull()
        return if (meta != null && meta.isCustom) {
            val file = File(keystoresDir, meta.fileName)
            KeystoreInfo(
                isCustom = true,
                alias = meta.alias,
                commonName = meta.commonName,
                organization = meta.organization,
                fileName = meta.fileName,
                filePath = "/toolchain/keystores/${meta.fileName}",
                exists = file.exists() && file.length() > 0L,
                isWorking = false,
                statusMessage = statusMessage,
                isError = isError
            )
        } else {
            val debugFile = debugKeystoreFile
            KeystoreInfo(
                isCustom = false,
                alias = DEBUG_ALIAS,
                commonName = DEBUG_CN,
                organization = DEBUG_ORG,
                fileName = DEBUG_KEYSTORE_FILE,
                filePath = "/toolchain/keystores/$DEBUG_KEYSTORE_FILE",
                exists = debugFile.exists() && debugFile.length() > 0L,
                isWorking = false,
                statusMessage = statusMessage,
                isError = isError
            )
        }
    }

    private fun saveActiveConfig(meta: StoredKeystoreMeta) {
        val encryptedPwd = if (meta.isCustom) {
            SecretCrypto.encrypt(meta.password)
        } else {
            ""
        }
        val json = JSONObject().apply {
            put("isCustom", meta.isCustom)
            put("alias", meta.alias)
            put("commonName", meta.commonName)
            put("organization", meta.organization)
            put("fileName", meta.fileName)
            put("encryptedPassword", encryptedPwd)
        }
        configFile.writeText(json.toString(2), StandardCharsets.UTF_8)
    }

    private fun readActiveConfigOrNull(): StoredKeystoreMeta? {
        val file = configFile
        if (!file.exists()) return null
        return runCatching {
            val json = JSONObject(file.readText(StandardCharsets.UTF_8))
            val isCustom = json.optBoolean("isCustom", false)
            val encryptedPwd = json.optString("encryptedPassword", "")
            val password = if (isCustom) {
                SecretCrypto.decrypt(encryptedPwd)
            } else {
                DEBUG_PASSWORD
            }
            StoredKeystoreMeta(
                isCustom = isCustom,
                alias = json.optString("alias", DEBUG_ALIAS),
                commonName = json.optString("commonName", DEBUG_CN),
                organization = json.optString("organization", DEBUG_ORG),
                fileName = json.optString("fileName", DEBUG_KEYSTORE_FILE),
                password = password
            )
        }.getOrNull()
    }

    private data class StoredKeystoreMeta(
        val isCustom: Boolean,
        val alias: String,
        val commonName: String,
        val organization: String,
        val fileName: String,
        val password: String
    )

    companion object {
        private const val TOOLCHAIN_DIR = "toolchain"
        private const val KEYSTORES_DIR = "keystores"
        private const val ACTIVE_CONFIG_FILE = "active_keystore.json"
        const val DEBUG_KEYSTORE_FILE = "nova_debug.p12"
        const val DEBUG_ALIAS = "novadebug"
        const val DEBUG_PASSWORD = "novadebug"
        const val DEBUG_CN = "Nova IDE Debug"
        const val DEBUG_ORG = "Nova IDE"
    }
}
