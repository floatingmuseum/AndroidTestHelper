package com.floatingmuseum.android.test.helper.filemanager

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import java.io.File
import java.net.URI

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun Modifier.localFileDropTarget(
    remotePath: String,
    onHover: (String?) -> Unit,
    onFilesDropped: (List<String>, String) -> Unit,
    onUnsupportedDrop: () -> Unit,
): Modifier {
    val currentRemotePath = rememberUpdatedState(remotePath)
    val currentHover = rememberUpdatedState(onHover)
    val currentFilesDropped = rememberUpdatedState(onFilesDropped)
    val currentUnsupportedDrop = rememberUpdatedState(onUnsupportedDrop)
    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                currentHover.value(currentRemotePath.value)
            }

            override fun onMoved(event: DragAndDropEvent) {
                currentHover.value(currentRemotePath.value)
            }

            override fun onExited(event: DragAndDropEvent) {
                currentHover.value(null)
            }

            override fun onEnded(event: DragAndDropEvent) {
                currentHover.value(null)
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                val files = event.localFilePaths()
                currentHover.value(null)
                return if (files.isNotEmpty()) {
                    currentFilesDropped.value(files, currentRemotePath.value)
                    true
                } else {
                    currentUnsupportedDrop.value()
                    false
                }
            }
        }
    }
    return dragAndDropTarget(
        shouldStartDragAndDrop = { event -> event.localFilePaths().isNotEmpty() },
        target = target,
    )
}

@OptIn(ExperimentalComposeUiApi::class)
private fun DragAndDropEvent.localFilePaths(): List<String> {
    return try {
        when (val data = dragData()) {
            is DragData.FilesList -> parseLocalFileReferences(data.readFiles())
            is DragData.Text -> parseLocalFileReferences(listOf(data.readText()))
            else -> emptyList()
        }
    } catch (error: Throwable) {
        emptyList()
    }
}

internal fun parseLocalFileReferences(values: List<String>): List<String> {
    return values
        .asSequence()
        .flatMap { it.lineSequence() }
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .mapNotNull { value ->
            runCatching {
                when {
                    value.startsWith("file:", ignoreCase = true) -> File(URI(value)).absoluteFile
                    else -> File(value.removeSurrounding("\"")).absoluteFile
                }
            }.getOrNull()
        }
        .filter { it.exists() }
        .map { it.absolutePath }
        .distinct()
        .toList()
}
