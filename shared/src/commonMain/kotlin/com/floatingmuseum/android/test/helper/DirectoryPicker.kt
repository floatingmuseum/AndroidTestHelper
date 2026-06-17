package com.floatingmuseum.android.test.helper

/**
 * 弹出系统目录选择器，让用户选择一个本地文件夹路径。
 * 如果用户取消了选择，则返回 null。
 */
expect suspend fun selectDirectory(
    dialogTitle: String,
    approveButtonText: String,
): String?

/**
 * 弹出系统文件选择器，让用户一次选择一个或多个 APK 文件。
 * 如果用户取消了选择，则返回空列表。
 */
expect suspend fun selectApkFiles(
    dialogTitle: String,
    approveButtonText: String,
): List<String>
