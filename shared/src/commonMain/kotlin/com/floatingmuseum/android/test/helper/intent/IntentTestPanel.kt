package com.floatingmuseum.android.test.helper.intent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.floatingmuseum.android.test.helper.AndroidDevice
import com.floatingmuseum.android.test.helper.app.InstalledAppInfo
import com.floatingmuseum.android.test.helper.localization.rememberAppStrings

@Composable
internal fun IntentTestPanel(
    selectedDevice: AndroidDevice?,
    appCandidates: List<InstalledAppInfo>,
    form: IntentTestForm,
    onFormChange: (IntentTestForm) -> Unit,
    templateName: String,
    onTemplateNameChange: (String) -> Unit,
    templates: List<IntentTemplate>,
    onSaveTemplate: () -> Unit,
    onApplyTemplate: (IntentTemplate) -> Unit,
    onDeleteTemplate: (IntentTemplate) -> Unit,
    validationResult: IntentValidationResult,
    commandPreview: String,
    lastResult: IntentExecutionResult?,
    isRunning: Boolean,
    onExecute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = rememberAppStrings()
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    var pendingDeleteTemplate by remember { mutableStateOf<IntentTemplate?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val useColumns = maxWidth >= 980.dp
        val contentModifier = Modifier.fillMaxSize().padding(12.dp)
        val arrangement = Arrangement.spacedBy(12.dp)
        if (useColumns) {
            Row(
                modifier = contentModifier,
                horizontalArrangement = arrangement,
            ) {
                TemplateColumn(
                    templateName = templateName,
                    onTemplateNameChange = onTemplateNameChange,
                    templates = templates,
                    onSaveTemplate = onSaveTemplate,
                    onApplyTemplate = onApplyTemplate,
                    onRequestDeleteTemplate = { pendingDeleteTemplate = it },
                    modifier = Modifier.weight(0.72f).fillMaxHeight(),
                )
                IntentFormColumn(
                    selectedDevice = selectedDevice,
                    appCandidates = appCandidates,
                    form = form,
                    onFormChange = onFormChange,
                    validationResult = validationResult,
                    isRunning = isRunning,
                    onExecute = onExecute,
                    modifier = Modifier.weight(1.35f).fillMaxHeight(),
                )
                PreviewColumn(
                    commandPreview = commandPreview,
                    lastResult = lastResult,
                    onCopyCommand = {
                        clipboardManager.setText(AnnotatedString(commandPreview))
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        } else {
            Column(
                modifier = contentModifier.verticalScroll(rememberScrollState()),
                verticalArrangement = arrangement,
            ) {
                TemplateColumn(
                    templateName = templateName,
                    onTemplateNameChange = onTemplateNameChange,
                    templates = templates,
                    onSaveTemplate = onSaveTemplate,
                    onApplyTemplate = onApplyTemplate,
                    onRequestDeleteTemplate = { pendingDeleteTemplate = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                IntentFormColumn(
                    selectedDevice = selectedDevice,
                    appCandidates = appCandidates,
                    form = form,
                    onFormChange = onFormChange,
                    validationResult = validationResult,
                    isRunning = isRunning,
                    onExecute = onExecute,
                    modifier = Modifier.fillMaxWidth(),
                )
                PreviewColumn(
                    commandPreview = commandPreview,
                    lastResult = lastResult,
                    onCopyCommand = {
                        clipboardManager.setText(AnnotatedString(commandPreview))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    pendingDeleteTemplate?.let { template ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTemplate = null },
            title = { Text(strings.t("intent.template.delete_confirm_title")) },
            text = { Text(strings.t("intent.template.delete_confirm_message_arg0", template.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteTemplate(template)
                        pendingDeleteTemplate = null
                    },
                ) {
                    Text(strings.t("common.action.delete"))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteTemplate = null }) {
                    Text(strings.t("common.cancel"))
                }
            },
        )
    }
}

@Composable
private fun TemplateColumn(
    templateName: String,
    onTemplateNameChange: (String) -> Unit,
    templates: List<IntentTemplate>,
    onSaveTemplate: () -> Unit,
    onApplyTemplate: (IntentTemplate) -> Unit,
    onRequestDeleteTemplate: (IntentTemplate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = rememberAppStrings()
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = strings.t("intent.templates"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = templateName,
                onValueChange = onTemplateNameChange,
                label = { Text(strings.t("intent.template.name")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onSaveTemplate,
                enabled = templateName.trim().isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(strings.t("intent.template.save_current"))
            }
            if (templates.isEmpty()) {
                Text(
                    text = strings.t("intent.template.empty"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    templates.forEach { template ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            ),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = template.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = strings.t(intentModeLabelKey(template.form.mode)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = intentTemplateSummary(template.form),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { onApplyTemplate(template) }) {
                                        Text(strings.t("intent.template.apply"))
                                    }
                                    TextButton(onClick = { onRequestDeleteTemplate(template) }) {
                                        Text(strings.t("common.action.delete"))
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

@Composable
private fun IntentFormColumn(
    selectedDevice: AndroidDevice?,
    appCandidates: List<InstalledAppInfo>,
    form: IntentTestForm,
    onFormChange: (IntentTestForm) -> Unit,
    validationResult: IntentValidationResult,
    isRunning: Boolean,
    onExecute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = rememberAppStrings()
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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = strings.t("intent.form"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = selectedDevice?.let { "${it.model} · ${it.serialNumber}" }
                            ?: strings.t("common.device.select_from_bottom_panel"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Button(
                    onClick = onExecute,
                    enabled = selectedDevice?.isReady == true && !isRunning && validationResult.isValid,
                ) {
                    Text(strings.t("intent.execute"))
                }
            }

            ModeSwitcher(
                mode = form.mode,
                onModeChange = { mode ->
                    val nextAction = when {
                        mode == IntentCommandMode.Start && form.action.isBlank() -> "android.intent.action.VIEW"
                        mode == IntentCommandMode.Broadcast && form.action == "android.intent.action.VIEW" -> ""
                        else -> form.action
                    }
                    onFormChange(form.copy(mode = mode, action = nextAction))
                },
            )

            PackageFields(
                appCandidates = appCandidates,
                form = form,
                onFormChange = onFormChange,
            )

            OutlinedTextField(
                value = form.action,
                onValueChange = { onFormChange(form.copy(action = it)) },
                label = { Text(strings.t("intent.action")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.dataUri,
                onValueChange = { onFormChange(form.copy(dataUri = it)) },
                label = { Text(strings.t("intent.data_uri")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.categories.joinToString(", "),
                onValueChange = { value ->
                    onFormChange(form.copy(categories = value.split(",").map { it.trim() }.filter { it.isNotBlank() }))
                },
                label = { Text(strings.t("intent.categories")) },
                modifier = Modifier.fillMaxWidth(),
            )

            FlagSection(form = form, onFormChange = onFormChange)
            ExtrasSection(form = form, onFormChange = onFormChange)

            if (!validationResult.isValid) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    validationResult.errors.forEach { error ->
                        Text(
                            text = validationErrorText(error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeSwitcher(
    mode: IntentCommandMode,
    onModeChange: (IntentCommandMode) -> Unit,
) {
    val strings = rememberAppStrings()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IntentCommandMode.entries.forEach { option ->
            val selected = option == mode
            Button(
                onClick = { onModeChange(option) },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
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
                Text(strings.t(intentModeLabelKey(option)))
            }
        }
    }
}

@Composable
private fun PackageFields(
    appCandidates: List<InstalledAppInfo>,
    form: IntentTestForm,
    onFormChange: (IntentTestForm) -> Unit,
) {
    val strings = rememberAppStrings()
    var showAppPicker by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = form.packageName,
            onValueChange = { onFormChange(form.copy(packageName = it)) },
            label = { Text(strings.t("app.package_name")) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(
            onClick = { showAppPicker = true },
            enabled = appCandidates.isNotEmpty(),
        ) {
            Text(strings.t("intent.select_app"))
        }
    }
    OutlinedTextField(
        value = form.className,
        onValueChange = { onFormChange(form.copy(className = it)) },
        label = { Text(strings.t("intent.class_name")) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    if (showAppPicker) {
        AlertDialog(
            onDismissRequest = { showAppPicker = false },
            title = { Text(strings.t("intent.select_app")) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    appCandidates.sortedBy { it.appName.lowercase() }.forEach { app ->
                        TextButton(
                            onClick = {
                                onFormChange(form.copy(packageName = app.packageName))
                                showAppPicker = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(app.appName.ifBlank { app.packageName }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppPicker = false }) {
                    Text(strings.t("common.cancel"))
                }
            },
        )
    }
}

@Composable
private fun FlagSection(
    form: IntentTestForm,
    onFormChange: (IntentTestForm) -> Unit,
) {
    val strings = rememberAppStrings()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = strings.t("intent.flags"),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        when (form.mode) {
            IntentCommandMode.Start -> IntentStartFlag.entries.forEach { flag ->
                FlagRow(
                    checked = flag in form.startFlags,
                    label = strings.t(intentStartFlagLabelKey(flag)),
                    onCheckedChange = { checked ->
                        val next = if (checked) form.startFlags + flag else form.startFlags - flag
                        onFormChange(form.copy(startFlags = next))
                    },
                )
            }
            IntentCommandMode.Broadcast -> IntentBroadcastFlag.entries.forEach { flag ->
                FlagRow(
                    checked = flag in form.broadcastFlags,
                    label = strings.t(intentBroadcastFlagLabelKey(flag)),
                    onCheckedChange = { checked ->
                        val next = if (checked) form.broadcastFlags + flag else form.broadcastFlags - flag
                        onFormChange(form.copy(broadcastFlags = next))
                    },
                )
            }
        }
    }
}

@Composable
private fun FlagRow(
    checked: Boolean,
    label: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ExtrasSection(
    form: IntentTestForm,
    onFormChange: (IntentTestForm) -> Unit,
) {
    val strings = rememberAppStrings()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = strings.t("intent.extras"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedButton(
                onClick = { onFormChange(form.copy(extras = form.extras + IntentExtra())) },
            ) {
                Text(strings.t("intent.extra.add"))
            }
        }
        form.extras.forEachIndexed { index, extra ->
            ExtraRow(
                extra = extra,
                onChange = { next ->
                    onFormChange(form.copy(extras = form.extras.toMutableList().also { it[index] = next }))
                },
                onRemove = {
                    onFormChange(form.copy(extras = form.extras.filterIndexed { itemIndex, _ -> itemIndex != index }))
                },
            )
        }
    }
}

@Composable
private fun ExtraRow(
    extra: IntentExtra,
    onChange: (IntentExtra) -> Unit,
    onRemove: () -> Unit,
) {
    val strings = rememberAppStrings()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = extra.key,
            onValueChange = { onChange(extra.copy(key = it)) },
            label = { Text(strings.t("intent.extra.key")) },
            singleLine = true,
            modifier = Modifier.weight(0.9f),
        )
        ExtraTypeMenu(
            type = extra.type,
            onTypeChange = { onChange(extra.copy(type = it)) },
            modifier = Modifier.weight(0.8f),
        )
        OutlinedTextField(
            value = extra.value,
            onValueChange = { onChange(extra.copy(value = it)) },
            label = { Text(strings.t("intent.extra.value")) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRemove) {
            Text(strings.t("common.action.delete"))
        }
    }
}

@Composable
private fun ExtraTypeMenu(
    type: IntentExtraType,
    onTypeChange: (IntentExtraType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = rememberAppStrings()
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(strings.t(intentExtraTypeLabelKey(type)), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            IntentExtraType.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(strings.t(intentExtraTypeLabelKey(option))) },
                    onClick = {
                        onTypeChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PreviewColumn(
    commandPreview: String,
    lastResult: IntentExecutionResult?,
    onCopyCommand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = rememberAppStrings()
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = strings.t("intent.command_preview"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedButton(onClick = onCopyCommand, enabled = commandPreview.isNotBlank()) {
                    Text(strings.t("common.copy"))
                }
            }
            SelectionContainer {
                Text(
                    text = commandPreview.ifBlank { strings.t("intent.command_preview_empty") },
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = strings.t("intent.latest_result"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (lastResult == null) {
                Text(
                    text = strings.t("intent.latest_result_empty"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (lastResult.isSuccess) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = if (lastResult.isSuccess) strings.t("intent.result.success") else strings.t("intent.result.failed"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        SelectionContainer {
                            Text(
                                text = lastResult.output.trim().ifBlank { strings.t("intent.result.no_output") },
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun intentModeLabelKey(mode: IntentCommandMode): String = when (mode) {
    IntentCommandMode.Start -> "intent.mode.start"
    IntentCommandMode.Broadcast -> "intent.mode.broadcast"
}

private fun intentExtraTypeLabelKey(type: IntentExtraType): String = when (type) {
    IntentExtraType.StringValue -> "intent.extra.type.string"
    IntentExtraType.IntValue -> "intent.extra.type.int"
    IntentExtraType.LongValue -> "intent.extra.type.long"
    IntentExtraType.BooleanValue -> "intent.extra.type.boolean"
    IntentExtraType.StringArray -> "intent.extra.type.string_array"
}

private fun intentStartFlagLabelKey(flag: IntentStartFlag): String = when (flag) {
    IntentStartFlag.NewTask -> "intent.flag.activity_new_task"
    IntentStartFlag.ClearTop -> "intent.flag.activity_clear_top"
    IntentStartFlag.ClearTask -> "intent.flag.activity_clear_task"
    IntentStartFlag.SingleTop -> "intent.flag.activity_single_top"
}

private fun intentBroadcastFlagLabelKey(flag: IntentBroadcastFlag): String = when (flag) {
    IntentBroadcastFlag.ReceiverForeground -> "intent.flag.receiver_foreground"
    IntentBroadcastFlag.ReceiverIncludeBackground -> "intent.flag.receiver_include_background"
}

@Composable
private fun validationErrorText(error: String): String {
    val strings = rememberAppStrings()
    val key = error.substringBefore(":")
    val index = error.substringAfter(":", "").takeIf { it.isNotBlank() }
    return if (index == null) {
        strings.t(key)
    } else {
        strings.t(key, index)
    }
}
