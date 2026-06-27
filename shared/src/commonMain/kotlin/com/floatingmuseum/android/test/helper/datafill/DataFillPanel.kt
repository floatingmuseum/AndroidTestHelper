package com.floatingmuseum.android.test.helper.datafill

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.floatingmuseum.android.test.helper.localization.rememberAppStrings

@Composable
fun StoragePanel(
    storageInfo: StorageInfo?,
    isRunning: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = rememberAppStrings()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = strings.t("data_fill.device_storage"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Button(onClick = onRefresh, enabled = !isRunning) {
                    Text(strings.t("common.action.refresh"))
                }
            }

            if (storageInfo == null) {
                Text(strings.t("data_fill.not_loaded_connect_a_tablet_and_refresh"))
            } else {
                LinearProgressIndicator(
                    progress = { storageInfo.usedRatio.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    StorageMetric(strings.t("data_fill.total"), formatBytes(storageInfo.totalBytes), Modifier.weight(1f))
                    StorageMetric(strings.t("data_fill.used"), formatBytes(storageInfo.usedBytes), Modifier.weight(1f))
                    StorageMetric(strings.t("data_fill.available"), formatBytes(storageInfo.availableBytes), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StorageMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun FillControls(
    customFillValue: String,
    onCustomFillValueChange: (String) -> Unit,
    remainingValue: String,
    onRemainingValueChange: (String) -> Unit,
    isRunning: Boolean,
    hasReadyDevice: Boolean,
    fillProgress: FillProgress?,
    onStopFill: () -> Unit,
    onFillFixed: (Long) -> Unit,
    onFillCustom: () -> Unit,
    onFillUntilRemaining: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = rememberAppStrings()
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = strings.t("data_fill.fill_tasks"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            if (fillProgress != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LinearProgressIndicator(
                        progress = { fillProgress.ratio.coerceIn(0f, 1f) },
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = onStopFill,
                        enabled = isRunning,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Text(strings.t("common.action.stop"))
                    }
                }
                Text(
                    text = strings.t("data_fill.progress_arg0_written_arg1_arg2", formatPercent(fillProgress.ratio), formatBytes(fillProgress.completedBytes), formatBytes(fillProgress.totalBytes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { onFillFixed(1L * BytesInGiB) }, enabled = !isRunning && hasReadyDevice) {
                    Text(strings.t("data_fill.fill_1g"))
                }
                Button(onClick = { onFillFixed(5L * BytesInGiB) }, enabled = !isRunning && hasReadyDevice) {
                    Text(strings.t("data_fill.fill_5g"))
                }
                Button(onClick = { onFillFixed(10L * BytesInGiB) }, enabled = !isRunning && hasReadyDevice) {
                    Text(strings.t("data_fill.fill_10g"))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = customFillValue,
                    onValueChange = onCustomFillValueChange,
                    enabled = !isRunning && hasReadyDevice,
                    singleLine = true,
                    label = { Text(strings.t("data_fill.custom_fill_size_gb")) },
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onFillCustom, enabled = !isRunning && hasReadyDevice) {
                    Text(strings.t("data_fill.action.start"))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = remainingValue,
                    onValueChange = onRemainingValueChange,
                    enabled = !isRunning && hasReadyDevice,
                    singleLine = true,
                    label = { Text(strings.t("data_fill.target_remaining_gb")) },
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onFillUntilRemaining, enabled = !isRunning && hasReadyDevice) {
                    Text(strings.t("data_fill.fill_to_target"))
                }
            }
        }
    }
}

private fun formatPercent(value: Float): String {
    val percent = (value.coerceIn(0f, 1f) * 100).toInt()
    return "$percent%"
}
