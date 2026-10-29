package com.floatingmuseum.android.test.helper.app

import com.floatingmuseum.android.test.helper.localization.localized
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SystemApplicationVersionTest {
    @Test
    fun updatedSystemAppsUseInstalledVersionsInsteadOfHiddenPreinstalledVersions() {
        val packages = parsePackageDumpsys(
            """
            Packages:
              Package [com.hat.settings] (installed-settings):
                codePath=/data/app/com.hat.settings-updated
                versionCode=5017 minSdk=21 targetSdk=28
                versionName=1.0.5017-settings
              Package [com.example.edcationcloud] (installed-cloud):
                codePath=/data/app/com.example.edcationcloud-updated
                versionCode=5032 minSdk=23 targetSdk=33
                versionName=1.0.5032-launcher
            Hidden system packages:
              Package [com.hat.settings] (preinstalled-settings):
                codePath=/product/app/HatSettings
                versionCode=5011 minSdk=21 targetSdk=28
                versionName=1.0.5011-settings
              Package [com.example.edcationcloud] (preinstalled-cloud):
                codePath=/product/app/HatLauncher
                versionCode=5010 minSdk=23 targetSdk=29
                versionName=1.0.5010-launcher
            """.trimIndent(),
        )

        assertEquals(5017L, packages["com.hat.settings"]?.versionCode)
        assertEquals("1.0.5017-settings", packages["com.hat.settings"]?.versionName)
        assertEquals(5032L, packages["com.example.edcationcloud"]?.versionCode)
        assertEquals("1.0.5032-launcher", packages["com.example.edcationcloud"]?.versionName)
        assertEquals(33, packages["com.example.edcationcloud"]?.targetSdkVersion)
    }

    @Test
    fun sectionOrderDoesNotChangeInstalledVersionSelection() {
        val packages = parsePackageDumpsys(
            """
            Hidden system packages:
              Package [com.hat.settings] (preinstalled):
                versionCode=5011 minSdk=21 targetSdk=28
                versionName=1.0.5011-settings
              Package [com.example.hiddenonly] (hidden-only):
                versionCode=100
                versionName=1.0
            Packages:
              Package [com.hat.settings] (installed):
                versionCode=5017 minSdk=21 targetSdk=28
                versionName=1.0.5017-settings
            """.trimIndent(),
        )

        assertEquals(setOf("com.hat.settings"), packages.keys)
        assertEquals(5017L, packages["com.hat.settings"]?.versionCode)
    }

    @Test
    fun hiddenOnlyPackageIsNotReportedAsInstalled() {
        val packages = parsePackageDumpsys(
            """
            Packages:
              Package [com.example.installed] (installed):
                versionCode=2
                versionName=2.0
            Hidden system packages:
              Package [com.example.removed] (preinstalled):
                versionCode=1
                versionName=1.0
            """.trimIndent(),
        )

        assertEquals(setOf("com.example.installed"), packages.keys)
        assertNull(packages["com.example.removed"])
    }

    @Test
    fun laterTopLevelSectionsDoNotOverwriteInstalledPackageMetadata() {
        val packages = parsePackageDumpsys(
            """
            Packages:
              Package [com.hat.settings] (installed):
                versionCode=5017 minSdk=21 targetSdk=28
                versionName=1.0.5017-settings
            Dexopt state:
              Package [com.hat.settings] (other-section):
                versionCode=9999 minSdk=99 targetSdk=99
                versionName=not-installed-metadata
            """.trimIndent(),
        )

        assertEquals(5017L, packages["com.hat.settings"]?.versionCode)
        assertEquals("1.0.5017-settings", packages["com.hat.settings"]?.versionName)
        assertEquals(28, packages["com.hat.settings"]?.targetSdkVersion)
    }

    @Test
    fun activePackageWinsEvenWhenItsVersionCodeIsLower() {
        val packages = parsePackageDumpsys(
            """
            Packages:
              Package [com.example.system] (installed):
                versionCode=7
                versionName=active-build
            Hidden system packages:
              Package [com.example.system] (preinstalled):
                versionCode=9
                versionName=preinstalled-build
            """.trimIndent(),
        )

        assertEquals(7L, packages["com.example.system"]?.versionCode)
        assertEquals("active-build", packages["com.example.system"]?.versionName)
    }

    @Test
    fun packageOnlyOutputRemainsSupported() {
        val packages = parsePackageDumpsys(
            """
            Package [com.example.first] (first):
              versionCode=42 minSdk=23 targetSdk=35
              versionName=1.2.3
            Package [com.example.second] (second):
              versionCode=10 minSdk=21 targetSdk=29
              versionName=1.0
            """.trimIndent(),
        )

        assertEquals(setOf("com.example.first", "com.example.second"), packages.keys)
        assertEquals(42L, packages["com.example.first"]?.versionCode)
        assertEquals(10L, packages["com.example.second"]?.versionCode)
    }

    @Test
    fun systemDetailsShowLiveCurrentAndPreinstalledVersionsSeparately() {
        val items = buildApplicationVersionDetailItems(
            app = systemApp(),
            dumpsysOutput = """
                Packages:
                  Package [com.hat.settings] (installed):
                    codePath=/data/app/com.hat.settings-updated
                    versionCode=5017 minSdk=21 targetSdk=28
                    versionName=1.0.5017-settings
                    pkgFlags=[ SYSTEM HAS_CODE UPDATED_SYSTEM_APP ]
                Hidden system packages:
                  Package [com.hat.settings] (preinstalled):
                    codePath=/product/app/HatSettings
                    versionCode=5011 minSdk=21 targetSdk=28
                    versionName=1.0.5011-settings
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                ApplicationDetailItem(localized("app.current_version_name"), "1.0.5017-settings"),
                ApplicationDetailItem(localized("app.current_version_code"), "5017"),
                ApplicationDetailItem(localized("app.preinstalled_version_name"), "1.0.5011-settings"),
                ApplicationDetailItem(localized("app.preinstalled_version_code"), "5011"),
            ),
            items,
        )
    }

    @Test
    fun unmodifiedSystemAppUsesTheActivePreinstalledVersionForBothGroups() {
        val items = buildApplicationVersionDetailItems(
            app = systemApp(),
            dumpsysOutput = """
                Packages:
                  Package [com.hat.settings] (preinstalled):
                    codePath=/product/app/HatSettings
                    versionCode=5011 minSdk=21 targetSdk=28
                    versionName=1.0.5011-settings
                    pkgFlags=[ SYSTEM HAS_CODE ]
            """.trimIndent(),
        ).associate { it.label to it.value }

        assertEquals("1.0.5011-settings", items[localized("app.current_version_name")])
        assertEquals("5011", items[localized("app.current_version_code")])
        assertEquals("1.0.5011-settings", items[localized("app.preinstalled_version_name")])
        assertEquals("5011", items[localized("app.preinstalled_version_code")])
    }

    @Test
    fun missingHiddenPackageDoesNotInventAPreinstalledVersionForAnUpdatedSystemApp() {
        val items = buildApplicationVersionDetailItems(
            app = systemApp(),
            dumpsysOutput = """
                Packages:
                  Package [com.hat.settings] (installed):
                    codePath=/data/app/com.hat.settings-updated
                    versionCode=5017 minSdk=21 targetSdk=28
                    versionName=1.0.5017-settings
                    pkgFlags=[ SYSTEM HAS_CODE UPDATED_SYSTEM_APP ]
            """.trimIndent(),
        ).associate { it.label to it.value }

        assertEquals("5017", items[localized("app.current_version_code")])
        assertEquals(localized("app.version_unavailable"), items[localized("app.preinstalled_version_name")])
        assertEquals(localized("app.version_unavailable"), items[localized("app.preinstalled_version_code")])
    }

    @Test
    fun updatedSystemFlagPreventsTreatingTheCurrentVersionAsPreinstalled() {
        val items = buildApplicationVersionDetailItems(
            app = systemApp(),
            dumpsysOutput = """
                Packages:
                  Package [com.hat.settings] (installed):
                    codePath=/product/app/HatSettings
                    versionCode=5017 minSdk=21 targetSdk=28
                    versionName=1.0.5017-settings
                    pkgFlags=[ SYSTEM HAS_CODE UPDATED_SYSTEM_APP ]
            """.trimIndent(),
        ).associate { it.label to it.value }

        assertEquals("5017", items[localized("app.current_version_code")])
        assertEquals(localized("app.version_unavailable"), items[localized("app.preinstalled_version_code")])
    }

    @Test
    fun missingActiveSystemPackageDoesNotReuseTheCachedListVersion() {
        val items = buildApplicationVersionDetailItems(
            app = systemApp(),
            dumpsysOutput = """
                Hidden system packages:
                  Package [com.hat.settings] (preinstalled):
                    codePath=/product/app/HatSettings
                    versionCode=5011 minSdk=21 targetSdk=28
                    versionName=1.0.5011-settings
            """.trimIndent(),
        ).associate { it.label to it.value }

        assertEquals(localized("app.version_unavailable"), items[localized("app.current_version_name")])
        assertEquals(localized("app.version_unavailable"), items[localized("app.current_version_code")])
        assertEquals("1.0.5011-settings", items[localized("app.preinstalled_version_name")])
        assertEquals("5011", items[localized("app.preinstalled_version_code")])
    }

    @Test
    fun thirdPartyDetailsRetainOrdinaryLabelsAndUseLiveVersionWhenAvailable() {
        val items = buildApplicationVersionDetailItems(
            app = systemApp().copy(isSystem = false),
            dumpsysOutput = """
                Packages:
                  Package [com.hat.settings] (installed):
                    codePath=/data/app/com.hat.settings-updated
                    versionCode=5017 minSdk=21 targetSdk=28
                    versionName=1.0.5017-settings
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                ApplicationDetailItem(localized("app.version_name"), "1.0.5017-settings"),
                ApplicationDetailItem(localized("app.version_code"), "5017"),
            ),
            items,
        )
    }

    @Test
    fun thirdPartyDetailsKeepExistingMetadataWhenLiveVersionIsUnavailable() {
        val items = buildApplicationVersionDetailItems(
            app = systemApp().copy(isSystem = false),
            dumpsysOutput = "",
        )

        assertEquals(
            listOf(
                ApplicationDetailItem(localized("app.version_name"), "cached-version"),
                ApplicationDetailItem(localized("app.version_code"), "5000"),
            ),
            items,
        )
    }

    @Test
    fun basicDetailsDoNotMixInstalledAndHiddenPackagePathsOrUserState() {
        val items = parsePackageDumpsysBasicItems(
            """
            Packages:
              Package [com.hat.settings] (installed):
                codePath=/data/app/com.hat.settings-updated
                dataDir=/data/user/0/com.hat.settings
                User 0: installed=true hidden=false
            Hidden system packages:
              Package [com.hat.settings] (preinstalled):
                codePath=/product/app/HatSettings
                dataDir=/data/user/0/com.hat.settings
                User 0: installed=false hidden=true
            Dexopt state:
              codePath=/ignored/path
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                ApplicationDetailItem("codePath", "/data/app/com.hat.settings-updated"),
                ApplicationDetailItem("dataDir", "/data/user/0/com.hat.settings"),
                ApplicationDetailItem("User 0", "installed=true hidden=false"),
            ),
            items,
        )
    }

    private fun systemApp() = InstalledAppInfo(
        packageName = "com.hat.settings",
        appName = "S设置",
        versionName = "cached-version",
        versionCode = 5000,
        isSystem = true,
        isEnabled = true,
        iconBytes = null,
    )
}
