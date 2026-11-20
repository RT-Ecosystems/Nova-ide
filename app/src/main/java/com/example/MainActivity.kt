package com.example

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.components.IdeBottomBar
import com.example.ui.components.IdeNavigationDrawer
import com.example.ui.components.ModelSelectorBottomSheet
import com.example.ui.components.VoiceSettingsBottomSheet
import com.example.ui.navigation.IdeDestination
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.FilesScreen
import com.example.ui.screens.PreviewScreen
import com.example.ui.screens.ToolsScreen
import com.example.ui.screens.WebToApkScreen
import com.example.domain.model.ProjectTemplate
import com.example.ui.theme.BorderLight
import com.example.ui.theme.BrandIndigo
import com.example.ui.theme.NovaIdeTheme
import com.example.ui.theme.SurfaceBackground
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.viewmodel.IdeUiEvent
import com.example.ui.viewmodel.IdeViewModel
import com.example.ui.viewmodel.IdeViewModelFactory
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT
            )
        )
        setContent {
            NovaIdeTheme {
                NovaIdeRootApp()
            }
        }
    }
}

@Composable
fun NovaIdeRootApp() {
    val context = LocalContext.current.applicationContext
    val viewModel: IdeViewModel = viewModel(
        factory = remember(context) { IdeViewModelFactory(context) }
    )

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    var showDrawerVoiceSheet by remember { mutableStateOf(false) }
    var showDrawerModelSheet by remember { mutableStateOf(false) }

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val chatSessions by viewModel.chatSessions.collectAsStateWithLifecycle()
    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()
    val selectedChatMode by viewModel.selectedChatMode.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val aiWorkStatus by viewModel.aiWorkStatus.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()
    val webSearchEnabled by viewModel.webSearchEnabled.collectAsStateWithLifecycle()
    val attachedContextFiles by viewModel.attachedContextFiles.collectAsStateWithLifecycle()
    val attachableWorkspaceFiles by viewModel.attachableWorkspaceFiles.collectAsStateWithLifecycle()
    val rootProjectNames by viewModel.rootProjectNames.collectAsStateWithLifecycle()
    val rootProjectFolders by viewModel.rootProjectFolders.collectAsStateWithLifecycle()
    val activeAiProjectName by viewModel.activeAiProjectName.collectAsStateWithLifecycle()
    val isAutoFixing by viewModel.isAutoFixing.collectAsStateWithLifecycle()
    val webToApkBuildState by viewModel.webToApkBuildState.collectAsStateWithLifecycle()
    val recentExports by viewModel.recentExports.collectAsStateWithLifecycle()
    val toolchainStatus by viewModel.toolchainStatus.collectAsStateWithLifecycle()
    val keystoreInfo by viewModel.keystoreInfo.collectAsStateWithLifecycle()
    val javaProjects by viewModel.javaProjects.collectAsStateWithLifecycle()
    val javaCompileState by viewModel.javaCompileState.collectAsStateWithLifecycle()
    val filesState by viewModel.filesState.collectAsStateWithLifecycle()
    val editorState by viewModel.editorState.collectAsStateWithLifecycle()
    val serverState by viewModel.serverState.collectAsStateWithLifecycle()
    val previewProjects by viewModel.previewProjects.collectAsStateWithLifecycle()
    val consoleLogs by viewModel.consoleLogs.collectAsStateWithLifecycle()
    val gitRepos by viewModel.gitRepos.collectAsStateWithLifecycle()
    val cloneProgress by viewModel.cloneProgress.collectAsStateWithLifecycle()
    val pushState by viewModel.pushState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is IdeUiEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        duration = SnackbarDuration.Short
                    )
                }
                is IdeUiEvent.NavigateToTab -> {
                    navigateToTab(event.route)
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = currentRoute != IdeDestination.Editor.route,
        drawerContent = {
            IdeNavigationDrawer(
                sessions = chatSessions,
                activeSessionId = activeSessionId,
                currentRoute = currentRoute,
                isAiWorking = aiWorkStatus.isWorking,
                onNewChat = {
                    coroutineScope.launch { drawerState.close() }
                    viewModel.startNewChatSession()
                    navigateToTab(IdeDestination.Chat.route)
                },
                onSelectSession = { sessionId ->
                    coroutineScope.launch { drawerState.close() }
                    viewModel.selectChatSession(sessionId)
                },
                onRenameSession = viewModel::renameChatSession,
                onDeleteSession = viewModel::deleteChatSession,
                onOpenWebToApk = {
                    coroutineScope.launch { drawerState.close() }
                    viewModel.refreshWorkspace()
                    viewModel.refreshRecentExports()
                    viewModel.refreshToolchainStatus()
                    navigateToTab(IdeDestination.Web2Apk.route)
                },
                onOpenWorkspaceFiles = {
                    coroutineScope.launch { drawerState.close() }
                    viewModel.refreshWorkspace()
                    navigateToTab(IdeDestination.Files.route)
                },
                onOpenMemoryManager = {
                    coroutineScope.launch { drawerState.close() }
                    viewModel.refreshMemories()
                    viewModel.refreshGitRepos()
                    viewModel.refreshWorkspace()
                    navigateToTab(IdeDestination.Tools.route)
                },
                onOpenVoiceSettings = {
                    coroutineScope.launch { drawerState.close() }
                    showDrawerVoiceSheet = true
                },
                onOpenApiSettings = {
                    coroutineScope.launch { drawerState.close() }
                    showDrawerModelSheet = true
                }
            )
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = SurfaceBackground,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    Surface(
                        color = SurfaceCard,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, BorderLight),
                        shadowElevation = 6.dp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Snackbar(
                            snackbarData = data,
                            containerColor = SurfaceCard,
                            contentColor = TextPrimary,
                            actionColor = BrandIndigo
                        )
                    }
                }
            },
            bottomBar = {
                IdeBottomBar(
                    currentRoute = currentRoute,
                    hasUnsavedEditorChanges = editorState.isDirty,
                    onSelectDestination = { destination ->
                        when (destination) {
                            IdeDestination.Tools -> {
                                viewModel.refreshGitRepos()
                                viewModel.refreshWorkspace()
                                viewModel.refreshMemories()
                                viewModel.refreshToolchainStatus()
                            }
                            IdeDestination.Web2Apk -> {
                                viewModel.refreshWorkspace()
                                viewModel.refreshRecentExports()
                                viewModel.refreshToolchainStatus()
                            }
                            IdeDestination.Files, IdeDestination.Preview -> {
                                viewModel.refreshWorkspace()
                            }
                            else -> Unit
                        }
                        navigateToTab(destination.route)
                    }
                )
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = IdeDestination.Chat.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                composable(IdeDestination.Chat.route) {
                    ChatScreen(
                        selectedMode = selectedChatMode,
                        settings = settings,
                        messages = chatMessages,
                        aiWorkStatus = aiWorkStatus,
                        ttsState = ttsState,
                        webSearchEnabled = webSearchEnabled,
                        attachedFiles = attachedContextFiles,
                        attachableFiles = attachableWorkspaceFiles,
                        rootProjectNames = rootProjectNames,
                        activeAiProjectName = activeAiProjectName,
                        onSelectMode = viewModel::selectChatMode,
                        onToggleWebSearch = viewModel::toggleWebSearch,
                        onToggleAutoSpeak = viewModel::toggleAutoSpeak,
                        onSetAutoSpeak = viewModel::setAutoSpeakEnabled,
                        onSelectVoice = viewModel::setSelectedVoice,
                        onSelectTtsSpeed = viewModel::setTtsSpeed,
                        onTestVoice = viewModel::testActiveVoice,
                        onStopSpeaking = viewModel::stopSpeaking,
                        onSelectActiveAiProject = viewModel::selectActiveAiProject,
                        onRefreshAttachableFiles = viewModel::refreshAttachableFiles,
                        onToggleAttachFile = viewModel::toggleAttachContextFile,
                        onRemoveAttachedFile = viewModel::removeAttachedContextFile,
                        onClearChat = viewModel::clearChatHistory,
                        onSubmitPrompt = viewModel::sendChatPrompt,
                        onKeepChangeSet = viewModel::keepChangeSet,
                        onRevertChangeSet = viewModel::revertChangeSet,
                        onOpenFileInEditor = viewModel::openFileByAbsolutePath,
                        onRunPreview = viewModel::runProjectPreviewByPath,
                        onToggleSpeakMessage = viewModel::toggleSpeakMessage,
                        onRegenerateMessage = viewModel::regenerateMessage,
                        onFeedbackClick = viewModel::toggleMessageFeedback,
                        onSaveToMemory = { text -> viewModel.addMemory(text) },
                        onDeleteMessage = viewModel::deleteChatMessage,
                        onShowToast = viewModel::showToastMessage,
                        onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                        onOpenWorkspace = { navigateToTab(IdeDestination.Files.route) },
                        onSelectPollinationsDefault = viewModel::activatePollinationsDefault,
                        onSaveAndActivateByok = viewModel::saveAndActivateByok
                    )
                }

                composable(IdeDestination.Files.route) {
                    FilesScreen(
                        state = filesState,
                        cloneProgress = cloneProgress,
                        hasGithubPat = settings.hasGithubPat,
                        onNavigateDirectory = viewModel::navigateToDirectory,
                        onNavigateRoot = viewModel::navigateToRoot,
                        onNavigateUp = viewModel::navigateUpDirectory,
                        onOpenFile = viewModel::openFileInEditor,
                        onCreateProject = viewModel::createProject,
                        onImportFromGithub = viewModel::cloneRepository,
                        onCreateEntry = viewModel::createEntry,
                        onRenameEntry = viewModel::renameEntry,
                        onDeleteEntry = viewModel::deleteEntry,
                        onRefresh = { viewModel.refreshWorkspace() }
                    )
                }

                composable(IdeDestination.Editor.route) {
                    EditorScreen(
                        state = editorState,
                        onContentChanged = viewModel::onEditorContentChanged,
                        onCursorChanged = viewModel::onEditorCursorChanged,
                        onSaveClick = viewModel::saveActiveFile,
                        onRunPreviewClick = viewModel::runActiveEditorFileInPreview,
                        onCycleFontSize = viewModel::cycleEditorFontSize,
                        onBrowseFilesClick = { navigateToTab(IdeDestination.Files.route) }
                    )
                }

                composable(IdeDestination.Preview.route) {
                    PreviewScreen(
                        serverState = serverState,
                        previewProjects = previewProjects,
                        consoleLogs = consoleLogs,
                        isAutoFixing = isAutoFixing,
                        onSelectProjectAndStart = { option ->
                            viewModel.startPreviewServer(option.projectPath)
                        },
                        onToggleServer = viewModel::stopPreviewServer,
                        onReloadPreview = viewModel::reloadPreview,
                        onConsoleLog = viewModel::appendConsoleLog,
                        onClearConsoleLogs = viewModel::clearConsoleLogs,
                        onAutoFixClick = viewModel::autoFixConsoleErrors,
                        onBrowseFilesClick = { navigateToTab(IdeDestination.Files.route) }
                    )
                }

                composable(IdeDestination.Web2Apk.route) {
                    WebToApkScreen(
                        rootProjectFolders = rootProjectFolders,
                        buildState = webToApkBuildState,
                        recentExports = recentExports,
                        toolchainStatus = toolchainStatus,
                        keystoreInfo = keystoreInfo,
                        javaProjects = javaProjects,
                        javaCompileState = javaCompileState,
                        onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                        onCalculateProjectSize = viewModel::calculateProjectSizeBytes,
                        onBuildAndroidZip = viewModel::buildAndroidWebViewZip,
                        onBuildPwaZip = viewModel::buildStandalonePwaZip,
                        onDeleteExport = viewModel::deleteExportedArtifact,
                        onGetShareUri = viewModel::getExportShareUri,
                        onImportAndroidJar = viewModel::importAndroidJar,
                        onOpenToolchainSettings = {
                            viewModel.refreshToolchainStatus()
                            navigateToTab(IdeDestination.Tools.route)
                        },
                        onCreateSampleJavaProject = {
                            val existingNames = rootProjectNames.toSet()
                            var candidate = "nova-java-app"
                            var suffix = 2
                            while (candidate in existingNames) {
                                candidate = "nova-java-app-$suffix"
                                suffix++
                            }
                            viewModel.createProject(candidate, ProjectTemplate.ANDROID_JAVA_APP)
                        },
                        onCompileJavaProject = viewModel::compileJavaProject,
                        onBuildSignedApk = viewModel::buildSignedApk,
                        onAutoFixJavaErrors = viewModel::autoFixJavaErrors,
                        onOpenWorkspaceFiles = {
                            viewModel.refreshWorkspace()
                            navigateToTab(IdeDestination.Files.route)
                        },
                        onShowToast = viewModel::showToastMessage
                    )
                }

                composable(IdeDestination.Tools.route) {
                    ToolsScreen(
                        settings = settings,
                        toolchainStatus = toolchainStatus,
                        keystoreInfo = keystoreInfo,
                        serverState = serverState,
                        previewProjects = previewProjects,
                        memories = memories,
                        ttsState = ttsState,
                        gitRepos = gitRepos,
                        cloneProgress = cloneProgress,
                        pushState = pushState,
                        onImportAndroidJar = viewModel::importAndroidJar,
                        onDownloadAndroidJar = viewModel::downloadAndroidJar,
                        onDeleteAndroidJar = viewModel::deleteAndroidJar,
                        onCreateCustomKeystore = viewModel::createCustomKeystore,
                        onResetToDebugKeystore = viewModel::resetToDebugKeystore,
                        onStartServer = viewModel::startPreviewServer,
                        onRestartServer = viewModel::restartPreviewServer,
                        onStopServer = viewModel::stopPreviewServer,
                        onOpenPreviewTab = { navigateToTab(IdeDestination.Preview.route) },
                        onAddMemory = { text -> viewModel.addMemory(text) },
                        onDeleteMemory = viewModel::deleteMemory,
                        onClearAllMemories = viewModel::clearAllMemories,
                        onSetAutoSpeak = viewModel::setAutoSpeakEnabled,
                        onSelectVoice = viewModel::setSelectedVoice,
                        onSelectTtsSpeed = viewModel::setTtsSpeed,
                        onTestVoice = viewModel::testActiveVoice,
                        onStopSpeaking = viewModel::stopSpeaking,
                        onCloneRepo = viewModel::cloneRepository,
                        onRefreshRepos = viewModel::refreshGitRepos,
                        onCommitLocally = viewModel::commitLocally,
                        onCommitAndPush = viewModel::commitAndPush,
                        onOpenClonedRepo = { relPath ->
                            viewModel.navigateToDirectory(relPath)
                            navigateToTab(IdeDestination.Files.route)
                        },
                        onToggleDefaultFreeAi = viewModel::setUseDefaultFreeAi,
                        onSaveByokConfig = viewModel::saveByokSettings,
                        onResetAiConfig = viewModel::resetAiSettingsToDefault,
                        onSaveGithubPat = viewModel::saveGithubPat,
                        onClearGithubPat = viewModel::clearGithubPat
                    )
                }
            }
        }
    }

    if (showDrawerVoiceSheet) {
        VoiceSettingsBottomSheet(
            settings = settings,
            ttsState = ttsState,
            onToggleAutoSpeak = viewModel::setAutoSpeakEnabled,
            onSelectVoice = viewModel::setSelectedVoice,
            onSelectSpeed = viewModel::setTtsSpeed,
            onTestVoice = viewModel::testActiveVoice,
            onStopSpeaking = viewModel::stopSpeaking,
            onDismiss = { showDrawerVoiceSheet = false }
        )
    }

    if (showDrawerModelSheet) {
        ModelSelectorBottomSheet(
            settings = settings,
            onDismiss = { showDrawerModelSheet = false },
            onSelectPollinationsDefault = viewModel::activatePollinationsDefault,
            onSaveAndActivateByok = viewModel::saveAndActivateByok
        )
    }
}
