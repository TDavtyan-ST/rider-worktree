package com.github.tdavtyan.riderworktrees.actions

import com.github.tdavtyan.riderworktrees.services.WorktreeService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware

class UnlockWorktreeAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val panel = WorktreeActionUtil.getWorktreePanel(e)
        val selected = panel?.getSelectedWorktree()
        e.presentation.isEnabled = selected != null && selected.isLocked
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val panel = WorktreeActionUtil.getWorktreePanel(e) ?: return
        val selected = panel.getSelectedWorktree() ?: return

        if (!selected.isLocked) return

        ApplicationManager.getApplication().executeOnPooledThread {
            val service = WorktreeService.getInstance(project)
            val result = service.unlockWorktree(selected)

            ApplicationManager.getApplication().invokeLater {
                result.fold(
                    onSuccess = {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("GitWorktrees")
                            .createNotification(
                                "Worktree unlocked: ${selected.displayName}",
                                NotificationType.INFORMATION
                            )
                            .notify(project)
                        panel.refresh()
                    },
                    onFailure = { error ->
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("GitWorktrees")
                            .createNotification(
                                "Failed to unlock worktree: ${error.message}",
                                NotificationType.ERROR
                            )
                            .notify(project)
                    }
                )
            }
        }
    }
}
