package com.floatingmuseum.android.test.helper

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.min

private const val FillDirectory = "/sdcard/AndroidTestHelperFill"
private const val FillChunkBytes = 128L * BytesInMiB

actual fun createDataFillAdb(): DataFillAdb = JvmDataFillAdb()

class AdbCommandException(
    command: String,
    exitCode: Int,
    output: String,
) : RuntimeException("ADB 命令失败，退出码 $exitCode\n$command\n$output")

private class JvmDataFillAdb(
    private val adbPath: String = resolveAdbPath(),
) : DataFillAdb {
    override suspend fun listDevices(logCommand: (String) -> Unit): List<AndroidDevice> {
        val output = executeAdb(
            args = listOf("devices", "-l"),
            displayCommand = "adb devices -l",
            logCommand = logCommand,
        )
        return parseAdbDevices(output)
    }

    override suspend fun loadStorageInfo(
        deviceSerial: String,
        logCommand: (String) -> Unit,
    ): StorageInfo {
        val output = executeAdb(
            args = listOf("-s", deviceSerial, "shell", "df", "-k", "/data"),
            displayCommand = "adb -s $deviceSerial shell df -k /data",
            logCommand = logCommand,
        )
        return parseDfStorageInfo(output)
    }

    override suspend fun fillSize(
        deviceSerial: String,
        sizeBytes: Long,
        logCommand: (String) -> Unit,
        onProgress: (FillProgress) -> Unit,
    ): StorageInfo {
        fillBytes(
            deviceSerial = deviceSerial,
            sizeBytes = sizeBytes,
            completedBeforeBytes = 0L,
            totalBytes = sizeBytes,
            logCommand = logCommand,
            onProgress = onProgress,
        )
        return loadStorageInfo(deviceSerial, logCommand)
    }

    override suspend fun fillUntilRemaining(
        deviceSerial: String,
        targetAvailableBytes: Long,
        logCommand: (String) -> Unit,
        onStorageProgress: (StorageInfo) -> Unit,
        onFillProgress: (FillProgress) -> Unit,
    ): StorageInfo {
        var storageInfo = loadStorageInfo(deviceSerial, logCommand)
        onStorageProgress(storageInfo)

        val initialAvailableBytes = storageInfo.availableBytes
        val totalToFillBytes = (initialAvailableBytes - targetAvailableBytes).coerceAtLeast(0L)
        var completedBytes = 0L
        onFillProgress(FillProgress(completedBytes, totalToFillBytes))

        while (storageInfo.availableBytes > targetAvailableBytes) {
            val remainingGap = storageInfo.availableBytes - targetAvailableBytes
            if (remainingGap < BytesInMiB) break

            val nextChunkBytes = min(remainingGap, FillChunkBytes)
            fillBytes(
                deviceSerial = deviceSerial,
                sizeBytes = nextChunkBytes,
                completedBeforeBytes = completedBytes,
                totalBytes = totalToFillBytes,
                logCommand = logCommand,
                onProgress = onFillProgress,
            )
            completedBytes = (initialAvailableBytes - storageInfo.availableBytes + nextChunkBytes)
                .coerceAtMost(totalToFillBytes)
            storageInfo = loadStorageInfo(deviceSerial, logCommand)
            completedBytes = (initialAvailableBytes - storageInfo.availableBytes)
                .coerceIn(0L, totalToFillBytes)
            onFillProgress(FillProgress(completedBytes, totalToFillBytes))
            onStorageProgress(storageInfo)
        }

        return storageInfo
    }

    private suspend fun fillBytes(
        deviceSerial: String,
        sizeBytes: Long,
        completedBeforeBytes: Long,
        totalBytes: Long,
        logCommand: (String) -> Unit,
        onProgress: (FillProgress) -> Unit,
    ) {
        executeAdb(
            args = listOf("-s", deviceSerial, "shell", "mkdir", "-p", FillDirectory),
            displayCommand = "adb -s $deviceSerial shell mkdir -p $FillDirectory",
            logCommand = logCommand,
        )
        onProgress(FillProgress(completedBeforeBytes.coerceAtMost(totalBytes), totalBytes))

        var remainingBytes = sizeBytes
        var completedBytes = completedBeforeBytes
        var chunkIndex = 0
        val runId = System.currentTimeMillis()

        while (remainingBytes > 0L) {
            val chunkBytes = min(remainingBytes, FillChunkBytes)
            val chunkMiB = (chunkBytes / BytesInMiB).coerceAtLeast(1L)
            val fileName = "$FillDirectory/fill_${runId}_${chunkIndex}_${chunkMiB}m.bin"

            executeAdb(
                args = listOf("-s", deviceSerial, "shell", "dd", "if=/dev/zero", "of=$fileName", "bs=1M", "count=$chunkMiB"),
                displayCommand = "adb -s $deviceSerial shell dd if=/dev/zero of=$fileName bs=1M count=$chunkMiB",
                logCommand = logCommand,
            )

            remainingBytes -= chunkMiB * BytesInMiB
            completedBytes = (completedBytes + chunkMiB * BytesInMiB).coerceAtMost(totalBytes)
            onProgress(FillProgress(completedBytes, totalBytes))
            chunkIndex += 1
        }

        executeAdb(
            args = listOf("-s", deviceSerial, "shell", "sync"),
            displayCommand = "adb -s $deviceSerial shell sync",
            logCommand = logCommand,
        )
    }

    private suspend fun executeAdb(
        args: List<String>,
        displayCommand: String,
        logCommand: (String) -> Unit,
    ): String {
        logCommand(displayCommand)

        return withContext(Dispatchers.IO) {
            val process = ProcessBuilder(listOf(adbPath) + args)
                .redirectErrorStream(true)
                .start()
            try {
                while (!process.waitFor(100L, TimeUnit.MILLISECONDS)) {
                    currentCoroutineContext().ensureActive()
                }

                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.exitValue()
                if (exitCode != 0) {
                    throw AdbCommandException(displayCommand, exitCode, output)
                }
                output
            } catch (error: CancellationException) {
                process.destroyForcibly()
                throw error
            }
        }
    }
}

internal fun parseAdbDevices(output: String): List<AndroidDevice> {
    return output
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("List of devices attached") }
        .mapNotNull { line ->
            val columns = line.split(Regex("\\s+"))
            val serialNumber = columns.getOrNull(0) ?: return@mapNotNull null
            val state = columns.getOrNull(1) ?: return@mapNotNull null
            val model = columns
                .firstOrNull { it.startsWith("model:") }
                ?.substringAfter("model:")
                ?.replace('_', ' ')
                ?.takeIf { it.isNotBlank() }
                ?: "未知型号"

            AndroidDevice(
                serialNumber = serialNumber,
                model = model,
                state = state,
            )
        }
        .toList()
}

internal fun parseDfStorageInfo(output: String): StorageInfo {
    val rows = output
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toList()

    val dataRow = rows
        .drop(1)
        .firstOrNull { it.split(Regex("\\s+")).lastOrNull() == "/data" }
        ?: rows.drop(1).firstOrNull()
        ?: throw IllegalArgumentException("无法解析存储信息：$output")

    val columns = dataRow.split(Regex("\\s+"))
    val totalKiB = columns.getOrNull(1)?.toLongOrNull()
    val usedKiB = columns.getOrNull(2)?.toLongOrNull()
    val availableKiB = columns.getOrNull(3)?.toLongOrNull()

    if (totalKiB == null || usedKiB == null || availableKiB == null) {
        throw IllegalArgumentException("无法解析存储信息：$output")
    }

    return StorageInfo(
        totalBytes = totalKiB * 1024L,
        usedBytes = usedKiB * 1024L,
        availableBytes = availableKiB * 1024L,
    )
}

private fun resolveAdbPath(): String {
    val osName = System.getProperty("os.name").lowercase()
    val platformFolder = when {
        osName.contains("win") -> "platform-tools-latest-windows"
        osName.contains("mac") || osName.contains("darwin") -> "platform-tools-latest-darwin"
        else -> "platform-tools-latest-linux"
    }
    val adbBinary = if (osName.contains("win")) "adb.exe" else "adb"
    val userDir = File(System.getProperty("user.dir")).absoluteFile

    generateSequence(userDir) { it.parentFile }.forEach { directory ->
        val candidate = directory.resolve("platform-tools")
            .resolve(platformFolder)
            .resolve("platform-tools")
            .resolve(adbBinary)
        if (candidate.exists()) {
            return candidate.absolutePath
        }
    }

    return adbBinary
}
