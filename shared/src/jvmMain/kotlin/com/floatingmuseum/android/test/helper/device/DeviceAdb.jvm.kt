package com.floatingmuseum.android.test.helper.device

import com.floatingmuseum.android.test.helper.AppRuntimePaths
import com.floatingmuseum.android.test.helper.adb.AdbShell
import com.floatingmuseum.android.test.helper.localization.commandError
import com.floatingmuseum.android.test.helper.localization.commandStatus
import com.floatingmuseum.android.test.helper.localization.localized
import com.floatingmuseum.android.test.helper.scrcpy.ScrcpyShell
import com.floatingmuseum.android.test.helper.settings.AppSettingsShared
import com.floatingmuseum.android.test.helper.settings.ScreenRecordAudioMode
import com.floatingmuseum.android.test.helper.settings.ScreenRecordBitRate
import com.floatingmuseum.android.test.helper.settings.ScreenRecordFormat
import com.floatingmuseum.android.test.helper.settings.ScreenRecordMaxFps
import com.floatingmuseum.android.test.helper.settings.ScreenRecordMaxSize
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile

actual fun createDeviceAdb(): DeviceAdb = JvmDeviceAdb()

private const val ScreenshotRemoteDirectory = "/sdcard/AndroidTestHelperScreenshots"
private val ScreenRecordRemoteDirectories = listOf(
    "/sdcard/Movies",
    "/sdcard/Download",
    "/sdcard",
    "/data/local/tmp",
)
private val ScreenshotTimestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

private class JvmDeviceAdb : DeviceAdb {
    @Volatile
    private var activeScreenRecordProcess: Process? = null

    @Volatile
    private var activeScreenRecordStopMode: ScreenRecordStopMode = ScreenRecordStopMode.DestroyProcess

    @Volatile
    private var activeScreenRecordStopSignalFile: File? = null

    @Volatile
    private var screenRecordStopRequested: Boolean = false

    @Volatile
    private var activeDeviceMirrorProcess: Process? = null

    @Volatile
    private var deviceMirrorStopRequested: Boolean = false

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

    override suspend fun recordScreen(
        deviceSerial: String,
        outputDirectoryPath: String,
        logCommand: (String) -> Unit,
        onRecordingStarted: () -> Unit,
    ): ScreenRecordResult {
        val capturedAt = LocalDateTime.now()
        val plan = buildScreenRecordTransferPlan(
            deviceSerial = deviceSerial,
            outputDirectoryPath = outputDirectoryPath,
            capturedAt = capturedAt,
        )
        val settings = AppSettingsShared.currentSettings
        val scrcpyLocalPath = File(
            outputDirectoryPath,
            buildScrcpyRecordFileName(deviceSerial, capturedAt, settings.screenRecordFormat),
        ).absolutePath
        val localDirectory = File(outputDirectoryPath)
        if (!localDirectory.exists() && !localDirectory.mkdirs()) {
            throw IllegalStateException(localized("device.unable_to_create_screen_record_output_directory_arg0", localDirectory.absolutePath))
        }
        if (!localDirectory.isDirectory) {
            throw IllegalStateException(localized("device.screen_record_output_path_is_not_a_directory_arg0", localDirectory.absolutePath))
        }

        screenRecordStopRequested = false
        val scrcpyPath = ScrcpyShell.scrcpyPath
        val scrcpyVersion = ScrcpyShell.readScrcpyVersion(scrcpyPath)
        if (screenRecordStopRequested) {
            return ScreenRecordResult(
                remotePath = "",
                localPath = scrcpyLocalPath,
                endState = ScreenRecordEndState.STOPPED,
                message = localized("device.screen_record.stopped"),
            )
        }
        if (scrcpyVersion.isSuccess) {
            val outcome = runScrcpyRecordCommand(
                command = buildScrcpyRecordCommand(
                    scrcpyPath = scrcpyPath,
                    deviceSerial = deviceSerial,
                    localPath = scrcpyLocalPath,
                    format = settings.screenRecordFormat,
                    maxSize = settings.screenRecordMaxSize,
                    bitRate = settings.screenRecordBitRate,
                    maxFps = settings.screenRecordMaxFps,
                    audioMode = settings.screenRecordAudioMode,
                ),
                logCommand = logCommand,
                onRecordingStarted = onRecordingStarted,
            )
            return ScreenRecordResult(
                remotePath = "",
                localPath = scrcpyLocalPath,
                endState = outcome.endState,
                message = outcome.message,
            )
        }

        logCommand(commandStatus(localized(
            "device.screen_record.scrcpy_unavailable_fallback_arg0",
            scrcpyVersion.exceptionOrNull()?.displayMessage() ?: localized("common.unknown_error"),
        )))
        return recordScreenWithAdbScreenrecord(
            deviceSerial = deviceSerial,
            plan = plan,
            logCommand = logCommand,
            onRecordingStarted = onRecordingStarted,
        )
    }

    private suspend fun recordScreenWithAdbScreenrecord(
        deviceSerial: String,
        plan: ScreenRecordTransferPlan,
        logCommand: (String) -> Unit,
        onRecordingStarted: () -> Unit,
    ): ScreenRecordResult {
        screenRecordStopRequested = false
        var lastInterruptedResult: ScreenRecordResult? = null
        for (remotePath in plan.remotePaths) {
            val directoryReady = ensureRemoteScreenRecordParentDirectory(
                deviceSerial = deviceSerial,
                remotePath = remotePath,
                logCommand = logCommand,
            )
            if (!directoryReady) {
                lastInterruptedResult = ScreenRecordResult(
                    remotePath = remotePath,
                    localPath = plan.localPath,
                    endState = ScreenRecordEndState.INTERRUPTED,
                    message = localized("device.screen_record.remote_path_unavailable_arg0", remotePath),
                )
                continue
            }

            val outcome = runScreenRecordCommand(
                command = buildScreenRecordCommand(deviceSerial, remotePath),
                logCommand = logCommand,
                onRecordingStarted = onRecordingStarted,
            )

            if (outcome.endState == ScreenRecordEndState.INTERRUPTED) {
                val result = ScreenRecordResult(
                    remotePath = remotePath,
                    localPath = plan.localPath,
                    endState = outcome.endState,
                    message = outcome.message,
                )
                if (shouldTryNextScreenRecordPath(outcome.message)) {
                    lastInterruptedResult = result
                    continue
                }
                return result
            }

            AdbShell.executeAdb(
                args = listOf("-s", deviceSerial, "pull", remotePath, plan.localPath),
                displayCommand = "adb -s $deviceSerial pull $remotePath \"${plan.localPath}\"",
                logCommand = logCommand
            )
            deleteRemoteScreenRecordFile(deviceSerial, remotePath, logCommand)
            return ScreenRecordResult(
                remotePath = remotePath,
                localPath = plan.localPath,
                endState = outcome.endState,
                message = outcome.message,
            )
        }

        return lastInterruptedResult ?: ScreenRecordResult(
            remotePath = plan.remotePath,
            localPath = plan.localPath,
            endState = ScreenRecordEndState.INTERRUPTED,
            message = localized("device.screen_record.no_available_remote_path"),
        )
    }

    private suspend fun ensureRemoteScreenRecordParentDirectory(
        deviceSerial: String,
        remotePath: String,
        logCommand: (String) -> Unit,
    ): Boolean {
        val parentPath = remotePath.substringBeforeLast('/', missingDelimiterValue = "")
        if (parentPath.isBlank()) return true
        return try {
            AdbShell.executeAdb(
                args = listOf("-s", deviceSerial, "shell", "mkdir", "-p", parentPath),
                displayCommand = "adb -s $deviceSerial shell mkdir -p $parentPath",
                logCommand = logCommand,
            )
            true
        } catch (_: Throwable) {
            false
        }
    }

    private suspend fun deleteRemoteScreenRecordFile(
        deviceSerial: String,
        remotePath: String,
        logCommand: (String) -> Unit,
    ) {
        try {
            AdbShell.executeAdb(
                args = listOf("-s", deviceSerial, "shell", "rm", "-f", remotePath),
                displayCommand = "adb -s $deviceSerial shell rm -f $remotePath",
                logCommand = logCommand,
            )
        } catch (_: Throwable) {
            // The local MP4 has already been pulled. Remote temp cleanup failure should not fail the recording.
        }
    }

    override fun stopScreenRecording() {
        screenRecordStopRequested = true
        activeScreenRecordProcess?.let { process ->
            when (activeScreenRecordStopMode) {
                ScreenRecordStopMode.DestroyProcess -> process.destroy()
                ScreenRecordStopMode.SignalCtrlBreak -> {
                    val stopFile = activeScreenRecordStopSignalFile
                    if (stopFile == null) {
                        process.destroy()
                    } else {
                        runCatching {
                            stopFile.parentFile?.mkdirs()
                            stopFile.writeText("stop")
                        }.onFailure {
                            process.destroy()
                        }
                    }
                }
            }
        }
    }

    override suspend fun mirrorDevice(
        deviceSerial: String,
        windowTitle: String,
        logCommand: (String) -> Unit,
        onMirrorStarted: () -> Unit,
    ): DeviceMirrorResult {
        deviceMirrorStopRequested = false
        val settings = AppSettingsShared.currentSettings
        val scrcpyPath = ScrcpyShell.scrcpyPath
        val scrcpyVersion = ScrcpyShell.readScrcpyVersion(scrcpyPath)
        if (scrcpyVersion.isFailure) {
            throw IllegalStateException(localized(
                "device.mirror.scrcpy_unavailable_arg0",
                scrcpyVersion.exceptionOrNull()?.displayMessage() ?: localized("common.unknown_error"),
            ))
        }
        return runScrcpyMirrorCommand(
            command = buildScrcpyMirrorCommand(
                scrcpyPath = scrcpyPath,
                deviceSerial = deviceSerial,
                windowTitle = windowTitle,
                maxSize = settings.screenRecordMaxSize,
                bitRate = settings.screenRecordBitRate,
                maxFps = settings.screenRecordMaxFps,
            ),
            deviceSerial = deviceSerial,
            logCommand = logCommand,
            onMirrorStarted = onMirrorStarted,
        )
    }

    override fun stopDeviceMirror() {
        deviceMirrorStopRequested = true
        activeDeviceMirrorProcess?.destroy()
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
    ): DeviceInfoValue {
        return try {
            AdbShell.executeAdb(
                args = listOf("-s", deviceSerial, "shell", "getprop", propKey),
                displayCommand = "adb -s $deviceSerial shell getprop $propKey",
                logCommand = logCommand
            ).trim()
        } catch (e: Throwable) {
            null
        }.toDeviceInfoValue()
    }

    private suspend fun runScreenRecordCommand(
        command: ScreenRecordCommand,
        logCommand: (String) -> Unit,
        onRecordingStarted: () -> Unit,
    ): ScreenRecordCommandOutcome {
        logCommand(command.displayCommand)
        return withContext(Dispatchers.IO) {
            val process = ProcessBuilder(listOf(AdbShell.adbPath) + command.args)
                .redirectErrorStream(true)
                .start()
            activeScreenRecordProcess = process
            activeScreenRecordStopMode = ScreenRecordStopMode.DestroyProcess
            activeScreenRecordStopSignalFile = null
            onRecordingStarted()
            val outputReader = async(Dispatchers.IO) {
                process.inputStream.bufferedReader(Charsets.UTF_8).readText()
            }
            try {
                while (!process.waitFor(100L, TimeUnit.MILLISECONDS)) {
                    currentCoroutineContext().ensureActive()
                }
                val output = outputReader.await().trim()
                val exitCode = process.exitValue()
                when {
                    screenRecordStopRequested -> ScreenRecordCommandOutcome(
                        endState = ScreenRecordEndState.STOPPED,
                        message = output.ifBlank { localized("device.screen_record.stopped") },
                    )
                    exitCode == 0 -> ScreenRecordCommandOutcome(
                        endState = ScreenRecordEndState.COMPLETED,
                        message = output.takeIf { it.isNotBlank() },
                    )
                    else -> ScreenRecordCommandOutcome(
                        endState = ScreenRecordEndState.INTERRUPTED,
                        message = localized("device.screen_record.interrupted_exit_code_arg0", exitCode) +
                            output.takeIf { it.isNotBlank() }?.let { "\n$it" }.orEmpty(),
                    )
                }
            } catch (error: CancellationException) {
                process.destroyForcibly()
                outputReader.cancel()
                throw error
            } finally {
                if (activeScreenRecordProcess === process) {
                    activeScreenRecordProcess = null
                    activeScreenRecordStopMode = ScreenRecordStopMode.DestroyProcess
                    activeScreenRecordStopSignalFile = null
                }
            }
        }
    }

    private suspend fun runScrcpyRecordCommand(
        command: ScrcpyRecordCommand,
        logCommand: (String) -> Unit,
        onRecordingStarted: () -> Unit,
    ): ScreenRecordCommandOutcome {
        logCommand(command.displayCommand)
        return withContext(Dispatchers.IO) {
            val scrcpyFile = File(command.scrcpyPath)
            val scrcpyDirectory = scrcpyFile.parentFile?.takeIf { it.isDirectory }
            val windowsStopSignalFile = if (isWindowsHost()) {
                AppRuntimePaths.createTempDirectory("scrcpy_record_").resolve("stop.signal")
            } else {
                null
            }
            val processBuilder = if (windowsStopSignalFile != null) {
                val scriptFile = windowsStopSignalFile.parentFile.resolve("run-scrcpy-record.ps1")
                scriptFile.writeText(
                    buildWindowsScrcpyRecordWrapperScript(
                        command = command,
                        workingDirectory = scrcpyDirectory ?: File(".").absoluteFile,
                        stopSignalFile = windowsStopSignalFile,
                    ),
                )
                ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-File",
                    scriptFile.absolutePath,
                )
            } else {
                ProcessBuilder(listOf(command.scrcpyPath) + command.args)
            }.redirectErrorStream(true)
            scrcpyDirectory?.let { directory ->
                processBuilder.directory(directory)
                directory.resolve("scrcpy-server").takeIf { it.isFile }?.let { serverFile ->
                    processBuilder.environment()["SCRCPY_SERVER_PATH"] = serverFile.absolutePath
                }
            }
            processBuilder.environment()["ADB"] = AdbShell.adbPath
            val process = processBuilder.start()
            activeScreenRecordProcess = process
            activeScreenRecordStopSignalFile = windowsStopSignalFile
            activeScreenRecordStopMode = if (windowsStopSignalFile != null) {
                ScreenRecordStopMode.SignalCtrlBreak
            } else {
                ScreenRecordStopMode.DestroyProcess
            }
            val recordingStarted = AtomicBoolean(false)
            val outputReader = async(Dispatchers.IO) {
                val lines = mutableListOf<String>()
                process.inputStream.bufferedReader(Charsets.UTF_8).useLines { outputLines ->
                    outputLines.forEach { line ->
                        lines += line
                        if (isScrcpyRecordingStartedLine(line) && recordingStarted.compareAndSet(false, true)) {
                            onRecordingStarted()
                        }
                    }
                }
                lines.joinToString("\n")
            }
            try {
                while (!process.waitFor(100L, TimeUnit.MILLISECONDS)) {
                    currentCoroutineContext().ensureActive()
                }
                val output = outputReader.await().trim()
                val exitCode = process.exitValue()
                when {
                    screenRecordStopRequested -> ScreenRecordCommandOutcome(
                        endState = ScreenRecordEndState.STOPPED,
                        message = output.ifBlank { localized("device.screen_record.stopped") },
                    )
                    exitCode == 0 -> ScreenRecordCommandOutcome(
                        endState = ScreenRecordEndState.COMPLETED,
                        message = output.takeIf { it.isNotBlank() },
                    )
                    else -> ScreenRecordCommandOutcome(
                        endState = ScreenRecordEndState.INTERRUPTED,
                        message = localized("device.screen_record.scrcpy_interrupted_exit_code_arg0", exitCode) +
                            output.takeIf { it.isNotBlank() }?.let { "\n$it" }.orEmpty(),
                    )
                }
            } catch (error: CancellationException) {
                process.destroyForcibly()
                outputReader.cancel()
                throw error
            } finally {
                if (activeScreenRecordProcess === process) {
                    activeScreenRecordProcess = null
                    activeScreenRecordStopMode = ScreenRecordStopMode.DestroyProcess
                    activeScreenRecordStopSignalFile = null
                }
            }
        }
    }

    private suspend fun runScrcpyMirrorCommand(
        command: ScrcpyMirrorCommand,
        deviceSerial: String,
        logCommand: (String) -> Unit,
        onMirrorStarted: () -> Unit,
    ): DeviceMirrorResult {
        logCommand(command.displayCommand)
        return withContext(Dispatchers.IO) {
            val scrcpyFile = File(command.scrcpyPath)
            val processBuilder = ProcessBuilder(listOf(command.scrcpyPath) + command.args)
                .redirectErrorStream(true)
            scrcpyFile.parentFile?.takeIf { it.isDirectory }?.let { scrcpyDirectory ->
                processBuilder.directory(scrcpyDirectory)
                scrcpyDirectory.resolve("scrcpy-server").takeIf { it.isFile }?.let { serverFile ->
                    processBuilder.environment()["SCRCPY_SERVER_PATH"] = serverFile.absolutePath
                }
            }
            processBuilder.environment()["ADB"] = AdbShell.adbPath
            val process = processBuilder.start()
            activeDeviceMirrorProcess = process
            onMirrorStarted()
            val outputReader = async(Dispatchers.IO) {
                buildString {
                    process.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                        lines.forEach { line ->
                            logScrcpyMirrorInstallEvent(
                                line = line,
                                deviceSerial = deviceSerial,
                                logCommand = logCommand,
                            )
                            append(line).append('\n')
                        }
                    }
                }
            }
            try {
                while (!process.waitFor(100L, TimeUnit.MILLISECONDS)) {
                    currentCoroutineContext().ensureActive()
                }
                val output = outputReader.await().trim()
                val exitCode = process.exitValue()
                when {
                    deviceMirrorStopRequested -> DeviceMirrorResult(
                        endState = DeviceMirrorEndState.STOPPED,
                        message = output.ifBlank { localized("device.mirror.stopped") },
                    )
                    exitCode == 0 -> DeviceMirrorResult(
                        endState = DeviceMirrorEndState.CLOSED,
                        message = output.takeIf { it.isNotBlank() },
                    )
                    else -> DeviceMirrorResult(
                        endState = DeviceMirrorEndState.INTERRUPTED,
                        message = localized("device.mirror.interrupted_exit_code_arg0", exitCode) +
                            output.takeIf { it.isNotBlank() }?.let { "\n$it" }.orEmpty(),
                    )
                }
            } catch (error: CancellationException) {
                process.destroyForcibly()
                outputReader.cancel()
                throw error
            } finally {
                if (activeDeviceMirrorProcess === process) {
                    activeDeviceMirrorProcess = null
                }
            }
        }
    }
}

private fun Throwable.displayMessage(): String {
    return message
        ?.lineSequence()
        ?.firstOrNull { it.isNotBlank() }
        ?.trim()
        ?: this::class.simpleName
        ?: localized("common.unknown_error")
}

private fun String?.toDeviceInfoValue(): DeviceInfoValue {
    return this?.takeIf { it.isNotBlank() }?.let { DeviceInfoValue.Text(it) } ?: DeviceInfoValue.Unknown
}

internal fun parseScreenSize(output: String): DeviceInfoValue {
    val overrideRegex = Regex("""Override size:\s*(\d+x\d+)""")
    val physicalRegex = Regex("""Physical size:\s*(\d+x\d+)""")
    val overrideMatch = overrideRegex.find(output)?.groupValues?.get(1)
    val physicalMatch = physicalRegex.find(output)?.groupValues?.get(1)
    return when {
        overrideMatch != null && physicalMatch != null -> DeviceInfoValue.PhysicalOverride(overrideMatch, physicalMatch)
        overrideMatch != null -> DeviceInfoValue.Text(overrideMatch)
        physicalMatch != null -> DeviceInfoValue.Text(physicalMatch)
        else -> DeviceInfoValue.Unknown
    }
}

internal fun parseDisplayId(output: String): DeviceInfoValue {
    val regex = Regex("""Display:\s+mDisplayId=(\d+)""")
    return regex.find(output)?.groupValues?.get(1).toDeviceInfoValue()
}

internal fun parseDisplayInit(output: String): DeviceInfoValue {
    val regex = Regex("""\binit=(\d+x\d+\s+\d+dpi|\d+x\d+)""")
    return regex.find(output)?.groupValues?.get(1).toDeviceInfoValue()
}

internal fun parseDisplayCur(output: String): DeviceInfoValue {
    val regex = Regex("""\bcur=(\d+x\d+)""")
    return regex.find(output)?.groupValues?.get(1).toDeviceInfoValue()
}

internal fun parseDisplayApp(output: String): DeviceInfoValue {
    val regex = Regex("""\bapp=(\d+x\d+)""")
    return regex.find(output)?.groupValues?.get(1).toDeviceInfoValue()
}

internal fun parseDisplayRefreshRate(output: String): DeviceInfoValue {
    val fpsRegex = Regex("""fps\s*[=:\s]\s*(\d+(?:\.\d+)?)""")
    val fpsMatch = fpsRegex.find(output)?.groupValues?.get(1)
    if (fpsMatch != null) return DeviceInfoValue.Text("$fpsMatch Hz")

    val renderRateRegex = Regex("""renderFrameRate\s+(\d+(?:\.\d+)?)""")
    val renderRateMatch = renderRateRegex.find(output)?.groupValues?.get(1)
    if (renderRateMatch != null) return DeviceInfoValue.Text("$renderRateMatch Hz")

    val defaultRateRegex = Regex("""mDefaultRefreshRate:\s*(\d+(?:\.\d+)?)""")
    val defaultRateMatch = defaultRateRegex.find(output)?.groupValues?.get(1)
    if (defaultRateMatch != null) return DeviceInfoValue.Text("$defaultRateMatch Hz")

    val refreshRateRegex = Regex("""\b(?:mRefreshRate|refreshRate|fps)\b\s*[=:\s]\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    val refreshRateMatch = refreshRateRegex.find(output)?.groupValues?.get(1)
    if (refreshRateMatch != null) return DeviceInfoValue.Text("$refreshRateMatch Hz")

    return DeviceInfoValue.Unknown
}

internal data class CpuInfoDetails(
    val processor: DeviceInfoValue = DeviceInfoValue.Unknown,
    val hardware: DeviceInfoValue = DeviceInfoValue.Unknown,
    val architecture: DeviceInfoValue = DeviceInfoValue.Unknown,
    val coreCount: DeviceInfoValue = DeviceInfoValue.Unknown,
    val features: DeviceInfoValue = DeviceInfoValue.Unknown,
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
        indexedProcessors.isNotEmpty() -> DeviceInfoValue.Text(indexedProcessors.size.toString())
        cpuCores != null -> DeviceInfoValue.Text(cpuCores.toString())
        else -> DeviceInfoValue.Unknown
    }
    return CpuInfoDetails(
        processor = (values.firstExactValue("Processor")
            ?: values.firstValue("model name")
            ?: values.firstValue("Hardware")).toDeviceInfoValue(),
        hardware = (values.firstValue("Hardware")
            ?: values.firstValue("model name")
            ?: values.firstExactValue("Processor")).toDeviceInfoValue(),
        architecture = (values.firstValue("CPU architecture")
            ?: values.firstValue("cpu architecture")).toDeviceInfoValue(),
        coreCount = coreCount,
        features = (values.firstValue("Features")
            ?: values.firstValue("flags")).toDeviceInfoValue(),
    )
}

internal data class MemoryInfoDetails(
    val total: DeviceInfoValue = DeviceInfoValue.Unknown,
    val free: DeviceInfoValue = DeviceInfoValue.Unknown,
    val available: DeviceInfoValue = DeviceInfoValue.Unknown,
    val buffers: DeviceInfoValue = DeviceInfoValue.Unknown,
    val cached: DeviceInfoValue = DeviceInfoValue.Unknown,
    val swapTotal: DeviceInfoValue = DeviceInfoValue.Unknown,
    val swapFree: DeviceInfoValue = DeviceInfoValue.Unknown,
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

private fun Map<String, List<String>>.firstMemoryValue(key: String): DeviceInfoValue {
    val rawValue = firstValue(key) ?: return DeviceInfoValue.Unknown
    val kb = Regex("""(\d+)\s*kB""", RegexOption.IGNORE_CASE)
        .find(rawValue)
        ?.groupValues
        ?.get(1)
        ?.toLongOrNull()
        ?: return rawValue.toDeviceInfoValue()
    return DeviceInfoValue.Text(formatMemoryKilobytes(kb))
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

internal fun parseScreenDensity(output: String): DeviceInfoValue {
    val overrideRegex = Regex("""Override density:\s*(\d+)""")
    val physicalRegex = Regex("""Physical density:\s*(\d+)""")
    val overrideMatch = overrideRegex.find(output)?.groupValues?.get(1)
    val physicalMatch = physicalRegex.find(output)?.groupValues?.get(1)
    return when {
        overrideMatch != null && physicalMatch != null -> DeviceInfoValue.PhysicalOverride(overrideMatch, physicalMatch)
        overrideMatch != null -> DeviceInfoValue.Text(overrideMatch)
        physicalMatch != null -> DeviceInfoValue.Text(physicalMatch)
        else -> DeviceInfoValue.Unknown
    }
}

internal fun parseBatteryLevel(output: String): Int? {
    val regex = Regex("(?m)^\\s*level:\\s*(\\d+)")
    return regex.find(output)?.groupValues?.get(1)?.toIntOrNull()
}

internal fun parseBatteryStatus(output: String): DeviceInfoValue {
    val regex = Regex("(?m)^\\s*status:\\s*(\\d+)")
    val statusInt = regex.find(output)?.groupValues?.get(1)?.toIntOrNull() ?: return DeviceInfoValue.Unknown
    return when (statusInt) {
        2 -> DeviceInfoValue.Localized(DeviceInfoToken.BatteryStatusCharging)
        3 -> DeviceInfoValue.Localized(DeviceInfoToken.BatteryStatusDischarging)
        4 -> DeviceInfoValue.Localized(DeviceInfoToken.BatteryStatusNotCharging)
        5 -> DeviceInfoValue.Localized(DeviceInfoToken.BatteryStatusFull)
        else -> DeviceInfoValue.Unknown
    }
}

internal fun parseBatteryHealth(output: String): DeviceInfoValue {
    val regex = Regex("(?m)^\\s*health:\\s*(\\d+)")
    val healthInt = regex.find(output)?.groupValues?.get(1)?.toIntOrNull() ?: return DeviceInfoValue.Unknown
    return when (healthInt) {
        2 -> DeviceInfoValue.Localized(DeviceInfoToken.BatteryHealthGood)
        3 -> DeviceInfoValue.Localized(DeviceInfoToken.BatteryHealthOverheated)
        4 -> DeviceInfoValue.Localized(DeviceInfoToken.BatteryHealthDamaged)
        5 -> DeviceInfoValue.Localized(DeviceInfoToken.BatteryHealthOverVoltage)
        6 -> DeviceInfoValue.Localized(DeviceInfoToken.BatteryHealthUnknownFailure)
        7 -> DeviceInfoValue.Localized(DeviceInfoToken.BatteryHealthCold)
        else -> DeviceInfoValue.Unknown
    }
}

internal fun parseBatteryTemp(output: String): DeviceInfoValue {
    val regex = Regex("(?m)^\\s*temp:\\s*(\\d+)")
    val tempInt = regex.find(output)?.groupValues?.get(1)?.toIntOrNull() ?: return DeviceInfoValue.Unknown
    return DeviceInfoValue.Text("${tempInt / 10.0} °C")
}

internal fun parseBatteryVoltage(output: String): DeviceInfoValue {
    val regex = Regex("(?m)^\\s*voltage:\\s*(\\d+)")
    val voltageInt = regex.find(output)?.groupValues?.get(1)?.toIntOrNull() ?: return DeviceInfoValue.Unknown
    return DeviceInfoValue.Text("$voltageInt mV")
}

internal fun parseBatteryACPowered(output: String): DeviceInfoValue {
    val regex = Regex("(?m)^\\s*AC powered:\\s*(\\w+)")
    val match = regex.find(output)?.groupValues?.get(1) ?: return DeviceInfoValue.Unknown
    return DeviceInfoValue.BooleanValue(match.equals("true", ignoreCase = true))
}

internal fun parseBatteryUSBPowered(output: String): DeviceInfoValue {
    val regex = Regex("(?m)^\\s*USB powered:\\s*(\\w+)")
    val match = regex.find(output)?.groupValues?.get(1) ?: return DeviceInfoValue.Unknown
    return DeviceInfoValue.BooleanValue(match.equals("true", ignoreCase = true))
}

internal fun parseBatteryWirelessPowered(output: String): DeviceInfoValue {
    val regex = Regex("(?m)^\\s*Wireless powered:\\s*(\\w+)")
    val match = regex.find(output)?.groupValues?.get(1) ?: return DeviceInfoValue.Unknown
    return DeviceInfoValue.BooleanValue(match.equals("true", ignoreCase = true))
}

internal fun parseBatteryMaxChargingCurrent(output: String): DeviceInfoValue {
    val regex = Regex("(?m)^\\s*Max charging current:\\s*(\\d+)")
    val currentInt = regex.find(output)?.groupValues?.get(1)?.toLongOrNull() ?: return DeviceInfoValue.Unknown
    val mA = currentInt / 1000
    return DeviceInfoValue.Text("$mA mA (${currentInt} μA)")
}

internal fun parseBatteryMaxChargingVoltage(output: String): DeviceInfoValue {
    val regex = Regex("(?m)^\\s*Max charging voltage:\\s*(\\d+)")
    val voltageInt = regex.find(output)?.groupValues?.get(1)?.toLongOrNull() ?: return DeviceInfoValue.Unknown
    val mV = voltageInt / 1000
    val V = voltageInt / 1000000.0
    return DeviceInfoValue.Text("$V V ($mV mV)")
}

internal fun parseBatteryChargeCounter(output: String): DeviceInfoValue {
    val regex = Regex("(?m)^\\s*Charge counter:\\s*(\\d+)")
    val counterInt = regex.find(output)?.groupValues?.get(1)?.toLongOrNull() ?: return DeviceInfoValue.Unknown
    val mAh = counterInt / 1000
    return DeviceInfoValue.Text("$mAh mAh (${counterInt} μAh)")
}

internal fun parseBatteryPresent(output: String): DeviceInfoValue {
    val regex = Regex("(?m)^\\s*present:\\s*(\\w+)")
    val match = regex.find(output)?.groupValues?.get(1) ?: return DeviceInfoValue.Unknown
    return DeviceInfoValue.BooleanValue(match.equals("true", ignoreCase = true))
}

internal fun parseBatteryTechnology(output: String): DeviceInfoValue {
    val regex = Regex("(?m)^\\s*technology:\\s*(.+)")
    return regex.find(output)?.groupValues?.get(1)?.trim().toDeviceInfoValue()
}

internal fun parseIpAddress(output: String): DeviceInfoValue {
    val ipRegex = Regex("""inet\s+(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})""")
    return output.lineSequence()
        .mapNotNull { line ->
            ipRegex.find(line)?.groupValues?.get(1)
        }
        .firstOrNull { it != "127.0.0.1" }
        .toDeviceInfoValue()
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

internal data class ScreenRecordTransferPlan(
    val fileName: String,
    val remotePath: String,
    val remotePaths: List<String>,
    val localPath: String,
)

internal fun buildScreenRecordTransferPlan(
    deviceSerial: String,
    outputDirectoryPath: String,
    capturedAt: LocalDateTime,
): ScreenRecordTransferPlan {
    val fileName = buildScreenRecordFileName(deviceSerial, capturedAt)
    val remotePaths = buildScreenRecordRemotePaths(fileName)
    return ScreenRecordTransferPlan(
        fileName = fileName,
        remotePath = remotePaths.first(),
        remotePaths = remotePaths,
        localPath = File(outputDirectoryPath, fileName).absolutePath,
    )
}

internal fun buildScreenRecordRemotePaths(fileName: String): List<String> {
    return ScreenRecordRemoteDirectories.map { directory -> "$directory/$fileName" }
}

internal fun buildScreenRecordFileName(
    deviceSerial: String,
    capturedAt: LocalDateTime,
): String {
    val safeSerial = deviceSerial.toScreenshotFileToken().ifBlank { "unknown_serial" }
    return "screenrecord_${safeSerial}_${capturedAt.format(ScreenshotTimestampFormatter)}.mp4"
}

internal fun buildScrcpyRecordFileName(
    deviceSerial: String,
    capturedAt: LocalDateTime,
    format: ScreenRecordFormat = ScreenRecordFormat.Mp4,
): String {
    val safeSerial = deviceSerial.toScreenshotFileToken().ifBlank { "unknown_serial" }
    return "screenrecord_${safeSerial}_${capturedAt.format(ScreenshotTimestampFormatter)}.${format.fileExtension}"
}

internal data class ScreenRecordCommand(
    val args: List<String>,
    val displayCommand: String,
)

internal fun buildScreenRecordCommand(
    deviceSerial: String,
    remotePath: String,
): ScreenRecordCommand {
    return ScreenRecordCommand(
        args = listOf("-s", deviceSerial, "shell", "screenrecord", remotePath),
        displayCommand = "adb -s $deviceSerial shell screenrecord $remotePath",
    )
}

internal data class ScrcpyRecordCommand(
    val scrcpyPath: String,
    val args: List<String>,
    val displayCommand: String,
)

internal fun buildScrcpyRecordCommand(
    scrcpyPath: String,
    deviceSerial: String,
    localPath: String,
    format: ScreenRecordFormat = ScreenRecordFormat.Mp4,
    maxSize: ScreenRecordMaxSize = ScreenRecordMaxSize.Original,
    bitRate: ScreenRecordBitRate = ScreenRecordBitRate.Default,
    maxFps: ScreenRecordMaxFps = ScreenRecordMaxFps.Default,
    audioMode: ScreenRecordAudioMode = ScreenRecordAudioMode.Disabled,
): ScrcpyRecordCommand {
    val args = buildList {
        add("--serial=$deviceSerial")
        addAll(audioMode.scrcpyArgs)
        add("--no-playback")
        add("--no-window")
        add("--no-control")
        maxSize.scrcpyArg?.let { add(it) }
        bitRate.scrcpyArg?.let { add(it) }
        maxFps.scrcpyArg?.let { add(it) }
        add("--record-format=${format.scrcpyValue}")
        add("--record=$localPath")
    }
    return ScrcpyRecordCommand(
        scrcpyPath = scrcpyPath,
        args = args,
        displayCommand = buildScrcpyDisplayCommand(scrcpyPath, args),
    )
}

private val ScreenRecordFormat.scrcpyValue: String
    get() = when (this) {
        ScreenRecordFormat.Mkv -> "mkv"
        ScreenRecordFormat.Mp4 -> "mp4"
    }

private val ScreenRecordFormat.fileExtension: String
    get() = scrcpyValue

private val ScreenRecordMaxSize.scrcpyArg: String?
    get() = when (this) {
        ScreenRecordMaxSize.Original -> null
        ScreenRecordMaxSize.Size1080 -> "--max-size=1080"
        ScreenRecordMaxSize.Size720 -> "--max-size=720"
        ScreenRecordMaxSize.Size480 -> "--max-size=480"
    }

private val ScreenRecordBitRate.scrcpyArg: String?
    get() = when (this) {
        ScreenRecordBitRate.Default -> null
        ScreenRecordBitRate.Mbps4 -> "--video-bit-rate=4M"
        ScreenRecordBitRate.Mbps8 -> "--video-bit-rate=8M"
        ScreenRecordBitRate.Mbps12 -> "--video-bit-rate=12M"
        ScreenRecordBitRate.Mbps20 -> "--video-bit-rate=20M"
    }

private val ScreenRecordMaxFps.scrcpyArg: String?
    get() = when (this) {
        ScreenRecordMaxFps.Default -> null
        ScreenRecordMaxFps.Fps15 -> "--max-fps=15"
        ScreenRecordMaxFps.Fps30 -> "--max-fps=30"
        ScreenRecordMaxFps.Fps60 -> "--max-fps=60"
    }

private val ScreenRecordAudioMode.scrcpyArgs: List<String>
    get() = when (this) {
        ScreenRecordAudioMode.Disabled -> listOf("--no-audio")
        ScreenRecordAudioMode.DeviceOutput -> listOf("--audio-source=output")
        ScreenRecordAudioMode.Microphone -> listOf("--audio-source=mic")
    }

private fun buildScrcpyDisplayCommand(scrcpyPath: String, args: List<String>): String {
    val displayArgs = args.joinToString(" ") { arg ->
        when {
            arg.startsWith("--record=") -> {
                "--record=\"${arg.substringAfter('=').toDisplayCommandToken()}\""
            }
            arg.startsWith("--window-title=") -> {
                "--window-title=\"${arg.substringAfter('=').toDisplayCommandToken()}\""
            }
            else -> arg
        }
    }
    return "\"$scrcpyPath\" $displayArgs"
}

internal fun isScrcpyRecordingStartedLine(line: String): Boolean {
    val normalized = line.trim().lowercase()
    return "recording" in normalized &&
        "started" in normalized &&
        ("to " in normalized || "file" in normalized || "record" in normalized)
}

internal enum class ScrcpyMirrorInstallEventType {
    INSTALLING,
    SUCCEEDED,
    FAILED,
}

internal data class ScrcpyMirrorInstallEvent(
    val type: ScrcpyMirrorInstallEventType,
    val filePath: String,
)

internal fun parseScrcpyMirrorInstallEvent(line: String): ScrcpyMirrorInstallEvent? {
    val message = line.substringAfterLast(": ", line).trim()
    Regex("""Installing (.+)\.\.\.""", RegexOption.IGNORE_CASE).matchEntire(message)?.let {
        return ScrcpyMirrorInstallEvent(ScrcpyMirrorInstallEventType.INSTALLING, it.groupValues[1])
    }
    Regex("""(.+) successfully installed""", RegexOption.IGNORE_CASE).matchEntire(message)?.let {
        return ScrcpyMirrorInstallEvent(ScrcpyMirrorInstallEventType.SUCCEEDED, it.groupValues[1])
    }
    Regex("""Failed to install (.+)""", RegexOption.IGNORE_CASE).matchEntire(message)?.let {
        return ScrcpyMirrorInstallEvent(ScrcpyMirrorInstallEventType.FAILED, it.groupValues[1])
    }
    return null
}

internal fun buildScrcpyApkInstallDisplayCommand(deviceSerial: String, apkFilePath: String): String {
    return "adb -s $deviceSerial install -r \"${apkFilePath.toDisplayCommandToken()}\""
}

private fun logScrcpyMirrorInstallEvent(
    line: String,
    deviceSerial: String,
    logCommand: (String) -> Unit,
) {
    val event = parseScrcpyMirrorInstallEvent(line) ?: return
    when (event.type) {
        ScrcpyMirrorInstallEventType.INSTALLING -> {
            logCommand(buildScrcpyApkInstallDisplayCommand(deviceSerial, event.filePath))
        }
        ScrcpyMirrorInstallEventType.SUCCEEDED -> {
            logCommand(commandStatus(localized("device.mirror.apk_install_succeeded_arg0", event.filePath)))
        }
        ScrcpyMirrorInstallEventType.FAILED -> {
            logCommand(commandError(localized("device.mirror.apk_install_failed_arg0", event.filePath)))
        }
    }
}

private enum class ScreenRecordStopMode {
    DestroyProcess,
    SignalCtrlBreak,
}

private fun isWindowsHost(): Boolean {
    return System.getProperty("os.name").contains("win", ignoreCase = true)
}

internal fun buildWindowsScrcpyRecordWrapperScript(
    command: ScrcpyRecordCommand,
    workingDirectory: File,
    stopSignalFile: File,
): String {
    val commandLine = buildWindowsCommandLine(command.scrcpyPath, command.args)
    return """
        ${'$'}ErrorActionPreference = 'Stop'
        ${'$'}scrcpyPath = ${command.scrcpyPath.toPowerShellSingleQuoted()}
        ${'$'}commandLine = ${commandLine.toPowerShellSingleQuoted()}
        ${'$'}workingDirectory = ${workingDirectory.absolutePath.toPowerShellSingleQuoted()}
        ${'$'}stopFile = ${stopSignalFile.absolutePath.toPowerShellSingleQuoted()}

        if (-not ('AndroidTestHelperScrcpyProcessControl' -as [type])) {
        Add-Type -TypeDefinition @'
        using System;
        using System.Runtime.InteropServices;

        public class AndroidTestHelperScrcpyProcessControl {
            [StructLayout(LayoutKind.Sequential, CharSet=CharSet.Unicode)]
            public struct STARTUPINFO {
                public UInt32 cb;
                public string lpReserved;
                public string lpDesktop;
                public string lpTitle;
                public UInt32 dwX;
                public UInt32 dwY;
                public UInt32 dwXSize;
                public UInt32 dwYSize;
                public UInt32 dwXCountChars;
                public UInt32 dwYCountChars;
                public UInt32 dwFillAttribute;
                public UInt32 dwFlags;
                public UInt16 wShowWindow;
                public UInt16 cbReserved2;
                public IntPtr lpReserved2;
                public IntPtr hStdInput;
                public IntPtr hStdOutput;
                public IntPtr hStdError;
            }

            [StructLayout(LayoutKind.Sequential)]
            public struct PROCESS_INFORMATION {
                public IntPtr hProcess;
                public IntPtr hThread;
                public UInt32 dwProcessId;
                public UInt32 dwThreadId;
            }

            [DllImport("kernel32.dll", SetLastError=true, CharSet=CharSet.Unicode)]
            public static extern bool CreateProcessW(
                string app,
                string cmd,
                IntPtr processAttributes,
                IntPtr threadAttributes,
                bool inheritHandles,
                UInt32 creationFlags,
                IntPtr environment,
                string currentDirectory,
                ref STARTUPINFO startupInfo,
                out PROCESS_INFORMATION processInformation
            );

            [DllImport("kernel32.dll", SetLastError=true)]
            public static extern bool GenerateConsoleCtrlEvent(UInt32 ctrlEvent, UInt32 processGroupId);

            [DllImport("kernel32.dll", SetLastError=true)]
            public static extern bool CloseHandle(IntPtr handle);

            [DllImport("kernel32.dll", SetLastError=true)]
            public static extern IntPtr GetStdHandle(Int32 standardHandle);

            [DllImport("kernel32.dll", SetLastError=true)]
            public static extern bool AllocConsole();

            [DllImport("kernel32.dll", SetLastError=true)]
            public static extern IntPtr GetConsoleWindow();

            [DllImport("user32.dll", SetLastError=true)]
            public static extern bool ShowWindow(IntPtr window, Int32 command);
        }
        '@
        }

        ${'$'}stdIn = [AndroidTestHelperScrcpyProcessControl]::GetStdHandle(-10)
        ${'$'}stdOut = [AndroidTestHelperScrcpyProcessControl]::GetStdHandle(-11)
        ${'$'}stdErr = [AndroidTestHelperScrcpyProcessControl]::GetStdHandle(-12)
        if ([AndroidTestHelperScrcpyProcessControl]::GetConsoleWindow() -eq [IntPtr]::Zero) {
            [AndroidTestHelperScrcpyProcessControl]::AllocConsole() | Out-Null
            ${'$'}consoleWindow = [AndroidTestHelperScrcpyProcessControl]::GetConsoleWindow()
            if (${'$'}consoleWindow -ne [IntPtr]::Zero) {
                [AndroidTestHelperScrcpyProcessControl]::ShowWindow(${'$'}consoleWindow, 0) | Out-Null
            }
        }

        ${'$'}startupInfo = New-Object AndroidTestHelperScrcpyProcessControl+STARTUPINFO
        ${'$'}startupInfo.cb = [Runtime.InteropServices.Marshal]::SizeOf(${'$'}startupInfo)
        ${'$'}startupInfo.dwFlags = 0x00000100
        ${'$'}startupInfo.hStdInput = ${'$'}stdIn
        ${'$'}startupInfo.hStdOutput = ${'$'}stdOut
        ${'$'}startupInfo.hStdError = ${'$'}stdErr
        ${'$'}processInfo = New-Object AndroidTestHelperScrcpyProcessControl+PROCESS_INFORMATION
        ${'$'}createNewProcessGroup = 0x00000200

        ${'$'}created = [AndroidTestHelperScrcpyProcessControl]::CreateProcessW(
            ${'$'}scrcpyPath,
            ${'$'}commandLine,
            [IntPtr]::Zero,
            [IntPtr]::Zero,
            ${'$'}true,
            ${'$'}createNewProcessGroup,
            [IntPtr]::Zero,
            ${'$'}workingDirectory,
            [ref]${'$'}startupInfo,
            [ref]${'$'}processInfo
        )

        if (-not ${'$'}created) {
            ${'$'}errorCode = [Runtime.InteropServices.Marshal]::GetLastWin32Error()
            Write-Error "Unable to start scrcpy process. Win32 error: ${'$'}errorCode"
            exit 1
        }

        try {
            ${'$'}child = [System.Diagnostics.Process]::GetProcessById([int]${'$'}processInfo.dwProcessId)
            ${'$'}stopSent = ${'$'}false
            ${'$'}stopDeadline = ${'$'}null
            while (-not ${'$'}child.HasExited) {
                if ((-not ${'$'}stopSent) -and (Test-Path -LiteralPath ${'$'}stopFile)) {
                    ${'$'}sent = [AndroidTestHelperScrcpyProcessControl]::GenerateConsoleCtrlEvent(1, ${'$'}processInfo.dwProcessId)
                    if (-not ${'$'}sent) {
                        ${'$'}errorCode = [Runtime.InteropServices.Marshal]::GetLastWin32Error()
                        Write-Output "Unable to signal scrcpy with Ctrl+Break. Win32 error: ${'$'}errorCode"
                        ${'$'}child.Kill()
                        break
                    }
                    ${'$'}stopSent = ${'$'}true
                    ${'$'}stopDeadline = (Get-Date).AddSeconds(15)
                }
                if (${'$'}stopSent -and ${'$'}stopDeadline -ne ${'$'}null -and (Get-Date) -gt ${'$'}stopDeadline) {
                    if (-not ${'$'}child.HasExited) {
                        ${'$'}child.Kill()
                    }
                    break
                }
                Start-Sleep -Milliseconds 100
            }
            ${'$'}child.WaitForExit()
            exit ${'$'}child.ExitCode
        } finally {
            [AndroidTestHelperScrcpyProcessControl]::CloseHandle(${'$'}processInfo.hProcess) | Out-Null
            [AndroidTestHelperScrcpyProcessControl]::CloseHandle(${'$'}processInfo.hThread) | Out-Null
            Remove-Item -LiteralPath ${'$'}stopFile -ErrorAction SilentlyContinue
        }
    """.trimIndent()
}

internal fun buildWindowsCommandLine(executablePath: String, args: List<String>): String {
    return (listOf(executablePath) + args)
        .joinToString(" ") { it.toWindowsCommandLineArgument() }
}

internal fun String.toWindowsCommandLineArgument(): String {
    if (isEmpty()) return "\"\""
    val result = StringBuilder()
    result.append('"')
    var backslashCount = 0
    for (char in this) {
        when (char) {
            '\\' -> backslashCount += 1
            '"' -> {
                repeat(backslashCount * 2 + 1) { result.append('\\') }
                result.append('"')
                backslashCount = 0
            }
            else -> {
                repeat(backslashCount) { result.append('\\') }
                backslashCount = 0
                result.append(char)
            }
        }
    }
    repeat(backslashCount * 2) { result.append('\\') }
    result.append('"')
    return result.toString()
}

private fun String.toPowerShellSingleQuoted(): String {
    return "'${replace("'", "''")}'"
}

internal data class ScrcpyMirrorCommand(
    val scrcpyPath: String,
    val args: List<String>,
    val displayCommand: String,
)

internal fun buildScrcpyMirrorCommand(
    scrcpyPath: String,
    deviceSerial: String,
    windowTitle: String,
    maxSize: ScreenRecordMaxSize = ScreenRecordMaxSize.Original,
    bitRate: ScreenRecordBitRate = ScreenRecordBitRate.Default,
    maxFps: ScreenRecordMaxFps = ScreenRecordMaxFps.Default,
): ScrcpyMirrorCommand {
    val args = buildList {
        add("--serial=$deviceSerial")
        add("--no-audio")
        maxSize.scrcpyArg?.let { add(it) }
        bitRate.scrcpyArg?.let { add(it) }
        maxFps.scrcpyArg?.let { add(it) }
        add("--window-title=$windowTitle")
    }
    return ScrcpyMirrorCommand(
        scrcpyPath = scrcpyPath,
        args = args,
        displayCommand = buildScrcpyDisplayCommand(scrcpyPath, args),
    )
}

private fun String.toDisplayCommandToken(): String {
    return replace("\"", "\\\"")
}

private data class ScreenRecordCommandOutcome(
    val endState: ScreenRecordEndState,
    val message: String? = null,
)

private fun shouldTryNextScreenRecordPath(message: String?): Boolean {
    val lowerMessage = message?.lowercase() ?: return false
    return lowerMessage.contains("permission denied") ||
        lowerMessage.contains("unable to open")
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
