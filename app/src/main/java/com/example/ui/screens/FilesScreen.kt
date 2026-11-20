package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.GitCloneProgress
import com.example.domain.model.ProjectTemplate
import com.example.domain.model.WorkspaceItem
import com.example.ui.components.CreateEntryDialog
import com.example.ui.components.CreateProjectDialog
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.ImportFromGithubDialog
import com.example.ui.components.RenameEntryDialog
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentEmeraldBorder
import com.example.ui.theme.AccentEmeraldContainer
import com.example.ui.theme.AccentRose
import com.example.ui.theme.AccentSky
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
import com.example.ui.viewmodel.FilesUiState
import java.util.Locale

@Composable
fun FilesScreen(
    state: FilesUiState,
    cloneProgress: GitCloneProgress,
    hasGithubPat: Boolean,
    onNavigateDirectory: (String) -> Unit,
    onNavigateRoot: () -> Unit,
    onNavigateUp: () -> Boolean,
    onOpenFile: (WorkspaceItem) -> Unit,
    onCreateProject: (name: String, template: ProjectTemplate) -> Unit,
    onImportFromGithub: (repoUrl: String, localFolderName: String) -> Unit,
    onCreateEntry: (name: String, isDirectory: Boolean) -> Unit,
    onRenameEntry: (WorkspaceItem, String) -> Unit,
    onDeleteEntry: (WorkspaceItem) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAtRoot = state.currentRelativePath.isBlank()
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var showImportGithubDialog by remember { mutableStateOf(false) }
    var awaitingGithubClone by remember { mutableStateOf(false) }
    var createEntryIsDir by remember { mutableStateOf<Boolean?>(null) }
    var itemToRename by remember { mutableStateOf<WorkspaceItem?>(null) }
    var itemToDelete by remember { mutableStateOf<WorkspaceItem?>(null) }

    LaunchedEffect(
        awaitingGithubClone,
        cloneProgress.isRunning,
        cloneProgress.isError,
        cloneProgress.lastClonedRelativePath
    ) {
        if (awaitingGithubClone && !cloneProgress.isRunning) {
            if (!cloneProgress.isError && !cloneProgress.lastClonedRelativePath.isNullOrBlank()) {
                val targetPath = cloneProgress.lastClonedRelativePath
                awaitingGithubClone = false
                showImportGithubDialog = false
                onNavigateDirectory(targetPath)
            } else if (cloneProgress.isError) {
                awaitingGithubClone = false
            }
        }
    }

    BackHandler(enabled = !isAtRoot) {
        onNavigateUp()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceBackground)
            .statusBarsPadding()
    ) {
        // Crisp Light IDE Header
        Surface(
            color = SurfaceCard,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (!isAtRoot) {
                        IconButton(
                            onClick = { onNavigateUp() },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("files_up_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Navigate up",
                                tint = BrandIndigo
                            )
                        }
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.files_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = "/projects/${state.currentRelativePath}",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.testTag("files_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh workspace",
                            tint = TextSecondary
                        )
                    }
                    IconButton(
                        onClick = { showImportGithubDialog = true },
                        modifier = Modifier.testTag("files_clone_shortcut")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudDownload,
                            contentDescription = "Import from GitHub",
                            tint = AccentEmerald
                        )
                    }
                }
            }
        }

        // Breadcrumb Trail with Root Button
        Surface(
            color = SurfaceCard,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                HorizontalDivider(thickness = 1.dp, color = BorderLight)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    state.breadcrumbs.forEachIndexed { index, segment ->
                        val isLast = index == state.breadcrumbs.lastIndex
                        Surface(
                            color = if (isLast) BrandIndigoContainer else SurfaceElevated,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isLast) BrandIndigoBorder else BorderLight
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (segment.relativePath.isEmpty()) onNavigateRoot()
                                    else onNavigateDirectory(segment.relativePath)
                                }
                                .testTag("breadcrumb_${segment.label}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                if (index == 0) {
                                    Icon(
                                        imageVector = Icons.Filled.Home,
                                        contentDescription = "Workspace root",
                                        tint = if (isLast) BrandIndigo else TextSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = segment.label,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isLast) BrandIndigo else TextPrimary
                                )
                            }
                        }

                        if (!isLast) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier
                                    .padding(horizontal = 2.dp)
                                    .size(16.dp)
                            )
                        }
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = BorderLight)
            }
        }

        if (state.isLoading) {
            LinearProgressIndicator(
                color = BrandIndigo,
                trackColor = BrandIndigoContainer,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Quick Action Bar
        if (isAtRoot) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showNewProjectDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandIndigo,
                        contentColor = OnBrandLight
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("files_new_project_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Create Project", fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = { showImportGithubDialog = true },
                    border = BorderStroke(1.dp, AccentEmeraldBorder),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = SurfaceCard),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("files_import_github_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudDownload,
                        contentDescription = null,
                        tint = AccentEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.files_import_github),
                        color = AccentEmerald,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { createEntryIsDir = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandIndigo,
                        contentColor = OnBrandLight
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("files_new_file_button")
                ) {
                    Icon(Icons.Filled.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.files_new_file), fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = { createEntryIsDir = true },
                    border = BorderStroke(1.dp, BorderLight),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = SurfaceCard),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("files_new_folder_button")
                ) {
                    Icon(Icons.Filled.CreateNewFolder, contentDescription = null, tint = BrandIndigo, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.files_new_folder), color = TextPrimary)
                }
            }
        }

        // File List or Empty State
        if (state.items.isEmpty() && !state.isLoading) {
            WorkspaceEmptyState(
                isAtRoot = isAtRoot,
                onPrimaryAction = {
                    if (isAtRoot) showNewProjectDialog = true else createEntryIsDir = false
                },
                onImportGithubAction = { showImportGithubDialog = true },
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = state.items, key = { it.absolutePath }) { item ->
                    WorkspaceItemCard(
                        item = item,
                        onClick = {
                            if (item.isDirectory) {
                                onNavigateDirectory(item.relativePath)
                            } else {
                                onOpenFile(item)
                            }
                        },
                        onRenameClick = { itemToRename = item },
                        onDeleteClick = { itemToDelete = item }
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }

    if (showNewProjectDialog) {
        CreateProjectDialog(
            onDismiss = { showNewProjectDialog = false },
            onCreate = { name, template ->
                showNewProjectDialog = false
                onCreateProject(name, template)
            },
            onImportFromGithub = {
                showNewProjectDialog = false
                showImportGithubDialog = true
            }
        )
    }

    if (showImportGithubDialog) {
        ImportFromGithubDialog(
            cloneProgress = cloneProgress,
            hasGithubPat = hasGithubPat,
            onDismiss = {
                awaitingGithubClone = false
                showImportGithubDialog = false
            },
            onImport = { repoUrl, localFolder ->
                awaitingGithubClone = true
                onImportFromGithub(repoUrl, localFolder)
            }
        )
    }

    createEntryIsDir?.let { isDir ->
        val parentLabel = if (state.currentRelativePath.isBlank()) "projects" else "projects/${state.currentRelativePath}"
        CreateEntryDialog(
            initialIsDirectory = isDir,
            parentLabel = parentLabel,
            onDismiss = { createEntryIsDir = null },
            onCreate = { name, chosenIsDir ->
                createEntryIsDir = null
                onCreateEntry(name, chosenIsDir)
            }
        )
    }

    itemToRename?.let { target ->
        RenameEntryDialog(
            item = target,
            onDismiss = { itemToRename = null },
            onRename = { newName ->
                itemToRename = null
                onRenameEntry(target, newName)
            }
        )
    }

    itemToDelete?.let { target ->
        DeleteConfirmDialog(
            item = target,
            onDismiss = { itemToDelete = null },
            onConfirmDelete = {
                itemToDelete = null
                onDeleteEntry(target)
            }
        )
    }
}

@Composable
private fun WorkspaceItemCard(
    item: WorkspaceItem,
    onClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val (icon, accentColor) = remember(item.isDirectory, item.extension, item.isGitRepo) {
        resolveItemIconAndAccent(item)
    }

    Surface(
        color = SurfaceCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, BorderLight),
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("workspace_item_${item.name}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 11.dp, bottom = 11.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = if (item.isDirectory) "Folder" else "File",
                    tint = accentColor,
                    modifier = Modifier.size(21.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.isGitRepo) {
                        Surface(
                            color = AccentEmeraldContainer,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, AccentEmeraldBorder)
                        ) {
                            Text(
                                text = "GIT",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentEmerald,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                val subtitle = if (item.isDirectory) {
                    if (item.childCount == 1) "1 item" else "${item.childCount} items"
                } else {
                    val extLabel = item.extension.uppercase(Locale.US).ifBlank { "FILE" }
                    "$extLabel • ${formatFileSize(item.sizeBytes)}"
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("workspace_item_menu_${item.name}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "More options for ${item.name}",
                        tint = TextMuted
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = SurfaceCard
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.files_rename), color = TextPrimary) },
                        leadingIcon = {
                            Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null, tint = BrandIndigo)
                        },
                        onClick = {
                            menuExpanded = false
                            onRenameClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.files_delete), color = AccentRose) },
                        leadingIcon = {
                            Icon(Icons.Filled.Delete, contentDescription = null, tint = AccentRose)
                        },
                        onClick = {
                            menuExpanded = false
                            onDeleteClick()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspaceEmptyState(
    isAtRoot: Boolean,
    onPrimaryAction: () -> Unit,
    onImportGithubAction: () -> Unit,
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
                        imageVector = if (isAtRoot) Icons.Filled.FolderSpecial else Icons.Filled.NoteAdd,
                        contentDescription = null,
                        tint = BrandIndigo,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    text = if (isAtRoot) stringResource(R.string.files_empty_title) else "Folder is empty",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                Text(
                    text = if (isAtRoot) {
                        stringResource(R.string.files_empty_subtitle)
                    } else {
                        "Create a new source file or subfolder to start building here."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onPrimaryAction,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo, contentColor = OnBrandLight),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("files_empty_create_project_button")
                ) {
                    Text(
                        text = if (isAtRoot) "Create Project" else "Create New File",
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (isAtRoot) {
                    OutlinedButton(
                        onClick = onImportGithubAction,
                        border = BorderStroke(1.dp, AccentEmeraldBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("files_empty_import_github_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudDownload,
                            contentDescription = null,
                            tint = AccentEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.files_import_github),
                            color = AccentEmerald,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

private fun resolveItemIconAndAccent(item: WorkspaceItem): Pair<ImageVector, Color> {
    if (item.isDirectory) {
        return if (item.isGitRepo) Icons.Filled.FolderSpecial to AccentEmerald else Icons.Filled.Folder to BrandIndigo
    }
    return when (item.extension.lowercase()) {
        "html", "htm", "xml" -> Icons.Filled.Code to AccentAmber
        "css", "scss" -> Icons.Filled.Code to AccentSky
        "js", "jsx", "ts", "tsx" -> Icons.Filled.Code to Color(0xFFCA8A04)
        "kt", "kts", "java" -> Icons.Filled.Code to AccentViolet
        "py" -> Icons.Filled.Code to AccentEmerald
        "json", "yaml", "yml", "toml" -> Icons.Filled.DataObject to BrandIndigo
        else -> Icons.Filled.Description to TextSecondary
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
