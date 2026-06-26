package com.floatingmuseum.android.test.helper.devicelog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.floatingmuseum.android.test.helper.AndroidDevice
import com.floatingmuseum.android.test.helper.localization.localized
import com.floatingmuseum.android.test.helper.localization.rememberAppStrings

@Composable
fun DeviceLogPanel(
    selectedDevice: AndroidDevice?,
    isRunning: Boolean,
    progress: DeviceLogCaptureProgress?,
    lastResult: DeviceLogCaptureResult?,
    onCaptureLogs: () -> Unit,
    onStopCapture: () -> Unit,
    onRevealLogFile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = rememberAppStrings()
    Card(modifier = modifier.fillMaxWidth()) {
        if (selectedDevice == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = strings.t("auto.select_a_connected_device_in_device_state_from_the_b.cc929da4"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = strings.t("auto.log_capture.a0fa067a"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${selectedDevice.model} · ${selectedDevice.serialNumber}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onCaptureLogs,
                            enabled = !isRunning && selectedDevice.isReady,
                        ) {
                            Text(strings.t("auto.start.51e1dab3"))
                        }
                        Button(
                            onClick = onStopCapture,
                            enabled = isRunning && progress != null,
                        ) {
                            Text(strings.t("auto.stop.83cc81af"))
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = strings.t("auto.capture_scope.1580090c"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = strings.t("auto.captures_logcat_only_the_all_buffer_exports_currentl.357fe2a0"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (progress != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(
                            progress = {
                                val ratio = if (progress.totalSections <= 0) {
                                    0f
                                } else {
                                    progress.completedSections.toFloat() / progress.totalSections.toFloat()
                                }
                                ratio.coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = strings.t("auto.capturing_0_1_2.c62f8557", progress.currentSection, progress.completedSections, progress.totalSections),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (lastResult != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = strings.t("auto.latest_log.6bcc12ab"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = lastResult.summaryText(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            SelectionContainer {
                                Text(
                                    text = lastResult.filePath,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onRevealLogFile(lastResult.filePath) },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    textDecoration = TextDecoration.Underline,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = strings.t("auto.after_capture_completes_the_local_path_appears_here_.2ffa3a53"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun DeviceLogCaptureResult.summaryText(): String {
    val stateText = when (endState) {
        DeviceLogCaptureEndState.COMPLETED -> localized("auto.completed.384c5f3a")
        DeviceLogCaptureEndState.STOPPED -> localized("auto.stopped.abb59f88")
        DeviceLogCaptureEndState.INTERRUPTED -> localized("auto.interrupted.0c146a61")
    }
    val sectionText = localized("auto.0_1_capture_sections.4231c6d8", completedSections, totalSections)
    return message
        ?.takeIf { it.isNotBlank() }
        ?.let { "$stateText · $sectionText · $it" }
        ?: "$stateText · $sectionText"
}
