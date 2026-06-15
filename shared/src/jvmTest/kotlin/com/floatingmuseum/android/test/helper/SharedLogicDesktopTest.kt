package com.floatingmuseum.android.test.helper

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedLogicDesktopTest {
    @Test
    fun parsesAdbDevicesOutput() {
        val devices = parseAdbDevices(
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

    @Test
    fun parsesAndroidDfOutput() {
        val storageInfo = parseDfStorageInfo(
            """
            Filesystem       1K-blocks     Used Available Use% Mounted on
            /dev/block/dm-53 110077224 35155936  74842480  32% /data
            """.trimIndent(),
        )

        assertEquals(110077224L * 1024L, storageInfo.totalBytes)
        assertEquals(35155936L * 1024L, storageInfo.usedBytes)
        assertEquals(74842480L * 1024L, storageInfo.availableBytes)
    }

    @Test
    fun parsesPackagePathListOutput() {
        val packages = parsePackagePathList(
            """
            package:/data/app/~~abcd/com.example.app-123/base.apk=com.example.app uid:10234
            package:/system/priv-app/Settings/Settings.apk=com.android.settings uid:1000
            """.trimIndent(),
        )

        assertEquals(2, packages.size)
        assertEquals("com.example.app", packages[0].packageName)
        assertEquals("/data/app/~~abcd/com.example.app-123/base.apk", packages[0].path)
        assertEquals("com.android.settings", packages[1].packageName)
        assertEquals("/system/priv-app/Settings/Settings.apk", packages[1].path)
    }

    @Test
    fun parsesPackageNameListOutput() {
        val packages = parsePackageNameList(
            """
            package:com.example.disabled
            package:/data/app/com.example.path/base.apk=com.example.path uid:10235
            """.trimIndent(),
        )

        assertEquals(setOf("com.example.disabled", "com.example.path"), packages)
    }

    @Test
    fun parsesPackageDumpsysVersions() {
        val packages = parsePackageDumpsys(
            """
            Package [com.example.app] (123abc):
              versionCode=42 minSdk=23 targetSdk=35
              versionName=1.2.3
            Package [com.android.settings] (456def):
              versionCode=350000000 minSdk=35 targetSdk=35
              versionName=15
            """.trimIndent(),
        )

        assertEquals("1.2.3", packages["com.example.app"]?.versionName)
        assertEquals(42L, packages["com.example.app"]?.versionCode)
        assertEquals("15", packages["com.android.settings"]?.versionName)
        assertEquals(350000000L, packages["com.android.settings"]?.versionCode)
    }
}
