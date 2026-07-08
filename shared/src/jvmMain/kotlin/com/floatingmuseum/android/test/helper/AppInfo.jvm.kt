package com.floatingmuseum.android.test.helper

import java.util.Properties

actual fun loadAppInfo(): AppInfo {
    val properties = Properties()
    val resourceStream = Thread.currentThread()
        .contextClassLoader
        ?.getResourceAsStream("app-info.properties")
        ?: AppInfo::class.java.classLoader.getResourceAsStream("app-info.properties")

    resourceStream?.use(properties::load)

    return AppInfo(
        versionName = properties.getProperty("versionName")?.takeIf { it.isNotBlank() } ?: "unknown",
        author = properties.getProperty("author")?.takeIf { it.isNotBlank() } ?: "unknown",
    )
}
