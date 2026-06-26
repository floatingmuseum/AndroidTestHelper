package com.floatingmuseum.android.test.helper.filemanager

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.floatingmuseum.android.test.helper.AndroidDevice
import com.floatingmuseum.android.test.helper.selectDirectory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun rootEntry(rootPath: String) = RemoteFileEntry(
    name = rootPath,
    path = rootPath,
    type = RemoteFileType.Directory,
    sizeBytes = 0L,
    permissions = "-",
    modifiedTime = "-",
)

internal class FileManagerModuleController(
    private val fileManagerAdb: FileManagerAdb,
    private val scope: CoroutineScope,
    private val getSelectedReadyDevice: () -> AndroidDevice?,
    private val getDefaultRootPath: () -> String,
    private val isRunning: () -> Boolean,
    private val setRunning: (Boolean) -> Unit,
    private val setStatusText: (String) -> Unit,
    private val appendCommand: (String) -> Unit,
) {
    var appliedRootPath by mutableStateOf(normalizedDefaultRootPath())
        private set
    var currentPath by mutableStateOf(appliedRootPath)
        private set
    var childrenByPath by mutableStateOf<Map<String, List<RemoteFileEntry>>>(emptyMap())
        private set
    var expandedPaths by mutableStateOf(setOf(appliedRootPath))
        private set
    var loadedSerial by mutableStateOf<String?>(null)
        private set
    var selectedEntryPath by mutableStateOf(appliedRootPath)
        private set
    var loadingPath by mutableStateOf<String?>(null)
        private set
    var isDragOver by mutableStateOf(false)
        private set
    var dragTargetPath by mutableStateOf<String?>(null)
        private set
    var uploadProgress by mutableStateOf<FileUploadProgress?>(null)
        private set

    private var uploadJob by mutableStateOf<Job?>(null)

    val treeRows: List<RemoteFileTreeRow>
        get() = buildTreeRows()

    fun clearDeviceState() {
        uploadJob?.cancel()
        val rootPath = normalizedDefaultRootPath()
        appliedRootPath = rootPath
        childrenByPath = emptyMap()
        expandedPaths = setOf(rootPath)
        loadedSerial = null
        selectedEntryPath = rootPath
        currentPath = rootPath
        loadingPath = null
        isDragOver = false
        dragTargetPath = null
        uploadProgress = null
        uploadJob = null
    }

    fun applyDefaultRootPath(): Boolean {
        val rootPath = normalizedDefaultRootPath()
        if (rootPath == appliedRootPath) return false
        uploadJob?.cancel()
        appliedRootPath = rootPath
        childrenByPath = emptyMap()
        expandedPaths = setOf(rootPath)
        loadedSerial = null
        selectedEntryPath = rootPath
        currentPath = rootPath
        loadingPath = null
        isDragOver = false
        dragTargetPath = null
        uploadProgress = null
        uploadJob = null
        return true
    }

    fun refreshSelectedDirectory() {
        applyDefaultRootPath()
        val serial = getSelectedReadyDevice()?.serialNumber
        if (serial == null) {
            setStatusText("先选择状态为 device 的设备")
            appendCommand("错误: 先选择状态为 device 的设备")
            return
        }
        loadDirectory(serial, currentPath, forceRefresh = true)
    }

    fun refreshEntryDirectory(entry: RemoteFileEntry) {
        val serial = getSelectedReadyDevice()?.serialNumber
        if (serial == null) {
            setStatusText("先选择状态为 device 的设备")
            appendCommand("错误: 先选择状态为 device 的设备")
            return
        }
        val targetPath = if (entry.isDirectory) entry.path else parentRemotePath(entry.path)
        loadDirectory(serial, targetPath, forceRefresh = true)
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
        loadDirectoryNow(deviceSerial, normalizedDefaultRootPath())
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

    fun deleteEntry(entry: RemoteFileEntry) {
        val serial = getSelectedReadyDevice()?.serialNumber
        if (serial == null) {
            setStatusText("先选择状态为 device 的设备")
            appendCommand("错误: 先选择状态为 device 的设备")
            return
        }
        if (isRunning()) return

        val normalizedPath = normalizeRemotePath(entry.path)
        if (normalizedPath == appliedRootPath || normalizedPath == "/") {
            setStatusText("不能删除当前文件管理根目录")
            appendCommand("错误: 不能删除当前文件管理根目录 $normalizedPath")
            return
        }

        scope.launch {
            setRunning(true)
            setStatusText("删除 ${entry.name}...")
            appendCommand("状态: 删除设备 $serial 文件 ${entry.path}")
            try {
                fileManagerAdb.deletePath(serial, normalizedPath, appendCommand)
                val parentPath = parentRemotePath(normalizedPath)
                val refreshedChildren = fileManagerAdb.listDirectory(serial, parentPath, appendCommand)
                val descendantPrefix = "$normalizedPath/"
                childrenByPath = childrenByPath
                    .filterKeys { path -> path != normalizedPath && !path.startsWith(descendantPrefix) }
                    .plus(parentPath to refreshedChildren)
                expandedPaths = expandedPaths
                    .filterNot { path -> path == normalizedPath || path.startsWith(descendantPrefix) }
                    .toSet() + parentPath
                if (currentPath == normalizedPath || currentPath.startsWith(descendantPrefix)) {
                    currentPath = parentPath
                }
                selectedEntryPath = parentPath
                loadedSerial = serial
                setStatusText("已删除 ${entry.name}")
                appendCommand("状态: 已删除 ${entry.path}，并刷新 $parentPath")
            } catch (error: CancellationException) {
                setStatusText("删除已停止")
                appendCommand("状态: 删除已停止")
            } catch (error: Throwable) {
                setStatusText(error.message ?: "删除失败")
                appendCommand("错误: 删除失败 - ${error.message ?: "未知错误"}")
            } finally {
                setRunning(false)
            }
        }
    }

    fun createEntry(targetDirectory: RemoteFileEntry, name: String, type: RemoteCreateType) {
        val serial = getSelectedReadyDevice()?.serialNumber
        if (serial == null) {
            setStatusText("先选择状态为 device 的设备")
            appendCommand("错误: 先选择状态为 device 的设备")
            return
        }
        if (isRunning()) return
        if (!targetDirectory.isDirectory) {
            setStatusText("只能在文件夹内创建")
            appendCommand("错误: 只能在文件夹内创建")
            return
        }

        val normalizedDirectory = normalizeRemotePath(targetDirectory.path)
        val childName = try {
            validateRemoteChildName(name)
        } catch (error: IllegalArgumentException) {
            setStatusText(error.message ?: "名称无效")
            appendCommand("错误: 创建失败 - ${error.message ?: "名称无效"}")
            return
        }

        scope.launch {
            setRunning(true)
            val createTypeText = if (type == RemoteCreateType.Directory) "文件夹" else "文件"
            setStatusText("创建$createTypeText $childName...")
            appendCommand("状态: 在设备 $serial:$normalizedDirectory 创建$createTypeText $childName")
            try {
                val createdPath = fileManagerAdb.createPath(
                    deviceSerial = serial,
                    remoteDirectoryPath = normalizedDirectory,
                    name = childName,
                    type = type,
                    logCommand = appendCommand,
                )
                val refreshedChildren = fileManagerAdb.listDirectory(serial, normalizedDirectory, appendCommand)
                childrenByPath = childrenByPath + (normalizedDirectory to refreshedChildren)
                expandedPaths = expandedPaths + normalizedDirectory
                currentPath = normalizedDirectory
                selectedEntryPath = createdPath
                loadedSerial = serial
                setStatusText("已创建 $createdPath")
                appendCommand("状态: 已创建 $createdPath，并刷新 $normalizedDirectory")
            } catch (error: CancellationException) {
                setStatusText("创建已停止")
                appendCommand("状态: 创建已停止")
            } catch (error: Throwable) {
                setStatusText(error.message ?: "创建失败")
                appendCommand("错误: 创建失败 - ${error.message ?: "未知错误"}")
            } finally {
                setRunning(false)
            }
        }
    }

    fun copyEntryPath(path: String) {
        setStatusText("已复制路径: $path")
        appendCommand("状态: 已复制绝对路径 $path 到剪贴板")
    }

    fun uploadDroppedFiles(filePaths: List<String>, targetDirectoryPath: String) {
        if (filePaths.isEmpty()) return
        if (isRunning()) {
            setStatusText("已有任务运行，暂不能上传")
            appendCommand("错误: 已有任务运行，暂不能上传")
            return
        }
        val job = scope.launch {
            setRunning(true)
            uploadProgress = null
            try {
                uploadFilesToDirectory(filePaths, targetDirectoryPath)
            } finally {
                uploadProgress = null
                uploadJob = null
                setRunning(false)
            }
        }
        uploadJob = job
    }

    fun stopUpload() {
        if (uploadJob == null) return
        setStatusText("正在中止上传...")
        appendCommand("状态: 请求中止上传")
        uploadJob?.cancel()
    }

    fun updateDragOver(value: Boolean, targetDirectoryPath: String?) {
        isDragOver = value
        dragTargetPath = if (value) targetDirectoryPath?.let(::normalizeRemotePath) else null
    }

    fun handleUnsupportedDrop() {
        setStatusText("请拖到目录行或目录内文件行上松手")
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
            val count = fileManagerAdb.uploadFiles(
                deviceSerial = serial,
                localFilePaths = filePaths,
                remoteDirectoryPath = normalizedTarget,
                logCommand = appendCommand,
                onProgress = { progress ->
                    uploadProgress = progress
                    setStatusText(
                        "上传 ${progress.currentFileIndex}/${progress.totalFiles}: " +
                            "${progress.currentFileName} ${formatFileManagerProgressPercent(progress.ratio)}",
                    )
                },
            )
            val children = fileManagerAdb.listDirectory(serial, normalizedTarget, appendCommand)
            childrenByPath = childrenByPath + (normalizedTarget to children)
            expandedPaths = expandedPaths + normalizedTarget
            currentPath = normalizedTarget
            selectedEntryPath = normalizedTarget
            loadedSerial = serial
            setStatusText("已上传 $count 个文件到 $normalizedTarget")
            appendCommand("状态: 已上传 $count 个文件到 $normalizedTarget，并刷新目录")
        } catch (error: CancellationException) {
            setStatusText("上传已中止，刷新目录...")
            appendCommand("状态: 上传已中止，刷新 $normalizedTarget")
            try {
                val children = withContext(NonCancellable) {
                    fileManagerAdb.listDirectory(serial, normalizedTarget, appendCommand)
                }
                childrenByPath = childrenByPath + (normalizedTarget to children)
                expandedPaths = expandedPaths + normalizedTarget
                currentPath = normalizedTarget
                selectedEntryPath = normalizedTarget
                loadedSerial = serial
                setStatusText("上传已中止，已刷新 $normalizedTarget")
                appendCommand("状态: 上传已中止，已刷新 $normalizedTarget")
            } catch (refreshError: Throwable) {
                setStatusText("上传已中止，刷新失败：${refreshError.message ?: "未知错误"}")
                appendCommand("错误: 上传中止后刷新失败 - ${refreshError.message ?: "未知错误"}")
            }
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
        append(rootEntry(appliedRootPath), 0)
        return rows
    }

    private fun normalizedDefaultRootPath(): String = normalizeRemotePath(getDefaultRootPath())
}

@Composable
internal fun rememberFileManagerModuleController(
    scope: CoroutineScope,
    getSelectedReadyDevice: () -> AndroidDevice?,
    getDefaultRootPath: () -> String,
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
            getDefaultRootPath = getDefaultRootPath,
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
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    FileManagerPanel(
        selectedDevice = selectedDevice,
        currentPath = controller.currentPath,
        treeRows = controller.treeRows,
        loadedSerial = controller.loadedSerial,
        selectedEntryPath = controller.selectedEntryPath,
        dragTargetPath = controller.dragTargetPath,
        isRunning = isRunning,
        isDragOver = controller.isDragOver,
        onToggleEntry = controller::toggleEntry,
        onSelectEntry = controller::selectEntry,
        onRefreshEntry = controller::refreshEntryDirectory,
        onExportEntry = controller::exportEntry,
        onDeleteEntry = controller::deleteEntry,
        onCreateEntry = controller::createEntry,
        onCopyPath = { entry ->
            clipboardManager.setText(AnnotatedString(entry.path))
            controller.copyEntryPath(entry.path)
        },
        uploadProgress = controller.uploadProgress,
        onStopUpload = controller::stopUpload,
        onDroppedFiles = controller::uploadDroppedFiles,
        onDragStateChange = controller::updateDragOver,
        onUnsupportedDrop = controller::handleUnsupportedDrop,
        modifier = modifier,
    )
}

private fun formatFileManagerProgressPercent(value: Float): String {
    val percent = (value.coerceIn(0f, 1f) * 100).toInt()
    return "$percent%"
}
