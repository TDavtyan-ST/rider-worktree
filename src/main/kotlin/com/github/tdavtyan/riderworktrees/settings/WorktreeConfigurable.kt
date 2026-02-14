package com.github.tdavtyan.riderworktrees.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel

class WorktreeConfigurable(private val project: Project) : BoundConfigurable("Git Worktrees") {

    private val settings get() = WorktreeSettings.getInstance(project)

    override fun createPanel() = panel {
        group("Defaults") {
            row("Worktree directory:") {
                textField()
                    .bindText(settings.state::defaultWorktreeDirectory)
                    .comment("Relative path from repository root for new worktrees")
            }
            row {
                checkBox("Open worktree in IDE after creation")
                    .bindSelected(settings.state::openAfterCreation)
            }
        }
    }
}
