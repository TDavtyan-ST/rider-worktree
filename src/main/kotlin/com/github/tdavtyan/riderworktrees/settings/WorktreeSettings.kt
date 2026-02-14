package com.github.tdavtyan.riderworktrees.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "GitWorktreeSettings", storages = [Storage("gitWorktrees.xml")])
class WorktreeSettings : PersistentStateComponent<WorktreeSettings.State> {

    data class State(
        var defaultWorktreeDirectory: String = ".worktrees",
        var openAfterCreation: Boolean = true,
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(project: Project): WorktreeSettings {
            return project.getService(WorktreeSettings::class.java)
        }
    }
}
