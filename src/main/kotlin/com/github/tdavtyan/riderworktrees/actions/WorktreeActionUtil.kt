package com.github.tdavtyan.riderworktrees.actions

import com.github.tdavtyan.riderworktrees.ui.WorktreePanel
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager

object WorktreeActionUtil {

    fun getWorktreePanel(e: AnActionEvent): WorktreePanel? {
        val project = e.project ?: return null
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Git Worktrees") ?: return null
        val content = toolWindow.contentManager.getContent(0) ?: return null
        return content.component as? WorktreePanel
    }
}
