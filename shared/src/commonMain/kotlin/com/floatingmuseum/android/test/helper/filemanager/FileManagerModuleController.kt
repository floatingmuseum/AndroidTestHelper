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
import com.floatingmuseum.android.test.helper.localization.commandError
import com.floatingmuseum.android.test.helper.localization.commandStatus
import com.floatingmuseum.android.test.helper.localization.localized
import com.floatingmuseum.android.test.helper.localization.unknownError
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
            val message = localized("common.device.select_device_first")
            setStatusText(message)
            appendCommand(commandError(message))
            return
        }
        loadDirectory(serial, currentPath, forceRefresh = true)
    }

    fun refreshEntryDirectory(entry: RemoteFileEntry) {
        val serial = getSelectedReadyDevice()?.serialNumber
        if (serial == null) {
            val message = localized("common.device.select_device_first")
            setStatusText(message)
            appendCommand(commandError(message))
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
            setStatusText(localized("file_manager.reading_directory_arg0", normalizedPath))
            appendCommand(commandStatus(localized("file_manager.read_directory_arg0_on_device_arg1", normalizedPath, deviceSerial)))
            try {
                loadDirectoryNow(deviceSerial, normalizedPath)
                val count = childrenByPath[normalizedPath].orEmpty().size
                setStatusText(localized("file_manager.read_arg0_arg1_items", normalizedPath, count))
                appendCommand(commandStatus(localized("file_manager.read_directory_arg0_arg1_items", normalizedPath, count)))
            } catch (error: CancellationException) {
                val message = localized("file_manager.operation_stopped")
                setStatusText(message)
                appendCommand(commandStatus(message))
            } catch (error: Throwable) {
                setStatusText(error.message ?: localized("file_manager.directory_read_failed"))
                appendCommand(commandError(localized("file_manager.directory_read_failed") + " - ${error.message ?: unknownError()}"))
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
            val message = localized("common.device.select_device_first")
            setStatusText(message)
            appendCommand(commandError(message))
            return
        }
        if (isRunning()) return

        scope.launch {
            setRunning(true)
            setStatusText(localized("file_manager.select_export_directory"))
            appendCommand(commandStatus(localized("file_manager.select_export_directory_for_arg0", entry.path)))
            try {
                val outputPath = selectDirectory(
                    dialogTitle = localized("file_manager.select_file_export_directory"),
                    approveButtonText = localized("file_manager.export"),
                )
                if (outputPath == null) {
                    val message = localized("file_manager.export_cancelled")
                    setStatusText(message)
                    appendCommand(commandStatus(message))
                } else {
                    val exportedPath = fileManagerAdb.exportPath(serial, entry.path, outputPath, appendCommand)
                    setStatusText(localized("file_manager.exported_arg0_to_arg1", entry.name, exportedPath))
                    appendCommand(commandStatus(localized("file_manager.exported_arg0_to_arg1", entry.path, exportedPath)))
                }
            } catch (error: CancellationException) {
                val message = localized("file_manager.export_stopped")
                setStatusText(message)
                appendCommand(commandStatus(message))
            } catch (error: Throwable) {
                setStatusText(error.message ?: localized("file_manager.export_failed"))
                appendCommand(commandError(localized("file_manager.export_failed") + " - ${error.message ?: unknownError()}"))
            } finally {
                setRunning(false)
            }
        }
    }

    fun deleteEntry(entry: RemoteFileEntry) {
        val serial = getSelectedReadyDevice()?.serialNumber
        if (serial == null) {
            val message = localized("common.device.select_device_first")
            setStatusText(message)
            appendCommand(commandError(message))
            return
        }
        if (isRunning()) return

        val normalizedPath = normalizeRemotePath(entry.path)
        if (normalizedPath == appliedRootPath || normalizedPath == "/") {
            val message = localized("file_manager.cannot_delete_the_current_file_manager_root")
            setStatusText(message)
            appendCommand(commandError("$message $normalizedPath"))
            return
        }

        scope.launch {
            setRunning(true)
            setStatusText(localized("file_manager.deleting_arg0", entry.name))
            appendCommand(commandStatus(localized("file_manager.delete_arg0_on_device_arg1", entry.path, serial)))
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
                setStatusText(localized("file_manager.deleted_arg0", entry.name))
                appendCommand(commandStatus(localized("file_manager.deleted_arg0_and_refreshed_arg1", entry.path, parentPath)))
            } catch (error: CancellationException) {
                val message = localized("file_manager.delete_stopped")
                setStatusText(message)
                appendCommand(commandStatus(message))
            } catch (error: Throwable) {
                setStatusText(error.message ?: localized("file_manager.delete_failed"))
                appendCommand(commandError(localized("file_manager.delete_failed") + " - ${error.message ?: unknownError()}"))
            } finally {
                setRunning(false)
            }
        }
    }

    fun createEntry(targetDirectory: RemoteFileEntry, name: String, type: RemoteCreateType) {
        val serial = getSelectedReadyDevice()?.serialNumber
        if (serial == null) {
            val message = localized("common.device.select_device_first")
            setStatusText(message)
            appendCommand(commandError(message))
            return
        }
        if (isRunning()) return
        if (!targetDirectory.isDirectory) {
            val message = localized("file_manager.can_only_create_inside_a_directory")
            setStatusText(message)
            appendCommand(commandError(message))
            return
        }

        val normalizedDirectory = normalizeRemotePath(targetDirectory.path)
        val childName = try {
            validateRemoteChildName(name)
        } catch (error: IllegalArgumentException) {
            setStatusText(error.message ?: localized("file_manager.invalid_name"))
            appendCommand(commandError(localized("file_manager.create_failed") + " - ${error.message ?: localized("file_manager.invalid_name")}"))
            return
        }

        scope.launch {
            setRunning(true)
            val createTypeText = if (type == RemoteCreateType.Directory) {
                localized("file_manager.entry_type.directory_lowercase")
            } else {
                localized("file_manager.entry_type.file_lowercase")
            }
            setStatusText(localized("file_manager.creating_arg0_arg1", createTypeText, childName))
            appendCommand(commandStatus(localized("file_manager.create_arg0_arg1_on_device_arg2_arg3", createTypeText, childName, serial, normalizedDirectory)))
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
                setStatusText(localized("file_manager.created_arg0", createdPath))
                appendCommand(commandStatus(localized("file_manager.created_arg0_and_refreshed_arg1", createdPath, normalizedDirectory)))
            } catch (error: CancellationException) {
                val message = localized("file_manager.create_stopped")
                setStatusText(message)
                appendCommand(commandStatus(message))
            } catch (error: Throwable) {
                setStatusText(error.message ?: localized("file_manager.create_failed"))
                appendCommand(commandError(localized("file_manager.create_failed") + " - ${error.message ?: unknownError()}"))
            } finally {
                setRunning(false)
            }
        }
    }

    fun copyEntryPath(path: String) {
        setStatusText(localized("file_manager.copied_path_arg0", path))
        appendCommand(commandStatus(localized("file_manager.copied_absolute_path_arg0_to_clipboard", path)))
    }

    fun uploadDroppedFiles(filePaths: List<String>, targetDirectoryPath: String) {
        if (filePaths.isEmpty()) return
        if (isRunning()) {
            val message = localized("file_manager.a_task_is_already_running_cannot_upload")
            setStatusText(message)
            appendCommand(commandError(message))
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
        setStatusText(localized("file_manager.stopping_upload"))
        appendCommand(commandStatus(localized("file_manager.request_upload_stop")))
        uploadJob?.cancel()
    }

    fun updateDragOver(value: Boolean, targetDirectoryPath: String?) {
        isDragOver = value
        dragTargetPath = if (value) targetDirectoryPath?.let(::normalizeRemotePath) else null
    }

    fun handleUnsupportedDrop() {
        val message = localized("file_manager.drop_on_a_directory_row_or_a_file_row_inside_a_direc")
        setStatusText(message)
        appendCommand(commandStatus(localized("file_manager.drop_did_not_hit_an_uploadable_directory")))
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
            val message = localized("common.device.select_device_first")
            setStatusText(message)
            appendCommand(commandError(message))
            return
        }
        val normalizedTarget = normalizeRemotePath(targetDirectoryPath)
        try {
            setStatusText(localized("file_manager.uploading_arg0_files_to_arg1", filePaths.size, normalizedTarget))
            appendCommand(commandStatus(localized("file_manager.upload_arg0_local_files_to_device_arg1_arg2", filePaths.size, serial, normalizedTarget)))
            val count = fileManagerAdb.uploadFiles(
                deviceSerial = serial,
                localFilePaths = filePaths,
                remoteDirectoryPath = normalizedTarget,
                logCommand = appendCommand,
                onProgress = { progress ->
                    uploadProgress = progress
                    setStatusText(
                        localized("file_manager.upload_arg0_arg1_arg2_arg3", progress.currentFileIndex, progress.totalFiles, progress.currentFileName, formatFileManagerProgressPercent(progress.ratio)),
                    )
                },
            )
            val children = fileManagerAdb.listDirectory(serial, normalizedTarget, appendCommand)
            childrenByPath = childrenByPath + (normalizedTarget to children)
            expandedPaths = expandedPaths + normalizedTarget
            currentPath = normalizedTarget
            selectedEntryPath = normalizedTarget
            loadedSerial = serial
            setStatusText(localized("file_manager.uploaded_arg0_files_to_arg1", count, normalizedTarget))
            appendCommand(commandStatus(localized("file_manager.uploaded_arg0_files_to_arg1_and_refreshed_directory", count, normalizedTarget)))
        } catch (error: CancellationException) {
            setStatusText(localized("file_manager.upload_stopped_refreshing_directory"))
            appendCommand(commandStatus(localized("file_manager.upload_stopped_refreshing_arg0", normalizedTarget)))
            try {
                val children = withContext(NonCancellable) {
                    fileManagerAdb.listDirectory(serial, normalizedTarget, appendCommand)
                }
                childrenByPath = childrenByPath + (normalizedTarget to children)
                expandedPaths = expandedPaths + normalizedTarget
                currentPath = normalizedTarget
                selectedEntryPath = normalizedTarget
                loadedSerial = serial
                setStatusText(localized("file_manager.upload_stopped_refreshed_arg0", normalizedTarget))
                appendCommand(commandStatus(localized("file_manager.upload_stopped_refreshed_arg0", normalizedTarget)))
            } catch (refreshError: Throwable) {
                setStatusText(localized("file_manager.upload_stopped_refresh_failed_arg0", refreshError.message ?: unknownError()))
                appendCommand(commandError(localized("file_manager.refresh_failed_after_upload_stopped") + " - ${refreshError.message ?: unknownError()}"))
            }
        } catch (error: Throwable) {
            setStatusText(error.message ?: localized("file_manager.upload_failed"))
            appendCommand(commandError(localized("file_manager.upload_failed") + " - ${error.message ?: unknownError()}"))
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
