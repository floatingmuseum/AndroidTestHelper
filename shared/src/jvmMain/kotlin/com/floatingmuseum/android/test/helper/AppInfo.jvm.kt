package com.floatingmuseum.android.test.helper

import java.util.Properties

actual fun loadAppInfo(): AppInfo {
    val properties = Properties()
    val resourceStream = Thread.currentThread()
        .contextClassLoader
        ?.getResourceAsStream("app-info.properties")
        ?: AppInfo::class.java.classLoader.getResourceAsStream("app-info.properties")

    resourceStream?.use(properties::load)

    return appInfoFromProperties(properties)
}

internal fun appInfoFromProperties(properties: Properties): AppInfo = AppInfo(
    appName = properties.requiredAppInfo("appName"),
    versionName = properties.requiredAppInfo("versionName"),
    author = properties.requiredAppInfo("author"),
    vendor = properties.requiredAppInfo("vendor"),
    description = properties.requiredAppInfo("description"),
    windowsUpgradeUuid = properties.requiredAppInfo("windowsUpgradeUuid"),
    macosBundleID = properties.requiredAppInfo("macosBundleID"),
    linuxPackageID = properties.requiredAppInfo("linuxPackageID"),
)

private fun Properties.requiredAppInfo(key: String): String =
    getProperty(key)?.trim()?.takeIf { it.isNotBlank() } ?: "unknown"
