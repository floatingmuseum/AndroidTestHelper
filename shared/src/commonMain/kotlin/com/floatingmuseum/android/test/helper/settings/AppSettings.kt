package com.floatingmuseum.android.test.helper.settings

import kotlinx.serialization.Serializable

const val DEFAULT_FILE_MANAGER_ROOT_PATH = "/sdcard"

val FILE_MANAGER_ROOT_PATH_OPTIONS = listOf("/", "/sdcard")

@Serializable
enum class AppLanguage {
    SimplifiedChinese,
    English,
}

val APP_LANGUAGE_OPTIONS = listOf(
    AppLanguage.SimplifiedChinese,
    AppLanguage.English,
)

@Serializable
data class AppSettings(
    val language: AppLanguage? = null,
    val showCommandTime: Boolean = false,
    val showCommandDuration: Boolean = false,
    val fileManagerDefaultRootPath: String = DEFAULT_FILE_MANAGER_ROOT_PATH,
    val customAdbPath: String? = null,
    val customScrcpyPath: String? = null,
)

fun AppSettings.normalized(): AppSettings {
    val normalizedRootPath = fileManagerDefaultRootPath
        .takeIf { it in FILE_MANAGER_ROOT_PATH_OPTIONS }
        ?: DEFAULT_FILE_MANAGER_ROOT_PATH
    val normalizedCustomAdbPath = customAdbPath
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    val normalizedCustomScrcpyPath = customScrcpyPath
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    return copy(
        fileManagerDefaultRootPath = normalizedRootPath,
        customAdbPath = normalizedCustomAdbPath,
        customScrcpyPath = normalizedCustomScrcpyPath,
    )
}

fun AppSettings.effectiveLanguage(systemLanguage: AppLanguage): AppLanguage {
    return language ?: systemLanguage
}
