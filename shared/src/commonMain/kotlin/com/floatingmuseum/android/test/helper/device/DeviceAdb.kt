package com.floatingmuseum.android.test.helper.device

import com.floatingmuseum.android.test.helper.localization.localized
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
    val cpuProcessor: String = "未知",
    val cpuHardware: String = "未知",
    val cpuArchitecture: String = "未知",
    val cpuCoreCount: String = "未知",
    val cpuFeatures: String = "未知",
    val memoryTotal: String = "未知",
    val memoryFree: String = "未知",
    val memoryAvailable: String = "未知",
    val memoryBuffers: String = "未知",
    val memoryCached: String = "未知",
    val memorySwapTotal: String = "未知",
    val memorySwapFree: String = "未知",
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
    val displayId: String = "未知",
    val displayInit: String = "未知",
    val displayCur: String = "未知",
    val displayApp: String = "未知",
    val displayRefreshRate: String = "未知",
    val romVersion: String = "未知",
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

enum class DeviceQuickAction {
    SHUTDOWN,
    POWER,
    MENU,
    HOME,
    BACK,
    VOLUME_UP,
    VOLUME_DOWN,
    MUTE,
    WAKE,
    SLEEP,
    REBOOT_RECOVERY,
    REBOOT_FASTBOOT,
    CURRENT_ACTIVITY,
}

fun DeviceQuickAction.displayLabel(): String = when (this) {
    DeviceQuickAction.SHUTDOWN -> localized("device.shut_down")
    DeviceQuickAction.POWER -> localized("device.power")
    DeviceQuickAction.MENU -> localized("device.menu")
    DeviceQuickAction.HOME -> localized("device.home")
    DeviceQuickAction.BACK -> localized("device.shortcut.back")
    DeviceQuickAction.VOLUME_UP -> localized("device.volume_up")
    DeviceQuickAction.VOLUME_DOWN -> localized("device.volume_down")
    DeviceQuickAction.MUTE -> localized("device.mute")
    DeviceQuickAction.WAKE -> localized("device.wake")
    DeviceQuickAction.SLEEP -> localized("device.sleep")
    DeviceQuickAction.REBOOT_RECOVERY -> localized("device.reboot_to_recovery")
    DeviceQuickAction.REBOOT_FASTBOOT -> localized("device.reboot_to_fastboot")
    DeviceQuickAction.CURRENT_ACTIVITY -> localized("device.current_activity")
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

    suspend fun modifyScreenSize(
        deviceSerial: String,
        size: String,
        logCommand: (String) -> Unit,
    )

    suspend fun modifyScreenDensity(
        deviceSerial: String,
        density: String,
        logCommand: (String) -> Unit,
    )
}

expect fun createDeviceAdb(): DeviceAdb
