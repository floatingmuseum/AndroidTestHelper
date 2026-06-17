package com.floatingmuseum.android.test.helper.device

import java.io.File
import java.time.LocalDateTime
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

    @Test
    fun testParseScreenDensity() {
        val physicalOnly = """
            Physical density: 440
        """.trimIndent()
        assertEquals("440", parseScreenDensity(physicalOnly))

        val withOverride = """
            Physical density: 440
            Override density: 400
        """.trimIndent()
        assertEquals("400 (物理: 440)", parseScreenDensity(withOverride))

        val unknown = "something else"
        assertEquals("未知", parseScreenDensity(unknown))
    }

    @Test
    fun testParseBatteryDetails() {
        val output = """
            Current Battery Service state:
              AC powered: false
              USB powered: true
              status: 2
              health: 2
              present: true
              level: 95
              scale: 100
              temp: 290
              voltage: 4123
        """.trimIndent()
        assertEquals("充电中", parseBatteryStatus(output))
        assertEquals("良好", parseBatteryHealth(output))
        assertEquals("29.0 °C", parseBatteryTemp(output))
        assertEquals("4123 mV", parseBatteryVoltage(output))

        val unknownOutput = """
              status: 9
              health: 8
              temp: unknown
              voltage: unknown
        """.trimIndent()
        assertEquals("未知", parseBatteryStatus(unknownOutput))
        assertEquals("未知", parseBatteryHealth(unknownOutput))
        assertEquals("未知", parseBatteryTemp(unknownOutput))
        assertEquals("未知", parseBatteryVoltage(unknownOutput))
    }

    @Test
    fun testBuildScreenshotFileNameIncludesSafeSerialAndTimestamp() {
        val capturedAt = LocalDateTime.of(2026, 6, 17, 9, 8, 7)

        assertEquals(
            "screenshot_R58M123ABC_20260617_090807.png",
            buildScreenshotFileName("R58M123ABC", capturedAt)
        )
        assertEquals(
            "screenshot_192.168.1.5_5555_20260617_090807.png",
            buildScreenshotFileName("192.168.1.5:5555", capturedAt)
        )
    }

    @Test
    fun testBuildScreenshotTransferPlanUsesRemoteAndLocalTargets() {
        val capturedAt = LocalDateTime.of(2026, 6, 17, 9, 8, 7)
        val outputDirectory = File("screenshots").absolutePath

        val plan = buildScreenshotTransferPlan(
            deviceSerial = "serial/with:bad chars",
            outputDirectoryPath = outputDirectory,
            capturedAt = capturedAt,
        )

        assertEquals("screenshot_serial_with_bad_chars_20260617_090807.png", plan.fileName)
        assertEquals(
            "/sdcard/AndroidTestHelperScreenshots/screenshot_serial_with_bad_chars_20260617_090807.png",
            plan.remotePath
        )
        assertEquals(File(outputDirectory, plan.fileName).absolutePath, plan.localPath)
    }

    @Test
    fun testBuildInstallApplicationCommandUsesExplicitDeviceAndApkPath() {
        val apkFile = File("local apps/demo.apk").absoluteFile

        val command = buildInstallApplicationCommand(
            deviceSerial = "R58M123ABC",
            apkFile = apkFile,
        )

        assertEquals(
            listOf("-s", "R58M123ABC", "install", "-r", apkFile.absolutePath),
            command.args,
        )
        assertEquals(
            "adb -s R58M123ABC install -r \"${apkFile.absolutePath}\"",
            command.displayCommand,
        )
    }
}
