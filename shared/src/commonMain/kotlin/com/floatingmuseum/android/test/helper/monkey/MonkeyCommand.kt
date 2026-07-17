package com.floatingmuseum.android.test.helper.monkey

import kotlin.random.Random
import kotlinx.serialization.Serializable

@Serializable
enum class MonkeyTargetScope {
    WholeDevice,
    Packages,
}

@Serializable
data class MonkeyEventPercentages(
    val touch: String = "",
    val motion: String = "",
    val trackball: String = "",
    val navigation: String = "",
    val majorNavigation: String = "",
    val systemKeys: String = "",
    val appSwitch: String = "",
    val anyEvent: String = "",
)

@Serializable
data class MonkeyTestForm(
    val targetScope: MonkeyTargetScope = MonkeyTargetScope.Packages,
    val packages: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val eventCount: String = "1000",
    val throttleMs: String = "100",
    val seed: String = "",
    val verbosity: Int = 1,
    val eventPercentages: MonkeyEventPercentages = MonkeyEventPercentages(),
    val ignoreCrashes: Boolean = false,
    val ignoreTimeouts: Boolean = false,
    val ignoreSecurityExceptions: Boolean = false,
    val killProcessAfterError: Boolean = false,
    val monitorNativeCrashes: Boolean = true,
) {
    fun normalized(): MonkeyTestForm = copy(
        packages = packages.map(String::trim).filter(String::isNotBlank),
        categories = categories.map(String::trim).filter(String::isNotBlank),
        eventCount = eventCount.trim(),
        throttleMs = throttleMs.trim(),
        seed = seed.trim(),
        verbosity = verbosity.coerceIn(0, 2),
        eventPercentages = eventPercentages.normalized(),
    )
}

@Serializable
data class MonkeyPreset(
    val id: String,
    val name: String,
    val form: MonkeyTestForm,
)

data class MonkeyValidationError(
    val key: String,
    val argument: String? = null,
)

data class MonkeyValidationResult(
    val errors: List<MonkeyValidationError>,
) {
    val isValid: Boolean get() = errors.isEmpty()
}

data class MonkeyAdbCommand(
    val args: List<String>,
    val displayCommand: String,
)

enum class MonkeyRunEndState {
    Completed,
    CompletedWithIncidents,
    AbortedByIncident,
    Stopped,
    Interrupted,
    StopCleanupFailed,
}

data class MonkeyDroppedEvents(
    val keys: Long = 0,
    val pointers: Long = 0,
    val trackballs: Long = 0,
    val flips: Long = 0,
    val rotations: Long = 0,
)

data class MonkeyRunSummary(
    val plannedEvents: Long,
    val injectedEvents: Long = 0,
    val crashCount: Int = 0,
    val anrCount: Int = 0,
    val nativeCrashCount: Int = 0,
    val droppedEvents: MonkeyDroppedEvents = MonkeyDroppedEvents(),
    val outputLineCount: Long = 0,
    val monkeyFinished: Boolean = false,
    val aborted: Boolean = false,
) {
    val incidentCount: Int get() = crashCount + anrCount + nativeCrashCount
}

internal class MonkeyReportSummaryBuilder(
    private val plannedEvents: Long,
) {
    private var injectedEvents: Long = 0
    private var crashCount: Int = 0
    private var anrCount: Int = 0
    private var nativeCrashCount: Int = 0
    private var droppedEvents: MonkeyDroppedEvents = MonkeyDroppedEvents()
    private var outputLineCount: Long = 0
    private var monkeyFinished: Boolean = false
    private var aborted: Boolean = false

    fun accept(line: String) {
        outputLineCount += 1
        val trimmed = line.trim()
        if (trimmed.startsWith("Events injected:")) {
            injectedEvents = trimmed.substringAfter(":").trim().toLongOrNull() ?: injectedEvents
        }
        if (trimmed.startsWith("// CRASH:")) crashCount += 1
        if (trimmed.startsWith("// NOT RESPONDING:")) anrCount += 1
        if (trimmed.contains("New native crash detected")) nativeCrashCount += 1
        if (trimmed == "// Monkey finished") monkeyFinished = true
        if (trimmed.contains("Monkey aborted due to error")) aborted = true
        DroppedEventsRegex.find(trimmed)?.let { match ->
            droppedEvents = MonkeyDroppedEvents(
                keys = match.groupValues[1].toLongOrNull() ?: 0,
                pointers = match.groupValues[2].toLongOrNull() ?: 0,
                trackballs = match.groupValues[3].toLongOrNull() ?: 0,
                flips = match.groupValues[4].toLongOrNull() ?: 0,
                rotations = match.groupValues[5].toLongOrNull() ?: 0,
            )
        }
    }

    fun build(): MonkeyRunSummary = MonkeyRunSummary(
        plannedEvents = plannedEvents,
        injectedEvents = injectedEvents,
        crashCount = crashCount,
        anrCount = anrCount,
        nativeCrashCount = nativeCrashCount,
        droppedEvents = droppedEvents,
        outputLineCount = outputLineCount,
        monkeyFinished = monkeyFinished,
        aborted = aborted,
    )
}

fun generateMonkeySeed(): Long = Random.nextLong(1, Long.MAX_VALUE)

fun validateMonkeyForm(form: MonkeyTestForm): MonkeyValidationResult {
    val normalized = form.normalized()
    val errors = mutableListOf<MonkeyValidationError>()
    if (normalized.targetScope == MonkeyTargetScope.Packages && normalized.packages.isEmpty()) {
        errors += MonkeyValidationError("monkey.validation.package_required")
    }
    normalized.packages.forEach { packageName ->
        if (!PackageNameRegex.matches(packageName)) {
            errors += MonkeyValidationError("monkey.validation.invalid_package_arg0", packageName)
        }
    }
    normalized.packages.findDuplicateIgnoringCase()?.let {
        errors += MonkeyValidationError("monkey.validation.duplicate_package_arg0", it)
    }
    normalized.categories.findDuplicateIgnoringCase()?.let {
        errors += MonkeyValidationError("monkey.validation.duplicate_category_arg0", it)
    }
    if (normalized.eventCount.toLongOrNull()?.let { it in 1..Int.MAX_VALUE.toLong() } != true) {
        errors += MonkeyValidationError("monkey.validation.event_count")
    }
    if (normalized.throttleMs.toLongOrNull()?.let { it >= 0 } != true) {
        errors += MonkeyValidationError("monkey.validation.throttle")
    }
    if (normalized.seed.toLongOrNull() == null) {
        errors += MonkeyValidationError("monkey.validation.seed")
    }
    if (normalized.verbosity !in 0..2) {
        errors += MonkeyValidationError("monkey.validation.verbosity")
    }

    var percentageTotal = 0
    normalized.eventPercentages.entries().forEach { (key, value) ->
        if (value.isBlank()) return@forEach
        val percentage = value.toIntOrNull()
        if (percentage == null || percentage !in 0..100) {
            errors += MonkeyValidationError("monkey.validation.percentage_arg0", key)
        } else {
            percentageTotal += percentage
        }
    }
    if (percentageTotal > 100) {
        errors += MonkeyValidationError("monkey.validation.percentage_total")
    }
    return MonkeyValidationResult(errors.distinct())
}

fun buildMonkeyAdbCommand(
    deviceSerial: String,
    form: MonkeyTestForm,
): MonkeyAdbCommand {
    val normalized = form.normalized()
    val monkeyArgs = buildList {
        add("monkey")
        if (normalized.targetScope == MonkeyTargetScope.Packages) {
            normalized.packages.forEach { packageName ->
                add("-p")
                add(packageName)
            }
        }
        normalized.categories.forEach { category ->
            add("-c")
            add(category)
        }
        add("-s")
        add(normalized.seed)
        add("--throttle")
        add(normalized.throttleMs)
        repeat(normalized.verbosity) { add("-v") }
        normalized.eventPercentages.entries().forEach { (option, value) ->
            if (value.isNotBlank()) {
                add(option)
                add(value)
            }
        }
        if (normalized.ignoreCrashes) add("--ignore-crashes")
        if (normalized.ignoreTimeouts) add("--ignore-timeouts")
        if (normalized.ignoreSecurityExceptions) add("--ignore-security-exceptions")
        if (normalized.killProcessAfterError) add("--kill-process-after-error")
        if (normalized.monitorNativeCrashes) add("--monitor-native-crashes")
        add(normalized.eventCount)
    }
    val args = listOf("-s", deviceSerial, "shell") + monkeyArgs
    val displayParts = listOf("adb", "-s", deviceSerial, "shell") + monkeyArgs
    return MonkeyAdbCommand(
        args = args,
        displayCommand = displayParts.joinToString(" ") { it.displayQuoted() },
    )
}

fun resolveMonkeyRunEndState(
    exitCode: Int?,
    stopRequested: Boolean,
    cleanupFailed: Boolean,
    summary: MonkeyRunSummary,
): MonkeyRunEndState {
    if (cleanupFailed) return MonkeyRunEndState.StopCleanupFailed
    if (stopRequested) return MonkeyRunEndState.Stopped
    if (summary.aborted || (summary.incidentCount > 0 && !summary.monkeyFinished)) {
        return MonkeyRunEndState.AbortedByIncident
    }
    if (exitCode == 0 && (summary.monkeyFinished || summary.injectedEvents >= summary.plannedEvents)) {
        return if (summary.incidentCount > 0) {
            MonkeyRunEndState.CompletedWithIncidents
        } else {
            MonkeyRunEndState.Completed
        }
    }
    return MonkeyRunEndState.Interrupted
}

fun buildMonkeyReportFileName(
    deviceModel: String,
    form: MonkeyTestForm,
    timestampToken: String,
): String {
    val target = when (form.targetScope) {
        MonkeyTargetScope.WholeDevice -> "whole_device"
        MonkeyTargetScope.Packages -> form.packages.firstOrNull().orEmpty().ifBlank { "packages" }
    }
    val modelToken = deviceModel.toFileToken().ifBlank { "unknown_device" }
    val targetToken = target.toFileToken().ifBlank { "target" }
    return "monkey_${modelToken}_${targetToken}_${timestampToken.toFileToken()}.log"
}

private fun MonkeyEventPercentages.normalized(): MonkeyEventPercentages = copy(
    touch = touch.trim(),
    motion = motion.trim(),
    trackball = trackball.trim(),
    navigation = navigation.trim(),
    majorNavigation = majorNavigation.trim(),
    systemKeys = systemKeys.trim(),
    appSwitch = appSwitch.trim(),
    anyEvent = anyEvent.trim(),
)

internal fun MonkeyEventPercentages.entries(): List<Pair<String, String>> = listOf(
    "--pct-touch" to touch,
    "--pct-motion" to motion,
    "--pct-trackball" to trackball,
    "--pct-nav" to navigation,
    "--pct-majornav" to majorNavigation,
    "--pct-syskeys" to systemKeys,
    "--pct-appswitch" to appSwitch,
    "--pct-anyevent" to anyEvent,
)

private fun List<String>.findDuplicateIgnoringCase(): String? {
    val seen = mutableSetOf<String>()
    return firstOrNull { !seen.add(it.lowercase()) }
}

private fun String.displayQuoted(): String {
    if (isEmpty()) return "\"\""
    if (none { it.isWhitespace() || it in listOf('"', '\'', '&', '|', ';') }) return this
    return "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

private fun String.toFileToken(): String = trim()
    .replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_")
    .trim('_')

private val PackageNameRegex = Regex("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*$")
private val DroppedEventsRegex = Regex(
    """:Dropped:\s+keys=(\d+)\s+pointers=(\d+)\s+trackballs=(\d+)\s+flips=(\d+)\s+rotations=(\d+)""",
)
