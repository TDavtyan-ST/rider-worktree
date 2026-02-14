package com.github.tdavtyan.riderworktrees.actions

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.ProjectManager

class OpenWorktreeAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val panel = WorktreeActionUtil.getWorktreePanel(e)
        val selected = panel?.getSelectedWorktree()
        e.presentation.isEnabled = selected != null && !selected.isCurrent
    }

    override fun actionPerformed(e: AnActionEvent) {
        val panel = WorktreeActionUtil.getWorktreePanel(e) ?: return
        val selected = panel.getSelectedWorktree() ?: return

        if (selected.isCurrent) return

        // Check if already open
        val openProjects = ProjectManager.getInstance().openProjects
        val alreadyOpen = openProjects.find { project ->
            project.basePath?.let { java.nio.file.Paths.get(it) } == selected.path
        }

        if (alreadyOpen != null) {
            // Focus the existing window
            val frame = com.intellij.openapi.wm.WindowManager.getInstance().getFrame(alreadyOpen)
            frame?.toFront()
        } else {
            ProjectUtil.openOrImport(selected.path, null, true)
        }
    }
}
