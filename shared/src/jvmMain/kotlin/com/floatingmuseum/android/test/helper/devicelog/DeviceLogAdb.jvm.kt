package com.floatingmuseum.android.test.helper.devicelog

import com.floatingmuseum.android.test.helper.AppRuntimePaths
import com.floatingmuseum.android.test.helper.adb.AdbShell
import com.floatingmuseum.android.test.helper.localization.commandStatus
import com.floatingmuseum.android.test.helper.localization.localized
import com.floatingmuseum.android.test.helper.localization.unknownError
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

actual fun createDeviceLogAdb(): DeviceLogAdb = JvmDeviceLogAdb()

private val DeviceLogTimestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

private class JvmDeviceLogAdb : DeviceLogAdb {
    @Volatile
    private var activeProcess: Process? = null

    @Volatile
    private var stopRequested: Boolean = false

    override suspend fun captureFullLogs(
        deviceSerial: String,
        deviceModel: String,
        logCommand: (String) -> Unit,
        onProgress: (DeviceLogCaptureProgress) -> Unit,
    ): DeviceLogCaptureResult {
        stopRequested = false
        val capturedAt = LocalDateTime.now()
        val directory = AppRuntimePaths.logsDirectory().absoluteFile
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException(localized("auto.unable_to_create_log_output_directory_0.a8520227", directory.absolutePath))
        }
        if (!directory.isDirectory) {
            throw IllegalStateException(localized("auto.log_output_path_is_not_a_directory_0.926786fb", directory.absolutePath))
        }

        val fileName = buildDeviceLogFileName(deviceModel, deviceSerial, capturedAt)
        val outputFile = directory.resolve(fileName)
        val sections = buildLogSections(deviceSerial)
        var completed = 0
        var endState = DeviceLogCaptureEndState.COMPLETED
        var endMessage: String? = null

        withContext(Dispatchers.IO) {
            outputFile.logWriter(append = false).use { writer ->
                writeHeader(writer, deviceSerial, deviceModel, capturedAt, sections.size)
            }
        }

        sections.forEach { section ->
            onProgress(DeviceLogCaptureProgress(section.title, completed, sections.size))
            val outcome = runSectionToFile(
                section = section,
                outputFile = outputFile,
                logCommand = logCommand,
            )
            if (outcome.endState == DeviceLogCaptureEndState.COMPLETED) {
                completed += 1
            } else {
                endState = outcome.endState
                endMessage = outcome.message
            }
            onProgress(DeviceLogCaptureProgress(section.title, completed, sections.size))
            if (outcome.endState != DeviceLogCaptureEndState.COMPLETED) {
                return@forEach
            }
        }

        withContext(Dispatchers.IO) {
            outputFile.logWriter(append = true).use { writer ->
                writer.appendLine()
                writer.appendLine("===== CAPTURE ${endState.name} ${LocalDateTime.now()} =====")
                endMessage?.let { writer.appendLine(it) }
            }
        }

        return DeviceLogCaptureResult(
            fileName = fileName,
            filePath = outputFile.absolutePath,
            directoryPath = directory.absolutePath,
            completedSections = completed,
            totalSections = sections.size,
            endState = endState,
            message = endMessage,
        )
    }

    override fun stopCurrentCapture() {
        stopRequested = true
        activeProcess?.destroy()
    }

    private suspend fun runSectionToFile(
        section: LogSection,
        outputFile: File,
        logCommand: (String) -> Unit,
    ): LogSectionOutcome {
        logCommand(section.displayCommand)
        var sectionFailureMessage: String? = null
        var outcome = LogSectionOutcome(DeviceLogCaptureEndState.COMPLETED)

        withContext(Dispatchers.IO) {
            outputFile.logWriter(append = true).use { writer ->
                writer.appendLine()
                writer.appendLine("===== ${section.title} =====")
                writer.appendLine("\$ ${section.displayCommand}")
                writer.flush()

                val process = ProcessBuilder(listOf(AdbShell.adbPath) + section.args)
                    .redirectErrorStream(true)
                    .start()
                activeProcess = process
                val outputReader = async(Dispatchers.IO) {
                    process.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                        lines.forEach { line ->
                            writer.appendLine(line)
                        }
                    }
                }
                try {
                    while (!process.waitFor(100L, TimeUnit.MILLISECONDS)) {
                        currentCoroutineContext().ensureActive()
                    }
                    outputReader.await()
                    val exitCode = process.exitValue()
                    if (exitCode != 0) {
                        writer.appendLine()
                        if (stopRequested) {
                            val message = localized("auto.user_stopped_capture_logcat_exited_with_code_0.0d4a96f8", exitCode)
                            writer.appendLine("[$message]")
                            outcome = LogSectionOutcome(
                                endState = DeviceLogCaptureEndState.STOPPED,
                                message = message,
                            )
                        } else {
                            val message = localized("auto.logcat_stopped_unexpectedly_possibly_because_the_dev.d9b1baa5", exitCode)
                            writer.appendLine("[$message]")
                            sectionFailureMessage = commandStatus(message)
                            outcome = LogSectionOutcome(
                                endState = DeviceLogCaptureEndState.INTERRUPTED,
                                message = message,
                            )
                        }
                    }
                } catch (error: CancellationException) {
                    process.destroyForcibly()
                    outputReader.cancel()
                    writer.appendLine()
                    writer.appendLine("[${localized("auto.capture_stopped.4833feb5")}]")
                    throw error
                } catch (error: Throwable) {
                    process.destroyForcibly()
                    outputReader.cancel()
                    writer.appendLine()
                    if (stopRequested) {
                        val message = localized("auto.user_stopped_capture.67a4fd80")
                        writer.appendLine("[$message]")
                        outcome = LogSectionOutcome(
                            endState = DeviceLogCaptureEndState.STOPPED,
                            message = message,
                        )
                    } else {
                        val message = localized("auto.logcat_stopped_unexpectedly.2184fdb9") + ": ${error.message ?: unknownError()}"
                        writer.appendLine("[$message]")
                        sectionFailureMessage = commandStatus(message)
                        outcome = LogSectionOutcome(
                            endState = DeviceLogCaptureEndState.INTERRUPTED,
                            message = message,
                        )
                    }
                } finally {
                    if (activeProcess === process) {
                        activeProcess = null
                    }
                    writer.flush()
                }
            }
        }

        sectionFailureMessage?.let(logCommand)
        return outcome
    }
}

private data class LogSectionOutcome(
    val endState: DeviceLogCaptureEndState,
    val message: String? = null,
)

private data class LogSection(
    val title: String,
    val args: List<String>,
    val displayCommand: String,
)

private fun buildLogSections(deviceSerial: String): List<LogSection> {
    fun adb(title: String, vararg args: String, display: String = "adb -s $deviceSerial ${args.joinToString(" ")}"): LogSection {
        return LogSection(
            title = title,
            args = listOf("-s", deviceSerial) + args,
            displayCommand = display,
        )
    }

    return listOf(
        adb(
            localized("auto.logcat_all_buffers.d7023d5b"),
            "shell",
            "logcat",
            "-b",
            "all",
            "-v",
            "threadtime",
            "-v",
            "year",
            "-v",
            "zone",
            "-v",
            "usec",
            "-v",
            "uid",
        ),
    )
}

private fun writeHeader(
    writer: BufferedWriter,
    deviceSerial: String,
    deviceModel: String,
    capturedAt: LocalDateTime,
    totalSections: Int,
) {
    writer.appendLine("AndroidTestHelper device log")
    writer.appendLine("CapturedAt: $capturedAt")
    writer.appendLine("DeviceModel: $deviceModel")
    writer.appendLine("DeviceSerial: $deviceSerial")
    writer.appendLine("SectionCount: $totalSections")
}

internal fun buildDeviceLogFileName(
    deviceModel: String,
    deviceSerial: String,
    capturedAt: LocalDateTime,
): String {
    val modelToken = deviceModel.toDeviceLogFileToken().ifBlank { "unknown_model" }
    val serialToken = deviceSerial.toDeviceLogFileToken().ifBlank { "unknown_serial" }
    val timestamp = capturedAt.format(DeviceLogTimestampFormatter)
    return "${modelToken}_${serialToken}_$timestamp.log"
}

private fun String.toDeviceLogFileToken(): String {
    return trim()
        .replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_")
        .trim('_')
}

private fun File.logWriter(append: Boolean): BufferedWriter {
    return FileOutputStream(this, append).bufferedWriter(Charsets.UTF_8)
}
