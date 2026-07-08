package com.floatingmuseum.android.test.helper

data class AppInfo(
    val versionName: String,
    val author: String,
)

expect fun loadAppInfo(): AppInfo
