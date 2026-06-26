package com.floatingmuseum.android.test.helper.filemanager

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidtesthelper.shared.generated.resources.Res
import androidtesthelper.shared.generated.resources.ic_file_android
import androidtesthelper.shared.generated.resources.ic_file_archive
import androidtesthelper.shared.generated.resources.ic_file_audio_file
import androidtesthelper.shared.generated.resources.ic_file_code
import androidtesthelper.shared.generated.resources.ic_file_description
import androidtesthelper.shared.generated.resources.ic_file_folder
import androidtesthelper.shared.generated.resources.ic_file_image
import androidtesthelper.shared.generated.resources.ic_file_link
import androidtesthelper.shared.generated.resources.ic_file_movie
import androidtesthelper.shared.generated.resources.ic_file_picture_as_pdf
import androidtesthelper.shared.generated.resources.ic_file_table_chart
import androidtesthelper.shared.generated.resources.ic_file_terminal
import androidtesthelper.shared.generated.resources.ic_file_text_snippet
import com.floatingmuseum.android.test.helper.AndroidDevice
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private data class PendingCreateEntry(
    val targetDirectory: RemoteFileEntry,
    val type: RemoteCreateType,
)

private data class FileIconSpec(
    val resource: DrawableResource,
    val tint: Color,
)

@Composable
internal fun FileManagerPanel(
    selectedDevice: AndroidDevice?,
    currentPath: String,
    treeRows: List<RemoteFileTreeRow>,
    loadedSerial: String?,
    selectedEntryPath: String?,
    dragTargetPath: String?,
    isRunning: Boolean,
    isDragOver: Boolean,
    onToggleEntry: (RemoteFileEntry) -> Unit,
    onSelectEntry: (RemoteFileEntry) -> Unit,
    onRefreshEntry: (RemoteFileEntry) -> Unit,
    onExportEntry: (RemoteFileEntry) -> Unit,
    onDeleteEntry: (RemoteFileEntry) -> Unit,
    onCreateEntry: (RemoteFileEntry, String, RemoteCreateType) -> Unit,
    onCopyPath: (RemoteFileEntry) -> Unit,
    onDroppedFiles: (List<String>, String) -> Unit,
    onDragStateChange: (Boolean, String?) -> Unit,
    onUnsupportedDrop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasReadyDevice = selectedDevice?.isReady == true
    var pendingDeleteEntry by remember { mutableStateOf<RemoteFileEntry?>(null) }
    var pendingCreateEntry by remember { mutableStateOf<PendingCreateEntry?>(null) }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isDragOver) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
            ),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "设备文件",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = selectedDevice?.let { "${it.model} · ${it.serialNumber}" } ?: "未选择设备",
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = if (isDragOver) {
                            dragTargetPath?.let { "松开后上传到 $it" } ?: "拖到目录行上松手才会上传"
                        } else {
                            "右键刷新、导出、删除；拖入本地文件到目录行即可上传"
                        },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                FileManagerHeader()

                when {
                    !hasReadyDevice -> {
                        EmptyFileManagerState("先选择状态为 device 的设备。")
                    }
                    loadedSerial != selectedDevice.serialNumber -> {
                        EmptyFileManagerState("尚未读取根目录。扫描或切换设备后会自动读取。")
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            itemsIndexed(
                                items = treeRows,
                                key = { index, row -> "${row.entry.path}#$index" },
                            ) { _, row ->
                                FileTreeRow(
                                    row = row,
                                    selected = row.entry.path == selectedEntryPath,
                                    dropTargeted = isDragOver &&
                                        normalizeRemotePath(row.entry.path) == normalizeRemotePath(dragTargetPath ?: ""),
                                    isRunning = isRunning,
                                    onToggle = { onToggleEntry(row.entry) },
                                    onSelect = { onSelectEntry(row.entry) },
                                    onRefresh = { onRefreshEntry(row.entry) },
                                    onExport = { onExportEntry(row.entry) },
                                    onDeleteRequest = { pendingDeleteEntry = row.entry },
                                    onCreateFileRequest = {
                                        pendingCreateEntry = PendingCreateEntry(row.entry, RemoteCreateType.File)
                                    },
                                    onCreateDirectoryRequest = {
                                        pendingCreateEntry = PendingCreateEntry(row.entry, RemoteCreateType.Directory)
                                    },
                                    onCopyPath = { onCopyPath(row.entry) },
                                    onDropTargetHover = { targetPath ->
                                        onDragStateChange(targetPath != null, targetPath)
                                    },
                                    onDroppedFiles = { paths, targetPath ->
                                        onDragStateChange(false, null)
                                        onDroppedFiles(paths, targetPath)
                                    },
                                    onUnsupportedDrop = {
                                        onDragStateChange(false, null)
                                        onUnsupportedDrop()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDeleteEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDeleteEntry = null },
            title = {
                Text(
                    text = "确认删除",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                Text(
                    text = "是否要删除${if (entry.isDirectory) "文件夹" else "文件"} ${entry.name}？\n${entry.path}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteEntry(entry)
                        pendingDeleteEntry = null
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteEntry = null }) {
                    Text("取消")
                }
            }
        )
    }

    pendingCreateEntry?.let { request ->
        CreateEntryDialog(
            request = request,
            onConfirm = { name ->
                onCreateEntry(request.targetDirectory, name, request.type)
                pendingCreateEntry = null
            },
            onDismiss = { pendingCreateEntry = null },
        )
    }
}

@Composable
private fun FileManagerHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Name", modifier = Modifier.weight(3.4f), fontWeight = FontWeight.SemiBold)
        Text("Permissions", modifier = Modifier.weight(1.2f), fontWeight = FontWeight.SemiBold)
        Text("Date", modifier = Modifier.weight(1.4f), fontWeight = FontWeight.SemiBold)
        Text("Size", modifier = Modifier.weight(0.8f), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FileTreeRow(
    row: RemoteFileTreeRow,
    selected: Boolean,
    dropTargeted: Boolean,
    isRunning: Boolean,
    onToggle: () -> Unit,
    onSelect: () -> Unit,
    onRefresh: () -> Unit,
    onExport: () -> Unit,
    onDeleteRequest: () -> Unit,
    onCreateFileRequest: () -> Unit,
    onCreateDirectoryRequest: () -> Unit,
    onCopyPath: () -> Unit,
    onDropTargetHover: (String?) -> Unit,
    onDroppedFiles: (List<String>, String) -> Unit,
    onUnsupportedDrop: () -> Unit,
) {
    val entry = row.entry
    val normalizedEntryPath = normalizeRemotePath(entry.path)
    FileManagerEntryContextMenu(
        enabled = !isRunning,
        createEnabled = !isRunning && entry.isDirectory,
        deleteEnabled = !isRunning && row.depth > 0,
        onRefresh = onRefresh,
        onExport = onExport,
        onDelete = onDeleteRequest,
        onCreateFile = onCreateFileRequest,
        onCreateDirectory = onCreateDirectoryRequest,
        onCopyPath = onCopyPath,
        onRightClick = onSelect,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (entry.isExpandable && !isRunning) {
                            Modifier.localFileDropTarget(
                                remotePath = normalizedEntryPath,
                                onHover = onDropTargetHover,
                                onFilesDropped = onDroppedFiles,
                                onUnsupportedDrop = onUnsupportedDrop,
                            )
                        } else {
                            Modifier
                        },
                    )
                    .background(
                        when {
                            dropTargeted -> MaterialTheme.colorScheme.tertiaryContainer
                            selected -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surface
                        },
                    )
                    .border(
                        width = if (dropTargeted) 2.dp else 0.dp,
                        color = if (dropTargeted) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    )
                    .clickable(
                        enabled = !isRunning,
                        onClick = if (entry.isExpandable) onToggle else onSelect,
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(3.4f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(modifier = Modifier.width((row.depth * 20).dp))
                    if (row.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(16.dp).height(16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = when {
                                !entry.isExpandable -> " "
                                row.isExpanded -> "▾"
                                else -> "▸"
                            },
                            modifier = Modifier
                                .width(16.dp)
                                .clickable(enabled = entry.isExpandable && !isRunning, onClick = onToggle),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    RemoteFileIcon(entry)
                    Text(
                        text = entry.name,
                        modifier = Modifier.padding(start = 6.dp),
                        fontWeight = if (entry.isExpandable) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (entry.isExpandable) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = entry.permissions,
                    modifier = Modifier.weight(1.2f),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
                Text(
                    text = entry.modifiedTime,
                    modifier = Modifier.weight(1.4f),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (entry.isExpandable) {
                        formatRemoteFileSize(entry.sizeBytes).takeIf { it != "-" } ?: "-"
                    } else {
                        formatRemoteFileSize(entry.sizeBytes)
                    },
                    modifier = Modifier.weight(0.8f),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun RemoteFileIcon(entry: RemoteFileEntry) {
    val icon = fileIconSpec(entry)
    Icon(
        painter = painterResource(icon.resource),
        contentDescription = null,
        modifier = Modifier.padding(start = 6.dp).width(18.dp).height(18.dp),
        tint = icon.tint,
    )
}

@Composable
private fun fileIconSpec(entry: RemoteFileEntry): FileIconSpec {
    val colors = MaterialTheme.colorScheme
    if (entry.type == RemoteFileType.Link) {
        return FileIconSpec(Res.drawable.ic_file_link, colors.tertiary)
    }
    if (entry.isDirectory) {
        return FileIconSpec(Res.drawable.ic_file_folder, colors.primary)
    }

    val extension = entry.name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return when (extension) {
        "apk", "aab" -> FileIconSpec(Res.drawable.ic_file_android, colors.primary)
        "zip", "rar", "7z", "tar", "gz", "gzip", "bz2", "xz", "tgz" ->
            FileIconSpec(Res.drawable.ic_file_archive, colors.secondary)
        "pdf" -> FileIconSpec(Res.drawable.ic_file_picture_as_pdf, colors.error)
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "heif", "svg", "ico" ->
            FileIconSpec(Res.drawable.ic_file_image, colors.tertiary)
        "mp4", "mkv", "avi", "mov", "webm", "m4v", "3gp", "ts" ->
            FileIconSpec(Res.drawable.ic_file_movie, colors.tertiary)
        "mp3", "wav", "flac", "aac", "m4a", "ogg", "opus", "amr" ->
            FileIconSpec(Res.drawable.ic_file_audio_file, colors.tertiary)
        "csv", "tsv", "xls", "xlsx", "ods" ->
            FileIconSpec(Res.drawable.ic_file_table_chart, colors.secondary)
        "sh", "bash", "zsh", "fish", "bat", "cmd", "ps1" ->
            FileIconSpec(Res.drawable.ic_file_terminal, colors.primary)
        "kt", "kts", "java", "xml", "json", "yaml", "yml", "html", "htm", "css", "js", "ts",
        "jsx", "tsx", "c", "cc", "cpp", "h", "hpp", "py", "rb", "go", "rs", "sql", "gradle",
        "properties", "pro", "conf", "ini" ->
            FileIconSpec(Res.drawable.ic_file_code, colors.primary)
        "txt", "log", "md", "markdown", "rtf" ->
            FileIconSpec(Res.drawable.ic_file_text_snippet, colors.onSurfaceVariant)
        "doc", "docx", "ppt", "pptx", "odt", "odp", "epub" ->
            FileIconSpec(Res.drawable.ic_file_description, colors.onSurfaceVariant)
        else -> FileIconSpec(Res.drawable.ic_file_description, colors.onSurfaceVariant)
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun FileManagerEntryContextMenu(
    enabled: Boolean,
    createEnabled: Boolean,
    deleteEnabled: Boolean,
    onRefresh: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onCreateFile: () -> Unit,
    onCreateDirectory: () -> Unit,
    onCopyPath: () -> Unit,
    onRightClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    var menuOffset by remember { mutableStateOf<IntOffset?>(null) }
    var showCreateMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.onPointerEvent(PointerEventType.Press) { event ->
            if (enabled && event.buttons.isSecondaryPressed) {
                val position = event.changes.firstOrNull()?.position ?: Offset.Zero
                menuOffset = IntOffset(position.x.roundToInt(), position.y.roundToInt())
                showCreateMenu = false
                onRightClick()
            }
        },
    ) {
        content()

        val offset = menuOffset
        if (offset != null) {
            Popup(
                popupPositionProvider = FileContextMenuPositionProvider(offset),
                onDismissRequest = {
                    menuOffset = null
                    showCreateMenu = false
                },
                properties = PopupProperties(focusable = false),
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.onPointerEvent(PointerEventType.Exit) {
                        showCreateMenu = false
                    },
                ) {
                    FileContextMenuSurface {
                        FileContextMenuItem(
                            text = "刷新",
                            enabled = enabled,
                            onHover = { showCreateMenu = false },
                            onClick = {
                                menuOffset = null
                                onRefresh()
                            },
                        )
                        FileContextMenuItem(
                            text = "创建  >",
                            enabled = createEnabled,
                            onHover = { showCreateMenu = createEnabled },
                            onClick = { showCreateMenu = createEnabled },
                        )
                        FileContextMenuItem(
                            text = "复制路径",
                            enabled = enabled,
                            onHover = { showCreateMenu = false },
                            onClick = {
                                menuOffset = null
                                onCopyPath()
                            },
                        )
                        FileContextMenuItem(
                            text = "导出",
                            enabled = enabled,
                            onHover = { showCreateMenu = false },
                            onClick = {
                                menuOffset = null
                                onExport()
                            },
                        )
                        FileContextMenuItem(
                            text = "删除",
                            enabled = deleteEnabled,
                            onHover = { showCreateMenu = false },
                            onClick = {
                                menuOffset = null
                                onDelete()
                            },
                            danger = true,
                        )
                    }

                    if (showCreateMenu && createEnabled) {
                        FileContextMenuSurface {
                            FileContextMenuItem(
                                text = "文件",
                                enabled = true,
                                onClick = {
                                    menuOffset = null
                                    showCreateMenu = false
                                    onCreateFile()
                                },
                            )
                            FileContextMenuItem(
                                text = "文件夹",
                                enabled = true,
                                onClick = {
                                    menuOffset = null
                                    showCreateMenu = false
                                    onCreateDirectory()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileContextMenuSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.widthIn(min = 132.dp).padding(vertical = 4.dp),
            content = content,
        )
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun FileContextMenuItem(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    onHover: () -> Unit = {},
    danger: Boolean = false,
) {
    Text(
        text = text,
        modifier = Modifier
            .width(152.dp)
            .height(34.dp)
            .onPointerEvent(PointerEventType.Enter) {
                if (enabled) onHover()
            }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            danger -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurface
        },
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private class FileContextMenuPositionProvider(
    private val offset: IntOffset,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(
            x = (anchorBounds.left + offset.x).coerceIn(0, maxX),
            y = (anchorBounds.top + offset.y).coerceIn(0, maxY),
        )
    }
}

@Composable
private fun CreateEntryDialog(
    request: PendingCreateEntry,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val defaultName = if (request.type == RemoteCreateType.Directory) "NewDir" else "NewFile.txt"
    val title = if (request.type == RemoteCreateType.Directory) "创建文件夹" else "创建文件"
    val label = if (request.type == RemoteCreateType.Directory) "文件夹名" else "文件名"
    var nameValue by remember(request) {
        mutableStateOf(
            TextFieldValue(
                text = defaultName,
                selection = TextRange(0, defaultName.length),
            )
        )
    }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(request) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "目标目录：${request.targetDirectory.path}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                OutlinedTextField(
                    value = nameValue,
                    onValueChange = { nameValue = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    label = { Text(label) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = nameValue.text.trim().isNotEmpty(),
                onClick = { onConfirm(nameValue.text) },
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun EmptyFileManagerState(text: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "目录左侧三角展开；选中目录后可上传文件到该目录。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
