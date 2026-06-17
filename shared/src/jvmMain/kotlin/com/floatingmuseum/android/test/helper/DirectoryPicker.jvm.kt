package com.floatingmuseum.android.test.helper

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun selectDirectory(
    dialogTitle: String,
    approveButtonText: String,
): String? = withContext(Dispatchers.Main) {
    val osName = System.getProperty("os.name").lowercase()
    if (osName.contains("mac")) {
        try {
            System.setProperty("apple.awt.fileDialogForDirectories", "true")
            val dialog = FileDialog(null as Frame?, dialogTitle, FileDialog.LOAD)
            dialog.isVisible = true
            val directory = dialog.directory
            System.setProperty("apple.awt.fileDialogForDirectories", "false")
            if (directory != null) {
                File(directory).absolutePath
            } else {
                null
            }
        } catch (e: Throwable) {
            fallbackJFileChooser(dialogTitle, approveButtonText)
        }
    } else {
        fallbackJFileChooser(dialogTitle, approveButtonText)
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
