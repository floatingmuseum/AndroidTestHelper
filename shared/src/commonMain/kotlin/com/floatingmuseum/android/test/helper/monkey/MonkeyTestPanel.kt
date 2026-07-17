package com.floatingmuseum.android.test.helper.monkey

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.floatingmuseum.android.test.helper.AndroidDevice
import com.floatingmuseum.android.test.helper.app.InstalledAppInfo
import com.floatingmuseum.android.test.helper.app.filterInstalledApps
import com.floatingmuseum.android.test.helper.localization.rememberAppStrings
import kotlinx.coroutines.launch

@Composable
internal fun MonkeyModuleContent(
    controller: MonkeyModuleController,
    selectedDevice: AndroidDevice?,
    appCandidates: List<InstalledAppInfo>,
    isGlobalRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    val strings = rememberAppStrings()
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    var pendingDelete by remember { mutableStateOf<MonkeyPreset?>(null) }
    var showOverwriteConfirm by remember { mutableStateOf(false) }
    var showWholeDeviceConfirm by remember { mutableStateOf(false) }
    val commandPreview = buildMonkeyAdbCommand(
        deviceSerial = selectedDevice?.transportId ?: "<serial>",
        form = controller.form,
    ).displayCommand

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val contentModifier = Modifier.fillMaxSize().padding(12.dp)
        if (maxWidth >= 1050.dp) {
            Row(
                modifier = contentModifier,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PresetColumn(
                    controller = controller,
                    onSave = {
                        val exists = controller.presets.any {
                            it.name.equals(controller.presetName.trim(), ignoreCase = true)
                        }
                        if (exists) showOverwriteConfirm = true else controller.savePreset()
                    },
                    onDelete = { pendingDelete = it },
                    modifier = Modifier.weight(0.72f).fillMaxHeight(),
                )
                ConfigurationColumn(
                    controller = controller,
                    selectedDevice = selectedDevice,
                    appCandidates = appCandidates,
                    isGlobalRunning = isGlobalRunning,
                    onRun = {
                        if (controller.form.targetScope == MonkeyTargetScope.WholeDevice) {
                            showWholeDeviceConfirm = true
                        } else {
                            controller.startRun()
                        }
                    },
                    modifier = Modifier.weight(1.25f).fillMaxHeight(),
                )
                ResultColumn(
                    controller = controller,
                    commandPreview = commandPreview,
                    onCopy = { clipboardManager.setText(AnnotatedString(commandPreview)) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        } else {
            Column(
                modifier = contentModifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PresetColumn(
                    controller = controller,
                    onSave = {
                        val exists = controller.presets.any {
                            it.name.equals(controller.presetName.trim(), ignoreCase = true)
                        }
                        if (exists) showOverwriteConfirm = true else controller.savePreset()
                    },
                    onDelete = { pendingDelete = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                ConfigurationColumn(
                    controller = controller,
                    selectedDevice = selectedDevice,
                    appCandidates = appCandidates,
                    isGlobalRunning = isGlobalRunning,
                    onRun = {
                        if (controller.form.targetScope == MonkeyTargetScope.WholeDevice) {
                            showWholeDeviceConfirm = true
                        } else {
                            controller.startRun()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                ResultColumn(
                    controller = controller,
                    commandPreview = commandPreview,
                    onCopy = { clipboardManager.setText(AnnotatedString(commandPreview)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    pendingDelete?.let { preset ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(strings.t("monkey.preset.delete_title")) },
            text = { Text(strings.t("monkey.preset.delete_message_arg0", preset.name)) },
            confirmButton = {
                TextButton(onClick = {
                    controller.deletePreset(preset)
                    pendingDelete = null
                }) { Text(strings.t("common.action.delete")) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(strings.t("common.cancel")) }
            },
        )
    }
    if (showOverwriteConfirm) {
        AlertDialog(
            onDismissRequest = { showOverwriteConfirm = false },
            title = { Text(strings.t("monkey.preset.overwrite_title")) },
            text = { Text(strings.t("monkey.preset.overwrite_message_arg0", controller.presetName.trim())) },
            confirmButton = {
                TextButton(onClick = {
                    controller.savePreset()
                    showOverwriteConfirm = false
                }) { Text(strings.t("monkey.preset.overwrite")) }
            },
            dismissButton = {
                TextButton(onClick = { showOverwriteConfirm = false }) { Text(strings.t("common.cancel")) }
            },
        )
    }
    if (showWholeDeviceConfirm) {
        AlertDialog(
            onDismissRequest = { showWholeDeviceConfirm = false },
            title = { Text(strings.t("monkey.whole_device.confirm_title")) },
            text = { Text(strings.t("monkey.whole_device.confirm_message")) },
            confirmButton = {
                TextButton(onClick = {
                    showWholeDeviceConfirm = false
                    controller.startRun()
                }) { Text(strings.t("monkey.run")) }
            },
            dismissButton = {
                TextButton(onClick = { showWholeDeviceConfirm = false }) { Text(strings.t("common.cancel")) }
            },
        )
    }
}

@Composable
private fun PresetColumn(
    controller: MonkeyModuleController,
    onSave: () -> Unit,
    onDelete: (MonkeyPreset) -> Unit,
    modifier: Modifier,
) {
    val strings = rememberAppStrings()
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(strings.t("monkey.presets"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = controller.presetName,
                onValueChange = controller::updatePresetName,
                label = { Text(strings.t("monkey.preset.name")) },
                singleLine = true,
                enabled = !controller.isRunning,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onSave,
                enabled = controller.presetName.trim().isNotBlank() && !controller.isRunning,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(strings.t("monkey.preset.save")) }
            if (controller.presets.isEmpty()) {
                Text(
                    strings.t("monkey.preset.empty"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    controller.presets.forEach { preset ->
                        Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Text(preset.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                Text(
                                    monkeyPresetSummary(preset, strings),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(
                                        onClick = { controller.applyPreset(preset) },
                                        enabled = !controller.isRunning,
                                    ) { Text(strings.t("monkey.preset.apply")) }
                                    TextButton(
                                        onClick = { onDelete(preset) },
                                        enabled = !controller.isRunning,
                                    ) { Text(strings.t("common.action.delete")) }
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
private fun ConfigurationColumn(
    controller: MonkeyModuleController,
    selectedDevice: AndroidDevice?,
    appCandidates: List<InstalledAppInfo>,
    isGlobalRunning: Boolean,
    onRun: () -> Unit,
    modifier: Modifier,
) {
    val strings = rememberAppStrings()
    val form = controller.form
    var advancedExpanded by remember { mutableStateOf(false) }
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(strings.t("monkey.configuration"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        selectedDevice?.let { "${it.model} · ${it.serialNumber}" }
                            ?: strings.t("common.device.select_from_bottom_panel"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                if (controller.isRunning) {
                    Button(
                        onClick = controller::stopRun,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) { Text(strings.t("common.action.stop")) }
                } else {
                    Button(
                        onClick = onRun,
                        enabled = selectedDevice?.isReady == true && !isGlobalRunning && controller.validationResult.isValid,
                    ) { Text(strings.t("monkey.run")) }
                }
            }

            TargetScopeSection(form, controller::updateForm, appCandidates, controller.isRunning)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = form.eventCount,
                    onValueChange = { controller.updateForm(form.copy(eventCount = it.filter(Char::isDigit))) },
                    label = { Text(strings.t("monkey.event_count")) },
                    singleLine = true,
                    enabled = !controller.isRunning,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = form.throttleMs,
                    onValueChange = { controller.updateForm(form.copy(throttleMs = it.filter(Char::isDigit))) },
                    label = { Text(strings.t("monkey.throttle_ms")) },
                    singleLine = true,
                    enabled = !controller.isRunning,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = form.seed,
                    onValueChange = { value ->
                        controller.updateForm(form.copy(seed = value.filterIndexed { index, char -> char.isDigit() || (char == '-' && index == 0) }))
                    },
                    label = { Text(strings.t("monkey.seed")) },
                    trailingIcon = {
                        ParameterHelpTooltip(
                            text = strings.t("monkey.seed.help"),
                            contentDescription = strings.t("monkey.help.content_description"),
                        )
                    },
                    singleLine = true,
                    enabled = !controller.isRunning,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = controller::regenerateSeed, enabled = !controller.isRunning) {
                    Text(strings.t("monkey.seed.regenerate"))
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = strings.t("monkey.verbosity"),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                ParameterHelpTooltip(
                    text = strings.t("monkey.verbosity.help"),
                    contentDescription = strings.t("monkey.help.content_description"),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0..2).forEach { verbosity ->
                    Button(
                        onClick = { controller.updateForm(form.copy(verbosity = verbosity)) },
                        enabled = !controller.isRunning,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (form.verbosity == verbosity) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (form.verbosity == verbosity) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) { Text(verbosity.toString()) }
                }
            }
            OutlinedTextField(
                value = form.categories.joinToString(", "),
                onValueChange = { value ->
                    controller.updateForm(form.copy(categories = value.split(",").map(String::trim).filter(String::isNotBlank)))
                },
                label = { Text(strings.t("monkey.categories")) },
                trailingIcon = {
                    ParameterHelpTooltip(
                        text = strings.t("monkey.categories.help"),
                        contentDescription = strings.t("monkey.help.content_description"),
                    )
                },
                enabled = !controller.isRunning,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedButton(onClick = { advancedExpanded = !advancedExpanded }, modifier = Modifier.fillMaxWidth()) {
                Text(strings.t(if (advancedExpanded) "monkey.advanced.collapse" else "monkey.advanced.expand"))
            }
            if (advancedExpanded) {
                EventPercentageSection(form, controller::updateForm, controller.isRunning)
                ErrorPolicySection(form, controller::updateForm, controller.isRunning)
            }

            controller.validationResult.errors.forEach { error ->
                Text(
                    text = error.argument?.let { strings.t(error.key, it) } ?: strings.t(error.key),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun TargetScopeSection(
    form: MonkeyTestForm,
    onFormChange: (MonkeyTestForm) -> Unit,
    appCandidates: List<InstalledAppInfo>,
    isRunning: Boolean,
) {
    val strings = rememberAppStrings()
    var packageInput by remember { mutableStateOf("") }
    var showAppPicker by remember { mutableStateOf(false) }
    var appSearch by remember { mutableStateOf("") }
    Text(strings.t("monkey.target_scope"), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MonkeyTargetScope.entries.forEach { scope ->
            val selected = form.targetScope == scope
            Button(
                onClick = { onFormChange(form.copy(targetScope = scope)) },
                enabled = !isRunning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) { Text(strings.t(monkeyTargetScopeKey(scope))) }
        }
    }
    if (form.targetScope == MonkeyTargetScope.WholeDevice) {
        Text(
            strings.t("monkey.whole_device.warning"),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = packageInput,
                onValueChange = { packageInput = it.trim() },
                label = { Text(strings.t("app.package_name")) },
                singleLine = true,
                enabled = !isRunning,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = {
                    val value = packageInput.trim()
                    if (value.isNotBlank()) {
                        onFormChange(form.copy(packages = form.packages + value))
                        packageInput = ""
                    }
                },
                enabled = packageInput.isNotBlank() && !isRunning,
            ) { Text(strings.t("common.action.add")) }
            OutlinedButton(
                onClick = { showAppPicker = true },
                enabled = appCandidates.isNotEmpty() && !isRunning,
            ) { Text(strings.t("monkey.select_app")) }
        }
        form.packages.forEach { packageName ->
            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(packageName, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                TextButton(
                    onClick = { onFormChange(form.copy(packages = form.packages.filterNot { it == packageName })) },
                    enabled = !isRunning,
                ) { Text(strings.t("common.action.delete")) }
            }
        }
    }

    if (showAppPicker) {
        val filteredApps = remember(appCandidates, appSearch) {
            filterInstalledApps(appCandidates, appSearch)
                .sortedWith(compareBy({ it.appName.lowercase() }, { it.packageName.lowercase() }))
        }
        AlertDialog(
            onDismissRequest = { showAppPicker = false },
            title = { Text(strings.t("monkey.select_app")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = appSearch,
                        onValueChange = { appSearch = it },
                        label = { Text(strings.t("app.search_app_name_or_package")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        filteredApps.forEach { app ->
                            TextButton(
                                onClick = {
                                    if (app.packageName !in form.packages) {
                                        onFormChange(form.copy(packages = form.packages + app.packageName))
                                    }
                                    showAppPicker = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(app.appName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(app.packageName, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppPicker = false }) { Text(strings.t("common.close")) }
            },
        )
    }
}

@Composable
private fun EventPercentageSection(
    form: MonkeyTestForm,
    onFormChange: (MonkeyTestForm) -> Unit,
    isRunning: Boolean,
) {
    val strings = rememberAppStrings()
    Text(strings.t("monkey.event_percentages"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Text(strings.t("monkey.event_percentages.hint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    PercentageRow(
        leftLabel = strings.t("monkey.percentage.touch"),
        leftValue = form.eventPercentages.touch,
        onLeftChange = { onFormChange(form.copy(eventPercentages = form.eventPercentages.copy(touch = it))) },
        rightLabel = strings.t("monkey.percentage.motion"),
        rightValue = form.eventPercentages.motion,
        onRightChange = { onFormChange(form.copy(eventPercentages = form.eventPercentages.copy(motion = it))) },
        enabled = !isRunning,
    )
    PercentageRow(
        leftLabel = strings.t("monkey.percentage.trackball"),
        leftValue = form.eventPercentages.trackball,
        onLeftChange = { onFormChange(form.copy(eventPercentages = form.eventPercentages.copy(trackball = it))) },
        rightLabel = strings.t("monkey.percentage.navigation"),
        rightValue = form.eventPercentages.navigation,
        onRightChange = { onFormChange(form.copy(eventPercentages = form.eventPercentages.copy(navigation = it))) },
        enabled = !isRunning,
    )
    PercentageRow(
        leftLabel = strings.t("monkey.percentage.major_navigation"),
        leftValue = form.eventPercentages.majorNavigation,
        onLeftChange = { onFormChange(form.copy(eventPercentages = form.eventPercentages.copy(majorNavigation = it))) },
        rightLabel = strings.t("monkey.percentage.system_keys"),
        rightValue = form.eventPercentages.systemKeys,
        onRightChange = { onFormChange(form.copy(eventPercentages = form.eventPercentages.copy(systemKeys = it))) },
        enabled = !isRunning,
    )
    PercentageRow(
        leftLabel = strings.t("monkey.percentage.app_switch"),
        leftValue = form.eventPercentages.appSwitch,
        onLeftChange = { onFormChange(form.copy(eventPercentages = form.eventPercentages.copy(appSwitch = it))) },
        rightLabel = strings.t("monkey.percentage.any_event"),
        rightValue = form.eventPercentages.anyEvent,
        onRightChange = { onFormChange(form.copy(eventPercentages = form.eventPercentages.copy(anyEvent = it))) },
        enabled = !isRunning,
    )
}

@Composable
private fun PercentageRow(
    leftLabel: String,
    leftValue: String,
    onLeftChange: (String) -> Unit,
    rightLabel: String,
    rightValue: String,
    onRightChange: (String) -> Unit,
    enabled: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = leftValue,
            onValueChange = { onLeftChange(it.filter(Char::isDigit)) },
            label = { Text(leftLabel) },
            placeholder = { Text("0-100") },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = rightValue,
            onValueChange = { onRightChange(it.filter(Char::isDigit)) },
            label = { Text(rightLabel) },
            placeholder = { Text("0-100") },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ErrorPolicySection(
    form: MonkeyTestForm,
    onFormChange: (MonkeyTestForm) -> Unit,
    isRunning: Boolean,
) {
    val strings = rememberAppStrings()
    Text(strings.t("monkey.error_policy"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    OptionCheckbox(strings.t("monkey.ignore_crashes"), form.ignoreCrashes, !isRunning) {
        onFormChange(form.copy(ignoreCrashes = it))
    }
    OptionCheckbox(strings.t("monkey.ignore_timeouts"), form.ignoreTimeouts, !isRunning) {
        onFormChange(form.copy(ignoreTimeouts = it))
    }
    OptionCheckbox(strings.t("monkey.ignore_security_exceptions"), form.ignoreSecurityExceptions, !isRunning) {
        onFormChange(form.copy(ignoreSecurityExceptions = it))
    }
    OptionCheckbox(strings.t("monkey.kill_process_after_error"), form.killProcessAfterError, !isRunning) {
        onFormChange(form.copy(killProcessAfterError = it))
    }
    OptionCheckbox(strings.t("monkey.monitor_native_crashes"), form.monitorNativeCrashes, !isRunning) {
        onFormChange(form.copy(monitorNativeCrashes = it))
    }
}

@Composable
private fun OptionCheckbox(label: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ResultColumn(
    controller: MonkeyModuleController,
    commandPreview: String,
    onCopy: () -> Unit,
    modifier: Modifier,
) {
    val strings = rememberAppStrings()
    val outputScroll = rememberScrollState()
    LaunchedEffect(controller.visibleOutputLines.size) {
        outputScroll.scrollTo(outputScroll.maxValue)
    }
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(strings.t("monkey.command_preview"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onCopy) { Text(strings.t("common.copy")) }
            }
            SelectionContainer {
                Text(
                    commandPreview,
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Text(strings.t("monkey.live_output"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            SelectionContainer {
                Text(
                    text = controller.visibleOutputLines.joinToString("\n").ifBlank { strings.t("monkey.output.empty") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 360.dp).background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp).verticalScroll(outputScroll),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
            controller.lastResult?.let { result ->
                MonkeyResultSummary(result)
                if (result.reportFilePath.isNotBlank()) {
                    OutlinedButton(
                        onClick = { controller.revealReport(result.reportFilePath) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(strings.t("monkey.open_report")) }
                }
            }
        }
    }
}

@Composable
private fun MonkeyResultSummary(result: MonkeyRunResult) {
    val strings = rememberAppStrings()
    val summary = result.summary
    Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(strings.t(monkeyEndStateMessageKey(result.endState)), fontWeight = FontWeight.SemiBold)
            Text(strings.t("monkey.summary.injected_arg0_arg1", summary.injectedEvents, summary.plannedEvents), style = MaterialTheme.typography.bodySmall)
            Text(strings.t("monkey.summary.incidents_arg0_arg1_arg2", summary.crashCount, summary.anrCount, summary.nativeCrashCount), style = MaterialTheme.typography.bodySmall)
            Text(strings.t("monkey.summary.dropped_arg0", summary.droppedEvents.keys + summary.droppedEvents.pointers + summary.droppedEvents.trackballs + summary.droppedEvents.flips + summary.droppedEvents.rotations), style = MaterialTheme.typography.bodySmall)
            Text(result.reportFilePath, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ParameterHelpTooltip(
    text: String,
    contentDescription: String,
) {
    val tooltipState = rememberTooltipState()
    val coroutineScope = rememberCoroutineScope()

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip(maxWidth = 320.dp) {
                Text(text = text, style = MaterialTheme.typography.bodySmall)
            }
        },
        state = tooltipState,
    ) {
        IconButton(
            onClick = { coroutineScope.launch { tooltipState.show() } },
            modifier = Modifier.semantics { this.contentDescription = contentDescription },
        ) {
            Surface(
                modifier = Modifier.size(20.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "?",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

private fun monkeyTargetScopeKey(scope: MonkeyTargetScope): String = when (scope) {
    MonkeyTargetScope.WholeDevice -> "monkey.scope.whole_device"
    MonkeyTargetScope.Packages -> "monkey.scope.packages"
}

private fun monkeyPresetSummary(preset: MonkeyPreset, strings: com.floatingmuseum.android.test.helper.localization.AppStrings): String {
    val target = when (preset.form.targetScope) {
        MonkeyTargetScope.WholeDevice -> strings.t("monkey.scope.whole_device")
        MonkeyTargetScope.Packages -> preset.form.packages.joinToString(", ")
    }
    return "$target · ${preset.form.eventCount} · ${preset.form.throttleMs}ms"
}
