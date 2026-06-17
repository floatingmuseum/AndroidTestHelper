package com.floatingmuseum.android.test.helper.devicelog

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceLogAdbTest {
    @Test
    fun testBuildDeviceLogFileNameUsesModelSerialAndTimestamp() {
        val capturedAt = LocalDateTime.of(2026, 6, 17, 15, 4, 9)

        val fileName = buildDeviceLogFileName(
            deviceModel = "SM-X700",
            deviceSerial = "R58M123ABC",
            capturedAt = capturedAt,
        )

        assertEquals("SM-X700_R58M123ABC_20260617_150409.log", fileName)
    }

    @Test
    fun testBuildDeviceLogFileNameSanitizesPathTokens() {
        val capturedAt = LocalDateTime.of(2026, 6, 17, 15, 4, 9)

        val fileName = buildDeviceLogFileName(
            deviceModel = "Pixel Tablet/Debug",
            deviceSerial = "192.168.1.5:5555",
            capturedAt = capturedAt,
        )

        assertEquals("Pixel_Tablet_Debug_192.168.1.5_5555_20260617_150409.log", fileName)
    }
}
