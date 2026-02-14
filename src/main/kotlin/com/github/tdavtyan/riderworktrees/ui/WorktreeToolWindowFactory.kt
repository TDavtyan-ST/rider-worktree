package com.github.tdavtyan.riderworktrees.ui

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class WorktreeToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        LOG.info("createToolWindowContent called for project: ${project.name}, basePath: ${project.basePath}")
        val panel = WorktreePanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        content.isCloseable = false
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
        LOG.info("Tool window content created successfully")
    }

    companion object {
        private val LOG = Logger.getInstance(WorktreeToolWindowFactory::class.java)
    }
}
