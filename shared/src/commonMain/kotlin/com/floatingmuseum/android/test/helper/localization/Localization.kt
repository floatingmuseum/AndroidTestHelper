package com.floatingmuseum.android.test.helper.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.floatingmuseum.android.test.helper.settings.AppLanguage
import com.floatingmuseum.android.test.helper.settings.AppSettingsShared
import com.floatingmuseum.android.test.helper.settings.ScreenRecordAudioMode
import com.floatingmuseum.android.test.helper.settings.ScreenRecordBitRate
import com.floatingmuseum.android.test.helper.settings.ScreenRecordFormat
import com.floatingmuseum.android.test.helper.settings.ScreenRecordMaxFps
import com.floatingmuseum.android.test.helper.settings.ScreenRecordMaxSize
import kotlinx.serialization.json.Json

private val JsonParser = Json {
    ignoreUnknownKeys = true
}

private val loadedBundles = mutableMapOf<AppLanguage, Map<String, String>>()

internal expect fun loadLocalizationResource(languageTag: String): String?

fun localized(
    key: String,
    vararg args: Any?,
    language: AppLanguage = AppSettingsShared.currentLanguage,
): String {
    val template = languageBundle(language)[key]
        ?: languageBundle(AppLanguage.English)[key]
        ?: key
    return formatTemplate(template, args)
}

fun commandStatus(message: String): String = "${localized("command.status.prefix")} $message"

fun commandError(message: String): String = "${localized("command.error.prefix")} $message"

fun unknownError(): String = localized("common.error.unknown")

fun isErrorCommandLog(command: String): Boolean {
    return command.contains(localized("command.error.prefix", language = AppLanguage.SimplifiedChinese)) ||
        command.contains(localized("command.error.prefix", language = AppLanguage.English)) ||
        command.contains("失败") ||
        command.contains("failed", ignoreCase = true)
}

fun isStatusCommandLog(command: String): Boolean {
    return command.contains(localized("command.status.prefix", language = AppLanguage.SimplifiedChinese)) ||
        command.contains(localized("command.status.prefix", language = AppLanguage.English))
}

@Composable
internal fun rememberAppStrings(): AppStrings {
    val language = AppSettingsShared.currentLanguage
    return remember(language) { AppStrings(language) }
}

internal class AppStrings(private val language: AppLanguage) {
    fun t(key: String, vararg args: Any?): String = localized(key, *args, language = language)

    val languageSimplifiedChinese: String get() = t("settings.language.simplified_chinese")
    val languageEnglish: String get() = t("settings.language.english")

    fun languageDisplayName(option: AppLanguage): String = when (option) {
        AppLanguage.SimplifiedChinese -> languageSimplifiedChinese
        AppLanguage.English -> languageEnglish
    }

    fun screenRecordFormatLabel(option: ScreenRecordFormat): String = when (option) {
        ScreenRecordFormat.Mkv -> t("settings.screen_record.format.mkv")
        ScreenRecordFormat.Mp4 -> t("settings.screen_record.format.mp4")
    }

    fun screenRecordMaxSizeLabel(option: ScreenRecordMaxSize): String = when (option) {
        ScreenRecordMaxSize.Original -> t("settings.screen_record.max_size.original")
        ScreenRecordMaxSize.Size1080 -> t("settings.screen_record.max_size.1080")
        ScreenRecordMaxSize.Size720 -> t("settings.screen_record.max_size.720")
        ScreenRecordMaxSize.Size480 -> t("settings.screen_record.max_size.480")
    }

    fun screenRecordBitRateLabel(option: ScreenRecordBitRate): String = when (option) {
        ScreenRecordBitRate.Default -> t("settings.screen_record.bit_rate.default")
        ScreenRecordBitRate.Mbps4 -> t("settings.screen_record.bit_rate.4m")
        ScreenRecordBitRate.Mbps8 -> t("settings.screen_record.bit_rate.8m")
        ScreenRecordBitRate.Mbps12 -> t("settings.screen_record.bit_rate.12m")
        ScreenRecordBitRate.Mbps20 -> t("settings.screen_record.bit_rate.20m")
    }

    fun screenRecordMaxFpsLabel(option: ScreenRecordMaxFps): String = when (option) {
        ScreenRecordMaxFps.Default -> t("settings.screen_record.max_fps.default")
        ScreenRecordMaxFps.Fps15 -> t("settings.screen_record.max_fps.15")
        ScreenRecordMaxFps.Fps30 -> t("settings.screen_record.max_fps.30")
        ScreenRecordMaxFps.Fps60 -> t("settings.screen_record.max_fps.60")
    }

    fun screenRecordAudioModeLabel(option: ScreenRecordAudioMode): String = when (option) {
        ScreenRecordAudioMode.Disabled -> t("settings.screen_record.audio_mode.disabled")
        ScreenRecordAudioMode.DeviceOutput -> t("settings.screen_record.audio_mode.device_output")
        ScreenRecordAudioMode.Microphone -> t("settings.screen_record.audio_mode.microphone")
    }
}

private fun languageBundle(language: AppLanguage): Map<String, String> {
    return loadedBundles.getOrPut(language) {
        val resourceText = loadLocalizationResource(language.resourceTag)
        if (resourceText.isNullOrBlank()) {
            emptyMap()
        } else {
            JsonParser.decodeFromString<Map<String, String>>(resourceText)
        }
    }
}

private val AppLanguage.resourceTag: String
    get() = when (this) {
        AppLanguage.English -> "en"
        AppLanguage.SimplifiedChinese -> "zh-Hans"
    }

private fun formatTemplate(template: String, args: Array<out Any?>): String {
    if (args.isEmpty()) return template
    var result = template
    args.forEachIndexed { index, value ->
        result = result.replace("{$index}", value?.toString().orEmpty())
    }
    return result
}
