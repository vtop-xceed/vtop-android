package dev.vtoppp.vtop.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vtoppp.vtop.data.AcademicCalendarParser
import dev.vtoppp.vtop.data.AttendanceParser
import dev.vtoppp.vtop.data.CalendarDayEvent
import dev.vtoppp.vtop.data.GradeHistoryParser
import dev.vtoppp.vtop.data.MarksParser
import dev.vtoppp.vtop.data.ProfileParser
import dev.vtoppp.vtop.data.SemesterAttended
import dev.vtoppp.vtop.data.SessionCache
import dev.vtoppp.vtop.data.TimetableParser
import dev.vtoppp.vtop.data.UserDatabase
import dev.vtoppp.vtop.data.UserProfile
import dev.vtoppp.vtop.data.VtopClient
import dev.vtoppp.vtop.data.VtopError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MenuItem(val label: String, val endpoint: String, val section: String)

sealed interface HomeUiState {
    data object Idle : HomeUiState
    data object Loading : HomeUiState
    data class Ready(
        val authorizedId: String,
        val sections: List<Pair<String, List<MenuItem>>>,
    ) : HomeUiState
    data class SectionReady(
        val title: String,
        val html: String,
        val cookies: List<Pair<String, String>>,
        /** True when the rendered HTML is the offline cache copy. */
        val fromCache: Boolean = false,
        /** Cached badge label; null only for a fresh network render. */
        val cacheAge: String? = null,
    ) : HomeUiState
    data class Failure(val error: VtopError, val item: MenuItem? = null) : HomeUiState
}

class HomeViewModel(
    private val client: VtopClient,
    private val authorizedId: String,
) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Ready(authorizedId, menuSections()))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    val profile: StateFlow<UserProfile?> = UserDatabase.profileFlow

    private val _lastSectionTitle = MutableStateFlow<String?>(null)
    val lastSectionTitle: StateFlow<String?> = _lastSectionTitle.asStateFlow()

    private var openJob: Job? = null
    private var openGeneration = 0
    private var lastItem: MenuItem? = null

    init {
        syncAllData()
    }

    fun syncAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            UserDatabase.setSyncing(true)
            try {
                // 0. Load cached data from local SQLite database into flows
                UserDatabase.loadProfile(authorizedId)
                UserDatabase.loadTimetable(authorizedId)
                UserDatabase.loadAttendance(authorizedId)
                UserDatabase.loadGradeHistory(authorizedId)
                UserDatabase.loadMarks(authorizedId)

                UserDatabase.loadCalendarEvents()

                // 1. Fetch Profile & Cumulative Grade History from StudentGradeHistory
                // This endpoint contains the student's real name (MAYANK KUSHWAHA), degree, school, CGPA, and course grades
                try {
                    val cachedGrades = SessionCache.load("examinations/examGradeView/StudentGradeHistory")
                    if (cachedGrades != null) {
                        val p = ProfileParser.parse(cachedGrades.first, authorizedId)
                        if (p.name != authorizedId && p.name.isNotBlank()) UserDatabase.saveProfile(p)
                        val (summary, grades) = GradeHistoryParser.parse(cachedGrades.first, authorizedId)
                        if (grades.isNotEmpty() || summary != null) UserDatabase.saveGradeHistory(authorizedId, summary, grades)
                    }
                    val cachedTt = SessionCache.load("processViewTimeTable_BL20262701")
                    if (cachedTt != null) {
                        val entries = TimetableParser.parse(cachedTt.first, authorizedId, "BL20262701")
                        if (entries.isNotEmpty()) UserDatabase.saveTimetable(authorizedId, entries, "BL20262701")
                    }
                    val cachedAtt = SessionCache.load("processViewStudentAttendance_BL20262701")
                    if (cachedAtt != null) {
                        val att = AttendanceParser.parse(cachedAtt.first, authorizedId, "BL20262701")
                        if (att.isNotEmpty()) UserDatabase.saveAttendance(authorizedId, att)
                    }
                } catch (_: Exception) {}

                client.fetchSection("examinations/examGradeView/StudentGradeHistory").onSuccess { html ->
                    SessionCache.save("examinations/examGradeView/StudentGradeHistory", html)
                    val p = ProfileParser.parse(html, authorizedId)
                    if (p.name != authorizedId && p.name.isNotBlank()) {
                        UserDatabase.saveProfile(p)
                    }
                    val (summary, grades) = GradeHistoryParser.parse(html, authorizedId)
                    if (grades.isNotEmpty() || summary != null) {
                        UserDatabase.saveGradeHistory(authorizedId, summary, grades)
                    }
                }

                // Also check StudentProfileAllView just in case
                client.fetchSection("studentsRecord/StudentProfileAllView").onSuccess { html ->
                    val p = ProfileParser.parse(html, authorizedId)
                    if (p.name != authorizedId && p.name.isNotBlank()) {
                        UserDatabase.saveProfile(p)
                    }
                }

                // 1.5 Sync Academic Calendar for Fall Semester 2026-27
                val calSemId = "BL20262701"
                client.postSubAction(
                    "getDateForSemesterPreview",
                    mapOf("semSubId" to calSemId, "paramReturnId" to "getDateForSemesterPreview"),
                ).onSuccess { dateHtml ->
                    var monthDates = AcademicCalendarParser.extractMonthDates(dateHtml)
                    if (monthDates.isEmpty()) {
                        monthDates = listOf("01-JUN-2026", "01-JUL-2026", "01-AUG-2026", "01-SEP-2026", "01-OCT-2026", "01-NOV-2026")
                    }
                    val allEvents = mutableListOf<CalendarDayEvent>()
                    for (mDate in monthDates) {
                        client.postSubAction(
                            "processViewCalendar",
                            mapOf("semSubId" to calSemId, "classGroupId" to "COMB", "calDate" to mDate),
                        ).onSuccess { mHtml ->
                            val parsed = AcademicCalendarParser.parse(mHtml, mDate)
                            allEvents.addAll(parsed)
                        }
                        delay(80)
                    }
                    if (allEvents.isNotEmpty()) {
                        UserDatabase.saveCalendarEvents(calSemId, allEvents)
                    }
                }

                // 2. Discover all attended semesters
                var attendedSemesters: List<Pair<String, String>> = emptyList()

                client.fetchSection("academics/common/StudentAttendance").onSuccess { html ->
                    SessionCache.save("academics/common/StudentAttendance", html)
                    val sems = MarksParser.extractSemesters(html)
                    if (sems.isNotEmpty()) attendedSemesters = sems
                }

                if (attendedSemesters.isEmpty()) {
                    client.fetchSection("academics/common/StudentTimeTable").onSuccess { html ->
                        SessionCache.save("academics/common/StudentTimeTable", html)
                        val sems = MarksParser.extractSemesters(html)
                        if (sems.isNotEmpty()) attendedSemesters = sems
                    }
                }

                if (attendedSemesters.isEmpty()) {
                    client.fetchSection("examinations/StudentMarkView").onSuccess { html ->
                        SessionCache.save("examinations/StudentMarkView", html)
                        val sems = MarksParser.extractSemesters(html)
                        if (sems.isNotEmpty()) attendedSemesters = sems
                    }
                }

                if (attendedSemesters.isEmpty()) {
                    val local = UserDatabase.loadSemesters(authorizedId)
                    if (local.isNotEmpty()) {
                        attendedSemesters = local.map { it.semesterSubId to it.semesterName }
                    }
                }

                // Fallback list of known registered semesters for 24BCY10147 if network dropdown was unparsed
                if (attendedSemesters.isEmpty()) {
                    attendedSemesters = listOf(
                        "BL20262701" to "Fall Semester 2026-27 - BPL",
                        "BL20252605" to "Winter Semester 2025-26 - BPL",
                        "BL20252604" to "INTERIM SEMESTER 2025-26 - BPL",
                        "BL20252601" to "Fall Semester 2025-26 - BPL",
                        "BL20242505" to "Winter Semester 2024-25 - BPL",
                        "BL20242504" to "INTERIM SEMESTER 2024-25 - BPL",
                    )
                }

                val semAttendedList = attendedSemesters.map { (id, name) ->
                    SemesterAttended(regNo = authorizedId, semesterSubId = id, semesterName = name)
                }
                UserDatabase.saveSemesters(authorizedId, semAttendedList)

                // 3. Sequentially fetch data for each attended semester (Attendance, Timetable, Marks)
                for ((semId, semName) in attendedSemesters) {
                    // A. Attendance for semester
                    client.postSubAction(
                        "processViewStudentAttendance",
                        mapOf("semesterSubId" to semId),
                    ).onSuccess { attHtml ->
                        SessionCache.save("processViewStudentAttendance_$semId", attHtml)
                        val attEntries = AttendanceParser.parse(attHtml, authorizedId, semId)
                        if (attEntries.isNotEmpty()) {
                            UserDatabase.saveAttendance(authorizedId, attEntries)
                        }
                    }
                    delay(120)

                    // B. Timetable for semester
                    client.postSubAction(
                        "processViewTimeTable",
                        mapOf("semesterSubId" to semId),
                    ).onSuccess { ttHtml ->
                        SessionCache.save("processViewTimeTable_$semId", ttHtml)
                        val ttEntries = TimetableParser.parse(ttHtml, authorizedId, semId)
                        if (ttEntries.isNotEmpty()) {
                            UserDatabase.saveTimetable(authorizedId, ttEntries, semesterSubId = semId)
                        }
                    }
                    delay(120)

                    // C. Marks for semester
                    client.postSubAction(
                        "examinations/doStudentMarkView",
                        mapOf("semesterSubId" to semId),
                    ).onSuccess { marksHtml ->
                        val marksEntries = MarksParser.parse(marksHtml, authorizedId, semId)
                        if (marksEntries.isNotEmpty()) {
                            SessionCache.save("examinations/doStudentMarkView_$semId", marksHtml)
                            UserDatabase.saveMarks(authorizedId, marksEntries)
                        }
                    }
                    delay(120)
                }

                // Export to phone /sdcard/Download/ for immediate verification
                UserDatabase.autoExport()
            } finally {
                UserDatabase.setSyncing(false)
            }
        }
    }

    fun open(item: MenuItem) {
        openJob?.cancel()
        val generation = ++openGeneration
        lastItem = item
        _lastSectionTitle.value = item.label

        // T18.5: Loading goes out first from the main thread; the disk read
        // (SessionCache.load) joins the IO coroutine instead of blocking here.
        _state.value = HomeUiState.Loading

        openJob = viewModelScope.launch(Dispatchers.IO) {
            // Stale-while-revalidate: the cached copy renders immediately; the
            // network refresh replaces it below once the fetch settles.
            val cached = SessionCache.load(item.endpoint)
            if (cached != null) {
                withContext(Dispatchers.Main) {
                    if (generation == openGeneration) {
                        _state.value = HomeUiState.SectionReady(
                            title = item.label,
                            html = cached.first,
                            cookies = client.cookiePairs(),
                            fromCache = true,
                            cacheAge = SessionCache.ageLabel(cached.second),
                        )
                    }
                }
            }
            val outcome = try {
                client.fetchSection(item.endpoint).fold(
                    onSuccess = { html ->
                        SessionCache.save(item.endpoint, html)
                        if (item.endpoint == "studentsRecord/StudentProfileAllView") {
                            val p = ProfileParser.parse(html, authorizedId)
                            UserDatabase.saveProfile(p)
                        } else if (item.endpoint == "academics/common/StudentTimeTable") {
                            val entries = TimetableParser.parse(html, authorizedId)
                            if (entries.isNotEmpty()) {
                                UserDatabase.saveTimetable(authorizedId, entries)
                            }
                        } else if (item.endpoint == "examinations/examGradeView/StudentGradeHistory") {
                            val (summary, grades) = GradeHistoryParser.parse(html, authorizedId)
                            if (grades.isNotEmpty() || summary != null) {
                                UserDatabase.saveGradeHistory(authorizedId, summary, grades)
                            }
                        } else if (item.endpoint == "examinations/StudentMarkView" || item.endpoint == "examinations/doStudentMarkView") {
                            val marks = MarksParser.parse(html, authorizedId)
                            if (marks.isNotEmpty()) {
                                UserDatabase.saveMarks(authorizedId, marks)
                            }
                        }
                        HomeUiState.SectionReady(
                            title = item.label,
                            html = html,
                            cookies = client.cookiePairs(),
                        ) as HomeUiState?
                    },
                    onFailure = { e -> refreshFailure(e, item) },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                refreshFailure(t, item)
            }
            // null = the on-screen cached copy stays (refresh failed).
            withContext(Dispatchers.Main) {
                if (generation == openGeneration && outcome != null) _state.value = outcome
            }
        }
    }

    /**
     * A failed refresh with an offline copy present never blanks the screen:
     * it publishes a stale-notice and keeps the cached SectionReady. A cold
     * miss still renders the full-screen failure card.
     */
    private fun refreshFailure(error: Throwable, item: MenuItem): HomeUiState? {
        val err = error as? VtopError ?: VtopError.Unknown(error.message ?: "unknown")
        if (SessionCache.load(item.endpoint) != null) {
            _staleNotice.value = failureMessage(err)
            return null
        }
        return HomeUiState.Failure(error = err, item = item)
    }

    fun retry() {
        lastItem?.let(::open)
    }

    fun backToMenu() {
        // T14: a stale fetch must neither replace the Menu with a Failure card
        // nor teleport the user back into the section they left — the in-flight
        // job is cancelled and its outcome generation becomes invalid.
        openJob?.cancel()
        openGeneration++
        _state.value = HomeUiState.Ready(authorizedId, menuSections())
        _staleNotice.value = null
    }

    /** Fired when a background refresh failed but the on-screen cache stays. */
    private val _staleNotice = MutableStateFlow<String?>(null)
    val staleNotice: StateFlow<String?> = _staleNotice.asStateFlow()

    fun consumeStaleNotice() {
        _staleNotice.value = null
    }

    private fun failureMessage(error: VtopError) = when (error) {
        VtopError.SessionExpired -> "VTOP session expired — re-authenticating; showing cached copy"
        VtopError.Network -> "Offline — showing the cached copy"
        is VtopError.Blocked -> "VTOP is blocking this app right now — showing the cached copy"
        else -> "VTOP unreachable — showing the cached copy"
    }
}

/**
 * Menu map extracted from /vtop/content (see docs/vtop-session.md).
 * Keyed by VTOP section heading.
 */
fun menuSections(): List<Pair<String, List<MenuItem>>> = listOf(
    "Student" to listOf(
        MenuItem("Profile", "studentsRecord/StudentProfileAllView", "Student"),
        MenuItem("Credentials", "proctor/viewStudentCredentials", "Student"),
        MenuItem("Spot-light", "spotlight/spotlightViewOld", "Student"),
        MenuItem("Acknowledgement View", "admissions/AcknowledgmentView", "Student"),
        MenuItem("Student Bank Info", "studentBankInformation/BankInfoStudent", "Student"),
        MenuItem("Contact Us", "hrms/contactDetails", "Student"),
    ),
    "Academics" to listOf(
        MenuItem("Class Messages", "academics/common/StudentClassMessage", "Academics"),
        MenuItem("My Curriculum", "academics/common/Curriculum", "Academics"),
        MenuItem("Time Table", "academics/common/StudentTimeTable", "Academics"),
        MenuItem("Class Attendance", "academics/common/StudentAttendance", "Academics"),
        MenuItem("Course Page", "academics/common/StudentCoursePage", "Academics"),
        MenuItem("Academics Calendar", "academics/common/CalendarPreview", "Academics"),
        MenuItem("Course Withdraw", "academics/withdraw/courseWithdraw", "Academics"),
    ),
    "COE" to listOf(
        MenuItem("Exam Schedule", "examinations/StudExamSchedule", "COE"),
        MenuItem("Marks", "examinations/StudentMarkView", "COE"),
        MenuItem("Grades", "examinations/examGradeView/StudentGradeView", "COE"),
        MenuItem("Grade History", "examinations/examGradeView/StudentGradeHistory", "COE"),
    ),
    "Hostels" to listOf(
        MenuItem("Late Hour Request", "hostels/late/hour/student/request/1", "Hostels"),
        MenuItem("Leave Request", "hostels/student/leave/1", "Hostels"),
        MenuItem("Leave History", "hostels/student/barcodeleave", "Hostels"),
        MenuItem("Hostel Room Allotment", "hostels/room/allotment/home", "Hostels"),
    ),
    "Finance" to listOf(
        MenuItem("Payments", "finance/Payments", "Finance"),
        MenuItem("Payment Receipts", "finance/getStudentReceipts", "Finance"),
        MenuItem("Fees Intimation", "finance/getFeesIntimation", "Finance"),
    ),
    "Others" to listOf(
        MenuItem("Apply Bonafide", "others/bonafide/StudentBonafidePageControl", "Others"),
        MenuItem("Support & Complaints", "ticketrise/createTicket", "Others"),
        MenuItem("Change Password", "controlpanel/ChangePassword", "Others"),
    ),
)
