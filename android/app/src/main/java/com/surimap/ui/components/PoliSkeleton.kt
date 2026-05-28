package com.surimap.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.surimap.ui.theme.PoliBgInput
import com.surimap.ui.theme.PoliBorder

@Composable
fun rememberPoliShimmerBrush(label: String = "poli-skeleton"): Brush {
    val shimmerOffset =
        rememberInfiniteTransition(label = label)
            .animateFloat(
                initialValue = -320f,
                targetValue = 960f,
                animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1_100),
                    repeatMode = RepeatMode.Restart
                ),
                label = "$label-offset"
            ).value

    return Brush.linearGradient(
        colors = listOf(PoliBgInput, PoliBorder.copy(alpha = 0.62f), PoliBgInput),
        start = Offset(shimmerOffset, 0f),
        end = Offset(shimmerOffset + 320f, 0f)
    )
}

@Composable
fun PoliSkeletonCard(
    title: String,
    lineCount: Int,
    shimmerBrush: Brush,
    modifier: Modifier = Modifier,
    strong: Boolean = false
) {
    PoliCard(modifier = modifier, strong = strong) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        repeat(lineCount) { index ->
            PoliSkeletonLine(
                shimmerBrush = shimmerBrush,
                widthFraction = if (index == lineCount - 1) 0.68f else 1f,
                minHeight = if (index == 0) 44.dp else 28.dp
            )
        }
    }
}

@Composable
fun PoliSkeletonLine(
    shimmerBrush: Brush,
    modifier: Modifier = Modifier,
    widthFraction: Float = 1f,
    minHeight: Dp = 28.dp
) {
    Box(
        modifier =
        modifier
            .fillMaxWidth(widthFraction.coerceIn(0.1f, 1f))
            .heightIn(min = minHeight)
            .clip(MaterialTheme.shapes.medium)
            .background(shimmerBrush)
    )
}
