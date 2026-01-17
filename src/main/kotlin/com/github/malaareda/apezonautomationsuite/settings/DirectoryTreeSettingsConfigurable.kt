package com.github.malaareda.apezonautomationsuite.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent
import javax.swing.JPanel

class DirectoryTreeSettingsConfigurable(project: Project) : Configurable {

    private val settings = ApezonSettings.getInstance(project)

    private val excludedModel = CollectionListModel<String>()
    private val terminalModel = CollectionListModel<String>()

    override fun getDisplayName(): String = "Directory Tree Script"

    override fun createComponent(): JComponent {
        reset()
        return panel {
            group("Exclusion Rules") {
                row {
                    label("Folders to hide completely:")
                    comment("These will not appear in the generated tree at all.")
                }
                row {
                    cell(createListPanel(excludedModel, "Folder Name"))
                        .align(Align.FILL)
                }.resizableRow()
            }

            group("Terminal Folders") {
                row {
                    label("Show name only (No contents):")
                    comment("Example: 'node_modules'. The tree will show the folder exists, but won't list what's inside.")
                }
                row {
                    cell(createListPanel(terminalModel, "Folder Name"))
                        .align(Align.FILL)
                }.resizableRow()
            }
        }
    }

    private fun createListPanel(model: CollectionListModel<String>, inputTitle: String): JPanel {
        val list = JBList(model)
        return ToolbarDecorator.createDecorator(list)
            .setAddAction {
                val input = javax.swing.JOptionPane.showInputDialog("Enter $inputTitle:")
                if (!input.isNullOrBlank() && !model.items.contains(input.trim())) {
                    model.add(input.trim())
                }
            }
            .setRemoveAction {
                val selectedIndex = list.selectedIndex
                if (selectedIndex >= 0) {
                    model.remove(model.getElementAt(selectedIndex))
                }
            }
            .createPanel()
    }

    override fun isModified(): Boolean {
        val state = settings.state
        return state.treeExcludedFolders != excludedModel.items ||
                state.treeTerminalFolders != terminalModel.items
    }

    override fun apply() {
        val state = settings.state
        state.treeExcludedFolders = excludedModel.items.toMutableList()
        state.treeTerminalFolders = terminalModel.items.toMutableList()
    }

    override fun reset() {
        val state = settings.state
        excludedModel.removeAll()
        excludedModel.add(state.treeExcludedFolders)

        terminalModel.removeAll()
        terminalModel.add(state.treeTerminalFolders)
    }
}