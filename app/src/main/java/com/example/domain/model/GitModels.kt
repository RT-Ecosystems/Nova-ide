package com.example.domain.model

data class GitRepoInfo(
    val projectName: String,
    val absolutePath: String,
    val branchName: String,
    val remoteUrl: String?,
    val changedFilesCount: Int
)

data class GitCloneProgress(
    val isRunning: Boolean = false,
    val taskTitle: String = "",
    val completedUnits: Int = 0,
    val totalUnits: Int = 0,
    val statusMessage: String? = null,
    val isError: Boolean = false,
    val lastClonedRelativePath: String? = null
) {
    val fraction: Float?
        get() = if (totalUnits > 0) (completedUnits.toFloat() / totalUnits.toFloat()).coerceIn(0f, 1f) else null
}

data class GitPushState(
    val isRunning: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false
)

sealed class GitOperationResult {
    data class Success(val message: String, val path: String? = null) : GitOperationResult()
    data class Failure(val message: String) : GitOperationResult()
}
