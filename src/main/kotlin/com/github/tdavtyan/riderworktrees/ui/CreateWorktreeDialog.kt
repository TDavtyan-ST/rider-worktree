package com.github.tdavtyan.riderworktrees.ui

import com.github.tdavtyan.riderworktrees.services.WorktreeService
import com.github.tdavtyan.riderworktrees.settings.WorktreeSettings
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTextField
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.ButtonGroup
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class CreateWorktreeDialog(private val project: Project) : DialogWrapper(project) {

    private val service = WorktreeService.getInstance(project)
    private val settings = WorktreeSettings.getInstance(project)

    private val existingBranchRadio = JBRadioButton("Existing branch", true)
    private val newBranchRadio = JBRadioButton("New branch")
    private val branchCombo = ComboBox<String>()
    private val newBranchField = JBTextField()
    private val pathField = TextFieldWithBrowseButton()
    private val openAfterCheckbox = JCheckBox("Open in IDE after creation", settings.state.openAfterCreation)

    val selectedBranch: String
        get() = if (existingBranchRadio.isSelected) {
            branchCombo.selectedItem as? String ?: ""
        } else {
            newBranchField.text.trim()
        }

    val selectedPath: Path
        get() = Path.of(pathField.text.trim())

    val isNewBranch: Boolean
        get() = newBranchRadio.isSelected

    val shouldOpenAfterCreation: Boolean
        get() = openAfterCheckbox.isSelected

    init {
        title = "Create Worktree"
        init()
        loadBranches()
        setupListeners()
    }

    private fun loadBranches() {
        val branches = service.getAvailableBranches()
        branches.forEach { branchCombo.addItem(it) }
        branchCombo.renderer = SimpleListCellRenderer.create("") { it }
        updatePathFromBranch()
    }

    private fun setupListeners() {
        val radioGroup = ButtonGroup()
        radioGroup.add(existingBranchRadio)
        radioGroup.add(newBranchRadio)

        existingBranchRadio.addActionListener {
            branchCombo.isEnabled = true
            newBranchField.isEnabled = false
            updatePathFromBranch()
        }
        newBranchRadio.addActionListener {
            branchCombo.isEnabled = false
            newBranchField.isEnabled = true
            updatePathFromBranch()
        }

        branchCombo.addActionListener { updatePathFromBranch() }
        newBranchField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = updatePathFromBranch()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = updatePathFromBranch()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = updatePathFromBranch()
        })

        newBranchField.isEnabled = false

        pathField.addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle("Select Worktree Location")
                .withDescription("Choose the directory for the new worktree")
        )
    }

    private fun updatePathFromBranch() {
        val branch = if (existingBranchRadio.isSelected) {
            branchCombo.selectedItem as? String
        } else {
            newBranchField.text.trim().ifEmpty { null }
        }
        if (branch != null) {
            val defaultPath = service.getDefaultWorktreePath(branch)
            if (defaultPath != null) {
                pathField.text = defaultPath.toString()
            }
        }
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.insets = Insets(4, 4, 4, 4)
        gbc.fill = GridBagConstraints.HORIZONTAL

        // Branch mode radio buttons
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2; gbc.weightx = 1.0
        panel.add(existingBranchRadio, gbc)

        gbc.gridy = 1
        panel.add(newBranchRadio, gbc)

        // Branch combo
        gbc.gridy = 2; gbc.gridwidth = 1; gbc.gridx = 0; gbc.weightx = 0.0
        panel.add(JLabel("Branch:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        panel.add(branchCombo, gbc)

        // New branch name
        gbc.gridy = 3; gbc.gridx = 0; gbc.weightx = 0.0
        panel.add(JLabel("New branch name:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        panel.add(newBranchField, gbc)

        // Path
        gbc.gridy = 4; gbc.gridx = 0; gbc.weightx = 0.0
        panel.add(JLabel("Path:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        panel.add(pathField, gbc)

        // Open after creation
        gbc.gridy = 5; gbc.gridx = 0; gbc.gridwidth = 2; gbc.weightx = 1.0
        panel.add(openAfterCheckbox, gbc)

        return panel
    }

    override fun doValidate(): ValidationInfo? {
        val branch = selectedBranch
        if (branch.isBlank()) {
            return if (existingBranchRadio.isSelected) {
                ValidationInfo("Please select a branch", branchCombo)
            } else {
                ValidationInfo("Please enter a branch name", newBranchField)
            }
        }

        val pathText = pathField.text.trim()
        if (pathText.isBlank()) {
            return ValidationInfo("Please specify a path", pathField)
        }

        val path = Path.of(pathText)
        if (Files.exists(path) && Files.list(path).findFirst().isPresent) {
            return ValidationInfo("Path already exists and is not empty", pathField)
        }

        // Check if branch is already checked out in another worktree (for existing branch mode)
        if (existingBranchRadio.isSelected) {
            val worktrees = service.getCachedWorktrees() ?: service.listWorktrees()
            val branchRef = "refs/heads/$branch"
            val existing = worktrees.find { it.branch == branchRef || it.branch == branch }
            if (existing != null) {
                return ValidationInfo("Branch '$branch' is already checked out in: ${existing.path}", branchCombo)
            }
        }

        return null
    }

    override fun getPreferredFocusedComponent(): JComponent = branchCombo
}
