package com.floatingmuseum.android.test.helper

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() {
    val appInfo = loadAppInfo()

    application {
        Window(
            onCloseRequest = ::exitApplication,
            state = rememberWindowState(width = 1280.dp, height = 860.dp),
            title = appInfo.appName,
            icon = painterResource("icons/android-test-helper.png"),
        ) {
            App()
        }
    }
}
