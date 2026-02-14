package com.github.tdavtyan.riderworktrees.services

import com.github.tdavtyan.riderworktrees.model.FileChange
import com.github.tdavtyan.riderworktrees.model.WorktreeInfo
import com.intellij.openapi.project.Project
import java.nio.file.Path

interface WorktreeService {

    fun listWorktrees(): List<WorktreeInfo>

    fun getCachedWorktrees(): List<WorktreeInfo>?

    fun getWorktreeStatus(worktree: WorktreeInfo): List<FileChange>

    fun getFileDiff(fileChange: FileChange): String

    fun createWorktree(branch: String, path: Path, createNewBranch: Boolean): Result<Unit>

    fun removeWorktree(info: WorktreeInfo, force: Boolean = false): Result<Unit>

    fun lockWorktree(info: WorktreeInfo, reason: String? = null): Result<Unit>

    fun unlockWorktree(info: WorktreeInfo): Result<Unit>

    fun pruneWorktrees(): Result<String>

    fun getDefaultWorktreePath(branchName: String): Path?

    fun getAvailableBranches(): List<String>

    fun invalidateCache()

    companion object {
        fun getInstance(project: Project): WorktreeService {
            return project.getService(WorktreeService::class.java)
        }
    }
}
