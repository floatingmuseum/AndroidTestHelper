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
import androidx.compose.runtime.DisposableEffect
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
import com.floatingmuseum.android.test.helper.app.ApplicationTestPanelState
import com.floatingmuseum.android.test.helper.app.PluginCheckBanner
import com.floatingmuseum.android.test.helper.app.applicationActionLabel
import com.floatingmuseum.android.test.helper.app.createAppAdb
import com.floatingmuseum.android.test.helper.app.displayTitle
import com.floatingmuseum.android.test.helper.app.pluginCheckIgnoreKey
import com.floatingmuseum.android.test.helper.app.removeInstalledApp
import com.floatingmuseum.android.test.helper.app.toDisplayDetailItems
import com.floatingmuseum.android.test.helper.device.DeviceSystemInfo
import com.floatingmuseum.android.test.helper.device.DeviceQuickAction
import com.floatingmuseum.android.test.helper.device.DeviceMirrorEndState
import com.floatingmuseum.android.test.helper.device.ScreenRecordEndState
import com.floatingmuseum.android.test.helper.device.ScreenRecordResult
import com.floatingmuseum.android.test.helper.device.ScreenRecordFloatingButton
import com.floatingmuseum.android.test.helper.device.SystemProperty
import com.floatingmuseum.android.test.helper.device.createDeviceAdb
import com.floatingmuseum.android.test.helper.device.DeviceTestPanel
import com.floatingmuseum.android.test.helper.device.DeviceTestPanelState
import com.floatingmuseum.android.test.helper.device.displayLabel
import com.floatingmuseum.android.test.helper.devicelog.DeviceLogPanel
import com.floatingmuseum.android.test.helper.devicelog.LogCaptureFloatingButton
import com.floatingmuseum.android.test.helper.devicelog.rememberDeviceLogModuleController
import com.floatingmuseum.android.test.helper.filemanager.FileManagerModuleContent
import com.floatingmuseum.android.test.helper.filemanager.rememberFileManagerModuleController
import com.floatingmuseum.android.test.helper.intent.IntentExecutionResult
import com.floatingmuseum.android.test.helper.intent.IntentCommandMode
import com.floatingmuseum.android.test.helper.intent.IntentTemplate
import com.floatingmuseum.android.test.helper.intent.IntentTestForm
import com.floatingmuseum.android.test.helper.intent.IntentTestPanel
import com.floatingmuseum.android.test.helper.intent.buildIntentAdbCommand
import com.floatingmuseum.android.test.helper.intent.createIntentAdb
import com.floatingmuseum.android.test.helper.intent.createIntentTemplateRepository
import com.floatingmuseum.android.test.helper.intent.validateIntentForm
import com.floatingmuseum.android.test.helper.localization.commandError
import com.floatingmuseum.android.test.helper.localization.commandStatus
import com.floatingmuseum.android.test.helper.localization.localized
import com.floatingmuseum.android.test.helper.localization.unknownError
import com.floatingmuseum.android.test.helper.settings.AppSettingsShared
import com.floatingmuseum.android.test.helper.settings.SettingsModuleContent
import com.floatingmuseum.android.test.helper.settings.SettingsModuleState
import com.floatingmuseum.android.test.helper.getCurrentTimeFormatted
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class BannerActionType {
    INSTALL,
    UPDATE,
    ENABLE
}

internal fun extractAdbDeviceNotFoundTransport(message: String): String? {
    return Regex("""device '([^']+)' not found""")
        .find(message)
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf { it.isNotBlank() }
}

private fun buildManifestExportFileName(packageName: String): String {
    val safePackageName = packageName
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .ifBlank { "application" }
    return "${safePackageName}_AndroidManifest.xml"
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
        val intentAdb = remember { createIntentAdb() }
        val intentTemplateRepository = remember { createIntentTemplateRepository() }
        val appSettings = AppSettingsShared.currentSettings

        val scope = rememberCoroutineScope()
        var devices by remember { mutableStateOf<List<AndroidDevice>>(emptyList()) }
        var selectedDeviceTransportId by remember { mutableStateOf<String?>(null) }

        var showPluginBanner by remember { mutableStateOf(false) }
        var bannerMessage by remember { mutableStateOf("") }
        var isInstallingPlugin by remember { mutableStateOf(false) }
        var isEnablingPlugin by remember { mutableStateOf(false) }
        var bannerActionType by remember { mutableStateOf(BannerActionType.INSTALL) }
        var isBannerDismissedThisSession by remember { mutableStateOf(false) }
        var localApkBytes by remember { mutableStateOf<ByteArray?>(null) }
        var localApkVersionInfo by remember { mutableStateOf<PluginVersionInfo?>(null) }
        var statusText by remember { mutableStateOf(localized("shell.waiting_for_device")) }
        var isRunning by remember { mutableStateOf(false) }
        var commandLog by remember { mutableStateOf<List<String>>(emptyList()) }
        var bottomPanelHeightPx by remember { mutableStateOf<Float?>(null) }
        var selectedTestModule by remember { mutableStateOf(TestModule.Device) }
        var deviceTestPanelState by remember { mutableStateOf(DeviceTestPanelState()) }
        var applicationTestPanelState by remember { mutableStateOf(ApplicationTestPanelState()) }
        var settingsModuleState by remember { mutableStateOf(SettingsModuleState()) }
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
        var isScreenRecording by remember { mutableStateOf(false) }
        var screenRecordingDeviceSerial by remember { mutableStateOf<String?>(null) }
        var screenRecordingDeviceLabel by remember { mutableStateOf<String?>(null) }
        var screenRecordingStartedAtMillis by remember { mutableStateOf<Long?>(null) }
        var screenRecordingStopRequestedAtMillis by remember { mutableStateOf<Long?>(null) }
        var lastScreenRecordResult by remember { mutableStateOf<ScreenRecordResult?>(null) }
        var isDeviceMirroring by remember { mutableStateOf(false) }
        var deviceMirrorDeviceSerial by remember { mutableStateOf<String?>(null) }
        var deviceMirrorStopRequestedAtMillis by remember { mutableStateOf<Long?>(null) }
        var intentForm by remember { mutableStateOf(IntentTestForm()) }
        var intentTemplateName by remember { mutableStateOf("") }
        var intentTemplates by remember { mutableStateOf(intentTemplateRepository.loadTemplates()) }
        var lastIntentResult by remember { mutableStateOf<IntentExecutionResult?>(null) }
        val selectedDevice = devices.firstOrNull { it.transportId == selectedDeviceTransportId }
        val selectedReadyDevice = selectedDevice?.takeIf { it.isReady }
        val intentValidationResult = validateIntentForm(intentForm)
        val intentCommandPreview = buildIntentAdbCommand(
            deviceSerial = selectedReadyDevice?.transportId ?: "<serial>",
            form = intentForm,
        ).displayCommand
        val intentAppCandidates = remember(thirdPartyApps, systemApps) {
            (thirdPartyApps + systemApps).distinctBy { it.packageName }
        }
        val intentClassNameDetailSection = when (intentForm.mode) {
            IntentCommandMode.Start -> ApplicationDetailSection.ACTIVITIES
            IntentCommandMode.Broadcast -> ApplicationDetailSection.BROADCAST_RECEIVERS
        }
        val intentSelectedApp = remember(intentAppCandidates, intentForm.packageName) {
            intentAppCandidates.firstOrNull { it.packageName == intentForm.packageName.trim() }
        }
        val intentClassNameCandidates = remember(
            intentForm.packageName,
            intentClassNameDetailSection,
            applicationDetailPackageName,
            applicationDetailSections,
        ) {
            if (intentForm.packageName.isNotBlank() && applicationDetailPackageName == intentForm.packageName.trim()) {
                applicationDetailSections[intentClassNameDetailSection]
                    ?.toDisplayDetailItems(intentClassNameDetailSection)
                    ?.mapNotNull { it.intentClassName }
                    ?.distinct()
                    .orEmpty()
            } else {
                emptyList()
            }
        }
        var clearModuleDeviceState: () -> Unit = {}
        var shouldRefreshDevicesAfterAdbNotFound by remember { mutableStateOf(false) }

        DisposableEffect(Unit) {
            onDispose {
                deviceAdb.stopScreenRecording()
                deviceAdb.stopDeviceMirror()
            }
        }

        fun appendCommandLog(command: String) {
            val timePrefix = if (appSettings.showCommandTime) {
                "[${getCurrentTimeFormatted()}] "
            } else ""
            commandLog = (commandLog + "$timePrefix$command").takeLast(200)
        }

        fun clearDeviceScopedState() {
            clearModuleDeviceState()
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
            applicationTestPanelState = applicationTestPanelState.copy(
                selectedAppPackageName = null,
                selectedDetailSection = ApplicationDetailSection.BASIC,
                detailSearchQuery = "",
            )
            deviceSystemInfo = null
            systemProperties = emptyList()
            deviceSystemInfoLoadedSerial = null
            devicePropertiesLoadedSerial = null
            isLoadingDeviceSystemInfo = false
            isLoadingDeviceProperties = false
            if (!isScreenRecording) {
                lastScreenRecordResult = null
                screenRecordingDeviceSerial = null
                screenRecordingDeviceLabel = null
                screenRecordingStartedAtMillis = null
                screenRecordingStopRequestedAtMillis = null
            }
            if (!isDeviceMirroring) {
                deviceMirrorDeviceSerial = null
                deviceMirrorStopRequestedAtMillis = null
            }
        }

        fun scheduleDeviceRefreshAfterAdbNotFoundIfNeeded(message: String) {
            val missingTransport = extractAdbDeviceNotFoundTransport(message) ?: return
            val missingDevice = devices.firstOrNull { it.transportId == missingTransport } ?: return
            if (selectedDeviceTransportId == missingTransport) {
                clearDeviceScopedState()
            }
            val status = localized("shell.device_refresh_after_adb_not_found_arg0", missingDevice.serialNumber)
            statusText = status
            appendCommandLog(commandStatus(status))
            shouldRefreshDevicesAfterAdbNotFound = true
        }

        fun appendCommand(command: String) {
            appendCommandLog(command)
            scheduleDeviceRefreshAfterAdbNotFoundIfNeeded(command)
        }

        fun appendStatus(message: String) {
            appendCommand(commandStatus(message))
        }

        fun appendError(message: String) {
            appendCommand(commandError(message))
        }

        fun noReadyDeviceMessage(): String = localized("common.device.select_device_first")

        fun selectedReadyTransportOrReport(): String? {
            return selectedReadyDevice?.transportId ?: run {
                val message = noReadyDeviceMessage()
                statusText = message
                appendError(message)
                null
            }
        }

        fun saveIntentTemplate() {
            val name = intentTemplateName.trim()
            if (name.isBlank()) return
            val template = IntentTemplate(
                id = "intent-${System.currentTimeMillis()}",
                name = name,
                form = intentForm.normalized(),
            )
            intentTemplates = listOf(template) + intentTemplates
            intentTemplateRepository.saveTemplates(intentTemplates)
            intentTemplateName = ""
            appendStatus(localized("intent.template.saved_arg0", name))
        }

        fun deleteIntentTemplate(template: IntentTemplate) {
            intentTemplates = intentTemplates.filterNot { it.id == template.id }
            intentTemplateRepository.saveTemplates(intentTemplates)
            appendStatus(localized("intent.template.deleted_arg0", template.name))
        }

        fun runIntentCommand() {
            val deviceSerial = selectedReadyTransportOrReport() ?: return
            val validation = validateIntentForm(intentForm)
            if (!validation.isValid) {
                val message = localized("intent.validation.failed")
                statusText = message
                appendError(message)
                return
            }
            scope.launch {
                isRunning = true
                statusText = localized("intent.running")
                appendStatus(statusText)
                try {
                    val result = intentAdb.executeIntentCommand(
                        deviceSerial = deviceSerial,
                        form = intentForm,
                        logCommand = ::appendCommand,
                    )
                    lastIntentResult = result
                    val output = result.output.trim()
                    if (result.isSuccess) {
                        statusText = localized("intent.result.success")
                        appendStatus(statusText)
                        if (output.isNotBlank()) appendCommand(output)
                    } else {
                        statusText = localized("intent.result.failed")
                        appendError(output.ifBlank { statusText })
                    }
                } catch (error: Throwable) {
                    statusText = error.message ?: localized("intent.result.failed")
                    appendError(localized("intent.result.failed") + " - ${error.message ?: unknownError()}")
                } finally {
                    isRunning = false
                }
            }
        }

        fun openIntentTestFromApplicationDetail(
            app: InstalledAppInfo,
            section: ApplicationDetailSection,
            className: String,
        ) {
            val mode = when (section) {
                ApplicationDetailSection.ACTIVITIES -> IntentCommandMode.Start
                ApplicationDetailSection.BROADCAST_RECEIVERS -> IntentCommandMode.Broadcast
                else -> return
            }
            intentForm = IntentTestForm(
                mode = mode,
                packageName = app.packageName,
                className = className,
                action = "",
            )
            selectedTestModule = TestModule.Intent
            statusText = localized("intent.prefilled_from_app_detail_arg0", className)
            appendStatus(statusText)
        }

        val dataFillModule = rememberDataFillModuleController(
            scope = scope,
            getSelectedReadyDevice = {
                devices.firstOrNull { it.transportId == selectedDeviceTransportId }?.takeIf { it.isReady }
            },
            isRunning = { isRunning },
            setRunning = { isRunning = it },
            setStatusText = { statusText = it },
            appendCommand = ::appendCommand,
        )
        val fileManagerModule = rememberFileManagerModuleController(
            scope = scope,
            getSelectedReadyDevice = {
                devices.firstOrNull { it.transportId == selectedDeviceTransportId }?.takeIf { it.isReady }
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
                devices.firstOrNull { it.transportId == selectedDeviceTransportId }?.takeIf { it.isReady }
            },
            setStatusText = { statusText = it },
            appendCommand = ::appendCommand,
        )
        val isCapturingLogcat = deviceLogModule.isCapturing
        clearModuleDeviceState = {
            dataFillModule.clearDeviceState()
            fileManagerModule.clearDeviceState()
            deviceLogModule.clearIfIdle()
        }

        fun refreshDevices() {
            if (isRunning) return
            scope.launch {
                isRunning = true
                statusText = localized("shell.scanning_devices")
                try {
                    val discoveredDevices = adbDeviceManager.listDevices(::appendCommand)
                    devices = discoveredDevices
                    val selectedStillConnected = discoveredDevices.any {
                        it.transportId == selectedDeviceTransportId && it.isReady
                    }
                    val nextSelectedDeviceTransportId = when {
                        selectedStillConnected -> selectedDeviceTransportId
                        else -> discoveredDevices.firstOrNull { it.isReady }?.transportId
                    }
                    selectedDeviceTransportId = nextSelectedDeviceTransportId
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
                    applicationTestPanelState = applicationTestPanelState.copy(
                        selectedAppPackageName = null,
                        selectedDetailSection = ApplicationDetailSection.BASIC,
                        detailSearchQuery = "",
                    )
                    deviceSystemInfo = null
                    systemProperties = emptyList()
                    deviceSystemInfoLoadedSerial = null
                    devicePropertiesLoadedSerial = null
                    isLoadingDeviceSystemInfo = false
                    isLoadingDeviceProperties = false
                    deviceLogModule.clearIfIdle()

                    if (nextSelectedDeviceTransportId == null) {
                        statusText = if (discoveredDevices.isEmpty()) {
                            localized("shell.no_devices_found")
                        } else {
                            localized("shell.found_arg0_devices_none_available", discoveredDevices.size)
                        }
                        appendStatus(statusText)
                    } else if (selectedTestModule == TestModule.DataFill) {
                        statusText = localized("shell.found_arg0_devices_reading_storage", discoveredDevices.size)
                        try {
                            dataFillModule.loadStorageInfoForDeviceScan(nextSelectedDeviceTransportId)
                            statusText = localized("shell.found_arg0_devices_storage_refreshed", discoveredDevices.size)
                            appendStatus(localized("shell.device_connected_storage_refreshed"))
                        } catch (error: Throwable) {
                            statusText = error.message ?: localized("shell.storage_read_failed")
                            appendError(localized("shell.storage_read_failed") + " - ${error.message ?: unknownError()}")
                        }
                    } else if (selectedTestModule == TestModule.Device) {
                        statusText = localized("shell.found_arg0_devices_reading_system_info", discoveredDevices.size)
                        try {
                            deviceSystemInfo = deviceAdb.loadSystemInfo(nextSelectedDeviceTransportId, ::appendCommand)
                            systemProperties = deviceAdb.loadSystemProperties(nextSelectedDeviceTransportId, ::appendCommand)
                            deviceSystemInfoLoadedSerial = nextSelectedDeviceTransportId
                            devicePropertiesLoadedSerial = nextSelectedDeviceTransportId
                            statusText = localized("shell.found_arg0_devices_system_info_refreshed", discoveredDevices.size)
                            appendStatus(localized("shell.device_connected_system_info_and_properties_refreshe"))
                        } catch (error: Throwable) {
                            statusText = error.message ?: localized("shell.system_info_read_failed")
                            appendError(localized("shell.system_info_read_failed") + " - ${error.message ?: unknownError()}")
                        }
                    } else if (selectedTestModule == TestModule.Log) {
                        statusText = localized("shell.found_arg0_devices_ready_to_capture_logcat", discoveredDevices.size)
                        appendStatus(statusText)
                    } else if (selectedTestModule == TestModule.FileManager) {
                        statusText = localized("shell.found_arg0_devices_reading_files", discoveredDevices.size)
                        try {
                            fileManagerModule.loadRootForDeviceScan(nextSelectedDeviceTransportId)
                            statusText = localized("shell.found_arg0_devices_files_loaded", discoveredDevices.size)
                            appendStatus(localized("shell.device_connected_file_directory_refreshed"))
                        } catch (error: Throwable) {
                            statusText = error.message ?: localized("shell.file_read_failed")
                            appendError(localized("shell.file_read_failed") + " - ${error.message ?: unknownError()}")
                        }
                    } else {
                        statusText = localized("shell.found_arg0_devices_ready_to_read_apps", discoveredDevices.size)
                        appendStatus(statusText)
                    }
                } catch (error: Throwable) {
                    statusText = error.message ?: localized("shell.device_scan_failed")
                    appendError(localized("shell.device_scan_failed") + " - ${error.message ?: unknownError()}")
                } finally {
                    isRunning = false
                }
            }
        }

        LaunchedEffect(shouldRefreshDevicesAfterAdbNotFound, isRunning) {
            if (shouldRefreshDevicesAfterAdbNotFound && !isRunning) {
                shouldRefreshDevicesAfterAdbNotFound = false
                refreshDevices()
            }
        }

        suspend fun pairWirelessDevice(
            host: String,
            pairingPort: String,
            pairingCode: String,
        ): String {
            if (isRunning) return localized("shell.waiting_for_the_current_task_to_finish")
            isRunning = true
            statusText = localized("shell.wireless_pairing")
            val output = try {
                val output = adbDeviceManager.pairWirelessDevice(
                    host = host,
                    pairingPort = pairingPort,
                    pairingCode = pairingCode,
                    logCommand = ::appendCommand,
                )
                statusText = localized("shell.wireless_pairing_succeeded")
                appendStatus(statusText)
                output.trim()
            } catch (error: Throwable) {
                statusText = error.message ?: localized("shell.wireless_pairing_failed")
                appendError(localized("shell.wireless_pairing_failed") + " - ${error.message ?: unknownError()}")
                throw error
            } finally {
                isRunning = false
            }
            return output
        }

        suspend fun connectWirelessDevice(
            host: String,
            debugPort: String,
        ): String {
            if (isRunning) return localized("shell.waiting_for_the_current_task_to_finish")
            isRunning = true
            statusText = localized("shell.wireless_connecting")
            val output = try {
                val output = adbDeviceManager.connectWirelessDevice(
                    host = host,
                    debugPort = debugPort,
                    logCommand = ::appendCommand,
                )
                statusText = localized("shell.wireless_connect_succeeded")
                appendStatus(statusText)
                output.trim()
            } catch (error: Throwable) {
                statusText = error.message ?: localized("shell.wireless_connect_failed")
                appendError(localized("shell.wireless_connect_failed") + " - ${error.message ?: unknownError()}")
                throw error
            } finally {
                isRunning = false
            }
            refreshDevices()
            return output
        }

        fun loadDeviceSystemInfo(deviceSerial: String) {
            if (isRunning) return
            scope.launch {
                isRunning = true
                isLoadingDeviceSystemInfo = true
                deviceSystemInfo = null
                deviceSystemInfoLoadedSerial = deviceSerial
                statusText = localized("shell.reading_device_system_info")
                appendStatus(localized("shell.reading_device_system_info"))
                try {
                    deviceSystemInfo = deviceAdb.loadSystemInfo(deviceSerial, ::appendCommand)
                    statusText = localized("shell.device_system_info_loaded")
                    appendStatus(statusText)
                } catch (error: Throwable) {
                    deviceSystemInfoLoadedSerial = null
                    statusText = error.message ?: localized("shell.device_system_info_read_failed")
                    appendError(localized("shell.device_system_info_read_failed") + " - ${error.message ?: unknownError()}")
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
                statusText = localized("shell.reading_system_properties")
                appendStatus(localized("shell.reading_system_properties"))
                try {
                    systemProperties = deviceAdb.loadSystemProperties(deviceSerial, ::appendCommand)
                    statusText = localized("shell.system_properties_loaded_arg0_properties", systemProperties.size)
                    appendStatus(statusText)
                } catch (error: Throwable) {
                    devicePropertiesLoadedSerial = null
                    statusText = error.message ?: localized("shell.system_properties_read_failed")
                    appendError(localized("shell.system_properties_read_failed") + " - ${error.message ?: unknownError()}")
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
                statusText = localized("shell.rebooting_device")
                appendStatus(localized("shell.start_rebooting_device_arg0", deviceSerial))
                try {
                    deviceAdb.rebootDevice(deviceSerial, ::appendCommand)
                    statusText = localized("shell.reboot_command_sent")
                    appendStatus(localized("shell.reboot_command_sent_device_will_reboot"))
                    selectedDeviceTransportId = null
                    deviceSystemInfo = null
                    systemProperties = emptyList()
                } catch (error: Throwable) {
                    statusText = error.message ?: localized("shell.device_reboot_failed")
                    appendError(localized("shell.device_reboot_failed") + " - ${error.message ?: unknownError()}")
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
                statusText = localized("shell.running_arg0", actionLabel)
                appendStatus(localized("shell.start_arg0_on_arg1", actionLabel, deviceSerial))
                try {
                    deviceAdb.runQuickAction(deviceSerial, action, ::appendCommand)
                    statusText = localized("shell.action.command_sent_status", actionLabel)
                    appendStatus(localized("shell.action.command_sent_log", actionLabel))
                    if (action == DeviceQuickAction.SHUTDOWN ||
                        action == DeviceQuickAction.REBOOT_RECOVERY ||
                        action == DeviceQuickAction.REBOOT_FASTBOOT
                    ) {
                        selectedDeviceTransportId = null
                        deviceSystemInfo = null
                        systemProperties = emptyList()
                    }
                } catch (error: Throwable) {
                    statusText = error.message ?: localized("shell.action.failed_with_name", actionLabel)
                    appendError(localized("shell.action.failed_with_name", actionLabel) + " - ${error.message ?: unknownError()}")
                } finally {
                    isRunning = false
                }
            }
        }

        fun takeSelectedDeviceScreenshot(deviceSerial: String) {
            if (isRunning) return
            scope.launch {
                isRunning = true
                statusText = localized("device.screenshot.select_save_directory_status")
                appendStatus(localized("device.screenshot.select_save_directory_dialog_title"))
                try {
                    val outputPath = selectDirectory(
                        dialogTitle = localized("shell.select_screenshot_save_path"),
                        approveButtonText = localized("shell.save"),
                    )
                    if (outputPath == null) {
                        statusText = localized("shell.screenshot_cancelled")
                        appendStatus(statusText)
                    } else {
                        statusText = localized("shell.capturing_device_screen")
                        appendStatus(localized("shell.start_screenshot_on_device_arg0_save_to_arg1", deviceSerial, outputPath))
                        val result = deviceAdb.takeScreenshot(deviceSerial, outputPath, ::appendCommand)
                        statusText = localized("shell.screenshot_saved_to_arg0", result.localPath)
                        appendStatus(localized("shell.screenshot_succeeded_local_file_arg0_device_file_arg1", result.localPath, result.remotePath))
                    }
                } catch (error: Throwable) {
                    statusText = error.message ?: localized("shell.screenshot_failed")
                    appendError(localized("shell.screenshot_failed") + " - ${error.message ?: unknownError()}")
                } finally {
                    isRunning = false
                }
            }
        }

        fun startSelectedDeviceScreenRecording(deviceSerial: String) {
            if (isRunning || isScreenRecording) return
            scope.launch {
                isRunning = true
                statusText = localized("device.screen_record.select_save_directory_status")
                appendStatus(localized("device.screen_record.select_save_directory_dialog_title"))
                try {
                    val outputPath = selectDirectory(
                        dialogTitle = localized("shell.select_screen_record_save_path"),
                        approveButtonText = localized("shell.save"),
                    )
                    if (outputPath == null) {
                        statusText = localized("shell.screen_record_cancelled")
                        appendStatus(statusText)
                    } else {
                        val recordingDevice = devices.firstOrNull { it.transportId == deviceSerial }
                        isRunning = false
                        isScreenRecording = true
                        screenRecordingDeviceSerial = deviceSerial
                        screenRecordingDeviceLabel = recordingDevice?.let { "${it.model} · ${it.serialNumber}" } ?: deviceSerial
                        screenRecordingStartedAtMillis = null
                        screenRecordingStopRequestedAtMillis = null
                        lastScreenRecordResult = null
                        statusText = localized("shell.screen_recording")
                        appendStatus(localized("shell.start_screen_record_on_device_arg0_save_to_arg1", deviceSerial, outputPath))
                        val result = deviceAdb.recordScreen(
                            deviceSerial = deviceSerial,
                            outputDirectoryPath = outputPath,
                            logCommand = ::appendCommand,
                            onRecordingStarted = {
                                scope.launch {
                                    if (isScreenRecording &&
                                        screenRecordingStartedAtMillis == null &&
                                        screenRecordingStopRequestedAtMillis == null
                                    ) {
                                        screenRecordingStartedAtMillis = System.currentTimeMillis()
                                    }
                                }
                            },
                        )
                        lastScreenRecordResult = result
                        when (result.endState) {
                            ScreenRecordEndState.COMPLETED -> {
                                statusText = localized("shell.screen_record_saved_to_arg0", result.localPath)
                                if (result.remotePath.isBlank()) {
                                    appendStatus(localized("shell.screen_record_succeeded_local_file_arg0", result.localPath))
                                } else {
                                    appendStatus(localized("shell.screen_record_succeeded_local_file_arg0_device_file_arg1", result.localPath, result.remotePath))
                                }
                            }
                            ScreenRecordEndState.STOPPED -> {
                                statusText = localized("shell.screen_record_stopped_saved_to_arg0", result.localPath)
                                if (result.remotePath.isBlank()) {
                                    appendStatus(localized("shell.screen_record_stopped_local_file_arg0", result.localPath))
                                } else {
                                    appendStatus(localized("shell.screen_record_stopped_local_file_arg0_device_file_arg1", result.localPath, result.remotePath))
                                }
                            }
                            ScreenRecordEndState.INTERRUPTED -> {
                                statusText = result.message ?: localized("shell.screen_record_interrupted")
                                appendError(localized("shell.screen_record_interrupted") + " - ${result.message ?: unknownError()}")
                            }
                        }
                    }
                } catch (error: CancellationException) {
                    statusText = localized("shell.screen_record_stopped")
                    appendStatus(statusText)
                } catch (error: Throwable) {
                    statusText = error.message ?: localized("shell.screen_record_failed")
                    appendError(localized("shell.screen_record_failed") + " - ${error.message ?: unknownError()}")
                } finally {
                    isRunning = false
                    isScreenRecording = false
                    screenRecordingDeviceSerial = null
                    screenRecordingDeviceLabel = null
                    screenRecordingStartedAtMillis = null
                    screenRecordingStopRequestedAtMillis = null
                }
            }
        }

        fun stopSelectedDeviceScreenRecording() {
            if (!isScreenRecording) return
            if (screenRecordingStopRequestedAtMillis == null) {
                screenRecordingStopRequestedAtMillis = System.currentTimeMillis()
            }
            statusText = localized("shell.stopping_screen_record")
            appendStatus(statusText)
            deviceAdb.stopScreenRecording()
        }

        fun revealScreenRecordFile(filePath: String) {
            val opened = revealFileInDirectory(filePath)
            if (opened) {
                val message = localized("device.screen_record.opened_recording_directory")
                statusText = message
                appendStatus("$message - $filePath")
            } else {
                val message = localized("device.screen_record.unable_to_open_recording_directory")
                statusText = message
                appendError("$message - $filePath")
            }
        }

        fun startSelectedDeviceMirror(deviceSerial: String) {
            if (isDeviceMirroring) return
            scope.launch {
                val mirrorDevice = devices.firstOrNull { it.transportId == deviceSerial }
                val mirrorLabel = mirrorDevice?.let { "${it.model} · ${it.serialNumber}" } ?: deviceSerial
                isDeviceMirroring = true
                deviceMirrorDeviceSerial = deviceSerial
                deviceMirrorStopRequestedAtMillis = null
                statusText = localized("shell.device_mirror_starting")
                appendStatus(localized("shell.start_device_mirror_on_device_arg0", deviceSerial))
                try {
                    val result = deviceAdb.mirrorDevice(
                        deviceSerial = deviceSerial,
                        windowTitle = localized("device.mirror.window_title_arg0", mirrorLabel),
                        logCommand = ::appendCommand,
                        onMirrorStarted = {
                            scope.launch {
                                if (isDeviceMirroring && deviceMirrorStopRequestedAtMillis == null) {
                                    statusText = localized("shell.device_mirror_running")
                                    appendStatus(localized("shell.device_mirror_window_opened_arg0", deviceSerial))
                                }
                            }
                        },
                    )
                    when (result.endState) {
                        DeviceMirrorEndState.CLOSED -> {
                            statusText = localized("shell.device_mirror_closed")
                            appendStatus(statusText)
                        }
                        DeviceMirrorEndState.STOPPED -> {
                            statusText = localized("shell.device_mirror_stopped")
                            appendStatus(statusText)
                        }
                        DeviceMirrorEndState.INTERRUPTED -> {
                            statusText = result.message ?: localized("shell.device_mirror_interrupted")
                            appendError(localized("shell.device_mirror_interrupted") + " - ${result.message ?: unknownError()}")
                        }
                    }
                } catch (error: CancellationException) {
                    statusText = localized("shell.device_mirror_stopped")
                    appendStatus(statusText)
                } catch (error: Throwable) {
                    statusText = error.message ?: localized("shell.device_mirror_failed")
                    appendError(localized("shell.device_mirror_failed") + " - ${error.message ?: unknownError()}")
                } finally {
                    isDeviceMirroring = false
                    deviceMirrorDeviceSerial = null
                    deviceMirrorStopRequestedAtMillis = null
                }
            }
        }

        fun stopSelectedDeviceMirror() {
            if (!isDeviceMirroring) return
            if (deviceMirrorStopRequestedAtMillis == null) {
                deviceMirrorStopRequestedAtMillis = System.currentTimeMillis()
            }
            statusText = localized("shell.device_mirror_stopping")
            appendStatus(statusText)
            deviceAdb.stopDeviceMirror()
        }

        fun installSelectedApplications(deviceSerial: String) {
            if (isRunning) return
            scope.launch {
                isRunning = true
                statusText = localized("device.install.select_apk_files_status")
                appendStatus(localized("device.install.select_apk_files_dialog_title"))
                try {
                    val apkPaths = selectApkFiles(
                        dialogTitle = localized("device.install.select_apk_files_dialog_title"),
                        approveButtonText = localized("shell.install"),
                    )
                    if (apkPaths.isEmpty()) {
                        statusText = localized("shell.app_installation_cancelled")
                        appendStatus(statusText)
                    } else {
                        statusText = localized("shell.installing_arg0_apps", apkPaths.size)
                        appendStatus(localized("shell.start_installing_arg0_apk_files_to_device_arg1", apkPaths.size, deviceSerial))
                        val results = deviceAdb.installApplications(deviceSerial, apkPaths, ::appendCommand)
                        val successCount = results.count { it.success }
                        val failureResults = results.filterNot { it.success }

                        results.forEach { result ->
                            if (result.success) {
                                appendStatus(localized("shell.install_succeeded") + " - ${result.fileName}")
                            } else {
                                appendError(localized("shell.install_failed") + " - ${result.fileName} - ${result.message}")
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
                            localized("shell.app_installation_complete_succeeded_arg0_arg1", successCount, results.size)
                        } else {
                            localized("shell.app_installation_complete_succeeded_arg0_arg1_failed_arg2", successCount, results.size, failureResults.size)
                        }
                        appendStatus(statusText)
                    }
                } catch (error: CancellationException) {
                    statusText = localized("shell.app_installation_stopped")
                    appendStatus(statusText)
                } catch (error: Throwable) {
                    statusText = error.message ?: localized("shell.app_installation_failed")
                    appendError(localized("shell.app_installation_failed") + " - ${error.message ?: unknownError()}")
                } finally {
                    isRunning = false
                }
            }
        }

        fun loadThirdPartyApps(
            deviceSerial: String,
            deviceTransportId: String,
        ) {
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
                statusText = localized("shell.reading_third_party_apps")
                appendStatus(localized("shell.reading_third_party_apps"))
                try {
                    thirdPartyApps = appAdb.loadInstalledApps(deviceTransportId, false, ::appendCommand) { current, total ->
                        thirdPartyProgressCurrent = current
                        thirdPartyProgressTotal = total
                        statusText = localized("shell.reading_third_party_apps_arg0_arg1", current, total)
                    }
                    val disabledCount = thirdPartyApps.count { !it.isEnabled }
                    statusText = localized("shell.loaded_arg0_third_party_apps_arg1_disabled", thirdPartyApps.size, disabledCount)
                    appendStatus(statusText)
                } catch (error: Throwable) {
                    thirdPartyLoadedSerial = null
                    statusText = error.message ?: localized("shell.third_party_app_read_failed")
                    appendError(localized("shell.third_party_app_read_failed") + " - ${error.message ?: unknownError()}")
                } finally {
                    isLoadingThirdParty = false
                    isRunning = false
                }
            }
        }

        fun loadSystemApps(
            deviceSerial: String,
            deviceTransportId: String,
        ) {
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
                statusText = localized("shell.reading_system_apps")
                appendStatus(localized("shell.reading_system_apps"))
                try {
                    val apps = appAdb.loadInstalledApps(deviceTransportId, true, ::appendCommand) { current, total ->
                        systemProgressCurrent = current
                        systemProgressTotal = total
                        statusText = localized("shell.reading_system_apps_arg0_arg1", current, total)
                    }
                    systemApps = apps
                    val disabledCount = apps.count { !it.isEnabled }
                    statusText = localized("shell.loaded_arg0_system_apps_arg1_disabled", apps.size, disabledCount)
                    appendStatus(statusText)
                    appAdb.saveCachedSystemApps(deviceSerial, apps)
                    val cached = appAdb.loadCachedSystemApps(deviceSerial)
                    if (cached != null) {
                        systemAppsCacheFormattedTime = cached.cacheTimeFormatted
                    }
                    systemCacheCheckedSerial = deviceSerial
                } catch (error: Throwable) {
                    systemLoadedSerial = null
                    statusText = error.message ?: localized("shell.system_app_read_failed")
                    appendError(localized("shell.system_app_read_failed") + " - ${error.message ?: unknownError()}")
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
                statusText = localized("shell.clearing_app_list_cache")
                appendStatus(localized("shell.clearing_app_list_cache"))
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
                    statusText = localized("shell.app_list_cache_cleared")
                    appendStatus(statusText)
                } catch (error: Throwable) {
                    statusText = error.message ?: localized("shell.app_list_cache_clear_failed")
                    appendError(localized("shell.app_list_cache_clear_failed") + " - ${error.message ?: unknownError()}")
                } finally {
                    isRunning = false
                }
            }
        }

        fun loadApplicationDetail(app: InstalledAppInfo, section: ApplicationDetailSection) {
            if (isRunning) return
            val deviceTransportId = selectedReadyDevice?.transportId
            if (deviceTransportId == null) {
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
                statusText = localized("shell.reading_arg0_arg1_info", app.packageName, sectionTitle)
                appendStatus(statusText)
                try {
                    val content = appAdb.loadApplicationDetail(
                        deviceSerial = deviceTransportId,
                        app = app,
                        section = section,
                        logCommand = ::appendCommand,
                    )
                    if (applicationDetailPackageName == app.packageName) {
                        applicationDetailSections = applicationDetailSections + (section to content)
                    }
                    statusText = localized("shell.read_arg0_arg1_info", app.packageName, sectionTitle)
                    appendStatus(statusText)
                } catch (error: Throwable) {
                    val message = error.message ?: localized("shell.app_detail_read_failed")
                    if (applicationDetailPackageName == app.packageName) {
                        applicationDetailSections = applicationDetailSections + (
                            section to ApplicationDetailContent(
                                section = section,
                                source = ApplicationDetailSource.ADB,
                                items = listOf(
                                    ApplicationDetailItem(
                                        label = localized("common.error"),
                                        value = message,
                                    ),
                                ),
                            )
                        )
                    }
                    statusText = message
                    appendError(localized("shell.read_arg0_arg1_info_failed", app.packageName, sectionTitle) + " - $message")
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

        fun exportApplicationManifest(app: InstalledAppInfo, manifestText: String) {
            if (isRunning) return
            if (manifestText.isBlank()) {
                statusText = localized("shell.manifest_export_failed_empty")
                appendError(statusText)
                return
            }

            scope.launch {
                isRunning = true
                val fileName = buildManifestExportFileName(app.packageName)
                statusText = localized("shell.select_manifest_export_path")
                appendStatus(localized("shell.select_manifest_export_path_for_arg0", app.packageName))
                try {
                    val outputPath = selectDirectory(
                        dialogTitle = localized("shell.select_manifest_export_path"),
                        approveButtonText = localized("file_manager.export"),
                    )
                    if (outputPath == null) {
                        statusText = localized("shell.manifest_export_cancelled")
                        appendStatus(statusText)
                    } else {
                        saveBytesToFile(outputPath, fileName, manifestText.encodeToByteArray())
                        statusText = localized("shell.exported_manifest_to_arg0_arg1", outputPath, fileName)
                        appendStatus(statusText)
                    }
                } catch (error: Throwable) {
                    statusText = localized("shell.manifest_export_failed_arg0", error.message ?: unknownError())
                    appendError(statusText)
                } finally {
                    isRunning = false
                }
            }
        }

        fun runApplicationAction(app: InstalledAppInfo, action: String) {
            if (isRunning) return
            val deviceSerial = selectedReadyDevice?.serialNumber
            val deviceTransportId = selectedReadyDevice?.transportId
            if (deviceSerial == null || deviceTransportId == null) {
                statusText = noReadyDeviceMessage()
                appendError(statusText)
                return
            }

            scope.launch {
                isRunning = true
                val actionLabel = applicationActionLabel(action)
                statusText = localized("shell.action.app_action_loading", actionLabel, app.packageName)
                appendStatus(localized("shell.start_arg0_arg1", actionLabel, app.packageName))
                try {
                    when (action) {
                        ApplicationAction.LAUNCH -> {
                            appAdb.launchApplication(deviceTransportId, app.packageName, ::appendCommand)
                            statusText = localized("shell.launched_arg0", app.packageName)
                            appendStatus(statusText)
                        }
                        ApplicationAction.STOP -> {
                            appAdb.stopApplication(deviceTransportId, app.packageName, ::appendCommand)
                            statusText = localized("shell.force_stopped_arg0", app.packageName)
                            appendStatus(statusText)
                        }
                        ApplicationAction.CLEAR_DATA -> {
                            appAdb.clearApplicationData(deviceTransportId, app.packageName, ::appendCommand)
                            statusText = localized("shell.cleared_data_for_arg0", app.packageName)
                            appendStatus(statusText)
                        }
                        ApplicationAction.CLEAR_CACHE -> {
                            appAdb.clearApplicationCache(deviceTransportId, app.packageName, ::appendCommand)
                            statusText = localized("shell.cleared_cache_for_arg0", app.packageName)
                            appendStatus(statusText)
                        }
                        ApplicationAction.DISABLE -> {
                            appAdb.disableApplication(deviceTransportId, app.packageName, ::appendCommand)
                            val nextSystemApps = updateApplicationEnabledState(app.packageName, false)
                            if (app.isSystem) {
                                appAdb.saveCachedSystemApps(deviceSerial, nextSystemApps)
                            }
                            statusText = localized("shell.device.disabled_count", app.packageName)
                            appendStatus(statusText)
                        }
                        ApplicationAction.ENABLE -> {
                            appAdb.enableApplication(deviceTransportId, app.packageName, ::appendCommand)
                            val nextSystemApps = updateApplicationEnabledState(app.packageName, true)
                            if (app.isSystem) {
                                appAdb.saveCachedSystemApps(deviceSerial, nextSystemApps)
                            }
                            statusText = localized("shell.enabled_arg0", app.packageName)
                            appendStatus(statusText)
                        }
                        ApplicationAction.UNINSTALL -> {
                            appAdb.uninstallApplication(deviceTransportId, app.packageName, app.isSystem, ::appendCommand)
                            val nextSystemApps = removeApplicationFromLists(app.packageName)
                            if (app.isSystem) {
                                appAdb.saveCachedSystemApps(deviceSerial, nextSystemApps)
                                val cached = appAdb.loadCachedSystemApps(deviceSerial)
                                systemAppsCacheFormattedTime = cached?.cacheTimeFormatted
                            }
                            statusText = localized("shell.uninstalled_arg0", app.packageName)
                            appendStatus(statusText)
                        }
                        ApplicationAction.EXPORT_APK -> {
                            val outputPath = selectDirectory(
                                dialogTitle = localized("shell.select_apk_export_path"),
                                approveButtonText = localized("file_manager.export"),
                            )
                            if (outputPath == null) {
                                statusText = localized("file_manager.export_cancelled")
                                appendStatus(statusText)
                            } else {
                                val result = appAdb.exportApplicationApk(deviceTransportId, app.packageName, outputPath, ::appendCommand)
                                statusText = localized("shell.exported_arg0_apk_files_to_arg1", result.fileCount, result.directoryPath)
                                appendStatus(statusText)
                            }
                        }
                        ApplicationAction.SAVE_ICON -> {
                            val iconBytes = app.iconBytes
                            if (iconBytes == null) {
                                statusText = localized("shell.cannot_save_app_has_no_icon_data")
                                appendError(statusText)
                            } else {
                                val outputPath = selectDirectory(
                                    dialogTitle = localized("shell.select_icon_save_path"),
                                    approveButtonText = localized("shell.save"),
                                )
                                if (outputPath == null) {
                                    statusText = localized("shell.icon_save_cancelled")
                                    appendStatus(statusText)
                                } else {
                                    val safeAppName = app.appName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                                    val fileName = "${safeAppName}_${app.packageName}.png"
                                    try {
                                        saveBytesToFile(outputPath, fileName, iconBytes)
                                        statusText = localized("shell.icon_saved_to_arg0_arg1", outputPath, fileName)
                                        appendStatus(statusText)
                                    } catch (e: Exception) {
                                        statusText = localized("shell.icon_save_failed_arg0", e.message)
                                        appendError(statusText)
                                    }
                                }
                            }
                        }
                        else -> {
                            statusText = localized("shell.unknown_app_action_arg0", action)
                            appendError(statusText)
                        }
                    }
                } catch (error: CancellationException) {
                    statusText = localized("shell.app_operation_stopped")
                    appendStatus(statusText)
                } catch (error: Throwable) {
                    statusText = error.message ?: localized("shell.action.failed_with_name", actionLabel)
                    appendError(localized("shell.action.failed_with_name", actionLabel) + " - ${error.message ?: unknownError()}")
                } finally {
                    isRunning = false
                }
            }
        }

        LaunchedEffect(selectedReadyDevice?.transportId) {
            val deviceTransportId = selectedReadyDevice?.transportId
            if (deviceTransportId != null) {
                if (localApkBytes == null || localApkVersionInfo == null) {
                    try {
                        val bytes = appAdb.getLocalPluginApkBytes()
                        if (bytes != null) {
                            localApkBytes = bytes
                            localApkVersionInfo = appAdb.getApkVersionInfo(bytes)
                        } else {
                            appendError(localized("app.plugin.apk_missing_in_resources"))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        appendError(localized("app.plugin.apk_load_failed") + ": ${e.message}")
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
                        val installedVersionInfo = appAdb.getInstalledPluginVersionInfo(deviceTransportId, ::appendCommand)
                        if (installedVersionInfo == null) {
                            bannerMessage = localized("shell.athplugin_is_not_installed_on_this_device_installing")
                            bannerActionType = BannerActionType.INSTALL
                            showPluginBanner = true
                        } else {
                            val isPluginEnabled = appAdb.isPluginEnabled(deviceTransportId, ::appendCommand)
                            if (!isPluginEnabled) {
                                bannerMessage = localized("shell.athplugin_is_disabled_app_info_loading_may_take_long")
                                bannerActionType = BannerActionType.ENABLE
                                showPluginBanner = true
                            } else if (installedVersionInfo.versionCode < targetLocalVersionInfo.versionCode) {
                                bannerMessage = localized("app.plugin.outdated_update_recommended", installedVersionInfo.versionName, targetLocalVersionInfo.versionName)
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
            selectedReadyDevice?.transportId,
            appSettings.fileManagerDefaultRootPath,
        ) {
            val deviceSerial = selectedReadyDevice?.serialNumber
            val deviceTransportId = selectedReadyDevice?.transportId
            if (deviceSerial != null && deviceTransportId != null) {
                if (selectedTestModule == TestModule.Device) {
                    if (deviceSystemInfoLoadedSerial != deviceTransportId) {
                        loadDeviceSystemInfo(deviceTransportId)
                    }
                    if (devicePropertiesLoadedSerial != deviceTransportId) {
                        loadDeviceSystemProperties(deviceTransportId)
                    }
                } else if (selectedTestModule == TestModule.App || selectedTestModule == TestModule.Intent) {
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
                    if (fileManagerModule.loadedSerial != deviceTransportId) {
                        fileManagerModule.loadDirectory(deviceTransportId, fileManagerModule.appliedRootPath)
                    }
                }
            }
        }

        LaunchedEffect(
            selectedTestModule,
            selectedReadyDevice?.transportId,
            isRunning,
            systemCacheCheckedSerial,
            systemAutoRefreshAttemptedSerial,
        ) {
            val deviceSerial = selectedReadyDevice?.serialNumber
            val deviceTransportId = selectedReadyDevice?.transportId
            if ((selectedTestModule == TestModule.App || selectedTestModule == TestModule.Intent) &&
                deviceSerial != null &&
                deviceTransportId != null &&
                !isRunning &&
                systemCacheCheckedSerial == deviceSerial &&
                systemLoadedSerial != deviceSerial &&
                systemAutoRefreshAttemptedSerial != deviceSerial
            ) {
                systemAutoRefreshAttemptedSerial = deviceSerial
                loadSystemApps(deviceSerial, deviceTransportId)
            }
        }

        LaunchedEffect(
            selectedTestModule,
            selectedReadyDevice?.transportId,
            isRunning,
            thirdPartyAutoRefreshAttemptedSerial,
        ) {
            val deviceSerial = selectedReadyDevice?.serialNumber
            val deviceTransportId = selectedReadyDevice?.transportId
            if ((selectedTestModule == TestModule.App || selectedTestModule == TestModule.Intent) &&
                deviceSerial != null &&
                deviceTransportId != null &&
                !isRunning &&
                thirdPartyLoadedSerial != deviceSerial &&
                thirdPartyAutoRefreshAttemptedSerial != deviceSerial
            ) {
                thirdPartyAutoRefreshAttemptedSerial = deviceSerial
                loadThirdPartyApps(deviceSerial, deviceTransportId)
            }
        }

        LaunchedEffect(
            selectedTestModule,
            selectedReadyDevice?.transportId,
            isRunning,
            intentSelectedApp?.packageName,
            intentClassNameDetailSection,
            applicationDetailPackageName,
            applicationDetailSections,
            loadingApplicationDetailSection,
        ) {
            val app = intentSelectedApp ?: return@LaunchedEffect
            if (selectedTestModule == TestModule.Intent &&
                selectedReadyDevice != null &&
                !isRunning &&
                loadingApplicationDetailSection != intentClassNameDetailSection &&
                (
                    applicationDetailPackageName != app.packageName ||
                        applicationDetailSections[intentClassNameDetailSection] == null
                    )
            ) {
                loadApplicationDetail(app, intentClassNameDetailSection)
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
                        BannerActionType.INSTALL -> localized("shell.install_now")
                        BannerActionType.UPDATE -> localized("shell.update_now")
                        BannerActionType.ENABLE -> localized("shell.enable_now")
                    }
                    PluginCheckBanner(
                        message = bannerMessage,
                        actionLabel = actionLabel,
                        onAction = {
                            val serial = selectedReadyDevice?.serialNumber
                            val transportId = selectedReadyDevice?.transportId
                            if (serial != null && transportId != null) {
                                if (bannerActionType == BannerActionType.ENABLE) {
                                    if (!isEnablingPlugin) {
                                        isEnablingPlugin = true
                                        scope.launch {
                                            statusText = localized("shell.enabling_helper_plugin")
                                            appendStatus(localized("shell.start_enabling_helper_plugin_on_device_arg0", serial))
                                            try {
                                                appAdb.enableApplication(transportId, "com.floatingmuseum.android.test.helper.plugin", ::appendCommand)
                                                // 延迟 500ms，等待系统状态更新
                                                kotlinx.coroutines.delay(500)
                                                val isEnabled = appAdb.isPluginEnabled(transportId, ::appendCommand)
                                                if (isEnabled) {
                                                    statusText = localized("shell.helper_plugin_enabled")
                                                    appendStatus(localized("shell.helper_plugin_enabled_on_device_arg0", serial))
                                                } else {
                                                    statusText = localized("shell.automatic_enable_failed_manual_enable_is_recommended")
                                                    appendError(localized("app.plugin.enable_finished_but_disabled", serial))
                                                }
                                            } catch (e: Exception) {
                                                statusText = localized("shell.automatic_enable_failed_manual_enable_is_recommended")
                                                appendError(localized("shell.failed_to_enable_helper_plugin_on_device_arg0", serial) + " - ${e.message}")
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
                                            statusText = localized("shell.installing_helper_plugin_on_device_arg0", serial)
                                            appendStatus(localized("shell.start_installing_helper_plugin_on_device_arg0", serial))
                                            val success = appAdb.installPluginApk(transportId, bytes, ::appendCommand)
                                            if (success) {
                                                statusText = localized("shell.helper_plugin_installed")
                                                appendStatus(localized("shell.helper_plugin_installed_on_device_arg0", serial))
                                                showPluginBanner = false
                                            } else {
                                                statusText = localized("shell.helper_plugin_installation_failed_check_the_connecti")
                                                appendError(localized("app.plugin.helper_plugin_installation_failed_on_device_arg0", serial))
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
                                        selectedReadyTransportOrReport()?.let { loadDeviceSystemInfo(it) }
                                    },
                                    onRefreshProperties = {
                                        selectedReadyTransportOrReport()?.let { loadDeviceSystemProperties(it) }
                                    },
                                    onReboot = {
                                        selectedReadyTransportOrReport()?.let { rebootSelectedDevice(it) }
                                    },
                                    onTakeScreenshot = {
                                        selectedReadyTransportOrReport()?.let { takeSelectedDeviceScreenshot(it) }
                                    },
                                    onStartScreenRecording = {
                                        selectedReadyTransportOrReport()?.let { startSelectedDeviceScreenRecording(it) }
                                    },
                                    onStopScreenRecording = ::stopSelectedDeviceScreenRecording,
                                    onStartDeviceMirror = {
                                        selectedReadyTransportOrReport()?.let { startSelectedDeviceMirror(it) }
                                    },
                                    onStopDeviceMirror = ::stopSelectedDeviceMirror,
                                    onInstallApplications = {
                                        selectedReadyTransportOrReport()?.let { installSelectedApplications(it) }
                                    },
                                    onQuickAction = { action ->
                                        selectedReadyTransportOrReport()?.let {
                                            runSelectedDeviceQuickAction(it, action)
                                        }
                                    },
                                    isRunning = isRunning,
                                    isScreenRecording = isScreenRecording,
                                    isScreenRecordingThisDevice = isScreenRecording && screenRecordingDeviceSerial == selectedReadyDevice?.transportId,
                                    isDeviceMirroring = isDeviceMirroring,
                                    isDeviceMirroringThisDevice = isDeviceMirroring && deviceMirrorDeviceSerial == selectedReadyDevice?.transportId,
                                    lastScreenRecordResult = lastScreenRecordResult,
                                    onRevealScreenRecordFile = ::revealScreenRecordFile,
                                    onBatteryControl = { args ->
                                        selectedReadyTransportOrReport()?.let { transportId ->
                                            scope.launch {
                                                isRunning = true
                                                statusText = localized("shell.running_battery_simulation")
                                                appendStatus(localized("shell.run_battery_simulation_adb_s_arg0_shell_dumpsys_battery", transportId, args.joinToString(" ")))
                                                try {
                                                    deviceAdb.controlBattery(transportId, args, ::appendCommand)
                                                    statusText = localized("shell.battery_simulation_executed")
                                                    appendStatus(statusText)
                                                    deviceSystemInfo = deviceAdb.loadSystemInfo(transportId, ::appendCommand)
                                                } catch (error: Throwable) {
                                                    statusText = error.message ?: localized("shell.battery_simulation_failed")
                                                    appendError(localized("shell.battery_simulation_failed") + " - ${error.message ?: unknownError()}")
                                                } finally {
                                                    isRunning = false
                                                }
                                            }
                                        }
                                    },
                                    onScreenSizeControl = { size ->
                                        selectedReadyTransportOrReport()?.let { transportId ->
                                            scope.launch {
                                                isRunning = true
                                                statusText = localized("shell.changing_screen_resolution")
                                                appendStatus(localized("shell.change_screen_resolution_adb_s_arg0_shell_wm_size_arg1", transportId, size))
                                                try {
                                                    deviceAdb.modifyScreenSize(transportId, size, ::appendCommand)
                                                    statusText = localized("shell.screen_resolution_change_executed")
                                                    appendStatus(statusText)
                                                    deviceSystemInfo = deviceAdb.loadSystemInfo(transportId, ::appendCommand)
                                                } catch (error: Throwable) {
                                                    statusText = error.message ?: localized("shell.screen_resolution_change_failed")
                                                    appendError(localized("shell.screen_resolution_change_failed") + " - ${error.message ?: unknownError()}")
                                                } finally {
                                                    isRunning = false
                                                }
                                            }
                                        }
                                    },
                                    onScreenDensityControl = { density ->
                                        selectedReadyTransportOrReport()?.let { transportId ->
                                            scope.launch {
                                                isRunning = true
                                                statusText = localized("shell.changing_screen_density")
                                                appendStatus(localized("shell.change_screen_density_adb_s_arg0_shell_wm_density_arg1", transportId, density))
                                                try {
                                                    deviceAdb.modifyScreenDensity(transportId, density, ::appendCommand)
                                                    statusText = localized("shell.screen_density_change_executed")
                                                    appendStatus(statusText)
                                                    deviceSystemInfo = deviceAdb.loadSystemInfo(transportId, ::appendCommand)
                                                } catch (error: Throwable) {
                                                    statusText = error.message ?: localized("shell.screen_density_change_failed")
                                                    appendError(localized("shell.screen_density_change_failed") + " - ${error.message ?: unknownError()}")
                                                } finally {
                                                    isRunning = false
                                                }
                                            }
                                        }
                                    },
                                    state = deviceTestPanelState,
                                    onStateChange = { deviceTestPanelState = it },
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

                            TestModule.Intent -> {
                                IntentTestPanel(
                                    selectedDevice = selectedDevice,
                                    appCandidates = intentAppCandidates,
                                    classNameCandidates = intentClassNameCandidates,
                                    form = intentForm,
                                    onFormChange = { intentForm = it },
                                    templateName = intentTemplateName,
                                    onTemplateNameChange = { intentTemplateName = it },
                                    templates = intentTemplates,
                                    onSaveTemplate = ::saveIntentTemplate,
                                    onApplyTemplate = { template ->
                                        intentForm = template.form
                                        intentTemplateName = template.name
                                    },
                                    onDeleteTemplate = ::deleteIntentTemplate,
                                    validationResult = intentValidationResult,
                                    commandPreview = intentCommandPreview,
                                    lastResult = lastIntentResult,
                                    isRunning = isRunning,
                                    onExecute = ::runIntentCommand,
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
                                    currentCommandPreset = deviceLogModule.currentCommandPreset,
                                    savedCommandPresets = deviceLogModule.savedCommandPresets,
                                    isCommandEditorOpen = deviceLogModule.isCommandEditorOpen,
                                    editorCommandName = deviceLogModule.editorCommandName,
                                    editorCommandNameHasError = deviceLogModule.editorCommandNameHasError,
                                    editorCommandParts = deviceLogModule.editorCommandParts,
                                    editorFilterTag = deviceLogModule.editorFilterTag,
                                    editorFilterPriority = deviceLogModule.editorFilterPriority,
                                    editorRegex = deviceLogModule.editorRegex,
                                    editorPid = deviceLogModule.editorPid,
                                    editorMaxCount = deviceLogModule.editorMaxCount,
                                    editorRecentValue = deviceLogModule.editorRecentValue,
                                    onCaptureLogs = deviceLogModule::captureSelectedDeviceLogs,
                                    onStopCapture = deviceLogModule::stopCapture,
                                    onRevealLogFile = deviceLogModule::revealLogFile,
                                    onOpenCommandEditor = deviceLogModule::openCommandEditor,
                                    onCloseCommandEditor = deviceLogModule::closeCommandEditor,
                                    onEditorCommandNameChange = deviceLogModule::updateEditorCommandName,
                                    onEditorFilterTagChange = deviceLogModule::updateEditorFilterTag,
                                    onEditorFilterPriorityChange = deviceLogModule::updateEditorFilterPriority,
                                    onEditorRegexChange = deviceLogModule::updateEditorRegex,
                                    onEditorPidChange = deviceLogModule::updateEditorPid,
                                    onEditorMaxCountChange = deviceLogModule::updateEditorMaxCount,
                                    onEditorRecentValueChange = deviceLogModule::updateEditorRecentValue,
                                    onAddEditorCommandPart = deviceLogModule::addEditorCommandPart,
                                    onRemoveEditorCommandPart = deviceLogModule::removeEditorCommandPart,
                                    onAddEditorFilterPart = deviceLogModule::addEditorFilterPart,
                                    onAddEditorRegexPart = deviceLogModule::addEditorRegexPart,
                                    onAddEditorPidPart = deviceLogModule::addEditorPidPart,
                                    onAddEditorMaxCountPart = deviceLogModule::addEditorMaxCountPart,
                                    onAddEditorRecentPart = deviceLogModule::addEditorRecentPart,
                                    onSaveEditorCommandPreset = deviceLogModule::saveEditorCommandPreset,
                                    onApplyCommandPreset = deviceLogModule::applyCommandPreset,
                                    onDeleteCommandPreset = deviceLogModule::deleteCommandPreset,
                                    onRestoreDefaultCommandPreset = deviceLogModule::restoreDefaultCommandPreset,
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
                                        val transportId = selectedReadyDevice?.transportId
                                        if (serial != null && transportId != null) loadThirdPartyApps(serial, transportId)
                                    },
                                    onRefreshSystem = {
                                        val serial = selectedReadyDevice?.serialNumber
                                        val transportId = selectedReadyDevice?.transportId
                                        if (serial != null && transportId != null) loadSystemApps(serial, transportId)
                                    },
                                    onClearCache = ::clearApplicationListCache,
                                    onApplicationAction = ::runApplicationAction,
                                    onExportManifest = ::exportApplicationManifest,
                                    applicationDetailPackageName = applicationDetailPackageName,
                                    applicationDetailSections = applicationDetailSections,
                                    loadingApplicationDetailSection = loadingApplicationDetailSection,
                                    onLoadApplicationDetail = ::loadApplicationDetail,
                                    onTestIntent = ::openIntentTestFromApplicationDetail,
                                    isRunning = isRunning,
                                    state = applicationTestPanelState,
                                    onStateChange = { applicationTestPanelState = it },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            TestModule.Settings -> {
                                SettingsModuleContent(
                                    state = settingsModuleState,
                                    onStateChange = { settingsModuleState = it },
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
                                selectedDeviceTransportId = selectedDeviceTransportId,
                                isRunning = isRunning,
                                onRefresh = ::refreshDevices,
                                onPairWirelessDevice = ::pairWirelessDevice,
                                onConnectWirelessDevice = ::connectWirelessDevice,
                                onSelect = { device ->
                                    selectedDeviceTransportId = device.transportId
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
                                    applicationTestPanelState = applicationTestPanelState.copy(
                                        selectedAppPackageName = null,
                                        selectedDetailSection = ApplicationDetailSection.BASIC,
                                        detailSearchQuery = "",
                                    )

                                    deviceSystemInfo = null
                                    systemProperties = emptyList()
                                    deviceSystemInfoLoadedSerial = null
                                    devicePropertiesLoadedSerial = null
                                    deviceLogModule.clearIfIdle()

                                    if (device.isReady) {
                                        if (selectedTestModule == TestModule.DataFill) {
                                            dataFillModule.refreshStorageForDevice(device.transportId)
                                        } else if (selectedTestModule == TestModule.FileManager) {
                                            fileManagerModule.loadDirectory(
                                                deviceSerial = device.transportId,
                                                remotePath = fileManagerModule.appliedRootPath,
                                            )
                                        } else if (selectedTestModule == TestModule.Device) {
                                            loadDeviceSystemInfo(device.transportId)
                                            loadDeviceSystemProperties(device.transportId)
                                        } else if (selectedTestModule == TestModule.Log) {
                                            statusText = localized("shell.selected_device_arg0_ready_to_capture_logcat", device.serialNumber)
                                        } else {
                                            statusText = localized("shell.selected_device_arg0", device.serialNumber)
                                        }
                                    } else {
                                        statusText = localized("shell.device_unavailable_arg0", device.state)
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
                        deviceLabel = deviceLogModule.capturingDeviceLabel ?: localized("log.capture.capturing_title"),
                        onStop = deviceLogModule::stopCapture,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 88.dp, end = 40.dp),
                    )
                }
                if (isScreenRecording) {
                    ScreenRecordFloatingButton(
                        deviceLabel = screenRecordingDeviceLabel ?: localized("device.screen_record.recording_title"),
                        isPreparing = screenRecordingStartedAtMillis == null,
                        isStopping = screenRecordingStopRequestedAtMillis != null,
                        onStop = ::stopSelectedDeviceScreenRecording,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = if (isCapturingLogcat) 176.dp else 88.dp, end = 40.dp),
                    )
                }
            }
        }
    }
}
