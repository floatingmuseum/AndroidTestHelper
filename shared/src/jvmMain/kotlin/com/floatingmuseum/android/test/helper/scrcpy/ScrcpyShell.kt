package com.floatingmuseum.android.test.helper.scrcpy

import com.floatingmuseum.android.test.helper.AppRuntimePaths
import com.floatingmuseum.android.test.helper.localization.localized
import com.floatingmuseum.android.test.helper.settings.AppSettings
import com.floatingmuseum.android.test.helper.settings.AppSettingsShared
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object ScrcpyShell {
    val scrcpyPath: String
        get() = resolveScrcpyPath()

    internal fun resolveScrcpyPath(settings: AppSettings = AppSettingsShared.currentSettings): String {
        settings.customScrcpyPath?.let { return File(it).absolutePath }
        return resolveDefaultScrcpyPath()
    }

    internal fun resolveDefaultScrcpyPath(): String {
        val osName = System.getProperty("os.name").lowercase()
        val binaryName = scrcpyBinaryName(osName)
        val platformFolders = scrcpyPlatformFolders(
            osName = osName,
            osArch = System.getProperty("os.arch"),
        )
        val userDir = File(System.getProperty("user.dir")).absoluteFile
        val searchRoots = listOf(AppRuntimePaths.installDirectory, userDir).distinctBy { it.absolutePath }

        searchRoots.asSequence().flatMap { root ->
            generateSequence(root.absoluteFile) { it.parentFile }
        }.distinctBy { it.absolutePath }.forEach { directory ->
            scrcpyCandidatePaths(directory, platformFolders, binaryName)
                .firstOrNull { it.isFile }
                ?.let { return it.absolutePath }
        }

        return binaryName
    }

    internal suspend fun readScrcpyVersion(scrcpyExecutablePath: String): Result<String> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val process = ProcessBuilder(scrcpyExecutablePath, "--version")
                    .redirectErrorStream(true)
                    .start()
                val completed = process.waitFor(5L, TimeUnit.SECONDS)
                if (!completed) {
                    process.destroyForcibly()
                    throw IllegalStateException(localized("scrcpy.timed_out_while_reading_version"))
                }
                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.exitValue()
                if (exitCode != 0) {
                    throw IllegalStateException(output.ifBlank { localized("scrcpy.version_exited_with_code_arg0", exitCode) })
                }
                parseScrcpyVersion(output).ifBlank {
                    throw IllegalStateException(localized("scrcpy.version_returned_no_version_info"))
                }
            }
        }
    }

    internal fun parseScrcpyVersion(output: String): String {
        return output
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("scrcpy ", ignoreCase = true) || Regex("""^\d+(\.\d+)+""").containsMatchIn(it) }
            ?: output.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
    }
}

internal fun scrcpyBinaryName(osName: String): String {
    return if (isWindowsOs(osName)) "scrcpy.exe" else "scrcpy"
}

internal fun scrcpyPlatformFolders(osName: String, osArch: String?): List<String> {
    val normalizedOsName = osName.lowercase()
    if (isWindowsOs(normalizedOsName)) {
        return listOf("windows")
    }

    val family = when {
        normalizedOsName.contains("mac") || normalizedOsName.contains("darwin") -> "darwin"
        else -> "linux"
    }
    val normalizedArch = normalizeScrcpyArch(osArch)
    return listOfNotNull(
        normalizedArch?.let { "$family-$it" },
        family,
    ).distinct()
}

private fun isWindowsOs(osName: String): Boolean {
    return osName.lowercase().startsWith("windows")
}

private fun normalizeScrcpyArch(osArch: String?): String? {
    return when (osArch?.lowercase()?.replace("-", "_")) {
        "aarch64", "arm64" -> "aarch64"
        "amd64", "x86_64" -> "x86_64"
        null, "" -> null
        else -> osArch.lowercase().replace("-", "_")
    }
}

internal fun scrcpyCandidatePaths(
    directory: File,
    platformFolder: String,
    binaryName: String,
): List<File> {
    return scrcpyCandidatePaths(directory, listOf(platformFolder), binaryName)
}

internal fun scrcpyCandidatePaths(
    directory: File,
    platformFolders: List<String>,
    binaryName: String,
): List<File> {
    return buildList {
        platformFolders.forEach { platformFolder ->
            add(directory.resolve("plugins").resolve("scrcpy").resolve(platformFolder).resolve(binaryName))
        }
        add(directory.resolve("plugins").resolve("scrcpy").resolve(binaryName))
        platformFolders.forEach { platformFolder ->
            add(directory.resolve("resources").resolve("plugins").resolve("scrcpy").resolve(platformFolder).resolve(binaryName))
        }
        add(directory.resolve("resources").resolve("plugins").resolve("scrcpy").resolve(binaryName))
        add(directory.resolve(binaryName))
        add(directory.resolve("scrcpy").resolve(binaryName))
        platformFolders.forEach { platformFolder ->
            add(directory.resolve("scrcpy").resolve(platformFolder).resolve(binaryName))
        }
    }
}

actual suspend fun loadScrcpyRuntimeInfo(): ScrcpyRuntimeInfo {
    val settings = AppSettingsShared.currentSettings
    val path = ScrcpyShell.scrcpyPath
    val versionResult = ScrcpyShell.readScrcpyVersion(path)
    return ScrcpyRuntimeInfo(
        path = path,
        version = versionResult.getOrNull(),
        isCustom = settings.customScrcpyPath != null,
        errorMessage = versionResult.exceptionOrNull()?.displayMessage(),
    )
}

actual suspend fun checkScrcpyExecutable(path: String): ScrcpyExecutableCheckResult {
    val file = File(path).absoluteFile
    if (!file.isFile) {
        return ScrcpyExecutableCheckResult(
            isValid = false,
            normalizedPath = file.absolutePath,
            version = null,
            errorMessage = localized("scrcpy.selected_path_is_not_an_executable_file"),
        )
    }

    val versionResult = ScrcpyShell.readScrcpyVersion(file.absolutePath)
    return ScrcpyExecutableCheckResult(
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
