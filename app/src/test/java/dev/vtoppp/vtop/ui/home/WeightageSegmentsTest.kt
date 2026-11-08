package dev.vtoppp.vtop.ui.home

import dev.vtoppp.vtop.data.MarkEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun entry(name: String, weight: Double, max: Double, scored: Double) = MarkEntry(
    regNo = "REG",
    semesterSubId = "SEM",
    courseCode = "ABC1234",
    courseTitle = "Course",
    componentName = name,
    maxMarks = max,
    weightagePercent = weight,
    scoredMarks = scored,
    scoredPercentage = if (max > 0) scored / max * 100 else 0.0,
)

class WeightageSegmentsTest {

    @Test
    fun missingFat_isSynthesizedAsFat30PlusRemainingInternal() {
        val segments = buildWeightSegments(
            listOf(
                entry("Continuous Assessment Test – I", 15.0, 50.0, 29.0),
                entry("Continuous Assessment Test – II", 15.0, 50.0, 20.0),
                entry("Assignment", 10.0, 10.0, 8.0),
            ),
        )
        assertEquals(5, segments.size)
        assertEquals(listOf("Assignment", "CAT-I", "CAT-II", "FAT", "Internal"), segments.map { it.label })
        val fat = segments.first { it.label == "FAT" }
        assertEquals(30f, fat.weight, 0.001f)
        assertEquals(0f, fat.fill, 0.0f)
        assertTrue(fat.unposted)
        val internal = segments.first { it.label == "Internal" }
        assertEquals(30f, internal.weight, 0.001f)
        assertTrue(internal.unposted)
    }

    @Test
    fun presentTermEnd_suppressesGhostFat() {
        val segments = buildWeightSegments(
            listOf(
                entry("Mid Term", 30.0, 50.0, 36.0),
                entry("Term End Examination", 30.0, 100.0, 77.0),
            ),
        )
        assertEquals(2, segments.size)
        assertTrue(segments.last().label == "Term End")
    }

    @Test
    fun orderMatchesMarksLayoutPostedAscByMaxThenAlphabetical() {
        val segments = buildWeightSegments(
            listOf(
                entry("Assignment", 10.0, 10.0, 8.0),
                entry("Term End Examination", 30.0, 100.0, 77.0),
                entry("Continuous Assessment Test – II", 15.0, 50.0, 20.0),
                entry("Continuous Assessment Test – I", 15.0, 50.0, 29.0),
            ),
        )
        assertEquals(
            listOf("Assignment", "CAT-I", "CAT-II", "Term End"),
            segments.map { it.label },
        )
    }

    @Test
    fun fillIsScoredOverMaxClamped() {
        val segments = buildWeightSegments(
            listOf(
                entry("Term End Examination", 30.0, 100.0, 81.0),
                entry("Continuous Assessment Test – I", 15.0, 50.0, 29.0),
            ),
        )
        val fat = segments.first { it.label == "Term End" }
        val cat = segments.first { it.label == "CAT-I" }
        assertEquals(0.81f, fat.fill, 0.001f)
        assertEquals(0.58f, cat.fill, 0.001f)
    }

    @Test
    fun unpostedComponent_fillsZero() {
        val segments = buildWeightSegments(
            listOf(entry("Term End Examination", 30.0, 100.0, 0.0)),
        )
        assertEquals(0f, segments.first().fill, 0.0f)
        assertTrue(segments.first().unposted)
    }

    @Test
    fun emptyInput_yieldsNoBar() {
        assertTrue(buildWeightSegments(emptyList()).isEmpty())
    }

    @Test
    fun earnedWeightage_sumsFilteredScoredWeights() {
        val earned = earnedWeightage(
            listOf(
                entry("Continuous Assessment Test – I", 15.0, 50.0, 29.0),
                entry("Continuous Assessment Test – II", 15.0, 50.0, 20.0),
                entry("Assignment", 10.0, 10.0, 8.0),
                entry("Term End Examination", 30.0, 100.0, 77.0),
            ),
        )
        assertEquals(8.7f + 6.0f + 8.0f + 23.1f, earned, 0.01f)
    }

    @Test
    fun earnedWeightage_ignoresUnpostedComponents() {
        val earned = earnedWeightage(
            listOf(
                entry("Term End Examination", 30.0, 100.0, 0.0),
                entry("Continuous Assessment Test – II", 15.0, 50.0, 20.0),
            ),
        )
        assertEquals(6.0f, earned, 0.01f)
    }
}
