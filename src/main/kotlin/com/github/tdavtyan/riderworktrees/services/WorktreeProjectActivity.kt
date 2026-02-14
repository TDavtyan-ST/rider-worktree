package com.github.tdavtyan.riderworktrees.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindowManager
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.delay

class WorktreeProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        LOG.info("WorktreeProjectActivity.execute() called for project: ${project.name}, basePath: ${project.basePath}")

        // Subscribe to git repo changes for live refresh
        project.messageBus.connect().subscribe(
            GitRepository.GIT_REPO_CHANGE,
            GitRepositoryChangeListener { repo ->
                LOG.info("Git repo changed: ${repo.root.path}, refreshing worktree panel")
                refreshAndAutoShow(project)
            }
        )

        // Wait for VCS initialization (git roots are detected asynchronously)
        for (attempt in 1..10) {
            val repos = GitRepositoryManager.getInstance(project).repositories
            if (repos.isNotEmpty()) {
                LOG.info("Git repos available on attempt $attempt: ${repos.map { it.root.path }}")
                break
            }
            LOG.info("No git repos yet on attempt $attempt, waiting...")
            delay(1000)
        }

        refreshAndAutoShow(project)
    }

    private fun refreshAndAutoShow(project: Project) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val service = WorktreeService.getInstance(project)
                service.invalidateCache()
                val worktrees = service.listWorktrees()
                LOG.info("Found ${worktrees.size} worktrees: ${worktrees.map { "${it.displayName} (current=${it.isCurrent})" }}")

                ApplicationManager.getApplication().invokeLater {
                    if (project.isDisposed) return@invokeLater

                    // Refresh the panel if it exists
                    val tw = ToolWindowManager.getInstance(project).getToolWindow("Git Worktrees")
                    if (tw == null) {
                        LOG.warn("Tool window 'Git Worktrees' NOT found!")
                        return@invokeLater
                    }

                    // Find and refresh the WorktreePanel
                    tw.contentManager.contents.forEach { content ->
                        val panel = content.component
                        if (panel is com.github.tdavtyan.riderworktrees.ui.WorktreePanel) {
                            panel.refresh()
                        }
                    }

                    // Auto-show if there are multiple worktrees
                    if (worktrees.size > 1 && !tw.isVisible) {
                        LOG.info("Auto-showing tool window (${worktrees.size} worktrees)")
                        tw.show()
                    }
                }
            } catch (e: Exception) {
                LOG.error("Error refreshing worktrees", e)
            }
        }
    }

    companion object {
        private val LOG = Logger.getInstance(WorktreeProjectActivity::class.java)
    }
}
