package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.EdgeVoicePreset
import com.example.domain.model.IdeSettings
import com.example.domain.model.TtsPlaybackState
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentEmeraldBorder
import com.example.ui.theme.AccentEmeraldContainer
import com.example.ui.theme.BorderLight
import com.example.ui.theme.BrandIndigo
import com.example.ui.theme.BrandIndigoBorder
import com.example.ui.theme.BrandIndigoContainer
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.OnBrandLight
import com.example.ui.theme.SurfaceBackground
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsBottomSheet(
    settings: IdeSettings,
    ttsState: TtsPlaybackState,
    onToggleAutoSpeak: (Boolean) -> Unit,
    onSelectVoice: (voiceId: String) -> Unit,
    onSelectSpeed: (speed: Float) -> Unit,
    onTestVoice: () -> Unit,
    onStopSpeaking: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceCard,
        contentColor = TextPrimary,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column {
                Text(
                    text = "Edge-TTS Voice & Speed",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                Text(
                    text = "Neural Microsoft Edge voices with automatic offline TTS fallback",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            HorizontalDivider(thickness = 1.dp, color = BorderLight)

            // Auto-Speak Switch Row
            Surface(
                color = if (settings.autoSpeakEnabled) AccentEmeraldContainer else SurfaceBackground,
                shape = RoundedCornerShape(14.dp),
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
                            text = "Auto-Speak AI Responses",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = "Read every new AI reply aloud automatically",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = settings.autoSpeakEnabled,
                        onCheckedChange = onToggleAutoSpeak,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = OnBrandLight,
                            checkedTrackColor = AccentEmerald,
                            uncheckedThumbColor = SurfaceCard,
                            uncheckedTrackColor = BorderLight
                        ),
                        modifier = Modifier.testTag("sheet_auto_speak_switch")
                    )
                }
            }

            // Playback Speed Presets
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
                    val selected = kotlin.math.abs(settings.ttsSpeed - speedOption) < 0.05f
                    Surface(
                        color = if (selected) BrandIndigo else SurfaceBackground,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (selected) BrandIndigo else BorderLight),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectSpeed(speedOption) }
                            .testTag("sheet_speed_${EdgeVoicePreset.formatSpeed(speedOption)}")
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = EdgeVoicePreset.formatSpeed(speedOption),
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) OnBrandLight else TextPrimary
                            )
                        }
                    }
                }
            }

            // Voice Presets
            Text(
                text = "Neural Voice",
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EdgeVoicePreset.entries.forEach { preset ->
                    val selected = settings.selectedVoiceId == preset.voiceId
                    Surface(
                        color = if (selected) BrandIndigoContainer else SurfaceBackground,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) BrandIndigo else BorderLight
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectVoice(preset.voiceId) }
                            .testTag("sheet_voice_${preset.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) BrandIndigo else SurfaceCard),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.GraphicEq,
                                    contentDescription = null,
                                    tint = if (selected) OnBrandLight else BrandIndigo,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary
                                )
                                Text(
                                    text = preset.voiceId,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                            Icon(
                                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (selected) BrandIndigo else TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Test Voice / Stop Button
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
                    .height(48.dp)
                    .testTag("sheet_voice_test_button")
            ) {
                Icon(
                    imageVector = if (isSpeakingNow) Icons.Filled.Stop else Icons.Filled.VolumeUp,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isSpeakingNow) "Stop Playback" else "Test Selected Voice",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
