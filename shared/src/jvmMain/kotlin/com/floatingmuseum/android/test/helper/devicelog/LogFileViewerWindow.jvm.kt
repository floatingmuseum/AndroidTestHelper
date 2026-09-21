package com.floatingmuseum.android.test.helper.devicelog

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.rememberWindowState
import com.floatingmuseum.android.test.helper.localization.rememberAppStrings
import java.io.File

@Composable
internal actual fun LogFileViewerWindow(controller: LogFileViewerController) {
    if (!controller.isWindowOpen) return
    val strings = rememberAppStrings()
    val state = rememberWindowState(placement = WindowPlacement.Maximized, width = 1440.dp, height = 900.dp)
    val fileName = controller.filePath?.let { File(it).name }
    Window(
        onCloseRequest = controller::closeWindow,
        state = state,
        title = listOfNotNull(strings.t("log.viewer.title"), fileName).joinToString(" · "),
    ) {
        LaunchedEffect(controller.windowRequestId) {
            state.isMinimized = false
            window.toFront()
            window.requestFocus()
        }
        Surface(Modifier.fillMaxSize()) {
            LogFileViewerPanel(controller)
        }
    }
}
