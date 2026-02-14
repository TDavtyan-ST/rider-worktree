package com.github.tdavtyan.riderworktrees.actions

import com.github.tdavtyan.riderworktrees.services.WorktreeService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages

class RemoveWorktreeAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val panel = WorktreeActionUtil.getWorktreePanel(e)
        val selected = panel?.getSelectedWorktree()
        e.presentation.isEnabled = selected != null && !selected.isCurrent && !selected.isMain
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val panel = WorktreeActionUtil.getWorktreePanel(e) ?: return
        val selected = panel.getSelectedWorktree() ?: return

        if (selected.isCurrent || selected.isMain) return

        // Check if worktree is open in another window
        val openProjects = ProjectManager.getInstance().openProjects
        val openProject = openProjects.find { p ->
            p.basePath?.let { java.nio.file.Paths.get(it) } == selected.path
        }
        if (openProject != null) {
            Messages.showWarningDialog(
                project,
                "This worktree is open in another IDE window. Please close it first.",
                "Cannot Remove Worktree"
            )
            return
        }

        // Confirm removal
        val result = Messages.showYesNoDialog(
            project,
            "Remove worktree at ${selected.path}?\n\nBranch: ${selected.displayName}",
            "Remove Worktree",
            Messages.getQuestionIcon()
        )
        if (result != Messages.YES) return

        ApplicationManager.getApplication().executeOnPooledThread {
            val service = WorktreeService.getInstance(project)
            val removeResult = service.removeWorktree(selected, force = false)

            ApplicationManager.getApplication().invokeLater {
                removeResult.fold(
                    onSuccess = {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("GitWorktrees")
                            .createNotification(
                                "Worktree removed: ${selected.displayName}",
                                NotificationType.INFORMATION
                            )
                            .notify(project)
                        panel.refresh()
                    },
                    onFailure = { error ->
                        // Offer force removal
                        val forceResult = Messages.showYesNoDialog(
                            project,
                            "Removal failed: ${error.message}\n\nForce removal?",
                            "Remove Worktree",
                            Messages.getWarningIcon()
                        )
                        if (forceResult == Messages.YES) {
                            ApplicationManager.getApplication().executeOnPooledThread {
                                val forceRemoveResult = service.removeWorktree(selected, force = true)
                                ApplicationManager.getApplication().invokeLater {
                                    forceRemoveResult.fold(
                                        onSuccess = {
                                            NotificationGroupManager.getInstance()
                                                .getNotificationGroup("GitWorktrees")
                                                .createNotification(
                                                    "Worktree force-removed: ${selected.displayName}",
                                                    NotificationType.INFORMATION
                                                )
                                                .notify(project)
                                            panel.refresh()
                                        },
                                        onFailure = { forceError ->
                                            NotificationGroupManager.getInstance()
                                                .getNotificationGroup("GitWorktrees")
                                                .createNotification(
                                                    "Failed to remove worktree: ${forceError.message}",
                                                    NotificationType.ERROR
                                                )
                                                .notify(project)
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}
