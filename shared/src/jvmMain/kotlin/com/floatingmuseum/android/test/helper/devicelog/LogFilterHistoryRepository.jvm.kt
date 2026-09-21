package com.floatingmuseum.android.test.helper.devicelog

import com.floatingmuseum.android.test.helper.AppRuntimePaths
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

actual fun createLogFilterHistoryRepository(): LogFilterHistoryRepository = JvmLogFilterHistoryRepository()

internal class JvmLogFilterHistoryRepository(
    private val historyFile: () -> File = { AppRuntimePaths.cacheDirectory().resolve("log_filter_history.json") },
) : LogFilterHistoryRepository {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    override fun loadHistory(): List<LogKeywordFilter> {
        val file = historyFile()
        if (!file.exists()) return emptyList()
        return try {
            normalizeLogFilterHistory(json.decodeFromString<List<LogKeywordFilter>>(file.readText(Charsets.UTF_8)))
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun saveHistory(history: List<LogKeywordFilter>) {
        val file = historyFile()
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(normalizeLogFilterHistory(history)), Charsets.UTF_8)
    }
}
