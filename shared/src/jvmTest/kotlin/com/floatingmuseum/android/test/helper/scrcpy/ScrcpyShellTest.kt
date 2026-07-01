package com.floatingmuseum.android.test.helper.scrcpy

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class ScrcpyShellTest {
    @Test
    fun parseScrcpyVersionUsesScrcpyLine() {
        val output = """
            scrcpy 3.2 <https://github.com/Genymobile/scrcpy>
            Dependencies (compiled / linked):
             - SDL: 2.30.0 / 2.30.0
        """.trimIndent()

        assertEquals("scrcpy 3.2 <https://github.com/Genymobile/scrcpy>", ScrcpyShell.parseScrcpyVersion(output))
    }

    @Test
    fun parseScrcpyVersionFallsBackToFirstNonBlankLine() {
        val output = """

            custom build
            more details
        """.trimIndent()

        assertEquals("custom build", ScrcpyShell.parseScrcpyVersion(output))
    }

    @Test
    fun scrcpyCandidatePathsPreferPluginsScrcpyPlatformFolder() {
        val root = File("project-root").absoluteFile
        val paths = scrcpyCandidatePaths(
            directory = root,
            platformFolder = "windows",
            binaryName = "scrcpy.exe",
        )

        assertEquals(root.resolve("plugins/scrcpy/windows/scrcpy.exe"), paths[0])
        assertEquals(root.resolve("plugins/scrcpy/scrcpy.exe"), paths[1])
        assertEquals(root.resolve("resources/plugins/scrcpy/windows/scrcpy.exe"), paths[2])
        assertEquals(root.resolve("resources/plugins/scrcpy/scrcpy.exe"), paths[3])
    }

    @Test
    fun scrcpyPlatformFoldersUseDarwinArchitectureBeforeLegacyFolder() {
        assertEquals(
            listOf("darwin-aarch64", "darwin"),
            scrcpyPlatformFolders(osName = "Mac OS X", osArch = "arm64"),
        )
        assertEquals(
            listOf("darwin-x86_64", "darwin"),
            scrcpyPlatformFolders(osName = "Darwin", osArch = "x86_64"),
        )
    }

    @Test
    fun scrcpyPlatformFoldersUseLinuxArchitectureBeforeLegacyFolder() {
        assertEquals(
            listOf("linux-x86_64", "linux"),
            scrcpyPlatformFolders(osName = "Linux", osArch = "amd64"),
        )
    }

    @Test
    fun scrcpyPlatformFoldersKeepWindowsLegacyFolder() {
        assertEquals(
            listOf("windows"),
            scrcpyPlatformFolders(osName = "Windows 11", osArch = "amd64"),
        )
        assertEquals("scrcpy.exe", scrcpyBinaryName("Windows 11"))
    }

    @Test
    fun scrcpyCandidatePathsPreferArchitectureSpecificPluginFolder() {
        val root = File("project-root").absoluteFile
        val paths = scrcpyCandidatePaths(
            directory = root,
            platformFolders = listOf("darwin-aarch64", "darwin"),
            binaryName = "scrcpy",
        )

        assertEquals(root.resolve("plugins/scrcpy/darwin-aarch64/scrcpy"), paths[0])
        assertEquals(root.resolve("plugins/scrcpy/darwin/scrcpy"), paths[1])
        assertEquals(root.resolve("plugins/scrcpy/scrcpy"), paths[2])
        assertEquals(root.resolve("resources/plugins/scrcpy/darwin-aarch64/scrcpy"), paths[3])
        assertEquals(root.resolve("resources/plugins/scrcpy/darwin/scrcpy"), paths[4])
        assertEquals(root.resolve("resources/plugins/scrcpy/scrcpy"), paths[5])
    }
}
