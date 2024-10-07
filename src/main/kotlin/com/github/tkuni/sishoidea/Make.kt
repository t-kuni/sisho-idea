package com.github.tkuni.sishoidea

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import javax.swing.*
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class Make : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val selectedFiles = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
        val isChainMake = event.presentation.text == "Chain Make"

        val dialog = MultiLineInputDialog(project)
        if (!dialog.showAndGet()) return

        val input = dialog.getInput()

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Executing Sisho Make") {
            override fun run(indicator: ProgressIndicator) {
                if (isChainMake) {
                    updateDependencyGraph(project, indicator)
                }

                executeMake(project, selectedFiles, indicator, isChainMake, input)
            }
        })
    }

    private class MultiLineInputDialog(project: Project) : DialogWrapper(project) {
        private val textArea = JTextArea(10, 50)

        init {
            init()
            title = "Enter Additional Instructions"
        }

        override fun createCenterPanel(): JComponent {
            val panel = JPanel(BorderLayout())
            textArea.preferredSize = Dimension(400, 200)
            panel.add(JScrollPane(textArea), BorderLayout.CENTER)
            return panel
        }

        override fun show() {
            super.show()
            textArea.requestFocusInWindow()
        }

        fun getInput(): String = textArea.text
    }

    private fun updateDependencyGraph(project: Project, indicator: ProgressIndicator) {
        indicator.text = "Updating dependency graph..."
        executeCommand(project, listOf(getSishoPath(), "deps-graph"), null)
    }

    private fun executeMake(project: Project, files: Array<VirtualFile>, indicator: ProgressIndicator, isChainMake: Boolean, input: String?) {
        for (file in files) {
            indicator.text = "Executing make for ${file.name}"
            val relativePath = getRelativePath(project, file)
            val command = mutableListOf(getSishoPath(), "make", "-a", "-i")
            if (isChainMake) {
                command.add("-c")
            }
            command.add(relativePath)
            executeCommandWithRealTimeOutput(project, command, input)
        }
    }

    private fun executeCommand(project: Project, command: List<String>, input: String?): String {
        val processBuilder = ProcessBuilder(command)
        processBuilder.directory(File(project.basePath))
        processBuilder.redirectErrorStream(true)

        val process = processBuilder.start()

        if (input != null) {
            process.outputStream.bufferedWriter().use { it.write(input) }
        }

        val output = StringBuilder()
        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
        }

        process.waitFor()
        return output.toString()
    }

    private fun executeCommandWithRealTimeOutput(project: Project, command: List<String>, input: String?) {
        val processBuilder = ProcessBuilder(command)
        processBuilder.directory(File(project.basePath))
        processBuilder.redirectErrorStream(true)

        val process = processBuilder.start()

        ApplicationManager.getApplication().invokeLater {
            val toolWindow = getOrCreateToolWindow(project)
            val contentManager = toolWindow.contentManager
            val textArea = JTextArea()
            textArea.isEditable = false
            val scrollPane = JScrollPane(textArea)
            val content = contentManager.factory.createContent(scrollPane, getContentTitle(command), false)
            contentManager.addContent(content)
            toolWindow.show()

            Thread {
                if (input != null) {
                    process.outputStream.bufferedWriter().use { it.write(input) }
                }

                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    reader.lines().forEach { line ->
                        SwingUtilities.invokeLater {
                            textArea.append(line + "\n")
                            textArea.caretPosition = textArea.document.length
                        }
                    }
                }
                process.waitFor()
            }.start()
        }
    }

    private fun getContentTitle(command: List<String>): String {
        val action = command[1]
        val target = command.last()
        return "$action: $target"
    }

    private fun getRelativePath(project: Project, file: VirtualFile): String {
        return File(project.basePath).toURI().relativize(File(file.path).toURI()).path
    }

    private fun getOrCreateToolWindow(project: Project): com.intellij.openapi.wm.ToolWindow {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        var toolWindow = toolWindowManager.getToolWindow("Sisho Output")

        if (toolWindow == null) {
            toolWindow = toolWindowManager.registerToolWindow("Sisho Output", true, com.intellij.openapi.wm.ToolWindowAnchor.BOTTOM)
        }

        return toolWindow
    }

    private fun getSishoPath(): String {
        val homeDir = System.getProperty("user.home")
        return "$homeDir/go/bin/sisho"
    }
}