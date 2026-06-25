package com.floatingmuseum.android.test.helper.filemanager

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.floatingmuseum.android.test.helper.AndroidDevice

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
    onRefresh: () -> Unit,
    onToggleEntry: (RemoteFileEntry) -> Unit,
    onSelectEntry: (RemoteFileEntry) -> Unit,
    onExportEntry: (RemoteFileEntry) -> Unit,
    onDroppedFiles: (List<String>, String) -> Unit,
    onDragStateChange: (Boolean, String?) -> Unit,
    onUnsupportedDrop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasReadyDevice = selectedDevice?.isReady == true

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FileManagerToolbar(
            selectedDevice = selectedDevice,
            isRunning = isRunning,
            hasReadyDevice = hasReadyDevice,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxWidth(),
        )

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
                    Text(
                        text = "设备文件",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = if (isDragOver) {
                            dragTargetPath?.let { "松开后上传到 $it" } ?: "拖到目录行上松手才会上传"
                        } else {
                            "右键导出；拖入本地文件到目录行即可上传"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                FileManagerHeader()

                when {
                    !hasReadyDevice -> {
                        EmptyFileManagerState("先选择状态为 device 的设备。")
                    }
                    loadedSerial != selectedDevice.serialNumber -> {
                        EmptyFileManagerState("尚未读取根目录。点击刷新。")
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
                                    onExport = { onExportEntry(row.entry) },
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
}

@Composable
private fun FileManagerToolbar(
    selectedDevice: AndroidDevice?,
    isRunning: Boolean,
    hasReadyDevice: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "文件管理",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = selectedDevice?.let { "${it.model} · ${it.serialNumber}" } ?: "未选择设备",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = onRefresh,
                enabled = hasReadyDevice && !isRunning,
            ) {
                Text("刷新选中目录")
            }
        }
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
    onExport: () -> Unit,
    onDropTargetHover: (String?) -> Unit,
    onDroppedFiles: (List<String>, String) -> Unit,
    onUnsupportedDrop: () -> Unit,
) {
    val entry = row.entry
    val normalizedEntryPath = normalizeRemotePath(entry.path)
    FileManagerEntryContextMenu(
        enabled = !isRunning,
        onExport = onExport,
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
