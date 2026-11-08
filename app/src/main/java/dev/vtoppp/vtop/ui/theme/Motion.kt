package dev.vtoppp.vtop.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween

// ponytail: one duration ladder for the whole app; callers read from here, never raw numbers
object Motion {
    const val Fast = 150
    const val Quick = 200
    const val Content = 250
    const val Color = 350
    const val Slow = 800
    const val PulseMs = 700
    const val PulseMinAlpha = 0.3f

    val BannerEnter: EnterTransition = fadeIn(tween(Quick)) + expandVertically(tween(Content))
    val BannerExit: ExitTransition = fadeOut(tween(Fast)) + shrinkVertically(tween(Fast))
}
