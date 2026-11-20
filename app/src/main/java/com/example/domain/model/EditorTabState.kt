package com.example.domain.model

data class EditorTabState(
    val filePath: String? = null,
    val fileName: String = "",
    val relativePath: String = "",
    val extension: String = "",
    val languageLabel: String = "Plain Text",
    val savedContent: String = "",
    val currentContent: String = "",
    val contentRevision: Long = 0L,
    val fontSizeSp: Float = 14f,
    val cursorLine: Int = 1,
    val cursorColumn: Int = 1,
    val lineCount: Int = 1,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isSaving: Boolean = false
) {
    val hasOpenFile: Boolean
        get() = !filePath.isNullOrBlank()

    val isDirty: Boolean
        get() = hasOpenFile && currentContent != savedContent
}
