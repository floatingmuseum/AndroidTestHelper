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

    override suspend fun deletePath(
        deviceSerial: String,
        remotePath: String,
        logCommand: (String) -> Unit,
    ) {
        val normalizedPath = normalizeRemotePath(remotePath)
        require(normalizedPath != "/") { "不能删除设备根目录" }
        AdbShell.executeAdb(
            args = listOf("-s", deviceSerial, "shell", "rm", "-rf", shellQuote(normalizedPath)),
            displayCommand = "adb -s $deviceSerial shell rm -rf ${shellQuote(normalizedPath)}",
            logCommand = logCommand,
        )
    }

    override suspend fun createPath(
        deviceSerial: String,
        remoteDirectoryPath: String,
        name: String,
        type: RemoteCreateType,
        logCommand: (String) -> Unit,
    ): String {
        val normalizedDirectory = normalizeRemotePath(remoteDirectoryPath)
        val childName = validateRemoteChildName(name)
        val targetPath = childRemotePath(normalizedDirectory, childName)
        val args = when (type) {
            RemoteCreateType.File -> listOf("-s", deviceSerial, "shell", "touch", shellQuote(targetPath))
            RemoteCreateType.Directory -> listOf("-s", deviceSerial, "shell", "mkdir", "-p", shellQuote(targetPath))
        }
        val displayCommand = when (type) {
            RemoteCreateType.File -> "adb -s $deviceSerial shell touch ${shellQuote(targetPath)}"
            RemoteCreateType.Directory -> "adb -s $deviceSerial shell mkdir -p ${shellQuote(targetPath)}"
        }
        AdbShell.executeAdb(
            args = args,
            displayCommand = displayCommand,
            logCommand = logCommand,
        )
        return targetPath
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
