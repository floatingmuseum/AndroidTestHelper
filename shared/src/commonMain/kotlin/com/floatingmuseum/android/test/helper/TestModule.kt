package com.floatingmuseum.android.test.helper

import com.floatingmuseum.android.test.helper.localization.AppStrings

internal enum class TestModule {
    Device,
    App,
    DataFill,
    FileManager,
    Log,
    Settings,
}

internal fun TestModule.title(strings: AppStrings): String = when (this) {
    TestModule.Device -> strings.t("auto.device.fb0336cd")
    TestModule.App -> strings.t("auto.apps.dfc620ce")
    TestModule.DataFill -> strings.t("auto.data_fill.c232aadd")
    TestModule.FileManager -> strings.t("auto.files.220e07f7")
    TestModule.Log -> strings.t("auto.logs.ff42c1f9")
    TestModule.Settings -> strings.t("auto.settings.3c65c5f8")
}
