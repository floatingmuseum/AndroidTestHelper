package com.floatingmuseum.android.test.helper.device

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeviceAdbTest {

    @Test
    fun testParseScreenSize() {
        val output = """
            Physical size: 1600x2560
        """.trimIndent()
        assertEquals("1600x2560", parseScreenSize(output))

        val outputUnknown = """
            Something else
        """.trimIndent()
        assertEquals("未知", parseScreenSize(outputUnknown))
    }

    @Test
    fun testParseBatteryLevel() {
        val output = """
            Current Battery Service state:
              AC powered: false
              USB powered: true
              level: 95
              scale: 100
        """.trimIndent()
        assertEquals(95, parseBatteryLevel(output))

        val outputUnknown = """
            level unknown
        """.trimIndent()
        assertNull(parseBatteryLevel(outputUnknown))
    }

    @Test
    fun testParseIpAddress() {
        val output = """
            1: lo: <LOOPBACK,UP,LOWER_UP> mtu 65536 qdisc noqueue state UNKNOWN group default qlen 1000
                link/loopback 00:00:00:00:00:00 brd 00:00:00:00:00:00
                inet 127.0.0.1/8 scope host lo
                   valid_lft forever preferred_lft forever
            8: wlan0: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc mq state UP group default qlen 3000
                link/ether 00:11:22:33:44:55 brd ff:ff:ff:ff:ff:ff
                inet 192.168.1.100/24 brd 192.168.1.255 scope global wlan0
                   valid_lft forever preferred_lft forever
        """.trimIndent()
        assertEquals("192.168.1.100", parseIpAddress(output))

        val outputUnknown = """
            loopback only:
            inet 127.0.0.1/8 scope host lo
        """.trimIndent()
        assertEquals("未知", parseIpAddress(outputUnknown))
    }

    @Test
    fun testParseSystemProperties() {
        val output = """
            [ro.product.model]: [SM-X700]
            [ro.build.version.release]: [13]
            [custom.empty.property]: []
        """.trimIndent()
        val props = parseSystemProperties(output)
        assertEquals(3, props.size)
        assertEquals("ro.product.model", props[0].key)
        assertEquals("SM-X700", props[0].value)
        assertEquals("ro.build.version.release", props[1].key)
        assertEquals("13", props[1].value)
        assertEquals("custom.empty.property", props[2].key)
        assertEquals("", props[2].value)
    }
}
