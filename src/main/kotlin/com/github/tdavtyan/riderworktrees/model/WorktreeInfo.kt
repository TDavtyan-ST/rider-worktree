package com.github.tdavtyan.riderworktrees.model

import java.nio.file.Path

data class WorktreeInfo(
    val path: Path,
    val branch: String?,
    val commitHash: String,
    val isMain: Boolean,
    val isCurrent: Boolean,
    val isLocked: Boolean,
    val isPrunable: Boolean,
) {
    val displayName: String
        get() = branch?.removePrefix("refs/heads/") ?: "detached HEAD"

    val shortHash: String
        get() = if (commitHash.length >= 7) commitHash.take(7) else commitHash

    val statusText: String
        get() = buildList {
            if (isMain) add("main")
            if (isCurrent) add("current")
            if (isLocked) add("locked")
            if (isPrunable) add("prunable")
        }.joinToString(", ")

    val directoryName: String
        get() = path.fileName?.toString() ?: path.toString()
}
