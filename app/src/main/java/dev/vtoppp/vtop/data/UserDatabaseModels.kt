package dev.vtoppp.vtop.data

data class UserProfile(
    val regNo: String,
    val name: String,
    val programme: String = "",
    val branch: String = "",
    val school: String = "",
    val email: String = "",
    val mobile: String = "",
    val photoUrl: String = "",
    val updatedAtMs: Long = System.currentTimeMillis(),
) {
    /** Convenient formatted degree/branch label for the dashboard header (e.g. "B.Tech CSE"). */
    val formattedBranch: String
        get() {
            if (branch.isBlank() && programme.isBlank()) return "Student"
            // ponytail: simple branch abbreviation mapping for common VIT programmes
            val prog = when {
                programme.contains("Bachelor of Technology", ignoreCase = true) ||
                    programme.contains("B.Tech", ignoreCase = true) -> "B.Tech"
                programme.contains("Master of Technology", ignoreCase = true) ||
                    programme.contains("M.Tech", ignoreCase = true) -> "M.Tech"
                programme.contains("Bachelor of Science", ignoreCase = true) ||
                    programme.contains("B.Sc", ignoreCase = true) -> "B.Sc"
                programme.contains("Master of Computer Applications", ignoreCase = true) ||
                    programme.contains("MCA", ignoreCase = true) -> "MCA"
                else -> programme.split(" ").firstOrNull().orEmpty()
            }
            val br = when {
                branch.contains("Computer Science", ignoreCase = true) -> "CSE"
                branch.contains("Electronics and Communication", ignoreCase = true) -> "ECE"
                branch.contains("Mechanical", ignoreCase = true) -> "MECH"
                branch.contains("Electrical", ignoreCase = true) -> "EEE"
                branch.contains("Information Technology", ignoreCase = true) -> "IT"
                branch.isNotBlank() -> branch
                else -> ""
            }
            return listOf(prog, br).filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { branch.ifBlank { programme } }
        }

    /** First name or display name for greeting. */
    val firstName: String
        get() {
            val trimmed = name.trim()
            if (trimmed.isBlank()) return regNo
            // If it's a registration number / ID (contains digits, e.g. 24BCY10147), keep uppercase ID
            if (trimmed.any { it.isDigit() }) return trimmed.uppercase()
            val firstWord = trimmed.split(" ").firstOrNull { it.isNotBlank() } ?: trimmed
            return firstWord.lowercase().replaceFirstChar { it.uppercase() }
        }
}

data class SemesterAttended(
    val regNo: String,
    val semesterSubId: String,
    val semesterName: String,
    val updatedAtMs: Long = System.currentTimeMillis(),
)

data class AttendanceEntry(
    val id: Long = 0,
    val regNo: String,
    val semesterSubId: String,
    val courseCode: String,
    val courseTitle: String,
    val courseType: String = "",
    val slot: String = "",
    val facultyName: String = "",
    val attendedClasses: Int = 0,
    val totalClasses: Int = 0,
    val attendancePercentage: Double = 0.0,
    val updatedAtMs: Long = System.currentTimeMillis(),
)

data class TimetableEntry(
    val regNo: String,
    val semesterSubId: String = "",
    val dayOfWeek: Int, // 1 = Monday, 2 = Tuesday, ..., 7 = Sunday
    val startTime: String,
    val endTime: String,
    val roomBlock: String,
    val roomNumber: String,
    val courseCode: String,
    val courseTitle: String,
    val slots: List<String> = emptyList(),
    val activeSlot: String = "",
    val updatedAtMs: Long = System.currentTimeMillis(),
)

data class ClassScheduleItem(
    val startTime: String,
    val endTime: String,
    val roomBlock: String,
    val roomNumber: String,
    val courseCode: String,
    val title: String,
    val slots: List<String> = emptyList(),
    val activeSlot: String = "",
    val isOngoing: Boolean = false,
    val isClosest: Boolean = false,
)

data class DaySchedule(
    val dayName: String,
    val dateLabel: String,
    val classes: List<ClassScheduleItem>,
)

data class GradeHistoryEntry(
    val regNo: String,
    val semesterSubId: String = "",
    val courseCode: String,
    val courseTitle: String,
    val courseType: String = "",
    val credits: Double = 0.0,
    val grade: String = "",
    val examMonth: String = "",
    val courseDistribution: String = "",
    val updatedAtMs: Long = System.currentTimeMillis(),
)

data class AcademicSummary(
    val regNo: String,
    val cgpa: Double = 0.0,
    val creditsEarned: Double = 0.0,
    val creditsRegistered: Double = 0.0,
    val curriculumCredits: Double = 0.0,
    val sCount: Int = 0,
    val aCount: Int = 0,
    val bCount: Int = 0,
    val cCount: Int = 0,
    val dCount: Int = 0,
    val eCount: Int = 0,
    val fCount: Int = 0,
    val updatedAtMs: Long = System.currentTimeMillis(),
)

data class SemesterResult(
    val regNo: String,
    val semesterSubId: String,
    val gpa: Double,
    val updatedAtMs: Long = System.currentTimeMillis(),
)

data class MarkEntry(
    val id: Long = 0,
    val regNo: String,
    val semesterSubId: String,
    val courseCode: String,
    val courseTitle: String,
    val courseType: String = "",
    val facultyName: String = "",
    val slot: String = "",
    val componentName: String,
    val maxMarks: Double = 0.0,
    val weightagePercent: Double = 0.0,
    val scoredMarks: Double = 0.0,
    val scoredPercentage: Double = 0.0,
    val updatedAtMs: Long = System.currentTimeMillis(),
)
