package com.example.core.git

import com.example.core.files.WorkspaceFileManager
import com.example.domain.model.GitOperationResult
import com.example.domain.model.GitRepoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File

class JGitManager(private val fileManager: WorkspaceFileManager) {

    fun deriveFolderName(repoUrl: String): String {
        val cleaned = repoUrl.trim().trimEnd('/')
        val segment = cleaned.substringAfterLast('/').removeSuffix(".git")
        val sanitized = segment.replace(Regex("[^A-Za-z0-9._-]"), "-")
        return sanitized.ifBlank { "cloned-repo" }
    }

    suspend fun cloneRepository(
        repoUrl: String,
        customFolderName: String,
        optionalPat: String?,
        onProgress: (taskTitle: String, completed: Int, total: Int) -> Unit
    ): GitOperationResult = withContext(Dispatchers.IO) {
        val trimmedUrl = repoUrl.trim()
        if (trimmedUrl.isBlank()) {
            return@withContext GitOperationResult.Failure("Please enter a GitHub repository URL.")
        }

        val normalizedUrl = normalizeGitUrl(trimmedUrl)
        val folderName = try {
            val chosen = customFolderName.trim().ifBlank { deriveFolderName(normalizedUrl) }
            fileManager.sanitizeEntryName(chosen)
        } catch (e: IllegalArgumentException) {
            return@withContext GitOperationResult.Failure(e.message ?: "Invalid folder name.")
        }

        val targetDir = try {
            fileManager.resolveSafe(folderName)
        } catch (e: Exception) {
            return@withContext GitOperationResult.Failure(e.message ?: "Invalid destination directory.")
        }

        if (targetDir.exists() && targetDir.list()?.isNotEmpty() == true) {
            return@withContext GitOperationResult.Failure("Folder \"$folderName\" already exists and is not empty.")
        }

        val monitor = object : ProgressMonitor {
            private var currentTask = "Connecting to remote..."
            private var totalWork = 0
            private var completedWork = 0

            override fun start(totalTasks: Int) {
                onProgress("Preparing clone ($totalTasks steps)...", 0, 0)
            }

            override fun beginTask(title: String?, totalWork: Int) {
                currentTask = title?.takeIf { it.isNotBlank() } ?: "Cloning repository..."
                this.totalWork = if (totalWork == ProgressMonitor.UNKNOWN) 0 else totalWork
                this.completedWork = 0
                onProgress(currentTask, 0, this.totalWork)
            }

            override fun update(completed: Int) {
                completedWork += completed
                onProgress(currentTask, completedWork, totalWork)
            }

            override fun endTask() {
                if (totalWork > 0) {
                    onProgress(currentTask, totalWork, totalWork)
                }
            }

            override fun isCancelled(): Boolean = false
        }

        try {
            onProgress("Connecting to GitHub...", 0, 0)
            val cloneCommand = Git.cloneRepository()
                .setURI(normalizedUrl)
                .setDirectory(targetDir)
                .setProgressMonitor(monitor)

            if (!optionalPat.isNullOrBlank()) {
                cloneCommand.setCredentialsProvider(
                    UsernamePasswordCredentialsProvider("x-access-token", optionalPat.trim())
                )
            }

            cloneCommand.call().use { git ->
                val branch = git.repository.branch ?: "main"
                GitOperationResult.Success(
                    message = "Cloned \"$folderName\" (branch: $branch)",
                    path = fileManager.relativePathOf(targetDir)
                )
            }
        } catch (e: Exception) {
            if (targetDir.exists() && !File(targetDir, ".git").exists()) {
                targetDir.deleteRecursively()
            }
            val friendly = e.cause?.localizedMessage ?: e.localizedMessage ?: "Clone failed."
            GitOperationResult.Failure("Clone failed: $friendly")
        }
    }

    suspend fun discoverRepositories(): List<GitRepoInfo> = withContext(Dispatchers.IO) {
        val root = fileManager.ensureWorkspaceRoot()
        val topDirs = root.listFiles().orEmpty().filter { it.isDirectory && File(it, ".git").exists() }
        topDirs.mapNotNull { dir ->
            runCatching {
                Git.open(dir).use { git ->
                    val branch = git.repository.branch ?: "HEAD"
                    val remoteUrl = git.repository.config.getString("remote", "origin", "url")
                    val status = git.status().call()
                    val changedCount = status.uncommittedChanges.size + status.untracked.size
                    GitRepoInfo(
                        projectName = dir.name,
                        absolutePath = dir.canonicalPath,
                        branchName = branch,
                        remoteUrl = remoteUrl,
                        changedFilesCount = changedCount
                    )
                }
            }.getOrNull()
        }.sortedBy { it.projectName.lowercase() }
    }

    suspend fun commitLocally(
        repoAbsolutePath: String,
        commitMessage: String
    ): GitOperationResult = withContext(Dispatchers.IO) {
        val message = commitMessage.trim()
        if (message.isEmpty()) {
            return@withContext GitOperationResult.Failure("Please enter a commit message.")
        }

        try {
            val repoDir = File(repoAbsolutePath)
            Git.open(repoDir).use { git ->
                git.add().addFilepattern(".").call()
                git.add().setUpdate(true).addFilepattern(".").call()
                val status = git.status().call()
                if (status.isClean) {
                    return@withContext GitOperationResult.Success("Working tree clean — nothing new to commit.")
                }
                val commit = git.commit()
                    .setMessage(message)
                    .setAuthor("Nova IDE Developer", "dev@novaide.local")
                    .setCommitter("Nova IDE Developer", "dev@novaide.local")
                    .call()
                val shortId = commit.id.abbreviate(7).name()
                GitOperationResult.Success("Committed locally ($shortId): \"$message\"")
            }
        } catch (e: Exception) {
            GitOperationResult.Failure("Commit failed: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    suspend fun commitAndPush(
        repoAbsolutePath: String,
        commitMessage: String,
        githubPat: String
    ): GitOperationResult = withContext(Dispatchers.IO) {
        val token = githubPat.trim()
        if (token.isEmpty()) {
            return@withContext GitOperationResult.Failure("GitHub Personal Access Token (PAT) is required to push.")
        }

        try {
            val repoDir = File(repoAbsolutePath)
            Git.open(repoDir).use { git ->
                git.add().addFilepattern(".").call()
                git.add().setUpdate(true).addFilepattern(".").call()
                val status = git.status().call()
                var commitSummary = ""
                if (!status.isClean && commitMessage.isNotBlank()) {
                    val commit = git.commit()
                        .setMessage(commitMessage.trim())
                        .setAuthor("Nova IDE Developer", "dev@novaide.local")
                        .setCommitter("Nova IDE Developer", "dev@novaide.local")
                        .call()
                    commitSummary = "Committed ${commit.id.abbreviate(7).name()} & "
                }

                val credentials = UsernamePasswordCredentialsProvider("x-access-token", token)
                val pushResults = git.push()
                    .setCredentialsProvider(credentials)
                    .call()

                val rejected = pushResults
                    .flatMap { it.remoteUpdates }
                    .firstOrNull { update ->
                        update.status.name.contains("REJECTED")
                    }

                if (rejected != null) {
                    GitOperationResult.Failure(
                        "Push rejected (${rejected.status}): ${rejected.message ?: "Pull remote changes first."}"
                    )
                } else {
                    GitOperationResult.Success("${commitSummary}pushed to origin/${git.repository.branch}.")
                }
            }
        } catch (e: Exception) {
            val detail = e.cause?.localizedMessage ?: e.localizedMessage ?: "Authentication or network error."
            GitOperationResult.Failure("Push failed: $detail")
        }
    }

    private fun normalizeGitUrl(raw: String): String {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("https://") || trimmed.startsWith("http://") -> trimmed
            trimmed.startsWith("github.com/") -> "https://$trimmed"
            Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$").matches(trimmed) -> "https://github.com/$trimmed.git"
            else -> trimmed
        }
    }
}
