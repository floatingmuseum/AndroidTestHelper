package com.floatingmuseum.android.test.helper

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform