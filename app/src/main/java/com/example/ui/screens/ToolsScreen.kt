package com.example.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.GitCloneProgress
import com.example.domain.model.GitPushState
import com.example.domain.model.GitRepoInfo
import com.example.domain.model.IdeSettings
import com.example.domain.model.KeystoreInfo
import com.example.domain.model.MemoryItem
import com.example.domain.model.PreviewProjectOption
import com.example.domain.model.ServerState
import com.example.domain.model.ToolchainStatus
import com.example.domain.model.TtsPlaybackState
import com.example.ui.components.MissingPatPromptDialog
import com.example.ui.components.ToolsMemoryManagerCard
import com.example.ui.components.ToolsToolchainCard
import com.example.ui.components.ToolsVoiceSettingsCard
import com.example.ui.components.ideTextFieldColors
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentEmeraldBorder
import com.example.ui.theme.AccentEmeraldContainer
import com.example.ui.theme.AccentRose
import com.example.ui.theme.AccentRoseContainer
import com.example.ui.theme.BorderLight
import com.example.ui.theme.BrandIndigo
import com.example.ui.theme.BrandIndigoContainer
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.OnBrandLight
import com.example.ui.theme.SurfaceBackground
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun ToolsScreen(
    settings: IdeSettings,
    toolchainStatus: ToolchainStatus,
    keystoreInfo: KeystoreInfo,
    serverState: ServerState,
    previewProjects: List<PreviewProjectOption>,
    memories: List<MemoryItem>,
    ttsState: TtsPlaybackState,
    gitRepos: List<GitRepoInfo>,
    cloneProgress: GitCloneProgress,
    pushState: GitPushState,
    onImportAndroidJar: (Uri) -> Unit,
    onDownloadAndroidJar: (String) -> Unit,
    onDeleteAndroidJar: () -> Unit,
    onCreateCustomKeystore: (alias: String, commonName: String, organization: String, password: String) -> Unit,
    onResetToDebugKeystore: () -> Unit,
    onStartServer: (projectPath: String) -> Unit,
    onRestartServer: () -> Unit,
    onStopServer: () -> Unit,
    onOpenPreviewTab: () -> Unit,
    onAddMemory: (String) -> Unit,
    onDeleteMemory: (Long) -> Unit,
    onClearAllMemories: () -> Unit,
    onSetAutoSpeak: (Boolean) -> Unit,
    onSelectVoice: (String) -> Unit,
    onSelectTtsSpeed: (Float) -> Unit,
    onTestVoice: () -> Unit,
    onStopSpeaking: () -> Unit,
    onCloneRepo: (url: String, folderName: String) -> Unit,
    onRefreshRepos: () -> Unit,
    onCommitLocally: (repoPath: String, message: String) -> Unit,
    onCommitAndPush: (repoPath: String, message: String) -> Unit,
    onOpenClonedRepo: (relativePath: String) -> Unit,
    onToggleDefaultFreeAi: (Boolean) -> Unit,
    onSaveByokConfig: (baseUrl: String, apiKey: String, modelName: String) -> Unit,
    onResetAiConfig: () -> Unit,
    onSaveGithubPat: (String) -> Unit,
    onClearGithubPat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // Clone state
    var repoUrl by remember { mutableStateOf("") }
    var customFolderName by remember { mutableStateOf("") }

    // Commit & Push state
    var selectedRepoPath by remember(gitRepos) {
        mutableStateOf(gitRepos.firstOrNull()?.absolutePath.orEmpty())
    }
    var commitMessage by remember { mutableStateOf("") }
    var showMissingPatDialog by remember { mutableStateOf(false) }

    // AI BYOK Settings state
    var baseUrlInput by remember { mutableStateOf(settings.customBaseUrl) }
    var apiKeyInput by remember { mutableStateOf(settings.customApiKey) }
    var modelNameInput by remember { mutableStateOf(settings.customModelName) }
    var showApiKey by remember { mutableStateOf(false) }

    // GitHub PAT state
    var patInput by remember { mutableStateOf(settings.githubPat) }
    var showPat by remember { mutableStateOf(false) }

    LaunchedEffect(settings) {
        baseUrlInput = settings.customBaseUrl
        apiKeyInput = settings.customApiKey
        modelNameInput = settings.customModelName
        patInput = settings.githubPat
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceBackground)
            .statusBarsPadding()
            .imePadding()
    ) {
        // Crisp Light Header
        Surface(
            color = SurfaceCard,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.tools_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "Toolchain • Localhost • Memory • Voice • JGit • AI",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Surface(
                    color = if (settings.hasGithubPat) AccentEmeraldContainer else SurfaceElevated,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(
                        1.dp,
                        if (settings.hasGithubPat) AccentEmeraldBorder else BorderLight
                    )
                ) {
                    Text(
                        text = if (settings.hasGithubPat) "PAT Set" else "No Auth Mode",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (settings.hasGithubPat) AccentEmerald else TextSecondary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        HorizontalDivider(thickness = 1.dp, color = BorderLight)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 0. Phase 2 Running Localhost Servers Manager
            ToolsSectionCard(
                title = stringResource(R.string.server_manager_title),
                subtitle = "Embedded NanoHTTPD static server bound to 0.0.0.0.",
                icon = Icons.Filled.Dns,
                iconTint = if (serverState.isRunning) AccentEmerald else BrandIndigo,
                headerTrailing = {
                    Surface(
                        color = if (serverState.isRunning) AccentEmeraldContainer else SurfaceElevated,
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(
                            1.dp,
                            if (serverState.isRunning) AccentEmeraldBorder else BorderLight
                        )
                    ) {
                        Text(
                            text = if (serverState.isRunning) "ONLINE" else "IDLE",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (serverState.isRunning) AccentEmerald else TextMuted,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            ) {
                if (serverState.isRunning) {
                    Surface(
                        color = AccentEmeraldContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AccentEmeraldBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tools_active_server_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "/projects/${serverState.activeProjectName.orEmpty()}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${serverState.bindAddress}:${serverState.port}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AccentEmerald
                                )
                            }
                            Text(
                                text = "Local: ${serverState.localUrl}",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            serverState.lanUrl?.let { lan ->
                                Text(
                                    text = "LAN:   $lan",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onRestartServer,
                            border = BorderStroke(1.dp, BorderLight),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tools_server_refresh_button")
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, tint = BrandIndigo, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Refresh", color = TextPrimary, maxLines = 1)
                        }

                        Button(
                            onClick = onOpenPreviewTab,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandIndigo,
                                contentColor = OnBrandLight
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tools_server_open_preview_button")
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Preview", fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = onStopServer,
                            border = BorderStroke(1.dp, AccentRose.copy(alpha = 0.45f)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tools_server_stop_button")
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = null, tint = AccentRose, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Stop", color = AccentRose, maxLines = 1)
                        }
                    }
                } else {
                    Surface(
                        color = SurfaceBackground,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BorderLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (previewProjects.isEmpty()) {
                                "No localhost server running. Create a Web project in Files to preview it locally."
                            } else {
                                "No server running. Tap a project below to start serving on 0.0.0.0:8080:"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    if (previewProjects.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            previewProjects.forEach { project ->
                                Surface(
                                    color = BrandIndigoContainer,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, BrandIndigo.copy(alpha = 0.3f)),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onStartServer(project.projectPath) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.PlayArrow,
                                            contentDescription = null,
                                            tint = BrandIndigo,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = project.projectName,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = BrandIndigo
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Phase 6–8: Android SDK Toolchain & APK Signing Keystore Manager
            ToolsToolchainCard(
                status = toolchainStatus,
                keystoreInfo = keystoreInfo,
                onImportAndroidJar = onImportAndroidJar,
                onDownloadAndroidJar = onDownloadAndroidJar,
                onDeleteAndroidJar = onDeleteAndroidJar,
                onCreateCustomKeystore = onCreateCustomKeystore,
                onResetToDebugKeystore = onResetToDebugKeystore
            )

            // Phase 4: Persistent AI Memory Manager
            ToolsMemoryManagerCard(
                memories = memories,
                onAddMemory = onAddMemory,
                onDeleteMemory = onDeleteMemory,
                onClearAllMemories = onClearAllMemories
            )

            // Phase 4: Microsoft Edge-TTS Voice & Speed Presets
            ToolsVoiceSettingsCard(
                settings = settings,
                ttsState = ttsState,
                onSetAutoSpeak = onSetAutoSpeak,
                onSelectVoice = onSelectVoice,
                onSelectSpeed = onSelectTtsSpeed,
                onTestVoice = onTestVoice,
                onStopSpeaking = onStopSpeaking
            )

            // 1. Clone Public GitHub Repo (JGit)
            ToolsSectionCard(
                title = stringResource(R.string.git_clone_title),
                subtitle = "Clone any public repo directly into /projects/ with zero login.",
                icon = Icons.Filled.CloudDownload,
                iconTint = BrandIndigo
            ) {
                OutlinedTextField(
                    value = repoUrl,
                    onValueChange = { repoUrl = it },
                    label = { Text("GitHub URL or owner/repo") },
                    placeholder = { Text("https://github.com/octocat/Hello-World") },
                    singleLine = true,
                    colors = ideTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("git_clone_url_input")
                )

                OutlinedTextField(
                    value = customFolderName,
                    onValueChange = { customFolderName = it },
                    label = { Text("Custom folder name (optional)") },
                    placeholder = { Text("Auto-derived from repo URL") },
                    singleLine = true,
                    colors = ideTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("git_clone_folder_input")
                )

                Button(
                    onClick = { onCloneRepo(repoUrl, customFolderName) },
                    enabled = repoUrl.isNotBlank() && !cloneProgress.isRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandIndigo,
                        contentColor = OnBrandLight
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("git_clone_button")
                ) {
                    if (cloneProgress.isRunning) {
                        CircularProgressIndicator(
                            color = OnBrandLight,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cloning...", fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clone Repository", fontWeight = FontWeight.SemiBold)
                    }
                }

                // Live JGit Clone Progress
                AnimatedVisibility(visible = cloneProgress.isRunning || cloneProgress.statusMessage != null) {
                    Surface(
                        color = if (cloneProgress.isError) AccentRoseContainer else SurfaceBackground,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (cloneProgress.isError) AccentRose.copy(alpha = 0.4f) else BorderLight
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (cloneProgress.isRunning) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = cloneProgress.taskTitle.ifBlank { "Cloning..." },
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = BrandIndigo,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    cloneProgress.fraction?.let { frac ->
                                        Text(
                                            text = "${(frac * 100).toInt()}%",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextPrimary
                                        )
                                    }
                                }
                                val fraction = cloneProgress.fraction
                                if (fraction != null) {
                                    LinearProgressIndicator(
                                        progress = { fraction },
                                        color = BrandIndigo,
                                        trackColor = BrandIndigoContainer,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(4.dp))
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        color = BrandIndigo,
                                        trackColor = BrandIndigoContainer,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(4.dp))
                                    )
                                }
                            }

                            cloneProgress.statusMessage?.let { status ->
                                Text(
                                    text = status,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (cloneProgress.isError) AccentRose else AccentEmerald
                                )
                            }

                            val clonedPath = cloneProgress.lastClonedRelativePath
                            if (!cloneProgress.isRunning && !cloneProgress.isError && clonedPath != null) {
                                OutlinedButton(
                                    onClick = { onOpenClonedRepo(clonedPath) },
                                    border = BorderStroke(1.dp, AccentEmeraldBorder),
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = SurfaceCard),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open in Files", color = AccentEmerald)
                                }
                            }
                        }
                    }
                }
            }

            // 2. Commit & Push (JGit)
            ToolsSectionCard(
                title = stringResource(R.string.git_push_title),
                subtitle = "Commit locally anytime, or push to GitHub using an optional PAT.",
                icon = Icons.Filled.CloudUpload,
                iconTint = AccentEmerald,
                headerTrailing = {
                    IconButton(onClick = onRefreshRepos) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Rescan Git repos", tint = TextSecondary)
                    }
                }
            ) {
                if (gitRepos.isEmpty()) {
                    Surface(
                        color = SurfaceBackground,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BorderLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No cloned Git repositories found in /projects/ yet. Clone a repository above to enable Commit & Push.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    Text(
                        text = "Select Repository",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        gitRepos.forEach { repo ->
                            val isSelected = selectedRepoPath == repo.absolutePath
                            Surface(
                                color = if (isSelected) AccentEmeraldContainer else SurfaceBackground,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) AccentEmerald else BorderLight
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { selectedRepoPath = repo.absolutePath }
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text(
                                        text = repo.projectName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (isSelected) AccentEmerald else TextPrimary
                                    )
                                    Text(
                                        text = "${repo.branchName} • ${repo.changedFilesCount} changed",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (repo.changedFilesCount > 0) AccentAmber else TextMuted
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = commitMessage,
                        onValueChange = { commitMessage = it },
                        label = { Text("Commit message") },
                        placeholder = { Text("feat: update workspace files") },
                        singleLine = true,
                        colors = ideTextFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("git_commit_message_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (selectedRepoPath.isNotBlank()) {
                                    onCommitLocally(selectedRepoPath, commitMessage)
                                    commitMessage = ""
                                }
                            },
                            enabled = selectedRepoPath.isNotBlank() && commitMessage.isNotBlank() && !pushState.isRunning,
                            border = BorderStroke(1.dp, BorderLight),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("git_commit_local_button")
                        ) {
                            Text("Commit Local", color = TextPrimary, maxLines = 1)
                        }

                        Button(
                            onClick = {
                                if (!settings.hasGithubPat) {
                                    showMissingPatDialog = true
                                } else if (selectedRepoPath.isNotBlank()) {
                                    onCommitAndPush(selectedRepoPath, commitMessage)
                                    commitMessage = ""
                                }
                            },
                            enabled = selectedRepoPath.isNotBlank() && !pushState.isRunning,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentEmerald,
                                contentColor = OnBrandLight
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("git_push_button")
                        ) {
                            if (pushState.isRunning) {
                                CircularProgressIndicator(
                                    color = OnBrandLight,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Text("Commit & Push", fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }
                    }

                    pushState.statusMessage?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (pushState.isError) AccentRose else AccentEmerald
                        )
                    }
                }
            }

            // 3. AI Provider Config
            ToolsSectionCard(
                title = stringResource(R.string.settings_ai_title),
                subtitle = "Active for Chat, Builder, Pipeline & Auto-Fix. Free default needs zero setup.",
                icon = Icons.Filled.AutoAwesome,
                iconTint = BrandIndigo
            ) {
                Surface(
                    color = if (settings.useDefaultFreeAi) AccentEmeraldContainer else SurfaceBackground,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (settings.useDefaultFreeAi) AccentEmeraldBorder else BorderLight
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Use Default Free AI (Pollinations.ai)",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Text(
                                text = "ON by default • No API key required",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (settings.useDefaultFreeAi) AccentEmerald else TextSecondary
                            )
                        }
                        Switch(
                            checked = settings.useDefaultFreeAi,
                            onCheckedChange = onToggleDefaultFreeAi,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OnBrandLight,
                                checkedTrackColor = AccentEmerald,
                                uncheckedThumbColor = SurfaceCard,
                                uncheckedTrackColor = BorderLight
                            ),
                            modifier = Modifier.testTag("settings_pollinations_switch")
                        )
                    }
                }

                Text(
                    text = "Optional BYOK (Bring Your Own Key — Skippable)",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )

                OutlinedTextField(
                    value = baseUrlInput,
                    onValueChange = { baseUrlInput = it },
                    label = { Text("Custom Base URL (optional)") },
                    placeholder = { Text("https://api.openai.com/v1") },
                    singleLine = true,
                    colors = ideTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_byok_url_input")
                )

                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("API Key (optional • Keystore encrypted)") },
                    placeholder = { Text("sk-...") },
                    singleLine = true,
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                imageVector = if (showApiKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showApiKey) "Hide API key" else "Show API key",
                                tint = TextSecondary
                            )
                        }
                    },
                    colors = ideTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_byok_key_input")
                )

                OutlinedTextField(
                    value = modelNameInput,
                    onValueChange = { modelNameInput = it },
                    label = { Text("Model Name (optional)") },
                    placeholder = { Text("gpt-4o-mini / claude-3-5-sonnet") },
                    singleLine = true,
                    colors = ideTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_byok_model_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            baseUrlInput = ""
                            apiKeyInput = ""
                            modelNameInput = ""
                            onResetAiConfig()
                        },
                        border = BorderStroke(1.dp, BorderLight),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset Default", color = TextSecondary)
                    }
                    Button(
                        onClick = { onSaveByokConfig(baseUrlInput, apiKeyInput, modelNameInput) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandIndigo,
                            contentColor = OnBrandLight
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_byok_save_button")
                    ) {
                        Text("Save AI Config", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // 4. Optional GitHub Personal Access Token (PAT)
            ToolsSectionCard(
                title = stringResource(R.string.settings_github_title),
                subtitle = "Optional. Only needed for pushing commits or cloning private repos.",
                icon = Icons.Filled.Key,
                iconTint = AccentAmber
            ) {
                OutlinedTextField(
                    value = patInput,
                    onValueChange = { patInput = it },
                    label = { Text("GitHub Personal Access Token (PAT)") },
                    placeholder = { Text("ghp_xxxxxxxxxxxx (skippable)") },
                    singleLine = true,
                    visualTransformation = if (showPat) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPat = !showPat }) {
                            Icon(
                                imageVector = if (showPat) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showPat) "Hide token" else "Show token",
                                tint = TextSecondary
                            )
                        }
                    },
                    colors = ideTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_github_pat_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            patInput = ""
                            onClearGithubPat()
                        },
                        enabled = settings.hasGithubPat || patInput.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_github_pat_clear_button")
                    ) {
                        Text("Clear Token", color = AccentRose)
                    }

                    Button(
                        onClick = { onSaveGithubPat(patInput) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandIndigo,
                            contentColor = OnBrandLight
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_github_pat_save_button")
                    ) {
                        Text("Save PAT", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (showMissingPatDialog) {
        MissingPatPromptDialog(
            onDismiss = { showMissingPatDialog = false },
            onAddPatInSettings = {
                showMissingPatDialog = false
                coroutineScope.launch {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
            },
            onSkipAndCommitLocally = {
                showMissingPatDialog = false
                if (selectedRepoPath.isNotBlank()) {
                    val msg = commitMessage.ifBlank { "chore: local checkpoint from Nova IDE" }
                    onCommitLocally(selectedRepoPath, msg)
                    commitMessage = ""
                }
            }
        )
    }
}

@Composable
private fun ToolsSectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    headerTrailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Surface(
        color = SurfaceCard,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, BorderLight),
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(21.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                headerTrailing?.invoke()
            }
            HorizontalDivider(thickness = 1.dp, color = BorderLight)
            content()
        }
    }
}
