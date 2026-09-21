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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.floatingmuseum.android.test.helper.AndroidDevice
import com.floatingmuseum.android.test.helper.localization.localized
import com.floatingmuseum.android.test.helper.localization.rememberAppStrings

@Composable
internal fun DeviceLogPanel(
    fileViewer: LogFileViewerController,
    commands: LogCommandController,
    selectedDevice: AndroidDevice?,
    isRunning: Boolean,
    progress: DeviceLogCaptureProgress?,
    lastResult: DeviceLogCaptureResult?,
    filterQuery: String,
    matchCase: Boolean,
    filterHistory: List<LogKeywordFilter>,
    onFilterQueryChange: (String) -> Unit,
    onMatchCaseChange: (Boolean) -> Unit,
    onSaveFilter: () -> Unit,
    onApplyFilterHistory: (LogKeywordFilter) -> Unit,
    onDeleteFilterHistory: (LogKeywordFilter) -> Unit,
    onCaptureLogs: () -> Unit,
    onStopCapture: () -> Unit,
    onRevealLogFile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = rememberAppStrings()
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(strings.t("log.capture"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = selectedDevice?.let { "${it.model} · ${it.serialNumber}" }
                            ?: strings.t("common.device.select_from_bottom_panel"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = fileViewer::openWindow) {
                    Text(strings.t("log.viewer.open_window"))
                }
                Button(onClick = onCaptureLogs, enabled = !isRunning && selectedDevice?.isReady == true) {
                    Text(strings.t("log.capture.start"))
                }
                Button(onClick = onStopCapture, enabled = isRunning) {
                    Text(strings.t("common.action.stop"))
                }
            }

            LatestLogResult(lastResult, onRevealLogFile, fileViewer::openFile)

            LogCommandSelector(commands, enabled = !isRunning)

            KeywordFilterBar(
                query = filterQuery,
                matchCase = matchCase,
                history = filterHistory,
                enabled = !isRunning,
                onQueryChange = onFilterQueryChange,
                onMatchCaseChange = onMatchCaseChange,
                onSave = onSaveFilter,
                onApply = onApplyFilterHistory,
                onDelete = onDeleteFilterHistory,
            )

            if (isRunning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = progress?.let {
                        if (it.hasFilter) strings.t("log.filter.progress", it.capturedLines, it.matchedLines)
                        else strings.t("log.capture.lines", it.capturedLines)
                    } ?: strings.t("log.capture.capturing_status"),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    if (commands.managerOpen) LogCommandManager(commands)
}

@Composable
private fun KeywordFilterBar(
    query: String,
    matchCase: Boolean,
    history: List<LogKeywordFilter>,
    enabled: Boolean,
    onQueryChange: (String) -> Unit,
    onMatchCaseChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onApply: (LogKeywordFilter) -> Unit,
    onDelete: (LogKeywordFilter) -> Unit,
) {
    val strings = rememberAppStrings()
    var historyExpanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text(strings.t("log.filter.label")) },
            placeholder = { Text("JPush | CSDK | UsbDeviceManager") },
            supportingText = { Text(strings.t("log.filter.hint")) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    TextButton(onClick = { onQueryChange("") }, enabled = enabled) {
                        Text(strings.t("log.filter.clear"))
                    }
                }
            },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSave() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = matchCase, onCheckedChange = onMatchCaseChange, enabled = enabled)
            Text(
                strings.t("log.filter.match_case"),
                modifier = Modifier.clickable(enabled = enabled) { onMatchCaseChange(!matchCase) },
                style = MaterialTheme.typography.bodySmall,
            )
            Box {
                TextButton(onClick = { historyExpanded = true }, enabled = enabled && history.isNotEmpty()) {
                    Text(strings.t("log.filter.history", history.size))
                }
                DropdownMenu(
                    expanded = historyExpanded && enabled && history.isNotEmpty(),
                    onDismissRequest = { historyExpanded = false },
                    modifier = Modifier.width(1000.dp).heightIn(max = 340.dp),
                ) {
                    history.forEach { filter ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(filter.query, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (filter.matchCase) {
                                        Text(strings.t("log.filter.match_case"), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            },
                            onClick = {
                                onApply(filter)
                                historyExpanded = false
                            },
                            trailingIcon = {
                                TextButton(onClick = {
                                    onDelete(filter)
                                    if (history.size == 1) historyExpanded = false
                                }) {
                                    Text(strings.t("log.filter.delete"))
                                }
                            },
                        )
                    }
                }
            }
            TextButton(onClick = onSave, enabled = enabled && LogKeywordFilter(query).keywords.isNotEmpty()) {
                Text(strings.t("log.filter.save"))
            }
        }
        Text(
            strings.t("log.filter.files_hint"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LatestLogResult(lastResult: DeviceLogCaptureResult?, onRevealLogFile: (String) -> Unit, onViewLogFile: (String) -> Unit) {
    val strings = rememberAppStrings()
    if (lastResult == null) {
        Text(
            strings.t("log.capture.completed_path_hint"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.t("log.latest_log"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            val state = when (lastResult.endState) {
                DeviceLogCaptureEndState.COMPLETED -> localized("log.completed")
                DeviceLogCaptureEndState.STOPPED -> localized("log.stopped")
                DeviceLogCaptureEndState.INTERRUPTED -> localized("log.interrupted")
            }
            Text(state, style = MaterialTheme.typography.bodySmall)
            LogFileLink(strings.t("log.filter.full_file", lastResult.capturedLines), lastResult.filePath, onRevealLogFile, onViewLogFile)
            lastResult.filteredFilePath?.let {
                LogFileLink(strings.t("log.filter.filtered_file", lastResult.matchedLines), it, onRevealLogFile, onViewLogFile)
                Text(lastResult.filter.query, style = MaterialTheme.typography.bodySmall)
            }
            lastResult.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun LogFileLink(label: String, path: String, onReveal: (String) -> Unit, onView: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { onView(path) }) { Text(rememberAppStrings().t("log.viewer.view")) }
        }
        SelectionContainer {
            Text(
                text = path,
                modifier = Modifier.fillMaxWidth().clickable { onReveal(path) },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                textDecoration = TextDecoration.Underline,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
