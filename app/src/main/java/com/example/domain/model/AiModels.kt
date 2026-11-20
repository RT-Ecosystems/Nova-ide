package com.example.domain.model

import com.example.ui.viewmodel.ChatMode

enum class ChatRole {
    USER,
    ASSISTANT
}

data class WebSearchResult(
    val title: String,
    val snippet: String,
    val url: String
)

enum class ChangeReviewStatus {
    PENDING,
    KEPT,
    REVERTED
}

data class FileBackupEntry(
    val relativePathInProject: String,
    val workspaceRelativePath: String,
    val absolutePath: String,
    val previousContent: String?,
    val existedBefore: Boolean
)

data class AppliedChangeSet(
    val id: String,
    val projectName: String,
    val projectAbsolutePath: String,
    val files: List<FileBackupEntry>,
    val status: ChangeReviewStatus = ChangeReviewStatus.PENDING
)

enum class MessageFeedback {
    NONE,
    LIKED,
    DISLIKED
}

data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val mode: ChatMode,
    val content: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val attachedFiles: List<String> = emptyList(),
    val searchSources: List<WebSearchResult> = emptyList(),
    val pipelinePlan: String? = null,
    val changeSet: AppliedChangeSet? = null,
    val isError: Boolean = false,
    val feedback: MessageFeedback = MessageFeedback.NONE,
    val sourcePrompt: String? = null
)

data class AiWorkStatus(
    val isWorking: Boolean = false,
    val label: String = ""
)
