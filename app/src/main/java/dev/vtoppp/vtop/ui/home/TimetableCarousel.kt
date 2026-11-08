package dev.vtoppp.vtop.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.activity.compose.BackHandler
import androidx.compose.ui.zIndex
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import dev.vtoppp.vtop.R
import dev.vtoppp.vtop.data.AgendaRow
import dev.vtoppp.vtop.data.CalendarAgenda
import dev.vtoppp.vtop.data.CalendarDayEvent
import dev.vtoppp.vtop.data.ClassScheduleItem
import dev.vtoppp.vtop.data.DaySchedule
import dev.vtoppp.vtop.data.PillKind
import dev.vtoppp.vtop.data.PillState
import dev.vtoppp.vtop.data.UserDatabase
import dev.vtoppp.vtop.data.UserProfile
import dev.vtoppp.vtop.ui.theme.AppPalette
import dev.vtoppp.vtop.ui.theme.Motion
import dev.vtoppp.vtop.ui.theme.StatusColors
import kotlin.math.absoluteValue

/**
 * 2D Carousel schedule:
 * - Left/Right swipe changes Day with layered card stack preview.
 * - Active card overshadows both left and right cards in background (zIndex + elevation + scale).
 * - Shows at least 3 sub-cards in view vertically inside the active day card, scrolling cleanly if more.
 */
@Composable
internal fun DayCarouselSchedule(
    days: List<DaySchedule>,
    pagerState: PagerState,
) {
    HorizontalPager(
        state = pagerState,
        contentPadding = PaddingValues(horizontal = 40.dp),
        beyondViewportPageCount = 1,
        pageSpacing = (-6).dp,
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
            label = "cardScale",
        )
        val alpha by animateFloatAsState(
            targetValue = (1f - (pageOffset * 0.60f)).coerceIn(0.40f, 1f), // Dull background cards
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
                .then(if (blurRadius > 0.dp && dev.vtoppp.vtop.ui.theme.LocalBlurEnabled.current) Modifier.blur(radius = blurRadius) else Modifier), // Blur on background cards only
            shape = RoundedCornerShape(26.dp),
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
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "no more classes today · go touch grass",
            style = MaterialTheme.typography.bodySmall,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
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


@Composable
internal fun ClassSubCard(
    item: ClassScheduleItem,
    modifier: Modifier = Modifier,
) {
    val cardBorder = when {
        item.isOngoing -> androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.error)
        item.isClosest -> androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else -> androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = cardBorder,
        modifier = modifier,
        shadowElevation = if (item.isOngoing) 3.dp else 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left & Center Column: Time, Code + Slots, Title
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Line 1: [time start] - time end (no slot word)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when {
                            item.isOngoing -> MaterialTheme.colorScheme.error
                            item.isClosest -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.primaryContainer
                        },
                    ) {
                        Text(
                            text = item.startTime,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                item.isOngoing -> MaterialTheme.colorScheme.onError
                                item.isClosest -> MaterialTheme.colorScheme.onPrimary
                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                    Text(
                        text = "-",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = item.endTime,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // Line 2: sts200  ( a11 + b11 ) where active slot is highlighted
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = item.courseCode,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = when {
                            item.isOngoing -> MaterialTheme.colorScheme.error
                            item.isClosest -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.primary
                        },
                        letterSpacing = 0.5.sp,
                    )

                    if (item.slots.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                text = "(",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            item.slots.forEachIndexed { index, slotItem ->
                                val isSelected = slotItem.equals(item.activeSlot, ignoreCase = true)
                                if (isSelected) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = when {
                                            item.isOngoing -> MaterialTheme.colorScheme.errorContainer
                                            item.isClosest -> MaterialTheme.colorScheme.primaryContainer
                                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                                        },
                                    ) {
                                        Text(
                                            text = slotItem,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Black,
                                            color = when {
                                                item.isOngoing -> MaterialTheme.colorScheme.onErrorContainer
                                                item.isClosest -> MaterialTheme.colorScheme.onPrimaryContainer
                                                else -> MaterialTheme.colorScheme.primary
                                            },
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                        )
                                    }
                                } else {
                                    Text(
                                        text = slotItem,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                    )
                                }
                                if (index < item.slots.size - 1) {
                                    Text(
                                        text = "+",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    )
                                }
                            }
                            Text(
                                text = ")",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }

                // Line 3: Title
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Right-hand full side covering the location: Block on top, room number next line
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = when {
                    item.isOngoing -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                    item.isClosest -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                },
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    when {
                        item.isOngoing -> MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                        item.isClosest -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    },
                ),
                modifier = Modifier
                    .width(72.dp)
                    .height(76.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = item.roomBlock,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            item.isOngoing -> MaterialTheme.colorScheme.error
                            item.isClosest -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = item.roomNumber,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
