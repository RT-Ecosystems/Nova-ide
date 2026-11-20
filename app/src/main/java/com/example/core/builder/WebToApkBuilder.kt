package com.example.core.builder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.domain.model.ExportedArtifact
import com.example.domain.model.WebToApkConfig
import com.example.domain.model.WebToApkExportKind
import com.example.domain.model.WebToApkSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Production build & packaging engine for Phase 5 Web-to-APK Converter.
 * Generates either:
 * 1) A complete, ready-to-compile Android Gradle/Kotlin WebView project (.zip) with GitHub Actions APK CI.
 * 2) A standalone offline-capable PWA web bundle (.zip) with manifest.json, sw.js, and resized icons.
 */
class WebToApkBuilder(private val context: Context) {

    private val appContext = context.applicationContext

    fun ensureExportsDir(): File {
        val dir = File(appContext.filesDir, EXPORTS_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun calculateDirectorySizeBytes(dirPath: String?): Long = withContext(Dispatchers.IO) {
        if (dirPath.isNullOrBlank()) return@withContext 0L
        val root = File(dirPath)
        if (!root.exists() || !root.isDirectory) return@withContext 0L
        computeSizeRecursive(root)
    }

    private fun computeSizeRecursive(file: File): Long {
        if (file.name == ".git" || file.name == "node_modules") return 0L
        if (file.isFile) return file.length()
        val children = file.listFiles() ?: return 0L
        var total = 0L
        for (child in children) {
            total += computeSizeRecursive(child)
        }
        return total
    }

    suspend fun listRecentExports(): List<ExportedArtifact> = withContext(Dispatchers.IO) {
        val dir = ensureExportsDir()
        val files = dir.listFiles { f ->
            f.isFile && (f.name.endsWith(".zip", ignoreCase = true) || f.name.endsWith(".apk", ignoreCase = true))
        } ?: return@withContext emptyList()

        files.sortedByDescending { it.lastModified() }.map { f ->
            val kind = when {
                f.name.endsWith(".apk", ignoreCase = true) -> WebToApkExportKind.SIGNED_APK
                f.name.contains("_pwa_", ignoreCase = true) -> WebToApkExportKind.PWA_BUNDLE_ZIP
                else -> WebToApkExportKind.ANDROID_PROJECT_ZIP
            }
            ExportedArtifact(
                fileName = f.name,
                absolutePath = f.canonicalPath,
                sizeBytes = f.length(),
                createdAt = f.lastModified(),
                kind = kind
            )
        }
    }

    suspend fun deleteExport(absolutePath: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(absolutePath)
        val exportsCanonical = ensureExportsDir().canonicalPath
        if (!file.canonicalPath.startsWith(exportsCanonical)) return@withContext false
        file.delete()
    }

    fun getShareableUri(file: File): Uri {
        return FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file
        )
    }

    suspend fun buildAndroidWebViewProjectZip(
        config: WebToApkConfig,
        onStep: (progress: Float, log: String) -> Unit
    ): Result<ExportedArtifact> = withContext(Dispatchers.IO) {
        runCatching {
            validateConfig(config)
            onStep(0.08f, "✓ Validated app metadata (${config.packageName})")

            val sourceDir = if (config.sourceType == WebToApkSourceType.LOCAL_PROJECT) {
                val path = config.projectDirPath?.takeIf { it.isNotBlank() }
                    ?: error("Please select a local project from /projects/.")
                val dir = File(path)
                if (!dir.exists() || !dir.isDirectory) {
                    error("Selected project directory does not exist.")
                }
                val sizeBytes = computeSizeRecursive(dir)
                if (sizeBytes > MAX_PROJECT_SIZE_BYTES) {
                    error("Project exceeds the 100 MB safety limit (${formatSize(sizeBytes)}). Trim large assets and retry.")
                }
                onStep(0.16f, "✓ Verified local project size (${formatSize(sizeBytes)})")
                dir
            } else {
                onStep(0.16f, "✓ Configured Live URL target (${config.liveUrl.trim()})")
                null
            }

            onStep(0.28f, "🎨 Generating launcher icons (mdpi → xxxhdpi)...")
            val baseIconBitmap = loadOrGenerateBaseIcon(config.iconUriString, config.appName)
            val mipmapIcons = mapOf(
                "app/src/main/res/mipmap-mdpi/ic_launcher.png" to encodePng(baseIconBitmap, 48),
                "app/src/main/res/mipmap-hdpi/ic_launcher.png" to encodePng(baseIconBitmap, 72),
                "app/src/main/res/mipmap-xhdpi/ic_launcher.png" to encodePng(baseIconBitmap, 96),
                "app/src/main/res/mipmap-xxhdpi/ic_launcher.png" to encodePng(baseIconBitmap, 144),
                "app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" to encodePng(baseIconBitmap, 192),
                "app/src/main/res/mipmap-mdpi/ic_launcher_round.png" to encodePng(baseIconBitmap, 48),
                "app/src/main/res/mipmap-hdpi/ic_launcher_round.png" to encodePng(baseIconBitmap, 72),
                "app/src/main/res/mipmap-xhdpi/ic_launcher_round.png" to encodePng(baseIconBitmap, 96),
                "app/src/main/res/mipmap-xxhdpi/ic_launcher_round.png" to encodePng(baseIconBitmap, 144),
                "app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" to encodePng(baseIconBitmap, 192)
            )
            baseIconBitmap.recycle()

            val slug = slugify(config.appName)
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outputZip = File(ensureExportsDir(), "${slug}_android_app_${timestamp}.zip")
            val packagePath = config.packageName.trim().replace('.', '/')

            onStep(0.45f, "📦 Scaffolding Gradle & Kotlin WebView source files...")

            ZipOutputStream(FileOutputStream(outputZip)).use { zos ->
                writeTextEntry(zos, "settings.gradle.kts", generateSettingsGradle(config))
                writeTextEntry(zos, "build.gradle.kts", generateRootBuildGradle())
                writeTextEntry(zos, "gradle.properties", generateGradleProperties())
                writeTextEntry(zos, "app/build.gradle.kts", generateAppBuildGradle(config))
                writeTextEntry(zos, "app/proguard-rules.pro", "# ProGuard rules for ${config.appName}\n")
                writeTextEntry(zos, "app/src/main/AndroidManifest.xml", generateManifestXml(config))
                writeTextEntry(zos, "app/src/main/res/values/strings.xml", generateStringsXml(config))
                writeTextEntry(zos, "app/src/main/res/values/themes.xml", generateThemesXml(config))
                writeTextEntry(zos, "app/src/main/java/$packagePath/MainActivity.kt", generateMainActivityKt(config))
                writeTextEntry(zos, ".github/workflows/build-apk.yml", generateGithubWorkflowYml(config))
                writeTextEntry(zos, "README.md", generateAndroidReadme(config))

                onStep(0.62f, "🖼️ Writing resized app icons into mipmap resources...")
                mipmapIcons.forEach { (entryPath, pngBytes) ->
                    writeBytesEntry(zos, entryPath, pngBytes)
                }

                if (sourceDir != null) {
                    onStep(0.76f, "📂 Packaging /projects/${sourceDir.name} into assets/www/...")
                    val packedCount = zipDirectoryIntoPrefix(zos, sourceDir, "app/src/main/assets/www/")
                    onStep(0.92f, "✓ Bundled $packedCount web asset file(s) into assets/www/")
                } else {
                    onStep(0.88f, "✓ Wired live URL loader into MainActivity.kt")
                }
            }

            onStep(1.0f, "✅ Export complete: ${outputZip.name} (${formatSize(outputZip.length())})")
            ExportedArtifact(
                fileName = outputZip.name,
                absolutePath = outputZip.canonicalPath,
                sizeBytes = outputZip.length(),
                createdAt = outputZip.lastModified(),
                kind = WebToApkExportKind.ANDROID_PROJECT_ZIP
            )
        }
    }

    suspend fun buildStandalonePwaBundleZip(
        config: WebToApkConfig,
        onStep: (progress: Float, log: String) -> Unit
    ): Result<ExportedArtifact> = withContext(Dispatchers.IO) {
        runCatching {
            validateConfig(config)
            onStep(0.10f, "✓ Validated PWA bundle settings for ${config.appName}")

            val sourceDir = if (config.sourceType == WebToApkSourceType.LOCAL_PROJECT) {
                val path = config.projectDirPath?.takeIf { it.isNotBlank() }
                    ?: error("Please select a local project from /projects/.")
                val dir = File(path)
                if (!dir.exists() || !dir.isDirectory) error("Selected project folder not found.")
                val sizeBytes = computeSizeRecursive(dir)
                if (sizeBytes > MAX_PROJECT_SIZE_BYTES) {
                    error("Project exceeds the 100 MB safety limit (${formatSize(sizeBytes)}).")
                }
                onStep(0.22f, "✓ Checked project size (${formatSize(sizeBytes)})")
                dir
            } else {
                null
            }

            onStep(0.38f, "🎨 Generating 192px & 512px PWA icons...")
            val baseIcon = loadOrGenerateBaseIcon(config.iconUriString, config.appName)
            val icon192 = encodePng(baseIcon, 192)
            val icon512 = encodePng(baseIcon, 512)
            baseIcon.recycle()

            val slug = slugify(config.appName)
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outputZip = File(ensureExportsDir(), "${slug}_pwa_bundle_${timestamp}.zip")

            onStep(0.55f, "⚡ Injecting manifest.json & offline service worker sw.js...")

            ZipOutputStream(FileOutputStream(outputZip)).use { zos ->
                val cachedUrls = mutableListOf("./", "./index.html", "./manifest.json", "./icon-192.png", "./icon-512.png")

                if (sourceDir != null) {
                    val allFiles = collectFilesRecursive(sourceDir)
                    var wroteIndex = false
                    for (file in allFiles) {
                        val relPath = file.relativeTo(sourceDir).path.replace('\\', '/')
                        if (relPath.equals("manifest.json", ignoreCase = true) ||
                            relPath.equals("sw.js", ignoreCase = true)
                        ) {
                            continue
                        }
                        if (relPath.equals("index.html", ignoreCase = true)) {
                            val originalHtml = file.readText(StandardCharsets.UTF_8)
                            val enhancedHtml = injectPwaTagsIntoHtml(originalHtml, config.appName)
                            writeTextEntry(zos, "index.html", enhancedHtml)
                            wroteIndex = true
                        } else {
                            writeFileEntry(zos, relPath, file)
                            if (cachedUrls.size < 40) {
                                cachedUrls.add("./$relPath")
                            }
                        }
                    }
                    if (!wroteIndex) {
                        writeTextEntry(zos, "index.html", generateUrlWrapperHtml(config))
                    }
                } else {
                    writeTextEntry(zos, "index.html", generateUrlWrapperHtml(config))
                }

                onStep(0.78f, "📦 Packaging icons, manifest.json & sw.js...")
                writeTextEntry(zos, "manifest.json", generatePwaManifestJson(config))
                writeTextEntry(zos, "sw.js", generateServiceWorkerJs(slug, cachedUrls))
                writeBytesEntry(zos, "icon-192.png", icon192)
                writeBytesEntry(zos, "icon-512.png", icon512)
            }

            onStep(1.0f, "✅ Exported PWA bundle: ${outputZip.name} (${formatSize(outputZip.length())})")
            ExportedArtifact(
                fileName = outputZip.name,
                absolutePath = outputZip.canonicalPath,
                sizeBytes = outputZip.length(),
                createdAt = outputZip.lastModified(),
                kind = WebToApkExportKind.PWA_BUNDLE_ZIP
            )
        }
    }

    private fun validateConfig(config: WebToApkConfig) {
        if (config.appName.trim().isEmpty()) {
            error("App Name cannot be empty.")
        }
        val pkg = config.packageName.trim()
        if (!PACKAGE_NAME_REGEX.matches(pkg)) {
            error("Package Name must follow reverse-domain format (e.g., com.nova.myapp).")
        }
        if (config.versionName.trim().isEmpty()) {
            error("Version Name cannot be empty.")
        }
        if (config.versionCode < 1) {
            error("Version Code must be 1 or greater.")
        }
        if (config.sourceType == WebToApkSourceType.LIVE_URL) {
            val url = config.liveUrl.trim()
            if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                error("Live Website URL must start with https:// or http://")
            }
        }
    }

    private fun loadOrGenerateBaseIcon(iconUriString: String?, appName: String): Bitmap {
        if (!iconUriString.isNullOrBlank()) {
            val decoded = runCatching {
                val uri = Uri.parse(iconUriString)
                appContext.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()
            if (decoded != null) {
                return decoded
            }
        }
        return generateDefaultIconBitmap(appName)
    }

    private fun generateDefaultIconBitmap(appName: String): Bitmap {
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                Color.parseColor("#2563EB"),
                Color.parseColor("#7C3AED"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 112f, 112f, bgPaint)

        val letter = appName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "N"
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 240f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val baseline = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(letter, size / 2f, baseline, textPaint)
        return bitmap
    }

    private fun encodePng(source: Bitmap, targetPx: Int): ByteArray {
        val scaled = Bitmap.createScaledBitmap(source, targetPx, targetPx, true)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
        if (scaled !== source) {
            scaled.recycle()
        }
        return out.toByteArray()
    }

    private fun collectFilesRecursive(dir: File): List<File> {
        val list = mutableListOf<File>()
        fun walk(current: File) {
            val children = current.listFiles() ?: return
            for (child in children) {
                if (child.name == ".git" || child.name == "node_modules") continue
                if (child.isDirectory) {
                    walk(child)
                } else if (child.isFile) {
                    list.add(child)
                }
            }
        }
        walk(dir)
        return list
    }

    private fun zipDirectoryIntoPrefix(zos: ZipOutputStream, dir: File, prefix: String): Int {
        val files = collectFilesRecursive(dir)
        for (file in files) {
            val relPath = file.relativeTo(dir).path.replace('\\', '/')
            writeFileEntry(zos, "$prefix$relPath", file)
        }
        return files.size
    }

    private fun writeTextEntry(zos: ZipOutputStream, path: String, content: String) {
        writeBytesEntry(zos, path, content.toByteArray(StandardCharsets.UTF_8))
    }

    private fun writeBytesEntry(zos: ZipOutputStream, path: String, bytes: ByteArray) {
        zos.putNextEntry(ZipEntry(path))
        zos.write(bytes)
        zos.closeEntry()
    }

    private fun writeFileEntry(zos: ZipOutputStream, path: String, file: File) {
        zos.putNextEntry(ZipEntry(path))
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                zos.write(buffer, 0, read)
            }
        }
        zos.closeEntry()
    }

    private fun injectPwaTagsIntoHtml(html: String, appName: String): String {
        val headInjection = """
            |    <link rel="manifest" href="manifest.json">
            |    <link rel="icon" type="image/png" href="icon-192.png">
            |    <meta name="theme-color" content="#2563EB">
            |    <meta name="apple-mobile-web-app-capable" content="yes">
            |    <meta name="apple-mobile-web-app-title" content="${escapeXml(appName)}">
        """.trimMargin()

        val swScript = """
            |<script>
            |if ('serviceWorker' in navigator) {
            |  window.addEventListener('load', function() {
            |    navigator.serviceWorker.register('./sw.js').catch(function() {});
            |  });
            |}
            |</script>
        """.trimMargin()

        var result = html
        if (!result.contains("manifest.json", ignoreCase = true)) {
            result = if (result.contains("</head>", ignoreCase = true)) {
                result.replaceFirst(Regex("</head>", RegexOption.IGNORE_CASE), "$headInjection\n</head>")
            } else {
                "$headInjection\n$result"
            }
        }
        if (!result.contains("serviceWorker.register", ignoreCase = true)) {
            result = if (result.contains("</body>", ignoreCase = true)) {
                result.replaceFirst(Regex("</body>", RegexOption.IGNORE_CASE), "$swScript\n</body>")
            } else {
                "$result\n$swScript"
            }
        }
        return result
    }

    private fun generateUrlWrapperHtml(config: WebToApkConfig): String {
        val safeTitle = escapeXml(config.appName.trim())
        val safeUrl = config.liveUrl.trim().ifBlank { "https://example.com" }
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <title>$safeTitle</title>
              <link rel="manifest" href="manifest.json" />
              <link rel="icon" type="image/png" href="icon-192.png" />
              <meta name="theme-color" content="#2563EB" />
              <style>
                html, body, iframe { margin: 0; padding: 0; width: 100%; height: 100%; border: 0; overflow: hidden; background: #F8FAFC; }
              </style>
            </head>
            <body>
              <iframe src="$safeUrl" allow="fullscreen; clipboard-read; clipboard-write"></iframe>
              <script>
                if ('serviceWorker' in navigator) {
                  window.addEventListener('load', () => navigator.serviceWorker.register('./sw.js').catch(() => {}));
                }
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun generatePwaManifestJson(config: WebToApkConfig): String {
        val json = JSONObject().apply {
            put("name", config.appName.trim())
            put("short_name", config.appName.trim().take(16))
            put("start_url", "./index.html")
            put("display", if (config.fullscreenMode) "fullscreen" else "standalone")
            put("background_color", "#FFFFFF")
            put("theme_color", "#2563EB")
            put(
                "icons",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("src", "icon-192.png")
                            put("sizes", "192x192")
                            put("type", "image/png")
                        }
                    )
                    put(
                        JSONObject().apply {
                            put("src", "icon-512.png")
                            put("sizes", "512x512")
                            put("type", "image/png")
                        }
                    )
                }
            )
        }
        return json.toString(2)
    }

    private fun generateServiceWorkerJs(slug: String, cachedUrls: List<String>): String {
        val urlsJson = JSONArray(cachedUrls).toString()
        return """
            const CACHE_NAME = '$slug-pwa-v1';
            const PRECACHE_URLS = $urlsJson;

            self.addEventListener('install', event => {
              event.waitUntil(
                caches.open(CACHE_NAME).then(cache => cache.addAll(PRECACHE_URLS)).then(() => self.skipWaiting())
              );
            });

            self.addEventListener('activate', event => {
              event.waitUntil(
                caches.keys().then(keys =>
                  Promise.all(keys.filter(k => k !== CACHE_NAME).map(k => caches.delete(k)))
                ).then(() => self.clients.claim())
              );
            });

            self.addEventListener('fetch', event => {
              if (event.request.method !== 'GET') return;
              event.respondWith(
                caches.match(event.request).then(cached => cached || fetch(event.request))
              );
            });
        """.trimIndent()
    }

    private fun generateSettingsGradle(config: WebToApkConfig): String {
        val safeName = config.appName.trim().replace("\"", "")
        return """
            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    google()
                    mavenCentral()
                }
            }
            rootProject.name = "$safeName"
            include(":app")
        """.trimIndent()
    }

    private fun generateRootBuildGradle(): String = """
        plugins {
            id("com.android.application") version "8.5.2" apply false
            id("org.jetbrains.kotlin.android") version "1.9.24" apply false
        }
    """.trimIndent()

    private fun generateGradleProperties(): String = """
        org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
        android.useAndroidX=true
        kotlin.code.style=official
        android.nonTransitiveRClass=true
    """.trimIndent()

    private fun generateAppBuildGradle(config: WebToApkConfig): String {
        val swipeDep = if (config.pullToRefresh) {
            "\n    implementation(\"androidx.swiperefreshlayout:swiperefreshlayout:1.1.0\")"
        } else ""
        return """
            plugins {
                id("com.android.application")
                id("org.jetbrains.kotlin.android")
            }

            android {
                namespace = "${config.packageName.trim()}"
                compileSdk = 34

                defaultConfig {
                    applicationId = "${config.packageName.trim()}"
                    minSdk = 26
                    targetSdk = 34
                    versionCode = ${config.versionCode}
                    versionName = "${config.versionName.trim()}"
                }

                buildTypes {
                    release {
                        isMinifyEnabled = false
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro"
                        )
                    }
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
                kotlinOptions {
                    jvmTarget = "17"
                }
            }

            dependencies {
                implementation("androidx.core:core-ktx:1.13.1")
                implementation("androidx.appcompat:appcompat:1.7.0")
                implementation("com.google.android.material:material:1.12.0")$swipeDep
            }
        """.trimIndent()
    }

    private fun generateManifestXml(config: WebToApkConfig): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android">

            <uses-permission android:name="android.permission.INTERNET" />
            <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

            <application
                android:allowBackup="true"
                android:icon="@mipmap/ic_launcher"
                android:label="@string/app_name"
                android:roundIcon="@mipmap/ic_launcher_round"
                android:supportsRtl="true"
                android:theme="@style/Theme.NovaWebApp"
                android:usesCleartextTraffic="true">
                <activity
                    android:name=".MainActivity"
                    android:configChanges="orientation|screenSize|keyboardHidden"
                    android:exported="true">
                    <intent-filter>
                        <action android:name="android.intent.action.MAIN" />
                        <category android:name="android.intent.category.LAUNCHER" />
                    </intent-filter>
                </activity>
            </application>

        </manifest>
    """.trimIndent()

    private fun generateStringsXml(config: WebToApkConfig): String = """
        <resources>
            <string name="app_name">${escapeXml(config.appName.trim())}</string>
        </resources>
    """.trimIndent()

    private fun generateThemesXml(config: WebToApkConfig): String {
        val fullscreenTag = if (config.fullscreenMode) {
            "\n        <item name=\"android:windowFullscreen\">true</item>"
        } else ""
        return """
            <resources>
                <style name="Theme.NovaWebApp" parent="Theme.Material3.Light.NoActionBar">
                    <item name="android:statusBarColor">#FFFFFF</item>
                    <item name="android:windowLightStatusBar">true</item>$fullscreenTag
                </style>
            </resources>
        """.trimIndent()
    }

    private fun generateMainActivityKt(config: WebToApkConfig): String {
        val pkg = config.packageName.trim()
        val entryUrl = if (config.sourceType == WebToApkSourceType.LOCAL_PROJECT) {
            "file:///android_asset/www/index.html"
        } else {
            config.liveUrl.trim()
        }
        val swipeImport = if (config.pullToRefresh) {
            "import androidx.swiperefreshlayout.widget.SwipeRefreshLayout\n"
        } else ""

        val rootViewCode = if (config.pullToRefresh) {
            """
                val swipeRefresh = SwipeRefreshLayout(this).apply {
                    addView(webView)
                    setOnRefreshListener { webView.reload() }
                }
                swipeRefreshLayout = swipeRefresh
                setContentView(swipeRefresh)
            """.trimIndent()
        } else {
            "setContentView(webView)"
        }

        val swipeField = if (config.pullToRefresh) {
            "private var swipeRefreshLayout: SwipeRefreshLayout? = null\n"
        } else ""

        val stopRefreshingLine = if (config.pullToRefresh) {
            "swipeRefreshLayout?.isRefreshing = false"
        } else ""

        val fullscreenSnippet = if (config.fullscreenMode) {
            """
                WindowCompat.setDecorFitsSystemWindows(window, false)
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            """.trimIndent()
        } else ""

        val externalLinksLogic = if (config.openExternalLinksInBrowser) {
            """
                val targetUri = request?.url ?: return false
                val targetHost = targetUri.host.orEmpty()
                val startHost = Uri.parse(ENTRY_URL).host.orEmpty()
                val isExternal = (targetUri.scheme == "http" || targetUri.scheme == "https") &&
                    (ENTRY_URL.startsWith("file://") || (startHost.isNotEmpty() && !targetHost.equals(startHost, ignoreCase = true)))
                if (isExternal) {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, targetUri)) }
                    return true
                }
                return false
            """.trimIndent()
        } else {
            "return false"
        }

        return """
            package $pkg

            import android.annotation.SuppressLint
            import android.content.Intent
            import android.net.Uri
            import android.os.Bundle
            import android.webkit.WebChromeClient
            import android.webkit.WebResourceRequest
            import android.webkit.WebSettings
            import android.webkit.WebView
            import android.webkit.WebViewClient
            import androidx.activity.OnBackPressedCallback
            import androidx.appcompat.app.AppCompatActivity
            import androidx.core.view.WindowCompat
            import androidx.core.view.WindowInsetsCompat
            import androidx.core.view.WindowInsetsControllerCompat
            $swipeImport
            class MainActivity : AppCompatActivity() {

                private lateinit var webView: WebView
                $swipeField
                @SuppressLint("SetJavaScriptEnabled")
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    $fullscreenSnippet

                    webView = WebView(this).apply {
                        settings.apply {
                            javaScriptEnabled = ${config.enableJavaScript}
                            domStorageEnabled = true
                            databaseEnabled = true
                            allowFileAccess = true
                            allowContentAccess = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            setSupportZoom(${config.pinchToZoom})
                            builtInZoomControls = ${config.pinchToZoom}
                            displayZoomControls = false
                        }
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                $externalLinksLogic
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                $stopRefreshingLine
                            }
                        }
                    }

                    $rootViewCode
                    webView.loadUrl(ENTRY_URL)

                    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                        override fun handleOnBackPressed() {
                            if (webView.canGoBack()) {
                                webView.goBack()
                            } else {
                                isEnabled = false
                                onBackPressedDispatcher.onBackPressed()
                            }
                        }
                    })
                }

                companion object {
                    private const val ENTRY_URL = "$entryUrl"
                }
            }
        """.trimIndent()
    }

    private fun generateGithubWorkflowYml(config: WebToApkConfig): String = """
        name: Build Android APK (${config.appName.trim()})

        on:
          push:
            branches: [ "main", "master" ]
          workflow_dispatch:

        jobs:
          build-apk:
            runs-on: ubuntu-latest
            steps:
              - name: Checkout code
                uses: actions/checkout@v4

              - name: Set up JDK 17
                uses: actions/setup-java@v4
                with:
                  distribution: 'temurin'
                  java-version: '17'

              - name: Setup Gradle
                uses: gradle/actions/setup-gradle@v4
                with:
                  gradle-version: '8.7'

              - name: Build Debug APK
                run: gradle assembleDebug

              - name: Upload APK Artifact
                uses: actions/upload-artifact@v4
                with:
                  name: ${slugify(config.appName)}-debug-apk
                  path: app/build/outputs/apk/debug/*.apk
    """.trimIndent()

    private fun generateAndroidReadme(config: WebToApkConfig): String = """
        # ${config.appName.trim()}

        Generated by **Nova IDE Web-to-APK Converter**.

        - **Package Name:** `${config.packageName.trim()}`
        - **Version:** `${config.versionName.trim()}` (code `${config.versionCode}`)
        - **Source:** ${if (config.sourceType == WebToApkSourceType.LOCAL_PROJECT) "Bundled offline assets (`app/src/main/assets/www/`)" else config.liveUrl.trim()}

        ## How to Build the APK
        1. **Automatic Cloud Build (GitHub Actions):** Push this folder to a GitHub repository. The included `.github/workflows/build-apk.yml` automatically builds and uploads your installable `.apk` artifact.
        2. **Local Build (Android Studio / Gradle):** Open this folder in Android Studio or run `gradle assembleDebug`.
    """.trimIndent()

    private fun slugify(input: String): String {
        return input.trim()
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "nova_app" }
    }

    private fun escapeXml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "\\'")
    }

    companion object {
        const val EXPORTS_DIR_NAME = "exports"
        const val MAX_PROJECT_SIZE_BYTES = 100L * 1024L * 1024L // 100 MB guard
        val PACKAGE_NAME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")

        fun formatSize(bytes: Long): String {
            return when {
                bytes >= 1024L * 1024L -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
                bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
                else -> "$bytes B"
            }
        }
    }
}
