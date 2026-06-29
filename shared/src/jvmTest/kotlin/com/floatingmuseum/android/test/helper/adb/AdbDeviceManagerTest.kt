package com.floatingmuseum.android.test.helper.adb

import com.floatingmuseum.android.test.helper.AndroidDevice
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
        assertEquals("R52T90ABC12", devices[0].transportId)
        assertEquals("SM X700", devices[0].model)
        assertEquals("device", devices[0].state)
        assertEquals(true, devices[0].isReady)
        assertEquals("emulator-5554", devices[1].serialNumber)
        assertEquals("emulator-5554", devices[1].transportId)
        assertEquals("Unknown model", devices[1].model)
        assertEquals("offline", devices[1].state)
        assertEquals(false, devices[1].isReady)
    }

    @Test
    fun parsesWirelessAdbTransportSeparatelyFromHardwareSerial() {
        val transport = "adb-HA1WXSKY-DOTVjI._adb-tls-connect._tcp"
        val devices = AdbShell.parseAdbDevices(
            """
            List of devices attached
            $transport device product:tablet model:Demo_Tablet device:tablet transport_id:7
            """.trimIndent(),
            unknownModelFallback = "Unknown model",
        )

        assertEquals(1, devices.size)
        assertEquals("HA1WXSKY", devices[0].serialNumber)
        assertEquals(transport, devices[0].transportId)
        assertEquals(true, devices[0].hasDistinctTransport)
    }

    @Test
    fun detectsIpPortTransport() {
        assertEquals(true, isIpPortTransport("192.168.0.6:37107"))
        assertEquals(true, isIpPortTransport("[fe80::1234]:37107"))
        assertEquals(false, isIpPortTransport("HA1K5S7Y"))
        assertEquals(false, isIpPortTransport("adb-HA1WXSKY-DOTVjI._adb-tls-connect._tcp"))
    }

    @Test
    fun deduplicatesManualWirelessConnectAndStaleMdnsTransportBySerial() {
        val devices = deduplicateDevicesBySerial(
            listOf(
                AndroidDevice(
                    serialNumber = "HA1K5S7Y",
                    model = "LENOVO TB X306NC PRC",
                    state = "device",
                ),
                AndroidDevice(
                    serialNumber = "HA1WXSKY",
                    model = "TB330FU",
                    state = "device",
                    transportId = "192.168.0.6:37107",
                ),
                AndroidDevice(
                    serialNumber = "HA1WXSKY",
                    model = "TB330FU",
                    state = "offline",
                    transportId = "adb-HA1WXSKY-DOTVjI._adb-tls-connect._tcp",
                ),
            ),
        )

        assertEquals(2, devices.size)
        assertEquals("HA1K5S7Y", devices[0].serialNumber)
        assertEquals("HA1WXSKY", devices[1].serialNumber)
        assertEquals("192.168.0.6:37107", devices[1].transportId)
        assertEquals("device", devices[1].state)
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

    @Test
    fun buildsWirelessPairCommand() {
        val command = buildWirelessPairCommand(
            host = " 192.168.1.23 ",
            pairingPort = " 37123 ",
            pairingCode = " 123456 ",
        )

        assertEquals(listOf("pair", "192.168.1.23:37123", "123456"), command.args)
        assertEquals("adb pair 192.168.1.23:37123 123456", command.displayCommand)
    }

    @Test
    fun buildsWirelessConnectCommand() {
        val command = buildWirelessConnectCommand(
            host = " 192.168.1.23 ",
            debugPort = " 5555 ",
        )

        assertEquals(listOf("connect", "192.168.1.23:5555"), command.args)
        assertEquals("adb connect 192.168.1.23:5555", command.displayCommand)
    }
}
