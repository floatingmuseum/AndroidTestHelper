package com.floatingmuseum.android.test.helper.devicelog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.floatingmuseum.android.test.helper.localization.localized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class LogFileNotesController(
    private val scope: CoroutineScope,
    private val repository: LogFileNotesRepository,
) {
    var notes by mutableStateOf(emptyList<LogFileNote>())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var canEdit by mutableStateOf(false)
        private set
    var editorRow by mutableStateOf<LogFileRow?>(null)
        private set
    var editorText by mutableStateOf("")
        private set
    var showDiscardConfirmation by mutableStateOf(false)
        private set
    private var originalText = ""
    private var discardAction: (() -> Unit)? = null
    private var filePath: String? = null
    private var fileRevision: String? = null
    private var generation = 0
    private var loadJob: Job? = null

    fun open(path: String) {
        close()
        filePath = path
        isLoading = true
        val current = generation
        loadJob = scope.launch {
            try {
                val loaded = repository.load(path)
                if (current == generation) {
                    notes = loaded.notes
                    fileRevision = loaded.revision
                    canEdit = true
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (current == generation) error = localized("log.notes.load_failed", failure.message ?: localized("common.error.unknown"))
            } finally {
                if (current == generation) isLoading = false
            }
        }
    }

    fun close() {
        check(!isSaving)
        generation++
        loadJob?.cancel()
        filePath = null
        fileRevision = null
        notes = emptyList()
        canEdit = false
        isLoading = false
        error = null
        editorRow = null
        editorText = ""
        discardAction = null
        showDiscardConfirmation = false
    }

    fun edit(row: LogFileRow) {
        if (!canEdit || isSaving) return
        val existing = notes.firstOrNull { it.lineNumber == row.lineNumber }
        // An existing note keeps its saved source snapshot even if the log changed.
        editorRow = existing?.row ?: row
        originalText = existing?.text.orEmpty()
        editorText = originalText
        error = null
    }

    fun updateText(value: String) { if (!isSaving) editorText = value }

    fun dismissEditor() = afterDiscard { editorRow = null; editorText = "" }

    fun afterDiscard(action: () -> Unit) {
        if (isSaving) return
        if (editorRow != null && editorText != originalText) {
            discardAction = action
            showDiscardConfirmation = true
        } else action()
    }

    fun keepEditing() { discardAction = null; showDiscardConfirmation = false }

    fun discard() {
        val action = discardAction
        keepEditing()
        editorRow = null
        editorText = ""
        action?.invoke()
    }

    fun saveEditor() {
        val row = editorRow ?: return
        if (editorText.isBlank()) return
        persist(notes.filterNot { it.lineNumber == row.lineNumber } + LogFileNote(row.lineNumber, row.raw, editorText)) {
            editorRow = null
            editorText = ""
        }
    }

    fun delete(note: LogFileNote) = persist(notes.filterNot { it.lineNumber == note.lineNumber }) {}

    private fun persist(updated: List<LogFileNote>, onSuccess: () -> Unit) {
        val path = filePath ?: return
        if (!canEdit || isSaving) return
        isSaving = true
        error = null
        scope.launch {
            try {
                val saved = repository.save(path, updated, fileRevision)
                notes = saved.notes
                fileRevision = saved.revision
                onSuccess()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = localized("log.notes.save_failed", failure.message ?: localized("common.error.unknown"))
            } finally {
                isSaving = false
            }
        }
    }
}
