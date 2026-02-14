package com.github.tdavtyan.riderworktrees.actions

import com.github.tdavtyan.riderworktrees.services.WorktreeService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages

class LockWorktreeAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val panel = WorktreeActionUtil.getWorktreePanel(e)
        val selected = panel?.getSelectedWorktree()
        e.presentation.isEnabled = selected != null && !selected.isLocked
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val panel = WorktreeActionUtil.getWorktreePanel(e) ?: return
        val selected = panel.getSelectedWorktree() ?: return

        if (selected.isLocked) return

        val reason = Messages.showInputDialog(
            project,
            "Reason for locking (optional):",
            "Lock Worktree: ${selected.displayName}",
            null
        )
        // If user cancelled the dialog, reason is null
        if (reason == null) return

        ApplicationManager.getApplication().executeOnPooledThread {
            val service = WorktreeService.getInstance(project)
            val result = service.lockWorktree(selected, reason.ifBlank { null })

            ApplicationManager.getApplication().invokeLater {
                result.fold(
                    onSuccess = {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("GitWorktrees")
                            .createNotification(
                                "Worktree locked: ${selected.displayName}",
                                NotificationType.INFORMATION
                            )
                            .notify(project)
                        panel.refresh()
                    },
                    onFailure = { error ->
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("GitWorktrees")
                            .createNotification(
                                "Failed to lock worktree: ${error.message}",
                                NotificationType.ERROR
                            )
                            .notify(project)
                    }
                )
            }
        }
    }
}
