package com.floatingmuseum.android.test.helper.devicelog

import kotlinx.serialization.Serializable

data class DeviceLogCaptureProgress(
    val capturedLines: Long,
    val matchedLines: Long,
    val hasFilter: Boolean,
)

data class DeviceLogCaptureResult(
    val fileName: String,
    val filePath: String,
    val directoryPath: String,
    val capturedLines: Long,
    val matchedLines: Long,
    val filteredFilePath: String? = null,
    val filter: LogKeywordFilter = LogKeywordFilter(),
    val endState: DeviceLogCaptureEndState = DeviceLogCaptureEndState.COMPLETED,
    val message: String? = null,
)

enum class DeviceLogCaptureEndState { COMPLETED, STOPPED, INTERRUPTED }

data class LogcatAdbCommand(
    val args: List<String>,
    val displayCommand: String,
    val studioFormat: Boolean = true,
    val completesOnExit: Boolean = false,
)

fun buildLogcatAdbCommand(deviceSerial: String, customCommand: CustomLogCommand? = null): LogcatAdbCommand {
    if (customCommand != null) {
        val arguments = parseCustomLogcatArguments(customCommand.command)
        val args = listOf("-s", deviceSerial, "shell", "logcat") + arguments.map(::quoteLogcatArgument)
        return LogcatAdbCommand(
            args, "adb -s ${quoteLogcatArgument(deviceSerial)} shell logcat " + arguments.joinToString(" ", transform = ::quoteLogcatArgument),
            studioFormat = false,
            completesOnExit = arguments.any {
                it in listOf("-d", "--dump", "-t", "-m", "--max-count") ||
                    it.startsWith("--max-count=") || it.matches(Regex("-[tm]\\d+"))
            },
        )
    }
    val args = listOf("-s", deviceSerial, "shell", "logcat", "-b", "all", "-v", "threadtime", "-v", "year", "*:V")
    return LogcatAdbCommand(args, "adb " + args.joinToString(" "))
}

/** Literal keywords, separated by |. Whitespace inside a keyword is significant. */
@Serializable
data class LogKeywordFilter(val query: String = "", val matchCase: Boolean = false) {
    val keywords: List<String>
        get() = query.split('|').map { it.trim() }.filter { it.isNotEmpty() }
            .distinctBy { if (matchCase) it else it.lowercase() }

    fun normalized(): LogKeywordFilter = copy(query = keywords.joinToString(" | "))
}

class LogKeywordMatcher(filter: LogKeywordFilter) {
    private val keywords = filter.keywords
    private val ignoreCase = !filter.matchCase
    val isEmpty: Boolean get() = keywords.isEmpty()

    fun matches(line: String): Boolean = isEmpty || keywords.any { line.contains(it, ignoreCase) }
}

const val LOG_FILTER_HISTORY_LIMIT = 50

fun normalizeLogFilterHistory(history: List<LogKeywordFilter>): List<LogKeywordFilter> = history
    .map { it.normalized() }
    .filter { it.query.isNotEmpty() }
    .distinctBy { (if (it.matchCase) it.query else it.query.lowercase()) to it.matchCase }
    .take(LOG_FILTER_HISTORY_LIMIT)

fun rememberLogFilter(history: List<LogKeywordFilter>, filter: LogKeywordFilter): List<LogKeywordFilter> =
    normalizeLogFilterHistory(listOf(filter) + history)

internal data class LogcatEntry(
    val timestamp: String,
    val pid: String,
    val tid: String,
    val priority: String,
    val tag: String,
    val message: String,
) {
    fun format(processName: String?): String =
        "$timestamp ${(pid + "-" + tid).padEnd(11)} ${tag.padEnd(24)} ${(processName ?: "-").padEnd(32)} $priority  $message"
}

private val threadtimePattern = Regex("""^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\s+(\d+)\s+(\d+)\s+([VDIWEFA])\s(.*?): (.*)$""")

internal fun parseThreadtimeLogLine(line: String): LogcatEntry? {
    val match = threadtimePattern.matchEntire(line) ?: return null
    val (timestamp, pid, tid, priority, tag, message) = match.destructured
    return LogcatEntry(timestamp, pid, tid, priority, tag.trim(), message)
}

internal fun parseLogProcessNames(output: String): Map<String, String> = output.lineSequence()
    .map { it.trim().split(Regex("\\s+"), limit = 2) }
    .filter { it.size == 2 && it[0].toLongOrNull() != null && it[1].isNotBlank() }
    .associate { it[0] to it[1] }

interface DeviceLogAdb {
    suspend fun captureFullLogs(
        deviceSerial: String,
        deviceModel: String,
        filter: LogKeywordFilter,
        logCommand: (String) -> Unit,
        onProgress: (DeviceLogCaptureProgress) -> Unit,
        customCommand: CustomLogCommand? = null,
    ): DeviceLogCaptureResult

    fun stopCurrentCapture()
}

interface LogFilterHistoryRepository {
    fun loadHistory(): List<LogKeywordFilter>
    fun saveHistory(history: List<LogKeywordFilter>)
}

expect fun createDeviceLogAdb(): DeviceLogAdb
expect fun createLogFilterHistoryRepository(): LogFilterHistoryRepository
