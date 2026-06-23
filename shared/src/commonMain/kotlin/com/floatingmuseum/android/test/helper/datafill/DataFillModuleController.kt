package com.floatingmuseum.android.test.helper.datafill

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.floatingmuseum.android.test.helper.AndroidDevice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class DataFillModuleController(
    private val dataFillAdb: DataFillAdb,
    private val scope: CoroutineScope,
    private val getSelectedReadyDevice: () -> AndroidDevice?,
    private val isRunning: () -> Boolean,
    private val setRunning: (Boolean) -> Unit,
    private val setStatusText: (String) -> Unit,
    private val appendCommand: (String) -> Unit,
) {
    var storageInfo by mutableStateOf<StorageInfo?>(null)
        private set
    var customFillValue by mutableStateOf("")
    var remainingValue by mutableStateOf("")
    var fillProgress by mutableStateOf<FillProgress?>(null)
        private set

    private var runningJob by mutableStateOf<Job?>(null)

    fun clearDeviceState() {
        storageInfo = null
        fillProgress = null
    }

    fun stopFill() {
        runningJob?.cancel()
    }

    fun refreshStorageForSelectedDevice() {
        val deviceSerial = getSelectedReadyDevice()?.serialNumber
        if (deviceSerial == null) {
            setStatusText("先选择状态为 device 的设备")
            appendCommand("错误: 先选择状态为 device 的设备")
        } else {
            refreshStorageForDevice(deviceSerial)
        }
    }

    fun refreshStorageForDevice(deviceSerial: String) {
        runAdbTask("读取平板存储") {
            dataFillAdb.loadStorageInfo(deviceSerial, appendCommand)
        }
    }

    suspend fun loadStorageInfoForDeviceScan(deviceSerial: String) {
        storageInfo = dataFillAdb.loadStorageInfo(deviceSerial, appendCommand)
    }

    fun fillFixed(sizeBytes: Long) {
        val deviceSerial = getSelectedReadyDevice()?.serialNumber
        if (deviceSerial != null) {
            runAdbTask("填充 ${formatBytes(sizeBytes)}") {
                dataFillAdb.fillSize(deviceSerial, sizeBytes, appendCommand) { progress ->
                    fillProgress = progress
                }
            }
        }
    }

    fun fillCustom() {
        val deviceSerial = getSelectedReadyDevice()?.serialNumber
        val sizeBytes = parseGiBInput(customFillValue)
        if (deviceSerial != null && sizeBytes != null) {
            runAdbTask("填充 ${formatBytes(sizeBytes)}") {
                dataFillAdb.fillSize(deviceSerial, sizeBytes, appendCommand) { progress ->
                    fillProgress = progress
                }
            }
        } else {
            setStatusText("请输入有效的填充大小")
            appendCommand("错误: 请输入有效的填充大小")
        }
    }

    fun fillUntilRemaining() {
        val deviceSerial = getSelectedReadyDevice()?.serialNumber
        val targetBytes = parseGiBInput(remainingValue)
        if (deviceSerial != null && targetBytes != null) {
            runAdbTask("填充到剩余 ${formatBytes(targetBytes)}") {
                dataFillAdb.fillUntilRemaining(
                    deviceSerial = deviceSerial,
                    targetAvailableBytes = targetBytes,
                    logCommand = appendCommand,
                    onStorageProgress = { storage ->
                        storageInfo = storage
                    },
                    onFillProgress = { progress ->
                        fillProgress = progress
                    },
                )
            }
        } else {
            setStatusText("请输入有效的剩余空间")
            appendCommand("错误: 请输入有效的剩余空间")
        }
    }

    private fun runAdbTask(name: String, block: suspend () -> StorageInfo) {
        if (isRunning()) return
        val job = scope.launch {
            setRunning(true)
            fillProgress = null
            setStatusText("$name...")
            appendCommand("状态: 开始$name...")
            try {
                storageInfo = block()
                setStatusText("$name 完成")
                appendCommand("状态: $name 完成")
            } catch (error: CancellationException) {
                fillProgress = null
                val deviceSerial = getSelectedReadyDevice()?.serialNumber
                if (deviceSerial == null) {
                    setStatusText("任务已停止")
                    appendCommand("状态: 任务已停止")
                } else {
                    setStatusText("任务已停止，刷新存储...")
                    appendCommand("状态: 任务已停止，刷新存储...")
                    try {
                        storageInfo = withContext(NonCancellable) {
                            dataFillAdb.loadStorageInfo(deviceSerial, appendCommand)
                        }
                        setStatusText("任务已停止，已刷新存储")
                        appendCommand("状态: 任务已停止，已刷新存储")
                    } catch (refreshError: Throwable) {
                        setStatusText("任务已停止，刷新存储失败：${refreshError.message ?: "未知错误"}")
                        appendCommand("错误: 刷新存储失败 - ${refreshError.message ?: "未知错误"}")
                    }
                }
            } catch (error: Throwable) {
                setStatusText(error.message ?: "任务失败")
                appendCommand("错误: $name 失败 - ${error.message ?: "未知错误"}")
            } finally {
                setRunning(false)
                runningJob = null
            }
        }
        runningJob = job
    }
}

@Composable
internal fun rememberDataFillModuleController(
    scope: CoroutineScope,
    getSelectedReadyDevice: () -> AndroidDevice?,
    isRunning: () -> Boolean,
    setRunning: (Boolean) -> Unit,
    setStatusText: (String) -> Unit,
    appendCommand: (String) -> Unit,
): DataFillModuleController {
    val dataFillAdb = remember { createDataFillAdb() }
    return remember {
        DataFillModuleController(
            dataFillAdb = dataFillAdb,
            scope = scope,
            getSelectedReadyDevice = getSelectedReadyDevice,
            isRunning = isRunning,
            setRunning = setRunning,
            setStatusText = setStatusText,
            appendCommand = appendCommand,
        )
    }
}

@Composable
internal fun DataFillModuleContent(
    controller: DataFillModuleController,
    isRunning: Boolean,
    hasReadyDevice: Boolean,
    modifier: Modifier = Modifier,
) {
    DataFillTestPanel(
        storageInfo = controller.storageInfo,
        customFillValue = controller.customFillValue,
        onCustomFillValueChange = { controller.customFillValue = it },
        remainingValue = controller.remainingValue,
        onRemainingValueChange = { controller.remainingValue = it },
        isRunning = isRunning,
        hasReadyDevice = hasReadyDevice,
        fillProgress = controller.fillProgress,
        onRefresh = controller::refreshStorageForSelectedDevice,
        onStopFill = controller::stopFill,
        onFillFixed = controller::fillFixed,
        onFillCustom = controller::fillCustom,
        onFillUntilRemaining = controller::fillUntilRemaining,
        modifier = modifier,
    )
}
