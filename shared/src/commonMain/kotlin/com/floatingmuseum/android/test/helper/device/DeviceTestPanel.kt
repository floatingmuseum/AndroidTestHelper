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
    onInstallApplications: () -> Unit,
    onQuickAction: (DeviceQuickAction) -> Unit,
    isRunning: Boolean,
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
                    text = strings.t("auto.select_a_connected_device_in_device_state_from_the_b.cc929da4"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
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
                        text = strings.t("auto.device_system_info.d1a713da"),
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
                                InfoSection.BASIC -> strings.t("auto.basic.0c39f352")
                                InfoSection.HARDWARE -> strings.t("auto.hardware.d0b7f811")
                                InfoSection.SCREEN -> strings.t("auto.display.d27e495a")
                                InfoSection.BATTERY -> strings.t("auto.battery.d9ec8cf9")
                                InfoSection.PROPERTIES -> strings.t("auto.properties.73ceec76")
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
                                            InfoRow(strings.t("auto.serial_number_sn.40aab042"), selectedDevice.serialNumber)
                                            InfoRow(strings.t("auto.brand.6f078946"), systemInfo.brand)
                                            InfoRow(strings.t("auto.model.075ee47c"), systemInfo.model)
                                            InfoRow(strings.t("auto.rom_version.8ad66e7b"), systemInfo.romVersion)
                                            InfoRow(strings.t("auto.android_version.f996a9e2"), "Android ${systemInfo.androidVersion}")
                                            InfoRow(strings.t("auto.sdk_version.de747d51"), "API ${systemInfo.sdkVersion}")
                                            InfoRow(strings.t("auto.cpu_abi.83639c12"), systemInfo.cpuAbi)
                                            InfoRow(strings.t("auto.ip_address.d7440b41"), systemInfo.ipAddress)
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = strings.t("auto.no_data_click_the_tab_above_to_refresh.66509312"),
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
                                                        text = strings.t("auto.cpu_info_proc_cpuinfo.94d19a44"),
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    InfoRow(strings.t("auto.cpu_abi.83639c12"), systemInfo.cpuAbi)
                                                    InfoRow(strings.t("auto.processor.6e51e73d"), systemInfo.cpuProcessor)
                                                    InfoRow(strings.t("auto.hardware.72c25591"), systemInfo.cpuHardware)
                                                    InfoRow(strings.t("auto.cpu_architecture.ef8462da"), systemInfo.cpuArchitecture)
                                                    InfoRow(strings.t("auto.core_count.2d95c453"), systemInfo.cpuCoreCount)
                                                    InfoTextBlock(strings.t("auto.features.2f092021"), systemInfo.cpuFeatures)
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
                                                        text = strings.t("auto.memory_info_proc_meminfo.019a0fab"),
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    InfoRow(strings.t("auto.total_memory_memtotal.ffda36cd"), systemInfo.memoryTotal)
                                                    InfoRow(strings.t("auto.available_memory_memavailable.6802331e"), systemInfo.memoryAvailable)
                                                    InfoRow(strings.t("auto.free_memory_memfree.50518212"), systemInfo.memoryFree)
                                                    InfoRow(strings.t("auto.buffers.d8615be7"), systemInfo.memoryBuffers)
                                                    InfoRow(strings.t("auto.page_cache_cached.68cc0b6f"), systemInfo.memoryCached)
                                                    InfoRow(strings.t("auto.swap_total.eee9203a"), systemInfo.memorySwapTotal)
                                                    InfoRow(strings.t("auto.swap_free.043d2880"), systemInfo.memorySwapFree)
                                                }
                                            }
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = strings.t("auto.no_data_click_the_tab_above_to_refresh.66509312"),
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
                                                        text = strings.t("auto.display_basics.34f61660"),
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    InfoRow(strings.t("auto.resolution.9ac821bf"), systemInfo.screenSize)
                                                    InfoRow(strings.t("auto.density.ed216e02"), systemInfo.screenDensity)
                                                    InfoRow(strings.t("auto.refresh_rate.19cef23c"), systemInfo.displayRefreshRate)
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
                                                        text = strings.t("auto.display_details_dumpsys.0cd4784c"),
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    InfoRow(strings.t("auto.display_id_mdisplayid.e644ddad"), systemInfo.displayId)
                                                    InfoRow(strings.t("auto.initial_config_init.363a60c0"), systemInfo.displayInit)
                                                    InfoRow(strings.t("auto.current_config_cur.fc1723b4"), systemInfo.displayCur)
                                                    InfoRow(strings.t("auto.app_bounds_app.ba35a79a"), systemInfo.displayApp)
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
                                                            text = strings.t("auto.display_simulation_and_debug_testing_only.870fad5f"),
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
                                                                Text(strings.t("auto.set_resolution_for_example_1080x1920.18f9e905"), style = MaterialTheme.typography.bodyMedium)
                                                                Row(
                                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    OutlinedTextField(
                                                                        value = mockSizeText,
                                                                        onValueChange = { mockSizeText = it },
                                                                        modifier = Modifier.width(150.dp),
                                                                        singleLine = true,
                                                                        placeholder = { Text(strings.t("auto.wxh.54a58df4")) }
                                                                    )
                                                                    Button(
                                                                        onClick = {
                                                                            if (mockSizeText.trim().isNotEmpty()) {
                                                                                onScreenSizeControl(mockSizeText.trim())
                                                                            }
                                                                        },
                                                                        enabled = !isRunning && mockSizeText.isNotEmpty(),
                                                                        modifier = Modifier.height(36.dp)
                                                                    ) {
                                                                        Text(strings.t("auto.change.8df5aec0"))
                                                                    }
                                                                }
                                                            }
                                                            Button(
                                                                onClick = { onScreenSizeControl("reset") },
                                                                enabled = !isRunning,
                                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                                modifier = Modifier.fillMaxWidth().height(40.dp)
                                                            ) {
                                                                Text(strings.t("auto.reset_resolution.4c2e59c1"))
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
                                                                Text(strings.t("auto.set_density_for_example_480.b4f52d08"), style = MaterialTheme.typography.bodyMedium)
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
                                                                        enabled = !isRunning && mockDensityText.isNotEmpty(),
                                                                        modifier = Modifier.height(36.dp)
                                                                    ) {
                                                                        Text(strings.t("auto.change.8df5aec0"))
                                                                    }
                                                                }
                                                            }
                                                            Button(
                                                                onClick = { onScreenDensityControl("reset") },
                                                                enabled = !isRunning,
                                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                                modifier = Modifier.fillMaxWidth().height(40.dp)
                                                            ) {
                                                                Text(strings.t("auto.reset_density.19e95dc1"))
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
                                                text = strings.t("auto.no_data_click_the_tab_above_to_refresh.66509312"),
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
                                                        text = strings.t("auto.battery_basics.e38715b8"),
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    InfoRow(strings.t("auto.battery_level.b0f37339"), systemInfo.batteryLevel?.let { "$it%" } ?: localized("auto.unknown.54dfee5a"))
                                                    InfoRow(strings.t("auto.battery_status.d609a438"), systemInfo.batteryStatus)
                                                    InfoRow(strings.t("auto.battery_health.8191d436"), systemInfo.batteryHealth)
                                                    InfoRow(strings.t("auto.battery_temperature.0c6d3606"), systemInfo.batteryTemp)
                                                    InfoRow(strings.t("auto.battery_voltage.4bc0ea51"), systemInfo.batteryVoltage)
                                                    InfoRow(strings.t("auto.battery_present.a1891aa6"), systemInfo.batteryPresent)
                                                    InfoRow(strings.t("auto.battery_technology.2c6e4682"), systemInfo.batteryTechnology)
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
                                                        text = strings.t("auto.power_and_charging.160c9e6c"),
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    InfoRow(strings.t("auto.ac_powered.d9bd01e2"), systemInfo.batteryACPowered)
                                                    InfoRow(strings.t("auto.usb_powered.5c1aacb2"), systemInfo.batteryUSBPowered)
                                                    InfoRow(strings.t("auto.wireless_powered.0df00564"), systemInfo.batteryWirelessPowered)
                                                    InfoRow(strings.t("auto.max_charging_current.68c1dbea"), systemInfo.batteryMaxChargingCurrent)
                                                    InfoRow(strings.t("auto.max_charging_voltage.d1c313fc"), systemInfo.batteryMaxChargingVoltage)
                                                    InfoRow(strings.t("auto.charge_counter.059e5794"), systemInfo.batteryChargeCounter)
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
                                                            text = strings.t("auto.battery_simulation_debug_testing_only.7d1d178b"),
                                                            style = MaterialTheme.typography.titleSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.error
                                                        )

                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(strings.t("auto.simulate_unplug_charger.dcd73478"), style = MaterialTheme.typography.bodyMedium)
                                                            Button(
                                                                onClick = { onBatteryControl(listOf("unplug")) },
                                                                enabled = !isRunning,
                                                                modifier = Modifier.height(36.dp)
                                                            ) {
                                                                Text(strings.t("auto.unplug.9446fbf6"))
                                                            }
                                                        }

                                                        var mockLevelText by remember { mutableStateOf("") }
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(strings.t("auto.simulate_battery_level.d7b0d914"), style = MaterialTheme.typography.bodyMedium)
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
                                                                    enabled = !isRunning && mockLevelText.isNotEmpty(),
                                                                    modifier = Modifier.height(36.dp)
                                                                ) {
                                                                    Text(strings.t("auto.set.e5416817"))
                                                                }
                                                            }
                                                        }

                                                        var mockTempText by remember { mutableStateOf("") }
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(strings.t("auto.simulate_temperature_c.2ab4fc6e"), style = MaterialTheme.typography.bodyMedium)
                                                            Row(
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                OutlinedTextField(
                                                                    value = mockTempText,
                                                                    onValueChange = { mockTempText = it },
                                                                    modifier = Modifier.width(100.dp),
                                                                    singleLine = true,
                                                                    placeholder = { Text(strings.t("auto.example_32.0d7fd982")) }
                                                                )
                                                                Button(
                                                                    onClick = {
                                                                        val tempDouble = mockTempText.toDoubleOrNull()
                                                                        if (tempDouble != null) {
                                                                            val tempInt = (tempDouble * 10).toInt()
                                                                            onBatteryControl(listOf("set", "temp", tempInt.toString()))
                                                                        }
                                                                    },
                                                                    enabled = !isRunning && mockTempText.isNotEmpty(),
                                                                    modifier = Modifier.height(36.dp)
                                                                ) {
                                                                    Text(strings.t("auto.set.e5416817"))
                                                                }
                                                            }
                                                        }

                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(strings.t("auto.simulate_charging_status.4e59822f"), style = MaterialTheme.typography.bodyMedium)
                                                            Row(
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Button(
                                                                    onClick = { onBatteryControl(listOf("set", "status", "2")) },
                                                                    enabled = !isRunning,
                                                                    modifier = Modifier.height(32.dp),
                                                                    contentPadding = ButtonDefaults.ContentPadding
                                                                ) {
                                                                    Text(strings.t("auto.charging.ba3a87b7"), style = MaterialTheme.typography.labelMedium)
                                                                }
                                                                Button(
                                                                    onClick = { onBatteryControl(listOf("set", "status", "3")) },
                                                                    enabled = !isRunning,
                                                                    modifier = Modifier.height(32.dp),
                                                                    contentPadding = ButtonDefaults.ContentPadding
                                                                ) {
                                                                    Text(strings.t("auto.discharging.5ffc49ce"), style = MaterialTheme.typography.labelMedium)
                                                                }
                                                                Button(
                                                                    onClick = { onBatteryControl(listOf("set", "status", "5")) },
                                                                    enabled = !isRunning,
                                                                    modifier = Modifier.height(32.dp),
                                                                    contentPadding = ButtonDefaults.ContentPadding
                                                                ) {
                                                                    Text(strings.t("auto.full.d0c02083"), style = MaterialTheme.typography.labelMedium)
                                                                }
                                                            }
                                                        }

                                                        Spacer(modifier = Modifier.height(8.dp))

                                                        Button(
                                                            onClick = { onBatteryControl(listOf("reset")) },
                                                            enabled = !isRunning,
                                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                            modifier = Modifier.fillMaxWidth().height(40.dp)
                                                        ) {
                                                            Text(strings.t("auto.reset_battery_state.445e086a"))
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
                                                text = strings.t("auto.no_data_click_the_tab_above_to_refresh.66509312"),
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
                                            label = { Text(strings.t("auto.search_properties_for_example_ro_product.cedb8dec")) },
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
                                                    text = strings.t("auto.no_property_data_click_the_tab_above_to_reload.4ce8ae8f"),
                                                    modifier = Modifier.align(Alignment.Center),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            } else if (filteredProperties.isEmpty()) {
                                                Text(
                                                    text = strings.t("auto.no_matching_property_values.c9f81559"),
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
                        text = strings.t("auto.device_shortcuts.879e2961"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    val shortcutActions = listOf(
                        DeviceShortcutAction(DeviceQuickAction.SHUTDOWN.displayLabel(), true) { onQuickAction(DeviceQuickAction.SHUTDOWN) },
                        DeviceShortcutAction(strings.t("auto.reboot.dface16c"), true, onReboot),
                        DeviceShortcutAction(DeviceQuickAction.REBOOT_RECOVERY.displayLabel(), true) { onQuickAction(DeviceQuickAction.REBOOT_RECOVERY) },
                        DeviceShortcutAction(DeviceQuickAction.REBOOT_FASTBOOT.displayLabel(), true) { onQuickAction(DeviceQuickAction.REBOOT_FASTBOOT) },
                        DeviceShortcutAction(DeviceQuickAction.CURRENT_ACTIVITY.displayLabel(), false) { onQuickAction(DeviceQuickAction.CURRENT_ACTIVITY) },
                        DeviceShortcutAction(strings.t("auto.screenshot.970a0cf1"), false, onTakeScreenshot),
                        DeviceShortcutAction(strings.t("auto.install_apk.769a503b"), false, onInstallApplications),
                        DeviceShortcutAction(DeviceQuickAction.POWER.displayLabel(), false) { onQuickAction(DeviceQuickAction.POWER) },
                        DeviceShortcutAction(DeviceQuickAction.MENU.displayLabel(), false) { onQuickAction(DeviceQuickAction.MENU) },
                        DeviceShortcutAction(DeviceQuickAction.HOME.displayLabel(), false) { onQuickAction(DeviceQuickAction.HOME) },
                        DeviceShortcutAction(DeviceQuickAction.BACK.displayLabel(), false) { onQuickAction(DeviceQuickAction.BACK) },
                        DeviceShortcutAction(DeviceQuickAction.VOLUME_UP.displayLabel(), false) { onQuickAction(DeviceQuickAction.VOLUME_UP) },
                        DeviceShortcutAction(DeviceQuickAction.VOLUME_DOWN.displayLabel(), false) { onQuickAction(DeviceQuickAction.VOLUME_DOWN) },
                        DeviceShortcutAction(DeviceQuickAction.MUTE.displayLabel(), false) { onQuickAction(DeviceQuickAction.MUTE) },
                        DeviceShortcutAction(DeviceQuickAction.WAKE.displayLabel(), false) { onQuickAction(DeviceQuickAction.WAKE) },
                        DeviceShortcutAction(DeviceQuickAction.SLEEP.displayLabel(), false) { onQuickAction(DeviceQuickAction.SLEEP) },
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
    val displayValue = localizedDeviceInfoValue(value)
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
                text = displayValue,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun InfoTextBlock(label: String, value: String) {
    val displayValue = localizedDeviceInfoValue(value)
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
                text = displayValue,
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

private fun localizedDeviceInfoValue(value: String): String {
    val exact = when (value) {
        "未知" -> localized("auto.unknown.54dfee5a")
        "未知型号" -> localized("auto.unknown_model.5dd7a8ba")
        "是" -> localized("auto.yes.7b0a458c")
        "否" -> localized("auto.no.787e69b1")
        "充电中" -> localized("auto.charging.e9731331")
        "放电中" -> localized("auto.discharging.05a399cd")
        "未充电" -> localized("auto.not_charging.f5b3b4ef")
        "已充满" -> localized("auto.full.2fe76bfb")
        "良好" -> localized("auto.good.792f7d68")
        "过热" -> localized("auto.overheated.a38f47c3")
        "损坏" -> localized("auto.damaged.76620589")
        "过压" -> localized("auto.over_voltage.8f51bb17")
        "未知故障" -> localized("auto.unknown_failure.0fe42e25")
        "过冷" -> localized("auto.cold.becb7a06")
        else -> null
    }
    if (exact != null) return exact

    return value
        .replace("未知型号", localized("auto.unknown_model.5dd7a8ba"))
        .replace("未知故障", localized("auto.unknown_failure.0fe42e25"))
        .replace("未知", localized("auto.unknown.54dfee5a"))
        .replace("物理:", localized("auto.physical.ae8ea9ac"))
}
