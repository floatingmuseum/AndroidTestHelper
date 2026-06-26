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
            val message = localized("auto.select_a_device_in_device_state_first.cf5bc374")
            setStatusText(message)
            appendCommand(commandError(message))
            return
        }
        loadDirectory(serial, currentPath, forceRefresh = true)
    }

    fun refreshEntryDirectory(entry: RemoteFileEntry) {
        val serial = getSelectedReadyDevice()?.serialNumber
        if (serial == null) {
            val message = localized("auto.select_a_device_in_device_state_first.cf5bc374")
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
            setStatusText(localized("auto.reading_directory_0.095874ba", normalizedPath))
            appendCommand(commandStatus(localized("auto.read_directory_0_on_device_1.2ee7c1b5", normalizedPath, deviceSerial)))
            try {
                loadDirectoryNow(deviceSerial, normalizedPath)
                val count = childrenByPath[normalizedPath].orEmpty().size
                setStatusText(localized("auto.read_0_1_items.e1a8eca4", normalizedPath, count))
                appendCommand(commandStatus(localized("auto.read_directory_0_1_items.eda0769f", normalizedPath, count)))
            } catch (error: CancellationException) {
                val message = localized("auto.file_manager_operation_stopped.945bb508")
                setStatusText(message)
                appendCommand(commandStatus(message))
            } catch (error: Throwable) {
                setStatusText(error.message ?: localized("auto.directory_read_failed.824769f2"))
                appendCommand(commandError(localized("auto.directory_read_failed.824769f2") + " - ${error.message ?: unknownError()}"))
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
            val message = localized("auto.select_a_device_in_device_state_first.cf5bc374")
            setStatusText(message)
            appendCommand(commandError(message))
            return
        }
        if (isRunning()) return

        scope.launch {
            setRunning(true)
            setStatusText(localized("auto.select_export_directory.cd01fd2b"))
            appendCommand(commandStatus(localized("auto.select_export_directory_for_0.c0dd8ac3", entry.path)))
            try {
                val outputPath = selectDirectory(
                    dialogTitle = localized("auto.select_file_export_directory.9cf7507b"),
                    approveButtonText = localized("auto.export.5a8c8fe7"),
                )
                if (outputPath == null) {
                    val message = localized("auto.export_cancelled.658ddbf7")
                    setStatusText(message)
                    appendCommand(commandStatus(message))
                } else {
                    val exportedPath = fileManagerAdb.exportPath(serial, entry.path, outputPath, appendCommand)
                    setStatusText(localized("auto.exported_0_to_1.b4782f86", entry.name, exportedPath))
                    appendCommand(commandStatus(localized("auto.exported_0_to_1.b4782f86", entry.path, exportedPath)))
                }
            } catch (error: CancellationException) {
                val message = localized("auto.export_stopped.6fefb88a")
                setStatusText(message)
                appendCommand(commandStatus(message))
            } catch (error: Throwable) {
                setStatusText(error.message ?: localized("auto.export_failed.b732b4aa"))
                appendCommand(commandError(localized("auto.export_failed.b732b4aa") + " - ${error.message ?: unknownError()}"))
            } finally {
                setRunning(false)
            }
        }
    }

    fun deleteEntry(entry: RemoteFileEntry) {
        val serial = getSelectedReadyDevice()?.serialNumber
        if (serial == null) {
            val message = localized("auto.select_a_device_in_device_state_first.cf5bc374")
            setStatusText(message)
            appendCommand(commandError(message))
            return
        }
        if (isRunning()) return

        val normalizedPath = normalizeRemotePath(entry.path)
        if (normalizedPath == appliedRootPath || normalizedPath == "/") {
            val message = localized("auto.cannot_delete_the_current_file_manager_root.d9deddd5")
            setStatusText(message)
            appendCommand(commandError("$message $normalizedPath"))
            return
        }

        scope.launch {
            setRunning(true)
            setStatusText(localized("auto.deleting_0.65ecf623", entry.name))
            appendCommand(commandStatus(localized("auto.delete_0_on_device_1.a57bf6d9", entry.path, serial)))
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
                setStatusText(localized("auto.deleted_0.3efd7127", entry.name))
                appendCommand(commandStatus(localized("auto.deleted_0_and_refreshed_1.125ae0ae", entry.path, parentPath)))
            } catch (error: CancellationException) {
                val message = localized("auto.delete_stopped.a7aec9a4")
                setStatusText(message)
                appendCommand(commandStatus(message))
            } catch (error: Throwable) {
                setStatusText(error.message ?: localized("auto.delete_failed.4ee171d6"))
                appendCommand(commandError(localized("auto.delete_failed.4ee171d6") + " - ${error.message ?: unknownError()}"))
            } finally {
                setRunning(false)
            }
        }
    }

    fun createEntry(targetDirectory: RemoteFileEntry, name: String, type: RemoteCreateType) {
        val serial = getSelectedReadyDevice()?.serialNumber
        if (serial == null) {
            val message = localized("auto.select_a_device_in_device_state_first.cf5bc374")
            setStatusText(message)
            appendCommand(commandError(message))
            return
        }
        if (isRunning()) return
        if (!targetDirectory.isDirectory) {
            val message = localized("auto.can_only_create_inside_a_directory.212e5ff9")
            setStatusText(message)
            appendCommand(commandError(message))
            return
        }

        val normalizedDirectory = normalizeRemotePath(targetDirectory.path)
        val childName = try {
            validateRemoteChildName(name)
        } catch (error: IllegalArgumentException) {
            setStatusText(error.message ?: localized("auto.invalid_name.106612ca"))
            appendCommand(commandError(localized("auto.create_failed.bfa5b5ba") + " - ${error.message ?: localized("auto.invalid_name.106612ca")}"))
            return
        }

        scope.launch {
            setRunning(true)
            val createTypeText = if (type == RemoteCreateType.Directory) {
                localized("auto.directory.21d3c6af")
            } else {
                localized("auto.file.a2fa9a6b")
            }
            setStatusText(localized("auto.creating_0_1.fa0eda03", createTypeText, childName))
            appendCommand(commandStatus(localized("auto.create_0_1_on_device_2_3.6272ec22", createTypeText, childName, serial, normalizedDirectory)))
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
                setStatusText(localized("auto.created_0.4f6e6cfb", createdPath))
                appendCommand(commandStatus(localized("auto.created_0_and_refreshed_1.a5663f6b", createdPath, normalizedDirectory)))
            } catch (error: CancellationException) {
                val message = localized("auto.create_stopped.c9de71d5")
                setStatusText(message)
                appendCommand(commandStatus(message))
            } catch (error: Throwable) {
                setStatusText(error.message ?: localized("auto.create_failed.bfa5b5ba"))
                appendCommand(commandError(localized("auto.create_failed.bfa5b5ba") + " - ${error.message ?: unknownError()}"))
            } finally {
                setRunning(false)
            }
        }
    }

    fun copyEntryPath(path: String) {
        setStatusText(localized("auto.copied_path_0.74f15e26", path))
        appendCommand(commandStatus(localized("auto.copied_absolute_path_0_to_clipboard.4c46f4f6", path)))
    }

    fun uploadDroppedFiles(filePaths: List<String>, targetDirectoryPath: String) {
        if (filePaths.isEmpty()) return
        if (isRunning()) {
            val message = localized("auto.a_task_is_already_running_cannot_upload.b387f70f")
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
        setStatusText(localized("auto.stopping_upload.cf34f350"))
        appendCommand(commandStatus(localized("auto.request_upload_stop.7149ded6")))
        uploadJob?.cancel()
    }

    fun updateDragOver(value: Boolean, targetDirectoryPath: String?) {
        isDragOver = value
        dragTargetPath = if (value) targetDirectoryPath?.let(::normalizeRemotePath) else null
    }

    fun handleUnsupportedDrop() {
        val message = localized("auto.drop_on_a_directory_row_or_a_file_row_inside_a_direc.85afc052")
        setStatusText(message)
        appendCommand(commandStatus(localized("auto.drop_did_not_hit_an_uploadable_directory.bbaa7de4")))
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
            val message = localized("auto.select_a_device_in_device_state_first.cf5bc374")
            setStatusText(message)
            appendCommand(commandError(message))
            return
        }
        val normalizedTarget = normalizeRemotePath(targetDirectoryPath)
        try {
            setStatusText(localized("auto.uploading_0_files_to_1.f8d73625", filePaths.size, normalizedTarget))
            appendCommand(commandStatus(localized("auto.upload_0_local_files_to_device_1_2.dbf7bb35", filePaths.size, serial, normalizedTarget)))
            val count = fileManagerAdb.uploadFiles(
                deviceSerial = serial,
                localFilePaths = filePaths,
                remoteDirectoryPath = normalizedTarget,
                logCommand = appendCommand,
                onProgress = { progress ->
                    uploadProgress = progress
                    setStatusText(
                        localized("auto.upload_0_1_2_3.fb4a7a3d", progress.currentFileIndex, progress.totalFiles, progress.currentFileName, formatFileManagerProgressPercent(progress.ratio)),
                    )
                },
            )
            val children = fileManagerAdb.listDirectory(serial, normalizedTarget, appendCommand)
            childrenByPath = childrenByPath + (normalizedTarget to children)
            expandedPaths = expandedPaths + normalizedTarget
            currentPath = normalizedTarget
            selectedEntryPath = normalizedTarget
            loadedSerial = serial
            setStatusText(localized("auto.uploaded_0_files_to_1.3f10cd8f", count, normalizedTarget))
            appendCommand(commandStatus(localized("auto.uploaded_0_files_to_1_and_refreshed_directory.408d6069", count, normalizedTarget)))
        } catch (error: CancellationException) {
            setStatusText(localized("auto.upload_stopped_refreshing_directory.0b16a20c"))
            appendCommand(commandStatus(localized("auto.upload_stopped_refreshing_0.3c022488", normalizedTarget)))
            try {
                val children = withContext(NonCancellable) {
                    fileManagerAdb.listDirectory(serial, normalizedTarget, appendCommand)
                }
                childrenByPath = childrenByPath + (normalizedTarget to children)
                expandedPaths = expandedPaths + normalizedTarget
                currentPath = normalizedTarget
                selectedEntryPath = normalizedTarget
                loadedSerial = serial
                setStatusText(localized("auto.upload_stopped_refreshed_0.1decb8d1", normalizedTarget))
                appendCommand(commandStatus(localized("auto.upload_stopped_refreshed_0.1decb8d1", normalizedTarget)))
            } catch (refreshError: Throwable) {
                setStatusText(localized("auto.upload_stopped_refresh_failed_0.f7db5742", refreshError.message ?: unknownError()))
                appendCommand(commandError(localized("auto.refresh_failed_after_upload_stopped.bf81aae9") + " - ${refreshError.message ?: unknownError()}"))
            }
        } catch (error: Throwable) {
            setStatusText(error.message ?: localized("auto.upload_failed.16f782d9"))
            appendCommand(commandError(localized("auto.upload_failed.16f782d9") + " - ${error.message ?: unknownError()}"))
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
