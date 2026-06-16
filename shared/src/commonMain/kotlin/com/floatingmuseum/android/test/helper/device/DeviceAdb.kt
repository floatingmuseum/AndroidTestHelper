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
)

@Serializable
data class SystemProperty(
    val key: String,
    val value: String,
)

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
        logCommand: (String) -> Unit,
    ): String
}

expect fun createDeviceAdb(): DeviceAdb
