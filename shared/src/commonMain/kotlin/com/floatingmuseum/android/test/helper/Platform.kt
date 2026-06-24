package com.floatingmuseum.android.test.helper

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

/**
 * 获取当前时间的格式化字符串，例如 "23:34:34"
 */
expect fun getCurrentTimeFormatted(): String