package com.floatingmuseum.android.test.helper.device

import com.floatingmuseum.android.test.helper.AppRuntimePaths
import com.floatingmuseum.android.test.helper.adb.AdbShell
import com.floatingmuseum.android.test.helper.localization.commandError
import com.floatingmuseum.android.test.helper.localization.commandStatus
import com.floatingmuseum.android.test.helper.localization.localized
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import java.util.zip.ZipFile

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

        val cpuInfoOutput = try {
            AdbShell.executeAdb(
                args = listOf("-s", deviceSerial, "shell", "cat", "/proc/cpuinfo"),
                displayCommand = "adb -s $deviceSerial shell cat /proc/cpuinfo",
                logCommand = logCommand
            )
        } catch (e: Throwable) {
            ""
        }
        val cpuDetails = parseCpuInfo(cpuInfoOutput)

        val memoryInfoOutput = try {
            AdbShell.executeAdb(
                args = listOf("-s", deviceSerial, "shell", "cat", "/proc/meminfo"),
                displayCommand = "adb -s $deviceSerial shell cat /proc/meminfo",
                logCommand = logCommand
            )
        } catch (e: Throwable) {
            ""
        }
        val memoryDetails = parseMemoryInfo(memoryInfoOutput)

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
        val romVersion = executeGetProp(deviceSerial, "ro.build.display.id", logCommand)

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
            cpuProcessor = cpuDetails.processor,
            cpuHardware = cpuDetails.hardware,
            cpuArchitecture = cpuDetails.architecture,
            cpuCoreCount = cpuDetails.coreCount,
            cpuFeatures = cpuDetails.features,
            memoryTotal = memoryDetails.total,
            memoryFree = memoryDetails.free,
            memoryAvailable = memoryDetails.available,
            memoryBuffers = memoryDetails.buffers,
            memoryCached = memoryDetails.cached,
            memorySwapTotal = memoryDetails.swapTotal,
            memorySwapFree = memoryDetails.swapFree,
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
            romVersion = romVersion,
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
            throw IllegalStateException(localized("device.unable_to_create_screenshot_output_directory_arg0", localDirectory.absolutePath))
        }
        if (!localDirectory.isDirectory) {
            throw IllegalStateException(localized("device.screenshot_output_path_is_not_a_directory_arg0", localDirectory.absolutePath))
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
            try {
                if (!file.exists() || !file.isFile) {
                    ApkInstallResult(
                        filePath = file.absolutePath,
                        fileName = file.name,
                        success = false,
                        message = localized("device.file_does_not_exist_or_is_not_readable"),
                    )
                } else {
                    val extension = file.extension.lowercase()
                    if (extension == "apk") {
                        val command = buildInstallApplicationCommand(deviceSerial, file)
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
                                message = trimmedOutput.ifBlank { localized("device.install_command_did_not_return_success") },
                            )
                        }
                    } else if (extension == "xapk") {
                        installXApk(deviceSerial, file, logCommand)
                    } else {
                        ApkInstallResult(
                            filePath = file.absolutePath,
                            fileName = file.name,
                            success = false,
                            message = localized("device.unsupported_file_format_arg0", extension),
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
                    message = error.message ?: localized("common.unknown_error"),
                )
            }
        }
    }

    override suspend fun runQuickAction(
        deviceSerial: String,
        action: DeviceQuickAction,
        logCommand: (String) -> Unit,
    ) {
        if (action == DeviceQuickAction.CURRENT_ACTIVITY) {
            runCurrentActivityAction(deviceSerial, logCommand)
            return
        }
        val command = buildQuickActionCommand(deviceSerial, action)
        AdbShell.executeAdb(
            args = command.args,
            displayCommand = command.displayCommand,
            logCommand = logCommand,
        )
    }

    private suspend fun runCurrentActivityAction(
        deviceSerial: String,
        logCommand: (String) -> Unit,
    ) {
        var result: Pair<String, String>? = null

        // 1. 尝试使用 dumpsys window | grep mCurrentFocus
        val displayCommand1 = "adb -s $deviceSerial shell \"dumpsys window | grep mCurrentFocus\""
        val args1 = listOf("-s", deviceSerial, "shell", "dumpsys window | grep mCurrentFocus")
        try {
            val output1 = AdbShell.executeAdb(args1, displayCommand1, logCommand)
            result = parseCurrentActivity(output1)
        } catch (e: Exception) {
            // 忽略
        }

        // 2. 尝试 dumpsys activity resumed | grep mResumedActivity
        if (result == null) {
            val displayCommand2 = "adb -s $deviceSerial shell \"dumpsys activity resumed | grep mResumedActivity\""
            val args2 = listOf("-s", deviceSerial, "shell", "dumpsys activity resumed | grep mResumedActivity")
            try {
                val output2 = AdbShell.executeAdb(args2, displayCommand2, logCommand)
                result = parseCurrentActivity(output2)
            } catch (e: Exception) {
                // 忽略
            }
        }

        // 3. 尝试 dumpsys activity activities | grep mResumedActivity
        if (result == null) {
            val displayCommand3 = "adb -s $deviceSerial shell \"dumpsys activity activities | grep mResumedActivity\""
            val args3 = listOf("-s", deviceSerial, "shell", "dumpsys activity activities | grep mResumedActivity")
            try {
                val output3 = AdbShell.executeAdb(args3, displayCommand3, logCommand)
                result = parseCurrentActivity(output3)
            } catch (e: Exception) {
                // 忽略
            }
        }

        if (result != null) {
            logCommand(commandStatus(localized("device.current_package_arg0", result.first)))
            logCommand(commandStatus(localized("device.current_activity_arg0", result.second)))
        } else {
            logCommand(commandError(localized("device.unable_to_get_current_screen_info")))
        }
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

internal data class CpuInfoDetails(
    val processor: String = "未知",
    val hardware: String = "未知",
    val architecture: String = "未知",
    val coreCount: String = "未知",
    val features: String = "未知",
)

internal fun parseCpuInfo(output: String): CpuInfoDetails {
    val values = parseColonKeyValues(output)
    val indexedProcessors = output.lineSequence()
        .mapNotNull { line ->
            val match = Regex("""(?i)^\s*processor\s*:\s*(\d+)\s*$""").find(line)
            match?.groupValues?.get(1)?.toIntOrNull()
        }
        .toSet()
    val cpuCores = values.firstValue("cpu cores")?.toIntOrNull()
    val coreCount = when {
        indexedProcessors.isNotEmpty() -> indexedProcessors.size.toString()
        cpuCores != null -> cpuCores.toString()
        else -> "未知"
    }
    return CpuInfoDetails(
        processor = values.firstExactValue("Processor")
            ?: values.firstValue("model name")
            ?: values.firstValue("Hardware")
            ?: "未知",
        hardware = values.firstValue("Hardware")
            ?: values.firstValue("model name")
            ?: values.firstExactValue("Processor")
            ?: "未知",
        architecture = values.firstValue("CPU architecture")
            ?: values.firstValue("cpu architecture")
            ?: "未知",
        coreCount = coreCount,
        features = values.firstValue("Features")
            ?: values.firstValue("flags")
            ?: "未知",
    )
}

internal data class MemoryInfoDetails(
    val total: String = "未知",
    val free: String = "未知",
    val available: String = "未知",
    val buffers: String = "未知",
    val cached: String = "未知",
    val swapTotal: String = "未知",
    val swapFree: String = "未知",
)

internal fun parseMemoryInfo(output: String): MemoryInfoDetails {
    val values = parseColonKeyValues(output)
    return MemoryInfoDetails(
        total = values.firstMemoryValue("MemTotal"),
        free = values.firstMemoryValue("MemFree"),
        available = values.firstMemoryValue("MemAvailable"),
        buffers = values.firstMemoryValue("Buffers"),
        cached = values.firstMemoryValue("Cached"),
        swapTotal = values.firstMemoryValue("SwapTotal"),
        swapFree = values.firstMemoryValue("SwapFree"),
    )
}

private fun parseColonKeyValues(output: String): Map<String, List<String>> {
    val regex = Regex("""^\s*([^:]+):\s*(.*?)\s*$""")
    return output.lineSequence()
        .mapNotNull { line ->
            val match = regex.find(line) ?: return@mapNotNull null
            match.groupValues[1].trim() to match.groupValues[2].trim()
        }
        .groupBy({ it.first }, { it.second })
}

private fun Map<String, List<String>>.firstValue(key: String): String? {
    return entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
        ?.value
        ?.firstOrNull()
        ?.takeIf { it.isNotBlank() }
}

private fun Map<String, List<String>>.firstExactValue(key: String): String? {
    return this[key]?.firstOrNull()?.takeIf { it.isNotBlank() }
}

private fun Map<String, List<String>>.firstMemoryValue(key: String): String {
    val rawValue = firstValue(key) ?: return "未知"
    val kb = Regex("""(\d+)\s*kB""", RegexOption.IGNORE_CASE)
        .find(rawValue)
        ?.groupValues
        ?.get(1)
        ?.toLongOrNull()
        ?: return rawValue.ifBlank { "未知" }
    return formatMemoryKilobytes(kb)
}

internal fun formatMemoryKilobytes(kilobytes: Long): String {
    val gib = kilobytes / 1024.0 / 1024.0
    return if (gib >= 1.0) {
        "${formatDecimal(gib)} GiB (${kilobytes} kB)"
    } else {
        val mib = kilobytes / 1024.0
        "${formatDecimal(mib)} MiB (${kilobytes} kB)"
    }
}

private fun formatDecimal(value: Double): String {
    val rounded = kotlin.math.round(value * 10.0) / 10.0
    return if (rounded % 1.0 == 0.0) {
        rounded.toInt().toString()
    } else {
        rounded.toString()
    }
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
        DeviceQuickAction.REBOOT_RECOVERY -> DeviceQuickActionCommand(
            args = listOf("-s", deviceSerial, "reboot", "recovery"),
            displayCommand = "adb -s $deviceSerial reboot recovery",
        )
        DeviceQuickAction.REBOOT_FASTBOOT -> DeviceQuickActionCommand(
            args = listOf("-s", deviceSerial, "reboot", "bootloader"),
            displayCommand = "adb -s $deviceSerial reboot bootloader",
        )
        DeviceQuickAction.CURRENT_ACTIVITY -> DeviceQuickActionCommand(
            args = listOf("-s", deviceSerial, "shell", "dumpsys window | grep mCurrentFocus"),
            displayCommand = "adb -s $deviceSerial shell \"dumpsys window | grep mCurrentFocus\"",
        )
    }
}

internal fun parseCurrentActivity(output: String): Pair<String, String>? {
    val regex = """([a-zA-Z0-9._]+)/([a-zA-Z0-9._]+)""".toRegex()
    val matchResult = regex.find(output) ?: return null
    val packageName = matchResult.groupValues[1]
    val rawActivityName = matchResult.groupValues[2]
    val fullActivityName = when {
        rawActivityName.startsWith(".") -> "$packageName$rawActivityName"
        !rawActivityName.contains(".") -> "$packageName.$rawActivityName"
        else -> rawActivityName
    }
    return Pair(packageName, fullActivityName)
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

private suspend fun installXApk(
    deviceSerial: String,
    xapkFile: File,
    logCommand: (String) -> Unit,
): ApkInstallResult {
    var tempDir: File? = null
    try {
        tempDir = AppRuntimePaths.createTempDirectory("xapk_install_")
        
        // 1. 解压 XAPK
        unzip(xapkFile, tempDir)
        
        // 2. 寻找到 manifest.json 并确定基础目录
        val json = Json { ignoreUnknownKeys = true }
        val manifestFile = tempDir.walk().firstOrNull { it.name == "manifest.json" }
        val baseDir = manifestFile?.parentFile ?: tempDir
        
        val manifest = if (manifestFile != null && manifestFile.exists()) {
            try {
                json.decodeFromString<XApkManifest>(manifestFile.readText())
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
        
        // 3. 寻找 APK 文件
        val apkFiles = if (manifest != null && manifest.splitApks.isNotEmpty()) {
            manifest.splitApks.map { splitApk ->
                val directFile = File(baseDir, splitApk.file)
                if (directFile.exists()) {
                    directFile
                } else {
                    baseDir.walk().firstOrNull { it.name == File(splitApk.file).name }
                }
            }.filterNotNull()
        } else {
            baseDir.walk().filter { it.extension.lowercase() == "apk" }.toList()
        }
        
        if (apkFiles.isEmpty()) {
            return ApkInstallResult(
                filePath = xapkFile.absolutePath,
                fileName = xapkFile.name,
                success = false,
                message = localized("device.no_apk_file_found_after_extracting_xapk"),
            )
        }
        
        // 4. 执行 APK(s) 安装
        val installOutput = if (apkFiles.size == 1) {
            val apkFile = apkFiles.first()
            val args = listOf("-s", deviceSerial, "install", "-r", apkFile.absolutePath)
            val displayCmd = "adb -s $deviceSerial install -r \"${apkFile.absolutePath}\""
            AdbShell.executeAdb(args, displayCmd, logCommand)
        } else {
            val args = listOf("-s", deviceSerial, "install-multiple", "-r") + apkFiles.map { it.absolutePath }
            val displayCmd = "adb -s $deviceSerial install-multiple -r ${apkFiles.joinToString(" ") { "\"${it.absolutePath}\"" }}"
            AdbShell.executeAdb(args, displayCmd, logCommand)
        }
        
        val trimmedOutput = installOutput.trim()
        val isInstallSuccess = trimmedOutput.lineSequence().any { it.trim() == "Success" }
        if (!isInstallSuccess) {
            return ApkInstallResult(
                filePath = xapkFile.absolutePath,
                fileName = xapkFile.name,
                success = false,
                message = localized("device.install_command_did_not_return_success_arg0", trimmedOutput),
            )
        }
        
        // 5. 推送 OBB 扩展文件（如果有的话）
        val expansions = manifest?.expansions ?: emptyList()
        val obbResults = mutableListOf<String>()
        var obbSuccess = true
        
        for (expansion in expansions) {
            val expansionFile = baseDir.walk().firstOrNull { it.name == File(expansion.file).name }
            if (expansionFile == null || !expansionFile.exists()) {
                obbResults.add(localized("device.obb_file_not_found_arg0", expansion.file))
                obbSuccess = false
                continue
            }
            
            val installPath = expansion.installPath.ifBlank {
                if (manifest?.packageName?.isNotBlank() == true) {
                    "Android/obb/${manifest.packageName}/${File(expansion.file).name}"
                } else {
                    ""
                }
            }
            
            if (installPath.isBlank()) {
                obbResults.add(localized("device.unable_to_determine_obb_install_path_arg0", expansion.file))
                obbSuccess = false
                continue
            }
            
            val remotePath = installPath.replace('\\', '/')
            val deviceObbPath = if (remotePath.startsWith("/")) {
                if (remotePath.startsWith("/sdcard")) remotePath else "/sdcard$remotePath"
            } else {
                if (remotePath.startsWith("sdcard/")) "/$remotePath" else "/sdcard/$remotePath"
            }
            
            val parentPath = File(deviceObbPath).parent.replace('\\', '/')
            try {
                // 先在设备上创建 parent 目录
                val mkdirArgs = listOf("-s", deviceSerial, "shell", "mkdir", "-p", parentPath)
                val mkdirDisplay = "adb -s $deviceSerial shell mkdir -p \"$parentPath\""
                AdbShell.executeAdb(mkdirArgs, mkdirDisplay, logCommand)
                
                // 推送 OBB
                val pushArgs = listOf("-s", deviceSerial, "push", expansionFile.absolutePath, deviceObbPath)
                val pushDisplay = "adb -s $deviceSerial push \"${expansionFile.absolutePath}\" \"$deviceObbPath\""
                AdbShell.executeAdb(pushArgs, pushDisplay, logCommand)
            } catch (e: Exception) {
                obbResults.add(localized("device.failed_to_push_obb_arg0_arg1", expansion.file, e.message))
                obbSuccess = false
            }
        }
        
        val message = if (obbResults.isEmpty()) {
            "Success"
        } else if (obbSuccess) {
            localized("device.success_with_obb_push")
        } else {
            localized("device.apk_installed_but_obb_push_failed_arg0", obbResults.joinToString("; "))
        }
        
        return ApkInstallResult(
            filePath = xapkFile.absolutePath,
            fileName = xapkFile.name,
            success = true,
            message = message,
        )
    } finally {
        tempDir?.let { deleteDirectory(it) }
    }
}

private fun unzip(zipFile: File, destDir: File) {
    ZipFile(zipFile).use { zip ->
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            val entryFile = File(destDir, entry.name)
            if (entry.isDirectory) {
                entryFile.mkdirs()
            } else {
                entryFile.parentFile.mkdirs()
                zip.getInputStream(entry).use { input ->
                    entryFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
}

private fun deleteDirectory(directory: File) {
    directory.walkBottomUp().forEach {
        it.delete()
    }
}

@Serializable
internal data class XApkManifest(
    @SerialName("package_name") val packageName: String = "",
    val name: String = "",
    @SerialName("split_apks") val splitApks: List<XApkSplitApk> = emptyList(),
    val expansions: List<XApkExpansion> = emptyList()
)

@Serializable
internal data class XApkSplitApk(
    val file: String
)

@Serializable
internal data class XApkExpansion(
    val file: String,
    @SerialName("install_path") val installPath: String = ""
)

object AppLabelCache {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun put(packageName: String, appName: String) {
        cache[packageName] = appName
    }

    fun get(packageName: String): String? {
        return cache[packageName]
    }
}

internal fun parseAppNameFromJson(jsonStr: String): String? {
    val regex = """"appName"\s*:\s*"([^"]+)"""".toRegex()
    return regex.find(jsonStr)?.groupValues?.get(1)
}

internal fun parseContentQueryJson(output: String): String? {
    val key = "json_data="
    val line = output.lineSequence().firstOrNull { it.contains(key) } ?: return null
    return line.substringAfter(key).trim()
}
