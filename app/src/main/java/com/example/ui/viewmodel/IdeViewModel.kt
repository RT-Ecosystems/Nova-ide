package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.ai.AiChatTurn
import com.example.core.ai.CodingAgent
import com.example.core.builder.WebToApkBuilder
import com.example.core.chat.ChatHistoryRepository
import com.example.core.compiler.ApkPackagerEngine
import com.example.core.compiler.ApkSignerEngine
import com.example.core.compiler.DexCompilerEngine
import com.example.core.compiler.JavaCompilerEngine
import com.example.core.compiler.KeystoreManager
import com.example.core.compiler.ToolchainManager
import com.example.core.editor.CodeLanguageResolver
import com.example.core.files.WorkspaceFileManager
import com.example.core.git.JGitManager
import com.example.core.memory.MemoryRepository
import com.example.core.server.ServerManager
import com.example.core.voice.EdgeTtsEngine
import com.example.data.SettingsRepository
import com.example.domain.model.AiWorkStatus
import com.example.domain.model.BreadcrumbSegment
import com.example.domain.model.BuildStageStatus
import com.example.domain.model.ChangeReviewStatus
import com.example.domain.model.ChatMessage
import com.example.domain.model.ChatRole
import com.example.domain.model.ChatSessionSummary
import com.example.domain.model.ConsoleLogEntry
import com.example.domain.model.ConsoleLogLevel
import com.example.domain.model.EdgeVoicePreset
import com.example.domain.model.EditorTabState
import com.example.domain.model.ExportedArtifact
import com.example.domain.model.GitCloneProgress
import com.example.domain.model.GitOperationResult
import com.example.domain.model.GitPushState
import com.example.domain.model.GitRepoInfo
import com.example.domain.model.IdeSettings
import com.example.domain.model.JavaCompileState
import com.example.domain.model.JavaProjectOption
import com.example.domain.model.KeystoreInfo
import com.example.domain.model.MemoryItem
import com.example.domain.model.MessageFeedback
import com.example.domain.model.NativeBuildArtifact
import com.example.domain.model.PreviewProjectOption
import com.example.domain.model.ProjectTemplate
import com.example.domain.model.ServerState
import com.example.domain.model.ToolchainStatus
import com.example.domain.model.TtsPlaybackState
import com.example.domain.model.WebToApkBuildState
import com.example.domain.model.WebToApkConfig
import com.example.domain.model.WorkspaceItem
import com.example.ui.navigation.IdeDestination
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

enum class ChatMode(val label: String, val tagline: String) {
    CHAT("Chat", "Conversational Q&A, debugging & live web search"),
    BUILDER("Builder", "Create & edit real files directly inside /projects/"),
    PIPELINE("Pipeline", "Plan architecture first, then build files automatically")
}

data class FilesUiState(
    val currentRelativePath: String = "",
    val breadcrumbs: List<BreadcrumbSegment> = listOf(BreadcrumbSegment("projects", "")),
    val items: List<WorkspaceItem> = emptyList(),
    val isLoading: Boolean = false,
    val htmlEntryPoints: List<WorkspaceItem> = emptyList(),
    val rootProjectCount: Int = 0
)

sealed class IdeUiEvent {
    data class ShowMessage(val message: String, val isError: Boolean = false) : IdeUiEvent()
    data class NavigateToTab(val route: String) : IdeUiEvent()
}

class IdeViewModel(
    private val fileManager: WorkspaceFileManager,
    private val gitManager: JGitManager,
    private val settingsRepository: SettingsRepository,
    private val serverManager: ServerManager,
    private val codingAgent: CodingAgent,
    private val memoryRepository: MemoryRepository,
    private val edgeTtsEngine: EdgeTtsEngine,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val webToApkBuilder: WebToApkBuilder,
    private val toolchainManager: ToolchainManager,
    private val javaCompilerEngine: JavaCompilerEngine,
    private val dexCompilerEngine: DexCompilerEngine,
    private val apkPackagerEngine: ApkPackagerEngine,
    private val keystoreManager: KeystoreManager,
    private val apkSignerEngine: ApkSignerEngine
) : ViewModel() {

    private val consoleLogIdGenerator = AtomicLong(1L)

    val settings: StateFlow<IdeSettings> = settingsRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = IdeSettings()
        )

    val serverState: StateFlow<ServerState> = serverManager.serverState
    val memories: StateFlow<List<MemoryItem>> = memoryRepository.memories
    val ttsState: StateFlow<TtsPlaybackState> = edgeTtsEngine.playbackState
    val chatSessions: StateFlow<List<ChatSessionSummary>> = chatHistoryRepository.sessions
    val toolchainStatus: StateFlow<ToolchainStatus> = toolchainManager.status
    val keystoreInfo: StateFlow<KeystoreInfo> = keystoreManager.keystoreInfo

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private val _selectedChatMode = MutableStateFlow(ChatMode.CHAT)
    val selectedChatMode: StateFlow<ChatMode> = _selectedChatMode.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _aiWorkStatus = MutableStateFlow(AiWorkStatus())
    val aiWorkStatus: StateFlow<AiWorkStatus> = _aiWorkStatus.asStateFlow()

    private val _webSearchEnabled = MutableStateFlow(false)
    val webSearchEnabled: StateFlow<Boolean> = _webSearchEnabled.asStateFlow()

    private val _attachedContextFiles = MutableStateFlow<List<WorkspaceItem>>(emptyList())
    val attachedContextFiles: StateFlow<List<WorkspaceItem>> = _attachedContextFiles.asStateFlow()

    private val _attachableWorkspaceFiles = MutableStateFlow<List<WorkspaceItem>>(emptyList())
    val attachableWorkspaceFiles: StateFlow<List<WorkspaceItem>> = _attachableWorkspaceFiles.asStateFlow()

    private val _rootProjectNames = MutableStateFlow<List<String>>(emptyList())
    val rootProjectNames: StateFlow<List<String>> = _rootProjectNames.asStateFlow()

    private val _rootProjectFolders = MutableStateFlow<List<WorkspaceItem>>(emptyList())
    val rootProjectFolders: StateFlow<List<WorkspaceItem>> = _rootProjectFolders.asStateFlow()

    private val _activeAiProjectName = MutableStateFlow<String?>(null)
    val activeAiProjectName: StateFlow<String?> = _activeAiProjectName.asStateFlow()

    private val _isAutoFixing = MutableStateFlow(false)
    val isAutoFixing: StateFlow<Boolean> = _isAutoFixing.asStateFlow()

    private val _webToApkBuildState = MutableStateFlow(WebToApkBuildState())
    val webToApkBuildState: StateFlow<WebToApkBuildState> = _webToApkBuildState.asStateFlow()

    private val _recentExports = MutableStateFlow<List<ExportedArtifact>>(emptyList())
    val recentExports: StateFlow<List<ExportedArtifact>> = _recentExports.asStateFlow()

    private val _javaProjects = MutableStateFlow<List<JavaProjectOption>>(emptyList())
    val javaProjects: StateFlow<List<JavaProjectOption>> = _javaProjects.asStateFlow()

    private val _javaCompileState = MutableStateFlow(JavaCompileState())
    val javaCompileState: StateFlow<JavaCompileState> = _javaCompileState.asStateFlow()

    private val _filesState = MutableStateFlow(FilesUiState())
    val filesState: StateFlow<FilesUiState> = _filesState.asStateFlow()

    private val _previewProjects = MutableStateFlow<List<PreviewProjectOption>>(emptyList())
    val previewProjects: StateFlow<List<PreviewProjectOption>> = _previewProjects.asStateFlow()

    private val _consoleLogs = MutableStateFlow<List<ConsoleLogEntry>>(emptyList())
    val consoleLogs: StateFlow<List<ConsoleLogEntry>> = _consoleLogs.asStateFlow()

    private val _editorState = MutableStateFlow(EditorTabState())
    val editorState: StateFlow<EditorTabState> = _editorState.asStateFlow()

    private val _gitRepos = MutableStateFlow<List<GitRepoInfo>>(emptyList())
    val gitRepos: StateFlow<List<GitRepoInfo>> = _gitRepos.asStateFlow()

    private val _cloneProgress = MutableStateFlow(GitCloneProgress())
    val cloneProgress: StateFlow<GitCloneProgress> = _cloneProgress.asStateFlow()

    private val _pushState = MutableStateFlow(GitPushState())
    val pushState: StateFlow<GitPushState> = _pushState.asStateFlow()

    private val _events = MutableSharedFlow<IdeUiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<IdeUiEvent> = _events.asSharedFlow()

    init {
        refreshWorkspace()
        refreshGitRepos()
        refreshMemories()
        refreshRecentExports()
        viewModelScope.launch {
            val savedSessions = chatHistoryRepository.refreshSessions()
            if (savedSessions.isNotEmpty() && _activeSessionId.value == null && _chatMessages.value.isEmpty()) {
                val latest = savedSessions.first()
                _activeSessionId.value = latest.id
                _selectedChatMode.value = latest.mode
                if (!latest.projectName.isNullOrBlank()) {
                    _activeAiProjectName.value = latest.projectName
                }
                _chatMessages.value = chatHistoryRepository.loadMessages(latest.id)
            }
        }
        viewModelScope.launch {
            settings.collect { ideSettings ->
                _editorState.update { current ->
                    if (current.fontSizeSp != ideSettings.editorFontSizeSp) {
                        current.copy(fontSizeSp = ideSettings.editorFontSizeSp)
                    } else current
                }
            }
        }
    }

    override fun onCleared() {
        edgeTtsEngine.release()
        serverManager.stopServerSync()
        super.onCleared()
    }

    // --- Phase 3: Real AI Brain, Web Search, Builder, Pipeline & Auto-Fix ---

    fun selectChatMode(mode: ChatMode) {
        _selectedChatMode.value = mode
    }

    fun toggleWebSearch() {
        _webSearchEnabled.update { !it }
    }

    fun selectActiveAiProject(projectName: String) {
        _activeAiProjectName.value = projectName
        refreshAttachableFiles()
    }

    fun refreshAttachableFiles() {
        viewModelScope.launch {
            _attachableWorkspaceFiles.value = fileManager.listEditableFiles(_activeAiProjectName.value)
        }
    }

    fun toggleAttachContextFile(item: WorkspaceItem) {
        _attachedContextFiles.update { current ->
            if (current.any { it.absolutePath == item.absolutePath }) {
                current.filterNot { it.absolutePath == item.absolutePath }
            } else {
                (current + item).takeLast(6)
            }
        }
    }

    fun removeAttachedContextFile(absolutePath: String) {
        _attachedContextFiles.update { current ->
            current.filterNot { it.absolutePath == absolutePath }
        }
    }

    fun clearChatHistory() {
        startNewChatSession()
    }

    fun startNewChatSession() {
        if (_aiWorkStatus.value.isWorking) return
        edgeTtsEngine.stop()
        _activeSessionId.value = null
        _chatMessages.value = emptyList()
        _attachedContextFiles.value = emptyList()
        _events.tryEmit(IdeUiEvent.ShowMessage("Started a fresh chat"))
    }

    fun selectChatSession(sessionId: String) {
        if (_aiWorkStatus.value.isWorking) return
        edgeTtsEngine.stop()
        viewModelScope.launch {
            val session = chatSessions.value.firstOrNull { it.id == sessionId }
            val loaded = chatHistoryRepository.loadMessages(sessionId)
            _activeSessionId.value = sessionId
            if (session != null) {
                _selectedChatMode.value = session.mode
                if (!session.projectName.isNullOrBlank()) {
                    _activeAiProjectName.value = session.projectName
                }
            }
            _chatMessages.value = loaded
            _attachedContextFiles.value = emptyList()
            _events.tryEmit(IdeUiEvent.NavigateToTab(IdeDestination.Chat.route))
        }
    }

    fun renameChatSession(sessionId: String, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            chatHistoryRepository.renameSession(sessionId, trimmed)
            _events.tryEmit(IdeUiEvent.ShowMessage("Renamed chat to \"$trimmed\""))
        }
    }

    fun deleteChatSession(sessionId: String) {
        if (_aiWorkStatus.value.isWorking && _activeSessionId.value == sessionId) return
        viewModelScope.launch {
            chatHistoryRepository.deleteSession(sessionId)
            if (_activeSessionId.value == sessionId) {
                edgeTtsEngine.stop()
                _activeSessionId.value = null
                _chatMessages.value = emptyList()
            }
            _events.tryEmit(IdeUiEvent.ShowMessage("Chat deleted"))
        }
    }

    fun sendChatPrompt(rawPrompt: String) {
        val prompt = rawPrompt.trim()
        if (prompt.isEmpty() || _aiWorkStatus.value.isWorking) return

        val mode = _selectedChatMode.value
        val attached = _attachedContextFiles.value
        val forceSearch = _webSearchEnabled.value

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = ChatRole.USER,
            mode = mode,
            content = prompt,
            attachedFiles = attached.map { it.relativePath }
        )
        _chatMessages.update { it + userMessage }
        _attachedContextFiles.value = emptyList()

        executeAiTurn(
            prompt = prompt,
            mode = mode,
            attached = attached,
            forceSearch = forceSearch,
            replaceAssistantMessageId = null,
            pendingUserMessageToPersist = userMessage
        )
    }

    fun regenerateMessage(assistantMessageId: String) {
        if (_aiWorkStatus.value.isWorking) return
        val currentList = _chatMessages.value
        val targetIndex = currentList.indexOfFirst { it.id == assistantMessageId }
        if (targetIndex == -1) return
        val targetMsg = currentList[targetIndex]

        val resolvedPrompt = targetMsg.sourcePrompt?.takeIf { it.isNotBlank() }
            ?: currentList.subList(0, targetIndex)
                .lastOrNull { it.role == ChatRole.USER }
                ?.content
        if (resolvedPrompt.isNullOrBlank()) {
            _events.tryEmit(IdeUiEvent.ShowMessage("No user prompt found to regenerate.", isError = true))
            return
        }

        edgeTtsEngine.stop()
        executeAiTurn(
            prompt = resolvedPrompt,
            mode = targetMsg.mode,
            attached = emptyList(),
            forceSearch = _webSearchEnabled.value,
            replaceAssistantMessageId = assistantMessageId,
            pendingUserMessageToPersist = null
        )
    }

    private suspend fun ensureActiveSessionId(seedPrompt: String, mode: ChatMode): String {
        _activeSessionId.value?.let { return it }
        val title = chatHistoryRepository.generateTitleFromPrompt(seedPrompt)
        val created = chatHistoryRepository.createSession(
            title = title,
            mode = mode,
            projectName = _activeAiProjectName.value
        )
        _activeSessionId.value = created.id
        return created.id
    }

    private fun executeAiTurn(
        prompt: String,
        mode: ChatMode,
        attached: List<WorkspaceItem>,
        forceSearch: Boolean,
        replaceAssistantMessageId: String?,
        pendingUserMessageToPersist: ChatMessage?
    ) {
        val currentSettings = settings.value

        viewModelScope.launch {
            _aiWorkStatus.value = AiWorkStatus(isWorking = true, label = "✦ Preparing request...")

            val sessionId = ensureActiveSessionId(prompt, mode)
            if (pendingUserMessageToPersist != null) {
                chatHistoryRepository.saveMessage(
                    sessionId = sessionId,
                    message = pendingUserMessageToPersist,
                    mode = mode,
                    projectName = _activeAiProjectName.value
                )
            }

            // Phase 4 Auto-Memory Detection (skip on regenerate)
            if (replaceAssistantMessageId == null) {
                val detectedMemory = memoryRepository.detectAutoMemory(prompt)
                if (detectedMemory != null) {
                    val saved = memoryRepository.addMemory(detectedMemory)
                    if (saved != null) {
                        _events.tryEmit(IdeUiEvent.ShowMessage("🧠 Saved to memory"))
                    }
                }
            }

            val allMemories = memoryRepository.refreshMemories()
            val memoryContext = memoryRepository.formatMemoriesForSystemPrompt(allMemories)

            val attachedContents = LinkedHashMap<String, String>()
            attached.forEach { fileItem ->
                fileManager.readTextFile(fileItem.absolutePath).onSuccess { text ->
                    attachedContents[fileItem.relativePath] = text.take(8_000)
                }
            }

            val historySource = if (replaceAssistantMessageId != null) {
                val idx = _chatMessages.value.indexOfFirst { it.id == replaceAssistantMessageId }
                if (idx > 0) _chatMessages.value.subList(0, idx) else _chatMessages.value
            } else {
                _chatMessages.value.dropLast(1)
            }

            val historyTurns = historySource
                .filterNot { it.isError }
                .takeLast(8)
                .map { msg ->
                    AiChatTurn(
                        role = if (msg.role == ChatRole.USER) "user" else "assistant",
                        content = msg.content.take(3_500)
                    )
                }

            val result = when (mode) {
                ChatMode.CHAT -> {
                    codingAgent.runChatMode(
                        prompt = prompt,
                        history = historyTurns,
                        attachedFileContents = attachedContents,
                        settings = currentSettings,
                        forceWebSearch = forceSearch,
                        memoryContext = memoryContext,
                        onStatus = { status ->
                            _aiWorkStatus.value = AiWorkStatus(isWorking = true, label = status)
                        }
                    )
                }

                ChatMode.BUILDER -> {
                    val targetDir = fileManager.ensureTargetProjectDir(resolvePreferredAiProjectName())
                    _activeAiProjectName.value = targetDir.name
                    codingAgent.runBuilderMode(
                        prompt = prompt,
                        targetProjectDir = targetDir,
                        attachedFileContents = attachedContents,
                        settings = currentSettings,
                        forceWebSearch = forceSearch,
                        memoryContext = memoryContext,
                        onStatus = { status ->
                            _aiWorkStatus.value = AiWorkStatus(isWorking = true, label = status)
                        }
                    )
                }

                ChatMode.PIPELINE -> {
                    val targetDir = fileManager.ensureTargetProjectDir(resolvePreferredAiProjectName())
                    _activeAiProjectName.value = targetDir.name
                    val planMessageId = UUID.randomUUID().toString()
                    codingAgent.runPipelineMode(
                        prompt = prompt,
                        targetProjectDir = targetDir,
                        attachedFileContents = attachedContents,
                        settings = currentSettings,
                        forceWebSearch = forceSearch,
                        memoryContext = memoryContext,
                        onStatus = { status ->
                            _aiWorkStatus.value = AiWorkStatus(isWorking = true, label = status)
                        },
                        onPlanReady = { planText ->
                            val planBubble = ChatMessage(
                                id = planMessageId,
                                role = ChatRole.ASSISTANT,
                                mode = ChatMode.PIPELINE,
                                content = "Architecture plan ready. Executing build steps now...",
                                pipelinePlan = planText,
                                sourcePrompt = prompt
                            )
                            _chatMessages.update { it + planBubble }
                            viewModelScope.launch {
                                chatHistoryRepository.saveMessage(
                                    sessionId = sessionId,
                                    message = planBubble,
                                    mode = ChatMode.PIPELINE,
                                    projectName = _activeAiProjectName.value
                                )
                            }
                        }
                    )
                }
            }

            _aiWorkStatus.value = AiWorkStatus(isWorking = false, label = "")

            result.onSuccess { output ->
                val newMsgId = replaceAssistantMessageId ?: UUID.randomUUID().toString()
                val assistantMsg = ChatMessage(
                    id = newMsgId,
                    role = ChatRole.ASSISTANT,
                    mode = mode,
                    content = output.summaryText,
                    searchSources = output.searchSources,
                    changeSet = output.changeSet,
                    sourcePrompt = prompt
                )

                if (replaceAssistantMessageId != null) {
                    _chatMessages.update { list ->
                        list.map { if (it.id == replaceAssistantMessageId) assistantMsg else it }
                    }
                } else {
                    _chatMessages.update { it + assistantMsg }
                }

                chatHistoryRepository.saveMessage(
                    sessionId = sessionId,
                    message = assistantMsg,
                    mode = mode,
                    projectName = _activeAiProjectName.value
                )

                if (output.changeSet != null) {
                    refreshWorkspace()
                    refreshGitRepos()
                    reloadOpenEditorIfAffected(output.changeSet.files.map { it.absolutePath }.toSet())
                    if (serverState.value.isRunning && serverState.value.activeProjectPath == output.changeSet.projectAbsolutePath) {
                        serverManager.triggerReload()
                    }
                }

                // Phase 4 Auto-Speak
                val latestSettings = settings.value
                if (latestSettings.autoSpeakEnabled && assistantMsg.content.isNotBlank()) {
                    edgeTtsEngine.speak(
                        messageId = assistantMsg.id,
                        rawText = assistantMsg.content,
                        voiceId = latestSettings.selectedVoiceId,
                        speed = latestSettings.ttsSpeed
                    )
                }
            }.onFailure { error ->
                val errorMsg = ChatMessage(
                    id = replaceAssistantMessageId ?: UUID.randomUUID().toString(),
                    role = ChatRole.ASSISTANT,
                    mode = mode,
                    content = error.message ?: "Something went wrong contacting the AI provider.",
                    isError = true,
                    sourcePrompt = prompt
                )
                if (replaceAssistantMessageId != null) {
                    _chatMessages.update { list ->
                        list.map { if (it.id == replaceAssistantMessageId) errorMsg else it }
                    }
                } else {
                    _chatMessages.update { it + errorMsg }
                }
                chatHistoryRepository.saveMessage(
                    sessionId = sessionId,
                    message = errorMsg,
                    mode = mode,
                    projectName = _activeAiProjectName.value
                )
            }
        }
    }

    // --- Phase 4: Persistent Memory, Edge-TTS Voice & Message Actions ---

    fun refreshMemories() {
        viewModelScope.launch {
            memoryRepository.refreshMemories()
        }
    }

    fun addMemory(content: String, announce: Boolean = true) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val saved = memoryRepository.addMemory(trimmed)
            if (saved != null && announce) {
                _events.tryEmit(IdeUiEvent.ShowMessage("🧠 Saved to AI Memory"))
            }
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            memoryRepository.deleteMemory(id)
            _events.tryEmit(IdeUiEvent.ShowMessage("Memory deleted"))
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            memoryRepository.clearAllMemories()
            _events.tryEmit(IdeUiEvent.ShowMessage("All AI memories cleared"))
        }
    }

    fun toggleMessageFeedback(messageId: String, feedback: MessageFeedback) {
        var updatedMsg: ChatMessage? = null
        _chatMessages.update { list ->
            list.map { msg ->
                if (msg.id == messageId) {
                    val next = if (msg.feedback == feedback) MessageFeedback.NONE else feedback
                    val copy = msg.copy(feedback = next)
                    updatedMsg = copy
                    copy
                } else msg
            }
        }
        val sid = _activeSessionId.value
        val msgToPersist = updatedMsg
        if (sid != null && msgToPersist != null) {
            viewModelScope.launch {
                chatHistoryRepository.saveMessage(
                    sessionId = sid,
                    message = msgToPersist,
                    mode = _selectedChatMode.value,
                    projectName = _activeAiProjectName.value
                )
            }
        }
    }

    fun deleteChatMessage(messageId: String) {
        if (ttsState.value.activeMessageId == messageId) {
            edgeTtsEngine.stop()
        }
        _chatMessages.update { list -> list.filterNot { it.id == messageId } }
        viewModelScope.launch {
            chatHistoryRepository.deleteMessage(messageId)
        }
        _events.tryEmit(IdeUiEvent.ShowMessage("Message deleted"))
    }

    fun toggleSpeakMessage(message: ChatMessage) {
        if (ttsState.value.isMessageActive(message.id)) {
            edgeTtsEngine.stop()
        } else {
            val currentSettings = settings.value
            edgeTtsEngine.speak(
                messageId = message.id,
                rawText = message.content,
                voiceId = currentSettings.selectedVoiceId,
                speed = currentSettings.ttsSpeed
            )
        }
    }

    fun stopSpeaking() {
        edgeTtsEngine.stop()
    }

    fun testActiveVoice() {
        val currentSettings = settings.value
        val preset = currentSettings.activeVoicePreset
        val sampleText = if (preset == EdgeVoicePreset.SWARA_HI || preset == EdgeVoicePreset.MADHUR_HI) {
            "नमस्ते! मैं नोवा आईडीई की वॉइस असिस्टेंट हूँ। मैं कोडिंग और वेब डेवलपमेंट में आपकी मदद करने के लिए तैयार हूँ।"
        } else {
            "Hello! I am Nova IDE voice assistant, ready to help you build and debug your apps."
        }
        edgeTtsEngine.speak(
            messageId = "voice_preview_test",
            rawText = sampleText,
            voiceId = preset.voiceId,
            speed = currentSettings.ttsSpeed
        )
    }

    fun toggleAutoSpeak() {
        val next = !settings.value.autoSpeakEnabled
        viewModelScope.launch {
            settingsRepository.setAutoSpeakEnabled(next)
            if (!next) {
                edgeTtsEngine.stop()
            }
            _events.tryEmit(
                IdeUiEvent.ShowMessage(
                    if (next) "🔊 Auto-Speak enabled" else "🔇 Auto-Speak muted"
                )
            )
        }
    }

    fun setAutoSpeakEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoSpeakEnabled(enabled)
            if (!enabled) edgeTtsEngine.stop()
        }
    }

    fun setSelectedVoice(voiceId: String) {
        viewModelScope.launch {
            settingsRepository.setSelectedVoiceId(voiceId)
            val preset = EdgeVoicePreset.fromVoiceId(voiceId)
            _events.tryEmit(IdeUiEvent.ShowMessage("Voice: ${preset.displayName}"))
        }
    }

    fun setTtsSpeed(speed: Float) {
        viewModelScope.launch {
            settingsRepository.setTtsSpeed(speed)
            _events.tryEmit(IdeUiEvent.ShowMessage("Speech speed: ${EdgeVoicePreset.formatSpeed(speed)}"))
        }
    }

    fun showToastMessage(message: String, isError: Boolean = false) {
        _events.tryEmit(IdeUiEvent.ShowMessage(message, isError))
    }

    // --- Phase 5: Web-to-APK Converter & Export Manager ---

    fun refreshRecentExports() {
        viewModelScope.launch {
            _recentExports.value = webToApkBuilder.listRecentExports()
        }
    }

    suspend fun calculateProjectSizeBytes(projectDirPath: String?): Long {
        return webToApkBuilder.calculateDirectorySizeBytes(projectDirPath)
    }

    fun getExportShareUri(file: File): android.net.Uri {
        return webToApkBuilder.getShareableUri(file)
    }

    fun buildAndroidWebViewZip(config: WebToApkConfig) {
        if (_webToApkBuildState.value.isBuilding) return
        viewModelScope.launch {
            _webToApkBuildState.value = WebToApkBuildState(
                isBuilding = true,
                progress = 0.04f,
                logs = listOf("🚀 Starting Android WebView project build...")
            )
            val result = webToApkBuilder.buildAndroidWebViewProjectZip(config) { progress, log ->
                _webToApkBuildState.update { state ->
                    state.copy(
                        isBuilding = true,
                        progress = progress,
                        logs = state.logs + log
                    )
                }
            }
            result.onSuccess { artifact ->
                _webToApkBuildState.update { state ->
                    state.copy(
                        isBuilding = false,
                        progress = 1f,
                        lastExport = artifact,
                        errorMessage = null
                    )
                }
                refreshRecentExports()
                _events.tryEmit(IdeUiEvent.ShowMessage("✅ Built ${artifact.fileName}"))
            }.onFailure { err ->
                val message = err.message ?: "Android package build failed."
                _webToApkBuildState.update { state ->
                    state.copy(
                        isBuilding = false,
                        errorMessage = message,
                        logs = state.logs + "❌ Error: $message"
                    )
                }
                _events.tryEmit(IdeUiEvent.ShowMessage(message, isError = true))
            }
        }
    }

    fun buildStandalonePwaZip(config: WebToApkConfig) {
        if (_webToApkBuildState.value.isBuilding) return
        viewModelScope.launch {
            _webToApkBuildState.value = WebToApkBuildState(
                isBuilding = true,
                progress = 0.05f,
                logs = listOf("🌐 Starting Standalone PWA bundle export...")
            )
            val result = webToApkBuilder.buildStandalonePwaBundleZip(config) { progress, log ->
                _webToApkBuildState.update { state ->
                    state.copy(
                        isBuilding = true,
                        progress = progress,
                        logs = state.logs + log
                    )
                }
            }
            result.onSuccess { artifact ->
                _webToApkBuildState.update { state ->
                    state.copy(
                        isBuilding = false,
                        progress = 1f,
                        lastExport = artifact,
                        errorMessage = null
                    )
                }
                refreshRecentExports()
                _events.tryEmit(IdeUiEvent.ShowMessage("✅ Exported ${artifact.fileName}"))
            }.onFailure { err ->
                val message = err.message ?: "PWA export failed."
                _webToApkBuildState.update { state ->
                    state.copy(
                        isBuilding = false,
                        errorMessage = message,
                        logs = state.logs + "❌ Error: $message"
                    )
                }
                _events.tryEmit(IdeUiEvent.ShowMessage(message, isError = true))
            }
        }
    }

    fun deleteExportedArtifact(absolutePath: String) {
        viewModelScope.launch {
            val deleted = webToApkBuilder.deleteExport(absolutePath)
            refreshRecentExports()
            if (_webToApkBuildState.value.lastExport?.absolutePath == absolutePath) {
                _webToApkBuildState.update { it.copy(lastExport = null) }
            }
            if (deleted) {
                _events.tryEmit(IdeUiEvent.ShowMessage("Export deleted"))
            }
        }
    }

    // --- Phase 6: SDK Toolchain Manager & On-Device Eclipse ECJ Java Compiler ---

    fun refreshToolchainStatus() {
        toolchainManager.refreshStatus()
        keystoreManager.refreshInfo()
    }

    fun createCustomKeystore(
        alias: String,
        commonName: String,
        organization: String,
        password: String
    ) {
        viewModelScope.launch {
            keystoreManager.createCustomKeystore(
                alias = alias,
                commonName = commonName,
                organization = organization,
                password = password
            ).onSuccess { info ->
                _events.tryEmit(IdeUiEvent.ShowMessage("✅ Custom keystore ${info.fileName} is now active"))
            }.onFailure { err ->
                _events.tryEmit(
                    IdeUiEvent.ShowMessage(err.message ?: "Failed to create custom keystore", isError = true)
                )
            }
        }
    }

    fun resetToDebugKeystore() {
        viewModelScope.launch {
            keystoreManager.resetToDebugKeystore()
                .onSuccess {
                    _events.tryEmit(IdeUiEvent.ShowMessage("Switched back to Nova Debug keystore"))
                }
                .onFailure { err ->
                    _events.tryEmit(
                        IdeUiEvent.ShowMessage(err.message ?: "Could not reset keystore", isError = true)
                    )
                }
        }
    }

    fun importAndroidJar(uri: android.net.Uri) {
        viewModelScope.launch {
            toolchainManager.importAndroidJarFromUri(uri)
                .onSuccess {
                    _events.tryEmit(IdeUiEvent.ShowMessage("✅ Imported android.jar to /toolchain/"))
                }
                .onFailure { err ->
                    _events.tryEmit(
                        IdeUiEvent.ShowMessage(err.message ?: "Failed to import android.jar", isError = true)
                    )
                }
        }
    }

    fun downloadAndroidJar(url: String) {
        viewModelScope.launch {
            toolchainManager.downloadAndroidJarFromUrl(url)
                .onSuccess {
                    _events.tryEmit(IdeUiEvent.ShowMessage("✅ Downloaded android.jar successfully"))
                }
                .onFailure { err ->
                    _events.tryEmit(
                        IdeUiEvent.ShowMessage(err.message ?: "Download failed", isError = true)
                    )
                }
        }
    }

    fun deleteAndroidJar() {
        viewModelScope.launch {
            toolchainManager.deleteAndroidJar()
                .onSuccess {
                    _events.tryEmit(IdeUiEvent.ShowMessage("android.jar removed"))
                }
                .onFailure { err ->
                    _events.tryEmit(
                        IdeUiEvent.ShowMessage(err.message ?: "Could not remove android.jar", isError = true)
                    )
                }
        }
    }

    fun compileJavaProject(projectPath: String) {
        if (_javaCompileState.value.isBusy) return
        val projectDir = File(projectPath)
        viewModelScope.launch {
            _javaCompileState.value = JavaCompileState(
                isCompiling = true,
                isBuildingApk = false,
                selectedProjectPath = projectPath,
                ecjStatus = BuildStageStatus.RUNNING,
                dexStatus = BuildStageStatus.IDLE,
                packageStatus = BuildStageStatus.IDLE,
                signStatus = BuildStageStatus.IDLE,
                logs = emptyList()
            )
            val result = javaCompilerEngine.compileProject(projectDir) { logEntry ->
                _javaCompileState.update { state ->
                    state.copy(logs = state.logs + logEntry)
                }
            }
            _javaCompileState.value = JavaCompileState(
                isCompiling = false,
                isBuildingApk = false,
                selectedProjectPath = projectPath,
                ecjStatus = if (result.success) BuildStageStatus.SUCCESS else BuildStageStatus.FAILED,
                dexStatus = BuildStageStatus.IDLE,
                packageStatus = BuildStageStatus.IDLE,
                signStatus = BuildStageStatus.IDLE,
                compiledClassFiles = result.compiledClassFiles,
                logs = result.logs,
                rawErrorOutput = result.rawErrorOutput,
                durationMs = result.durationMs,
                success = result.success,
                errorCount = result.errorCount,
                warningCount = result.warningCount,
                isAutoFixingJava = false
            )
            refreshWorkspace()
            if (result.success) {
                _events.tryEmit(
                    IdeUiEvent.ShowMessage(
                        "✅ Compiled ${result.compiledClassFiles.size} .class file(s) in ${result.durationMs}ms"
                    )
                )
            } else {
                _events.tryEmit(
                    IdeUiEvent.ShowMessage("Java compilation failed (${result.errorCount} error(s))", isError = true)
                )
            }
        }
    }

    fun buildSignedApk(projectPath: String) {
        if (_javaCompileState.value.isBusy) return
        val projectDir = File(projectPath)
        viewModelScope.launch {
            val pipelineStart = System.currentTimeMillis()
            _javaCompileState.value = JavaCompileState(
                isCompiling = true,
                isBuildingApk = true,
                selectedProjectPath = projectPath,
                ecjStatus = BuildStageStatus.RUNNING,
                dexStatus = BuildStageStatus.IDLE,
                packageStatus = BuildStageStatus.IDLE,
                signStatus = BuildStageStatus.IDLE,
                logs = emptyList()
            )

            // Stage 1: Eclipse ECJ (.java -> .class)
            val ecjResult = javaCompilerEngine.compileProject(projectDir) { logEntry ->
                _javaCompileState.update { state -> state.copy(logs = state.logs + logEntry) }
            }
            if (!ecjResult.success) {
                val totalMs = (System.currentTimeMillis() - pipelineStart).coerceAtLeast(1L)
                _javaCompileState.update { state ->
                    state.copy(
                        isCompiling = false,
                        isBuildingApk = false,
                        ecjStatus = BuildStageStatus.FAILED,
                        dexStatus = BuildStageStatus.SKIPPED,
                        packageStatus = BuildStageStatus.SKIPPED,
                        signStatus = BuildStageStatus.SKIPPED,
                        compiledClassFiles = ecjResult.compiledClassFiles,
                        rawErrorOutput = ecjResult.rawErrorOutput,
                        durationMs = totalMs,
                        success = false,
                        errorCount = ecjResult.errorCount,
                        warningCount = ecjResult.warningCount
                    )
                }
                refreshWorkspace()
                _events.tryEmit(
                    IdeUiEvent.ShowMessage("Build stopped at ECJ (${ecjResult.errorCount} Java error(s))", isError = true)
                )
                return@launch
            }

            // Stage 2: DEX Converter (.class -> classes.dex)
            _javaCompileState.update { state ->
                state.copy(
                    ecjStatus = BuildStageStatus.SUCCESS,
                    dexStatus = BuildStageStatus.RUNNING,
                    compiledClassFiles = ecjResult.compiledClassFiles
                )
            }

            val dexResult = dexCompilerEngine.compileClassesToDex(projectDir) { logEntry ->
                _javaCompileState.update { state -> state.copy(logs = state.logs + logEntry) }
            }
            if (!dexResult.success || dexResult.dexFile == null) {
                val totalMs = (System.currentTimeMillis() - pipelineStart).coerceAtLeast(1L)
                _javaCompileState.update { state ->
                    state.copy(
                        isCompiling = false,
                        isBuildingApk = false,
                        dexStatus = BuildStageStatus.FAILED,
                        packageStatus = BuildStageStatus.SKIPPED,
                        signStatus = BuildStageStatus.SKIPPED,
                        durationMs = totalMs,
                        success = false,
                        errorCount = state.logs.count { it.level == com.example.domain.model.CompilerLogLevel.ERROR }.coerceAtLeast(1),
                        warningCount = state.logs.count { it.level == com.example.domain.model.CompilerLogLevel.WARNING }
                    )
                }
                refreshWorkspace()
                _events.tryEmit(IdeUiEvent.ShowMessage("Build failed during DEX conversion", isError = true))
                return@launch
            }

            val dexArtifact = NativeBuildArtifact(
                label = "Dalvik Bytecode",
                fileName = dexResult.dexFile.name,
                relativePath = "build/dex/${dexResult.dexFile.name}",
                absolutePath = dexResult.dexFile.canonicalPath,
                sizeBytes = dexResult.dexSizeBytes
            )

            // Stage 3: Resource & Unsigned APK Packager
            _javaCompileState.update { state ->
                state.copy(
                    dexStatus = BuildStageStatus.SUCCESS,
                    packageStatus = BuildStageStatus.RUNNING,
                    dexArtifact = dexArtifact
                )
            }

            val pkgResult = apkPackagerEngine.packageUnsignedApk(projectDir) { logEntry ->
                _javaCompileState.update { state -> state.copy(logs = state.logs + logEntry) }
            }

            if (!pkgResult.success || pkgResult.unsignedApkFile == null) {
                val totalMs = (System.currentTimeMillis() - pipelineStart).coerceAtLeast(1L)
                _javaCompileState.update { state ->
                    state.copy(
                        isCompiling = false,
                        isBuildingApk = false,
                        packageStatus = BuildStageStatus.FAILED,
                        signStatus = BuildStageStatus.SKIPPED,
                        durationMs = totalMs,
                        success = false,
                        errorCount = state.logs.count { it.level == com.example.domain.model.CompilerLogLevel.ERROR }.coerceAtLeast(1),
                        warningCount = state.logs.count { it.level == com.example.domain.model.CompilerLogLevel.WARNING }
                    )
                }
                refreshWorkspace()
                _events.tryEmit(IdeUiEvent.ShowMessage("Unsigned APK packaging failed", isError = true))
                return@launch
            }

            val unsignedApkArtifact = NativeBuildArtifact(
                label = if (pkgResult.usedAapt2) "Unsigned APK (aapt2)" else "Unsigned APK (Zip Fallback)",
                fileName = pkgResult.unsignedApkFile.name,
                relativePath = "build/apk/${pkgResult.unsignedApkFile.name}",
                absolutePath = pkgResult.unsignedApkFile.canonicalPath,
                sizeBytes = pkgResult.apkSizeBytes
            )

            // Stage 4: On-Device APK Signer (apksig v1 + v2)
            _javaCompileState.update { state ->
                state.copy(
                    packageStatus = BuildStageStatus.SUCCESS,
                    signStatus = BuildStageStatus.RUNNING,
                    unsignedApkArtifact = unsignedApkArtifact,
                    usedAapt2 = pkgResult.usedAapt2
                )
            }

            val signResult = apkSignerEngine.signUnsignedApk(
                projectDir = projectDir,
                unsignedApkFile = pkgResult.unsignedApkFile
            ) { logEntry ->
                _javaCompileState.update { state -> state.copy(logs = state.logs + logEntry) }
            }

            val totalMs = (System.currentTimeMillis() - pipelineStart).coerceAtLeast(1L)

            if (!signResult.success || signResult.signedApkFile == null) {
                _javaCompileState.update { state ->
                    state.copy(
                        isCompiling = false,
                        isBuildingApk = false,
                        signStatus = BuildStageStatus.FAILED,
                        durationMs = totalMs,
                        success = false,
                        errorCount = state.logs.count { it.level == com.example.domain.model.CompilerLogLevel.ERROR }.coerceAtLeast(1),
                        warningCount = state.logs.count { it.level == com.example.domain.model.CompilerLogLevel.WARNING }
                    )
                }
                refreshWorkspace()
                _events.tryEmit(IdeUiEvent.ShowMessage("APK signing failed", isError = true))
                return@launch
            }

            val signedApkArtifact = NativeBuildArtifact(
                label = if (signResult.isCustomKeystore) {
                    "Signed APK (v1+v2 • Custom)"
                } else {
                    "Signed APK (v1+v2 • Debug)"
                },
                fileName = signResult.signedApkFile.name,
                relativePath = "build/apk/${signResult.signedApkFile.name}",
                absolutePath = signResult.signedApkFile.canonicalPath,
                sizeBytes = signResult.apkSizeBytes
            )

            _javaCompileState.update { state ->
                state.copy(
                    isCompiling = false,
                    isBuildingApk = false,
                    signStatus = BuildStageStatus.SUCCESS,
                    dexArtifact = dexArtifact,
                    unsignedApkArtifact = unsignedApkArtifact,
                    signedApkArtifact = signedApkArtifact,
                    usedAapt2 = pkgResult.usedAapt2,
                    durationMs = totalMs,
                    success = true,
                    errorCount = 0,
                    warningCount = state.logs.count { it.level == com.example.domain.model.CompilerLogLevel.WARNING }
                )
            }
            refreshWorkspace()
            refreshRecentExports()
            _events.tryEmit(
                IdeUiEvent.ShowMessage("✅ Built & signed ${signedApkArtifact.fileName} in ${totalMs}ms")
            )
        }
    }

    fun autoFixJavaErrors(projectPath: String) {
        val current = _javaCompileState.value
        if (current.isCompiling || current.isAutoFixingJava) return
        val errorLog = current.rawErrorOutput.ifBlank {
            current.logs.filter { it.level == com.example.domain.model.CompilerLogLevel.ERROR }
                .joinToString("\n") { entry ->
                    val loc = entry.filePath?.let { p -> "$p:${entry.lineNumber ?: 0}" }.orEmpty()
                    "$loc ${entry.message}".trim()
                }
        }
        if (errorLog.isBlank()) {
            _events.tryEmit(IdeUiEvent.ShowMessage("No Java compiler errors to fix.", isError = true))
            return
        }

        val projectDir = File(projectPath)
        viewModelScope.launch {
            _javaCompileState.update { it.copy(isAutoFixingJava = true) }

            val allMemories = memoryRepository.refreshMemories()
            val memoryContext = memoryRepository.formatMemoriesForSystemPrompt(allMemories)

            val fixResult = codingAgent.autoFixJavaCompileErrors(
                targetProjectDir = projectDir,
                compilerErrorLog = errorLog,
                settings = settings.value,
                memoryContext = memoryContext,
                onStatus = { status ->
                    _aiWorkStatus.value = AiWorkStatus(isWorking = true, label = status)
                }
            )

            _aiWorkStatus.value = AiWorkStatus(isWorking = false, label = "")
            _javaCompileState.update { it.copy(isAutoFixingJava = false) }

            fixResult.onSuccess { output ->
                val fixMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = ChatRole.ASSISTANT,
                    mode = ChatMode.BUILDER,
                    content = "✨ Java Auto-Fix applied for ${projectDir.name}:\n${output.summaryText}",
                    changeSet = output.changeSet
                )
                _chatMessages.update { it + fixMsg }
                refreshWorkspace()
                output.changeSet?.let { cs ->
                    reloadOpenEditorIfAffected(cs.files.map { it.absolutePath }.toSet())
                }
                _events.tryEmit(IdeUiEvent.ShowMessage("✨ AI patched Java files — re-compiling..."))
                compileJavaProject(projectPath)
            }.onFailure { err ->
                _events.tryEmit(
                    IdeUiEvent.ShowMessage(err.message ?: "AI Java Auto-Fix failed.", isError = true)
                )
            }
        }
    }

    fun keepChangeSet(messageId: String) {
        _chatMessages.update { messages ->
            messages.map { msg ->
                if (msg.id == messageId && msg.changeSet != null) {
                    msg.copy(changeSet = msg.changeSet.copy(status = ChangeReviewStatus.KEPT))
                } else msg
            }
        }
        _events.tryEmit(IdeUiEvent.ShowMessage("Changes kept"))
    }

    fun revertChangeSet(messageId: String) {
        val targetMsg = _chatMessages.value.firstOrNull { it.id == messageId } ?: return
        val changeSet = targetMsg.changeSet ?: return
        if (changeSet.status == ChangeReviewStatus.REVERTED) return

        viewModelScope.launch {
            fileManager.revertChangeSet(changeSet)
                .onSuccess {
                    _chatMessages.update { messages ->
                        messages.map { msg ->
                            if (msg.id == messageId && msg.changeSet != null) {
                                msg.copy(changeSet = msg.changeSet.copy(status = ChangeReviewStatus.REVERTED))
                            } else msg
                        }
                    }
                    refreshWorkspace()
                    refreshGitRepos()
                    reloadOpenEditorIfAffected(changeSet.files.map { it.absolutePath }.toSet())
                    if (serverState.value.isRunning && serverState.value.activeProjectPath == changeSet.projectAbsolutePath) {
                        serverManager.triggerReload()
                    }
                    _events.tryEmit(IdeUiEvent.ShowMessage("Reverted ${changeSet.files.size} file(s)"))
                }
                .onFailure { error ->
                    _events.tryEmit(IdeUiEvent.ShowMessage(error.message ?: "Revert failed.", isError = true))
                }
        }
    }

    fun openFileByAbsolutePath(absolutePath: String) {
        val file = File(absolutePath)
        if (!file.exists() || !file.isFile) {
            _events.tryEmit(IdeUiEvent.ShowMessage("File no longer exists.", isError = true))
            return
        }
        val item = WorkspaceItem(
            name = file.name,
            absolutePath = file.canonicalPath,
            relativePath = fileManager.relativePathOf(file),
            isDirectory = false,
            sizeBytes = file.length(),
            lastModifiedMillis = file.lastModified(),
            extension = file.extension.lowercase()
        )
        openFileInEditor(item)
    }

    fun runProjectPreviewByPath(projectAbsolutePath: String) {
        viewModelScope.launch {
            val dir = File(projectAbsolutePath)
            val current = serverState.value
            if (current.isRunning && current.activeProjectPath == dir.canonicalPath) {
                serverManager.triggerReload()
                _events.tryEmit(IdeUiEvent.NavigateToTab(IdeDestination.Preview.route))
            } else {
                serverManager.startServer(dir)
                    .onSuccess { started ->
                        clearConsoleLogs()
                        _events.tryEmit(IdeUiEvent.ShowMessage("Serving ${started.activeProjectName} on :${started.port}"))
                        _events.tryEmit(IdeUiEvent.NavigateToTab(IdeDestination.Preview.route))
                    }
                    .onFailure { error ->
                        _events.tryEmit(IdeUiEvent.ShowMessage(error.message ?: "Failed to launch preview.", isError = true))
                    }
            }
        }
    }

    fun autoFixConsoleErrors() {
        if (_isAutoFixing.value) return
        val logs = _consoleLogs.value
        val activeProjectPath = serverState.value.activeProjectPath
        if (logs.isEmpty() || activeProjectPath.isNullOrBlank()) {
            _events.tryEmit(IdeUiEvent.ShowMessage("No console errors to auto-fix.", isError = true))
            return
        }

        viewModelScope.launch {
            _isAutoFixing.value = true
            val projectDir = File(activeProjectPath)

            val allMemories = memoryRepository.refreshMemories()
            val memoryContext = memoryRepository.formatMemoriesForSystemPrompt(allMemories)

            val result = codingAgent.autoFixConsoleErrors(
                targetProjectDir = projectDir,
                consoleLogs = logs,
                settings = settings.value,
                memoryContext = memoryContext,
                onStatus = { status ->
                    _aiWorkStatus.value = AiWorkStatus(isWorking = true, label = status)
                }
            )

            _aiWorkStatus.value = AiWorkStatus(isWorking = false, label = "")
            _isAutoFixing.value = false

            result.onSuccess { output ->
                // Record fix + review card in Chat history too
                val fixMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = ChatRole.ASSISTANT,
                    mode = ChatMode.BUILDER,
                    content = "✨ Auto-Fix applied for ${projectDir.name}:\n${output.summaryText}",
                    changeSet = output.changeSet
                )
                _chatMessages.update { it + fixMsg }

                refreshWorkspace()
                refreshGitRepos()
                output.changeSet?.let { cs ->
                    reloadOpenEditorIfAffected(cs.files.map { it.absolutePath }.toSet())
                }
                clearConsoleLogs()
                serverManager.restartServer()
                _events.tryEmit(IdeUiEvent.ShowMessage("✨ Auto-fixed & reloaded Preview!"))
            }.onFailure { error ->
                _events.tryEmit(
                    IdeUiEvent.ShowMessage(error.message ?: "Auto-Fix failed.", isError = true)
                )
            }
        }
    }

    private fun resolvePreferredAiProjectName(): String? {
        _activeAiProjectName.value?.let { return it }
        serverState.value.activeProjectName?.let { return it }
        _editorState.value.filePath?.let { openPath ->
            fileManager.resolveTopLevelProjectDir(openPath)?.name?.let { return it }
        }
        return _rootProjectNames.value.firstOrNull()
    }

    private suspend fun reloadOpenEditorIfAffected(affectedPaths: Set<String>) {
        val currentEditor = _editorState.value
        val openPath = currentEditor.filePath ?: return
        if (openPath !in affectedPaths) return
        val file = File(openPath)
        if (!file.exists()) {
            _editorState.value = EditorTabState(fontSizeSp = settings.value.editorFontSizeSp)
            return
        }
        fileManager.readTextFile(openPath).onSuccess { refreshed ->
            _editorState.update { prev ->
                prev.copy(
                    savedContent = refreshed,
                    currentContent = refreshed,
                    contentRevision = prev.contentRevision + 1L,
                    lineCount = refreshed.count { it == '\n' } + 1
                )
            }
        }
    }

    // --- Workspace & File Manager ---

    fun refreshWorkspace(relativePath: String = _filesState.value.currentRelativePath) {
        viewModelScope.launch {
            _filesState.update { it.copy(isLoading = true) }
            val result = fileManager.listItems(relativePath)
            val rootItemsResult = fileManager.listItems("")
            val rootFolders = rootItemsResult.getOrDefault(emptyList()).filter { it.isDirectory }
            _rootProjectFolders.value = rootFolders
            val rootNames = rootFolders.map { it.name }
            _rootProjectNames.value = rootNames
            if (_activeAiProjectName.value == null || _activeAiProjectName.value !in rootNames) {
                _activeAiProjectName.value = serverState.value.activeProjectName ?: rootNames.firstOrNull()
            }

            val htmlFiles = fileManager.findHtmlEntryPoints()
            val previewable = fileManager.findPreviewableProjects()
            _previewProjects.value = previewable
            _javaProjects.value = fileManager.findJavaProjects()
            _attachableWorkspaceFiles.value = fileManager.listEditableFiles(_activeAiProjectName.value)

            result.onSuccess { items ->
                _filesState.update {
                    it.copy(
                        currentRelativePath = relativePath,
                        breadcrumbs = fileManager.buildBreadcrumbs(relativePath),
                        items = items,
                        isLoading = false,
                        htmlEntryPoints = htmlFiles,
                        rootProjectCount = rootNames.size
                    )
                }
            }.onFailure { error ->
                _filesState.update { it.copy(isLoading = false) }
                _events.tryEmit(IdeUiEvent.ShowMessage(error.message ?: "Failed to read workspace.", isError = true))
            }
        }
    }

    fun navigateToDirectory(relativePath: String) {
        refreshWorkspace(relativePath)
    }

    fun navigateToRoot() {
        refreshWorkspace("")
    }

    fun navigateUpDirectory(): Boolean {
        val current = _filesState.value.currentRelativePath
        if (current.isBlank()) return false
        val parent = current.substringBeforeLast('/', "")
        refreshWorkspace(parent)
        return true
    }

    fun createProject(name: String, template: ProjectTemplate) {
        viewModelScope.launch {
            fileManager.createProject(name, template)
                .onSuccess { created ->
                    _activeAiProjectName.value = created.name
                    refreshWorkspace("")
                    _events.tryEmit(IdeUiEvent.ShowMessage("Created project \"${created.name}\""))
                }
                .onFailure { error ->
                    _events.tryEmit(IdeUiEvent.ShowMessage(error.message ?: "Could not create project.", isError = true))
                }
        }
    }

    fun createEntry(name: String, isDirectory: Boolean) {
        viewModelScope.launch {
            val currentDir = _filesState.value.currentRelativePath
            fileManager.createEntry(currentDir, name, isDirectory)
                .onSuccess { created ->
                    refreshWorkspace(currentDir)
                    val kind = if (isDirectory) "folder" else "file"
                    _events.tryEmit(IdeUiEvent.ShowMessage("Created $kind \"${created.name}\""))
                    if (!isDirectory && created.isCodeEditable) {
                        openFileInEditor(created)
                    }
                }
                .onFailure { error ->
                    _events.tryEmit(IdeUiEvent.ShowMessage(error.message ?: "Creation failed.", isError = true))
                }
        }
    }

    fun renameEntry(item: WorkspaceItem, newName: String) {
        viewModelScope.launch {
            val currentDir = _filesState.value.currentRelativePath
            fileManager.renameEntry(item.relativePath, newName)
                .onSuccess { renamed ->
                    if (_editorState.value.filePath == item.absolutePath) {
                        _editorState.update {
                            it.copy(
                                filePath = renamed.absolutePath,
                                fileName = renamed.name,
                                relativePath = renamed.relativePath,
                                extension = renamed.extension,
                                languageLabel = CodeLanguageResolver.languageLabelFor(renamed.extension)
                            )
                        }
                    }
                    if (serverState.value.activeProjectPath == item.absolutePath) {
                        serverManager.stopServer()
                    }
                    refreshWorkspace(currentDir)
                    refreshGitRepos()
                    _events.tryEmit(IdeUiEvent.ShowMessage("Renamed to \"${renamed.name}\""))
                }
                .onFailure { error ->
                    _events.tryEmit(IdeUiEvent.ShowMessage(error.message ?: "Rename failed.", isError = true))
                }
        }
    }

    fun deleteEntry(item: WorkspaceItem) {
        viewModelScope.launch {
            val currentDir = _filesState.value.currentRelativePath
            fileManager.deleteEntry(item.relativePath)
                .onSuccess {
                    val openPath = _editorState.value.filePath
                    if (openPath != null && (openPath == item.absolutePath || openPath.startsWith(item.absolutePath + "/"))) {
                        _editorState.value = EditorTabState(fontSizeSp = settings.value.editorFontSizeSp)
                    }
                    val activeServerPath = serverState.value.activeProjectPath
                    if (activeServerPath != null && (activeServerPath == item.absolutePath || activeServerPath.startsWith(item.absolutePath + "/"))) {
                        serverManager.stopServer()
                    }
                    refreshWorkspace(currentDir)
                    refreshGitRepos()
                    _events.tryEmit(IdeUiEvent.ShowMessage("Deleted \"${item.name}\""))
                }
                .onFailure { error ->
                    _events.tryEmit(IdeUiEvent.ShowMessage(error.message ?: "Delete failed.", isError = true))
                }
        }
    }

    // --- Code Editor ---

    fun openFileInEditor(item: WorkspaceItem) {
        if (item.isDirectory) {
            navigateToDirectory(item.relativePath)
            return
        }
        viewModelScope.launch {
            fileManager.readTextFile(item.absolutePath)
                .onSuccess { text ->
                    val lines = text.count { it == '\n' } + 1
                    _editorState.update { prev ->
                        prev.copy(
                            filePath = item.absolutePath,
                            fileName = item.name,
                            relativePath = item.relativePath,
                            extension = item.extension,
                            languageLabel = CodeLanguageResolver.languageLabelFor(item.extension),
                            savedContent = text,
                            currentContent = text,
                            contentRevision = prev.contentRevision + 1L,
                            cursorLine = 1,
                            cursorColumn = 1,
                            lineCount = lines,
                            canUndo = false,
                            canRedo = false
                        )
                    }
                    _events.tryEmit(IdeUiEvent.NavigateToTab(IdeDestination.Editor.route))
                }
                .onFailure { error ->
                    _events.tryEmit(IdeUiEvent.ShowMessage(error.message ?: "Cannot open file.", isError = true))
                }
        }
    }

    fun onEditorContentChanged(newContent: String, lineCount: Int, canUndo: Boolean, canRedo: Boolean) {
        _editorState.update { state ->
            if (!state.hasOpenFile) state
            else state.copy(
                currentContent = newContent,
                lineCount = lineCount.coerceAtLeast(1),
                canUndo = canUndo,
                canRedo = canRedo
            )
        }
    }

    fun onEditorCursorChanged(line: Int, column: Int) {
        _editorState.update { state ->
            if (!state.hasOpenFile) state
            else state.copy(cursorLine = line + 1, cursorColumn = column + 1)
        }
    }

    fun saveActiveFile() {
        val state = _editorState.value
        val path = state.filePath ?: return
        viewModelScope.launch {
            _editorState.update { it.copy(isSaving = true) }
            fileManager.writeTextFile(path, state.currentContent)
                .onSuccess {
                    _editorState.update {
                        it.copy(savedContent = state.currentContent, isSaving = false)
                    }
                    refreshWorkspace()
                    refreshGitRepos()
                    val projectDir = fileManager.resolveTopLevelProjectDir(path)
                    if (projectDir != null && serverState.value.isRunning && serverState.value.activeProjectPath == projectDir.canonicalPath) {
                        serverManager.triggerReload()
                    }
                    _events.tryEmit(IdeUiEvent.ShowMessage("Saved ${state.fileName}"))
                }
                .onFailure { error ->
                    _editorState.update { it.copy(isSaving = false) }
                    _events.tryEmit(IdeUiEvent.ShowMessage(error.message ?: "Save failed.", isError = true))
                }
        }
    }

    fun runActiveEditorFileInPreview() {
        val state = _editorState.value
        val path = state.filePath ?: return
        viewModelScope.launch {
            if (state.isDirty) {
                _editorState.update { it.copy(isSaving = true) }
                val writeResult = fileManager.writeTextFile(path, state.currentContent)
                if (writeResult.isFailure) {
                    _editorState.update { it.copy(isSaving = false) }
                    _events.tryEmit(
                        IdeUiEvent.ShowMessage(
                            writeResult.exceptionOrNull()?.message ?: "Save failed before preview.",
                            isError = true
                        )
                    )
                    return@launch
                }
                _editorState.update { it.copy(savedContent = state.currentContent, isSaving = false) }
                refreshWorkspace()
                refreshGitRepos()
            }

            val projectDir = fileManager.resolveTopLevelProjectDir(path) ?: fileManager.ensureWorkspaceRoot()
            val currentServer = serverState.value
            if (currentServer.isRunning && currentServer.activeProjectPath == projectDir.canonicalPath) {
                serverManager.triggerReload()
                _events.tryEmit(IdeUiEvent.NavigateToTab(IdeDestination.Preview.route))
            } else {
                serverManager.startServer(projectDir)
                    .onSuccess { started ->
                        clearConsoleLogs()
                        _events.tryEmit(
                            IdeUiEvent.ShowMessage("Serving ${started.activeProjectName} on :${started.port}")
                        )
                        _events.tryEmit(IdeUiEvent.NavigateToTab(IdeDestination.Preview.route))
                    }
                    .onFailure { error ->
                        _events.tryEmit(
                            IdeUiEvent.ShowMessage(error.message ?: "Failed to start preview server.", isError = true)
                        )
                    }
            }
        }
    }

    fun cycleEditorFontSize() {
        val sizes = listOf(12f, 14f, 16f, 18f)
        val current = _editorState.value.fontSizeSp
        val next = sizes.firstOrNull { it > current + 0.1f } ?: sizes.first()
        _editorState.update { it.copy(fontSizeSp = next) }
        viewModelScope.launch {
            settingsRepository.setEditorFontSize(next)
        }
    }

    // --- Phase 2: Embedded Localhost Server & Live Console ---

    fun startPreviewServer(projectDirPath: String) {
        viewModelScope.launch {
            val dir = File(projectDirPath)
            serverManager.startServer(dir)
                .onSuccess { started ->
                    clearConsoleLogs()
                    _events.tryEmit(
                        IdeUiEvent.ShowMessage("Server running at ${started.localUrl}")
                    )
                }
                .onFailure { error ->
                    _events.tryEmit(
                        IdeUiEvent.ShowMessage(error.message ?: "Unable to start local server.", isError = true)
                    )
                }
        }
    }

    fun stopPreviewServer() {
        viewModelScope.launch {
            serverManager.stopServer()
            _events.tryEmit(IdeUiEvent.ShowMessage("Localhost server stopped"))
        }
    }

    fun restartPreviewServer() {
        viewModelScope.launch {
            serverManager.restartServer()
                .onSuccess { restarted ->
                    _events.tryEmit(IdeUiEvent.ShowMessage("Restarted server on :${restarted.port}"))
                }
                .onFailure { error ->
                    _events.tryEmit(IdeUiEvent.ShowMessage(error.message ?: "Restart failed.", isError = true))
                }
        }
    }

    fun reloadPreview() {
        if (serverState.value.isRunning) {
            serverManager.triggerReload()
        }
    }

    fun appendConsoleLog(
        level: ConsoleLogLevel,
        message: String,
        source: String,
        lineNumber: Int
    ) {
        val cleanSource = source
            .substringAfterLast('/')
            .substringBefore('?')
            .ifBlank { "webview" }
        val entry = ConsoleLogEntry(
            id = consoleLogIdGenerator.getAndIncrement(),
            level = level,
            message = message,
            source = cleanSource,
            lineNumber = lineNumber,
            timestampMillis = System.currentTimeMillis()
        )
        _consoleLogs.update { existing ->
            (existing + entry).takeLast(MAX_CONSOLE_LOGS)
        }
    }

    fun clearConsoleLogs() {
        _consoleLogs.value = emptyList()
    }

    // --- JGit Integration ---

    fun refreshGitRepos() {
        viewModelScope.launch {
            _gitRepos.value = gitManager.discoverRepositories()
        }
    }

    fun cloneRepository(repoUrl: String, customFolderName: String) {
        if (_cloneProgress.value.isRunning) return
        viewModelScope.launch {
            _cloneProgress.value = GitCloneProgress(
                isRunning = true,
                taskTitle = "Initializing clone...",
                completedUnits = 0,
                totalUnits = 0,
                statusMessage = null,
                isError = false
            )
            val result = gitManager.cloneRepository(
                repoUrl = repoUrl,
                customFolderName = customFolderName,
                optionalPat = settings.value.githubPat.takeIf { it.isNotBlank() }
            ) { title, completed, total ->
                _cloneProgress.update {
                    it.copy(
                        isRunning = true,
                        taskTitle = title,
                        completedUnits = completed,
                        totalUnits = total
                    )
                }
            }

            when (result) {
                is GitOperationResult.Success -> {
                    _cloneProgress.value = GitCloneProgress(
                        isRunning = false,
                        taskTitle = "Completed",
                        completedUnits = 100,
                        totalUnits = 100,
                        statusMessage = result.message,
                        isError = false,
                        lastClonedRelativePath = result.path
                    )
                    refreshWorkspace()
                    refreshGitRepos()
                    _events.tryEmit(IdeUiEvent.ShowMessage(result.message))
                }

                is GitOperationResult.Failure -> {
                    _cloneProgress.value = GitCloneProgress(
                        isRunning = false,
                        taskTitle = "",
                        statusMessage = result.message,
                        isError = true
                    )
                    _events.tryEmit(IdeUiEvent.ShowMessage(result.message, isError = true))
                }
            }
        }
    }

    fun commitLocally(repoAbsolutePath: String, message: String) {
        if (_pushState.value.isRunning) return
        viewModelScope.launch {
            _pushState.value = GitPushState(isRunning = true, statusMessage = "Committing changes...")
            when (val result = gitManager.commitLocally(repoAbsolutePath, message)) {
                is GitOperationResult.Success -> {
                    _pushState.value = GitPushState(isRunning = false, statusMessage = result.message, isError = false)
                    refreshGitRepos()
                    _events.tryEmit(IdeUiEvent.ShowMessage(result.message))
                }
                is GitOperationResult.Failure -> {
                    _pushState.value = GitPushState(isRunning = false, statusMessage = result.message, isError = true)
                    _events.tryEmit(IdeUiEvent.ShowMessage(result.message, isError = true))
                }
            }
        }
    }

    fun commitAndPush(repoAbsolutePath: String, message: String) {
        if (_pushState.value.isRunning) return
        val pat = settings.value.githubPat
        viewModelScope.launch {
            _pushState.value = GitPushState(isRunning = true, statusMessage = "Committing & pushing to GitHub...")
            when (val result = gitManager.commitAndPush(repoAbsolutePath, message, pat)) {
                is GitOperationResult.Success -> {
                    _pushState.value = GitPushState(isRunning = false, statusMessage = result.message, isError = false)
                    refreshGitRepos()
                    _events.tryEmit(IdeUiEvent.ShowMessage(result.message))
                }
                is GitOperationResult.Failure -> {
                    _pushState.value = GitPushState(isRunning = false, statusMessage = result.message, isError = true)
                    _events.tryEmit(IdeUiEvent.ShowMessage(result.message, isError = true))
                }
            }
        }
    }

    // --- Settings Persistence ---

    fun setUseDefaultFreeAi(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setUseDefaultFreeAi(enabled)
        }
    }

    fun saveByokSettings(baseUrl: String, apiKey: String, modelName: String) {
        viewModelScope.launch {
            settingsRepository.saveByokConfig(baseUrl, apiKey, modelName)
            _events.tryEmit(IdeUiEvent.ShowMessage("AI provider settings saved"))
        }
    }

    fun saveAndActivateByok(baseUrl: String, apiKey: String, modelName: String) {
        viewModelScope.launch {
            settingsRepository.saveByokConfig(baseUrl, apiKey, modelName)
            settingsRepository.setUseDefaultFreeAi(false)
            val label = modelName.trim().ifBlank { "Custom BYOK" }
            _events.tryEmit(IdeUiEvent.ShowMessage("Activated BYOK • $label"))
        }
    }

    fun activatePollinationsDefault() {
        viewModelScope.launch {
            settingsRepository.setUseDefaultFreeAi(true)
            _events.tryEmit(IdeUiEvent.ShowMessage("Switched to Pollinations.ai (Free Default)"))
        }
    }

    fun resetAiSettingsToDefault() {
        viewModelScope.launch {
            settingsRepository.clearByokConfig()
            _events.tryEmit(IdeUiEvent.ShowMessage("Reset to Pollinations.ai free default"))
        }
    }

    fun saveGithubPat(token: String) {
        viewModelScope.launch {
            settingsRepository.saveGithubPat(token)
            val msg = if (token.isBlank()) "GitHub PAT cleared" else "GitHub PAT saved securely"
            _events.tryEmit(IdeUiEvent.ShowMessage(msg))
        }
    }

    fun clearGithubPat() {
        viewModelScope.launch {
            settingsRepository.clearGithubPat()
            _events.tryEmit(IdeUiEvent.ShowMessage("GitHub PAT cleared"))
        }
    }

    companion object {
        private const val MAX_CONSOLE_LOGS = 300
    }
}
