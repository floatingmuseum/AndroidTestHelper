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

        val outputWithOverride = """
            Physical size: 1600x2560
            Override size: 1200x1920
        """.trimIndent()
        assertEquals("1200x1920 (物理: 1600x2560)", parseScreenSize(outputWithOverride))

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
    fun testParseDisplayDetails() {
        val dumpsysWindowDisplays = """
            WINDOW MANAGER DISPLAY CONTENTS (dumpsys window displays)
              Display: mDisplayId=0 (organized)
                init=1600x2560 320dpi mMinSizeOfResizeableTaskDp=220 cur=2560x1600 app=2560x1600 rng=1600x1600-2560x2560
        """.trimIndent()

        assertEquals("0", parseDisplayId(dumpsysWindowDisplays))
        assertEquals("1600x2560 320dpi", parseDisplayInit(dumpsysWindowDisplays))
        assertEquals("2560x1600", parseDisplayCur(dumpsysWindowDisplays))
        assertEquals("2560x1600", parseDisplayApp(dumpsysWindowDisplays))

        val dumpsysDisplayWithFps = """
            mOverrideDisplayInfo=DisplayInfo{"内置屏幕", displayId 0, fps 90.0, vsync 90.0}
        """.trimIndent()
        assertEquals("90.0 Hz", parseDisplayRefreshRate(dumpsysDisplayWithFps))

        val dumpsysDisplayWithRenderRate = """
            renderFrameRate 120.0
        """.trimIndent()
        assertEquals("120.0 Hz", parseDisplayRefreshRate(dumpsysDisplayWithRenderRate))

        val dumpsysDisplayWithDefaultRate = """
            mDefaultRefreshRate: 60.0
        """.trimIndent()
        assertEquals("60.0 Hz", parseDisplayRefreshRate(dumpsysDisplayWithDefaultRate))
    }

    @Test
    fun testParseBatteryDetails() {
        val output = """
            Current Battery Service state:
              AC powered: false
              USB powered: true
              Wireless powered: false
              Max charging current: 500000
              Max charging voltage: 5000000
              Charge counter: 2841000
              status: 2
              health: 2
              present: true
              level: 95
              scale: 100
              temp: 290
              voltage: 4123
              technology: Li-poly
        """.trimIndent()
        assertEquals("充电中", parseBatteryStatus(output))
        assertEquals("良好", parseBatteryHealth(output))
        assertEquals("29.0 °C", parseBatteryTemp(output))
        assertEquals("4123 mV", parseBatteryVoltage(output))
        assertEquals("否", parseBatteryACPowered(output))
        assertEquals("是", parseBatteryUSBPowered(output))
        assertEquals("否", parseBatteryWirelessPowered(output))
        assertEquals("500 mA (500000 μA)", parseBatteryMaxChargingCurrent(output))
        assertEquals("5.0 V (5000 mV)", parseBatteryMaxChargingVoltage(output))
        assertEquals("2841 mAh (2841000 μAh)", parseBatteryChargeCounter(output))
        assertEquals("是", parseBatteryPresent(output))
        assertEquals("Li-poly", parseBatteryTechnology(output))

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
        assertEquals("未知", parseBatteryACPowered(unknownOutput))
        assertEquals("未知", parseBatteryUSBPowered(unknownOutput))
        assertEquals("未知", parseBatteryWirelessPowered(unknownOutput))
        assertEquals("未知", parseBatteryMaxChargingCurrent(unknownOutput))
        assertEquals("未知", parseBatteryMaxChargingVoltage(unknownOutput))
        assertEquals("未知", parseBatteryChargeCounter(unknownOutput))
        assertEquals("未知", parseBatteryPresent(unknownOutput))
        assertEquals("未知", parseBatteryTechnology(unknownOutput))
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

    @Test
    fun testBuildQuickActionCommandUsesExplicitDeviceForShutdown() {
        val command = buildQuickActionCommand(
            deviceSerial = "R58M123ABC",
            action = DeviceQuickAction.SHUTDOWN,
        )

        assertEquals(listOf("-s", "R58M123ABC", "reboot", "-p"), command.args)
        assertEquals("adb -s R58M123ABC reboot -p", command.displayCommand)
    }

    @Test
    fun testBuildQuickActionCommandUsesExplicitDeviceForKeyEvent() {
        val command = buildQuickActionCommand(
            deviceSerial = "R58M123ABC",
            action = DeviceQuickAction.HOME,
        )

        assertEquals(
            listOf("-s", "R58M123ABC", "shell", "input", "keyevent", "KEYCODE_HOME"),
            command.args,
        )
        assertEquals("adb -s R58M123ABC shell input keyevent KEYCODE_HOME", command.displayCommand)
    }

    @Test
    fun testBuildQuickActionCommandMapsMenuToAppSwitch() {
        val command = buildQuickActionCommand(
            deviceSerial = "R58M123ABC",
            action = DeviceQuickAction.MENU,
        )

        assertEquals(
            listOf("-s", "R58M123ABC", "shell", "input", "keyevent", "KEYCODE_APP_SWITCH"),
            command.args,
        )
        assertEquals("adb -s R58M123ABC shell input keyevent KEYCODE_APP_SWITCH", command.displayCommand)
    }
}
