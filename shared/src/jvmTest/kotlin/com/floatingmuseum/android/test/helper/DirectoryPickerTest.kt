package com.floatingmuseum.android.test.helper

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class DirectoryPickerTest {

    @Test
    fun testBuildRevealFileInDirectoryCommandSelectsFileOnWindows() {
        val file = File("recordings/demo file.mkv").absoluteFile

        val command = buildRevealFileInDirectoryCommand(file.path, "Windows 11")

        assertEquals(listOf("explorer.exe", "/select,${file.absolutePath}"), command?.args)
        assertEquals(file.parentFile, command?.fallbackDirectory)
    }

    @Test
    fun testBuildRevealFileInDirectoryCommandSelectsFileOnMac() {
        val file = File("recordings/demo file.mkv").absoluteFile

        val command = buildRevealFileInDirectoryCommand(file.path, "Mac OS X")

        assertEquals(listOf("open", "-R", file.absolutePath), command?.args)
        assertEquals(file.parentFile, command?.fallbackDirectory)
    }

    @Test
    fun testBuildRevealFileInDirectoryCommandOpensParentDirectoryOnLinux() {
        val file = File("recordings/demo file.mkv").absoluteFile

        val command = buildRevealFileInDirectoryCommand(file.path, "Linux")

        assertEquals(listOf("xdg-open", file.parentFile.absolutePath), command?.args)
        assertEquals(file.parentFile, command?.fallbackDirectory)
    }
}
