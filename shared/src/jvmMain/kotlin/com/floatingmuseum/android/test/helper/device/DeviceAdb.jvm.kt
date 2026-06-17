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
            screenDensity = screenDensity,
            batteryStatus = batteryStatus,
            batteryHealth = batteryHealth,
            batteryTemp = batteryTemp,
            batteryVoltage = batteryVoltage,
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
    val regex = Regex("""level:\s*(\d+)""")
    return regex.find(output)?.groupValues?.get(1)?.toIntOrNull()
}

internal fun parseBatteryStatus(output: String): String {
    val regex = Regex("""status:\s*(\d+)""")
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
    val regex = Regex("""health:\s*(\d+)""")
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
    val regex = Regex("""temp:\s*(\d+)""")
    val tempInt = regex.find(output)?.groupValues?.get(1)?.toIntOrNull() ?: return "未知"
    return "${tempInt / 10.0} °C"
}

internal fun parseBatteryVoltage(output: String): String {
    val regex = Regex("""voltage:\s*(\d+)""")
    val voltageInt = regex.find(output)?.groupValues?.get(1)?.toIntOrNull() ?: return "未知"
    return "$voltageInt mV"
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
