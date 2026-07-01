package com.floatingmuseum.android.test.helper.intent

import kotlinx.serialization.Serializable

@Serializable
data class IntentTestForm(
    val mode: IntentCommandMode = IntentCommandMode.Start,
    val packageName: String = "",
    val className: String = "",
    val action: String = "android.intent.action.VIEW",
    val dataUri: String = "",
    val categories: List<String> = emptyList(),
    val extras: List<IntentExtra> = emptyList(),
    val startFlags: Set<IntentStartFlag> = emptySet(),
    val broadcastFlags: Set<IntentBroadcastFlag> = emptySet(),
) {
    fun normalized(): IntentTestForm {
        return copy(
            packageName = packageName.trim(),
            className = className.trim(),
            action = action.trim(),
            dataUri = dataUri.trim(),
            categories = categories.map { it.trim() }.filter { it.isNotBlank() },
            extras = extras.map { it.normalized() }.filter { it.key.isNotBlank() },
        )
    }
}

@Serializable
enum class IntentCommandMode {
    Start,
    Broadcast,
}

@Serializable
data class IntentExtra(
    val key: String = "",
    val type: IntentExtraType = IntentExtraType.StringValue,
    val value: String = "",
) {
    fun normalized(): IntentExtra {
        return copy(
            key = key.trim(),
            value = value.trim(),
        )
    }
}

@Serializable
enum class IntentExtraType(val adbOption: String) {
    StringValue("--es"),
    IntValue("--ei"),
    LongValue("--el"),
    BooleanValue("--ez"),
    StringArray("--esa"),
}

@Serializable
enum class IntentStartFlag(val adbOption: String) {
    NewTask("--activity-new-task"),
    ClearTop("--activity-clear-top"),
    ClearTask("--activity-clear-task"),
    SingleTop("--activity-single-top"),
}

@Serializable
enum class IntentBroadcastFlag(val adbOption: String) {
    ReceiverForeground("--receiver-foreground"),
    ReceiverIncludeBackground("--receiver-include-background"),
}

data class IntentAdbCommand(
    val args: List<String>,
    val displayCommand: String,
)

data class IntentExecutionResult(
    val command: IntentAdbCommand,
    val output: String,
    val isSuccess: Boolean,
)

@Serializable
data class IntentTemplate(
    val id: String,
    val name: String,
    val form: IntentTestForm,
)

data class IntentValidationResult(
    val errors: List<String>,
) {
    val isValid: Boolean get() = errors.isEmpty()
}

fun buildIntentAdbCommand(
    deviceSerial: String,
    form: IntentTestForm,
): IntentAdbCommand {
    val normalized = form.normalized()
    val shellArgs = buildList {
        add("am")
        add(
            when (normalized.mode) {
                IntentCommandMode.Start -> "start"
                IntentCommandMode.Broadcast -> "broadcast"
            },
        )
        addAll(componentArgs(normalized))
        normalized.action.takeIf { it.isNotBlank() }?.let {
            add("-a")
            add(it)
        }
        normalized.dataUri.takeIf { it.isNotBlank() }?.let {
            add("-d")
            add(it)
        }
        normalized.categories.forEach { category ->
            add("-c")
            add(category)
        }
        when (normalized.mode) {
            IntentCommandMode.Start -> normalized.startFlags.forEach { add(it.adbOption) }
            IntentCommandMode.Broadcast -> normalized.broadcastFlags.forEach { add(it.adbOption) }
        }
        normalized.extras.forEach { extra ->
            add(extra.type.adbOption)
            add(extra.key)
            add(extra.adbValue())
        }
    }
    val args = listOf("-s", deviceSerial, "shell") + shellArgs
    return IntentAdbCommand(
        args = args,
        displayCommand = displayCommand(deviceSerial, shellArgs),
    )
}

fun validateIntentForm(form: IntentTestForm): IntentValidationResult {
    val normalized = form.copy(
        packageName = form.packageName.trim(),
        className = form.className.trim(),
        action = form.action.trim(),
        dataUri = form.dataUri.trim(),
        categories = form.categories.map { it.trim() }.filter { it.isNotBlank() },
        extras = form.extras.map { it.normalized() },
    )
    val errors = mutableListOf<String>()
    val hasComponent = normalized.packageName.isNotBlank() && normalized.className.isNotBlank()
    val hasAction = normalized.action.isNotBlank()
    val hasData = normalized.dataUri.isNotBlank()

    if (!hasComponent && !hasAction && !hasData) {
        errors += "intent.validation.target_required"
    }
    if (normalized.className.isNotBlank() && normalized.packageName.isBlank()) {
        errors += "intent.validation.package_required_for_class"
    }

    normalized.extras.forEachIndexed { index, extra ->
        if (extra.key.isBlank()) {
            errors += "intent.validation.extra_key_required:${index + 1}"
        }
        when (extra.type) {
            IntentExtraType.IntValue -> if (extra.value.toIntOrNull() == null) {
                errors += "intent.validation.extra_int_required:${index + 1}"
            }
            IntentExtraType.LongValue -> if (extra.value.toLongOrNull() == null) {
                errors += "intent.validation.extra_long_required:${index + 1}"
            }
            IntentExtraType.BooleanValue -> if (!extra.value.isBooleanLiteral()) {
                errors += "intent.validation.extra_boolean_required:${index + 1}"
            }
            IntentExtraType.StringValue,
            IntentExtraType.StringArray -> Unit
        }
    }

    return IntentValidationResult(errors)
}

fun intentTemplateSummary(form: IntentTestForm): String {
    val normalized = form.normalized()
    return listOfNotNull(
        normalized.packageName.takeIf { it.isNotBlank() },
        normalized.action.takeIf { it.isNotBlank() },
        normalized.dataUri.takeIf { it.isNotBlank() },
    ).joinToString(" · ").ifBlank {
        when (normalized.mode) {
            IntentCommandMode.Start -> "am start"
            IntentCommandMode.Broadcast -> "am broadcast"
        }
    }
}

private fun componentArgs(form: IntentTestForm): List<String> {
    val packageName = form.packageName
    val className = form.className
    return when {
        packageName.isNotBlank() && className.isNotBlank() -> listOf("-n", componentName(packageName, className))
        packageName.isNotBlank() -> listOf("-p", packageName)
        else -> emptyList()
    }
}

private fun componentName(packageName: String, className: String): String {
    val normalizedClass = when {
        className.startsWith(".") -> className
        className.startsWith("$packageName.") -> className
        else -> className
    }
    return "$packageName/$normalizedClass"
}

private fun IntentExtra.adbValue(): String {
    return when (type) {
        IntentExtraType.BooleanValue -> value.trim().lowercase()
        IntentExtraType.StringArray -> value.split(",")
            .joinToString(",") { it.trim() }
        IntentExtraType.StringValue,
        IntentExtraType.IntValue,
        IntentExtraType.LongValue -> value.trim()
    }
}

private fun String.isBooleanLiteral(): Boolean {
    return equals("true", ignoreCase = true) || equals("false", ignoreCase = true)
}

private fun displayCommand(deviceSerial: String, shellArgs: List<String>): String {
    return (listOf("adb", "-s", deviceSerial, "shell") + shellArgs)
        .joinToString(" ") { it.displayQuoted() }
}

private fun String.displayQuoted(): String {
    if (isEmpty()) return "\"\""
    val needsQuoting = any { it.isWhitespace() || it == '"' || it == '\'' || it == '&' || it == '|' || it == ';' }
    if (!needsQuoting) return this
    return "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
