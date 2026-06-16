package com.floatingmuseum.android.test.helper.app

import kotlin.test.Test
import kotlin.test.assertEquals

class AppTest {
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
    fun parsesPmPathOutput() {
        val paths = parsePmPathOutput(
            """
            package:/data/app/~~token/com.example.app/base.apk
            package:/data/app/~~token/com.example.app/split_config.arm64_v8a.apk
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                "/data/app/~~token/com.example.app/base.apk",
                "/data/app/~~token/com.example.app/split_config.arm64_v8a.apk",
            ),
            paths,
        )
    }

    @Test
    fun parsesPackageDumpsysVersions() {
        val packages = parsePackageDumpsys(
            """
            Package [com.example.app] (123abc):
              versionCode=42 minSdk=23 targetSdk=35
              compileSdkVersion=35
              versionName=1.2.3
            Package [com.android.settings] (456def):
              versionCode=350000000 minSdk=35 targetSdk=35
              compileSdkVersion=36
              versionName=15
            """.trimIndent(),
        )

        assertEquals("1.2.3", packages["com.example.app"]?.versionName)
        assertEquals(42L, packages["com.example.app"]?.versionCode)
        assertEquals(35, packages["com.example.app"]?.compileSdkVersion)
        assertEquals(23, packages["com.example.app"]?.minSdkVersion)
        assertEquals(35, packages["com.example.app"]?.targetSdkVersion)
        assertEquals("15", packages["com.android.settings"]?.versionName)
        assertEquals(350000000L, packages["com.android.settings"]?.versionCode)
        assertEquals(36, packages["com.android.settings"]?.compileSdkVersion)
        assertEquals(35, packages["com.android.settings"]?.minSdkVersion)
        assertEquals(35, packages["com.android.settings"]?.targetSdkVersion)
    }

    @Test
    fun filtersInstalledAppsByNameOrPackage() {
        val apps = listOf(
            testInstalledApp("com.su.assistant.pro", "Dev Assistant"),
            testInstalledApp("com.android.settings", "设置"),
            testInstalledApp("com.example.camera", "Camera Lab"),
        )

        assertEquals(apps, filterInstalledApps(apps, " "))
        assertEquals(listOf(apps[0]), filterInstalledApps(apps, "assistant"))
        assertEquals(listOf(apps[1]), filterInstalledApps(apps, "ANDROID.SETTINGS"))
        assertEquals(listOf(apps[2]), filterInstalledApps(apps, "camera"))
    }

    private fun testInstalledApp(
        packageName: String,
        appName: String,
    ): InstalledAppInfo = InstalledAppInfo(
        packageName = packageName,
        appName = appName,
        versionName = "-",
        versionCode = null,
        isSystem = false,
        isEnabled = true,
        iconBytes = null,
    )
}
