package com.github.tdavtyan.riderworktrees.ui

import com.github.tdavtyan.riderworktrees.services.WorktreeService
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class WorktreeFrameTitleBuilder : com.intellij.openapi.wm.impl.FrameTitleBuilder() {

    override fun getProjectTitle(project: Project): String {
        val service = WorktreeService.getInstance(project)
        val worktrees = service.getCachedWorktrees() ?: service.listWorktrees()

        if (worktrees.isEmpty()) {
            return project.name
        }

        val currentWorktree = worktrees.find { it.isCurrent }
        return if (currentWorktree != null && !currentWorktree.isMain) {
            // Show worktree branch name for non-main worktrees
            val branchName = currentWorktree.displayName
            "${project.name} [$branchName]"
        } else {
            project.name
        }
    }

    override fun getFileTitle(project: Project, file: VirtualFile): String {
        return file.presentableName
    }
}
