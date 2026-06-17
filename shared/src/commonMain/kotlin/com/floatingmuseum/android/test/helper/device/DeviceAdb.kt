package com.floatingmuseum.android.test.helper.device

import kotlinx.serialization.Serializable

@Serializable
data class DeviceSystemInfo(
    val brand: String,
    val model: String,
    val androidVersion: String,
    val sdkVersion: String,
    val cpuAbi: String,
    val batteryLevel: Int?,
    val screenSize: String,
    val ipAddress: String,
    val screenDensity: String = "未知",
    val batteryStatus: String = "未知",
    val batteryHealth: String = "未知",
    val batteryTemp: String = "未知",
    val batteryVoltage: String = "未知",
    val batteryACPowered: String = "未知",
    val batteryUSBPowered: String = "未知",
    val batteryWirelessPowered: String = "未知",
    val batteryMaxChargingCurrent: String = "未知",
    val batteryMaxChargingVoltage: String = "未知",
    val batteryChargeCounter: String = "未知",
    val batteryPresent: String = "未知",
    val batteryTechnology: String = "未知",
)

@Serializable
data class SystemProperty(
    val key: String,
    val value: String,
)

data class ScreenshotResult(
    val remotePath: String,
    val localPath: String,
)

data class ApkInstallResult(
    val filePath: String,
    val fileName: String,
    val success: Boolean,
    val message: String,
)

enum class DeviceQuickAction(val label: String) {
    SHUTDOWN("关机"),
    POWER("电源键"),
    MENU("菜单键"),
    HOME("HOME键"),
    BACK("返回键"),
    VOLUME_UP("音量加"),
    VOLUME_DOWN("音量减"),
    MUTE("静音"),
    WAKE("亮屏"),
    SLEEP("熄屏"),
}

interface DeviceAdb {
    suspend fun loadSystemInfo(
        deviceSerial: String,
        logCommand: (String) -> Unit,
    ): DeviceSystemInfo

    suspend fun loadSystemProperties(
        deviceSerial: String,
        logCommand: (String) -> Unit,
    ): List<SystemProperty>

    suspend fun rebootDevice(
        deviceSerial: String,
        logCommand: (String) -> Unit,
    )

    suspend fun takeScreenshot(
        deviceSerial: String,
        outputDirectoryPath: String,
        logCommand: (String) -> Unit,
    ): ScreenshotResult

    suspend fun installApplications(
        deviceSerial: String,
        apkFilePaths: List<String>,
        logCommand: (String) -> Unit,
    ): List<ApkInstallResult>

    suspend fun runQuickAction(
        deviceSerial: String,
        action: DeviceQuickAction,
        logCommand: (String) -> Unit,
    )

    suspend fun controlBattery(
        deviceSerial: String,
        args: List<String>,
        logCommand: (String) -> Unit,
    )
}

expect fun createDeviceAdb(): DeviceAdb
