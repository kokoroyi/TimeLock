package com.timelock.app.ui.common

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 应用图标。
 *
 * 认应用靠图标比读名字快得多——名字会被翻译得千奇百怪，图标不会。
 * 两处列表（选择应用、首页受管列表）共用同一个实现，免得各写一份。
 *
 * 取不到图标时退化成首字占位，不留空洞。
 */
@Composable
fun AppIcon(
    bitmap: Bitmap?,
    fallbackText: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    if (bitmap != null) {
        val image = remember(bitmap) { bitmap.asImageBitmap() }
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = modifier.size(size),
        )
    } else {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = modifier.size(size),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = fallbackText.take(1),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
