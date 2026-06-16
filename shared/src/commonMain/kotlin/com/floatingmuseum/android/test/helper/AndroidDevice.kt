package com.floatingmuseum.android.test.helper

data class AndroidDevice(
    val serialNumber: String,
    val model: String,
    val state: String,
) {
    val isReady: Boolean
        get() = state == "device"
}
