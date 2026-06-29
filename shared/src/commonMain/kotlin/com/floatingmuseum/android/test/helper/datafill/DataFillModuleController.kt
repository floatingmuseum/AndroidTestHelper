package com.floatingmuseum.android.test.helper.datafill

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.floatingmuseum.android.test.helper.AndroidDevice
import com.floatingmuseum.android.test.helper.localization.commandError
import com.floatingmuseum.android.test.helper.localization.commandStatus
import com.floatingmuseum.android.test.helper.localization.localized
import com.floatingmuseum.android.test.helper.localization.unknownError
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
        val deviceSerial = getSelectedReadyDevice()?.transportId
        if (deviceSerial == null) {
            val message = localized("common.device.select_device_first")
            setStatusText(message)
            appendCommand(commandError(message))
        } else {
            refreshStorageForDevice(deviceSerial)
        }
    }

    fun refreshStorageForDevice(deviceSerial: String) {
        runAdbTask(localized("data_fill.read_tablet_storage")) {
            dataFillAdb.loadStorageInfo(deviceSerial, appendCommand)
        }
    }

    suspend fun loadStorageInfoForDeviceScan(deviceSerial: String) {
        storageInfo = dataFillAdb.loadStorageInfo(deviceSerial, appendCommand)
    }

    fun fillFixed(sizeBytes: Long) {
        val deviceSerial = getSelectedReadyDevice()?.transportId
        if (deviceSerial != null) {
            runAdbTask(localized("data_fill.fill_arg0", formatBytes(sizeBytes))) {
                dataFillAdb.fillSize(deviceSerial, sizeBytes, appendCommand) { progress ->
                    fillProgress = progress
                }
            }
        }
    }

    fun fillCustom() {
        val deviceSerial = getSelectedReadyDevice()?.transportId
        val sizeBytes = parseGiBInput(customFillValue)
        if (deviceSerial != null && sizeBytes != null) {
            runAdbTask(localized("data_fill.fill_arg0", formatBytes(sizeBytes))) {
                dataFillAdb.fillSize(deviceSerial, sizeBytes, appendCommand) { progress ->
                    fillProgress = progress
                }
            }
        } else {
            val message = localized("data_fill.enter_a_valid_fill_size")
            setStatusText(message)
            appendCommand(commandError(message))
        }
    }

    fun fillUntilRemaining() {
        val deviceSerial = getSelectedReadyDevice()?.transportId
        val targetBytes = parseGiBInput(remainingValue)
        if (deviceSerial != null && targetBytes != null) {
            runAdbTask(localized("data_fill.fill_until_arg0_remains", formatBytes(targetBytes))) {
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
            val message = localized("data_fill.enter_valid_remaining_space")
            setStatusText(message)
            appendCommand(commandError(message))
        }
    }

    private fun runAdbTask(name: String, block: suspend () -> StorageInfo) {
        if (isRunning()) return
        val job = scope.launch {
            setRunning(true)
            fillProgress = null
            setStatusText("$name...")
            appendCommand(commandStatus(localized("data_fill.start_arg0", name)))
            try {
                storageInfo = block()
                setStatusText(localized("data_fill.task.completed_with_name", name))
                appendCommand(commandStatus(localized("data_fill.task.completed_with_name", name)))
            } catch (error: CancellationException) {
                fillProgress = null
                val deviceSerial = getSelectedReadyDevice()?.transportId
                if (deviceSerial == null) {
                    val message = localized("data_fill.task_stopped")
                    setStatusText(message)
                    appendCommand(commandStatus(message))
                } else {
                    val message = localized("data_fill.task_stopped_refreshing_storage")
                    setStatusText(message)
                    appendCommand(commandStatus(message))
                    try {
                        storageInfo = withContext(NonCancellable) {
                            dataFillAdb.loadStorageInfo(deviceSerial, appendCommand)
                        }
                        val refreshedMessage = localized("data_fill.task_stopped_storage_refreshed")
                        setStatusText(refreshedMessage)
                        appendCommand(commandStatus(refreshedMessage))
                    } catch (refreshError: Throwable) {
                        setStatusText(
                            localized("data_fill.task_stopped_storage_refresh_failed_arg0", refreshError.message ?: unknownError())
                        )
                        appendCommand(commandError(localized("data_fill.storage_refresh_failed") + " - ${refreshError.message ?: unknownError()}"))
                    }
                }
            } catch (error: Throwable) {
                setStatusText(error.message ?: localized("data_fill.task_failed"))
                appendCommand(commandError(localized("shell.action.failed_with_name", name) + " - ${error.message ?: unknownError()}"))
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
