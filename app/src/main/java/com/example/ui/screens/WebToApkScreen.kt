package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.builder.WebToApkBuilder
import com.example.domain.model.ExportedArtifact
import com.example.domain.model.JavaCompileState
import com.example.domain.model.JavaProjectOption
import com.example.domain.model.KeystoreInfo
import com.example.domain.model.NativeBuildArtifact
import com.example.domain.model.ToolchainStatus
import com.example.domain.model.WebToApkBuildState
import com.example.domain.model.WebToApkConfig
import com.example.domain.model.WebToApkExportKind
import com.example.domain.model.WebToApkSourceType
import com.example.domain.model.WorkspaceItem
import com.example.ui.components.NativeJavaBuildPanel
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentAmberContainer
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentEmeraldBorder
import com.example.ui.theme.AccentEmeraldContainer
import com.example.ui.theme.AccentRose
import com.example.ui.theme.AccentRoseContainer
import com.example.ui.theme.AccentViolet
import com.example.ui.theme.BorderLight
import com.example.ui.theme.BrandIndigo
import com.example.ui.theme.BrandIndigoBorder
import com.example.ui.theme.BrandIndigoContainer
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.OnBrandLight
import com.example.ui.theme.SurfaceBackground
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPlaceholder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class BuildTabMode(val label: String) {
    WEB_TO_APK("Web-to-APK"),
    NATIVE_JAVA("Native Java Build")
}

@Composable
fun WebToApkScreen(
    rootProjectFolders: List<WorkspaceItem>,
    buildState: WebToApkBuildState,
    recentExports: List<ExportedArtifact>,
    toolchainStatus: ToolchainStatus,
    keystoreInfo: KeystoreInfo,
    javaProjects: List<JavaProjectOption>,
    javaCompileState: JavaCompileState,
    onOpenDrawer: () -> Unit,
    onCalculateProjectSize: suspend (String?) -> Long,
    onBuildAndroidZip: (WebToApkConfig) -> Unit,
    onBuildPwaZip: (WebToApkConfig) -> Unit,
    onDeleteExport: (absolutePath: String) -> Unit,
    onGetShareUri: (File) -> Uri,
    onImportAndroidJar: (Uri) -> Unit,
    onOpenToolchainSettings: () -> Unit,
    onCreateSampleJavaProject: () -> Unit,
    onCompileJavaProject: (projectPath: String) -> Unit,
    onBuildSignedApk: (projectPath: String) -> Unit,
    onAutoFixJavaErrors: (projectPath: String) -> Unit,
    onOpenWorkspaceFiles: () -> Unit,
    onShowToast: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var activeTabMode by remember { mutableStateOf(BuildTabMode.WEB_TO_APK) }

    var sourceType by remember { mutableStateOf(WebToApkSourceType.LOCAL_PROJECT) }
    var selectedProjectPath by remember { mutableStateOf<String?>(null) }
    var selectedProjectName by remember { mutableStateOf<String?>(null) }
    var projectDropdownExpanded by remember { mutableStateOf(false) }
    var selectedProjectSizeBytes by remember { mutableLongStateOf(0L) }
    var isCalculatingSize by remember { mutableStateOf(false) }

    var liveUrl by remember { mutableStateOf("https://") }
    var appName by remember { mutableStateOf("My Nova App") }
    var packageName by remember { mutableStateOf("com.nova.myapp") }
    var versionName by remember { mutableStateOf("1.0.0") }
    var versionCodeText by remember { mutableStateOf("1") }
    var iconUriString by remember { mutableStateOf<String?>(null) }
    var iconPreviewBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    var fullscreenMode by remember { mutableStateOf(false) }
    var enableJavaScript by remember { mutableStateOf(true) }
    var pinchToZoom by remember { mutableStateOf(false) }
    var pullToRefresh by remember { mutableStateOf(true) }
    var openExternalLinksInBrowser by remember { mutableStateOf(true) }

    // Default to first workspace project if none chosen yet
    LaunchedEffect(rootProjectFolders) {
        if (selectedProjectPath == null && rootProjectFolders.isNotEmpty()) {
            val first = rootProjectFolders.first()
            selectedProjectPath = first.absolutePath
            selectedProjectName = first.name
        } else if (selectedProjectPath != null && rootProjectFolders.none { it.absolutePath == selectedProjectPath }) {
            val fallback = rootProjectFolders.firstOrNull()
            selectedProjectPath = fallback?.absolutePath
            selectedProjectName = fallback?.name
        }
    }

    // Recompute folder size whenever selected local project changes
    LaunchedEffect(sourceType, selectedProjectPath) {
        if (sourceType == WebToApkSourceType.LOCAL_PROJECT && selectedProjectPath != null) {
            isCalculatingSize = true
            selectedProjectSizeBytes = onCalculateProjectSize(selectedProjectPath)
            isCalculatingSize = false
        } else {
            selectedProjectSizeBytes = 0L
            isCalculatingSize = false
        }
    }

    // Decode icon bitmap safely off the UI thread when user picks an image
    LaunchedEffect(iconUriString) {
        val uriStr = iconUriString
        if (uriStr.isNullOrBlank()) {
            iconPreviewBitmap = null
        } else {
            iconPreviewBitmap = withContext(Dispatchers.IO) {
                runCatching {
                    val uri = Uri.parse(uriStr)
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        }
    }

    // Zero-permission Android Photo Picker (Play Policy compliant)
    val iconPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            iconUriString = uri.toString()
        }
    }

    val isOverSizeLimit = sourceType == WebToApkSourceType.LOCAL_PROJECT &&
        selectedProjectSizeBytes > WebToApkBuilder.MAX_PROJECT_SIZE_BYTES
    val isPackageNameValid = WebToApkBuilder.PACKAGE_NAME_REGEX.matches(packageName.trim())
    val parsedVersionCode = versionCodeText.trim().toIntOrNull() ?: 0
    val isLiveUrlValid = sourceType != WebToApkSourceType.LIVE_URL ||
        liveUrl.trim().startsWith("https://", ignoreCase = true) ||
        liveUrl.trim().startsWith("http://", ignoreCase = true)
    val hasValidLocalProject = sourceType != WebToApkSourceType.LOCAL_PROJECT ||
        !selectedProjectPath.isNullOrBlank()

    val canBuild = !buildState.isBuilding &&
        !isOverSizeLimit &&
        appName.trim().isNotEmpty() &&
        isPackageNameValid &&
        versionName.trim().isNotEmpty() &&
        parsedVersionCode >= 1 &&
        isLiveUrlValid &&
        hasValidLocalProject

    fun currentConfig(): WebToApkConfig = WebToApkConfig(
        sourceType = sourceType,
        projectDirPath = selectedProjectPath,
        projectName = selectedProjectName,
        liveUrl = liveUrl.trim(),
        appName = appName.trim(),
        packageName = packageName.trim(),
        versionName = versionName.trim(),
        versionCode = parsedVersionCode.coerceAtLeast(1),
        iconUriString = iconUriString,
        fullscreenMode = fullscreenMode,
        enableJavaScript = enableJavaScript,
        pinchToZoom = pinchToZoom,
        pullToRefresh = pullToRefresh,
        openExternalLinksInBrowser = openExternalLinksInBrowser
    )

    fun shareArtifact(artifact: ExportedArtifact) {
        val file = File(artifact.absolutePath)
        if (!file.exists()) {
            onShowToast("Export file not found on device.", true)
            return
        }
        val mimeType = if (artifact.kind == WebToApkExportKind.SIGNED_APK || artifact.fileName.endsWith(".apk", ignoreCase = true)) {
            "application/vnd.android.package-archive"
        } else {
            "application/zip"
        }
        shareExportFile(
            context = context,
            uri = onGetShareUri(file),
            fileName = artifact.fileName,
            mimeType = mimeType,
            onError = { msg -> onShowToast(msg, true) }
        )
    }

    fun shareSignedNativeApk(artifact: NativeBuildArtifact) {
        val file = File(artifact.absolutePath)
        if (!file.exists()) {
            onShowToast("Signed APK file not found on device.", true)
            return
        }
        shareExportFile(
            context = context,
            uri = onGetShareUri(file),
            fileName = artifact.fileName,
            mimeType = "application/vnd.android.package-archive",
            onError = { msg -> onShowToast(msg, true) }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceBackground)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .align(Alignment.TopCenter)
        ) {
            // Top App Bar with 3-Line Menu Drawer Trigger & 100 MB Guard Badge
            Surface(
                color = SurfaceCard,
                border = BorderStroke(1.dp, BorderLight),
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier.testTag("web2apk_open_drawer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = "Open navigation drawer",
                            tint = TextPrimary
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.web2apk_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Package web apps as Android projects or PWAs",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Surface(
                        color = AccentEmeraldContainer,
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, AccentEmeraldBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = "100 MB safety guard",
                                tint = AccentEmerald,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "100 MB",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentEmerald
                            )
                        }
                    }
                }
            }

            // Segmented Switcher: Web-to-APK | Native Java Build
            Surface(
                color = SurfaceCard,
                border = BorderStroke(1.dp, BorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = SurfaceElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, BorderLight),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        BuildTabMode.entries.forEach { mode ->
                            val selected = activeTabMode == mode
                            Surface(
                                color = if (selected) SurfaceCard else SurfaceElevated,
                                shape = RoundedCornerShape(10.dp),
                                border = if (selected) BorderStroke(1.dp, BrandIndigoBorder) else null,
                                shadowElevation = if (selected) 1.dp else 0.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { activeTabMode = mode }
                                    .testTag("build_tab_mode_${mode.name.lowercase()}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 9.dp, horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = mode.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (selected) BrandIndigo else TextSecondary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (activeTabMode == BuildTabMode.NATIVE_JAVA) {
                NativeJavaBuildPanel(
                    toolchainStatus = toolchainStatus,
                    keystoreInfo = keystoreInfo,
                    javaProjects = javaProjects,
                    compileState = javaCompileState,
                    onImportAndroidJar = onImportAndroidJar,
                    onOpenToolchainSettings = onOpenToolchainSettings,
                    onCreateSampleJavaProject = onCreateSampleJavaProject,
                    onOpenWorkspaceFiles = onOpenWorkspaceFiles,
                    onCompileJavaProject = onCompileJavaProject,
                    onBuildSignedApk = onBuildSignedApk,
                    onShareSignedApk = ::shareSignedNativeApk,
                    onAutoFixJavaErrors = onAutoFixJavaErrors,
                    modifier = Modifier.weight(1f)
                )
            } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("web2apk_scroll_list"),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Source Selector Card
                item(key = "source_card") {
                    ConverterSectionCard(
                        title = "1. Web Source",
                        subtitle = "Bundle an offline /projects/ folder or wrap a live URL"
                    ) {
                        // Segmented Source Switcher
                        Surface(
                            color = SurfaceElevated,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, BorderLight),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                WebToApkSourceType.entries.forEach { option ->
                                    val selected = sourceType == option
                                    Surface(
                                        color = if (selected) SurfaceCard else SurfaceElevated,
                                        shape = RoundedCornerShape(10.dp),
                                        border = if (selected) BorderStroke(1.dp, BrandIndigoBorder) else null,
                                        shadowElevation = if (selected) 1.dp else 0.dp,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { sourceType = option }
                                            .testTag("web2apk_source_${option.name.lowercase()}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (option == WebToApkSourceType.LOCAL_PROJECT) {
                                                    Icons.Filled.FolderOpen
                                                } else {
                                                    Icons.Filled.Language
                                                },
                                                contentDescription = null,
                                                tint = if (selected) BrandIndigo else TextSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = option.label,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                                color = if (selected) BrandIndigo else TextSecondary,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (sourceType == WebToApkSourceType.LOCAL_PROJECT) {
                            if (rootProjectFolders.isEmpty()) {
                                Surface(
                                    color = AccentAmberContainer,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, AccentAmber.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "No workspace projects yet",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = TextPrimary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Create or generate a project in Files or Builder first.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = onOpenWorkspaceFiles,
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, BrandIndigo)
                                        ) {
                                            Text("Open Files", color = BrandIndigo)
                                        }
                                    }
                                }
                            } else {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Surface(
                                        color = SurfaceBackground,
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, BorderLight),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { projectDropdownExpanded = true }
                                            .testTag("web2apk_project_dropdown")
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.FolderOpen,
                                                    contentDescription = "Selected project",
                                                    tint = BrandIndigo,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = selectedProjectName ?: "Select project...",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = TextPrimary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = if (isCalculatingSize) {
                                                            "Calculating folder size..."
                                                        } else {
                                                            "Size: ${WebToApkBuilder.formatSize(selectedProjectSizeBytes)} / 100 MB max"
                                                        },
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (isOverSizeLimit) AccentRose else TextMuted
                                                    )
                                                }
                                            }
                                            Icon(
                                                imageVector = Icons.Filled.KeyboardArrowDown,
                                                contentDescription = "Expand project list",
                                                tint = TextSecondary
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = projectDropdownExpanded,
                                        onDismissRequest = { projectDropdownExpanded = false },
                                        containerColor = SurfaceCard
                                    ) {
                                        rootProjectFolders.forEach { folder ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = folder.name,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = if (folder.absolutePath == selectedProjectPath) BrandIndigo else TextPrimary,
                                                        fontWeight = if (folder.absolutePath == selectedProjectPath) FontWeight.SemiBold else FontWeight.Normal
                                                    )
                                                },
                                                onClick = {
                                                    projectDropdownExpanded = false
                                                    selectedProjectPath = folder.absolutePath
                                                    selectedProjectName = folder.name
                                                    val cleanSlug = folder.name.lowercase(Locale.US)
                                                        .replace(Regex("[^a-z0-9]"), "")
                                                        .ifBlank { "myapp" }
                                                    if (appName == "My Nova App" || appName.isBlank()) {
                                                        appName = folder.name.replace('-', ' ').replace('_', ' ')
                                                            .replaceFirstChar { it.uppercase() }
                                                    }
                                                    if (packageName == "com.nova.myapp") {
                                                        packageName = "com.nova.$cleanSlug"
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }

                                // 100 MB Safety Guard Blocker Warning
                                AnimatedVisibility(visible = isOverSizeLimit) {
                                    Surface(
                                        color = AccentRoseContainer,
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, AccentRose.copy(alpha = 0.4f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("web2apk_size_guard_warning")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Warning,
                                                contentDescription = "Size limit exceeded",
                                                tint = AccentRose,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(
                                                    text = "100 MB Safety Limit Exceeded",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = AccentRose
                                                )
                                                Text(
                                                    text = "This project is ${WebToApkBuilder.formatSize(selectedProjectSizeBytes)}. Remove oversized videos/assets so the bundle stays under 100 MB before building.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = TextPrimary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = liveUrl,
                                onValueChange = { liveUrl = it },
                                label = { Text("Live Website URL") },
                                placeholder = { Text("https://your-site.com") },
                                singleLine = true,
                                isError = liveUrl.isNotBlank() && !isLiveUrlValid,
                                supportingText = {
                                    if (liveUrl.isNotBlank() && !isLiveUrlValid) {
                                        Text("Must start with https:// or http://", color = AccentRose)
                                    } else {
                                        Text("Wraps your deployed website inside a native Android WebView.")
                                    }
                                },
                                colors = converterTextFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("web2apk_live_url_input")
                            )
                        }
                    }
                }

                // 2. App Identity & Icon Card
                item(key = "config_card") {
                    ConverterSectionCard(
                        title = "2. App Identity & Icon",
                        subtitle = "Configure launcher metadata and app icon"
                    ) {
                        // App Icon Picker Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        Brush.linearGradient(listOf(BrandIndigo, AccentViolet))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                val bmp = iconPreviewBitmap
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp,
                                        contentDescription = "Selected app icon preview",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = appName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "N",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = OnBrandLight
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "App Launcher Icon",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Auto-resized to mdpi–xxxhdpi & 192/512px PWA icons.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            iconPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, BrandIndigo),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("web2apk_pick_icon_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Image,
                                            contentDescription = null,
                                            tint = BrandIndigo,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (iconUriString == null) "Pick Icon" else "Change",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = BrandIndigo
                                        )
                                    }

                                    if (iconUriString != null) {
                                        OutlinedButton(
                                            onClick = { iconUriString = null },
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, BorderLight),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            modifier = Modifier.testTag("web2apk_reset_icon_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Refresh,
                                                contentDescription = "Reset icon",
                                                tint = TextSecondary,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Reset",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = appName,
                            onValueChange = { appName = it },
                            label = { Text("App Name") },
                            placeholder = { Text("My Nova App") },
                            singleLine = true,
                            isError = appName.isBlank(),
                            colors = converterTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("web2apk_app_name_input")
                        )

                        OutlinedTextField(
                            value = packageName,
                            onValueChange = { packageName = it.replace(" ", "") },
                            label = { Text("Package Name (Application ID)") },
                            placeholder = { Text("com.nova.myapp") },
                            singleLine = true,
                            isError = !isPackageNameValid,
                            supportingText = {
                                if (!isPackageNameValid) {
                                    Text(
                                        "Use reverse-domain format like com.studio.myapp",
                                        color = AccentRose
                                    )
                                } else {
                                    Text("Valid Android applicationId")
                                }
                            },
                            colors = converterTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("web2apk_package_name_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = versionName,
                                onValueChange = { versionName = it },
                                label = { Text("Version Name") },
                                singleLine = true,
                                isError = versionName.isBlank(),
                                colors = converterTextFieldColors(),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("web2apk_version_name_input")
                            )
                            OutlinedTextField(
                                value = versionCodeText,
                                onValueChange = { input ->
                                    versionCodeText = input.filter { it.isDigit() }.take(6)
                                },
                                label = { Text("Version Code") },
                                singleLine = true,
                                isError = parsedVersionCode < 1,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = converterTextFieldColors(),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("web2apk_version_code_input")
                            )
                        }
                    }
                }

                // 3. WebView Capabilities Card
                item(key = "toggles_card") {
                    ConverterSectionCard(
                        title = "3. WebView Capabilities",
                        subtitle = "Customize runtime behavior in the generated app"
                    ) {
                        ConverterToggleRow(
                            title = "Enable JavaScript",
                            subtitle = "Required for interactive web apps, React, and Tailwind",
                            checked = enableJavaScript,
                            onCheckedChange = { enableJavaScript = it },
                            testTag = "web2apk_toggle_js"
                        )
                        HorizontalDivider(thickness = 1.dp, color = BorderLight)
                        ConverterToggleRow(
                            title = "Fullscreen Mode",
                            subtitle = "Immersive edge-to-edge display hiding status bars",
                            checked = fullscreenMode,
                            onCheckedChange = { fullscreenMode = it },
                            testTag = "web2apk_toggle_fullscreen"
                        )
                        HorizontalDivider(thickness = 1.dp, color = BorderLight)
                        ConverterToggleRow(
                            title = "Pull-to-Refresh",
                            subtitle = "Swipe down to reload content via SwipeRefreshLayout",
                            checked = pullToRefresh,
                            onCheckedChange = { pullToRefresh = it },
                            testTag = "web2apk_toggle_refresh"
                        )
                        HorizontalDivider(thickness = 1.dp, color = BorderLight)
                        ConverterToggleRow(
                            title = "Pinch-to-Zoom",
                            subtitle = "Allow multi-touch zoom gestures inside WebView",
                            checked = pinchToZoom,
                            onCheckedChange = { pinchToZoom = it },
                            testTag = "web2apk_toggle_zoom"
                        )
                        HorizontalDivider(thickness = 1.dp, color = BorderLight)
                        ConverterToggleRow(
                            title = "Open External Links in Browser",
                            subtitle = "Launch third-party URLs in the user's default browser",
                            checked = openExternalLinksInBrowser,
                            onCheckedChange = { openExternalLinksInBrowser = it },
                            testTag = "web2apk_toggle_external_links"
                        )
                    }
                }

                // 4. Build & Export Actions + Live Console
                item(key = "build_card") {
                    ConverterSectionCard(
                        title = "4. Build & Export",
                        subtitle = "Choose Android Gradle WebView project (with GitHub Actions APK CI) or Standalone PWA"
                    ) {
                        Button(
                            onClick = { onBuildAndroidZip(currentConfig()) },
                            enabled = canBuild,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandIndigo,
                                contentColor = OnBrandLight,
                                disabledContainerColor = SurfaceElevated,
                                disabledContentColor = TextMuted
                            ),
                            contentPadding = PaddingValues(vertical = 13.dp, horizontal = 16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("web2apk_build_android_button")
                        ) {
                            if (buildState.isBuilding) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = OnBrandLight,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Android,
                                    contentDescription = null,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Build Android WebView Package (.zip)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutlinedButton(
                            onClick = { onBuildPwaZip(currentConfig()) },
                            enabled = canBuild,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(
                                1.dp,
                                if (canBuild) BrandIndigo else BorderLight
                            ),
                            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("web2apk_build_pwa_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.InstallMobile,
                                contentDescription = null,
                                tint = if (canBuild) BrandIndigo else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Export Standalone PWA Bundle (.zip)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (canBuild) BrandIndigo else TextMuted
                            )
                        }

                        if (buildState.isBuilding || buildState.logs.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            if (buildState.isBuilding) {
                                LinearProgressIndicator(
                                    progress = { buildState.progress.coerceIn(0f, 1f) },
                                    color = BrandIndigo,
                                    trackColor = BrandIndigoContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                )
                            }

                            Surface(
                                color = SurfaceBackground,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, BorderLight),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("web2apk_build_console")
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Build Output",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextSecondary
                                    )
                                    buildState.logs.forEach { line ->
                                        Text(
                                            text = line,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontSize = 11.5.sp,
                                            lineHeight = 16.sp,
                                            color = when {
                                                line.startsWith("❌") -> AccentRose
                                                line.startsWith("✅") -> AccentEmerald
                                                else -> TextPrimary
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        val latest = buildState.lastExport
                        if (latest != null && !buildState.isBuilding) {
                            Surface(
                                color = AccentEmeraldContainer,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, AccentEmeraldBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("web2apk_last_export_banner")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = "Export ready",
                                        tint = AccentEmerald,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = latest.fileName,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${latest.kind.displayName} • ${WebToApkBuilder.formatSize(latest.sizeBytes)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }
                                    Button(
                                        onClick = { shareArtifact(latest) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = AccentEmerald,
                                            contentColor = OnBrandLight
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        modifier = Modifier.testTag("web2apk_share_latest_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Share,
                                            contentDescription = "Share ZIP",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Share", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. Recent Exports Card
                item(key = "recent_exports_card") {
                    ConverterSectionCard(
                        title = "Recent Exports (${recentExports.size})",
                        subtitle = "Saved inside private /exports/ storage — share or download anytime"
                    ) {
                        if (recentExports.isEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    color = SurfaceElevated,
                                    shape = CircleShape,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.Archive,
                                            contentDescription = null,
                                            tint = TextMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "No exported packages yet. Build an Android WebView ZIP or PWA bundle above.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                recentExports.forEach { artifact ->
                                    RecentExportRow(
                                        artifact = artifact,
                                        onShare = { shareArtifact(artifact) },
                                        onDelete = { onDeleteExport(artifact.absolutePath) }
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            }
        }
    }
}

@Composable
private fun ConverterSectionCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Surface(
        color = SurfaceCard,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, BorderLight),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            content()
        }
    }
}

@Composable
private fun ConverterToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OnBrandLight,
                checkedTrackColor = BrandIndigo,
                uncheckedThumbColor = SurfaceCard,
                uncheckedTrackColor = SurfaceElevated,
                uncheckedBorderColor = BorderLight
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun RecentExportRow(
    artifact: ExportedArtifact,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val badgeBg = when (artifact.kind) {
        WebToApkExportKind.ANDROID_PROJECT_ZIP -> BrandIndigoContainer
        WebToApkExportKind.PWA_BUNDLE_ZIP -> AccentAmberContainer
        WebToApkExportKind.SIGNED_APK -> AccentEmeraldContainer
    }
    val badgeBorder = when (artifact.kind) {
        WebToApkExportKind.ANDROID_PROJECT_ZIP -> BrandIndigoBorder
        WebToApkExportKind.PWA_BUNDLE_ZIP -> AccentAmber.copy(alpha = 0.4f)
        WebToApkExportKind.SIGNED_APK -> AccentEmeraldBorder
    }
    val badgeFg = when (artifact.kind) {
        WebToApkExportKind.ANDROID_PROJECT_ZIP -> BrandIndigo
        WebToApkExportKind.PWA_BUNDLE_ZIP -> AccentAmber
        WebToApkExportKind.SIGNED_APK -> AccentEmerald
    }
    val formattedDate = remember(artifact.createdAt) {
        SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(artifact.createdAt))
    }

    Surface(
        color = SurfaceBackground,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderLight),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("web2apk_export_item_${artifact.fileName}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = badgeBg,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, badgeBorder)
            ) {
                Text(
                    text = artifact.kind.badge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = badgeFg,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = artifact.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${WebToApkBuilder.formatSize(artifact.sizeBytes)} • $formattedDate",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }

            IconButton(
                onClick = onShare,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = "Share ${artifact.fileName}",
                    tint = BrandIndigo,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Delete ${artifact.fileName}",
                    tint = AccentRose,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun converterTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = BrandIndigo,
    unfocusedBorderColor = BorderLight,
    focusedLabelColor = BrandIndigo,
    unfocusedLabelColor = TextSecondary,
    cursorColor = BrandIndigo,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedPlaceholderColor = TextPlaceholder,
    unfocusedPlaceholderColor = TextPlaceholder,
    errorBorderColor = AccentRose,
    errorLabelColor = AccentRose
)

private fun shareExportFile(
    context: Context,
    uri: Uri,
    fileName: String,
    mimeType: String,
    onError: (String) -> Unit
) {
    runCatching {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            putExtra(Intent.EXTRA_TEXT, "Exported from Nova IDE: $fileName")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share or Save $fileName").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }.onFailure { err ->
        onError(err.message ?: "Unable to open share sheet.")
    }
}
