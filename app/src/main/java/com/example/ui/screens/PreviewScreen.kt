package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.domain.model.ConsoleLogEntry
import com.example.domain.model.ConsoleLogLevel
import com.example.domain.model.PreviewProjectOption
import com.example.domain.model.ServerState
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentAmberContainer
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentEmeraldBorder
import com.example.ui.theme.AccentEmeraldContainer
import com.example.ui.theme.AccentRose
import com.example.ui.theme.AccentRoseContainer
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class PreviewViewport(
    val label: String,
    val icon: ImageVector,
    val widthFraction: Float,
    val targetScalePercent: Int,
    val useDesktopUa: Boolean
) {
    MOBILE("Mobile", Icons.Filled.PhoneAndroid, 0.94f, 0, false),
    TABLET("Tablet", Icons.Filled.TabletAndroid, 1.0f, 65, false),
    DESKTOP("Desktop", Icons.Filled.DesktopWindows, 1.0f, 42, true)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PreviewScreen(
    serverState: ServerState,
    previewProjects: List<PreviewProjectOption>,
    consoleLogs: List<ConsoleLogEntry>,
    isAutoFixing: Boolean,
    onSelectProjectAndStart: (PreviewProjectOption) -> Unit,
    onToggleServer: () -> Unit,
    onReloadPreview: () -> Unit,
    onConsoleLog: (level: ConsoleLogLevel, message: String, source: String, lineNumber: Int) -> Unit,
    onClearConsoleLogs: () -> Unit,
    onAutoFixClick: () -> Unit,
    onBrowseFilesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedViewport by remember { mutableStateOf(PreviewViewport.MOBILE) }
    var projectDropdownExpanded by remember { mutableStateOf(false) }
    var consoleExpanded by remember { mutableStateOf(false) }
    var isPageLoading by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var lastLoadedUrl by remember { mutableStateOf<String?>(null) }
    var lastLoadedNonce by remember { mutableLongStateOf(-1L) }
    var lastAppliedViewport by remember { mutableStateOf<PreviewViewport?>(null) }

    val latestOnConsoleLog by rememberUpdatedState(onConsoleLog)

    val activeProjectOption = remember(serverState.activeProjectPath, previewProjects) {
        previewProjects.firstOrNull { it.projectPath == serverState.activeProjectPath }
            ?: previewProjects.firstOrNull()
    }

    val errorCount = remember(consoleLogs) {
        consoleLogs.count { it.level == ConsoleLogLevel.ERROR }
    }

    val targetPreviewUrl = remember(serverState.isRunning, serverState.localUrl, activeProjectOption) {
        if (!serverState.isRunning || serverState.localUrl.isBlank()) {
            ""
        } else {
            val entry = activeProjectOption?.entryHtmlRelativePath.orEmpty()
            if (entry.isBlank() || entry.equals("index.html", true)) {
                "${serverState.localUrl}/"
            } else {
                "${serverState.localUrl}/$entry"
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.apply {
                stopLoading()
                webChromeClient = null
                destroy()
            }
            webViewRef = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceBackground)
            .statusBarsPadding()
    ) {
        // Top Preview Toolbar (Light Theme)
        Surface(
            color = SurfaceCard,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Row 1: Project Selector Dropdown + Status Pill + Action Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Project Dropdown Selector
                    Box(modifier = Modifier.weight(1f)) {
                        Surface(
                            color = SurfaceBackground,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, BorderLight),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = previewProjects.isNotEmpty()) {
                                    projectDropdownExpanded = true
                                }
                                .testTag("preview_project_selector")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = serverState.activeProjectName
                                        ?: activeProjectOption?.projectName
                                        ?: "No HTML projects",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (previewProjects.isNotEmpty()) TextPrimary else TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "Select project",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = projectDropdownExpanded,
                            onDismissRequest = { projectDropdownExpanded = false },
                            containerColor = SurfaceCard
                        ) {
                            previewProjects.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = option.projectName,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = if (option.projectPath == serverState.activeProjectPath) BrandIndigo else TextPrimary
                                            )
                                            Text(
                                                text = option.entryHtmlRelativePath,
                                                fontFamily = JetBrainsMonoFontFamily,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextMuted
                                            )
                                        }
                                    },
                                    onClick = {
                                        projectDropdownExpanded = false
                                        onSelectProjectAndStart(option)
                                    }
                                )
                            }
                        }
                    }

                    // Live Status Pill
                    Surface(
                        color = if (serverState.isRunning) AccentEmeraldContainer else SurfaceElevated,
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(
                            1.dp,
                            if (serverState.isRunning) AccentEmeraldBorder else BorderLight
                        ),
                        modifier = Modifier.testTag("preview_status_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (serverState.isRunning) AccentEmerald else TextPlaceholder)
                            )
                            Text(
                                text = if (serverState.isRunning) "Running :${serverState.port}" else "Stopped",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (serverState.isRunning) AccentEmerald else TextMuted
                            )
                        }
                    }

                    // Reload
                    IconButton(
                        onClick = {
                            onReloadPreview()
                            webViewRef?.reload()
                        },
                        enabled = serverState.isRunning,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("preview_reload_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Reload preview",
                            tint = if (serverState.isRunning) BrandIndigo else TextPlaceholder,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Start / Stop Toggle
                    IconButton(
                        onClick = {
                            if (serverState.isRunning) {
                                onToggleServer()
                            } else if (activeProjectOption != null) {
                                onSelectProjectAndStart(activeProjectOption)
                            }
                        },
                        enabled = serverState.isRunning || activeProjectOption != null,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("preview_start_stop_button")
                    ) {
                        Icon(
                            imageVector = if (serverState.isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = if (serverState.isRunning) "Stop server" else "Start server",
                            tint = if (serverState.isRunning) AccentRose else if (activeProjectOption != null) AccentEmerald else TextPlaceholder,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Open in External Browser
                    IconButton(
                        onClick = {
                            if (targetPreviewUrl.isNotBlank()) {
                                runCatching {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetPreviewUrl)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                            }
                        },
                        enabled = serverState.isRunning && targetPreviewUrl.isNotBlank(),
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("preview_external_browser_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open in external browser",
                            tint = if (serverState.isRunning) TextSecondary else TextPlaceholder,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                // Row 2: Working Viewport Switcher + Live Address Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PreviewViewport.entries.forEach { vp ->
                        val selected = selectedViewport == vp
                        Surface(
                            color = if (selected) BrandIndigoContainer else SurfaceBackground,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (selected) BrandIndigoBorder else BorderLight),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedViewport = vp }
                                .testTag("preview_viewport_${vp.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = vp.icon,
                                    contentDescription = vp.label,
                                    tint = if (selected) BrandIndigo else TextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = vp.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) BrandIndigo else TextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                if (isPageLoading) {
                    LinearProgressIndicator(
                        color = BrandIndigo,
                        trackColor = BrandIndigoContainer,
                        modifier = Modifier.fillMaxWidth().height(2.dp)
                    )
                }

                HorizontalDivider(thickness = 1.dp, color = BorderLight)
            }
        }

        // Main Preview Stage
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(SurfaceElevated)
        ) {
            if (!serverState.isRunning) {
                PreviewIdleState(
                    previewProjects = previewProjects,
                    onStartProject = onSelectProjectAndStart,
                    onBrowseFilesClick = onBrowseFilesClick
                )
            } else {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            horizontal = if (selectedViewport == PreviewViewport.MOBILE) 10.dp else 4.dp,
                            vertical = 8.dp
                        ),
                    contentAlignment = Alignment.TopCenter
                ) {
                    val frameMaxWidth = when (selectedViewport) {
                        PreviewViewport.MOBILE -> (maxWidth * selectedViewport.widthFraction).coerceAtMost(420.dp)
                        PreviewViewport.TABLET -> maxWidth
                        PreviewViewport.DESKTOP -> maxWidth
                    }

                    Surface(
                        color = SurfaceCard,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, BorderLight),
                        shadowElevation = 3.dp,
                        modifier = Modifier
                            .width(frameMaxWidth)
                            .fillMaxHeight()
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Subtle URL Bar inside preview frame
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SurfaceBackground)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Language,
                                    contentDescription = null,
                                    tint = AccentEmerald,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = targetPreviewUrl,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = selectedViewport.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BrandIndigo
                                )
                            }
                            HorizontalDivider(thickness = 1.dp, color = BorderLight)

                            // Embedded Live WebView
                            AndroidView(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .testTag("preview_webview"),
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                        settings.apply {
                                            javaScriptEnabled = true
                                            domStorageEnabled = true
                                            databaseEnabled = true
                                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                            cacheMode = WebSettings.LOAD_NO_CACHE
                                            useWideViewPort = true
                                            loadWithOverviewMode = true
                                            builtInZoomControls = true
                                            displayZoomControls = false
                                        }

                                        webChromeClient = object : WebChromeClient() {
                                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                                if (consoleMessage != null) {
                                                    val level = when (consoleMessage.messageLevel()) {
                                                        ConsoleMessage.MessageLevel.ERROR -> ConsoleLogLevel.ERROR
                                                        ConsoleMessage.MessageLevel.WARNING -> ConsoleLogLevel.WARN
                                                        ConsoleMessage.MessageLevel.TIP -> ConsoleLogLevel.INFO
                                                        else -> ConsoleLogLevel.LOG
                                                    }
                                                    latestOnConsoleLog(
                                                        level,
                                                        consoleMessage.message().orEmpty(),
                                                        consoleMessage.sourceId().orEmpty(),
                                                        consoleMessage.lineNumber()
                                                    )
                                                }
                                                return true
                                            }
                                        }

                                        webViewClient = object : WebViewClient() {
                                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                                isPageLoading = true
                                            }

                                            override fun onPageFinished(view: WebView?, url: String?) {
                                                isPageLoading = false
                                            }

                                            override fun onReceivedError(
                                                view: WebView?,
                                                request: WebResourceRequest?,
                                                error: WebResourceError?
                                            ) {
                                                isPageLoading = false
                                                val desc = error?.description?.toString() ?: "Network error"
                                                val reqUrl = request?.url?.toString().orEmpty()
                                                latestOnConsoleLog(
                                                    ConsoleLogLevel.ERROR,
                                                    "Page error: $desc",
                                                    reqUrl,
                                                    0
                                                )
                                            }

                                            override fun onReceivedHttpError(
                                                view: WebView?,
                                                request: WebResourceRequest?,
                                                errorResponse: WebResourceResponse?
                                            ) {
                                                val status = errorResponse?.statusCode ?: return
                                                if (status >= 400) {
                                                    val reqUrl = request?.url?.toString().orEmpty()
                                                    latestOnConsoleLog(
                                                        ConsoleLogLevel.ERROR,
                                                        "HTTP $status ${errorResponse.reasonPhrase.orEmpty()}",
                                                        reqUrl,
                                                        0
                                                    )
                                                }
                                            }
                                        }

                                        webViewRef = this
                                    }
                                },
                                update = { webView ->
                                    val viewportChanged = lastAppliedViewport != selectedViewport
                                    if (viewportChanged) {
                                        applyViewportSettings(webView, selectedViewport)
                                        lastAppliedViewport = selectedViewport
                                    }

                                    val urlChanged = lastLoadedUrl != targetPreviewUrl
                                    val nonceChanged = lastLoadedNonce != serverState.reloadNonce
                                    if (targetPreviewUrl.isNotBlank() && (urlChanged || nonceChanged || viewportChanged)) {
                                        webView.clearCache(true)
                                        webView.loadUrl(targetPreviewUrl)
                                        lastLoadedUrl = targetPreviewUrl
                                        lastLoadedNonce = serverState.reloadNonce
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Collapsible Live Console Drawer with Phase 3 1-Tap Auto-Fix with AI
        LiveConsoleDrawer(
            logs = consoleLogs,
            errorCount = errorCount,
            expanded = consoleExpanded,
            isAutoFixing = isAutoFixing,
            canAutoFix = serverState.isRunning && errorCount > 0,
            onToggleExpand = { consoleExpanded = !consoleExpanded },
            onClear = onClearConsoleLogs,
            onAutoFixClick = onAutoFixClick
        )
    }
}

private fun applyViewportSettings(webView: WebView, viewport: PreviewViewport) {
    val defaultUa = WebSettings.getDefaultUserAgent(webView.context)
    webView.settings.userAgentString = if (viewport.useDesktopUa) {
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    } else {
        defaultUa
    }
    webView.settings.useWideViewPort = true
    webView.settings.loadWithOverviewMode = true
    webView.setInitialScale(viewport.targetScalePercent)
}

@Composable
private fun LiveConsoleDrawer(
    logs: List<ConsoleLogEntry>,
    errorCount: Int,
    expanded: Boolean,
    isAutoFixing: Boolean,
    canAutoFix: Boolean,
    onToggleExpand: () -> Unit,
    onClear: () -> Unit,
    onAutoFixClick: () -> Unit
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    Surface(
        color = SurfaceCard,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(thickness = 1.dp, color = BorderLight)

            // Drawer Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .testTag("preview_console_bar"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Terminal,
                        contentDescription = null,
                        tint = if (errorCount > 0) AccentRose else BrandIndigo,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "${stringResource(R.string.preview_console_title)} ($errorCount ${if (errorCount == 1) "error" else "errors"})",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (errorCount > 0) AccentRose else TextPrimary
                    )
                    if (logs.isNotEmpty()) {
                        Surface(
                            color = SurfaceElevated,
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.dp, BorderLight)
                        ) {
                            Text(
                                text = "${logs.size} logs",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (canAutoFix || isAutoFixing) {
                        Surface(
                            color = BrandIndigo,
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable(enabled = !isAutoFixing, onClick = onAutoFixClick)
                                .testTag("preview_auto_fix_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                if (isAutoFixing) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        color = OnBrandLight,
                                        modifier = Modifier.size(13.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesome,
                                        contentDescription = stringResource(R.string.preview_auto_fix),
                                        tint = OnBrandLight,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = if (isAutoFixing) "Fixing..." else "Auto-Fix",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = OnBrandLight
                                )
                            }
                        }
                    }
                    if (expanded && logs.isNotEmpty()) {
                        TextButton(
                            onClick = onClear,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.testTag("preview_console_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = "Clear console",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Clear", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        }
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                        contentDescription = if (expanded) "Collapse console" else "Expand console",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(thickness = 1.dp, color = BorderLight)
                    if (canAutoFix || isAutoFixing) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BrandIndigoContainer)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isAutoFixing) {
                                    "✨ AI is analyzing errors and patching project files..."
                                } else {
                                    "Detected $errorCount console error${if (errorCount == 1) "" else "s"} in live preview"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = onAutoFixClick,
                                enabled = !isAutoFixing,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BrandIndigo,
                                    contentColor = OnBrandLight
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("preview_auto_fix_expanded_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (isAutoFixing) "Fixing..." else stringResource(R.string.preview_auto_fix),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        HorizontalDivider(thickness = 1.dp, color = BrandIndigoBorder)
                    }
                    if (logs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .background(SurfaceBackground)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No console messages yet. JavaScript console.log, warnings, and runtime errors appear here in real time.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                                .background(SurfaceBackground),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(items = logs, key = { it.id }) { entry ->
                                val (bgColor, accentColor) = when (entry.level) {
                                    ConsoleLogLevel.ERROR -> AccentRoseContainer to AccentRose
                                    ConsoleLogLevel.WARN -> AccentAmberContainer to AccentAmber
                                    ConsoleLogLevel.INFO -> BrandIndigoContainer to BrandIndigo
                                    ConsoleLogLevel.LOG -> SurfaceCard to TextSecondary
                                }
                                Surface(
                                    color = bgColor,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.22f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${entry.level.label} • ${entry.source}${if (entry.lineNumber > 0) ":${entry.lineNumber}" else ""}",
                                                fontFamily = JetBrainsMonoFontFamily,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = accentColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = timeFormatter.format(Date(entry.timestampMillis)),
                                                fontFamily = JetBrainsMonoFontFamily,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextMuted
                                            )
                                        }
                                        Text(
                                            text = entry.message,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewIdleState(
    previewProjects: List<PreviewProjectOption>,
    onStartProject: (PreviewProjectOption) -> Unit,
    onBrowseFilesClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = SurfaceCard,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, BorderLight),
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(BrandIndigoContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = BrandIndigo,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    text = if (previewProjects.isEmpty()) "No Web Projects Yet" else "Start Live Preview",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                Text(
                    text = if (previewProjects.isEmpty()) {
                        "Create a Web App starter project in the Files tab (with index.html) to run it on the embedded localhost server."
                    } else {
                        stringResource(R.string.preview_subtitle)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                if (previewProjects.isEmpty()) {
                    Button(
                        onClick = onBrowseFilesClick,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo, contentColor = OnBrandLight),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Files Tab", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        previewProjects.take(4).forEach { project ->
                            Surface(
                                color = SurfaceBackground,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, BorderLight),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onStartProject(project) }
                                    .testTag("preview_quick_start_${project.projectName}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = project.projectName,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = project.entryHtmlRelativePath,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted
                                        )
                                    }
                                    Surface(
                                        color = BrandIndigo,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.PlayArrow,
                                                contentDescription = null,
                                                tint = OnBrandLight,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "Run",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = OnBrandLight
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
