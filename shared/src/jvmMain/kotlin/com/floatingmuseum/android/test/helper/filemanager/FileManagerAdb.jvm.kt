package com.floatingmuseum.android.test.helper.filemanager

import com.floatingmuseum.android.test.helper.adb.AdbShell
import com.floatingmuseum.android.test.helper.adb.AdbCommandException
import com.floatingmuseum.android.test.helper.localization.commandStatus
import com.floatingmuseum.android.test.helper.localization.localized
import com.floatingmuseum.android.test.helper.settings.AppSettingsShared
import com.floatingmuseum.android.test.helper.AppRuntimePaths
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

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
        onProgress: (FileUploadProgress) -> Unit,
    ): Int {
        val normalizedDirectory = normalizeRemotePath(remoteDirectoryPath)
        val localFiles = localFilePaths
            .map { File(it).absoluteFile }
            .filter { it.exists() }
        if (localFiles.isEmpty()) {
            throw IllegalArgumentException(localized("file_manager.no_local_files_available_to_upload"))
        }
        val totalBytes = localFiles.sumOf { uploadSourceSizeBytes(it) }
        var completedBytes = 0L
        var uploadedCount = 0
        localFiles
            .forEachIndexed { index, localFile ->
                val sourceBytes = uploadSourceSizeBytes(localFile)
                fun emitProgress(currentFileBytes: Long) {
                    onProgress(
                        FileUploadProgress(
                            currentFileName = localFile.name,
                            currentFileIndex = index + 1,
                            totalFiles = localFiles.size,
                            completedBytes = (completedBytes + currentFileBytes).coerceAtMost(totalBytes),
                            totalBytes = totalBytes,
                            currentFileBytes = currentFileBytes.coerceAtMost(sourceBytes),
                            currentFileTotalBytes = sourceBytes,
                        ),
                    )
                }

                emitProgress(0L)
                val remoteTargetPath = remoteUploadTargetPath(
                    remoteDirectoryPath = normalizedDirectory,
                    localFile = localFile,
                )
                executeAdbPushWithProgress(
                    deviceSerial = deviceSerial,
                    remoteTargetPath = remoteTargetPath,
                    args = listOf("-s", deviceSerial, "push", localFile.absolutePath, remoteTargetPath),
                    displayCommand = "adb -s $deviceSerial push ${quoteDisplay(localFile.absolutePath)} ${quoteDisplay(remoteTargetPath)}",
                    logCommand = logCommand,
                    onPercent = { percent ->
                        val currentFileBytes = sourceBytes * percent.coerceIn(0, 100) / 100L
                        emitProgress(currentFileBytes)
                    },
                    onRemoteSize = { remoteBytes ->
                        emitProgress(remoteBytes)
                    },
                )
                emitProgress(sourceBytes)
                completedBytes = (completedBytes + sourceBytes).coerceAtMost(totalBytes)
                uploadedCount += 1
            }
        return uploadedCount
    }

    override suspend fun deletePath(
        deviceSerial: String,
        remotePath: String,
        logCommand: (String) -> Unit,
    ) {
        val normalizedPath = normalizeRemotePath(remotePath)
        require(normalizedPath != "/") { localized("file_manager.cannot_delete_the_device_root_directory") }
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

    override suspend fun readFileContent(
        deviceSerial: String,
        remotePath: String,
        limitBytes: Long?,
        logCommand: (String) -> Unit,
    ): String {
        val normalizedPath = normalizeRemotePath(remotePath)
        val useTail = limitBytes != null
        val normalCmd = if (useTail) {
            listOf("tail", "-c", limitBytes.toString(), shellQuote(normalizedPath))
        } else {
            listOf("cat", shellQuote(normalizedPath))
        }

        try {
            return AdbShell.executeAdb(
                args = listOf("-s", deviceSerial, "shell") + normalCmd,
                displayCommand = "adb -s $deviceSerial shell " + normalCmd.joinToString(" ") { if (it.startsWith("'")) it else shellQuote(it) },
                logCommand = logCommand,
            )
        } catch (e: Exception) {
            val suCmdStr = if (useTail) {
                "tail -c $limitBytes ${shellQuote(normalizedPath)}"
            } else {
                "cat ${shellQuote(normalizedPath)}"
            }
            try {
                return AdbShell.executeAdb(
                    args = listOf("-s", deviceSerial, "shell", "su", "-c", suCmdStr),
                    displayCommand = "adb -s $deviceSerial shell su -c \"$suCmdStr\"",
                    logCommand = logCommand,
                )
            } catch (suEx: Exception) {
                throw e
            }
        }
    }

    override suspend fun readImageBytes(
        deviceSerial: String,
        remotePath: String,
        logCommand: (String) -> Unit,
    ): ByteArray {
        val normalizedPath = normalizeRemotePath(remotePath)
        val tempDir = AppRuntimePaths.createTempDirectory("file_editor_img")
        try {
            val localPath = exportPath(deviceSerial, normalizedPath, tempDir.absolutePath, logCommand)
            return withContext(Dispatchers.IO) {
                File(localPath).readBytes()
            }
        } finally {
            try {
                tempDir.deleteRecursively()
            } catch (_: Exception) {}
        }
    }

    override suspend fun saveFileContent(
        deviceSerial: String,
        remotePath: String,
        content: String,
        logCommand: (String) -> Unit,
    ) {
        val normalizedPath = normalizeRemotePath(remotePath)
        var hasBackup = false
        val backupPath = "$normalizedPath.bak"

        // 1. 尝试在设备端进行备份
        try {
            AdbShell.executeAdb(
                args = listOf("-s", deviceSerial, "shell", "cp", shellQuote(normalizedPath), shellQuote(backupPath)),
                displayCommand = "adb -s $deviceSerial shell cp ${shellQuote(normalizedPath)} ${shellQuote(backupPath)}",
                logCommand = logCommand,
            )
            hasBackup = true
        } catch (_: Exception) {
            // 尝试 Root 提权备份
            try {
                AdbShell.executeAdb(
                    args = listOf("-s", deviceSerial, "shell", "su", "-c", "cp ${shellQuote(normalizedPath)} ${shellQuote(backupPath)}"),
                    displayCommand = "adb -s $deviceSerial shell su -c \"cp ${shellQuote(normalizedPath)} ${shellQuote(backupPath)}\"",
                    logCommand = logCommand,
                )
                hasBackup = true
            } catch (_: Exception) {
                // 备份失败说明文件可能不存在，或者无法备份，继续执行
            }
        }

        val tempDir = AppRuntimePaths.createTempDirectory("file_editor")
        val tempLocalFile = File(tempDir, "editor_temp.txt")
        var hasPushed = false

        try {
            // 2. 将内容写入电脑本地的临时文件
            withContext(Dispatchers.IO) {
                tempLocalFile.writeText(content, Charsets.UTF_8)
            }

            // 3. 尝试常规 push
            try {
                AdbShell.executeAdb(
                    args = listOf("-s", deviceSerial, "push", tempLocalFile.absolutePath, normalizedPath),
                    displayCommand = "adb -s $deviceSerial push ${quoteDisplay(tempLocalFile.absolutePath)} ${quoteDisplay(normalizedPath)}",
                    logCommand = logCommand,
                )
                hasPushed = true
            } catch (pushErr: Exception) {
                // 常规 push 失败，尝试 Root 提权写入
                val tempDevicePath = "/data/local/tmp/ath_temp_edit"
                try {
                    AdbShell.executeAdb(
                        args = listOf("-s", deviceSerial, "push", tempLocalFile.absolutePath, tempDevicePath),
                        displayCommand = "adb -s $deviceSerial push ${quoteDisplay(tempLocalFile.absolutePath)} $tempDevicePath",
                        logCommand = logCommand,
                    )
                    AdbShell.executeAdb(
                        args = listOf("-s", deviceSerial, "shell", "su", "-c", "cp $tempDevicePath ${shellQuote(normalizedPath)}"),
                        displayCommand = "adb -s $deviceSerial shell su -c \"cp $tempDevicePath ${shellQuote(normalizedPath)}\"",
                        logCommand = logCommand,
                    )
                    hasPushed = true
                } catch (suErr: Exception) {
                    throw pushErr // 抛出最初的写入错误
                } finally {
                    // 清理公共临时文件
                    try {
                        AdbShell.executeAdb(
                            args = listOf("-s", deviceSerial, "shell", "rm", "-f", tempDevicePath),
                            displayCommand = "adb -s $deviceSerial shell rm -f $tempDevicePath",
                            logCommand = logCommand,
                        )
                    } catch (_: Exception) {
                        try {
                            AdbShell.executeAdb(
                                args = listOf("-s", deviceSerial, "shell", "su", "-c", "rm -f $tempDevicePath"),
                                displayCommand = "adb -s $deviceSerial shell su -c \"rm -f $tempDevicePath\"",
                                logCommand = logCommand,
                            )
                        } catch (_: Exception) {}
                    }
                }
            }

            // 4. 写入成功后清理远程备份
            if (hasPushed && hasBackup) {
                try {
                    AdbShell.executeAdb(
                        args = listOf("-s", deviceSerial, "shell", "rm", "-f", shellQuote(backupPath)),
                        displayCommand = "adb -s $deviceSerial shell rm -f ${shellQuote(backupPath)}",
                        logCommand = logCommand,
                    )
                } catch (_: Exception) {
                    try {
                        AdbShell.executeAdb(
                            args = listOf("-s", deviceSerial, "shell", "su", "-c", "rm -f ${shellQuote(backupPath)}"),
                            displayCommand = "adb -s $deviceSerial shell su -c \"rm -f ${shellQuote(backupPath)}\"",
                            logCommand = logCommand,
                        )
                    } catch (_: Exception) {}
                }
            }

        } catch (e: Exception) {
            // 5. 写入中发生任何异常，如果存在备份则尝试回滚
            if (hasBackup) {
                try {
                    AdbShell.executeAdb(
                        args = listOf("-s", deviceSerial, "shell", "mv", shellQuote(backupPath), shellQuote(normalizedPath)),
                        displayCommand = "adb -s $deviceSerial shell mv ${shellQuote(backupPath)} ${shellQuote(normalizedPath)}",
                        logCommand = logCommand,
                    )
                } catch (_: Exception) {
                    try {
                        AdbShell.executeAdb(
                            args = listOf("-s", deviceSerial, "shell", "su", "-c", "mv ${shellQuote(backupPath)} ${shellQuote(normalizedPath)}"),
                            displayCommand = "adb -s $deviceSerial shell su -c \"mv ${shellQuote(backupPath)} ${shellQuote(normalizedPath)}\"",
                            logCommand = logCommand,
                        )
                    } catch (_: Exception) {}
                }
            }
            throw e
        } finally {
            // 6. 最终清理本地临时文件和目录
            try {
                if (tempLocalFile.exists()) {
                    tempLocalFile.delete()
                }
                if (tempDir.exists()) {
                    tempDir.delete()
                }
            } catch (_: Exception) {}
        }
    }
}

internal fun parseAdbPushProgressPercent(outputChunk: String): Int? {
    return Regex("""(?:^|\D)(\d{1,3})%""")
        .findAll(outputChunk)
        .mapNotNull { match -> match.groupValues[1].toIntOrNull() }
        .filter { percent -> percent in 0..100 }
        .lastOrNull()
}

private suspend fun executeAdbPushWithProgress(
    deviceSerial: String,
    remoteTargetPath: String?,
    args: List<String>,
    displayCommand: String,
    logCommand: (String) -> Unit,
    onPercent: (Int) -> Unit,
    onRemoteSize: (Long) -> Unit,
) {
    val startTime = System.currentTimeMillis()
    logCommand(displayCommand)
    withContext(Dispatchers.IO) {
        val process = ProcessBuilder(listOf(AdbShell.adbPath) + args)
            .redirectErrorStream(true)
            .start()
        val remoteSizePoller = remoteTargetPath?.let { targetPath ->
            async(Dispatchers.IO) {
                var lastSize = -1L
                while (process.isAlive) {
                    currentCoroutineContext().ensureActive()
                    remotePathSizeBytesOrNull(deviceSerial, targetPath)?.let { size ->
                        if (size != lastSize) {
                            lastSize = size
                            onRemoteSize(size)
                        }
                    }
                    delay(400L)
                }
            }
        }
        val output = StringBuilder()
        val outputReader = async(Dispatchers.IO) {
            val buffer = CharArray(512)
            var parseWindow = ""
            process.inputStream.bufferedReader().use { reader ->
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    currentCoroutineContext().ensureActive()
                    val chunk = String(buffer, 0, count)
                    output.append(chunk)
                    parseWindow = (parseWindow + chunk).takeLast(256)
                    parseAdbPushProgressPercent(parseWindow)?.let(onPercent)
                }
            }
        }
        try {
            while (!process.waitFor(100L, TimeUnit.MILLISECONDS)) {
                currentCoroutineContext().ensureActive()
            }
            outputReader.await()
            remoteSizePoller?.cancel()
            val exitCode = process.exitValue()
            if (exitCode != 0) {
                throw AdbCommandException(displayCommand, exitCode, output.toString())
            }
            if (AppSettingsShared.currentSettings.showCommandDuration) {
                val duration = System.currentTimeMillis() - startTime
                logCommand(commandStatus(localized("shell.command_duration_arg0_ms", duration)))
            }
        } catch (error: CancellationException) {
            process.destroyForcibly()
            outputReader.cancel()
            remoteSizePoller?.cancel()
            throw error
        }
    }
}

private fun remotePathSizeBytesOrNull(
    deviceSerial: String,
    remotePath: String,
): Long? {
    val quotedPath = shellQuote(remotePath)
    val command = "if [ -d $quotedPath ]; then " +
        "find $quotedPath -type f -exec stat -c %s {} \\; 2>/dev/null | awk '{s+=\$1} END {print s+0}'; " +
        "else stat -c %s $quotedPath 2>/dev/null || wc -c < $quotedPath 2>/dev/null; fi"
    val process = ProcessBuilder(listOf(AdbShell.adbPath, "-s", deviceSerial, "shell", command))
        .redirectErrorStream(true)
        .start()
    return try {
        if (!process.waitFor(1L, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            null
        } else {
            process.inputStream.bufferedReader().readText()
                .lineSequence()
                .mapNotNull { line -> line.trim().split(Regex("\\s+")).firstOrNull()?.toLongOrNull() }
                .firstOrNull()
        }
    } catch (_: Throwable) {
        process.destroyForcibly()
        null
    }
}

private fun uploadSourceSizeBytes(file: File): Long {
    return if (file.isDirectory) {
        file.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    } else {
        file.length()
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
        childRemotePath(normalizedDirectory, localFile.name)
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
