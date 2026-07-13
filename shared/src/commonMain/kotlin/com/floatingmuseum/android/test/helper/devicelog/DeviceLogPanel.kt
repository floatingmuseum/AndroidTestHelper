package com.floatingmuseum.android.test.helper.devicelog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.floatingmuseum.android.test.helper.AndroidDevice
import com.floatingmuseum.android.test.helper.localization.localized
import com.floatingmuseum.android.test.helper.localization.rememberAppStrings

@Composable
fun DeviceLogPanel(
    selectedDevice: AndroidDevice?,
    isRunning: Boolean,
    progress: DeviceLogCaptureProgress?,
    lastResult: DeviceLogCaptureResult?,
    currentCommandPreset: LogCommandPreset,
    savedCommandPresets: List<LogCommandPreset>,
    defaultLogCommandTemplatesExpanded: Boolean,
    isCommandEditorOpen: Boolean,
    editorCommandName: String,
    editorCommandNameHasError: Boolean,
    editorCommandParts: List<LogCommandPart>,
    editorFilterTag: String,
    editorFilterPriority: String,
    editorRegex: String,
    editorPid: String,
    editorMaxCount: String,
    editorRecentValue: String,
    onCaptureLogs: () -> Unit,
    onStopCapture: () -> Unit,
    onRevealLogFile: (String) -> Unit,
    onOpenCommandEditor: () -> Unit,
    onCloseCommandEditor: () -> Unit,
    onEditorCommandNameChange: (String) -> Unit,
    onEditorFilterTagChange: (String) -> Unit,
    onEditorFilterPriorityChange: (String) -> Unit,
    onEditorRegexChange: (String) -> Unit,
    onEditorPidChange: (String) -> Unit,
    onEditorMaxCountChange: (String) -> Unit,
    onEditorRecentValueChange: (String) -> Unit,
    onAddEditorCommandPart: (LogCommandPart) -> Unit,
    onRemoveEditorCommandPart: (Int) -> Unit,
    onAddEditorFilterPart: () -> Unit,
    onAddEditorRegexPart: () -> Unit,
    onAddEditorPidPart: () -> Unit,
    onAddEditorMaxCountPart: () -> Unit,
    onAddEditorRecentPart: () -> Unit,
    onSaveEditorCommandPreset: (Boolean) -> Unit,
    onApplyCommandPreset: (LogCommandPreset) -> Unit,
    onDeleteCommandPreset: (LogCommandPreset) -> Unit,
    onDefaultLogCommandTemplatesExpandedChange: (Boolean) -> Unit,
    onRestoreDefaultCommandPreset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = rememberAppStrings()
    Card(modifier = modifier.fillMaxWidth()) {
        if (selectedDevice == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = strings.t("common.device.select_from_bottom_panel"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Card
        }

        val currentCommand = buildLogcatAdbCommand(selectedDevice.transportId, currentCommandPreset)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LogCaptureHeader(
                selectedDevice = selectedDevice,
                isRunning = isRunning,
                progress = progress,
                onCaptureLogs = onCaptureLogs,
                onStopCapture = onStopCapture,
            )

            CurrentCommandCard(
                command = currentCommand.displayCommand,
                isRunning = isRunning,
                onOpenCommandEditor = onOpenCommandEditor,
                onRestoreDefaultCommandPreset = onRestoreDefaultCommandPreset,
            )

            DefaultLogCommandTemplatesCard(
                selectedDevice = selectedDevice,
                isRunning = isRunning,
                expanded = defaultLogCommandTemplatesExpanded,
                onExpandedChange = onDefaultLogCommandTemplatesExpandedChange,
                onApplyCommandPreset = onApplyCommandPreset,
            )

            SavedCommandsCard(
                selectedDevice = selectedDevice,
                savedCommandPresets = savedCommandPresets,
                isRunning = isRunning,
                onApplyCommandPreset = onApplyCommandPreset,
                onDeleteCommandPreset = onDeleteCommandPreset,
            )

            CaptureProgress(progress)
            LatestLogResult(lastResult, onRevealLogFile)
        }

        if (isCommandEditorOpen) {
            LogCommandEditorDialog(
                selectedDevice = selectedDevice,
                editorCommandName = editorCommandName,
                editorCommandNameHasError = editorCommandNameHasError,
                editorCommandParts = editorCommandParts,
                editorFilterTag = editorFilterTag,
                editorFilterPriority = editorFilterPriority,
                editorRegex = editorRegex,
                editorPid = editorPid,
                editorMaxCount = editorMaxCount,
                editorRecentValue = editorRecentValue,
                onClose = onCloseCommandEditor,
                onEditorCommandNameChange = onEditorCommandNameChange,
                onEditorFilterTagChange = onEditorFilterTagChange,
                onEditorFilterPriorityChange = onEditorFilterPriorityChange,
                onEditorRegexChange = onEditorRegexChange,
                onEditorPidChange = onEditorPidChange,
                onEditorMaxCountChange = onEditorMaxCountChange,
                onEditorRecentValueChange = onEditorRecentValueChange,
                onAddEditorCommandPart = onAddEditorCommandPart,
                onRemoveEditorCommandPart = onRemoveEditorCommandPart,
                onAddEditorFilterPart = onAddEditorFilterPart,
                onAddEditorRegexPart = onAddEditorRegexPart,
                onAddEditorPidPart = onAddEditorPidPart,
                onAddEditorMaxCountPart = onAddEditorMaxCountPart,
                onAddEditorRecentPart = onAddEditorRecentPart,
                onSaveEditorCommandPreset = onSaveEditorCommandPreset,
            )
        }
    }
}

@Composable
private fun DefaultLogCommandTemplatesCard(
    selectedDevice: AndroidDevice,
    isRunning: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onApplyCommandPreset: (LogCommandPreset) -> Unit,
) {
    val strings = rememberAppStrings()
    val templates = defaultLogCommandTemplates()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = strings.t("log.command.default_templates"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = strings.t("log.command.default_templates_hint", templates.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onExpandedChange(!expanded) }) {
                    Text(
                        if (expanded) {
                            strings.t("log.command.default_templates_collapse")
                        } else {
                            strings.t("log.command.default_templates_expand")
                        },
                    )
                }
            }

            if (expanded) {
                templates.forEachIndexed { index, template ->
                    if (index > 0) {
                        androidx.compose.material3.HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    DefaultLogCommandTemplateRow(
                        selectedDevice = selectedDevice,
                        template = template,
                        enabled = !isRunning,
                        onApply = { onApplyCommandPreset(template.preset) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DefaultLogCommandTemplateRow(
    selectedDevice: AndroidDevice,
    template: DefaultLogCommandTemplate,
    enabled: Boolean,
    onApply: () -> Unit,
) {
    val strings = rememberAppStrings()
    val command = buildLogcatAdbCommand(selectedDevice.transportId, template.preset)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = strings.t(template.titleKey),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onApply, enabled = enabled) {
                    Text(strings.t("log.command.apply"))
                }
            }
            Text(
                text = "${strings.t("log.command.default_template.scenario")}: ${strings.t(template.scenarioKey)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CommandPreviewLine(command.displayCommand)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = strings.t("log.command.default_template.parameters"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                CommandExplanationRow(
                    parameter = DEFAULT_LOG_COMMAND_TEMPLATE_PREFIX,
                    explanation = strings.t(template.prefixExplanationKey),
                )
                template.preset.parts.forEachIndexed { index, part ->
                    CommandExplanationRow(
                        parameter = logCommandPartSummary(part),
                        explanation = strings.t(template.parameterExplanationKeys[index]),
                    )
                }
            }
        }
    }
}

@Composable
private fun CommandExplanationRow(
    parameter: String,
    explanation: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = parameter,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = explanation,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LogCaptureHeader(
    selectedDevice: AndroidDevice,
    isRunning: Boolean,
    progress: DeviceLogCaptureProgress?,
    onCaptureLogs: () -> Unit,
    onStopCapture: () -> Unit,
) {
    val strings = rememberAppStrings()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = strings.t("log.capture"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${selectedDevice.model} · ${selectedDevice.serialNumber}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onCaptureLogs,
                enabled = !isRunning && selectedDevice.isReady,
            ) {
                Text(strings.t("log.capture.start"))
            }
            Button(
                onClick = onStopCapture,
                enabled = isRunning && progress != null,
            ) {
                Text(strings.t("common.action.stop"))
            }
        }
    }
}

@Composable
private fun CurrentCommandCard(
    command: String,
    isRunning: Boolean,
    onOpenCommandEditor: () -> Unit,
    onRestoreDefaultCommandPreset: () -> Unit,
) {
    val strings = rememberAppStrings()
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = strings.t("log.command.current"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onOpenCommandEditor,
                        enabled = !isRunning,
                    ) {
                        Text(strings.t("log.command.create_custom"))
                    }
                    TextButton(
                        onClick = onRestoreDefaultCommandPreset,
                        enabled = !isRunning,
                    ) {
                        Text(strings.t("log.command.restore_default"))
                    }
                }
            }
            Text(
                text = strings.t("log.command.current_hint"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CommandPreviewLine(command)
        }
    }
}

@Composable
private fun SavedCommandsCard(
    selectedDevice: AndroidDevice,
    savedCommandPresets: List<LogCommandPreset>,
    isRunning: Boolean,
    onApplyCommandPreset: (LogCommandPreset) -> Unit,
    onDeleteCommandPreset: (LogCommandPreset) -> Unit,
) {
    val strings = rememberAppStrings()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = strings.t("log.command.saved_presets"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (savedCommandPresets.isEmpty()) {
                Text(
                    text = strings.t("log.command.no_saved_presets"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                savedCommandPresets.forEach { preset ->
                    SavedPresetRow(
                        selectedDevice = selectedDevice,
                        preset = preset,
                        enabled = !isRunning,
                        onApply = { onApplyCommandPreset(preset) },
                        onDelete = { onDeleteCommandPreset(preset) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LogCommandEditorDialog(
    selectedDevice: AndroidDevice,
    editorCommandName: String,
    editorCommandNameHasError: Boolean,
    editorCommandParts: List<LogCommandPart>,
    editorFilterTag: String,
    editorFilterPriority: String,
    editorRegex: String,
    editorPid: String,
    editorMaxCount: String,
    editorRecentValue: String,
    onClose: () -> Unit,
    onEditorCommandNameChange: (String) -> Unit,
    onEditorFilterTagChange: (String) -> Unit,
    onEditorFilterPriorityChange: (String) -> Unit,
    onEditorRegexChange: (String) -> Unit,
    onEditorPidChange: (String) -> Unit,
    onEditorMaxCountChange: (String) -> Unit,
    onEditorRecentValueChange: (String) -> Unit,
    onAddEditorCommandPart: (LogCommandPart) -> Unit,
    onRemoveEditorCommandPart: (Int) -> Unit,
    onAddEditorFilterPart: () -> Unit,
    onAddEditorRegexPart: () -> Unit,
    onAddEditorPidPart: () -> Unit,
    onAddEditorMaxCountPart: () -> Unit,
    onAddEditorRecentPart: () -> Unit,
    onSaveEditorCommandPreset: (Boolean) -> Unit,
) {
    val strings = rememberAppStrings()
    val previewPreset = LogCommandPreset(
        id = "editor-logcat",
        name = editorCommandName,
        parts = editorCommandParts,
    )
    val previewCommand = buildLogcatAdbCommand(LOG_COMMAND_SERIAL_PLACEHOLDER, previewPreset)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(strings.t("log.command.editor_title")) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 620.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedTextField(
                    value = editorCommandName,
                    onValueChange = onEditorCommandNameChange,
                    singleLine = true,
                    isError = editorCommandNameHasError,
                    label = { Text(strings.t("log.command.preset_name")) },
                    supportingText = {
                        if (editorCommandNameHasError) {
                            Text(strings.t("log.command.name_required"))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                CommandInfoBlock(
                    title = strings.t("log.command.full_preview"),
                    body = previewCommand.displayCommand,
                )

                Column(
                    modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CurrentPartsEditor(
                        parts = editorCommandParts,
                        onRemove = onRemoveEditorCommandPart,
                    )
                    ChoiceGroup(
                        title = strings.t("log.command.buffer_group"),
                        description = strings.t("log.command.buffer_group_hint"),
                        choices = bufferChoices,
                        onAdd = onAddEditorCommandPart,
                    )
                    ChoiceGroup(
                        title = strings.t("log.command.format_group"),
                        description = strings.t("log.command.format_group_hint"),
                        choices = formatChoices,
                        onAdd = onAddEditorCommandPart,
                    )
                    ChoiceGroup(
                        title = strings.t("log.command.modifier_group"),
                        description = strings.t("log.command.modifier_group_hint"),
                        choices = modifierChoices,
                        onAdd = onAddEditorCommandPart,
                    )
                    ChoiceGroup(
                        title = strings.t("log.command.switch_group"),
                        description = strings.t("log.command.switch_group_hint"),
                        choices = switchChoices,
                        onAdd = onAddEditorCommandPart,
                    )
                    FilterInputs(
                        editorFilterTag = editorFilterTag,
                        editorFilterPriority = editorFilterPriority,
                        editorRegex = editorRegex,
                        editorPid = editorPid,
                        editorMaxCount = editorMaxCount,
                        editorRecentValue = editorRecentValue,
                        onEditorFilterTagChange = onEditorFilterTagChange,
                        onEditorFilterPriorityChange = onEditorFilterPriorityChange,
                        onEditorRegexChange = onEditorRegexChange,
                        onEditorPidChange = onEditorPidChange,
                        onEditorMaxCountChange = onEditorMaxCountChange,
                        onEditorRecentValueChange = onEditorRecentValueChange,
                        onAddEditorFilterPart = onAddEditorFilterPart,
                        onAddEditorRegexPart = onAddEditorRegexPart,
                        onAddEditorPidPart = onAddEditorPidPart,
                        onAddEditorMaxCountPart = onAddEditorMaxCountPart,
                        onAddEditorRecentPart = onAddEditorRecentPart,
                    )
                    Text(
                        text = strings.t("log.command.excluded_options_hint"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSaveEditorCommandPreset(true) }) {
                    Text(strings.t("log.command.save_and_use"))
                }
                OutlinedButton(onClick = { onSaveEditorCommandPreset(false) }) {
                    Text(strings.t("log.command.save_current"))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text(strings.t("common.cancel"))
            }
        },
    )
}

@Composable
private fun ChoiceGroup(
    title: String,
    description: String,
    choices: List<LogcatChoice>,
    onAdd: (LogCommandPart) -> Unit,
) {
    val strings = rememberAppStrings()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        choices.chunked(3).forEach { rowChoices ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowChoices.forEach { choice ->
                    OutlinedButton(
                        onClick = { onAdd(choice.part) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(choice.commandText, fontFamily = FontFamily.Monospace)
                            Text(
                                text = strings.t(choice.descriptionKey),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
                repeat(3 - rowChoices.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FilterInputs(
    editorFilterTag: String,
    editorFilterPriority: String,
    editorRegex: String,
    editorPid: String,
    editorMaxCount: String,
    editorRecentValue: String,
    onEditorFilterTagChange: (String) -> Unit,
    onEditorFilterPriorityChange: (String) -> Unit,
    onEditorRegexChange: (String) -> Unit,
    onEditorPidChange: (String) -> Unit,
    onEditorMaxCountChange: (String) -> Unit,
    onEditorRecentValueChange: (String) -> Unit,
    onAddEditorFilterPart: () -> Unit,
    onAddEditorRegexPart: () -> Unit,
    onAddEditorPidPart: () -> Unit,
    onAddEditorMaxCountPart: () -> Unit,
    onAddEditorRecentPart: () -> Unit,
) {
    val strings = rememberAppStrings()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = strings.t("log.command.manual_filter_group"),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = strings.t("log.command.manual_filter_hint"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = editorFilterTag,
            onValueChange = onEditorFilterTagChange,
            singleLine = true,
            label = { Text(strings.t("log.command.filter_tag")) },
            supportingText = { Text(strings.t("log.command.filter_tag_hint")) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("V", "D", "I", "W", "E", "F", "S").forEach { priority ->
                val selected = editorFilterPriority.equals(priority, ignoreCase = true)
                if (selected) {
                    Button(onClick = { onEditorFilterPriorityChange(priority) }) {
                        Text(priority)
                    }
                } else {
                    OutlinedButton(onClick = { onEditorFilterPriorityChange(priority) }) {
                        Text(priority)
                    }
                }
            }
        }
        Button(onClick = onAddEditorFilterPart) {
            Text(strings.t("log.command.add_filter"))
        }
        TextInputPart(
            value = editorRegex,
            onValueChange = onEditorRegexChange,
            label = strings.t("log.command.regex_value"),
            hint = strings.t("log.command.regex_hint"),
            addLabel = strings.t("log.command.add_regex"),
            onAdd = onAddEditorRegexPart,
        )
        TextInputPart(
            value = editorPid,
            onValueChange = onEditorPidChange,
            label = strings.t("log.command.pid_value"),
            hint = strings.t("log.command.pid_hint"),
            addLabel = strings.t("log.command.add_pid"),
            onAdd = onAddEditorPidPart,
        )
        TextInputPart(
            value = editorMaxCount,
            onValueChange = onEditorMaxCountChange,
            label = strings.t("log.command.max_count_value"),
            hint = strings.t("log.command.max_count_hint"),
            addLabel = strings.t("log.command.add_max_count"),
            onAdd = onAddEditorMaxCountPart,
        )
        TextInputPart(
            value = editorRecentValue,
            onValueChange = onEditorRecentValueChange,
            label = strings.t("log.command.recent_value"),
            hint = strings.t("log.command.recent_hint"),
            addLabel = strings.t("log.command.add_recent"),
            onAdd = onAddEditorRecentPart,
        )
    }
}

@Composable
private fun TextInputPart(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    hint: String,
    addLabel: String,
    onAdd: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            label = { Text(label) },
            supportingText = { Text(hint) },
            modifier = Modifier.weight(1f),
        )
        Button(onClick = onAdd) {
            Text(addLabel)
        }
    }
}

@Composable
private fun CurrentPartsEditor(
    parts: List<LogCommandPart>,
    onRemove: (Int) -> Unit,
) {
    val strings = rememberAppStrings()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = strings.t("log.command.preview_parts"),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        if (parts.isEmpty()) {
            Text(
                text = strings.t("log.command.no_parts"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            parts.forEachIndexed { index, part ->
                CommandPartRow(
                    index = index,
                    part = part,
                    enabled = true,
                    onRemove = { onRemove(index) },
                )
            }
        }
    }
}

@Composable
private fun CommandPartRow(
    index: Int,
    part: LogCommandPart,
    enabled: Boolean,
    onRemove: () -> Unit,
) {
    val strings = rememberAppStrings()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = "${index + 1}. ${logCommandPartSummary(part)}",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = part.defaultDescription(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = onRemove,
            enabled = enabled,
        ) {
            Text(strings.t("common.action.delete"))
        }
    }
}

@Composable
private fun SavedPresetRow(
    selectedDevice: AndroidDevice,
    preset: LogCommandPreset,
    enabled: Boolean,
    onApply: () -> Unit,
    onDelete: () -> Unit,
) {
    val strings = rememberAppStrings()
    val command = buildLogcatAdbCommand(selectedDevice.transportId, preset)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = strings.t("log.command.parts_count_arg0", preset.normalized().parts.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = onApply,
                    enabled = enabled,
                ) {
                    Text(strings.t("log.command.apply"))
                }
                TextButton(
                    onClick = onDelete,
                    enabled = enabled,
                ) {
                    Text(strings.t("common.action.delete"))
                }
            }
        }
        CommandPreviewLine(command.displayCommand)
    }
}

@Composable
private fun CaptureProgress(progress: DeviceLogCaptureProgress?) {
    val strings = rememberAppStrings()
    if (progress == null) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(
            progress = {
                val ratio = if (progress.totalSections <= 0) {
                    0f
                } else {
                    progress.completedSections.toFloat() / progress.totalSections.toFloat()
                }
                ratio.coerceIn(0f, 1f)
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = strings.t("log.capturing_arg0_arg1_arg2", progress.currentSection, progress.completedSections, progress.totalSections),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LatestLogResult(
    lastResult: DeviceLogCaptureResult?,
    onRevealLogFile: (String) -> Unit,
) {
    val strings = rememberAppStrings()
    if (lastResult == null) {
        Text(
            text = strings.t("log.capture.completed_path_hint"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = strings.t("log.latest_log"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = lastResult.summaryText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            SelectionContainer {
                Text(
                    text = lastResult.filePath,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRevealLogFile(lastResult.filePath) },
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    textDecoration = TextDecoration.Underline,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun CommandInfoBlock(
    title: String,
    body: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CommandPreviewLine(body)
    }
}

@Composable
private fun CommandPreviewLine(value: String) {
    SelectionContainer {
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun DeviceLogCaptureResult.summaryText(): String {
    val stateText = when (endState) {
        DeviceLogCaptureEndState.COMPLETED -> localized("log.completed")
        DeviceLogCaptureEndState.STOPPED -> localized("log.stopped")
        DeviceLogCaptureEndState.INTERRUPTED -> localized("log.interrupted")
    }
    val sectionText = localized("log.capture.sections_progress", completedSections, totalSections)
    return message
        ?.takeIf { it.isNotBlank() }
        ?.let { "$stateText · $sectionText · $it" }
        ?: "$stateText · $sectionText"
}

@Composable
private fun LogCommandPart.defaultDescription(): String {
    val strings = rememberAppStrings()
    return when (type) {
        LogCommandPartType.Buffer -> strings.t("log.command.part.buffer_description_arg0", value)
        LogCommandPartType.Format -> strings.t("log.command.part.format_description_arg0", value)
        LogCommandPartType.Filter -> strings.t("log.command.part.filter_description_arg0", value)
        LogCommandPartType.Regex -> strings.t("log.command.part.regex_description_arg0", value)
        LogCommandPartType.Pid -> strings.t("log.command.part.pid_description_arg0", value)
        LogCommandPartType.MaxCount -> strings.t("log.command.part.max_count_description_arg0", value)
        LogCommandPartType.Recent -> strings.t("log.command.part.recent_description_arg0", value)
        LogCommandPartType.Silent -> strings.t("log.command.part.silent_description")
        LogCommandPartType.Dividers -> strings.t("log.command.part.dividers_description")
    }
}

private data class LogcatChoice(
    val commandText: String,
    val descriptionKey: String,
    val part: LogCommandPart,
)

private val bufferChoices = listOf(
    LogcatChoice("-b all", "log.command.choice.buffer.all", LogCommandPart(LogCommandPartType.Buffer, "all")),
    LogcatChoice("-b main", "log.command.choice.buffer.main", LogCommandPart(LogCommandPartType.Buffer, "main")),
    LogcatChoice("-b system", "log.command.choice.buffer.system", LogCommandPart(LogCommandPartType.Buffer, "system")),
    LogcatChoice("-b radio", "log.command.choice.buffer.radio", LogCommandPart(LogCommandPartType.Buffer, "radio")),
    LogcatChoice("-b events", "log.command.choice.buffer.events", LogCommandPart(LogCommandPartType.Buffer, "events")),
    LogcatChoice("-b crash", "log.command.choice.buffer.crash", LogCommandPart(LogCommandPartType.Buffer, "crash")),
    LogcatChoice("-b default", "log.command.choice.buffer.default", LogCommandPart(LogCommandPartType.Buffer, "default")),
    LogcatChoice("-b kernel", "log.command.choice.buffer.kernel", LogCommandPart(LogCommandPartType.Buffer, "kernel")),
    LogcatChoice("-b security", "log.command.choice.buffer.security", LogCommandPart(LogCommandPartType.Buffer, "security")),
)

private val formatChoices = listOf(
    LogcatChoice("-v threadtime", "log.command.choice.format.threadtime", LogCommandPart(LogCommandPartType.Format, "threadtime")),
    LogcatChoice("-v time", "log.command.choice.format.time", LogCommandPart(LogCommandPartType.Format, "time")),
    LogcatChoice("-v brief", "log.command.choice.format.brief", LogCommandPart(LogCommandPartType.Format, "brief")),
    LogcatChoice("-v long", "log.command.choice.format.long", LogCommandPart(LogCommandPartType.Format, "long")),
    LogcatChoice("-v process", "log.command.choice.format.process", LogCommandPart(LogCommandPartType.Format, "process")),
    LogcatChoice("-v tag", "log.command.choice.format.tag", LogCommandPart(LogCommandPartType.Format, "tag")),
    LogcatChoice("-v raw", "log.command.choice.format.raw", LogCommandPart(LogCommandPartType.Format, "raw")),
    LogcatChoice("-v thread", "log.command.choice.format.thread", LogCommandPart(LogCommandPartType.Format, "thread")),
)

private val modifierChoices = listOf(
    LogcatChoice("-v year", "log.command.choice.modifier.year", LogCommandPart(LogCommandPartType.Format, "year")),
    LogcatChoice("-v zone", "log.command.choice.modifier.zone", LogCommandPart(LogCommandPartType.Format, "zone")),
    LogcatChoice("-v usec", "log.command.choice.modifier.usec", LogCommandPart(LogCommandPartType.Format, "usec")),
    LogcatChoice("-v uid", "log.command.choice.modifier.uid", LogCommandPart(LogCommandPartType.Format, "uid")),
    LogcatChoice("-v monotonic", "log.command.choice.modifier.monotonic", LogCommandPart(LogCommandPartType.Format, "monotonic")),
    LogcatChoice("-v epoch", "log.command.choice.modifier.epoch", LogCommandPart(LogCommandPartType.Format, "epoch")),
    LogcatChoice("-v printable", "log.command.choice.modifier.printable", LogCommandPart(LogCommandPartType.Format, "printable")),
    LogcatChoice("-v descriptive", "log.command.choice.modifier.descriptive", LogCommandPart(LogCommandPartType.Format, "descriptive")),
    LogcatChoice("-v UTC", "log.command.choice.modifier.utc", LogCommandPart(LogCommandPartType.Format, "UTC")),
)

private val switchChoices = listOf(
    LogcatChoice("-D", "log.command.choice.switch.dividers", LogCommandPart(LogCommandPartType.Dividers, "enabled")),
    LogcatChoice("-s", "log.command.choice.switch.silent", LogCommandPart(LogCommandPartType.Silent, "enabled")),
)
