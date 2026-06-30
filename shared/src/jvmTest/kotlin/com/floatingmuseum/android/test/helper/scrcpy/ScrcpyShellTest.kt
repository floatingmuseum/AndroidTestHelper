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
}
