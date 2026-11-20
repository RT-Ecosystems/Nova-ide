package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.GitCloneProgress
import com.example.domain.model.ProjectTemplate
import com.example.domain.model.WorkspaceItem
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentEmeraldBorder
import com.example.ui.theme.AccentEmeraldContainer
import com.example.ui.theme.AccentRose
import com.example.ui.theme.AccentRoseContainer
import com.example.ui.theme.BorderLight
import com.example.ui.theme.BrandIndigo
import com.example.ui.theme.BrandIndigoBorder
import com.example.ui.theme.BrandIndigoContainer
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.OnBrandLight
import com.example.ui.theme.SurfaceBackground
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPlaceholder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, template: ProjectTemplate) -> Unit,
    onImportFromGithub: () -> Unit = {}
) {
    var name by remember { mutableStateOf("") }
    var selectedTemplate by remember { mutableStateOf(ProjectTemplate.WEB_STARTER) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "Create Project",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Stored inside internal workspace /projects/",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Project folder name") },
                    placeholder = { Text("my-web-app") },
                    singleLine = true,
                    colors = ideTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_project_name_input")
                )
                Text(
                    text = "Starter Template",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProjectTemplate.entries.forEach { template ->
                        val isSelected = selectedTemplate == template
                        Surface(
                            color = if (isSelected) BrandIndigoContainer else SurfaceBackground,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) BrandIndigo else BorderLight
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedTemplate = template }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = template.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (isSelected) BrandIndigo else TextPrimary
                                    )
                                    Text(
                                        text = template.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                                Surface(
                                    color = SurfaceCard,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) BrandIndigoBorder else BorderLight
                                    )
                                ) {
                                    Text(
                                        text = template.badge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) BrandIndigo else TextSecondary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(thickness = 1.dp, color = BorderLight)

                OutlinedButton(
                    onClick = onImportFromGithub,
                    border = BorderStroke(1.dp, AccentEmeraldBorder),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = AccentEmeraldContainer.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_create_project_import_github")
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudDownload,
                        contentDescription = null,
                        tint = AccentEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Import from GitHub",
                        color = AccentEmerald,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, selectedTemplate) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandIndigo,
                    contentColor = OnBrandLight
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("dialog_create_project_confirm")
            ) {
                Text("Create Project", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
fun ImportFromGithubDialog(
    cloneProgress: GitCloneProgress,
    hasGithubPat: Boolean,
    onDismiss: () -> Unit,
    onImport: (repoUrl: String, localFolderName: String) -> Unit
) {
    var repoUrl by remember { mutableStateOf("") }
    var localFolderName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!cloneProgress.isRunning) onDismiss() },
        containerColor = SurfaceCard,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Import from GitHub",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = if (hasGithubPat) AccentEmeraldContainer else SurfaceElevated,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, if (hasGithubPat) AccentEmeraldBorder else BorderLight)
                ) {
                    Text(
                        text = if (hasGithubPat) "PAT Ready" else "Public",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hasGithubPat) AccentEmerald else TextSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (hasGithubPat) {
                        "Clone any public or private GitHub repository into /projects/ using your saved PAT."
                    } else {
                        "Clone any public GitHub repository into /projects/ with zero login required."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = repoUrl,
                    onValueChange = { repoUrl = it },
                    label = { Text("GitHub Repo URL") },
                    placeholder = { Text("https://github.com/owner/repo") },
                    singleLine = true,
                    enabled = !cloneProgress.isRunning,
                    colors = ideTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_github_url_input")
                )
                OutlinedTextField(
                    value = localFolderName,
                    onValueChange = { localFolderName = it },
                    label = { Text("Local Folder Name (optional)") },
                    placeholder = { Text("Auto-derived from repository") },
                    singleLine = true,
                    enabled = !cloneProgress.isRunning,
                    colors = ideTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_github_folder_input")
                )

                AnimatedVisibility(visible = cloneProgress.isRunning || (cloneProgress.isError && !cloneProgress.statusMessage.isNullOrBlank())) {
                    Surface(
                        color = if (cloneProgress.isError) AccentRoseContainer else SurfaceBackground,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (cloneProgress.isError) AccentRose.copy(alpha = 0.4f) else BorderLight
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (cloneProgress.isRunning) {
                                Text(
                                    text = cloneProgress.taskTitle.ifBlank { "Cloning repository..." },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = BrandIndigo
                                )
                                val frac = cloneProgress.fraction
                                if (frac != null) {
                                    LinearProgressIndicator(
                                        progress = { frac },
                                        color = BrandIndigo,
                                        trackColor = BrandIndigoContainer,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(999.dp))
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        color = BrandIndigo,
                                        trackColor = BrandIndigoContainer,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(999.dp))
                                    )
                                }
                            } else if (cloneProgress.isError && !cloneProgress.statusMessage.isNullOrBlank()) {
                                Text(
                                    text = cloneProgress.statusMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AccentRose
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(repoUrl.trim(), localFolderName.trim()) },
                enabled = repoUrl.isNotBlank() && !cloneProgress.isRunning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandIndigo,
                    contentColor = OnBrandLight
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("dialog_github_import_confirm")
            ) {
                if (cloneProgress.isRunning) {
                    CircularProgressIndicator(
                        color = OnBrandLight,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cloning...", fontWeight = FontWeight.SemiBold)
                } else {
                    Text("Import", fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !cloneProgress.isRunning
            ) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateEntryDialog(
    initialIsDirectory: Boolean,
    parentLabel: String,
    onDismiss: () -> Unit,
    onCreate: (name: String, isDirectory: Boolean) -> Unit
) {
    var isDirectory by remember { mutableStateOf(initialIsDirectory) }
    var name by remember { mutableStateOf("") }
    val quickExtensions = remember { listOf(".html", ".css", ".js", ".kt", ".py", ".json", ".md") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = if (isDirectory) "New Folder" else "New File",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Location: /$parentLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandIndigo
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isDirectory,
                        onClick = { isDirectory = false },
                        label = { Text("File") },
                        colors = ideFilterChipColors()
                    )
                    FilterChip(
                        selected = isDirectory,
                        onClick = { isDirectory = true },
                        label = { Text("Folder") },
                        colors = ideFilterChipColors()
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (isDirectory) "Folder name" else "File name") },
                    placeholder = { Text(if (isDirectory) "components" else "script.js") },
                    singleLine = true,
                    colors = ideTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_entry_name_input")
                )
                if (!isDirectory) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickExtensions.forEach { ext ->
                            Surface(
                                color = AccentEmeraldContainer,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.25f)),
                                modifier = Modifier.clickable {
                                    val base = name.substringBeforeLast('.').ifBlank { "untitled" }
                                    name = "$base$ext"
                                }
                            ) {
                                Text(
                                    text = ext,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AccentEmerald,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, isDirectory) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandIndigo,
                    contentColor = OnBrandLight
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("dialog_create_entry_confirm")
            ) {
                Text("Create", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
fun RenameEntryDialog(
    item: WorkspaceItem,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var newName by remember(item.relativePath) { mutableStateOf(item.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        titleContentColor = TextPrimary,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Rename ${if (item.isDirectory) "Folder" else "File"}") },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("New name") },
                singleLine = true,
                colors = ideTextFieldColors(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dialog_rename_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { onRename(newName) },
                enabled = newName.isNotBlank() && newName.trim() != item.name,
                colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo, contentColor = OnBrandLight),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("dialog_rename_confirm")
            ) {
                Text("Rename", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
fun DeleteConfirmDialog(
    item: WorkspaceItem,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text("Delete ${if (item.isDirectory) "Folder" else "File"}?")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "This permanently removes \"${item.name}\" from your internal workspace.",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (item.isDirectory) {
                    Text(
                        text = "All nested files and subfolders inside it will also be deleted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentRose
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                colors = ButtonDefaults.buttonColors(containerColor = AccentRose, contentColor = OnBrandLight),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("dialog_delete_confirm")
            ) {
                Text("Delete", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
fun MissingPatPromptDialog(
    onDismiss: () -> Unit,
    onAddPatInSettings: () -> Unit,
    onSkipAndCommitLocally: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        shape = RoundedCornerShape(20.dp),
        title = { Text("GitHub PAT Not Set") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Pushing commits to GitHub requires a Personal Access Token (PAT). Adding a token is completely optional — you can still commit your work locally right now.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onSkipAndCommitLocally,
                        border = BorderStroke(1.dp, BorderLight),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Skip • Commit Local", color = TextPrimary)
                    }
                    Button(
                        onClick = onAddPatInSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo, contentColor = OnBrandLight),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Add PAT", fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        }
    )
}

@Composable
fun ideTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = BrandIndigo,
    unfocusedBorderColor = BorderLight,
    focusedLabelColor = BrandIndigo,
    unfocusedLabelColor = TextSecondary,
    cursorColor = BrandIndigo,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedContainerColor = SurfaceBackground,
    unfocusedContainerColor = SurfaceBackground,
    focusedPlaceholderColor = TextPlaceholder,
    unfocusedPlaceholderColor = TextPlaceholder
)

@Composable
fun ideFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = SurfaceElevated,
    labelColor = TextSecondary,
    selectedContainerColor = BrandIndigoContainer,
    selectedLabelColor = BrandIndigo
)
