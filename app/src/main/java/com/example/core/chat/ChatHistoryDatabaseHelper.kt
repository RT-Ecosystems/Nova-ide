package com.example.core.chat

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.domain.model.AppliedChangeSet
import com.example.domain.model.ChangeReviewStatus
import com.example.domain.model.ChatMessage
import com.example.domain.model.ChatRole
import com.example.domain.model.ChatSessionSummary
import com.example.domain.model.FileBackupEntry
import com.example.domain.model.MessageFeedback
import com.example.domain.model.WebSearchResult
import com.example.ui.viewmodel.ChatMode
import org.json.JSONArray
import org.json.JSONObject

/**
 * Native Android SQLiteOpenHelper persisting multi-session chat history and full message metadata
 * without annotation processors (AGP 9 safe).
 */
class ChatHistoryDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_SESSIONS (
                $COL_ID TEXT PRIMARY KEY,
                $COL_TITLE TEXT NOT NULL,
                $COL_MODE TEXT NOT NULL,
                $COL_PROJECT_NAME TEXT,
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_UPDATED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_MESSAGES (
                $COL_ID TEXT PRIMARY KEY,
                $COL_SESSION_ID TEXT NOT NULL,
                $COL_ROLE TEXT NOT NULL,
                $COL_CONTENT TEXT NOT NULL,
                $COL_TIMESTAMP INTEGER NOT NULL,
                $COL_CITATIONS_JSON TEXT,
                $COL_CHANGES_JSON TEXT,
                FOREIGN KEY($COL_SESSION_ID) REFERENCES $TABLE_SESSIONS($COL_ID) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_messages_session ON $TABLE_MESSAGES($COL_SESSION_ID, $COL_TIMESTAMP)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 1) {
            onCreate(db)
        }
    }

    fun getAllSessions(): List<ChatSessionSummary> {
        val sessions = mutableListOf<ChatSessionSummary>()
        readableDatabase.query(
            TABLE_SESSIONS,
            arrayOf(COL_ID, COL_TITLE, COL_MODE, COL_PROJECT_NAME, COL_CREATED_AT, COL_UPDATED_AT),
            null,
            null,
            null,
            null,
            "$COL_UPDATED_AT DESC"
        ).use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(COL_ID)
            val titleIdx = cursor.getColumnIndexOrThrow(COL_TITLE)
            val modeIdx = cursor.getColumnIndexOrThrow(COL_MODE)
            val projectIdx = cursor.getColumnIndexOrThrow(COL_PROJECT_NAME)
            val createdIdx = cursor.getColumnIndexOrThrow(COL_CREATED_AT)
            val updatedIdx = cursor.getColumnIndexOrThrow(COL_UPDATED_AT)

            while (cursor.moveToNext()) {
                val modeStr = cursor.getString(modeIdx)
                val parsedMode = runCatching { ChatMode.valueOf(modeStr) }.getOrDefault(ChatMode.CHAT)
                sessions.add(
                    ChatSessionSummary(
                        id = cursor.getString(idIdx),
                        title = cursor.getString(titleIdx),
                        mode = parsedMode,
                        projectName = cursor.getString(projectIdx),
                        createdAt = cursor.getLong(createdIdx),
                        updatedAt = cursor.getLong(updatedIdx)
                    )
                )
            }
        }
        return sessions
    }

    fun upsertSession(session: ChatSessionSummary) {
        val values = ContentValues().apply {
            put(COL_ID, session.id)
            put(COL_TITLE, session.title)
            put(COL_MODE, session.mode.name)
            put(COL_PROJECT_NAME, session.projectName)
            put(COL_CREATED_AT, session.createdAt)
            put(COL_UPDATED_AT, session.updatedAt)
        }
        writableDatabase.insertWithOnConflict(
            TABLE_SESSIONS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun updateSessionTitle(sessionId: String, newTitle: String, updatedAt: Long) {
        val values = ContentValues().apply {
            put(COL_TITLE, newTitle.trim())
            put(COL_UPDATED_AT, updatedAt)
        }
        writableDatabase.update(
            TABLE_SESSIONS,
            values,
            "$COL_ID = ?",
            arrayOf(sessionId)
        )
    }

    fun touchSession(sessionId: String, mode: ChatMode, projectName: String?, updatedAt: Long) {
        val values = ContentValues().apply {
            put(COL_MODE, mode.name)
            put(COL_PROJECT_NAME, projectName)
            put(COL_UPDATED_AT, updatedAt)
        }
        writableDatabase.update(
            TABLE_SESSIONS,
            values,
            "$COL_ID = ?",
            arrayOf(sessionId)
        )
    }

    fun deleteSession(sessionId: String) {
        writableDatabase.delete(TABLE_MESSAGES, "$COL_SESSION_ID = ?", arrayOf(sessionId))
        writableDatabase.delete(TABLE_SESSIONS, "$COL_ID = ?", arrayOf(sessionId))
    }

    fun upsertMessage(sessionId: String, message: ChatMessage) {
        val values = ContentValues().apply {
            put(COL_ID, message.id)
            put(COL_SESSION_ID, sessionId)
            put(COL_ROLE, message.role.name)
            put(COL_CONTENT, message.content)
            put(COL_TIMESTAMP, message.timestampMillis)
            put(COL_CITATIONS_JSON, serializeCitations(message.searchSources))
            put(COL_CHANGES_JSON, serializeMessageMeta(message))
        }
        writableDatabase.insertWithOnConflict(
            TABLE_MESSAGES,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun deleteMessage(messageId: String) {
        writableDatabase.delete(TABLE_MESSAGES, "$COL_ID = ?", arrayOf(messageId))
    }

    fun getMessagesForSession(sessionId: String): List<ChatMessage> {
        val result = mutableListOf<ChatMessage>()
        readableDatabase.query(
            TABLE_MESSAGES,
            arrayOf(
                COL_ID,
                COL_ROLE,
                COL_CONTENT,
                COL_TIMESTAMP,
                COL_CITATIONS_JSON,
                COL_CHANGES_JSON
            ),
            "$COL_SESSION_ID = ?",
            arrayOf(sessionId),
            null,
            null,
            "$COL_TIMESTAMP ASC"
        ).use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(COL_ID)
            val roleIdx = cursor.getColumnIndexOrThrow(COL_ROLE)
            val contentIdx = cursor.getColumnIndexOrThrow(COL_CONTENT)
            val tsIdx = cursor.getColumnIndexOrThrow(COL_TIMESTAMP)
            val citationsIdx = cursor.getColumnIndexOrThrow(COL_CITATIONS_JSON)
            val changesIdx = cursor.getColumnIndexOrThrow(COL_CHANGES_JSON)

            while (cursor.moveToNext()) {
                val id = cursor.getString(idIdx)
                val role = runCatching { ChatRole.valueOf(cursor.getString(roleIdx)) }
                    .getOrDefault(ChatRole.ASSISTANT)
                val content = cursor.getString(contentIdx)
                val timestamp = cursor.getLong(tsIdx)
                val citations = deserializeCitations(cursor.getString(citationsIdx))
                val meta = deserializeMessageMeta(cursor.getString(changesIdx))

                result.add(
                    ChatMessage(
                        id = id,
                        role = role,
                        mode = meta.mode,
                        content = content,
                        timestampMillis = timestamp,
                        attachedFiles = meta.attachedFiles,
                        searchSources = citations,
                        pipelinePlan = meta.pipelinePlan,
                        changeSet = meta.changeSet,
                        isError = meta.isError,
                        feedback = meta.feedback,
                        sourcePrompt = meta.sourcePrompt
                    )
                )
            }
        }
        return result
    }

    private fun serializeCitations(sources: List<WebSearchResult>): String? {
        if (sources.isEmpty()) return null
        val array = JSONArray()
        sources.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("title", item.title)
                    put("snippet", item.snippet)
                    put("url", item.url)
                }
            )
        }
        return array.toString()
    }

    private fun deserializeCitations(json: String?): List<WebSearchResult> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    add(
                        WebSearchResult(
                            title = obj.optString("title"),
                            snippet = obj.optString("snippet"),
                            url = obj.optString("url")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private data class MessageMeta(
        val mode: ChatMode = ChatMode.CHAT,
        val attachedFiles: List<String> = emptyList(),
        val pipelinePlan: String? = null,
        val changeSet: AppliedChangeSet? = null,
        val isError: Boolean = false,
        val feedback: MessageFeedback = MessageFeedback.NONE,
        val sourcePrompt: String? = null
    )

    private fun serializeMessageMeta(message: ChatMessage): String {
        val root = JSONObject().apply {
            put("mode", message.mode.name)
            put("isError", message.isError)
            put("feedback", message.feedback.name)
            if (!message.pipelinePlan.isNullOrBlank()) {
                put("pipelinePlan", message.pipelinePlan)
            }
            if (!message.sourcePrompt.isNullOrBlank()) {
                put("sourcePrompt", message.sourcePrompt)
            }
            if (message.attachedFiles.isNotEmpty()) {
                put("attachedFiles", JSONArray(message.attachedFiles))
            }
            message.changeSet?.let { cs ->
                val filesArr = JSONArray()
                cs.files.forEach { fileEntry ->
                    filesArr.put(
                        JSONObject().apply {
                            put("relativePathInProject", fileEntry.relativePathInProject)
                            put("workspaceRelativePath", fileEntry.workspaceRelativePath)
                            put("absolutePath", fileEntry.absolutePath)
                            put("existedBefore", fileEntry.existedBefore)
                        }
                    )
                }
                put(
                    "changeSet",
                    JSONObject().apply {
                        put("id", cs.id)
                        put("projectName", cs.projectName)
                        put("projectAbsolutePath", cs.projectAbsolutePath)
                        put("status", cs.status.name)
                        put("files", filesArr)
                    }
                )
            }
        }
        return root.toString()
    }

    private fun deserializeMessageMeta(json: String?): MessageMeta {
        if (json.isNullOrBlank()) return MessageMeta()
        return runCatching {
            val root = JSONObject(json)
            val mode = runCatching { ChatMode.valueOf(root.optString("mode")) }
                .getOrDefault(ChatMode.CHAT)
            val feedback = runCatching { MessageFeedback.valueOf(root.optString("feedback")) }
                .getOrDefault(MessageFeedback.NONE)
            val attached = mutableListOf<String>()
            root.optJSONArray("attachedFiles")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optString(i).takeIf { it.isNotBlank() }?.let { attached.add(it) }
                }
            }
            val csObj = root.optJSONObject("changeSet")
            val changeSet = csObj?.let { obj ->
                val filesList = mutableListOf<FileBackupEntry>()
                obj.optJSONArray("files")?.let { fArr ->
                    for (i in 0 until fArr.length()) {
                        val f = fArr.optJSONObject(i) ?: continue
                        filesList.add(
                            FileBackupEntry(
                                relativePathInProject = f.optString("relativePathInProject"),
                                workspaceRelativePath = f.optString("workspaceRelativePath"),
                                absolutePath = f.optString("absolutePath"),
                                previousContent = null,
                                existedBefore = f.optBoolean("existedBefore", false)
                            )
                        )
                    }
                }
                val status = runCatching { ChangeReviewStatus.valueOf(obj.optString("status")) }
                    .getOrDefault(ChangeReviewStatus.KEPT)
                AppliedChangeSet(
                    id = obj.optString("id"),
                    projectName = obj.optString("projectName"),
                    projectAbsolutePath = obj.optString("projectAbsolutePath"),
                    files = filesList,
                    status = status
                )
            }
            MessageMeta(
                mode = mode,
                attachedFiles = attached,
                pipelinePlan = root.optString("pipelinePlan").takeIf { it.isNotBlank() },
                changeSet = changeSet,
                isError = root.optBoolean("isError", false),
                feedback = feedback,
                sourcePrompt = root.optString("sourcePrompt").takeIf { it.isNotBlank() }
            )
        }.getOrDefault(MessageMeta())
    }

    companion object {
        private const val DATABASE_NAME = "nova_ide_chats.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_SESSIONS = "chat_sessions"
        private const val TABLE_MESSAGES = "chat_messages"

        private const val COL_ID = "id"
        private const val COL_TITLE = "title"
        private const val COL_MODE = "mode"
        private const val COL_PROJECT_NAME = "projectName"
        private const val COL_CREATED_AT = "createdAt"
        private const val COL_UPDATED_AT = "updatedAt"

        private const val COL_SESSION_ID = "sessionId"
        private const val COL_ROLE = "role"
        private const val COL_CONTENT = "content"
        private const val COL_TIMESTAMP = "timestamp"
        private const val COL_CITATIONS_JSON = "citationsJson"
        private const val COL_CHANGES_JSON = "changesJson"
    }
}
