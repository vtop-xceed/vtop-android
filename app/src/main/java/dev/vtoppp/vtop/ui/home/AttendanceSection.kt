package dev.vtoppp.vtop.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.vtoppp.vtop.ui.theme.AppDimens
import dev.vtoppp.vtop.ui.theme.AttendanceGreen
import dev.vtoppp.vtop.ui.theme.LocalBlurEnabled
import dev.vtoppp.vtop.ui.theme.StatusColors
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AttendanceSection(
    subjects: List<SubjectAttendance>,
    initialIndex: Int = 0,
    onOpenAttendance: () -> Unit,
) {
    val initialPage = initialIndex.coerceIn(0, (subjects.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { subjects.size },
    )

    LaunchedEffect(initialIndex, subjects.size) {
        if (subjects.isNotEmpty() && !pagerState.isScrollInProgress) {
            val target = initialIndex.coerceIn(0, subjects.size - 1)
            if (pagerState.currentPage != target) {
                pagerState.animateScrollToPage(target)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Attendance Header with arrow ↗ on far right inside a circle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.ScreenHorizontal, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.CompactGap),
                modifier = Modifier.clickable { onOpenAttendance() },
            ) {
                Text(
                    text = "Attendance",
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subjects.isNotEmpty()) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Text(
                            text = "${pagerState.currentPage + 1}/${subjects.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Surface(
                onClick = onOpenAttendance,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "↗",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (subjects.isEmpty()) {
            Surface(
                onClick = onOpenAttendance,
                shape = MaterialTheme.shapes.largeIncreased,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.ScreenHorizontal)
                    .height(96.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = AppDimens.CardPadding, vertical = AppDimens.CompactPadding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "No attendance cached yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Sync when online to load attendance",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            // Horizontal Pager with Card Deck Layering (matching Timeline carousel effect)
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 40.dp),
                beyondViewportPageCount = 1,
                pageSpacing = (-6).dp,
                flingBehavior = PagerDefaults.flingBehavior(
                    pagerState,
                    snapAnimationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp),
            ) { pageIndex ->
                val subject = subjects[pageIndex]
                val isWarning = subject.percentage in 75..79
                val isDanger = subject.percentage < 75

                // Calculate offset for card deck layering
                val pageOffset = ((pagerState.currentPage - pageIndex) +
                    pagerState.currentPageOffsetFraction).absoluteValue
                val isCenter = pageOffset < 0.5f

                val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
                val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Dp>()
                val scale by animateFloatAsState(
                    targetValue = (1f - (pageOffset * 0.08f)).coerceIn(0.92f, 1f),
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    label = "attendanceCardScale",
                )
                val alpha by animateFloatAsState(
                    targetValue = (1f - (pageOffset * 0.60f)).coerceIn(0.40f, 1f),
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "attendanceCardAlpha",
                )
                val elevation by animateDpAsState(
                    targetValue = when {
                        isCenter && subject.isOngoing -> 12.dp
                        isCenter && subject.isNext -> 10.dp
                        isCenter -> 8.dp
                        else -> 1.dp
                    },
                    animationSpec = spatial,
                    label = "attendanceCardElevation",
                )
                // ponytail: safe bunks/miss indicators hidden for now per user request
                val containerColor by animateColorAsState(
                    targetValue = if (isCenter) {
                        when {
                            subject.isOngoing -> MaterialTheme.colorScheme.surfaceContainerHighest
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                    animationSpec = effects,
                    label = "attendanceContainer",
                )
                val borderColor by animateColorAsState(
                    targetValue = when {
                        subject.isOngoing -> AttendanceGreen.copy(alpha = if (isCenter) 0.85f else 0.45f)
                        subject.isNext -> MaterialTheme.colorScheme.primary.copy(alpha = if (isCenter) 0.85f else 0.45f)
                        isDanger -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                        isWarning -> StatusColors.Warning.copy(alpha = 0.45f)
                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isCenter) 0.35f else 0.15f)
                    },
                    animationSpec = effects,
                    label = "attendanceBorder",
                )
                val blurRadius = (8.dp * pageOffset).coerceIn(0.dp, 8.dp)
                val pivotX = when {
                    pageIndex < pagerState.currentPage -> 0.95f
                    pageIndex > pagerState.currentPage -> 0.05f
                    else -> 0.5f
                }

                Card(
                    onClick = onOpenAttendance,
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(1f - pageOffset)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                            transformOrigin = TransformOrigin(pivotX, 0.5f)
                        }
                        .then(if (blurRadius > 0.dp && LocalBlurEnabled.current) Modifier.blur(radius = blurRadius) else Modifier),
                    shape = MaterialTheme.shapes.largeIncreased,
                    colors = CardDefaults.cardColors(
                        containerColor = containerColor,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = elevation),
                    border = BorderStroke(
                        width = if ((subject.isOngoing || subject.isNext) && isCenter) 1.5.dp else 1.dp,
                        color = borderColor,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = AppDimens.CardPadding, vertical = AppDimens.CompactPadding),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Left Column (takes remaining width, all lines with TextOverflow.Ellipsis)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = AppDimens.CompactPadding),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            // Row 1: Course code + Status badge
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = subject.courseCode,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 0.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                // Status badge: LIVE / NEXT / Slot+Time / NOT TODAY
                                when {
                                    subject.isOngoing -> {
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = AttendanceGreen.copy(alpha = 0.2f),
                                            border = BorderStroke(1.dp, AttendanceGreen.copy(alpha = 0.6f)),
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(5.dp)
                                                        .background(AttendanceGreen, CircleShape),
                                                )
                                                Text(
                                                    text = "LIVE",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = AttendanceGreen,
                                                )
                                            }
                                        }
                                    }
                                    subject.isNext -> {
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                        ) {
                                            Text(
                                                text = if (subject.todayStartTime.isNotBlank()) "NEXT · ${subject.todayStartTime}" else "NEXT",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    subject.isToday -> {
                                        val statusLabel = when {
                                            !subject.todayStatus.isNullOrBlank() -> "${subject.todaySlot.ifBlank { "TODAY" }} · ${subject.todayStatus}"
                                            subject.todayStartTime.isNotBlank() -> "${subject.todaySlot.ifBlank { "TODAY" }} · ${subject.todayStartTime}"
                                            else -> subject.todaySlot.ifBlank { "TODAY" }
                                        }
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                                        ) {
                                            Text(
                                                text = statusLabel,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    else -> {
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
                                        ) {
                                            Text(
                                                text = "NOT TODAY",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(5.dp))

                            // Row 2: Full Course Title
                            Text(
                                text = subject.courseTitle,
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )

                            // ponytail: miss calculator hidden for now per user request
                        }

                        // Right Side: Uniform, prominent stat block with big percentage & fraction
                        AttendanceStatBadge(
                            percentage = subject.percentage,
                            attendedClasses = subject.attendedClasses,
                            totalClasses = subject.totalClasses,
                            modifier = Modifier
                                .width(84.dp)
                                .fillMaxHeight(),
                            compact = false,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Doodle card filling empty space on days with 2 classes or less.
 * "aha, freedom!"
 */
