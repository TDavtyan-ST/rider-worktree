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

class PruneWorktreesAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val result = Messages.showYesNoDialog(
            project,
            "Prune stale worktree entries? This removes administrative data for worktrees whose directories no longer exist.",
            "Prune Worktrees",
            Messages.getQuestionIcon()
        )
        if (result != Messages.YES) return

        ApplicationManager.getApplication().executeOnPooledThread {
            val service = WorktreeService.getInstance(project)
            val pruneResult = service.pruneWorktrees()

            ApplicationManager.getApplication().invokeLater {
                pruneResult.fold(
                    onSuccess = { output ->
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("GitWorktrees")
                            .createNotification(
                                output,
                                NotificationType.INFORMATION
                            )
                            .notify(project)
                        WorktreeActionUtil.getWorktreePanel(e)?.refresh()
                    },
                    onFailure = { error ->
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("GitWorktrees")
                            .createNotification(
                                "Failed to prune worktrees: ${error.message}",
                                NotificationType.ERROR
                            )
                            .notify(project)
                    }
                )
            }
        }
    }
}
