package com.floatingmuseum.android.test.helper.device

import com.floatingmuseum.android.test.helper.adb.AdbShell
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException

actual fun createDeviceAdb(): DeviceAdb = JvmDeviceAdb()

private const val ScreenshotRemoteDirectory = "/sdcard/AndroidTestHelperScreenshots"
private val ScreenshotTimestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

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

        val wmDensityOutput = try {
            AdbShell.executeAdb(
                args = listOf("-s", deviceSerial, "shell", "wm", "density"),
                displayCommand = "adb -s $deviceSerial shell wm density",
                logCommand = logCommand
            )
        } catch (e: Throwable) {
            ""
        }
        val screenDensity = parseScreenDensity(wmDensityOutput)

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
        val batteryStatus = parseBatteryStatus(batteryOutput)
        val batteryHealth = parseBatteryHealth(batteryOutput)
        val batteryTemp = parseBatteryTemp(batteryOutput)
        val batteryVoltage = parseBatteryVoltage(batteryOutput)
        val batteryACPowered = parseBatteryACPowered(batteryOutput)
        val batteryUSBPowered = parseBatteryUSBPowered(batteryOutput)
        val batteryWirelessPowered = parseBatteryWirelessPowered(batteryOutput)
        val batteryMaxChargingCurrent = parseBatteryMaxChargingCurrent(batteryOutput)
        val batteryMaxChargingVoltage = parseBatteryMaxChargingVoltage(batteryOutput)
        val batteryChargeCounter = parseBatteryChargeCounter(batteryOutput)
        val batteryPresent = parseBatteryPresent(batteryOutput)
        val batteryTechnology = parseBatteryTechnology(batteryOutput)

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

        val dumpsysWindowDisplaysOutput = try {
            AdbShell.executeAdb(
                args = listOf("-s", deviceSerial, "shell", "dumpsys", "window", "displays"),
                displayCommand = "adb -s $deviceSerial shell dumpsys window displays",
                logCommand = logCommand
            )
        } catch (e: Throwable) {
            ""
        }
        val displayId = parseDisplayId(dumpsysWindowDisplaysOutput)
        val displayInit = parseDisplayInit(dumpsysWindowDisplaysOutput)
        val displayCur = parseDisplayCur(dumpsysWindowDisplaysOutput)
        val displayApp = parseDisplayApp(dumpsysWindowDisplaysOutput)

        val dumpsysDisplayOutput = try {
            AdbShell.executeAdb(
                args = listOf("-s", deviceSerial, "shell", "dumpsys", "display"),
                displayCommand = "adb -s $deviceSerial shell dumpsys display",
                logCommand = logCommand
            )
        } catch (e: Throwable) {
            ""
        }
        val displayRefreshRate = parseDisplayRefreshRate(dumpsysDisplayOutput)

        return DeviceSystemInfo(
            brand = brand,
            model = model,
            androidVersion = androidVersion,
            sdkVersion = sdkVersion,
            cpuAbi = cpuAbi,
            batteryLevel = batteryLevel,
            screenSize = screenSize,
            ipAddress = ipAddress,
            screenDensity = screenDensity,
            batteryStatus = batteryStatus,
            batteryHealth = batteryHealth,
            batteryTemp = batteryTemp,
            batteryVoltage = batteryVoltage,
            batteryACPowered = batteryACPowered,
            batteryUSBPowered = batteryUSBPowered,
            batteryWirelessPowered = batteryWirelessPowered,
            batteryMaxChargingCurrent = batteryMaxChargingCurrent,
            batteryMaxChargingVoltage = batteryMaxChargingVoltage,
            batteryChargeCounter = batteryChargeCounter,
            batteryPresent = batteryPresent,
            batteryTechnology = batteryTechnology,
            displayId = displayId,
            displayInit = displayInit,
            displayCur = displayCur,
            displayApp = displayApp,
            displayRefreshRate = displayRefreshRate,
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
        outputDirectoryPath: String,
        logCommand: (String) -> Unit,
    ): ScreenshotResult {
        val plan = buildScreenshotTransferPlan(
            deviceSerial = deviceSerial,
            outputDirectoryPath = outputDirectoryPath,
            capturedAt = LocalDateTime.now(),
        )
        val localDirectory = File(outputDirectoryPath)
        if (!localDirectory.exists() && !localDirectory.mkdirs()) {
            throw IllegalStateException("无法创建截图保存目录：${localDirectory.absolutePath}")
        }
        if (!localDirectory.isDirectory) {
            throw IllegalStateException("截图保存路径不是目录：${localDirectory.absolutePath}")
        }

        AdbShell.executeAdb(
            args = listOf("-s", deviceSerial, "shell", "mkdir", "-p", ScreenshotRemoteDirectory),
            displayCommand = "adb -s $deviceSerial shell mkdir -p $ScreenshotRemoteDirectory",
            logCommand = logCommand
        )
        AdbShell.executeAdb(
            args = listOf("-s", deviceSerial, "shell", "screencap", "-p", plan.remotePath),
            displayCommand = "adb -s $deviceSerial shell screencap -p ${plan.remotePath}",
            logCommand = logCommand
        )
        AdbShell.executeAdb(
            args = listOf("-s", deviceSerial, "pull", plan.remotePath, plan.localPath),
            displayCommand = "adb -s $deviceSerial pull ${plan.remotePath} ${plan.localPath}",
            logCommand = logCommand
        )
        return ScreenshotResult(
            remotePath = plan.remotePath,
            localPath = plan.localPath,
        )
    }

    override suspend fun installApplications(
        deviceSerial: String,
        apkFilePaths: List<String>,
        logCommand: (String) -> Unit,
    ): List<ApkInstallResult> {
        return apkFilePaths.map { filePath ->
            val file = File(filePath)
            val command = buildInstallApplicationCommand(deviceSerial, file)
            try {
                if (!file.exists() || !file.isFile) {
                    ApkInstallResult(
                        filePath = file.absolutePath,
                        fileName = file.name,
                        success = false,
                        message = "文件不存在或不可读取",
                    )
                } else if (!file.extension.equals("apk", ignoreCase = true)) {
                    ApkInstallResult(
                        filePath = file.absolutePath,
                        fileName = file.name,
                        success = false,
                        message = "不是 APK 文件",
                    )
                } else {
                    val output = AdbShell.executeAdb(
                        args = command.args,
                        displayCommand = command.displayCommand,
                        logCommand = logCommand,
                    )
                    val trimmedOutput = output.trim()
                    if (trimmedOutput.lineSequence().any { it.trim() == "Success" }) {
                        ApkInstallResult(
                            filePath = file.absolutePath,
                            fileName = file.name,
                            success = true,
                            message = trimmedOutput.ifBlank { "Success" },
                        )
                    } else {
                        ApkInstallResult(
                            filePath = file.absolutePath,
                            fileName = file.name,
                            success = false,
                            message = trimmedOutput.ifBlank { "安装命令未返回 Success" },
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                ApkInstallResult(
                    filePath = file.absolutePath,
                    fileName = file.name,
                    success = false,
                    message = error.message ?: "未知错误",
                )
            }
        }
    }

    override suspend fun runQuickAction(
        deviceSerial: String,
        action: DeviceQuickAction,
        logCommand: (String) -> Unit,
    ) {
        val command = buildQuickActionCommand(deviceSerial, action)
        AdbShell.executeAdb(
            args = command.args,
            displayCommand = command.displayCommand,
            logCommand = logCommand,
        )
    }

    override suspend fun controlBattery(
        deviceSerial: String,
        args: List<String>,
        logCommand: (String) -> Unit,
    ) {
        val fullArgs = listOf("-s", deviceSerial, "shell", "dumpsys", "battery") + args
        val displayCmd = "adb -s $deviceSerial shell dumpsys battery ${args.joinToString(" ")}"
        AdbShell.executeAdb(
            args = fullArgs,
            displayCommand = displayCmd,
            logCommand = logCommand
        )
    }

    override suspend fun modifyScreenSize(
        deviceSerial: String,
        size: String,
        logCommand: (String) -> Unit,
    ) {
        val args = if (size.trim().lowercase() == "reset") {
            listOf("-s", deviceSerial, "shell", "wm", "size", "reset")
        } else {
            listOf("-s", deviceSerial, "shell", "wm", "size", size)
        }
        val displayCmd = "adb -s $deviceSerial shell wm size $size"
        AdbShell.executeAdb(
            args = args,
            displayCommand = displayCmd,
            logCommand = logCommand
        )
    }

    override suspend fun modifyScreenDensity(
        deviceSerial: String,
        density: String,
        logCommand: (String) -> Unit,
    ) {
        val args = if (density.trim().lowercase() == "reset") {
            listOf("-s", deviceSerial, "shell", "wm", "density", "reset")
        } else {
            listOf("-s", deviceSerial, "shell", "wm", "density", density)
        }
        val displayCmd = "adb -s $deviceSerial shell wm density $density"
        AdbShell.executeAdb(
            args = args,
            displayCommand = displayCmd,
            logCommand = logCommand
        )
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
    val overrideRegex = Regex("""Override size:\s*(\d+x\d+)""")
    val physicalRegex = Regex("""Physical size:\s*(\d+x\d+)""")
    val overrideMatch = overrideRegex.find(output)?.groupValues?.get(1)
    val physicalMatch = physicalRegex.find(output)?.groupValues?.get(1)
    return when {
        overrideMatch != null && physicalMatch != null -> "$overrideMatch (物理: $physicalMatch)"
        overrideMatch != null -> overrideMatch
        physicalMatch != null -> physicalMatch
        else -> "未知"
    }
}

internal fun parseDisplayId(output: String): String {
    val regex = Regex("""Display:\s+mDisplayId=(\d+)""")
    return regex.find(output)?.groupValues?.get(1) ?: "未知"
}

internal fun parseDisplayInit(output: String): String {
    val regex = Regex("""\binit=(\d+x\d+\s+\d+dpi|\d+x\d+)""")
    return regex.find(output)?.groupValues?.get(1) ?: "未知"
}

internal fun parseDisplayCur(output: String): String {
    val regex = Regex("""\bcur=(\d+x\d+)""")
    return regex.find(output)?.groupValues?.get(1) ?: "未知"
}

internal fun parseDisplayApp(output: String): String {
    val regex = Regex("""\bapp=(\d+x\d+)""")
    return regex.find(output)?.groupValues?.get(1) ?: "未知"
}

internal fun parseDisplayRefreshRate(output: String): String {
    val fpsRegex = Regex("""fps\s*[=:\s]\s*(\d+(?:\.\d+)?)""")
    val fpsMatch = fpsRegex.find(output)?.groupValues?.get(1)
    if (fpsMatch != null) return "$fpsMatch Hz"

    val renderRateRegex = Regex("""renderFrameRate\s+(\d+(?:\.\d+)?)""")
    val renderRateMatch = renderRateRegex.find(output)?.groupValues?.get(1)
    if (renderRateMatch != null) return "$renderRateMatch Hz"

    val defaultRateRegex = Regex("""mDefaultRefreshRate:\s*(\d+(?:\.\d+)?)""")
    val defaultRateMatch = defaultRateRegex.find(output)?.groupValues?.get(1)
    if (defaultRateMatch != null) return "$defaultRateMatch Hz"

    val refreshRateRegex = Regex("""\b(?:mRefreshRate|refreshRate|fps)\b\s*[=:\s]\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    val refreshRateMatch = refreshRateRegex.find(output)?.groupValues?.get(1)
    if (refreshRateMatch != null) return "$refreshRateMatch Hz"

    return "未知"
}

internal fun parseScreenDensity(output: String): String {
    val overrideRegex = Regex("""Override density:\s*(\d+)""")
    val physicalRegex = Regex("""Physical density:\s*(\d+)""")
    val overrideMatch = overrideRegex.find(output)?.groupValues?.get(1)
    val physicalMatch = physicalRegex.find(output)?.groupValues?.get(1)
    return when {
        overrideMatch != null && physicalMatch != null -> "$overrideMatch (物理: $physicalMatch)"
        overrideMatch != null -> overrideMatch
        physicalMatch != null -> physicalMatch
        else -> "未知"
    }
}

internal fun parseBatteryLevel(output: String): Int? {
    val regex = Regex("(?m)^\\s*level:\\s*(\\d+)")
    return regex.find(output)?.groupValues?.get(1)?.toIntOrNull()
}

internal fun parseBatteryStatus(output: String): String {
    val regex = Regex("(?m)^\\s*status:\\s*(\\d+)")
    val statusInt = regex.find(output)?.groupValues?.get(1)?.toIntOrNull() ?: return "未知"
    return when (statusInt) {
        1 -> "未知"
        2 -> "充电中"
        3 -> "放电中"
        4 -> "未充电"
        5 -> "已充满"
        else -> "未知"
    }
}

internal fun parseBatteryHealth(output: String): String {
    val regex = Regex("(?m)^\\s*health:\\s*(\\d+)")
    val healthInt = regex.find(output)?.groupValues?.get(1)?.toIntOrNull() ?: return "未知"
    return when (healthInt) {
        1 -> "未知"
        2 -> "良好"
        3 -> "过热"
        4 -> "损坏"
        5 -> "过压"
        6 -> "未知故障"
        7 -> "过冷"
        else -> "未知"
    }
}

internal fun parseBatteryTemp(output: String): String {
    val regex = Regex("(?m)^\\s*temp:\\s*(\\d+)")
    val tempInt = regex.find(output)?.groupValues?.get(1)?.toIntOrNull() ?: return "未知"
    return "${tempInt / 10.0} °C"
}

internal fun parseBatteryVoltage(output: String): String {
    val regex = Regex("(?m)^\\s*voltage:\\s*(\\d+)")
    val voltageInt = regex.find(output)?.groupValues?.get(1)?.toIntOrNull() ?: return "未知"
    return "$voltageInt mV"
}

internal fun parseBatteryACPowered(output: String): String {
    val regex = Regex("(?m)^\\s*AC powered:\\s*(\\w+)")
    val match = regex.find(output)?.groupValues?.get(1) ?: return "未知"
    return if (match.equals("true", ignoreCase = true)) "是" else "否"
}

internal fun parseBatteryUSBPowered(output: String): String {
    val regex = Regex("(?m)^\\s*USB powered:\\s*(\\w+)")
    val match = regex.find(output)?.groupValues?.get(1) ?: return "未知"
    return if (match.equals("true", ignoreCase = true)) "是" else "否"
}

internal fun parseBatteryWirelessPowered(output: String): String {
    val regex = Regex("(?m)^\\s*Wireless powered:\\s*(\\w+)")
    val match = regex.find(output)?.groupValues?.get(1) ?: return "未知"
    return if (match.equals("true", ignoreCase = true)) "是" else "否"
}

internal fun parseBatteryMaxChargingCurrent(output: String): String {
    val regex = Regex("(?m)^\\s*Max charging current:\\s*(\\d+)")
    val currentInt = regex.find(output)?.groupValues?.get(1)?.toLongOrNull() ?: return "未知"
    val mA = currentInt / 1000
    return "$mA mA (${currentInt} μA)"
}

internal fun parseBatteryMaxChargingVoltage(output: String): String {
    val regex = Regex("(?m)^\\s*Max charging voltage:\\s*(\\d+)")
    val voltageInt = regex.find(output)?.groupValues?.get(1)?.toLongOrNull() ?: return "未知"
    val mV = voltageInt / 1000
    val V = voltageInt / 1000000.0
    return "$V V ($mV mV)"
}

internal fun parseBatteryChargeCounter(output: String): String {
    val regex = Regex("(?m)^\\s*Charge counter:\\s*(\\d+)")
    val counterInt = regex.find(output)?.groupValues?.get(1)?.toLongOrNull() ?: return "未知"
    val mAh = counterInt / 1000
    return "$mAh mAh (${counterInt} μAh)"
}

internal fun parseBatteryPresent(output: String): String {
    val regex = Regex("(?m)^\\s*present:\\s*(\\w+)")
    val match = regex.find(output)?.groupValues?.get(1) ?: return "未知"
    return if (match.equals("true", ignoreCase = true)) "是" else "否"
}

internal fun parseBatteryTechnology(output: String): String {
    val regex = Regex("(?m)^\\s*technology:\\s*(.+)")
    return regex.find(output)?.groupValues?.get(1)?.trim() ?: "未知"
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

internal data class ScreenshotTransferPlan(
    val fileName: String,
    val remotePath: String,
    val localPath: String,
)

internal fun buildScreenshotTransferPlan(
    deviceSerial: String,
    outputDirectoryPath: String,
    capturedAt: LocalDateTime,
): ScreenshotTransferPlan {
    val fileName = buildScreenshotFileName(deviceSerial, capturedAt)
    return ScreenshotTransferPlan(
        fileName = fileName,
        remotePath = "$ScreenshotRemoteDirectory/$fileName",
        localPath = File(outputDirectoryPath, fileName).absolutePath,
    )
}

internal fun buildScreenshotFileName(
    deviceSerial: String,
    capturedAt: LocalDateTime,
): String {
    val safeSerial = deviceSerial.toScreenshotFileToken().ifBlank { "unknown_serial" }
    return "screenshot_${safeSerial}_${capturedAt.format(ScreenshotTimestampFormatter)}.png"
}

private fun String.toScreenshotFileToken(): String {
    return replace(Regex("[^a-zA-Z0-9._-]"), "_").trim('_')
}

internal data class ApplicationInstallCommand(
    val args: List<String>,
    val displayCommand: String,
)

internal fun buildInstallApplicationCommand(
    deviceSerial: String,
    apkFile: File,
): ApplicationInstallCommand {
    return ApplicationInstallCommand(
        args = listOf("-s", deviceSerial, "install", "-r", apkFile.absolutePath),
        displayCommand = "adb -s $deviceSerial install -r \"${apkFile.absolutePath}\"",
    )
}

internal data class DeviceQuickActionCommand(
    val args: List<String>,
    val displayCommand: String,
)

internal fun buildQuickActionCommand(
    deviceSerial: String,
    action: DeviceQuickAction,
): DeviceQuickActionCommand {
    return when (action) {
        DeviceQuickAction.SHUTDOWN -> DeviceQuickActionCommand(
            args = listOf("-s", deviceSerial, "reboot", "-p"),
            displayCommand = "adb -s $deviceSerial reboot -p",
        )

        DeviceQuickAction.POWER -> buildKeyEventCommand(deviceSerial, "KEYCODE_POWER")
        DeviceQuickAction.MENU -> buildKeyEventCommand(deviceSerial, "KEYCODE_APP_SWITCH")
        DeviceQuickAction.HOME -> buildKeyEventCommand(deviceSerial, "KEYCODE_HOME")
        DeviceQuickAction.BACK -> buildKeyEventCommand(deviceSerial, "KEYCODE_BACK")
        DeviceQuickAction.VOLUME_UP -> buildKeyEventCommand(deviceSerial, "KEYCODE_VOLUME_UP")
        DeviceQuickAction.VOLUME_DOWN -> buildKeyEventCommand(deviceSerial, "KEYCODE_VOLUME_DOWN")
        DeviceQuickAction.MUTE -> buildKeyEventCommand(deviceSerial, "KEYCODE_VOLUME_MUTE")
        DeviceQuickAction.WAKE -> buildKeyEventCommand(deviceSerial, "KEYCODE_WAKEUP")
        DeviceQuickAction.SLEEP -> buildKeyEventCommand(deviceSerial, "KEYCODE_SLEEP")
    }
}

private fun buildKeyEventCommand(
    deviceSerial: String,
    keyCode: String,
): DeviceQuickActionCommand {
    return DeviceQuickActionCommand(
        args = listOf("-s", deviceSerial, "shell", "input", "keyevent", keyCode),
        displayCommand = "adb -s $deviceSerial shell input keyevent $keyCode",
    )
}
