@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.vtoppp.vtop.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.vtoppp.vtop.R
import dev.vtoppp.vtop.data.UserDatabase

@Composable
fun GradeListPage(
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val grades by UserDatabase.gradeHistoryFlow.collectAsState()
    val marks by UserDatabase.marksFlow.collectAsState()
    val summary by UserDatabase.academicSummaryFlow.collectAsState()
    val isSyncing by UserDatabase.isSyncingFlow.collectAsState()
    val marksByCourse = remember(marks) { marks.groupBy { it.courseCode } }
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
    var sortByGrade by rememberSaveable { mutableStateOf(false) }
    val displayedCourses = remember(courses, sortByGrade) {
        if (sortByGrade) {
            courses.sortedWith(compareBy({ -gradeRank(it.grade) }, { -examEpochDay(it.examMonth) }, { -it.updatedAtMs }))
        } else {
            courses
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 84.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = onBack,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Grades",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${courses.size} courses · ${freshness.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                onClick = { sortByGrade = !sortByGrade },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_expand_more),
                        contentDescription = "Toggle sort order",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (sortByGrade) "Grade" else "Recent",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (courses.isEmpty()) {
            EmptyState(
                title = "No grades yet",
                body = "Courses with grades appear here after the first sync.",
                onRefresh = onRefresh,
                isSyncing = isSyncing,
            )
            return@Column
        }

        LazyColumn(
            state = rememberLazyListState(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 2.dp),
        ) {
            itemsIndexed(displayedCourses, key = { i, c -> "$i:${c.examMonth}:${c.courseCode}" }) { i, course ->
                CourseRow(
                    grade = course,
                    components = marksByCourse[course.courseCode].orEmpty(),
                    stateKey = "$i:${course.examMonth}:${course.courseCode}",
                )
            }
        }
    }
}
