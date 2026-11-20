package com.example.core.editor

import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * Clean Light Developer Syntax theme tailored to Nova IDE's #FFFFFF / #F8FAFC light aesthetic.
 */
class LightDeveloperColorScheme : EditorColorScheme() {

    override fun applyDefault() {
        super.applyDefault()

        // Surfaces & Gutters
        setColor(WHOLE_BACKGROUND, 0xFFFFFFFF.toInt())
        setColor(LINE_NUMBER_BACKGROUND, 0xFFF8FAFC.toInt())
        setColor(LINE_DIVIDER, 0xFFE2E8F0.toInt())
        setColor(CURRENT_LINE, 0xFFF1F5F9.toInt())
        setColor(LINE_NUMBER, 0xFF94A3B8.toInt())
        setColor(LINE_NUMBER_CURRENT, 0xFF2563EB.toInt())
        setColor(LINE_NUMBER_PANEL, 0xFF0F172A.toInt())
        setColor(LINE_NUMBER_PANEL_TEXT, 0xFFFFFFFF.toInt())

        // Cursor, Selection & Guides
        setColor(TEXT_NORMAL, 0xFF0F172A.toInt())
        setColor(SELECTION_INSERT, 0xFF2563EB.toInt())
        setColor(SELECTION_HANDLE, 0xFF2563EB.toInt())
        setColor(SELECTED_TEXT_BACKGROUND, 0xFFDBEAFE.toInt())
        setColor(MATCHED_TEXT_BACKGROUND, 0xFFBFDBFE.toInt())
        setColor(BLOCK_LINE, 0xFFE2E8F0.toInt())
        setColor(BLOCK_LINE_CURRENT, 0xFF93C5FD.toInt())
        setColor(SCROLL_BAR_THUMB, 0xFFCBD5E1.toInt())
        setColor(SCROLL_BAR_THUMB_PRESSED, 0xFF2563EB.toInt())
        setColor(SCROLL_BAR_TRACK, 0x00000000)

        // Completion Popup
        setColor(COMPLETION_WND_BACKGROUND, 0xFFFFFFFF.toInt())
        setColor(COMPLETION_WND_CORNER, 0xFFE2E8F0.toInt())
        setColor(COMPLETION_WND_TEXT_PRIMARY, 0xFF0F172A.toInt())
        setColor(COMPLETION_WND_TEXT_SECONDARY, 0xFF64748B.toInt())
        setColor(COMPLETION_WND_ITEM_CURRENT, 0xFFEFF6FF.toInt())

        // Light Syntax Token Accents (Refined Indigo, Emerald, Violet, Amber, Slate)
        setColor(KEYWORD, 0xFF2563EB.toInt())
        setColor(OPERATOR, 0xFF334155.toInt())
        setColor(LITERAL, 0xFF059669.toInt())
        setColor(COMMENT, 0xFF64748B.toInt())
        setColor(ANNOTATION, 0xFFD97706.toInt())
        setColor(IDENTIFIER_NAME, 0xFF0F172A.toInt())
        setColor(IDENTIFIER_VAR, 0xFF7C3AED.toInt())
        setColor(FUNCTION_NAME, 0xFF0284C7.toInt())
    }
}
