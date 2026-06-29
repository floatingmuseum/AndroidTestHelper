package com.floatingmuseum.android.test.helper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppDeviceTransportTest {
    @Test
    fun extractsAdbDeviceNotFoundTransport() {
        val message = """
            错误: 读取系统属性失败 - ADB 命令失败，退出码 1
            adb -s 192.168.0.6:37107 shell getprop
            adb.exe: device '192.168.0.6:37107' not found
        """.trimIndent()

        assertEquals("192.168.0.6:37107", extractAdbDeviceNotFoundTransport(message))
    }

    @Test
    fun ignoresOtherAdbErrors() {
        assertNull(extractAdbDeviceNotFoundTransport("adb.exe: device offline"))
    }
}
