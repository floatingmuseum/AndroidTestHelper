package com.floatingmuseum.android.test.helper.devicelog

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.floatingmuseum.android.test.helper.localization.rememberAppStrings

@Composable
internal fun LogFileViewerPanel(controller: LogFileViewerController, modifier: Modifier = Modifier) {
    val strings = rememberAppStrings()
    Column(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = controller::selectFile, enabled = !controller.isPicking) { Text(strings.t("log.viewer.open")) }
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
                TextButton(onClick = controller::closeFile) { Text(strings.t("log.viewer.close_file")) }
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
        Row(
            modifier = Modifier.fillMaxWidth().height(32.dp).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(strings.t("log.viewer.columns_hint"), style = MaterialTheme.typography.bodySmall)
            if (controller.hiddenColumns.isNotEmpty()) {
                TextButton(onClick = controller::showAllColumns, modifier = Modifier.height(32.dp)) { Text(strings.t("log.viewer.restore_all")) }
                LogFileColumn.entries.filter { it in controller.hiddenColumns }.forEach { column ->
                    TextButton(onClick = { controller.toggleColumn(column) }, modifier = Modifier.height(32.dp)) {
                        Text(strings.t("log.viewer.restore_column", strings.t(column.labelKey)))
                    }
                }
            }
        }
        val content = controller.content
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
        Text(
            strings.t("log.viewer.rows", content?.rowCount ?: 0, content?.totalLines ?: 0),
            style = MaterialTheme.typography.bodySmall,
        )
    }
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
    val scroll = rememberScrollState()
    val list = rememberLazyListState()
    LaunchedEffect(content) { list.scrollToItem(0) }
    LaunchedEffect(hidden) { scroll.scrollTo(0) }
    if (columns.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(strings.t("log.viewer.all_hidden"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val tableWidth = maxOf(maxWidth - 12.dp, columns.sumOf { it.width().value.toDouble() }.toFloat().dp)
        Column(Modifier.padding(end = 12.dp, bottom = 12.dp).horizontalScroll(scroll).width(tableWidth)) {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
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
                            SelectionContainer {
                                Row(Modifier.fillMaxWidth().background(
                                    if (row.lineNumber % 2L == 0L) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f) else Color.Transparent
                                )) {
                                    columns.forEach { column ->
                                        val cell = if (column == LogFileColumn.Message) Modifier.weight(1f) else Modifier.width(column.width())
                                        LogFileCell(row.value(column), filter, row.priority, column, cell.padding(6.dp))
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
private fun LogFileCell(text: String, filter: LogKeywordFilter, priority: String, column: LogFileColumn, modifier: Modifier) {
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
    Text(
        marked, modifier = modifier, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp,
        color = if (column == LogFileColumn.Priority && priority in listOf("E", "F", "A")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        softWrap = true,
    )
}
