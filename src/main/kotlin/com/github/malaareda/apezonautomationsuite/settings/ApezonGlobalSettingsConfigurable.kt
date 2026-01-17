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

class ApezonGlobalSettingsConfigurable(project: Project) : Configurable {

    private val settings = ApezonSettings.getInstance(project)
    private val folderListModel = CollectionListModel<String>()

    override fun getDisplayName(): String = "Apezon Automation Suite"

    override fun createComponent(): JComponent {
        reset()
        return panel {
            group("Global Project Filters") {
                row {
                    label("Folders to ignore everywhere (Tree View & Scans):")
                }
                row {
                    cell(createListPanel(folderListModel, "Folder Name"))
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
        return settings.state.excludedFolders != folderListModel.items
    }

    override fun apply() {
        settings.state.excludedFolders = folderListModel.items.toMutableList()
    }

    override fun reset() {
        folderListModel.removeAll()
        folderListModel.add(settings.state.excludedFolders)
    }
}