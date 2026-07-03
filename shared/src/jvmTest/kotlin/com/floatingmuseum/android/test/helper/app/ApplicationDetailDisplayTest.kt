package com.floatingmuseum.android.test.helper.app

import com.floatingmuseum.android.test.helper.settings.AppLanguage
import com.floatingmuseum.android.test.helper.settings.AppSettings
import com.floatingmuseum.android.test.helper.settings.AppSettingsShared
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationDetailDisplayTest {
    @Test
    fun dropsLocalizedNoInformationPlaceholderItems() = withLanguage(AppLanguage.English) {
        val content = ApplicationDetailContent(
            section = ApplicationDetailSection.ACTIVITIES,
            source = ApplicationDetailSource.ADB,
            items = listOf(ApplicationDetailItem("状态", "未解析到该分类信息")),
        )

        assertTrue(content.toDisplayDetailItems().isEmpty())
    }

    @Test
    fun normalizesLegacyPermissionLabelsForCurrentLanguage() = withLanguage(AppLanguage.English) {
        val item = ApplicationDetailItem("权限", "android.permission.CAMERA")
            .toDisplayDetailItem(ApplicationDetailSection.PERMISSIONS)

        assertEquals("android.permission.CAMERA", item.title)
        assertEquals("Declared", item.body)
    }

    @Test
    fun normalizesCachedLocalizedPermissionLabelsForCurrentLanguage() = withLanguage(AppLanguage.English) {
        val item = ApplicationDetailItem("声明权限", "android.permission.INTERNET")
            .toDisplayDetailItem(ApplicationDetailSection.PERMISSIONS)

        assertEquals("android.permission.INTERNET", item.title)
        assertEquals("Declared Permission", item.body)
    }

    @Test
    fun normalizesCachedBasicDetailLabelsForCurrentLanguage() = withLanguage(AppLanguage.English) {
        val content = ApplicationDetailContent(
            section = ApplicationDetailSection.BASIC,
            source = ApplicationDetailSource.ATH_PLUGIN,
            items = listOf(
                ApplicationDetailItem("应用名", "android开发工具箱"),
                ApplicationDetailItem("包名", "com.su.assistant.pro"),
                ApplicationDetailItem("版本名", "3.1.2"),
                ApplicationDetailItem("版本号", "171"),
                ApplicationDetailItem("应用类型", "第三方应用"),
                ApplicationDetailItem("启用状态", "已启用"),
            ),
        )

        val items = content.toDisplayDetailItems()

        assertEquals("App Name", items[0].title)
        assertEquals("android开发工具箱", items[0].body)
        assertEquals("Package Name", items[1].title)
        assertEquals("Version Name", items[2].title)
        assertEquals("Version Code", items[3].title)
        assertEquals("App Type", items[4].title)
        assertEquals("Third-party app", items[4].body)
        assertEquals("Enabled State", items[5].title)
        assertEquals("Enabled", items[5].body)
    }

    @Test
    fun exposesComponentClassNameForIntentTesting() = withLanguage(AppLanguage.English) {
        val item = ApplicationDetailItem(
            label = "com.example.app.MainActivity",
            value = "exported=true",
        ).toDisplayDetailItem(ApplicationDetailSection.ACTIVITIES)

        assertEquals("com.example.app.MainActivity", item.title)
        assertEquals("com.example.app.MainActivity", item.intentClassName)
        assertEquals("exported=true", item.body)
    }

    @Test
    fun keepsUnknownPluginDetailContentUntouched() = withLanguage(AppLanguage.English) {
        val item = ApplicationDetailItem("自定义标签", "真实中文内容")
            .toDisplayDetailItem(ApplicationDetailSection.BASIC)

        assertEquals("自定义标签", item.title)
        assertEquals("真实中文内容", item.body)
    }

    @Test
    fun findsManifestSearchMatchesIgnoringCaseAndTracksLines() {
        val manifestText = """
            <manifest package="com.example">
                <uses-permission android:name="android.permission.INTERNET" />
                <application>
                    <activity android:name=".MainActivity" />
                </application>
            </manifest>
        """.trimIndent()

        val matches = findManifestSearchMatches(manifestText, "ANDROID:NAME")

        assertEquals(2, matches.size)
        assertEquals(1, matches[0].lineIndex)
        assertEquals(3, matches[1].lineIndex)
        assertEquals(
            "android:name",
            manifestText.lines()[matches[0].lineIndex].substring(matches[0].start, matches[0].end),
        )
    }

    @Test
    fun trimsManifestSearchKeywordAndIgnoresBlankSearch() {
        val manifestText = """<manifest package="com.example" />"""

        assertEquals(1, findManifestSearchMatches(manifestText, " package ").size)
        assertEquals(emptyList(), findManifestSearchMatches(manifestText, "   "))
    }

    @Test
    fun normalizesManifestSearchMatchNavigationIndex() {
        assertEquals(0, normalizedManifestSearchMatchIndex(0, 0))
        assertEquals(0, normalizedManifestSearchMatchIndex(3, 3))
        assertEquals(2, normalizedManifestSearchMatchIndex(-1, 3))
        assertEquals(1, normalizedManifestSearchMatchIndex(4, 3))
    }

    private fun withLanguage(language: AppLanguage, block: () -> Unit) {
        val previousSettings = AppSettingsShared.currentSettings
        try {
            AppSettingsShared.updateSettings(AppSettings(language = language))
            block()
        } finally {
            AppSettingsShared.updateSettings(previousSettings)
        }
    }
}
