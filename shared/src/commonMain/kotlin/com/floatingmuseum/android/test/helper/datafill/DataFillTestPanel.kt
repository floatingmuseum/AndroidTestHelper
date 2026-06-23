package com.floatingmuseum.android.test.helper.datafill

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DataFillTestPanel(
    storageInfo: StorageInfo?,
    customFillValue: String,
    onCustomFillValueChange: (String) -> Unit,
    remainingValue: String,
    onRemainingValueChange: (String) -> Unit,
    isRunning: Boolean,
    hasReadyDevice: Boolean,
    fillProgress: FillProgress?,
    onRefresh: () -> Unit,
    onStopFill: () -> Unit,
    onFillFixed: (Long) -> Unit,
    onFillCustom: () -> Unit,
    onFillUntilRemaining: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StoragePanel(
            storageInfo = storageInfo,
            isRunning = isRunning,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxWidth(),
        )

        FillControls(
            customFillValue = customFillValue,
            onCustomFillValueChange = onCustomFillValueChange,
            remainingValue = remainingValue,
            onRemainingValueChange = onRemainingValueChange,
            isRunning = isRunning,
            hasReadyDevice = hasReadyDevice,
            fillProgress = fillProgress,
            onStopFill = onStopFill,
            onFillFixed = onFillFixed,
            onFillCustom = onFillCustom,
            onFillUntilRemaining = onFillUntilRemaining,
            modifier = Modifier.weight(1f),
        )
    }
}
