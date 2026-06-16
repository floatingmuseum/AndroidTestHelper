package com.floatingmuseum.android.test.helper.adb

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
        )

        assertEquals(2, devices.size)
        assertEquals("R52T90ABC12", devices[0].serialNumber)
        assertEquals("SM X700", devices[0].model)
        assertEquals("device", devices[0].state)
        assertEquals(true, devices[0].isReady)
        assertEquals("emulator-5554", devices[1].serialNumber)
        assertEquals("未知型号", devices[1].model)
        assertEquals("offline", devices[1].state)
        assertEquals(false, devices[1].isReady)
    }
}
