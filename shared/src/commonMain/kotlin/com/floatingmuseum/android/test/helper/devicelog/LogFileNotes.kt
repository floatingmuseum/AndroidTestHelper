package com.floatingmuseum.android.test.helper.devicelog

import kotlinx.serialization.Serializable

@Serializable
internal data class LogFileNote(
    val lineNumber: Long,
    val raw: String,
    val text: String,
) {
    val row: LogFileRow get() = parseLogFileRow(raw, lineNumber)
}

@Serializable
internal data class LogFileNotesDocument(
    val version: Int,
    val notes: List<LogFileNote>,
)

internal data class LoadedLogFileNotes(val notes: List<LogFileNote>, val revision: String?)

internal interface LogFileNotesRepository {
    suspend fun load(logPath: String): LoadedLogFileNotes
    suspend fun save(logPath: String, notes: List<LogFileNote>, expectedRevision: String?): LoadedLogFileNotes
}

internal expect fun createLogFileNotesRepository(): LogFileNotesRepository

internal fun logFileNotesPath(logPath: String): String = "$logPath.notes.json"
