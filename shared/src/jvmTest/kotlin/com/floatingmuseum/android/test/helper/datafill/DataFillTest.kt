package com.floatingmuseum.android.test.helper.datafill

import kotlin.test.Test
import kotlin.test.assertEquals

class DataFillTest {
    @Test
    fun parsesAndroidDfOutput() {
        val storageInfo = parseDfStorageInfo(
            """
            Filesystem       1K-blocks     Used Available Use% Mounted on
            /dev/block/dm-53 110077224 35155936  74842480  32% /data
            """.trimIndent(),
        )

        assertEquals(110077224L * 1024L, storageInfo.totalBytes)
        assertEquals(35155936L * 1024L, storageInfo.usedBytes)
        assertEquals(74842480L * 1024L, storageInfo.availableBytes)
    }
}
