package com.floatingmuseum.android.test.helper.device

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.floatingmuseum.android.test.helper.AndroidDevice

private enum class InfoSection {
    BASIC,
    SCREEN,
    BATTERY,
    PROPERTIES
}

@Composable
fun DeviceTestPanel(
    selectedDevice: AndroidDevice?,
    systemInfo: DeviceSystemInfo?,
    systemProperties: List<SystemProperty>,
    isLoadingInfo: Boolean,
    isLoadingProperties: Boolean,
    onRefreshInfo: () -> Unit,
    onRefreshProperties: () -> Unit,
    onReboot: () -> Unit,
    onTakeScreenshot: () -> Unit,
    onInstallApplications: () -> Unit,
    onQuickAction: (DeviceQuickAction) -> Unit,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedSection by remember { mutableStateOf(InfoSection.BASIC) }

    val filteredProperties = remember(systemProperties, searchQuery) {
        val query = searchQuery.trim().lowercase()
        if (query.isEmpty()) {
            systemProperties
        } else {
            systemProperties.filter {
                it.key.lowercase().contains(query) || it.value.lowercase().contains(query)
            }
        }
    }

    Card(modifier = modifier.fillMaxWidth()) {
        if (selectedDevice == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "请先在底部选择一个状态为 device 的已连接设备。",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 左侧：获取设备信息（标签切换页）
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "设备系统信息",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    // 横向排列的信息类别选项
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        InfoSection.values().forEach { section ->
                            val isSelected = selectedSection == section
                            val title = when (section) {
                                InfoSection.BASIC -> "基础信息"
                                InfoSection.SCREEN -> "屏幕信息"
                                InfoSection.BATTERY -> "电池信息"
                                InfoSection.PROPERTIES -> "系统属性"
                            }
                            
                            val isSecProperties = section == InfoSection.PROPERTIES
                            val isLoading = if (isSecProperties) isLoadingProperties else isLoadingInfo
                            val onRefresh = if (isSecProperties) onRefreshProperties else onRefreshInfo

                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        if (isSelected) {
                                            if (!isLoading && !isRunning) {
                                                onRefresh()
                                            }
                                        } else {
                                            selectedSection = section
                                        }
                                    }
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    
                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        if (isLoading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(12.dp),
                                                strokeWidth = 1.5.dp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        } else {
                                            Text(
                                                text = "↻",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 信息展示卡片（占满左半边剩下的空间）
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            when (selectedSection) {
                                InfoSection.BASIC -> {
                                    if (systemInfo != null) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .verticalScroll(rememberScrollState()),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            InfoRow("序列号 (SN)", selectedDevice.serialNumber)
                                            InfoRow("设备品牌", systemInfo.brand)
                                            InfoRow("设备型号", systemInfo.model)
                                            InfoRow("Android 版本", "Android ${systemInfo.androidVersion}")
                                            InfoRow("SDK 版本", "API ${systemInfo.sdkVersion}")
                                            InfoRow("CPU 架构 (ABI)", systemInfo.cpuAbi)
                                            InfoRow("IP 地址", systemInfo.ipAddress)
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "暂无数据，请点击上方选项卡刷新获取",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                InfoSection.SCREEN -> {
                                    if (systemInfo != null) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .verticalScroll(rememberScrollState()),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            InfoRow("屏幕分辨率", systemInfo.screenSize)
                                            InfoRow("屏幕密度 (Density)", systemInfo.screenDensity)
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "暂无数据，请点击上方选项卡刷新获取",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                InfoSection.BATTERY -> {
                                    if (systemInfo != null) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .verticalScroll(rememberScrollState()),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            InfoRow("电池电量", systemInfo.batteryLevel?.let { "$it%" } ?: "未知")
                                            InfoRow("电池状态", systemInfo.batteryStatus)
                                            InfoRow("电池健康度", systemInfo.batteryHealth)
                                            InfoRow("电池温度", systemInfo.batteryTemp)
                                            InfoRow("电池电压", systemInfo.batteryVoltage)
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "暂无数据，请点击上方选项卡刷新获取",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                InfoSection.PROPERTIES -> {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = searchQuery,
                                            onValueChange = { searchQuery = it },
                                            label = { Text("搜索属性 (例如: ro.product)...") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f)
                                        ) {
                                            if (systemProperties.isEmpty()) {
                                                Text(
                                                    text = "无属性数据，请点击上方选项卡重新拉取。",
                                                    modifier = Modifier.align(Alignment.Center),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            } else if (filteredProperties.isEmpty()) {
                                                Text(
                                                    text = "没有找到匹配的属性值。",
                                                    modifier = Modifier.align(Alignment.Center),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            } else {
                                                LazyColumn(
                                                    modifier = Modifier.fillMaxSize(),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    items(
                                                        items = filteredProperties,
                                                        key = { it.key }
                                                    ) { prop ->
                                                        Column(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .border(
                                                                    width = 1.dp,
                                                                    color = MaterialTheme.colorScheme.outlineVariant,
                                                                    shape = RoundedCornerShape(4.dp)
                                                                )
                                                                .background(
                                                                    color = MaterialTheme.colorScheme.surface,
                                                                    shape = RoundedCornerShape(4.dp)
                                                                )
                                                                .padding(8.dp)
                                                        ) {
                                                            SelectionContainer {
                                                                Text(
                                                                    text = prop.key,
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.primary,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }
                                                            Spacer(modifier = Modifier.height(2.dp))
                                                            SelectionContainer {
                                                                Text(
                                                                    text = prop.value,
                                                                    style = MaterialTheme.typography.bodyMedium,
                                                                    fontFamily = FontFamily.Monospace,
                                                                    color = MaterialTheme.colorScheme.onSurface,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 右侧：快捷操作区
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "设备快捷操作",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    val shortcutActions = listOf(
                        DeviceShortcutAction("重启", true, onReboot),
                        DeviceShortcutAction("关机", true) { onQuickAction(DeviceQuickAction.SHUTDOWN) },
                        DeviceShortcutAction("截屏", false, onTakeScreenshot),
                        DeviceShortcutAction("APK安装", false, onInstallApplications),
                        DeviceShortcutAction("电源键", false) { onQuickAction(DeviceQuickAction.POWER) },
                        DeviceShortcutAction("菜单键", false) { onQuickAction(DeviceQuickAction.MENU) },
                        DeviceShortcutAction("HOME键", false) { onQuickAction(DeviceQuickAction.HOME) },
                        DeviceShortcutAction("返回键", false) { onQuickAction(DeviceQuickAction.BACK) },
                        DeviceShortcutAction("音量加", false) { onQuickAction(DeviceQuickAction.VOLUME_UP) },
                        DeviceShortcutAction("音量减", false) { onQuickAction(DeviceQuickAction.VOLUME_DOWN) },
                        DeviceShortcutAction("静音", false) { onQuickAction(DeviceQuickAction.MUTE) },
                        DeviceShortcutAction("亮屏", false) { onQuickAction(DeviceQuickAction.WAKE) },
                        DeviceShortcutAction("熄屏", false) { onQuickAction(DeviceQuickAction.SLEEP) },
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        shortcutActions.chunked(3).forEach { rowActions ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowActions.forEach { action ->
                                    DeviceShortcutButton(
                                        action = action,
                                        enabled = !isRunning,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(3 - rowActions.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class DeviceShortcutAction(
    val label: String,
    val isDanger: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun DeviceShortcutButton(
    action: DeviceShortcutAction,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = if (action.isDanger) {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        )
    } else {
        ButtonDefaults.buttonColors()
    }
    Button(
        onClick = action.onClick,
        enabled = enabled,
        colors = colors,
        modifier = modifier.height(38.dp)
    ) {
        Text(
            text = action.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        SelectionContainer {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
