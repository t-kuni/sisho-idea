package com.github.tkuni.sishoidea

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import java.io.BufferedReader
import java.io.InputStreamReader

class MyShellAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        // 実行するシェルコマンドを定義
        val command = listOf("sh", "-c", "echo Hello, World!") // Unix系コマンド例

        try {
            // ProcessBuilderでシェルコマンドを実行
            val processBuilder = ProcessBuilder(command)
            processBuilder.redirectErrorStream(true)
            val process = processBuilder.start()

            // コマンドの出力を取得
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String? = reader.readLine()
            while (line != null) {
                output.append(line).append("\n")
                line = reader.readLine()
            }
            process.waitFor()

            // 出力をIDEのメッセージダイアログで表示
            Messages.showMessageDialog(event.project, output.toString(), "Command Output", Messages.getInformationIcon())

        } catch (e: Exception) {
            // エラーハンドリング
            Messages.showErrorDialog(event.project, "An error occurred: ${e.message}", "Error")
        }
    }
}
