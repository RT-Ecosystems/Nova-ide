package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.core.voice.SpeechInputHelper
import com.example.domain.model.AiWorkStatus
import com.example.domain.model.ChatMessage
import com.example.domain.model.IdeSettings
import com.example.domain.model.MessageFeedback
import com.example.domain.model.TtsPlaybackState
import com.example.domain.model.WorkspaceItem
import com.example.ui.components.AiWorkingStatusPill
import com.example.ui.components.AttachFilesBottomSheet
import com.example.ui.components.ChatMessageBubble
import com.example.ui.components.ModelSelectorBottomSheet
import com.example.ui.components.VoiceSettingsBottomSheet
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
import com.example.ui.theme.TextPlaceholder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.ChatMode

@Composable
fun ChatScreen(
    selectedMode: ChatMode,
    settings: IdeSettings,
    messages: List<ChatMessage>,
    aiWorkStatus: AiWorkStatus,
    ttsState: TtsPlaybackState,
    webSearchEnabled: Boolean,
    attachedFiles: List<WorkspaceItem>,
    attachableFiles: List<WorkspaceItem>,
    rootProjectNames: List<String>,
    activeAiProjectName: String?,
    onSelectMode: (ChatMode) -> Unit,
    onToggleWebSearch: () -> Unit,
    onToggleAutoSpeak: () -> Unit,
    onSetAutoSpeak: (Boolean) -> Unit,
    onSelectVoice: (String) -> Unit,
    onSelectTtsSpeed: (Float) -> Unit,
    onTestVoice: () -> Unit,
    onStopSpeaking: () -> Unit,
    onSelectActiveAiProject: (String) -> Unit,
    onRefreshAttachableFiles: () -> Unit,
    onToggleAttachFile: (WorkspaceItem) -> Unit,
    onRemoveAttachedFile: (absolutePath: String) -> Unit,
    onClearChat: () -> Unit,
    onSubmitPrompt: (String) -> Unit,
    onKeepChangeSet: (messageId: String) -> Unit,
    onRevertChangeSet: (messageId: String) -> Unit,
    onOpenFileInEditor: (absolutePath: String) -> Unit,
    onRunPreview: (projectAbsolutePath: String) -> Unit,
    onToggleSpeakMessage: (ChatMessage) -> Unit,
    onRegenerateMessage: (messageId: String) -> Unit,
    onFeedbackClick: (messageId: String, feedback: MessageFeedback) -> Unit,
    onSaveToMemory: (String) -> Unit,
    onDeleteMessage: (messageId: String) -> Unit,
    onShowToast: (String, Boolean) -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenWorkspace: () -> Unit,
    onSelectPollinationsDefault: () -> Unit,
    onSaveAndActivateByok: (baseUrl: String, apiKey: String, modelName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var promptText by remember { mutableStateOf("") }
    var voiceBasePrefix by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }

    var showModelSheet by remember { mutableStateOf(false) }
    var showAttachSheet by remember { mutableStateOf(false) }
    var showVoiceSheet by remember { mutableStateOf(false) }
    var projectMenuExpanded by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    val speechHelper = remember(context) {
        SpeechInputHelper(
            context = context,
            onListeningChanged = { active -> isListening = active },
            onPartialText = { partial ->
                promptText = if (voiceBasePrefix.isBlank()) partial else "$voiceBasePrefix $partial"
            },
            onFinalText = { spoken ->
                promptText = if (voiceBasePrefix.isBlank()) spoken else "$voiceBasePrefix $spoken"
                voiceBasePrefix = promptText
            },
            onErrorMessage = { msg ->
                onShowToast(msg, true)
            }
        )
    }

    DisposableEffect(speechHelper) {
        onDispose {
            speechHelper.destroy()
        }
    }

    val startVoiceInput: () -> Unit = {
        onStopSpeaking()
        voiceBasePrefix = promptText.trim()
        speechHelper.startListening(settings.activeVoicePreset.localeTag)
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceInput()
        } else {
            onShowToast("Microphone permission skipped. You can type or enable it anytime.", false)
        }
    }

    val onMicTap: () -> Unit = {
        if (isListening) {
            speechHelper.stopListening()
        } else {
            val hasMicPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasMicPermission) {
                startVoiceInput()
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    LaunchedEffect(messages.size, aiWorkStatus.isWorking) {
        if (messages.isNotEmpty()) {
            val targetIndex = if (aiWorkStatus.isWorking) messages.size else messages.lastIndex
            runCatching { listState.animateScrollToItem(targetIndex.coerceAtLeast(0)) }
        }
    }

    val modelPillLabel = remember(settings.useDefaultFreeAi, settings.customModelName) {
        if (settings.useDefaultFreeAi) {
            "✦ Pollinations"
        } else if (settings.customModelName.isNotBlank()) {
            "⚡ ${settings.customModelName}"
        } else {
            "⚡ BYOK"
        }
    }

    val selectedAttachedPaths = remember(attachedFiles) {
        attachedFiles.map { it.absolutePath }.toSet()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SurfaceCard,
                        SurfaceBackground,
                        Color(0xFFF1F5F9)
                    )
                )
            )
            .statusBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar: 3-Line Drawer Menu + Target Project Selector + Auto-Speak + Voice Presets + Clear + Provider Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 3-Line Side Navigation Drawer Hamburger Button
                Surface(
                    color = SurfaceCard,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, BorderLight),
                    shadowElevation = 1.dp,
                    modifier = Modifier.size(38.dp)
                ) {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier.testTag("chat_open_drawer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = "Open navigation drawer",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Active Target Project Dropdown
                Box(modifier = Modifier.weight(1f)) {
                    Surface(
                        color = SurfaceCard,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BorderLight),
                        shadowElevation = 1.dp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                if (rootProjectNames.isEmpty()) {
                                    onOpenWorkspace()
                                } else {
                                    projectMenuExpanded = true
                                }
                            }
                            .testTag("chat_project_selector")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FolderOpen,
                                contentDescription = "Target project",
                                tint = BrandIndigo,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = activeAiProjectName ?: "Workspace",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = projectMenuExpanded,
                        onDismissRequest = { projectMenuExpanded = false },
                        containerColor = SurfaceCard
                    ) {
                        rootProjectNames.forEach { name ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (name == activeAiProjectName) BrandIndigo else TextPrimary,
                                        fontWeight = if (name == activeAiProjectName) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    projectMenuExpanded = false
                                    onSelectActiveAiProject(name)
                                }
                            )
                        }
                        HorizontalDivider(thickness = 1.dp, color = BorderLight)
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Open Files Manager...",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = BrandIndigo
                                )
                            },
                            onClick = {
                                projectMenuExpanded = false
                                onOpenWorkspace()
                            }
                        )
                    }
                }

                // Auto-Speak (🔊) Toggle Icon in Top Bar
                Surface(
                    color = if (settings.autoSpeakEnabled) AccentEmeraldContainer else SurfaceCard,
                    shape = CircleShape,
                    border = BorderStroke(
                        1.dp,
                        if (settings.autoSpeakEnabled) AccentEmeraldBorder else BorderLight
                    ),
                    modifier = Modifier.size(36.dp)
                ) {
                    IconButton(
                        onClick = onToggleAutoSpeak,
                        modifier = Modifier.testTag("chat_auto_speak_toggle")
                    ) {
                        Icon(
                            imageVector = if (settings.autoSpeakEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                            contentDescription = if (settings.autoSpeakEnabled) "Disable Auto-Speak" else "Enable Auto-Speak",
                            tint = if (settings.autoSpeakEnabled) AccentEmerald else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Quick Voice & Speed Presets Button
                Surface(
                    color = if (ttsState.isSpeaking) BrandIndigoContainer else SurfaceCard,
                    shape = CircleShape,
                    border = BorderStroke(
                        1.dp,
                        if (ttsState.isSpeaking) BrandIndigoBorder else BorderLight
                    ),
                    modifier = Modifier.size(36.dp)
                ) {
                    IconButton(
                        onClick = { showVoiceSheet = true },
                        modifier = Modifier.testTag("chat_voice_presets_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = "Voice and speed settings",
                            tint = if (ttsState.isSpeaking) BrandIndigo else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // New Chat / Clear Session Button
                if (messages.isNotEmpty()) {
                    Surface(
                        color = SurfaceCard,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, BorderLight),
                        modifier = Modifier.size(36.dp)
                    ) {
                        IconButton(
                            onClick = onClearChat,
                            enabled = !aiWorkStatus.isWorking,
                            modifier = Modifier.testTag("chat_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = stringResource(R.string.chat_new_session),
                                tint = if (aiWorkStatus.isWorking) TextPlaceholder else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Top-Right Provider Pill — opens Model & BYOK ModalBottomSheet
                Surface(
                    color = if (settings.useDefaultFreeAi) AccentEmeraldContainer else BrandIndigoContainer,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(
                        1.dp,
                        if (settings.useDefaultFreeAi) AccentEmeraldBorder else BrandIndigoBorder
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { showModelSheet = true }
                        .testTag("chat_provider_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = "Select AI Model",
                            tint = if (settings.useDefaultFreeAi) AccentEmerald else BrandIndigo,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (settings.useDefaultFreeAi) "Free" else "BYOK",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (settings.useDefaultFreeAi) AccentEmerald else BrandIndigo,
                            maxLines = 1
                        )
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = if (settings.useDefaultFreeAi) AccentEmerald else BrandIndigo,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Main Body: Hero Welcome State OR Active Scrollable Chat Thread
            if (messages.isEmpty()) {
                ChatEmptyHeroBody(
                    selectedMode = selectedMode,
                    onSelectMode = onSelectMode,
                    onSuggestionClick = { suggestion ->
                        promptText = suggestion
                    },
                    modifier = Modifier.weight(1f)
                )
            } else {
                ChatModePillBar(
                    selectedMode = selectedMode,
                    onSelectMode = onSelectMode,
                    compact = true,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("chat_messages_list"),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items = messages, key = { it.id }) { msg ->
                        ChatMessageBubble(
                            message = msg,
                            ttsState = ttsState,
                            isAiWorking = aiWorkStatus.isWorking,
                            onKeepChanges = onKeepChangeSet,
                            onRevertChanges = onRevertChangeSet,
                            onOpenFileInEditor = onOpenFileInEditor,
                            onRunPreview = onRunPreview,
                            onToggleSpeak = onToggleSpeakMessage,
                            onCopyMessage = { toast -> onShowToast(toast, false) },
                            onRegenerateMessage = onRegenerateMessage,
                            onFeedbackClick = onFeedbackClick,
                            onSaveToMemory = onSaveToMemory,
                            onDeleteMessage = onDeleteMessage
                        )
                    }

                    if (aiWorkStatus.isWorking) {
                        item(key = "ai_working_pill") {
                            AiWorkingStatusPill(status = aiWorkStatus)
                        }
                    }
                }
            }

            // Expanded Modern Gemini/Claude-Style Chat Composer Card
            Surface(
                color = SurfaceCard,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(
                    width = if (isListening) 1.5.dp else 1.dp,
                    color = if (isListening) AccentRose else BorderLight
                ),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .testTag("chat_composer_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Live Pulsing Listening Indicator while SpeechRecognizer is active
                    AnimatedVisibility(visible = isListening) {
                        ListeningPulseBanner(onStopListening = { speechHelper.stopListening() })
                    }

                    // Attached File Context Chips Strip
                    if (attachedFiles.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            attachedFiles.forEach { fileItem ->
                                Surface(
                                    color = BrandIndigoContainer,
                                    shape = RoundedCornerShape(999.dp),
                                    border = BorderStroke(1.dp, BrandIndigoBorder),
                                    modifier = Modifier.testTag("chat_attached_chip_${fileItem.name}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 9.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.AttachFile,
                                            contentDescription = null,
                                            tint = BrandIndigo,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = fileItem.name,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = BrandIndigo,
                                            maxLines = 1
                                        )
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Remove ${fileItem.name}",
                                            tint = BrandIndigo,
                                            modifier = Modifier
                                                .size(15.dp)
                                                .clip(CircleShape)
                                                .clickable { onRemoveAttachedFile(fileItem.absolutePath) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Top Row: Multi-line expandable input
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp, max = 126.dp)
                            .padding(end = 4.dp),
                        contentAlignment = Alignment.TopStart
                    ) {
                        if (promptText.isEmpty()) {
                            Text(
                                text = when (selectedMode) {
                                    ChatMode.CHAT -> stringResource(R.string.chat_input_placeholder)
                                    ChatMode.BUILDER -> "Tell Builder what app or files to create..."
                                    ChatMode.PIPELINE -> "Describe an app to plan & build end-to-end..."
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPlaceholder
                            )
                        }
                        BasicTextField(
                            value = promptText,
                            onValueChange = { promptText = it },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
                            cursorBrush = SolidColor(BrandIndigo),
                            minLines = 2,
                            maxLines = 5,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("chat_prompt_input")
                        )
                    }

                    // Bottom Action Bar inside Composer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left Group: '+' Attach Files + '🌐 Search' Toggle Pill + Model Selector Pill
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = if (attachedFiles.isNotEmpty()) BrandIndigoContainer else SurfaceElevated,
                                shape = CircleShape,
                                border = BorderStroke(
                                    1.dp,
                                    if (attachedFiles.isNotEmpty()) BrandIndigoBorder else BorderLight
                                ),
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        onRefreshAttachableFiles()
                                        showAttachSheet = true
                                    }
                                    .testTag("chat_attach_plus_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = "Attach project files",
                                        tint = if (attachedFiles.isNotEmpty()) BrandIndigo else TextPrimary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }

                            Surface(
                                color = if (webSearchEnabled) BrandIndigoContainer else SurfaceBackground,
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (webSearchEnabled) BrandIndigo else BorderLight
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable(onClick = onToggleWebSearch)
                                    .testTag("chat_web_search_toggle")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Language,
                                        contentDescription = "Toggle live web search",
                                        tint = if (webSearchEnabled) BrandIndigo else TextSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.chat_search_toggle),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (webSearchEnabled) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (webSearchEnabled) BrandIndigo else TextSecondary,
                                        maxLines = 1
                                    )
                                }
                            }

                            Surface(
                                color = if (settings.useDefaultFreeAi) SurfaceBackground else BrandIndigoContainer,
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (settings.useDefaultFreeAi) BorderLight else BrandIndigoBorder
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable { showModelSheet = true }
                                    .testTag("chat_model_selector_pill")
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 9.dp, end = 5.dp, top = 7.dp, bottom = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = modelPillLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = if (settings.useDefaultFreeAi) TextPrimary else BrandIndigo,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.KeyboardArrowDown,
                                        contentDescription = "Change model",
                                        tint = if (settings.useDefaultFreeAi) TextSecondary else BrandIndigo,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Right Group: Mic Voice Input Button + Circular Send Button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = if (isListening) AccentRose else SurfaceElevated,
                                shape = CircleShape,
                                border = if (isListening) null else BorderStroke(1.dp, BorderLight),
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .clickable(onClick = onMicTap)
                                    .testTag("chat_mic_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                                        contentDescription = if (isListening) "Stop voice input" else "Start voice input",
                                        tint = if (isListening) OnBrandLight else TextPrimary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }

                            val canSend = promptText.isNotBlank() && !aiWorkStatus.isWorking
                            Surface(
                                color = if (canSend || aiWorkStatus.isWorking) BrandIndigo else SurfaceElevated,
                                shape = CircleShape,
                                border = if (canSend || aiWorkStatus.isWorking) null else BorderStroke(1.dp, BorderLight),
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .clickable(enabled = canSend) {
                                        if (isListening) speechHelper.stopListening()
                                        onSubmitPrompt(promptText)
                                        promptText = ""
                                        voiceBasePrefix = ""
                                    }
                                    .testTag("chat_send_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (aiWorkStatus.isWorking) {
                                        CircularProgressIndicator(
                                            strokeWidth = 2.dp,
                                            color = OnBrandLight,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Send prompt",
                                            tint = if (canSend) OnBrandLight else TextPlaceholder,
                                            modifier = Modifier.size(18.dp)
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

    if (showModelSheet) {
        ModelSelectorBottomSheet(
            settings = settings,
            onDismiss = { showModelSheet = false },
            onSelectPollinationsDefault = onSelectPollinationsDefault,
            onSaveAndActivateByok = onSaveAndActivateByok
        )
    }

    if (showAttachSheet) {
        AttachFilesBottomSheet(
            files = attachableFiles,
            selectedAbsolutePaths = selectedAttachedPaths,
            activeProjectName = activeAiProjectName,
            onToggleFile = onToggleAttachFile,
            onDismiss = { showAttachSheet = false },
            onBrowseWorkspace = onOpenWorkspace
        )
    }

    if (showVoiceSheet) {
        VoiceSettingsBottomSheet(
            settings = settings,
            ttsState = ttsState,
            onToggleAutoSpeak = onSetAutoSpeak,
            onSelectVoice = onSelectVoice,
            onSelectSpeed = onSelectTtsSpeed,
            onTestVoice = onTestVoice,
            onStopSpeaking = onStopSpeaking,
            onDismiss = { showVoiceSheet = false }
        )
    }
}

@Composable
private fun ListeningPulseBanner(
    onStopListening: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_dot_alpha"
    )

    Surface(
        color = AccentRoseContainer,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AccentRose.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chat_listening_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .alpha(pulseAlpha)
                        .clip(CircleShape)
                        .background(AccentRose)
                )
                Text(
                    text = "Listening (Hindi / English)... Speak now",
                    style = MaterialTheme.typography.labelMedium,
                    color = AccentRose,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = "Stop",
                style = MaterialTheme.typography.labelMedium,
                color = AccentRose,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onStopListening)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun ChatEmptyHeroBody(
    selectedMode: ChatMode,
    onSelectMode: (ChatMode) -> Unit,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val suggestions = remember(selectedMode) {
        when (selectedMode) {
            ChatMode.CHAT -> listOf(
                "Explain CSS Grid vs Flexbox with clean examples",
                "Remember my name is Alex and always explain step by step",
                "What are the latest JavaScript ES2025 features?"
            )
            ChatMode.BUILDER -> listOf(
                "Build a sleek Pomodoro timer web app with sound & dark/light toggle",
                "Create a modern coffee shop landing page with hero art & menu cards",
                "Build an interactive expense tracker web app with charts"
            )
            ChatMode.PIPELINE -> listOf(
                "Plan and build a personal portfolio site with projects gallery & contact form",
                "Plan and build a weather & habit dashboard with localStorage",
                "Plan and build a flashcard study app with flip animations"
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        NovaSparkEmblemLight()

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.chat_title),
            style = MaterialTheme.typography.headlineLarge,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = stringResource(R.string.chat_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(18.dp))

        ChatModePillBar(
            selectedMode = selectedMode,
            onSelectMode = onSelectMode,
            compact = false
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = selectedMode.tagline,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(18.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suggestions.forEachIndexed { idx, prompt ->
                Surface(
                    color = SurfaceCard,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, BorderLight),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSuggestionClick(prompt) }
                        .testTag("chat_suggestion_$idx")
                ) {
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatModePillBar(
    selectedMode: ChatMode,
    onSelectMode: (ChatMode) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SurfaceCard,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, BorderLight),
        shadowElevation = if (compact) 0.dp else 2.dp,
        modifier = modifier.testTag("chat_mode_selector")
    ) {
        Row(
            modifier = Modifier.padding(if (compact) 3.dp else 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChatMode.entries.forEach { mode ->
                val isSelected = selectedMode == mode
                val pillBg by animateColorAsState(
                    targetValue = if (isSelected) BrandIndigo else Color.Transparent,
                    animationSpec = tween(180),
                    label = "mode_pill_bg"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) OnBrandLight else TextSecondary,
                    animationSpec = tween(180),
                    label = "mode_pill_text"
                )

                Surface(
                    color = pillBg,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { onSelectMode(mode) }
                        .testTag("chat_mode_pill_${mode.name.lowercase()}")
                ) {
                    Text(
                        text = mode.label,
                        style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                        color = textColor,
                        modifier = Modifier.padding(
                            horizontal = if (compact) 14.dp else 18.dp,
                            vertical = if (compact) 6.dp else 9.dp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun NovaSparkEmblemLight() {
    Box(
        modifier = Modifier
            .size(100.dp)
            .testTag("chat_welcome_brand_logo"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        BrandIndigo.copy(alpha = 0.16f),
                        AccentViolet.copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = size.minDimension * 0.54f
                )
            )
        }

        Surface(
            color = Color.White,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, BorderLight),
            shadowElevation = 6.dp,
            modifier = Modifier.size(78.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_nova_brand_mark),
                    contentDescription = "Nova IDE logo",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
