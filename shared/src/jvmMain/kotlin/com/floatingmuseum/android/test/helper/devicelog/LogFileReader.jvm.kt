package com.floatingmuseum.android.test.helper.devicelog

import com.floatingmuseum.android.test.helper.AppRuntimePaths
import com.floatingmuseum.android.test.helper.localization.localized
import java.awt.FileDialog
import java.awt.Frame
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.io.FileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal actual fun createLogFileReader(): LogFileReader = JvmLogFileReader()

internal actual suspend fun selectLogFile(): String? = withContext(Dispatchers.Main) {
    val dialog = FileDialog(null as Frame?, localized("log.viewer.open"), FileDialog.LOAD).apply {
        isMultipleMode = false
        directory = AppRuntimePaths.logsDirectory().absolutePath
    }
    try {
        dialog.isVisible = true
        dialog.files.firstOrNull()?.absolutePath
    } finally {
        dialog.dispose()
    }
}

/** Stores only offsets, lengths and original line numbers, never the full file text. */
internal class JvmLogFileReader : LogFileReader {
    override suspend fun read(path: String, filter: LogKeywordFilter): LogFileContent = withContext(Dispatchers.IO) {
        val file = File(path).absoluteFile
        val matcher = LogKeywordMatcher(filter)
        val index = LogLineIndex()
        var lineNumber = 0
        val snapshotSize: Long
        FileInputStream(file).use { stream ->
            // A growing capture is viewed as a snapshot; Reload includes newly appended lines.
            snapshotSize = stream.channel.size()
            stream.buffered().use { input ->
                var offset = 0L
                var lineStart = 0L
                val buffer = ByteArray(64 * 1024)
                val line = if (matcher.isEmpty) null else ByteArrayOutputStream()
                fun finishLine(end: Long) {
                    check(lineNumber < Int.MAX_VALUE) { localized("log.viewer.too_many_lines") }
                    lineNumber++
                    val length = end - lineStart
                    check(length <= Int.MAX_VALUE) { localized("log.viewer.line_too_long") }
                    if (line == null || matcher.matches(line.toString(Charsets.UTF_8.name()).removeSuffix("\r"))) {
                        index.add(lineStart, length.toInt(), lineNumber)
                    }
                    line?.reset()
                    lineStart = end
                }
                while (offset < snapshotSize) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), snapshotSize - offset).toInt())
                    check(read > 0) { localized("log.viewer.file_changed") }
                    var segmentStart = 0
                    for (i in 0 until read) {
                        if (buffer[i] == 10.toByte()) {
                            line?.write(buffer, segmentStart, i - segmentStart)
                            finishLine(offset + i + 1)
                            segmentStart = i + 1
                        }
                    }
                    line?.write(buffer, segmentStart, read - segmentStart)
                    offset += read
                }
                if (lineStart < offset) finishLine(offset)
            }
        }
        IndexedLogFileContent(file, snapshotSize, index, lineNumber)
    }
}

private class LogLineIndex {
    var size = 0
        private set
    private var offsets = LongArray(4096)
    private var lengths = IntArray(4096)
    private var numbers = IntArray(4096)

    fun add(offset: Long, length: Int, number: Int) {
        if (size == offsets.size) {
            val capacity = (size.toLong() * 2).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            offsets = offsets.copyOf(capacity)
            lengths = lengths.copyOf(capacity)
            numbers = numbers.copyOf(capacity)
        }
        offsets[size] = offset
        lengths[size] = length
        numbers[size] = number
        size++
    }

    fun offset(index: Int): Long = offsets[index]
    fun length(index: Int): Int = lengths[index]
    fun number(index: Int): Int = numbers[index]
}

private class IndexedLogFileContent(
    private val file: File,
    private val snapshotSize: Long,
    private val index: LogLineIndex,
    override val totalLines: Int,
) : LogFileContent {
    override val filePath: String = file.path
    override val rowCount: Int get() = index.size

    override suspend fun readRows(start: Int, count: Int): List<LogFileRow> = withContext(Dispatchers.IO) {
        require(start in 0..rowCount && count >= 0)
        val end = minOf(start.toLong() + count, rowCount.toLong()).toInt()
        val rows = ArrayList<LogFileRow>(end - start)
        RandomAccessFile(file, "r").use { input ->
            check(input.length() >= snapshotSize) { localized("log.viewer.file_changed") }
            for (i in start until end) {
                currentCoroutineContext().ensureActive()
                input.seek(index.offset(i))
                val bytes = ByteArray(index.length(i))
                input.readFully(bytes)
                val line = bytes.toString(Charsets.UTF_8).removeSuffix("\n").removeSuffix("\r")
                rows.add(parseLogFileRow(line, index.number(i).toLong()))
            }
        }
        rows
    }
}
