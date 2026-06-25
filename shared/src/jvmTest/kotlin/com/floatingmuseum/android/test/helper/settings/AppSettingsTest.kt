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
}
