package com.floatingmuseum.android.test.helper

import kotlin.math.round

const val BytesInGiB: Long = 1024L * 1024L * 1024L
const val BytesInMiB: Long = 1024L * 1024L

data class StorageInfo(
    val totalBytes: Long,
    val usedBytes: Long,
    val availableBytes: Long,
) {
    val usedRatio: Float
        get() = if (totalBytes <= 0L) 0f else (usedBytes.toDouble() / totalBytes).toFloat()
}

data class AndroidDevice(
    val serialNumber: String,
    val model: String,
    val state: String,
) {
    val isReady: Boolean
        get() = state == "device"
}

data class FillProgress(
    val completedBytes: Long,
    val totalBytes: Long,
) {
    val ratio: Float
        get() = if (totalBytes <= 0L) 0f else (completedBytes.toDouble() / totalBytes).toFloat()
}

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long?,
    val isSystem: Boolean,
    val isEnabled: Boolean,
    val iconBytes: ByteArray?,
)

interface DataFillAdb {
    suspend fun listDevices(logCommand: (String) -> Unit): List<AndroidDevice>

    suspend fun loadStorageInfo(
        deviceSerial: String,
        logCommand: (String) -> Unit,
    ): StorageInfo

    suspend fun fillSize(
        deviceSerial: String,
        sizeBytes: Long,
        logCommand: (String) -> Unit,
        onProgress: (FillProgress) -> Unit,
    ): StorageInfo

    suspend fun fillUntilRemaining(
        deviceSerial: String,
        targetAvailableBytes: Long,
        logCommand: (String) -> Unit,
        onStorageProgress: (StorageInfo) -> Unit,
        onFillProgress: (FillProgress) -> Unit,
    ): StorageInfo

    suspend fun loadInstalledApps(
        deviceSerial: String,
        logCommand: (String) -> Unit,
    ): List<InstalledAppInfo>
}

expect fun createDataFillAdb(): DataFillAdb

fun formatBytes(bytes: Long): String {
    val absBytes = if (bytes < 0L) -bytes else bytes
    val unit = when {
        absBytes >= BytesInGiB -> BytesInGiB
        absBytes >= BytesInMiB -> BytesInMiB
        else -> 1024L
    }
    val suffix = when (unit) {
        BytesInGiB -> "GB"
        BytesInMiB -> "MB"
        else -> "KB"
    }
    val value = bytes.toDouble() / unit.toDouble()
    val rounded = round(value * 10.0) / 10.0
    return if (rounded == round(rounded)) {
        "${rounded.toLong()} $suffix"
    } else {
        "$rounded $suffix"
    }
}

fun parseGiBInput(value: String): Long? {
    val number = value.trim().replace(',', '.').toDoubleOrNull() ?: return null
    if (number <= 0.0) return null
    return (number * BytesInGiB).toLong()
}
