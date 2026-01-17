package com.github.malaareda.apezonautomationsuite.actions

import com.github.malaareda.apezonautomationsuite.settings.ApezonSettings
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.*
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
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
// 1. CORE LOGIC
// =================================================================================================
object DirectoryTreeCore {
    private const val DEV_ONLY_DIR = "devOnly"
    private const val OUTPUT_FOLDER_NAME = "Directory_Tree_Map"

    enum class ScanMode {
        FULL_DEPTH_FILES,
        FULL_DEPTH_FOLDERS_ONLY,
        LIMITED_DEPTH_FOLDERS_ONLY
    }

    data class TreeResult(
        val content: String,
        val fileCount: Int,
        val folderCount: Int,
        val ignoredFolders: Set<String>
    )

    private class ScanContext(
        val mode: ScanMode,
        val maxDepth: Int,
        val settings: ApezonSettings.State,
        var files: Int = 0,
        var folders: Int = 0,
        val ignored: MutableSet<String> = mutableSetOf()
    )

    fun generateTree(project: Project, roots: List<VirtualFile>, mode: ScanMode, limitDepth: Int): TreeResult {
        val settings = ApezonSettings.getInstance(project).state
        val ctx = ScanContext(mode, limitDepth, settings)
        val sb = StringBuilder()

        // FIX: Get Project Root for relative path calculation
        val projectRoot = ProjectRootManager.getInstance(project).contentRoots.firstOrNull()

        for (root in roots) {
            // FIX: Construct Display Name (ProjectName/path/to/selection)
            val displayName = if (projectRoot != null) {
                val relPath = VfsUtilCore.getRelativePath(root, projectRoot)
                if (relPath.isNullOrEmpty()) root.name else "${projectRoot.name}/$relPath"
            } else {
                root.name
            }

            sb.append(displayName).append("\n")
            buildTreeRecursive(root, sb, "", true, 0, ctx)
            sb.append("\n")
        }

        return TreeResult(sb.toString(), ctx.files, ctx.folders, ctx.ignored)
    }

    private fun buildTreeRecursive(
        dir: VirtualFile,
        sb: StringBuilder,
        prefix: String,
        isLast: Boolean,
        currentDepth: Int,
        ctx: ScanContext
    ) {
        val allChildren = dir.children
            .filter { isValidItem(it, ctx) }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name }))

        for (i in allChildren.indices) {
            val child = allChildren[i]
            val childIsLast = (i == allChildren.lastIndex)
            val connector = if (childIsLast) "└── " else "├── "

            sb.append(prefix).append(connector).append(child.name).append("\n")

            if (child.isDirectory) {
                if (ctx.settings.treeTerminalFolders.contains(child.name)) {
                    ctx.folders++
                    ctx.ignored.add(child.name + " (Terminal)")
                    continue
                }

                if (ctx.mode == ScanMode.LIMITED_DEPTH_FOLDERS_ONLY && currentDepth >= ctx.maxDepth - 1) {
                    ctx.folders++
                    continue
                }

                ctx.folders++
                val newPrefix = prefix + if (childIsLast) "    " else "│   "
                buildTreeRecursive(child, sb, newPrefix, childIsLast, currentDepth + 1, ctx)
            } else {
                ctx.files++
            }
        }
    }

    private fun isValidItem(file: VirtualFile, ctx: ScanContext): Boolean {
        if (file.isDirectory) {
            if (ctx.settings.treeExcludedFolders.contains(file.name) || file.name == DEV_ONLY_DIR) {
                ctx.ignored.add(file.name)
                return false
            }
        }
        if (file.name.startsWith(".")) return false
        if (!file.isDirectory) {
            if (ctx.mode != ScanMode.FULL_DEPTH_FILES) return false
        }
        return true
    }

    fun saveFile(project: Project, result: TreeResult, baseFileName: String, scanPathName: String): File? {
        val root = ProjectRootManager.getInstance(project).contentRoots.firstOrNull() ?: return null

        var devOnly = root.findChild(DEV_ONLY_DIR)
        if (devOnly == null) {
            try {
                WriteCommandAction.runWriteCommandAction(project) {
                    devOnly = root.createChildDirectory(this, DEV_ONLY_DIR)
                }
            } catch (e: IOException) { return null }
        }

        var mapDir = devOnly!!.findChild(OUTPUT_FOLDER_NAME)
        if (mapDir == null) {
            try {
                WriteCommandAction.runWriteCommandAction(project) {
                    mapDir = devOnly!!.createChildDirectory(this, OUTPUT_FOLDER_NAME)
                }
            } catch (e: IOException) { return null }
        }

        val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm a")
        val formattedDate = LocalDateTime.now().format(formatter)

        val mdContent = StringBuilder()
        mdContent.append("# Directory Tree for: $scanPathName\n\n")
        mdContent.append("## Project Information\n")
        mdContent.append("- **Date and Time**: $formattedDate\n")
        mdContent.append("- **Total Folders**: ${result.folderCount}\n")
        mdContent.append("- **Total Files**: ${result.fileCount}\n")
        val ignoredStr = if (result.ignoredFolders.isNotEmpty()) result.ignoredFolders.joinToString(", ") else "None"
        mdContent.append("- **Ignored Folders**: $ignoredStr\n\n")
        mdContent.append("```text\n")
        mdContent.append(result.content)
        mdContent.append("```")

        val nextRevision = getNextRevision(mapDir!!, baseFileName)
        val finalFileName = "${baseFileName}_R.$nextRevision.md"
        val ioFile = File(mapDir!!.path, finalFileName)

        try {
            ioFile.writeBytes(mdContent.toString().toByteArray())
            WriteCommandAction.runWriteCommandAction(project) {
                LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile)
            }
            return ioFile
        } catch (e: Exception) { return null }
    }

    private fun getNextRevision(directory: VirtualFile, baseName: String): String {
        val existingFiles = directory.children.filter {
            it.name.startsWith(baseName) && it.name.endsWith(".md") && !it.isDirectory
        }
        if (existingFiles.isEmpty()) return "00"

        var maxRev = -1
        val regex = Regex("""${Regex.escape(baseName)}_R\.(\d+)\.md""")

        existingFiles.forEach { file ->
            val match = regex.matchEntire(file.name)
            if (match != null) {
                val num = match.groupValues[1].toIntOrNull() ?: -1
                if (num > maxRev) maxRev = num
            }
        }
        return String.format("%02d", maxRev + 1)
    }
}

// =================================================================================================
// 2. THE DIALOG
// =================================================================================================

class DirectoryTreeDialog(private val project: Project) : DialogWrapper(project) {

    private lateinit var projectTree: Tree
    private val selectedListModel = CollectionListModel<VirtualFile>()
    private val selectedList = JBList(selectedListModel)

    private val validationArea = JTextPane()
    private val timeFormatter = DateTimeFormatter.ofPattern("hh:mm:ss a")

    private val rbScopeRoot = JBRadioButton("Project Root", true)
    private val rbScopeSelected = JBRadioButton("Selected Directory(s)")

    private val rbOptFull = JBRadioButton("Full Depth (Files + Folders)", true)
    private val rbOptStructure = JBRadioButton("Folder Structure Only (No Files)")
    private val rbOptLimited = JBRadioButton("Limited Depth (Folders Only)")

    private val txtDepth = JTextField("2", 3)
    private val btnAdd = JButton("Add ->")

    init {
        title = "Directory Tree Script"
        setOKButtonText("Generate Tree")
        setCancelButtonText("Close")
        init()
    }

    override fun createActions(): Array<Action> {
        val generateAction = object : DialogWrapperAction("Generate Tree") {
            override fun doAction(e: ActionEvent?) {
                performGeneration()
            }
        }
        return arrayOf(generateAction, cancelAction)
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout())
        mainPanel.preferredSize = Dimension(1200, 800)

        // Settings Panel
        val settingsPanel = JPanel(GridBagLayout())
        settingsPanel.border = IdeBorderFactory.createTitledBorder("Configuration")
        val c = GridBagConstraints()
        c.fill = GridBagConstraints.HORIZONTAL
        c.anchor = GridBagConstraints.WEST
        c.insets = JBUI.insets(5)

        val scopeGroup = ButtonGroup()
        scopeGroup.add(rbScopeRoot)
        scopeGroup.add(rbScopeSelected)

        val scopePanel = JPanel(FlowLayout(FlowLayout.LEFT))
        scopePanel.add(JLabel("Scope:"))
        scopePanel.add(rbScopeRoot)
        scopePanel.add(rbScopeSelected)

        c.gridx = 0; c.gridy = 0; c.gridwidth = 2
        settingsPanel.add(scopePanel, c)

        val optGroup = ButtonGroup()
        optGroup.add(rbOptFull)
        optGroup.add(rbOptStructure)
        optGroup.add(rbOptLimited)

        val optPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        optPanel.add(JLabel("Mode:"))
        optPanel.add(rbOptFull)
        optPanel.add(rbOptStructure)
        optPanel.add(rbOptLimited)
        optPanel.add(JLabel("Depth:"))
        optPanel.add(txtDepth)

        c.gridy = 1
        settingsPanel.add(optPanel, c)

        // UI Logic
        val updateUiState = {
            val isSelectedMode = rbScopeSelected.isSelected
            projectTree.isEnabled = isSelectedMode
            btnAdd.isEnabled = isSelectedMode
            selectedList.isEnabled = isSelectedMode

            projectTree.background = if(isSelectedMode) UIUtil.getTreeBackground() else UIUtil.getPanelBackground()
            txtDepth.isEnabled = rbOptLimited.isSelected
        }

        rbScopeRoot.addActionListener { updateUiState() }
        rbScopeSelected.addActionListener { updateUiState() }
        rbOptFull.addActionListener { updateUiState() }
        rbOptStructure.addActionListener { updateUiState() }
        rbOptLimited.addActionListener { updateUiState() }

        // Tree & List
        val root = ProjectRootManager.getInstance(project).contentRoots.firstOrNull()
        val treeModel = if (root != null) buildTreeModel(root) else DefaultTreeModel(DefaultMutableTreeNode("No Root"))
        projectTree = Tree(treeModel)
        projectTree.selectionModel.selectionMode = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION
        val treeScroll = JBScrollPane(projectTree)
        treeScroll.border = IdeBorderFactory.createTitledBorder("Project Structure")

        selectedList.cellRenderer = object : ColoredListCellRenderer<VirtualFile>() {
            override fun customizeCellRenderer(list: JList<out VirtualFile>, value: VirtualFile?, index: Int, selected: Boolean, hasFocus: Boolean) {
                if (value == null) return
                append(value.name)
                append(" (${value.parent?.path ?: ""})", SimpleTextAttributes.GRAY_ATTRIBUTES)
                icon = IconUtil.getIcon(value, 0, project)
            }
        }
        val listPanel = ToolbarDecorator.createDecorator(selectedList)
            .setRemoveAction {
                val indices = selectedList.selectedIndices
                for (i in indices.reversed()) selectedListModel.remove(i)
            }
            .disableAddAction()
            .createPanel()
        listPanel.border = IdeBorderFactory.createTitledBorder("Selected Directories")

        btnAdd.addActionListener { addSelection() }
        val btnPanel = JPanel(GridBagLayout())
        btnPanel.add(btnAdd, GridBagConstraints())

        // Console
        validationArea.contentType = "text/html"
        validationArea.isEditable = false
        validationArea.background = UIUtil.getPanelBackground()
        val consoleScroll = JBScrollPane(validationArea)
        consoleScroll.border = IdeBorderFactory.createTitledBorder("Operation Log")
        consoleScroll.preferredSize = Dimension(1100, 200)

        // Layout
        val topSplit = Splitter(false, 0.4f)
        topSplit.firstComponent = treeScroll
        val rightContainer = JPanel(BorderLayout())
        rightContainer.add(btnPanel, BorderLayout.WEST)
        rightContainer.add(listPanel, BorderLayout.CENTER)
        topSplit.secondComponent = rightContainer

        val mainSplit = Splitter(true, 0.65f)
        val upperPart = JPanel(BorderLayout())
        upperPart.add(settingsPanel, BorderLayout.NORTH)
        upperPart.add(topSplit, BorderLayout.CENTER)

        mainSplit.firstComponent = upperPart
        mainSplit.secondComponent = consoleScroll

        mainPanel.add(mainSplit, BorderLayout.CENTER)

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

        updateUiState()
        return mainPanel
    }

    private fun addSelection() {
        val paths = projectTree.selectionPaths ?: return
        var count = 0
        for (path in paths) {
            val node = path.lastPathComponent as DefaultMutableTreeNode
            val file = node.userObject as VirtualFile
            if (file.isDirectory && !selectedListModel.items.contains(file)) {
                selectedListModel.add(file)
                count++
            }
        }
        if (count > 0) log("✅ Added $count directories.")
    }

    private fun performGeneration() {
        val targets = mutableListOf<VirtualFile>()
        val baseName: String
        val scanPathName: String

        if (rbScopeRoot.isSelected) {
            val root = ProjectRootManager.getInstance(project).contentRoots.firstOrNull()
            if (root == null) {
                log("🛑 Error: No Project Root found.")
                return
            }
            targets.add(root)
            baseName = "rootDirectoryTree"
            scanPathName = "Project Root (${root.name})"
        } else {
            if (selectedListModel.isEmpty) {
                log("🛑 Error: No directories selected.")
                return
            }
            targets.addAll(selectedListModel.items)
            baseName = "SelectedDirectoryTree"
            scanPathName = if(targets.size == 1) targets[0].name else "Multiple Directories (${targets.size})"
        }

        val mode = when {
            rbOptFull.isSelected -> DirectoryTreeCore.ScanMode.FULL_DEPTH_FILES
            rbOptStructure.isSelected -> DirectoryTreeCore.ScanMode.FULL_DEPTH_FOLDERS_ONLY
            else -> DirectoryTreeCore.ScanMode.LIMITED_DEPTH_FOLDERS_ONLY
        }

        var depth = 100
        if (mode == DirectoryTreeCore.ScanMode.LIMITED_DEPTH_FOLDERS_ONLY) {
            try {
                depth = txtDepth.text.toInt()
                if (depth < 1) throw NumberFormatException()
            } catch (e: Exception) {
                log("🛑 Error: Invalid depth.")
                return
            }
        }

        log("🚀 Generating tree for: <b>$scanPathName</b>...")

        try {
            val result = DirectoryTreeCore.generateTree(project, targets, mode, depth)
            val savedFile = DirectoryTreeCore.saveFile(project, result, baseName, scanPathName)

            if (savedFile != null) {
                log("✅ <span style='color: #55FF55;'><b>Success!</b> Tree saved.</span>")
                log("&nbsp;&nbsp;📁 Name: <b><span style='color: #FFFF55;'>${savedFile.name}</span></b>")
                log("&nbsp;&nbsp;📍 Path: <span style='color: #FFFF55;'>${savedFile.parent}</span>")
            } else {
                log("🛑 Failed to save file.")
            }
        } catch (e: Exception) {
            log("🛑 Error: ${e.message}")
        }
    }

    private fun log(message: String) {
        val time = LocalDateTime.now().format(timeFormatter)
        val current = try {
            validationArea.text.substringAfter("<body>").substringBeforeLast("</body>")
        } catch (e: Exception) { "" }
        val entry = "<div style='margin: 2px;'><span style='color: #888888;'>[$time]</span> $message</div>"
        validationArea.text = "<html><body style='font-family: sans-serif; font-size: 11px; margin: 2px;'>$current$entry</body></html>"
        SwingUtilities.invokeLater { validationArea.caretPosition = validationArea.document.length }
    }

    private fun buildTreeModel(root: VirtualFile): DefaultTreeModel {
        val rootNode = DefaultMutableTreeNode(root)
        val settings = ApezonSettings.getInstance(project).state
        buildNodeRecursively(root, rootNode, settings)
        return DefaultTreeModel(rootNode)
    }

    private fun buildNodeRecursively(file: VirtualFile, node: DefaultMutableTreeNode, settings: ApezonSettings.State) {
        val children = file.children
            .filter {
                !it.name.startsWith(".") &&
                        !settings.treeExcludedFolders.contains(it.name) &&
                        it.isDirectory
            }
            .sortedBy { it.name }

        for (child in children) {
            val childNode = DefaultMutableTreeNode(child)
            node.add(childNode)
            buildNodeRecursively(child, childNode, settings)
        }
    }
}

class DirectoryTreeAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ToolWindowManager.getInstance(project).getToolWindow("Apezon Automation")?.hide(null)
        val dialog = DirectoryTreeDialog(project)
        dialog.show()
    }
}