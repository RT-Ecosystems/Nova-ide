package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.EdgeVoicePreset
import com.example.domain.model.IdeSettings
import com.example.domain.model.MemoryItem
import com.example.domain.model.TtsPlaybackState
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentEmeraldBorder
import com.example.ui.theme.AccentEmeraldContainer
import com.example.ui.theme.AccentRose
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
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ToolsMemoryManagerCard(
    memories: List<MemoryItem>,
    onAddMemory: (String) -> Unit,
    onDeleteMemory: (Long) -> Unit,
    onClearAllMemories: () -> Unit
) {
    var newMemoryText by remember { mutableStateOf("") }
    val dateFormatter = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.US) }

    Phase4ToolsCardContainer(
        title = stringResource(R.string.memory_manager_title),
        subtitle = "Saved facts, language & coding preferences injected automatically into every AI prompt.",
        icon = Icons.Filled.Psychology,
        iconTint = AccentViolet,
        headerTrailing = {
            Surface(
                color = if (memories.isNotEmpty()) BrandIndigoContainer else SurfaceElevated,
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(
                    1.dp,
                    if (memories.isNotEmpty()) BrandIndigoBorder else BorderLight
                )
            ) {
                Text(
                    text = "${memories.size} saved",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (memories.isNotEmpty()) BrandIndigo else TextMuted,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    ) {
        // Manual Add Memory Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = newMemoryText,
                onValueChange = { newMemoryText = it },
                placeholder = { Text("e.g., My name is Alex • Always reply in Hindi") },
                singleLine = true,
                colors = ideTextFieldColors(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("tools_memory_input")
            )
            Button(
                onClick = {
                    if (newMemoryText.isNotBlank()) {
                        onAddMemory(newMemoryText)
                        newMemoryText = ""
                    }
                },
                enabled = newMemoryText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandIndigo,
                    contentColor = OnBrandLight
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                modifier = Modifier
                    .height(52.dp)
                    .testTag("tools_memory_add_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add memory", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add", fontWeight = FontWeight.SemiBold)
            }
        }

        if (memories.isEmpty()) {
            Surface(
                color = SurfaceBackground,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "No memories saved yet. Add one above, say \"remember my name is...\" in Chat, or save any AI reply via its 3-dots menu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(12.dp)
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                memories.forEach { item ->
                    Surface(
                        color = SurfaceBackground,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BorderLight),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tools_memory_item_${item.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = dateFormatter.format(Date(item.createdAt)),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                            IconButton(
                                onClick = { onDeleteMemory(item.id) },
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("tools_memory_delete_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = "Delete memory",
                                    tint = AccentRose,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onClearAllMemories,
                border = BorderStroke(1.dp, AccentRose.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tools_memory_clear_all_button")
            ) {
                Text("Clear All Memories", color = AccentRose, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun ToolsVoiceSettingsCard(
    settings: IdeSettings,
    ttsState: TtsPlaybackState,
    onSetAutoSpeak: (Boolean) -> Unit,
    onSelectVoice: (String) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onTestVoice: () -> Unit,
    onStopSpeaking: () -> Unit
) {
    Phase4ToolsCardContainer(
        title = stringResource(R.string.voice_settings_title),
        subtitle = "Zero-API-key Microsoft Edge neural voices with automatic Android TTS fallback.",
        icon = Icons.Filled.RecordVoiceOver,
        iconTint = BrandIndigo,
        headerTrailing = {
            Surface(
                color = BrandIndigoContainer,
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, BrandIndigoBorder)
            ) {
                Text(
                    text = EdgeVoicePreset.formatSpeed(settings.ttsSpeed),
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandIndigo,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    ) {
        // Auto-Speak Switch
        Surface(
            color = if (settings.autoSpeakEnabled) AccentEmeraldContainer else SurfaceBackground,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(
                1.dp,
                if (settings.autoSpeakEnabled) AccentEmeraldBorder else BorderLight
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                    Text(
                        text = "Auto-Speak New AI Replies",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                    Text(
                        text = "Automatically strips markdown & code blocks for clean speech",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = settings.autoSpeakEnabled,
                    onCheckedChange = onSetAutoSpeak,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = OnBrandLight,
                        checkedTrackColor = AccentEmerald,
                        uncheckedThumbColor = SurfaceCard,
                        uncheckedTrackColor = BorderLight
                    ),
                    modifier = Modifier.testTag("tools_auto_speak_switch")
                )
            }
        }

        // Playback Speed Chips
        Text(
            text = "Playback Speed",
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EdgeVoicePreset.SPEED_PRESETS.forEach { speedOption ->
                val isSelected = kotlin.math.abs(settings.ttsSpeed - speedOption) < 0.05f
                Surface(
                    color = if (isSelected) BrandIndigo else SurfaceBackground,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (isSelected) BrandIndigo else BorderLight),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectSpeed(speedOption) }
                        .testTag("tools_speed_${EdgeVoicePreset.formatSpeed(speedOption)}")
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = EdgeVoicePreset.formatSpeed(speedOption),
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) OnBrandLight else TextPrimary
                        )
                    }
                }
            }
        }

        // Voice Preset Selector
        Text(
            text = "Edge-TTS Neural Voice",
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary
        )
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            EdgeVoicePreset.entries.forEach { preset ->
                val isSelected = settings.selectedVoiceId == preset.voiceId
                Surface(
                    color = if (isSelected) BrandIndigoContainer else SurfaceBackground,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) BrandIndigo else BorderLight
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectVoice(preset.voiceId) }
                        .testTag("tools_voice_${preset.name.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = null,
                            tint = if (isSelected) BrandIndigo else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = preset.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = preset.voiceId,
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isSelected) BrandIndigo else TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        val isSpeakingNow = ttsState.isSpeaking || ttsState.isPreparing
        Button(
            onClick = { if (isSpeakingNow) onStopSpeaking() else onTestVoice() },
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandIndigo,
                contentColor = OnBrandLight
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .testTag("tools_voice_test_button")
        ) {
            Icon(
                imageVector = if (isSpeakingNow) Icons.Filled.Stop else Icons.Filled.VolumeUp,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isSpeakingNow) "Stop Voice Playback" else "Test Selected Voice",
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun Phase4ToolsCardContainer(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    headerTrailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Surface(
        color = SurfaceCard,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, BorderLight),
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 680.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(iconTint.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                headerTrailing?.invoke()
            }

            HorizontalDivider(thickness = 1.dp, color = BorderLight)
            content()
        }
    }
}
