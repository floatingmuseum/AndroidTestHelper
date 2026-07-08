package com.floatingmuseum.android.test.helper.devicelog

import kotlinx.serialization.Serializable

const val DEFAULT_LOG_COMMAND_PRESET_ID = "default-logcat"
const val LOG_COMMAND_SERIAL_PLACEHOLDER = "<serialNumber>"

data class DeviceLogCaptureProgress(
    val currentSection: String,
    val completedSections: Int,
    val totalSections: Int,
)

data class DeviceLogCaptureResult(
    val fileName: String,
    val filePath: String,
    val directoryPath: String,
    val completedSections: Int,
    val totalSections: Int,
    val endState: DeviceLogCaptureEndState = DeviceLogCaptureEndState.COMPLETED,
    val message: String? = null,
)

enum class DeviceLogCaptureEndState {
    COMPLETED,
    STOPPED,
    INTERRUPTED,
}

@Serializable
data class LogCommandPreset(
    val id: String,
    val name: String,
    val parts: List<LogCommandPart>,
) {
    fun normalized(): LogCommandPreset {
        return copy(
            name = name.trim(),
            parts = parts.mapNotNull { it.normalizedOrNull() },
        )
    }
}

@Serializable
data class LogCommandPart(
    val type: LogCommandPartType,
    val value: String,
    val description: String = "",
) {
    fun normalizedOrNull(): LogCommandPart? {
        val normalizedValue = value.trim()
        if (normalizedValue.isBlank()) return null
        return copy(
            value = normalizedValue,
            description = description.trim(),
        )
    }
}

@Serializable
enum class LogCommandPartType {
    Buffer,
    Format,
    Filter,
    Regex,
    Pid,
    MaxCount,
    Recent,
    Silent,
    Dividers,
}

data class LogcatAdbCommand(
    val args: List<String>,
    val displayCommand: String,
)

fun defaultLogCommandPreset(): LogCommandPreset {
    return LogCommandPreset(
        id = DEFAULT_LOG_COMMAND_PRESET_ID,
        name = "Default logcat",
        parts = listOf(
            LogCommandPart(LogCommandPartType.Buffer, "all"),
            LogCommandPart(LogCommandPartType.Format, "threadtime"),
            LogCommandPart(LogCommandPartType.Format, "year"),
            LogCommandPart(LogCommandPartType.Format, "zone"),
            LogCommandPart(LogCommandPartType.Format, "usec"),
            LogCommandPart(LogCommandPartType.Format, "uid"),
        ),
    )
}

fun buildLogcatAdbCommand(
    deviceSerial: String,
    preset: LogCommandPreset,
): LogcatAdbCommand {
    val normalized = preset.normalized()
    val shellArgs = listOf("shell", "logcat") + normalized.parts.flatMap { it.toLogcatArgs() }
    val args = listOf("-s", deviceSerial) + shellArgs
    return LogcatAdbCommand(
        args = args,
        displayCommand = displayCommand(listOf("adb", "-s", deviceSerial) + shellArgs),
    )
}

fun createSavedLogCommandPreset(
    id: String,
    name: String,
    sourcePreset: LogCommandPreset,
): LogCommandPreset? {
    val normalizedName = name.trim()
    if (normalizedName.isBlank()) return null
    val normalizedSource = sourcePreset.normalized()
    return normalizedSource.copy(
        id = id,
        name = normalizedName,
    )
}

fun addLogCommandPart(
    parts: List<LogCommandPart>,
    part: LogCommandPart,
): List<LogCommandPart> {
    val normalizedPart = part.normalizedOrNull() ?: return parts
    if (normalizedPart.type != LogCommandPartType.Buffer) {
        return parts + normalizedPart
    }

    val bufferValue = normalizedPart.value
    val partsWithoutConflictingBuffers = if (bufferValue.equals("all", ignoreCase = true)) {
        parts.filterNot { it.type == LogCommandPartType.Buffer }
    } else {
        parts.filterNot {
            it.type == LogCommandPartType.Buffer &&
                (it.value.equals("all", ignoreCase = true) || it.value.equals(bufferValue, ignoreCase = true))
        }
    }
    return partsWithoutConflictingBuffers + normalizedPart
}

fun logCommandPartSummary(part: LogCommandPart): String {
    return part.toLogcatArgs().joinToString(" ")
}

private fun LogCommandPart.toLogcatArgs(): List<String> {
    val normalized = normalizedOrNull() ?: return emptyList()
    return when (normalized.type) {
        LogCommandPartType.Buffer -> listOf("-b", normalized.value)
        LogCommandPartType.Format -> listOf("-v", normalized.value)
        LogCommandPartType.Filter -> listOf(normalized.value)
        LogCommandPartType.Regex -> listOf("-e", normalized.value)
        LogCommandPartType.Pid -> listOf("--pid=${normalized.value}")
        LogCommandPartType.MaxCount -> listOf("-m", normalized.value)
        LogCommandPartType.Recent -> listOf("-T", normalized.value)
        LogCommandPartType.Silent -> listOf("-s")
        LogCommandPartType.Dividers -> listOf("-D")
    }
}

private fun displayCommand(parts: List<String>): String {
    return parts.joinToString(" ") { it.displayQuoted() }
}

private fun String.displayQuoted(): String {
    if (isEmpty()) return "\"\""
    val needsQuoting = any { it.isWhitespace() || it == '"' || it == '\'' || it == '&' || it == '|' || it == ';' }
    if (!needsQuoting) return this
    return "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

interface DeviceLogAdb {
    suspend fun captureFullLogs(
        deviceSerial: String,
        deviceModel: String,
        commandPreset: LogCommandPreset,
        logCommand: (String) -> Unit,
        onProgress: (DeviceLogCaptureProgress) -> Unit,
    ): DeviceLogCaptureResult

    fun stopCurrentCapture()
}

interface LogCommandPresetRepository {
    fun loadPresets(): List<LogCommandPreset>

    fun savePresets(presets: List<LogCommandPreset>)
}

expect fun createDeviceLogAdb(): DeviceLogAdb

expect fun createLogCommandPresetRepository(): LogCommandPresetRepository
