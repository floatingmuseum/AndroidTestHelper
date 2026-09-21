package com.floatingmuseum.android.test.helper.devicelog

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogFilterHistoryRepositoryTest {
    @Test
    fun historySurvivesReloadAndDeletionWithCaseOption() {
        val directory = Files.createTempDirectory("ath_log_history_").toFile()
        try {
            val file = directory.resolve("cache/history.json")
            val repository = JvmLogFilterHistoryRepository { file }
            assertTrue(repository.loadHistory().isEmpty())
            repository.saveHistory(listOf(LogKeywordFilter(" CSDK | Usb "), LogKeywordFilter("Exception", true)))
            val reloaded = JvmLogFilterHistoryRepository { file }.loadHistory()
            assertEquals(listOf(LogKeywordFilter("CSDK | Usb"), LogKeywordFilter("Exception", true)), reloaded)
            repository.saveHistory(reloaded.drop(1))
            assertEquals(listOf(LogKeywordFilter("Exception", true)), repository.loadHistory())
            file.writeText("{ corrupt")
            assertTrue(repository.loadHistory().isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }
}
