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
import com.floatingmuseum.android.test.helper.selectFiles
import kotlinx.coroutines.launch

enum class SettingCategory {
    General,
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
                                    SettingCategory.General -> strings.t("auto.general.1823af98")
                                    SettingCategory.FileManager -> strings.t("auto.file_manager.b61a05b1")
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
                    text = strings.t("auto.adb_configuration.6658200c"),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${strings.t("auto.current_source.ebf3885d")}: " +
                        if (adbRuntimeInfo?.isCustom == true) {
                            strings.t("auto.custom.69796b08")
                        } else {
                            strings.t("auto.bundled.b55b9572")
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${strings.t("auto.path.f433503b")}: ${adbRuntimeInfo?.path ?: strings.t("auto.loading.7d20d2dc")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${strings.t("auto.version.8bd064a3")}: " +
                        (adbRuntimeInfo?.version ?: if (adbRuntimeInfo == null) {
                            strings.t("auto.loading.7d20d2dc")
                        } else {
                            strings.t("auto.unavailable.4dc00079")
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
                        text = "${strings.t("auto.error.5966c2d3")}: $errorMessage",
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
                                        dialogTitle = strings.t("auto.select_adb_executable.b74e2c6e"),
                                        approveButtonText = strings.t("auto.use.7770795d"),
                                    ).firstOrNull()
                                    if (selectedPath != null) {
                                        val checkResult = checkAdbExecutable(selectedPath)
                                        if (checkResult.isValid && checkResult.normalizedPath != null) {
                                            AppSettingsShared.updateSettings(
                                                settings.copy(customAdbPath = checkResult.normalizedPath)
                                            )
                                            adbActionMessage = strings.t("auto.switched_to_custom_adb.6466a20b")
                                        } else {
                                            adbActionMessage = strings.t("auto.not_switched.63fb8e6d") +
                                                ": ${checkResult.errorMessage ?: strings.t("auto.unable_to_read_adb_version.287fb046")}"
                                        }
                                    }
                                } finally {
                                    isCheckingAdb = false
                                }
                            }
                        }
                    ) {
                        Text(if (isCheckingAdb) strings.t("auto.checking.f7a3748f") else strings.t("auto.change.8df5aec0"))
                    }
                    OutlinedButton(
                        enabled = settings.customAdbPath != null && !isCheckingAdb,
                        onClick = {
                            AppSettingsShared.updateSettings(settings.copy(customAdbPath = null))
                            adbActionMessage = strings.t("auto.restored_bundled_adb.f4ad0ea9")
                        }
                    ) {
                        Text(strings.t("auto.restore_default.38a935c9"))
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
                    text = strings.t("auto.language.48dea00b"),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = strings.t("auto.before_manual_switching_the_app_follows_the_system_l.3baa80ed"),
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
                        text = strings.t("auto.show_command_timestamp.8e0e5ebd"),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = strings.t("auto.when_enabled_command_log_entries_include_a_time_pref.47b8a52f"),
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
                        text = strings.t("auto.show_command_duration.8b060224"),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = strings.t("auto.when_enabled_each_completed_adb_command_logs_the_tim.937c1e6a"),
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
                    text = strings.t("auto.default_root_directory.b817d958"),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = strings.t("auto.the_file_manager_starts_from_this_directory_when_ent.75400650"),
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
