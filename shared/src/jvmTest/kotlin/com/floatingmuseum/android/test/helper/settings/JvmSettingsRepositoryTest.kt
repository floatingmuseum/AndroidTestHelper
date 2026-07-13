package com.floatingmuseum.android.test.helper.settings

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmSettingsRepositoryTest {
    @Test
    fun missingSettingsFileUsesMp4WithoutUserSelectionMarker() {
        withTemporarySettingsFile { settingsFile ->
            val settings = JvmSettingsRepository { settingsFile }.loadSettings()

            assertEquals(ScreenRecordFormat.Mp4, settings.screenRecordFormat)
            assertFalse(settings.screenRecordFormatUserSelected)
        }
    }

    @Test
    fun oldSettingsWithoutScreenRecordFormatMigratesToMp4() {
        withTemporarySettingsFile { settingsFile ->
            settingsFile.writeText(
                """
                {
                    "showCommandTime": true
                }
                """.trimIndent()
            )

            val settings = JvmSettingsRepository { settingsFile }.loadSettings()

            assertEquals(ScreenRecordFormat.Mp4, settings.screenRecordFormat)
            assertFalse(settings.screenRecordFormatUserSelected)
        }
    }

    @Test
    fun oldSettingsWithStoredMkvPreservesUserChoice() {
        withTemporarySettingsFile { settingsFile ->
            settingsFile.writeText(
                """
                {
                    "screenRecordFormat": "Mkv"
                }
                """.trimIndent()
            )

            val settings = JvmSettingsRepository { settingsFile }.loadSettings()

            assertEquals(ScreenRecordFormat.Mkv, settings.screenRecordFormat)
            assertTrue(settings.screenRecordFormatUserSelected)
        }
    }

    @Test
    fun savingExplicitMkvPersistsUserSelectionMarker() {
        withTemporarySettingsFile { settingsFile ->
            JvmSettingsRepository { settingsFile }.saveSettings(
                AppSettings(
                    screenRecordFormat = ScreenRecordFormat.Mkv,
                    screenRecordFormatUserSelected = true,
                )
            )

            val jsonText = settingsFile.readText()

            assertTrue(jsonText.contains("\"screenRecordFormat\": \"Mkv\""))
            assertTrue(jsonText.contains("\"screenRecordFormatUserSelected\": true"))
        }
    }

    @Test
    fun savingExplicitMp4PersistsUserSelectionMarker() {
        withTemporarySettingsFile { settingsFile ->
            JvmSettingsRepository { settingsFile }.saveSettings(
                AppSettings(screenRecordFormatUserSelected = true)
            )

            val settings = JvmSettingsRepository { settingsFile }.loadSettings()

            assertEquals(ScreenRecordFormat.Mp4, settings.screenRecordFormat)
            assertTrue(settings.screenRecordFormatUserSelected)
        }
    }

    @Test
    fun savingLogCommandTemplateExpansionStatePersists() {
        withTemporarySettingsFile { settingsFile ->
            JvmSettingsRepository { settingsFile }.saveSettings(
                AppSettings(defaultLogCommandTemplatesExpanded = false),
            )

            val settings = JvmSettingsRepository { settingsFile }.loadSettings()

            assertFalse(settings.defaultLogCommandTemplatesExpanded)
        }
    }

    private fun withTemporarySettingsFile(block: (java.io.File) -> Unit) {
        val tempRoot = Files.createTempDirectory("ath_settings_").toFile()
        try {
            block(tempRoot.resolve("settings.json"))
        } finally {
            tempRoot.deleteRecursively()
        }
    }
}
