package dev.vtoppp.vtop.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

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
                course_id TEXT NOT NULL DEFAULT '',
                course_type_a TEXT NOT NULL DEFAULT '',
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
        createIndexes(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            step(db) {
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
        }
        if (oldVersion < 3) {
            step(db) { createAcademicTables(db) }
        }
        if (oldVersion < 4) {
            step(db) {
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
        if (oldVersion < 6) {
            // v6: per-semester economics for the Cumulative table
            step(db) {
                runCatching {
                    db.execSQL("ALTER TABLE grade_history ADD COLUMN semester_sub_id TEXT NOT NULL DEFAULT ''")
                }
            }
        }
        if (oldVersion < 7) {
            step(db) {
                runCatching {
                    db.execSQL("ALTER TABLE academic_summary ADD COLUMN curriculum_credits REAL NOT NULL DEFAULT 0.0")
                }
            }
        }
        if (oldVersion < 8) {
            step(db) {
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
        }
        if (oldVersion < 9) {
            step(db) {
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
        }
        if (oldVersion < 10) {
            step(db) {
                // Clean up un-attended semesters previously scraped from university-wide mark view
                runCatching {
                    db.execSQL(
                        """
                        DELETE FROM semesters_attended
                        WHERE (reg_no, semester_sub_id) NOT IN (
                            SELECT DISTINCT reg_no, semester_sub_id FROM student_attendance
                        ) AND EXISTS (SELECT 1 FROM student_attendance WHERE student_attendance.reg_no = semesters_attended.reg_no)
                        """.trimIndent(),
                    )
                }
            }
        }
        if (oldVersion < 5) {
            step(db) {
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
        if (oldVersion < 11) {
            step(db) { createIndexes(db) }
        }
        if (oldVersion < 12) {
            step(db) {
                runCatching { db.execSQL("ALTER TABLE student_attendance ADD COLUMN course_id TEXT NOT NULL DEFAULT ''") }
                runCatching { db.execSQL("ALTER TABLE student_attendance ADD COLUMN course_type_a TEXT NOT NULL DEFAULT ''") }
            }
        }
    }

    private fun step(db: SQLiteDatabase, action: () -> Unit) {
        db.beginTransaction()
        try {
            action()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun createIndexes(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_student_attendance_reg ON student_attendance(reg_no, semester_sub_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_timetable_classes_reg ON timetable_classes(reg_no, semester_sub_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_student_marks_reg ON student_marks(reg_no, semester_sub_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_grade_history_reg ON grade_history(reg_no, semester_sub_id)")
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
        const val DB_VERSION = 12
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

    private val _semestersFlow = MutableStateFlow<List<SemesterAttended>>(emptyList())
    val semestersFlow: StateFlow<List<SemesterAttended>> = _semestersFlow.asStateFlow()

    private val _isSyncingFlow = MutableStateFlow(false)
    val isSyncingFlow: StateFlow<Boolean> = _isSyncingFlow.asStateFlow()

    private val _dbVersionFlow = MutableStateFlow(0L)
    val dbVersionFlow: StateFlow<Long> = _dbVersionFlow.asStateFlow()

    private val LEGACY_BOGUS_START_TIMES = setOf("08:00", "09:00", "11:00", "14:00", "15:00", "16:00")
    private val DAY_CUTOFF_TIME: LocalTime = LocalTime.of(22, 0)
    private val DAY_NAMES = arrayOf("", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    private val DAY_ABBRS = arrayOf("", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val MONTH_ABBRS = arrayOf("", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    private inline fun SQLiteDatabase.inTransaction(block: () -> Unit) {
        beginTransaction()
        try {
            block()
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }

    private fun bumpRevision() {
        _dbVersionFlow.value += 1L
    }

    fun setSyncing(syncing: Boolean) {
        _isSyncingFlow.value = syncing
    }

    private var appContext: Context? = null

    fun isOnline(): Boolean {
        val ctx = appContext ?: return true
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun getLastKnownRegNo(): String? {
        val db = helper?.readableDatabase ?: return null
        return try {
            val fromProfile = db.rawQuery(
                "SELECT reg_no FROM user_profile ORDER BY updated_at_ms DESC LIMIT 1",
                null,
            ).use { c ->
                if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null
            }
            if (fromProfile != null) return fromProfile

            db.rawQuery(
                "SELECT reg_no FROM semesters_attended LIMIT 1",
                null,
            ).use { c ->
                if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null
            }
        } catch (e: Exception) {
            AppLog.d("db: getLastKnownRegNo failed: ${e.message}")
            null
        }
    }

    fun loadAllCached(regNo: String) {
        if (regNo.isBlank()) return
        loadProfile(regNo)
        loadSemesters(regNo)
        loadTimetable(regNo)
        loadAttendance(regNo)
        loadAttendanceSessions(regNo)
        loadGradeHistory(regNo)
        loadMarks(regNo)
        loadSemesterResults(regNo)
        loadCalendarEvents()
    }

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
        db.inTransaction {
            db.delete("semesters_attended", "reg_no = ?", arrayOf(regNo))
            db.compileStatement(
                "INSERT OR REPLACE INTO semesters_attended (reg_no, semester_sub_id, semester_name, updated_at_ms) VALUES (?, ?, ?, ?)",
            ).use { stmt ->
                for (s in semesters) {
                    stmt.clearBindings()
                    stmt.bindString(1, s.regNo)
                    stmt.bindString(2, s.semesterSubId)
                    stmt.bindString(3, s.semesterName)
                    stmt.bindLong(4, s.updatedAtMs)
                    stmt.executeInsert()
                }
            }
        }
        _semestersFlow.value = semesters
    }

    fun loadSemesters(regNo: String): List<SemesterAttended> {
        val db = helper?.readableDatabase ?: return emptyList()
        val list = mutableListOf<SemesterAttended>()
        db.query("semesters_attended", null, "reg_no = ?", arrayOf(regNo), null, null, "semester_sub_id DESC").use { c ->
            val regNoIdx = c.getColumnIndexOrThrow("reg_no")
            val semIdIdx = c.getColumnIndexOrThrow("semester_sub_id")
            val semNameIdx = c.getColumnIndexOrThrow("semester_name")
            val updatedAtIdx = c.getColumnIndexOrThrow("updated_at_ms")
            while (c.moveToNext()) {
                list.add(
                    SemesterAttended(
                        regNo = c.getString(regNoIdx),
                        semesterSubId = c.getString(semIdIdx),
                        semesterName = c.getString(semNameIdx),
                        updatedAtMs = c.getLong(updatedAtIdx),
                    ),
                )
            }
        }
        if (list.isNotEmpty()) {
            _semestersFlow.value = list
        }
        return list
    }

    fun getAttendanceSemesterSubIds(regNo: String): Set<String> {
        val db = helper?.readableDatabase ?: return emptySet()
        val set = mutableSetOf<String>()
        db.rawQuery(
            "SELECT DISTINCT semester_sub_id FROM student_attendance WHERE reg_no = ?",
            arrayOf(regNo),
        ).use { c ->
            while (c.moveToNext()) {
                val sem = c.getString(0)
                if (!sem.isNullOrBlank()) set.add(sem)
            }
        }
        return set
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
        db.inTransaction {
            db.delete("student_attendance", "reg_no = ? AND semester_sub_id = ?", arrayOf(regNo, semId))
            db.compileStatement(
                """
                INSERT INTO student_attendance (
                    reg_no, semester_sub_id, course_code, course_title, course_type,
                    slot, faculty_name, course_id, course_type_a,
                    attended_classes, total_classes, attendance_percentage, updated_at_ms
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { stmt ->
                for (item in entries) {
                    stmt.clearBindings()
                    stmt.bindString(1, item.regNo)
                    stmt.bindString(2, item.semesterSubId)
                    stmt.bindString(3, item.courseCode)
                    stmt.bindString(4, item.courseTitle)
                    stmt.bindString(5, item.courseType)
                    stmt.bindString(6, item.slot)
                    stmt.bindString(7, item.facultyName)
                    stmt.bindString(8, item.courseId)
                    stmt.bindString(9, item.courseTypeA)
                    stmt.bindLong(10, item.attendedClasses.toLong())
                    stmt.bindLong(11, item.totalClasses.toLong())
                    stmt.bindDouble(12, item.attendancePercentage)
                    stmt.bindLong(13, item.updatedAtMs)
                    stmt.executeInsert()
                }
            }
        }
        val latestSem = getLatestSemesterId(regNo)
        if (latestSem == null || semId == latestSem) {
            _attendanceFlow.value = entries
        }
        bumpRevision()
    }

    fun loadAttendance(regNo: String, semesterSubId: String? = null): List<AttendanceEntry> {
        val db = helper?.readableDatabase ?: return emptyList()
        val latestSem = getLatestSemesterId(regNo)
        val semId = semesterSubId ?: latestSem
        val selection = if (semId.isNullOrBlank()) "reg_no = ?" else "reg_no = ? AND semester_sub_id = ?"
        val args = if (semId.isNullOrBlank()) arrayOf(regNo) else arrayOf(regNo, semId)
        val list = mutableListOf<AttendanceEntry>()
        db.query("student_attendance", null, selection, args, null, null, "course_code ASC").use { c ->
            val idIdx = c.getColumnIndexOrThrow("id")
            val regNoIdx = c.getColumnIndexOrThrow("reg_no")
            val semIdIdx = c.getColumnIndexOrThrow("semester_sub_id")
            val codeIdx = c.getColumnIndexOrThrow("course_code")
            val titleIdx = c.getColumnIndexOrThrow("course_title")
            val typeIdx = c.getColumnIndexOrThrow("course_type")
            val slotIdx = c.getColumnIndexOrThrow("slot")
            val facultyIdx = c.getColumnIndexOrThrow("faculty_name")
            val courseIdIdx = c.getColumnIndex("course_id")
            val courseTypeAIdx = c.getColumnIndex("course_type_a")
            val attendedIdx = c.getColumnIndexOrThrow("attended_classes")
            val totalIdx = c.getColumnIndexOrThrow("total_classes")
            val pctIdx = c.getColumnIndexOrThrow("attendance_percentage")
            val updatedAtIdx = c.getColumnIndexOrThrow("updated_at_ms")
            while (c.moveToNext()) {
                list.add(
                    AttendanceEntry(
                        id = c.getLong(idIdx),
                        regNo = c.getString(regNoIdx),
                        semesterSubId = c.getString(semIdIdx),
                        courseCode = c.getString(codeIdx),
                        courseTitle = c.getString(titleIdx),
                        courseType = c.getString(typeIdx) ?: "",
                        slot = c.getString(slotIdx) ?: "",
                        facultyName = c.getString(facultyIdx) ?: "",
                        courseId = if (courseIdIdx >= 0) c.getString(courseIdIdx) ?: "" else "",
                        courseTypeA = if (courseTypeAIdx >= 0) c.getString(courseTypeAIdx) ?: "" else "",
                        attendedClasses = c.getInt(attendedIdx),
                        totalClasses = c.getInt(totalIdx),
                        attendancePercentage = c.getDouble(pctIdx),
                        updatedAtMs = c.getLong(updatedAtIdx),
                    ),
                )
            }
        }
        if (latestSem == null || semId == latestSem) {
            _attendanceFlow.value = list
        }
        return list
    }

    fun loadAllFaculties(regNo: String): Map<String, String> {
        val db = helper?.readableDatabase ?: return emptyMap()
        val map = mutableMapOf<String, String>()
        db.query(
            "student_attendance",
            arrayOf("course_code", "faculty_name"),
            "reg_no = ? AND faculty_name != ''",
            arrayOf(regNo),
            null,
            null,
            null,
        ).use { c ->
            while (c.moveToNext()) {
                val code = c.getString(0)?.trim()?.uppercase().orEmpty()
                val name = c.getString(1)?.trim().orEmpty()
                if (code.isNotBlank() && name.isNotBlank() && !map.containsKey(code)) {
                    map[code] = name
                }
            }
        }
        return map
    }

    fun saveTimetable(regNo: String, entries: List<TimetableEntry>, semesterSubId: String? = null) {
        val db = helper?.writableDatabase ?: return
        val semId = semesterSubId ?: entries.firstOrNull()?.semesterSubId?.ifBlank { null }
        db.inTransaction {
            if (semId != null) {
                db.delete("timetable_classes", "reg_no = ? AND semester_sub_id = ?", arrayOf(regNo, semId))
            } else {
                db.delete("timetable_classes", "reg_no = ?", arrayOf(regNo))
            }
            if (entries.isNotEmpty()) {
                db.compileStatement(
                    """
                    INSERT INTO timetable_classes (
                        reg_no, semester_sub_id, day_of_week, start_time, end_time,
                        room_block, room_number, course_code, course_title,
                        slots, active_slot, updated_at_ms
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use { stmt ->
                    for (item in entries) {
                        stmt.clearBindings()
                        stmt.bindString(1, item.regNo)
                        stmt.bindString(2, item.semesterSubId.ifBlank { semId.orEmpty() })
                        stmt.bindLong(3, item.dayOfWeek.toLong())
                        stmt.bindString(4, item.startTime)
                        stmt.bindString(5, item.endTime)
                        stmt.bindString(6, item.roomBlock)
                        stmt.bindString(7, item.roomNumber)
                        stmt.bindString(8, item.courseCode)
                        stmt.bindString(9, item.courseTitle)
                        stmt.bindString(10, item.slots.joinToString(","))
                        stmt.bindString(11, item.activeSlot)
                        stmt.bindLong(12, item.updatedAtMs)
                        stmt.executeInsert()
                    }
                }
            }
        }
        val latestSem = getLatestSemesterId(regNo)
        if (latestSem == null || semId == latestSem) {
            _timetableFlow.value = entries
        }
        bumpRevision()
    }

    fun loadTimetable(regNo: String, semesterSubId: String? = null): List<TimetableEntry> {
        val db = helper?.readableDatabase ?: return emptyList()
        val latestSem = getLatestSemesterId(regNo)
        val semId = semesterSubId ?: latestSem
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
            val regNoIdx = c.getColumnIndexOrThrow("reg_no")
            val semIdIdx = c.getColumnIndexOrThrow("semester_sub_id")
            val dayIdx = c.getColumnIndexOrThrow("day_of_week")
            val startIdx = c.getColumnIndexOrThrow("start_time")
            val endIdx = c.getColumnIndexOrThrow("end_time")
            val blockIdx = c.getColumnIndexOrThrow("room_block")
            val roomIdx = c.getColumnIndexOrThrow("room_number")
            val codeIdx = c.getColumnIndexOrThrow("course_code")
            val titleIdx = c.getColumnIndexOrThrow("course_title")
            val slotsIdx = c.getColumnIndexOrThrow("slots")
            val activeSlotIdx = c.getColumnIndexOrThrow("active_slot")
            val updatedAtIdx = c.getColumnIndexOrThrow("updated_at_ms")
            while (c.moveToNext()) {
                val slotsStr = c.getString(slotsIdx) ?: ""
                val rawBlock = c.getString(blockIdx) ?: ""
                val rawRoom = c.getString(roomIdx) ?: ""
                val isCorruptVenue = rawRoom.length > 12 || rawRoom.contains("Lecture", ignoreCase = true)
                val block = if (isCorruptVenue) "" else rawBlock
                val room = if (isCorruptVenue) "" else rawRoom
                val rawStart = c.getString(startIdx)
                val rawEnd = c.getString(endIdx)
                val rawDay = c.getInt(dayIdx)
                val activeSlot = c.getString(activeSlotIdx) ?: ""
                // ponytail: remap legacy cached entries with bogus 50-min times to authentic FFCS slot timings
                val slotTiming = if (rawStart in LEGACY_BOGUS_START_TIMES && activeSlot.isNotBlank()) {
                    SlotTimings.getTimings(activeSlot).firstOrNull()
                } else null
                val dayOfWeek = slotTiming?.dayOfWeek ?: rawDay
                val startTime = slotTiming?.startTime ?: rawStart
                val endTime = slotTiming?.endTime ?: rawEnd
                list.add(
                    TimetableEntry(
                        regNo = c.getString(regNoIdx),
                        semesterSubId = c.getString(semIdIdx) ?: "",
                        dayOfWeek = dayOfWeek,
                        startTime = startTime,
                        endTime = endTime,
                        roomBlock = block,
                        roomNumber = room,
                        courseCode = c.getString(codeIdx),
                        courseTitle = c.getString(titleIdx),
                        slots = if (slotsStr.isBlank()) emptyList() else slotsStr.split(","),
                        activeSlot = activeSlot,
                        updatedAtMs = c.getLong(updatedAtIdx),
                    ),
                )
            }
            if (latestSem == null || semId == latestSem) {
                _timetableFlow.value = list
            }
            list
        }
    }

    private class ParsedEntry(
        val entry: TimetableEntry,
        val start: LocalTime?,
        val end: LocalTime?,
    )

    /**
     * Builds dynamic day-wise schedule cards from stored timetable entries,
     * stamping the class that is running at [referenceTime].
     */
    fun buildScheduleDays(
        entries: List<TimetableEntry>,
        sessions: List<AttendanceSession> = emptyList(),
        referenceDate: LocalDate = LocalDate.now(),
        referenceTime: LocalTime = LocalTime.now(),
    ): List<DaySchedule> {
        if (entries.isEmpty()) return emptyList()

        // Group and sort entries once per dayOfWeek (1..7), parsing each entry's times once.
        val byDay = arrayOfNulls<MutableList<ParsedEntry>>(8)
        for (entry in entries) {
            val dow = entry.dayOfWeek
            if (dow !in 1..7) continue
            val bucket = byDay[dow] ?: ArrayList<ParsedEntry>().also { byDay[dow] = it }
            bucket.add(ParsedEntry(entry, parseTime(entry.startTime), parseTime(entry.endTime)))
        }
        for (dow in 1..7) {
            byDay[dow]?.sortBy { it.start?.toSecondOfDay() ?: Int.MAX_VALUE }
        }

        // Index only sessions falling in the 7-day window so lookup is O(1) per class instead of O(N*M).
        val rangeStart = referenceDate.minusDays(1).toString()
        val rangeEnd = referenceDate.plusDays(5).toString()
        val sessionIndex: Map<String, Map<String, List<AttendanceSession>>> = if (sessions.isEmpty()) {
            emptyMap()
        } else {
            HashMap<String, HashMap<String, MutableList<AttendanceSession>>>(8).apply {
                for (s in sessions) {
                    if (s.date !in rangeStart..rangeEnd) continue
                    val byCourse = getOrPut(s.date) { HashMap() }
                    byCourse.getOrPut(s.courseCode.uppercase(Locale.US)) { ArrayList(2) }.add(s)
                }
            }
        }

        // Find the single ongoing (or next upcoming) class across Today..+5 in chronological order.
        val now = referenceDate.atTime(referenceTime)
        var runningOffset = Int.MIN_VALUE
        var runningIdx = -1
        var nextOffset = Int.MIN_VALUE
        var nextIdx = -1
        for (offset in 0..5) {
            val date = referenceDate.plusDays(offset.toLong())
            val bucket = byDay[date.dayOfWeek.value] ?: continue
            for (idx in bucket.indices) {
                val pe = bucket[idx]
                val start = pe.start?.let { date.atTime(it) } ?: continue
                val end = pe.end?.let { date.atTime(it) }?.let { if (it <= start) it.plusDays(1) else it }
                if (end != null && !now.isBefore(start) && now.isBefore(end)) {
                    runningOffset = offset
                    runningIdx = idx
                    break
                }
                if (nextIdx < 0 && now.isBefore(start)) {
                    nextOffset = offset
                    nextIdx = idx
                }
            }
            if (runningIdx >= 0) break
        }

        val days = ArrayList<DaySchedule>(7)
        for (offset in -1..5) {
            val date = referenceDate.plusDays(offset.toLong())
            val dow = date.dayOfWeek.value // 1 = Mon .. 7 = Sun
            val dayName = when (offset) {
                -1 -> "Yesterday"
                0 -> "Today"
                1 -> "Tomorrow"
                else -> DAY_NAMES[dow]
            }
            val dateLabel = "${DAY_ABBRS[dow]}, ${MONTH_ABBRS[date.monthValue]} ${date.dayOfMonth}"
            val dateIso = date.toString()
            val bucket = byDay[dow]
            val dayClasses = if (bucket.isNullOrEmpty()) {
                emptyList()
            } else {
                val daySessions = sessionIndex[dateIso]
                ArrayList<ClassScheduleItem>(bucket.size).apply {
                    for (idx in bucket.indices) {
                        val entry = bucket[idx].entry
                        val isOngoing = offset == runningOffset && idx == runningIdx
                        val isNext = runningIdx < 0 && offset == nextOffset && idx == nextIdx
                        add(
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
                                isNext = isNext,
                                date = date,
                                attendanceStatus = lookupSessionStatus(date, referenceDate, entry.courseCode, entry.slots, daySessions),
                            ),
                        )
                    }
                }
            }

            days.add(DaySchedule(dayName = dayName, dateLabel = dateLabel, classes = dayClasses))
        }
        return days
    }

    /**
     * Index of the day the timeline opens on. Today stays on screen until the
     * fixed cutoff of 22:00 (10:00 PM); only then does the timeline move to the
     * next day that has classes. Never yesterday.
     */
    fun nearestClassDayIndex(days: List<DaySchedule>, now: LocalDateTime = LocalDateTime.now()): Int {
        if (days.isEmpty()) return 0
        val todayIndex = days.indexOfFirst { it.dayName == "Today" }.coerceAtLeast(0)
        val today = days.getOrNull(todayIndex)
        // With classes today, stay on Today until the fixed cutoff of 22:00.
        if (today != null && today.classes.isNotEmpty() && now.toLocalTime().isBefore(DAY_CUTOFF_TIME)) {
            return todayIndex
        }
        // No class today, or the 22:00 cutoff has passed: move to the next day with classes.
        for (i in todayIndex + 1 until days.size) {
            if (days[i].classes.isNotEmpty()) return i
        }
        return todayIndex
    }

    private fun lookupSessionStatus(
        date: LocalDate,
        referenceDate: LocalDate,
        courseCode: String,
        slots: List<String>,
        daySessions: Map<String, List<AttendanceSession>>?,
    ): String? {
        val matches = daySessions?.get(courseCode.uppercase(Locale.US))
        if (matches.isNullOrEmpty()) {
            return if (!date.isAfter(referenceDate)) "Not marked" else null
        }
        for (slot in slots) {
            for (m in matches) {
                if (m.slot.equals(slot, ignoreCase = true)) return m.status
            }
        }
        return null
    }

    fun parseTime(str: String): LocalTime? {
        var s = 0
        var e = str.length
        while (s < e && str[s].isWhitespace()) s++
        while (e > s && str[e - 1].isWhitespace()) e--
        if (s >= e) return null

        var spaceIdx = -1
        for (i in s until e) {
            if (str[i] == ' ') {
                spaceIdx = i
                break
            }
        }
        val timeEnd = if (spaceIdx >= 0) spaceIdx else e

        var colonIdx = -1
        for (i in s until timeEnd) {
            if (str[i] == ':') {
                colonIdx = i
                break
            }
        }

        var hour = parseDigits(str, s, if (colonIdx >= 0) colonIdx else timeEnd) ?: return null
        val minute = if (colonIdx >= 0) {
            parseDigits(str, colonIdx + 1, timeEnd) ?: return null
        } else {
            0
        }

        if (spaceIdx >= 0) {
            val amPmStart = spaceIdx + 1
            var amPmEnd = amPmStart
            while (amPmEnd < e && str[amPmEnd] != ' ') amPmEnd++
            if (amPmEnd - amPmStart == 2) {
                val c0 = str[amPmStart]
                val c1 = str[amPmStart + 1]
                if (c1 == 'M' || c1 == 'm') {
                    if ((c0 == 'P' || c0 == 'p') && hour < 12) hour += 12
                    else if ((c0 == 'A' || c0 == 'a') && hour == 12) hour = 0
                }
            }
        }

        return LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    }

    private fun parseDigits(str: String, start: Int, end: Int): Int? {
        if (start >= end) return null
        var value = 0
        for (i in start until end) {
            val d = str[i] - '0'
            if (d !in 0..9) return null
            if (value < 1000) value = value * 10 + d
        }
        return value
    }

    fun saveGradeHistory(regNo: String, summary: AcademicSummary?, grades: List<GradeHistoryEntry>) {
        val db = helper?.writableDatabase ?: return
        db.inTransaction {
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
                db.compileStatement(
                    """
                    INSERT INTO grade_history (
                        reg_no, course_code, course_title, course_type, credits,
                        grade, exam_month, course_distribution, updated_at_ms
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use { stmt ->
                    for (g in grades) {
                        stmt.clearBindings()
                        stmt.bindString(1, g.regNo)
                        stmt.bindString(2, g.courseCode)
                        stmt.bindString(3, g.courseTitle)
                        stmt.bindString(4, g.courseType)
                        stmt.bindDouble(5, g.credits)
                        stmt.bindString(6, g.grade)
                        stmt.bindString(7, g.examMonth)
                        stmt.bindString(8, g.courseDistribution)
                        stmt.bindLong(9, g.updatedAtMs)
                        stmt.executeInsert()
                    }
                }
                _gradeHistoryFlow.value = grades
            }
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
            val regNoIdx = c.getColumnIndexOrThrow("reg_no")
            val codeIdx = c.getColumnIndexOrThrow("course_code")
            val titleIdx = c.getColumnIndexOrThrow("course_title")
            val typeIdx = c.getColumnIndexOrThrow("course_type")
            val creditsIdx = c.getColumnIndexOrThrow("credits")
            val gradeIdx = c.getColumnIndexOrThrow("grade")
            val examMonthIdx = c.getColumnIndexOrThrow("exam_month")
            val distIdx = c.getColumnIndexOrThrow("course_distribution")
            val updatedAtIdx = c.getColumnIndexOrThrow("updated_at_ms")
            while (c.moveToNext()) {
                list.add(
                    GradeHistoryEntry(
                        regNo = c.getString(regNoIdx),
                        courseCode = c.getString(codeIdx),
                        courseTitle = c.getString(titleIdx),
                        courseType = c.getString(typeIdx) ?: "",
                        credits = c.getDouble(creditsIdx),
                        grade = c.getString(gradeIdx),
                        examMonth = c.getString(examMonthIdx) ?: "",
                        courseDistribution = c.getString(distIdx) ?: "",
                        updatedAtMs = c.getLong(updatedAtIdx),
                    ),
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
        db.inTransaction {
            db.delete("student_marks", "reg_no = ? AND semester_sub_id = ?", arrayOf(regNo, semId))
            db.compileStatement(
                """
                INSERT INTO student_marks (
                    reg_no, semester_sub_id, course_code, course_title, course_type,
                    faculty_name, slot, component_name, max_marks, weightage_percent,
                    scored_marks, scored_percentage, updated_at_ms
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { stmt ->
                for (m in marks) {
                    stmt.clearBindings()
                    stmt.bindString(1, m.regNo)
                    stmt.bindString(2, m.semesterSubId)
                    stmt.bindString(3, m.courseCode)
                    stmt.bindString(4, m.courseTitle)
                    stmt.bindString(5, m.courseType)
                    stmt.bindString(6, m.facultyName)
                    stmt.bindString(7, m.slot)
                    stmt.bindString(8, m.componentName)
                    stmt.bindDouble(9, m.maxMarks)
                    stmt.bindDouble(10, m.weightagePercent)
                    stmt.bindDouble(11, m.scoredMarks)
                    stmt.bindDouble(12, m.scoredPercentage)
                    stmt.bindLong(13, m.updatedAtMs)
                    stmt.executeInsert()
                }
            }
        }
        // ponytail: reload all semesters after the write lock releases so cached siblings stay visible
        loadMarks(regNo)
    }

    fun loadMarks(regNo: String, semesterSubId: String? = null): List<MarkEntry> {
        val db = helper?.readableDatabase ?: return emptyList()
        val selection = if (semesterSubId.isNullOrBlank()) "reg_no = ?" else "reg_no = ? AND semester_sub_id = ?"
        val args = if (semesterSubId.isNullOrBlank()) arrayOf(regNo) else arrayOf(regNo, semesterSubId)

        val list = mutableListOf<MarkEntry>()
        db.query("student_marks", null, selection, args, null, null, "course_code ASC, component_name ASC").use { c ->
            val idIdx = c.getColumnIndexOrThrow("id")
            val regNoIdx = c.getColumnIndexOrThrow("reg_no")
            val semIdIdx = c.getColumnIndexOrThrow("semester_sub_id")
            val codeIdx = c.getColumnIndexOrThrow("course_code")
            val titleIdx = c.getColumnIndexOrThrow("course_title")
            val typeIdx = c.getColumnIndexOrThrow("course_type")
            val facultyIdx = c.getColumnIndexOrThrow("faculty_name")
            val slotIdx = c.getColumnIndexOrThrow("slot")
            val componentIdx = c.getColumnIndexOrThrow("component_name")
            val maxIdx = c.getColumnIndexOrThrow("max_marks")
            val weightageIdx = c.getColumnIndexOrThrow("weightage_percent")
            val scoredIdx = c.getColumnIndexOrThrow("scored_marks")
            val pctIdx = c.getColumnIndexOrThrow("scored_percentage")
            val updatedAtIdx = c.getColumnIndexOrThrow("updated_at_ms")
            while (c.moveToNext()) {
                list.add(
                    MarkEntry(
                        id = c.getLong(idIdx),
                        regNo = c.getString(regNoIdx),
                        semesterSubId = c.getString(semIdIdx),
                        courseCode = c.getString(codeIdx),
                        courseTitle = c.getString(titleIdx),
                        courseType = c.getString(typeIdx) ?: "",
                        facultyName = c.getString(facultyIdx) ?: "",
                        slot = c.getString(slotIdx) ?: "",
                        componentName = c.getString(componentIdx),
                        maxMarks = c.getDouble(maxIdx),
                        weightagePercent = c.getDouble(weightageIdx),
                        scoredMarks = c.getDouble(scoredIdx),
                        scoredPercentage = c.getDouble(pctIdx),
                        updatedAtMs = c.getLong(updatedAtIdx),
                    ),
                )
            }
        }
        // Only publish full-user queries to _marksFlow; per-semester lookups must not drop sibling semesters.
        if (semesterSubId.isNullOrBlank()) {
            _marksFlow.value = list
        }
        return list
    }

    fun saveSemesterResultGpa(regNo: String, semesterSubId: String, gpa: Double) {
        val db = helper?.writableDatabase ?: return
        db.inTransaction {
            val cv = ContentValues().apply {
                put("reg_no", regNo)
                put("semester_sub_id", semesterSubId)
                put("gpa", gpa)
                put("updated_at_ms", System.currentTimeMillis())
            }
            db.insertWithOnConflict("semester_results", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
        loadSemesterResults(regNo)
    }

    fun saveAttendanceSessions(regNo: String, semesterSubId: String, sessions: List<AttendanceSession>) {
        if (sessions.isEmpty()) return
        val db = helper?.writableDatabase ?: return
        db.inTransaction {
            val courses = sessions.mapTo(LinkedHashSet()) { it.courseCode }
            db.compileStatement(
                "DELETE FROM attendance_sessions WHERE reg_no = ? AND semester_sub_id = ? AND course_code = ?",
            ).use { del ->
                for (course in courses) {
                    del.clearBindings()
                    del.bindString(1, regNo)
                    del.bindString(2, semesterSubId)
                    del.bindString(3, course)
                    del.executeUpdateDelete()
                }
            }
            db.compileStatement(
                """
                INSERT OR REPLACE INTO attendance_sessions (
                    reg_no, semester_sub_id, course_code, date, slot, status, updated_at_ms
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { stmt ->
                for (s in sessions) {
                    stmt.clearBindings()
                    stmt.bindString(1, s.regNo)
                    stmt.bindString(2, s.semesterSubId)
                    stmt.bindString(3, s.courseCode)
                    stmt.bindString(4, s.date)
                    stmt.bindString(5, s.slot)
                    stmt.bindString(6, s.status)
                    stmt.bindLong(7, s.updatedAtMs)
                    stmt.executeInsert()
                }
            }
        }
        loadAttendanceSessions(regNo)
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
            val idIdx = c.getColumnIndexOrThrow("id")
            val regNoIdx = c.getColumnIndexOrThrow("reg_no")
            val semIdIdx = c.getColumnIndexOrThrow("semester_sub_id")
            val codeIdx = c.getColumnIndexOrThrow("course_code")
            val dateIdx = c.getColumnIndexOrThrow("date")
            val slotIdx = c.getColumnIndexOrThrow("slot")
            val statusIdx = c.getColumnIndexOrThrow("status")
            val updatedAtIdx = c.getColumnIndexOrThrow("updated_at_ms")
            while (c.moveToNext()) {
                list.add(
                    AttendanceSession(
                        id = c.getLong(idIdx),
                        regNo = c.getString(regNoIdx),
                        semesterSubId = c.getString(semIdIdx),
                        courseCode = c.getString(codeIdx),
                        date = c.getString(dateIdx),
                        slot = c.getString(slotIdx),
                        status = c.getString(statusIdx),
                        updatedAtMs = c.getLong(updatedAtIdx),
                    ),
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
            val regNoIdx = c.getColumnIndexOrThrow("reg_no")
            val semIdIdx = c.getColumnIndexOrThrow("semester_sub_id")
            val gpaIdx = c.getColumnIndexOrThrow("gpa")
            val updatedAtIdx = c.getColumnIndexOrThrow("updated_at_ms")
            while (c.moveToNext()) {
                list.add(
                    SemesterResult(
                        regNo = c.getString(regNoIdx),
                        semesterSubId = c.getString(semIdIdx),
                        gpa = c.getDouble(gpaIdx),
                        updatedAtMs = c.getLong(updatedAtIdx),
                    ),
                )
            }
        }
        _semesterResultsFlow.value = list
        return list
    }

    fun saveCalendarEvents(semesterSubId: String, events: List<CalendarDayEvent>) {
        if (events.isEmpty()) return
        val db = helper?.writableDatabase ?: return
        val now = System.currentTimeMillis()
        db.inTransaction {
            // Clear only the months that were actually re-fetched: a month whose fetch
            // failed must keep its cached days instead of vanishing from the calendar.
            val months = events.mapTo(LinkedHashSet()) { it.monthYear }
            db.compileStatement(
                "DELETE FROM academic_calendar WHERE semester_sub_id = ? AND month_year = ?",
            ).use { del ->
                for (month in months) {
                    del.clearBindings()
                    del.bindString(1, semesterSubId)
                    del.bindString(2, month)
                    del.executeUpdateDelete()
                }
            }
            db.compileStatement(
                """
                INSERT OR REPLACE INTO academic_calendar (
                    semester_sub_id, date, day_number, month_year, day_of_week,
                    title, subtitle, event_category, is_holiday, is_instructional, is_exam, updated_at_ms
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { stmt ->
                for (e in events) {
                    stmt.clearBindings()
                    stmt.bindString(1, semesterSubId)
                    stmt.bindString(2, e.date)
                    stmt.bindLong(3, e.dayNumber.toLong())
                    stmt.bindString(4, e.monthYear)
                    stmt.bindString(5, e.dayOfWeek)
                    stmt.bindString(6, e.title)
                    stmt.bindString(7, e.subtitle)
                    stmt.bindString(8, e.eventCategory)
                    stmt.bindLong(9, if (e.isHoliday) 1L else 0L)
                    stmt.bindLong(10, if (e.isInstructional) 1L else 0L)
                    stmt.bindLong(11, if (e.isExam) 1L else 0L)
                    stmt.bindLong(12, now)
                    stmt.executeInsert()
                }
            }
        }
        // Publish what is actually stored, so a partial sync keeps the other months on screen
        _calendarEventsFlow.value = loadCalendarEvents(semesterSubId)
        bumpRevision()
    }

    /** Latest calendar sync timestamp (epoch ms), or 0L if empty. */
    fun loadCalendarUpdatedAtMs(semesterSubId: String = CALENDAR_SEMESTER_ID): Long {
        val db = helper?.readableDatabase ?: return 0L
        return db.rawQuery(
            "SELECT MAX(updated_at_ms) FROM academic_calendar WHERE semester_sub_id = ?",
            arrayOf(semesterSubId),
        ).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
    }

    /** Name of one semester, branch suffix removed. */
    fun semesterName(semesterSubId: String): String? {
        val db = helper?.readableDatabase ?: return null
        return db.rawQuery(
            "SELECT semester_name FROM semesters_attended WHERE semester_sub_id = ? LIMIT 1",
            arrayOf(semesterSubId),
        ).use { c ->
            if (c.moveToFirst()) c.getString(0)?.substringBefore(" - ")?.takeIf { it.isNotBlank() } else null
        }
    }

    fun loadCalendarEvents(semesterSubId: String? = null): List<CalendarDayEvent> {
        val db = helper?.readableDatabase ?: return emptyList()
        val selection = if (semesterSubId.isNullOrBlank()) null else "semester_sub_id = ?"
        val args = if (semesterSubId.isNullOrBlank()) null else arrayOf(semesterSubId)
        val list = mutableListOf<CalendarDayEvent>()
        db.query("academic_calendar", null, selection, args, null, null, "date ASC").use { c ->
            val dateIdx = c.getColumnIndexOrThrow("date")
            val dayNumberIdx = c.getColumnIndexOrThrow("day_number")
            val monthYearIdx = c.getColumnIndexOrThrow("month_year")
            val dayOfWeekIdx = c.getColumnIndexOrThrow("day_of_week")
            val titleIdx = c.getColumnIndexOrThrow("title")
            val subtitleIdx = c.getColumnIndexOrThrow("subtitle")
            val categoryIdx = c.getColumnIndexOrThrow("event_category")
            val holidayIdx = c.getColumnIndexOrThrow("is_holiday")
            val instructionalIdx = c.getColumnIndexOrThrow("is_instructional")
            val examIdx = c.getColumnIndexOrThrow("is_exam")
            while (c.moveToNext()) {
                list.add(
                    CalendarDayEvent(
                        date = c.getString(dateIdx),
                        dayNumber = c.getInt(dayNumberIdx),
                        monthYear = c.getString(monthYearIdx),
                        dayOfWeek = c.getString(dayOfWeekIdx),
                        title = c.getString(titleIdx),
                        subtitle = c.getString(subtitleIdx) ?: "",
                        eventCategory = c.getString(categoryIdx),
                        isHoliday = c.getInt(holidayIdx) == 1,
                        isInstructional = c.getInt(instructionalIdx) == 1,
                        isExam = c.getInt(examIdx) == 1,
                    ),
                )
            }
        }
        if (list.isNotEmpty()) {
            _calendarEventsFlow.value = list
        }
        return list
    }

    /** Returns true when no profile, attendance, or timetable records exist for this user. */
    fun isDatabaseEmpty(regNo: String): Boolean {
        if (regNo.isBlank()) return true
        val db = helper?.readableDatabase ?: return true
        return try {
            db.rawQuery(
                """
                SELECT NOT (
                    EXISTS(SELECT 1 FROM user_profile WHERE reg_no = ?1)
                    OR EXISTS(SELECT 1 FROM student_attendance WHERE reg_no = ?1)
                    OR EXISTS(SELECT 1 FROM timetable_classes WHERE reg_no = ?1)
                    OR EXISTS(SELECT 1 FROM semesters_attended WHERE reg_no = ?1)
                    OR EXISTS(SELECT 1 FROM grade_history WHERE reg_no = ?1)
                    OR EXISTS(SELECT 1 FROM student_marks WHERE reg_no = ?1)
                )
                """.trimIndent(),
                arrayOf(regNo),
            ).use { c -> if (c.moveToFirst()) c.getInt(0) == 1 else true }
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Wipes local tables and clears memory StateFlows.
     * When [regNo] is null or blank, clears all user records.
     */
    fun purgeDatabase(regNo: String? = null) {
        val db = helper?.writableDatabase ?: return
        try {
            db.inTransaction {
                if (regNo.isNullOrBlank()) {
                    db.delete("user_profile", null, null)
                    db.delete("semesters_attended", null, null)
                    db.delete("student_attendance", null, null)
                    db.delete("attendance_sessions", null, null)
                    db.delete("timetable_classes", null, null)
                    db.delete("student_marks", null, null)
                    db.delete("academic_summary", null, null)
                    db.delete("grade_history", null, null)
                    db.delete("semester_results", null, null)
                    db.delete("academic_calendar", null, null)
                } else {
                    val args = arrayOf(regNo)
                    db.delete("user_profile", "reg_no = ?", args)
                    db.delete("semesters_attended", "reg_no = ?", args)
                    db.delete("student_attendance", "reg_no = ?", args)
                    db.delete("attendance_sessions", "reg_no = ?", args)
                    db.delete("timetable_classes", "reg_no = ?", args)
                    db.delete("student_marks", "reg_no = ?", args)
                    db.delete("academic_summary", "reg_no = ?", args)
                    db.delete("grade_history", "reg_no = ?", args)
                    db.delete("semester_results", "reg_no = ?", args)
                    db.delete("academic_calendar", null, null)
                }
            }
        } catch (e: Exception) {
            AppLog.d("db: purgeDatabase failed: ${e.message}")
        }
        _profileFlow.value = null
        _timetableFlow.value = emptyList()
        _academicSummaryFlow.value = null
        _gradeHistoryFlow.value = emptyList()
        _marksFlow.value = emptyList()
        _semesterResultsFlow.value = emptyList()
        _attendanceSessionsFlow.value = emptyList()
        _attendanceFlow.value = emptyList()
        _calendarEventsFlow.value = emptyList()
        _semestersFlow.value = emptyList()
        bumpRevision()
    }
}
