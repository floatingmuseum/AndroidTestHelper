package com.floatingmuseum.android.test.helper.devicelog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.floatingmuseum.android.test.helper.localization.rememberAppStrings

@Composable
internal fun LogCommandSelector(controller: LogCommandController, enabled: Boolean) {
    val strings = rememberAppStrings()
    var expanded by remember { mutableStateOf(false) }
    val configuration = controller.configuration
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { expanded = true },
                    enabled = enabled && configuration.commands.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        configuration.selectedName ?: strings.t("log.command.default"),
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (configuration.commands.isNotEmpty()) Text("▾")
                }
                DropdownMenu(
                    expanded = expanded && enabled,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.width(600.dp).heightIn(max = 320.dp),
                ) {
                    // Built-in command is always the first item, outside the user-managed list.
                    DropdownMenuItem(
                        text = { Text(strings.t("log.command.default")) },
                        onClick = { controller.select(null); expanded = false },
                    )
                    configuration.commands.forEach { command ->
                        DropdownMenuItem(
                            text = { Text(command.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            onClick = { controller.select(command.name); expanded = false },
                        )
                    }
                }
            }
            TextButton(onClick = controller::openManager, enabled = enabled) {
                Text(strings.t("log.command.manage"))
            }
        }
        configuration.selectedCommand?.let {
            SelectionContainer {
                Text(it.command, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        }
        controller.persistenceError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
internal fun LogCommandManager(controller: LogCommandController) {
    val strings = rememberAppStrings()
    var pendingDelete by remember { mutableStateOf<CustomLogCommand?>(null) }
    AlertDialog(
        onDismissRequest = controller::closeManager,
        modifier = Modifier.width(860.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(strings.t("log.command.manage")) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(strings.t("log.command.default"))
                        SelectionContainer {
                            Text(DEFAULT_LOGCAT_COMMAND, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (controller.configuration.commands.isNotEmpty()) {
                    Text(strings.t("log.command.saved"), style = MaterialTheme.typography.titleSmall)
                    controller.configuration.commands.forEach { command ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(command.name, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = { controller.edit(command) }) { Text(strings.t("log.command.edit")) }
                            TextButton(onClick = { pendingDelete = command }) { Text(strings.t("common.action.delete")) }
                        }
                    }
                    HorizontalDivider()
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        strings.t(if (controller.editingName == null) "log.command.new" else "log.command.edit"),
                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                    )
                    if (controller.editingName != null) {
                        TextButton(onClick = controller::newCommand) { Text(strings.t("log.command.new")) }
                    }
                }
                OutlinedTextField(
                    value = controller.editorName,
                    onValueChange = controller::updateName,
                    label = { Text(strings.t("log.command.name")) },
                    singleLine = true,
                    isError = controller.nameError != null,
                    supportingText = { controller.nameError?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = controller.editorCommand,
                    onValueChange = controller::updateCommand,
                    label = { Text(strings.t("log.command.input")) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    isError = controller.commandError != null,
                    supportingText = { controller.commandError?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                controller.persistenceError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(onClick = controller::saveEditor) { Text(strings.t("log.command.save")) } },
        dismissButton = { TextButton(onClick = controller::closeManager) { Text(strings.t("common.close")) } },
    )
    pendingDelete?.let { command ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(strings.t("log.command.delete_title")) },
            text = { Text(strings.t("log.command.delete_message", command.name)) },
            confirmButton = {
                TextButton(onClick = { controller.delete(command.name); pendingDelete = null }) {
                    Text(strings.t("common.action.delete"))
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(strings.t("common.cancel")) } },
        )
    }
}
