package com.example.ui.screens

import android.graphics.Typeface
import android.view.ViewGroup
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.core.editor.CodeLanguageResolver
import com.example.core.editor.LightDeveloperColorScheme
import com.example.domain.model.EditorTabState
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentEmeraldContainer
import com.example.ui.theme.BorderLight
import com.example.ui.theme.BrandIndigo
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
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor

@Composable
fun EditorScreen(
    state: EditorTabState,
    onContentChanged: (content: String, lineCount: Int, canUndo: Boolean, canRedo: Boolean) -> Unit,
    onCursorChanged: (line: Int, column: Int) -> Unit,
    onSaveClick: () -> Unit,
    onRunPreviewClick: () -> Unit,
    onCycleFontSize: () -> Unit,
    onBrowseFilesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editorInstance by remember { mutableStateOf<CodeEditor?>(null) }
    var lastBoundFilePath by remember { mutableStateOf<String?>(null) }
    var lastBoundRevision by remember { mutableLongStateOf(-1L) }
    var isProgrammaticSync by remember { mutableStateOf(false) }

    val latestOnContentChanged by rememberUpdatedState(onContentChanged)
    val latestOnCursorChanged by rememberUpdatedState(onCursorChanged)

    DisposableEffect(Unit) {
        onDispose {
            editorInstance?.release()
            editorInstance = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceBackground)
            .statusBarsPadding()
            .imePadding()
    ) {
        // Compact Light IDE Editor Toolbar
        Surface(
            color = SurfaceCard,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Active filename + Unsaved indicator dot
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Code,
                            contentDescription = null,
                            tint = BrandIndigo,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (state.hasOpenFile) state.fileName else "Editor",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .testTag("editor_active_filename")
                        )
                        if (state.isDirty) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(AccentAmber)
                                    .testTag("editor_unsaved_dot")
                            )
                        }
                    }

                    // Undo / Redo / Font Size / Quick Play Preview / Save
                    if (state.hasOpenFile) {
                        IconButton(
                            onClick = { editorInstance?.undo() },
                            enabled = state.canUndo,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("editor_undo_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = stringResource(R.string.editor_undo),
                                tint = if (state.canUndo) TextPrimary else TextPlaceholder,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = { editorInstance?.redo() },
                            enabled = state.canRedo,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("editor_redo_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Redo,
                                contentDescription = stringResource(R.string.editor_redo),
                                tint = if (state.canRedo) TextPrimary else TextPlaceholder,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Surface(
                            color = SurfaceElevated,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, BorderLight),
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onCycleFontSize)
                                .testTag("editor_font_size_toggle")
                        ) {
                            Text(
                                text = "${state.fontSizeSp.toInt()}sp",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
                            )
                        }

                        // Quick Run / Preview Button
                        IconButton(
                            onClick = onRunPreviewClick,
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("editor_run_preview_button")
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Filled.PlayArrow,
                                contentDescription = stringResource(R.string.editor_run_preview),
                                tint = AccentEmerald,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Button(
                            onClick = onSaveClick,
                            enabled = !state.isSaving,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.isDirty) BrandIndigo else AccentEmeraldContainer,
                                contentColor = if (state.isDirty) OnBrandLight else AccentEmerald
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("editor_save_button")
                        ) {
                            Icon(
                                imageVector = if (state.isDirty) Icons.Filled.Save else Icons.Filled.Check,
                                contentDescription = stringResource(R.string.editor_save),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (state.isDirty) stringResource(R.string.editor_save) else "Saved",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Slim Light IDE Status Strip
                if (state.hasOpenFile) {
                    HorizontalDivider(thickness = 1.dp, color = BorderLight)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceBackground)
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = state.relativePath,
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${state.languageLabel}  •  Ln ${state.cursorLine}, Col ${state.cursorColumn}",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandIndigo
                        )
                    }
                }

                HorizontalDivider(thickness = 1.dp, color = BorderLight)
            }
        }

        if (!state.hasOpenFile) {
            EditorEmptyState(
                onBrowseFilesClick = onBrowseFilesClick,
                modifier = Modifier.weight(1f)
            )
        } else {
            // Sora Editor embedded inside Compose with LightDeveloperColorScheme
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(SurfaceCard)
            ) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("sora_code_editor_view"),
                    factory = { ctx ->
                        val monoTypeface = ResourcesCompat.getFont(ctx, R.font.jetbrains_mono)
                            ?: Typeface.MONOSPACE

                        CodeEditor(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            colorScheme = LightDeveloperColorScheme()
                            typefaceText = monoTypeface
                            typefaceLineNumber = monoTypeface
                            setTextSize(state.fontSizeSp)
                            isWordwrap = false
                            setLineNumberEnabled(true)
                            setPinLineNumber(true)
                            tabWidth = 2

                            subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
                                if (!isProgrammaticSync) {
                                    latestOnContentChanged(
                                        text.toString(),
                                        text.lineCount,
                                        canUndo(),
                                        canRedo()
                                    )
                                }
                            }

                            subscribeEvent(SelectionChangeEvent::class.java) { event, _ ->
                                latestOnCursorChanged(event.left.line, event.left.column)
                            }

                            editorInstance = this
                        }
                    },
                    update = { editor ->
                        editor.setTextSize(state.fontSizeSp)

                        val needsReload =
                            lastBoundFilePath != state.filePath || lastBoundRevision != state.contentRevision

                        if (needsReload) {
                            isProgrammaticSync = true
                            try {
                                editor.setEditorLanguage(CodeLanguageResolver.resolveLanguage(state.extension))
                                editor.setText(state.currentContent)
                                lastBoundFilePath = state.filePath
                                lastBoundRevision = state.contentRevision
                            } finally {
                                isProgrammaticSync = false
                            }
                        }
                    }
                )
            }

            // Compact Light Mobile Coding Symbol Strip
            SymbolAccessoryBar(
                onInsertSymbol = { symbol ->
                    editorInstance?.let { editor ->
                        val insertValue = if (symbol == "TAB") "  " else symbol
                        editor.insertText(insertValue, insertValue.length)
                    }
                }
            )
        }
    }
}

@Composable
private fun SymbolAccessoryBar(
    onInsertSymbol: (String) -> Unit
) {
    val symbols = remember {
        listOf("TAB", "{", "}", "(", ")", "[", "]", "<", ">", "=", ";", ":", "\"", "'", "/", "=>")
    }

    Surface(
        color = SurfaceBackground,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            HorizontalDivider(thickness = 1.dp, color = BorderLight)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                symbols.forEach { symbol ->
                    Surface(
                        color = SurfaceCard,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, BorderLight),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onInsertSymbol(symbol) }
                    ) {
                        Text(
                            text = symbol,
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (symbol == "TAB") BrandIndigo else TextPrimary,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorEmptyState(
    onBrowseFilesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = SurfaceCard,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, BorderLight),
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(BrandIndigoContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Code,
                        contentDescription = null,
                        tint = BrandIndigo,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.editor_no_file_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                Text(
                    text = stringResource(R.string.editor_no_file_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onBrowseFilesClick,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo, contentColor = OnBrandLight),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("editor_browse_workspace_cta")
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.editor_open_files_cta), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
