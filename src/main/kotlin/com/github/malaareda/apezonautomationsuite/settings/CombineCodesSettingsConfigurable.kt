package com.github.malaareda.apezonautomationsuite.settings

import com.github.malaareda.apezonautomationsuite.actions.MANDATORY_HEADER_EXTENSIONS
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent
import javax.swing.JPanel

class CombineCodesSettingsConfigurable(project: Project) : Configurable {

    private val settings = ApezonSettings.getInstance(project)

    private val extensionListModel = CollectionListModel<String>()
    private val headerExceptionListModel = CollectionListModel<String>()

    override fun getDisplayName(): String = "Combine Codes Script"

    override fun createComponent(): JComponent {
        reset()
        return panel {
            group("Collection Filters") {
                row {
                    label("Extensions to exclude (File Collection):")
                    comment("Files with these extensions will be completely ignored.")
                }
                row {
                    cell(createListPanel(extensionListModel, "Extension (e.g., exe)"))
                        .align(Align.FILL)
                }.resizableRow()
            }

            group("Validation Rules") {
                row {
                    label("Header scanning exceptions:")
                    comment("Files to collect but SKIP mandatory header checks.")
                }
                row {
                    cell(createListPanel(headerExceptionListModel, "Extension (e.g., json)"))
                        .align(Align.FILL)
                }.resizableRow()

                separator()
                row {
                    label("Mandatory header extensions (hardcoded):")
                }
                row {
                    val extString = MANDATORY_HEADER_EXTENSIONS.joinToString(", ") { ".$it" }
                    label("<html>Files requiring headers: <b>$extString</b></html>")
                }
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
        return state.excludedExtensions != extensionListModel.items ||
                state.headerScanningExceptions != headerExceptionListModel.items
    }

    override fun apply() {
        val state = settings.state
        state.excludedExtensions = extensionListModel.items.toMutableList()
        state.headerScanningExceptions = headerExceptionListModel.items.toMutableList()
    }

    override fun reset() {
        val state = settings.state

        extensionListModel.removeAll()
        extensionListModel.add(state.excludedExtensions)

        headerExceptionListModel.removeAll()
        headerExceptionListModel.add(state.headerScanningExceptions)
    }
}