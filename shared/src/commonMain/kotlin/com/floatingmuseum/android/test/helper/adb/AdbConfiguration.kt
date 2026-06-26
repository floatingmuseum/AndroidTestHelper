package com.floatingmuseum.android.test.helper.adb

data class AdbRuntimeInfo(
    val path: String,
    val version: String?,
    val isCustom: Boolean,
    val errorMessage: String?,
)

data class AdbExecutableCheckResult(
    val isValid: Boolean,
    val normalizedPath: String?,
    val version: String?,
    val errorMessage: String?,
)

expect suspend fun loadAdbRuntimeInfo(): AdbRuntimeInfo

expect suspend fun checkAdbExecutable(path: String): AdbExecutableCheckResult
