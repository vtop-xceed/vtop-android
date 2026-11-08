package dev.vtoppp.vtop.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import dev.vtoppp.vtop.ui.theme.Motion

// ponytail: one pulsing dot for every status row; graphicsLayer keeps the pulse off the composition layer
@Composable
internal fun PulseDot(
    pulse: Boolean,
    color: androidx.compose.ui.graphics.Color,
    size: Dp = 8.dp,
) {
    if (!pulse) {
        Box(Modifier.size(size).clip(CircleShape).background(color))
        return
    }
    val transition = rememberInfiniteTransition(label = "pulseDot")
    val alpha by transition.animateFloat(
        initialValue = Motion.PulseMinAlpha,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(Motion.PulseMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseDotAlpha",
    )
    Box(Modifier.size(size).graphicsLayer { this.alpha = alpha }.clip(CircleShape).background(color))
}
