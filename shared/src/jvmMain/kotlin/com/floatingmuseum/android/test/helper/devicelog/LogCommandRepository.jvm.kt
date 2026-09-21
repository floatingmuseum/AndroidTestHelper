package com.floatingmuseum.android.test.helper.devicelog

import com.floatingmuseum.android.test.helper.AppRuntimePaths
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

actual fun createLogCommandRepository(): LogCommandRepository = JvmLogCommandRepository()

internal class JvmLogCommandRepository(
    private val configFile: () -> File = { AppRuntimePaths.cacheDirectory().resolve("log_commands.json") },
) : LogCommandRepository {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    override fun load(): LogCommandConfiguration {
        val file = configFile()
        if (!file.exists()) return LogCommandConfiguration()
        return runCatching {
            json.decodeFromString<LogCommandConfiguration>(file.readText(Charsets.UTF_8)).normalized()
        }.getOrDefault(LogCommandConfiguration())
    }

    override fun save(configuration: LogCommandConfiguration) {
        val file = configFile().absoluteFile
        file.parentFile.mkdirs()
        val temporary = Files.createTempFile(file.parentFile.toPath(), "log_commands_", ".tmp")
        try {
            Files.writeString(temporary, json.encodeToString(configuration.normalized()), Charsets.UTF_8)
            try {
                Files.move(temporary, file.toPath(), REPLACE_EXISTING, ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, file.toPath(), REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
