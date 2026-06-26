package com.floatingmuseum.android.test.helper.filemanager

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileManagerAdbTest {
    @Test
    fun parseRemoteDirectoryListingKeepsNamesWithSpacesAndSortsDirectoriesFirst() {
        val output = """
            total 12
            drwxrwx--x 2 root sdcard_rw 4096 2026-06-24 09:30 Pictures
            -rw-rw---- 1 u0_a123 media_rw 12345 2026-06-24 09:31 report file.txt
            lrwxrwxrwx 1 root root 11 2026-06-24 09:32 link-name -> /sdcard/Target
            drwxrwx--x 2 root sdcard_rw 4096 2026-06-24 09:33 Download
        """.trimIndent()

        val entries = parseRemoteDirectoryListing("/sdcard", output)

        assertEquals(listOf("Download", "Pictures", "link-name", "report file.txt"), entries.map { it.name })
        assertEquals("/sdcard/Download", entries[0].path)
        assertEquals(RemoteFileType.Directory, entries[0].type)
        assertEquals(RemoteFileType.File, entries[3].type)
        assertEquals(12345L, entries[3].sizeBytes)
    }

    @Test
    fun remotePathHelpersNormalizeParentAndChildPaths() {
        assertEquals("/", normalizeRemotePath(""))
        assertEquals("/sdcard/Download", normalizeRemotePath("sdcard//Download/"))
        assertEquals("/sdcard", parentRemotePath("/sdcard/Download"))
        assertEquals("/", parentRemotePath("/sdcard"))
        assertEquals("/sdcard/file.txt", childRemotePath("/sdcard/", "file.txt"))
        assertEquals("file.txt", remoteFileName("/sdcard/file.txt"))
    }

    @Test
    fun validateRemoteChildNameRejectsBlankParentAndNestedPaths() {
        assertEquals("NewFile.txt", validateRemoteChildName(" NewFile.txt "))
        assertFailsWith<IllegalArgumentException> { validateRemoteChildName("") }
        assertFailsWith<IllegalArgumentException> { validateRemoteChildName(".") }
        assertFailsWith<IllegalArgumentException> { validateRemoteChildName("..") }
        assertFailsWith<IllegalArgumentException> { validateRemoteChildName("nested/file.txt") }
        assertFailsWith<IllegalArgumentException> { validateRemoteChildName("nested\\file.txt") }
    }

    @Test
    fun remoteDirectoryListArgumentDereferencesDirectorySymlinks() {
        assertEquals("/", remoteDirectoryListArgument("/"))
        assertEquals("/sdcard/", remoteDirectoryListArgument("/sdcard"))
        assertEquals("/storage/self/primary/", remoteDirectoryListArgument("/storage/self/primary/"))
    }

    @Test
    fun remoteUploadTargetPathUsesFileNameForFiles() {
        val localFile = File("C:/Users/floatingmuseum/Downloads/组 1.png")

        assertEquals(
            "/sdcard/Download/组 1.png",
            remoteUploadTargetPath("/sdcard/Download", localFile),
        )
        assertEquals(
            "/组 1.png",
            remoteUploadTargetPath("/", localFile),
        )
    }

    @Test
    fun remoteUploadTargetPathUsesDirectoryNameForDirectories() {
        val localDirectory = kotlin.io.path.createTempDirectory("Movie Folder").toFile()
        try {
            assertEquals(
                "/sdcard/Movies/${localDirectory.name}",
                remoteUploadTargetPath("/sdcard/Movies", localDirectory),
            )
            assertEquals(
                "/${localDirectory.name}",
                remoteUploadTargetPath("/", localDirectory),
            )
        } finally {
            localDirectory.deleteRecursively()
        }
    }

    @Test
    fun parseAdbPushProgressPercentReadsLatestPercent() {
        assertEquals(42, parseAdbPushProgressPercent("[ 42%] /sdcard/big.zip"))
        assertEquals(100, parseAdbPushProgressPercent("[ 7%] /sdcard/big.zip\r[100%] /sdcard/big.zip"))
        assertEquals(null, parseAdbPushProgressPercent("1 file pushed, 0 skipped."))
        assertEquals(null, parseAdbPushProgressPercent("[101%] invalid"))
    }

    @Test
    fun remoteFileEntryMarksOnlyDirectoriesAsDirectories() {
        assertTrue(RemoteFileEntry("dir", "/dir", RemoteFileType.Directory, 0, "drwx", "now").isDirectory)
        assertFalse(RemoteFileEntry("link", "/link", RemoteFileType.Link, 0, "lrwx", "now").isDirectory)
    }

    @Test
    fun remoteDropTargetDirectoryPathUsesParentDirectoryForFiles() {
        val directory = RemoteFileEntry("Download", "/sdcard/Download", RemoteFileType.Directory, 0, "drwx", "now")
        val file = RemoteFileEntry("report.txt", "/sdcard/Download/report.txt", RemoteFileType.File, 8, "-rw-", "now")
        val rootFile = RemoteFileEntry("init.rc", "/init.rc", RemoteFileType.File, 8, "-rw-", "now")
        val link = RemoteFileEntry("linked-dir", "/sdcard/linked-dir", RemoteFileType.Link, 0, "lrwx", "now")

        assertEquals("/sdcard/Download", remoteDropTargetDirectoryPath(directory))
        assertEquals("/sdcard/Download", remoteDropTargetDirectoryPath(file))
        assertEquals("/", remoteDropTargetDirectoryPath(rootFile))
        assertEquals("/sdcard/linked-dir", remoteDropTargetDirectoryPath(link))
    }

    @Test
    fun isRemotePathInDirectoryTreeMatchesVisibleDropSubtree() {
        assertTrue(isRemotePathInDirectoryTree("/sdcard/Download", "/sdcard/Download"))
        assertTrue(isRemotePathInDirectoryTree("/sdcard/Download/report.txt", "/sdcard/Download"))
        assertTrue(isRemotePathInDirectoryTree("/sdcard/Download/ccs/file.txt", "/sdcard/Download"))
        assertTrue(isRemotePathInDirectoryTree("/sdcard/Download", "/"))
        assertFalse(isRemotePathInDirectoryTree("/sdcard/Downloads", "/sdcard/Download"))
        assertFalse(isRemotePathInDirectoryTree("/sdcard/Pictures/report.txt", "/sdcard/Download"))
    }

    @Test
    fun parseLocalFileReferencesReadsFileUrisAndPlainPaths() {
        val localFile = File("build.gradle.kts").absoluteFile

        assertEquals(
            listOf(localFile.absolutePath),
            parseLocalFileReferences(
                listOf(
                    "# comment",
                    localFile.toURI().toString(),
                    "\"${localFile.absolutePath}\"",
                ),
            ),
        )
    }
}
