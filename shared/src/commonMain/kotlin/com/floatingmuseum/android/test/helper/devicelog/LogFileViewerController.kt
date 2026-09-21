package com.floatingmuseum.android.test.helper.devicelog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.floatingmuseum.android.test.helper.localization.localized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal const val LOG_VIEWER_BLOCK_ROWS = 128
internal const val LOG_VIEWER_CACHED_BLOCKS = 8

internal class LogFileViewerController(
    private val scope: CoroutineScope,
    private val reader: LogFileReader = createLogFileReader(),
    private val picker: suspend () -> String? = ::selectLogFile,
) {
    var isWindowOpen by mutableStateOf(false)
        private set
    var windowRequestId by mutableStateOf(0)
        private set
    var content by mutableStateOf<LogFileContent?>(null)
        private set
    var filePath by mutableStateOf<String?>(null)
        private set
    var query by mutableStateOf("")
        private set
    var matchCase by mutableStateOf(false)
        private set
    var hiddenColumns by mutableStateOf(emptySet<LogFileColumn>())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isPicking by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var cachedBlocks by mutableStateOf(emptyMap<Int, List<LogFileRow>>())
    private val blockJobs = mutableMapOf<Int, Job>()
    private val reads = Semaphore(2)
    private var loadJob: Job? = null
    private var revision = 0

    fun openWindow() {
        isWindowOpen = true
        windowRequestId++
    }

    fun closeWindow() {
        isWindowOpen = false
        closeFile()
    }

    fun selectFile() {
        if (isPicking) return
        isPicking = true
        scope.launch {
            try {
                picker()?.let(::openFile)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: localized("common.error.unknown")
            } finally {
                isPicking = false
            }
        }
    }

    fun openFile(path: String) {
        openWindow()
        filePath = path
        query = ""
        matchCase = false
        hiddenColumns = emptySet()
        reload()
    }

    fun closeFile() {
        cancelReads()
        filePath = null
        content = null
        error = null
        isLoading = false
        query = ""
        matchCase = false
        hiddenColumns = emptySet()
    }

    fun toggleColumn(column: LogFileColumn) {
        hiddenColumns = if (column in hiddenColumns) hiddenColumns - column else hiddenColumns + column
    }

    fun showAllColumns() { hiddenColumns = emptySet() }

    fun updateQuery(value: String) {
        query = value
        reload(debounce = true)
    }

    fun updateMatchCase(value: Boolean) {
        matchCase = value
        reload()
    }

    fun reload(debounce: Boolean = false) {
        val path = filePath ?: return
        cancelReads()
        val currentRevision = revision
        content = null
        error = null
        isLoading = true
        val filter = LogKeywordFilter(query, matchCase)
        loadJob = scope.launch {
            try {
                if (debounce) delay(250)
                val result = reader.read(path, filter)
                if (revision == currentRevision) content = result
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (revision == currentRevision) error = failure.message ?: localized("common.error.unknown")
            } finally {
                if (revision == currentRevision) isLoading = false
            }
        }
    }

    fun rowAt(index: Int): LogFileRow? = cachedBlocks[index / LOG_VIEWER_BLOCK_ROWS]?.getOrNull(index % LOG_VIEWER_BLOCK_ROWS)

    /** Called only for composed rows. Both IO concurrency and retained text are bounded. */
    fun ensureRows(index: Int) {
        val source = content ?: return
        if (index !in 0 until source.rowCount || error != null) return
        val block = index / LOG_VIEWER_BLOCK_ROWS
        if (block in cachedBlocks || block in blockJobs) return
        val currentRevision = revision
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val rows = reads.withPermit { source.readRows(block * LOG_VIEWER_BLOCK_ROWS, LOG_VIEWER_BLOCK_ROWS) }
                if (revision == currentRevision) {
                    val updated = cachedBlocks.toMutableMap()
                    if (updated.size >= LOG_VIEWER_CACHED_BLOCKS) updated.remove(updated.keys.first())
                    updated[block] = rows
                    cachedBlocks = updated
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (revision == currentRevision) error = failure.message ?: localized("common.error.unknown")
            } finally {
                if (revision == currentRevision) blockJobs.remove(block)
            }
        }
        blockJobs[block] = job
        job.start()
    }

    private fun cancelReads() {
        revision++
        loadJob?.cancel()
        blockJobs.values.toList().forEach { it.cancel() }
        blockJobs.clear()
        cachedBlocks = emptyMap()
    }
}
