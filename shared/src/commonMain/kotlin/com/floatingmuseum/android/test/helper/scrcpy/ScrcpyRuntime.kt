package com.floatingmuseum.android.test.helper.scrcpy

data class ScrcpyRuntimeInfo(
    val path: String,
    val version: String?,
    val isCustom: Boolean,
    val errorMessage: String?,
)

data class ScrcpyExecutableCheckResult(
    val isValid: Boolean,
    val normalizedPath: String?,
    val version: String?,
    val errorMessage: String?,
)

expect suspend fun loadScrcpyRuntimeInfo(): ScrcpyRuntimeInfo

expect suspend fun checkScrcpyExecutable(path: String): ScrcpyExecutableCheckResult
