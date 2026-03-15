package com.simtop.presentation_utils.core

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun shimmerBrush(showShimmer: Boolean = true, targetValue: Float = SHIMMER_TARGET_VALUE): Brush {
    return if (showShimmer) {
        val shimmerColors = listOf(
            Color.LightGray.copy(alpha = SHIMMER_ALPHA_HIGH),
            Color.LightGray.copy(alpha = SHIMMER_ALPHA_LOW),
            Color.LightGray.copy(alpha = SHIMMER_ALPHA_HIGH),
        )

        val transition = rememberInfiniteTransition(label = "shimmer")
        val translateAnimation = transition.animateFloat(
            initialValue = 0f,
            targetValue = targetValue,
            animationSpec = infiniteRepeatable(
                animation = tween(SHIMMER_DURATION, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ), label = "shimmer"
        )
        Brush.linearGradient(
            colors = shimmerColors,
            start = Offset.Zero,
            end = Offset(x = translateAnimation.value, y = translateAnimation.value)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.Transparent),
            start = Offset.Zero,
            end = Offset.Zero
        )
    }
}

private const val SHIMMER_TARGET_VALUE = 1000f
private const val SHIMMER_ALPHA_HIGH = 0.6f
private const val SHIMMER_ALPHA_LOW = 0.2f
private const val SHIMMER_DURATION = 800
