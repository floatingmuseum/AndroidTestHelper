package com.floatingmuseum.android.test.helper.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.floatingmuseum.android.test.helper.localization.rememberAppStrings

@Composable
fun PluginCheckBanner(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    onIgnore: () -> Unit,
    onDismiss: () -> Unit,
    isProcessing: Boolean,
    modifier: Modifier = Modifier,
) {
    val strings = rememberAppStrings()
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "💡",
                style = MaterialTheme.typography.bodyLarge,
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )

            Button(
                onClick = onAction,
                enabled = !isProcessing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(if (isProcessing) strings.t("plugin_banner.processing") else actionLabel)
            }

            TextButton(
                onClick = onIgnore,
                enabled = !isProcessing,
            ) {
                Text(strings.t("plugin_banner.ignore"))
            }

            TextButton(
                onClick = onDismiss,
                enabled = !isProcessing,
            ) {
                Text(
                    text = "✕",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}
