package dev.vtoppp.vtop.ui.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.vtoppp.vtop.data.SemesterAttended
import dev.vtoppp.vtop.ui.theme.AppDimens

/**
 * Horizontal semester filter chips matching the design in MarksGradesPage.
 * Allows 1-tap switching across semesters without modal/dropdown clutter.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
        horizontalArrangement = Arrangement.spacedBy(AppDimens.CompactGap),
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        options.forEach { sem ->
            val isSelected = sem.semesterSubId == effectiveSelectedId
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(sem.semesterSubId) },
                label = {
                    Text(
                        text = sem.semesterName.substringBefore(" - "),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    )
                },
                shapes = FilterChipDefaults.shapes(
                    shape = MaterialTheme.shapes.medium,
                    selectedShape = MaterialTheme.shapes.medium,
                    pressedShape = MaterialTheme.shapes.small,
                ),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    labelColor = MaterialTheme.colorScheme.onSurface,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                border = null,
                contentPadding = PaddingValues(horizontal = 14.dp),
            )
        }
    }
}
