package com.floatingmuseum.android.test.helper.settings

import com.floatingmuseum.android.test.helper.AppRuntimePaths
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.util.Locale

class JvmSettingsRepository(
    private val settingsFileProvider: () -> File = {
        AppRuntimePaths.cacheDirectory().resolve("settings.json")
    },
) : SettingsRepository {
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    override fun loadSettings(): AppSettings {
        val settingsFile = settingsFileProvider()
        if (!settingsFile.exists()) return AppSettings()
        return try {
            val jsonText = settingsFile.readText()
            migrateLoadedSettings(jsonText, json.decodeFromString<AppSettings>(jsonText))
        } catch (e: Exception) {
            e.printStackTrace()
            AppSettings()
        }
    }

    override fun saveSettings(settings: AppSettings) {
        try {
            val settingsFile = settingsFileProvider()
            settingsFile.parentFile?.mkdirs()
            val jsonText = json.encodeToString(settings)
            settingsFile.writeText(jsonText)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun migrateLoadedSettings(jsonText: String, settings: AppSettings): AppSettings {
        val root = json.parseToJsonElement(jsonText).jsonObject
        val hasStoredFormat = root.containsKey("screenRecordFormat")
        return settings.copy(
            screenRecordFormat = if (hasStoredFormat) settings.screenRecordFormat else ScreenRecordFormat.Mp4,
            screenRecordFormatUserSelected = settings.screenRecordFormatUserSelected || hasStoredFormat,
        )
    }
}

actual fun createSettingsRepository(): SettingsRepository = JvmSettingsRepository()

actual fun systemDefaultAppLanguage(): AppLanguage {
    return if (Locale.getDefault().language.equals("zh", ignoreCase = true)) {
        AppLanguage.SimplifiedChinese
    } else {
        AppLanguage.English
    }
}
