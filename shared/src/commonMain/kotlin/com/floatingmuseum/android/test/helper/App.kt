package com.floatingmuseum.android.test.helper

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class TestModule(val title: String) {
    DataFill("数据填充"),
    App("应用"),
}

@Composable
@Preview
fun App() {
    MaterialTheme {
        val adb = remember { createDataFillAdb() }
        val scope = rememberCoroutineScope()
        var devices by remember { mutableStateOf<List<AndroidDevice>>(emptyList()) }
        var selectedDeviceSerial by remember { mutableStateOf<String?>(null) }
        var storageInfo by remember { mutableStateOf<StorageInfo?>(null) }
        var customFillValue by remember { mutableStateOf("") }
        var remainingValue by remember { mutableStateOf("") }
        var statusText by remember { mutableStateOf("等待连接设备") }
        var isRunning by remember { mutableStateOf(false) }
        var runningJob by remember { mutableStateOf<Job?>(null) }
        var fillProgress by remember { mutableStateOf<FillProgress?>(null) }
        var commandLog by remember { mutableStateOf<List<String>>(emptyList()) }
        var bottomPanelHeightPx by remember { mutableStateOf<Float?>(null) }
        var selectedTestModule by remember { mutableStateOf(TestModule.DataFill) }
        var installedApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
        var appListDeviceSerial by remember { mutableStateOf<String?>(null) }
        var isLoadingApplications by remember { mutableStateOf(false) }
        val selectedDevice = devices.firstOrNull { it.serialNumber == selectedDeviceSerial }
        val selectedReadyDevice = selectedDevice?.takeIf { it.isReady }

        fun appendCommand(command: String) {
            commandLog = (commandLog + command).takeLast(200)
        }

        fun refreshDevices() {
            if (isRunning) return
            scope.launch {
                isRunning = true
                statusText = "扫描设备..."
                try {
                    val discoveredDevices = adb.listDevices(::appendCommand)
                    devices = discoveredDevices
                    val selectedStillConnected = discoveredDevices.any {
                        it.serialNumber == selectedDeviceSerial && it.isReady
                    }
                    val nextSelectedDeviceSerial = when {
                        selectedStillConnected -> selectedDeviceSerial
                        else -> discoveredDevices.firstOrNull { it.isReady }?.serialNumber
                    }
                    selectedDeviceSerial = nextSelectedDeviceSerial
                    storageInfo = null
                    fillProgress = null
                    installedApps = emptyList()
                    appListDeviceSerial = null

                    if (nextSelectedDeviceSerial == null) {
                        statusText = if (discoveredDevices.isEmpty()) {
                            "未发现设备"
                        } else {
                            "发现 ${discoveredDevices.size} 台设备，无可用设备"
                        }
                    } else if (selectedTestModule == TestModule.DataFill) {
                        statusText = "发现 ${discoveredDevices.size} 台设备，读取存储..."
                        try {
                            storageInfo = adb.loadStorageInfo(nextSelectedDeviceSerial, ::appendCommand)
                            statusText = "发现 ${discoveredDevices.size} 台设备，已刷新存储"
                        } catch (error: Throwable) {
                            statusText = error.message ?: "读取存储失败"
                        }
                    } else {
                        statusText = "发现 ${discoveredDevices.size} 台设备，准备读取应用"
                    }
                } catch (error: Throwable) {
                    statusText = error.message ?: "扫描设备失败"
                } finally {
                    isRunning = false
                }
            }
        }

        fun runAdbTask(name: String, block: suspend () -> StorageInfo) {
            if (isRunning) return
            val job = scope.launch {
                isRunning = true
                fillProgress = null
                statusText = "$name..."
                try {
                    storageInfo = block()
                    statusText = "$name 完成"
                } catch (error: CancellationException) {
                    fillProgress = null
                    val deviceSerial = selectedReadyDevice?.serialNumber
                    if (deviceSerial == null) {
                        statusText = "任务已停止"
                    } else {
                        statusText = "任务已停止，刷新存储..."
                        try {
                            storageInfo = withContext(NonCancellable) {
                                adb.loadStorageInfo(deviceSerial, ::appendCommand)
                            }
                            statusText = "任务已停止，已刷新存储"
                        } catch (refreshError: Throwable) {
                            statusText = "任务已停止，刷新存储失败：${refreshError.message ?: "未知错误"}"
                        }
                    }
                } catch (error: Throwable) {
                    statusText = error.message ?: "任务失败"
                } finally {
                    isRunning = false
                    runningJob = null
                }
            }
            runningJob = job
        }

        fun loadApplicationsForDevice(deviceSerial: String) {
            if (isRunning) return
            scope.launch {
                isRunning = true
                isLoadingApplications = true
                installedApps = emptyList()
                appListDeviceSerial = deviceSerial
                statusText = "读取应用列表..."
                try {
                    installedApps = adb.loadInstalledApps(deviceSerial, ::appendCommand)
                    val disabledCount = installedApps.count { !it.isEnabled }
                    statusText = "已读取 ${installedApps.size} 个应用，禁用 $disabledCount 个"
                } catch (error: Throwable) {
                    appListDeviceSerial = null
                    statusText = error.message ?: "读取应用列表失败"
                } finally {
                    isLoadingApplications = false
                    isRunning = false
                }
            }
        }

        fun refreshStorageForDevice(deviceSerial: String) {
            runAdbTask("读取平板存储") {
                adb.loadStorageInfo(deviceSerial, ::appendCommand)
            }
        }

        LaunchedEffect(Unit) {
            refreshDevices()
        }

        LaunchedEffect(selectedTestModule, selectedReadyDevice?.serialNumber, isRunning) {
            val deviceSerial = selectedReadyDevice?.serialNumber
            if (
                selectedTestModule == TestModule.App &&
                deviceSerial != null &&
                !isRunning &&
                appListDeviceSerial != deviceSerial
            ) {
                loadApplicationsForDevice(deviceSerial)
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeContentPadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ModuleSwitcher(
                    selectedModule = selectedTestModule,
                    isRunning = isRunning,
                    onSelect = { selectedTestModule = it },
                )

                SplitContent(
                    bottomPanelHeightPx = bottomPanelHeightPx,
                    onBottomPanelHeightPxChange = { bottomPanelHeightPx = it },
                    topContent = {
                        when (selectedTestModule) {
                            TestModule.DataFill -> {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    StoragePanel(
                                        storageInfo = storageInfo,
                                        isRunning = isRunning,
                                        onRefresh = {
                                            val deviceSerial = selectedReadyDevice?.serialNumber
                                            if (deviceSerial == null) {
                                                statusText = "先选择状态为 device 的设备"
                                            } else {
                                                refreshStorageForDevice(deviceSerial)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    )

                                    FillControls(
                                        customFillValue = customFillValue,
                                        onCustomFillValueChange = { customFillValue = it },
                                        remainingValue = remainingValue,
                                        onRemainingValueChange = { remainingValue = it },
                                        isRunning = isRunning,
                                        hasReadyDevice = selectedReadyDevice != null,
                                        fillProgress = fillProgress,
                                        onStopFill = {
                                            statusText = "正在停止任务..."
                                            runningJob?.cancel()
                                        },
                                        onFillFixed = { sizeBytes ->
                                            val deviceSerial = selectedReadyDevice?.serialNumber
                                            if (deviceSerial == null) {
                                                statusText = "先选择状态为 device 的设备"
                                            } else {
                                                runAdbTask("填充 ${formatBytes(sizeBytes)}") {
                                                    adb.fillSize(
                                                        deviceSerial = deviceSerial,
                                                        sizeBytes = sizeBytes,
                                                        logCommand = ::appendCommand,
                                                        onProgress = { progress ->
                                                            fillProgress = progress
                                                            statusText = "填充中 ${formatPercent(progress.ratio)}"
                                                        },
                                                    )
                                                }
                                            }
                                        },
                                        onFillCustom = {
                                            val sizeBytes = parseGiBInput(customFillValue)
                                            if (sizeBytes == null) {
                                                statusText = "自定义填充大小无效，单位为 GB"
                                            } else if (selectedReadyDevice == null) {
                                                statusText = "先选择状态为 device 的设备"
                                            } else {
                                                val deviceSerial = selectedReadyDevice.serialNumber
                                                runAdbTask("填充 ${formatBytes(sizeBytes)}") {
                                                    adb.fillSize(
                                                        deviceSerial = deviceSerial,
                                                        sizeBytes = sizeBytes,
                                                        logCommand = ::appendCommand,
                                                        onProgress = { progress ->
                                                            fillProgress = progress
                                                            statusText = "填充中 ${formatPercent(progress.ratio)}"
                                                        },
                                                    )
                                                }
                                            }
                                        },
                                        onFillUntilRemaining = {
                                            val targetBytes = parseGiBInput(remainingValue)
                                            if (targetBytes == null) {
                                                statusText = "剩余空间目标无效，单位为 GB"
                                            } else if (selectedReadyDevice == null) {
                                                statusText = "先选择状态为 device 的设备"
                                            } else {
                                                val deviceSerial = selectedReadyDevice.serialNumber
                                                runAdbTask("填充到剩余 ${formatBytes(targetBytes)}") {
                                                    adb.fillUntilRemaining(
                                                        deviceSerial = deviceSerial,
                                                        targetAvailableBytes = targetBytes,
                                                        logCommand = ::appendCommand,
                                                        onStorageProgress = { storageInfo = it },
                                                        onFillProgress = { progress ->
                                                            fillProgress = progress
                                                            statusText = "填充中 ${formatPercent(progress.ratio)}"
                                                        },
                                                    )
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }

                            TestModule.App -> ApplicationTestPanel(
                                apps = installedApps,
                                isLoading = isLoadingApplications,
                                selectedDevice = selectedReadyDevice,
                                loadedDeviceSerial = appListDeviceSerial,
                                onRefresh = {
                                    val deviceSerial = selectedReadyDevice?.serialNumber
                                    if (deviceSerial == null) {
                                        statusText = "先选择状态为 device 的设备"
                                    } else {
                                        loadApplicationsForDevice(deviceSerial)
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    },
                    bottomContent = {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            DevicePanel(
                                devices = devices,
                                selectedDeviceSerial = selectedDeviceSerial,
                                isRunning = isRunning,
                                onRefresh = ::refreshDevices,
                                onSelect = { device ->
                                    selectedDeviceSerial = device.serialNumber
                                    storageInfo = null
                                    installedApps = emptyList()
                                    appListDeviceSerial = null
                                    statusText = if (device.isReady) {
                                        "已选择 ${device.model}"
                                    } else {
                                        "设备 ${device.serialNumber} 当前不可用：${device.state}"
                                    }
                                    if (device.isReady) {
                                        when (selectedTestModule) {
                                            TestModule.DataFill -> refreshStorageForDevice(device.serialNumber)
                                            TestModule.App -> loadApplicationsForDevice(device.serialNumber)
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f).fillMaxSize(),
                            )
                            CommandLogPanel(
                                commandLog = commandLog,
                                modifier = Modifier.weight(3f).fillMaxSize(),
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ModuleSwitcher(
    selectedModule: TestModule,
    isRunning: Boolean,
    onSelect: (TestModule) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TestModule.entries.forEach { module ->
                val selected = module == selectedModule
                Button(
                    onClick = { onSelect(module) },
                    enabled = !isRunning || selected,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    ),
                ) {
                    Text(module.title)
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun SplitContent(
    bottomPanelHeightPx: Float?,
    onBottomPanelHeightPxChange: (Float) -> Unit,
    topContent: @Composable () -> Unit,
    bottomContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val handleHeight = 24.dp
        val totalHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val minBottomHeightPx = totalHeightPx * 0.18f
        val maxBottomHeightPx = totalHeightPx * 0.65f
        val bottomHeightPx = (bottomPanelHeightPx ?: totalHeightPx * 0.25f)
            .coerceIn(minBottomHeightPx, maxBottomHeightPx)
        val bottomHeight = with(density) { bottomHeightPx.toDp() }
        val topHeight = maxHeight - bottomHeight - handleHeight
        var isHandleHovered by remember { mutableStateOf(false) }
        var isHandleDragging by remember { mutableStateOf(false) }
        val dragState = rememberDraggableState { delta ->
            val nextHeightPx = (bottomHeightPx - delta).coerceIn(minBottomHeightPx, maxBottomHeightPx)
            onBottomPanelHeightPxChange(nextHeightPx)
        }
        val handleColor = if (isHandleHovered || isHandleDragging) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        }
        val handleBackground = if (isHandleHovered || isHandleDragging) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surface
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topHeight),
            ) {
                topContent()
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(handleHeight)
                    .background(handleBackground)
                    .onPointerEvent(PointerEventType.Enter) { isHandleHovered = true }
                    .onPointerEvent(PointerEventType.Exit) { isHandleHovered = false }
                    .verticalResizePointerIcon()
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = dragState,
                        onDragStarted = { isHandleDragging = true },
                        onDragStopped = { isHandleDragging = false },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(handleColor),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bottomHeight),
            ) {
                bottomContent()
            }
        }
    }
}

@Composable
private fun ApplicationTestPanel(
    apps: List<InstalledAppInfo>,
    isLoading: Boolean,
    selectedDevice: AndroidDevice?,
    loadedDeviceSerial: String?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val thirdPartyApps = apps.filterNot { it.isSystem }
    val systemApps = apps.filter { it.isSystem }
    val disabledCount = apps.count { !it.isEnabled }
    var isThirdPartyExpanded by remember { mutableStateOf(true) }
    var isSystemExpanded by remember { mutableStateOf(true) }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "应用",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (apps.isEmpty()) {
                            "第三方应用优先，系统应用在后"
                        } else {
                            "共 ${apps.size} 个，第三方 ${thirdPartyApps.size} 个，系统 ${systemApps.size} 个，禁用 $disabledCount 个"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = onRefresh,
                    enabled = selectedDevice != null && !isLoading,
                ) {
                    Text("刷新应用")
                }
            }

            when {
                selectedDevice == null -> {
                    Text("先选择状态为 device 的设备。")
                }

                isLoading -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = "正在读取 ${selectedDevice.model} 的应用列表...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                loadedDeviceSerial != selectedDevice.serialNumber -> {
                    Text("进入模块后将自动读取当前设备应用。")
                }

                apps.isEmpty() -> {
                    Text("未读取到应用。可刷新设备或应用列表。")
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 260.dp),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (thirdPartyApps.isNotEmpty()) {
                            item(
                                key = "third-party-header",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                ApplicationSectionHeader(
                                    title = "第三方应用",
                                    count = thirdPartyApps.size,
                                    isExpanded = isThirdPartyExpanded,
                                    onToggle = { isThirdPartyExpanded = !isThirdPartyExpanded },
                                )
                            }
                            if (isThirdPartyExpanded) {
                                items(
                                    items = thirdPartyApps,
                                    key = { it.packageName },
                                ) { app ->
                                    ApplicationTile(app)
                                }
                            }
                        }

                        if (systemApps.isNotEmpty()) {
                            item(
                                key = "system-header",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                ApplicationSectionHeader(
                                    title = "系统应用",
                                    count = systemApps.size,
                                    isExpanded = isSystemExpanded,
                                    onToggle = { isSystemExpanded = !isSystemExpanded },
                                )
                            }
                            if (isSystemExpanded) {
                                items(
                                    items = systemApps,
                                    key = { it.packageName },
                                ) { app ->
                                    ApplicationTile(app)
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
private fun ApplicationSectionHeader(
    title: String,
    count: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$title ($count)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (isExpanded) "收起" else "展开",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ApplicationTile(app: InstalledAppInfo) {
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
        modifier = Modifier.fillMaxWidth(),
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
                    text = app.appName,
                    style = MaterialTheme.typography.titleSmall,
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
                    text = "版本名 ${app.versionName} · 版本号 ${app.versionCode?.toString() ?: "-"}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun formatPercent(value: Float): String {
    val percent = (value.coerceIn(0f, 1f) * 100).toInt()
    return "$percent%"
}

@Composable
private fun DevicePanel(
    devices: List<AndroidDevice>,
    selectedDeviceSerial: String?,
    isRunning: Boolean,
    onRefresh: () -> Unit,
    onSelect: (AndroidDevice) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "连接设备",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Button(onClick = onRefresh, enabled = !isRunning) {
                    Text("刷新设备")
                }
            }

            if (devices.isEmpty()) {
                Text("未发现设备。连接 USB 后刷新。")
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    devices.forEach { device ->
                        val isSelected = device.serialNumber == selectedDeviceSerial
                        Button(
                            onClick = { onSelect(device) },
                            enabled = !isRunning,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                contentColor = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            ),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text("SN: ${device.serialNumber}")
                                Text("型号: ${device.model}")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoragePanel(
    storageInfo: StorageInfo?,
    isRunning: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "设备存储",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Button(onClick = onRefresh, enabled = !isRunning) {
                    Text("刷新")
                }
            }

            if (storageInfo == null) {
                Text("尚未读取。连接平板后点击刷新。")
            } else {
                LinearProgressIndicator(
                    progress = { storageInfo.usedRatio.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    StorageMetric("总容量", formatBytes(storageInfo.totalBytes), Modifier.weight(1f))
                    StorageMetric("已使用", formatBytes(storageInfo.usedBytes), Modifier.weight(1f))
                    StorageMetric("可用", formatBytes(storageInfo.availableBytes), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StorageMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FillControls(
    customFillValue: String,
    onCustomFillValueChange: (String) -> Unit,
    remainingValue: String,
    onRemainingValueChange: (String) -> Unit,
    isRunning: Boolean,
    hasReadyDevice: Boolean,
    fillProgress: FillProgress?,
    onStopFill: () -> Unit,
    onFillFixed: (Long) -> Unit,
    onFillCustom: () -> Unit,
    onFillUntilRemaining: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "填充任务",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            if (fillProgress != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LinearProgressIndicator(
                        progress = { fillProgress.ratio.coerceIn(0f, 1f) },
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = onStopFill,
                        enabled = isRunning,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Text("停止")
                    }
                }
                Text(
                    text = "进度 ${formatPercent(fillProgress.ratio)} · 已写入 ${formatBytes(fillProgress.completedBytes)} / ${formatBytes(fillProgress.totalBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { onFillFixed(1L * BytesInGiB) }, enabled = !isRunning && hasReadyDevice) {
                    Text("填充 1G")
                }
                Button(onClick = { onFillFixed(5L * BytesInGiB) }, enabled = !isRunning && hasReadyDevice) {
                    Text("填充 5G")
                }
                Button(onClick = { onFillFixed(10L * BytesInGiB) }, enabled = !isRunning && hasReadyDevice) {
                    Text("填充 10G")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = customFillValue,
                    onValueChange = onCustomFillValueChange,
                    enabled = !isRunning && hasReadyDevice,
                    singleLine = true,
                    label = { Text("自定义填充大小 GB") },
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onFillCustom, enabled = !isRunning && hasReadyDevice) {
                    Text("开始填充")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = remainingValue,
                    onValueChange = onRemainingValueChange,
                    enabled = !isRunning && hasReadyDevice,
                    singleLine = true,
                    label = { Text("目标剩余空间 GB") },
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onFillUntilRemaining, enabled = !isRunning && hasReadyDevice) {
                    Text("填充到目标")
                }
            }
        }
    }
}

@Composable
private fun CommandLogPanel(
    commandLog: List<String>,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(commandLog.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "命令记录",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp)
                    .verticalScroll(scrollState),
            ) {
                if (commandLog.isEmpty()) {
                    Text("暂无命令")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        commandLog.forEach { command ->
                            Text(
                                text = "> $command",
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Spacer(Modifier.height(1.dp))
                    }
                }
            }
        }
    }
}
