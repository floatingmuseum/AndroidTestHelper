package com.floatingmuseum.android.test.helper.monkey

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MonkeyJvmTest {
    @Test
    fun presetRepositoryRoundTripsWithoutDeviceBinding() {
        withTemporaryPresetFile { presetFile ->
            val repository = JvmMonkeyPresetRepository { presetFile }
            val preset = MonkeyPreset(
                id = "preset-1",
                name = " Stability ",
                form = MonkeyTestForm(
                    packages = listOf(" com.example.app "),
                    seed = "42",
                ),
            )

            repository.savePresets(listOf(preset))
            val loaded = repository.loadPresets()

            assertEquals("Stability", loaded.single().name)
            assertEquals(listOf("com.example.app"), loaded.single().form.packages)
            assertFalse(presetFile.readText().contains("deviceSerial"))
            assertFalse(presetFile.readText().contains("transportId"))
        }
    }

    @Test
    fun corruptPresetFileFallsBackToEmptyList() {
        withTemporaryPresetFile { presetFile ->
            presetFile.writeText("{broken")

            assertEquals(emptyList(), JvmMonkeyPresetRepository { presetFile }.loadPresets())
        }
    }

    @Test
    fun presetFileIgnoresUnknownFields() {
        withTemporaryPresetFile { presetFile ->
            presetFile.writeText(
                """
                {
                  "unknownRoot": true,
                  "presets": [
                    {
                      "id": "preset-1",
                      "name": "Regression",
                      "unknownPreset": 7,
                      "form": {
                        "packages": ["com.example.app"],
                        "seed": "42",
                        "unknownForm": "ignored"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            )

            val loaded = JvmMonkeyPresetRepository { presetFile }.loadPresets()

            assertEquals("Regression", loaded.single().name)
            assertEquals("42", loaded.single().form.seed)
        }
    }

    @Test
    fun parsesMonkeyProcessIdsFromPsOutput() {
        val output = """
            USER      PID   PPID  VSZ      RSS   WCHAN PC NAME
            shell     1234  100   10000    1000  0     0  com.android.commands.monkey
            u0_a123   5678  100   10000    1000  0     0  com.example.app
        """.trimIndent()

        assertEquals(listOf("1234"), parseMonkeyProcessIds(output))
    }

    @Test
    fun reportFileCollisionUsesIncrementingSuffix() {
        val directory = Files.createTempDirectory("android-test-helper-monkey-report-test").toFile()
        try {
            directory.resolve("monkey_device_target_20260717_120000.log").writeText("first")
            directory.resolve("monkey_device_target_20260717_120000_2.log").writeText("second")

            val resolved = resolveUniqueMonkeyReportFile(
                directory,
                "monkey_device_target_20260717_120000.log",
            )

            assertEquals("monkey_device_target_20260717_120000_3.log", resolved.name)
        } finally {
            directory.deleteRecursively()
        }
    }

    private inline fun withTemporaryPresetFile(block: (java.io.File) -> Unit) {
        val directory = Files.createTempDirectory("android-test-helper-monkey-test").toFile()
        try {
            block(directory.resolve("monkey_presets.json"))
        } finally {
            directory.deleteRecursively()
        }
    }
}
