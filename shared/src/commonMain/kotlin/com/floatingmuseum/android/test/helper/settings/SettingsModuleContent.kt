package com.floatingmuseum.android.test.helper.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.floatingmuseum.android.test.helper.adb.AdbRuntimeInfo
import com.floatingmuseum.android.test.helper.adb.checkAdbExecutable
import com.floatingmuseum.android.test.helper.adb.loadAdbRuntimeInfo
import com.floatingmuseum.android.test.helper.localization.rememberAppStrings
import com.floatingmuseum.android.test.helper.scrcpy.ScrcpyRuntimeInfo
import com.floatingmuseum.android.test.helper.scrcpy.checkScrcpyExecutable
import com.floatingmuseum.android.test.helper.scrcpy.loadScrcpyRuntimeInfo
import com.floatingmuseum.android.test.helper.selectFiles
import kotlinx.coroutines.launch

enum class SettingCategory {
    General,
    ScreenRecordMirror,
    FileManager,
}

@Composable
fun SettingsModuleContent(
    modifier: Modifier = Modifier,
) {
    val strings = rememberAppStrings()
    var selectedCategory by remember { mutableStateOf(SettingCategory.General) }
    
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Card(
            modifier = Modifier
                .width(180.dp)
                .fillMaxHeight()
                .padding(end = 12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SettingCategory.entries.forEach { category ->
                    val isSelected = category == selectedCategory
                    val backgroundColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                    val contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable { selectedCategory = category },
                        shape = MaterialTheme.shapes.medium,
                        color = backgroundColor,
                        contentColor = contentColor
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = when (category) {
                                    SettingCategory.General -> strings.t("settings.general")
                                    SettingCategory.ScreenRecordMirror -> strings.t("settings.screen_record_mirror")
                                    SettingCategory.FileManager -> strings.t("settings.file_manager")
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }
        
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            when (selectedCategory) {
                SettingCategory.General -> {
                    GeneralSettingsPanel(modifier = Modifier.fillMaxSize())
                }
                SettingCategory.ScreenRecordMirror -> {
                    ScreenRecordMirrorSettingsPanel(modifier = Modifier.fillMaxSize())
                }
                SettingCategory.FileManager -> {
                    FileManagerSettingsPanel(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
fun GeneralSettingsPanel(
    modifier: Modifier = Modifier,
) {
    val strings = rememberAppStrings()
    val scrollState = rememberScrollState()
    val settings = AppSettingsShared.currentSettings
    val coroutineScope = rememberCoroutineScope()
    var adbRuntimeInfo by remember { mutableStateOf<AdbRuntimeInfo?>(null) }
    var adbActionMessage by remember { mutableStateOf<String?>(null) }
    var isCheckingAdb by remember { mutableStateOf(false) }

    LaunchedEffect(settings.customAdbPath) {
        adbRuntimeInfo = null
        adbRuntimeInfo = loadAdbRuntimeInfo()
    }
    
    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = strings.t("settings.adb_configuration"),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${strings.t("settings.current_source")}: " +
                        if (adbRuntimeInfo?.isCustom == true) {
                            strings.t("settings.custom")
                        } else {
                            strings.t("settings.bundled")
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${strings.t("settings.path")}: ${adbRuntimeInfo?.path ?: strings.t("common.loading")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${strings.t("settings.version")}: " +
                        (adbRuntimeInfo?.version ?: if (adbRuntimeInfo == null) {
                            strings.t("common.loading")
                        } else {
                            strings.t("settings.unavailable")
                        }),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (adbRuntimeInfo?.version == null && adbRuntimeInfo != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                adbRuntimeInfo?.errorMessage?.let { errorMessage ->
                    Text(
                        text = "${strings.t("common.error")}: $errorMessage",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                adbActionMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        enabled = !isCheckingAdb,
                        onClick = {
                            coroutineScope.launch {
                                isCheckingAdb = true
                                adbActionMessage = null
                                try {
                                    val selectedPath = selectFiles(
                                        dialogTitle = strings.t("settings.select_adb_executable"),
                                        approveButtonText = strings.t("settings.use"),
                                    ).firstOrNull()
                                    if (selectedPath != null) {
                                        val checkResult = checkAdbExecutable(selectedPath)
                                        if (checkResult.isValid && checkResult.normalizedPath != null) {
                                            AppSettingsShared.updateSettings(
                                                settings.copy(customAdbPath = checkResult.normalizedPath)
                                            )
                                            adbActionMessage = strings.t("settings.switched_to_custom_adb")
                                        } else {
                                            adbActionMessage = strings.t("settings.not_switched") +
                                                ": ${checkResult.errorMessage ?: strings.t("settings.unable_to_read_adb_version")}"
                                        }
                                    }
                                } finally {
                                    isCheckingAdb = false
                                }
                            }
                        }
                    ) {
                        Text(if (isCheckingAdb) strings.t("settings.checking") else strings.t("common.change"))
                    }
                    OutlinedButton(
                        enabled = settings.customAdbPath != null && !isCheckingAdb,
                        onClick = {
                            AppSettingsShared.updateSettings(settings.copy(customAdbPath = null))
                            adbActionMessage = strings.t("settings.restored_bundled_adb")
                        }
                    ) {
                        Text(strings.t("settings.restore_default"))
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = strings.t("settings.language"),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = strings.t("settings.language.system_default_hint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                APP_LANGUAGE_OPTIONS.forEach { language ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                AppSettingsShared.updateSettings(settings.copy(language = language))
                            },
                        shape = MaterialTheme.shapes.medium,
                        color = if (AppSettingsShared.currentLanguage == language) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (AppSettingsShared.currentLanguage == language) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = AppSettingsShared.currentLanguage == language,
                                onClick = {
                                    AppSettingsShared.updateSettings(settings.copy(language = language))
                                }
                            )
                            Text(
                                text = strings.languageDisplayName(language),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.t("settings.show_command_timestamp"),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = strings.t("settings.when_enabled_command_log_entries_include_a_time_pref"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.showCommandTime,
                    onCheckedChange = { isChecked ->
                        AppSettingsShared.updateSettings(settings.copy(showCommandTime = isChecked))
                    }
                )
            }
        }
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.t("settings.show_command_duration"),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = strings.t("settings.command_log.duration_hint"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.showCommandDuration,
                    onCheckedChange = { isChecked ->
                        AppSettingsShared.updateSettings(settings.copy(showCommandDuration = isChecked))
                    }
                )
            }
        }
    }
}

@Composable
fun ScreenRecordMirrorSettingsPanel(
    modifier: Modifier = Modifier,
) {
    val strings = rememberAppStrings()
    val scrollState = rememberScrollState()
    val settings = AppSettingsShared.currentSettings
    val coroutineScope = rememberCoroutineScope()
    var scrcpyRuntimeInfo by remember { mutableStateOf<ScrcpyRuntimeInfo?>(null) }
    var scrcpyActionMessage by remember { mutableStateOf<String?>(null) }
    var isCheckingScrcpy by remember { mutableStateOf(false) }

    LaunchedEffect(settings.customScrcpyPath) {
        scrcpyRuntimeInfo = null
        scrcpyRuntimeInfo = loadScrcpyRuntimeInfo()
    }

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = strings.t("settings.scrcpy_configuration"),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = strings.t("settings.scrcpy_configuration_hint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${strings.t("settings.current_source")}: " +
                        if (scrcpyRuntimeInfo?.isCustom == true) {
                            strings.t("settings.custom")
                        } else {
                            strings.t("settings.bundled_or_path")
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${strings.t("settings.path")}: ${scrcpyRuntimeInfo?.path ?: strings.t("common.loading")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${strings.t("settings.version")}: " +
                        (scrcpyRuntimeInfo?.version ?: if (scrcpyRuntimeInfo == null) {
                            strings.t("common.loading")
                        } else {
                            strings.t("settings.unavailable")
                        }),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (scrcpyRuntimeInfo?.version == null && scrcpyRuntimeInfo != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                scrcpyRuntimeInfo?.errorMessage?.let { errorMessage ->
                    Text(
                        text = "${strings.t("common.error")}: $errorMessage",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                scrcpyActionMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        enabled = !isCheckingScrcpy,
                        onClick = {
                            coroutineScope.launch {
                                isCheckingScrcpy = true
                                scrcpyActionMessage = null
                                try {
                                    val selectedPath = selectFiles(
                                        dialogTitle = strings.t("settings.select_scrcpy_executable"),
                                        approveButtonText = strings.t("settings.use"),
                                    ).firstOrNull()
                                    if (selectedPath != null) {
                                        val checkResult = checkScrcpyExecutable(selectedPath)
                                        if (checkResult.isValid && checkResult.normalizedPath != null) {
                                            AppSettingsShared.updateSettings(
                                                settings.copy(customScrcpyPath = checkResult.normalizedPath)
                                            )
                                            scrcpyActionMessage = strings.t("settings.switched_to_custom_scrcpy")
                                        } else {
                                            scrcpyActionMessage = strings.t("settings.not_switched") +
                                                ": ${checkResult.errorMessage ?: strings.t("settings.unable_to_read_scrcpy_version")}"
                                        }
                                    }
                                } finally {
                                    isCheckingScrcpy = false
                                }
                            }
                        }
                    ) {
                        Text(if (isCheckingScrcpy) strings.t("settings.checking") else strings.t("common.change"))
                    }
                    OutlinedButton(
                        enabled = settings.customScrcpyPath != null && !isCheckingScrcpy,
                        onClick = {
                            AppSettingsShared.updateSettings(settings.copy(customScrcpyPath = null))
                            scrcpyActionMessage = strings.t("settings.restored_default_scrcpy")
                        }
                    ) {
                        Text(strings.t("settings.restore_default"))
                    }
                }
            }
        }

        SettingsOptionGroup(
            title = strings.t("settings.screen_record.format"),
            hint = strings.t("settings.screen_record.format_hint"),
            options = SCREEN_RECORD_FORMAT_OPTIONS,
            selected = settings.screenRecordFormat,
            optionLabel = { strings.screenRecordFormatLabel(it) },
            onSelected = { format ->
                AppSettingsShared.updateSettings(settings.copy(screenRecordFormat = format))
            },
        )

        SettingsOptionGroup(
            title = strings.t("settings.screen_record.max_size"),
            hint = strings.t("settings.screen_record.max_size_hint"),
            options = SCREEN_RECORD_MAX_SIZE_OPTIONS,
            selected = settings.screenRecordMaxSize,
            optionLabel = { strings.screenRecordMaxSizeLabel(it) },
            onSelected = { maxSize ->
                AppSettingsShared.updateSettings(settings.copy(screenRecordMaxSize = maxSize))
            },
        )

        SettingsOptionGroup(
            title = strings.t("settings.screen_record.bit_rate"),
            hint = strings.t("settings.screen_record.bit_rate_hint"),
            options = SCREEN_RECORD_BIT_RATE_OPTIONS,
            selected = settings.screenRecordBitRate,
            optionLabel = { strings.screenRecordBitRateLabel(it) },
            onSelected = { bitRate ->
                AppSettingsShared.updateSettings(settings.copy(screenRecordBitRate = bitRate))
            },
        )

        SettingsOptionGroup(
            title = strings.t("settings.screen_record.max_fps"),
            hint = strings.t("settings.screen_record.max_fps_hint"),
            options = SCREEN_RECORD_MAX_FPS_OPTIONS,
            selected = settings.screenRecordMaxFps,
            optionLabel = { strings.screenRecordMaxFpsLabel(it) },
            onSelected = { maxFps ->
                AppSettingsShared.updateSettings(settings.copy(screenRecordMaxFps = maxFps))
            },
        )

        SettingsOptionGroup(
            title = strings.t("settings.screen_record.audio_mode"),
            hint = strings.t("settings.screen_record.audio_mode_hint"),
            options = SCREEN_RECORD_AUDIO_MODE_OPTIONS,
            selected = settings.screenRecordAudioMode,
            optionLabel = { strings.screenRecordAudioModeLabel(it) },
            onSelected = { audioMode ->
                AppSettingsShared.updateSettings(settings.copy(screenRecordAudioMode = audioMode))
            },
        )
    }
}

@Composable
private fun <T> SettingsOptionGroup(
    title: String,
    hint: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            options.forEach { option ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelected(option) },
                    shape = MaterialTheme.shapes.medium,
                    color = if (selected == option) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (selected == option) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(
                            selected = selected == option,
                            onClick = { onSelected(option) }
                        )
                        Text(
                            text = optionLabel(option),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FileManagerSettingsPanel(
    modifier: Modifier = Modifier,
) {
    val strings = rememberAppStrings()
    val scrollState = rememberScrollState()
    val settings = AppSettingsShared.currentSettings

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = strings.t("settings.default_root_directory"),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = strings.t("settings.file_manager.default_root_hint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FILE_MANAGER_ROOT_PATH_OPTIONS.forEach { rootPath ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                AppSettingsShared.updateSettings(
                                    settings.copy(fileManagerDefaultRootPath = rootPath)
                                )
                            },
                        shape = MaterialTheme.shapes.medium,
                        color = if (settings.fileManagerDefaultRootPath == rootPath) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (settings.fileManagerDefaultRootPath == rootPath) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = settings.fileManagerDefaultRootPath == rootPath,
                                onClick = {
                                    AppSettingsShared.updateSettings(
                                        settings.copy(fileManagerDefaultRootPath = rootPath)
                                    )
                                }
                            )
                            Text(
                                text = rootPath,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
    }
}
