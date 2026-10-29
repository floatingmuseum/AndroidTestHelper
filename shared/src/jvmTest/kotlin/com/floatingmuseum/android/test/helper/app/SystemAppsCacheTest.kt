package com.floatingmuseum.android.test.helper.app

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemAppsCacheTest {
    @Test
    fun rejectsUnversionedCacheWithPreinstalledVersionMetadata() {
        val cache = Json.decodeFromString<CachedSystemApps>(
            """
            {
              "apps": [{
                "packageName": "com.hat.settings",
                "appName": "S设置",
                "versionName": "1.0.5011",
                "versionCode": 5011,
                "isSystem": true,
                "isEnabled": true,
                "iconBytes": null
              }],
              "cacheTimeMillis": 1,
              "cacheTimeFormatted": "2026-10-29 09:56:41"
            }
            """.trimIndent(),
        )

        assertEquals(1, cache.schemaVersion)
        assertEquals(5011L, cache.apps.single().versionCode)
        assertFalse(cache.isCompatible())
    }

    @Test
    fun persistsCurrentCacheVersionWithDefaultJsonSettings() {
        val cache = CachedSystemApps(
            apps = listOf(
                InstalledAppInfo(
                    packageName = "com.hat.settings",
                    appName = "S设置",
                    versionName = "1.0.5017",
                    versionCode = 5017,
                    isSystem = true,
                    isEnabled = true,
                    iconBytes = null,
                ),
            ),
            cacheTimeMillis = 2,
            cacheTimeFormatted = "2026-10-29 10:00:00",
            schemaVersion = CurrentSystemAppsCacheVersion,
        )

        val json = Json.encodeToString(cache)
        val restored = Json.decodeFromString<CachedSystemApps>(json)

        assertTrue(json.contains("\"schemaVersion\":$CurrentSystemAppsCacheVersion"))
        assertEquals(cache, restored)
        assertEquals(5017L, restored.apps.single().versionCode)
        assertTrue(restored.isCompatible())
    }

    @Test
    fun rejectsUnsupportedCacheVersions() {
        for (version in listOf(-1, 0, 1, CurrentSystemAppsCacheVersion + 1)) {
            val cache = Json.decodeFromString<CachedSystemApps>(
                """{"apps":[],"cacheTimeMillis":1,"cacheTimeFormatted":"cached","schemaVersion":$version}""",
            )

            assertFalse(cache.isCompatible(), "Unexpected compatible schema version: $version")
        }
    }
}
