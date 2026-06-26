package com.floatingmuseum.android.test.helper

import com.floatingmuseum.android.test.helper.localization.localized
import java.io.File
import java.nio.file.Files

internal object AppRuntimePaths {
    val installDirectory: File by lazy {
        resolveApplicationInstallDirectory(
            userDir = File(System.getProperty("user.dir")),
            commandPath = ProcessHandle.current().info().command().orElse(null),
            codeSourcePath = codeSourceFile(),
        )
    }

    fun cacheDirectory(): File = installDirectory.resolve("AndroidTestHelperData/cache").ensureDirectory()

    fun logsDirectory(): File = installDirectory.resolve("AndroidTestHelperData/logs").ensureDirectory()

    fun tempRootDirectory(): File = installDirectory.resolve("AndroidTestHelperData/temp").ensureDirectory()

    fun createTempDirectory(prefix: String): File {
        return Files.createTempDirectory(tempRootDirectory().toPath(), prefix).toFile()
    }

    private fun codeSourceFile(): File? {
        return try {
            val location = AppRuntimePaths::class.java.protectionDomain?.codeSource?.location ?: return null
            File(location.toURI()).absoluteFile
        } catch (_: Throwable) {
            null
        }
    }
}

internal fun resolveApplicationInstallDirectory(
    userDir: File,
    commandPath: String?,
    codeSourcePath: File?,
): File {
    commandPath
        ?.let { File(it).absoluteFile }
        ?.takeIf { it.isFile && !it.nameWithoutExtension.equals("java", ignoreCase = true) && !it.nameWithoutExtension.equals("javaw", ignoreCase = true) }
        ?.parentFile
        ?.let { return it }

    codeSourcePath?.let { source ->
        val sourceDirectory = if (source.isFile) source.parentFile else source
        if (sourceDirectory != null && sourceDirectory.name == "app" && sourceDirectory.parentFile != null) {
            return sourceDirectory.parentFile.absoluteFile
        }
    }

    findProjectRoot(userDir.absoluteFile)?.let { return it }
    return userDir.absoluteFile
}

internal fun findProjectRoot(start: File): File? {
    return generateSequence(start.absoluteFile) { it.parentFile }
        .firstOrNull { directory ->
            directory.resolve("platform-tools").isDirectory ||
                directory.resolve("settings.gradle.kts").isFile
        }
}

private fun File.ensureDirectory(): File {
    if (!exists()) {
        mkdirs()
    }
    if (!isDirectory) {
        throw IllegalStateException(localized("runtime_paths.error.path_is_not_directory", absolutePath))
    }
    return this
}
