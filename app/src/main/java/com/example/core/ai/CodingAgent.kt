package com.example.core.ai

import com.example.core.files.WorkspaceFileManager
import com.example.domain.model.AppliedChangeSet
import com.example.domain.model.ChangeReviewStatus
import com.example.domain.model.ConsoleLogEntry
import com.example.domain.model.FileBackupEntry
import com.example.domain.model.IdeSettings
import com.example.domain.model.WebSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

data class ParsedFileWrite(
    val relativePath: String,
    val content: String
)

data class CodingAgentOutput(
    val summaryText: String,
    val planText: String? = null,
    val changeSet: AppliedChangeSet? = null,
    val searchSources: List<WebSearchResult> = emptyList()
)

/**
 * Multi-mode AI Coding Agent powering Chat, Builder, Pipeline, and 1-Tap Console Auto-Fix.
 * Parses structured <<<FILE: path>>> ... <<<END_FILE>>> blocks and writes safely into /projects/
 * while retaining in-memory backups for instant Keep All / Revert review.
 */
class CodingAgent(
    private val aiClient: AiClient,
    private val webSearchTool: WebSearchTool,
    private val fileManager: WorkspaceFileManager
) {

    suspend fun runChatMode(
        prompt: String,
        history: List<AiChatTurn>,
        attachedFileContents: Map<String, String>,
        settings: IdeSettings,
        forceWebSearch: Boolean,
        memoryContext: String = "",
        onStatus: (String) -> Unit
    ): Result<CodingAgentOutput> {
        val searchResults = maybeSearchWeb(prompt, forceWebSearch, onStatus)

        val systemBuilder = StringBuilder().apply {
            appendLine("You are Nova AI, an expert senior software engineer inside Nova IDE on Android.")
            appendLine("Give crisp, accurate, practical developer answers with clean fenced markdown code blocks when helpful.")
            if (memoryContext.isNotBlank()) {
                appendLine()
                appendLine(memoryContext)
            }
            if (searchResults.isNotEmpty()) {
                appendLine()
                appendLine(webSearchTool.formatResultsForSystemPrompt(prompt, searchResults))
            }
            if (attachedFileContents.isNotEmpty()) {
                appendLine()
                appendLine("USER ATTACHED WORKSPACE FILES:")
                attachedFileContents.forEach { (path, content) ->
                    appendLine("--- $path ---")
                    appendLine(content)
                }
            }
        }

        val turns = buildList {
            add(AiChatTurn("system", systemBuilder.toString()))
            addAll(history.takeLast(8))
            add(AiChatTurn("user", prompt))
        }

        onStatus("✦ Thinking...")
        return aiClient.chatCompletion(settings = settings, messages = turns, temperature = 0.5)
            .map { reply ->
                CodingAgentOutput(
                    summaryText = reply,
                    searchSources = searchResults
                )
            }
    }

    suspend fun runBuilderMode(
        prompt: String,
        targetProjectDir: File,
        attachedFileContents: Map<String, String>,
        settings: IdeSettings,
        forceWebSearch: Boolean,
        existingPlan: String? = null,
        memoryContext: String = "",
        onStatus: (String) -> Unit
    ): Result<CodingAgentOutput> {
        val searchResults = maybeSearchWeb(prompt, forceWebSearch, onStatus)

        onStatus("📂 Reading ${targetProjectDir.name} context...")
        val projectSnapshot = fileManager.readProjectContextSnapshot(targetProjectDir)
        val mergedContext = LinkedHashMap<String, String>().apply {
            putAll(projectSnapshot)
            putAll(attachedFileContents)
        }

        val systemPrompt = buildBuilderSystemPrompt(
            projectName = targetProjectDir.name,
            contextFiles = mergedContext,
            searchResults = searchResults,
            query = prompt,
            architecturePlan = existingPlan,
            memoryContext = memoryContext
        )

        onStatus("⚡ Generating code for ${targetProjectDir.name}...")
        val completion = aiClient.chatCompletion(
            settings = settings,
            messages = listOf(
                AiChatTurn("system", systemPrompt),
                AiChatTurn("user", prompt)
            ),
            temperature = 0.35
        )

        return completion.mapCatching { rawResponse ->
            val fileWrites = parseFileBlocks(rawResponse)
            val summary = stripFileBlocks(rawResponse).ifBlank {
                if (fileWrites.isNotEmpty()) {
                    "Applied ${fileWrites.size} file ${if (fileWrites.size == 1) "update" else "updates"} in /projects/${targetProjectDir.name}."
                } else {
                    rawResponse
                }
            }

            val changeSet = if (fileWrites.isNotEmpty()) {
                applyFileWritesWithBackup(
                    projectDir = targetProjectDir,
                    writes = fileWrites,
                    onStatus = onStatus
                )
            } else null

            CodingAgentOutput(
                summaryText = summary,
                planText = existingPlan,
                changeSet = changeSet,
                searchSources = searchResults
            )
        }
    }

    suspend fun runPipelineMode(
        prompt: String,
        targetProjectDir: File,
        attachedFileContents: Map<String, String>,
        settings: IdeSettings,
        forceWebSearch: Boolean,
        memoryContext: String = "",
        onStatus: (String) -> Unit,
        onPlanReady: (String) -> Unit
    ): Result<CodingAgentOutput> {
        val searchResults = maybeSearchWeb(prompt, forceWebSearch, onStatus)

        onStatus("🧠 Step 1/2: Creating architecture plan...")
        val projectSnapshot = fileManager.readProjectContextSnapshot(targetProjectDir)
        val planSystem = buildString {
            appendLine("You are Nova AI Pipeline Architect.")
            appendLine("Create a concise, numbered step-by-step architecture plan (3 to 6 steps) listing the exact files to create or modify inside project \"${targetProjectDir.name}\".")
            appendLine("Do NOT write full code yet — only the crisp implementation plan.")
            if (memoryContext.isNotBlank()) {
                appendLine()
                appendLine(memoryContext)
            }
            if (projectSnapshot.isNotEmpty()) {
                appendLine("Existing files: ${projectSnapshot.keys.joinToString(", ")}")
            }
            if (searchResults.isNotEmpty()) {
                appendLine()
                appendLine(webSearchTool.formatResultsForSystemPrompt(prompt, searchResults))
            }
        }

        val planResult = aiClient.chatCompletion(
            settings = settings,
            messages = listOf(
                AiChatTurn("system", planSystem),
                AiChatTurn("user", prompt)
            ),
            temperature = 0.3
        )

        val planText = planResult.getOrElse { return Result.failure(it) }
        onPlanReady(planText)

        onStatus("⚡ Step 2/2: Executing build plan...")
        return runBuilderMode(
            prompt = prompt,
            targetProjectDir = targetProjectDir,
            attachedFileContents = attachedFileContents,
            settings = settings,
            forceWebSearch = false,
            existingPlan = planText,
            memoryContext = memoryContext,
            onStatus = onStatus
        ).map { output ->
            output.copy(
                planText = planText,
                searchSources = searchResults
            )
        }
    }

    suspend fun autoFixConsoleErrors(
        targetProjectDir: File,
        consoleLogs: List<ConsoleLogEntry>,
        settings: IdeSettings,
        memoryContext: String = "",
        onStatus: (String) -> Unit
    ): Result<CodingAgentOutput> {
        onStatus("🔍 Inspecting errors in ${targetProjectDir.name}...")
        val projectSnapshot = fileManager.readProjectContextSnapshot(targetProjectDir, maxFiles = 8)

        val errorReport = consoleLogs
            .takeLast(20)
            .joinToString("\n") { entry ->
                "[${entry.level.label}] ${entry.source}:${entry.lineNumber} -> ${entry.message}"
            }

        val systemPrompt = buildString {
            appendLine("You are Nova AI Auto-Fix Agent inside Nova IDE.")
            appendLine("Fix ALL JavaScript, HTML, CSS, and runtime errors reported by the Live Preview WebView console.")
            appendLine("OUTPUT FORMAT (STRICT): Output every fixed file in full using:")
            appendLine("<<<FILE: relative/path/filename.ext>>>")
            appendLine("<complete corrected file content>")
            appendLine("<<<END_FILE>>>")
            appendLine("Outside the file blocks, write a brief 1–2 sentence explanation of what was fixed.")
            appendLine("Do NOT truncate code or leave placeholders.")
            if (memoryContext.isNotBlank()) {
                appendLine()
                appendLine(memoryContext)
            }
            appendLine()
            appendLine("CURRENT PROJECT FILES (${targetProjectDir.name}):")
            projectSnapshot.forEach { (path, content) ->
                appendLine("--- $path ---")
                appendLine(content)
            }
        }

        val userPrompt = "Fix these runtime/console errors from Live Preview:\n\n$errorReport"

        onStatus("✨ Auto-fixing with AI...")
        val completion = aiClient.chatCompletion(
            settings = settings,
            messages = listOf(
                AiChatTurn("system", systemPrompt),
                AiChatTurn("user", userPrompt)
            ),
            temperature = 0.2
        )

        return completion.mapCatching { rawResponse ->
            val fileWrites = parseFileBlocks(rawResponse)
            if (fileWrites.isEmpty()) {
                error("AI analyzed the error but did not output any <<<FILE: ...>>> patches. Try again or inspect the explanation in Chat.")
            }
            val changeSet = applyFileWritesWithBackup(
                projectDir = targetProjectDir,
                writes = fileWrites,
                onStatus = onStatus
            )
            val summary = stripFileBlocks(rawResponse).ifBlank {
                "Auto-fixed ${fileWrites.size} file(s) in ${targetProjectDir.name} and reloaded Preview."
            }
            CodingAgentOutput(
                summaryText = summary,
                changeSet = changeSet
            )
        }
    }

    suspend fun autoFixJavaCompileErrors(
        targetProjectDir: File,
        compilerErrorLog: String,
        settings: IdeSettings,
        memoryContext: String = "",
        onStatus: (String) -> Unit
    ): Result<CodingAgentOutput> {
        onStatus("🔍 Reading Java sources in ${targetProjectDir.name}...")
        val projectSnapshot = fileManager.readProjectContextSnapshot(targetProjectDir, maxFiles = 10)

        val systemPrompt = buildString {
            appendLine("You are Nova AI Java Compiler Auto-Fix Agent inside Nova IDE.")
            appendLine("Fix ALL Java 8 / Android SDK compilation errors reported by the Eclipse ECJ compiler.")
            appendLine("Target SDK: standard Android framework classes (`android.app.Activity`, `android.widget.*`, `android.os.Bundle`, Java 8).")
            appendLine("OUTPUT FORMAT (STRICT): Output every fixed file in full using:")
            appendLine("<<<FILE: relative/path/to/File.java>>>")
            appendLine("<complete corrected Java source>")
            appendLine("<<<END_FILE>>>")
            appendLine("Outside the file blocks, write a concise 1–2 sentence summary of the Java fix.")
            appendLine("Never truncate code or use placeholders.")
            if (memoryContext.isNotBlank()) {
                appendLine()
                appendLine(memoryContext)
            }
            appendLine()
            appendLine("CURRENT PROJECT FILES (${targetProjectDir.name}):")
            projectSnapshot.forEach { (path, content) ->
                appendLine("--- $path ---")
                appendLine(content)
            }
        }

        val userPrompt = "Fix these Eclipse ECJ Java compiler errors:\n\n${compilerErrorLog.take(6_000)}"

        onStatus("✨ Auto-fixing Java errors with AI...")
        val completion = aiClient.chatCompletion(
            settings = settings,
            messages = listOf(
                AiChatTurn("system", systemPrompt),
                AiChatTurn("user", userPrompt)
            ),
            temperature = 0.2
        )

        return completion.mapCatching { rawResponse ->
            val fileWrites = parseFileBlocks(rawResponse)
            if (fileWrites.isEmpty()) {
                error("AI analyzed the compiler error but did not output any <<<FILE: ...>>> patches.")
            }
            val changeSet = applyFileWritesWithBackup(
                projectDir = targetProjectDir,
                writes = fileWrites,
                onStatus = onStatus
            )
            val summary = stripFileBlocks(rawResponse).ifBlank {
                "Auto-fixed ${fileWrites.size} Java source file(s) in ${targetProjectDir.name}."
            }
            CodingAgentOutput(
                summaryText = summary,
                changeSet = changeSet
            )
        }
    }

    private suspend fun maybeSearchWeb(
        prompt: String,
        forceWebSearch: Boolean,
        onStatus: (String) -> Unit
    ): List<WebSearchResult> {
        if (!forceWebSearch && !webSearchTool.shouldAutoTriggerSearch(prompt)) {
            return emptyList()
        }
        onStatus("🌐 Searching web...")
        return webSearchTool.search(prompt, maxResults = 5).getOrDefault(emptyList())
    }

    private fun buildBuilderSystemPrompt(
        projectName: String,
        contextFiles: Map<String, String>,
        searchResults: List<WebSearchResult>,
        query: String,
        architecturePlan: String?,
        memoryContext: String = ""
    ): String = buildString {
        appendLine("You are Nova AI Builder, a world-class full-stack & web developer inside Nova IDE.")
        appendLine("Target project folder: /projects/$projectName/")
        if (memoryContext.isNotBlank()) {
            appendLine()
            appendLine(memoryContext)
        }
        appendLine()
        appendLine("STRICT FILE OUTPUT PROTOCOL:")
        appendLine("For EVERY file you create or update, output its COMPLETE content wrapped in exact markers:")
        appendLine("<<<FILE: relative/path/to/file.ext>>>")
        appendLine("...full complete code with zero placeholders...")
        appendLine("<<<END_FILE>>>")
        appendLine()
        appendLine("RULES:")
        appendLine("1. Always output complete, runnable files (e.g., index.html, styles.css, app.js). Never use '// rest of code' or TODOs.")
        appendLine("2. IMAGE RULE: Whenever building a website or UI that needs images, ALWAYS use https://image.pollinations.ai/prompt/<url-encoded-description> in <img src=\"...\"> tags so real AI-generated images load automatically!")
        appendLine("3. Use responsive, modern, polished visual design that looks great on mobile and desktop screens.")
        appendLine("4. Outside the <<<FILE>>> blocks, write only a concise 2–3 sentence summary of what you built.")
        if (!architecturePlan.isNullOrBlank()) {
            appendLine()
            appendLine("FOLLOW THIS APPROVED ARCHITECTURE PLAN:")
            appendLine(architecturePlan)
        }
        if (searchResults.isNotEmpty()) {
            appendLine()
            appendLine(webSearchTool.formatResultsForSystemPrompt(query, searchResults))
        }
        if (contextFiles.isNotEmpty()) {
            appendLine()
            appendLine("EXISTING WORKSPACE FILES FOR REFERENCE:")
            contextFiles.forEach { (path, content) ->
                appendLine("--- $path ---")
                appendLine(content)
            }
        }
    }

    fun parseFileBlocks(rawResponse: String): List<ParsedFileWrite> {
        val pattern = Regex(
            pattern = "<<<FILE:\\s*([^>\\r\\n]+?)\\s*>>>\\s*\\r?\\n([\\s\\S]*?)\\r?\\n?<<<END_FILE>>>",
            options = setOf(RegexOption.IGNORE_CASE)
        )
        return pattern.findAll(rawResponse).mapNotNull { match ->
            val rawPath = match.groupValues[1].trim()
                .removePrefix("./")
                .removePrefix("/")
                .replace('\\', '/')
            if (rawPath.isBlank() || rawPath.contains("..")) return@mapNotNull null

            val body = stripOuterMarkdownFence(match.groupValues[2])
            ParsedFileWrite(relativePath = rawPath, content = body)
        }.toList()
    }

    private fun stripFileBlocks(rawResponse: String): String {
        val pattern = Regex(
            pattern = "<<<FILE:\\s*[^>\\r\\n]+?\\s*>>>[\\s\\S]*?<<<END_FILE>>>",
            options = setOf(RegexOption.IGNORE_CASE)
        )
        return pattern.replace(rawResponse, "").replace(Regex("\\n{3,}"), "\n\n").trim()
    }

    private fun stripOuterMarkdownFence(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
            val firstNewline = trimmed.indexOf('\n')
            if (firstNewline != -1 && trimmed.length > firstNewline + 4) {
                return trimmed.substring(firstNewline + 1, trimmed.length - 3).trimEnd()
            }
        }
        return raw.trimEnd()
    }

    private suspend fun applyFileWritesWithBackup(
        projectDir: File,
        writes: List<ParsedFileWrite>,
        onStatus: (String) -> Unit
    ): AppliedChangeSet = withContext(Dispatchers.IO) {
        val projectCanonical = projectDir.canonicalPath
        val prefix = if (projectCanonical.endsWith(File.separator)) projectCanonical else projectCanonical + File.separator
        val backups = mutableListOf<FileBackupEntry>()

        for (write in writes) {
            val targetFile = File(projectDir, write.relativePath)
            val targetCanonical = targetFile.canonicalPath
            if (!targetCanonical.startsWith(prefix)) continue

            onStatus("⚡ Writing ${targetFile.name}...")

            val existedBefore = targetFile.exists() && targetFile.isFile
            val previousContent = if (existedBefore) {
                runCatching { targetFile.readText(StandardCharsets.UTF_8) }.getOrNull()
            } else null

            targetFile.parentFile?.mkdirs()
            targetFile.writeText(write.content, StandardCharsets.UTF_8)

            backups.add(
                FileBackupEntry(
                    relativePathInProject = write.relativePath,
                    workspaceRelativePath = fileManager.relativePathOf(targetFile),
                    absolutePath = targetCanonical,
                    previousContent = previousContent,
                    existedBefore = existedBefore
                )
            )
        }

        AppliedChangeSet(
            id = UUID.randomUUID().toString(),
            projectName = projectDir.name,
            projectAbsolutePath = projectCanonical,
            files = backups,
            status = ChangeReviewStatus.PENDING
        )
    }
}
