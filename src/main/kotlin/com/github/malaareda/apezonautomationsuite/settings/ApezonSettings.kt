package com.github.malaareda.apezonautomationsuite.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@State(
    name = "com.github.malaareda.apezonautomationsuite.settings.ApezonSettings",
    storages = [Storage("ApezonAutomationSuite.xml")]
)
@Service(Service.Level.PROJECT)
class ApezonSettings : PersistentStateComponent<ApezonSettings.State> {

    data class State(
        // --- GLOBAL ---
        var excludedFolders: MutableList<String> = mutableListOf(
            ".git", ".idea", "node_modules", "dist", "build", "out", "coverage", ".gradle"
        ),

        // --- COMBINE CODES SCRIPT ---
        var excludedExtensions: MutableList<String> = mutableListOf(
            "exe", "dll", "class", "jar", "png", "jpg", "jpeg", "gif", "ico", "zip", "tar", "gz"
        ),
        var headerScanningExceptions: MutableList<String> = mutableListOf(
            "json", "md", "txt", "xml", "html", "css", "yaml", "yml", "properties", "gitignore"
        ),

        // --- DIRECTORY TREE SCRIPT (NEW) ---
        // 1. Completely ignore these (don't show, don't scan)
        var treeExcludedFolders: MutableList<String> = mutableListOf(
            ".git", ".idea", ".output", "dist", "archive", "devOnly"
        ),
        // 2. Show the folder name, but do NOT scan inside (Terminal Folders)
        var treeTerminalFolders: MutableList<String> = mutableListOf(
            "node_modules", ".nuxt", ".data"
        )
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(project: Project): ApezonSettings = project.getService(ApezonSettings::class.java)
    }
}