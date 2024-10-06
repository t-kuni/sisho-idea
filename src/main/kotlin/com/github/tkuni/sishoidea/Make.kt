package com.github.tkuni.sishoidea

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
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

        val input = showMultiLineInputDialog(project)

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Executing Sisho Make") {
            override fun run(indicator: ProgressIndicator) {
                if (isChainMake) {
                    updateDependencyGraph(project, indicator)
                }

                executeMake(project, selectedFiles, indicator, isChainMake, input)
            }
        })
    }

    private fun showMultiLineInputDialog(project: Project): String? {
        val dialog = object : DialogWrapper(project) {
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

            fun getInput(): String = textArea.text
        }

        return if (dialog.showAndGet()) {
            dialog.getInput()
        } else {
            null
        }
    }

    private fun updateDependencyGraph(project: Project, indicator: ProgressIndicator) {
        indicator.text = "Updating dependency graph..."
        executeCommand(project, listOf(getSishoPath(), "deps-graph"), null)
    }

    private fun executeMake(project: Project, files: Array<VirtualFile>, indicator: ProgressIndicator, isChainMake: Boolean, input: String?) {
        for (file in files) {
            indicator.text = "Executing make for ${file.name}"
            val relativePath = getRelativePath(project, file)
            val command = mutableListOf(getSishoPath(), "make")
            if (isChainMake) {
                command.add("-c")
            }
            command.add(relativePath)
            val output = executeCommand(project, command, input)
            showOutput(project, output)
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

    private fun getRelativePath(project: Project, file: VirtualFile): String {
        return File(project.basePath).toURI().relativize(File(file.path).toURI()).path
    }

    private fun showOutput(project: Project, output: String) {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        var toolWindow = toolWindowManager.getToolWindow("Sisho Output")

        if (toolWindow == null) {
            toolWindow = toolWindowManager.registerToolWindow("Sisho Output", true, com.intellij.openapi.wm.ToolWindowAnchor.BOTTOM)
        }

        val contentFactory = ContentFactory.SERVICE.getInstance()
        val textArea = JTextArea(output)
        textArea.isEditable = false
        val scrollPane = JScrollPane(textArea)

        val content = contentFactory.createContent(scrollPane, "", false)
        toolWindow.contentManager.addContent(content)
        toolWindow.show()
    }

    private fun getSishoPath(): String {
        val homeDir = System.getProperty("user.home")
        return "$homeDir/go/bin/sisho"
    }
}