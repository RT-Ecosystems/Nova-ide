package com.example.core.chat

import com.example.domain.model.ChatMessage
import com.example.domain.model.ChatSessionSummary
import com.example.ui.viewmodel.ChatMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Coroutines repository wrapping ChatHistoryDatabaseHelper for persistent multi-session chat history.
 */
class ChatHistoryRepository(
    private val dbHelper: ChatHistoryDatabaseHelper
) {
    private val _sessions = MutableStateFlow<List<ChatSessionSummary>>(emptyList())
    val sessions: StateFlow<List<ChatSessionSummary>> = _sessions.asStateFlow()

    suspend fun refreshSessions(): List<ChatSessionSummary> = withContext(Dispatchers.IO) {
        val list = dbHelper.getAllSessions()
        _sessions.value = list
        list
    }

    suspend fun createSession(
        title: String,
        mode: ChatMode,
        projectName: String?
    ): ChatSessionSummary = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val session = ChatSessionSummary(
            id = UUID.randomUUID().toString(),
            title = title.trim().ifBlank { "New Chat" },
            mode = mode,
            projectName = projectName,
            createdAt = now,
            updatedAt = now
        )
        dbHelper.upsertSession(session)
        _sessions.value = dbHelper.getAllSessions()
        session
    }

    suspend fun saveMessage(
        sessionId: String,
        message: ChatMessage,
        mode: ChatMode,
        projectName: String?
    ) = withContext(Dispatchers.IO) {
        dbHelper.upsertMessage(sessionId, message)
        dbHelper.touchSession(
            sessionId = sessionId,
            mode = mode,
            projectName = projectName,
            updatedAt = System.currentTimeMillis()
        )
        _sessions.value = dbHelper.getAllSessions()
    }

    suspend fun loadMessages(sessionId: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        dbHelper.getMessagesForSession(sessionId)
    }

    suspend fun renameSession(sessionId: String, newTitle: String) = withContext(Dispatchers.IO) {
        val cleaned = newTitle.trim().take(64)
        if (cleaned.isEmpty()) return@withContext
        dbHelper.updateSessionTitle(sessionId, cleaned, System.currentTimeMillis())
        _sessions.value = dbHelper.getAllSessions()
    }

    suspend fun deleteMessage(messageId: String) = withContext(Dispatchers.IO) {
        dbHelper.deleteMessage(messageId)
    }

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        dbHelper.deleteSession(sessionId)
        _sessions.value = dbHelper.getAllSessions()
    }

    fun generateTitleFromPrompt(prompt: String): String {
        val singleLine = prompt
            .replace(Regex("\\s+"), " ")
            .trim()
        if (singleLine.isEmpty()) return "New Chat"
        return if (singleLine.length <= 44) {
            singleLine
        } else {
            singleLine.take(41).trimEnd() + "..."
        }
    }
}
