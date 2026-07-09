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
enum class ScreenRecordFormat {
    Mkv,
    Mp4,
}

val SCREEN_RECORD_FORMAT_OPTIONS = listOf(
    ScreenRecordFormat.Mkv,
    ScreenRecordFormat.Mp4,
)

@Serializable
enum class ScreenRecordMaxSize {
    Original,
    Size1080,
    Size720,
    Size480,
}

val SCREEN_RECORD_MAX_SIZE_OPTIONS = listOf(
    ScreenRecordMaxSize.Original,
    ScreenRecordMaxSize.Size1080,
    ScreenRecordMaxSize.Size720,
    ScreenRecordMaxSize.Size480,
)

@Serializable
enum class ScreenRecordBitRate {
    Default,
    Mbps4,
    Mbps8,
    Mbps12,
    Mbps20,
}

val SCREEN_RECORD_BIT_RATE_OPTIONS = listOf(
    ScreenRecordBitRate.Default,
    ScreenRecordBitRate.Mbps4,
    ScreenRecordBitRate.Mbps8,
    ScreenRecordBitRate.Mbps12,
    ScreenRecordBitRate.Mbps20,
)

@Serializable
enum class ScreenRecordMaxFps {
    Default,
    Fps15,
    Fps30,
    Fps60,
}

val SCREEN_RECORD_MAX_FPS_OPTIONS = listOf(
    ScreenRecordMaxFps.Default,
    ScreenRecordMaxFps.Fps15,
    ScreenRecordMaxFps.Fps30,
    ScreenRecordMaxFps.Fps60,
)

@Serializable
enum class ScreenRecordAudioMode {
    Disabled,
    DeviceOutput,
    Microphone,
}

val SCREEN_RECORD_AUDIO_MODE_OPTIONS = listOf(
    ScreenRecordAudioMode.Disabled,
    ScreenRecordAudioMode.DeviceOutput,
    ScreenRecordAudioMode.Microphone,
)

@Serializable
data class AppSettings(
    val language: AppLanguage? = null,
    val showCommandTime: Boolean = false,
    val showCommandDuration: Boolean = false,
    val fileManagerDefaultRootPath: String = DEFAULT_FILE_MANAGER_ROOT_PATH,
    val customAdbPath: String? = null,
    val customScrcpyPath: String? = null,
    val screenRecordFormat: ScreenRecordFormat = ScreenRecordFormat.Mp4,
    val screenRecordFormatUserSelected: Boolean = false,
    val screenRecordMaxSize: ScreenRecordMaxSize = ScreenRecordMaxSize.Original,
    val screenRecordBitRate: ScreenRecordBitRate = ScreenRecordBitRate.Default,
    val screenRecordMaxFps: ScreenRecordMaxFps = ScreenRecordMaxFps.Default,
    val screenRecordAudioMode: ScreenRecordAudioMode = ScreenRecordAudioMode.Disabled,
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
