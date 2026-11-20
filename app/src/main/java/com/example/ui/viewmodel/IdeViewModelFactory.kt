package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.core.ai.AiClient
import com.example.core.ai.CodingAgent
import com.example.core.ai.WebSearchTool
import com.example.core.builder.WebToApkBuilder
import com.example.core.chat.ChatHistoryDatabaseHelper
import com.example.core.chat.ChatHistoryRepository
import com.example.core.compiler.ApkPackagerEngine
import com.example.core.compiler.ApkSignerEngine
import com.example.core.compiler.DexCompilerEngine
import com.example.core.compiler.JavaCompilerEngine
import com.example.core.compiler.KeystoreManager
import com.example.core.compiler.ToolchainManager
import com.example.core.files.WorkspaceFileManager
import com.example.core.git.JGitManager
import com.example.core.memory.MemoryDatabaseHelper
import com.example.core.memory.MemoryRepository
import com.example.core.server.ServerManager
import com.example.core.voice.EdgeTtsEngine
import com.example.data.SettingsRepository

class IdeViewModelFactory(
    private val appContext: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(IdeViewModel::class.java)) {
            val fileManager = WorkspaceFileManager(appContext)
            val gitManager = JGitManager(fileManager)
            val settingsRepository = SettingsRepository(appContext)
            val serverManager = ServerManager()
            val aiClient = AiClient()
            val webSearchTool = WebSearchTool()
            val codingAgent = CodingAgent(
                aiClient = aiClient,
                webSearchTool = webSearchTool,
                fileManager = fileManager
            )
            val memoryDbHelper = MemoryDatabaseHelper(appContext)
            val memoryRepository = MemoryRepository(memoryDbHelper)
            val edgeTtsEngine = EdgeTtsEngine(appContext)
            val chatHistoryDbHelper = ChatHistoryDatabaseHelper(appContext)
            val chatHistoryRepository = ChatHistoryRepository(chatHistoryDbHelper)
            val webToApkBuilder = WebToApkBuilder(appContext)
            val toolchainManager = ToolchainManager(appContext)
            val javaCompilerEngine = JavaCompilerEngine(toolchainManager)
            val dexCompilerEngine = DexCompilerEngine()
            val apkPackagerEngine = ApkPackagerEngine(toolchainManager)
            val keystoreManager = KeystoreManager(appContext)
            val apkSignerEngine = ApkSignerEngine(keystoreManager, webToApkBuilder)

            return IdeViewModel(
                fileManager = fileManager,
                gitManager = gitManager,
                settingsRepository = settingsRepository,
                serverManager = serverManager,
                codingAgent = codingAgent,
                memoryRepository = memoryRepository,
                edgeTtsEngine = edgeTtsEngine,
                chatHistoryRepository = chatHistoryRepository,
                webToApkBuilder = webToApkBuilder,
                toolchainManager = toolchainManager,
                javaCompilerEngine = javaCompilerEngine,
                dexCompilerEngine = dexCompilerEngine,
                apkPackagerEngine = apkPackagerEngine,
                keystoreManager = keystoreManager,
                apkSignerEngine = apkSignerEngine
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
