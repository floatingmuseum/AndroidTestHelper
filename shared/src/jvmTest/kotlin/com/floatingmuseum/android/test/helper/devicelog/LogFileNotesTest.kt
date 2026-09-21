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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LogFileNotesTest {
    @Test
    fun editingFromLogRowsPreservesTimelineExpansion() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = LogFileViewerController(scope, notesRepository = MemoryNotesRepository())
            controller.openFile("test.log")
            controller.toggleNotesExpansion()
            val row = LogFileRow(5, "source")
            controller.editNote(row)
            assertFalse(controller.notesExpanded)
            assertEquals(row, controller.notes.editorRow)
            controller.notes.updateText("analysis")
            controller.notes.saveEditor()
            assertFalse(controller.notesExpanded)
            assertEquals("analysis", controller.notes.notes.single().text)
            controller.editNote(row)
            assertFalse(controller.notesExpanded)
            controller.notes.dismissEditor()
            assertFalse(controller.notesExpanded)
            controller.toggleNotesExpansion()
            controller.editNote(row)
            assertTrue(controller.notesExpanded)
            controller.notes.dismissEditor()
            controller.closeWindow()
        } finally { scope.cancel() }
    }

    @Test
    fun sidecarRoundTripsSnapshotsAndNotesInSourceOrderWithoutChangingTheLog() = runBlocking {
        withLog { file ->
            val bytes = file.readBytes()
            val repository = JvmLogFileNotesRepository()
            assertEquals(LoadedLogFileNotes(emptyList(), null), repository.load(file.path))
            val first = LogFileNote(1, "2026-09-21 10:00:01.123 123 456 E Tag: 中文 😀", "原因一\n  保留缩进与 \"引号\"")
            val second = LogFileNote(3, "  at Example.run(Test.kt:7)", "原因二")
            val saved = repository.save(file.path, listOf(second, first), null)
            assertEquals(listOf(first, second), saved.notes)
            assertEquals(saved, repository.load(file.path))
            assertTrue(File(file.path + ".notes.json").isFile)
            val edited = first.copy(text = "修订后的判断")
            val update = repository.save(file.path, listOf(edited, second), saved.revision)
            val deleted = repository.save(file.path, listOf(edited), update.revision)
            assertEquals(listOf(edited), repository.load(file.path).notes)
            repository.save(file.path, emptyList(), deleted.revision)
            assertTrue(repository.load(file.path).notes.isEmpty())
            assertContentEquals(bytes, file.readBytes())
        }
    }

    @Test
    fun unreadableInvalidAndExternallyModifiedSidecarsAreNotOverwritten() = runBlocking {
        withLog { file ->
            val repository = JvmLogFileNotesRepository()
            val sidecar = File(logFileNotesPath(file.path))
            val note = LogFileNote(1, "line 1", "note")
            sidecar.writeText("broken JSON")
            assertFailsWith<Exception> { repository.load(file.path) }
            assertFailsWith<IllegalStateException> { repository.save(file.path, listOf(note), null) }
            assertEquals("broken JSON", sidecar.readText())
            sidecar.writeText("{}")
            assertFailsWith<Exception> { repository.load(file.path) }
            sidecar.writeText("""{"version":2,"notes":[]}""")
            assertFailsWith<IllegalStateException> { repository.load(file.path) }
            sidecar.writeText("""{"version":1,"notes":[{"lineNumber":0,"raw":"x","text":"note"}]}""")
            assertFailsWith<IllegalStateException> { repository.load(file.path) }
            sidecar.delete()
            val loaded = repository.save(file.path, listOf(note), null)
            val external = repository.save(file.path, listOf(note.copy(text = "external")), loaded.revision)
            assertFailsWith<IllegalStateException> { repository.save(file.path, emptyList(), loaded.revision) }
            assertEquals(external, repository.load(file.path))
            assertFailsWith<IllegalStateException> { repository.save(file.path, listOf(note, note), external.revision) }
            assertEquals(external, repository.load(file.path))
        }
    }

    @Test
    fun jumpUsesOriginalLineNumbersAcrossSearchAndRejectsChangedSource() = runBlocking {
        withLog { file ->
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            try {
                val controller = LogFileViewerController(scope, notesRepository = MemoryNotesRepository())
                controller.openFile(file.path)
                await { !controller.isLoading }
                val target = controller.content!!.readRows(999, 1).single()
                val note = LogFileNote(target.lineNumber, target.raw, "potential cause")
                controller.updateQuery("line 1")
                await { !controller.isLoading }
                val filteredIndex = controller.content!!.findRowIndex(1000)
                assertTrue(filteredIndex >= 0 && filteredIndex < 999)
                controller.jumpToNote(note)
                await { controller.jumpRequest != null }
                assertEquals("line 1", controller.query)
                assertEquals(filteredIndex, controller.jumpRequest!!.rowIndex)
                controller.updateQuery("line 2")
                await { !controller.isLoading }
                controller.toggleNotesExpansion()
                assertFalse(controller.notesExpanded)
                LogFileColumn.entries.forEach(controller::toggleColumn)
                controller.jumpToNote(note)
                await { controller.jumpRequest != null }
                assertEquals("", controller.query)
                assertEquals(999, controller.jumpRequest!!.rowIndex)
                assertEquals(1000L, controller.selectedLine)
                assertFalse(controller.notesExpanded)
                assertTrue(controller.hiddenColumns.isEmpty())
                val requestId = controller.jumpRequest!!.requestId
                controller.jumpToNote(note)
                await { controller.jumpRequest!!.requestId > requestId }
                // Same-size replacement must not silently associate a note with another log.
                file.writeText(file.readText().replace("line 1000", "LINE 1000"))
                controller.jumpToNote(note)
                await { controller.selectedLine == null }
                assertNotNull(controller.navigationMessage)
                assertNull(controller.jumpRequest)
                controller.closeWindow()
            } finally { scope.cancel() }
        }
    }

    @Test
    fun savingFailureRetainsDraftAndPreviousNotesAndSuccessfulRetryPersists() = runBlocking {
        val repository = MemoryNotesRepository()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = LogFileViewerController(scope, notesRepository = repository)
            controller.openFile("test.log")
            val state = controller.notes
            val row = LogFileRow(5, "saved source")
            controller.editNote(row)
            state.updateText("first")
            state.saveEditor()
            assertEquals("first", state.notes.single().text)
            controller.editNote(row)
            state.updateText("second")
            repository.failSave = true
            state.saveEditor()
            assertEquals("second", state.editorText)
            assertEquals(row, state.editorRow)
            assertEquals("first", state.notes.single().text)
            assertNotNull(state.error)
            repository.failSave = false
            state.saveEditor()
            assertNull(state.editorRow)
            assertEquals("second", state.notes.single().text)
            state.delete(state.notes.single())
            assertTrue(state.notes.isEmpty())
            controller.closeWindow()
        } finally { scope.cancel() }
    }

    @Test
    fun fileSwitchAndCloseProtectDirtyDraftsAndInFlightSaves() = runBlocking {
        val repository = MemoryNotesRepository()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = LogFileViewerController(scope, notesRepository = repository)
            controller.openFile("old.log")
            controller.editNote(LogFileRow(1, "original"))
            controller.notes.updateText("analysis")
            controller.openFile("new.log")
            assertEquals("old.log", controller.filePath)
            assertTrue(controller.notes.showDiscardConfirmation)
            controller.notes.keepEditing()
            controller.closeWindow()
            assertTrue(controller.isWindowOpen)
            controller.notes.keepEditing()
            val pending = CompletableDeferred<Unit>()
            repository.pendingSave = pending
            controller.notes.saveEditor()
            assertTrue(controller.notes.isSaving)
            controller.closeWindow()
            controller.openFile("new.log")
            assertEquals("old.log", controller.filePath)
            assertTrue(controller.isWindowOpen)
            pending.complete(Unit)
            assertFalse(controller.notes.isSaving)
            controller.editNote(LogFileRow(1, "different source"))
            assertEquals("original", controller.notes.editorRow!!.raw)
            controller.notes.updateText("unsaved revision")
            controller.openFile("new.log")
            controller.notes.discard()
            assertEquals("new.log", controller.filePath)
            assertTrue(controller.notes.notes.isEmpty())
            controller.openFile("old.log")
            assertEquals("analysis", controller.notes.notes.single().text)
            controller.closeWindow()
            assertTrue(controller.notes.notes.isEmpty())
        } finally { scope.cancel() }
    }

    @Test
    fun staleLoadCannotLeakNotesIntoANewerFileAndLoadFailureDisablesSaving() = runBlocking {
        val pending = CompletableDeferred<Unit>()
        val repository = object : MemoryNotesRepository() {
            override suspend fun load(logPath: String): LoadedLogFileNotes {
                if (logPath == "old") {
                    withContext(NonCancellable) { pending.await() }
                    return LoadedLogFileNotes(listOf(LogFileNote(1, "old", "old note")), "old")
                }
                if (logPath == "broken") error("Invalid JSON")
                return super.load(logPath)
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val state = LogFileNotesController(scope, repository)
            state.open("old")
            state.open("new")
            pending.complete(Unit)
            assertTrue(state.notes.isEmpty())
            assertTrue(state.canEdit)
            state.open("broken")
            assertFalse(state.canEdit)
            assertNotNull(state.error)
            state.edit(LogFileRow(1, "test"))
            assertNull(state.editorRow)
            state.close()
        } finally { scope.cancel() }
    }

    @Test
    fun staleJumpCannotMoveTheNextFile() = runBlocking {
        val pending = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val reader = object : LogFileReader {
            override suspend fun read(path: String, filter: LogKeywordFilter) = object : LogFileContent {
                override val filePath = path
                override val rowCount = 1
                override val totalLines = 1
                override suspend fun findRowIndex(lineNumber: Long): Int {
                    withContext(NonCancellable) { pending.await() }
                    return 0
                }
                override suspend fun readRows(start: Int, count: Int) = listOf(LogFileRow(1, path))
            }
        }
        try {
            val controller = LogFileViewerController(scope, reader, notesRepository = MemoryNotesRepository())
            controller.openFile("old")
            controller.jumpToNote(LogFileNote(1, "old", "note"))
            controller.openFile("new")
            pending.complete(Unit)
            assertEquals("new", controller.content!!.filePath)
            assertNull(controller.jumpRequest)
            assertNull(controller.selectedLine)
            controller.closeWindow()
        } finally { scope.cancel() }
    }

    private suspend fun await(condition: () -> Boolean) = withTimeout(5_000) { while (!condition()) delay(1) }

    private suspend fun withLog(block: suspend (File) -> Unit) {
        val directory = Files.createTempDirectory("ath-log-notes-test").toFile()
        try {
            val file = File(directory, "中文 日志.log")
            file.writeText((1..1200).joinToString("\r\n") { "line $it" })
            block(file)
        } finally { directory.deleteRecursively() }
    }

    private open class MemoryNotesRepository : LogFileNotesRepository {
        val files = mutableMapOf<String, LoadedLogFileNotes>()
        var failSave = false
        var pendingSave: CompletableDeferred<Unit>? = null
        override suspend fun load(logPath: String) = files[logPath] ?: LoadedLogFileNotes(emptyList(), null)
        override suspend fun save(logPath: String, notes: List<LogFileNote>, expectedRevision: String?): LoadedLogFileNotes {
            pendingSave?.await()
            if (failSave) error("Permission denied")
            return LoadedLogFileNotes(notes.sortedBy { it.lineNumber }, "saved").also { files[logPath] = it }
        }
    }
}
