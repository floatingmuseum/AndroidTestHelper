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
import com.floatingmuseum.android.test.helper.selectFiles
import kotlinx.coroutines.launch

enum class SettingCategory(val title: String) {
    General("通用"),
    FileManager("文件管理"),
}

@Composable
fun SettingsModuleContent(
    modifier: Modifier = Modifier,
) {
    var selectedCategory by remember { mutableStateOf(SettingCategory.General) }
    
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 左边栏：细分选项
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
                                text = category.title,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }
        
        // 右边栏：具体设置项
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
                    text = "ADB 配置",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "当前来源：${if (adbRuntimeInfo?.isCustom == true) "自定义" else "项目自带"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "路径：${adbRuntimeInfo?.path ?: "读取中..."}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "版本：${adbRuntimeInfo?.version ?: if (adbRuntimeInfo == null) "读取中..." else "无法读取"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (adbRuntimeInfo?.version == null && adbRuntimeInfo != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                adbRuntimeInfo?.errorMessage?.let { errorMessage ->
                    Text(
                        text = "错误：$errorMessage",
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
                                        dialogTitle = "选择 adb 可执行文件",
                                        approveButtonText = "使用",
                                    ).firstOrNull()
                                    if (selectedPath != null) {
                                        val checkResult = checkAdbExecutable(selectedPath)
                                        if (checkResult.isValid && checkResult.normalizedPath != null) {
                                            AppSettingsShared.updateSettings(
                                                settings.copy(customAdbPath = checkResult.normalizedPath)
                                            )
                                            adbActionMessage = "已切换到自定义 adb。"
                                        } else {
                                            adbActionMessage = "未切换：${checkResult.errorMessage ?: "无法读取 adb 版本"}"
                                        }
                                    }
                                } finally {
                                    isCheckingAdb = false
                                }
                            }
                        }
                    ) {
                        Text(if (isCheckingAdb) "检测中" else "修改")
                    }
                    OutlinedButton(
                        enabled = settings.customAdbPath != null && !isCheckingAdb,
                        onClick = {
                            AppSettingsShared.updateSettings(settings.copy(customAdbPath = null))
                            adbActionMessage = "已恢复使用项目自带 adb。"
                        }
                    ) {
                        Text("恢复默认")
                    }
                }
            }
        }

        // 1. 命令前显示执行时间
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
                        text = "在命令前显示执行时间",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "开启后，命令记录中输出的命令前面会带有时间前缀（格式如：23:34:34）。",
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
        
        // 2. 显示命令执行耗时时长
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
                        text = "显示命令执行耗时时长",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "开启后，每条 ADB 命令执行完成后，都会额外显示该命令执行所花费的时间。",
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
                    text = "默认根目录",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "文件管理模块进入、刷新设备和切换设备时，都会从该目录开始读取。",
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
