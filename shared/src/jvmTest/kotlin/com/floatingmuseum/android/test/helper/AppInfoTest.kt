package com.floatingmuseum.android.test.helper

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class AppInfoTest {
    @Test
    fun `loads release metadata from bundled properties`() {
        val appInfo = loadAppInfo()

        assertEquals("AndroidTestHelper", appInfo.appName)
        assertEquals("0.1.1", appInfo.versionName)
        assertEquals("floatingmuseum", appInfo.author)
        assertEquals("Floating Museum", appInfo.vendor)
        assertEquals(
            "Desktop console for testing Android devices with bundled ADB, scrcpy, and platform-tools.",
            appInfo.description,
        )
        assertEquals("com.floatingmuseum.android.test.helper", appInfo.macosBundleID)
        assertEquals("com.floatingmuseum.android.test.helper", appInfo.linuxPackageID)
        UUID.fromString(appInfo.windowsUpgradeUuid)
    }
}
