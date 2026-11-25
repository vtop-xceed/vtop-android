package dev.vtoppp.vtop.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.vtoppp.vtop.R
import dev.vtoppp.vtop.ui.theme.Motion
import dev.vtoppp.vtop.ui.theme.StatusColors

/**
 * Bridge & GitHub pill icon. Busy: the pulsing 6dp dot (same colors as the old pill dot).
 * Ready: the check glyph — the dot fades out while `ic_check` fades in and springs into scale.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MorphingStatusIcon(
    isReady: Boolean,
    isError: Boolean,
    contentColor: Color,
    readyLabel: String? = null,
    readyIcon: Int = R.drawable.ic_check,
    modifier: Modifier = Modifier,
) {
    val pulse = rememberInfiniteTransition(label = "morphPulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = Motion.PulseMinAlpha,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(Motion.PulseMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "morphPulseAlpha",
    )
    val dotAlpha by animateFloatAsState(
        targetValue = if (isReady) 0f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "morphDotAlpha",
    )
    val checkAlpha by animateFloatAsState(
        targetValue = if (isReady) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "morphCheckAlpha",
    )
    val checkScale by animateFloatAsState(
        targetValue = if (isReady) 1f else 0.85f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "morphCheckScale",
    )

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .graphicsLayer { alpha = pulseAlpha * dotAlpha }
                .clip(CircleShape)
                .background(if (isError) MaterialTheme.colorScheme.error else StatusColors.Warning),
        )
        Icon(
            painter = painterResource(readyIcon),
            contentDescription = if (isReady) readyLabel else null,
            tint = contentColor,
            modifier = Modifier
                .size(10.dp)
                .graphicsLayer {
                    alpha = checkAlpha
                    scaleX = checkScale
                    scaleY = checkScale
                },
        )
    }
}
