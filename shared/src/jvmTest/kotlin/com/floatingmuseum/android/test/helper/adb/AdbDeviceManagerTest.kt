package com.floatingmuseum.android.test.helper.adb

import com.floatingmuseum.android.test.helper.settings.AppLanguage
import com.floatingmuseum.android.test.helper.settings.AppSettings
import com.floatingmuseum.android.test.helper.settings.AppSettingsShared
import kotlin.test.Test
import kotlin.test.assertEquals

class AdbDeviceManagerTest {
    @Test
    fun parsesAdbDevicesOutput() {
        val devices = AdbShell.parseAdbDevices(
            """
            List of devices attached
            R52T90ABC12            device product:gts8wifi model:SM_X700 device:gts8wifi transport_id:1
            emulator-5554          offline transport_id:2
            """.trimIndent(),
            unknownModelFallback = "Unknown model",
        )

        assertEquals(2, devices.size)
        assertEquals("R52T90ABC12", devices[0].serialNumber)
        assertEquals("SM X700", devices[0].model)
        assertEquals("device", devices[0].state)
        assertEquals(true, devices[0].isReady)
        assertEquals("emulator-5554", devices[1].serialNumber)
        assertEquals("Unknown model", devices[1].model)
        assertEquals("offline", devices[1].state)
        assertEquals(false, devices[1].isReady)
    }

    @Test
    fun parsesAdbDevicesOutputWithCurrentLanguageUnknownModelFallback() {
        val previousSettings = AppSettingsShared.currentSettings
        try {
            AppSettingsShared.updateSettings(AppSettings(language = AppLanguage.SimplifiedChinese))
            val zhDevices = AdbShell.parseAdbDevices("emulator-5554 offline transport_id:2")
            assertEquals("未知型号", zhDevices.single().model)

            AppSettingsShared.updateSettings(AppSettings(language = AppLanguage.English))
            val enDevices = AdbShell.parseAdbDevices("emulator-5554 offline transport_id:2")
            assertEquals("Unknown model", enDevices.single().model)
        } finally {
            AppSettingsShared.updateSettings(previousSettings)
        }
    }

    @Test
    fun parsesAdbVersionOutputForDisplay() {
        val version = AdbShell.parseAdbVersion(
            """
            Android Debug Bridge version 1.0.41
            Version 36.0.0-13206524
            Installed as D:\workspace\platform-tools\adb.exe
            Running on Windows 10.0.26100
            """.trimIndent(),
        )

        assertEquals("Android Debug Bridge version 1.0.41 / Version 36.0.0-13206524", version)
    }
}
