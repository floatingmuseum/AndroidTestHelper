package com.floatingmuseum.android.test.helper.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class AppSettingsTest {
    @Test
    fun fileManagerDefaultRootPathDefaultsToSdcard() {
        assertEquals("/sdcard", AppSettings().fileManagerDefaultRootPath)
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
}
