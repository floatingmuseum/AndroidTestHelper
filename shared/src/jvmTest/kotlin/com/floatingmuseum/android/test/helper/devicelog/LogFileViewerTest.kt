package com.floatingmuseum.android.test.helper.devicelog

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LogFileViewerTest {
    @Test
    fun savedCaptureRoundTripsAllSevenColumnsIncludingLongTagsAndSpaces() {
        listOf("Launcher Click", "ActivityTaskManager", "A very long tag with spaces beyond twenty four characters", "").forEach { tag ->
            listOf("system_server", "com.example.a.process.name.longer.than.thirty.two", "-").forEach { process ->
                val entry = LogcatEntry("2026-09-21 10:29:03.694", "123456", "987654", "D", tag, "  内容: with   spaces D  and trailing  ")
                val row = parseLogFileRow(entry.format(process), 16)
                assertEquals("2026-09-21", row.date)
                assertEquals("10:29:03.694", row.time)
                assertEquals("123456-987654", row.pidTid)
                assertEquals(tag, row.tag)
                assertEquals(process, row.process)
                assertEquals("D", row.priority)
                assertEquals(entry.message, row.message)
            }
        }
    }

    @Test
    fun unknownAndRawLinesArePreserved() {
        val raw = "2026-09-21 10:29:03.694  2125  3270 I Launcher.Click: hello: world"
        val parsed = parseLogFileRow(raw, 2)
        assertEquals("2125-3270", parsed.pidTid)
        assertEquals("-", parsed.process)
        assertEquals("hello: world", parsed.message)
        assertEquals("09-21", parseLogFileRow(raw.removePrefix("2026-"), 2).date)
        listOf("", "--------- beginning of main", "Columns: Date Time PID-TID Tag Process Priority Message", "  at sample.method(Test.kt:7)").forEach {
            val row = parseLogFileRow(it, 3)
            assertEquals(it, row.message)
            assertEquals("", row.date)
        }
        assertEquals("2026-09-21", parseLogFileRow("\uFEFF$raw", 1).date)
    }

    @Test
    fun wholeFileIndexKeepsUtf8CrlfBlankAndFinalLinesWithoutDuplicates() = runBlocking {
        withLog("\uFEFF第一行 😀\r\n\r\n第三行\n尾行") { file ->
            val content = JvmLogFileReader().read(file.path, LogKeywordFilter())
            assertEquals(4, content.rowCount)
            assertEquals(4, content.totalLines)
            assertEquals(listOf("第一行 😀", "", "第三行", "尾行"), content.readRows(0, 100).map { it.message })
            assertEquals(listOf(3L, 4L), content.readRows(2, 2).map { it.lineNumber })
            assertEquals("第一行 😀", content.readRows(0, 1).single().message)
        }
    }

    @Test
    fun allRowsBeyondTheOldPageLimitAreAddressableInOneResult() = runBlocking {
        val text = (1..10_500).joinToString("\n") { "row $it 中文 😀" }
        withLog(text) { file ->
            val content = JvmLogFileReader().read(file.path, LogKeywordFilter())
            assertEquals(10_500, content.rowCount)
            assertEquals("row 10500 中文 😀", content.readRows(10_499, 128).single().message)
            assertEquals(listOf(999L, 1000L, 1001L, 1002L), content.readRows(998, 4).map { it.lineNumber })
            assertEquals("row 1 中文 😀", content.readRows(0, 1).single().message)
        }
    }

    @Test
    fun searchIndexesTheWholeFileIncludingHiddenMetadataWithLiteralOrCaseRules() = runBlocking {
        val entry = LogcatEntry("2026-09-21 10:29:03.694", "123", "456", "E", "Tag", "a.* [literal]")
        val text = (1..2_100).joinToString("\n") { "unrelated $it" } + "\n" + entry.format("SearchableProcess") + "\nfinal match"
        withLog(text) { file ->
            val reader = JvmLogFileReader()
            val content = reader.read(file.path, LogKeywordFilter("searchableprocess | final"))
            assertEquals(2, content.rowCount)
            assertEquals(2102, content.totalLines)
            val rows = content.readRows(0, 128)
            assertEquals(listOf(2101L, 2102L), rows.map { it.lineNumber })
            assertEquals("SearchableProcess", rows.first().process)
            assertEquals("final match", rows.last().message)
            assertEquals(0, reader.read(file.path, LogKeywordFilter("searchableprocess", true)).rowCount)
            assertEquals("a.* [literal]", reader.read(file.path, LogKeywordFilter("a.*")).readRows(0, 1).single().message)
            assertEquals(0, reader.read(file.path, LogKeywordFilter("a.+")).rowCount)
        }
    }

    @Test
    fun scanningAcrossByteBufferBoundariesPreservesWholeUtf8Lines() = runBlocking {
        val longLine = "x".repeat(65_535) + "😀 内容"
        withLog("$longLine\r\n\r\n尾行") { file ->
            val reader = JvmLogFileReader()
            assertEquals(listOf(longLine, "", "尾行"), reader.read(file.path, LogKeywordFilter()).readRows(0, 10).map { it.message })
            assertEquals(longLine, reader.read(file.path, LogKeywordFilter("😀")).readRows(0, 1).single().message)
            file.writeText("")
            val empty = reader.read(file.path, LogKeywordFilter())
            assertEquals(0, empty.rowCount)
            assertTrue(empty.readRows(0, 100).isEmpty())
        }
    }

    @Test
    fun truncationIsReportedAndAppendingNeedsReload() = runBlocking {
        withLog("first\nsecond\n") { file ->
            val reader = JvmLogFileReader()
            val content = reader.read(file.path, LogKeywordFilter())
            file.appendText("third\n")
            assertEquals(2, content.rowCount)
            assertEquals(listOf("first", "second"), content.readRows(0, 10).map { it.message })
            assertEquals(3, reader.read(file.path, LogKeywordFilter()).rowCount)
            file.writeText("first")
            assertFailsWith<IllegalStateException> { content.readRows(0, 10) }
        }
    }

    @Test
    fun oldReadCannotReplaceANewerFileAndWindowCloseReleasesState() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val oldRead = CompletableDeferred<Unit>()
        val reader = object : LogFileReader {
            override suspend fun read(path: String, filter: LogKeywordFilter): LogFileContent {
                if (path == "old") withContext(NonCancellable) { oldRead.await() }
                return testContent(path)
            }
        }
        try {
            val controller = LogFileViewerController(scope, reader)
            controller.openFile("old")
            controller.openFile("new")
            oldRead.complete(Unit)
            withTimeout(5_000) { while (controller.isLoading) delay(1) }
            assertTrue(controller.isWindowOpen)
            assertEquals("new", controller.content?.filePath)
            LogFileColumn.entries.forEach(controller::toggleColumn)
            assertEquals(7, controller.hiddenColumns.size)
            controller.showAllColumns()
            assertTrue(controller.hiddenColumns.isEmpty())
            controller.closeWindow()
            assertFalse(controller.isWindowOpen)
            assertNull(controller.content)
            assertNull(controller.filePath)
            assertFalse(controller.isLoading)
            controller.openWindow()
            assertTrue(controller.isWindowOpen)
            assertNull(controller.content)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun visibleTextCacheIsBoundedAndRowsReloadAfterEviction() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var reads = 0
        val reader = object : LogFileReader {
            override suspend fun read(path: String, filter: LogKeywordFilter) = testContent(path) { reads++ }
        }
        try {
            val controller = LogFileViewerController(scope, reader)
            controller.openFile("large")
            assertEquals(0, reads) // Index creation does not read/parse all text into the controller.
            controller.ensureRows(0)
            controller.ensureRows(1)
            assertEquals(1, reads)
            for (block in 1..LOG_VIEWER_CACHED_BLOCKS) controller.ensureRows(block * LOG_VIEWER_BLOCK_ROWS)
            assertNull(controller.rowAt(0))
            assertNotNull(controller.rowAt(LOG_VIEWER_CACHED_BLOCKS * LOG_VIEWER_BLOCK_ROWS))
            controller.ensureRows(0)
            assertEquals("row 0", controller.rowAt(0)?.message)
            controller.closeWindow()
            assertNull(controller.rowAt(0))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun closingWhileVisibleRowsLoadCannotRestoreOldText() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val pending = CompletableDeferred<Unit>()
        val reader = object : LogFileReader {
            override suspend fun read(path: String, filter: LogKeywordFilter) = object : LogFileContent {
                override val filePath = path
                override val totalLines = 1
                override val rowCount = 1
                override suspend fun readRows(start: Int, count: Int): List<LogFileRow> {
                    withContext(NonCancellable) { pending.await() }
                    return listOf(LogFileRow(1, "old row"))
                }
            }
        }
        try {
            val controller = LogFileViewerController(scope, reader)
            controller.openFile("old")
            controller.ensureRows(0)
            controller.closeWindow()
            pending.complete(Unit)
            assertNull(controller.rowAt(0))
            assertNull(controller.content)
            assertFalse(controller.isWindowOpen)
        } finally {
            scope.cancel()
        }
    }

    private fun testContent(path: String, onRead: () -> Unit = {}) = object : LogFileContent {
        override val filePath = path
        override val rowCount = 10_500
        override val totalLines = rowCount
        override suspend fun readRows(start: Int, count: Int): List<LogFileRow> {
            onRead()
            return (start until minOf(start + count, rowCount)).map { LogFileRow(it.toLong() + 1, "row $it") }
        }
    }

    private suspend fun withLog(text: String, block: suspend (File) -> Unit) {
        val directory = Files.createTempDirectory("ath-log-viewer-test").toFile()
        try {
            val file = File(directory, "中文 日志.log")
            file.writeText(text)
            block(file)
        } finally {
            directory.deleteRecursively()
        }
    }
}
