package com.floatingmuseum.android.test.helper.devicelog

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.floatingmuseum.android.test.helper.localization.rememberAppStrings
import androidtesthelper.shared.generated.resources.Res
import androidtesthelper.shared.generated.resources.ic_log_note
import org.jetbrains.compose.resources.painterResource
import kotlinx.coroutines.flow.first

@Composable
internal fun LogFileViewerPanel(controller: LogFileViewerController, modifier: Modifier = Modifier) {
    val strings = rememberAppStrings()
    Column(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = controller::selectFile, enabled = !controller.isPicking && !controller.notes.isSaving) { Text(strings.t("log.viewer.open")) }
            if (controller.filePath == null) {
                Text(strings.t("log.viewer.no_file"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            } else {
                OutlinedTextField(
                    value = controller.query,
                    onValueChange = controller::updateQuery,
                    label = { Text(strings.t("log.viewer.search")) },
                    singleLine = true,
                    trailingIcon = {
                        if (controller.query.isNotEmpty()) TextButton(onClick = { controller.updateQuery("") }) {
                            Text(strings.t("log.filter.clear"))
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = controller.matchCase, onCheckedChange = controller::updateMatchCase)
                    Text(strings.t("log.filter.match_case"), style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.clickable { controller.updateMatchCase(!controller.matchCase) })
                }
            }
            if (controller.filePath != null) {
                TextButton(onClick = { controller.reload() }, enabled = !controller.isLoading) { Text(strings.t("log.viewer.reload")) }
                TextButton(onClick = controller::closeFile, enabled = !controller.notes.isSaving) { Text(strings.t("log.viewer.close_file")) }
            }
        }
        if (controller.filePath == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(strings.t("log.viewer.empty_hint"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            controller.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            return@Column
        }
        SelectionContainer {
            Text(controller.filePath.orEmpty(), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(strings.t("log.viewer.search_hint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(strings.t("log.viewer.columns_hint"), style = MaterialTheme.typography.bodySmall)
        if (controller.hiddenColumns.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = controller::showAllColumns) { Text(strings.t("log.viewer.restore_all")) }
                LogFileColumn.entries.filter { it in controller.hiddenColumns }.forEach { column ->
                    TextButton(onClick = { controller.toggleColumn(column) }) {
                        Text(strings.t("log.viewer.restore_column", strings.t(column.labelKey)))
                    }
                }
            }
        }
        controller.navigationMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        val content = controller.content
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val numberMeasurer = rememberTextMeasurer()
            val compactNotesWidth = with(LocalDensity.current) {
                numberMeasurer.measure(
                    controller.notes.notes.maxOfOrNull { it.lineNumber }?.toString().orEmpty(),
                    MaterialTheme.typography.labelMedium,
                ).size.width.toDp() + 40.dp
            }.coerceAtLeast(56.dp)
            val notesWidth by animateDpAsState(
                if (controller.notesExpanded) (maxWidth * 0.32f).coerceIn(320.dp, 420.dp) else compactNotesWidth,
                animationSpec = tween(180),
            )
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f).fillMaxSize()) {
                    when {
                        controller.isLoading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                LinearProgressIndicator(Modifier.width(240.dp))
                                Text(strings.t("log.viewer.loading"), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        controller.error != null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(strings.t("log.viewer.read_failed", controller.error), color = MaterialTheme.colorScheme.error)
                        }
                        content != null -> LogFileTable(
                            controller = controller,
                            content = content,
                            hidden = controller.hiddenColumns,
                            filter = LogKeywordFilter(controller.query, controller.matchCase),
                            onHide = controller::toggleColumn,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                LogFileNotesPanel(controller, Modifier.width(notesWidth))
            }
        }
        Text(
            strings.t("log.viewer.rows", content?.rowCount ?: 0, content?.totalLines ?: 0),
            style = MaterialTheme.typography.bodySmall,
        )
    }
    LogFileNoteDialogs(controller.notes)
}

private fun LogFileColumn.width() = when (this) {
    LogFileColumn.Date -> 96.dp
    LogFileColumn.Time -> 110.dp
    LogFileColumn.PidTid -> 104.dp
    LogFileColumn.Tag -> 154.dp
    LogFileColumn.Process -> 190.dp
    LogFileColumn.Priority -> 64.dp
    LogFileColumn.Message -> 280.dp
}

@Composable
private fun LogFileTable(
    controller: LogFileViewerController,
    content: LogFileContent,
    hidden: Set<LogFileColumn>,
    filter: LogKeywordFilter,
    onHide: (LogFileColumn) -> Unit,
    modifier: Modifier,
) {
    val strings = rememberAppStrings()
    val columns = LogFileColumn.entries.filterNot { it in hidden }
    val textMeasurer = rememberTextMeasurer()
    val rowTextStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp)
    val lineNumberLabel = strings.t("log.viewer.column.line_number")
    val lineNumberHeaderStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
    val lineNumberWidth = with(LocalDensity.current) {
        maxOf(
            textMeasurer.measure(content.totalLines.toString(), rowTextStyle).size.width,
            textMeasurer.measure(lineNumberLabel, lineNumberHeaderStyle).size.width,
        ).toDp() + 12.dp + 20.dp // Hover action belongs to the line-number cell; reserve space to avoid shifts.
    }
    val scroll = rememberScrollState()
    val list = rememberLazyListState()
    val jumpRequest = controller.jumpRequest
    val jumpFlash = remember(content) { Animatable(0f) }
    val flashColor = MaterialTheme.colorScheme.primary
    val notedLines = remember(controller.notes.notes) { controller.notes.notes.map { it.lineNumber }.toSet() }
    LaunchedEffect(content) { if (controller.jumpRequest == null) list.scrollToItem(0) }
    LaunchedEffect(content, jumpRequest) {
        if (jumpRequest != null) {
            list.scrollToItem(jumpRequest.rowIndex)
            scroll.scrollTo(0)
            snapshotFlow { controller.rowAt(jumpRequest.rowIndex) }.first { it != null }
            repeat(2) {
                jumpFlash.snapTo(1f)
                jumpFlash.animateTo(0f, tween(450))
            }
        } else {
            jumpFlash.snapTo(0f)
        }
    }
    LaunchedEffect(hidden) { scroll.scrollTo(0) }
    if (columns.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(strings.t("log.viewer.all_hidden"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val tableWidth = maxOf(maxWidth - 12.dp, lineNumberWidth + columns.sumOf { it.width().value.toDouble() }.toFloat().dp)
        Column(Modifier.padding(end = 12.dp, bottom = 12.dp).horizontalScroll(scroll).width(tableWidth)) {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
                Text(
                    lineNumberLabel,
                    modifier = Modifier.width(lineNumberWidth).padding(6.dp),
                    style = lineNumberHeaderStyle,
                    textAlign = TextAlign.Left,
                )
                columns.forEach { column ->
                    val cell = if (column == LogFileColumn.Message) Modifier.weight(1f) else Modifier.width(column.width())
                    Text(
                        strings.t(column.labelKey),
                        modifier = cell.clickable(onClickLabel = strings.t("log.viewer.hide_column", strings.t(column.labelKey))) { onHide(column) }.padding(6.dp),
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            HorizontalDivider()
            if (content.rowCount == 0) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(strings.t("log.viewer.no_matches"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(content.rowCount) { index ->
                        val row = controller.rowAt(index)
                        LaunchedEffect(content, index / LOG_VIEWER_BLOCK_ROWS, row == null) {
                            if (row == null) controller.ensureRows(index)
                        }
                        if (row == null) {
                            Text(strings.t("common.loading"), modifier = Modifier.fillMaxWidth().padding(6.dp),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            val interaction = remember(row.lineNumber) { MutableInteractionSource() }
                            val hovered by interaction.collectIsHoveredAsState()
                            val hasNote = row.lineNumber in notedLines
                            val isJumpTarget = row.lineNumber == controller.selectedLine
                            SelectionContainer {
                                Row(Modifier.fillMaxWidth().hoverable(interaction).background(
                                    when {
                                        hovered -> MaterialTheme.colorScheme.tertiaryContainer
                                        hasNote -> MaterialTheme.colorScheme.secondaryContainer
                                        row.lineNumber % 2L == 0L -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                        else -> Color.Transparent
                                    }
                                ).drawBehind {
                                    if (isJumpTarget) drawRect(flashColor, alpha = jumpFlash.value * 0.38f)
                                }) {
                                    Row(
                                        Modifier.width(lineNumberWidth).padding(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            row.lineNumber.toString(), style = rowTextStyle,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Left, softWrap = false,
                                        )
                                        if (hovered) Icon(
                                            painterResource(Res.drawable.ic_log_note),
                                            contentDescription = strings.t(if (hasNote) "log.notes.edit_line" else "log.notes.add_line", row.lineNumber),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.width(20.dp).height(18.dp)
                                                .clickable(enabled = controller.notes.canEdit && !controller.notes.isSaving) { controller.editNote(row) }
                                                .padding(start = 2.dp),
                                        )
                                    }
                                    columns.forEach { column ->
                                        val cell = if (column == LogFileColumn.Message) Modifier.weight(1f) else Modifier.width(column.width())
                                        LogFileCell(row.value(column), filter, row.priority, column, rowTextStyle, cell.padding(6.dp))
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(list), Modifier.align(Alignment.CenterEnd).padding(top = 32.dp, bottom = 12.dp))
        HorizontalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(end = 12.dp))
    }
}

@Composable
private fun LogFileCell(
    text: String,
    filter: LogKeywordFilter,
    priority: String,
    column: LogFileColumn,
    style: TextStyle,
    modifier: Modifier,
) {
    val colors = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) darkLogPriorityColors else lightLogPriorityColors
    val priorityColors = colors[priority]
    val textColor = when (column) {
        LogFileColumn.Priority -> priorityColors?.indicatorText
        LogFileColumn.Message -> priorityColors?.messageText
        else -> null
    } ?: MaterialTheme.colorScheme.onSurface
    val marked = remember(text, filter) {
        buildAnnotatedString {
            append(text)
            filter.keywords.forEach { keyword ->
                var start = text.indexOf(keyword, ignoreCase = !filter.matchCase)
                while (start >= 0) {
                    addStyle(SpanStyle(background = Color(0xFFFFDF80), color = Color(0xFF241A00)), start, start + keyword.length)
                    start = text.indexOf(keyword, start + keyword.length, ignoreCase = !filter.matchCase)
                }
            }
        }
    }
    Box(modifier) {
        Text(
            marked,
            modifier = if (column == LogFileColumn.Priority && priorityColors != null) {
                Modifier.background(priorityColors.indicatorBackground).padding(horizontal = 5.dp)
            } else Modifier,
            style = style,
            color = textColor,
            softWrap = true,
        )
    }
}

private data class LogPriorityColors(
    val messageText: Color,
    val indicatorText: Color,
    val indicatorBackground: Color,
)

// Android Studio-style message colors and compact level indicators. Row backgrounds
// remain available for notes, hover and navigation; search spans override text colors.
private val darkLogPriorityColors = mapOf(
    "V" to LogPriorityColors(Color(0xFFBBBBBB), Color(0xFF000000), Color(0xFFBBBBBB)),
    "D" to LogPriorityColors(Color(0xFF299999), Color(0xFFA0C7E4), Color(0xFF365C6B)),
    "I" to LogPriorityColors(Color(0xFFA8C023), Color(0xFFE3F0D7), Color(0xFF6B8659)),
    "W" to LogPriorityColors(Color(0xFFBBBB23), Color(0xFF000000), Color(0xFFBBBB23)),
    "E" to LogPriorityColors(Color(0xFFFF6B68), Color(0xFF000000), Color(0xFFCF5B56)),
    "A" to LogPriorityColors(Color(0xFFFF6B68), Color(0xFFFFFFFF), Color(0xFF8F3939)),
).let { it + ("F" to it.getValue("A")) }

// Deeper message hues keep the same level associations readable on light surfaces.
private val lightLogPriorityColors = mapOf(
    "V" to LogPriorityColors(Color(0xFF5F6368), Color(0xFF333333), Color(0xFFD7D7D7)),
    "D" to LogPriorityColors(Color(0xFF006B73), Color(0xFF244D61), Color(0xFFD2EAF1)),
    "I" to LogPriorityColors(Color(0xFF3E6B18), Color(0xFF35571D), Color(0xFFDBEACC)),
    "W" to LogPriorityColors(Color(0xFF755C00), Color(0xFF3B3000), Color(0xFFF1D766)),
    "E" to LogPriorityColors(Color(0xFFC62828), Color(0xFF631414), Color(0xFFF3B3AE)),
    "A" to LogPriorityColors(Color(0xFFC62828), Color(0xFFFFFFFF), Color(0xFF8F3939)),
).let { it + ("F" to it.getValue("A")) }
