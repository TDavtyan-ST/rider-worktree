package com.github.tdavtyan.riderworktrees.model

import java.nio.file.Path

data class FileChange(
    val relativePath: String,
    val status: Status,
    val worktreePath: Path,
) {
    val absolutePath: Path
        get() = worktreePath.resolve(relativePath)

    val fileName: String
        get() = relativePath.substringAfterLast('/')

    enum class Status(val label: String, val shortCode: String) {
        ADDED("Added", "A"),
        MODIFIED("Modified", "M"),
        DELETED("Deleted", "D"),
        RENAMED("Renamed", "R"),
        COPIED("Copied", "C"),
        UNTRACKED("Untracked", "?"),
        ;

        companion object {
            fun fromGitCode(code: String): Status = when {
                code.startsWith("A") || code == "??" -> if (code == "??") UNTRACKED else ADDED
                code.startsWith("M") || code.endsWith("M") -> MODIFIED
                code.startsWith("D") || code.endsWith("D") -> DELETED
                code.startsWith("R") -> RENAMED
                code.startsWith("C") -> COPIED
                else -> MODIFIED
            }
        }
    }
}
