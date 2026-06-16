package com.floatingmuseum.android.test.helper.adb

import com.floatingmuseum.android.test.helper.AndroidDevice
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
) : RuntimeException("ADB 命令失败，退出码 $exitCode\n$command\n$output")

object AdbShell {
    val adbPath: String by lazy { resolveAdbPath() }

    suspend fun executeAdb(
        args: List<String>,
        displayCommand: String,
        logCommand: (String) -> Unit,
    ): String {
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
                output
            } catch (error: CancellationException) {
                process.destroyForcibly()
                outputReader.cancel()
                errorReader.cancel()
                throw error
            }
        }
    }

    private fun resolveAdbPath(): String {
        val osName = System.getProperty("os.name").lowercase()
        val platformFolder = when {
            osName.contains("win") -> "platform-tools-latest-windows"
            osName.contains("mac") || osName.contains("darwin") -> "platform-tools-latest-darwin"
            else -> "platform-tools-latest-linux"
        }
        val adbBinary = if (osName.contains("win")) "adb.exe" else "adb"
        val userDir = File(System.getProperty("user.dir")).absoluteFile

        generateSequence(userDir) { it.parentFile }.forEach { directory ->
            val candidate = directory.resolve("platform-tools")
                .resolve(platformFolder)
                .resolve("platform-tools")
                .resolve(adbBinary)
            if (candidate.exists()) {
                return candidate.absolutePath
            }
        }

        return adbBinary
    }

    fun parseAdbDevices(output: String): List<AndroidDevice> {
        return output
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("List of devices attached") }
            .mapNotNull { line ->
                val columns = line.split(Regex("\\s+"))
                val serialNumber = columns.getOrNull(0) ?: return@mapNotNull null
                val state = columns.getOrNull(1) ?: return@mapNotNull null
                val model = columns
                    .firstOrNull { it.startsWith("model:") }
                    ?.substringAfter("model:")
                    ?.replace('_', ' ')
                    ?.takeIf { it.isNotBlank() }
                    ?: "未知型号"

                AndroidDevice(
                    serialNumber = serialNumber,
                    model = model,
                    state = state,
                )
            }
            .toList()
    }
}

class JvmAdbDeviceManager : AdbDeviceManager {
    override suspend fun listDevices(logCommand: (String) -> Unit): List<AndroidDevice> {
        val output = AdbShell.executeAdb(
            args = listOf("devices", "-l"),
            displayCommand = "adb devices -l",
            logCommand = logCommand,
        )
        return AdbShell.parseAdbDevices(output)
    }
}

actual fun createAdbDeviceManager(): AdbDeviceManager = JvmAdbDeviceManager()
