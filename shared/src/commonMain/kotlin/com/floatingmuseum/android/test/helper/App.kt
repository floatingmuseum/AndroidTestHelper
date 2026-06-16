package com.floatingmuseum.android.test.helper

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.floatingmuseum.android.test.helper.adb.createAdbDeviceManager
import com.floatingmuseum.android.test.helper.datafill.BytesInGiB
import com.floatingmuseum.android.test.helper.datafill.FillControls
import com.floatingmuseum.android.test.helper.datafill.FillProgress
import com.floatingmuseum.android.test.helper.datafill.StorageInfo
import com.floatingmuseum.android.test.helper.datafill.StoragePanel
import com.floatingmuseum.android.test.helper.datafill.createDataFillAdb
import com.floatingmuseum.android.test.helper.datafill.formatBytes
import com.floatingmuseum.android.test.helper.datafill.parseGiBInput
import com.floatingmuseum.android.test.helper.app.InstalledAppInfo
import com.floatingmuseum.android.test.helper.app.ApplicationTestPanel
import com.floatingmuseum.android.test.helper.app.createAppAdb
import com.floatingmuseum.android.test.helper.device.DeviceSystemInfo
import com.floatingmuseum.android.test.helper.device.SystemProperty
import com.floatingmuseum.android.test.helper.device.createDeviceAdb
import com.floatingmuseum.android.test.helper.device.DeviceTestPanel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class TestModule(val title: String) {
    Device("设备"),
    App("应用"),
    DataFill("数据填充"),
}

@Composable
@Preview
fun App() {
    MaterialTheme {
        val adbDeviceManager = remember { createAdbDeviceManager() }
        val dataFillAdb = remember { createDataFillAdb() }
        val appAdb = remember { createAppAdb() }
        val deviceAdb = remember { createDeviceAdb() }

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
        var selectedTestModule by remember { mutableStateOf(TestModule.Device) }
        var thirdPartyApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
        var systemApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
        var thirdPartyLoadedSerial by remember { mutableStateOf<String?>(null) }
        var systemLoadedSerial by remember { mutableStateOf<String?>(null) }
        var isLoadingThirdParty by remember { mutableStateOf(false) }
        var isLoadingSystem by remember { mutableStateOf(false) }
        var systemAppsCacheFormattedTime by remember { mutableStateOf<String?>(null) }
        var thirdPartyProgressCurrent by remember { mutableStateOf(0) }
        var thirdPartyProgressTotal by remember { mutableStateOf(0) }
        var systemProgressCurrent by remember { mutableStateOf(0) }
        var systemProgressTotal by remember { mutableStateOf(0) }
        var deviceSystemInfo by remember { mutableStateOf<DeviceSystemInfo?>(null) }
        var systemProperties by remember { mutableStateOf<List<SystemProperty>>(emptyList()) }
        var isLoadingDeviceSystemInfo by remember { mutableStateOf(false) }
        var isLoadingDeviceProperties by remember { mutableStateOf(false) }
        var deviceSystemInfoLoadedSerial by remember { mutableStateOf<String?>(null) }
        var devicePropertiesLoadedSerial by remember { mutableStateOf<String?>(null) }
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
                    val discoveredDevices = adbDeviceManager.listDevices(::appendCommand)
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
                    thirdPartyApps = emptyList()
                    systemApps = emptyList()
                    thirdPartyLoadedSerial = null
                    systemLoadedSerial = null
                    systemAppsCacheFormattedTime = null
                    thirdPartyProgressCurrent = 0
                    thirdPartyProgressTotal = 0
                    systemProgressCurrent = 0
                    systemProgressTotal = 0
                    deviceSystemInfo = null
                    systemProperties = emptyList()
                    deviceSystemInfoLoadedSerial = null
                    devicePropertiesLoadedSerial = null
                    isLoadingDeviceSystemInfo = false
                    isLoadingDeviceProperties = false

                    if (nextSelectedDeviceSerial == null) {
                        statusText = if (discoveredDevices.isEmpty()) {
                            "未发现设备"
                        } else {
                            "发现 ${discoveredDevices.size} 台设备，无可用设备"
                        }
                        appendCommand("状态: $statusText")
                    } else if (selectedTestModule == TestModule.DataFill) {
                        statusText = "发现 ${discoveredDevices.size} 台设备，读取存储..."
                        try {
                            storageInfo = dataFillAdb.loadStorageInfo(nextSelectedDeviceSerial, ::appendCommand)
                            statusText = "发现 ${discoveredDevices.size} 台设备，已刷新存储"
                            appendCommand("状态: 设备已连接，存储已刷新")
                        } catch (error: Throwable) {
                            statusText = error.message ?: "读取存储失败"
                            appendCommand("错误: 读取存储失败 - ${error.message ?: "未知错误"}")
                        }
                    } else if (selectedTestModule == TestModule.Device) {
                        statusText = "发现 ${discoveredDevices.size} 台设备，读取设备系统信息..."
                        try {
                            deviceSystemInfo = deviceAdb.loadSystemInfo(nextSelectedDeviceSerial, ::appendCommand)
                            systemProperties = deviceAdb.loadSystemProperties(nextSelectedDeviceSerial, ::appendCommand)
                            deviceSystemInfoLoadedSerial = nextSelectedDeviceSerial
                            devicePropertiesLoadedSerial = nextSelectedDeviceSerial
                            statusText = "发现 ${discoveredDevices.size} 台设备，已刷新系统信息"
                            appendCommand("状态: 设备已连接，系统信息与属性已刷新")
                        } catch (error: Throwable) {
                            statusText = error.message ?: "读取系统信息失败"
                            appendCommand("错误: 读取系统信息失败 - ${error.message ?: "未知错误"}")
                        }
                    } else {
                        statusText = "发现 ${discoveredDevices.size} 台设备，准备读取应用"
                        appendCommand("状态: $statusText")
                    }
                } catch (error: Throwable) {
                    statusText = error.message ?: "扫描设备失败"
                    appendCommand("错误: 扫描设备失败 - ${error.message ?: "未知错误"}")
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
                appendCommand("状态: 开始$name...")
                try {
                    storageInfo = block()
                    statusText = "$name 完成"
                    appendCommand("状态: $name 完成")
                } catch (error: CancellationException) {
                    fillProgress = null
                    val deviceSerial = selectedReadyDevice?.serialNumber
                    if (deviceSerial == null) {
                        statusText = "任务已停止"
                        appendCommand("状态: 任务已停止")
                    } else {
                        statusText = "任务已停止，刷新存储..."
                        appendCommand("状态: 任务已停止，刷新存储...")
                        try {
                            storageInfo = withContext(NonCancellable) {
                                dataFillAdb.loadStorageInfo(deviceSerial, ::appendCommand)
                            }
                            statusText = "任务已停止，已刷新存储"
                            appendCommand("状态: 任务已停止，已刷新存储")
                        } catch (refreshError: Throwable) {
                            statusText = "任务已停止，刷新存储失败：${refreshError.message ?: "未知错误"}"
                            appendCommand("错误: 刷新存储失败 - ${refreshError.message ?: "未知错误"}")
                        }
                    }
                } catch (error: Throwable) {
                    statusText = error.message ?: "任务失败"
                    appendCommand("错误: $name 失败 - ${error.message ?: "未知错误"}")
                } finally {
                    isRunning = false
                    runningJob = null
                }
            }
            runningJob = job
        }

        fun loadDeviceSystemInfo(deviceSerial: String) {
            if (isRunning) return
            scope.launch {
                isRunning = true
                isLoadingDeviceSystemInfo = true
                deviceSystemInfo = null
                deviceSystemInfoLoadedSerial = deviceSerial
                statusText = "读取设备系统信息..."
                appendCommand("状态: 读取设备系统信息...")
                try {
                    deviceSystemInfo = deviceAdb.loadSystemInfo(deviceSerial, ::appendCommand)
                    statusText = "读取设备系统信息完成"
                    appendCommand("状态: 读取设备系统信息完成")
                } catch (error: Throwable) {
                    deviceSystemInfoLoadedSerial = null
                    statusText = error.message ?: "读取设备系统信息失败"
                    appendCommand("错误: 读取设备系统信息失败 - ${error.message ?: "未知错误"}")
                } finally {
                    isLoadingDeviceSystemInfo = false
                    isRunning = false
                }
            }
        }

        fun loadDeviceSystemProperties(deviceSerial: String) {
            if (isRunning) return
            scope.launch {
                isRunning = true
                isLoadingDeviceProperties = true
                systemProperties = emptyList()
                devicePropertiesLoadedSerial = deviceSerial
                statusText = "读取系统属性..."
                appendCommand("状态: 读取系统属性...")
                try {
                    systemProperties = deviceAdb.loadSystemProperties(deviceSerial, ::appendCommand)
                    statusText = "读取系统属性完成，共 ${systemProperties.size} 个属性"
                    appendCommand("状态: 读取系统属性完成，共 ${systemProperties.size} 个属性")
                } catch (error: Throwable) {
                    devicePropertiesLoadedSerial = null
                    statusText = error.message ?: "读取系统属性失败"
                    appendCommand("错误: 读取系统属性失败 - ${error.message ?: "未知错误"}")
                } finally {
                    isLoadingDeviceProperties = false
                    isRunning = false
                }
            }
        }

        fun rebootSelectedDevice(deviceSerial: String) {
            if (isRunning) return
            scope.launch {
                isRunning = true
                statusText = "正在重启设备..."
                appendCommand("状态: 开始重启设备 $deviceSerial")
                try {
                    deviceAdb.rebootDevice(deviceSerial, ::appendCommand)
                    statusText = "重启命令已发送"
                    appendCommand("状态: 重启命令已发送完成，设备即将重启")
                    selectedDeviceSerial = null
                    deviceSystemInfo = null
                    systemProperties = emptyList()
                } catch (error: Throwable) {
                    statusText = error.message ?: "重启设备失败"
                    appendCommand("错误: 重启设备失败 - ${error.message ?: "未知错误"}")
                } finally {
                    isRunning = false
                }
            }
        }

        fun takeSelectedDeviceScreenshot(deviceSerial: String) {
            if (isRunning) return
            scope.launch {
                isRunning = true
                statusText = "正在截取设备屏幕..."
                appendCommand("状态: 开始截取设备 $deviceSerial 屏幕...")
                try {
                    val remotePath = deviceAdb.takeScreenshot(deviceSerial, ::appendCommand)
                    statusText = "屏幕截图成功，保存在设备 $remotePath"
                    appendCommand("状态: 屏幕截图成功，文件保存在设备: $remotePath")
                } catch (error: Throwable) {
                    statusText = error.message ?: "屏幕截图失败"
                    appendCommand("错误: 屏幕截图失败 - ${error.message ?: "未知错误"}")
                } finally {
                    isRunning = false
                }
            }
        }

        fun loadThirdPartyApps(deviceSerial: String) {
            if (isRunning) return
            scope.launch {
                isRunning = true
                isLoadingThirdParty = true
                thirdPartyApps = emptyList()
                thirdPartyLoadedSerial = deviceSerial
                thirdPartyProgressCurrent = 0
                thirdPartyProgressTotal = 0
                statusText = "读取第三方应用..."
                appendCommand("状态: 读取第三方应用...")
                try {
                    thirdPartyApps = appAdb.loadInstalledApps(deviceSerial, false, ::appendCommand) { current, total ->
                        thirdPartyProgressCurrent = current
                        thirdPartyProgressTotal = total
                        statusText = "读取第三方应用: $current / $total..."
                    }
                    val disabledCount = thirdPartyApps.count { !it.isEnabled }
                    statusText = "已读取 ${thirdPartyApps.size} 个第三方应用，禁用 $disabledCount 个"
                    appendCommand("状态: 已读取 ${thirdPartyApps.size} 个第三方应用，禁用 $disabledCount 个")
                } catch (error: Throwable) {
                    thirdPartyLoadedSerial = null
                    statusText = error.message ?: "读取第三方应用失败"
                    appendCommand("错误: 读取第三方应用失败 - ${error.message ?: "未知错误"}")
                } finally {
                    isLoadingThirdParty = false
                    isRunning = false
                }
            }
        }

        fun loadSystemApps(deviceSerial: String) {
            if (isRunning) return
            scope.launch {
                isRunning = true
                isLoadingSystem = true
                systemApps = emptyList()
                systemLoadedSerial = deviceSerial
                systemAppsCacheFormattedTime = null
                systemProgressCurrent = 0
                systemProgressTotal = 0
                statusText = "读取系统应用..."
                appendCommand("状态: 读取系统应用...")
                try {
                    val apps = appAdb.loadInstalledApps(deviceSerial, true, ::appendCommand) { current, total ->
                        systemProgressCurrent = current
                        systemProgressTotal = total
                        statusText = "读取系统应用: $current / $total..."
                    }
                    systemApps = apps
                    val disabledCount = apps.count { !it.isEnabled }
                    statusText = "已读取 ${apps.size} 个系统应用，禁用 $disabledCount 个"
                    appendCommand("状态: 已读取 ${apps.size} 个系统应用，禁用 $disabledCount 个")
                    appAdb.saveCachedSystemApps(deviceSerial, apps)
                    val cached = appAdb.loadCachedSystemApps(deviceSerial)
                    if (cached != null) {
                        systemAppsCacheFormattedTime = cached.cacheTimeFormatted
                    }
                } catch (error: Throwable) {
                    systemLoadedSerial = null
                    statusText = error.message ?: "读取系统应用失败"
                    appendCommand("错误: 读取系统应用失败 - ${error.message ?: "未知错误"}")
                } finally {
                    isLoadingSystem = false
                    isRunning = false
                }
            }
        }

        fun refreshStorageForDevice(deviceSerial: String) {
            runAdbTask("读取平板存储") {
                dataFillAdb.loadStorageInfo(deviceSerial, ::appendCommand)
            }
        }

        fun updateApplicationEnabledState(packageName: String, isEnabled: Boolean): List<InstalledAppInfo> {
            thirdPartyApps = thirdPartyApps.map { app ->
                if (app.packageName == packageName) app.copy(isEnabled = isEnabled) else app
            }
            val nextSystemApps = systemApps.map { app ->
                if (app.packageName == packageName) app.copy(isEnabled = isEnabled) else app
            }
            systemApps = nextSystemApps
            return nextSystemApps
        }

        fun runApplicationAction(app: InstalledAppInfo, action: String) {
            if (isRunning) return
            val deviceSerial = selectedReadyDevice?.serialNumber
            if (deviceSerial == null) {
                statusText = "先选择状态为 device 的设备"
                appendCommand("错误: 先选择状态为 device 的设备")
                return
            }

            scope.launch {
                isRunning = true
                statusText = "$action ${app.packageName}..."
                appendCommand("状态: 开始$action ${app.packageName}...")
                try {
                    when (action) {
                        "启动应用" -> {
                            appAdb.launchApplication(deviceSerial, app.packageName, ::appendCommand)
                            statusText = "已启动 ${app.packageName}"
                            appendCommand("状态: 已启动 ${app.packageName}")
                        }
                        "结束应用" -> {
                            appAdb.stopApplication(deviceSerial, app.packageName, ::appendCommand)
                            statusText = "已结束 ${app.packageName}"
                            appendCommand("状态: 已结束 ${app.packageName}")
                        }
                        "清除数据" -> {
                            appAdb.clearApplicationData(deviceSerial, app.packageName, ::appendCommand)
                            statusText = "已清除数据 ${app.packageName}"
                            appendCommand("状态: 已清除数据 ${app.packageName}")
                        }
                        "停用应用" -> {
                            appAdb.disableApplication(deviceSerial, app.packageName, ::appendCommand)
                            val nextSystemApps = updateApplicationEnabledState(app.packageName, false)
                            if (app.isSystem) {
                                appAdb.saveCachedSystemApps(deviceSerial, nextSystemApps)
                            }
                            statusText = "已停用 ${app.packageName}"
                            appendCommand("状态: 已停用 ${app.packageName}")
                        }
                        "启用应用" -> {
                            appAdb.enableApplication(deviceSerial, app.packageName, ::appendCommand)
                            val nextSystemApps = updateApplicationEnabledState(app.packageName, true)
                            if (app.isSystem) {
                                appAdb.saveCachedSystemApps(deviceSerial, nextSystemApps)
                            }
                            statusText = "已启用 ${app.packageName}"
                            appendCommand("状态: 已启用 ${app.packageName}")
                        }
                        "导出APK" -> {
                            val outputPath = selectDirectory()
                            if (outputPath == null) {
                                statusText = "已取消导出"
                                appendCommand("状态: 已取消导出")
                            } else {
                                val result = appAdb.exportApplicationApk(deviceSerial, app.packageName, outputPath, ::appendCommand)
                                statusText = "已导出 ${result.fileCount} 个 APK 到 ${result.directoryPath}"
                                appendCommand("状态: 已导出 ${result.fileCount} 个 APK 到 ${result.directoryPath}")
                            }
                        }
                        else -> {
                            statusText = "未知应用操作：$action"
                            appendCommand("错误: 未知应用操作：$action")
                        }
                    }
                } catch (error: CancellationException) {
                    statusText = "应用操作已停止"
                    appendCommand("状态: 应用操作已停止")
                } catch (error: Throwable) {
                    statusText = error.message ?: "$action 失败"
                    appendCommand("错误: $action 失败 - ${error.message ?: "未知错误"}")
                } finally {
                    isRunning = false
                }
            }
        }

        LaunchedEffect(Unit) {
            refreshDevices()
        }

        LaunchedEffect(selectedTestModule, selectedReadyDevice?.serialNumber) {
            val deviceSerial = selectedReadyDevice?.serialNumber
            if (deviceSerial != null) {
                if (selectedTestModule == TestModule.Device) {
                    if (deviceSystemInfoLoadedSerial != deviceSerial) {
                        loadDeviceSystemInfo(deviceSerial)
                    }
                    if (devicePropertiesLoadedSerial != deviceSerial) {
                        loadDeviceSystemProperties(deviceSerial)
                    }
                } else if (selectedTestModule == TestModule.App) {
                    if (systemLoadedSerial != deviceSerial) {
                        val cached = appAdb.loadCachedSystemApps(deviceSerial)
                        if (cached != null) {
                            systemApps = cached.apps
                            systemAppsCacheFormattedTime = cached.cacheTimeFormatted
                            systemLoadedSerial = deviceSerial
                        } else {
                            systemApps = emptyList()
                            systemAppsCacheFormattedTime = null
                            systemLoadedSerial = null
                        }
                    }
                    if (thirdPartyLoadedSerial != deviceSerial) {
                        thirdPartyApps = emptyList()
                        thirdPartyLoadedSerial = null
                    }
                }
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
                            TestModule.Device -> {
                                DeviceTestPanel(
                                    selectedDevice = selectedDevice,
                                    systemInfo = deviceSystemInfo,
                                    systemProperties = systemProperties,
                                    isLoadingInfo = isLoadingDeviceSystemInfo,
                                    isLoadingProperties = isLoadingDeviceProperties,
                                    onRefreshInfo = {
                                        selectedReadyDevice?.serialNumber?.let { loadDeviceSystemInfo(it) }
                                    },
                                    onRefreshProperties = {
                                        selectedReadyDevice?.serialNumber?.let { loadDeviceSystemProperties(it) }
                                    },
                                    onReboot = {
                                        selectedReadyDevice?.serialNumber?.let { rebootSelectedDevice(it) }
                                    },
                                    onTakeScreenshot = {
                                        selectedReadyDevice?.serialNumber?.let { takeSelectedDeviceScreenshot(it) }
                                    },
                                    isRunning = isRunning,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

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
                                                appendCommand("错误: 先选择状态为 device 的设备")
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
                                            runningJob?.cancel()
                                        },
                                        onFillFixed = { sizeBytes ->
                                            val deviceSerial = selectedReadyDevice?.serialNumber
                                            if (deviceSerial != null) {
                                                runAdbTask("填充 ${formatBytes(sizeBytes)}") {
                                                    dataFillAdb.fillSize(deviceSerial, sizeBytes, ::appendCommand) { progress ->
                                                        fillProgress = progress
                                                    }
                                                }
                                            }
                                        },
                                        onFillCustom = {
                                            val deviceSerial = selectedReadyDevice?.serialNumber
                                            val sizeBytes = parseGiBInput(customFillValue)
                                            if (deviceSerial != null && sizeBytes != null) {
                                                runAdbTask("填充 ${formatBytes(sizeBytes)}") {
                                                    dataFillAdb.fillSize(deviceSerial, sizeBytes, ::appendCommand) { progress ->
                                                        fillProgress = progress
                                                    }
                                                }
                                            } else {
                                                statusText = "请输入有效的填充大小"
                                                appendCommand("错误: 请输入有效的填充大小")
                                            }
                                        },
                                        onFillUntilRemaining = {
                                            val deviceSerial = selectedReadyDevice?.serialNumber
                                            val targetBytes = parseGiBInput(remainingValue)
                                            if (deviceSerial != null && targetBytes != null) {
                                                runAdbTask("填充到剩余 ${formatBytes(targetBytes)}") {
                                                    dataFillAdb.fillUntilRemaining(
                                                        deviceSerial = deviceSerial,
                                                        targetAvailableBytes = targetBytes,
                                                        logCommand = ::appendCommand,
                                                        onStorageProgress = { storage ->
                                                            storageInfo = storage
                                                        },
                                                        onFillProgress = { progress ->
                                                            fillProgress = progress
                                                        },
                                                    )
                                                }
                                            } else {
                                                statusText = "请输入有效的剩余空间"
                                                appendCommand("错误: 请输入有效的剩余空间")
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }

                            TestModule.App -> {
                                ApplicationTestPanel(
                                    thirdPartyApps = thirdPartyApps,
                                    systemApps = systemApps,
                                    isLoadingThirdParty = isLoadingThirdParty,
                                    isLoadingSystem = isLoadingSystem,
                                    selectedDevice = selectedDevice,
                                    thirdPartyLoadedSerial = thirdPartyLoadedSerial,
                                    systemLoadedSerial = systemLoadedSerial,
                                    systemAppsCacheFormattedTime = systemAppsCacheFormattedTime,
                                    thirdPartyProgressCurrent = thirdPartyProgressCurrent,
                                    thirdPartyProgressTotal = thirdPartyProgressTotal,
                                    systemProgressCurrent = systemProgressCurrent,
                                    systemProgressTotal = systemProgressTotal,
                                    onRefreshThirdParty = {
                                        val serial = selectedReadyDevice?.serialNumber
                                        if (serial != null) loadThirdPartyApps(serial)
                                    },
                                    onRefreshSystem = {
                                        val serial = selectedReadyDevice?.serialNumber
                                        if (serial != null) loadSystemApps(serial)
                                    },
                                    onApplicationAction = ::runApplicationAction,
                                    isRunning = isRunning,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
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
                                    fillProgress = null
                                    thirdPartyApps = emptyList()
                                    systemApps = emptyList()
                                    thirdPartyLoadedSerial = null
                                    systemLoadedSerial = null
                                    systemAppsCacheFormattedTime = null
                                    thirdPartyProgressCurrent = 0
                                    thirdPartyProgressTotal = 0
                                    systemProgressCurrent = 0
                                    systemProgressTotal = 0

                                    deviceSystemInfo = null
                                    systemProperties = emptyList()
                                    deviceSystemInfoLoadedSerial = null
                                    devicePropertiesLoadedSerial = null

                                    if (device.isReady) {
                                        if (selectedTestModule == TestModule.DataFill) {
                                            refreshStorageForDevice(device.serialNumber)
                                        } else if (selectedTestModule == TestModule.Device) {
                                            loadDeviceSystemInfo(device.serialNumber)
                                            loadDeviceSystemProperties(device.serialNumber)
                                        } else {
                                            statusText = "已选择设备 ${device.serialNumber}"
                                        }
                                    } else {
                                        statusText = "设备不可用：${device.state}"
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )

                            CommandLogPanel(
                                commandLog = commandLog,
                                modifier = Modifier.weight(2f),
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
                SelectionContainer {
                    if (commandLog.isEmpty()) {
                        Text("暂无命令")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            commandLog.forEach { command ->
                                val textColor = when {
                                    command.startsWith("错误:") || command.startsWith("错误 ") || command.contains("失败") -> {
                                        MaterialTheme.colorScheme.error
                                    }
                                    command.startsWith("状态:") || command.startsWith("状态 ") -> {
                                        MaterialTheme.colorScheme.primary
                                    }
                                    else -> {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                }
                                Text(
                                    text = "> $command",
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textColor,
                                )
                            }
                            Spacer(Modifier.height(1.dp))
                        }
                    }
                }
            }
        }
    }
}
