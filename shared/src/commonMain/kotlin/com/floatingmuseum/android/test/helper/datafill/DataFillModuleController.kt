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
        val deviceSerial = getSelectedReadyDevice()?.serialNumber
        if (deviceSerial == null) {
            val message = localized("auto.select_a_device_in_device_state_first.cf5bc374")
            setStatusText(message)
            appendCommand(commandError(message))
        } else {
            refreshStorageForDevice(deviceSerial)
        }
    }

    fun refreshStorageForDevice(deviceSerial: String) {
        runAdbTask(localized("auto.read_tablet_storage.9a3ecb39")) {
            dataFillAdb.loadStorageInfo(deviceSerial, appendCommand)
        }
    }

    suspend fun loadStorageInfoForDeviceScan(deviceSerial: String) {
        storageInfo = dataFillAdb.loadStorageInfo(deviceSerial, appendCommand)
    }

    fun fillFixed(sizeBytes: Long) {
        val deviceSerial = getSelectedReadyDevice()?.serialNumber
        if (deviceSerial != null) {
            runAdbTask(localized("auto.fill_0.c4fa9280", formatBytes(sizeBytes))) {
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
            runAdbTask(localized("auto.fill_0.c4fa9280", formatBytes(sizeBytes))) {
                dataFillAdb.fillSize(deviceSerial, sizeBytes, appendCommand) { progress ->
                    fillProgress = progress
                }
            }
        } else {
            val message = localized("auto.enter_a_valid_fill_size.a897deb2")
            setStatusText(message)
            appendCommand(commandError(message))
        }
    }

    fun fillUntilRemaining() {
        val deviceSerial = getSelectedReadyDevice()?.serialNumber
        val targetBytes = parseGiBInput(remainingValue)
        if (deviceSerial != null && targetBytes != null) {
            runAdbTask(localized("auto.fill_until_0_remains.561cc5f9", formatBytes(targetBytes))) {
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
            val message = localized("auto.enter_valid_remaining_space.b8341939")
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
            appendCommand(commandStatus(localized("auto.start_0.7e1a56f0", name)))
            try {
                storageInfo = block()
                setStatusText(localized("auto.0_completed.ef9690e9", name))
                appendCommand(commandStatus(localized("auto.0_completed.ef9690e9", name)))
            } catch (error: CancellationException) {
                fillProgress = null
                val deviceSerial = getSelectedReadyDevice()?.serialNumber
                if (deviceSerial == null) {
                    val message = localized("auto.task_stopped.7d702bab")
                    setStatusText(message)
                    appendCommand(commandStatus(message))
                } else {
                    val message = localized("auto.task_stopped_refreshing_storage.70a471e2")
                    setStatusText(message)
                    appendCommand(commandStatus(message))
                    try {
                        storageInfo = withContext(NonCancellable) {
                            dataFillAdb.loadStorageInfo(deviceSerial, appendCommand)
                        }
                        val refreshedMessage = localized("auto.task_stopped_storage_refreshed.cafb2d5a")
                        setStatusText(refreshedMessage)
                        appendCommand(commandStatus(refreshedMessage))
                    } catch (refreshError: Throwable) {
                        setStatusText(
                            localized("auto.task_stopped_storage_refresh_failed_0.3c0353c5", refreshError.message ?: unknownError())
                        )
                        appendCommand(commandError(localized("auto.storage_refresh_failed.c3279cfb") + " - ${refreshError.message ?: unknownError()}"))
                    }
                }
            } catch (error: Throwable) {
                setStatusText(error.message ?: localized("auto.task_failed.94b9e504"))
                appendCommand(commandError(localized("auto.0_failed.7a359370", name) + " - ${error.message ?: unknownError()}"))
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
