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
import com.floatingmuseum.android.test.helper.localization.localized
import com.floatingmuseum.android.test.helper.localization.rememberAppStrings

private enum class InfoSection {
    BASIC,
    HARDWARE,
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
    onStartScreenRecording: () -> Unit,
    onStopScreenRecording: () -> Unit,
    onStartDeviceMirror: () -> Unit,
    onStopDeviceMirror: () -> Unit,
    onInstallApplications: () -> Unit,
    onQuickAction: (DeviceQuickAction) -> Unit,
    isRunning: Boolean,
    isScreenRecording: Boolean,
    isScreenRecordingThisDevice: Boolean,
    isDeviceMirroring: Boolean,
    isDeviceMirroringThisDevice: Boolean,
    lastScreenRecordResult: ScreenRecordResult?,
    onBatteryControl: ((args: List<String>) -> Unit)? = null,
    onScreenSizeControl: ((String) -> Unit)? = null,
    onScreenDensityControl: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val strings = rememberAppStrings()
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
                    text = strings.t("common.device.select_from_bottom_panel"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val hasReadyDevice = selectedDevice.isReady
            Row(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = strings.t("device.system_info"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        InfoSection.values().forEach { section ->
                            val isSelected = selectedSection == section
                            val title = when (section) {
                                InfoSection.BASIC -> strings.t("device.info.basic")
                                InfoSection.HARDWARE -> strings.t("device.info.hardware_label")
                                InfoSection.SCREEN -> strings.t("device.display")
                                InfoSection.BATTERY -> strings.t("device.battery")
                                InfoSection.PROPERTIES -> strings.t("device.properties")
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
                                            if (!isLoading && !isRunning && hasReadyDevice) {
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
                                            InfoRow(strings.t("device.serial_number_sn"), selectedDevice.serialNumber)
                                            if (selectedDevice.hasDistinctTransport) {
                                                InfoRow(strings.t("device.adb_transport"), selectedDevice.transportId)
                                            }
                                            InfoRow(strings.t("device.brand"), systemInfo.brand)
                                            InfoRow(strings.t("device.info.model"), systemInfo.model)
                                            InfoRow(strings.t("device.rom_version"), systemInfo.romVersion)
                                            InfoRow(strings.t("device.android_version"), systemInfo.androidVersion.withTextPrefix("Android "))
                                            InfoRow(strings.t("device.sdk_version"), systemInfo.sdkVersion.withTextPrefix("API "))
                                            InfoRow(strings.t("device.cpu_abi"), systemInfo.cpuAbi)
                                            InfoRow(strings.t("device.ip_address"), systemInfo.ipAddress)
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = strings.t("device.no_data_click_the_tab_above_to_refresh"),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                InfoSection.HARDWARE -> {
                                    if (systemInfo != null) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .verticalScroll(rememberScrollState()),
                                            verticalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                                )
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(16.dp),
                                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Text(
                                                        text = strings.t("device.cpu_info_proc_cpuinfo"),
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    InfoRow(strings.t("device.cpu_abi"), systemInfo.cpuAbi)
                                                    InfoRow(strings.t("device.processor"), systemInfo.cpuProcessor)
                                                    InfoRow(strings.t("device.info.hardware_section"), systemInfo.cpuHardware)
                                                    InfoRow(strings.t("device.cpu_architecture"), systemInfo.cpuArchitecture)
                                                    InfoRow(strings.t("device.core_count"), systemInfo.cpuCoreCount)
                                                    InfoTextBlock(strings.t("device.features"), systemInfo.cpuFeatures)
                                                }
                                            }

                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                                )
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(16.dp),
                                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Text(
                                                        text = strings.t("device.memory_info_proc_meminfo"),
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    InfoRow(strings.t("device.total_memory_memtotal"), systemInfo.memoryTotal)
                                                    InfoRow(strings.t("device.available_memory_memavailable"), systemInfo.memoryAvailable)
                                                    InfoRow(strings.t("device.free_memory_memfree"), systemInfo.memoryFree)
                                                    InfoRow(strings.t("device.buffers"), systemInfo.memoryBuffers)
                                                    InfoRow(strings.t("device.page_cache_cached"), systemInfo.memoryCached)
                                                    InfoRow(strings.t("device.swap_total"), systemInfo.memorySwapTotal)
                                                    InfoRow(strings.t("device.swap_free"), systemInfo.memorySwapFree)
                                                }
                                            }
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = strings.t("device.no_data_click_the_tab_above_to_refresh"),
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
                                            verticalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                                )
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(16.dp),
                                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Text(
                                                        text = strings.t("device.display_basics"),
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    InfoRow(strings.t("device.resolution"), systemInfo.screenSize)
                                                    InfoRow(strings.t("device.density"), systemInfo.screenDensity)
                                                    InfoRow(strings.t("device.refresh_rate"), systemInfo.displayRefreshRate)
                                                }
                                            }

                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                                )
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(16.dp),
                                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Text(
                                                        text = strings.t("device.display_details_dumpsys"),
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    InfoRow(strings.t("device.display_id_mdisplayid"), systemInfo.displayId)
                                                    InfoRow(strings.t("device.initial_config_init"), systemInfo.displayInit)
                                                    InfoRow(strings.t("device.current_config_cur"), systemInfo.displayCur)
                                                    InfoRow(strings.t("device.app_bounds_app"), systemInfo.displayApp)
                                                }
                                            }

                                            if (onScreenSizeControl != null || onScreenDensityControl != null) {
                                                Card(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                                                    )
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(16.dp),
                                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                                    ) {
                                                        Text(
                                                            text = strings.t("device.display_simulation_and_debug_testing_only"),
                                                            style = MaterialTheme.typography.titleSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.error
                                                        )

                                                        if (onScreenSizeControl != null) {
                                                            var mockSizeText by remember { mutableStateOf("") }
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Text(strings.t("device.set_resolution_for_example_1080x1920"), style = MaterialTheme.typography.bodyMedium)
                                                                Row(
                                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    OutlinedTextField(
                                                                        value = mockSizeText,
                                                                        onValueChange = { mockSizeText = it },
                                                                        modifier = Modifier.width(150.dp),
                                                                        singleLine = true,
                                                                        placeholder = { Text(strings.t("device.wxh")) }
                                                                    )
                                                                    Button(
                                                                        onClick = {
                                                                            if (mockSizeText.trim().isNotEmpty()) {
                                                                                onScreenSizeControl(mockSizeText.trim())
                                                                            }
                                                                        },
                                                                        enabled = !isRunning && hasReadyDevice && mockSizeText.isNotEmpty(),
                                                                        modifier = Modifier.height(36.dp)
                                                                    ) {
                                                                        Text(strings.t("common.change"))
                                                                    }
                                                                }
                                                            }
                                                            Button(
                                                                onClick = { onScreenSizeControl("reset") },
                                                                enabled = !isRunning && hasReadyDevice,
                                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                                modifier = Modifier.fillMaxWidth().height(40.dp)
                                                            ) {
                                                                Text(strings.t("device.reset_resolution"))
                                                            }
                                                        }

                                                        if (onScreenDensityControl != null) {
                                                            Spacer(modifier = Modifier.height(8.dp))
                                                            var mockDensityText by remember { mutableStateOf("") }
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Text(strings.t("device.set_density_for_example_480"), style = MaterialTheme.typography.bodyMedium)
                                                                Row(
                                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    OutlinedTextField(
                                                                        value = mockDensityText,
                                                                        onValueChange = { mockDensityText = it.filter { c -> c.isDigit() } },
                                                                        modifier = Modifier.width(150.dp),
                                                                        singleLine = true,
                                                                        placeholder = { Text("DPI") }
                                                                    )
                                                                    Button(
                                                                        onClick = {
                                                                            if (mockDensityText.trim().isNotEmpty()) {
                                                                                onScreenDensityControl(mockDensityText.trim())
                                                                            }
                                                                        },
                                                                        enabled = !isRunning && hasReadyDevice && mockDensityText.isNotEmpty(),
                                                                        modifier = Modifier.height(36.dp)
                                                                    ) {
                                                                        Text(strings.t("common.change"))
                                                                    }
                                                                }
                                                            }
                                                            Button(
                                                                onClick = { onScreenDensityControl("reset") },
                                                                enabled = !isRunning && hasReadyDevice,
                                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                                modifier = Modifier.fillMaxWidth().height(40.dp)
                                                            ) {
                                                                Text(strings.t("device.reset_density"))
                                                            }
                                                        }
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
                                                text = strings.t("device.no_data_click_the_tab_above_to_refresh"),
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
                                            verticalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                                )
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(16.dp),
                                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Text(
                                                        text = strings.t("device.battery_basics"),
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    InfoRow(strings.t("device.battery_level"), systemInfo.batteryLevel?.let { "$it%" } ?: localized("device.unknown"))
                                                    InfoRow(strings.t("device.battery_status"), systemInfo.batteryStatus)
                                                    InfoRow(strings.t("device.battery_health"), systemInfo.batteryHealth)
                                                    InfoRow(strings.t("device.battery_temperature"), systemInfo.batteryTemp)
                                                    InfoRow(strings.t("device.battery_voltage"), systemInfo.batteryVoltage)
                                                    InfoRow(strings.t("device.battery_present"), systemInfo.batteryPresent)
                                                    InfoRow(strings.t("device.battery_technology"), systemInfo.batteryTechnology)
                                                }
                                            }

                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                                )
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(16.dp),
                                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Text(
                                                        text = strings.t("device.power_and_charging"),
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    InfoRow(strings.t("device.ac_powered"), systemInfo.batteryACPowered)
                                                    InfoRow(strings.t("device.usb_powered"), systemInfo.batteryUSBPowered)
                                                    InfoRow(strings.t("device.wireless_powered"), systemInfo.batteryWirelessPowered)
                                                    InfoRow(strings.t("device.max_charging_current"), systemInfo.batteryMaxChargingCurrent)
                                                    InfoRow(strings.t("device.max_charging_voltage"), systemInfo.batteryMaxChargingVoltage)
                                                    InfoRow(strings.t("device.charge_counter"), systemInfo.batteryChargeCounter)
                                                }
                                            }

                                            if (onBatteryControl != null) {
                                                Card(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                                                    )
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(16.dp),
                                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                                    ) {
                                                        Text(
                                                            text = strings.t("device.battery_simulation_debug_testing_only"),
                                                            style = MaterialTheme.typography.titleSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.error
                                                        )

                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(strings.t("device.simulate_unplug_charger"), style = MaterialTheme.typography.bodyMedium)
                                                            Button(
                                                                onClick = { onBatteryControl(listOf("unplug")) },
                                                                enabled = !isRunning && hasReadyDevice,
                                                                modifier = Modifier.height(36.dp)
                                                            ) {
                                                                Text(strings.t("device.unplug"))
                                                            }
                                                        }

                                                        var mockLevelText by remember { mutableStateOf("") }
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(strings.t("device.simulate_battery_level"), style = MaterialTheme.typography.bodyMedium)
                                                            Row(
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                OutlinedTextField(
                                                                    value = mockLevelText,
                                                                    onValueChange = { mockLevelText = it.filter { c -> c.isDigit() } },
                                                                    modifier = Modifier.width(100.dp),
                                                                    singleLine = true,
                                                                    placeholder = { Text("0-100") }
                                                                )
                                                                Button(
                                                                    onClick = {
                                                                        val lvl = mockLevelText.toIntOrNull()
                                                                        if (lvl != null && lvl in 0..100) {
                                                                            onBatteryControl(listOf("set", "level", lvl.toString()))
                                                                        }
                                                                    },
                                                                    enabled = !isRunning && hasReadyDevice && mockLevelText.isNotEmpty(),
                                                                    modifier = Modifier.height(36.dp)
                                                                ) {
                                                                    Text(strings.t("device.set"))
                                                                }
                                                            }
                                                        }

                                                        var mockTempText by remember { mutableStateOf("") }
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(strings.t("device.simulate_temperature_c"), style = MaterialTheme.typography.bodyMedium)
                                                            Row(
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                OutlinedTextField(
                                                                    value = mockTempText,
                                                                    onValueChange = { mockTempText = it },
                                                                    modifier = Modifier.width(100.dp),
                                                                    singleLine = true,
                                                                    placeholder = { Text(strings.t("device.example_32")) }
                                                                )
                                                                Button(
                                                                    onClick = {
                                                                        val tempDouble = mockTempText.toDoubleOrNull()
                                                                        if (tempDouble != null) {
                                                                            val tempInt = (tempDouble * 10).toInt()
                                                                            onBatteryControl(listOf("set", "temp", tempInt.toString()))
                                                                        }
                                                                    },
                                                                    enabled = !isRunning && hasReadyDevice && mockTempText.isNotEmpty(),
                                                                    modifier = Modifier.height(36.dp)
                                                                ) {
                                                                    Text(strings.t("device.set"))
                                                                }
                                                            }
                                                        }

                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(strings.t("device.simulate_charging_status"), style = MaterialTheme.typography.bodyMedium)
                                                            Row(
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Button(
                                                                    onClick = { onBatteryControl(listOf("set", "status", "2")) },
                                                                    enabled = !isRunning && hasReadyDevice,
                                                                    modifier = Modifier.height(32.dp),
                                                                    contentPadding = ButtonDefaults.ContentPadding
                                                                ) {
                                                                    Text(strings.t("device.battery.action.charging"), style = MaterialTheme.typography.labelMedium)
                                                                }
                                                                Button(
                                                                    onClick = { onBatteryControl(listOf("set", "status", "3")) },
                                                                    enabled = !isRunning && hasReadyDevice,
                                                                    modifier = Modifier.height(32.dp),
                                                                    contentPadding = ButtonDefaults.ContentPadding
                                                                ) {
                                                                    Text(strings.t("device.battery.action.discharging"), style = MaterialTheme.typography.labelMedium)
                                                                }
                                                                Button(
                                                                    onClick = { onBatteryControl(listOf("set", "status", "5")) },
                                                                    enabled = !isRunning && hasReadyDevice,
                                                                    modifier = Modifier.height(32.dp),
                                                                    contentPadding = ButtonDefaults.ContentPadding
                                                                ) {
                                                                    Text(strings.t("device.battery.action.full"), style = MaterialTheme.typography.labelMedium)
                                                                }
                                                            }
                                                        }

                                                        Spacer(modifier = Modifier.height(8.dp))

                                                        Button(
                                                            onClick = { onBatteryControl(listOf("reset")) },
                                                            enabled = !isRunning && hasReadyDevice,
                                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                            modifier = Modifier.fillMaxWidth().height(40.dp)
                                                        ) {
                                                            Text(strings.t("device.reset_battery_state"))
                                                        }
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
                                                text = strings.t("device.no_data_click_the_tab_above_to_refresh"),
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
                                            label = { Text(strings.t("device.search_properties_for_example_ro_product")) },
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
                                                    text = strings.t("device.no_property_data_click_the_tab_above_to_reload"),
                                                    modifier = Modifier.align(Alignment.Center),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            } else if (filteredProperties.isEmpty()) {
                                                Text(
                                                    text = strings.t("device.no_matching_property_values"),
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

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = strings.t("device.shortcuts"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (isScreenRecordingThisDevice) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = strings.t("device.screen_record.recording_hint"),
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    } else if (isDeviceMirroringThisDevice) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = strings.t("device.mirror.running_hint"),
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    } else if (lastScreenRecordResult != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = strings.t("device.screen_record.last_result"),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                SelectionContainer {
                                    Text(
                                        text = lastScreenRecordResult.localPath,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }

                    val canRunDeviceShortcut = !isRunning && hasReadyDevice
                    val shortcutActions = listOf(
                        DeviceShortcutAction(DeviceQuickAction.SHUTDOWN.displayLabel(), true, canRunDeviceShortcut) { onQuickAction(DeviceQuickAction.SHUTDOWN) },
                        DeviceShortcutAction(strings.t("device.reboot"), true, canRunDeviceShortcut, onReboot),
                        DeviceShortcutAction(DeviceQuickAction.REBOOT_RECOVERY.displayLabel(), true, canRunDeviceShortcut) { onQuickAction(DeviceQuickAction.REBOOT_RECOVERY) },
                        DeviceShortcutAction(DeviceQuickAction.REBOOT_FASTBOOT.displayLabel(), true, canRunDeviceShortcut) { onQuickAction(DeviceQuickAction.REBOOT_FASTBOOT) },
                        DeviceShortcutAction(DeviceQuickAction.CURRENT_ACTIVITY.displayLabel(), false, canRunDeviceShortcut) { onQuickAction(DeviceQuickAction.CURRENT_ACTIVITY) },
                        DeviceShortcutAction(strings.t("device.screenshot"), false, canRunDeviceShortcut, onTakeScreenshot),
                        DeviceShortcutAction(
                            label = if (isDeviceMirroringThisDevice) {
                                strings.t("device.mirror.stop")
                            } else {
                                strings.t("device.mirror.start")
                            },
                            isDanger = isDeviceMirroringThisDevice,
                            enabled = if (isDeviceMirroringThisDevice) {
                                true
                            } else {
                                canRunDeviceShortcut && !isDeviceMirroring
                            },
                        ) {
                            if (isDeviceMirroringThisDevice) {
                                onStopDeviceMirror()
                            } else {
                                onStartDeviceMirror()
                            }
                        },
                        DeviceShortcutAction(
                            label = if (isScreenRecordingThisDevice) {
                                strings.t("device.screen_record.stop")
                            } else {
                                strings.t("device.screen_record.start")
                            },
                            isDanger = isScreenRecordingThisDevice,
                            enabled = if (isScreenRecordingThisDevice) {
                                !isRunning
                            } else {
                                canRunDeviceShortcut && !isScreenRecording
                            },
                        ) {
                            if (isScreenRecordingThisDevice) {
                                onStopScreenRecording()
                            } else {
                                onStartScreenRecording()
                            }
                        },
                        DeviceShortcutAction(strings.t("device.install_apk"), false, canRunDeviceShortcut, onInstallApplications),
                        DeviceShortcutAction(DeviceQuickAction.POWER.displayLabel(), false, canRunDeviceShortcut) { onQuickAction(DeviceQuickAction.POWER) },
                        DeviceShortcutAction(DeviceQuickAction.MENU.displayLabel(), false, canRunDeviceShortcut) { onQuickAction(DeviceQuickAction.MENU) },
                        DeviceShortcutAction(DeviceQuickAction.HOME.displayLabel(), false, canRunDeviceShortcut) { onQuickAction(DeviceQuickAction.HOME) },
                        DeviceShortcutAction(DeviceQuickAction.BACK.displayLabel(), false, canRunDeviceShortcut) { onQuickAction(DeviceQuickAction.BACK) },
                        DeviceShortcutAction(DeviceQuickAction.VOLUME_UP.displayLabel(), false, canRunDeviceShortcut) { onQuickAction(DeviceQuickAction.VOLUME_UP) },
                        DeviceShortcutAction(DeviceQuickAction.VOLUME_DOWN.displayLabel(), false, canRunDeviceShortcut) { onQuickAction(DeviceQuickAction.VOLUME_DOWN) },
                        DeviceShortcutAction(DeviceQuickAction.MUTE.displayLabel(), false, canRunDeviceShortcut) { onQuickAction(DeviceQuickAction.MUTE) },
                        DeviceShortcutAction(DeviceQuickAction.WAKE.displayLabel(), false, canRunDeviceShortcut) { onQuickAction(DeviceQuickAction.WAKE) },
                        DeviceShortcutAction(DeviceQuickAction.SLEEP.displayLabel(), false, canRunDeviceShortcut) { onQuickAction(DeviceQuickAction.SLEEP) },
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
                                        enabled = action.enabled,
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
    val enabled: Boolean,
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

@Composable
private fun InfoRow(label: String, value: DeviceInfoValue) {
    InfoRow(label, value.displayText())
}

@Composable
private fun InfoTextBlock(label: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SelectionContainer {
            Text(
                text = value,
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
                    .padding(8.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun InfoTextBlock(label: String, value: DeviceInfoValue) {
    InfoTextBlock(label, value.displayText())
}

private fun DeviceInfoValue.withTextPrefix(prefix: String): DeviceInfoValue {
    return when (this) {
        is DeviceInfoValue.Text -> DeviceInfoValue.Text(prefix + value)
        else -> this
    }
}

private fun DeviceInfoValue.displayText(): String {
    return when (this) {
        DeviceInfoValue.Unknown -> localized("device.unknown")
        is DeviceInfoValue.Text -> value
        is DeviceInfoValue.BooleanValue -> localized(if (value) "device.yes" else "device.no")
        is DeviceInfoValue.Localized -> localized(token.localizationKey)
        is DeviceInfoValue.PhysicalOverride -> "$overrideValue (${localized("device.physical")} $physicalValue)"
    }
}

private val DeviceInfoToken.localizationKey: String
    get() = when (this) {
        DeviceInfoToken.BatteryStatusCharging -> "device.battery.status.charging"
        DeviceInfoToken.BatteryStatusDischarging -> "device.battery.status.discharging"
        DeviceInfoToken.BatteryStatusNotCharging -> "device.not_charging"
        DeviceInfoToken.BatteryStatusFull -> "device.battery.status.full"
        DeviceInfoToken.BatteryHealthGood -> "device.good"
        DeviceInfoToken.BatteryHealthOverheated -> "device.overheated"
        DeviceInfoToken.BatteryHealthDamaged -> "device.damaged"
        DeviceInfoToken.BatteryHealthOverVoltage -> "device.over_voltage"
        DeviceInfoToken.BatteryHealthUnknownFailure -> "device.unknown_failure"
        DeviceInfoToken.BatteryHealthCold -> "device.cold"
    }
