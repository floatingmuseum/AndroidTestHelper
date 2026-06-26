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
                    text = strings.t("auto.device_storage.608239d7"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Button(onClick = onRefresh, enabled = !isRunning) {
                    Text(strings.t("auto.refresh.d44e61bb"))
                }
            }

            if (storageInfo == null) {
                Text(strings.t("auto.not_loaded_connect_a_tablet_and_refresh.353a74d4"))
            } else {
                LinearProgressIndicator(
                    progress = { storageInfo.usedRatio.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    StorageMetric(strings.t("auto.total.8cfcf59b"), formatBytes(storageInfo.totalBytes), Modifier.weight(1f))
                    StorageMetric(strings.t("auto.used.2a4775c2"), formatBytes(storageInfo.usedBytes), Modifier.weight(1f))
                    StorageMetric(strings.t("auto.available.3cb935e4"), formatBytes(storageInfo.availableBytes), Modifier.weight(1f))
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
                text = strings.t("auto.fill_tasks.9903610f"),
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
                        Text(strings.t("auto.stop.83cc81af"))
                    }
                }
                Text(
                    text = strings.t("auto.progress_0_written_1_2.63171655", formatPercent(fillProgress.ratio), formatBytes(fillProgress.completedBytes), formatBytes(fillProgress.totalBytes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { onFillFixed(1L * BytesInGiB) }, enabled = !isRunning && hasReadyDevice) {
                    Text(strings.t("auto.fill_1g.b1a44fab"))
                }
                Button(onClick = { onFillFixed(5L * BytesInGiB) }, enabled = !isRunning && hasReadyDevice) {
                    Text(strings.t("auto.fill_5g.dbdf944a"))
                }
                Button(onClick = { onFillFixed(10L * BytesInGiB) }, enabled = !isRunning && hasReadyDevice) {
                    Text(strings.t("auto.fill_10g.fa97672f"))
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
                    label = { Text(strings.t("auto.custom_fill_size_gb.aa9491c1")) },
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onFillCustom, enabled = !isRunning && hasReadyDevice) {
                    Text(strings.t("auto.start.127ecd79"))
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
                    label = { Text(strings.t("auto.target_remaining_gb.a202365c")) },
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onFillUntilRemaining, enabled = !isRunning && hasReadyDevice) {
                    Text(strings.t("auto.fill_to_target.3f194e69"))
                }
            }
        }
    }
}

private fun formatPercent(value: Float): String {
    val percent = (value.coerceIn(0f, 1f) * 100).toInt()
    return "$percent%"
}
