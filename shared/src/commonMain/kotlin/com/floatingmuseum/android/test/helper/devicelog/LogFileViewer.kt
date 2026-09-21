package com.floatingmuseum.android.test.helper.devicelog

import androidx.compose.runtime.Composable

internal enum class LogFileColumn(val labelKey: String) {
    Date("log.viewer.column.date"),
    Time("log.viewer.column.time"),
    PidTid("log.viewer.column.pid_tid"),
    Tag("log.viewer.column.tag"),
    Process("log.viewer.column.process"),
    Priority("log.viewer.column.priority"),
    Message("log.viewer.column.message"),
}

internal data class LogFileRow(
    val lineNumber: Long,
    val raw: String,
    val date: String = "",
    val time: String = "",
    val pidTid: String = "",
    val tag: String = "",
    val process: String = "",
    val priority: String = "",
    val message: String = raw,
) {
    fun value(column: LogFileColumn): String = when (column) {
        LogFileColumn.Date -> date
        LogFileColumn.Time -> time
        LogFileColumn.PidTid -> pidTid
        LogFileColumn.Tag -> tag
        LogFileColumn.Process -> process
        LogFileColumn.Priority -> priority
        LogFileColumn.Message -> message
    }
}

private val savedLinePrefix = Regex("""^(\d{4}-\d{2}-\d{2}) (\d{2}:\d{2}:\d{2}\.\d{3}) (\d+-\d+)""")
private val savedLineFields = Regex("""^(.{24,}?) (\S+ *) ([VDIWEFA])  (.*)$""")
private val rawThreadtime = Regex("""^((?:\d{4}-)?\d{2}-\d{2})\s+(\d{2}:\d{2}:\d{2}\.\d{3,6})\s+(\d+)\s+(\d+)\s+([VDIWEFA])\s+(.*?): (.*)$""")

/** Reads the existing padded capture format without splitting spaces inside tags or messages. */
internal fun parseLogFileRow(line: String, lineNumber: Long): LogFileRow {
    val text = if (lineNumber == 1L) line.removePrefix("\uFEFF") else line
    savedLinePrefix.find(text)?.let { prefix ->
        val (date, time, ids) = prefix.destructured
        val fieldsStart = prefix.range.last + 1 + (11 - ids.length).coerceAtLeast(0) + 1
        if (fieldsStart <= text.length) {
            savedLineFields.matchEntire(text.substring(fieldsStart))?.let { fields ->
                val (tag, process, priority, message) = fields.destructured
                if (process.length >= 32) {
                    return LogFileRow(lineNumber, text, date, time, ids, tag.trimEnd(), process.trimEnd(), priority, message)
                }
            }
        }
    }
    rawThreadtime.matchEntire(text)?.let { match ->
        val (date, time, pid, tid, priority, tag, message) = match.destructured
        return LogFileRow(lineNumber, text, date, time, "$pid-$tid", tag.trim(), "-", priority, message)
    }
    // Headers, buffer separators, stack traces and unknown formats must remain searchable.
    return LogFileRow(lineNumber, text)
}

/** An index of the complete result set. Text is read only for the visible part of the list. */
internal interface LogFileContent {
    val filePath: String
    val rowCount: Int
    val totalLines: Int
    suspend fun readRows(start: Int, count: Int): List<LogFileRow>
}

internal interface LogFileReader {
    suspend fun read(path: String, filter: LogKeywordFilter): LogFileContent
}

internal expect fun createLogFileReader(): LogFileReader
internal expect suspend fun selectLogFile(): String?

@Composable
internal expect fun LogFileViewerWindow(controller: LogFileViewerController)
