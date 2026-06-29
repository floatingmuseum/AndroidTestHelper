package com.floatingmuseum.android.test.helper.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

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
    fun parsesApplicationDumpsysBasicItems() {
        val items = parsePackageDumpsysBasicItems(
            """
            Package [com.example.app] (123abc):
              userId=10234
              codePath=/data/app/~~token/com.example.app/base.apk
              resourcePath=/data/app/~~token/com.example.app/base.apk
              legacyNativeLibraryDir=/data/app/~~token/com.example.app/lib
              primaryCpuAbi=arm64-v8a
              dataDir=/data/user/0/com.example.app
              User 0: ceDataInode=123 installed=true hidden=false suspended=false stopped=false notLaunched=false enabled=0
            """.trimIndent(),
        )

        assertEquals("10234", items.first { it.label == "userId" }.value)
        assertEquals("/data/user/0/com.example.app", items.first { it.label == "dataDir" }.value)
        assertEquals(
            "ceDataInode=123 installed=true hidden=false suspended=false stopped=false notLaunched=false enabled=0",
            items.first { it.label == "User 0" }.value,
        )
    }

    @Test
    fun parsesApplicationDumpsysPermissionLines() {
        val items = parsePackageDumpsysPermissionItems(
            """
            requested permissions:
              android.permission.INTERNET
              com.example.permission.INTERNAL
            install permissions:
              android.permission.CAMERA: granted=true
            runtime permissions:
              android.permission.POST_NOTIFICATIONS: granted=false, flags=[ USER_SET ]
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                "android.permission.INTERNET",
                "com.example.permission.INTERNAL",
                "android.permission.CAMERA: granted=true",
                "android.permission.POST_NOTIFICATIONS: granted=false, flags=[ USER_SET ]",
            ),
            items.map { it.value },
        )
    }

    @Test
    fun parsesApplicationDumpsysSigningBlock() {
        val items = parsePackageDumpsysSigningItems(
            """
            Package [com.example.app] (123abc):
              Signing Details:
                Scheme: v3
                Signatures: [308203]
                past signatures: []
              install permissions:
                android.permission.INTERNET: granted=true
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                "Scheme: v3",
                "Signatures: [308203]",
                "past signatures: []",
            ),
            items.map { it.value },
        )
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

    @Test
    fun removesInstalledAppByExactPackageName() {
        val apps = listOf(
            testInstalledApp("com.example.target", "Target"),
            testInstalledApp("com.example.target.beta", "Target Beta"),
            testInstalledApp("com.android.settings", "Settings"),
        )

        assertEquals(
            listOf(apps[1], apps[2]),
            removeInstalledApp(apps, "com.example.target"),
        )
    }

    @Test
    fun buildsStableApplicationIconCacheFileNameForSameVersion() {
        val first = buildApplicationIconCacheFileName(
            packageName = "com.example.app",
            versionName = "1.2.3",
            versionCode = 42,
        )
        val second = buildApplicationIconCacheFileName(
            packageName = "com.example.app",
            versionName = "ignored",
            versionCode = 42,
        )

        assertEquals(first, second)
    }

    @Test
    fun changesApplicationIconCacheFileNameWhenVersionChanges() {
        val oldVersion = buildApplicationIconCacheFileName(
            packageName = "com.example.app",
            versionName = "1.2.3",
            versionCode = 42,
        )
        val newVersion = buildApplicationIconCacheFileName(
            packageName = "com.example.app",
            versionName = "1.2.4",
            versionCode = 43,
        )

        kotlin.test.assertNotEquals(oldVersion, newVersion)
    }

    @Test
    fun usesVersionNameForApplicationIconCacheWhenVersionCodeMissing() {
        val fileName = buildApplicationIconCacheFileName(
            packageName = "com.example.app",
            versionName = "1.2.3-beta",
            versionCode = null,
        )

        kotlin.test.assertContains(fileName ?: "", "com.example.app__vn_1.2.3-beta__")
    }

    @Test
    fun skipsApplicationIconCacheWhenVersionIdentityMissing() {
        assertEquals(
            null,
            buildApplicationIconCacheFileName(
                packageName = "com.example.app",
                versionName = "-",
                versionCode = null,
            ),
        )
    }

    @Test
    fun buildsPluginIgnoreKeyFromBundledApkVersion() {
        val oldDesktopBoundKey = "1.0.0"
        val pluginVersion = PluginVersionInfo(versionCode = 105, versionName = "1.0.5")

        val ignoreKey = pluginVersion.pluginCheckIgnoreKey()

        assertEquals("athplugin:105:1.0.5", ignoreKey)
        kotlin.test.assertNotEquals(oldDesktopBoundKey, ignoreKey)
    }

    @Test
    fun selectsLatestLocalPluginApkByParsedVersionCode() {
        val old = LocalPluginApkCandidate(
            name = "ATHPlugin_1.0.9.apk",
            bytes = byteArrayOf(1),
            versionInfo = PluginVersionInfo(versionCode = 109, versionName = "1.0.9"),
        )
        val latest = LocalPluginApkCandidate(
            name = "ATHPlugin_1.0.5.apk",
            bytes = byteArrayOf(2),
            versionInfo = PluginVersionInfo(versionCode = 205, versionName = "1.0.5"),
        )

        assertEquals(
            latest,
            selectLatestLocalPluginApkCandidate(listOf(old, latest)),
        )
    }

    @Test
    fun selectsLatestLocalPluginApkByFileNameWhenMetadataIsMissing() {
        val old = LocalPluginApkCandidate(
            name = "ATHPlugin_1.0.9.apk",
            bytes = byteArrayOf(1),
            versionInfo = null,
        )
        val latest = LocalPluginApkCandidate(
            name = "ATHPlugin_1.0.10.apk",
            bytes = byteArrayOf(2),
            versionInfo = null,
        )

        assertEquals(
            latest,
            selectLatestLocalPluginApkCandidate(listOf(old, latest)),
        )
    }

    @Test
    fun prefersVersionedLocalPluginApkOverNameOnlyCandidate() {
        val nameOnly = LocalPluginApkCandidate(
            name = "ATHPlugin_9.9.9.apk",
            bytes = byteArrayOf(1),
            versionInfo = null,
        )
        val parsed = LocalPluginApkCandidate(
            name = "ATHPlugin_1.0.5.apk",
            bytes = byteArrayOf(2),
            versionInfo = PluginVersionInfo(versionCode = 105, versionName = "1.0.5"),
        )

        assertEquals(
            parsed,
            selectLatestLocalPluginApkCandidate(listOf(nameOnly, parsed)),
        )
    }

    @Test
    fun loadsBundledPluginApkVersionFromComposeResourcesWhenPresent() = kotlinx.coroutines.runBlocking {
        val filesDirectory = File("shared/src/commonMain/composeResources/files")
        val apkFiles = filesDirectory
            .listFiles()
            ?.filter { it.isFile && it.name.startsWith("ATHPlugin") && it.name.endsWith(".apk") }
            ?: return@runBlocking
        if (apkFiles.isEmpty()) return@runBlocking

        val appAdb = createAppAdb()
        val candidates = apkFiles.map { file ->
            val fileBytes = file.readBytes()
            LocalPluginApkCandidate(
                name = file.name,
                bytes = fileBytes,
                versionInfo = appAdb.getApkVersionInfo(fileBytes),
            )
        }
        candidates.firstOrNull { it.name == "ATHPlugin_1.0.5.apk" }?.let { candidate ->
            assertEquals("1.0.5", assertNotNull(candidate.versionInfo).versionName)
        }

        val expected = assertNotNull(selectLatestLocalPluginApkCandidate(candidates))
        val bytes = assertNotNull(appAdb.getLocalPluginApkBytes())
        val versionInfo = assertNotNull(appAdb.getApkVersionInfo(bytes))

        assertEquals(expected.versionInfo, versionInfo)
        assertContentEquals(expected.bytes, bytes)
    }

    @Test
    fun buildsClearApplicationCacheCommandWithExplicitSerial() {
        val command = buildClearApplicationCacheCommand(
            deviceSerial = "serial-123",
            packageName = "com.example.app",
        )

        assertEquals(
            listOf("-s", "serial-123", "shell", "pm", "clear", "--cache-only", "com.example.app"),
            command.args,
        )
        assertEquals(
            "adb -s serial-123 shell pm clear --cache-only com.example.app",
            command.displayCommand,
        )
    }

    @Test
    fun buildsThirdPartyUninstallCommandWithExplicitSerial() {
        val command = buildUninstallApplicationCommand(
            deviceSerial = "serial-123",
            packageName = "com.example.app",
            isSystem = false,
        )

        assertEquals(
            listOf("-s", "serial-123", "uninstall", "com.example.app"),
            command.args,
        )
        assertEquals(
            "adb -s serial-123 uninstall com.example.app",
            command.displayCommand,
        )
    }

    @Test
    fun buildsSystemUninstallCommandForUserZeroWithExplicitSerial() {
        val command = buildUninstallApplicationCommand(
            deviceSerial = "serial-123",
            packageName = "com.android.settings",
            isSystem = true,
        )

        assertEquals(
            listOf("-s", "serial-123", "shell", "pm", "uninstall", "--user", "0", "com.android.settings"),
            command.args,
        )
        assertEquals(
            "adb -s serial-123 shell pm uninstall --user 0 com.android.settings",
            command.displayCommand,
        )
    }

    @Test
    fun acceptsSuccessfulUninstallOutput() {
        requireSuccessfulUninstallOutput(
            output = "\nSuccess\n",
            displayCommand = "adb -s serial-123 uninstall com.example.app",
        )
    }

    @Test
    fun rejectsFailedUninstallOutputEvenWithZeroExitCode() {
        assertFailsWith<IllegalStateException> {
            requireSuccessfulUninstallOutput(
                output = "Failure [DELETE_FAILED_DEVICE_POLICY_MANAGER]",
                displayCommand = "adb -s serial-123 uninstall com.example.app",
            )
        }
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
