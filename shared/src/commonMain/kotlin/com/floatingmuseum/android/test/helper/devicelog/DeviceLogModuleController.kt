package com.floatingmuseum.android.test.helper.devicelog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.floatingmuseum.android.test.helper.AndroidDevice
import com.floatingmuseum.android.test.helper.localization.commandError
import com.floatingmuseum.android.test.helper.localization.commandStatus
import com.floatingmuseum.android.test.helper.localization.localized
import com.floatingmuseum.android.test.helper.localization.unknownError
import com.floatingmuseum.android.test.helper.revealFileInDirectory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class DeviceLogModuleController(
    private val deviceLogAdb: DeviceLogAdb,
    private val scope: CoroutineScope,
    private val getSelectedReadyDevice: () -> AndroidDevice?,
    private val setStatusText: (String) -> Unit,
    private val appendCommand: (String) -> Unit,
) {
    var progress by mutableStateOf<DeviceLogCaptureProgress?>(null)
        private set
    var lastResult by mutableStateOf<DeviceLogCaptureResult?>(null)
        private set
    var capturingDeviceLabel by mutableStateOf<String?>(null)
        private set

    private var deviceLogJob by mutableStateOf<Job?>(null)

    val isCapturing: Boolean
        get() = deviceLogJob != null

    fun clearIfIdle() {
        if (!isCapturing) {
            progress = null
            lastResult = null
            capturingDeviceLabel = null
        }
    }

    fun captureSelectedDeviceLogs() {
        if (isCapturing) return
        val device = getSelectedReadyDevice()
        if (device == null) {
            val message = localized("auto.select_a_device_in_device_state_first.cf5bc374")
            setStatusText(message)
            appendCommand(commandError(message))
            return
        }

        val job = scope.launch {
            progress = null
            lastResult = null
            capturingDeviceLabel = "${device.model} · ${device.serialNumber}"
            setStatusText(localized("auto.capturing_logcat.835215be"))
            appendCommand(commandStatus(localized("auto.start_capturing_logcat_from_device_0.9b881a13", device.serialNumber)))
            try {
                val result = deviceLogAdb.captureFullLogs(
                    deviceSerial = device.serialNumber,
                    deviceModel = device.model,
                    logCommand = appendCommand,
                    onProgress = { nextProgress ->
                        progress = nextProgress
                        setStatusText(
                            localized("auto.capturing_logcat_0_1_2.90692351", nextProgress.currentSection, nextProgress.completedSections, nextProgress.totalSections)
                        )
                    },
                )
                progress = null
                lastResult = result
                when (result.endState) {
                    DeviceLogCaptureEndState.COMPLETED -> {
                        setStatusText(localized("auto.logcat_capture_completed_0.13468776", result.filePath))
                        appendCommand(commandStatus(localized("auto.logcat_capture_completed.91af5080") + " - ${result.filePath}"))
                    }
                    DeviceLogCaptureEndState.STOPPED -> {
                        setStatusText(localized("auto.logcat_stopped_log_saved_0.4925a4c0", result.filePath))
                        appendCommand(commandStatus(localized("auto.logcat_stopped_log_saved.ac395a7f") + " - ${result.filePath}"))
                    }
                    DeviceLogCaptureEndState.INTERRUPTED -> {
                        setStatusText(localized("auto.logcat_interrupted_log_saved_0.20a89368", result.filePath))
                        appendCommand(commandStatus(localized("auto.logcat_interrupted_log_saved.2412026f") + " - ${result.filePath}"))
                    }
                }
            } catch (error: CancellationException) {
                progress = null
                capturingDeviceLabel = null
                val message = localized("auto.logcat_capture_stopped.98f70b20")
                setStatusText(message)
                appendCommand(commandStatus(message))
            } catch (error: Throwable) {
                progress = null
                capturingDeviceLabel = null
                setStatusText(error.message ?: localized("auto.logcat_capture_failed.7ff0923b"))
                appendCommand(commandError(localized("auto.logcat_capture_failed.7ff0923b") + " - ${error.message ?: unknownError()}"))
            } finally {
                deviceLogJob = null
                capturingDeviceLabel = null
            }
        }
        deviceLogJob = job
    }

    fun stopCapture() {
        deviceLogAdb.stopCurrentCapture()
    }

    fun revealLogFile(filePath: String) {
        val opened = revealFileInDirectory(filePath)
        if (opened) {
            val message = localized("auto.opened_log_directory.331d7b15")
            setStatusText(message)
            appendCommand(commandStatus("$message - $filePath"))
        } else {
            val message = localized("auto.unable_to_open_log_directory.411eddb7")
            setStatusText(message)
            appendCommand(commandError("$message - $filePath"))
        }
    }
}

@Composable
internal fun rememberDeviceLogModuleController(
    scope: CoroutineScope,
    getSelectedReadyDevice: () -> AndroidDevice?,
    setStatusText: (String) -> Unit,
    appendCommand: (String) -> Unit,
): DeviceLogModuleController {
    val deviceLogAdb = remember { createDeviceLogAdb() }
    return remember {
        DeviceLogModuleController(
            deviceLogAdb = deviceLogAdb,
            scope = scope,
            getSelectedReadyDevice = getSelectedReadyDevice,
            setStatusText = setStatusText,
            appendCommand = appendCommand,
        )
    }
}
