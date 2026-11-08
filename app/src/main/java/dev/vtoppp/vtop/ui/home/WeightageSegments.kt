package dev.vtoppp.vtop.ui.home

import dev.vtoppp.vtop.data.MarkEntry

internal data class WeightSegment(
    val label: String,
    val weight: Float,
    val fill: Float,
    val unposted: Boolean,
    private val orderHint: Double,
) {
    internal val orderKey: Double get() = orderHint
}

internal fun isUnposted(entry: MarkEntry): Boolean =
    entry.scoredMarks == 0.0 && entry.scoredPercentage == 0.0

internal fun weightOf(entry: MarkEntry): Float =
    entry.weightagePercent.toFloat().takeIf { it > 0f }
        ?: entry.maxMarks.toFloat().takeIf { it > 0f }
        ?: 1f

internal fun earnedWeightage(components: List<MarkEntry>): Float =
    components.fold(0f) { acc, c ->
        if (isUnposted(c) || c.maxMarks <= 0) {
            acc
        } else {
            acc + weightOf(c) * (c.scoredMarks / c.maxMarks).coerceIn(0.0, 1.0).toFloat()
        }
    }

internal fun buildWeightSegments(components: List<MarkEntry>): List<WeightSegment> {
    if (components.isEmpty()) return emptyList()

    val parsed = components.map { c ->
        val label = normalizeComponents(c.componentName)
        val weight = weightOf(c)
        val fill = if (isUnposted(c) || c.maxMarks <= 0) {
            0f
        } else {
            (c.scoredMarks / c.maxMarks).toFloat().coerceIn(0f, 1f)
        }
        WeightSegment(
            label = label,
            weight = weight,
            fill = fill,
            unposted = isUnposted(c),
            orderHint = if (c.maxMarks > 0) c.maxMarks else weight.toDouble(),
        )
    }

    val known = parsed.sumOf { it.weight.toDouble() }.toFloat()
    val hasTerminal = parsed.any { terminalLabel(it.label) }
    val ghosts = if (hasTerminal || known >= 100f) {
        emptyList()
    } else {
        val fat = WeightSegment("FAT", (30f).coerceAtMost(100f - known), 0f, unposted = true, orderHint = 30.0)
        val internalRemainder = (100f - known - fat.weight).takeIf { it > 0.5f }
            ?.let { listOf(WeightSegment("Internal", it, 0f, unposted = true, orderHint = it.toDouble())) }
            ?: emptyList()
        internalRemainder + fat
    }

    return (parsed + ghosts).sortedWith(
        compareBy(
            { it.unposted },
            { it.orderKey },
            { it.label.lowercase() },
        ),
    )
}

private val terminalRegex = Regex("""(?i)(term end|mid.?term|final exam|end sem|fat)""")
private fun terminalLabel(label: String): Boolean = terminalRegex.containsMatchIn(label)
