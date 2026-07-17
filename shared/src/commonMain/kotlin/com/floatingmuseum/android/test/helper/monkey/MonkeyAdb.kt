package com.floatingmuseum.android.test.helper.monkey

data class MonkeyRunResult(
    val command: MonkeyAdbCommand,
    val reportFilePath: String,
    val summary: MonkeyRunSummary,
    val endState: MonkeyRunEndState,
    val cleanupMessage: String? = null,
)

interface MonkeyAdb {
    suspend fun runMonkey(
        deviceSerial: String,
        deviceModel: String,
        form: MonkeyTestForm,
        logCommand: (String) -> Unit,
        onOutputLine: (String) -> Unit,
    ): MonkeyRunResult

    fun stopCurrentRun()
}

interface MonkeyPresetRepository {
    fun loadPresets(): List<MonkeyPreset>

    fun savePresets(presets: List<MonkeyPreset>)
}

expect fun createMonkeyAdb(): MonkeyAdb

expect fun createMonkeyPresetRepository(): MonkeyPresetRepository
