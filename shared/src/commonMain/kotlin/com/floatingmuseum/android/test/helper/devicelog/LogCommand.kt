package com.floatingmuseum.android.test.helper.devicelog

import com.floatingmuseum.android.test.helper.localization.localized
import kotlinx.serialization.Serializable

const val LOG_COMMAND_SERIAL_PLACEHOLDER = "<serialNumber>"
const val NEW_LOGCAT_COMMAND = "adb -s <serialNumber> shell logcat"
const val DEFAULT_LOGCAT_COMMAND = "adb -s <serialNumber> shell logcat -b all -v threadtime -v year *:V"

@Serializable
data class CustomLogCommand(val name: String, val command: String)

@Serializable
data class LogCommandConfiguration(
    val commands: List<CustomLogCommand> = emptyList(),
    // null always identifies the built-in command; it is never stored as an editable preset.
    val selectedName: String? = null,
) {
    val selectedCommand: CustomLogCommand? get() = commands.find { it.name == selectedName }

    fun normalized(): LogCommandConfiguration {
        val valid = commands.map { it.copy(name = it.name.trim(), command = it.command.trim()) }
            .filter { it.name.isNotEmpty() && runCatching { parseCustomLogcatArguments(it.command) }.isSuccess }
            .distinctBy { it.name.lowercase() }
        return copy(commands = valid, selectedName = selectedName?.trim()?.takeIf { name -> valid.any { it.name == name } })
    }
}

interface LogCommandRepository {
    fun load(): LogCommandConfiguration
    fun save(configuration: LogCommandConfiguration)
}

expect fun createLogCommandRepository(): LogCommandRepository

/** Accept a copied adb command or just logcat. The device is always supplied by the current selection. */
fun parseCustomLogcatArguments(command: String): List<String> {
    val tokens = tokenizeLogCommand(command.trim())
    var index = 0
    if (tokens.getOrNull(index) in listOf("adb", "adb.exe")) {
        index++
        if (tokens.getOrNull(index) == "-s") {
            require(!tokens.getOrNull(index + 1).isNullOrBlank()) { localized("log.command.invalid") }
            index += 2
        }
    }
    if (tokens.getOrNull(index) == "shell") index++
    require(tokens.getOrNull(index) == "logcat") { localized("log.command.invalid") }
    val arguments = tokens.drop(index + 1)
    require(arguments.none {
        it == "-c" || it == "--clear" || it == "-B" || it == "--binary" || it == "--proto" ||
            it.startsWith("-f") || it == "--file" || it.startsWith("--file=")
    }) { localized("log.command.text_output_required") }
    return arguments
}

private fun tokenizeLogCommand(command: String): List<String> {
    require(command.none { it == '\n' || it == '\r' || it == '\u0000' }) { localized("log.command.invalid") }
    val tokens = mutableListOf<String>()
    val token = StringBuilder()
    var quote: Char? = null
    var started = false
    var index = 0
    fun finish() {
        if (started) tokens += token.toString()
        token.clear()
        started = false
    }
    while (index < command.length) {
        val char = command[index]
        when {
            char == '\\' && quote != '\'' -> {
                val next = command.getOrNull(index + 1)
                require(next != null) { localized("log.command.invalid") }
                // Retain regex escapes such as \d; only unescape delimiters and escaped quotes.
                if (next == '\\' || next == '"' || (quote == null && (next == '\'' || next.isWhitespace()))) {
                    token.append(next)
                    index++
                } else token.append(char)
                started = true
            }
            quote != null -> {
                if (char == quote) quote = null else token.append(char)
            }
            char == '\'' || char == '"' -> { quote = char; started = true }
            char.isWhitespace() -> finish()
            // The placeholder is display-only, not shell redirection.
            char == '<' && command.startsWith(LOG_COMMAND_SERIAL_PLACEHOLDER, index) -> {
                token.append(LOG_COMMAND_SERIAL_PLACEHOLDER)
                index += LOG_COMMAND_SERIAL_PLACEHOLDER.length - 1
                started = true
            }
            else -> {
                require(char !in "|&;<>`$()") { localized("log.command.no_shell") }
                token.append(char)
                started = true
            }
        }
        index++
    }
    require(quote == null) { localized("log.command.invalid") }
    finish()
    return tokens
}

// adb shell joins its arguments into a remote shell command. Quote every custom argument there,
// including regexes, wildcards and strings that contain quotes or shell substitution characters.
internal fun quoteLogcatArgument(value: String): String = "'" + value.replace("'", "'\\''") + "'"
