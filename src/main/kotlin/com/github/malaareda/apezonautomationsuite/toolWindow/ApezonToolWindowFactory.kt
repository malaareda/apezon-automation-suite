package com.github.malaareda.apezonautomationsuite.toolWindow

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import java.awt.Component
import javax.swing.JComponent

class ApezonToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(createToolWindowPanel(project), "", false)
        toolWindow.contentManager.addContent(content)
    }

    private fun createToolWindowPanel(project: Project): JComponent {
        return panel {
            group("Scripts") {
                row {
                    button("Combine Codes Script") { event ->
                        triggerAction("com.apezon.automation.CombineCodes", event.source as? Component)
                    }.align(AlignX.FILL) // Makes button stretch to fill width
                }
                row {
                    button("Directory Tree Script") { event ->
                        triggerAction("com.apezon.automation.DirectoryTree", event.source as? Component)
                    }.align(AlignX.FILL)
                }
            }
        }
    }

    private fun triggerAction(actionId: String, component: Component?) {
        val actionManager = ActionManager.getInstance()
        val action = actionManager.getAction(actionId) ?: return

        actionManager.tryToExecute(
            action,
            null,
            component,
            ActionPlaces.TOOLWINDOW_CONTENT,
            true
        )
    }
}