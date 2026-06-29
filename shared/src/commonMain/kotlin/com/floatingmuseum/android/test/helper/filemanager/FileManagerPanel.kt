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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import com.floatingmuseum.android.test.helper.localization.rememberAppStrings
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
    uploadProgress: FileUploadProgress?,
    onStopUpload: () -> Unit,
    onDroppedFiles: (List<String>, String) -> Unit,
    onDragStateChange: (Boolean, String?) -> Unit,
    onUnsupportedDrop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = rememberAppStrings()
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
                            text = strings.t("file_manager.device_files"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = selectedDevice?.let { "${it.model} · ${it.serialNumber}" } ?: strings.t("file_manager.no_device_selected"),
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = if (isDragOver) {
                            dragTargetPath?.let {
                                strings.t("file_manager.release_to_upload_to_arg0", it)
                            } ?: strings.t("file_manager.drop_on_a_directory_or_file_row_to_upload")
                        } else {
                            strings.t("file_manager.help.context_menu_and_drop_upload")
                        },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                FileManagerHeader()

                uploadProgress?.let { progress ->
                    UploadProgressBar(
                        progress = progress,
                        isRunning = isRunning,
                        onStopUpload = onStopUpload,
                    )
                }

                when {
                    !hasReadyDevice -> {
                        EmptyFileManagerState(strings.t("common.device.select_device_first_with_period"))
                    }
                    loadedSerial != selectedDevice.transportId -> {
                        EmptyFileManagerState(strings.t("file_manager.root_directory_has_not_been_loaded_it_loads_automati"))
                    }
                    else -> {
                        val highlightedDropDirectory = if (isDragOver) {
                            dragTargetPath?.let(::normalizeRemotePath)
                        } else {
                            null
                        }
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
                                    dropHighlighted = highlightedDropDirectory?.let { targetPath ->
                                        isRemotePathInDirectoryTree(row.entry.path, targetPath)
                                    } == true,
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
                    text = strings.t("file_manager.confirm_delete"),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                Text(
                    text = strings.t("file_manager.delete_arg0_arg1_arg2", if (entry.isDirectory) "directory" else "file", entry.name, entry.path),
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
                    Text(strings.t("file_manager.delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteEntry = null }) {
                    Text(strings.t("common.cancel"))
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
private fun UploadProgressBar(
    progress: FileUploadProgress,
    isRunning: Boolean,
    onStopUpload: () -> Unit,
) {
    val strings = rememberAppStrings()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LinearProgressIndicator(
                    progress = { progress.ratio.coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatUploadPercent(progress.ratio),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = strings.t("file_manager.upload.progress_bytes", progress.currentFileIndex, progress.totalFiles, progress.currentFileName, formatUploadBytes(progress.completedBytes), formatUploadBytes(progress.totalBytes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Button(
            onClick = onStopUpload,
            enabled = isRunning,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Text(strings.t("file_manager.upload.stop"))
        }
    }
}

@Composable
private fun FileManagerHeader() {
    val strings = rememberAppStrings()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(strings.t("file_manager.header.name"), modifier = Modifier.weight(3.4f), fontWeight = FontWeight.SemiBold)
        Text(strings.t("file_manager.header.permissions"), modifier = Modifier.weight(1.2f), fontWeight = FontWeight.SemiBold)
        Text(strings.t("file_manager.header.date"), modifier = Modifier.weight(1.4f), fontWeight = FontWeight.SemiBold)
        Text(strings.t("file_manager.header.size"), modifier = Modifier.weight(0.8f), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FileTreeRow(
    row: RemoteFileTreeRow,
    selected: Boolean,
    dropHighlighted: Boolean,
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
    val dropTargetPath = remoteDropTargetDirectoryPath(entry)
    var dropTargeted by remember(normalizedEntryPath) { mutableStateOf(false) }
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
                        if (!isRunning) {
                            Modifier.localFileDropTarget(
                                remotePath = dropTargetPath,
                                onHover = { targetPath ->
                                    dropTargeted = targetPath != null
                                    onDropTargetHover(targetPath)
                                },
                                onFilesDropped = { paths, targetPath ->
                                    dropTargeted = false
                                    onDroppedFiles(paths, targetPath)
                                },
                                onUnsupportedDrop = {
                                    dropTargeted = false
                                    onUnsupportedDrop()
                                },
                            )
                        } else {
                            Modifier
                        },
                    )
                    .background(
                        when {
                            dropHighlighted -> MaterialTheme.colorScheme.errorContainer
                            selected -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surface
                        },
                    )
                    .border(
                        width = if (dropTargeted) 2.dp else 0.dp,
                        color = if (dropTargeted) {
                            MaterialTheme.colorScheme.error
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
    val strings = rememberAppStrings()
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
                            text = strings.t("common.action.refresh"),
                            enabled = enabled,
                            onHover = { showCreateMenu = false },
                            onClick = {
                                menuOffset = null
                                onRefresh()
                            },
                        )
                        FileContextMenuItem(
                            text = strings.t("file_manager.context_menu.create_submenu"),
                            enabled = createEnabled,
                            onHover = { showCreateMenu = createEnabled },
                            onClick = { showCreateMenu = createEnabled },
                        )
                        FileContextMenuItem(
                            text = strings.t("file_manager.copy_path"),
                            enabled = enabled,
                            onHover = { showCreateMenu = false },
                            onClick = {
                                menuOffset = null
                                onCopyPath()
                            },
                        )
                        FileContextMenuItem(
                            text = strings.t("file_manager.export"),
                            enabled = enabled,
                            onHover = { showCreateMenu = false },
                            onClick = {
                                menuOffset = null
                                onExport()
                            },
                        )
                        FileContextMenuItem(
                            text = strings.t("file_manager.delete"),
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
                                text = strings.t("file_manager.entry_type.file_label"),
                                enabled = true,
                                onClick = {
                                    menuOffset = null
                                    showCreateMenu = false
                                    onCreateFile()
                                },
                            )
                            FileContextMenuItem(
                                text = strings.t("file_manager.entry_type.directory_label"),
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
    val strings = rememberAppStrings()
    val defaultName = if (request.type == RemoteCreateType.Directory) "NewDir" else "NewFile.txt"
    val title = if (request.type == RemoteCreateType.Directory) {
        strings.t("file_manager.create_directory")
    } else {
        strings.t("file_manager.create_file")
    }
    val label = if (request.type == RemoteCreateType.Directory) {
        strings.t("file_manager.directory_name")
    } else {
        strings.t("file_manager.file_name")
    }
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
                    text = strings.t("file_manager.target_directory_arg0", request.targetDirectory.path),
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
                Text(strings.t("file_manager.create.confirm"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.t("common.cancel"))
            }
        },
    )
}

@Composable
private fun EmptyFileManagerState(text: String) {
    val strings = rememberAppStrings()
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = strings.t("file_manager.help.expand_and_drop_upload"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatUploadPercent(value: Float): String {
    val percent = (value.coerceIn(0f, 1f) * 100).toInt()
    return "$percent%"
}

private fun formatUploadBytes(bytes: Long): String {
    return if (bytes <= 0L) "0 B" else formatRemoteFileSize(bytes)
}
