package com.floatingmuseum.android.test.helper.adb

import com.floatingmuseum.android.test.helper.AndroidDevice
import com.floatingmuseum.android.test.helper.AppRuntimePaths
import com.floatingmuseum.android.test.helper.localization.commandStatus
import com.floatingmuseum.android.test.helper.localization.localized
import com.floatingmuseum.android.test.helper.settings.AppSettings
import com.floatingmuseum.android.test.helper.settings.AppSettingsShared
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class AdbCommandException(
    command: String,
    exitCode: Int,
    output: String,
) : RuntimeException(localized("adb.command_failed_with_exit_code_arg0", exitCode) + "\n$command\n$output")

object AdbShell {
    val adbPath: String
        get() = resolveAdbPath()

    suspend fun executeAdb(
        args: List<String>,
        displayCommand: String,
        logCommand: (String) -> Unit,
    ): String {
        val startTime = System.currentTimeMillis()
        logCommand(displayCommand)
        return withContext(Dispatchers.IO) {
            val process = ProcessBuilder(listOf(adbPath) + args)
                .redirectErrorStream(true)
                .start()
            val outputReader = async(Dispatchers.IO) {
                process.inputStream.bufferedReader().readText()
            }
            try {
                while (!process.waitFor(100L, TimeUnit.MILLISECONDS)) {
                    currentCoroutineContext().ensureActive()
                }
                val output = outputReader.await()
                val exitCode = process.exitValue()
                if (exitCode != 0) {
                    throw AdbCommandException(displayCommand, exitCode, output)
                }
                if (AppSettingsShared.currentSettings.showCommandDuration) {
                    val duration = System.currentTimeMillis() - startTime
                    logCommand(commandStatus(localized("shell.command_duration_arg0_ms", duration)))
                }
                output
            } catch (error: CancellationException) {
                process.destroyForcibly()
                outputReader.cancel()
                throw error
            }
        }
    }

    suspend fun executeAdbBinary(
        args: List<String>,
        displayCommand: String,
        logCommand: (String) -> Unit,
    ): ByteArray {
        val startTime = System.currentTimeMillis()
        logCommand(displayCommand)
        return withContext(Dispatchers.IO) {
            val process = ProcessBuilder(listOf(adbPath) + args).start()
            val outputReader = async(Dispatchers.IO) {
                process.inputStream.readBytes()
            }
            val errorReader = async(Dispatchers.IO) {
                process.errorStream.bufferedReader().readText()
            }
            try {
                while (!process.waitFor(100L, TimeUnit.MILLISECONDS)) {
                    currentCoroutineContext().ensureActive()
                }
                val output = outputReader.await()
                val exitCode = process.exitValue()
                if (exitCode != 0) {
                    val errorMsg = errorReader.await()
                    throw AdbCommandException(displayCommand, exitCode, errorMsg)
                }
                if (AppSettingsShared.currentSettings.showCommandDuration) {
                    val duration = System.currentTimeMillis() - startTime
                    logCommand(commandStatus(localized("shell.command_duration_arg0_ms", duration)))
                }
                output
            } catch (error: CancellationException) {
                process.destroyForcibly()
                outputReader.cancel()
                errorReader.cancel()
                throw error
            }
        }
    }

    internal fun resolveAdbPath(settings: AppSettings = AppSettingsShared.currentSettings): String {
        settings.customAdbPath?.let { return File(it).absolutePath }
        return resolveDefaultAdbPath()
    }

    internal fun resolveDefaultAdbPath(): String {
        val osName = System.getProperty("os.name").lowercase()
        val platformFolder = when {
            osName.contains("win") -> "platform-tools-latest-windows"
            osName.contains("mac") || osName.contains("darwin") -> "platform-tools-latest-darwin"
            else -> "platform-tools-latest-linux"
        }
        val adbBinary = if (osName.contains("win")) "adb.exe" else "adb"
        val userDir = File(System.getProperty("user.dir")).absoluteFile
        val searchRoots = listOf(AppRuntimePaths.installDirectory, userDir).distinctBy { it.absolutePath }

        searchRoots.asSequence().flatMap { root ->
            generateSequence(root.absoluteFile) { it.parentFile }
        }.distinctBy { it.absolutePath }.forEach { directory ->
            adbCandidatePaths(directory, platformFolder, adbBinary)
                .firstOrNull { it.exists() }
                ?.let { return it.absolutePath }
        }

        return adbBinary
    }

    internal suspend fun readAdbVersion(adbExecutablePath: String): Result<String> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val process = ProcessBuilder(adbExecutablePath, "version")
                    .redirectErrorStream(true)
                    .start()
                val completed = process.waitFor(5L, TimeUnit.SECONDS)
                if (!completed) {
                    process.destroyForcibly()
                    throw IllegalStateException(localized("adb.timed_out_while_reading_adb_version"))
                }
                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.exitValue()
                if (exitCode != 0) {
                    throw IllegalStateException(output.ifBlank { localized("adb.version_exited_with_code_arg0", exitCode) })
                }
                parseAdbVersion(output).ifBlank {
                    throw IllegalStateException(localized("adb.version_returned_no_version_info"))
                }
            }
        }
    }

    internal fun parseAdbVersion(output: String): String {
        return output
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString(" / ")
    }

    fun parseAdbDevices(
        output: String,
        unknownModelFallback: String = localized("device.unknown_model"),
    ): List<AndroidDevice> {
        return output
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("List of devices attached") }
            .mapNotNull { line ->
                val columns = line.split(Regex("\\s+"))
                val transportId = columns.getOrNull(0) ?: return@mapNotNull null
                val state = columns.getOrNull(1) ?: return@mapNotNull null
                val model = columns
                    .firstOrNull { it.startsWith("model:") }
                    ?.substringAfter("model:")
                    ?.replace('_', ' ')
                    ?.takeIf { it.isNotBlank() }
                    ?: unknownModelFallback

                AndroidDevice(
                    serialNumber = parseHardwareSerialFromTransport(transportId),
                    transportId = transportId,
                    model = model,
                    state = state,
                )
            }
            .toList()
    }
}

internal fun adbCandidatePaths(
    directory: File,
    platformFolder: String,
    adbBinary: String,
): List<File> {
    return listOf(
        directory.resolve("plugins")
            .resolve("android")
            .resolve("platform-tools")
            .resolve(platformFolder)
            .resolve("platform-tools")
            .resolve(adbBinary),
        directory.resolve("resources")
            .resolve("plugins")
            .resolve("android")
            .resolve("platform-tools")
            .resolve(platformFolder)
            .resolve("platform-tools")
            .resolve(adbBinary),
        directory.resolve("platform-tools")
            .resolve(platformFolder)
            .resolve("platform-tools")
            .resolve(adbBinary),
    )
}

internal fun parseHardwareSerialFromTransport(transportId: String): String {
    val wirelessMatch = Regex("""^adb-([^-\.]+)-.+\._adb-tls-connect\._tcp$""").matchEntire(transportId)
    return wirelessMatch?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() } ?: transportId
}

actual suspend fun loadAdbRuntimeInfo(): AdbRuntimeInfo {
    val settings = AppSettingsShared.currentSettings
    val path = AdbShell.adbPath
    val versionResult = AdbShell.readAdbVersion(path)
    return AdbRuntimeInfo(
        path = path,
        version = versionResult.getOrNull(),
        isCustom = settings.customAdbPath != null,
        errorMessage = versionResult.exceptionOrNull()?.displayMessage(),
    )
}

actual suspend fun checkAdbExecutable(path: String): AdbExecutableCheckResult {
    val file = File(path).absoluteFile
    if (!file.isFile) {
        return AdbExecutableCheckResult(
            isValid = false,
            normalizedPath = file.absolutePath,
            version = null,
            errorMessage = localized("adb.selected_path_is_not_an_executable_file"),
        )
    }

    val versionResult = AdbShell.readAdbVersion(file.absolutePath)
    return AdbExecutableCheckResult(
        isValid = versionResult.isSuccess,
        normalizedPath = file.absolutePath,
        version = versionResult.getOrNull(),
        errorMessage = versionResult.exceptionOrNull()?.displayMessage(),
    )
}

private fun Throwable.displayMessage(): String {
    return message
        ?.lineSequence()
        ?.firstOrNull { it.isNotBlank() }
        ?.trim()
        ?: this::class.simpleName
        ?: localized("common.unknown_error")
}

class JvmAdbDeviceManager : AdbDeviceManager {
    override suspend fun listDevices(logCommand: (String) -> Unit): List<AndroidDevice> {
        val output = AdbShell.executeAdb(
            args = listOf("devices", "-l"),
            displayCommand = "adb devices -l",
            logCommand = logCommand,
        )
        val devices = AdbShell.parseAdbDevices(output)
        return deduplicateDevicesBySerial(
            devices.map { device ->
                if (device.isReady && shouldReadHardwareSerial(device)) {
                    readHardwareSerial(device.transportId, logCommand)?.let { serial ->
                        device.copy(serialNumber = serial)
                    } ?: device
                } else {
                    device
                }
            },
        )
    }

    override suspend fun pairWirelessDevice(
        host: String,
        pairingPort: String,
        pairingCode: String,
        logCommand: (String) -> Unit,
    ): String {
        val command = buildWirelessPairCommand(host, pairingPort, pairingCode)
        val output = AdbShell.executeAdb(
            args = command.args,
            displayCommand = command.displayCommand,
            logCommand = logCommand,
        )
        requireWirelessAdbSuccess(output, command.displayCommand)
        return output
    }

    override suspend fun connectWirelessDevice(
        host: String,
        debugPort: String,
        logCommand: (String) -> Unit,
    ): String {
        val command = buildWirelessConnectCommand(host, debugPort)
        val output = AdbShell.executeAdb(
            args = command.args,
            displayCommand = command.displayCommand,
            logCommand = logCommand,
        )
        requireWirelessAdbSuccess(output, command.displayCommand)
        return output
    }
}

actual fun createAdbDeviceManager(): AdbDeviceManager = JvmAdbDeviceManager()

private suspend fun readHardwareSerial(
    transportId: String,
    logCommand: (String) -> Unit,
): String? {
    return runCatching {
        AdbShell.executeAdb(
            args = listOf("-s", transportId, "shell", "getprop", "ro.serialno"),
            displayCommand = "adb -s $transportId shell getprop ro.serialno",
            logCommand = logCommand,
        ).trim().takeIf { it.isNotBlank() }
    }.getOrNull()
}

internal fun shouldReadHardwareSerial(device: AndroidDevice): Boolean {
    return device.serialNumber == device.transportId && isIpPortTransport(device.transportId)
}

internal fun isIpPortTransport(transportId: String): Boolean {
    return Regex("""^\d{1,3}(\.\d{1,3}){3}:\d{1,5}$""").matches(transportId) ||
        Regex("""^\[[0-9a-fA-F:]+]:\d{1,5}$""").matches(transportId)
}

internal fun deduplicateDevicesBySerial(devices: List<AndroidDevice>): List<AndroidDevice> {
    return devices
        .groupBy { it.serialNumber }
        .values
        .map { candidates ->
            candidates.maxWithOrNull(
                compareBy<AndroidDevice> { it.isReady }
                    .thenBy { !it.hasDistinctTransport }
                    .thenBy { !it.transportId.startsWith("adb-") }
            ) ?: candidates.first()
        }
}

private fun requireWirelessAdbSuccess(
    output: String,
    displayCommand: String,
) {
    val normalized = output.lowercase()
    if (
        normalized.contains("failed") ||
        normalized.contains("unable") ||
        normalized.contains("cannot") ||
        normalized.contains("error")
    ) {
        throw IllegalStateException(localized("adb.command_reported_failure") + "\n$displayCommand\n${output.trim()}")
    }
}
