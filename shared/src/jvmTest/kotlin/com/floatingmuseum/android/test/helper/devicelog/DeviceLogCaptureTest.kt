package com.floatingmuseum.android.test.helper.devicelog

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceLogCaptureTest {
    private val logs = """
        --------- beginning of main
        2026-09-21 05:49:07.121 1105 1131 E UsbDeviceManager: csdk ready
        2026-09-21 05:49:07.122 1200 1201 D Other: unrelated payload
        2026-09-21 05:49:07.123 1200 1201 I JPush: connected
    """.trimIndent() + "\n"

    @Test
    fun interruptionKeepsFullAndFilteredFiles() = runBlocking {
        withDirectory { directory ->
            val process = FakeProcess(logs, 1)
            val adb = capture(directory, process)
            val callerThread = Thread.currentThread()
            val result = adb.captureFullLogs("serial", "tablet", LogKeywordFilter("system_server | jpush"), {
                assertEquals(callerThread, Thread.currentThread())
            }, {
                assertEquals(callerThread, Thread.currentThread())
            })
            assertEquals(DeviceLogCaptureEndState.INTERRUPTED, result.endState)
            assertEquals(3L, result.capturedLines)
            assertEquals(2L, result.matchedLines)
            val full = File(result.filePath).readText()
            val filtered = File(assertNotNull(result.filteredFilePath)).readText()
            assertTrue(full.contains("unrelated payload"))
            assertFalse(filtered.contains("unrelated payload"))
            assertTrue(filtered.contains("csdk ready"))
            assertTrue(filtered.contains("JPush"))
            assertTrue(full.contains("CAPTURE INTERRUPTED"))
            assertTrue(filtered.contains("CAPTURE INTERRUPTED"))
            assertTrue(process.destroyed)
        }
    }

    @Test
    fun emptyFilterOnlyCreatesFullFileAndUnexpectedZeroExitIsInterruption() = runBlocking {
        withDirectory { directory ->
            val result = capture(directory, FakeProcess(logs, 0))
                .captureFullLogs("serial", "tablet", LogKeywordFilter(" | "), {}, {})
            assertNull(result.filteredFilePath)
            assertEquals(1, directory.listFiles()!!.size)
            assertEquals(DeviceLogCaptureEndState.INTERRUPTED, result.endState)
        }
    }

    @Test
    fun stopWithZeroExitStillReturnsStoppedAndBothPaths() = runBlocking {
        withDirectory { directory ->
            val process = FakeProcess(logs, 0, staysAlive = true)
            val adb = capture(directory, process)
            val linesReceived = CompletableDeferred<Unit>()
            val job = async {
                adb.captureFullLogs("serial", "tablet", LogKeywordFilter("missing-keyword"), {}, {
                    if (it.capturedLines == 3L) linesReceived.complete(Unit)
                })
            }
            withTimeout(5_000) { linesReceived.await() }
            adb.stopCurrentCapture()
            val result = withTimeout(5_000) { job.await() }
            assertEquals(DeviceLogCaptureEndState.STOPPED, result.endState)
            assertEquals(0L, result.matchedLines)
            assertTrue(File(assertNotNull(result.filteredFilePath)).readText().contains("CAPTURE STOPPED"))
            assertTrue(File(result.filePath).readText().contains("unrelated payload"))
        }
    }

    @Test
    fun cancellationDestroysProcessAndClosesWrittenFiles() = runBlocking {
        withDirectory { directory ->
            val process = FakeProcess(logs, 0, staysAlive = true)
            val adb = capture(directory, process)
            val linesReceived = CompletableDeferred<Unit>()
            val job = async {
                adb.captureFullLogs("serial", "tablet", LogKeywordFilter("csdk"), {}, {
                    if (it.capturedLines == 3L) linesReceived.complete(Unit)
                })
            }
            withTimeout(5_000) { linesReceived.await() }
            withTimeout(5_000) { job.cancelAndJoin() }
            assertTrue(process.destroyed)
            directory.listFiles()!!.forEach { file ->
                assertTrue(file.readText().contains("csdk ready"))
            }
        }
    }

    private fun capture(directory: File, process: FakeProcess) = JvmDeviceLogAdb(
        logsDirectory = { directory },
        startLogcat = { process },
        readProcessNames = { _, _ -> mapOf("1105" to "system_server") },
    )

    private suspend fun withDirectory(block: suspend (File) -> Unit) {
        val directory = Files.createTempDirectory("ath_log_capture_").toFile()
        try { block(directory) } finally { directory.deleteRecursively() }
    }

    private class FakeProcess(text: String, private val code: Int, staysAlive: Boolean = false) : Process() {
        private val ended = CountDownLatch(if (staysAlive) 1 else 0)
        private val input = ByteArrayInputStream(text.toByteArray())
        var destroyed = false
            private set
        override fun getInputStream() = input
        override fun getOutputStream() = ByteArrayOutputStream()
        override fun getErrorStream() = ByteArrayInputStream(byteArrayOf())
        override fun waitFor(): Int { ended.await(); return code }
        override fun waitFor(timeout: Long, unit: TimeUnit) = ended.await(timeout, unit)
        override fun exitValue(): Int { check(ended.count == 0L); return code }
        override fun destroy() { destroyed = true; ended.countDown() }
        override fun destroyForcibly(): Process { destroy(); return this }
        override fun isAlive() = ended.count != 0L
    }
}
