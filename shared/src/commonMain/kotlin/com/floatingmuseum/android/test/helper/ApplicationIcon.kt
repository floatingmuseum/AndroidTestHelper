package com.floatingmuseum.android.test.helper

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun ApplicationIcon(
    iconBytes: ByteArray?,
    packageName: String,
    modifier: Modifier,
)
