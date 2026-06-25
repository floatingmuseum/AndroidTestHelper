package com.floatingmuseum.android.test.helper.filemanager

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun Modifier.localFileDropTarget(
    remotePath: String,
    onHover: (String?) -> Unit,
    onFilesDropped: (List<String>, String) -> Unit,
    onUnsupportedDrop: () -> Unit,
): Modifier
