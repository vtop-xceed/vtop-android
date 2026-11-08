package dev.vtoppp.vtop.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.vtoppp.vtop.ui.theme.Motion

// ponytail: one in-flow syncing bar for every page; never an overlay — titles never scrolled under
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SyncingBar(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = Motion.BannerEnter,
        exit = Motion.BannerExit,
        modifier = modifier.fillMaxWidth(),
    ) {
        LinearWavyProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
}
