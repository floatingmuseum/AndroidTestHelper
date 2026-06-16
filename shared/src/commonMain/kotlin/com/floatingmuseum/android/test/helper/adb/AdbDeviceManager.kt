package com.floatingmuseum.android.test.helper.adb

import com.floatingmuseum.android.test.helper.AndroidDevice

interface AdbDeviceManager {
    suspend fun listDevices(logCommand: (String) -> Unit): List<AndroidDevice>
}

expect fun createAdbDeviceManager(): AdbDeviceManager
