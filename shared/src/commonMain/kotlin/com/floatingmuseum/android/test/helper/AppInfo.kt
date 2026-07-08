package com.floatingmuseum.android.test.helper

data class AppInfo(
    val appName: String,
    val versionName: String,
    val author: String,
    val vendor: String,
    val description: String,
    val windowsUpgradeUuid: String,
    val macosBundleID: String,
    val linuxPackageID: String,
)

expect fun loadAppInfo(): AppInfo
