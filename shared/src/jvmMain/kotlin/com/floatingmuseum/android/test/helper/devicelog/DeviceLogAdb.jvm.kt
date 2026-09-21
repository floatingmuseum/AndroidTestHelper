package com.floatingmuseum.android.test.helper.devicelog

import com.floatingmuseum.android.test.helper.AppRuntimePaths
import com.floatingmuseum.android.test.helper.adb.AdbShell
import com.floatingmuseum.android.test.helper.localization.commandStatus
import com.floatingmuseum.android.test.helper.localization.localized
import java.io.BufferedWriter
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

actual fun createDeviceLogAdb(): DeviceLogAdb = JvmDeviceLogAdb()

private val DeviceLogTimestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")

internal class JvmDeviceLogAdb(
    private val logsDirectory: () -> File = { AppRuntimePaths.logsDirectory() },
    private val startLogcat: (List<String>) -> Process = { args ->
        ProcessBuilder(listOf(AdbShell.adbPath) + args).redirectErrorStream(true).start()
    },
    private val readProcessNames: suspend (String, (String) -> Unit) -> Map<String, String> = { serial, log ->
        parseLogProcessNames(
            AdbShell.executeAdb(
                listOf("-s", serial, "shell", "ps", "-A", "-o", "PID,NAME"),
                "adb -s $serial shell ps -A -o PID,NAME",
                log,
            ),
        )
    },
) : DeviceLogAdb {
    @Volatile private var activeProcess: Process? = null
    @Volatile private var stopRequested = false

    override suspend fun captureFullLogs(
        deviceSerial: String,
        deviceModel: String,
        filter: LogKeywordFilter,
        logCommand: (String) -> Unit,
        onProgress: (DeviceLogCaptureProgress) -> Unit,
        customCommand: CustomLogCommand?,
    ): DeviceLogCaptureResult {
        stopRequested = false
        val normalizedFilter = filter.normalized()
        val matcher = LogKeywordMatcher(normalizedFilter)
        val command = buildLogcatAdbCommand(deviceSerial, customCommand)
        val capturedAt = LocalDateTime.now()
        val capturedLines = AtomicLong()
        val matchedLines = AtomicLong()
        val callbackContext = currentCoroutineContext().minusKey(Job)
        fun progress() = DeviceLogCaptureProgress(capturedLines.get(), matchedLines.get(), !matcher.isEmpty)
        suspend fun reportProgress() = withContext(callbackContext) { onProgress(progress()) }
        suspend fun processNames() = withContext(callbackContext) { loadProcessNames(deviceSerial, logCommand) }
        onProgress(progress())

        return withContext(Dispatchers.IO) {
            val directory = logsDirectory().absoluteFile
            check(directory.isDirectory || directory.mkdirs()) {
                localized("log.unable_to_create_log_output_directory_arg0", directory.absolutePath)
            }
            val outputFile = uniqueLogFile(directory, buildDeviceLogFileName(deviceModel, deviceSerial, capturedAt))
            val filteredFile = if (matcher.isEmpty) null else directory.resolve(outputFile.nameWithoutExtension + "_filtered.log")
            var endState = DeviceLogCaptureEndState.STOPPED
            var endMessage: String? = null
            val names = AtomicReference(if (command.studioFormat) processNames() else emptyMap())
            outputFile.bufferedWriter(Charsets.UTF_8).use { fullWriter ->
                filteredFile?.bufferedWriter(Charsets.UTF_8).use { filteredWriter ->
                    writeHeader(fullWriter, deviceSerial, deviceModel, capturedAt, command, null)
                    filteredWriter?.let { writeHeader(it, deviceSerial, deviceModel, capturedAt, command, normalizedFilter) }
                    if (!stopRequested) {
                        withContext(callbackContext) { logCommand(command.displayCommand) }
                        val process = startLogcat(command.args)
                        activeProcess = process
                        // Stop may have arrived while the process was being created.
                        if (stopRequested) process.destroyForcibly()
                        val outputReader = async(Dispatchers.IO) {
                            // Keep stream-close errors local so an explicit stop still returns both file paths.
                            runCatching {
                                process.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                                    var lastFlush = System.nanoTime()
                                    lines.forEach { raw ->
                                        currentCoroutineContext().ensureActive()
                                        val entry = if (command.studioFormat) parseThreadtimeLogLine(raw) else null
                                        val line = entry?.format(names.get()[entry.pid]) ?: raw
                                        fullWriter.appendLine(line)
                                        if (!raw.startsWith("---------") && raw.isNotBlank()) {
                                            capturedLines.incrementAndGet()
                                            if (filteredWriter != null && matcher.matches(line)) {
                                                filteredWriter.appendLine(line)
                                                matchedLines.incrementAndGet()
                                            }
                                        } else {
                                            filteredWriter?.appendLine(raw)
                                        }
                                        if (System.nanoTime() - lastFlush >= TimeUnit.SECONDS.toNanos(1)) {
                                            fullWriter.flush()
                                            filteredWriter?.flush()
                                            lastFlush = System.nanoTime()
                                        }
                                    }
                                }
                            }
                        }
                        val nameUpdater = launch {
                            // Unsupported ps must not stop logcat or cause repeated failed commands.
                            if (names.get().isNotEmpty()) {
                                while (true) {
                                    delay(5_000)
                                    names.set(processNames())
                                }
                            }
                        }
                        try {
                            while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                                currentCoroutineContext().ensureActive()
                                if (outputReader.isCompleted && !stopRequested) outputReader.await().getOrThrow()
                                reportProgress()
                            }
                            val readResult = outputReader.await()
                            if (!stopRequested) readResult.getOrThrow()
                            endState = when {
                                stopRequested -> DeviceLogCaptureEndState.STOPPED
                                command.completesOnExit && process.exitValue() == 0 -> DeviceLogCaptureEndState.COMPLETED
                                else -> DeviceLogCaptureEndState.INTERRUPTED
                            }
                            endMessage = when (endState) {
                                DeviceLogCaptureEndState.STOPPED -> localized("log.user_stopped_capture")
                                DeviceLogCaptureEndState.COMPLETED -> localized("log.logcat_capture_completed")
                                DeviceLogCaptureEndState.INTERRUPTED -> localized("log.capture.interrupted_exit_code", process.exitValue())
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            endState = if (stopRequested) DeviceLogCaptureEndState.STOPPED else DeviceLogCaptureEndState.INTERRUPTED
                            endMessage = if (stopRequested) localized("log.user_stopped_capture") else
                                localized("log.logcat_stopped_unexpectedly") + ": " + error.message
                        } finally {
                            process.destroyForcibly()
                            withContext(NonCancellable) {
                                outputReader.cancelAndJoin()
                                nameUpdater.cancelAndJoin()
                            }
                            if (activeProcess === process) activeProcess = null
                        }
                    }
                    val footer = "===== CAPTURE ${endState.name} ${LocalDateTime.now()} ====="
                    fullWriter.appendLine(footer)
                    filteredWriter?.appendLine(footer)
                    endMessage?.let {
                        fullWriter.appendLine(it)
                        filteredWriter?.appendLine(it)
                    }
                }
            }
            reportProgress()
            withContext(callbackContext) { endMessage?.let { logCommand(commandStatus(it)) } }
            DeviceLogCaptureResult(
                fileName = outputFile.name,
                filePath = outputFile.absolutePath,
                directoryPath = directory.absolutePath,
                capturedLines = capturedLines.get(),
                matchedLines = matchedLines.get(),
                filteredFilePath = filteredFile?.absolutePath,
                filter = normalizedFilter,
                endState = endState,
                message = endMessage,
            )
        }
    }

    override fun stopCurrentCapture() {
        stopRequested = true
        activeProcess?.destroyForcibly()
    }

    private suspend fun loadProcessNames(serial: String, logCommand: (String) -> Unit): Map<String, String> {
        return try {
            withTimeoutOrNull(2_000) { readProcessNames(serial, logCommand) } ?: emptyMap()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyMap()
        }
    }
}

private fun writeHeader(
    writer: BufferedWriter,
    serial: String,
    model: String,
    capturedAt: LocalDateTime,
    command: LogcatAdbCommand,
    filter: LogKeywordFilter?,
) {
    writer.appendLine("AndroidTestHelper device log")
    writer.appendLine("CapturedAt: $capturedAt")
    writer.appendLine("DeviceModel: $model")
    writer.appendLine("DeviceSerial: $serial")
    writer.appendLine("Command: ${command.displayCommand}")
    if (filter != null) {
        writer.appendLine("Keywords (OR, literal): ${filter.query}")
        writer.appendLine("MatchCase: ${filter.matchCase}")
    }
    if (command.studioFormat) {
        writer.appendLine("Columns: Date Time PID-TID Tag Process Priority Message")
        writer.appendLine("Process names: current device snapshot, refreshed every 5 seconds; '-' when unavailable. Historical PID names may differ.")
    } else {
        writer.appendLine("Format: original logcat text output from the custom command")
    }
    writer.appendLine()
    writer.flush()
}

internal fun buildDeviceLogFileName(deviceModel: String, deviceSerial: String, capturedAt: LocalDateTime): String {
    val model = deviceModel.toDeviceLogFileToken().ifBlank { "unknown_model" }
    val serial = deviceSerial.toDeviceLogFileToken().ifBlank { "unknown_serial" }
    return "${model}_${serial}_${capturedAt.format(DeviceLogTimestampFormatter)}.log"
}

private fun uniqueLogFile(directory: File, name: String): File {
    var candidate = directory.resolve(name)
    var suffix = 1
    while (candidate.exists() || directory.resolve(candidate.nameWithoutExtension + "_filtered.log").exists()) {
        candidate = directory.resolve(name.removeSuffix(".log") + "_${suffix++}.log")
    }
    return candidate
}

private fun String.toDeviceLogFileToken(): String = trim().replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").trim('_')
