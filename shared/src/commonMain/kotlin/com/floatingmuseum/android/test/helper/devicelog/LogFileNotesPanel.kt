package com.floatingmuseum.android.test.helper.devicelog

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.floatingmuseum.android.test.helper.localization.rememberAppStrings
import androidtesthelper.shared.generated.resources.Res
import androidtesthelper.shared.generated.resources.ic_log_note_menu
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun LogFileNotesPanel(controller: LogFileViewerController, modifier: Modifier) {
    val strings = rememberAppStrings()
    val state = controller.notes
    val expanded = controller.notesExpanded
    val list = rememberLazyListState()
    var pendingDelete by remember(controller.filePath) { mutableStateOf<LogFileNote?>(null) }
    Column(modifier.fillMaxHeight().clipToBounds().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val toggleLabel = strings.t(if (expanded) "log.notes.hide" else "log.notes.show")
            Box(Modifier.size(28.dp)
                .semantics { contentDescription = toggleLabel }
                .clickable(role = Role.Button, onClickLabel = toggleLabel, onClick = controller::toggleNotesExpansion),
                contentAlignment = Alignment.Center) {
                Text(if (expanded) "›" else "‹", style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary)
            }
            if (expanded) Text(strings.t("log.notes.title", state.notes.size),
                style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Clip)
        }
        if (expanded) Text(strings.t("log.notes.order_hint"), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        if (state.isLoading || state.isSaving) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (expanded) state.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            if (!state.canEdit && !state.isLoading) TextButton(onClick = { controller.filePath?.let(state::open) }) {
                Text(strings.t("log.notes.retry"))
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (expanded && state.notes.isEmpty() && !state.isLoading) {
                Text(strings.t("log.notes.empty"), Modifier.padding(vertical = 12.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn(state = list, modifier = Modifier.fillMaxSize().padding(end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.notes, key = { it.lineNumber }) { note ->
                    val row = remember(note) { note.row }
                    var menuExpanded by remember(note.lineNumber, expanded) { mutableStateOf(false) }
                    Column(Modifier.fillMaxWidth()
                        .background(if (controller.selectedLine == note.lineNumber) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surface)
                        .clickable(enabled = controller.content != null && !controller.isLoading,
                            onClickLabel = strings.t("log.notes.jump", note.lineNumber)) { controller.jumpToNote(note) }
                        .padding(horizontal = if (expanded) 10.dp else 0.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) noteContent@{
                        if (!expanded) {
                            Text(note.lineNumber.toString(), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary, softWrap = false, maxLines = 1)
                            return@noteContent
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(strings.t("log.notes.line", note.lineNumber), modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            Text(listOf(row.date, row.time).filter { it.isNotEmpty() }.joinToString(" ")
                                .ifEmpty { strings.t("log.notes.no_timestamp") },
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                maxLines = 1, softWrap = false)
                            Box {
                                val actionsLabel = strings.t("log.notes.actions", note.lineNumber)
                                Box(Modifier.size(24.dp).clickable(
                                    enabled = state.canEdit && !state.isSaving,
                                    role = Role.Button, onClickLabel = actionsLabel,
                                ) { menuExpanded = true }, contentAlignment = Alignment.Center) {
                                    Icon(painterResource(Res.drawable.ic_log_note_menu), contentDescription = actionsLabel,
                                        modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                    DropdownMenuItem(
                                        text = { Text(strings.t("log.notes.edit")) },
                                        onClick = { menuExpanded = false; controller.editNote(row) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(strings.t("common.action.delete"), color = MaterialTheme.colorScheme.error) },
                                        onClick = { menuExpanded = false; pendingDelete = note },
                                    )
                                }
                            }
                        }
                        Text(row.message, style = MaterialTheme.typography.bodySmall, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(note.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(list), Modifier.align(Alignment.CenterEnd))
        }
        if (expanded) {
            Text(strings.t("log.notes.saved_beside"), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer {
                Text(logFileNotesPath(controller.filePath.orEmpty()), style = MaterialTheme.typography.bodySmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    pendingDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(strings.t("log.notes.delete_title")) },
            text = { Text(strings.t("log.notes.delete_message", note.lineNumber)) },
            confirmButton = { TextButton(onClick = { state.delete(note); pendingDelete = null }) {
                Text(strings.t("common.action.delete"), color = MaterialTheme.colorScheme.error)
            } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(strings.t("common.cancel")) } },
        )
    }
}

@Composable
internal fun LogFileNoteDialogs(state: LogFileNotesController) {
    val strings = rememberAppStrings()
    state.editorRow?.let { row ->
        val focus = remember(row) { FocusRequester() }
        AlertDialog(
            onDismissRequest = state::dismissEditor,
            modifier = Modifier.width(800.dp),
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
            title = { Text(strings.t("log.notes.editor_title", row.lineNumber)) },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(strings.t("log.notes.source"), style = MaterialTheme.typography.labelLarge)
                    SelectionContainer {
                        Box(Modifier.fillMaxWidth().heightIn(max = 200.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .verticalScroll(rememberScrollState()).padding(10.dp)) {
                            Text(row.raw, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                    OutlinedTextField(
                        value = state.editorText, onValueChange = state::updateText,
                        label = { Text(strings.t("log.notes.input")) },
                        placeholder = { Text(strings.t("log.notes.input_hint")) },
                        minLines = 4, maxLines = 10, enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                    LaunchedEffect(row) { focus.requestFocus() }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { Button(onClick = state::saveEditor, enabled = !state.isSaving && state.editorText.isNotBlank()) {
                Text(strings.t(if (state.isSaving) "log.notes.saving" else "log.notes.save"))
            } },
            dismissButton = { TextButton(onClick = state::dismissEditor, enabled = !state.isSaving) {
                Text(strings.t("common.cancel"))
            } },
        )
    }
    if (state.showDiscardConfirmation) AlertDialog(
        onDismissRequest = state::keepEditing,
        title = { Text(strings.t("log.notes.discard_title")) },
        text = { Text(strings.t("log.notes.discard_message")) },
        confirmButton = { TextButton(onClick = state::discard) { Text(strings.t("log.notes.discard")) } },
        dismissButton = { TextButton(onClick = state::keepEditing) { Text(strings.t("log.notes.keep_editing")) } },
    )
}
