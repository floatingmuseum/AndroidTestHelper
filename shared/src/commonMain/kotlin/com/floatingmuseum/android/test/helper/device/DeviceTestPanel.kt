package com.floatingmuseum.android.test.helper.device

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
    isRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }

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
                // 左侧：系统详细信息卡片
                Column(
                    modifier = Modifier.weight(2f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "设备系统信息",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Card(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (isLoadingInfo) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            } else if (systemInfo != null) {
                                val infoList = listOf(
                                    "序列号 (SN)" to selectedDevice.serialNumber,
                                    "设备品牌" to systemInfo.brand,
                                    "设备型号" to systemInfo.model,
                                    "Android 版本" to "Android ${systemInfo.androidVersion}",
                                    "SDK 版本" to "API ${systemInfo.sdkVersion}",
                                    "CPU 架构 (ABI)" to systemInfo.cpuAbi,
                                    "屏幕分辨率" to systemInfo.screenSize,
                                    "电池电量" to (systemInfo.batteryLevel?.let { "$it%" } ?: "未知"),
                                    "IP 地址" to systemInfo.ipAddress
                                )

                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(infoList) { (label, value) ->
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
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "暂无系统信息，点击下方刷新获取",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Button(
                                onClick = onRefreshInfo,
                                enabled = !isRunning && !isLoadingInfo,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("刷新基本信息")
                            }
                        }
                    }
                }

                // 右侧：快捷操作与 getprop 系统属性
                Column(
                    modifier = Modifier.weight(3f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 快捷操作栏
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "快捷控制操作",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = onReboot,
                                enabled = !isRunning,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("重启设备 (Reboot)")
                            }

                            Button(
                                onClick = onTakeScreenshot,
                                enabled = !isRunning,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("快捷屏幕截图")
                            }
                        }
                    }

                    // System Properties (getprop) 面板
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "系统属性 (getprop)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Button(
                                onClick = onRefreshProperties,
                                enabled = !isRunning && !isLoadingProperties,
                                contentPadding = ButtonDefaults.ContentPadding
                            ) {
                                Text("重新加载属性")
                            }
                        }

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("搜索属性 (例如: ro.product)...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Card(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (isLoadingProperties) {
                                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                                } else if (systemProperties.isEmpty()) {
                                    Text(
                                        text = "无属性数据，请点击刷新重新拉取。",
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
                                        modifier = Modifier.fillMaxSize().padding(8.dp),
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
}
