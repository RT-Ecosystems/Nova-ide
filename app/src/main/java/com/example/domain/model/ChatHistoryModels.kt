package com.example.domain.model

import com.example.ui.viewmodel.ChatMode

data class ChatSessionSummary(
    val id: String,
    val title: String,
    val mode: ChatMode,
    val projectName: String?,
    val createdAt: Long,
    val updatedAt: Long
)
