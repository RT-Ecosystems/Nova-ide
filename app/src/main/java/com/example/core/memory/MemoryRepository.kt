package com.example.core.memory

import com.example.domain.model.MemoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Repository managing persistent SQLite AI memories, automatic Hindi/English memory detection,
 * and system prompt formatting.
 */
class MemoryRepository(
    private val dbHelper: MemoryDatabaseHelper
) {
    private val _memories = MutableStateFlow<List<MemoryItem>>(emptyList())
    val memories: StateFlow<List<MemoryItem>> = _memories.asStateFlow()

    suspend fun refreshMemories(): List<MemoryItem> = withContext(Dispatchers.IO) {
        val all = dbHelper.getAllMemories()
        _memories.value = all
        all
    }

    suspend fun addMemory(rawContent: String): MemoryItem? = withContext(Dispatchers.IO) {
        val cleaned = rawContent.trim().take(MAX_MEMORY_LENGTH)
        if (cleaned.isEmpty()) return@withContext null
        if (dbHelper.existsNormalized(cleaned)) {
            _memories.value = dbHelper.getAllMemories()
            return@withContext _memories.value.firstOrNull {
                it.content.trim().equals(cleaned, ignoreCase = true)
            }
        }
        val now = System.currentTimeMillis()
        val id = dbHelper.insertMemory(cleaned, now)
        val refreshed = dbHelper.getAllMemories()
        _memories.value = refreshed
        if (id > 0L) MemoryItem(id = id, content = cleaned, createdAt = now) else null
    }

    suspend fun deleteMemory(id: Long) = withContext(Dispatchers.IO) {
        dbHelper.deleteMemory(id)
        _memories.value = dbHelper.getAllMemories()
    }

    suspend fun clearAllMemories() = withContext(Dispatchers.IO) {
        dbHelper.clearAllMemories()
        _memories.value = emptyList()
    }

    /**
     * Automatically detects explicit user memory instructions in English & Hindi/Hinglish
     * and extracts a clean, reusable memory statement.
     */
    fun detectAutoMemory(userPrompt: String): String? {
        val trimmed = userPrompt.trim()
        if (trimmed.length < 6 || trimmed.length > 360) return null
        val lower = trimmed.lowercase()

        // Explicit trigger prefixes / phrases (English)
        val englishLeadRegex = Regex(
            pattern = "^(?:please\\s+)?(?:remember\\s+that|remember|save\\s+to\\s+memory(?:\\s+that)?|add\\s+to\\s+memory(?:\\s+that)?|keep\\s+in\\s+mind(?:\\s+that)?|don't\\s+forget(?:\\s+that)?)[:\\s,-]+(.+)$",
            options = setOf(RegexOption.IGNORE_CASE)
        )
        englishLeadRegex.find(trimmed)?.let { match ->
            val extracted = match.groupValues[1].trim()
            if (extracted.length >= 4) return extracted.take(MAX_MEMORY_LENGTH)
        }

        // Inline "remember my name is..." / "save to memory" / "always speak/reply in..."
        val inlineTriggers = listOf(
            "remember my ",
            "remember i ",
            "remember i'm ",
            "save to memory",
            "save this to memory",
            "always reply in ",
            "always respond in ",
            "always talk in ",
            "always speak in ",
            "my name is ",
            "call me "
        )
        if (inlineTriggers.any { lower.contains(it) }) {
            return trimmed
                .replace(Regex("(?i)^(please\\s+)?(save\\s+(this\\s+)?to\\s+memory[:\\s,-]*)"), "")
                .trim()
                .ifBlank { trimmed }
                .take(MAX_MEMORY_LENGTH)
        }

        // Hindi & Hinglish memory cues
        val hindiCues = listOf(
            "याद रखो",
            "याद रखना",
            "मेमोरी में सेव",
            "मेमोरी में याद",
            "हमेशा हिंदी में",
            "हमेशा हिन्दी में",
            "हमेशा इंग्लिश में",
            "हमेशा अंग्रेजी में",
            "मेरा नाम ",
            "yaad rakho",
            "yaad rakhna",
            "memory me save",
            "memory mein save",
            "hamesha hindi me",
            "hamesha hindi mein",
            "mera naam "
        )
        if (hindiCues.any { lower.contains(it) || trimmed.contains(it) }) {
            return trimmed.take(MAX_MEMORY_LENGTH)
        }

        return null
    }

    fun formatMemoriesForSystemPrompt(items: List<MemoryItem>): String {
        if (items.isEmpty()) return ""
        return buildString {
            appendLine("PERSISTENT USER MEMORY (Always remember and follow these saved facts & preferences across every reply):")
            items.take(MAX_PROMPT_MEMORIES).forEachIndexed { index, item ->
                appendLine("${index + 1}. ${item.content}")
            }
        }.trimEnd()
    }

    companion object {
        private const val MAX_MEMORY_LENGTH = 280
        private const val MAX_PROMPT_MEMORIES = 25
    }
}
