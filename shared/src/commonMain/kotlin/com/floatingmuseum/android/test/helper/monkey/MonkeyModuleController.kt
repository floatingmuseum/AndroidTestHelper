package com.floatingmuseum.android.test.helper.monkey

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

private const val MaxVisibleMonkeyOutputLines = 500

internal class MonkeyModuleController(
    private val monkeyAdb: MonkeyAdb,
    private val presetRepository: MonkeyPresetRepository,
    private val scope: CoroutineScope,
    private val getSelectedReadyDevice: () -> AndroidDevice?,
    private val isGlobalRunning: () -> Boolean,
    private val setGlobalRunning: (Boolean) -> Unit,
    private val setStatusText: (String) -> Unit,
    private val appendCommand: (String) -> Unit,
) {
    var form by mutableStateOf(MonkeyTestForm(seed = generateMonkeySeed().toString()))
        private set
    var presetName by mutableStateOf("")
        private set
    var presets by mutableStateOf(
        presetRepository.loadPresets().map { preset ->
            preset.copy(form = preset.form.withEffectiveSeed())
        },
    )
        private set
    var visibleOutputLines by mutableStateOf<List<String>>(emptyList())
        private set
    var lastResult by mutableStateOf<MonkeyRunResult?>(null)
        private set
    var runningDeviceLabel by mutableStateOf<String?>(null)
        private set

    private var runningJob by mutableStateOf<Job?>(null)

    val isRunning: Boolean get() = runningJob != null
    val validationResult: MonkeyValidationResult get() = validateMonkeyForm(form)

    fun updateForm(next: MonkeyTestForm) {
        form = next
    }

    fun updatePresetName(value: String) {
        presetName = value
    }

    fun regenerateSeed() {
        form = form.copy(seed = generateMonkeySeed().toString())
    }

    fun savePreset() {
        val name = presetName.trim()
        if (name.isBlank()) return
        val existing = presets.firstOrNull { it.name.equals(name, ignoreCase = true) }
        val preset = MonkeyPreset(
            id = existing?.id ?: "monkey-${generateMonkeySeed()}",
            name = name,
            form = form.normalized().withEffectiveSeed(),
        )
        presets = listOf(preset) + presets.filterNot { it.id == preset.id }
        presetRepository.savePresets(presets)
        presetName = name
        val message = localized("monkey.preset.saved_arg0", name)
        setStatusText(message)
        appendCommand(commandStatus(message))
    }

    fun applyPreset(preset: MonkeyPreset) {
        form = preset.form.withEffectiveSeed()
        presetName = preset.name
        val message = localized("monkey.preset.applied_arg0", preset.name)
        setStatusText(message)
        appendCommand(commandStatus(message))
    }

    fun deletePreset(preset: MonkeyPreset) {
        presets = presets.filterNot { it.id == preset.id }
        presetRepository.savePresets(presets)
        if (presetName.equals(preset.name, ignoreCase = true)) presetName = ""
        val message = localized("monkey.preset.deleted_arg0", preset.name)
        setStatusText(message)
        appendCommand(commandStatus(message))
    }

    fun startRun() {
        if (isRunning || isGlobalRunning()) return
        val device = getSelectedReadyDevice()
        if (device == null) {
            reportError(localized("common.device.select_device_first"))
            return
        }
        val validation = validateMonkeyForm(form)
        if (!validation.isValid) {
            reportError(localized("monkey.validation.failed"))
            return
        }

        val runForm = form.normalized()
        val job = scope.launch {
            setGlobalRunning(true)
            visibleOutputLines = emptyList()
            lastResult = null
            runningDeviceLabel = "${device.model} · ${device.serialNumber}"
            val startMessage = localized("monkey.running_on_device_arg0", device.serialNumber)
            setStatusText(startMessage)
            appendCommand(commandStatus(startMessage))
            try {
                val result = monkeyAdb.runMonkey(
                    deviceSerial = device.transportId,
                    deviceModel = device.model,
                    form = runForm,
                    logCommand = appendCommand,
                    onOutputLine = { line ->
                        visibleOutputLines = (visibleOutputLines + line).takeLast(MaxVisibleMonkeyOutputLines)
                    },
                )
                lastResult = result
                val message = localized(monkeyEndStateMessageKey(result.endState))
                setStatusText(message)
                if (result.endState in setOf(
                        MonkeyRunEndState.Interrupted,
                        MonkeyRunEndState.StopCleanupFailed,
                    )
                ) {
                    appendCommand(commandError("$message - ${result.reportFilePath}"))
                } else {
                    appendCommand(commandStatus("$message - ${result.reportFilePath}"))
                }
                result.cleanupMessage?.let { appendCommand(commandError(it)) }
            } catch (error: CancellationException) {
                val message = localized("monkey.stopped")
                setStatusText(message)
                appendCommand(commandStatus(message))
            } catch (error: Throwable) {
                val message = error.message ?: localized("monkey.failed")
                setStatusText(message)
                appendCommand(commandError(localized("monkey.failed") + " - ${error.message ?: unknownError()}"))
            } finally {
                runningJob = null
                runningDeviceLabel = null
                setGlobalRunning(false)
            }
        }
        runningJob = job
    }

    fun stopRun() {
        if (!isRunning) return
        val message = localized("monkey.stopping")
        setStatusText(message)
        appendCommand(commandStatus(message))
        monkeyAdb.stopCurrentRun()
    }

    fun dispose() {
        monkeyAdb.stopCurrentRun()
        runningJob?.cancel()
    }

    fun revealReport(filePath: String) {
        val opened = revealFileInDirectory(filePath)
        val key = if (opened) "monkey.report_opened" else "monkey.report_open_failed"
        val message = localized(key)
        setStatusText(message)
        appendCommand(if (opened) commandStatus("$message - $filePath") else commandError("$message - $filePath"))
    }

    private fun reportError(message: String) {
        setStatusText(message)
        appendCommand(commandError(message))
    }
}

@Composable
internal fun rememberMonkeyModuleController(
    scope: CoroutineScope,
    getSelectedReadyDevice: () -> AndroidDevice?,
    isGlobalRunning: () -> Boolean,
    setGlobalRunning: (Boolean) -> Unit,
    setStatusText: (String) -> Unit,
    appendCommand: (String) -> Unit,
): MonkeyModuleController {
    val monkeyAdb = remember { createMonkeyAdb() }
    val presetRepository = remember { createMonkeyPresetRepository() }
    return remember {
        MonkeyModuleController(
            monkeyAdb = monkeyAdb,
            presetRepository = presetRepository,
            scope = scope,
            getSelectedReadyDevice = getSelectedReadyDevice,
            isGlobalRunning = isGlobalRunning,
            setGlobalRunning = setGlobalRunning,
            setStatusText = setStatusText,
            appendCommand = appendCommand,
        )
    }
}

private fun MonkeyTestForm.withEffectiveSeed(): MonkeyTestForm {
    return if (seed.toLongOrNull() == null) copy(seed = generateMonkeySeed().toString()) else this
}

internal fun monkeyEndStateMessageKey(endState: MonkeyRunEndState): String = when (endState) {
    MonkeyRunEndState.Completed -> "monkey.completed"
    MonkeyRunEndState.CompletedWithIncidents -> "monkey.completed_with_incidents"
    MonkeyRunEndState.AbortedByIncident -> "monkey.aborted_by_incident"
    MonkeyRunEndState.Stopped -> "monkey.stopped"
    MonkeyRunEndState.Interrupted -> "monkey.interrupted"
    MonkeyRunEndState.StopCleanupFailed -> "monkey.stop_cleanup_failed"
}
