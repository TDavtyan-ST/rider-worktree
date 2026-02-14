package com.github.tdavtyan.riderworktrees.actions

import com.github.tdavtyan.riderworktrees.services.WorktreeService
import com.github.tdavtyan.riderworktrees.ui.CreateWorktreeDialog
import com.intellij.ide.impl.ProjectUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware

class CreateWorktreeAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val dialog = CreateWorktreeDialog(project)

        if (dialog.showAndGet()) {
            val branch = dialog.selectedBranch
            val path = dialog.selectedPath
            val isNew = dialog.isNewBranch
            val shouldOpen = dialog.shouldOpenAfterCreation

            ApplicationManager.getApplication().executeOnPooledThread {
                val service = WorktreeService.getInstance(project)
                val result = service.createWorktree(branch, path, isNew)

                ApplicationManager.getApplication().invokeLater {
                    result.fold(
                        onSuccess = {
                            NotificationGroupManager.getInstance()
                                .getNotificationGroup("GitWorktrees")
                                .createNotification(
                                    "Worktree created at: $path",
                                    NotificationType.INFORMATION
                                )
                                .notify(project)

                            // Refresh the panel
                            WorktreeActionUtil.getWorktreePanel(e)?.refresh()

                            if (shouldOpen) {
                                ProjectUtil.openOrImport(path, null, true)
                            }
                        },
                        onFailure = { error ->
                            NotificationGroupManager.getInstance()
                                .getNotificationGroup("GitWorktrees")
                                .createNotification(
                                    "Failed to create worktree: ${error.message}",
                                    NotificationType.ERROR
                                )
                                .notify(project)
                        }
                    )
                }
            }
        }
    }
}
