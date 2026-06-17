package com.floatingmuseum.android.test.helper

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun selectDirectory(
    dialogTitle: String,
    approveButtonText: String,
): String? {
    val osName = System.getProperty("os.name").lowercase()
    val nativeResult = when {
        osName.contains("win") -> withContext(Dispatchers.IO) {
            selectDirectoryWithWindowsShell(dialogTitle)
        }
        osName.contains("mac") || osName.contains("darwin") -> withContext(Dispatchers.Main) {
            selectDirectoryWithMacFileDialog(dialogTitle)
        }
        else -> withContext(Dispatchers.IO) {
            selectDirectoryWithLinuxDialog(dialogTitle)
        }
    }
    return when (nativeResult) {
        NativePickerResult.Cancelled -> null
        is NativePickerResult.Selected -> nativeResult.path
        NativePickerResult.Unavailable -> withContext(Dispatchers.Main) {
            fallbackJFileChooser(dialogTitle, approveButtonText)
        }
    }
}

actual suspend fun selectApkFiles(
    dialogTitle: String,
    approveButtonText: String,
): List<String> = withContext(Dispatchers.Main) {
    selectApkFilesWithNativeDialog(dialogTitle)
        ?: fallbackJFileChooserApkFiles(dialogTitle, approveButtonText)
}

private fun selectApkFilesWithNativeDialog(
    dialogTitle: String,
): List<String>? {
    return try {
        val dialog = FileDialog(null as Frame?, dialogTitle, FileDialog.LOAD).apply {
            isMultipleMode = true
            directory = System.getProperty("user.home")
            file = "*.apk"
            filenameFilter = java.io.FilenameFilter { _, name ->
                name.endsWith(".apk", ignoreCase = true)
            }
        }
        dialog.isVisible = true
        dialog.files
            .map { it.absolutePath }
            .filter { it.isNotBlank() }
    } catch (e: Throwable) {
        null
    }
}

private fun selectDirectoryWithWindowsShell(
    dialogTitle: String,
): NativePickerResult {
    val escapedTitle = dialogTitle.replace("'", "''")
    val script = """
        [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
        ${'$'}shell = New-Object -ComObject Shell.Application
        ${'$'}folder = ${'$'}shell.BrowseForFolder(0, '$escapedTitle', 0x41, 0)
        if (${'$'}folder -ne ${'$'}null) { ${'$'}folder.Self.Path }
    """.trimIndent()
    return executePathPickerCommand(
        listOf(
            "powershell.exe",
            "-NoProfile",
            "-STA",
            "-ExecutionPolicy",
            "Bypass",
            "-Command",
            script,
        )
    )
}

private fun selectDirectoryWithMacFileDialog(
    dialogTitle: String,
): NativePickerResult {
    return try {
        System.setProperty("apple.awt.fileDialogForDirectories", "true")
        val dialog = FileDialog(null as Frame?, dialogTitle, FileDialog.LOAD)
        dialog.isVisible = true
        dialog.directory
            ?.let { NativePickerResult.Selected(File(it).absolutePath) }
            ?: NativePickerResult.Cancelled
    } catch (e: Throwable) {
        NativePickerResult.Unavailable
    } finally {
        System.setProperty("apple.awt.fileDialogForDirectories", "false")
    }
}

private fun selectDirectoryWithLinuxDialog(
    dialogTitle: String,
): NativePickerResult {
    val zenityResult = executePathPickerCommand(
        listOf(
            "zenity",
            "--file-selection",
            "--directory",
            "--title=$dialogTitle",
        )
    )
    return if (zenityResult != NativePickerResult.Unavailable) {
        zenityResult
    } else {
        executePathPickerCommand(
            listOf(
                "kdialog",
                "--title",
                dialogTitle,
                "--getexistingdirectory",
                System.getProperty("user.home"),
            )
        )
    }
}

private fun executePathPickerCommand(
    command: List<String>,
): NativePickerResult {
    return try {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.reader(Charsets.UTF_8).readText().trim()
        val exitCode = process.waitFor()
        if (exitCode == 0 && output.isNotBlank()) {
            NativePickerResult.Selected(File(output).absolutePath)
        } else {
            NativePickerResult.Cancelled
        }
    } catch (e: Throwable) {
        NativePickerResult.Unavailable
    }
}

private sealed interface NativePickerResult {
    data class Selected(val path: String) : NativePickerResult
    data object Cancelled : NativePickerResult
    data object Unavailable : NativePickerResult
}

private fun fallbackJFileChooserApkFiles(
    dialogTitle: String,
    approveButtonText: String,
): List<String> {
    try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
    } catch (e: Throwable) {
        // ignore
    }
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.FILES_ONLY
        isMultiSelectionEnabled = true
        this.dialogTitle = dialogTitle
        this.approveButtonText = approveButtonText
        fileFilter = FileNameExtensionFilter("Android APK (*.apk)", "apk")
        currentDirectory = File(System.getProperty("user.home"))
    }
    val result = chooser.showDialog(null, approveButtonText)
    return if (result == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFiles
            .map { it.absolutePath }
            .filter { it.isNotBlank() }
    } else {
        emptyList()
    }
}

private fun fallbackJFileChooser(
    dialogTitle: String,
    approveButtonText: String,
): String? {
    try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
    } catch (e: Throwable) {
        // ignore
    }
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        this.dialogTitle = dialogTitle
        this.approveButtonText = approveButtonText
        currentDirectory = File(System.getProperty("user.home"))
    }
    val result = chooser.showDialog(null, approveButtonText)
    return if (result == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile.absolutePath
    } else {
        null
    }
}

actual fun saveBytesToFile(directoryPath: String, fileName: String, bytes: ByteArray) {
    File(directoryPath, fileName).writeBytes(bytes)
}

