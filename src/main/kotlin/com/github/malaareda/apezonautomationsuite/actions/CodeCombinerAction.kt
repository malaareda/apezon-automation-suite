package com.github.malaareda.apezonautomationsuite.actions

import com.github.malaareda.apezonautomationsuite.settings.ApezonSettings
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.ui.*
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

// =================================================================================================
// CONSTANTS (Hardcoded Settings)
// =================================================================================================
val MANDATORY_HEADER_EXTENSIONS = listOf("py", "ts", "js", "go")

// =================================================================================================
// 1. STATE MANAGER
// =================================================================================================
object DialogStateManager {
    data class State(
        val mode: SelectionMode,
        val selectedPaths: List<String>,
        val logHistory: List<String>
    )

    private var savedState: State? = null

    fun save(mode: SelectionMode, files: List<VirtualFile>, logs: List<String>) {
        savedState = State(
            mode = mode,
            selectedPaths = files.map { it.path },
            logHistory = logs
        )
    }

    fun load(): State? = savedState

    fun clear() {
        savedState = null
    }
}

// =================================================================================================
// 2. SHARED CORE LOGIC
// =================================================================================================
object CodeCombinerCore {

    private const val BASE_NAME = "Combined_Codes_R"
    private const val DEV_ONLY_DIR = "devOnly"
    private const val SCRIPT_OUTPUT_DIR = "Combined_Codes"

    fun scanAndCollectFiles(project: Project, roots: List<VirtualFile>): List<VirtualFile> {
        val settings = ApezonSettings.getInstance(project).state
        val results = mutableListOf<VirtualFile>()

        val allExcludedFolders = settings.excludedFolders.toMutableSet()
        allExcludedFolders.add(DEV_ONLY_DIR)
        allExcludedFolders.add(SCRIPT_OUTPUT_DIR)

        roots.forEach { root ->
            if (root.isDirectory && allExcludedFolders.contains(root.name)) return@forEach

            if (!root.isDirectory) {
                if (isValidExtension(root, settings.excludedExtensions)) {
                    results.add(root)
                }
            } else {
                VfsUtilCore.visitChildrenRecursively(root, object : VirtualFileVisitor<Any>() {
                    override fun visitFile(file: VirtualFile): Boolean {
                        if (file.isDirectory) {
                            if (allExcludedFolders.contains(file.name)) return false
                        } else {
                            if (isValidExtension(file, settings.excludedExtensions)) {
                                results.add(file)
                            }
                        }
                        return true
                    }
                })
            }
        }
        return results
    }

    private fun isValidExtension(file: VirtualFile, excludedExtensions: List<String>): Boolean {
        val ext = file.extension?.lowercase() ?: ""
        return ext.isNotEmpty() && !excludedExtensions.contains(ext)
    }

    fun sortFiles(files: List<VirtualFile>): List<Triple<VirtualFile, Int, String>> {
        return files.map { file ->
            val partNum = getPartNumber(file)
            Triple(file, partNum, file.path)
        }.sortedWith(compareBy({ it.second }, { it.third }))
    }

    private fun getPartNumber(file: VirtualFile): Int {
        if (file.extension == "vue") return Int.MAX_VALUE
        try {
            val lines = file.inputStream.bufferedReader().use { reader ->
                val list = mutableListOf<String>()
                var line = reader.readLine()
                while (line != null && list.size < 15) {
                    list.add(line)
                    line = reader.readLine()
                }
                list
            }
            val numberedRegex = Regex("""//\s*\[(\d+)]\s*File:""", RegexOption.IGNORE_CASE)
            for (line in lines) {
                val match = numberedRegex.find(line.trim())
                if (match != null) return match.groupValues[1].toInt()
            }
        } catch (_: Exception) { /* Ignore read errors */ }
        return Int.MAX_VALUE
    }

    fun buildMarkdown(fileData: List<Triple<VirtualFile, Int, String>>): String {
        val sb = StringBuilder()
        val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm a")

        sb.append("# Combined Codes\n\n")
        sb.append("Generated on: ${LocalDateTime.now().format(formatter)}\n")
        sb.append("Total Files: ${fileData.size}\n\n")

        sb.append("## Index\n\n")
        sb.append("| File Name | Source Directory | Absolute Path |\n")
        sb.append("|---|---|---|\n")

        fileData.forEach { (file, _, path) ->
            val sourceDir = file.parent?.name ?: "Unknown"
            sb.append("| ${file.name} | $sourceDir | $path |\n")
        }
        sb.append("\n")

        fileData.forEachIndexed { index, (file, partNum, _) ->
            val partLabel = if (partNum == Int.MAX_VALUE) index + 1 else partNum
            sb.append("## Part[$partLabel] - ${file.name}\n\n```${file.extension}\n")
            try { sb.append(String(file.contentsToByteArray())) } catch (e: Exception) { sb.append("// Error: ${e.message}") }
            sb.append("\n```\n\n")
        }
        return sb.toString()
    }

    fun saveFile(project: Project, targetDirectory: VirtualFile, content: String): File {
        val nextRevision = getNextRevision(targetDirectory)
        val fileName = "$BASE_NAME$nextRevision.md"
        val ioFile = File(targetDirectory.path, fileName)

        if (!ioFile.parentFile.exists()) ioFile.parentFile.mkdirs()
        ioFile.writeBytes(content.toByteArray())

        WriteCommandAction.runWriteCommandAction(project) {
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile)
        }
        return ioFile
    }

    private fun getNextRevision(directory: VirtualFile): String {
        val existingFiles = directory.children.filter {
            it.name.startsWith(BASE_NAME) && it.name.endsWith(".md") && !it.isDirectory
        }
        if (existingFiles.isEmpty()) return "00"

        var maxRev = -1
        val regex = Regex("""${BASE_NAME}(\d+)\.md""")

        existingFiles.forEach { file ->
            val match = regex.matchEntire(file.name)
            if (match != null) {
                val num = match.groupValues[1].toIntOrNull() ?: -1
                if (num > maxRev) maxRev = num
            }
        }
        return String.format("%02d", maxRev + 1)
    }

    fun getOrCreateDevOnly(project: Project, parent: VirtualFile, allFiles: List<VirtualFile>): VirtualFile? {
        val commonTag = findCommonTagValue(allFiles)

        return try {
            var targetDir = createFolder(project, parent, DEV_ONLY_DIR) ?: return null
            targetDir = createFolder(project, targetDir, SCRIPT_OUTPUT_DIR) ?: return null
            if (commonTag != null) {
                targetDir = createFolder(project, targetDir, commonTag) ?: targetDir
            }
            targetDir
        } catch (e: IOException) {
            Messages.showErrorDialog("Error creating directories: ${e.message}", "IO Error")
            null
        }
    }

    private fun createFolder(project: Project, parent: VirtualFile, name: String): VirtualFile? {
        var folder = parent.findChild(name)
        if (folder == null || !folder.isDirectory) {
            try {
                WriteCommandAction.runWriteCommandAction(project) {
                    folder = parent.createChildDirectory(this, name)
                }
            } catch (_: Exception) { return null }
        }
        return folder
    }

    private fun findCommonTagValue(files: List<VirtualFile>): String? {
        if (files.isEmpty()) return null
        val tagRegex = Regex("""TAG:[ \t]*(\S+)""", RegexOption.IGNORE_CASE)
        var commonTag: String? = null

        for (file in files) {
            try {
                val headerText = ApplicationManager.getApplication().runReadAction<String> {
                    val document = FileDocumentManager.getInstance().getDocument(file)
                    if (document != null) document.text else {
                        file.refresh(false, false)
                        String(file.contentsToByteArray())
                    }
                }

                val header = headerText.lines().take(15).joinToString("\n")

                val match = tagRegex.find(header) ?: return null
                val currentTag = match.groupValues[1].trim()

                if (commonTag == null) commonTag = currentTag
                else if (!commonTag.equals(currentTag, ignoreCase = true)) return null
            } catch (_: Exception) { return null }
        }
        return commonTag
    }
}

// =================================================================================================
// 2. THE MASTER DIALOG
// =================================================================================================

enum class SelectionMode {
    SINGLE_DIR,
    MULTI_DIR,
    SPECIFIC_FILES
}

class UniversalCollectorDialog(private val project: Project) : DialogWrapper(project) {

    private val selectedListModel = CollectionListModel<VirtualFile>()
    private val selectedList = JBList(selectedListModel)
    private lateinit var projectTree: Tree

    private val validationArea = JTextPane()
    private val clearButton = JButton("Clear Log")

    private var currentMode = SelectionMode.SINGLE_DIR
    private val historyMap = mutableMapOf<SelectionMode, MutableList<String>>().apply {
        SelectionMode.values().forEach { put(it, mutableListOf()) }
    }
    private val currentLogs = mutableListOf<String>()
    private var hasSuccessfulCombine = false

    private val timeFormatter = DateTimeFormatter.ofPattern("hh:mm:ss a")

    private val rbSingle = JRadioButton("Single Directory", true)
    private val rbMulti = JRadioButton("Multiple Directories")
    private val rbFiles = JRadioButton("Specific Files")

    private val excludedFolders = ApezonSettings.getInstance(project).state.excludedFolders.toMutableSet().apply {
        add("devOnly")
        add("Combined_Codes")
    }

    init {
        title = "Combine Codes Script"
        setOKButtonText("Combine")
        setCancelButtonText("Close")
        init()
        restoreState()
    }

    override fun createActions(): Array<Action> {
        val combineAction = object : DialogWrapperAction("Combine") {
            override fun doAction(e: ActionEvent?) {
                performCombineLogic()
            }
        }
        return arrayOf(combineAction, cancelAction)
    }

    override fun doCancelAction() {
        if (hasSuccessfulCombine) {
            DialogStateManager.clear()
        } else {
            saveCurrentState()
        }
        super.doCancelAction()
    }

    override fun createCenterPanel(): JComponent {
        val topPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        topPanel.border = IdeBorderFactory.createTitledBorder("Operation Mode")

        val group = ButtonGroup()
        group.add(rbSingle)
        group.add(rbMulti)
        group.add(rbFiles)

        val modeListener = { _: java.awt.event.ActionEvent ->
            selectedListModel.removeAll()
            currentLogs.clear()
            DialogStateManager.clear()
            updateMode()
            refreshLogView()
        }
        rbSingle.addActionListener(modeListener)
        rbMulti.addActionListener(modeListener)
        rbFiles.addActionListener(modeListener)
        topPanel.add(rbSingle); topPanel.add(rbMulti); topPanel.add(rbFiles)

        selectedList.cellRenderer = object : ColoredListCellRenderer<VirtualFile>() {
            override fun customizeCellRenderer(list: JList<out VirtualFile>, value: VirtualFile?, index: Int, selected: Boolean, hasFocus: Boolean) {
                if (value == null) return
                append(value.name)
                append("  (${value.parent?.path ?: ""})", SimpleTextAttributes.GRAY_ATTRIBUTES)
                icon = IconUtil.getIcon(value, 0, project)
            }
        }
        val rightPanel = ToolbarDecorator.createDecorator(selectedList)
            .setRemoveAction {
                val indices = selectedList.selectedIndices
                if (indices.isNotEmpty()) {
                    val sb = StringBuilder()
                    if (indices.size == 1) {
                        val item = selectedListModel.getElementAt(indices[0])
                        log("🗑️ <span style='color: #FF5555;'>Removed: <b>'${item.name}'</b></span>")
                    } else {
                        log("🗑️ <span style='color: #FF5555;'>Removed <b>${indices.size}</b> items.</span>")
                    }
                    for (i in indices.reversed()) selectedListModel.remove(i)
                }
            }
            .disableAddAction()
            .createPanel()
        rightPanel.border = IdeBorderFactory.createTitledBorder("Selected Items")

        val root = ProjectRootManager.getInstance(project).contentRoots.firstOrNull()
        val treeModel = if (root != null) buildTreeModel(root) else DefaultTreeModel(DefaultMutableTreeNode("No Root"))

        projectTree = Tree(treeModel)
        projectTree.selectionModel.selectionMode = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION
        projectTree.background = UIUtil.getTreeBackground()

        projectTree.cellRenderer = object : ColoredTreeCellRenderer() {
            override fun customizeCellRenderer(tree: JTree, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean) {
                val node = value as? DefaultMutableTreeNode
                val file = node?.userObject as? VirtualFile
                if (file != null) {
                    append(file.name)
                    icon = IconUtil.getIcon(file, 0, project)
                }
            }
        }

        val leftScroll = JBScrollPane(projectTree)
        leftScroll.border = IdeBorderFactory.createTitledBorder("Project Structure")

        val btnAdd = JButton("Add ->")
        btnAdd.addActionListener { addSelectionFromTree() }

        val centerButtons = JPanel(GridBagLayout())
        val c = GridBagConstraints()
        c.gridx = 0; c.gridy = 0; c.insets = JBUI.insets(5)
        centerButtons.add(btnAdd, c)

        val splitter = Splitter(false, 0.4f)
        splitter.firstComponent = leftScroll
        val rightContainer = JPanel(BorderLayout())
        rightContainer.add(centerButtons, BorderLayout.WEST)
        rightContainer.add(rightPanel, BorderLayout.CENTER)
        splitter.secondComponent = rightContainer

        val bottomContainer = JPanel(BorderLayout())
        val consoleHeader = JPanel(BorderLayout())
        consoleHeader.add(JLabel("Operation Log"), BorderLayout.WEST)

        clearButton.addActionListener {
            currentLogs.clear()
            refreshLogView()
        }
        consoleHeader.add(clearButton, BorderLayout.EAST)
        consoleHeader.border = JBUI.Borders.empty(5)

        validationArea.contentType = "text/html"
        validationArea.isEditable = false
        validationArea.background = UIUtil.getPanelBackground()

        val scrollValidation = JBScrollPane(validationArea)
        scrollValidation.preferredSize = java.awt.Dimension(800, 200)

        bottomContainer.add(consoleHeader, BorderLayout.NORTH)
        bottomContainer.add(scrollValidation, BorderLayout.CENTER)
        bottomContainer.border = IdeBorderFactory.createTitledBorder("Console")

        val mainPanel = JPanel(BorderLayout())
        mainPanel.add(topPanel, BorderLayout.NORTH)
        mainPanel.add(splitter, BorderLayout.CENTER)
        mainPanel.add(bottomContainer, BorderLayout.SOUTH)

        mainPanel.preferredSize = java.awt.Dimension(1200, 800)

        return mainPanel
    }

    private fun saveCurrentState() {
        DialogStateManager.save(currentMode, selectedListModel.items, currentLogs)
    }

    private fun restoreState() {
        val state = DialogStateManager.load() ?: return
        when (state.mode) {
            SelectionMode.SINGLE_DIR -> rbSingle.isSelected = true
            SelectionMode.MULTI_DIR -> rbMulti.isSelected = true
            SelectionMode.SPECIFIC_FILES -> rbFiles.isSelected = true
        }
        updateMode()
        currentLogs.clear()
        currentLogs.addAll(state.logHistory)
        refreshLogView()
        selectedListModel.removeAll()
        val fs = LocalFileSystem.getInstance()
        for (path in state.selectedPaths) {
            val file = fs.findFileByPath(path)
            if (file != null && file.isValid) {
                selectedListModel.add(file)
            }
        }
    }

    private fun updateMode() {
        currentMode = when {
            rbSingle.isSelected -> SelectionMode.SINGLE_DIR
            rbMulti.isSelected -> SelectionMode.MULTI_DIR
            else -> SelectionMode.SPECIFIC_FILES
        }
    }

    private fun log(message: String) {
        val time = LocalDateTime.now().format(timeFormatter)
        val logEntry = "<div style='margin-top: 2px; margin-bottom: 2px;'><span style='color: #888888;'>[$time]</span> $message</div>"
        currentLogs.add(logEntry)
        refreshLogView()
    }

    private fun refreshLogView() {
        val sb = StringBuilder()
        sb.append("<html><body style='font-family: sans-serif; font-size: 11px; margin: 2px;'>")
        if (currentLogs.isEmpty()) {
            val time = LocalDateTime.now().format(timeFormatter)
            sb.append("<div style='color: #888888;'>[$time] Ready. Waiting for action...</div>")
        } else {
            for (msg in currentLogs) {
                sb.append(msg)
            }
        }
        sb.append("</body></html>")
        validationArea.text = sb.toString()
        SwingUtilities.invokeLater {
            validationArea.caretPosition = validationArea.document.length
        }
    }

    private fun performCombineLogic() {
        val selected = selectedListModel.items
        if (selected.isEmpty()) {
            log("🛑 <span style='color: #FF5555;'>No files selected. Please add items first.</span>")
            return
        }

        log("🚀 Starting combination process...")

        val allFiles = CodeCombinerCore.scanAndCollectFiles(project, selected)
        if (allFiles.isEmpty()) {
            log("🛑 <span style='color: #FF5555;'>No valid files found in selection (Check exclusions).</span>")
            return
        }

        val saveDir: VirtualFile? = if (currentMode == SelectionMode.SINGLE_DIR) {
            CodeCombinerCore.getOrCreateDevOnly(project, selected[0], allFiles)
        } else {
            val root = ProjectRootManager.getInstance(project).contentRoots.firstOrNull()
            if (root == null) {
                log("🛑 <span style='color: #FF5555;'>Could not determine Project Root.</span>")
                null
            } else {
                CodeCombinerCore.getOrCreateDevOnly(project, root, allFiles)
            }
        }

        if (saveDir != null) {
            try {
                val markdown = CodeCombinerCore.buildMarkdown(CodeCombinerCore.sortFiles(allFiles))
                val resultFile = CodeCombinerCore.saveFile(project, saveDir, markdown)

                log("✅ <span style='color: #55FF55;'><b>Success!</b> File created.</span>")
                log("&nbsp;&nbsp;📁 Name: <b><span style='color: #FFFF55;'>${resultFile.name}</span></b>")
                log("&nbsp;&nbsp;📍 Path: <span style='color: #FFFF55;'>${resultFile.parent}</span>")

                hasSuccessfulCombine = true

            } catch (e: Exception) {
                log("🛑 <span style='color: #FF5555;'>Error saving file: ${e.message}</span>")
            }
        } else {
            log("🛑 <span style='color: #FF5555;'>Failed to create/find target directory.</span>")
        }
    }

    private fun addSelectionFromTree() {
        val selectionPaths = projectTree.selectionPaths
        if (selectionPaths.isNullOrEmpty()) return

        val errors = mutableListOf<String>()
        val toAdd = mutableListOf<VirtualFile>()

        for (path in selectionPaths) {
            val node = path.lastPathComponent as? DefaultMutableTreeNode
            val file = node?.userObject as? VirtualFile ?: continue

            val modeError = checkModeCompatibility(file)
            if (modeError != null) {
                errors.add("⚠️ <span style='color: #FFA500;'>$modeError</span>")
                continue
            }

            val fileErrors = mutableListOf<String>()
            val isValid = deepScanAndValidate(file, fileErrors)

            if (isValid) {
                toAdd.add(file)
            } else {
                errors.addAll(fileErrors)
            }
        }

        if (errors.isEmpty() && toAdd.isNotEmpty()) {
            when (currentMode) {
                SelectionMode.SINGLE_DIR -> {
                    if (selectedListModel.size > 0) {
                        val oldDir = selectedListModel.items[0]
                        val newDir = toAdd[0]
                        if (oldDir.path != newDir.path) {
                            log("ℹ️ Switched directory: <b>'${oldDir.name}'</b> ➝ <b>'${newDir.name}'</b>")
                        }
                        selectedListModel.removeAll()
                    }
                    selectedListModel.add(toAdd[0])
                    log("✅ <span style='color: #55FF55;'>Set directory: <b>'${toAdd[0].name}'</b></span>")
                }
                else -> {
                    var addedCount = 0
                    for (f in toAdd) {
                        if (!selectedListModel.items.contains(f)) {
                            selectedListModel.add(f)
                            log("✅ <span style='color: #55FF55;'>Added: <b>'${f.name}'</b></span>")
                            addedCount++
                        }
                    }
                    if (addedCount == 0) {
                        log("ℹ️ <span style='color: #AAAAAA;'>Items already in list.</span>")
                    }
                }
            }
        } else if (errors.isNotEmpty()) {
            for (err in errors) {
                log(err)
            }
            log("🛑 <span style='color: #FF5555; font-weight: bold;'>Action Blocked. Please fix errors and retry.</span>")
        }
    }

    private fun checkModeCompatibility(file: VirtualFile): String? {
        if (file.isDirectory && excludedFolders.contains(file.name)) return "Folder '${file.name}' is excluded in Settings."
        return when (currentMode) {
            SelectionMode.SINGLE_DIR -> if (!file.isDirectory) "Ignored '${file.name}': Mode is 'Single Directory'." else null
            SelectionMode.MULTI_DIR -> if (!file.isDirectory) "Ignored '${file.name}': Mode is 'Multiple Directories'." else null
            SelectionMode.SPECIFIC_FILES -> if (file.isDirectory) "Ignored folder '${file.name}': Mode is 'Specific Files'." else null
        }
    }

    private fun deepScanAndValidate(item: VirtualFile, errorList: MutableList<String>): Boolean {
        var isValid = true

        fun validateOneFile(f: VirtualFile) {
            if (WolfTheProblemSolver.getInstance(project).isProblemFile(f)) {
                errorList.add("🛑 <span style='color: #FF5555;'>ERROR: '${f.name}' has syntax errors.</span>")
                isValid = false
            }
            val missing = checkMissingHeaders(f)
            if (missing.isNotEmpty()) {
                errorList.add("⚠️ <span style='color: #FFA500;'>WARNING: '${f.name}' is missing/empty: <b>$missing</b></span>")
                isValid = false
            }
        }

        if (item.isDirectory) {
            var isEmpty = true
            VfsUtilCore.visitChildrenRecursively(item, object : VirtualFileVisitor<Any>() {
                override fun visitFile(file: VirtualFile): Boolean {
                    if (file.isDirectory && excludedFolders.contains(file.name)) return false

                    if (!file.isDirectory && !file.name.startsWith(".")) {
                        isEmpty = false
                        validateOneFile(file)
                    }
                    return true
                }
            })
            if (isEmpty) {
                errorList.add("⚠️ <span style='color: #FFA500;'>WARNING: Directory '${item.name}' is empty.</span>")
                isValid = false
            }
        } else {
            validateOneFile(item)
        }

        return isValid
    }

    private fun checkMissingHeaders(file: VirtualFile): List<String> {
        // Updated to use the HARDCODED CONSTANT
        if (!MANDATORY_HEADER_EXTENSIONS.contains(file.extension?.lowercase())) {
            return emptyList()
        }

        val missing = mutableListOf<String>()
        try {
            val headerText = ApplicationManager.getApplication().runReadAction<String> {
                val document = FileDocumentManager.getInstance().getDocument(file)
                if (document != null) document.text else {
                    file.refresh(false, false)
                    String(file.contentsToByteArray())
                }
            }
            val header = headerText.lines().take(20).joinToString("\n")
            if (!header.contains(Regex("""File:[ \t]*\S+""", RegexOption.IGNORE_CASE))) missing.add("File")
            if (!header.contains(Regex("""Version:[ \t]*\S+""", RegexOption.IGNORE_CASE))) missing.add("Version")
            if (!header.contains(Regex("""Description:[ \t]*\S+""", RegexOption.IGNORE_CASE))) missing.add("Description")
            if (!header.contains(Regex("""TAG:[ \t]*\S+""", RegexOption.IGNORE_CASE))) missing.add("TAG")
        } catch (_: Exception) { missing.add("Unreadable") }
        return missing
    }

    private fun buildTreeModel(root: VirtualFile): DefaultTreeModel {
        val rootNode = DefaultMutableTreeNode(root)
        buildNodeRecursively(root, rootNode)
        return DefaultTreeModel(rootNode)
    }

    private fun buildNodeRecursively(file: VirtualFile, node: DefaultMutableTreeNode) {
        val children = file.children
            .filter { !it.name.startsWith(".") && !excludedFolders.contains(it.name) }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name }))
        for (child in children) {
            val childNode = DefaultMutableTreeNode(child)
            node.add(childNode)
            if (child.isDirectory) buildNodeRecursively(child, childNode)
        }
    }

    fun getSelectedItems(): List<VirtualFile> = selectedListModel.items
    fun getCurrentMode(): SelectionMode = currentMode
}

class CombineCodesAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ToolWindowManager.getInstance(project).getToolWindow("Apezon Automation")?.hide(null)
        val dialog = UniversalCollectorDialog(project)
        dialog.show()
    }
}