package com.floatingmuseum.android.test.helper.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.floatingmuseum.android.test.helper.AndroidDevice

private val SearchMatchBackground = Color(0xFFFFFF00)
private val SearchMatchContent = Color(0xFF111111)

@Composable
fun ApplicationTestPanel(
    thirdPartyApps: List<InstalledAppInfo>,
    systemApps: List<InstalledAppInfo>,
    isLoadingThirdParty: Boolean,
    isLoadingSystem: Boolean,
    selectedDevice: AndroidDevice?,
    thirdPartyLoadedSerial: String?,
    systemLoadedSerial: String?,
    systemAppsCacheFormattedTime: String?,
    thirdPartyProgressCurrent: Int,
    thirdPartyProgressTotal: Int,
    systemProgressCurrent: Int,
    systemProgressTotal: Int,
    onRefreshThirdParty: () -> Unit,
    onRefreshSystem: () -> Unit,
    onClearCache: () -> Unit,
    onApplicationAction: (InstalledAppInfo, String) -> Unit,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    var isThirdPartyExpanded by remember { mutableStateOf(true) }
    var isSystemExpanded by remember { mutableStateOf(true) }
    var appSearchQuery by remember { mutableStateOf("") }
    var selectedAppPackageName by remember(selectedDevice?.serialNumber) { mutableStateOf<String?>(null) }
    val filteredThirdPartyApps = remember(thirdPartyApps, appSearchQuery) {
        filterInstalledApps(thirdPartyApps, appSearchQuery)
    }
    val filteredSystemApps = remember(systemApps, appSearchQuery) {
        filterInstalledApps(systemApps, appSearchQuery)
    }
    val allApps = thirdPartyApps + systemApps
    val selectedApp = allApps.firstOrNull { it.packageName == selectedAppPackageName }
    val isSearching = appSearchQuery.trim().isNotEmpty()

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (selectedApp == null) {
                ApplicationListHeader(
                    searchQuery = appSearchQuery,
                    onSearchQueryChange = { appSearchQuery = it },
                    thirdPartyApps = thirdPartyApps,
                    systemApps = systemApps,
                    filteredThirdPartyApps = filteredThirdPartyApps,
                    filteredSystemApps = filteredSystemApps,
                    isSearching = isSearching,
                    isRunning = isRunning,
                    onClearCache = onClearCache,
                )
            }

            when {
                selectedDevice == null -> {
                    Text("先选择状态为 device 的设备。")
                }

                selectedApp != null -> {
                    ApplicationDetailPanel(
                        app = selectedApp,
                        onBack = { selectedAppPackageName = null },
                        onAction = { action -> onApplicationAction(selectedApp, action) },
                        isRunning = isRunning,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 260.dp),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(
                            key = "third-party-header",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            ApplicationSectionHeader(
                                title = "第三方应用",
                                count = if (thirdPartyApps.isNotEmpty()) filteredThirdPartyApps.size else null,
                                isLoading = isLoadingThirdParty,
                                isExpanded = isThirdPartyExpanded,
                                onRefresh = onRefreshThirdParty,
                                onToggle = { isThirdPartyExpanded = !isThirdPartyExpanded },
                            )
                        }
                        if (isThirdPartyExpanded) {
                            if (isLoadingThirdParty) {
                                item(
                                    key = "third-party-loading",
                                    span = { GridItemSpan(maxLineSpan) }
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (thirdPartyProgressTotal > 0) {
                                            val progress = thirdPartyProgressCurrent.toFloat() / thirdPartyProgressTotal
                                            LinearProgressIndicator(
                                                progress = { progress },
                                                modifier = Modifier.width(200.dp)
                                            )
                                            Text(
                                                text = "获取中: $thirdPartyProgressCurrent / $thirdPartyProgressTotal (${(progress * 100).toInt()}%)",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            LinearProgressIndicator(modifier = Modifier.width(200.dp))
                                            Text(
                                                text = "正在初始化应用列表...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            } else if (thirdPartyApps.isEmpty()) {
                                item(
                                    key = "third-party-empty",
                                    span = { GridItemSpan(maxLineSpan) }
                                ) {
                                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                        Text("无数据。请点击刷新获取第三方应用列表。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            } else if (filteredThirdPartyApps.isEmpty()) {
                                item(
                                    key = "third-party-no-match",
                                    span = { GridItemSpan(maxLineSpan) }
                                ) {
                                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                        Text("没有匹配的第三方应用。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            } else {
                                items(
                                    items = filteredThirdPartyApps,
                                    key = { it.packageName },
                                ) { app ->
                                    ApplicationTile(
                                        app = app,
                                        searchQuery = appSearchQuery,
                                        onClick = { selectedAppPackageName = app.packageName },
                                    )
                                }
                            }
                        }

                        item(
                            key = "system-header",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            ApplicationSectionHeader(
                                title = "系统应用",
                                count = if (systemApps.isNotEmpty()) filteredSystemApps.size else null,
                                isLoading = isLoadingSystem,
                                isExpanded = isSystemExpanded,
                                cacheTime = systemAppsCacheFormattedTime,
                                onRefresh = onRefreshSystem,
                                onToggle = { isSystemExpanded = !isSystemExpanded },
                            )
                        }
                        if (isSystemExpanded) {
                            if (isLoadingSystem) {
                                item(
                                    key = "system-loading",
                                    span = { GridItemSpan(maxLineSpan) }
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (systemProgressTotal > 0) {
                                            val progress = systemProgressCurrent.toFloat() / systemProgressTotal
                                            LinearProgressIndicator(
                                                progress = { progress },
                                                modifier = Modifier.width(200.dp)
                                            )
                                            Text(
                                                text = "获取中: $systemProgressCurrent / $systemProgressTotal (${(progress * 100).toInt()}%)",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            LinearProgressIndicator(modifier = Modifier.width(200.dp))
                                            Text(
                                                text = "正在初始化应用列表...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            } else if (systemApps.isEmpty()) {
                                item(
                                    key = "system-empty",
                                    span = { GridItemSpan(maxLineSpan) }
                                ) {
                                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                        Text("无数据。请点击刷新获取系统应用并生成本地缓存。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            } else if (filteredSystemApps.isEmpty()) {
                                item(
                                    key = "system-no-match",
                                    span = { GridItemSpan(maxLineSpan) }
                                ) {
                                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                        Text("没有匹配的系统应用。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            } else {
                                items(
                                    items = filteredSystemApps,
                                    key = { it.packageName },
                                ) { app ->
                                    ApplicationTile(
                                        app = app,
                                        searchQuery = appSearchQuery,
                                        onClick = { selectedAppPackageName = app.packageName },
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

@Composable
private fun ApplicationListHeader(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    thirdPartyApps: List<InstalledAppInfo>,
    systemApps: List<InstalledAppInfo>,
    filteredThirdPartyApps: List<InstalledAppInfo>,
    filteredSystemApps: List<InstalledAppInfo>,
    isSearching: Boolean,
    isRunning: Boolean,
    onClearCache: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "应用",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    label = { Text("搜索应用名或包名") },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onClearCache,
                    enabled = !isRunning,
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                    modifier = Modifier.height(40.dp),
                ) {
                    Text(
                        text = "清空缓存",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Text(
                text = applicationListDescription(
                    thirdPartyApps = thirdPartyApps,
                    systemApps = systemApps,
                    filteredThirdPartyApps = filteredThirdPartyApps,
                    filteredSystemApps = filteredSystemApps,
                    isSearching = isSearching,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun applicationListDescription(
    thirdPartyApps: List<InstalledAppInfo>,
    systemApps: List<InstalledAppInfo>,
    filteredThirdPartyApps: List<InstalledAppInfo>,
    filteredSystemApps: List<InstalledAppInfo>,
    isSearching: Boolean,
): String {
    if (thirdPartyApps.isEmpty() && systemApps.isEmpty()) {
        return "请手动刷新获取应用列表。系统应用获取后将自动缓存至本地。"
    }

    val parts = mutableListOf<String>()
    if (thirdPartyApps.isNotEmpty()) {
        parts.add(
            if (isSearching) {
                "第三方 ${filteredThirdPartyApps.size} / ${thirdPartyApps.size} 个"
            } else {
                "第三方 ${thirdPartyApps.size} 个"
            }
        )
    }
    if (systemApps.isNotEmpty()) {
        parts.add(
            if (isSearching) {
                "系统 ${filteredSystemApps.size} / ${systemApps.size} 个"
            } else {
                "系统 ${systemApps.size} 个"
            }
        )
    }
    val visibleApps = if (isSearching) {
        filteredThirdPartyApps + filteredSystemApps
    } else {
        thirdPartyApps + systemApps
    }
    val totalDisabled = visibleApps.count { !it.isEnabled }
    parts.add("禁用 $totalDisabled 个")
    return parts.joinToString(" · ")
}

@Composable
private fun ApplicationSectionHeader(
    title: String,
    count: Int?,
    isLoading: Boolean,
    isExpanded: Boolean,
    cacheTime: String? = null,
    onRefresh: () -> Unit,
    onToggle: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f).clickable(onClick = onToggle)
            ) {
                Text(
                    text = if (count != null) "$title ($count)" else title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (cacheTime != null) {
                    Text(
                        text = "· 缓存时间: $cacheTime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (isExpanded) "收起" else "展开",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.size(12.dp))

            Button(
                onClick = onRefresh,
                enabled = !isLoading,
                contentPadding = ButtonDefaults.TextButtonContentPadding,
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = if (isLoading) "读取中..." else "刷新",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun ApplicationTile(
    app: InstalledAppInfo,
    searchQuery: String,
    onClick: () -> Unit,
) {
    val disabled = !app.isEnabled
    val containerColor = if (disabled) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (disabled) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ApplicationIcon(
                iconBytes = app.iconBytes,
                packageName = app.packageName,
                modifier = Modifier.size(44.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = highlightedSearchText(app.appName, searchQuery),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = highlightedSearchText(app.packageName, searchQuery),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${app.versionName}(${app.versionCode?.toString() ?: "-"})",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ApplicationDetailPanel(
    app: InstalledAppInfo,
    onBack: () -> Unit,
    onAction: (String) -> Unit,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    var showDangerousActionConfirmDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ApplicationIcon(
                    iconBytes = app.iconBytes,
                    packageName = app.packageName,
                    modifier = Modifier
                        .size(56.dp)
                        .clickable(enabled = !isRunning && app.iconBytes != null) {
                            onAction("保存图标")
                        },
                )
                SelectionContainer {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = app.appName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = app.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${app.versionName}(${app.versionCode?.toString() ?: "-"})",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Button(onClick = onBack) {
                    Text("返回")
                }
            }
        }

        ApplicationInfoBlock(
            lines = listOf(
                "compileSdkVersion: ${formatSdkVersion(app.compileSdkVersion)}",
                "minSdkVersion: ${formatSdkVersion(app.minSdkVersion)}",
                "targetSdkVersion: ${formatSdkVersion(app.targetSdkVersion)}",
                "应用类型: ${if (app.isSystem) "系统应用" else "第三方应用"}",
                "启用状态: ${if (app.isEnabled) "已启用" else "已停用"}",
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        ApplicationActionGroup(
            actions = listOf("启动应用", "结束应用", "清除数据", "停用应用", "启用应用", "导出APK", "卸载应用"),
            onAction = { action ->
                if (action == "卸载应用" || app.isSystem && (action == "结束应用" || action == "清除数据" || action == "停用应用")) {
                    pendingAction = action
                    showDangerousActionConfirmDialog = true
                } else {
                    onAction(action)
                }
            },
            isRunning = isRunning,
        )
    }

    if (showDangerousActionConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                showDangerousActionConfirmDialog = false
                pendingAction = null
            },
            title = {
                Text(
                    text = "确认${pendingAction ?: "危险操作"}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = if (pendingAction == "卸载应用") {
                        "将从当前设备卸载 ${app.packageName}。系统应用会执行用户 0 卸载，可能影响设备功能。"
                    } else {
                        "此操作可能对设备造成严重影响，请在知晓风险的情况下操作。"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val action = pendingAction
                        if (action != null) {
                            onAction(action)
                        }
                        showDangerousActionConfirmDialog = false
                        pendingAction = null
                    }
                ) {
                    Text(
                        text = "确认",
                        color = if (pendingAction == "卸载应用") {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDangerousActionConfirmDialog = false
                        pendingAction = null
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun ApplicationInfoBlock(
    lines: List<String>,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier,
    ) {
        SelectionContainer {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                lines.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ApplicationActionGroup(
    actions: List<String>,
    onAction: (String) -> Unit,
    isRunning: Boolean,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            actions.chunked(5).forEach { rowActions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowActions.forEach { action ->
                        val isDangerAction = action == "卸载应用"
                        Button(
                            onClick = { onAction(action) },
                            enabled = !isRunning,
                            modifier = Modifier.weight(1f),
                            contentPadding = ButtonDefaults.TextButtonContentPadding,
                            colors = if (isDangerAction) {
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                )
                            } else {
                                ButtonDefaults.buttonColors()
                            },
                        ) {
                            Text(
                                text = action,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    repeat(5 - rowActions.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private fun formatSdkVersion(sdkVersion: Int?): String {
    if (sdkVersion == null) return "-"
    val androidVersion = androidVersionName(sdkVersion)
    return if (androidVersion == null) {
        sdkVersion.toString()
    } else {
        "$sdkVersion(${androidVersion.version},${androidVersion.name})"
    }
}

private data class AndroidVersionName(
    val version: String,
    val name: String,
)

private fun androidVersionName(sdkVersion: Int): AndroidVersionName? {
    return when (sdkVersion) {
        21 -> AndroidVersionName("Android 5.0", "Lollipop")
        22 -> AndroidVersionName("Android 5.1", "Lollipop")
        23 -> AndroidVersionName("Android 6", "Marshmallow")
        24 -> AndroidVersionName("Android 7.0", "Nougat")
        25 -> AndroidVersionName("Android 7.1", "Nougat")
        26 -> AndroidVersionName("Android 8.0", "Oreo")
        27 -> AndroidVersionName("Android 8.1", "Oreo")
        28 -> AndroidVersionName("Android 9", "Pie")
        29 -> AndroidVersionName("Android 10", "Q")
        30 -> AndroidVersionName("Android 11", "R")
        31 -> AndroidVersionName("Android 12", "Snow Cone")
        32 -> AndroidVersionName("Android 12L", "Snow Cone v2")
        33 -> AndroidVersionName("Android 13", "Tiramisu")
        34 -> AndroidVersionName("Android 14", "Upside Down Cake")
        35 -> AndroidVersionName("Android 15", "Vanilla Ice Cream")
        36 -> AndroidVersionName("Android 16", "Baklava")
        else -> null
    }
}

private fun highlightedSearchText(
    text: String,
    query: String,
) = buildAnnotatedString {
    val keyword = query.trim()
    if (keyword.isEmpty()) {
        append(text)
        return@buildAnnotatedString
    }

    var cursor = 0

    while (cursor < text.length) {
        val matchStart = text.indexOf(keyword, startIndex = cursor, ignoreCase = true)
        if (matchStart < 0) {
            append(text.substring(cursor))
            break
        }

        if (matchStart > cursor) {
            append(text.substring(cursor, matchStart))
        }

        val matchEnd = matchStart + keyword.length
        withStyle(
            SpanStyle(
                background = SearchMatchBackground,
                color = SearchMatchContent,
            )
        ) {
            append(text.substring(matchStart, matchEnd))
        }
        cursor = matchEnd
    }
}
