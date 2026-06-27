package com.floatingmuseum.android.test.helper.localization

import com.floatingmuseum.android.test.helper.settings.AppLanguage
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalizationTest {
    @Test
    fun localizedReturnsSimplifiedChineseFromResourceBundle() {
        assertEquals(
            "状态:",
            localized("command.status.prefix", language = AppLanguage.SimplifiedChinese),
        )
    }

    @Test
    fun localizedReturnsEnglishFromResourceBundle() {
        assertEquals(
            "Status:",
            localized("command.status.prefix", language = AppLanguage.English),
        )
    }

    @Test
    fun localizedFormatsPositionalArguments() {
        assertEquals(
            "ADB command failed with exit code 7",
            localized("adb.command_failed_with_exit_code_arg0", 7, language = AppLanguage.English),
        )
    }

    @Test
    fun localeResourceKeysUseReadableNames() {
        listOf("en", "zh-Hans").forEach { languageTag ->
            val resourceText = loadLocalizationResource(languageTag).orEmpty()
            val keys = Json.decodeFromString<Map<String, String>>(resourceText).keys

            assertEquals(emptyList(), keys.filter { it.startsWith("auto.") })
        }
    }

    @Test
    fun localeResourceKeysAreSorted() {
        listOf("en", "zh-Hans").forEach { languageTag ->
            val resourceText = loadLocalizationResource(languageTag).orEmpty()
            val keys = Json.decodeFromString<Map<String, String>>(resourceText).keys.toList()

            assertEquals(keys.sorted(), keys)
        }
    }
}
