package com.floatingmuseum.android.test.helper.device

import com.floatingmuseum.android.test.helper.localization.localized
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceSystemInfo(
    val brand: DeviceInfoValue,
    val model: DeviceInfoValue,
    val androidVersion: DeviceInfoValue,
    val sdkVersion: DeviceInfoValue,
    val cpuAbi: DeviceInfoValue,
    val batteryLevel: Int?,
    val screenSize: DeviceInfoValue,
    val ipAddress: DeviceInfoValue,
    val screenDensity: DeviceInfoValue = DeviceInfoValue.Unknown,
    val cpuProcessor: DeviceInfoValue = DeviceInfoValue.Unknown,
    val cpuHardware: DeviceInfoValue = DeviceInfoValue.Unknown,
    val cpuArchitecture: DeviceInfoValue = DeviceInfoValue.Unknown,
    val cpuCoreCount: DeviceInfoValue = DeviceInfoValue.Unknown,
    val cpuFeatures: DeviceInfoValue = DeviceInfoValue.Unknown,
    val memoryTotal: DeviceInfoValue = DeviceInfoValue.Unknown,
    val memoryFree: DeviceInfoValue = DeviceInfoValue.Unknown,
    val memoryAvailable: DeviceInfoValue = DeviceInfoValue.Unknown,
    val memoryBuffers: DeviceInfoValue = DeviceInfoValue.Unknown,
    val memoryCached: DeviceInfoValue = DeviceInfoValue.Unknown,
    val memorySwapTotal: DeviceInfoValue = DeviceInfoValue.Unknown,
    val memorySwapFree: DeviceInfoValue = DeviceInfoValue.Unknown,
    val batteryStatus: DeviceInfoValue = DeviceInfoValue.Unknown,
    val batteryHealth: DeviceInfoValue = DeviceInfoValue.Unknown,
    val batteryTemp: DeviceInfoValue = DeviceInfoValue.Unknown,
    val batteryVoltage: DeviceInfoValue = DeviceInfoValue.Unknown,
    val batteryACPowered: DeviceInfoValue = DeviceInfoValue.Unknown,
    val batteryUSBPowered: DeviceInfoValue = DeviceInfoValue.Unknown,
    val batteryWirelessPowered: DeviceInfoValue = DeviceInfoValue.Unknown,
    val batteryMaxChargingCurrent: DeviceInfoValue = DeviceInfoValue.Unknown,
    val batteryMaxChargingVoltage: DeviceInfoValue = DeviceInfoValue.Unknown,
    val batteryChargeCounter: DeviceInfoValue = DeviceInfoValue.Unknown,
    val batteryPresent: DeviceInfoValue = DeviceInfoValue.Unknown,
    val batteryTechnology: DeviceInfoValue = DeviceInfoValue.Unknown,
    val displayId: DeviceInfoValue = DeviceInfoValue.Unknown,
    val displayInit: DeviceInfoValue = DeviceInfoValue.Unknown,
    val displayCur: DeviceInfoValue = DeviceInfoValue.Unknown,
    val displayApp: DeviceInfoValue = DeviceInfoValue.Unknown,
    val displayRefreshRate: DeviceInfoValue = DeviceInfoValue.Unknown,
    val romVersion: DeviceInfoValue = DeviceInfoValue.Unknown,
)

@Serializable
sealed class DeviceInfoValue {
    @Serializable
    @SerialName("unknown")
    data object Unknown : DeviceInfoValue()

    @Serializable
    @SerialName("text")
    data class Text(val value: String) : DeviceInfoValue()

    @Serializable
    @SerialName("boolean")
    data class BooleanValue(val value: Boolean) : DeviceInfoValue()

    @Serializable
    @SerialName("localized")
    data class Localized(val token: DeviceInfoToken) : DeviceInfoValue()

    @Serializable
    @SerialName("physical_override")
    data class PhysicalOverride(
        val overrideValue: String,
        val physicalValue: String,
    ) : DeviceInfoValue()
}

@Serializable
enum class DeviceInfoToken {
    BatteryStatusCharging,
    BatteryStatusDischarging,
    BatteryStatusNotCharging,
    BatteryStatusFull,
    BatteryHealthGood,
    BatteryHealthOverheated,
    BatteryHealthDamaged,
    BatteryHealthOverVoltage,
    BatteryHealthUnknownFailure,
    BatteryHealthCold,
}

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
