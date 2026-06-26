package com.floatingmuseum.android.test.helper

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.floatingmuseum.android.test.helper.adb.createAdbDeviceManager
import com.floatingmuseum.android.test.helper.datafill.DataFillModuleContent
import com.floatingmuseum.android.test.helper.datafill.rememberDataFillModuleController
import com.floatingmuseum.android.test.helper.app.ApplicationAction
import com.floatingmuseum.android.test.helper.app.InstalledAppInfo
import com.floatingmuseum.android.test.helper.app.ApplicationDetailContent
import com.floatingmuseum.android.test.helper.app.ApplicationDetailSection
import com.floatingmuseum.android.test.helper.app.ApplicationDetailSource
import com.floatingmuseum.android.test.helper.app.ApplicationDetailItem
import com.floatingmuseum.android.test.helper.app.PluginVersionInfo
import com.floatingmuseum.android.test.helper.app.ApplicationTestPanel
import com.floatingmuseum.android.test.helper.app.PluginCheckBanner
import com.floatingmuseum.android.test.helper.app.applicationActionLabel
import com.floatingmuseum.android.test.helper.app.createAppAdb
import com.floatingmuseum.android.test.helper.app.displayTitle
import com.floatingmuseum.android.test.helper.app.pluginCheckIgnoreKey
import com.floatingmuseum.android.test.helper.app.removeInstalledApp
import com.floatingmuseum.android.test.helper.device.DeviceSystemInfo
import com.floatingmuseum.android.test.helper.device.DeviceQuickAction
import com.floatingmuseum.android.test.helper.device.SystemProperty
import com.floatingmuseum.android.test.helper.device.createDeviceAdb
import com.floatingmuseum.android.test.helper.device.DeviceTestPanel
import com.floatingmuseum.android.test.helper.device.displayLabel
import com.floatingmuseum.android.test.helper.devicelog.DeviceLogPanel
import com.floatingmuseum.android.test.helper.devicelog.LogCaptureFloatingButton
import com.floatingmuseum.android.test.helper.devicelog.rememberDeviceLogModuleController
import com.floatingmuseum.android.test.helper.filemanager.FileManagerModuleContent
import com.floatingmuseum.android.test.helper.filemanager.rememberFileManagerModuleController
import com.floatingmuseum.android.test.helper.localization.commandError
import com.floatingmuseum.android.test.helper.localization.commandStatus
import com.floatingmuseum.android.test.helper.localization.localized
import com.floatingmuseum.android.test.helper.localization.unknownError
import com.floatingmuseum.android.test.helper.settings.AppSettingsShared
import com.floatingmuseum.android.test.helper.settings.SettingsModuleContent
import com.floatingmuseum.android.test.helper.getCurrentTimeFormatted
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class BannerActionType {
    INSTALL,
    UPDATE,
    ENABLE
}

@Composable
@Preview
fun App() {
    MaterialTheme {
        remember {
            AppSettingsShared.init()
        }

        val adbDeviceManager = remember { createAdbDeviceManager() }
        val appAdb = remember { createAppAdb() }
        val deviceAdb = remember { createDeviceAdb() }
        val appSettings = AppSettingsShared.currentSettings

        val scope = rememberCoroutineScope()
        var devices by remember { mutableStateOf<List<AndroidDevice>>(emptyList()) }
        var selectedDeviceSerial by remember { mutableStateOf<String?>(null) }

        var showPluginBanner by remember { mutableStateOf(false) }
        var bannerMessage by remember { mutableStateOf("") }
        var isInstallingPlugin by remember { mutableStateOf(false) }
        var isEnablingPlugin by remember { mutableStateOf(false) }
        var bannerActionType by remember { mutableStateOf(BannerActionType.INSTALL) }
        var isBannerDismissedThisSession by remember { mutableStateOf(false) }
        var localApkBytes by remember { mutableStateOf<ByteArray?>(null) }
        var localApkVersionInfo by remember { mutableStateOf<PluginVersionInfo?>(null) }
        var statusText by remember { mutableStateOf(localized("auto.waiting_for_device.762ff8f8")) }
        var isRunning by remember { mutableStateOf(false) }
        var commandLog by remember { mutableStateOf<List<String>>(emptyList()) }
        var bottomPanelHeightPx by remember { mutableStateOf<Float?>(null) }
        var selectedTestModule by remember { mutableStateOf(TestModule.Device) }
        var thirdPartyApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
        var systemApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
        var thirdPartyLoadedSerial by remember { mutableStateOf<String?>(null) }
        var thirdPartyAutoRefreshAttemptedSerial by remember { mutableStateOf<String?>(null) }
        var systemLoadedSerial by remember { mutableStateOf<String?>(null) }
        var systemCacheCheckedSerial by remember { mutableStateOf<String?>(null) }
        var systemAutoRefreshAttemptedSerial by remember { mutableStateOf<String?>(null) }
        var isLoadingThirdParty by remember { mutableStateOf(false) }
        var isLoadingSystem by remember { mutableStateOf(false) }
        var systemAppsCacheFormattedTime by remember { mutableStateOf<String?>(null) }
        var thirdPartyProgressCurrent by remember { mutableStateOf(0) }
        var thirdPartyProgressTotal by remember { mutableStateOf(0) }
        var systemProgressCurrent by remember { mutableStateOf(0) }
        var systemProgressTotal by remember { mutableStateOf(0) }
        var applicationDetailPackageName by remember { mutableStateOf<String?>(null) }
        var applicationDetailSections by remember {
            mutableStateOf<Map<ApplicationDetailSection, ApplicationDetailContent>>(emptyMap())
        }
        var loadingApplicationDetailSection by remember { mutableStateOf<ApplicationDetailSection?>(null) }
        var deviceSystemInfo by remember { mutableStateOf<DeviceSystemInfo?>(null) }
        var systemProperties by remember { mutableStateOf<List<SystemProperty>>(emptyList()) }
        var isLoadingDeviceSystemInfo by remember { mutableStateOf(false) }
        var isLoadingDeviceProperties by remember { mutableStateOf(false) }
        var deviceSystemInfoLoadedSerial by remember { mutableStateOf<String?>(null) }
        var devicePropertiesLoadedSerial by remember { mutableStateOf<String?>(null) }
        val selectedDevice = devices.firstOrNull { it.serialNumber == selectedDeviceSerial }
        val selectedReadyDevice = selectedDevice?.takeIf { it.isReady }

        fun appendCommand(command: String) {
            val timePrefix = if (appSettings.showCommandTime) {
                "[${getCurrentTimeFormatted()}] "
            } else ""
            commandLog = (commandLog + "$timePrefix$command").takeLast(200)
        }

        fun appendStatus(message: String) {
            appendCommand(commandStatus(message))
        }

        fun appendError(message: String) {
            appendCommand(commandError(message))
        }

        fun noReadyDeviceMessage(): String = localized("auto.select_a_device_in_device_state_first.cf5bc374")

        val dataFillModule = rememberDataFillModuleController(
            scope = scope,
            getSelectedReadyDevice = {
                devices.firstOrNull { it.serialNumber == selectedDeviceSerial }?.takeIf { it.isReady }
            },
            isRunning = { isRunning },
            setRunning = { isRunning = it },
            setStatusText = { statusText = it },
            appendCommand = ::appendCommand,
        )
        val fileManagerModule = rememberFileManagerModuleController(
            scope = scope,
            getSelectedReadyDevice = {
                devices.firstOrNull { it.serialNumber == selectedDeviceSerial }?.takeIf { it.isReady }
            },
            getDefaultRootPath = { AppSettingsShared.currentSettings.fileManagerDefaultRootPath },
            isRunning = { isRunning },
            setRunning = { isRunning = it },
            setStatusText = { statusText = it },
            appendCommand = ::appendCommand,
        )
        val deviceLogModule = rememberDeviceLogModuleController(
            scope = scope,
            getSelectedReadyDevice = {
                devices.firstOrNull { it.serialNumber == selectedDeviceSerial }?.takeIf { it.isReady }
            },
            setStatusText = { statusText = it },
            appendCommand = ::appendCommand,
        )
        val isCapturingLogcat = deviceLogModule.isCapturing

        fun refreshDevices() {
            if (isRunning) return
            scope.launch {
                isRunning = true
                statusText = localized("auto.scanning_devices.36f421dd")
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
                    dataFillModule.clearDeviceState()
                    fileManagerModule.clearDeviceState()
                    thirdPartyApps = emptyList()
                    systemApps = emptyList()
                    thirdPartyLoadedSerial = null
                    thirdPartyAutoRefreshAttemptedSerial = null
                    systemLoadedSerial = null
                    systemCacheCheckedSerial = null
                    systemAutoRefreshAttemptedSerial = null
                    systemAppsCacheFormattedTime = null
                    thirdPartyProgressCurrent = 0
                    thirdPartyProgressTotal = 0
                    systemProgressCurrent = 0
                    systemProgressTotal = 0
                    applicationDetailPackageName = null
                    applicationDetailSections = emptyMap()
                    loadingApplicationDetailSection = null
                    deviceSystemInfo = null
                    systemProperties = emptyList()
                    deviceSystemInfoLoadedSerial = null
                    devicePropertiesLoadedSerial = null
                    isLoadingDeviceSystemInfo = false
                    isLoadingDeviceProperties = false
                    deviceLogModule.clearIfIdle()

                    if (nextSelectedDeviceSerial == null) {
                        statusText = if (discoveredDevices.isEmpty()) {
                            localized("auto.no_devices_found.0b4ef309")
                        } else {
                            localized("auto.found_0_devices_none_available.7cf498df", discoveredDevices.size)
                        }
                        appendStatus(statusText)
                    } else if (selectedTestModule == TestModule.DataFill) {
                        statusText = localized("auto.found_0_devices_reading_storage.bdbccf66", discoveredDevices.size)
                        try {
                            dataFillModule.loadStorageInfoForDeviceScan(nextSelectedDeviceSerial)
                            statusText = localized("auto.found_0_devices_storage_refreshed.ee664033", discoveredDevices.size)
                            appendStatus(localized("auto.device_connected_storage_refreshed.b9d926fe"))
                        } catch (error: Throwable) {
                            statusText = error.message ?: localized("auto.storage_read_failed.ea8fd918")
                            appendError(localized("auto.storage_read_failed.ea8fd918") + " - ${error.message ?: unknownError()}")
                        }
                    } else if (selectedTestModule == TestModule.Device) {
                        statusText = localized("auto.found_0_devices_reading_system_info.23c2e7a8", discoveredDevices.size)
                        try {
                            deviceSystemInfo = deviceAdb.loadSystemInfo(nextSelectedDeviceSerial, ::appendCommand)
                            systemProperties = deviceAdb.loadSystemProperties(nextSelectedDeviceSerial, ::appendCommand)
                            deviceSystemInfoLoadedSerial = nextSelectedDeviceSerial
                            devicePropertiesLoadedSerial = nextSelectedDeviceSerial
                            statusText = localized("auto.found_0_devices_system_info_refreshed.e01eb8d9", discoveredDevices.size)
                            appendStatus(localized("auto.device_connected_system_info_and_properties_refreshe.fcd4aa88"))
                        } catch (error: Throwable) {
                            statusText = error.message ?: localized("auto.system_info_read_failed.f415358a")
                            appendError(localized("auto.system_info_read_failed.f415358a") + " - ${error.message ?: unknownError()}")
                        }
                    } else if (selectedTestModule == TestModule.Log) {
                        statusText = localized("auto.found_0_devices_ready_to_capture_logcat.9eb6aaef", discoveredDevices.size)
                        appendStatus(statusText)
                    } else if (selectedTestModule == TestModule.FileManager) {
                        statusText = localized("auto.found_0_devices_reading_files.140270f5", discoveredDevices.size)
                        try {
                            fileManagerModule.loadRootForDeviceScan(nextSelectedDeviceSerial)
                            statusText = localized("auto.found_0_devices_files_loaded.877e8979", discoveredDevices.size)
                            appendStatus(localized("auto.device_connected_file_directory_refreshed.67d4b380"))
                        } catch (error: Throwable) {
                            statusText = error.message ?: localized("auto.file_read_failed.62cc5bec")
                            appendError(localized("auto.file_read_failed.62cc5bec") + " - ${error.message ?: unknownError()}")
                        }
                    } else {
                        statusText = localized("auto.found_0_devices_ready_to_read_apps.88e40384", discoveredDevices.size)
                        appendStatus(statusText)
                    }
                } catch (error: Throwable) {
                    statusText = error.message ?: localized("auto.device_scan_failed.6d2abdf4")
                    appendError(localized("auto.device_scan_failed.6d2abdf4") + " - ${error.message ?: unknownError()}")
                } finally {
                    isRunning = false
                }
            }
        }

        fun loadDeviceSystemInfo(deviceSerial: String) {
            if (isRunning) return
            scope.launch {
                isRunning = true
                isLoadingDeviceSystemInfo = true
                deviceSystemInfo = null
                deviceSystemInfoLoadedSerial = deviceSerial
                statusText = localized("auto.reading_device_system_info.eb9fdd51")
                appendStatus(localized("auto.reading_device_system_info.eb9fdd51"))
                try {
                    deviceSystemInfo = deviceAdb.loadSystemInfo(deviceSerial, ::appendCommand)
                    statusText = localized("auto.device_system_info_loaded.9c9737ed")
                    appendStatus(statusText)
                } catch (error: Throwable) {
                    deviceSystemInfoLoadedSerial = null
                    statusText = error.message ?: localized("auto.device_system_info_read_failed.fbb280e1")
                    appendError(localized("auto.device_system_info_read_failed.fbb280e1") + " - ${error.message ?: unknownError()}")
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
                statusText = localized("auto.reading_system_properties.a66e52e0")
                appendStatus(localized("auto.reading_system_properties.a66e52e0"))
                try {
                    systemProperties = deviceAdb.loadSystemProperties(deviceSerial, ::appendCommand)
                    statusText = localized("auto.system_properties_loaded_0_properties.06d5f95e", systemProperties.size)
                    appendStatus(statusText)
                } catch (error: Throwable) {
                    devicePropertiesLoadedSerial = null
                    statusText = error.message ?: localized("auto.system_properties_read_failed.54ef3499")
                    appendError(localized("auto.system_properties_read_failed.54ef3499") + " - ${error.message ?: unknownError()}")
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
                statusText = localized("auto.rebooting_device.e7db9aff")
                appendStatus(localized("auto.start_rebooting_device_0.2d49f3a3", deviceSerial))
                try {
                    deviceAdb.rebootDevice(deviceSerial, ::appendCommand)
                    statusText = localized("auto.reboot_command_sent.0c3ed4f8")
                    appendStatus(localized("auto.reboot_command_sent_device_will_reboot.1c2dfbc4"))
                    selectedDeviceSerial = null
                    deviceSystemInfo = null
                    systemProperties = emptyList()
                } catch (error: Throwable) {
                    statusText = error.message ?: localized("auto.device_reboot_failed.87f106a4")
                    appendError(localized("auto.device_reboot_failed.87f106a4") + " - ${error.message ?: unknownError()}")
                } finally {
                    isRunning = false
                }
            }
        }

        fun runSelectedDeviceQuickAction(deviceSerial: String, action: DeviceQuickAction) {
            if (isRunning) return
            scope.launch {
                isRunning = true
                val actionLabel = action.displayLabel()
                statusText = localized("auto.running_0.c6a590d5", actionLabel)
                appendStatus(localized("auto.start_0_on_1.4da0d1f5", actionLabel, deviceSerial))
                try {
                    deviceAdb.runQuickAction(deviceSerial, action, ::appendCommand)
                    statusText = localized("auto.0_command_sent.8a227ddb", actionLabel)
                    appendStatus(localized("auto.0_command_sent.9cbe1344", actionLabel))
                    if (action == DeviceQuickAction.SHUTDOWN ||
                        action == DeviceQuickAction.REBOOT_RECOVERY ||
                        action == DeviceQuickAction.REBOOT_FASTBOOT
                    ) {
                        selectedDeviceSerial = null
                        deviceSystemInfo = null
                        systemProperties = emptyList()
                    }
                } catch (error: Throwable) {
                    statusText = error.message ?: localized("auto.0_failed.7a359370", actionLabel)
                    appendError(localized("auto.0_failed.7a359370", actionLabel) + " - ${error.message ?: unknownError()}")
                } finally {
                    isRunning = false
                }
            }
        }

        fun takeSelectedDeviceScreenshot(deviceSerial: String) {
            if (isRunning) return
            scope.launch {
                isRunning = true
                statusText = localized("auto.select_screenshot_save_directory.834722ee")
                appendStatus(localized("auto.select_screenshot_save_directory.86e4055a"))
                try {
                    val outputPath = selectDirectory(
                        dialogTitle = localized("auto.select_screenshot_save_path.7fedf467"),
                        approveButtonText = localized("auto.save.429a21f2"),
                    )
                    if (outputPath == null) {
                        statusText = localized("auto.screenshot_cancelled.1b9f33e9")
                        appendStatus(statusText)
                    } else {
                        statusText = localized("auto.capturing_device_screen.55bda553")
                        appendStatus(localized("auto.start_screenshot_on_device_0_save_to_1.3eea5e54", deviceSerial, outputPath))
                        val result = deviceAdb.takeScreenshot(deviceSerial, outputPath, ::appendCommand)
                        statusText = localized("auto.screenshot_saved_to_0.be22fabc", result.localPath)
                        appendStatus(localized("auto.screenshot_succeeded_local_file_0_device_file_1.f844cafe", result.localPath, result.remotePath))
                    }
                } catch (error: Throwable) {
                    statusText = error.message ?: localized("auto.screenshot_failed.564fd41a")
                    appendError(localized("auto.screenshot_failed.564fd41a") + " - ${error.message ?: unknownError()}")
                } finally {
                    isRunning = false
                }
            }
        }

        fun installSelectedApplications(deviceSerial: String) {
            if (isRunning) return
            scope.launch {
                isRunning = true
                statusText = localized("auto.select_apk_files_to_install.34a69122")
                appendStatus(localized("auto.select_apk_files_to_install.c4350f36"))
                try {
                    val apkPaths = selectApkFiles(
                        dialogTitle = localized("auto.select_apk_files_to_install.c4350f36"),
                        approveButtonText = localized("auto.install.e1c753b2"),
                    )
                    if (apkPaths.isEmpty()) {
                        statusText = localized("auto.app_installation_cancelled.b67c3019")
                        appendStatus(statusText)
                    } else {
                        statusText = localized("auto.installing_0_apps.ab91b200", apkPaths.size)
                        appendStatus(localized("auto.start_installing_0_apk_files_to_device_1.846939de", apkPaths.size, deviceSerial))
                        val results = deviceAdb.installApplications(deviceSerial, apkPaths, ::appendCommand)
                        val successCount = results.count { it.success }
                        val failureResults = results.filterNot { it.success }

                        results.forEach { result ->
                            if (result.success) {
                                appendStatus(localized("auto.install_succeeded.c2bba9b2") + " - ${result.fileName}")
                            } else {
                                appendError(localized("auto.install_failed.41922c40") + " - ${result.fileName} - ${result.message}")
                            }
                        }

                        if (successCount > 0) {
                            thirdPartyApps = emptyList()
                            thirdPartyLoadedSerial = null
                            thirdPartyAutoRefreshAttemptedSerial = null
                            systemApps = emptyList()
                            systemLoadedSerial = null
                            systemCacheCheckedSerial = deviceSerial
                            systemAutoRefreshAttemptedSerial = null
                            systemAppsCacheFormattedTime = null
                            applicationDetailPackageName = null
                            applicationDetailSections = emptyMap()
                            loadingApplicationDetailSection = null
                        }

                        statusText = if (failureResults.isEmpty()) {
                            localized("auto.app_installation_complete_succeeded_0_1.ab878451", successCount, results.size)
                        } else {
                            localized("auto.app_installation_complete_succeeded_0_1_failed_2.06a49e90", successCount, results.size, failureResults.size)
                        }
                        appendStatus(statusText)
                    }
                } catch (error: CancellationException) {
                    statusText = localized("auto.app_installation_stopped.12d501d2")
                    appendStatus(statusText)
                } catch (error: Throwable) {
                    statusText = error.message ?: localized("auto.app_installation_failed.21882c2a")
                    appendError(localized("auto.app_installation_failed.21882c2a") + " - ${error.message ?: unknownError()}")
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
                applicationDetailPackageName = null
                applicationDetailSections = emptyMap()
                loadingApplicationDetailSection = null
                statusText = localized("auto.reading_third_party_apps.4e8f83ca")
                appendStatus(localized("auto.reading_third_party_apps.4e8f83ca"))
                try {
                    thirdPartyApps = appAdb.loadInstalledApps(deviceSerial, false, ::appendCommand) { current, total ->
                        thirdPartyProgressCurrent = current
                        thirdPartyProgressTotal = total
                        statusText = localized("auto.reading_third_party_apps_0_1.0485f183", current, total)
                    }
                    val disabledCount = thirdPartyApps.count { !it.isEnabled }
                    statusText = localized("auto.loaded_0_third_party_apps_1_disabled.3fb14c9c", thirdPartyApps.size, disabledCount)
                    appendStatus(statusText)
                } catch (error: Throwable) {
                    thirdPartyLoadedSerial = null
                    statusText = error.message ?: localized("auto.third_party_app_read_failed.70eaad85")
                    appendError(localized("auto.third_party_app_read_failed.70eaad85") + " - ${error.message ?: unknownError()}")
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
                applicationDetailPackageName = null
                applicationDetailSections = emptyMap()
                loadingApplicationDetailSection = null
                statusText = localized("auto.reading_system_apps.584fc54c")
                appendStatus(localized("auto.reading_system_apps.584fc54c"))
                try {
                    val apps = appAdb.loadInstalledApps(deviceSerial, true, ::appendCommand) { current, total ->
                        systemProgressCurrent = current
                        systemProgressTotal = total
                        statusText = localized("auto.reading_system_apps_0_1.cc0c89b3", current, total)
                    }
                    systemApps = apps
                    val disabledCount = apps.count { !it.isEnabled }
                    statusText = localized("auto.loaded_0_system_apps_1_disabled.984c8678", apps.size, disabledCount)
                    appendStatus(statusText)
                    appAdb.saveCachedSystemApps(deviceSerial, apps)
                    val cached = appAdb.loadCachedSystemApps(deviceSerial)
                    if (cached != null) {
                        systemAppsCacheFormattedTime = cached.cacheTimeFormatted
                    }
                    systemCacheCheckedSerial = deviceSerial
                } catch (error: Throwable) {
                    systemLoadedSerial = null
                    statusText = error.message ?: localized("auto.system_app_read_failed.2d2dd351")
                    appendError(localized("auto.system_app_read_failed.2d2dd351") + " - ${error.message ?: unknownError()}")
                } finally {
                    isLoadingSystem = false
                    isRunning = false
                }
            }
        }

        fun clearApplicationListCache() {
            if (isRunning) return
            val deviceSerial = selectedReadyDevice?.serialNumber
            if (deviceSerial == null) {
                statusText = noReadyDeviceMessage()
                appendError(statusText)
                return
            }

            scope.launch {
                isRunning = true
                statusText = localized("auto.clearing_app_list_cache.8fe5f820")
                appendStatus(localized("auto.clearing_app_list_cache.8fe5f820"))
                try {
                    appAdb.clearApplicationListCache(deviceSerial)
                    thirdPartyApps = emptyList()
                    systemApps = emptyList()
                    thirdPartyLoadedSerial = null
                    systemLoadedSerial = null
                    systemCacheCheckedSerial = null
                    systemAutoRefreshAttemptedSerial = null
                    systemAppsCacheFormattedTime = null
                    thirdPartyProgressCurrent = 0
                    thirdPartyProgressTotal = 0
                    systemProgressCurrent = 0
                    systemProgressTotal = 0
                    applicationDetailPackageName = null
                    applicationDetailSections = emptyMap()
                    loadingApplicationDetailSection = null
                    statusText = localized("auto.app_list_cache_cleared.932fc598")
                    appendStatus(statusText)
                } catch (error: Throwable) {
                    statusText = error.message ?: localized("auto.app_list_cache_clear_failed.4583079a")
                    appendError(localized("auto.app_list_cache_clear_failed.4583079a") + " - ${error.message ?: unknownError()}")
                } finally {
                    isRunning = false
                }
            }
        }

        fun loadApplicationDetail(app: InstalledAppInfo, section: ApplicationDetailSection) {
            if (isRunning) return
            val deviceSerial = selectedReadyDevice?.serialNumber
            if (deviceSerial == null) {
                statusText = noReadyDeviceMessage()
                appendError(statusText)
                return
            }

            scope.launch {
                isRunning = true
                if (applicationDetailPackageName != app.packageName) {
                    applicationDetailPackageName = app.packageName
                    applicationDetailSections = emptyMap()
                }
                loadingApplicationDetailSection = section
                val sectionTitle = section.displayTitle()
                statusText = localized("auto.reading_0_1_info.b2c00628", app.packageName, sectionTitle)
                appendStatus(statusText)
                try {
                    val content = appAdb.loadApplicationDetail(
                        deviceSerial = deviceSerial,
                        app = app,
                        section = section,
                        logCommand = ::appendCommand,
                    )
                    if (applicationDetailPackageName == app.packageName) {
                        applicationDetailSections = applicationDetailSections + (section to content)
                    }
                    statusText = localized("auto.read_0_1_info.91137c16", app.packageName, sectionTitle)
                    appendStatus(statusText)
                } catch (error: Throwable) {
                    val message = error.message ?: localized("auto.app_detail_read_failed.ae413164")
                    if (applicationDetailPackageName == app.packageName) {
                        applicationDetailSections = applicationDetailSections + (
                            section to ApplicationDetailContent(
                                section = section,
                                source = ApplicationDetailSource.ADB,
                                items = listOf(
                                    ApplicationDetailItem(
                                        label = localized("auto.error.5966c2d3"),
                                        value = message,
                                    ),
                                ),
                            )
                        )
                    }
                    statusText = message
                    appendError(localized("auto.read_0_1_info_failed.eda8a8a6", app.packageName, sectionTitle) + " - $message")
                } finally {
                    loadingApplicationDetailSection = null
                    isRunning = false
                }
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
            if (applicationDetailPackageName == packageName) {
                applicationDetailSections = applicationDetailSections - ApplicationDetailSection.BASIC
            }
            return nextSystemApps
        }

        fun removeApplicationFromLists(packageName: String): List<InstalledAppInfo> {
            thirdPartyApps = removeInstalledApp(thirdPartyApps, packageName)
            val nextSystemApps = removeInstalledApp(systemApps, packageName)
            systemApps = nextSystemApps
            if (applicationDetailPackageName == packageName) {
                applicationDetailPackageName = null
                applicationDetailSections = emptyMap()
                loadingApplicationDetailSection = null
            }
            return nextSystemApps
        }

        fun runApplicationAction(app: InstalledAppInfo, action: String) {
            if (isRunning) return
            val deviceSerial = selectedReadyDevice?.serialNumber
            if (deviceSerial == null) {
                statusText = noReadyDeviceMessage()
                appendError(statusText)
                return
            }

            scope.launch {
                isRunning = true
                val actionLabel = applicationActionLabel(action)
                statusText = localized("auto.0_1.c181f623", actionLabel, app.packageName)
                appendStatus(localized("auto.start_0_1.d7d85e25", actionLabel, app.packageName))
                try {
                    when (action) {
                        ApplicationAction.LAUNCH -> {
                            appAdb.launchApplication(deviceSerial, app.packageName, ::appendCommand)
                            statusText = localized("auto.launched_0.f1477d6a", app.packageName)
                            appendStatus(statusText)
                        }
                        ApplicationAction.STOP -> {
                            appAdb.stopApplication(deviceSerial, app.packageName, ::appendCommand)
                            statusText = localized("auto.force_stopped_0.68623e10", app.packageName)
                            appendStatus(statusText)
                        }
                        ApplicationAction.CLEAR_DATA -> {
                            appAdb.clearApplicationData(deviceSerial, app.packageName, ::appendCommand)
                            statusText = localized("auto.cleared_data_for_0.e6b79858", app.packageName)
                            appendStatus(statusText)
                        }
                        ApplicationAction.DISABLE -> {
                            appAdb.disableApplication(deviceSerial, app.packageName, ::appendCommand)
                            val nextSystemApps = updateApplicationEnabledState(app.packageName, false)
                            if (app.isSystem) {
                                appAdb.saveCachedSystemApps(deviceSerial, nextSystemApps)
                            }
                            statusText = localized("auto.disabled_0.0dbf6a84", app.packageName)
                            appendStatus(statusText)
                        }
                        ApplicationAction.ENABLE -> {
                            appAdb.enableApplication(deviceSerial, app.packageName, ::appendCommand)
                            val nextSystemApps = updateApplicationEnabledState(app.packageName, true)
                            if (app.isSystem) {
                                appAdb.saveCachedSystemApps(deviceSerial, nextSystemApps)
                            }
                            statusText = localized("auto.enabled_0.f1167c7c", app.packageName)
                            appendStatus(statusText)
                        }
                        ApplicationAction.UNINSTALL -> {
                            appAdb.uninstallApplication(deviceSerial, app.packageName, app.isSystem, ::appendCommand)
                            val nextSystemApps = removeApplicationFromLists(app.packageName)
                            if (app.isSystem) {
                                appAdb.saveCachedSystemApps(deviceSerial, nextSystemApps)
                                val cached = appAdb.loadCachedSystemApps(deviceSerial)
                                systemAppsCacheFormattedTime = cached?.cacheTimeFormatted
                            }
                            statusText = localized("auto.uninstalled_0.b26c29fe", app.packageName)
                            appendStatus(statusText)
                        }
                        ApplicationAction.EXPORT_APK -> {
                            val outputPath = selectDirectory(
                                dialogTitle = localized("auto.select_apk_export_path.19b08cbd"),
                                approveButtonText = localized("auto.export.5a8c8fe7"),
                            )
                            if (outputPath == null) {
                                statusText = localized("auto.export_cancelled.658ddbf7")
                                appendStatus(statusText)
                            } else {
                                val result = appAdb.exportApplicationApk(deviceSerial, app.packageName, outputPath, ::appendCommand)
                                statusText = localized("auto.exported_0_apk_files_to_1.933e41a3", result.fileCount, result.directoryPath)
                                appendStatus(statusText)
                            }
                        }
                        ApplicationAction.SAVE_ICON -> {
                            val iconBytes = app.iconBytes
                            if (iconBytes == null) {
                                statusText = localized("auto.cannot_save_app_has_no_icon_data.978f32e2")
                                appendError(statusText)
                            } else {
                                val outputPath = selectDirectory(
                                    dialogTitle = localized("auto.select_icon_save_path.72013e8c"),
                                    approveButtonText = localized("auto.save.429a21f2"),
                                )
                                if (outputPath == null) {
                                    statusText = localized("auto.icon_save_cancelled.46f65426")
                                    appendStatus(statusText)
                                } else {
                                    val safeAppName = app.appName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                                    val fileName = "${safeAppName}_${app.packageName}.png"
                                    try {
                                        saveBytesToFile(outputPath, fileName, iconBytes)
                                        statusText = localized("auto.icon_saved_to_0_1.7ffae884", outputPath, fileName)
                                        appendStatus(statusText)
                                    } catch (e: Exception) {
                                        statusText = localized("auto.icon_save_failed_0.3f0fbb2a", e.message)
                                        appendError(statusText)
                                    }
                                }
                            }
                        }
                        else -> {
                            statusText = localized("auto.unknown_app_action_0.2a004304", action)
                            appendError(statusText)
                        }
                    }
                } catch (error: CancellationException) {
                    statusText = localized("auto.app_operation_stopped.2fddacbf")
                    appendStatus(statusText)
                } catch (error: Throwable) {
                    statusText = error.message ?: localized("auto.0_failed.7a359370", actionLabel)
                    appendError(localized("auto.0_failed.7a359370", actionLabel) + " - ${error.message ?: unknownError()}")
                } finally {
                    isRunning = false
                }
            }
        }

        LaunchedEffect(selectedReadyDevice?.serialNumber) {
            val deviceSerial = selectedReadyDevice?.serialNumber
            if (deviceSerial != null) {
                if (localApkBytes == null || localApkVersionInfo == null) {
                    try {
                        val bytes = appAdb.getLocalPluginApkBytes()
                        if (bytes != null) {
                            localApkBytes = bytes
                            localApkVersionInfo = appAdb.getApkVersionInfo(bytes)
                        } else {
                            appendError(localized("auto.athplugin_apk_file_was_not_found_under_resources_fil.fb27afb1"))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        appendError(localized("auto.failed_to_load_athplugin_apk_file_under_resources_fi.006f4dc8") + ": ${e.message}")
                    }
                }

                val targetLocalVersionInfo = localApkVersionInfo
                if (targetLocalVersionInfo != null) {
                    val ignoredVersion = appAdb.getIgnoredPluginCheckVersion()
                    if (ignoredVersion == targetLocalVersionInfo.pluginCheckIgnoreKey()) {
                        showPluginBanner = false
                        return@LaunchedEffect
                    }

                    try {
                        val installedVersionInfo = appAdb.getInstalledPluginVersionInfo(deviceSerial, ::appendCommand)
                        if (installedVersionInfo == null) {
                            bannerMessage = localized("auto.athplugin_is_not_installed_on_this_device_installing.4986bdb1")
                            bannerActionType = BannerActionType.INSTALL
                            showPluginBanner = true
                        } else {
                            val isPluginEnabled = appAdb.isPluginEnabled(deviceSerial, ::appendCommand)
                            if (!isPluginEnabled) {
                                bannerMessage = localized("auto.athplugin_is_disabled_app_info_loading_may_take_long.55fe0abb")
                                bannerActionType = BannerActionType.ENABLE
                                showPluginBanner = true
                            } else if (installedVersionInfo.versionCode < targetLocalVersionInfo.versionCode) {
                                bannerMessage = localized("auto.installed_athplugin_is_outdated_current_0_latest_1_u.9a46c154", installedVersionInfo.versionName, targetLocalVersionInfo.versionName)
                                bannerActionType = BannerActionType.UPDATE
                                showPluginBanner = true
                            } else {
                                showPluginBanner = false
                            }
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

        LaunchedEffect(
            selectedTestModule,
            selectedReadyDevice?.serialNumber,
            appSettings.fileManagerDefaultRootPath,
        ) {
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
                        systemCacheCheckedSerial = deviceSerial
                    }
                    if (thirdPartyLoadedSerial != deviceSerial) {
                        thirdPartyApps = emptyList()
                        thirdPartyLoadedSerial = null
                    }
                } else if (selectedTestModule == TestModule.FileManager) {
                    fileManagerModule.applyDefaultRootPath()
                    if (fileManagerModule.loadedSerial != deviceSerial) {
                        fileManagerModule.loadDirectory(deviceSerial, fileManagerModule.appliedRootPath)
                    }
                }
            }
        }

        LaunchedEffect(
            selectedTestModule,
            selectedReadyDevice?.serialNumber,
            isRunning,
            systemCacheCheckedSerial,
            systemAutoRefreshAttemptedSerial,
        ) {
            val deviceSerial = selectedReadyDevice?.serialNumber
            if (selectedTestModule == TestModule.App &&
                deviceSerial != null &&
                !isRunning &&
                systemCacheCheckedSerial == deviceSerial &&
                systemLoadedSerial != deviceSerial &&
                systemAutoRefreshAttemptedSerial != deviceSerial
            ) {
                systemAutoRefreshAttemptedSerial = deviceSerial
                loadSystemApps(deviceSerial)
            }
        }

        LaunchedEffect(
            selectedTestModule,
            selectedReadyDevice?.serialNumber,
            isRunning,
            thirdPartyAutoRefreshAttemptedSerial,
        ) {
            val deviceSerial = selectedReadyDevice?.serialNumber
            if (selectedTestModule == TestModule.App &&
                deviceSerial != null &&
                !isRunning &&
                thirdPartyLoadedSerial != deviceSerial &&
                thirdPartyAutoRefreshAttemptedSerial != deviceSerial
            ) {
                thirdPartyAutoRefreshAttemptedSerial = deviceSerial
                loadThirdPartyApps(deviceSerial)
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeContentPadding(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                if (showPluginBanner && !isBannerDismissedThisSession) {
                    val actionLabel = when (bannerActionType) {
                        BannerActionType.INSTALL -> localized("auto.install_now.705c59ae")
                        BannerActionType.UPDATE -> localized("auto.update_now.9250be6a")
                        BannerActionType.ENABLE -> localized("auto.enable_now.4e53e542")
                    }
                    PluginCheckBanner(
                        message = bannerMessage,
                        actionLabel = actionLabel,
                        onAction = {
                            val serial = selectedReadyDevice?.serialNumber
                            if (serial != null) {
                                if (bannerActionType == BannerActionType.ENABLE) {
                                    if (!isEnablingPlugin) {
                                        isEnablingPlugin = true
                                        scope.launch {
                                            statusText = localized("auto.enabling_helper_plugin.39bc8397")
                                            appendStatus(localized("auto.start_enabling_helper_plugin_on_device_0.1e5c6c92", serial))
                                            try {
                                                appAdb.enableApplication(serial, "com.floatingmuseum.android.test.helper.plugin", ::appendCommand)
                                                // 延迟 500ms，等待系统状态更新
                                                kotlinx.coroutines.delay(500)
                                                val isEnabled = appAdb.isPluginEnabled(serial, ::appendCommand)
                                                if (isEnabled) {
                                                    statusText = localized("auto.helper_plugin_enabled.fefddac5")
                                                    appendStatus(localized("auto.helper_plugin_enabled_on_device_0.2c0d5229", serial))
                                                } else {
                                                    statusText = localized("auto.automatic_enable_failed_manual_enable_is_recommended.572c217c")
                                                    appendError(localized("auto.helper_plugin_enable_command_finished_on_device_0_bu.2f99c577", serial))
                                                }
                                            } catch (e: Exception) {
                                                statusText = localized("auto.automatic_enable_failed_manual_enable_is_recommended.572c217c")
                                                appendError(localized("auto.failed_to_enable_helper_plugin_on_device_0.9e500036", serial) + " - ${e.message}")
                                            } finally {
                                                isEnablingPlugin = false
                                                showPluginBanner = false // 无论成功与否，横幅直接隐藏，避免常驻
                                            }
                                        }
                                    }
                                } else {
                                    val bytes = localApkBytes
                                    if (bytes != null && !isInstallingPlugin) {
                                        isInstallingPlugin = true
                                        scope.launch {
                                            statusText = localized("auto.installing_helper_plugin_on_device_0.60df6f21", serial)
                                            appendStatus(localized("auto.start_installing_helper_plugin_on_device_0.4039eff2", serial))
                                            val success = appAdb.installPluginApk(serial, bytes, ::appendCommand)
                                            if (success) {
                                                statusText = localized("auto.helper_plugin_installed.3583e339")
                                                appendStatus(localized("auto.helper_plugin_installed_on_device_0.2e221c2f", serial))
                                                showPluginBanner = false
                                            } else {
                                                statusText = localized("auto.helper_plugin_installation_failed_check_the_connecti.5cb911b9")
                                                appendError(localized("auto.helper_plugin_installation_failed_on_device_0.7f843529", serial))
                                            }
                                            isInstallingPlugin = false
                                        }
                                    }
                                }
                            }
                        },
                        onIgnore = {
                            localApkVersionInfo?.let { versionInfo ->
                                appAdb.saveIgnoredPluginCheckVersion(versionInfo.pluginCheckIgnoreKey())
                            }
                            showPluginBanner = false
                        },
                        onDismiss = {
                            isBannerDismissedThisSession = true
                        },
                        isProcessing = isInstallingPlugin || isEnablingPlugin
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
                                                statusText = localized("auto.running_battery_simulation.8f955faa")
                                                appendStatus(localized("auto.run_battery_simulation_adb_s_0_shell_dumpsys_battery.38cbdda7", serial, args.joinToString(" ")))
                                                try {
                                                    deviceAdb.controlBattery(serial, args, ::appendCommand)
                                                    statusText = localized("auto.battery_simulation_executed.40b8397b")
                                                    appendStatus(statusText)
                                                    deviceSystemInfo = deviceAdb.loadSystemInfo(serial, ::appendCommand)
                                                } catch (error: Throwable) {
                                                    statusText = error.message ?: localized("auto.battery_simulation_failed.5cd705c3")
                                                    appendError(localized("auto.battery_simulation_failed.5cd705c3") + " - ${error.message ?: unknownError()}")
                                                } finally {
                                                    isRunning = false
                                                }
                                            }
                                        }
                                    },
                                    onScreenSizeControl = { size ->
                                        selectedReadyDevice?.serialNumber?.let { serial ->
                                            scope.launch {
                                                isRunning = true
                                                statusText = localized("auto.changing_screen_resolution.4c4fab34")
                                                appendStatus(localized("auto.change_screen_resolution_adb_s_0_shell_wm_size_1.ebafdbdd", serial, size))
                                                try {
                                                    deviceAdb.modifyScreenSize(serial, size, ::appendCommand)
                                                    statusText = localized("auto.screen_resolution_change_executed.8ddc0dbd")
                                                    appendStatus(statusText)
                                                    deviceSystemInfo = deviceAdb.loadSystemInfo(serial, ::appendCommand)
                                                } catch (error: Throwable) {
                                                    statusText = error.message ?: localized("auto.screen_resolution_change_failed.cbc576b4")
                                                    appendError(localized("auto.screen_resolution_change_failed.cbc576b4") + " - ${error.message ?: unknownError()}")
                                                } finally {
                                                    isRunning = false
                                                }
                                            }
                                        }
                                    },
                                    onScreenDensityControl = { density ->
                                        selectedReadyDevice?.serialNumber?.let { serial ->
                                            scope.launch {
                                                isRunning = true
                                                statusText = localized("auto.changing_screen_density.5afcf930")
                                                appendStatus(localized("auto.change_screen_density_adb_s_0_shell_wm_density_1.3504afeb", serial, density))
                                                try {
                                                    deviceAdb.modifyScreenDensity(serial, density, ::appendCommand)
                                                    statusText = localized("auto.screen_density_change_executed.6969aefb")
                                                    appendStatus(statusText)
                                                    deviceSystemInfo = deviceAdb.loadSystemInfo(serial, ::appendCommand)
                                                } catch (error: Throwable) {
                                                    statusText = error.message ?: localized("auto.screen_density_change_failed.b0e11d20")
                                                    appendError(localized("auto.screen_density_change_failed.b0e11d20") + " - ${error.message ?: unknownError()}")
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
                                DataFillModuleContent(
                                    controller = dataFillModule,
                                    isRunning = isRunning,
                                    hasReadyDevice = selectedReadyDevice != null,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            TestModule.FileManager -> {
                                FileManagerModuleContent(
                                    controller = fileManagerModule,
                                    selectedDevice = selectedDevice,
                                    isRunning = isRunning,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            TestModule.Log -> {
                                DeviceLogPanel(
                                    selectedDevice = selectedDevice,
                                    isRunning = isCapturingLogcat,
                                    progress = deviceLogModule.progress,
                                    lastResult = deviceLogModule.lastResult,
                                    onCaptureLogs = deviceLogModule::captureSelectedDeviceLogs,
                                    onStopCapture = deviceLogModule::stopCapture,
                                    onRevealLogFile = deviceLogModule::revealLogFile,
                                    modifier = Modifier.fillMaxSize(),
                                )
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
                                    onClearCache = ::clearApplicationListCache,
                                    onApplicationAction = ::runApplicationAction,
                                    applicationDetailPackageName = applicationDetailPackageName,
                                    applicationDetailSections = applicationDetailSections,
                                    loadingApplicationDetailSection = loadingApplicationDetailSection,
                                    onLoadApplicationDetail = ::loadApplicationDetail,
                                    isRunning = isRunning,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            TestModule.Settings -> {
                                SettingsModuleContent(
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
                                    dataFillModule.clearDeviceState()
                                    fileManagerModule.clearDeviceState()
                                    thirdPartyApps = emptyList()
                                    systemApps = emptyList()
                                    thirdPartyLoadedSerial = null
                                    thirdPartyAutoRefreshAttemptedSerial = null
                                    systemLoadedSerial = null
                                    systemCacheCheckedSerial = null
                                    systemAutoRefreshAttemptedSerial = null
                                    systemAppsCacheFormattedTime = null
                                    thirdPartyProgressCurrent = 0
                                    thirdPartyProgressTotal = 0
                                    systemProgressCurrent = 0
                                    systemProgressTotal = 0
                                    applicationDetailPackageName = null
                                    applicationDetailSections = emptyMap()
                                    loadingApplicationDetailSection = null

                                    deviceSystemInfo = null
                                    systemProperties = emptyList()
                                    deviceSystemInfoLoadedSerial = null
                                    devicePropertiesLoadedSerial = null
                                    deviceLogModule.clearIfIdle()

                                    if (device.isReady) {
                                        if (selectedTestModule == TestModule.DataFill) {
                                            dataFillModule.refreshStorageForDevice(device.serialNumber)
                                        } else if (selectedTestModule == TestModule.FileManager) {
                                            fileManagerModule.loadDirectory(
                                                deviceSerial = device.serialNumber,
                                                remotePath = fileManagerModule.appliedRootPath,
                                            )
                                        } else if (selectedTestModule == TestModule.Device) {
                                            loadDeviceSystemInfo(device.serialNumber)
                                            loadDeviceSystemProperties(device.serialNumber)
                                        } else if (selectedTestModule == TestModule.Log) {
                                            statusText = localized("auto.selected_device_0_ready_to_capture_logcat.311bdd3c", device.serialNumber)
                                        } else {
                                            statusText = localized("auto.selected_device_0.8087625d", device.serialNumber)
                                        }
                                    } else {
                                        statusText = localized("auto.device_unavailable_0.2717ccc4", device.state)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )

                            CommandLogPanel(
                                commandLog = commandLog,
                                onClearCommandLog = {
                                    commandLog = emptyList()
                                },
                                modifier = Modifier.weight(2f),
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                }
                if (isCapturingLogcat) {
                    LogCaptureFloatingButton(
                        deviceLabel = deviceLogModule.capturingDeviceLabel ?: localized("auto.capturing_logcat.fbada554"),
                        onStop = deviceLogModule::stopCapture,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 88.dp, end = 40.dp),
                    )
                }
            }
        }
    }
}
