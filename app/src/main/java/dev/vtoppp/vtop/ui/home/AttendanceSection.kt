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

@Composable
internal fun AttendanceSection(
    subjects: List<SubjectAttendance>,
    onOpenAttendance: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { subjects.size })
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Attendance Header with arrow ↗ as in sketch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onOpenAttendance() },
            ) {
                Text(
                    text = "Attendance",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "↗",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // Subject cycle indicator / controls (e.g. 1/5 with arrows)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "${pagerState.currentPage + 1}/${subjects.size}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Surface(
                    onClick = {
                        if (pagerState.currentPage > 0) {
                            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        }
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(24.dp),
                    enabled = pagerState.currentPage > 0,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_right),
                            contentDescription = "Previous subject",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (pagerState.currentPage > 0) 1f else 0.3f),
                            modifier = Modifier
                                .size(16.dp)
                                .graphicsLayer { rotationZ = 180f },
                        )
                    }
                }
                Surface(
                    onClick = {
                        if (pagerState.currentPage < subjects.size - 1) {
                            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(24.dp),
                    enabled = pagerState.currentPage < subjects.size - 1,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_right),
                            contentDescription = "Next subject",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (pagerState.currentPage < subjects.size - 1) 1f else 0.3f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Horizontal Pager with Card Deck Layering (matching Timeline carousel effect)
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 40.dp),
            beyondViewportPageCount = 1,
            pageSpacing = (-6).dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(124.dp),
        ) { pageIndex ->
            val subject = subjects[pageIndex]
            val isWarning = subject.percentage in 75..79
            val isDanger = subject.percentage < 75

            // Calculate offset for card deck layering
            val pageOffset = ((pagerState.currentPage - pageIndex) +
                pagerState.currentPageOffsetFraction).absoluteValue
            val isCenter = pageOffset < 0.5f

            val scale by animateFloatAsState(
                targetValue = (1f - (pageOffset * 0.08f)).coerceIn(0.92f, 1f),
                label = "attendanceCardScale",
            )
            val alpha by animateFloatAsState(
                targetValue = (1f - (pageOffset * 0.60f)).coerceIn(0.40f, 1f),
                label = "attendanceCardAlpha",
            )
            val elevation by animateDpAsState(
                targetValue = if (isCenter) 10.dp else 1.dp,
                label = "attendanceCardElevation",
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
                    .then(if (blurRadius > 0.dp && dev.vtoppp.vtop.ui.theme.LocalBlurEnabled.current) Modifier.blur(radius = blurRadius) else Modifier),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isCenter) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = elevation),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    when {
                        isDanger -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                        isWarning -> StatusColors.Warning.copy(alpha = 0.45f)
                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isCenter) 0.35f else 0.15f)
                    },
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Left Column: takes up to ~65-70% width, showing full subject name up to the skip box
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = subject.courseCode,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.5.sp,
                            )
                            Text(
                                text = "·",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            Text(
                                text = subject.courseTitle,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }

                        Spacer(Modifier.height(6.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = "${subject.percentage}%",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                color = when {
                                    isDanger -> MaterialTheme.colorScheme.error
                                    isWarning -> StatusColors.Warning
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                ),
                            ) {
                                Text(
                                    text = "${subject.attendedClasses} / ${subject.totalClasses}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }

                    // Right Column: can safely miss till CAT-1 - 0
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = when {
                            isDanger -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                            isWarning -> StatusColors.Warning.copy(alpha = 0.15f)
                            else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            when {
                                isDanger -> MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                                isWarning -> StatusColors.Warning.copy(alpha = 0.4f)
                                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            },
                        ),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = "can safely miss",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "till ${subject.examTarget}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            )
                            Spacer(Modifier.height(3.dp))
                            Surface(
                                shape = CircleShape,
                                color = when {
                                    isDanger -> MaterialTheme.colorScheme.error
                                    isWarning -> StatusColors.Warning
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${subject.safeBunks}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                    )
                                }
                            }
                        }
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
