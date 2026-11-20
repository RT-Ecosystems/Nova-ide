package com.example.core.memory

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.domain.model.MemoryItem

/**
 * Native Android SQLiteOpenHelper for persistent AI memories.
 * Stores user facts, language preferences, and coding style rules without annotation processors.
 */
class MemoryDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_MEMORIES (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_CONTENT TEXT NOT NULL,
                $COL_CREATED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 1) {
            onCreate(db)
        }
    }

    fun getAllMemories(): List<MemoryItem> {
        val result = mutableListOf<MemoryItem>()
        readableDatabase.query(
            TABLE_MEMORIES,
            arrayOf(COL_ID, COL_CONTENT, COL_CREATED_AT),
            null,
            null,
            null,
            null,
            "$COL_CREATED_AT DESC"
        ).use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(COL_ID)
            val contentIdx = cursor.getColumnIndexOrThrow(COL_CONTENT)
            val createdAtIdx = cursor.getColumnIndexOrThrow(COL_CREATED_AT)
            while (cursor.moveToNext()) {
                result.add(
                    MemoryItem(
                        id = cursor.getLong(idIdx),
                        content = cursor.getString(contentIdx),
                        createdAt = cursor.getLong(createdAtIdx)
                    )
                )
            }
        }
        return result
    }

    fun insertMemory(content: String, createdAt: Long = System.currentTimeMillis()): Long {
        val values = ContentValues().apply {
            put(COL_CONTENT, content.trim())
            put(COL_CREATED_AT, createdAt)
        }
        return writableDatabase.insert(TABLE_MEMORIES, null, values)
    }

    fun deleteMemory(id: Long): Int {
        return writableDatabase.delete(
            TABLE_MEMORIES,
            "$COL_ID = ?",
            arrayOf(id.toString())
        )
    }

    fun clearAllMemories(): Int {
        return writableDatabase.delete(TABLE_MEMORIES, null, null)
    }

    fun existsNormalized(content: String): Boolean {
        val target = content.trim().lowercase()
        return getAllMemories().any { it.content.trim().lowercase() == target }
    }

    companion object {
        private const val DATABASE_NAME = "nova_ide_memory.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_MEMORIES = "memories"
        private const val COL_ID = "id"
        private const val COL_CONTENT = "content"
        private const val COL_CREATED_AT = "created_at"
    }
}
