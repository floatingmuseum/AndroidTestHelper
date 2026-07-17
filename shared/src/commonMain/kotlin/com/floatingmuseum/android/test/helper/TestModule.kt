package com.floatingmuseum.android.test.helper

import com.floatingmuseum.android.test.helper.localization.AppStrings

internal enum class TestModule {
    Device,
    App,
    Intent,
    Monkey,
    DataFill,
    FileManager,
    Log,
    Settings,
}

internal fun TestModule.title(strings: AppStrings): String = when (this) {
    TestModule.Device -> strings.t("module.device")
    TestModule.App -> strings.t("common.apps")
    TestModule.Intent -> strings.t("module.intent")
    TestModule.Monkey -> strings.t("module.monkey")
    TestModule.DataFill -> strings.t("module.data_fill")
    TestModule.FileManager -> strings.t("module.files")
    TestModule.Log -> strings.t("module.logs")
    TestModule.Settings -> strings.t("module.settings")
}
