package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.AiWorkStatus
import com.example.domain.model.AppliedChangeSet
import com.example.domain.model.ChangeReviewStatus
import com.example.domain.model.ChatMessage
import com.example.domain.model.ChatRole
import com.example.domain.model.MessageFeedback
import com.example.domain.model.TtsPlaybackState
import com.example.domain.model.WebSearchResult
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentAmberContainer
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentEmeraldBorder
import com.example.ui.theme.AccentEmeraldContainer
import com.example.ui.theme.AccentRose
import com.example.ui.theme.AccentRoseContainer
import com.example.ui.theme.AccentViolet
import com.example.ui.theme.BorderLight
import com.example.ui.theme.BrandIndigo
import com.example.ui.theme.BrandIndigoBorder
import com.example.ui.theme.BrandIndigoContainer
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.OnBrandLight
import com.example.ui.theme.SurfaceBackground
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPlaceholder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay

private sealed class ContentSegment {
    data class Prose(val text: String) : ContentSegment()
    data class CodeBlock(val language: String, val code: String) : ContentSegment()
}

private val CODE_FENCE_REGEX = Regex("```([a-zA-Z0-9_+.-]*)\\s*\\n([\\s\\S]*?)```")

private fun parseMessageSegments(raw: String): List<ContentSegment> {
    if (raw.isBlank()) return emptyList()
    val segments = mutableListOf<ContentSegment>()
    var cursor = 0
    CODE_FENCE_REGEX.findAll(raw).forEach { match ->
        if (match.range.first > cursor) {
            val prose = raw.substring(cursor, match.range.first).trim()
            if (prose.isNotEmpty()) {
                segments.add(ContentSegment.Prose(prose))
            }
        }
        val lang = match.groupValues[1].trim().ifBlank { "code" }
        val code = match.groupValues[2].trimEnd()
        segments.add(ContentSegment.CodeBlock(language = lang, code = code))
        cursor = match.range.last + 1
    }
    if (cursor < raw.length) {
        val tail = raw.substring(cursor).trim()
        if (tail.isNotEmpty()) {
            segments.add(ContentSegment.Prose(tail))
        }
    }
    if (segments.isEmpty()) {
        segments.add(ContentSegment.Prose(raw.trim()))
    }
    return segments
}

@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    ttsState: TtsPlaybackState,
    isAiWorking: Boolean,
    onKeepChanges: (messageId: String) -> Unit,
    onRevertChanges: (messageId: String) -> Unit,
    onOpenFileInEditor: (absolutePath: String) -> Unit,
    onRunPreview: (projectAbsolutePath: String) -> Unit,
    onToggleSpeak: (ChatMessage) -> Unit,
    onCopyMessage: (String) -> Unit,
    onRegenerateMessage: (messageId: String) -> Unit,
    onFeedbackClick: (messageId: String, feedback: MessageFeedback) -> Unit,
    onSaveToMemory: (String) -> Unit,
    onDeleteMessage: (messageId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (message.role == ChatRole.USER) {
        UserMessageBubble(message = message, modifier = modifier)
    } else {
        AssistantMessageCard(
            message = message,
            ttsState = ttsState,
            isAiWorking = isAiWorking,
            onKeepChanges = { onKeepChanges(message.id) },
            onRevertChanges = { onRevertChanges(message.id) },
            onOpenFileInEditor = onOpenFileInEditor,
            onRunPreview = onRunPreview,
            onToggleSpeak = { onToggleSpeak(message) },
            onCopyMessage = onCopyMessage,
            onRegenerateMessage = { onRegenerateMessage(message.id) },
            onFeedbackClick = { feedback -> onFeedbackClick(message.id, feedback) },
            onSaveToMemory = onSaveToMemory,
            onDeleteMessage = { onDeleteMessage(message.id) },
            modifier = modifier
        )
    }
}

@Composable
private fun UserMessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            color = BrandIndigo,
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 6.dp,
                bottomStart = 20.dp,
                bottomEnd = 20.dp
            ),
            shadowElevation = 1.dp,
            modifier = Modifier
                .widthIn(max = 310.dp)
                .testTag("chat_user_bubble")
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (message.attachedFiles.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        message.attachedFiles.forEach { relPath ->
                            Surface(
                                color = OnBrandLight.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AttachFile,
                                        contentDescription = null,
                                        tint = OnBrandLight,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = relPath.substringAfterLast('/'),
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OnBrandLight,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnBrandLight
                )

                Text(
                    text = message.mode.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = OnBrandLight.copy(alpha = 0.72f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
private fun AssistantMessageCard(
    message: ChatMessage,
    ttsState: TtsPlaybackState,
    isAiWorking: Boolean,
    onKeepChanges: () -> Unit,
    onRevertChanges: () -> Unit,
    onOpenFileInEditor: (absolutePath: String) -> Unit,
    onRunPreview: (projectAbsolutePath: String) -> Unit,
    onToggleSpeak: () -> Unit,
    onCopyMessage: (String) -> Unit,
    onRegenerateMessage: () -> Unit,
    onFeedbackClick: (MessageFeedback) -> Unit,
    onSaveToMemory: (String) -> Unit,
    onDeleteMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val segments = remember(message.content) { parseMessageSegments(message.content) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            color = if (message.isError) AccentRoseContainer else SurfaceCard,
            shape = RoundedCornerShape(
                topStart = 6.dp,
                topEnd = 20.dp,
                bottomStart = 20.dp,
                bottomEnd = 20.dp
            ),
            border = BorderStroke(
                width = 1.dp,
                color = if (message.isError) AccentRose.copy(alpha = 0.35f) else BorderLight
            ),
            shadowElevation = 1.dp,
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .testTag("chat_assistant_card")
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Assistant Header Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (message.isError) AccentRose else BrandIndigoContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (message.isError) Icons.Filled.ErrorOutline else Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = if (message.isError) OnBrandLight else BrandIndigo,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = if (message.isError) "Nova AI • Error" else "Nova AI",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (message.isError) AccentRose else TextPrimary
                    )
                    Surface(
                        color = SurfaceElevated,
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, BorderLight)
                    ) {
                        Text(
                            text = message.mode.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                // Live Web Search Citations
                if (message.searchSources.isNotEmpty()) {
                    WebSearchCitationsStrip(sources = message.searchSources)
                }

                // Pipeline Architecture Plan Card
                if (!message.pipelinePlan.isNullOrBlank()) {
                    PipelinePlanBox(planText = message.pipelinePlan)
                }

                // Parsed Prose & Syntax-Styled Code Blocks
                segments.forEach { segment ->
                    when (segment) {
                        is ContentSegment.Prose -> {
                            Text(
                                text = segment.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (message.isError) AccentRose else TextPrimary
                            )
                        }
                        is ContentSegment.CodeBlock -> {
                            SyntaxCodeBlockCard(
                                language = segment.language,
                                code = segment.code
                            )
                        }
                    }
                }

                // Builder / Pipeline Change Review Card
                if (message.changeSet != null) {
                    ChangeReviewCard(
                        changeSet = message.changeSet,
                        onKeepAll = onKeepChanges,
                        onRevertAll = onRevertChanges,
                        onOpenFile = onOpenFileInEditor,
                        onRunPreview = { onRunPreview(message.changeSet.projectAbsolutePath) }
                    )
                }
            }
        }

        // Phase 4: Compact Horizontal AI Response Action Bar below EVERY AI response card
        AiMessageActionBar(
            message = message,
            isSpeaking = ttsState.activeMessageId == message.id && ttsState.isSpeaking,
            isPreparingSpeech = ttsState.activeMessageId == message.id && ttsState.isPreparing,
            isAiWorking = isAiWorking,
            onToggleSpeak = onToggleSpeak,
            onCopyMessage = onCopyMessage,
            onRegenerateMessage = onRegenerateMessage,
            onFeedbackClick = onFeedbackClick,
            onSaveToMemory = onSaveToMemory,
            onDeleteMessage = onDeleteMessage
        )
    }
}

@Composable
private fun AiMessageActionBar(
    message: ChatMessage,
    isSpeaking: Boolean,
    isPreparingSpeech: Boolean,
    isAiWorking: Boolean,
    onToggleSpeak: () -> Unit,
    onCopyMessage: (String) -> Unit,
    onRegenerateMessage: () -> Unit,
    onFeedbackClick: (MessageFeedback) -> Unit,
    onSaveToMemory: (String) -> Unit,
    onDeleteMessage: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    val isAudioActive = isSpeaking || isPreparingSpeech

    val fullCopyText = remember(message.content, message.pipelinePlan) {
        if (!message.pipelinePlan.isNullOrBlank()) {
            "${message.pipelinePlan}\n\n${message.content}".trim()
        } else {
            message.content
        }
    }

    Row(
        modifier = Modifier
            .padding(start = 6.dp, top = 2.dp)
            .testTag("ai_action_bar_${message.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // 1. Speaker / Stop (Edge-TTS)
        ActionBarIconChip(
            onClick = onToggleSpeak,
            active = isAudioActive,
            activeTint = BrandIndigo,
            contentDescription = if (isAudioActive) "Stop reading aloud" else "Read message aloud",
            testTag = "ai_action_speak"
        ) {
            if (isPreparingSpeech) {
                CircularProgressIndicator(
                    strokeWidth = 1.8.dp,
                    color = BrandIndigo,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Icon(
                    imageVector = if (isSpeaking) Icons.Filled.Stop else Icons.Filled.VolumeUp,
                    contentDescription = null,
                    tint = if (isSpeaking) BrandIndigo else TextMuted,
                    modifier = Modifier.size(17.dp)
                )
            }
        }

        // 2. Copy Full Response
        ActionBarIconChip(
            onClick = {
                clipboard.setText(AnnotatedString(fullCopyText))
                onCopyMessage("Copied response to clipboard")
            },
            contentDescription = "Copy full response",
            testTag = "ai_action_copy"
        ) {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }

        // 3. Regenerate
        ActionBarIconChip(
            onClick = onRegenerateMessage,
            enabled = !isAiWorking,
            contentDescription = "Regenerate response",
            testTag = "ai_action_regenerate"
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = if (isAiWorking) TextPlaceholder else TextMuted,
                modifier = Modifier.size(17.dp)
            )
        }

        // 4. Like (ThumbUp)
        val isLiked = message.feedback == MessageFeedback.LIKED
        ActionBarIconChip(
            onClick = { onFeedbackClick(MessageFeedback.LIKED) },
            active = isLiked,
            activeTint = BrandIndigo,
            contentDescription = "Like response",
            testTag = "ai_action_like"
        ) {
            Icon(
                imageVector = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                contentDescription = null,
                tint = if (isLiked) BrandIndigo else TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }

        // 5. Dislike (ThumbDown)
        val isDisliked = message.feedback == MessageFeedback.DISLIKED
        ActionBarIconChip(
            onClick = { onFeedbackClick(MessageFeedback.DISLIKED) },
            active = isDisliked,
            activeTint = AccentRose,
            contentDescription = "Dislike response",
            testTag = "ai_action_dislike"
        ) {
            Icon(
                imageVector = if (isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                contentDescription = null,
                tint = if (isDisliked) AccentRose else TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }

        // 6. 3-Dots Menu (Save to Memory, Share Response, Delete Message)
        Box {
            ActionBarIconChip(
                onClick = { menuExpanded = true },
                contentDescription = "More message options",
                testTag = "ai_action_more"
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(17.dp)
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                containerColor = SurfaceCard
            ) {
                DropdownMenuItem(
                    text = { Text("Save to Memory", color = TextPrimary) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.BookmarkAdd,
                            contentDescription = null,
                            tint = BrandIndigo,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onSaveToMemory(fullCopyText.take(280))
                    }
                )
                DropdownMenuItem(
                    text = { Text("Share Response", color = TextPrimary) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        runCatching {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, fullCopyText)
                            }
                            val chooser = Intent.createChooser(sendIntent, "Share AI Response").apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(chooser)
                        }
                    }
                )
                HorizontalDivider(thickness = 1.dp, color = BorderLight)
                DropdownMenuItem(
                    text = { Text("Delete Message", color = AccentRose) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = null,
                            tint = AccentRose,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onDeleteMessage()
                    }
                )
            }
        }
    }
}

@Composable
private fun ActionBarIconChip(
    onClick: () -> Unit,
    contentDescription: String,
    testTag: String,
    enabled: Boolean = true,
    active: Boolean = false,
    activeTint: androidx.compose.ui.graphics.Color = BrandIndigo,
    content: @Composable () -> Unit
) {
    Surface(
        color = if (active) activeTint.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent,
        shape = CircleShape,
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
private fun WebSearchCitationsStrip(sources: List<WebSearchResult>) {
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Language,
                contentDescription = null,
                tint = BrandIndigo,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "Live Web Sources (${sources.size})",
                style = MaterialTheme.typography.labelSmall,
                color = BrandIndigo
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            sources.forEachIndexed { idx, source ->
                val domain = remember(source.url) {
                    runCatching { Uri.parse(source.url).host?.removePrefix("www.") }.getOrNull()
                        ?.ifBlank { null }
                        ?: "source"
                }
                Surface(
                    color = BrandIndigoContainer,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, BrandIndigoBorder),
                    modifier = Modifier
                        .widthIn(max = 200.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            runCatching {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(source.url)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        }
                        .testTag("chat_web_source_$idx")
                ) {
                    Column(modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)) {
                        Text(
                            text = "[${idx + 1}] ${source.title}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = domain,
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandIndigo,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PipelinePlanBox(planText: String) {
    Surface(
        color = BrandIndigoContainer.copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BrandIndigoBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.AccountTree,
                    contentDescription = null,
                    tint = AccentViolet,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Architecture Pipeline Plan",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary
                )
            }
            Text(
                text = planText.trim(),
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun SyntaxCodeBlockCard(
    language: String,
    code: String
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1800)
            copied = false
        }
    }

    Surface(
        color = SurfaceBackground,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderLight),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceElevated)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Code,
                        contentDescription = null,
                        tint = BrandIndigo,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = language.lowercase(),
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                Surface(
                    color = if (copied) AccentEmeraldContainer else SurfaceCard,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (copied) AccentEmeraldBorder else BorderLight),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            clipboard.setText(AnnotatedString(code))
                            copied = true
                        }
                        .testTag("chat_code_copy_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                            contentDescription = "Copy code",
                            tint = if (copied) AccentEmerald else TextSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (copied) "Copied" else "Copy",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (copied) AccentEmerald else TextSecondary
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = BorderLight)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = code,
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary
                )
            }
        }
    }
}

@Composable
private fun ChangeReviewCard(
    changeSet: AppliedChangeSet,
    onKeepAll: () -> Unit,
    onRevertAll: () -> Unit,
    onOpenFile: (absolutePath: String) -> Unit,
    onRunPreview: () -> Unit
) {
    val (statusLabel, statusBg, statusFg) = when (changeSet.status) {
        ChangeReviewStatus.PENDING -> Triple("Pending Review", AccentAmberContainer, AccentAmber)
        ChangeReviewStatus.KEPT -> Triple("Kept", AccentEmeraldContainer, AccentEmerald)
        ChangeReviewStatus.REVERTED -> Triple("Reverted", SurfaceElevated, TextMuted)
    }

    val primaryFile = remember(changeSet.files) {
        changeSet.files.firstOrNull { it.relativePathInProject.endsWith("index.html", ignoreCase = true) }
            ?: changeSet.files.firstOrNull()
    }

    Surface(
        color = SurfaceBackground,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (changeSet.status == ChangeReviewStatus.KEPT) AccentEmeraldBorder else BorderLight
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chat_change_review_card")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Changes Applied (${changeSet.files.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                    Text(
                        text = "/projects/${changeSet.projectName}",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    color = statusBg,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, statusFg.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusFg,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                changeSet.files.forEach { fileEntry ->
                    Surface(
                        color = SurfaceCard,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, BorderLight),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = changeSet.status != ChangeReviewStatus.REVERTED) {
                                onOpenFile(fileEntry.absolutePath)
                            }
                            .testTag("change_review_file_${fileEntry.relativePathInProject}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Description,
                                contentDescription = null,
                                tint = BrandIndigo,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = fileEntry.relativePathInProject,
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                color = if (fileEntry.existedBefore) BrandIndigoContainer else AccentEmeraldContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (fileEntry.existedBefore) "MOD" else "NEW",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (fileEntry.existedBefore) BrandIndigo else AccentEmerald,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (changeSet.status != ChangeReviewStatus.REVERTED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (changeSet.status == ChangeReviewStatus.PENDING) {
                        Button(
                            onClick = onKeepAll,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentEmerald,
                                contentColor = OnBrandLight
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("change_review_keep_button")
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Keep All", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    OutlinedButton(
                        onClick = onRevertAll,
                        border = BorderStroke(1.dp, AccentRose.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("change_review_revert_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = null,
                            tint = AccentRose,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Revert", style = MaterialTheme.typography.labelLarge, color = AccentRose)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { primaryFile?.let { onOpenFile(it.absolutePath) } },
                        enabled = primaryFile != null,
                        border = BorderStroke(1.dp, BorderLight),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("change_review_editor_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Code,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Editor", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                    }

                    Button(
                        onClick = onRunPreview,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandIndigo,
                            contentColor = OnBrandLight
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("change_review_preview_button")
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Run Preview", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun AiWorkingStatusPill(
    status: AiWorkStatus,
    modifier: Modifier = Modifier
) {
    if (!status.isWorking) return

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            color = BrandIndigoContainer,
            shape = RoundedCornerShape(999.dp),
            border = BorderStroke(1.dp, BrandIndigoBorder),
            modifier = Modifier.testTag("chat_ai_status_pill")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = BrandIndigo,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = status.label.ifBlank { "Working..." },
                    style = MaterialTheme.typography.labelMedium,
                    color = BrandIndigo,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
