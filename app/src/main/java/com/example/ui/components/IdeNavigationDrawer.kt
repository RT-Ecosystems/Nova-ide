package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.ChatSessionSummary
import com.example.ui.navigation.IdeDestination
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentRose
import com.example.ui.theme.AccentViolet
import com.example.ui.theme.BorderLight
import com.example.ui.theme.BrandIndigo
import com.example.ui.theme.BrandIndigoBorder
import com.example.ui.theme.BrandIndigoContainer
import com.example.ui.theme.OnBrandLight
import com.example.ui.theme.SurfaceBackground
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun IdeNavigationDrawer(
    sessions: List<ChatSessionSummary>,
    activeSessionId: String?,
    currentRoute: String?,
    isAiWorking: Boolean,
    onNewChat: () -> Unit,
    onSelectSession: (String) -> Unit,
    onRenameSession: (sessionId: String, newTitle: String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onOpenWebToApk: () -> Unit,
    onOpenWorkspaceFiles: () -> Unit,
    onOpenMemoryManager: () -> Unit,
    onOpenVoiceSettings: () -> Unit,
    onOpenApiSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sessionToRename by remember { mutableStateOf<ChatSessionSummary?>(null) }
    var renameInput by remember { mutableStateOf("") }

    ModalDrawerSheet(
        drawerContainerColor = SurfaceCard,
        drawerContentColor = TextPrimary,
        modifier = modifier
            .width(308.dp)
            .fillMaxHeight()
            .testTag("ide_navigation_drawer")
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Header: Brand + New Chat Primary CTA
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BorderLight),
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("drawer_brand_logo")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(6.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_nova_brand_mark),
                                contentDescription = "Nova IDE logo",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "AI Mobile Workspace",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }

                Button(
                    onClick = onNewChat,
                    enabled = !isAiWorking,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandIndigo,
                        contentColor = OnBrandLight,
                        disabledContainerColor = SurfaceElevated,
                        disabledContentColor = TextMuted
                    ),
                    contentPadding = PaddingValues(vertical = 11.dp, horizontal = 14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("drawer_new_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.drawer_new_chat),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            HorizontalDivider(thickness = 1.dp, color = BorderLight)

            // Middle Section: Scrollable Recent Chats
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.drawer_recent_chats),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
                if (sessions.isNotEmpty()) {
                    Text(
                        text = "${sessions.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }

            if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = SurfaceBackground,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, BorderLight),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Text(
                            text = "No saved chats yet",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary
                        )
                        Text(
                            text = "Start a conversation and your history will appear here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("drawer_recent_chats_list"),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(items = sessions, key = { it.id }) { session ->
                        val isActive = session.id == activeSessionId && currentRoute == IdeDestination.Chat.route
                        DrawerChatSessionRow(
                            session = session,
                            isActive = isActive,
                            enabled = !isAiWorking,
                            onClick = { onSelectSession(session.id) },
                            onRenameClick = {
                                sessionToRename = session
                                renameInput = session.title
                            },
                            onDeleteClick = { onDeleteSession(session.id) }
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = BorderLight)

            // Bottom Section: Quick-Access Tools
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.drawer_quick_tools),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )

                DrawerQuickToolItem(
                    label = "Web-to-APK Converter",
                    icon = Icons.Filled.InstallMobile,
                    tint = BrandIndigo,
                    isSelected = currentRoute == IdeDestination.Web2Apk.route,
                    testTag = "drawer_tool_web2apk",
                    onClick = onOpenWebToApk
                )
                DrawerQuickToolItem(
                    label = "Workspace Files",
                    icon = Icons.Filled.FolderOpen,
                    tint = AccentEmerald,
                    isSelected = currentRoute == IdeDestination.Files.route,
                    testTag = "drawer_tool_files",
                    onClick = onOpenWorkspaceFiles
                )
                DrawerQuickToolItem(
                    label = "AI Memory Manager",
                    icon = Icons.Filled.Psychology,
                    tint = AccentViolet,
                    isSelected = currentRoute == IdeDestination.Tools.route,
                    testTag = "drawer_tool_memory",
                    onClick = onOpenMemoryManager
                )
                DrawerQuickToolItem(
                    label = "Voice & Edge-TTS Settings",
                    icon = Icons.Filled.GraphicEq,
                    tint = BrandIndigo,
                    isSelected = false,
                    testTag = "drawer_tool_voice",
                    onClick = onOpenVoiceSettings
                )
                DrawerQuickToolItem(
                    label = "API / BYOK Settings",
                    icon = Icons.Filled.Tune,
                    tint = TextSecondary,
                    isSelected = false,
                    testTag = "drawer_tool_api",
                    onClick = onOpenApiSettings
                )
            }
        }
    }

    val renaming = sessionToRename
    if (renaming != null) {
        AlertDialog(
            onDismissRequest = { sessionToRename = null },
            containerColor = SurfaceCard,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = {
                Text(
                    text = "Rename Chat",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    label = { Text("Chat Title") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandIndigo,
                        unfocusedBorderColor = BorderLight,
                        focusedLabelColor = BrandIndigo,
                        cursorColor = BrandIndigo,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("drawer_rename_chat_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleaned = renameInput.trim()
                        if (cleaned.isNotEmpty()) {
                            onRenameSession(renaming.id, cleaned)
                        }
                        sessionToRename = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandIndigo,
                        contentColor = OnBrandLight
                    ),
                    modifier = Modifier.testTag("drawer_rename_chat_confirm")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToRename = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun DrawerChatSessionRow(
    session: ChatSessionSummary,
    isActive: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val containerColor = if (isActive) BrandIndigoContainer else Color.Transparent
    val borderStroke = if (isActive) BorderStroke(1.dp, BrandIndigoBorder) else null

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
        border = borderStroke,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag("drawer_session_item_${session.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.ChatBubbleOutline,
                contentDescription = null,
                tint = if (isActive) BrandIndigo else TextMuted,
                modifier = Modifier.size(16.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isActive) BrandIndigo else TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = session.mode.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActive) BrandIndigo.copy(alpha = 0.85f) else TextSecondary
                    )
                    Text(
                        text = "• ${formatRelativeTimestamp(session.updatedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(
                onClick = onRenameClick,
                enabled = enabled,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Rename ${session.title}",
                    tint = TextMuted,
                    modifier = Modifier.size(15.dp)
                )
            }
            IconButton(
                onClick = onDeleteClick,
                enabled = enabled,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Delete ${session.title}",
                    tint = AccentRose.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun DrawerQuickToolItem(
    label: String,
    icon: ImageVector,
    tint: Color,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) BrandIndigoContainer else Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) BorderStroke(1.dp, BrandIndigoBorder) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) BrandIndigo else tint,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isSelected) BrandIndigo else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatRelativeTimestamp(timestampMillis: Long): String {
    val now = System.currentTimeMillis()
    val diff = (now - timestampMillis).coerceAtLeast(0L)
    val minuteMs = 60_000L
    val hourMs = 3_600_000L
    val dayMs = 86_400_000L
    return when {
        diff < minuteMs -> "Just now"
        diff < hourMs -> "${diff / minuteMs}m ago"
        diff < dayMs -> "${diff / hourMs}h ago"
        diff < 2 * dayMs -> "Yesterday"
        else -> SimpleDateFormat("MMM d", Locale.US).format(Date(timestampMillis))
    }
}
