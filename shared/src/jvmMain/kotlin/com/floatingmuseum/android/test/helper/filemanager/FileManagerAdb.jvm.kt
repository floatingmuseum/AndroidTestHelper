package com.floatingmuseum.android.test.helper.filemanager

import com.floatingmuseum.android.test.helper.adb.AdbShell
import java.io.File

actual fun createFileManagerAdb(): FileManagerAdb = JvmFileManagerAdb()

private class JvmFileManagerAdb : FileManagerAdb {
    override suspend fun listDirectory(
        deviceSerial: String,
        remotePath: String,
        logCommand: (String) -> Unit,
    ): List<RemoteFileEntry> {
        val normalizedPath = normalizeRemotePath(remotePath)
        val listPath = remoteDirectoryListArgument(normalizedPath)
        val output = AdbShell.executeAdb(
            args = listOf("-s", deviceSerial, "shell", "ls", "-la", shellQuote(listPath)),
            displayCommand = "adb -s $deviceSerial shell ls -la ${shellQuote(listPath)}",
            logCommand = logCommand,
        )
        return parseRemoteDirectoryListing(normalizedPath, output)
    }

    override suspend fun exportPath(
        deviceSerial: String,
        remotePath: String,
        localDirectoryPath: String,
        logCommand: (String) -> Unit,
    ): String {
        val normalizedPath = normalizeRemotePath(remotePath)
        val localDirectory = File(localDirectoryPath).absoluteFile
        localDirectory.mkdirs()
        AdbShell.executeAdb(
            args = listOf("-s", deviceSerial, "pull", normalizedPath, localDirectory.absolutePath),
            displayCommand = "adb -s $deviceSerial pull ${quoteDisplay(normalizedPath)} ${quoteDisplay(localDirectory.absolutePath)}",
            logCommand = logCommand,
        )
        return File(localDirectory, remoteFileName(normalizedPath)).absolutePath
    }

    override suspend fun uploadFiles(
        deviceSerial: String,
        localFilePaths: List<String>,
        remoteDirectoryPath: String,
        logCommand: (String) -> Unit,
    ): Int {
        val normalizedDirectory = normalizeRemotePath(remoteDirectoryPath)
        var uploadedCount = 0
        localFilePaths
            .map { File(it).absoluteFile }
            .filter { it.exists() }
            .forEach { localFile ->
                val remoteTargetPath = remoteUploadTargetPath(
                    remoteDirectoryPath = normalizedDirectory,
                    localFile = localFile,
                )
                AdbShell.executeAdb(
                    args = listOf("-s", deviceSerial, "push", localFile.absolutePath, remoteTargetPath),
                    displayCommand = "adb -s $deviceSerial push ${quoteDisplay(localFile.absolutePath)} ${quoteDisplay(remoteTargetPath)}",
                    logCommand = logCommand,
                )
                uploadedCount += 1
            }
        if (uploadedCount == 0) {
            throw IllegalArgumentException("没有可上传的本地文件")
        }
        return uploadedCount
    }
}

internal fun remoteDirectoryListArgument(path: String): String {
    val normalizedPath = normalizeRemotePath(path)
    return if (normalizedPath == "/") "/" else "$normalizedPath/"
}

internal fun remoteUploadTargetPath(
    remoteDirectoryPath: String,
    localFile: File,
): String {
    val normalizedDirectory = normalizeRemotePath(remoteDirectoryPath)
    return if (localFile.isDirectory) {
        remoteDirectoryListArgument(normalizedDirectory)
    } else {
        childRemotePath(normalizedDirectory, localFile.name)
    }
}

private fun shellQuote(value: String): String {
    return "'${value.replace("'", "'\\''")}'"
}

private fun quoteDisplay(value: String): String {
    return if (value.contains(' ')) "\"$value\"" else value
}
