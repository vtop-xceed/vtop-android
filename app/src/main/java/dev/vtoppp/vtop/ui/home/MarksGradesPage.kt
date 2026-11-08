@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.vtoppp.vtop.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vtoppp.vtop.R
import dev.vtoppp.vtop.data.GradeHistoryEntry
import dev.vtoppp.vtop.data.MarkEntry
import dev.vtoppp.vtop.data.UserDatabase
import java.util.Locale
import dev.vtoppp.vtop.ui.theme.Motion
import dev.vtoppp.vtop.ui.theme.StatusColors

@Composable
fun MarksGradesPage(
    authorizedId: String,
    onOpenGrades: () -> Unit,
    onRefresh: () -> Unit,
    marksNotice: String?,
    onMarksNoticeShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val grades by UserDatabase.gradeHistoryFlow.collectAsState()
    val summary by UserDatabase.academicSummaryFlow.collectAsState()
    val marks by UserDatabase.marksFlow.collectAsState()
    val isSyncing by UserDatabase.isSyncingFlow.collectAsState()
    val courses = remember(grades) {
        grades.groupBy { it.courseCode }
            .mapNotNull { (_, rows) ->
                rows.minWithOrNull(compareBy({ -gradeRank(it.grade) }, { examEpochDay(it.examMonth) }))
            }
            .sortedByDescending { examEpochDay(it.examMonth) }
    }
    val stamps = remember(summary, grades, marks) {
        listOf(
            summary?.updatedAtMs ?: 0L,
            grades.maxOfOrNull { it.updatedAtMs } ?: 0L,
            marks.maxOfOrNull { it.updatedAtMs } ?: 0L,
        )
    }
    val freshness = remember(stamps) { freshnessOf(stamps) }
    val isEmpty = courses.isEmpty() && marks.isEmpty()
    val semesterNames by produceState(initialValue = mapOf<String, String>(), keys = arrayOf(marks)) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            UserDatabase.loadSemesters(authorizedId).associate { it.semesterSubId to it.semesterName }
        }
    }
    val marksBySemester = remember(marks) { marks.groupBy { it.semesterSubId } }
    val semesterPicks = remember(marksBySemester, semesterNames) {
        val allIds = (marksBySemester.keys + semesterNames.keys).distinct().sortedByDescending { it }
        allIds.map { id ->
            val rows = marksBySemester[id].orEmpty()
            SemesterPick(
                id = id,
                name = semesterNames[id]?.substringBefore(" - ") ?: id,
                courses = rows.map { it.courseCode }.distinct().size,
            )
        }
    }

    PullToRefreshBox(
        state = rememberPullToRefreshState(),
        isRefreshing = isSyncing,
        onRefresh = onRefresh,
        modifier = modifier
            .fillMaxSize(),
        indicator = {},
    ) {
    Column(modifier = Modifier.fillMaxSize()) {
    SyncingBar(visible = isSyncing)
    LazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 84.dp),
    ) {
        item("header") {
            Box(Modifier.padding(horizontal = 20.dp)) {
                MarksHeader(
                    freshness = freshness,
                    marksNotice = marksNotice,
                    onMarksNoticeShown = onMarksNoticeShown,
                )
            }
        }

        if (isEmpty) {
            item("empty") {
                Box(Modifier.padding(horizontal = 20.dp)) {
                    EmptyState(
                    title = "No marks yet",
                    body = "Marks and grades sync from VTOP when you're online.",
                    onRefresh = onRefresh,
                    isSyncing = isSyncing,
                )
                }
            }
            return@LazyColumn
        }

        if (courses.isNotEmpty()) {
            item("hero") {
                Box(Modifier.padding(horizontal = 20.dp)) {
                    HeroCard(summary = summary, semesterCount = semesterPicks.size, courses = courses, onOpenGrades = onOpenGrades)
                }
            }
        }
        item("label") {
            Box(Modifier.padding(start = 20.dp)) { SectionLabel("Marks") }
        }
        if (semesterPicks.isNotEmpty()) {
            item {
                MarksSemesterSection(
                    picks = semesterPicks,
                    marksBySemester = marksBySemester,
                    gradeHistory = grades,
                )
            }
        }
    }
    }
}
}

@Composable
private fun MarksSemesterSection(
    picks: List<SemesterPick>,
    marksBySemester: Map<String, List<MarkEntry>>,
    gradeHistory: List<dev.vtoppp.vtop.data.GradeHistoryEntry>,
) {
    var selectedId by rememberSaveable(picks.map { it.id }) {
        mutableStateOf(picks.firstOrNull()?.id)
    }
    val selected = picks.firstOrNull { it.id == selectedId } ?: picks.firstOrNull() ?: return
    val rows = marksBySemester[selected.id].orEmpty()
    val results by UserDatabase.semesterResultsFlow.collectAsState()
    val gpas = remember(picks, results) {
        results.associate { it.semesterSubId to it.gpa }
    }
    val byCourse = remember(rows) {
        rows.groupBy { it.courseCode }.toList()
            .sortedWith(compareBy({ it.second.firstOrNull()?.courseTitle.orEmpty().lowercase() }, { it.first }))
    }
    val initialIndex = remember(picks, selectedId) {
        picks.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val blurEnabled = dev.vtoppp.vtop.ui.theme.LocalBlurEnabled.current

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        itemsIndexed(picks, key = { _, it -> it.id }) { index, sem ->
            val isSelected = sem.id == selected.id
            val selectedCardColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "semCard",
            )
            val edgeFade by remember(listState, index) {
                derivedStateOf {
                    val viewport = listState.layoutInfo.viewportSize.width.toFloat()
                    val itemPx = 132f * density
                    val stepPx = itemPx + 10f * density
                    val contentPadPx = 20f * density
                    val scrolledPx = listState.firstVisibleItemIndex * stepPx + listState.firstVisibleItemScrollOffset
                    val pos = contentPadPx + index * stepPx - scrolledPx
                    val band = 96f * density
                    val visibleStart = pos.coerceAtLeast(0f)
                    val visibleEnd = (pos + itemPx).coerceAtMost(viewport)
                    val hidden = (itemPx - (visibleEnd - visibleStart)).coerceAtLeast(0f)
                    (1f - hidden / band).coerceIn(0f, 1f)
                }
            }
            Surface(
                onClick = { selectedId = sem.id },
                shape = RoundedCornerShape(16.dp),
                color = selectedCardColor,
                modifier = Modifier.graphicsLayer {
                    alpha = 0.4f + 0.6f * edgeFade
                    if (android.os.Build.VERSION.SDK_INT >= 31 && blurEnabled && edgeFade < 0.999f) {
                        val r = (1f - edgeFade) * 8f * density
                        renderEffect = BlurEffect(r, r, TileMode.Clamp)
                    }
                },
            ) {
                Column(
                    modifier = Modifier
                        .width(132.dp)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = sem.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (sem.courses == 0) "Awaiting marks" else "${sem.courses} courses",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        val gpa = gpas[sem.id]
                        Text(
                            text = if (gpa != null) String.format(java.util.Locale.US, "%.2f", gpa) else "--",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    AnimatedContent(
        targetState = selected.id,
        transitionSpec = {
            fadeIn(tween(Motion.Content)) togetherWith fadeOut(tween(Motion.Quick))
        },
        label = "semMarks",
    ) { _ ->
    if (byCourse.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_grade),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "No marks posted for this semester yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "FAT-1 and FAT-2 scores appear here once VTOP publishes them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        byCourse.forEach { (code, components) ->
            MarksOnlyRow(
                courseCode = code,
                title = components.firstOrNull()?.courseTitle.orEmpty(),
                components = components,
                grade = gradeForCourse(code, gradeHistory),
            )
        }
    }
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

private data class SemesterPick(
    val id: String,
    val name: String,
    val courses: Int,
)

@Composable
private fun MarksHeader(
    freshness: Freshness,
    marksNotice: String?,
    onMarksNoticeShown: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Grades",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(
                visible = freshness.partial,
                enter = fadeIn(tween(Motion.Quick)) + scaleIn(tween(Motion.Content)),
                exit = fadeOut(tween(Motion.Fast)) + scaleOut(tween(Motion.Fast)),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(StatusColors.Warning, CircleShape),
                )
            }
            if (freshness.partial) Spacer(Modifier.width(6.dp))
            Text(
                text = freshness.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (freshness.partial) StatusColors.Warning else MaterialTheme.colorScheme.outline.copy(alpha = 0.85f),
            )
        }
    }
    AnimatedVisibility(
        visible = marksNotice != null,
        enter = Motion.BannerEnter,
        exit = Motion.BannerExit,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = 8.dp)
                .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(12.dp))
                .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Text(
                text = marksNotice ?: "",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onMarksNoticeShown, modifier = Modifier.size(48.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = "Dismiss notice",
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun HeroCard(
    summary: dev.vtoppp.vtop.data.AcademicSummary?,
    semesterCount: Int,
    courses: List<GradeHistoryEntry>,
    onOpenGrades: () -> Unit,
) {
    val cgpa = summary?.cgpa ?: 0.0
    val earned = summary?.creditsEarned ?: 0.0
    val registered = summary?.creditsRegistered ?: 0.0
    val total = summary?.curriculumCredits?.takeIf { it > 0.0 } ?: registered
    val fCount = summary?.fCount ?: 0
    val ratioTarget = if (total > 0.0) (earned / total).coerceIn(0.0, 1.0).toFloat() else 0f
    val ratio by animateFloatAsState(targetValue = ratioTarget, animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(), label = "ratio")
    val cgpaAnimated by animateFloatAsState(targetValue = cgpa.toFloat(), animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(), label = "cgpa")

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "CGPA",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    onClick = onOpenGrades,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp)) {
                        Text(
                            text = "↗",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = String.format(Locale.US, "%.2f", cgpaAnimated),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = " / 10",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                Spacer(Modifier.weight(1f))
                AnimatedVisibility(
                    visible = fCount > 0,
                    enter = fadeIn(tween(Motion.Quick)) + scaleIn(tween(Motion.Content)),
                    exit = fadeOut(tween(Motion.Fast)) + scaleOut(tween(Motion.Fast)),
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            text = "$fCount F",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            Text(
                text = String.format(Locale.US, "%.0f / %.0f credits earned", earned, total),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            LinearWavyProgressIndicator(
                progress = { ratio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${(total - earned).coerceAtLeast(0.0).toInt()} credits to go · $semesterCount semesters",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            val order = listOf("S", "A", "B", "C", "D", "E", "P", "F")
            val pills = remember(courses) {
                order.mapNotNull { g -> courses.count { it.grade.uppercase() == g }.takeIf { it > 0 }?.let { g to it } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pills.forEach { (g, count) ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = gradeContainer(g),
                    ) {
                        Text(
                            text = "${gradeLabel(g)} $count",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = gradeOnContainer(g),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

