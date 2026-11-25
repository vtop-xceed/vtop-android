package dev.vtoppp.vtop.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.vtoppp.vtop.data.ClassScheduleItem
import dev.vtoppp.vtop.data.DaySchedule
import dev.vtoppp.vtop.ui.theme.AppDimens
import dev.vtoppp.vtop.ui.theme.LocalBlurEnabled
import kotlin.math.absoluteValue

/**
 * 2D Carousel schedule:
 * - Left/Right swipe changes Day with layered card stack preview.
 * - Active card overshadows both left and right cards in background (zIndex + elevation + scale).
 * - Shows at least 3 sub-cards in view vertically inside the active day card, scrolling cleanly if more.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DayCarouselSchedule(
    days: List<DaySchedule>,
    pagerState: PagerState,
) {
    HorizontalPager(
        state = pagerState,
        contentPadding = PaddingValues(horizontal = AppDimens.ScreenHorizontal),
        beyondViewportPageCount = 1,
        pageSpacing = (-6).dp,
        flingBehavior = PagerDefaults.flingBehavior(
            pagerState,
            snapAnimationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(352.dp),
    ) { pageIndex ->
        val day = days[pageIndex]

        // Calculate offset for card deck layering
        val pageOffset = ((pagerState.currentPage - pageIndex) +
            pagerState.currentPageOffsetFraction).absoluteValue

        val scale by animateFloatAsState(
            targetValue = (1f - (pageOffset * 0.08f)).coerceIn(0.92f, 1f),
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            label = "cardScale",
        )
        val alpha by animateFloatAsState(
            targetValue = (1f - (pageOffset * 0.60f)).coerceIn(0.40f, 1f), // Dull background cards
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            label = "cardAlpha",
        )
        val blurRadius = (8.dp * pageOffset).coerceIn(0.dp, 8.dp) // Blurry background cards
        val pivotX = when {
            pageIndex < pagerState.currentPage -> 0.95f
            pageIndex > pagerState.currentPage -> 0.05f
            else -> 0.5f
        }

        Card(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f - pageOffset) // Center card overshadows both left and right cards
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                    transformOrigin = TransformOrigin(pivotX, 0.5f)
                }
                .then(if (blurRadius > 0.dp && LocalBlurEnabled.current) Modifier.blur(radius = blurRadius) else Modifier), // Blur on background cards only
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = null, // Transparent outermost day border per user request
        ) {
            val listState = rememberLazyListState()

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(AppDimens.CompactGap),
            ) {
                items(day.classes) { classItem ->
                    ClassSubCard(
                        item = classItem,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Days with 2 classes or less: fill empty space with doodle & "aha, freedom!"
                if (day.classes.size <= 2) {
                    item(key = "freedom") {
                        FreedomDoodleCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (day.classes.isEmpty()) {
                                        Modifier.fillParentMaxHeight()
                                    } else {
                                        Modifier.height(110.dp)
                                    }
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun FreedomDoodleCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "aha, freedom!",
            style = MaterialTheme.typography.titleMedium,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "no more classes today · go touch grass",
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Final class sub-card:
 * - Status indicated by border: running -> red (error), closest -> blue (primary)
 * - Line 1: [time start] - time end (no slot word)
 * - Line 2: CourseCode  ( a11 + b11 ) where active slot is highlighted
 * - Line 3: Course Title
 * - Right hand full side: Location container with block on top, room number next line
 */


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ClassSubCard(
    item: ClassScheduleItem,
    modifier: Modifier = Modifier,
) {
    val isHighlighted = item.isOngoing || item.isNext
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.Dp>()
    val borderColor by animateColorAsState(
        targetValue = when {
            item.isOngoing -> MaterialTheme.colorScheme.primary
            item.isNext -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        },
        animationSpec = effects,
        label = "cardBorder",
    )
    val borderWidth by animateDpAsState(
        targetValue = when {
            item.isOngoing -> 2.dp
            item.isNext -> 1.5.dp
            else -> 1.dp
        },
        animationSpec = spatial,
        label = "cardBorderWidth",
    )
    val cardBorder = BorderStroke(borderWidth, borderColor)
    val accent by animateColorAsState(
        targetValue = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        animationSpec = effects,
        label = "accent",
    )
    val accentContainer by animateColorAsState(
        targetValue = when {
            item.isOngoing -> MaterialTheme.colorScheme.primary
            item.isNext -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = effects,
        label = "accentContainer",
    )
    val onAccentContainer by animateColorAsState(
        targetValue = when {
            item.isOngoing -> MaterialTheme.colorScheme.onPrimary
            item.isNext -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = effects,
        label = "onAccentContainer",
    )
    val shadowElevation by animateDpAsState(
        targetValue = when {
            item.isOngoing -> 4.dp
            item.isNext -> 2.dp
            else -> 0.dp
        },
        animationSpec = spatial,
        label = "cardShadow",
    )

    Surface(
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = cardBorder,
        modifier = modifier,
        shadowElevation = shadowElevation,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.CompactPadding),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.ItemGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: start/end times, stacked
            Column(
                modifier = Modifier.widthIn(min = 58.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (item.isNext) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(bottom = 1.dp),
                    ) {
                        Text(
                            text = "NEXT",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = accentContainer,
                ) {
                    Text(
                        text = item.startTime,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = onAccentContainer,
                        // one line, always: a time that wraps reads as two times
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = AppDimens.CompactGap, vertical = 2.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), CircleShape),
                )
                Text(
                    text = item.endTime,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }

            // Vertical divider between times and info
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(48.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            )

            // Middle: course code + slots, then title
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.courseCode,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = accent,
                    letterSpacing = 0.5.sp,
                )

                if (item.slots.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        item.slots.forEachIndexed { index, slotItem ->
                            val isSelected = slotItem.equals(item.activeSlot, ignoreCase = true)
                            if (isSelected) {
                                Surface(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = accentContainer,
                                ) {
                                    Text(
                                        text = slotItem,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        fontWeight = FontWeight.Black,
                                        color = onAccentContainer,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.dp),
                                    )
                                }
                            } else {
                                Text(
                                    text = slotItem,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                )
                            }
                            if (index < item.slots.size - 1) {
                                Text(
                                    text = "+",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                )
                            }
                        }
                    }
                }

                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Right: class location, block on top of room number
            Column(
                modifier = Modifier.width(72.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.roomBlock,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.roomNumber,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
