@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.vtoppp.vtop.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vtoppp.vtop.R
import dev.vtoppp.vtop.data.UserDatabase
import dev.vtoppp.vtop.ui.theme.AppDimens
import dev.vtoppp.vtop.ui.theme.AppPageHeader

@Composable
fun GradeHistoryPage(
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val grades by UserDatabase.gradeHistoryFlow.collectAsStateWithLifecycle()
    val marks by UserDatabase.marksFlow.collectAsStateWithLifecycle()
    val summary by UserDatabase.academicSummaryFlow.collectAsStateWithLifecycle()
    val isSyncing by UserDatabase.isSyncingFlow.collectAsStateWithLifecycle()
    val marksByCourse = remember(marks) { marks.groupBy { it.courseCode } }
    val courses = remember(grades) {
        grades.groupBy { it.courseCode }
            .mapNotNull { (_, rows) ->
                rows.minWithOrNull(compareBy({ -gradeRank(it.grade) }, { examEpochDay(it.examMonth) }))
            }
            .sortedByDescending { examEpochDay(it.examMonth) }
    }
    val nowMs = rememberLiveClockMs()
    val stamps = remember(summary, grades, marks) {
        val gradeStamps = listOf(
            summary?.updatedAtMs ?: 0L,
            grades.maxOfOrNull { it.updatedAtMs } ?: 0L,
        )
        if (gradeStamps.any { it > 0L }) gradeStamps else listOf(marks.maxOfOrNull { it.updatedAtMs } ?: 0L)
    }
    val freshness = remember(stamps, nowMs) { freshnessOf(stamps, nowMs) }
    var sortByGrade by rememberSaveable { mutableStateOf(false) }
    val sortIconAngle by animateFloatAsState(
        targetValue = if (sortByGrade) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "sortIcon",
    )
    val displayedCourses = remember(courses, sortByGrade) {
        if (sortByGrade) {
            courses.sortedWith(compareBy({ -gradeRank(it.grade) }, { -examEpochDay(it.examMonth) }, { -it.updatedAtMs }))
        } else {
            courses
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SyncingBar(visible = isSyncing)
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            state = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState(),
            isRefreshing = isSyncing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
            indicator = {},
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = AppDimens.ScreenHorizontal)
                    .padding(top = AppDimens.ScreenTop, bottom = AppDimens.ScreenBottom),
            ) {
                AppPageHeader(
                    title = "Grades",
                    subtitle = "${courses.size} courses · ${freshness.label}",
                    onBack = onBack,
                    trailing = {
                        FilledTonalToggleButton(
                            checked = sortByGrade,
                            onCheckedChange = { sortByGrade = it },
                            buttonSize = ToggleButtonSize.ExtraSmall,
                            shapes = ToggleButtonDefaults.shapesFor(ToggleButtonSize.ExtraSmall),
                            contentPadding = ToggleButtonDefaults.contentPaddingFor(ButtonDefaults.ExtraSmallContainerHeight),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_expand_more),
                                contentDescription = "Toggle sort order",
                                modifier = Modifier
                                    .size(16.dp)
                                    .graphicsLayer { rotationZ = sortIconAngle },
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (sortByGrade) "Grade" else "Recent",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    },
                )
                Spacer(Modifier.height(AppDimens.ItemGap))

                if (courses.isEmpty()) {
                    EmptyState(
                        title = "No grades yet",
                        body = "Courses with grades appear here after the first sync.",
                        onRefresh = onRefresh,
                        isSyncing = isSyncing,
                    )
                } else {
                    LazyColumn(
                        state = rememberLazyListState(),
                        verticalArrangement = Arrangement.spacedBy(AppDimens.ItemGap),
                        contentPadding = PaddingValues(top = 2.dp),
                    ) {
                        itemsIndexed(displayedCourses, key = { _, c -> c.courseCode }) { _, course ->
                            Box(modifier = Modifier.animateItem()) {
                                CourseRow(
                                    grade = course,
                                    components = marksByCourse[course.courseCode].orEmpty(),
                                    stateKey = course.courseCode,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
