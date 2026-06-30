package com.floatingmuseum.android.test.helper.device

import com.floatingmuseum.android.test.helper.settings.ScreenRecordAudioMode
import com.floatingmuseum.android.test.helper.settings.ScreenRecordBitRate
import com.floatingmuseum.android.test.helper.settings.ScreenRecordFormat
import com.floatingmuseum.android.test.helper.settings.ScreenRecordMaxFps
import com.floatingmuseum.android.test.helper.settings.ScreenRecordMaxSize
import java.io.File
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.Json

class DeviceAdbTest {

    @Test
    fun testParseScreenSize() {
        val output = """
            Physical size: 1600x2560
        """.trimIndent()
        assertEquals(DeviceInfoValue.Text("1600x2560"), parseScreenSize(output))

        val outputWithOverride = """
            Physical size: 1600x2560
            Override size: 1200x1920
        """.trimIndent()
        assertEquals(DeviceInfoValue.PhysicalOverride("1200x1920", "1600x2560"), parseScreenSize(outputWithOverride))

        val outputUnknown = """
            Something else
        """.trimIndent()
        assertEquals(DeviceInfoValue.Unknown, parseScreenSize(outputUnknown))
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
        assertEquals(DeviceInfoValue.Text("192.168.1.100"), parseIpAddress(output))

        val outputUnknown = """
            loopback only:
            inet 127.0.0.1/8 scope host lo
        """.trimIndent()
        assertEquals(DeviceInfoValue.Unknown, parseIpAddress(outputUnknown))
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
        assertEquals(DeviceInfoValue.Text("440"), parseScreenDensity(physicalOnly))

        val withOverride = """
            Physical density: 440
            Override density: 400
        """.trimIndent()
        assertEquals(DeviceInfoValue.PhysicalOverride("400", "440"), parseScreenDensity(withOverride))

        val unknown = "something else"
        assertEquals(DeviceInfoValue.Unknown, parseScreenDensity(unknown))
    }

    @Test
    fun testParseDisplayDetails() {
        val dumpsysWindowDisplays = """
            WINDOW MANAGER DISPLAY CONTENTS (dumpsys window displays)
              Display: mDisplayId=0 (organized)
                init=1600x2560 320dpi mMinSizeOfResizeableTaskDp=220 cur=2560x1600 app=2560x1600 rng=1600x1600-2560x2560
        """.trimIndent()

        assertEquals(DeviceInfoValue.Text("0"), parseDisplayId(dumpsysWindowDisplays))
        assertEquals(DeviceInfoValue.Text("1600x2560 320dpi"), parseDisplayInit(dumpsysWindowDisplays))
        assertEquals(DeviceInfoValue.Text("2560x1600"), parseDisplayCur(dumpsysWindowDisplays))
        assertEquals(DeviceInfoValue.Text("2560x1600"), parseDisplayApp(dumpsysWindowDisplays))

        val dumpsysDisplayWithFps = """
            mOverrideDisplayInfo=DisplayInfo{"内置屏幕", displayId 0, fps 90.0, vsync 90.0}
        """.trimIndent()
        assertEquals(DeviceInfoValue.Text("90.0 Hz"), parseDisplayRefreshRate(dumpsysDisplayWithFps))

        val dumpsysDisplayWithRenderRate = """
            renderFrameRate 120.0
        """.trimIndent()
        assertEquals(DeviceInfoValue.Text("120.0 Hz"), parseDisplayRefreshRate(dumpsysDisplayWithRenderRate))

        val dumpsysDisplayWithDefaultRate = """
            mDefaultRefreshRate: 60.0
        """.trimIndent()
        assertEquals(DeviceInfoValue.Text("60.0 Hz"), parseDisplayRefreshRate(dumpsysDisplayWithDefaultRate))
    }

    @Test
    fun testParseCpuInfo() {
        val output = """
            Processor       : ARMv7 Processor rev 0 (v7l)
            processor       : 0
            BogoMIPS        : 38.40

            processor       : 1
            BogoMIPS        : 38.40

            processor       : 2
            BogoMIPS        : 38.40

            processor       : 3
            BogoMIPS        : 38.40
            Features        : swp half thumb fastmult vfp edsp neon vfpv3 tls vfpv4
            CPU implementer : 0x51
            CPU architecture: 7
            CPU variant     : 0x2
            CPU part        : 0x06f
            CPU revision    : 0

            Hardware        : Qualcomm MSM 8974 HAMMERHEAD (Flattened Device Tree)
            Revision        : 000b
            Serial          : 0000000000000000
        """.trimIndent()

        val details = parseCpuInfo(output)

        assertEquals(DeviceInfoValue.Text("ARMv7 Processor rev 0 (v7l)"), details.processor)
        assertEquals(DeviceInfoValue.Text("Qualcomm MSM 8974 HAMMERHEAD (Flattened Device Tree)"), details.hardware)
        assertEquals(DeviceInfoValue.Text("7"), details.architecture)
        assertEquals(DeviceInfoValue.Text("4"), details.coreCount)
        assertEquals(DeviceInfoValue.Text("swp half thumb fastmult vfp edsp neon vfpv3 tls vfpv4"), details.features)
    }

    @Test
    fun testParseCpuInfoFallsBackToModelNameAndCpuCores() {
        val output = """
            processor       : 0
            vendor_id       : GenuineIntel
            cpu family      : 6
            model name      : Intel(R) Core(TM) i7
            cpu cores       : 2
            flags           : fpu vme de pse
        """.trimIndent()

        val details = parseCpuInfo(output)

        assertEquals(DeviceInfoValue.Text("Intel(R) Core(TM) i7"), details.processor)
        assertEquals(DeviceInfoValue.Text("Intel(R) Core(TM) i7"), details.hardware)
        assertEquals(DeviceInfoValue.Unknown, details.architecture)
        assertEquals(DeviceInfoValue.Text("1"), details.coreCount)
        assertEquals(DeviceInfoValue.Text("fpu vme de pse"), details.features)
    }

    @Test
    fun testParseMemoryInfo() {
        val output = """
            MemTotal:        8388608 kB
            MemFree:          486564 kB
            MemAvailable:    4194304 kB
            Buffers:           15224 kB
            Cached:            72464 kB
            SwapTotal:        262140 kB
            SwapFree:         207572 kB
        """.trimIndent()

        val details = parseMemoryInfo(output)

        assertEquals(DeviceInfoValue.Text("8 GiB (8388608 kB)"), details.total)
        assertEquals(DeviceInfoValue.Text("475.2 MiB (486564 kB)"), details.free)
        assertEquals(DeviceInfoValue.Text("4 GiB (4194304 kB)"), details.available)
        assertEquals(DeviceInfoValue.Text("14.9 MiB (15224 kB)"), details.buffers)
        assertEquals(DeviceInfoValue.Text("70.8 MiB (72464 kB)"), details.cached)
        assertEquals(DeviceInfoValue.Text("256 MiB (262140 kB)"), details.swapTotal)
        assertEquals(DeviceInfoValue.Text("202.7 MiB (207572 kB)"), details.swapFree)
    }

    @Test
    fun testParseMemoryInfoHandlesMissingValues() {
        val details = parseMemoryInfo("MemTotal:        1024 kB")

        assertEquals(DeviceInfoValue.Text("1 MiB (1024 kB)"), details.total)
        assertEquals(DeviceInfoValue.Unknown, details.available)
        assertEquals(DeviceInfoValue.Unknown, details.free)
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
        assertEquals(DeviceInfoValue.Localized(DeviceInfoToken.BatteryStatusCharging), parseBatteryStatus(output))
        assertEquals(DeviceInfoValue.Localized(DeviceInfoToken.BatteryHealthGood), parseBatteryHealth(output))
        assertEquals(DeviceInfoValue.Text("29.0 °C"), parseBatteryTemp(output))
        assertEquals(DeviceInfoValue.Text("4123 mV"), parseBatteryVoltage(output))
        assertEquals(DeviceInfoValue.BooleanValue(false), parseBatteryACPowered(output))
        assertEquals(DeviceInfoValue.BooleanValue(true), parseBatteryUSBPowered(output))
        assertEquals(DeviceInfoValue.BooleanValue(false), parseBatteryWirelessPowered(output))
        assertEquals(DeviceInfoValue.Text("500 mA (500000 μA)"), parseBatteryMaxChargingCurrent(output))
        assertEquals(DeviceInfoValue.Text("5.0 V (5000 mV)"), parseBatteryMaxChargingVoltage(output))
        assertEquals(DeviceInfoValue.Text("2841 mAh (2841000 μAh)"), parseBatteryChargeCounter(output))
        assertEquals(DeviceInfoValue.BooleanValue(true), parseBatteryPresent(output))
        assertEquals(DeviceInfoValue.Text("Li-poly"), parseBatteryTechnology(output))

        val unknownOutput = """
              status: 9
              health: 8
              temp: unknown
              voltage: unknown
        """.trimIndent()
        assertEquals(DeviceInfoValue.Unknown, parseBatteryStatus(unknownOutput))
        assertEquals(DeviceInfoValue.Unknown, parseBatteryHealth(unknownOutput))
        assertEquals(DeviceInfoValue.Unknown, parseBatteryTemp(unknownOutput))
        assertEquals(DeviceInfoValue.Unknown, parseBatteryVoltage(unknownOutput))
        assertEquals(DeviceInfoValue.Unknown, parseBatteryACPowered(unknownOutput))
        assertEquals(DeviceInfoValue.Unknown, parseBatteryUSBPowered(unknownOutput))
        assertEquals(DeviceInfoValue.Unknown, parseBatteryWirelessPowered(unknownOutput))
        assertEquals(DeviceInfoValue.Unknown, parseBatteryMaxChargingCurrent(unknownOutput))
        assertEquals(DeviceInfoValue.Unknown, parseBatteryMaxChargingVoltage(unknownOutput))
        assertEquals(DeviceInfoValue.Unknown, parseBatteryChargeCounter(unknownOutput))
        assertEquals(DeviceInfoValue.Unknown, parseBatteryPresent(unknownOutput))
        assertEquals(DeviceInfoValue.Unknown, parseBatteryTechnology(unknownOutput))
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
    fun testBuildScreenRecordFileNameIncludesSafeSerialAndTimestamp() {
        val capturedAt = LocalDateTime.of(2026, 6, 17, 9, 8, 7)

        assertEquals(
            "screenrecord_R58M123ABC_20260617_090807.mp4",
            buildScreenRecordFileName("R58M123ABC", capturedAt)
        )
        assertEquals(
            "screenrecord_192.168.1.5_5555_20260617_090807.mp4",
            buildScreenRecordFileName("192.168.1.5:5555", capturedAt)
        )
    }

    @Test
    fun testBuildScrcpyRecordFileNameUsesMkvContainer() {
        val capturedAt = LocalDateTime.of(2026, 6, 17, 9, 8, 7)

        assertEquals(
            "screenrecord_R58M123ABC_20260617_090807.mkv",
            buildScrcpyRecordFileName("R58M123ABC", capturedAt)
        )
        assertEquals(
            "screenrecord_192.168.1.5_5555_20260617_090807.mkv",
            buildScrcpyRecordFileName("192.168.1.5:5555", capturedAt)
        )
    }

    @Test
    fun testBuildScrcpyRecordFileNameUsesConfiguredContainer() {
        val capturedAt = LocalDateTime.of(2026, 6, 17, 9, 8, 7)

        assertEquals(
            "screenrecord_R58M123ABC_20260617_090807.mp4",
            buildScrcpyRecordFileName("R58M123ABC", capturedAt, ScreenRecordFormat.Mp4)
        )
    }

    @Test
    fun testWindowsCommandLineQuotesScrcpyRecordingArguments() {
        assertEquals(
            "\"C:\\Program Files\\scrcpy\\scrcpy.exe\" \"--record=C:\\Recordings\\demo file.mp4\" \"--window-title=He said \\\"record\\\"\"",
            buildWindowsCommandLine(
                executablePath = "C:\\Program Files\\scrcpy\\scrcpy.exe",
                args = listOf(
                    "--record=C:\\Recordings\\demo file.mp4",
                    "--window-title=He said \"record\"",
                ),
            ),
        )
    }

    @Test
    fun testWindowsScrcpyRecordWrapperSignalsCtrlBreak() {
        val script = buildWindowsScrcpyRecordWrapperScript(
            command = ScrcpyRecordCommand(
                scrcpyPath = "C:\\scrcpy\\scrcpy.exe",
                args = listOf("--record=C:\\Recordings\\demo.mp4"),
                displayCommand = "",
            ),
            workingDirectory = File("C:\\scrcpy"),
            stopSignalFile = File("C:\\Temp\\ath-stop.signal"),
        )

        assertEquals(true, "CreateProcessW" in script)
        assertEquals(true, "GenerateConsoleCtrlEvent(1" in script)
        assertEquals(true, "0x00000200" in script)
        assertEquals(false, "taskkill" in script)
    }

    @Test
    fun testScrcpyRecordingStartedLineDetection() {
        assertEquals(true, isScrcpyRecordingStartedLine("INFO: Recording started to matroska file: demo.mkv"))
        assertEquals(true, isScrcpyRecordingStartedLine("[server] INFO: Recording started to file: demo.mkv"))
        assertEquals(false, isScrcpyRecordingStartedLine("INFO: Recording complete to file: demo.mkv"))
        assertEquals(false, isScrcpyRecordingStartedLine("INFO: scrcpy 4.0 <https://github.com/Genymobile/scrcpy>"))
    }

    @Test
    fun testBuildScreenRecordTransferPlanUsesRemoteAndLocalTargets() {
        val capturedAt = LocalDateTime.of(2026, 6, 17, 9, 8, 7)
        val outputDirectory = File("recordings").absolutePath

        val plan = buildScreenRecordTransferPlan(
            deviceSerial = "serial/with:bad chars",
            outputDirectoryPath = outputDirectory,
            capturedAt = capturedAt,
        )

        assertEquals("screenrecord_serial_with_bad_chars_20260617_090807.mp4", plan.fileName)
        assertEquals(
            "/sdcard/Movies/screenrecord_serial_with_bad_chars_20260617_090807.mp4",
            plan.remotePath
        )
        assertEquals(
            listOf(
                "/sdcard/Movies/screenrecord_serial_with_bad_chars_20260617_090807.mp4",
                "/sdcard/Download/screenrecord_serial_with_bad_chars_20260617_090807.mp4",
                "/sdcard/screenrecord_serial_with_bad_chars_20260617_090807.mp4",
                "/data/local/tmp/screenrecord_serial_with_bad_chars_20260617_090807.mp4",
            ),
            plan.remotePaths,
        )
        assertEquals(File(outputDirectory, plan.fileName).absolutePath, plan.localPath)
    }

    @Test
    fun testBuildScreenRecordCommandUsesExplicitDeviceAndRemotePath() {
        val command = buildScreenRecordCommand(
            deviceSerial = "R58M123ABC",
            remotePath = "/sdcard/Movies/demo.mp4",
        )

        assertEquals(
            listOf("-s", "R58M123ABC", "shell", "screenrecord", "/sdcard/Movies/demo.mp4"),
            command.args,
        )
        assertEquals(
            "adb -s R58M123ABC shell screenrecord /sdcard/Movies/demo.mp4",
            command.displayCommand,
        )
    }

    @Test
    fun testBuildScrcpyRecordCommandWritesLocalFileWithoutDeviceStorage() {
        val localFile = File("recordings/demo.mkv").absolutePath
        val command = buildScrcpyRecordCommand(
            scrcpyPath = "C:\\scrcpy\\scrcpy.exe",
            deviceSerial = "R58M123ABC",
            localPath = localFile,
        )

        assertEquals("C:\\scrcpy\\scrcpy.exe", command.scrcpyPath)
        assertEquals(
            listOf(
                "--serial=R58M123ABC",
                "--no-audio",
                "--no-playback",
                "--no-window",
                "--no-control",
                "--record-format=mkv",
                "--record=$localFile",
            ),
            command.args,
        )
        assertEquals(
            "\"C:\\scrcpy\\scrcpy.exe\" --serial=R58M123ABC --no-audio --no-playback --no-window --no-control --record-format=mkv --record=\"$localFile\"",
            command.displayCommand,
        )
    }

    @Test
    fun testBuildScrcpyRecordCommandUsesConfiguredRecordingOptions() {
        val localFile = File("recordings/demo.mp4").absolutePath
        val command = buildScrcpyRecordCommand(
            scrcpyPath = "C:\\scrcpy\\scrcpy.exe",
            deviceSerial = "R58M123ABC",
            localPath = localFile,
            format = ScreenRecordFormat.Mp4,
            maxSize = ScreenRecordMaxSize.Size720,
            bitRate = ScreenRecordBitRate.Mbps12,
            maxFps = ScreenRecordMaxFps.Fps30,
            audioMode = ScreenRecordAudioMode.Microphone,
        )

        assertEquals(
            listOf(
                "--serial=R58M123ABC",
                "--audio-source=mic",
                "--no-playback",
                "--no-window",
                "--no-control",
                "--max-size=720",
                "--video-bit-rate=12M",
                "--max-fps=30",
                "--record-format=mp4",
                "--record=$localFile",
            ),
            command.args,
        )
        assertEquals(
            "\"C:\\scrcpy\\scrcpy.exe\" --serial=R58M123ABC --audio-source=mic --no-playback --no-window --no-control --max-size=720 --video-bit-rate=12M --max-fps=30 --record-format=mp4 --record=\"$localFile\"",
            command.displayCommand,
        )
    }

    @Test
    fun testBuildScrcpyMirrorCommandUsesExplicitDeviceAndKeepsControlEnabled() {
        val command = buildScrcpyMirrorCommand(
            scrcpyPath = "C:\\scrcpy\\scrcpy.exe",
            deviceSerial = "R58M123ABC",
            windowTitle = "AndroidTestHelper - Demo",
        )

        assertEquals("C:\\scrcpy\\scrcpy.exe", command.scrcpyPath)
        assertEquals(
            listOf(
                "--serial=R58M123ABC",
                "--no-audio",
                "--window-title=AndroidTestHelper - Demo",
            ),
            command.args,
        )
        assertEquals(
            "\"C:\\scrcpy\\scrcpy.exe\" --serial=R58M123ABC --no-audio --window-title=\"AndroidTestHelper - Demo\"",
            command.displayCommand,
        )
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
    fun testBuildQuickActionCommandUsesExplicitDeviceForRebootRecovery() {
        val command = buildQuickActionCommand(
            deviceSerial = "R58M123ABC",
            action = DeviceQuickAction.REBOOT_RECOVERY,
        )

        assertEquals(listOf("-s", "R58M123ABC", "reboot", "recovery"), command.args)
        assertEquals("adb -s R58M123ABC reboot recovery", command.displayCommand)
    }

    @Test
    fun testBuildQuickActionCommandUsesExplicitDeviceForRebootFastboot() {
        val command = buildQuickActionCommand(
            deviceSerial = "R58M123ABC",
            action = DeviceQuickAction.REBOOT_FASTBOOT,
        )

        assertEquals(listOf("-s", "R58M123ABC", "reboot", "bootloader"), command.args)
        assertEquals("adb -s R58M123ABC reboot bootloader", command.displayCommand)
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

    @Test
    fun testParseXApkManifest() {
        val jsonStr = """
            {
                "xapk_version": 1,
                "package_name": "com.example.game",
                "name": "Super Game",
                "version_code": "100",
                "version_name": "1.0.0",
                "split_apks": [
                    {
                        "file": "com.example.game.apk",
                        "id": "base"
                    },
                    {
                        "file": "config.arm64_v8a.apk",
                        "id": "config.arm64_v8a"
                    }
                ],
                "expansions": [
                    {
                        "file": "main.100.com.example.game.obb",
                        "install_path": "Android/obb/com.example.game/main.100.com.example.game.obb",
                        "install_location": "external_obb"
                    }
                ]
            }
        """.trimIndent()
        val json = Json { ignoreUnknownKeys = true }
        val manifest = json.decodeFromString<XApkManifest>(jsonStr)
        assertEquals("com.example.game", manifest.packageName)
        assertEquals("Super Game", manifest.name)
        assertEquals(2, manifest.splitApks.size)
        assertEquals("com.example.game.apk", manifest.splitApks[0].file)
        assertEquals("config.arm64_v8a.apk", manifest.splitApks[1].file)
        assertEquals(1, manifest.expansions.size)
        assertEquals("main.100.com.example.game.obb", manifest.expansions[0].file)
        assertEquals("Android/obb/com.example.game/main.100.com.example.game.obb", manifest.expansions[0].installPath)
    }

    @Test
    fun testBuildQuickActionCommandForCurrentActivity() {
        val command = buildQuickActionCommand(
            deviceSerial = "R58M123ABC",
            action = DeviceQuickAction.CURRENT_ACTIVITY,
        )

        assertEquals(
            listOf("-s", "R58M123ABC", "shell", "dumpsys window | grep mCurrentFocus"),
            command.args,
        )
        assertEquals("adb -s R58M123ABC shell \"dumpsys window | grep mCurrentFocus\"", command.displayCommand)
    }

    @Test
    fun testParseCurrentActivity() {
        // Test format 1: mCurrentFocus window dumpsys output with u0
        val output1 = "  mCurrentFocus=Window{8f2d5a3 u0 com.floatingmuseum.android.test.helper/com.floatingmuseum.android.test.helper.MainActivity}"
        val res1 = parseCurrentActivity(output1)
        kotlin.test.assertNotNull(res1)
        assertEquals("com.floatingmuseum.android.test.helper", res1.first)
        assertEquals("com.floatingmuseum.android.test.helper.MainActivity", res1.second)

        // Test format 2: mCurrentFocus window dumpsys output with exiting flag
        val output2 = "  mCurrentFocus=Window{8f2d5a3 u0 com.floatingmuseum.android.test.helper/com.floatingmuseum.android.test.helper.MainActivity EXITING}"
        val res2 = parseCurrentActivity(output2)
        kotlin.test.assertNotNull(res2)
        assertEquals("com.floatingmuseum.android.test.helper", res2.first)
        assertEquals("com.floatingmuseum.android.test.helper.MainActivity", res2.second)

        // Test format 3: mCurrentFocus window dumpsys output without u0
        val output3 = "  mCurrentFocus=Window{8f2d5a3 com.floatingmuseum.android.test.helper/com.floatingmuseum.android.test.helper.MainActivity}"
        val res3 = parseCurrentActivity(output3)
        kotlin.test.assertNotNull(res3)
        assertEquals("com.floatingmuseum.android.test.helper", res3.first)
        assertEquals("com.floatingmuseum.android.test.helper.MainActivity", res3.second)

        // Test format 4: mResumedActivity dumpsys activity output with relative activity name
        val output4 = "    mResumedActivity: ActivityRecord{8b671cc u0 com.floatingmuseum.android.test.helper/.MainActivity t12}"
        val res4 = parseCurrentActivity(output4)
        kotlin.test.assertNotNull(res4)
        assertEquals("com.floatingmuseum.android.test.helper", res4.first)
        assertEquals("com.floatingmuseum.android.test.helper.MainActivity", res4.second)

        // Test format 5: mResumedActivity dumpsys activity output with short relative name
        val output5 = "    mResumedActivity: ActivityRecord{8b671cc u0 com.floatingmuseum.android.test.helper/MainActivity t12}"
        val res5 = parseCurrentActivity(output5)
        kotlin.test.assertNotNull(res5)
        assertEquals("com.floatingmuseum.android.test.helper", res5.first)
        assertEquals("com.floatingmuseum.android.test.helper.MainActivity", res5.second)

        // Test format 6: null or invalid inputs
        assertNull(parseCurrentActivity("mCurrentFocus=null"))
        assertNull(parseCurrentActivity("something completely random"))
    }

    @Test
    fun testParseAppNameFromJsonAndContentQueryJson() {
        val mockJson = """[{"packageName":"com.floatingmuseum.android.test.helper","appName":"AndroidTestHelper","versionName":"1.0.2"}]"""
        val appName = parseAppNameFromJson(mockJson)
        assertEquals("AndroidTestHelper", appName)

        val invalidJson = """{"error":"not found"}"""
        assertNull(parseAppNameFromJson(invalidJson))

        val mockContentOutput = """
            Row: 0 json_data=[{"packageName":"com.floatingmuseum.android.test.helper","appName":"AndroidTestHelper"}]
        """.trimIndent()
        val parsedJson = parseContentQueryJson(mockContentOutput)
        assertEquals("""[{"packageName":"com.floatingmuseum.android.test.helper","appName":"AndroidTestHelper"}]""", parsedJson)
    }
}
