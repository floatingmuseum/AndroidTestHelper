package com.floatingmuseum.android.test.helper.localization

internal actual fun loadLocalizationResource(languageTag: String): String? {
    val path = "locales/$languageTag.json"
    val classLoader = Thread.currentThread().contextClassLoader
        ?: object {}.javaClass.classLoader
    return classLoader
        ?.getResourceAsStream(path)
        ?.bufferedReader(Charsets.UTF_8)
        ?.use { it.readText() }
}
