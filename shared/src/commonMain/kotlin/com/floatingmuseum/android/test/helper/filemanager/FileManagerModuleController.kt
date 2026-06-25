package com.floatingmuseum.android.test.helper.filemanager

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.floatingmuseum.android.test.helper.AndroidDevice
import com.floatingmuseum.android.test.helper.selectDirectory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val RootEntry = RemoteFileEntry(
    name = "/",
    path = "/",
    type = RemoteFileType.Directory,
    sizeBytes = 0L,
    permissions = "-",
    modifiedTime = "-",
)

internal class FileManagerModuleController(
    private val fileManagerAdb: FileManagerAdb,
    private val scope: CoroutineScope,
    private val getSelectedReadyDevice: () -> AndroidDevice?,
    private val isRunning: () -> Boolean,
    private val setRunning: (Boolean) -> Unit,
    private val setStatusText: (String) -> Unit,
    private val appendCommand: (String) -> Unit,
) {
    var currentPath by mutableStateOf("/")
        private set
    var childrenByPath by mutableStateOf<Map<String, List<RemoteFileEntry>>>(emptyMap())
        private set
    var expandedPaths by mutableStateOf(setOf("/"))
        private set
    var loadedSerial by mutableStateOf<String?>(null)
        private set
    var selectedEntryPath by mutableStateOf("/")
        private set
    var loadingPath by mutableStateOf<String?>(null)
        private set
    var isDragOver by mutableStateOf(false)
        private set
    var dragTargetPath by mutableStateOf<String?>(null)
        private set

    val treeRows: List<RemoteFileTreeRow>
        get() = buildTreeRows()

    fun clearDeviceState() {
        childrenByPath = emptyMap()
        expandedPaths = setOf("/")
        loadedSerial = null
        selectedEntryPath = "/"
        currentPath = "/"
        loadingPath = null
        isDragOver = false
        dragTargetPath = null
    }

    fun refreshSelectedDirectory() {
        val serial = getSelectedReadyDevice()?.serialNumber
        if (serial == null) {
            setStatusText("先选择状态为 device 的设备")
            appendCommand("错误: 先选择状态为 device 的设备")
            return
        }
        loadDirectory(serial, currentPath, forceRefresh = true)
    }

    fun toggleEntry(entry: RemoteFileEntry) {
        selectedEntryPath = entry.path
        if (!entry.isExpandable) return

        val normalizedPath = normalizeRemotePath(entry.path)
        currentPath = normalizedPath
        if (expandedPaths.contains(normalizedPath)) {
            expandedPaths = expandedPaths - normalizedPath
            return
        }

        val serial = getSelectedReadyDevice()?.serialNumber ?: return
        if (childrenByPath.containsKey(normalizedPath)) {
            expandedPaths = expandedPaths + normalizedPath
        } else {
            loadDirectory(serial, normalizedPath, forceRefresh = false)
        }
    }

    fun selectEntry(entry: RemoteFileEntry) {
        selectedEntryPath = entry.path
        if (entry.isExpandable) {
            currentPath = normalizeRemotePath(entry.path)
        }
    }

    fun loadDirectory(
        deviceSerial: String,
        remotePath: String,
        forceRefresh: Boolean = false,
    ) {
        if (isRunning()) return
        val normalizedPath = normalizeRemotePath(remotePath)
        if (!forceRefresh && childrenByPath.containsKey(normalizedPath)) {
            currentPath = normalizedPath
            selectedEntryPath = normalizedPath
            expandedPaths = expandedPaths + normalizedPath
            return
        }

        scope.launch {
            setRunning(true)
            loadingPath = normalizedPath
            setStatusText("读取目录 $normalizedPath...")
            appendCommand("状态: 读取设备 $deviceSerial 目录 $normalizedPath")
            try {
                loadDirectoryNow(deviceSerial, normalizedPath)
                setStatusText("已读取 $normalizedPath，共 ${childrenByPath[normalizedPath].orEmpty().size} 项")
                appendCommand("状态: 已读取目录 $normalizedPath，共 ${childrenByPath[normalizedPath].orEmpty().size} 项")
            } catch (error: CancellationException) {
                setStatusText("文件管理操作已停止")
                appendCommand("状态: 文件管理操作已停止")
            } catch (error: Throwable) {
                setStatusText(error.message ?: "读取目录失败")
                appendCommand("错误: 读取目录失败 - ${error.message ?: "未知错误"}")
            } finally {
                loadingPath = null
                setRunning(false)
            }
        }
    }

    suspend fun loadRootForDeviceScan(deviceSerial: String) {
        loadDirectoryNow(deviceSerial, "/")
    }

    fun exportEntry(entry: RemoteFileEntry) {
        val serial = getSelectedReadyDevice()?.serialNumber
        if (serial == null) {
            setStatusText("先选择状态为 device 的设备")
            appendCommand("错误: 先选择状态为 device 的设备")
            return
        }
        if (isRunning()) return

        scope.launch {
            setRunning(true)
            setStatusText("选择导出目录...")
            appendCommand("状态: 选择 ${entry.path} 导出目录")
            try {
                val outputPath = selectDirectory(
                    dialogTitle = "选择文件导出路径",
                    approveButtonText = "导出",
                )
                if (outputPath == null) {
                    setStatusText("已取消导出")
                    appendCommand("状态: 已取消导出")
                } else {
                    val exportedPath = fileManagerAdb.exportPath(serial, entry.path, outputPath, appendCommand)
                    setStatusText("已导出 ${entry.name} 到 $exportedPath")
                    appendCommand("状态: 已导出 ${entry.path} 到 $exportedPath")
                }
            } catch (error: CancellationException) {
                setStatusText("导出已停止")
                appendCommand("状态: 导出已停止")
            } catch (error: Throwable) {
                setStatusText(error.message ?: "导出失败")
                appendCommand("错误: 导出失败 - ${error.message ?: "未知错误"}")
            } finally {
                setRunning(false)
            }
        }
    }

    fun uploadDroppedFiles(filePaths: List<String>, targetDirectoryPath: String) {
        if (filePaths.isEmpty()) return
        if (isRunning()) {
            setStatusText("已有任务运行，暂不能上传")
            appendCommand("错误: 已有任务运行，暂不能上传")
            return
        }
        scope.launch {
            setRunning(true)
            try {
                uploadFilesToDirectory(filePaths, targetDirectoryPath)
            } finally {
                setRunning(false)
            }
        }
    }

    fun updateDragOver(value: Boolean, targetDirectoryPath: String?) {
        isDragOver = value
        dragTargetPath = if (value) targetDirectoryPath?.let(::normalizeRemotePath) else null
    }

    fun handleUnsupportedDrop() {
        setStatusText("请拖到目录行上松手，只有目录可以接收上传")
        appendCommand("状态: 拖拽未命中可上传目录")
    }

    private suspend fun loadDirectoryNow(deviceSerial: String, remotePath: String) {
        val normalizedPath = normalizeRemotePath(remotePath)
        val children = fileManagerAdb.listDirectory(deviceSerial, normalizedPath, appendCommand)
        childrenByPath = childrenByPath + (normalizedPath to children)
        expandedPaths = expandedPaths + normalizedPath
        currentPath = normalizedPath
        selectedEntryPath = normalizedPath
        loadedSerial = deviceSerial
    }

    private suspend fun uploadFilesToDirectory(filePaths: List<String>, targetDirectoryPath: String) {
        val serial = getSelectedReadyDevice()?.serialNumber
        if (serial == null) {
            setStatusText("先选择状态为 device 的设备")
            appendCommand("错误: 先选择状态为 device 的设备")
            return
        }
        val normalizedTarget = normalizeRemotePath(targetDirectoryPath)
        try {
            setStatusText("上传 ${filePaths.size} 个文件到 $normalizedTarget...")
            appendCommand("状态: 上传 ${filePaths.size} 个本地文件到设备 $serial:$normalizedTarget")
            val count = fileManagerAdb.uploadFiles(serial, filePaths, normalizedTarget, appendCommand)
            val children = fileManagerAdb.listDirectory(serial, normalizedTarget, appendCommand)
            childrenByPath = childrenByPath + (normalizedTarget to children)
            expandedPaths = expandedPaths + normalizedTarget
            currentPath = normalizedTarget
            selectedEntryPath = normalizedTarget
            loadedSerial = serial
            setStatusText("已上传 $count 个文件到 $normalizedTarget")
            appendCommand("状态: 已上传 $count 个文件到 $normalizedTarget，并刷新目录")
        } catch (error: CancellationException) {
            setStatusText("上传已停止")
            appendCommand("状态: 上传已停止")
        } catch (error: Throwable) {
            setStatusText(error.message ?: "上传失败")
            appendCommand("错误: 上传失败 - ${error.message ?: "未知错误"}")
        }
    }

    private fun buildTreeRows(): List<RemoteFileTreeRow> {
        val rows = mutableListOf<RemoteFileTreeRow>()
        fun append(entry: RemoteFileEntry, depth: Int) {
            val path = normalizeRemotePath(entry.path)
            val expanded = expandedPaths.contains(path)
            rows += RemoteFileTreeRow(
                entry = entry,
                depth = depth,
                isExpanded = expanded,
                isLoading = loadingPath == path,
            )
            if (expanded) {
                childrenByPath[path].orEmpty().forEach { child ->
                    append(child, depth + 1)
                }
            }
        }
        append(RootEntry, 0)
        return rows
    }
}

@Composable
internal fun rememberFileManagerModuleController(
    scope: CoroutineScope,
    getSelectedReadyDevice: () -> AndroidDevice?,
    isRunning: () -> Boolean,
    setRunning: (Boolean) -> Unit,
    setStatusText: (String) -> Unit,
    appendCommand: (String) -> Unit,
): FileManagerModuleController {
    val fileManagerAdb = remember { createFileManagerAdb() }
    return remember {
        FileManagerModuleController(
            fileManagerAdb = fileManagerAdb,
            scope = scope,
            getSelectedReadyDevice = getSelectedReadyDevice,
            isRunning = isRunning,
            setRunning = setRunning,
            setStatusText = setStatusText,
            appendCommand = appendCommand,
        )
    }
}

@Composable
internal fun FileManagerModuleContent(
    controller: FileManagerModuleController,
    selectedDevice: AndroidDevice?,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    FileManagerPanel(
        selectedDevice = selectedDevice,
        currentPath = controller.currentPath,
        treeRows = controller.treeRows,
        loadedSerial = controller.loadedSerial,
        selectedEntryPath = controller.selectedEntryPath,
        dragTargetPath = controller.dragTargetPath,
        isRunning = isRunning,
        isDragOver = controller.isDragOver,
        onRefresh = controller::refreshSelectedDirectory,
        onToggleEntry = controller::toggleEntry,
        onSelectEntry = controller::selectEntry,
        onExportEntry = controller::exportEntry,
        onDroppedFiles = controller::uploadDroppedFiles,
        onDragStateChange = controller::updateDragOver,
        onUnsupportedDrop = controller::handleUnsupportedDrop,
        modifier = modifier,
    )
}
