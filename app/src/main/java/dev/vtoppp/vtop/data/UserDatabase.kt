package dev.vtoppp.vtop.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalTime

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
        if (oldVersion < 6) {
            // v6: per-semester economics for the Cumulative table
            runCatching {
                db.execSQL("ALTER TABLE grade_history ADD COLUMN semester_sub_id TEXT NOT NULL DEFAULT ''")
            }
        }
        if (oldVersion < 7) {
            runCatching {
                db.execSQL("ALTER TABLE academic_summary ADD COLUMN curriculum_credits REAL NOT NULL DEFAULT 0.0")
            }
        }
        if (oldVersion < 8) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS semester_results (
                    reg_no TEXT NOT NULL,
                    semester_sub_id TEXT NOT NULL,
                    gpa REAL,
                    updated_at_ms INTEGER NOT NULL,
                    PRIMARY KEY (reg_no, semester_sub_id)
                );
                """.trimIndent(),
            )
        }
        if (oldVersion < 9) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS attendance_sessions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    reg_no TEXT NOT NULL,
                    semester_sub_id TEXT NOT NULL,
                    course_code TEXT NOT NULL,
                    date TEXT NOT NULL,
                    slot TEXT NOT NULL,
                    status TEXT NOT NULL,
                    updated_at_ms INTEGER NOT NULL,
                    UNIQUE(reg_no, semester_sub_id, course_code, date, slot)
                );
                """.trimIndent(),
            )
        }
        if (oldVersion < 5) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS academic_calendar (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    semester_sub_id TEXT NOT NULL,
                    date TEXT NOT NULL,
                    day_number INTEGER NOT NULL,
                    month_year TEXT NOT NULL,
                    day_of_week TEXT NOT NULL,
                    title TEXT NOT NULL,
                    subtitle TEXT NOT NULL,
                    event_category TEXT NOT NULL,
                    is_holiday INTEGER NOT NULL DEFAULT 0,
                    is_instructional INTEGER NOT NULL DEFAULT 0,
                    is_exam INTEGER NOT NULL DEFAULT 0,
                    updated_at_ms INTEGER NOT NULL,
                    UNIQUE(semester_sub_id, date)
                );
                """.trimIndent(),
            )
        }
    }

    private fun createAcademicTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS attendance_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                reg_no TEXT NOT NULL,
                semester_sub_id TEXT NOT NULL,
                course_code TEXT NOT NULL,
                date TEXT NOT NULL,
                slot TEXT NOT NULL,
                status TEXT NOT NULL,
                updated_at_ms INTEGER NOT NULL,
                UNIQUE(reg_no, semester_sub_id, course_code, date, slot)
            );
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS semester_results (
                reg_no TEXT NOT NULL,
                semester_sub_id TEXT NOT NULL,
                gpa REAL,
                updated_at_ms INTEGER NOT NULL,
                PRIMARY KEY (reg_no, semester_sub_id)
            );
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS academic_calendar (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                semester_sub_id TEXT NOT NULL,
                date TEXT NOT NULL,
                day_number INTEGER NOT NULL,
                month_year TEXT NOT NULL,
                day_of_week TEXT NOT NULL,
                title TEXT NOT NULL,
                subtitle TEXT NOT NULL,
                event_category TEXT NOT NULL,
                is_holiday INTEGER NOT NULL DEFAULT 0,
                is_instructional INTEGER NOT NULL DEFAULT 0,
                is_exam INTEGER NOT NULL DEFAULT 0,
                updated_at_ms INTEGER NOT NULL,
                UNIQUE(semester_sub_id, date)
            );
            """.trimIndent(),
        )
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
                curriculum_credits REAL NOT NULL DEFAULT 0.0,
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
        const val DB_VERSION = 9
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

    private val _semesterResultsFlow = MutableStateFlow<List<SemesterResult>>(emptyList())
    val semesterResultsFlow: StateFlow<List<SemesterResult>> = _semesterResultsFlow.asStateFlow()

    private val _attendanceSessionsFlow = MutableStateFlow<List<AttendanceSession>>(emptyList())
    val attendanceSessionsFlow: StateFlow<List<AttendanceSession>> = _attendanceSessionsFlow.asStateFlow()

    private val _attendanceFlow = MutableStateFlow<List<AttendanceEntry>>(emptyList())
    val attendanceFlow: StateFlow<List<AttendanceEntry>> = _attendanceFlow.asStateFlow()

    private val _calendarEventsFlow = MutableStateFlow<List<CalendarDayEvent>>(emptyList())
    val calendarEventsFlow: StateFlow<List<CalendarDayEvent>> = _calendarEventsFlow.asStateFlow()

    private val _isSyncingFlow = MutableStateFlow(false)
    val isSyncingFlow: StateFlow<Boolean> = _isSyncingFlow.asStateFlow()

    fun setSyncing(syncing: Boolean) {
        _isSyncingFlow.value = syncing
    }

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        if (helper == null) {
            helper = UserDatabaseHelper(context.applicationContext)
        }
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
        sessions: List<AttendanceSession> = emptyList(),
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
                        date = date,
                        attendanceStatus = lookupSessionStatus(date, entry.courseCode, entry.slots, sessions),
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

    private fun lookupSessionStatus(
        date: LocalDate,
        courseCode: String,
        slots: List<String>,
        sessions: List<AttendanceSession>,
    ): String? {
        val dateIso = date.toString()
        val matches = sessions.filter { it.courseCode.equals(courseCode, ignoreCase = true) && it.date == dateIso }
        if (matches.isEmpty()) {
            return if (!date.isAfter(LocalDate.now())) "Not marked" else null
        }
        for (slot in slots) {
            matches.firstOrNull { it.slot.equals(slot, ignoreCase = true) }?.let { return it.status }
        }
        return null
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
                put("curriculum_credits", summary.curriculumCredits)
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
                    curriculumCredits = c.getDouble(c.getColumnIndexOrThrow("curriculum_credits")),
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
            // ponytail: merging is fiddly across semesters; reload everything after each save so cached siblings stay visible
            _marksFlow.value = loadMarks(regNo)
        } finally {
            db.endTransaction()
        }
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

    fun saveSemesterResultGpa(regNo: String, semesterSubId: String, gpa: Double) {
        val db = helper?.writableDatabase ?: return
        db.beginTransaction()
        try {
            val cv = ContentValues().apply {
                put("reg_no", regNo)
                put("semester_sub_id", semesterSubId)
                put("gpa", gpa)
                put("updated_at_ms", System.currentTimeMillis())
            }
            db.insertWithOnConflict("semester_results", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
            _semesterResultsFlow.value = loadSemesterResults(regNo)
        } finally {
            db.endTransaction()
        }
    }

    fun saveAttendanceSessions(regNo: String, semesterSubId: String, sessions: List<AttendanceSession>) {
        if (sessions.isEmpty()) return
        val db = helper?.writableDatabase ?: return
        db.beginTransaction()
        try {
            val courses = sessions.map { it.courseCode }.distinct()
            for (course in courses) {
                db.delete(
                    "attendance_sessions",
                    "reg_no = ? AND semester_sub_id = ? AND course_code = ?",
                    arrayOf(regNo, semesterSubId, course),
                )
            }
            for (s in sessions) {
                val cv = ContentValues().apply {
                    put("reg_no", s.regNo)
                    put("semester_sub_id", s.semesterSubId)
                    put("course_code", s.courseCode)
                    put("date", s.date)
                    put("slot", s.slot)
                    put("status", s.status)
                    put("updated_at_ms", s.updatedAtMs)
                }
                db.insertWithOnConflict("attendance_sessions", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
            _attendanceSessionsFlow.value = loadAttendanceSessions(regNo)
        } finally {
            db.endTransaction()
        }
    }

    fun loadAttendanceSessions(regNo: String): List<AttendanceSession> {
        val db = helper?.readableDatabase ?: return emptyList()
        val list = mutableListOf<AttendanceSession>()
        db.query(
            "attendance_sessions",
            null,
            "reg_no = ?",
            arrayOf(regNo),
            null,
            null,
            "date ASC, slot ASC",
        ).use { c ->
            while (c.moveToNext()) {
                list.add(
                    AttendanceSession(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        regNo = c.getString(c.getColumnIndexOrThrow("reg_no")),
                        semesterSubId = c.getString(c.getColumnIndexOrThrow("semester_sub_id")),
                        courseCode = c.getString(c.getColumnIndexOrThrow("course_code")),
                        date = c.getString(c.getColumnIndexOrThrow("date")),
                        slot = c.getString(c.getColumnIndexOrThrow("slot")),
                        status = c.getString(c.getColumnIndexOrThrow("status")),
                        updatedAtMs = c.getLong(c.getColumnIndexOrThrow("updated_at_ms")),
                    )
                )
            }
        }
        _attendanceSessionsFlow.value = list
        return list
    }

    fun loadSemesterResults(regNo: String): List<SemesterResult> {
        val db = helper?.readableDatabase ?: return emptyList()
        val list = mutableListOf<SemesterResult>()
        db.query("semester_results", null, "reg_no = ?", arrayOf(regNo), null, null, "semester_sub_id ASC").use { c ->
            while (c.moveToNext()) {
                list.add(
                    SemesterResult(
                        regNo = c.getString(c.getColumnIndexOrThrow("reg_no")),
                        semesterSubId = c.getString(c.getColumnIndexOrThrow("semester_sub_id")),
                        gpa = c.getDouble(c.getColumnIndexOrThrow("gpa")),
                        updatedAtMs = c.getLong(c.getColumnIndexOrThrow("updated_at_ms")),
                    )
                )
            }
        }
        _semesterResultsFlow.value = list
        return list
    }

    fun saveCalendarEvents(semesterSubId: String, events: List<CalendarDayEvent>) {
        if (events.isEmpty()) return
        val db = helper?.writableDatabase ?: return
        db.beginTransaction()
        try {
            db.delete("academic_calendar", "semester_sub_id = ?", arrayOf(semesterSubId))
            for (e in events) {
                val cv = ContentValues().apply {
                    put("semester_sub_id", semesterSubId)
                    put("date", e.date)
                    put("day_number", e.dayNumber)
                    put("month_year", e.monthYear)
                    put("day_of_week", e.dayOfWeek)
                    put("title", e.title)
                    put("subtitle", e.subtitle)
                    put("event_category", e.eventCategory)
                    put("is_holiday", if (e.isHoliday) 1 else 0)
                    put("is_instructional", if (e.isInstructional) 1 else 0)
                    put("is_exam", if (e.isExam) 1 else 0)
                    put("updated_at_ms", System.currentTimeMillis())
                }
                db.insertWithOnConflict("academic_calendar", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
            _calendarEventsFlow.value = events
        } finally {
            db.endTransaction()
        }
    }

    fun loadCalendarEvents(semesterSubId: String? = null): List<CalendarDayEvent> {
        val db = helper?.readableDatabase ?: return emptyList()
        val semId = semesterSubId ?: getLatestSemesterId("")
        val selection = if (semId.isNullOrBlank()) null else "semester_sub_id = ?"
        val args = if (semId.isNullOrBlank()) null else arrayOf(semId)
        val list = mutableListOf<CalendarDayEvent>()
        db.query("academic_calendar", null, selection, args, null, null, "date ASC").use { c ->
            while (c.moveToNext()) {
                list.add(
                    CalendarDayEvent(
                        date = c.getString(c.getColumnIndexOrThrow("date")),
                        dayNumber = c.getInt(c.getColumnIndexOrThrow("day_number")),
                        monthYear = c.getString(c.getColumnIndexOrThrow("month_year")),
                        dayOfWeek = c.getString(c.getColumnIndexOrThrow("day_of_week")),
                        title = c.getString(c.getColumnIndexOrThrow("title")),
                        subtitle = c.getString(c.getColumnIndexOrThrow("subtitle")) ?: "",
                        eventCategory = c.getString(c.getColumnIndexOrThrow("event_category")),
                        isHoliday = c.getInt(c.getColumnIndexOrThrow("is_holiday")) == 1,
                        isInstructional = c.getInt(c.getColumnIndexOrThrow("is_instructional")) == 1,
                        isExam = c.getInt(c.getColumnIndexOrThrow("is_exam")) == 1,
                    ),
                )
            }
        }
        if (list.isNotEmpty()) {
            _calendarEventsFlow.value = list
        }
        return list
    }

}
