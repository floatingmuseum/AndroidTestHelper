package com.floatingmuseum.android.test.helper.devicelog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.floatingmuseum.android.test.helper.AndroidDevice
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
            setStatusText("先选择状态为 device 的设备")
            appendCommand("错误: 先选择状态为 device 的设备")
            return
        }

        val job = scope.launch {
            progress = null
            lastResult = null
            capturingDeviceLabel = "${device.model} · ${device.serialNumber}"
            setStatusText("正在抓取 Logcat...")
            appendCommand("状态: 开始抓取设备 ${device.serialNumber} Logcat")
            try {
                val result = deviceLogAdb.captureFullLogs(
                    deviceSerial = device.serialNumber,
                    deviceModel = device.model,
                    logCommand = appendCommand,
                    onProgress = { nextProgress ->
                        progress = nextProgress
                        setStatusText(
                            "抓取 Logcat：${nextProgress.currentSection} " +
                                "${nextProgress.completedSections}/${nextProgress.totalSections}"
                        )
                    },
                )
                progress = null
                lastResult = result
                when (result.endState) {
                    DeviceLogCaptureEndState.COMPLETED -> {
                        setStatusText("Logcat 抓取完成：${result.filePath}")
                        appendCommand("状态: Logcat 抓取完成 - ${result.filePath}")
                    }
                    DeviceLogCaptureEndState.STOPPED -> {
                        setStatusText("Logcat 已停止，日志已保存：${result.filePath}")
                        appendCommand("状态: Logcat 已停止，日志已保存 - ${result.filePath}")
                    }
                    DeviceLogCaptureEndState.INTERRUPTED -> {
                        setStatusText("Logcat 意外中止，日志已保存：${result.filePath}")
                        appendCommand("状态: Logcat 意外中止，日志已保存 - ${result.filePath}")
                    }
                }
            } catch (error: CancellationException) {
                progress = null
                capturingDeviceLabel = null
                setStatusText("Logcat 抓取已停止")
                appendCommand("状态: Logcat 抓取已停止")
            } catch (error: Throwable) {
                progress = null
                capturingDeviceLabel = null
                setStatusText(error.message ?: "Logcat 抓取失败")
                appendCommand("错误: Logcat 抓取失败 - ${error.message ?: "未知错误"}")
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
            setStatusText("已打开日志所在目录")
            appendCommand("状态: 已打开日志所在目录 - $filePath")
        } else {
            setStatusText("无法打开日志所在目录")
            appendCommand("错误: 无法打开日志所在目录 - $filePath")
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
