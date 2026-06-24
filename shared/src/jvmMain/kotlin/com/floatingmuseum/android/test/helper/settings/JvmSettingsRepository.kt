package com.floatingmuseum.android.test.helper.settings

import com.floatingmuseum.android.test.helper.AppRuntimePaths
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File

class JvmSettingsRepository : SettingsRepository {
    private val settingsFile: File
        get() = AppRuntimePaths.cacheDirectory().resolve("settings.json")

    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    override fun loadSettings(): AppSettings {
        if (!settingsFile.exists()) return AppSettings()
        return try {
            json.decodeFromString<AppSettings>(settingsFile.readText())
        } catch (e: Exception) {
            e.printStackTrace()
            AppSettings()
        }
    }

    override fun saveSettings(settings: AppSettings) {
        try {
            settingsFile.parentFile?.mkdirs()
            val jsonText = json.encodeToString(settings)
            settingsFile.writeText(jsonText)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

actual fun createSettingsRepository(): SettingsRepository = JvmSettingsRepository()
