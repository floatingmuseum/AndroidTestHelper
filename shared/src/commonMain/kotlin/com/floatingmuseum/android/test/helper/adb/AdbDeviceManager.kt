package com.floatingmuseum.android.test.helper.adb

import com.floatingmuseum.android.test.helper.AndroidDevice

interface AdbDeviceManager {
    suspend fun listDevices(logCommand: (String) -> Unit): List<AndroidDevice>

    suspend fun pairWirelessDevice(
        host: String,
        pairingPort: String,
        pairingCode: String,
        logCommand: (String) -> Unit,
    ): String

    suspend fun connectWirelessDevice(
        host: String,
        debugPort: String,
        logCommand: (String) -> Unit,
    ): String
}

expect fun createAdbDeviceManager(): AdbDeviceManager

internal data class WirelessAdbCommand(
    val args: List<String>,
    val displayCommand: String,
)

internal fun buildWirelessPairCommand(
    host: String,
    pairingPort: String,
    pairingCode: String,
): WirelessAdbCommand {
    val endpoint = wirelessEndpoint(host, pairingPort)
    val code = pairingCode.trim()
    return WirelessAdbCommand(
        args = listOf("pair", endpoint, code),
        displayCommand = "adb pair $endpoint $code",
    )
}

internal fun buildWirelessConnectCommand(
    host: String,
    debugPort: String,
): WirelessAdbCommand {
    val endpoint = wirelessEndpoint(host, debugPort)
    return WirelessAdbCommand(
        args = listOf("connect", endpoint),
        displayCommand = "adb connect $endpoint",
    )
}

private fun wirelessEndpoint(host: String, port: String): String = "${host.trim()}:${port.trim()}"
