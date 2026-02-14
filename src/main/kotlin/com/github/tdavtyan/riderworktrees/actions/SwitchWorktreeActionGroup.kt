package com.github.tdavtyan.riderworktrees.actions

import com.github.tdavtyan.riderworktrees.model.WorktreeInfo
import com.github.tdavtyan.riderworktrees.services.WorktreeService
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.ProjectManager

class SwitchWorktreeActionGroup : ActionGroup(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val project = e?.project ?: return emptyArray()
        val service = WorktreeService.getInstance(project)
        val worktrees = service.getCachedWorktrees() ?: return emptyArray()

        return worktrees
            .filter { !it.isCurrent }
            .map { SwitchToWorktreeAction(it) }
            .toTypedArray()
    }

    private class SwitchToWorktreeAction(
        private val worktree: WorktreeInfo,
    ) : AnAction(worktree.displayName + "  (${worktree.shortHash})"), DumbAware {

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

        override fun actionPerformed(e: AnActionEvent) {
            // Check if already open
            val openProjects = ProjectManager.getInstance().openProjects
            val alreadyOpen = openProjects.find { p ->
                p.basePath?.let { java.nio.file.Paths.get(it) } == worktree.path
            }

            if (alreadyOpen != null) {
                val frame = com.intellij.openapi.wm.WindowManager.getInstance().getFrame(alreadyOpen)
                frame?.toFront()
            } else {
                ProjectUtil.openOrImport(worktree.path, null, true)
            }
        }
    }
}
