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
    DeviceQuickAction.SHUTDOWN -> localized("auto.shut_down.8a5ae346")
    DeviceQuickAction.POWER -> localized("auto.power.64fb184f")
    DeviceQuickAction.MENU -> localized("auto.menu.0ea06926")
    DeviceQuickAction.HOME -> localized("auto.home.c90ee457")
    DeviceQuickAction.BACK -> localized("auto.back.8c6f017e")
    DeviceQuickAction.VOLUME_UP -> localized("auto.volume_up.541bb141")
    DeviceQuickAction.VOLUME_DOWN -> localized("auto.volume_down.76d2f8ad")
    DeviceQuickAction.MUTE -> localized("auto.mute.7f10e234")
    DeviceQuickAction.WAKE -> localized("auto.wake.23581b44")
    DeviceQuickAction.SLEEP -> localized("auto.sleep.e74552c5")
    DeviceQuickAction.REBOOT_RECOVERY -> localized("auto.reboot_to_recovery.c6f901c2")
    DeviceQuickAction.REBOOT_FASTBOOT -> localized("auto.reboot_to_fastboot.3dc369b5")
    DeviceQuickAction.CURRENT_ACTIVITY -> localized("auto.current_activity.37ce7c5c")
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
