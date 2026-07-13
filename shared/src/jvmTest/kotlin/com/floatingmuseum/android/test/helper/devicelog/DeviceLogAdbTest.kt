package com.floatingmuseum.android.test.helper.devicelog

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceLogAdbTest {
    @Test
    fun testBuildDeviceLogFileNameUsesModelSerialAndTimestamp() {
        val capturedAt = LocalDateTime.of(2026, 6, 17, 15, 4, 9)

        val fileName = buildDeviceLogFileName(
            deviceModel = "SM-X700",
            deviceSerial = "R58M123ABC",
            capturedAt = capturedAt,
        )

        assertEquals("SM-X700_R58M123ABC_20260617_150409.log", fileName)
    }

    @Test
    fun testBuildDeviceLogFileNameSanitizesPathTokens() {
        val capturedAt = LocalDateTime.of(2026, 6, 17, 15, 4, 9)

        val fileName = buildDeviceLogFileName(
            deviceModel = "Pixel Tablet/Debug",
            deviceSerial = "192.168.1.5:5555",
            capturedAt = capturedAt,
        )

        assertEquals("Pixel_Tablet_Debug_192.168.1.5_5555_20260617_150409.log", fileName)
    }

    @Test
    fun defaultLogCommandMatchesExistingLogcatCommand() {
        val command = buildLogcatAdbCommand(
            deviceSerial = "R58M123ABC",
            preset = defaultLogCommandPreset(),
        )

        assertEquals(
            listOf(
                "-s",
                "R58M123ABC",
                "shell",
                "logcat",
                "-b",
                "all",
                "-v",
                "threadtime",
                "-v",
                "year",
                "-v",
                "zone",
                "-v",
                "usec",
                "-v",
                "uid",
            ),
            command.args,
        )
        assertEquals(
            "adb -s R58M123ABC shell logcat -b all -v threadtime -v year -v zone -v usec -v uid",
            command.displayCommand,
        )
    }

    @Test
    fun defaultTemplatesCoverFiveCommonLogcatScenarios() {
        val templates = defaultLogCommandTemplates()

        assertEquals(5, templates.size)
        assertTrue(templates.all { it.preset.parts.size == it.parameterExplanationKeys.size })
        assertTrue(templates.all { it.prefixExplanationKey.isNotBlank() })

        val commands = templates.map {
            buildLogcatAdbCommand("R58M123ABC", it.preset).displayCommand
        }
        assertTrue(commands.any { it.contains("-b all") })
        assertTrue(commands.any { it.contains("ActivityManager:I") })
        assertTrue(commands.any { it.contains("-b crash") })
        assertTrue(commands.any { it.contains("-T 500") })
        assertTrue(commands.any { it.contains("FATAL EXCEPTION") })
    }

    @Test
    fun logCommandPartsAllowRepeatedTypesAndPreserveOrder() {
        val preset = LogCommandPreset(
            id = "custom",
            name = "Repeated parts",
            parts = listOf(
                LogCommandPart(LogCommandPartType.Buffer, "main"),
                LogCommandPart(LogCommandPartType.Buffer, "system"),
                LogCommandPart(LogCommandPartType.Format, "threadtime"),
                LogCommandPart(LogCommandPartType.Format, "uid"),
                LogCommandPart(LogCommandPartType.Filter, "ActivityManager:I"),
                LogCommandPart(LogCommandPartType.Filter, "*:S"),
            ),
        )

        val command = buildLogcatAdbCommand("R58M123ABC", preset)

        assertEquals(
            listOf(
                "-s",
                "R58M123ABC",
                "shell",
                "logcat",
                "-b",
                "main",
                "-b",
                "system",
                "-v",
                "threadtime",
                "-v",
                "uid",
                "ActivityManager:I",
                "*:S",
            ),
            command.args,
        )
    }

    @Test
    fun addingAllBufferReplacesOtherBuffers() {
        val parts = listOf(
            LogCommandPart(LogCommandPartType.Buffer, "main"),
            LogCommandPart(LogCommandPartType.Format, "threadtime"),
            LogCommandPart(LogCommandPartType.Buffer, "system"),
        )

        val updated = addLogCommandPart(parts, LogCommandPart(LogCommandPartType.Buffer, "all"))

        assertEquals(
            listOf(
                LogCommandPart(LogCommandPartType.Format, "threadtime"),
                LogCommandPart(LogCommandPartType.Buffer, "all"),
            ),
            updated,
        )
    }

    @Test
    fun addingSpecificBufferRemovesAllAndDuplicateBuffer() {
        val parts = listOf(
            LogCommandPart(LogCommandPartType.Buffer, "all"),
            LogCommandPart(LogCommandPartType.Format, "threadtime"),
            LogCommandPart(LogCommandPartType.Buffer, "main"),
        )

        val updated = addLogCommandPart(parts, LogCommandPart(LogCommandPartType.Buffer, "main"))

        assertEquals(
            listOf(
                LogCommandPart(LogCommandPartType.Format, "threadtime"),
                LogCommandPart(LogCommandPartType.Buffer, "main"),
            ),
            updated,
        )
    }

    @Test
    fun buildsAdvancedLogcatOptionsFromStructuredParts() {
        val preset = LogCommandPreset(
            id = "custom",
            name = "Advanced",
            parts = listOf(
                LogCommandPart(LogCommandPartType.Dividers, "enabled"),
                LogCommandPart(LogCommandPartType.Silent, "enabled"),
                LogCommandPart(LogCommandPartType.Regex, "Exception|ANR"),
                LogCommandPart(LogCommandPartType.Pid, "1234"),
                LogCommandPart(LogCommandPartType.MaxCount, "20"),
                LogCommandPart(LogCommandPartType.Recent, "100"),
            ),
        )

        val command = buildLogcatAdbCommand("R58M123ABC", preset)

        assertEquals(
            listOf(
                "-s",
                "R58M123ABC",
                "shell",
                "logcat",
                "-D",
                "-s",
                "-e",
                "Exception|ANR",
                "--pid=1234",
                "-m",
                "20",
                "-T",
                "100",
            ),
            command.args,
        )
    }

    @Test
    fun savedLogCommandPresetDoesNotPersistDeviceSerial() {
        val preset = createSavedLogCommandPreset(
            id = "preset-1",
            name = "Activity only",
            sourcePreset = LogCommandPreset(
                id = "current",
                name = "",
                parts = listOf(LogCommandPart(LogCommandPartType.Filter, "ActivityManager:I")),
            ),
        )
        val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

        val encoded = json.encodeToString(preset)
        val command = buildLogcatAdbCommand("R58M123ABC", preset!!)

        assertFalse(encoded.contains("R58M123ABC"))
        assertTrue(command.displayCommand.contains("adb -s R58M123ABC shell logcat"))
    }

    @Test
    fun savedLogCommandPresetAllowsCommandWithoutParts() {
        val preset = createSavedLogCommandPreset(
            id = "preset-raw",
            name = "Raw logcat",
            sourcePreset = LogCommandPreset(
                id = "current",
                name = "",
                parts = emptyList(),
            ),
        )
        val command = buildLogcatAdbCommand(LOG_COMMAND_SERIAL_PLACEHOLDER, preset!!)

        assertEquals(emptyList(), preset.parts)
        assertEquals(
            "adb -s <serialNumber> shell logcat",
            command.displayCommand,
        )
    }

    @Test
    fun savedLogCommandPresetRejectsBlankName() {
        assertNull(
            createSavedLogCommandPreset(
                id = "preset-1",
                name = "   ",
                sourcePreset = defaultLogCommandPreset(),
            ),
        )
    }
}
