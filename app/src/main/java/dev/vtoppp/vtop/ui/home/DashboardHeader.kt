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
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
internal fun OrganicQuestionCard(
    modifier: Modifier = Modifier,
) {
    var questionIndex by remember { mutableIntStateOf(SessionGreeting.questionIndex) }
    var shapeIndex by remember { mutableIntStateOf(SessionGreeting.shapeIndex) }
    val cardShape = QuestionCardShapes[shapeIndex.coerceIn(QuestionCardShapes.indices)].toShape()
    val currentQuestion = IntriguingQuestions[questionIndex.coerceIn(IntriguingQuestions.indices)]

    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterEnd,
    ) {
        Surface(
            onClick = {
                // user presses it if it feels intriguing
                questionIndex = (questionIndex + 1) % IntriguingQuestions.size
                shapeIndex = (shapeIndex + 1) % QuestionCardShapes.size
            },
            shape = cardShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shadowElevation = 2.dp,
            modifier = Modifier
                .wrapContentSize(align = Alignment.CenterEnd, unbounded = true)
                .requiredSize(118.dp)
                .aspectRatio(1f),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = currentQuestion,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ponytail: fast lightweight ping to GitHub releases API; checks connectivity and updates
internal suspend fun pingGitHubRelease(): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val client = OkHttpClient.Builder()
            .connectTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url("https://api.github.com/repos/vtop-xceed/vtop-android/releases/latest")
            .header("User-Agent", "vtop-android")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            response.isSuccessful || response.code == 404
        }
    }.getOrDefault(true)
}

/**
 * Small fluid pill showing bridge status: every ongoing stage stays expanded with its
 * label; on success or full failure the result label stays visible for 3 seconds,
 * then collapses to a green or red dot. Tapping the dot re-expands the label for 4s.
 */
@Composable
internal fun FluidStatusPill(
    isReady: Boolean,
    label: String,
    readyLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isBusy: Boolean = false,
    isError: Boolean = false,
    readyIcon: Int = R.drawable.ic_check,
) {
    var userToggled by remember { mutableStateOf(false) }
    // Terminal states (ready or failure) keep the result label visible for 3s,
    // then collapse to a colored dot. Ongoing stages always stay expanded.
    var terminalVisible by remember { mutableStateOf(false) }

    LaunchedEffect(isReady, isError) {
        if (isReady || isError) {
            terminalVisible = true
            delay(3000L)
            terminalVisible = false
        } else {
            terminalVisible = false
        }
    }

    LaunchedEffect(userToggled) {
        if (userToggled) {
            delay(4000L)
            userToggled = false
        }
    }

    val showExpanded = (!isReady && !isError) || terminalVisible || userToggled

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isError -> MaterialTheme.colorScheme.errorContainer
            isReady && !userToggled -> StatusColors.Success
            isReady && userToggled -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = tween(350, easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)),
        label = "pillBg",
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            isError -> MaterialTheme.colorScheme.onErrorContainer
            isReady && !userToggled -> Color.White
            isReady && userToggled -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(350, easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)),
        label = "pillContent",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(
                onClickLabel = if (isReady) readyLabel else label,
            ) {
                if (isReady || isError) {
                    userToggled = !userToggled
                }
                onClick()
            },
    ) {
        if (showExpanded) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(20.dp)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                if (isReady) {
                    Icon(
                        painter = painterResource(readyIcon),
                        contentDescription = readyLabel,
                        tint = contentColor,
                        modifier = Modifier.size(10.dp),
                    )
                } else if (isBusy || !isReady) {
                    val transition = rememberInfiniteTransition(label = "pillPulse")
                    val alpha by transition.animateFloat(
                        initialValue = Motion.PulseMinAlpha,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(Motion.PulseMs, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "pillPulseAlpha",
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .graphicsLayer { this.alpha = alpha }
                            .clip(CircleShape)
                            .background(if (isError) MaterialTheme.colorScheme.error else StatusColors.Warning),
                    )
                }

                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (isReady) readyLabel else label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = contentColor,
                    maxLines = 1,
                )
            }
        } else {
            // Settled dot: green for ready, red for failure (background is the dot).
            Box(modifier = Modifier.size(8.dp))
        }
    }
}

