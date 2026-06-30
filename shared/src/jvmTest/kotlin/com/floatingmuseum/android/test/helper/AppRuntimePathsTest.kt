package com.floatingmuseum.android.test.helper

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class AppRuntimePathsTest {
    @Test
    fun resolvesPackagedWindowsLauncherDirectoryBeforeWorkingDirectory() {
        val tempRoot = Files.createTempDirectory("ath_paths_").toFile()
        try {
            val installDir = tempRoot.resolve("AndroidTestHelper").apply { mkdirs() }
            val launcher = installDir.resolve("AndroidTestHelper.exe").apply { writeText("") }
            val unrelatedWorkingDir = tempRoot.resolve("work").apply { mkdirs() }

            val resolved = resolveApplicationInstallDirectory(
                userDir = unrelatedWorkingDir,
                commandPath = launcher.absolutePath,
                codeSourcePath = null,
            )

            assertEquals(installDir.absoluteFile, resolved)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun resolvesJpackageAppJarToInstallDirectory() {
        val tempRoot = Files.createTempDirectory("ath_paths_").toFile()
        try {
            val installDir = tempRoot.resolve("AndroidTestHelper").apply { mkdirs() }
            val appDir = installDir.resolve("app").apply { mkdirs() }
            val appJar = appDir.resolve("desktopApp.jar").apply { writeText("") }

            val resolved = resolveApplicationInstallDirectory(
                userDir = tempRoot,
                commandPath = null,
                codeSourcePath = appJar,
            )

            assertEquals(installDir.absoluteFile, resolved)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun resolvesDevelopmentWorkingDirectoryToProjectRoot() {
        val tempRoot = Files.createTempDirectory("ath_paths_").toFile()
        try {
            val projectRoot = tempRoot.resolve("AndroidTestHelper").apply { mkdirs() }
            projectRoot.resolve("settings.gradle.kts").writeText("rootProject.name = \"AndroidTestHelper\"")
            val nestedDir = projectRoot.resolve("shared/src").apply { mkdirs() }

            val resolved = resolveApplicationInstallDirectory(
                userDir = nestedDir,
                commandPath = null,
                codeSourcePath = null,
            )

            assertEquals(projectRoot.absoluteFile, resolved)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun resolvesDevelopmentWorkingDirectoryToProjectRootWithPluginsAndroidPlatformTools() {
        val tempRoot = Files.createTempDirectory("ath_paths_").toFile()
        try {
            val projectRoot = tempRoot.resolve("AndroidTestHelper").apply { mkdirs() }
            projectRoot.resolve("plugins/android/platform-tools").mkdirs()
            val nestedDir = projectRoot.resolve("shared/src").apply { mkdirs() }

            val resolved = resolveApplicationInstallDirectory(
                userDir = nestedDir,
                commandPath = null,
                codeSourcePath = null,
            )

            assertEquals(projectRoot.absoluteFile, resolved)
        } finally {
            tempRoot.deleteRecursively()
        }
    }
}
