package com.floatingmuseum.android.test.helper.datafill

import com.floatingmuseum.android.test.helper.adb.AdbShell
import kotlin.math.min

private const val FillDirectory = "/sdcard/AndroidTestHelperFill"
private const val FillChunkBytes = 128L * BytesInMiB

actual fun createDataFillAdb(): DataFillAdb = JvmDataFillAdb()

private class JvmDataFillAdb : DataFillAdb {
    override suspend fun loadStorageInfo(
        deviceSerial: String,
        logCommand: (String) -> Unit,
    ): StorageInfo {
        val output = AdbShell.executeAdb(
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
        AdbShell.executeAdb(
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

            AdbShell.executeAdb(
                args = listOf("-s", deviceSerial, "shell", "dd", "if=/dev/zero", "of=$fileName", "bs=1M", "count=$chunkMiB"),
                displayCommand = "adb -s $deviceSerial shell dd if=/dev/zero of=$fileName bs=1M count=$chunkMiB",
                logCommand = logCommand,
            )

            remainingBytes -= chunkMiB * BytesInMiB
            completedBytes = (completedBytes + chunkMiB * BytesInMiB).coerceAtMost(totalBytes)
            onProgress(FillProgress(completedBytes, totalBytes))
            chunkIndex += 1
        }

        AdbShell.executeAdb(
            args = listOf("-s", deviceSerial, "shell", "sync"),
            displayCommand = "adb -s $deviceSerial shell sync",
            logCommand = logCommand,
        )
    }
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
