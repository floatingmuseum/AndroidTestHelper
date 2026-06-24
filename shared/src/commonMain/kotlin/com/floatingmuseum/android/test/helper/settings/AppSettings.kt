package com.floatingmuseum.android.test.helper.settings

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val showCommandTime: Boolean = false,
    val showCommandDuration: Boolean = false
)
