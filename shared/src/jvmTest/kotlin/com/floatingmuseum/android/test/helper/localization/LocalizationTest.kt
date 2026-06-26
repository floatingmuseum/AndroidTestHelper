package com.floatingmuseum.android.test.helper.localization

import com.floatingmuseum.android.test.helper.settings.AppLanguage
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
            localized("auto.adb_command_failed_with_exit_code_0.c428ca41", 7, language = AppLanguage.English),
        )
    }
}
