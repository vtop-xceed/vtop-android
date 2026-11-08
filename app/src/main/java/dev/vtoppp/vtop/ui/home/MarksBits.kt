package dev.vtoppp.vtop.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import dev.vtoppp.vtop.R
import dev.vtoppp.vtop.ui.theme.Motion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.vtoppp.vtop.data.GradeHistoryEntry
import dev.vtoppp.vtop.data.MarkEntry
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun examEpochDay(examMonth: String?): Long {
    if (examMonth.isNullOrBlank()) return 0L
    return runCatching {
        YearMonth.parse(examMonth.trim(), DateTimeFormatter.ofPattern("MMM-yyyy", Locale.ENGLISH)).atDay(1).toEpochDay()
    }.getOrDefault(0L)
}

internal fun gradeRank(grade: String): Int = when (grade.uppercase()) {
    "S" -> 90
    "A" -> 80
    "B" -> 70
    "C" -> 60
    "D" -> 50
    "E" -> 40
    "P" -> 30
    "F" -> 10
    else -> 0
}

internal fun componentComparator() = compareBy<MarkEntry>(
    { it.scoredMarks == 0.0 && it.scoredPercentage == 0.0 },
    { it.maxMarks },
    { normalizeComponents(it.componentName).lowercase(Locale.US) },
)

internal fun normalizeComponents(name: String): String {
    val t = name.replace("–", "-").trim()
    return when {
        t.contains("Continuous Assessment Test", ignoreCase = true) ->
            if (t.contains("II") || t.endsWith("2")) "CAT-II" else "CAT-I"
        t.equals("Term End Examination", ignoreCase = true) -> "Term End"
        t.equals("Class Assesment - Quiz", ignoreCase = true) -> "Quiz"
        t.equals("Class Assessment - Quiz", ignoreCase = true) -> "Quiz"
        t.equals("Assignment", ignoreCase = true) -> "Assignment"
        t.equals("Project Only", ignoreCase = true) -> "Project"
        else -> t
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WeightageBar(
    components: List<MarkEntry>,
    modifier: Modifier = Modifier,
) {
    val segments = remember(components) { buildWeightSegments(components) }
    if (segments.isEmpty()) return
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val barFill = MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f),
        ) {
            segments.forEach { segment ->
                key(segment.label) {
                    val animatedFill by animateFloatAsState(
                        targetValue = segment.fill,
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        label = "segmentFill",
                    )
                    Canvas(
                        modifier = Modifier
                            .weight(segment.weight)
                            .height(6.dp),
                    ) {
                        val radius = CornerRadius(size.height / 2f, size.height / 2f)
                        drawRoundRect(color = track, topLeft = Offset.Zero, size = size, cornerRadius = radius)
                        if (animatedFill > 0f) {
                            drawRoundRect(
                                color = barFill,
                                topLeft = Offset.Zero,
                                size = Size(size.width * animatedFill, size.height),
                                cornerRadius = radius,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WeightageChips(
    components: List<MarkEntry>,
    grade: String? = null,
    corner: androidx.compose.ui.unit.Dp = 10.dp,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
    val chipShape = remember(corner) { RoundedCornerShape(corner) }
    val earned = remember(components) { earnedWeightage(components) }
    val earnedAnimated by animateIntAsState(
        targetValue = kotlin.math.ceil(earned.toDouble() - 1e-9).toInt(),
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "earned",
    )
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Surface(
        shape = chipShape,
        color = track,
        modifier = Modifier
            .width(40.dp)
            .height(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = earnedAnimated.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
    Spacer(modifier = Modifier.width(6.dp))
    val gradeText = grade?.takeIf { it.isNotBlank() }?.let(::gradeLabel) ?: "?"
    val gradeSurface by animateColorAsState(
        targetValue = if (gradeText == "?") track else gradeContainer(grade!!),
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "gradeSurface",
    )
    val gradeContent by animateColorAsState(
        targetValue = if (gradeText == "?") MaterialTheme.colorScheme.onSurfaceVariant else gradeOnContainer(grade!!),
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "gradeContent",
    )
    Surface(
        shape = chipShape,
        color = gradeSurface,
        modifier = Modifier
            .width(40.dp)
            .height(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = gradeText,
                style = if (gradeText.length > 1) MaterialTheme.typography.labelSmall else MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = gradeContent,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
    }
}

@Composable
internal fun gradeContainer(grade: String): Color = when (grade.uppercase()) {
    "S", "A" -> MaterialTheme.colorScheme.primaryContainer
    "B" -> MaterialTheme.colorScheme.secondaryContainer
    "C", "D", "E" -> MaterialTheme.colorScheme.tertiaryContainer
    "F", "FAIL" -> MaterialTheme.colorScheme.errorContainer
    else -> MaterialTheme.colorScheme.surfaceContainerHighest
}

@Composable
internal fun gradeOnContainer(grade: String): Color = when (grade.uppercase()) {
    "S", "A" -> MaterialTheme.colorScheme.onPrimaryContainer
    "B" -> MaterialTheme.colorScheme.onSecondaryContainer
    "C", "D", "E" -> MaterialTheme.colorScheme.onTertiaryContainer
    "F", "FAIL" -> MaterialTheme.colorScheme.onErrorContainer
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

internal fun gradeForCourse(courseCode: String, history: List<GradeHistoryEntry>): String? =
    history.asSequence()
        .filter { it.courseCode.equals(courseCode, ignoreCase = true) && it.grade.isNotBlank() }
        .maxByOrNull { gradeRank(it.grade) }
        ?.grade

internal fun gradeLabel(grade: String): String = when (grade.uppercase()) {
    "P", "PASS" -> "PASS"
    else -> grade.uppercase()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CourseRow(
    grade: GradeHistoryEntry,
    components: List<MarkEntry>,
    stateKey: String,
) {
    var expanded by rememberSaveable(stateKey) { mutableStateOf(false) }
    val chevronAngle by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "chevron",
    )
    val cardCorner by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (expanded) 20.dp else 14.dp,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "cardCorner",
    )
    val chipCorner by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (expanded) 16.dp else 10.dp,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "chipCorner",
    )
    val cardShape = remember(cardCorner) { RoundedCornerShape(cardCorner) }
    val hasComponents = components.isNotEmpty()
    val componentsSorted = remember(components) { components.sortedWith(componentComparator()) }
    val terms = remember(components) { components.map { it.semesterSubId }.distinct() }
    val failBorder = if (grade.grade.uppercase() == "F") {
        Modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
            shape = cardShape,
        )
    } else {
        Modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            shape = cardShape,
        )
    }

    Surface(
        shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .then(failBorder)
            .clickable { if (hasComponents) expanded = !expanded },
    ) {
        Column(modifier = Modifier.animateContentSize(tween(Motion.Content))) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = grade.courseCode,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = String.format(Locale.US, "  ·  %.1f cr", grade.credits),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    Text(
                        text = grade.courseTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (components.isNotEmpty()) {
                    WeightageChips(components = components, grade = grade.grade, corner = chipCorner)
                }
                if (hasComponents) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(18.dp)
                            .graphicsLayer { rotationZ = chevronAngle },
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(Motion.Quick)),
                exit = fadeOut(tween(Motion.Fast)),
            ) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    WeightageBar(components = componentsSorted, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                    if (terms.size > 1) {
                        Text(
                            text = "Marks from ${terms.size} semesters (re-attempt mix)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(start = 14.dp, top = 6.dp),
                        )
                    }
                    componentsSorted.forEach { c -> MarksLine(c) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MarksOnlyRow(
    courseCode: String,
    title: String,
    components: List<MarkEntry>,
    grade: String? = null,
) {
    var expanded by rememberSaveable("m:$courseCode") { mutableStateOf(false) }
    val chevronAngle by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "chevron",
    )
    val cardCorner by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (expanded) 20.dp else 14.dp,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "cardCorner",
    )
    val chipCorner by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (expanded) 16.dp else 10.dp,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "chipCorner",
    )
    val cardShape = remember(cardCorner) { RoundedCornerShape(cardCorner) }
    val componentsSorted = remember(components) { components.sortedWith(componentComparator()) }
    Surface(
        shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = cardShape,
            )
            .clickable { expanded = !expanded },
    ) {
        Column(modifier = Modifier.animateContentSize(tween(Motion.Content))) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = courseCode,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (components.isNotEmpty()) {
                    WeightageChips(components = components, grade = grade, corner = chipCorner)
                }
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(18.dp)
                        .graphicsLayer { rotationZ = chevronAngle },
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(Motion.Quick)),
                exit = fadeOut(tween(Motion.Fast)),
            ) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    WeightageBar(components = componentsSorted, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                    componentsSorted.forEach { c -> MarksLine(c) }
                }
            }
        }
    }
}


@Composable
internal fun MarksLine(entry: MarkEntry) {
    val unposted = entry.scoredMarks == 0.0 && entry.scoredPercentage == 0.0
    val scoredText = if (unposted) "–" else String.format(Locale.US, "%.0f", entry.scoredMarks)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = normalizeComponents(entry.componentName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$scoredText / ${maxOf(0, entry.maxMarks.toInt())}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (unposted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
        )
    }
}

internal data class Freshness(val label: String, val partial: Boolean)

internal fun freshnessOf(stamps: List<Long>): Freshness {
    val present = stamps.filter { it > 0L }
    if (present.isEmpty()) return Freshness("Never synced", false)
    val oldest = present.min()
    val skewMs = present.max() - present.min()
    val ageMs = System.currentTimeMillis() - oldest
    val label = when {
        ageMs < 60_000 -> "Synced from VTOP just now"
        ageMs < 3_600_000 -> "Synced from VTOP ${ageMs / 60_000}m ago"
        ageMs < 86_400_000 -> "Synced from VTOP ${ageMs / 3_600_000}h ago"
        else -> "Synced from VTOP ${ageMs / 86_400_000}d ago"

    }
    val partial = skewMs > 60_000 || present.size < stamps.size
    return Freshness(label, partial)
}

