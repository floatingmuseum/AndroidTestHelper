package com.floatingmuseum.android.test.helper.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun ApplicationIcon(
    iconBytes: ByteArray?,
    packageName: String,
    modifier: Modifier,
)
