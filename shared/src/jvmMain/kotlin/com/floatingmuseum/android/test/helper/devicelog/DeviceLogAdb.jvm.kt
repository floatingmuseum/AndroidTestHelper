package com.floatingmuseum.android.test.helper.devicelog

import com.floatingmuseum.android.test.helper.adb.AdbShell
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
        val directory = File(System.getProperty("user.home"), "AndroidTestHelperLogs").absoluteFile
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("无法创建日志保存目录：${directory.absolutePath}")
        }
        if (!directory.isDirectory) {
            throw IllegalStateException("日志保存路径不是目录：${directory.absolutePath}")
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
                            writer.appendLine("[用户停止采集，logcat 进程退出码: $exitCode]")
                            outcome = LogSectionOutcome(
                                endState = DeviceLogCaptureEndState.STOPPED,
                                message = "用户停止采集，logcat 进程退出码: $exitCode",
                            )
                        } else {
                            val message = "Logcat 意外中止，可能是设备断开连接，logcat 进程退出码: $exitCode"
                            writer.appendLine("[$message]")
                            sectionFailureMessage = "状态: $message"
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
                    writer.appendLine("[采集已停止]")
                    throw error
                } catch (error: Throwable) {
                    process.destroyForcibly()
                    outputReader.cancel()
                    writer.appendLine()
                    if (stopRequested) {
                        writer.appendLine("[用户停止采集]")
                        outcome = LogSectionOutcome(
                            endState = DeviceLogCaptureEndState.STOPPED,
                            message = "用户停止采集",
                        )
                    } else {
                        val message = "Logcat 意外中止：${error.message ?: "未知错误"}"
                        writer.appendLine("[$message]")
                        sectionFailureMessage = "状态: $message"
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
            "logcat 全缓冲区",
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
