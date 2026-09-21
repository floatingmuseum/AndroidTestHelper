package com.floatingmuseum.android.test.helper.devicelog

import com.floatingmuseum.android.test.helper.localization.localized
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal actual fun createLogFileNotesRepository(): LogFileNotesRepository = JvmLogFileNotesRepository()

/** The log is read-only; notes live beside it and are replaced only after a complete write. */
internal class JvmLogFileNotesRepository : LogFileNotesRepository {
    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }

    override suspend fun load(logPath: String): LoadedLogFileNotes = withContext(Dispatchers.IO) {
        val bytes = readExisting(File(logFileNotesPath(logPath)))
        if (bytes == null) return@withContext LoadedLogFileNotes(emptyList(), null)
        val document = json.decodeFromString<LogFileNotesDocument>(bytes.toString(Charsets.UTF_8))
        check(document.version == 1) { localized("log.notes.unsupported_version") }
        validate(document.notes)
        LoadedLogFileNotes(document.notes.sortedBy { it.lineNumber }, fingerprint(bytes))
    }

    override suspend fun save(
        logPath: String,
        notes: List<LogFileNote>,
        expectedRevision: String?,
    ): LoadedLogFileNotes = withContext(Dispatchers.IO) {
        validate(notes)
        val file = File(logFileNotesPath(logPath)).absoluteFile
        check(fingerprint(readExisting(file)) == expectedRevision) { localized("log.notes.external_change") }
        val sorted = notes.sortedBy { it.lineNumber }
        val bytes = json.encodeToString(LogFileNotesDocument(version = 1, notes = sorted)).toByteArray(Charsets.UTF_8)
        val temporary = Files.createTempFile(file.parentFile.toPath(), ".ath-notes-", ".tmp")
        try {
            Files.write(temporary, bytes)
            check(fingerprint(readExisting(file)) == expectedRevision) { localized("log.notes.external_change") }
            try {
                Files.move(temporary, file.toPath(), REPLACE_EXISTING, ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, file.toPath(), REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
        LoadedLogFileNotes(sorted, fingerprint(bytes))
    }

    private fun readExisting(file: File): ByteArray? =
        if (Files.notExists(file.toPath())) null else file.readBytes()

    private fun fingerprint(bytes: ByteArray?): String? = bytes?.let {
        MessageDigest.getInstance("SHA-256").digest(it).joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun validate(notes: List<LogFileNote>) {
        check(notes.all { it.lineNumber in 1..Int.MAX_VALUE.toLong() && it.text.isNotBlank() } &&
            notes.map { it.lineNumber }.distinct().size == notes.size) { localized("log.notes.invalid_file") }
    }
}
