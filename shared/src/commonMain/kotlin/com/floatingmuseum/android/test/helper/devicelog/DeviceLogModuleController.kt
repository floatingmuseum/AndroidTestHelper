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
    private val presetRepository: LogCommandPresetRepository,
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

    var currentCommandPreset by mutableStateOf(defaultLogCommandPreset())
        private set
    var savedCommandPresets by mutableStateOf(presetRepository.loadPresets())
        private set
    var isCommandEditorOpen by mutableStateOf(false)
        private set
    var editorCommandName by mutableStateOf("")
        private set
    var editorCommandNameHasError by mutableStateOf(false)
        private set
    var editorCommandParts by mutableStateOf(defaultLogCommandPreset().parts)
        private set
    var editorFilterTag by mutableStateOf("")
        private set
    var editorFilterPriority by mutableStateOf("D")
        private set
    var editorRegex by mutableStateOf("")
        private set
    var editorPid by mutableStateOf("")
        private set
    var editorMaxCount by mutableStateOf("")
        private set
    var editorRecentValue by mutableStateOf("")
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
                    commandPreset = currentCommandPreset,
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

    fun openCommandEditor() {
        editorCommandName = ""
        editorCommandNameHasError = false
        editorCommandParts = emptyList()
        editorFilterTag = ""
        editorFilterPriority = "D"
        editorRegex = ""
        editorPid = ""
        editorMaxCount = ""
        editorRecentValue = ""
        isCommandEditorOpen = true
    }

    fun closeCommandEditor() {
        isCommandEditorOpen = false
    }

    fun updateEditorCommandName(value: String) {
        editorCommandName = value
        if (value.isNotBlank()) {
            editorCommandNameHasError = false
        }
    }

    fun updateEditorFilterTag(value: String) {
        editorFilterTag = value
    }

    fun updateEditorFilterPriority(value: String) {
        editorFilterPriority = value
    }

    fun updateEditorRegex(value: String) {
        editorRegex = value
    }

    fun updateEditorPid(value: String) {
        editorPid = value
    }

    fun updateEditorMaxCount(value: String) {
        editorMaxCount = value
    }

    fun updateEditorRecentValue(value: String) {
        editorRecentValue = value
    }

    fun addEditorCommandPart(part: LogCommandPart) {
        val normalizedPart = part.normalizedOrNull()
        if (normalizedPart == null) {
            val message = localized("log.command.part_empty_error")
            setStatusText(message)
            appendCommand(commandError(message))
            return
        }
        editorCommandParts = addLogCommandPart(editorCommandParts, normalizedPart)
    }

    fun removeEditorCommandPart(index: Int) {
        editorCommandParts = editorCommandParts.filterIndexed { partIndex, _ -> partIndex != index }
    }

    fun addEditorFilterPart() {
        val tag = editorFilterTag.trim()
        if (tag.isBlank()) {
            val message = localized("log.command.filter_empty_error")
            setStatusText(message)
            appendCommand(commandError(message))
            return
        }
        val priority = editorFilterPriority.trim().uppercase().takeIf { it.isNotBlank() } ?: "D"
        addEditorCommandPart(LogCommandPart(LogCommandPartType.Filter, "$tag:$priority"))
        editorFilterTag = ""
    }

    fun addEditorRegexPart() {
        addTextPart(LogCommandPartType.Regex, editorRegex, "log.command.regex_empty_error") {
            editorRegex = ""
        }
    }

    fun addEditorPidPart() {
        addTextPart(LogCommandPartType.Pid, editorPid, "log.command.pid_empty_error") {
            editorPid = ""
        }
    }

    fun addEditorMaxCountPart() {
        addTextPart(LogCommandPartType.MaxCount, editorMaxCount, "log.command.max_count_empty_error") {
            editorMaxCount = ""
        }
    }

    fun addEditorRecentPart() {
        addTextPart(LogCommandPartType.Recent, editorRecentValue, "log.command.recent_empty_error") {
            editorRecentValue = ""
        }
    }

    fun saveEditorCommandPreset(applyAfterSave: Boolean) {
        if (editorCommandName.isBlank()) {
            editorCommandNameHasError = true
            val message = localized("log.command.name_required")
            setStatusText(message)
            appendCommand(commandError(message))
            return
        }
        val preset = createSavedLogCommandPreset(
            id = nextPresetId(),
            name = editorCommandName,
            sourcePreset = LogCommandPreset(
                id = "editor-logcat",
                name = editorCommandName,
                parts = editorCommandParts,
            ),
        )
        if (preset == null) {
            editorCommandNameHasError = true
            val message = localized("log.command.save_validation")
            setStatusText(message)
            appendCommand(commandError(message))
            return
        }
        savedCommandPresets = listOf(preset) + savedCommandPresets
        presetRepository.savePresets(savedCommandPresets)
        if (applyAfterSave) {
            currentCommandPreset = preset
        }
        isCommandEditorOpen = false
        val message = localized("log.command.saved_arg0", preset.name)
        setStatusText(message)
        appendCommand(commandStatus(message))
    }

    fun applyCommandPreset(preset: LogCommandPreset) {
        currentCommandPreset = preset.normalized()
        val message = localized("log.command.applied_arg0", preset.name)
        setStatusText(message)
        appendCommand(commandStatus(message))
    }

    fun deleteCommandPreset(preset: LogCommandPreset) {
        savedCommandPresets = savedCommandPresets.filterNot { it.id == preset.id }
        presetRepository.savePresets(savedCommandPresets)
        val message = localized("log.command.deleted_arg0", preset.name)
        setStatusText(message)
        appendCommand(commandStatus(message))
    }

    fun restoreDefaultCommandPreset() {
        currentCommandPreset = defaultLogCommandPreset()
        val message = localized("log.command.restored_default")
        setStatusText(message)
        appendCommand(commandStatus(message))
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

    private fun nextPresetId(): String {
        val token = kotlin.random.Random.nextInt(0, Int.MAX_VALUE)
        return "log-command-$token"
    }

    private fun addTextPart(
        type: LogCommandPartType,
        value: String,
        errorKey: String,
        onAdded: () -> Unit,
    ) {
        val normalizedValue = value.trim()
        if (normalizedValue.isBlank()) {
            val message = localized(errorKey)
            setStatusText(message)
            appendCommand(commandError(message))
            return
        }
        addEditorCommandPart(LogCommandPart(type, normalizedValue))
        onAdded()
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
    val presetRepository = remember { createLogCommandPresetRepository() }
    return remember {
        DeviceLogModuleController(
            deviceLogAdb = deviceLogAdb,
            presetRepository = presetRepository,
            scope = scope,
            getSelectedReadyDevice = getSelectedReadyDevice,
            setStatusText = setStatusText,
            appendCommand = appendCommand,
        )
    }
}
