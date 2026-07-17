package com.floatingmuseum.android.test.helper.monkey

import com.floatingmuseum.android.test.helper.AppRuntimePaths
import com.floatingmuseum.android.test.helper.adb.AdbShell
import com.floatingmuseum.android.test.helper.localization.localized
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

actual fun createMonkeyAdb(): MonkeyAdb = JvmMonkeyAdb()

actual fun createMonkeyPresetRepository(): MonkeyPresetRepository = JvmMonkeyPresetRepository()

private val MonkeyTimestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

private class JvmMonkeyAdb : MonkeyAdb {
    @Volatile
    private var activeProcess: Process? = null

    @Volatile
    private var stopRequested: Boolean = false

    override suspend fun runMonkey(
        deviceSerial: String,
        deviceModel: String,
        form: MonkeyTestForm,
        logCommand: (String) -> Unit,
        onOutputLine: (String) -> Unit,
    ): MonkeyRunResult {
        val normalizedForm = form.normalized()
        val validation = validateMonkeyForm(normalizedForm)
        check(validation.isValid) { localized("monkey.validation.failed") }
        stopRequested = false

        if (normalizedForm.targetScope == MonkeyTargetScope.Packages) {
            for (packageName in normalizedForm.packages) {
                verifyPackageExists(deviceSerial, packageName, logCommand)
                if (stopRequested) break
            }
        }

        val command = buildMonkeyAdbCommand(deviceSerial, normalizedForm)
        val startedAt = LocalDateTime.now()
        val reportFile = createUniqueReportFile(deviceModel, normalizedForm, startedAt)
        val summaryBuilder = MonkeyReportSummaryBuilder(normalizedForm.eventCount.toLong())
        var exitCode: Int? = null
        var processError: Throwable? = null
        var cleanupMessage: String? = null
        var cleanupFailed = false

        reportFile.bufferedWriter(Charsets.UTF_8).use { writer ->
            writeReportHeader(
                writer = writer,
                deviceSerial = deviceSerial,
                deviceModel = deviceModel,
                form = normalizedForm,
                command = command,
                startedAt = startedAt,
            )
            writer.flush()

            if (!stopRequested) {
                logCommand(command.displayCommand)
                try {
                    withContext(Dispatchers.IO) {
                        val process = ProcessBuilder(listOf(AdbShell.adbPath) + command.args)
                            .redirectErrorStream(true)
                            .start()
                        activeProcess = process
                        val outputReader = async(Dispatchers.IO) {
                            process.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                                lines.forEach { line ->
                                    summaryBuilder.accept(line)
                                    writer.appendLine(line)
                                    writer.flush()
                                    onOutputLine(line)
                                }
                            }
                        }
                        try {
                            while (!process.waitFor(100L, TimeUnit.MILLISECONDS)) {
                                currentCoroutineContext().ensureActive()
                            }
                            outputReader.await()
                            exitCode = process.exitValue()
                        } catch (error: CancellationException) {
                            stopRequested = true
                            process.destroyForcibly()
                            outputReader.cancel()
                            throw error
                        } finally {
                            if (activeProcess === process) activeProcess = null
                        }
                    }
                } catch (error: CancellationException) {
                    val cleanup = withContext(NonCancellable) {
                        cleanupRemoteMonkey(deviceSerial, logCommand)
                    }
                    cleanupFailed = !cleanup.success
                    cleanupMessage = cleanup.message
                    val summary = summaryBuilder.build()
                    val endState = resolveMonkeyRunEndState(
                        exitCode = exitCode,
                        stopRequested = true,
                        cleanupFailed = cleanupFailed,
                        summary = summary,
                    )
                    writeReportFooter(
                        writer,
                        summary,
                        endState,
                        exitCode,
                        cleanupMessage,
                        startedAt,
                        LocalDateTime.now(),
                    )
                    throw error
                } catch (error: Throwable) {
                    processError = error
                }
            }

            if (stopRequested) {
                val cleanup = cleanupRemoteMonkey(deviceSerial, logCommand)
                cleanupFailed = !cleanup.success
                cleanupMessage = cleanup.message
            }

            val summary = summaryBuilder.build()
            val endState = resolveMonkeyRunEndState(
                exitCode = exitCode,
                stopRequested = stopRequested,
                cleanupFailed = cleanupFailed,
                summary = summary,
            )
            processError?.let { error ->
                writer.appendLine()
                writer.appendLine("ProcessError: ${error.message ?: error::class.simpleName}")
            }
            writeReportFooter(
                writer,
                summary,
                endState,
                exitCode,
                cleanupMessage,
                startedAt,
                LocalDateTime.now(),
            )
            return MonkeyRunResult(
                command = command,
                reportFilePath = reportFile.absolutePath,
                summary = summary,
                endState = endState,
                cleanupMessage = cleanupMessage,
            )
        }
    }

    override fun stopCurrentRun() {
        stopRequested = true
        activeProcess?.destroyForcibly()
    }

    private suspend fun verifyPackageExists(
        deviceSerial: String,
        packageName: String,
        logCommand: (String) -> Unit,
    ) {
        val args = listOf("-s", deviceSerial, "shell", "pm", "path", packageName)
        val displayCommand = "adb -s ${deviceSerial.quoteForDisplay()} shell pm path ${packageName.quoteForDisplay()}"
        val output = runCatching {
            AdbShell.executeAdb(args, displayCommand, logCommand)
        }.getOrElse {
            throw IllegalStateException(localized("monkey.package_not_installed_arg0", packageName), it)
        }
        if (output.lineSequence().none { it.trim().startsWith("package:") }) {
            throw IllegalStateException(localized("monkey.package_not_installed_arg0", packageName))
        }
    }

    private suspend fun cleanupRemoteMonkey(
        deviceSerial: String,
        logCommand: (String) -> Unit,
    ): CleanupResult {
        val pidof = executeCleanupAdb(
            args = listOf("-s", deviceSerial, "shell", "pidof", "com.android.commands.monkey"),
            displayCommand = "adb -s ${deviceSerial.quoteForDisplay()} shell pidof com.android.commands.monkey",
            logCommand = logCommand,
        )
        var inspectionSucceeded = pidof.exitCode == 0
        var pids = if (inspectionSucceeded) {
            pidof.output.trim().split(Regex("\\s+")).filter { it.all(Char::isDigit) }
        } else {
            emptyList()
        }
        if (pids.isEmpty()) {
            val psResult = executeCleanupAdb(
                args = listOf("-s", deviceSerial, "shell", "ps", "-A"),
                displayCommand = "adb -s ${deviceSerial.quoteForDisplay()} shell ps -A",
                logCommand = logCommand,
            )
            if (psResult.exitCode == 0) {
                inspectionSucceeded = true
                pids = parseMonkeyProcessIds(psResult.output)
            } else {
                val legacyPsResult = executeCleanupAdb(
                    args = listOf("-s", deviceSerial, "shell", "ps"),
                    displayCommand = "adb -s ${deviceSerial.quoteForDisplay()} shell ps",
                    logCommand = logCommand,
                )
                if (legacyPsResult.exitCode == 0) {
                    inspectionSucceeded = true
                    pids = parseMonkeyProcessIds(legacyPsResult.output)
                }
            }
        }
        if (pids.isEmpty()) {
            return if (inspectionSucceeded) {
                CleanupResult(success = true)
            } else {
                CleanupResult(success = false, message = localized("monkey.stop_cleanup_unverified"))
            }
        }

        var failures = pids.filter { pid ->
            executeCleanupAdb(
                args = listOf("-s", deviceSerial, "shell", "kill", pid),
                displayCommand = "adb -s ${deviceSerial.quoteForDisplay()} shell kill $pid",
                logCommand = logCommand,
            ).exitCode != 0
        }
        if (failures.isNotEmpty()) {
            failures = failures.filter { pid ->
                executeCleanupAdb(
                    args = listOf("-s", deviceSerial, "shell", "kill", "-9", pid),
                    displayCommand = "adb -s ${deviceSerial.quoteForDisplay()} shell kill -9 $pid",
                    logCommand = logCommand,
                ).exitCode != 0
            }
        }
        return if (failures.isEmpty()) {
            CleanupResult(success = true)
        } else {
            CleanupResult(
                success = false,
                message = localized("monkey.stop_cleanup_failed_arg0", failures.joinToString(", ")),
            )
        }
    }

    private suspend fun executeCleanupAdb(
        args: List<String>,
        displayCommand: String,
        logCommand: (String) -> Unit,
    ): CleanupCommandResult = withContext(Dispatchers.IO) {
        logCommand(displayCommand)
        runCatching {
            val process = ProcessBuilder(listOf(AdbShell.adbPath) + args)
                .redirectErrorStream(true)
                .start()
            val completed = process.waitFor(5L, TimeUnit.SECONDS)
            if (!completed) process.destroyForcibly()
            CleanupCommandResult(
                exitCode = if (completed) process.exitValue() else -1,
                output = process.inputStream.bufferedReader(Charsets.UTF_8).readText(),
            )
        }.getOrElse { CleanupCommandResult(-1, it.message.orEmpty()) }
    }

}

internal class JvmMonkeyPresetRepository(
    private val presetsFileProvider: () -> File = {
        AppRuntimePaths.cacheDirectory().resolve("monkey_presets.json")
    },
) : MonkeyPresetRepository {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    override fun loadPresets(): List<MonkeyPreset> {
        val presetsFile = presetsFileProvider()
        if (!presetsFile.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<MonkeyPresetFile>(presetsFile.readText())
                .presets
                .map { it.copy(name = it.name.trim(), form = it.form.normalized()) }
                .filter { it.name.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    override fun savePresets(presets: List<MonkeyPreset>) {
        val presetsFile = presetsFileProvider()
        presetsFile.parentFile?.mkdirs()
        val normalized = presets
            .map { it.copy(name = it.name.trim(), form = it.form.normalized()) }
            .filter { it.name.isNotBlank() }
        presetsFile.writeText(json.encodeToString(MonkeyPresetFile(normalized)))
    }
}

@Serializable
private data class MonkeyPresetFile(
    val presets: List<MonkeyPreset> = emptyList(),
)

private data class CleanupResult(
    val success: Boolean,
    val message: String? = null,
)

private data class CleanupCommandResult(
    val exitCode: Int,
    val output: String,
)

internal fun parseMonkeyProcessIds(output: String): List<String> = output
    .lineSequence()
    .map { it.trim().split(Regex("\\s+")) }
    .filter { columns -> columns.lastOrNull() == "com.android.commands.monkey" }
    .mapNotNull { columns -> columns.getOrNull(1)?.takeIf { it.all(Char::isDigit) } }
    .distinct()
    .toList()

private fun createUniqueReportFile(
    deviceModel: String,
    form: MonkeyTestForm,
    startedAt: LocalDateTime,
): File {
    val directory = AppRuntimePaths.logsDirectory().resolve("monkey")
    if (!directory.exists() && !directory.mkdirs()) {
        throw IllegalStateException(localized("monkey.report_directory_create_failed_arg0", directory.absolutePath))
    }
    val baseName = buildMonkeyReportFileName(
        deviceModel = deviceModel,
        form = form,
        timestampToken = startedAt.format(MonkeyTimestampFormatter),
    )
    return resolveUniqueMonkeyReportFile(directory, baseName)
}

internal fun resolveUniqueMonkeyReportFile(directory: File, baseName: String): File {
    val base = directory.resolve(baseName)
    if (!base.exists()) return base
    val stem = baseName.removeSuffix(".log")
    return generateSequence(2) { it + 1 }
        .map { directory.resolve("${stem}_$it.log") }
        .first { !it.exists() }
}

private fun writeReportHeader(
    writer: java.io.BufferedWriter,
    deviceSerial: String,
    deviceModel: String,
    form: MonkeyTestForm,
    command: MonkeyAdbCommand,
    startedAt: LocalDateTime,
) {
    writer.appendLine("AndroidTestHelper Monkey report")
    writer.appendLine("StartedAt: $startedAt")
    writer.appendLine("DeviceModel: $deviceModel")
    writer.appendLine("DeviceTransport: $deviceSerial")
    writer.appendLine("TargetScope: ${form.targetScope}")
    writer.appendLine(
        "Packages: " + if (form.targetScope == MonkeyTargetScope.WholeDevice) {
            "<all installed packages>"
        } else {
            form.packages.joinToString(", ")
        },
    )
    writer.appendLine("Categories: ${form.categories.joinToString(", ")}")
    writer.appendLine("EventCount: ${form.eventCount}")
    writer.appendLine("ThrottleMs: ${form.throttleMs}")
    writer.appendLine("Seed: ${form.seed}")
    writer.appendLine("Verbosity: ${form.verbosity}")
    writer.appendLine("EventPercentages: ${form.eventPercentages.entries().filter { it.second.isNotBlank() }.joinToString { "${it.first}=${it.second}" }}")
    writer.appendLine("IgnoreCrashes: ${form.ignoreCrashes}")
    writer.appendLine("IgnoreTimeouts: ${form.ignoreTimeouts}")
    writer.appendLine("IgnoreSecurityExceptions: ${form.ignoreSecurityExceptions}")
    writer.appendLine("KillProcessAfterError: ${form.killProcessAfterError}")
    writer.appendLine("MonitorNativeCrashes: ${form.monitorNativeCrashes}")
    writer.appendLine("Command: ${command.displayCommand}")
    writer.appendLine()
    writer.appendLine("===== MONKEY OUTPUT =====")
}

private fun writeReportFooter(
    writer: java.io.BufferedWriter,
    summary: MonkeyRunSummary,
    endState: MonkeyRunEndState,
    exitCode: Int?,
    cleanupMessage: String?,
    startedAt: LocalDateTime,
    finishedAt: LocalDateTime,
) {
    writer.appendLine()
    writer.appendLine("===== MONKEY SUMMARY =====")
    writer.appendLine("FinishedAt: $finishedAt")
    writer.appendLine("DurationMs: ${Duration.between(startedAt, finishedAt).toMillis().coerceAtLeast(0)}")
    writer.appendLine("EndState: $endState")
    writer.appendLine("ExitCode: ${exitCode ?: "unavailable"}")
    writer.appendLine("PlannedEvents: ${summary.plannedEvents}")
    writer.appendLine("InjectedEvents: ${summary.injectedEvents}")
    writer.appendLine("Crashes: ${summary.crashCount}")
    writer.appendLine("ANRs: ${summary.anrCount}")
    writer.appendLine("NativeCrashes: ${summary.nativeCrashCount}")
    writer.appendLine("DroppedKeys: ${summary.droppedEvents.keys}")
    writer.appendLine("DroppedPointers: ${summary.droppedEvents.pointers}")
    writer.appendLine("DroppedTrackballs: ${summary.droppedEvents.trackballs}")
    writer.appendLine("DroppedFlips: ${summary.droppedEvents.flips}")
    writer.appendLine("DroppedRotations: ${summary.droppedEvents.rotations}")
    cleanupMessage?.let { writer.appendLine("CleanupMessage: $it") }
    writer.flush()
}

private fun String.quoteForDisplay(): String {
    if (isNotEmpty() && none(Char::isWhitespace)) return this
    return "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
