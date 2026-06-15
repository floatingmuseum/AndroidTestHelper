package com.floatingmuseum.android.test.helper

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.Image as SkiaImage

@Composable
actual fun ApplicationIcon(
    iconBytes: ByteArray?,
    packageName: String,
    modifier: Modifier,
) {
    val bitmap = remember(iconBytes) {
        iconBytes?.decodeImageBitmapOrNull()
    }

    if (bitmap == null) {
        Box(
            modifier = modifier
                .size(42.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = packageName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        Image(
            bitmap = bitmap,
            contentDescription = "$packageName 图标",
            modifier = modifier
                .size(42.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit,
        )
    }
}

private fun ByteArray.decodeImageBitmapOrNull(): ImageBitmap? {
    return try {
        SkiaImage.makeFromEncoded(this).toComposeImageBitmap()
    } catch (_: Throwable) {
        null
    }
}
