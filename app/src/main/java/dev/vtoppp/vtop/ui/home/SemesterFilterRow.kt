package dev.vtoppp.vtop.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.vtoppp.vtop.data.SemesterAttended

/**
 * Horizontal semester filter chips matching the design in MarksGradesPage.
 * Allows 1-tap switching across semesters without modal/dropdown clutter.
 */
@Composable
internal fun SemesterFilterRow(
    options: List<SemesterAttended>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return

    val effectiveSelectedId = selectedId ?: options.firstOrNull()?.semesterSubId

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        options.forEach { sem ->
            val isSelected = sem.semesterSubId == effectiveSelectedId
            val containerColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                label = "semFilterContainer",
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                label = "semFilterContent",
            )
            Surface(
                onClick = { onSelect(sem.semesterSubId) },
                shape = RoundedCornerShape(12.dp),
                color = containerColor,
            ) {
                Text(
                    text = sem.semesterName.substringBefore(" - "),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}
