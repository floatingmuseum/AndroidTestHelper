package com.floatingmuseum.android.test.helper.devicelog

data class DeviceLogCaptureProgress(
    val currentSection: String,
    val completedSections: Int,
    val totalSections: Int,
)

data class DeviceLogCaptureResult(
    val fileName: String,
    val filePath: String,
    val directoryPath: String,
    val completedSections: Int,
    val totalSections: Int,
    val endState: DeviceLogCaptureEndState = DeviceLogCaptureEndState.COMPLETED,
    val message: String? = null,
)

enum class DeviceLogCaptureEndState {
    COMPLETED,
    STOPPED,
    INTERRUPTED,
}

interface DeviceLogAdb {
    suspend fun captureFullLogs(
        deviceSerial: String,
        deviceModel: String,
        logCommand: (String) -> Unit,
        onProgress: (DeviceLogCaptureProgress) -> Unit,
    ): DeviceLogCaptureResult

    fun stopCurrentCapture()
}

expect fun createDeviceLogAdb(): DeviceLogAdb
