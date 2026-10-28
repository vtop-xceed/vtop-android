package dev.vtoppp.vtop.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalTime

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
    val sCount: Int = 0,
    val aCount: Int = 0,
    val bCount: Int = 0,
    val cCount: Int = 0,
    val dCount: Int = 0,
    val eCount: Int = 0,
    val fCount: Int = 0,
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

class UserDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS user_profile (
                reg_no TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                programme TEXT,
                branch TEXT,
                school TEXT,
                email TEXT,
                mobile TEXT,
                photo_url TEXT,
                updated_at_ms INTEGER NOT NULL
            );
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS semesters_attended (
                reg_no TEXT NOT NULL,
                semester_sub_id TEXT NOT NULL,
                semester_name TEXT NOT NULL,
                updated_at_ms INTEGER NOT NULL,
                PRIMARY KEY (reg_no, semester_sub_id)
            );
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS student_attendance (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                reg_no TEXT NOT NULL,
                semester_sub_id TEXT NOT NULL,
                course_code TEXT NOT NULL,
                course_title TEXT NOT NULL,
                course_type TEXT,
                slot TEXT,
                faculty_name TEXT,
                attended_classes INTEGER NOT NULL DEFAULT 0,
                total_classes INTEGER NOT NULL DEFAULT 0,
                attendance_percentage REAL NOT NULL DEFAULT 0.0,
                updated_at_ms INTEGER NOT NULL
            );
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS timetable_classes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                reg_no TEXT NOT NULL,
                semester_sub_id TEXT NOT NULL DEFAULT '',
                day_of_week INTEGER NOT NULL,
                start_time TEXT NOT NULL,
                end_time TEXT NOT NULL,
                room_block TEXT NOT NULL,
                room_number TEXT NOT NULL,
                course_code TEXT NOT NULL,
                course_title TEXT NOT NULL,
                slots TEXT NOT NULL,
                active_slot TEXT NOT NULL,
                updated_at_ms INTEGER NOT NULL
            );
            """.trimIndent(),
        )
        createAcademicTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS timetable_classes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    reg_no TEXT NOT NULL,
                    semester_sub_id TEXT NOT NULL DEFAULT '',
                    day_of_week INTEGER NOT NULL,
                    start_time TEXT NOT NULL,
                    end_time TEXT NOT NULL,
                    room_block TEXT NOT NULL,
                    room_number TEXT NOT NULL,
                    course_code TEXT NOT NULL,
                    course_title TEXT NOT NULL,
                    slots TEXT NOT NULL,
                    active_slot TEXT NOT NULL,
                    updated_at_ms INTEGER NOT NULL
                );
                """.trimIndent(),
            )
        }
        if (oldVersion < 3) {
            createAcademicTables(db)
        }
        if (oldVersion < 4) {
            try { db.execSQL("ALTER TABLE timetable_classes ADD COLUMN semester_sub_id TEXT NOT NULL DEFAULT ''") } catch (_: Exception) {}
            try { db.execSQL("ALTER TABLE grade_history ADD COLUMN semester_sub_id TEXT NOT NULL DEFAULT ''") } catch (_: Exception) {}
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS semesters_attended (
                    reg_no TEXT NOT NULL,
                    semester_sub_id TEXT NOT NULL,
                    semester_name TEXT NOT NULL,
                    updated_at_ms INTEGER NOT NULL,
                    PRIMARY KEY (reg_no, semester_sub_id)
                );
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS student_attendance (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    reg_no TEXT NOT NULL,
                    semester_sub_id TEXT NOT NULL,
                    course_code TEXT NOT NULL,
                    course_title TEXT NOT NULL,
                    course_type TEXT,
                    slot TEXT,
                    faculty_name TEXT,
                    attended_classes INTEGER NOT NULL DEFAULT 0,
                    total_classes INTEGER NOT NULL DEFAULT 0,
                    attendance_percentage REAL NOT NULL DEFAULT 0.0,
                    updated_at_ms INTEGER NOT NULL
                );
                """.trimIndent(),
            )
        }
    }

    private fun createAcademicTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS grade_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                reg_no TEXT NOT NULL,
                semester_sub_id TEXT NOT NULL DEFAULT '',
                course_code TEXT NOT NULL,
                course_title TEXT NOT NULL,
                course_type TEXT,
                credits REAL NOT NULL DEFAULT 0.0,
                grade TEXT NOT NULL,
                exam_month TEXT,
                course_distribution TEXT,
                updated_at_ms INTEGER NOT NULL
            );
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS academic_summary (
                reg_no TEXT PRIMARY KEY,
                cgpa REAL NOT NULL DEFAULT 0.0,
                credits_earned REAL NOT NULL DEFAULT 0.0,
                credits_registered REAL NOT NULL DEFAULT 0.0,
                s_count INTEGER NOT NULL DEFAULT 0,
                a_count INTEGER NOT NULL DEFAULT 0,
                b_count INTEGER NOT NULL DEFAULT 0,
                c_count INTEGER NOT NULL DEFAULT 0,
                d_count INTEGER NOT NULL DEFAULT 0,
                e_count INTEGER NOT NULL DEFAULT 0,
                f_count INTEGER NOT NULL DEFAULT 0,
                updated_at_ms INTEGER NOT NULL
            );
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS student_marks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                reg_no TEXT NOT NULL,
                semester_sub_id TEXT NOT NULL,
                course_code TEXT NOT NULL,
                course_title TEXT NOT NULL,
                course_type TEXT,
                faculty_name TEXT,
                slot TEXT,
                component_name TEXT NOT NULL,
                max_marks REAL NOT NULL DEFAULT 0.0,
                weightage_percent REAL NOT NULL DEFAULT 0.0,
                scored_marks REAL NOT NULL DEFAULT 0.0,
                scored_percentage REAL NOT NULL DEFAULT 0.0,
                updated_at_ms INTEGER NOT NULL
            );
            """.trimIndent(),
        )
    }

    companion object {
        const val DB_NAME = "vtop_user_data.db"
        const val DB_VERSION = 4
    }
}

object UserDatabase {
    private var helper: UserDatabaseHelper? = null
    private val _profileFlow = MutableStateFlow<UserProfile?>(null)
    val profileFlow: StateFlow<UserProfile?> = _profileFlow.asStateFlow()

    private val _timetableFlow = MutableStateFlow<List<TimetableEntry>>(emptyList())
    val timetableFlow: StateFlow<List<TimetableEntry>> = _timetableFlow.asStateFlow()

    private val _academicSummaryFlow = MutableStateFlow<AcademicSummary?>(null)
    val academicSummaryFlow: StateFlow<AcademicSummary?> = _academicSummaryFlow.asStateFlow()

    private val _gradeHistoryFlow = MutableStateFlow<List<GradeHistoryEntry>>(emptyList())
    val gradeHistoryFlow: StateFlow<List<GradeHistoryEntry>> = _gradeHistoryFlow.asStateFlow()

    private val _marksFlow = MutableStateFlow<List<MarkEntry>>(emptyList())
    val marksFlow: StateFlow<List<MarkEntry>> = _marksFlow.asStateFlow()

    private val _attendanceFlow = MutableStateFlow<List<AttendanceEntry>>(emptyList())
    val attendanceFlow: StateFlow<List<AttendanceEntry>> = _attendanceFlow.asStateFlow()

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        if (helper == null) {
            helper = UserDatabaseHelper(context.applicationContext)
        }
    }

    fun autoExport() {
        val ctx = appContext ?: return
        exportDatabase(ctx)
        exportHtmlState(ctx)
    }

    fun saveProfile(profile: UserProfile) {
        val db = helper?.writableDatabase ?: return
        val cv = ContentValues().apply {
            put("reg_no", profile.regNo)
            put("name", profile.name)
            put("programme", profile.programme)
            put("branch", profile.branch)
            put("school", profile.school)
            put("email", profile.email)
            put("mobile", profile.mobile)
            put("photo_url", profile.photoUrl)
            put("updated_at_ms", profile.updatedAtMs)
        }
        db.insertWithOnConflict("user_profile", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        _profileFlow.value = profile
        autoExport()
    }

    fun loadProfile(regNo: String): UserProfile? {
        val db = helper?.readableDatabase ?: return null
        val cursor = db.query(
            "user_profile",
            null,
            "reg_no = ?",
            arrayOf(regNo),
            null,
            null,
            null,
            "1",
        )
        return cursor.use { c ->
            if (c.moveToFirst()) {
                val profile = UserProfile(
                    regNo = c.getString(c.getColumnIndexOrThrow("reg_no")),
                    name = c.getString(c.getColumnIndexOrThrow("name")),
                    programme = c.getString(c.getColumnIndexOrThrow("programme")) ?: "",
                    branch = c.getString(c.getColumnIndexOrThrow("branch")) ?: "",
                    school = c.getString(c.getColumnIndexOrThrow("school")) ?: "",
                    email = c.getString(c.getColumnIndexOrThrow("email")) ?: "",
                    mobile = c.getString(c.getColumnIndexOrThrow("mobile")) ?: "",
                    photoUrl = c.getString(c.getColumnIndexOrThrow("photo_url")) ?: "",
                    updatedAtMs = c.getLong(c.getColumnIndexOrThrow("updated_at_ms")),
                )
                _profileFlow.value = profile
                profile
            } else {
                null
            }
        }
    }

    fun saveSemesters(regNo: String, semesters: List<SemesterAttended>) {
        if (semesters.isEmpty()) return
        val db = helper?.writableDatabase ?: return
        db.beginTransaction()
        try {
            for (s in semesters) {
                val cv = ContentValues().apply {
                    put("reg_no", s.regNo)
                    put("semester_sub_id", s.semesterSubId)
                    put("semester_name", s.semesterName)
                    put("updated_at_ms", s.updatedAtMs)
                }
                db.insertWithOnConflict("semesters_attended", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        autoExport()
    }

    fun loadSemesters(regNo: String): List<SemesterAttended> {
        val db = helper?.readableDatabase ?: return emptyList()
        val list = mutableListOf<SemesterAttended>()
        db.query("semesters_attended", null, "reg_no = ?", arrayOf(regNo), null, null, "semester_sub_id DESC").use { c ->
            while (c.moveToNext()) {
                list.add(
                    SemesterAttended(
                        regNo = c.getString(c.getColumnIndexOrThrow("reg_no")),
                        semesterSubId = c.getString(c.getColumnIndexOrThrow("semester_sub_id")),
                        semesterName = c.getString(c.getColumnIndexOrThrow("semester_name")),
                        updatedAtMs = c.getLong(c.getColumnIndexOrThrow("updated_at_ms")),
                    ),
                )
            }
        }
        return list
    }

    fun getLatestSemesterId(regNo: String): String? {
        val db = helper?.readableDatabase ?: return null
        return db.rawQuery(
            "SELECT semester_sub_id FROM semesters_attended WHERE reg_no = ? ORDER BY semester_sub_id DESC LIMIT 1",
            arrayOf(regNo),
        ).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }

    fun saveAttendance(regNo: String, entries: List<AttendanceEntry>) {
        if (entries.isEmpty()) return
        val db = helper?.writableDatabase ?: return
        val semId = entries.first().semesterSubId
        db.beginTransaction()
        try {
            db.delete("student_attendance", "reg_no = ? AND semester_sub_id = ?", arrayOf(regNo, semId))
            for (item in entries) {
                val cv = ContentValues().apply {
                    put("reg_no", item.regNo)
                    put("semester_sub_id", item.semesterSubId)
                    put("course_code", item.courseCode)
                    put("course_title", item.courseTitle)
                    put("course_type", item.courseType)
                    put("slot", item.slot)
                    put("faculty_name", item.facultyName)
                    put("attended_classes", item.attendedClasses)
                    put("total_classes", item.totalClasses)
                    put("attendance_percentage", item.attendancePercentage)
                    put("updated_at_ms", item.updatedAtMs)
                }
                db.insert("student_attendance", null, cv)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        val latestSem = getLatestSemesterId(regNo)
        if (latestSem == null || semId == latestSem) {
            _attendanceFlow.value = entries
        }
        autoExport()
    }

    fun loadAttendance(regNo: String, semesterSubId: String? = null): List<AttendanceEntry> {
        val db = helper?.readableDatabase ?: return emptyList()
        val semId = semesterSubId ?: getLatestSemesterId(regNo)
        val selection = if (semId.isNullOrBlank()) "reg_no = ?" else "reg_no = ? AND semester_sub_id = ?"
        val args = if (semId.isNullOrBlank()) arrayOf(regNo) else arrayOf(regNo, semId)
        val list = mutableListOf<AttendanceEntry>()
        db.query("student_attendance", null, selection, args, null, null, "course_code ASC").use { c ->
            while (c.moveToNext()) {
                list.add(
                    AttendanceEntry(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        regNo = c.getString(c.getColumnIndexOrThrow("reg_no")),
                        semesterSubId = c.getString(c.getColumnIndexOrThrow("semester_sub_id")),
                        courseCode = c.getString(c.getColumnIndexOrThrow("course_code")),
                        courseTitle = c.getString(c.getColumnIndexOrThrow("course_title")),
                        courseType = c.getString(c.getColumnIndexOrThrow("course_type")) ?: "",
                        slot = c.getString(c.getColumnIndexOrThrow("slot")) ?: "",
                        facultyName = c.getString(c.getColumnIndexOrThrow("faculty_name")) ?: "",
                        attendedClasses = c.getInt(c.getColumnIndexOrThrow("attended_classes")),
                        totalClasses = c.getInt(c.getColumnIndexOrThrow("total_classes")),
                        attendancePercentage = c.getDouble(c.getColumnIndexOrThrow("attendance_percentage")),
                        updatedAtMs = c.getLong(c.getColumnIndexOrThrow("updated_at_ms")),
                    ),
                )
            }
        }
        if (list.isNotEmpty()) {
            _attendanceFlow.value = list
        }
        return list
    }

    fun saveTimetable(regNo: String, entries: List<TimetableEntry>, semesterSubId: String? = null) {
        val db = helper?.writableDatabase ?: return
        val semId = semesterSubId ?: entries.firstOrNull()?.semesterSubId?.ifBlank { null }
        db.beginTransaction()
        try {
            if (semId != null) {
                db.delete("timetable_classes", "reg_no = ? AND semester_sub_id = ?", arrayOf(regNo, semId))
            } else {
                db.delete("timetable_classes", "reg_no = ?", arrayOf(regNo))
            }
            for (item in entries) {
                val cv = ContentValues().apply {
                    put("reg_no", item.regNo)
                    put("semester_sub_id", item.semesterSubId.ifBlank { semId.orEmpty() })
                    put("day_of_week", item.dayOfWeek)
                    put("start_time", item.startTime)
                    put("end_time", item.endTime)
                    put("room_block", item.roomBlock)
                    put("room_number", item.roomNumber)
                    put("course_code", item.courseCode)
                    put("course_title", item.courseTitle)
                    put("slots", item.slots.joinToString(","))
                    put("active_slot", item.activeSlot)
                    put("updated_at_ms", item.updatedAtMs)
                }
                db.insert("timetable_classes", null, cv)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        val latestSem = getLatestSemesterId(regNo)
        if (latestSem == null || semId == latestSem) {
            _timetableFlow.value = entries
        }
        autoExport()
    }

    fun loadTimetable(regNo: String, semesterSubId: String? = null): List<TimetableEntry> {
        val db = helper?.readableDatabase ?: return emptyList()
        val semId = semesterSubId ?: getLatestSemesterId(regNo)
        val selection = if (semId.isNullOrBlank()) "reg_no = ?" else "reg_no = ? AND semester_sub_id = ?"
        val args = if (semId.isNullOrBlank()) arrayOf(regNo) else arrayOf(regNo, semId)
        val cursor = db.query(
            "timetable_classes",
            null,
            selection,
            args,
            null,
            null,
            "day_of_week ASC, start_time ASC",
        )
        return cursor.use { c ->
            val list = mutableListOf<TimetableEntry>()
            while (c.moveToNext()) {
                val slotsStr = c.getString(c.getColumnIndexOrThrow("slots")) ?: ""
                list.add(
                    TimetableEntry(
                        regNo = c.getString(c.getColumnIndexOrThrow("reg_no")),
                        semesterSubId = c.getString(c.getColumnIndexOrThrow("semester_sub_id")) ?: "",
                        dayOfWeek = c.getInt(c.getColumnIndexOrThrow("day_of_week")),
                        startTime = c.getString(c.getColumnIndexOrThrow("start_time")),
                        endTime = c.getString(c.getColumnIndexOrThrow("end_time")),
                        roomBlock = c.getString(c.getColumnIndexOrThrow("room_block")),
                        roomNumber = c.getString(c.getColumnIndexOrThrow("room_number")),
                        courseCode = c.getString(c.getColumnIndexOrThrow("course_code")),
                        courseTitle = c.getString(c.getColumnIndexOrThrow("course_title")),
                        slots = if (slotsStr.isBlank()) emptyList() else slotsStr.split(","),
                        activeSlot = c.getString(c.getColumnIndexOrThrow("active_slot")) ?: "",
                        updatedAtMs = c.getLong(c.getColumnIndexOrThrow("updated_at_ms")),
                    ),
                )
            }
            if (list.isNotEmpty()) {
                _timetableFlow.value = list
            }
            list
        }
    }

    /**
     * Builds dynamic day-wise schedule cards from stored timetable entries,
     * calculating ongoing and closest class status relative to current time.
     */
    fun buildScheduleDays(
        entries: List<TimetableEntry>,
        referenceDate: LocalDate = LocalDate.now(),
        referenceTime: LocalTime = LocalTime.now(),
    ): List<DaySchedule> {
        if (entries.isEmpty()) return emptyList()

        val days = mutableListOf<DaySchedule>()
        for (offset in -1..5) {
            val date = referenceDate.plusDays(offset.toLong())
            val dayOfWeekInt = date.dayOfWeek.value // 1 = Mon .. 7 = Sun
            val dayName = when (offset) {
                -1 -> "Yesterday"
                0 -> "Today"
                1 -> "Tomorrow"
                else -> date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
            }
            val dayAbbr = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            val monthAbbr = date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            val dateLabel = "$dayAbbr, $monthAbbr ${date.dayOfMonth}"

            val dayClasses = entries
                .filter { it.dayOfWeek == dayOfWeekInt }
                .sortedBy { it.startTime }
                .map { entry ->
                    var isOngoing = false
                    if (offset == 0) {
                        val start = parseTime(entry.startTime)
                        val end = parseTime(entry.endTime)
                        if (start != null && end != null) {
                            isOngoing = !referenceTime.isBefore(start) && !referenceTime.isAfter(end)
                        }
                    }
                    ClassScheduleItem(
                        startTime = entry.startTime,
                        endTime = entry.endTime,
                        roomBlock = entry.roomBlock,
                        roomNumber = entry.roomNumber,
                        courseCode = entry.courseCode,
                        title = entry.courseTitle,
                        slots = entry.slots,
                        activeSlot = entry.activeSlot,
                        isOngoing = isOngoing,
                        isClosest = false,
                    )
                }.toMutableList()

            // If no class is currently ongoing on Today, mark the closest upcoming class
            if (offset == 0 && dayClasses.none { it.isOngoing }) {
                val closestIdx = dayClasses.indexOfFirst {
                    val start = parseTime(it.startTime)
                    start != null && start.isAfter(referenceTime)
                }
                if (closestIdx >= 0) {
                    dayClasses[closestIdx] = dayClasses[closestIdx].copy(isClosest = true)
                }
            }

            days.add(DaySchedule(dayName = dayName, dateLabel = dateLabel, classes = dayClasses))
        }
        return days
    }

    fun parseTime(str: String): LocalTime? {
        return try {
            val clean = str.trim().uppercase()
            val parts = clean.split(" ")
            val timeParts = parts[0].split(":")
            var hour = timeParts[0].toInt()
            val minute = if (timeParts.size > 1) timeParts[1].toInt() else 0
            if (parts.size > 1) {
                val amPm = parts[1]
                if (amPm == "PM" && hour < 12) hour += 12
                if (amPm == "AM" && hour == 12) hour = 0
            }
            LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        } catch (_: Exception) {
            null
        }
    }

    fun saveGradeHistory(regNo: String, summary: AcademicSummary?, grades: List<GradeHistoryEntry>) {
        val db = helper?.writableDatabase ?: return
        db.beginTransaction()
        try {
            if (summary != null) {
                val cv = ContentValues().apply {
                    put("reg_no", summary.regNo)
                    put("cgpa", summary.cgpa)
                    put("credits_earned", summary.creditsEarned)
                    put("credits_registered", summary.creditsRegistered)
                    put("s_count", summary.sCount)
                    put("a_count", summary.aCount)
                    put("b_count", summary.bCount)
                    put("c_count", summary.cCount)
                    put("d_count", summary.dCount)
                    put("e_count", summary.eCount)
                    put("f_count", summary.fCount)
                    put("updated_at_ms", summary.updatedAtMs)
                }
                db.insertWithOnConflict("academic_summary", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                _academicSummaryFlow.value = summary
            }

            if (grades.isNotEmpty()) {
                db.delete("grade_history", "reg_no = ?", arrayOf(regNo))
                for (g in grades) {
                    val cv = ContentValues().apply {
                        put("reg_no", g.regNo)
                        put("course_code", g.courseCode)
                        put("course_title", g.courseTitle)
                        put("course_type", g.courseType)
                        put("credits", g.credits)
                        put("grade", g.grade)
                        put("exam_month", g.examMonth)
                        put("course_distribution", g.courseDistribution)
                        put("updated_at_ms", g.updatedAtMs)
                    }
                    db.insert("grade_history", null, cv)
                }
                _gradeHistoryFlow.value = grades
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        autoExport()
    }

    fun loadGradeHistory(regNo: String): Pair<AcademicSummary?, List<GradeHistoryEntry>> {
        val db = helper?.readableDatabase ?: return null to emptyList()

        var summary: AcademicSummary? = null
        db.query("academic_summary", null, "reg_no = ?", arrayOf(regNo), null, null, null).use { c ->
            if (c.moveToFirst()) {
                summary = AcademicSummary(
                    regNo = c.getString(c.getColumnIndexOrThrow("reg_no")),
                    cgpa = c.getDouble(c.getColumnIndexOrThrow("cgpa")),
                    creditsEarned = c.getDouble(c.getColumnIndexOrThrow("credits_earned")),
                    creditsRegistered = c.getDouble(c.getColumnIndexOrThrow("credits_registered")),
                    sCount = c.getInt(c.getColumnIndexOrThrow("s_count")),
                    aCount = c.getInt(c.getColumnIndexOrThrow("a_count")),
                    bCount = c.getInt(c.getColumnIndexOrThrow("b_count")),
                    cCount = c.getInt(c.getColumnIndexOrThrow("c_count")),
                    dCount = c.getInt(c.getColumnIndexOrThrow("d_count")),
                    eCount = c.getInt(c.getColumnIndexOrThrow("e_count")),
                    fCount = c.getInt(c.getColumnIndexOrThrow("f_count")),
                    updatedAtMs = c.getLong(c.getColumnIndexOrThrow("updated_at_ms")),
                )
                _academicSummaryFlow.value = summary
            }
        }

        val list = mutableListOf<GradeHistoryEntry>()
        db.query("grade_history", null, "reg_no = ?", arrayOf(regNo), null, null, "course_code ASC").use { c ->
            while (c.moveToNext()) {
                list.add(
                    GradeHistoryEntry(
                        regNo = c.getString(c.getColumnIndexOrThrow("reg_no")),
                        courseCode = c.getString(c.getColumnIndexOrThrow("course_code")),
                        courseTitle = c.getString(c.getColumnIndexOrThrow("course_title")),
                        courseType = c.getString(c.getColumnIndexOrThrow("course_type")) ?: "",
                        credits = c.getDouble(c.getColumnIndexOrThrow("credits")),
                        grade = c.getString(c.getColumnIndexOrThrow("grade")),
                        examMonth = c.getString(c.getColumnIndexOrThrow("exam_month")) ?: "",
                        courseDistribution = c.getString(c.getColumnIndexOrThrow("course_distribution")) ?: "",
                        updatedAtMs = c.getLong(c.getColumnIndexOrThrow("updated_at_ms")),
                    )
                )
            }
        }
        _gradeHistoryFlow.value = list
        return summary to list
    }

    fun saveMarks(regNo: String, marks: List<MarkEntry>) {
        if (marks.isEmpty()) return
        val db = helper?.writableDatabase ?: return
        val semId = marks.first().semesterSubId
        db.beginTransaction()
        try {
            db.delete("student_marks", "reg_no = ? AND semester_sub_id = ?", arrayOf(regNo, semId))
            for (m in marks) {
                val cv = ContentValues().apply {
                    put("reg_no", m.regNo)
                    put("semester_sub_id", m.semesterSubId)
                    put("course_code", m.courseCode)
                    put("course_title", m.courseTitle)
                    put("course_type", m.courseType)
                    put("faculty_name", m.facultyName)
                    put("slot", m.slot)
                    put("component_name", m.componentName)
                    put("max_marks", m.maxMarks)
                    put("weightage_percent", m.weightagePercent)
                    put("scored_marks", m.scoredMarks)
                    put("scored_percentage", m.scoredPercentage)
                    put("updated_at_ms", m.updatedAtMs)
                }
                db.insert("student_marks", null, cv)
            }
            db.setTransactionSuccessful()
            _marksFlow.value = marks
        } finally {
            db.endTransaction()
        }
        autoExport()
    }

    fun loadMarks(regNo: String, semesterSubId: String? = null): List<MarkEntry> {
        val db = helper?.readableDatabase ?: return emptyList()
        val selection = if (semesterSubId.isNullOrBlank()) "reg_no = ?" else "reg_no = ? AND semester_sub_id = ?"
        val args = if (semesterSubId.isNullOrBlank()) arrayOf(regNo) else arrayOf(regNo, semesterSubId)

        val list = mutableListOf<MarkEntry>()
        db.query("student_marks", null, selection, args, null, null, "course_code ASC, component_name ASC").use { c ->
            while (c.moveToNext()) {
                list.add(
                    MarkEntry(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        regNo = c.getString(c.getColumnIndexOrThrow("reg_no")),
                        semesterSubId = c.getString(c.getColumnIndexOrThrow("semester_sub_id")),
                        courseCode = c.getString(c.getColumnIndexOrThrow("course_code")),
                        courseTitle = c.getString(c.getColumnIndexOrThrow("course_title")),
                        courseType = c.getString(c.getColumnIndexOrThrow("course_type")) ?: "",
                        facultyName = c.getString(c.getColumnIndexOrThrow("faculty_name")) ?: "",
                        slot = c.getString(c.getColumnIndexOrThrow("slot")) ?: "",
                        componentName = c.getString(c.getColumnIndexOrThrow("component_name")),
                        maxMarks = c.getDouble(c.getColumnIndexOrThrow("max_marks")),
                        weightagePercent = c.getDouble(c.getColumnIndexOrThrow("weightage_percent")),
                        scoredMarks = c.getDouble(c.getColumnIndexOrThrow("scored_marks")),
                        scoredPercentage = c.getDouble(c.getColumnIndexOrThrow("scored_percentage")),
                        updatedAtMs = c.getLong(c.getColumnIndexOrThrow("updated_at_ms")),
                    )
                )
            }
        }
        _marksFlow.value = list
        return list
    }

    /**
     * Copies the active SQLite database to /sdcard/Download/ for easy ADB access.
     */
    fun exportDatabase(context: Context): File? {
        val dbFile = context.getDatabasePath(UserDatabaseHelper.DB_NAME)
        if (!dbFile.exists()) return null
        return try {
            val targets = listOf(
                File("/sdcard/Download/${UserDatabaseHelper.DB_NAME}"),
                File(context.getExternalFilesDir(null), UserDatabaseHelper.DB_NAME),
            )
            for (target in targets) {
                target.parentFile?.mkdirs()
                FileInputStream(dbFile).use { input ->
                    FileOutputStream(target).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            try {
                val cacheDir = File(context.filesDir, "section-cache")
                if (cacheDir.exists()) {
                    val outDir = File("/sdcard/Download/section_cache")
                    outDir.mkdirs()
                    cacheDir.listFiles()?.forEach { file ->
                        file.copyTo(File(outDir, file.name), overwrite = true)
                    }
                }
                val logFile = File(context.filesDir, "vtop-debug.log")
                if (logFile.exists()) {
                    logFile.copyTo(File("/sdcard/Download/vtop-debug.log"), overwrite = true)
                }
            } catch (_: Exception) {}
            targets.firstOrNull { it.exists() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Generates a clean, standalone HTML file displaying the complete contents of all SQLite tables,
     * including per-semester breakdowns for timetable, attendance, marks, and grades.
     */
    fun exportHtmlState(context: Context): File? {
        val db = helper?.readableDatabase ?: return null
        val html = buildString {
            append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>VTOP Database State</title>")
            append("<style>")
            append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background: #0F172A; color: #F1F5F9; margin: 0; padding: 24px; }")
            append("h1 { color: #38BDF8; margin-bottom: 4px; font-size: 26px; }")
            append(".meta { color: #94A3B8; font-size: 14px; margin-bottom: 24px; }")
            append(".card { background: #1E293B; border: 1px solid #334155; border-radius: 12px; padding: 20px; margin-bottom: 24px; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.3); }")
            append(".sem-card { background: #1E293B; border-left: 4px solid #38BDF8; border-radius: 12px; padding: 20px; margin-bottom: 28px; box-shadow: 0 6px 10px -2px rgba(0,0,0,0.4); }")
            append("h2 { color: #F8FAFC; font-size: 18px; margin-top: 0; margin-bottom: 14px; border-bottom: 1px solid #334155; padding-bottom: 8px; }")
            append("h3 { color: #38BDF8; font-size: 15px; margin-top: 18px; margin-bottom: 8px; }")
            append("table { width: 100%; border-collapse: collapse; font-size: 13px; margin-bottom: 12px; }")
            append("th { text-align: left; padding: 8px 12px; background: #0F172A; color: #94A3B8; font-weight: 600; }")
            append("td { padding: 8px 12px; border-bottom: 1px solid #334155; }")
            append("tr:hover { background: #273549; }")
            append(".pill { display: inline-block; padding: 2px 8px; border-radius: 6px; font-weight: 600; font-size: 12px; }")
            append(".pill-grade { background: #6D28D9; color: #EDE9FE; }")
            append(".pill-green { background: #065F46; color: #D1FAE5; }")
            append(".pill-red { background: #991B1B; color: #FEE2E2; }")
            append(".pill-sem { background: #0284C7; color: #E0F2FE; padding: 3px 10px; border-radius: 8px; font-size: 14px; font-weight: bold; }")
            append(".empty { color: #64748B; font-style: italic; padding: 8px 0; }")
            append("</style></head><body>")

            append("<h1>VTOP Local Database Inspector</h1>")
            append("<div class='meta'>Generated from phone database (${UserDatabaseHelper.DB_NAME}) &bull; DB Version ${UserDatabaseHelper.DB_VERSION} &bull; Generated: ${java.util.Date()}</div>")

            // 1. User Profile
            append("<div class='card'><h2>1. User Profile (user_profile)</h2>")
            db.rawQuery("SELECT * FROM user_profile", null).use { c ->
                if (c.moveToFirst()) {
                    append("<table><thead><tr>")
                    for (name in c.columnNames) append("<th>$name</th>")
                    append("</tr></thead><tbody><tr>")
                    for (i in 0 until c.columnCount) append("<td>${c.getString(i) ?: ""}</td>")
                    append("</tr></tbody></table>")
                } else {
                    append("<div class='empty'>No profile cached yet</div>")
                }
            }
            append("</div>")

            // 2. Academic Summary
            append("<div class='card'><h2>2. Academic Summary (academic_summary)</h2>")
            db.rawQuery("SELECT * FROM academic_summary", null).use { c ->
                if (c.moveToFirst()) {
                    append("<table><thead><tr>")
                    for (name in c.columnNames) append("<th>$name</th>")
                    append("</tr></thead><tbody><tr>")
                    for (i in 0 until c.columnCount) append("<td>${c.getString(i) ?: ""}</td>")
                    append("</tr></tbody></table>")
                } else {
                    append("<div class='empty'>No academic summary cached yet</div>")
                }
            }
            append("</div>")

            // 3. Semesters Attended
            val semestersList = mutableListOf<Pair<String, String>>()
            append("<div class='card'><h2>3. Semesters Attended (semesters_attended)</h2>")
            db.rawQuery("SELECT semester_sub_id, semester_name FROM semesters_attended ORDER BY semester_sub_id DESC", null).use { c ->
                if (c.count > 0) {
                    append("<table><thead><tr><th>Semester Sub ID</th><th>Semester Name</th></tr></thead><tbody>")
                    while (c.moveToNext()) {
                        val subId = c.getString(0)
                        val sName = c.getString(1)
                        semestersList.add(subId to sName)
                        append("<tr><td><span class='pill pill-sem'>$subId</span></td><td>$sName</td></tr>")
                    }
                    append("</tbody></table>")
                } else {
                    append("<div class='empty'>No semesters attended recorded yet</div>")
                }
            }
            append("</div>")

            // If semestersList is empty, discover from distinct semester_sub_id across tables
            if (semestersList.isEmpty()) {
                val foundSemIds = mutableSetOf<String>()
                listOf("timetable_classes", "student_attendance", "student_marks").forEach { tbl ->
                    try {
                        db.rawQuery("SELECT DISTINCT semester_sub_id FROM $tbl WHERE semester_sub_id != ''", null).use { c ->
                            while (c.moveToNext()) foundSemIds.add(c.getString(0))
                        }
                    } catch (_: Exception) {}
                }
                foundSemIds.sortedDescending().forEach { semId ->
                    semestersList.add(semId to semId)
                }
            }

            // 4. Cumulative Grade History
            append("<div class='card'><h2>4. Cumulative Grade History (grade_history)</h2>")
            db.rawQuery("SELECT * FROM grade_history ORDER BY course_code ASC", null).use { c ->
                if (c.count > 0) {
                    append("<table><thead><tr>")
                    for (name in c.columnNames) append("<th>$name</th>")
                    append("</tr></thead><tbody>")
                    while (c.moveToNext()) {
                        append("<tr>")
                        for (i in 0 until c.columnCount) {
                            val v = c.getString(i) ?: ""
                            if (c.getColumnName(i) == "grade") {
                                append("<td><span class='pill pill-grade'>$v</span></td>")
                            } else {
                                append("<td>$v</td>")
                            }
                        }
                        append("</tr>")
                    }
                    append("</tbody></table>")
                } else {
                    append("<div class='empty'>No grade history cached yet</div>")
                }
            }
            append("</div>")

            // 5. Per-Semester Breakdown: Timetable, Attendance, Marks, Grades
            append("<h2>5. Per-Semester Breakdown</h2>")
            for ((semId, semName) in semestersList) {
                append("<div class='sem-card'>")
                append("<h2>Semester: <span class='pill pill-sem'>$semId</span> - $semName</h2>")

                // Timetable for semester
                append("<h3>Timetable</h3>")
                db.rawQuery("SELECT day_of_week, start_time, end_time, room_block, room_number, course_code, course_title, slots, active_slot FROM timetable_classes WHERE semester_sub_id = ? ORDER BY day_of_week ASC, start_time ASC", arrayOf(semId)).use { c ->
                    if (c.count > 0) {
                        append("<table><thead><tr>")
                        for (name in c.columnNames) append("<th>$name</th>")
                        append("</tr></thead><tbody>")
                        while (c.moveToNext()) {
                            append("<tr>")
                            for (i in 0 until c.columnCount) append("<td>${c.getString(i) ?: ""}</td>")
                            append("</tr>")
                        }
                        append("</tbody></table>")
                    } else {
                        append("<div class='empty'>No timetable classes for $semId</div>")
                    }
                }

                // Attendance for semester
                append("<h3>Attendance</h3>")
                db.rawQuery("SELECT course_code, course_title, course_type, slot, attended_classes, total_classes, attendance_percentage FROM student_attendance WHERE semester_sub_id = ? ORDER BY course_code ASC", arrayOf(semId)).use { c ->
                    if (c.count > 0) {
                        append("<table><thead><tr><th>Course Code</th><th>Course Title</th><th>Type</th><th>Slot</th><th>Attended</th><th>Total</th><th>Attendance %</th></tr></thead><tbody>")
                        while (c.moveToNext()) {
                            val code = c.getString(0)
                            val title = c.getString(1)
                            val type = c.getString(2) ?: ""
                            val slot = c.getString(3) ?: ""
                            val att = c.getInt(4)
                            val tot = c.getInt(5)
                            val pct = c.getDouble(6)
                            val pillClass = if (pct >= 75.0) "pill-green" else "pill-red"
                            append("<tr><td><b>$code</b></td><td>$title</td><td>$type</td><td>$slot</td><td>$att</td><td>$tot</td><td><span class='pill $pillClass'>$pct%</span></td></tr>")
                        }
                        append("</tbody></table>")
                    } else {
                        append("<div class='empty'>No attendance records for $semId</div>")
                    }
                }

                // Marks for semester
                append("<h3>Marks</h3>")
                db.rawQuery("SELECT course_code, course_title, component_name, max_marks, weightage_percent, scored_marks, scored_percentage FROM student_marks WHERE semester_sub_id = ? ORDER BY course_code ASC, component_name ASC", arrayOf(semId)).use { c ->
                    if (c.count > 0) {
                        append("<table><thead><tr><th>Course Code</th><th>Course Title</th><th>Component</th><th>Max Marks</th><th>Weightage %</th><th>Scored</th><th>%</th></tr></thead><tbody>")
                        while (c.moveToNext()) {
                            append("<tr>")
                            append("<td><b>${c.getString(0)}</b></td>")
                            append("<td>${c.getString(1)}</td>")
                            append("<td>${c.getString(2)}</td>")
                            append("<td>${c.getDouble(3)}</td>")
                            append("<td>${c.getDouble(4)}%</td>")
                            append("<td><b>${c.getDouble(5)}</b></td>")
                            append("<td>${c.getDouble(6)}%</td>")
                            append("</tr>")
                        }
                        append("</tbody></table>")
                    } else {
                        append("<div class='empty'>No marks records for $semId</div>")
                    }
                }

                append("</div>") // sem-card
            }

            append("</body></html>")
        }

        return try {
            val targets = listOf(
                File("/sdcard/Download/vtop_db_state.html"),
                File(context.getExternalFilesDir(null), "vtop_db_state.html"),
            )
            for (target in targets) {
                target.parentFile?.mkdirs()
                target.writeText(html)
            }
            targets.firstOrNull { it.exists() }
        } catch (_: Exception) {
            null
        }
    }
}
