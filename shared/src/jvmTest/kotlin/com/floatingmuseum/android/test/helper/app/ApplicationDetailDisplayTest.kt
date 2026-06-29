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
    fun keepsUnknownPluginDetailContentUntouched() = withLanguage(AppLanguage.English) {
        val item = ApplicationDetailItem("自定义标签", "真实中文内容")
            .toDisplayDetailItem(ApplicationDetailSection.BASIC)

        assertEquals("自定义标签", item.title)
        assertEquals("真实中文内容", item.body)
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
