package com.floatingmuseum.android.test.helper

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        state = rememberWindowState(width = 1280.dp, height = 860.dp),
        title = "AndroidTestHelper",
    ) {
        App()
    }
}
