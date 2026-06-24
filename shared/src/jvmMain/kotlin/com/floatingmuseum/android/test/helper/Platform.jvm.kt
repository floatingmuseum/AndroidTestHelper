package com.floatingmuseum.android.test.helper

class JVMPlatform : Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
}

actual fun getPlatform(): Platform = JVMPlatform()

actual fun getCurrentTimeFormatted(): String {
    val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
    return java.time.LocalTime.now().format(formatter)
}