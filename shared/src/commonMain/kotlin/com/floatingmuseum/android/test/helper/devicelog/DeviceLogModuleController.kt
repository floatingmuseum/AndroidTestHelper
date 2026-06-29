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
            val message = localized("common.device.select_device_first")
            setStatusText(message)
            appendCommand(commandError(message))
            return
        }

        val job = scope.launch {
            progress = null
            lastResult = null
            capturingDeviceLabel = "${device.model} · ${device.serialNumber}"
            setStatusText(localized("log.capture.capturing_status"))
            appendCommand(commandStatus(localized("log.start_capturing_logcat_from_device_arg0", device.serialNumber)))
            try {
                val result = deviceLogAdb.captureFullLogs(
                    deviceSerial = device.transportId,
                    deviceModel = device.model,
                    logCommand = appendCommand,
                    onProgress = { nextProgress ->
                        progress = nextProgress
                        setStatusText(
                            localized("log.capturing_logcat_arg0_arg1_arg2", nextProgress.currentSection, nextProgress.completedSections, nextProgress.totalSections)
                        )
                    },
                )
                progress = null
                lastResult = result
                when (result.endState) {
                    DeviceLogCaptureEndState.COMPLETED -> {
                        setStatusText(localized("log.logcat_capture_completed_arg0", result.filePath))
                        appendCommand(commandStatus(localized("log.logcat_capture_completed") + " - ${result.filePath}"))
                    }
                    DeviceLogCaptureEndState.STOPPED -> {
                        setStatusText(localized("log.logcat_stopped_log_saved_arg0", result.filePath))
                        appendCommand(commandStatus(localized("log.logcat_stopped_log_saved") + " - ${result.filePath}"))
                    }
                    DeviceLogCaptureEndState.INTERRUPTED -> {
                        setStatusText(localized("log.logcat_interrupted_log_saved_arg0", result.filePath))
                        appendCommand(commandStatus(localized("log.logcat_interrupted_log_saved") + " - ${result.filePath}"))
                    }
                }
            } catch (error: CancellationException) {
                progress = null
                capturingDeviceLabel = null
                val message = localized("log.logcat_capture_stopped")
                setStatusText(message)
                appendCommand(commandStatus(message))
            } catch (error: Throwable) {
                progress = null
                capturingDeviceLabel = null
                setStatusText(error.message ?: localized("log.logcat_capture_failed"))
                appendCommand(commandError(localized("log.logcat_capture_failed") + " - ${error.message ?: unknownError()}"))
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
            val message = localized("log.opened_log_directory")
            setStatusText(message)
            appendCommand(commandStatus("$message - $filePath"))
        } else {
            val message = localized("log.unable_to_open_log_directory")
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
