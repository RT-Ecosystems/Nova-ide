package com.example.domain.model

enum class WebToApkSourceType(val label: String) {
    LOCAL_PROJECT("Local Project"),
    LIVE_URL("Live Website URL")
}

enum class WebToApkExportKind(val badge: String, val displayName: String) {
    ANDROID_PROJECT_ZIP("ANDROID", "Android WebView Project (.zip)"),
    PWA_BUNDLE_ZIP("PWA", "Standalone PWA Bundle (.zip)"),
    SIGNED_APK("APK", "Signed Android APK (.apk)")
}

data class WebToApkConfig(
    val sourceType: WebToApkSourceType = WebToApkSourceType.LOCAL_PROJECT,
    val projectDirPath: String? = null,
    val projectName: String? = null,
    val liveUrl: String = "",
    val appName: String = "My Nova App",
    val packageName: String = "com.nova.myapp",
    val versionName: String = "1.0.0",
    val versionCode: Int = 1,
    val iconUriString: String? = null,
    val fullscreenMode: Boolean = false,
    val enableJavaScript: Boolean = true,
    val pinchToZoom: Boolean = false,
    val pullToRefresh: Boolean = true,
    val openExternalLinksInBrowser: Boolean = true
)

data class ExportedArtifact(
    val fileName: String,
    val absolutePath: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val kind: WebToApkExportKind
)

data class WebToApkBuildState(
    val isBuilding: Boolean = false,
    val progress: Float = 0f,
    val logs: List<String> = emptyList(),
    val lastExport: ExportedArtifact? = null,
    val errorMessage: String? = null
)
