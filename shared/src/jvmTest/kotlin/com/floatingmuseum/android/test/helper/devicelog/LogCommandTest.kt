package com.floatingmuseum.android.test.helper.devicelog

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LogCommandTest {
    @Test
    fun copiedDefaultUsesSelectedDeviceAndRemainsIndependentOfBuiltIn() {
        val copied = CustomLogCommand("copy", DEFAULT_LOGCAT_COMMAND)
        assertEquals(listOf("-b", "all", "-v", "threadtime", "-v", "year", "*:V"), parseCustomLogcatArguments(copied.command))
        val command = buildLogcatAdbCommand("current-device", copied.copy(command = copied.command.replace("<serialNumber>", "old-device")))
        assertEquals(listOf("-s", "current-device", "shell", "logcat"), command.args.take(4))
        assertFalse(command.displayCommand.contains("old-device"))
        assertFalse(command.studioFormat)
        assertTrue(buildLogcatAdbCommand("current-device").studioFormat)
        assertEquals("adb -s current-device shell logcat -b all -v threadtime -v year *:V", buildLogcatAdbCommand("current-device").displayCommand)
    }

    @Test
    fun supportsCommandPrefixesAndPreservesQuotedRegexArguments() {
        listOf("logcat", "shell logcat", "adb logcat", "adb shell logcat", "adb.exe -s old shell logcat").forEach {
            assertEquals(listOf("-v", "raw"), parseCustomLogcatArguments("$it -v raw"))
        }
        val raw = """logcat -e "error|fatal \d+" -T '09-21 10:00:00.000' '*:W'"""
        assertEquals(listOf("-e", "error|fatal \\d+", "-T", "09-21 10:00:00.000", "*:W"), parseCustomLogcatArguments(raw))
        val command = buildLogcatAdbCommand("device", CustomLogCommand("quoted", """logcat -e "it's ${'$'}(literal)""""))
        assertEquals("'it'\\''s ${'$'}(literal)'", command.args.last())
        assertEquals(listOf("-e", "a\"b"), parseCustomLogcatArguments("""logcat -e "a\"b""""))
        assertFalse(command.completesOnExit)
        assertTrue(buildLogcatAdbCommand("device", CustomLogCommand("dump", "logcat -d")).completesOnExit)
        listOf("-t20", "-m20", "--max-count 20", "--max-count=20").forEach {
            assertTrue(buildLogcatAdbCommand("device", CustomLogCommand("limited", "logcat $it")).completesOnExit)
        }
        assertFalse(buildLogcatAdbCommand("device", CustomLogCommand("continuous", "logcat -T 20")).completesOnExit)
    }

    @Test
    fun rejectsInvalidCommandsAndUnsupportedCaptureModes() {
        assertFailsWith<IllegalArgumentException> { parseCustomLogcatArguments("logcat \\\n-d") }
        listOf("", "adb -s", "adb -s old shell rm -rf /sdcard", "logcat -e 'missing", "logcat\n-d", "logcat | grep test", "logcat > file", "logcat; reboot", "logcat -B", "logcat -f /sdcard/test", "logcat --file=test", "logcat -c").forEach { text ->
            assertFailsWith<IllegalArgumentException>(text) { parseCustomLogcatArguments(text) }
        }
    }

    @Test
    fun commandsAndSelectionSurviveReloadAndSelectedDeletionRestoresDefault() {
        val directory = Files.createTempDirectory("ath_log_commands_").toFile()
        try {
            val file = directory.resolve("cache/log_commands.json")
            val repository = JvmLogCommandRepository { file }
            assertEquals(LogCommandConfiguration(), repository.load())
            val controller = LogCommandController(repository, { false }, {})
            controller.openManager()
            assertEquals(NEW_LOGCAT_COMMAND, controller.editorCommand)
            controller.updateCommand("logcat -d")
            controller.closeManager()
            controller.openManager()
            assertEquals(NEW_LOGCAT_COMMAND, controller.editorCommand)
            controller.updateName(" 警告日志 ")
            controller.updateCommand("logcat -v raw *:W")
            controller.saveEditor()
            assertFalse(controller.managerOpen)
            assertEquals("警告日志", repository.load().selectedName)
            val reloaded = LogCommandController(JvmLogCommandRepository { file }, { false }, {})
            assertEquals(controller.configuration, reloaded.configuration)
            reloaded.select(null)
            assertNull(repository.load().selectedCommand)
            assertEquals(1, repository.load().commands.size)
            reloaded.select("警告日志")
            assertEquals("警告日志", repository.load().selectedName)
            reloaded.edit(reloaded.configuration.commands.single())
            reloaded.updateName("错误日志")
            reloaded.updateCommand("logcat -v raw *:E")
            reloaded.saveEditor()
            assertEquals("错误日志", repository.load().selectedName)
            assertEquals(1, repository.load().commands.size)
            reloaded.delete("错误日志")
            assertEquals(LogCommandConfiguration(), repository.load())
            file.writeText("{ corrupt")
            assertEquals(LogCommandConfiguration(), repository.load())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsEmptyAndDuplicateNamesAndDoesNotLoseStateOnSaveFailure() {
        val saved = CustomLogCommand("Errors", "logcat *:E")
        var failSave = false
        val repository = object : LogCommandRepository {
            var data = LogCommandConfiguration(listOf(saved), saved.name)
            override fun load() = data
            override fun save(configuration: LogCommandConfiguration) {
                check(!failSave) { "disk unavailable" }
                data = configuration
            }
        }
        val controller = LogCommandController(repository, { false }, {})
        controller.openManager()
        controller.updateCommand("")
        controller.saveEditor()
        assertNotNull(controller.nameError)
        assertNotNull(controller.commandError)
        controller.updateName("errors")
        controller.updateCommand("logcat")
        controller.saveEditor()
        assertNotNull(controller.nameError)
        assertEquals(listOf(saved), controller.configuration.commands)
        controller.updateName("Other")
        failSave = true
        controller.saveEditor()
        assertNotNull(controller.persistenceError)
        assertTrue(controller.managerOpen)
        assertEquals("Other", controller.editorName)
        assertEquals(repository.data, controller.configuration)
        controller.select(null)
        assertEquals(saved.name, controller.configuration.selectedName)
        controller.delete(saved.name)
        assertEquals(listOf(saved), controller.configuration.commands)
    }

    @Test
    fun captureLocksCommandSelectionAndManagementAtControllerBoundary() {
        val command = CustomLogCommand("Errors", "logcat *:E")
        val configuration = LogCommandConfiguration(listOf(command), command.name)
        val repository = object : LogCommandRepository {
            override fun load() = configuration
            override fun save(configuration: LogCommandConfiguration) = error("Must not write during capture")
        }
        val controller = LogCommandController(repository, { true }, {})
        controller.openManager()
        controller.edit(command)
        controller.updateName("Changed")
        controller.updateCommand("logcat -d")
        controller.saveEditor()
        controller.select(null)
        controller.delete(command.name)
        assertEquals(configuration, controller.configuration)
        assertFalse(controller.managerOpen)
        assertEquals(NEW_LOGCAT_COMMAND, controller.editorCommand)
    }

    @Test
    fun invalidPersistedCommandsAndDanglingSelectionFallBackToDefault() {
        val commands = listOf(CustomLogCommand(" ", "logcat"), CustomLogCommand("bad", "adb reboot"),
            CustomLogCommand(" valid ", " logcat "), CustomLogCommand("VALID", "logcat -d"))
        val normalized = LogCommandConfiguration(commands, "bad").normalized()
        assertEquals(listOf(CustomLogCommand("valid", "logcat")), normalized.commands)
        assertNull(normalized.selectedCommand)
        assertNull(normalized.selectedName)
    }
}
