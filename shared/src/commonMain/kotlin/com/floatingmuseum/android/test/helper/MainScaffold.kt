package com.floatingmuseum.android.test.helper

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.floatingmuseum.android.test.helper.localization.isErrorCommandLog
import com.floatingmuseum.android.test.helper.localization.isStatusCommandLog
import com.floatingmuseum.android.test.helper.localization.rememberAppStrings
import kotlinx.coroutines.launch

@Composable
internal fun ModuleSwitcher(
    selectedModule: TestModule,
    isRunning: Boolean,
    onSelect: (TestModule) -> Unit,
) {
    val strings = rememberAppStrings()
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
                    Text(module.title(strings))
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun SplitContent(
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
internal fun DevicePanel(
    devices: List<AndroidDevice>,
    selectedDeviceTransportId: String?,
    isRunning: Boolean,
    onRefresh: () -> Unit,
    onPairWirelessDevice: suspend (String, String, String) -> String,
    onConnectWirelessDevice: suspend (String, String) -> String,
    onSelect: (AndroidDevice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = rememberAppStrings()
    var showWirelessDialog by remember { mutableStateOf(false) }
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
                    text = strings.t("shell.connected_devices"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { showWirelessDialog = true },
                        enabled = !isRunning,
                    ) {
                        Text(strings.t("shell.wireless_connect"))
                    }
                    Button(onClick = onRefresh, enabled = !isRunning) {
                        Text(strings.t("shell.device.refresh"))
                    }
                }
            }

            if (devices.isEmpty()) {
                Text(strings.t("shell.no_devices_found_connect_usb_and_refresh"))
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    devices.forEach { device ->
                        val isSelected = device.transportId == selectedDeviceTransportId
                        Button(
                            onClick = { onSelect(device) },
                            enabled = !isRunning && device.isReady,
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
                                disabledContainerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                },
                                disabledContentColor = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.70f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f)
                                },
                            ),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text("SN: ${device.serialNumber}")
                                if (device.hasDistinctTransport) {
                                    Text("${strings.t("shell.device.transport")}: ${device.transportId}")
                                }
                                Text("${strings.t("shell.device.model")}: ${device.model}")
                                if (!device.isReady) {
                                    Text(
                                        text = "${strings.t("shell.device.state")}: ${device.state}",
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showWirelessDialog) {
        WirelessAdbDialog(
            onDismiss = { showWirelessDialog = false },
            onPairWirelessDevice = onPairWirelessDevice,
            onConnectWirelessDevice = onConnectWirelessDevice,
        )
    }
}

@Composable
private fun WirelessAdbDialog(
    onDismiss: () -> Unit,
    onPairWirelessDevice: suspend (String, String, String) -> String,
    onConnectWirelessDevice: suspend (String, String) -> String,
) {
    val strings = rememberAppStrings()
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf("") }
    var pairingPort by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }
    var debugPort by remember { mutableStateOf("") }
    var isPairing by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    var pairResult by remember { mutableStateOf<String?>(null) }
    var connectResult by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val isBusy = isPairing || isConnecting
    val hasPaired = pairResult != null
    val isHostValid = isValidWirelessIpAddress(host)
    val showHostError = host.isNotBlank() && !isHostValid
    val canPair = isHostValid && isValidWirelessPort(pairingPort) && pairingCode.isNotBlank() && !isBusy
    val canConnect = hasPaired && isHostValid && isValidWirelessPort(debugPort) && !isBusy

    AlertDialog(
        onDismissRequest = {
            if (!isBusy) onDismiss()
        },
        title = {
            Text(
                text = strings.t("shell.wireless_dialog.title"),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = strings.t("shell.wireless_dialog.guide"),
                    style = MaterialTheme.typography.bodyMedium,
                )
                WirelessStep(
                    title = strings.t("shell.wireless_dialog.step_1_title"),
                    body = strings.t("shell.wireless_dialog.step_1_body"),
                )
                Text(
                    text = strings.t("shell.wireless_dialog.port_notice"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                WirelessStep(
                    title = strings.t("shell.wireless_dialog.step_2_title"),
                    body = strings.t("shell.wireless_dialog.step_2_body"),
                )
                OutlinedTextField(
                    value = host,
                    onValueChange = {
                        host = it.trim()
                        pairResult = null
                        connectResult = null
                    },
                    label = { Text(strings.t("shell.wireless_dialog.device_ip")) },
                    placeholder = { Text("192.168.1.23") },
                    singleLine = true,
                    enabled = !isBusy,
                    isError = showHostError,
                    supportingText = {
                        if (showHostError) {
                            Text(strings.t("shell.wireless_dialog.device_ip_invalid"))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = pairingPort,
                        onValueChange = {
                            pairingPort = it.filter(Char::isDigit)
                            pairResult = null
                            connectResult = null
                        },
                        label = { Text(strings.t("shell.wireless_dialog.pairing_port")) },
                        placeholder = { Text("37123") },
                        singleLine = true,
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = pairingCode,
                        onValueChange = {
                            pairingCode = it.filter(Char::isDigit)
                            pairResult = null
                            connectResult = null
                        },
                        label = { Text(strings.t("shell.wireless_dialog.pairing_code")) },
                        placeholder = { Text("123456") },
                        singleLine = true,
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f),
                    )
                }
                Button(
                    onClick = {
                        scope.launch {
                            isPairing = true
                            errorMessage = null
                            pairResult = null
                            runCatching {
                                onPairWirelessDevice(host, pairingPort, pairingCode)
                            }.onSuccess { output ->
                                pairResult = output.ifBlank { strings.t("shell.wireless_dialog.pair_success") }
                                connectResult = null
                            }.onFailure { error ->
                                errorMessage = error.message ?: strings.t("common.error.unknown")
                            }
                            isPairing = false
                        }
                    },
                    enabled = canPair,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isPairing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(strings.t("shell.wireless_dialog.pair"))
                    }
                }
                pairResult?.let { result ->
                    WirelessActionResult(
                        title = strings.t("shell.wireless_dialog.pair_result"),
                        body = result,
                    )
                    WirelessStep(
                        title = strings.t("shell.wireless_dialog.step_3_title"),
                        body = strings.t("shell.wireless_dialog.step_3_body"),
                    )
                }
                OutlinedTextField(
                    value = debugPort,
                    onValueChange = {
                        debugPort = it.filter(Char::isDigit)
                        connectResult = null
                    },
                    label = { Text(strings.t("shell.wireless_dialog.debug_port")) },
                    placeholder = { Text("5555") },
                    singleLine = true,
                    enabled = hasPaired && !isBusy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        scope.launch {
                            isConnecting = true
                            errorMessage = null
                            connectResult = null
                            runCatching {
                                onConnectWirelessDevice(host, debugPort)
                            }.onSuccess { output ->
                                connectResult = output.ifBlank { strings.t("shell.wireless_dialog.connect_success") }
                                onDismiss()
                            }.onFailure { error ->
                                errorMessage = error.message ?: strings.t("common.error.unknown")
                            }
                            isConnecting = false
                        }
                    },
                    enabled = canConnect,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(strings.t("shell.wireless_dialog.connect"))
                    }
                }
                connectResult?.let { result ->
                    WirelessActionResult(
                        title = strings.t("shell.wireless_dialog.connect_result"),
                        body = result,
                    )
                }
                errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    text = strings.t("shell.wireless_dialog.command_log_hint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isBusy,
            ) {
                Text(strings.t("common.close"))
            }
        },
    )
}

@Composable
private fun WirelessStep(
    title: String,
    body: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WirelessActionResult(
    title: String,
    body: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
        SelectionContainer {
            Text(
                text = body.trim(),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

private fun isValidWirelessPort(value: String): Boolean {
    val port = value.toIntOrNull() ?: return false
    return port in 1..65535
}

internal fun isValidWirelessIpAddress(value: String): Boolean {
    val segments = value.trim().split(".")
    return segments.size == 4 && segments.all { segment ->
        segment.isNotEmpty() &&
            segment.length <= 3 &&
            segment.all(Char::isDigit) &&
            segment.toIntOrNull()?.let { it in 0..255 } == true
    }
}

@Composable
internal fun CommandLogPanel(
    commandLog: List<String>,
    onClearCommandLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = rememberAppStrings()
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = strings.t("shell.command_log"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = onClearCommandLog,
                    enabled = commandLog.isNotEmpty(),
                ) {
                    Text(strings.t("shell.clear"))
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp)
                    .verticalScroll(scrollState),
            ) {
                SelectionContainer {
                    if (commandLog.isEmpty()) {
                        Text(strings.t("shell.no_commands"))
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            commandLog.forEach { command ->
                                val textColor = when {
                                    isErrorCommandLog(command) -> {
                                        MaterialTheme.colorScheme.error
                                    }
                                    isStatusCommandLog(command) -> {
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
