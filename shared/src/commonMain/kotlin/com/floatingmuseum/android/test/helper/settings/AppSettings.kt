package com.floatingmuseum.android.test.helper.settings

import kotlinx.serialization.Serializable

const val DEFAULT_FILE_MANAGER_ROOT_PATH = "/sdcard"

val FILE_MANAGER_ROOT_PATH_OPTIONS = listOf("/", "/sdcard")

@Serializable
data class AppSettings(
    val showCommandTime: Boolean = false,
    val showCommandDuration: Boolean = false,
    val fileManagerDefaultRootPath: String = DEFAULT_FILE_MANAGER_ROOT_PATH,
)

fun AppSettings.normalized(): AppSettings {
    val normalizedRootPath = fileManagerDefaultRootPath
        .takeIf { it in FILE_MANAGER_ROOT_PATH_OPTIONS }
        ?: DEFAULT_FILE_MANAGER_ROOT_PATH
    return copy(fileManagerDefaultRootPath = normalizedRootPath)
}
