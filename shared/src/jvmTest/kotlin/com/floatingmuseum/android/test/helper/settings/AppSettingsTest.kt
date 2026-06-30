package com.floatingmuseum.android.test.helper.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class AppSettingsTest {
    @Test
    fun fileManagerDefaultRootPathDefaultsToSdcard() {
        assertEquals("/sdcard", AppSettings().fileManagerDefaultRootPath)
    }

    @Test
    fun languageDefaultsToSystemLanguageMode() {
        assertEquals(null, AppSettings().language)
    }

    @Test
    fun effectiveLanguageUsesSystemLanguageWithoutOverride() {
        val settings = AppSettings(language = null)

        assertEquals(AppLanguage.English, settings.effectiveLanguage(AppLanguage.English))
        assertEquals(AppLanguage.SimplifiedChinese, settings.effectiveLanguage(AppLanguage.SimplifiedChinese))
    }

    @Test
    fun effectiveLanguageUsesManualOverride() {
        val settings = AppSettings(language = AppLanguage.SimplifiedChinese)

        assertEquals(AppLanguage.SimplifiedChinese, settings.effectiveLanguage(AppLanguage.English))
    }

    @Test
    fun fileManagerDefaultRootPathRejectsUnsupportedValues() {
        val settings = AppSettings(fileManagerDefaultRootPath = "/data").normalized()

        assertEquals("/sdcard", settings.fileManagerDefaultRootPath)
    }

    @Test
    fun customAdbPathRejectsBlankValues() {
        val settings = AppSettings(customAdbPath = "   ").normalized()

        assertEquals(null, settings.customAdbPath)
    }

    @Test
    fun customAdbPathTrimsValue() {
        val settings = AppSettings(customAdbPath = "  C:\\platform-tools\\adb.exe  ").normalized()

        assertEquals("C:\\platform-tools\\adb.exe", settings.customAdbPath)
    }

    @Test
    fun customScrcpyPathRejectsBlankValues() {
        val settings = AppSettings(customScrcpyPath = "   ").normalized()

        assertEquals(null, settings.customScrcpyPath)
    }

    @Test
    fun customScrcpyPathTrimsValue() {
        val settings = AppSettings(customScrcpyPath = "  C:\\scrcpy\\scrcpy.exe  ").normalized()

        assertEquals("C:\\scrcpy\\scrcpy.exe", settings.customScrcpyPath)
    }
}
