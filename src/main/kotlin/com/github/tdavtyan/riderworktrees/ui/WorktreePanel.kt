package com.github.tdavtyan.riderworktrees.ui

import com.github.tdavtyan.riderworktrees.model.FileChange
import com.github.tdavtyan.riderworktrees.model.WorktreeInfo
import com.github.tdavtyan.riderworktrees.services.WorktreeService
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.Tree
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class WorktreePanel(private val project: Project) : SimpleToolWindowPanel(true, true), Disposable {

    private val rootNode = DefaultMutableTreeNode("Worktrees")
    private val treeModel = DefaultTreeModel(rootNode)
    val tree = Tree(treeModel)

    init {
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.cellRenderer = WorktreeTreeCellRenderer()
        tree.emptyText.text = "No worktrees found"
        tree.emptyText.appendLine("Create a worktree to get started")

        // Toolbar
        val actionGroup = ActionManager.getInstance().getAction("RiderWorktrees.ToolWindowToolbar")
        if (actionGroup != null) {
            val toolbar = ActionManager.getInstance().createActionToolbar(
                ActionPlaces.TOOLWINDOW_CONTENT,
                actionGroup as ActionGroup,
                true
            )
            toolbar.targetComponent = this
            setToolbar(toolbar.component)
        }

        // Content
        setContent(ScrollPaneFactory.createScrollPane(tree))

        // Context menu
        val contextGroup = ActionManager.getInstance().getAction("RiderWorktrees.ContextMenu")
        if (contextGroup != null) {
            PopupHandler.installPopupMenu(
                tree,
                contextGroup as ActionGroup,
                ActionPlaces.POPUP
            )
        }

        // Double-click handler
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val path = tree.getPathForLocation(e.x, e.y) ?: return
                    val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                    when (val userObject = node.userObject) {
                        is FileChange -> showDiff(userObject)
                        is WorktreeInfo -> {
                            if (!userObject.isCurrent) {
                                val action = ActionManager.getInstance().getAction("RiderWorktrees.Open")
                                if (action != null) {
                                    ActionManager.getInstance().tryToExecute(action, e, tree, ActionPlaces.TOOLWINDOW_CONTENT, true)
                                }
                            }
                        }
                    }
                }
            }
        })

        // Initial load
        refresh()
    }

    fun refresh() {
        ApplicationManager.getApplication().executeOnPooledThread {
            val service = WorktreeService.getInstance(project)
            service.invalidateCache()
            val worktrees = service.listWorktrees()

            // Fetch status for each worktree
            val worktreeWithChanges = worktrees.map { wt ->
                wt to service.getWorktreeStatus(wt)
            }

            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater

                // Remember expanded state
                val expandedPaths = mutableSetOf<String>()
                for (i in 0 until rootNode.childCount) {
                    val child = rootNode.getChildAt(i) as? DefaultMutableTreeNode ?: continue
                    val wt = child.userObject as? WorktreeInfo ?: continue
                    val treePath = javax.swing.tree.TreePath(arrayOf(rootNode, child))
                    if (tree.isExpanded(treePath)) {
                        expandedPaths.add(wt.path.toString())
                    }
                }

                rootNode.removeAllChildren()

                for ((wt, changes) in worktreeWithChanges) {
                    val wtNode = DefaultMutableTreeNode(wt)
                    for (change in changes) {
                        wtNode.add(DefaultMutableTreeNode(change))
                    }
                    rootNode.add(wtNode)
                }

                treeModel.reload()

                // Restore expanded state
                for (i in 0 until rootNode.childCount) {
                    val child = rootNode.getChildAt(i) as? DefaultMutableTreeNode ?: continue
                    val wt = child.userObject as? WorktreeInfo ?: continue
                    if (wt.path.toString() in expandedPaths) {
                        tree.expandPath(javax.swing.tree.TreePath(arrayOf(rootNode, child)))
                    }
                }
            }
        }
    }

    private fun showDiff(fileChange: FileChange) {
        ApplicationManager.getApplication().executeOnPooledThread {
            if (project.isDisposed) return@executeOnPooledThread

            // Fetch all content on background thread
            val headText = getHeadContentText(fileChange)
            val workingText = getWorkingCopyText(fileChange)

            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater

                val factory = DiffContentFactory.getInstance()
                val fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileChange.fileName)

                val headContent = factory.create(project, headText, fileType)

                val request = when (fileChange.status) {
                    FileChange.Status.DELETED -> {
                        val emptyContent = factory.create(project, "", fileType)
                        SimpleDiffRequest(
                            "${fileChange.relativePath} (Deleted)",
                            headContent,
                            emptyContent,
                            "HEAD",
                            "Deleted"
                        )
                    }
                    FileChange.Status.UNTRACKED, FileChange.Status.ADDED -> {
                        val emptyContent = factory.create(project, "", fileType)
                        val workingContent = factory.create(project, workingText, fileType)
                        SimpleDiffRequest(
                            "${fileChange.relativePath} (${fileChange.status.label})",
                            emptyContent,
                            workingContent,
                            "Not in HEAD",
                            "Working Copy"
                        )
                    }
                    else -> {
                        val workingContent = factory.create(project, workingText, fileType)
                        SimpleDiffRequest(
                            "${fileChange.relativePath} (${fileChange.status.label})",
                            headContent,
                            workingContent,
                            "HEAD",
                            "Working Copy"
                        )
                    }
                }

                DiffManager.getInstance().showDiff(project, request)
            }
        }
    }

    private fun getHeadContentText(fileChange: FileChange): String {
        return try {
            val commandLine = com.intellij.execution.configurations.GeneralCommandLine()
            commandLine.exePath = git4idea.config.GitExecutableManager.getInstance().getExecutable(project).exePath
            commandLine.setWorkDirectory(fileChange.worktreePath.toString())
            commandLine.addParameters("show", "HEAD:${fileChange.relativePath}")
            commandLine.charset = Charsets.UTF_8

            val handler = com.intellij.execution.process.CapturingProcessHandler(commandLine)
            val output = handler.runProcess(10_000)
            if (output.exitCode == 0) output.stdout else ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun getWorkingCopyText(fileChange: FileChange): String {
        return try {
            fileChange.absolutePath.toFile().readText()
        } catch (_: Exception) {
            ""
        }
    }

    fun getSelectedWorktree(): WorktreeInfo? {
        val path = tree.selectionPath ?: return null
        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return null
        return when (val obj = node.userObject) {
            is WorktreeInfo -> obj
            is FileChange -> {
                // Return the parent worktree
                val parent = node.parent as? DefaultMutableTreeNode ?: return null
                parent.userObject as? WorktreeInfo
            }
            else -> null
        }
    }

    fun getSelectedWorktrees(): List<WorktreeInfo> {
        return tree.selectionPaths?.mapNotNull { path ->
            val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return@mapNotNull null
            when (val obj = node.userObject) {
                is WorktreeInfo -> obj
                is FileChange -> {
                    val parent = node.parent as? DefaultMutableTreeNode ?: return@mapNotNull null
                    parent.userObject as? WorktreeInfo
                }
                else -> null
            }
        }?.distinct() ?: emptyList()
    }

    override fun dispose() {
        // Cleanup if needed
    }
}

private class WorktreeTreeCellRenderer : ColoredTreeCellRenderer() {

    override fun customizeCellRenderer(
        tree: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean,
    ) {
        val node = value as? DefaultMutableTreeNode ?: return
        when (val userObject = node.userObject) {
            is WorktreeInfo -> renderWorktree(userObject, node)
            is FileChange -> renderFileChange(userObject)
        }
    }

    private fun renderWorktree(wt: WorktreeInfo, node: DefaultMutableTreeNode) {
        val hasChanges = node.childCount > 0
        icon = when {
            wt.isCurrent -> AllIcons.Actions.Checked
            wt.isLocked -> AllIcons.Nodes.Locked
            wt.branch == null -> AllIcons.Vcs.CommitNode
            wt.isMain -> AllIcons.Nodes.HomeFolder
            hasChanges -> AllIcons.Vcs.Patch
            else -> AllIcons.Vcs.BranchNode
        }

        val nameAttrs = if (wt.isMain) {
            SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
        } else {
            SimpleTextAttributes.REGULAR_ATTRIBUTES
        }
        append(wt.displayName, nameAttrs)
        append("  ${wt.shortHash}", SimpleTextAttributes.GRAY_ATTRIBUTES)

        if (wt.statusText.isNotEmpty()) {
            append("  [${wt.statusText}]", SimpleTextAttributes.GRAY_SMALL_ATTRIBUTES)
        }

        // Show change count
        val changeCount = node.childCount
        if (changeCount > 0) {
            append("  $changeCount changed", SimpleTextAttributes(SimpleTextAttributes.STYLE_ITALIC, null))
        } else {
            append("  clean", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
        }
    }

    private fun renderFileChange(fc: FileChange) {
        icon = getFileChangeIcon(fc.status)
        append(fc.relativePath, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        append("  ${fc.status.label}", getStatusAttributes(fc.status))
    }

    private fun getFileChangeIcon(status: FileChange.Status): Icon = when (status) {
        FileChange.Status.ADDED -> AllIcons.General.Add
        FileChange.Status.MODIFIED -> AllIcons.Actions.Edit
        FileChange.Status.DELETED -> AllIcons.General.Remove
        FileChange.Status.RENAMED -> AllIcons.Actions.RefactoringBulb
        FileChange.Status.COPIED -> AllIcons.Actions.Copy
        FileChange.Status.UNTRACKED -> AllIcons.General.Add
    }

    private fun getStatusAttributes(status: FileChange.Status): SimpleTextAttributes {
        val color = when (status) {
            FileChange.Status.ADDED, FileChange.Status.UNTRACKED -> java.awt.Color(0, 128, 0)
            FileChange.Status.DELETED -> java.awt.Color(200, 0, 0)
            FileChange.Status.MODIFIED -> java.awt.Color(0, 0, 200)
            FileChange.Status.RENAMED -> java.awt.Color(128, 0, 128)
            FileChange.Status.COPIED -> java.awt.Color(0, 128, 128)
        }
        return SimpleTextAttributes(SimpleTextAttributes.STYLE_ITALIC, color)
    }
}
