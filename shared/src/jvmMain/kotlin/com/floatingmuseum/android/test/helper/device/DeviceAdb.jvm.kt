package com.floatingmuseum.android.test.helper.device

import com.floatingmuseum.android.test.helper.adb.AdbShell

actual fun createDeviceAdb(): DeviceAdb = JvmDeviceAdb()

private class JvmDeviceAdb : DeviceAdb {
    override suspend fun loadSystemInfo(
        deviceSerial: String,
        logCommand: (String) -> Unit,
    ): DeviceSystemInfo {
        val brand = executeGetProp(deviceSerial, "ro.product.brand", logCommand)
        val model = executeGetProp(deviceSerial, "ro.product.model", logCommand)
        val androidVersion = executeGetProp(deviceSerial, "ro.build.version.release", logCommand)
        val sdkVersion = executeGetProp(deviceSerial, "ro.build.version.sdk", logCommand)
        val cpuAbi = executeGetProp(deviceSerial, "ro.product.cpu.abi", logCommand)

        val wmSizeOutput = try {
            AdbShell.executeAdb(
                args = listOf("-s", deviceSerial, "shell", "wm", "size"),
                displayCommand = "adb -s $deviceSerial shell wm size",
                logCommand = logCommand
            )
        } catch (e: Throwable) {
            ""
        }
        val screenSize = parseScreenSize(wmSizeOutput)

        val batteryOutput = try {
            AdbShell.executeAdb(
                args = listOf("-s", deviceSerial, "shell", "dumpsys", "battery"),
                displayCommand = "adb -s $deviceSerial shell dumpsys battery",
                logCommand = logCommand
            )
        } catch (e: Throwable) {
            ""
        }
        val batteryLevel = parseBatteryLevel(batteryOutput)

        val ipOutput = try {
            AdbShell.executeAdb(
                args = listOf("-s", deviceSerial, "shell", "ip", "addr"),
                displayCommand = "adb -s $deviceSerial shell ip addr",
                logCommand = logCommand
            )
        } catch (e: Throwable) {
            ""
        }
        val ipAddress = parseIpAddress(ipOutput)

        return DeviceSystemInfo(
            brand = brand,
            model = model,
            androidVersion = androidVersion,
            sdkVersion = sdkVersion,
            cpuAbi = cpuAbi,
            batteryLevel = batteryLevel,
            screenSize = screenSize,
            ipAddress = ipAddress,
        )
    }

    override suspend fun loadSystemProperties(
        deviceSerial: String,
        logCommand: (String) -> Unit,
    ): List<SystemProperty> {
        val output = AdbShell.executeAdb(
            args = listOf("-s", deviceSerial, "shell", "getprop"),
            displayCommand = "adb -s $deviceSerial shell getprop",
            logCommand = logCommand
        )
        return parseSystemProperties(output)
    }

    override suspend fun rebootDevice(
        deviceSerial: String,
        logCommand: (String) -> Unit,
    ) {
        AdbShell.executeAdb(
            args = listOf("-s", deviceSerial, "reboot"),
            displayCommand = "adb -s $deviceSerial reboot",
            logCommand = logCommand
        )
    }

    override suspend fun takeScreenshot(
        deviceSerial: String,
        logCommand: (String) -> Unit,
    ): String {
        val remotePath = "/sdcard/screenshot.png"
        AdbShell.executeAdb(
            args = listOf("-s", deviceSerial, "shell", "screencap", "-p", remotePath),
            displayCommand = "adb -s $deviceSerial shell screencap -p $remotePath",
            logCommand = logCommand
        )
        return remotePath
    }

    private suspend fun executeGetProp(
        deviceSerial: String,
        propKey: String,
        logCommand: (String) -> Unit,
    ): String {
        return try {
            AdbShell.executeAdb(
                args = listOf("-s", deviceSerial, "shell", "getprop", propKey),
                displayCommand = "adb -s $deviceSerial shell getprop $propKey",
                logCommand = logCommand
            ).trim()
        } catch (e: Throwable) {
            "未知"
        }.takeIf { it.isNotEmpty() } ?: "未知"
    }
}

internal fun parseScreenSize(output: String): String {
    val regex = Regex("""Physical size:\s*(\d+x\d+)""")
    return regex.find(output)?.groupValues?.get(1) ?: "未知"
}

internal fun parseBatteryLevel(output: String): Int? {
    val regex = Regex("""level:\s*(\d+)""")
    return regex.find(output)?.groupValues?.get(1)?.toIntOrNull()
}

internal fun parseIpAddress(output: String): String {
    val ipRegex = Regex("""inet\s+(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})""")
    return output.lineSequence()
        .mapNotNull { line ->
            ipRegex.find(line)?.groupValues?.get(1)
        }
        .firstOrNull { it != "127.0.0.1" }
        ?: "未知"
}

internal fun parseSystemProperties(output: String): List<SystemProperty> {
    val regex = Regex("""\[([^\]]+)\]:\s*\[([^\]]*)\]""")
    return output.lineSequence()
        .mapNotNull { line ->
            val match = regex.find(line) ?: return@mapNotNull null
            SystemProperty(
                key = match.groupValues[1],
                value = match.groupValues[2]
            )
        }
        .toList()
}
