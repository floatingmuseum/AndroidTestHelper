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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
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
import com.floatingmuseum.android.test.helper.app.removeInstalledApp
import com.floatingmuseum.android.test.helper.device.DeviceSystemInfo
import com.floatingmuseum.android.test.helper.device.DeviceQuickAction
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

private const val APP_VERSION = "1.0.0"

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

        var showPluginBanner by remember { mutableStateOf(false) }
        var bannerMessage by remember { mutableStateOf("") }
        var isInstallingPlugin by remember { mutableStateOf(false) }
        var isBannerDismissedThisSession by remember { mutableStateOf(false) }
        var localApkBytes by remember { mutableStateOf<ByteArray?>(null) }
        var localApkVersionCode by remember { mutableStateOf<Long?>(null) }
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

        fun runSelectedDeviceQuickAction(deviceSerial: String, action: DeviceQuickAction) {
            if (isRunning) return
            scope.launch {
                isRunning = true
                statusText = "正在执行${action.label}..."
                appendCommand("状态: 开始执行${action.label} $deviceSerial")
                try {
                    deviceAdb.runQuickAction(deviceSerial, action, ::appendCommand)
                    statusText = "${action.label}命令已发送"
                    appendCommand("状态: ${action.label}命令已发送完成")
                    if (action == DeviceQuickAction.SHUTDOWN) {
                        selectedDeviceSerial = null
                        deviceSystemInfo = null
                        systemProperties = emptyList()
                    }
                } catch (error: Throwable) {
                    statusText = error.message ?: "${action.label}失败"
                    appendCommand("错误: ${action.label}失败 - ${error.message ?: "未知错误"}")
                } finally {
                    isRunning = false
                }
            }
        }

        fun takeSelectedDeviceScreenshot(deviceSerial: String) {
            if (isRunning) return
            scope.launch {
                isRunning = true
                statusText = "选择截图保存目录..."
                appendCommand("状态: 选择截图保存目录")
                try {
                    val outputPath = selectDirectory(
                        dialogTitle = "选择截图保存路径",
                        approveButtonText = "保存",
                    )
                    if (outputPath == null) {
                        statusText = "已取消截图"
                        appendCommand("状态: 已取消截图")
                    } else {
                        statusText = "正在截取设备屏幕..."
                        appendCommand("状态: 开始截取设备 $deviceSerial 屏幕，保存到 $outputPath")
                        val result = deviceAdb.takeScreenshot(deviceSerial, outputPath, ::appendCommand)
                        statusText = "屏幕截图成功，已保存到 ${result.localPath}"
                        appendCommand("状态: 屏幕截图成功，本地文件: ${result.localPath}，设备文件: ${result.remotePath}")
                    }
                } catch (error: Throwable) {
                    statusText = error.message ?: "屏幕截图失败"
                    appendCommand("错误: 屏幕截图失败 - ${error.message ?: "未知错误"}")
                } finally {
                    isRunning = false
                }
            }
        }

        fun installSelectedApplications(deviceSerial: String) {
            if (isRunning) return
            scope.launch {
                isRunning = true
                statusText = "选择要安装的 APK..."
                appendCommand("状态: 选择要安装的 APK")
                try {
                    val apkPaths = selectApkFiles(
                        dialogTitle = "选择要安装的 APK",
                        approveButtonText = "安装",
                    )
                    if (apkPaths.isEmpty()) {
                        statusText = "已取消安装应用"
                        appendCommand("状态: 已取消安装应用")
                    } else {
                        statusText = "正在安装 ${apkPaths.size} 个应用..."
                        appendCommand("状态: 开始向设备 $deviceSerial 安装 ${apkPaths.size} 个 APK")
                        val results = deviceAdb.installApplications(deviceSerial, apkPaths, ::appendCommand)
                        val successCount = results.count { it.success }
                        val failureResults = results.filterNot { it.success }

                        results.forEach { result ->
                            if (result.success) {
                                appendCommand("状态: 安装成功 - ${result.fileName}")
                            } else {
                                appendCommand("错误: 安装失败 - ${result.fileName} - ${result.message}")
                            }
                        }

                        if (successCount > 0) {
                            thirdPartyApps = emptyList()
                            thirdPartyLoadedSerial = null
                            systemApps = emptyList()
                            systemLoadedSerial = null
                            systemAppsCacheFormattedTime = null
                        }

                        statusText = if (failureResults.isEmpty()) {
                            "应用安装完成，成功 $successCount / ${results.size}"
                        } else {
                            "应用安装完成，成功 $successCount / ${results.size}，失败 ${failureResults.size} 个"
                        }
                        appendCommand("状态: $statusText")
                    }
                } catch (error: CancellationException) {
                    statusText = "安装应用已停止"
                    appendCommand("状态: 安装应用已停止")
                } catch (error: Throwable) {
                    statusText = error.message ?: "安装应用失败"
                    appendCommand("错误: 安装应用失败 - ${error.message ?: "未知错误"}")
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

        fun removeApplicationFromLists(packageName: String): List<InstalledAppInfo> {
            thirdPartyApps = removeInstalledApp(thirdPartyApps, packageName)
            val nextSystemApps = removeInstalledApp(systemApps, packageName)
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
                        "卸载应用" -> {
                            appAdb.uninstallApplication(deviceSerial, app.packageName, app.isSystem, ::appendCommand)
                            val nextSystemApps = removeApplicationFromLists(app.packageName)
                            if (app.isSystem) {
                                appAdb.saveCachedSystemApps(deviceSerial, nextSystemApps)
                                val cached = appAdb.loadCachedSystemApps(deviceSerial)
                                systemAppsCacheFormattedTime = cached?.cacheTimeFormatted
                            }
                            statusText = "已卸载 ${app.packageName}"
                            appendCommand("状态: 已卸载 ${app.packageName}")
                        }
                        "导出APK" -> {
                            val outputPath = selectDirectory(
                                dialogTitle = "选择 APK 导出路径",
                                approveButtonText = "导出",
                            )
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

        LaunchedEffect(selectedReadyDevice?.serialNumber) {
            val deviceSerial = selectedReadyDevice?.serialNumber
            if (deviceSerial != null) {
                val ignoredVersion = appAdb.getIgnoredPluginCheckVersion()
                if (ignoredVersion == APP_VERSION) {
                    showPluginBanner = false
                    return@LaunchedEffect
                }

                if (localApkBytes == null) {
                    try {
                        val bytes = appAdb.getLocalPluginApkBytes()
                        if (bytes != null) {
                            localApkBytes = bytes
                            localApkVersionCode = appAdb.getApkVersionCode(bytes)
                        } else {
                            appendCommand("错误: 未能在 resources/files 下找到 ATHPlugin apk 文件")
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        appendCommand("错误: 未能在 resources/files 下加载到 ATHPlugin apk 文件: ${e.message}")
                    }
                }

                val targetLocalVersion = localApkVersionCode
                if (targetLocalVersion != null) {
                    try {
                        val installedVersion = appAdb.getInstalledPluginVersionCode(deviceSerial, ::appendCommand)
                        if (installedVersion == null) {
                            bannerMessage = "检测到当前设备未安装辅助插件(ATHPlugin)，安装后可极大提升应用数据获取的效率与性能。"
                            showPluginBanner = true
                        } else if (installedVersion < targetLocalVersion) {
                            bannerMessage = "检测到设备上已安装的辅助插件(ATHPlugin)版本过低(设备: v$installedVersion，本地: v$targetLocalVersion)，建议更新。"
                            showPluginBanner = true
                        } else {
                            showPluginBanner = false
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } else {
                showPluginBanner = false
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
                if (showPluginBanner && !isBannerDismissedThisSession) {
                    PluginCheckBanner(
                        message = bannerMessage,
                        onInstall = {
                            val serial = selectedReadyDevice?.serialNumber
                            val bytes = localApkBytes
                            if (serial != null && bytes != null && !isInstallingPlugin) {
                                isInstallingPlugin = true
                                scope.launch {
                                    statusText = "正在设备 $serial 上安装辅助插件..."
                                    appendCommand("状态: 开始在设备 $serial 上安装辅助插件...")
                                    val success = appAdb.installPluginApk(serial, bytes, ::appendCommand)
                                    if (success) {
                                        statusText = "辅助插件安装成功"
                                        appendCommand("状态: 设备 $serial 上的辅助插件安装成功")
                                        showPluginBanner = false
                                    } else {
                                        statusText = "辅助插件安装失败，请检查连接"
                                        appendCommand("错误: 设备 $serial 上的辅助插件安装失败")
                                    }
                                    isInstallingPlugin = false
                                }
                            }
                        },
                        onIgnore = {
                            appAdb.saveIgnoredPluginCheckVersion(APP_VERSION)
                            showPluginBanner = false
                        },
                        onDismiss = {
                            isBannerDismissedThisSession = true
                        },
                        isInstalling = isInstallingPlugin
                    )
                }

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
                                    onInstallApplications = {
                                        selectedReadyDevice?.serialNumber?.let { installSelectedApplications(it) }
                                    },
                                    onQuickAction = { action ->
                                        selectedReadyDevice?.serialNumber?.let {
                                            runSelectedDeviceQuickAction(it, action)
                                        }
                                    },
                                    isRunning = isRunning,
                                    onBatteryControl = { args ->
                                        selectedReadyDevice?.serialNumber?.let { serial ->
                                            scope.launch {
                                                isRunning = true
                                                statusText = "执行电池模拟操作..."
                                                appendCommand("状态: 执行电池模拟操作 adb -s $serial shell dumpsys battery ${args.joinToString(" ")}")
                                                try {
                                                    deviceAdb.controlBattery(serial, args, ::appendCommand)
                                                    statusText = "电池模拟操作已执行"
                                                    appendCommand("状态: 电池模拟操作已执行")
                                                    deviceSystemInfo = deviceAdb.loadSystemInfo(serial, ::appendCommand)
                                                } catch (error: Throwable) {
                                                    statusText = error.message ?: "电池模拟操作失败"
                                                    appendCommand("错误: 电池模拟操作失败 - ${error.message ?: "未知错误"}")
                                                } finally {
                                                    isRunning = false
                                                }
                                            }
                                        }
                                    },
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

@Composable
private fun PluginCheckBanner(
    message: String,
    onInstall: () -> Unit,
    onIgnore: () -> Unit,
    onDismiss: () -> Unit,
    isInstalling: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "💡",
                style = MaterialTheme.typography.bodyLarge
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = onInstall,
                enabled = !isInstalling,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (isInstalling) "安装中..." else "立即安装")
            }

            TextButton(
                onClick = onIgnore,
                enabled = !isInstalling
            ) {
                Text("不再提示")
            }

            TextButton(
                onClick = onDismiss,
                enabled = !isInstalling
            ) {
                Text(
                    text = "✕",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
