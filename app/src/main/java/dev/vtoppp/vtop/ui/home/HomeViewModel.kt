package dev.vtoppp.vtop.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vtoppp.vtop.data.AcademicCalendarParser
import dev.vtoppp.vtop.data.AppLog
import dev.vtoppp.vtop.data.AttendanceDetailParser
import dev.vtoppp.vtop.data.AttendanceEntry
import dev.vtoppp.vtop.data.AttendanceParser
import dev.vtoppp.vtop.data.CALENDAR_SEMESTER_ID
import dev.vtoppp.vtop.data.CalendarDayEvent
import dev.vtoppp.vtop.data.GradeHistoryParser
import dev.vtoppp.vtop.data.MarksParser
import dev.vtoppp.vtop.data.ProfileParser
import dev.vtoppp.vtop.data.ResultPageParser
import dev.vtoppp.vtop.data.SemesterAttended
import dev.vtoppp.vtop.data.SessionCache
import dev.vtoppp.vtop.data.TimetableParser
import dev.vtoppp.vtop.data.UserDatabase
import dev.vtoppp.vtop.data.UserProfile
import dev.vtoppp.vtop.data.VtopClient
import dev.vtoppp.vtop.data.VtopError
import dev.vtoppp.vtop.data.WarmState
import java.io.IOException
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
        private val isOnline: () -> Boolean = UserDatabase::isOnline,
) : ViewModel() {

    private val _state =
            MutableStateFlow<HomeUiState>(HomeUiState.Ready(authorizedId, menuSections()))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    val profile: StateFlow<UserProfile?> = UserDatabase.profileFlow

    private val _lastSectionTitle = MutableStateFlow<String?>(null)
    val lastSectionTitle: StateFlow<String?> = _lastSectionTitle.asStateFlow()

    private val _isRebuilding = MutableStateFlow(false)
    val isRebuilding: StateFlow<Boolean> = _isRebuilding.asStateFlow()

    private val _rebuildStep = MutableStateFlow("")
    val rebuildStep: StateFlow<String> = _rebuildStep.asStateFlow()

    private val _rebuildProgress = MutableStateFlow(0f)
    val rebuildProgress: StateFlow<Float> = _rebuildProgress.asStateFlow()

    private val _rebuildLogs = MutableStateFlow<List<String>>(emptyList())
    val rebuildLogs: StateFlow<List<String>> = _rebuildLogs.asStateFlow()

    private val _loadingAttendanceKeys = MutableStateFlow<Set<String>>(emptySet())
    val loadingAttendanceKeys: StateFlow<Set<String>> = _loadingAttendanceKeys.asStateFlow()

    private var openJob: Job? = null
    private var openGeneration = 0
    private var lastItem: MenuItem? = null
    private val syncInProgress = java.util.concurrent.atomic.AtomicBoolean(false)
    private val activeTasks = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val refreshedInSession = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    companion object {
        private const val ACTIVE_FRESH_TTL_MS = 10 * 60 * 1000L // 10 min
        private const val CALENDAR_FRESH_TTL_MS = 6 * 60 * 60 * 1000L // 6 hr
    }

    init {
        loadFromDatabase()
        if (UserDatabase.isDatabaseEmpty(authorizedId)) {
            viewModelScope.launch(Dispatchers.IO) {
                hydrateFromSessionCache()
                if (UserDatabase.isDatabaseEmpty(authorizedId) &&
                    isOnline() &&
                    (client.hasSession || client.warm.value !is WarmState.Failed)
                ) {
                    purgeAndRebuild(isInitialStartup = true)
                }
            }
        }
    }

    private fun loadFromDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            UserDatabase.loadAllCached(authorizedId)
        }
    }

    // ponytail: offline cache hydration from SessionCache files
    private fun hydrateFromSessionCache() {
        try {
            SessionCache.load("academics/common/StudentAttendance")?.first?.let { html ->
                val sems = MarksParser.extractSemesters(html)
                if (sems.isNotEmpty()) saveAttendedSemesters(sems)
            }
            SessionCache.load("examinations/examGradeView/StudentGradeHistory")?.first?.let { html ->
                saveGradeHistoryFromPage(html)
            }
            SessionCache.load("studentsRecord/StudentProfileAllView")?.first?.let { html ->
                saveProfileFromPage(html)
            }
            val semIds = (UserDatabase.loadSemesters(authorizedId).map { it.semesterSubId } +
                    UserDatabase.getAttendanceSemesterSubIds(authorizedId) +
                    listOfNotNull(UserDatabase.getLatestSemesterId(authorizedId), CALENDAR_SEMESTER_ID)).distinct()
            for (semId in semIds) {
                SessionCache.load("processViewStudentAttendance_$semId")?.first?.let { attHtml ->
                    val att = AttendanceParser.parse(attHtml, authorizedId, semId)
                    if (att.isNotEmpty()) UserDatabase.saveAttendance(authorizedId, att)
                }
                SessionCache.load("processViewTimeTable_$semId")?.first?.let { ttHtml ->
                    val tt = TimetableParser.parse(ttHtml, authorizedId, semId)
                    if (tt.isNotEmpty()) UserDatabase.saveTimetable(authorizedId, tt, semesterSubId = semId)
                }
                SessionCache.load("examinations/doStudentMarkView_$semId")?.first?.let { marksHtml ->
                    val marks = MarksParser.parse(marksHtml, authorizedId, semId)
                    if (marks.isNotEmpty()) UserDatabase.saveMarks(authorizedId, marks)
                }
                SessionCache.load("examinations/doStudentGradeView_$semId")?.first?.let { gradeHtml ->
                    ResultPageParser.parseGpa(gradeHtml)?.let { gpa ->
                        UserDatabase.saveSemesterResultGpa(authorizedId, semId, gpa)
                    }
                }
            }
            UserDatabase.loadAllCached(authorizedId)
        } catch (e: Exception) {
            AppLog.d("hydrateFromSessionCache error: ${e.javaClass.simpleName}")
        }
    }

    private fun isConnectivityOrSessionError(t: Throwable): Boolean =
            when (t) {
                is VtopError.Network,
                is VtopError.RateLimited,
                is VtopError.CaptchaRejected,
                is VtopError.Blocked,
                is VtopError.SessionExpired,
                is IOException -> true
                else -> false
            }

    private fun appendLog(msg: String) {
        val time =
                java.time.LocalTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
        _rebuildLogs.value = _rebuildLogs.value + "[$time] $msg"
    }

    private suspend fun fetchProfileAndGrades(
        fetchGrades: Boolean = true,
        log: ((String) -> Unit)? = null,
    ): Boolean {
        if (!isOnline()) return false
        var gradesOk = true
        if (fetchGrades) {
            val gradeResult = client.fetchSection("examinations/examGradeView/StudentGradeHistory")
            gradeResult.onSuccess { html ->
                SessionCache.save("examinations/examGradeView/StudentGradeHistory", html)
                saveGradeHistoryFromPage(html)
                log?.invoke("Parsed cumulative grade history & profile")
            }.onFailure { err ->
                log?.invoke("Warning: Could not fetch StudentGradeHistory: ${err.message}")
                if (isConnectivityOrSessionError(err)) return false
                gradesOk = false
            }
        }
        if (!isOnline()) return false
        val profileResult = client.fetchSection("studentsRecord/StudentProfileAllView")
        var profileOk = true
        profileResult.onSuccess { html ->
            saveProfileFromPage(html)
            log?.invoke("Parsed StudentProfileAllView details")
        }.onFailure { err ->
            if (isConnectivityOrSessionError(err)) return false
            profileOk = false
        }
        return if (fetchGrades) (gradesOk || profileOk) else profileOk
    }

    private suspend fun fetchCalendarFromVtop(log: ((String) -> Unit)? = null): Boolean {
        if (!isOnline()) return false
        val calSemId = CALENDAR_SEMESTER_ID
        val dateResult = client.postSubAction(
            "getDateForSemesterPreview",
            mapOf("semSubId" to calSemId, "paramReturnId" to "getDateForSemesterPreview"),
        )
        if (dateResult.isFailure) {
            val err = dateResult.exceptionOrNull()
            if (err != null && isConnectivityOrSessionError(err)) return false
            return false
        }
        val dateHtml = dateResult.getOrNull().orEmpty()
        var monthDates = AcademicCalendarParser.extractMonthDates(dateHtml)
        if (monthDates.isEmpty()) {
            monthDates = listOf(
                "01-JUN-2026", "01-JUL-2026", "01-AUG-2026",
                "01-SEP-2026", "01-OCT-2026", "01-NOV-2026"
            )
        }
        val allEvents = mutableListOf<CalendarDayEvent>()
        for (mDate in monthDates) {
            if (!isOnline()) break
            val monthRes = client.postSubAction(
                "processViewCalendar",
                mapOf("semSubId" to calSemId, "classGroupId" to "COMB", "calDate" to mDate),
            )
            monthRes.onSuccess { mHtml ->
                allEvents.addAll(AcademicCalendarParser.parse(mHtml, mDate))
            }.onFailure { err ->
                if (isConnectivityOrSessionError(err)) return false
            }
            delay(50)
        }
        if (allEvents.isNotEmpty()) {
            UserDatabase.saveCalendarEvents(calSemId, allEvents)
            log?.invoke("Saved ${allEvents.size} calendar events")
        }
        return true
    }

    private suspend fun fetchAttendanceForSemester(
        semId: String,
        includeAllDetails: Boolean,
        log: ((String) -> Unit)? = null,
    ): Boolean {
        if (!isOnline()) return false
        val previousByCode =
                UserDatabase.loadAttendance(authorizedId, semId).associateBy {
                    it.courseCode.uppercase()
                }
        val existingSessionsByCourse =
                UserDatabase.loadAttendanceSessions(authorizedId)
                        .filter { it.semesterSubId == semId }
                        .groupBy { it.courseCode.uppercase() }

        val attResult = client.postSubAction(
            "processViewStudentAttendance",
            mapOf("semesterSubId" to semId),
        )
        if (attResult.isFailure) {
            val err = attResult.exceptionOrNull()
            log?.invoke("[$semId] Attendance fetch error: ${err?.message}")
            return false
        }
        val attHtml = attResult.getOrNull().orEmpty()
        SessionCache.save("processViewStudentAttendance_$semId", attHtml)
        val attEntries = AttendanceParser.parse(attHtml, authorizedId, semId)
        if (attEntries.isNotEmpty()) {
            UserDatabase.saveAttendance(authorizedId, attEntries)
            log?.invoke("[$semId] Cached ${attEntries.size} attendance records")
        } else {
            log?.invoke("[$semId] No attendance records found")
        }

        for (entry in attEntries) {
            if (!isOnline()) break
            if (entry.courseId.isBlank()) continue
            val prev = previousByCode[entry.courseCode.uppercase()]
            val hasSessions = !existingSessionsByCourse[entry.courseCode.uppercase()].isNullOrEmpty()
            val changed =
                    prev == null ||
                            prev.attendedClasses != entry.attendedClasses ||
                            prev.totalClasses != entry.totalClasses
            if (!includeAllDetails && hasSessions && !changed) continue

            val detailRes = client.postSubAction(
                "processViewAttendanceDetail",
                mapOf(
                    "semesterSubId" to semId,
                    "registerNumber" to authorizedId,
                    "courseId" to entry.courseId,
                    "courseType" to entry.courseTypeA.ifBlank { "LT" },
                ),
            )
            if (detailRes.isFailure) {
                val err = detailRes.exceptionOrNull()
                if (err != null && isConnectivityOrSessionError(err)) return false
            }
            detailRes.onSuccess { detailHtml ->
                SessionCache.save(
                    "processViewAttendanceDetail_${semId}_${entry.courseId}",
                    detailHtml
                )
                val parsed = AttendanceDetailParser.parse(
                    detailHtml,
                    authorizedId,
                    semId,
                    entry.courseCode
                )
                if (parsed.isNotEmpty()) {
                    UserDatabase.saveAttendanceSessions(authorizedId, semId, parsed)
                    log?.invoke("[$semId] Cached ${parsed.size} classes for ${entry.courseCode}")
                }
            }
            delay(70)
        }
        return true
    }

    private suspend fun fetchTimetableForSemester(semId: String, log: ((String) -> Unit)? = null): Boolean {
        if (!isOnline()) return false
        val ttResult = client.postSubAction(
            "processViewTimeTable",
            mapOf("semesterSubId" to semId),
        )
        if (ttResult.isFailure) {
            val err = ttResult.exceptionOrNull()
            log?.invoke("[$semId] Timetable fetch error: ${err?.message}")
            return false
        }
        val ttHtml = ttResult.getOrNull().orEmpty()
        SessionCache.save("processViewTimeTable_$semId", ttHtml)
        val ttEntries = TimetableParser.parse(ttHtml, authorizedId, semId)
        if (ttEntries.isNotEmpty()) {
            UserDatabase.saveTimetable(authorizedId, ttEntries, semesterSubId = semId)
            log?.invoke("[$semId] Cached ${ttEntries.size} timetable slots")
        } else {
            log?.invoke("[$semId] No timetable slots found")
        }
        return true
    }

    private suspend fun fetchMarksForSemester(
        semId: String,
        includeGpa: Boolean = true,
        log: ((String) -> Unit)? = null,
    ): Boolean {
        if (!isOnline()) return false
        if (includeGpa) {
            val gpaRes = client.postSubAction(
                "examinations/examGradeView/doStudentGradeView",
                mapOf("semesterSubId" to semId),
            )
            if (gpaRes.isFailure) {
                val err = gpaRes.exceptionOrNull()
                if (err != null && isConnectivityOrSessionError(err)) return false
            }
            gpaRes.onSuccess { resultHtml ->
                SessionCache.save("examinations/doStudentGradeView_$semId", resultHtml)
                ResultPageParser.parseGpa(resultHtml)?.let { gpa ->
                    UserDatabase.saveSemesterResultGpa(authorizedId, semId, gpa)
                }
            }
            delay(80)
        }
        if (!isOnline()) return false
        val marksRes = client.postSubAction(
            "examinations/doStudentMarkView",
            mapOf("semesterSubId" to semId),
        )
        if (marksRes.isFailure) {
            val err = marksRes.exceptionOrNull()
            log?.invoke("[$semId] Marks fetch error: ${err?.message}")
            return false
        }
        val marksHtml = marksRes.getOrNull().orEmpty()
        val marksEntries = MarksParser.parse(marksHtml, authorizedId, semId)
        if (marksEntries.isNotEmpty()) {
            SessionCache.save("examinations/doStudentMarkView_$semId", marksHtml)
            UserDatabase.saveMarks(authorizedId, marksEntries)
            log?.invoke("[$semId] Cached ${marksEntries.size} mark components")
        } else {
            log?.invoke("[$semId] No mark components found")
        }
        return true
    }

    fun purgeAndRebuild(isInitialStartup: Boolean = false) {
        if (!isOnline()) {
            AppLog.d("Aborted rebuild: offline — cached data preserved")
            appendLog("Aborted rebuild: offline — cached data preserved")
            _rebuildStep.value = "Offline — cached data preserved"
            return
        }
        if (!syncInProgress.compareAndSet(false, true)) return
        viewModelScope.launch(Dispatchers.IO) {
            _isRebuilding.value = true
            _rebuildLogs.value = emptyList()
            _rebuildProgress.value = 0.05f
            UserDatabase.setSyncing(true)
            try {
                if (!isInitialStartup) {
                    _rebuildStep.value = "Probing VTOP connection..."
                    appendLog("Probing VTOP connection before purge...")
                    val probeResult = client.fetchSection("examinations/examGradeView/StudentGradeHistory")
                    if (probeResult.isFailure) {
                        val err = probeResult.exceptionOrNull()
                        val msg = "Aborted rebuild: probe failed (${err?.message ?: "error"}) — cached data preserved"
                        AppLog.d(msg)
                        appendLog(msg)
                        _rebuildStep.value = "Probe failed — cached data preserved"
                        return@launch
                    }
                    val probedGradeHtml = probeResult.getOrNull()

                    _rebuildStep.value = "Purging local database..."
                    appendLog("Purging local SQLite database and session cache...")
                    SessionCache.clear()
                    UserDatabase.purgeDatabase(authorizedId)
                    refreshedInSession.clear()
                    appendLog("Database and cache cleared")
                    delay(100)

                    if (!probedGradeHtml.isNullOrBlank()) {
                        SessionCache.save("examinations/examGradeView/StudentGradeHistory", probedGradeHtml)
                        saveGradeHistoryFromPage(probedGradeHtml)
                        appendLog("Saved probed grade history & profile")
                    }
                } else {
                    _rebuildStep.value = "Building initial database..."
                    appendLog("Initial setup: building local database for $authorizedId")
                }

                _rebuildProgress.value = 0.15f
                _rebuildStep.value = "Fetching Grade History & Profile..."
                appendLog("Requesting StudentGradeHistory & Profile...")
                val profileOk = fetchProfileAndGrades(fetchGrades = isInitialStartup, log = ::appendLog)
                if (!profileOk && !isOnline()) {
                    appendLog("Aborted rebuild: network lost during profile sync")
                    return@launch
                }
                if (!isOnline()) return@launch

                _rebuildProgress.value = 0.25f
                _rebuildStep.value = "Syncing Academic Calendar..."
                appendLog("Fetching academic calendar for $CALENDAR_SEMESTER_ID...")
                val calOk = fetchCalendarFromVtop(::appendLog)
                if (!calOk && !isOnline()) {
                    appendLog("Aborted rebuild: network lost during calendar sync")
                    return@launch
                }
                if (!isOnline()) return@launch

                _rebuildProgress.value = 0.35f
                _rebuildStep.value = "Discovering attended semesters..."
                appendLog("Resolving attended semesters list...")
                val attendedSemesters = resolveAttendedSemesters()
                if (attendedSemesters.isEmpty() && !isOnline()) {
                    appendLog("Aborted rebuild: offline and no semesters discovered")
                    return@launch
                }
                saveAttendedSemesters(attendedSemesters)
                appendLog(
                        "Found ${attendedSemesters.size} semesters: ${attendedSemesters.joinToString { it.first }}"
                )

                val totalSems = attendedSemesters.size.coerceAtLeast(1)
                for ((index, pair) in attendedSemesters.withIndex()) {
                    if (!isOnline()) {
                        appendLog("Offline: stopping rebuild early after $index semesters")
                        break
                    }
                    val (semId, semName) = pair
                    val semFraction = 0.60f / totalSems
                    val baseProgress = 0.35f + (index * semFraction)
                    val cleanSemName = semName.substringBefore(" - ")

                    _rebuildProgress.value = baseProgress + (semFraction * 0.40f)
                    _rebuildStep.value = "Caching Attendance & History ($cleanSemName)..."
                    appendLog("[$semId] Fetching attendance & class history...")
                    val attOk = fetchAttendanceForSemester(semId, includeAllDetails = true, log = ::appendLog)
                    if (!attOk && !isOnline()) break
                    delay(60)

                    _rebuildProgress.value = baseProgress + (semFraction * 0.70f)
                    _rebuildStep.value = "Caching Timetable ($cleanSemName)..."
                    appendLog("[$semId] Fetching timetable...")
                    val ttOk = fetchTimetableForSemester(semId, log = ::appendLog)
                    if (!ttOk && !isOnline()) break
                    delay(60)

                    _rebuildProgress.value = baseProgress + semFraction
                    _rebuildStep.value = "Caching Marks ($cleanSemName)..."
                    appendLog("[$semId] Fetching marks & GPA...")
                    val marksOk = fetchMarksForSemester(semId, includeGpa = true, log = ::appendLog)
                    if (!marksOk && !isOnline()) break
                    delay(60)
                }

                _rebuildProgress.value = 1.0f
                _rebuildStep.value = "Rebuild complete!"
                appendLog("Database rebuild finished successfully")
                delay(500)
            } catch (t: Throwable) {
                AppLog.d("purgeAndRebuild error: ${t.javaClass.simpleName}")
                appendLog("Error during rebuild: ${t.message ?: t.javaClass.simpleName}")
                _rebuildStep.value = "Rebuild finished with error"
                delay(1000)
            } finally {
                _isRebuilding.value = false
                if (activeTasks.isEmpty()) UserDatabase.setSyncing(false)
                syncInProgress.set(false)
            }
        }
    }

    private fun launchLazy(
        key: String,
        force: Boolean = false,
        ttlMs: Long = 0L,
        lastUpdatedMs: () -> Long = { 0L },
        block: suspend () -> Boolean,
    ) {
        if (_isRebuilding.value) return
        if (!isOnline()) {
            if (force && key.startsWith("marks_")) {
                _marksNotice.value = "Offline — showing cached copy"
            }
            return
        }
        if (!force && !client.hasSession && (client.warm.value is WarmState.Working || client.warm.value is WarmState.Failed)) {
            return
        }
        if (!force && ttlMs > 0L && key in refreshedInSession) return
        if (!activeTasks.add(key)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!force && ttlMs > 0L) {
                    val last = lastUpdatedMs()
                    if (last > 0L && (System.currentTimeMillis() - last) < ttlMs) {
                        refreshedInSession.add(key)
                        return@launch
                    }
                }
                UserDatabase.setSyncing(true)
                val ok = block()
                if (ok && ttlMs > 0L) refreshedInSession.add(key)
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                AppLog.d("lazySync[$key] failed: ${t.javaClass.simpleName}")
            } finally {
                activeTasks.remove(key)
                if (activeTasks.isEmpty() && !_isRebuilding.value) {
                    UserDatabase.setSyncing(false)
                }
            }
        }
    }

    fun syncAllData() = refreshOverview(force = true)

    fun onSessionReady() {
        _staleNotice.value = null
        _marksNotice.value = null
        refreshedInSession.clear()
        if (isOnline()) {
            refreshOverview(force = false)
        }
    }

    fun refreshOverview(force: Boolean = false) {
        launchLazy(
            key = "overview",
            force = force,
            ttlMs = ACTIVE_FRESH_TTL_MS,
            lastUpdatedMs = {
                val latestSem = UserDatabase.getLatestSemesterId(authorizedId)
                val attTs = UserDatabase.loadAttendance(authorizedId, latestSem).maxOfOrNull { it.updatedAtMs } ?: 0L
                val ttTs = UserDatabase.loadTimetable(authorizedId, latestSem).maxOfOrNull { it.updatedAtMs } ?: 0L
                minOf(attTs, ttTs)
            },
        ) {
            var latestSem = UserDatabase.getLatestSemesterId(authorizedId)
            if (latestSem.isNullOrBlank()) {
                val sems = resolveAttendedSemesters()
                saveAttendedSemesters(sems)
                latestSem = sems.firstOrNull()?.first
            }
            var allOk = true
            if (!latestSem.isNullOrBlank()) {
                val attOk = fetchAttendanceForSemester(latestSem, includeAllDetails = false)
                if (!attOk) return@launchLazy false
                val ttOk = fetchTimetableForSemester(latestSem)
                if (!ttOk) return@launchLazy false
            } else {
                allOk = false
            }
            if (force || UserDatabase.loadProfile(authorizedId) == null) {
                val profOk = fetchProfileAndGrades()
                if (!profOk) allOk = false
            }
            allOk
        }
    }

    fun refreshAttendance(semesterSubId: String? = null, force: Boolean = false) {
        val targetKey = "att_${semesterSubId ?: "latest"}"
        launchLazy(
            key = targetKey,
            force = force,
            ttlMs = ACTIVE_FRESH_TTL_MS,
            lastUpdatedMs = {
                val semId = semesterSubId ?: UserDatabase.getLatestSemesterId(authorizedId)
                val latestId = UserDatabase.getLatestSemesterId(authorizedId)
                val entries = UserDatabase.loadAttendance(authorizedId, semId)
                when {
                    entries.isEmpty() -> 0L
                    semId != null && latestId != null && semId != latestId -> System.currentTimeMillis()
                    else -> entries.maxOf { it.updatedAtMs }
                }
            },
        ) {
            var semId = semesterSubId ?: UserDatabase.getLatestSemesterId(authorizedId)
            if (semId.isNullOrBlank()) {
                val sems = resolveAttendedSemesters()
                saveAttendedSemesters(sems)
                semId = sems.firstOrNull()?.first
            }
            if (!semId.isNullOrBlank()) {
                fetchAttendanceForSemester(semId, includeAllDetails = force)
            } else {
                false
            }
        }
    }

    fun refreshTimetable(semesterSubId: String? = null, force: Boolean = false) {
        val targetKey = "tt_${semesterSubId ?: "latest"}"
        launchLazy(
            key = targetKey,
            force = force,
            ttlMs = ACTIVE_FRESH_TTL_MS,
            lastUpdatedMs = {
                val semId = semesterSubId ?: UserDatabase.getLatestSemesterId(authorizedId)
                val latestId = UserDatabase.getLatestSemesterId(authorizedId)
                val entries = UserDatabase.loadTimetable(authorizedId, semId)
                when {
                    entries.isEmpty() -> 0L
                    semId != null && latestId != null && semId != latestId -> System.currentTimeMillis()
                    else -> entries.maxOf { it.updatedAtMs }
                }
            },
        ) {
            val semId = semesterSubId ?: UserDatabase.getLatestSemesterId(authorizedId) ?: return@launchLazy false
            fetchTimetableForSemester(semId)
        }
    }

    fun refreshCalendar(force: Boolean = false) {
        launchLazy(
            key = "calendar",
            force = force,
            ttlMs = CALENDAR_FRESH_TTL_MS,
            lastUpdatedMs = { UserDatabase.loadCalendarUpdatedAtMs(CALENDAR_SEMESTER_ID) },
        ) {
            fetchCalendarFromVtop()
        }
    }

    private suspend fun resolveAttendedSemesters(): List<Pair<String, String>> {
        var attendedSemesters: List<Pair<String, String>> = emptyList()

        if (isOnline()) {
            client.fetchSection("academics/common/StudentAttendance").onSuccess { html ->
                SessionCache.save("academics/common/StudentAttendance", html)
                val sems = MarksParser.extractSemesters(html)
                if (sems.isNotEmpty()) attendedSemesters = sems
            }
        }

        if (attendedSemesters.isEmpty()) {
            SessionCache.load("academics/common/StudentAttendance")?.let { cached ->
                val sems = MarksParser.extractSemesters(cached.first)
                if (sems.isNotEmpty()) attendedSemesters = sems
            }
        }

        if (attendedSemesters.isEmpty()) {
            val attIds = UserDatabase.getAttendanceSemesterSubIds(authorizedId)
            if (attIds.isNotEmpty()) {
                val markHtml = SessionCache.load("examinations/StudentMarkView")?.first
                val markSems =
                        if (!markHtml.isNullOrBlank()) MarksParser.extractSemesters(markHtml)
                        else emptyList()
                val nameMap = markSems.toMap()
                attendedSemesters = attIds.map { id -> id to (nameMap[id] ?: id) }
            }
        }

        if (attendedSemesters.isEmpty()) {
            val local = UserDatabase.loadSemesters(authorizedId)
            if (local.isNotEmpty()) {
                attendedSemesters = local.map { it.semesterSubId to it.semesterName }
            }
        }

        if (attendedSemesters.isEmpty()) {
            attendedSemesters =
                    listOf(
                            "BL20262701" to "Fall Semester 2026-27 - BPL",
                            "BL20252605" to "Winter Semester 2025-26 - BPL",
                            "BL20252604" to "INTERIM SEMESTER 2025-26 - BPL",
                            "BL20252601" to "Fall Semester 2025-26 - BPL",
                            "BL20242505" to "Winter Semester 2024-25 - BPL",
                            "BL20242504" to "INTERIM SEMESTER 2024-25 - BPL",
                    )
        }

        return attendedSemesters
    }

    /**
     * Marks & Grades refresh: grades page + requested/latest semester marks lazily.
     * Records honest success/failure into [marksNotice] (cached data shown otherwise).
     */
    fun refreshMarksAndGrades(semesterSubId: String? = null, force: Boolean = true) {
        val targetKey = "marks_${semesterSubId ?: "default"}"
        launchLazy(
            key = targetKey,
            force = force,
            ttlMs = ACTIVE_FRESH_TTL_MS,
            lastUpdatedMs = {
                val targetSem = semesterSubId ?: UserDatabase.getLatestSemesterId(authorizedId)
                val marksTs = if (targetSem != null) {
                    UserDatabase.loadMarks(authorizedId, targetSem).maxOfOrNull { it.updatedAtMs } ?: 0L
                } else 0L
                val (summary, grades) = UserDatabase.loadGradeHistory(authorizedId)
                val gradesTs = maxOf(summary?.updatedAtMs ?: 0L, grades.maxOfOrNull { it.updatedAtMs } ?: 0L)
                if (targetSem != null && targetSem != UserDatabase.getLatestSemesterId(authorizedId) && marksTs > 0L) {
                    System.currentTimeMillis()
                } else {
                    minOf(marksTs, gradesTs)
                }
            },
        ) {
            val (_, existingGrades) = UserDatabase.loadGradeHistory(authorizedId)
            val existingMarks = UserDatabase.loadMarks(authorizedId)
            val hasCachedData = existingGrades.isNotEmpty() || existingMarks.isNotEmpty()
            try {
                val profileOk = fetchProfileAndGrades()
                if (!profileOk && hasCachedData) {
                    if (force) _marksNotice.value = "VTOP unreachable — showing cached copy"
                    return@launchLazy false
                }
                var attended = UserDatabase.loadSemesters(authorizedId).map {
                    it.semesterSubId to it.semesterName
                }
                if (attended.isEmpty()) {
                    attended = resolveAttendedSemesters()
                    saveAttendedSemesters(attended)
                }
                val latestId = attended.firstOrNull()?.first
                val existingResults = UserDatabase.loadSemesterResults(authorizedId).map { it.semesterSubId }.toSet()
                val existingMarksSems = UserDatabase.loadMarks(authorizedId).map { it.semesterSubId }.toSet()
                val targets = attended.map { it.first }.filter { semId ->
                    semId == (semesterSubId ?: latestId) ||
                        semId !in existingResults ||
                        semId !in existingMarksSems
                }
                var anyMarksFailed = false
                for (semId in targets) {
                    val marksOk = fetchMarksForSemester(semId, includeGpa = true)
                    if (!marksOk) {
                        anyMarksFailed = true
                        if (hasCachedData) {
                            if (force) _marksNotice.value = "VTOP unreachable — showing cached copy"
                            return@launchLazy false
                        }
                    }
                    delay(80)
                }
                val (_, gradesAfter) = UserDatabase.loadGradeHistory(authorizedId)
                if (gradesAfter.isEmpty() && UserDatabase.loadMarks(authorizedId).isEmpty()) {
                    _marksNotice.value = "VTOP unreachable — no data cached yet"
                    false
                } else {
                    _marksNotice.value = null
                    !anyMarksFailed && profileOk
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                AppLog.d("refreshMarksAndGrades failed: ${t.javaClass.simpleName}")
                val (_, gradesAfter) = UserDatabase.loadGradeHistory(authorizedId)
                val hasCached = gradesAfter.isNotEmpty() || UserDatabase.loadMarks(authorizedId).isNotEmpty()
                _marksNotice.value = if (hasCached) {
                    if (force) "VTOP unreachable — showing cached copy" else null
                } else {
                    "VTOP unreachable — no data cached yet"
                }
                false
            }
        }
    }

    private fun saveProfileFromPage(html: String) {
        val p = ProfileParser.parse(html, authorizedId)
        if (p.name != authorizedId && p.name.isNotBlank()) UserDatabase.saveProfile(p)
    }

    private fun saveGradeHistoryFromPage(html: String) {
        saveProfileFromPage(html)
        val (summary, grades) = GradeHistoryParser.parse(html, authorizedId)
        if (grades.isNotEmpty() || summary != null)
                UserDatabase.saveGradeHistory(authorizedId, summary, grades)
    }

    private fun saveAttendedSemesters(semesters: List<Pair<String, String>>) {
        UserDatabase.saveSemesters(
                authorizedId,
                semesters.map { (id, name) ->
                    SemesterAttended(regNo = authorizedId, semesterSubId = id, semesterName = name)
                },
        )
    }

    fun open(item: MenuItem) {
        openJob?.cancel()
        val generation = ++openGeneration
        lastItem = item
        _lastSectionTitle.value = item.label

        // : Loading goes out first from the main thread; the disk read
        // (SessionCache.load) joins the IO coroutine instead of blocking here.
        _state.value = HomeUiState.Loading

        openJob =
                viewModelScope.launch(Dispatchers.IO) {
                    // Stale-while-revalidate: the cached copy renders immediately; the
                    // network refresh replaces it below once the fetch settles.
                    val cached = SessionCache.load(item.endpoint)
                    if (cached != null) {
                        withContext(Dispatchers.Main) {
                            if (generation == openGeneration) {
                                _state.value =
                                        HomeUiState.SectionReady(
                                                title = item.label,
                                                html = cached.first,
                                                cookies = client.cookiePairs(),
                                                fromCache = true,
                                                cacheAge = SessionCache.ageLabel(cached.second),
                                        )
                            }
                        }
                    }

                    if (!isOnline()) {
                        withContext(Dispatchers.Main) {
                            if (generation == openGeneration) {
                                if (cached != null) {
                                    _staleNotice.value = "Offline — showing the cached copy"
                                } else {
                                    _state.value = HomeUiState.Failure(VtopError.Network, item)
                                }
                            }
                        }
                        return@launch
                    }

                    val outcome =
                            try {
                                client.fetchSection(item.endpoint)
                                        .fold(
                                                onSuccess = { html ->
                                                    SessionCache.save(item.endpoint, html)
                                                    if (item.endpoint ==
                                                                    "studentsRecord/StudentProfileAllView"
                                                    ) {
                                                        val p =
                                                                ProfileParser.parse(
                                                                        html,
                                                                        authorizedId
                                                                )
                                                        UserDatabase.saveProfile(p)
                                                    } else if (item.endpoint ==
                                                                    "academics/common/StudentTimeTable"
                                                    ) {
                                                        val entries =
                                                                TimetableParser.parse(
                                                                        html,
                                                                        authorizedId
                                                                )
                                                        if (entries.isNotEmpty()) {
                                                            UserDatabase.saveTimetable(
                                                                    authorizedId,
                                                                    entries
                                                            )
                                                        }
                                                    } else if (item.endpoint ==
                                                                    "examinations/examGradeView/StudentGradeHistory"
                                                    ) {
                                                        val (summary, grades) =
                                                                GradeHistoryParser.parse(
                                                                        html,
                                                                        authorizedId
                                                                )
                                                        if (grades.isNotEmpty() || summary != null
                                                        ) {
                                                            UserDatabase.saveGradeHistory(
                                                                    authorizedId,
                                                                    summary,
                                                                    grades
                                                            )
                                                        }
                                                    } else if (item.endpoint ==
                                                                    "examinations/StudentMarkView" ||
                                                                    item.endpoint ==
                                                                            "examinations/doStudentMarkView"
                                                    ) {
                                                        val marks =
                                                                MarksParser.parse(
                                                                        html,
                                                                        authorizedId
                                                                )
                                                        if (marks.isNotEmpty()) {
                                                            UserDatabase.saveMarks(
                                                                    authorizedId,
                                                                    marks
                                                            )
                                                        }
                                                    }
                                                    HomeUiState.SectionReady(
                                                            title = item.label,
                                                            html = html,
                                                            cookies = client.cookiePairs(),
                                                    ) as
                                                            HomeUiState?
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
     * A failed refresh with an offline copy present never blanks the screen: it publishes a
     * stale-notice and keeps the cached SectionReady. A cold miss still renders the full-screen
     * failure card.
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
        // : a stale fetch must neither replace the Menu with a Failure card
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

    /** Marks & Grades refresh outcome; rendered as an inline banner on the page. */
    private val _marksNotice = MutableStateFlow<String?>(null)
    val marksNotice: StateFlow<String?> = _marksNotice.asStateFlow()

    fun consumeStaleNotice() {
        _staleNotice.value = null
    }

    fun consumeMarksNotice() {
        _marksNotice.value = null
    }

    fun loadAttendanceDetails(entry: AttendanceEntry) {
        loadAttendanceDetails(entry.semesterSubId, entry.courseId, entry.courseTypeA, entry.courseCode)
    }

    fun loadAttendanceDetails(
            semId: String,
            courseId: String,
            courseType: String,
            courseCode: String
    ) {
        val sem = semId.ifBlank { UserDatabase.getLatestSemesterId(authorizedId) ?: return }
        if (courseCode.isBlank()) return

        val key = "${sem}_${courseCode.uppercase()}"
        if (!activeTasks.add("detail_$key")) return
        _loadingAttendanceKeys.value = _loadingAttendanceKeys.value + key

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. If cached in SessionCache, hydrate to UserDatabase on IO thread
                var resolvedId = courseId
                var resolvedType = courseType
                if (resolvedId.isBlank()) {
                    SessionCache.load("processViewStudentAttendance_$sem")?.first?.let { cachedAtt ->
                        val match = AttendanceParser.parse(cachedAtt, authorizedId, sem)
                            .firstOrNull { it.courseCode.equals(courseCode, ignoreCase = true) }
                        if (match != null) {
                            resolvedId = match.courseId
                            resolvedType = match.courseTypeA
                        }
                    }
                }
                if (resolvedId.isNotBlank()) {
                    SessionCache.load("processViewAttendanceDetail_${sem}_$resolvedId")?.let { cached ->
                        val parsed = AttendanceDetailParser.parse(cached.first, authorizedId, sem, courseCode)
                        if (parsed.isNotEmpty()) {
                            UserDatabase.saveAttendanceSessions(authorizedId, sem, parsed)
                        }
                    }
                }

                // Fast-fail check
                if (!isOnline() || (!client.hasSession && client.warm.value is WarmState.Failed)) {
                    return@launch
                }

                if (resolvedId.isBlank()) {
                    val cachedAtt = SessionCache.load("processViewStudentAttendance_$sem")?.first
                        ?: if (client.hasSession) {
                            client.postSubAction(
                                "processViewStudentAttendance",
                                mapOf("semesterSubId" to sem),
                            ).getOrNull()?.also { html ->
                                SessionCache.save("processViewStudentAttendance_$sem", html)
                                val att = AttendanceParser.parse(html, authorizedId, sem)
                                if (att.isNotEmpty()) UserDatabase.saveAttendance(authorizedId, att)
                            }
                        } else null
                    if (!cachedAtt.isNullOrBlank()) {
                        val match = AttendanceParser.parse(cachedAtt, authorizedId, sem)
                            .firstOrNull { it.courseCode.equals(courseCode, ignoreCase = true) }
                        if (match != null) {
                            resolvedId = match.courseId
                            resolvedType = match.courseTypeA
                        }
                    }
                }
                if (resolvedId.isBlank()) return@launch

                if (!isOnline()) return@launch
                client.postSubAction(
                                "processViewAttendanceDetail",
                                mapOf(
                                        "semesterSubId" to sem,
                                        "registerNumber" to authorizedId,
                                        "courseId" to resolvedId,
                                        "courseType" to resolvedType.ifBlank { "LT" },
                                ),
                        )
                        .onSuccess { detailHtml ->
                            SessionCache.save("processViewAttendanceDetail_${sem}_$resolvedId", detailHtml)
                            val parsed = AttendanceDetailParser.parse(
                                    detailHtml,
                                    authorizedId,
                                    sem,
                                    courseCode
                            )
                            if (parsed.isNotEmpty()) {
                                UserDatabase.saveAttendanceSessions(authorizedId, sem, parsed)
                            }
                        }
            } finally {
                activeTasks.remove("detail_$key")
                _loadingAttendanceKeys.value = _loadingAttendanceKeys.value - key
            }
        }
    }

    private fun failureMessage(error: VtopError) =
            when (error) {
                VtopError.SessionExpired ->
                        "VTOP session expired — re-authenticating; showing cached copy"
                VtopError.Network -> "Offline — showing the cached copy"
                is VtopError.Blocked ->
                        "VTOP is blocking this app right now — showing the cached copy"
                else -> "VTOP unreachable — showing the cached copy"
            }
}

/**
 * Menu map extracted from /vtop/content (see docs/vtop-session.md). Keyed by VTOP section heading.
 */
fun menuSections(): List<Pair<String, List<MenuItem>>> =
        listOf(
                "Student" to
                        listOf(
                                MenuItem(
                                        "Profile",
                                        "studentsRecord/StudentProfileAllView",
                                        "Student"
                                ),
                                MenuItem(
                                        "Credentials",
                                        "proctor/viewStudentCredentials",
                                        "Student"
                                ),
                                MenuItem("Spot-light", "spotlight/spotlightViewOld", "Student"),
                                MenuItem(
                                        "Acknowledgement View",
                                        "admissions/AcknowledgmentView",
                                        "Student"
                                ),
                                MenuItem(
                                        "Student Bank Info",
                                        "studentBankInformation/BankInfoStudent",
                                        "Student"
                                ),
                                MenuItem("Contact Us", "hrms/contactDetails", "Student"),
                        ),
                "Academics" to
                        listOf(
                                MenuItem(
                                        "Class Messages",
                                        "academics/common/StudentClassMessage",
                                        "Academics"
                                ),
                                MenuItem(
                                        "My Curriculum",
                                        "academics/common/Curriculum",
                                        "Academics"
                                ),
                                MenuItem(
                                        "Time Table",
                                        "academics/common/StudentTimeTable",
                                        "Academics"
                                ),
                                MenuItem(
                                        "Class Attendance",
                                        "academics/common/StudentAttendance",
                                        "Academics"
                                ),
                                MenuItem(
                                        "Course Page",
                                        "academics/common/StudentCoursePage",
                                        "Academics"
                                ),
                                MenuItem(
                                        "Academics Calendar",
                                        "academics/common/CalendarPreview",
                                        "Academics"
                                ),
                                MenuItem(
                                        "Course Withdraw",
                                        "academics/withdraw/courseWithdraw",
                                        "Academics"
                                ),
                        ),
                "COE" to
                        listOf(
                                MenuItem("Exam Schedule", "examinations/StudExamSchedule", "COE"),
                                MenuItem("Marks", "examinations/StudentMarkView", "COE"),
                                MenuItem(
                                        "Grades",
                                        "examinations/examGradeView/StudentGradeView",
                                        "COE"
                                ),
                                MenuItem(
                                        "Grade History",
                                        "examinations/examGradeView/StudentGradeHistory",
                                        "COE"
                                ),
                        ),
                "Hostels" to
                        listOf(
                                MenuItem(
                                        "Late Hour Request",
                                        "hostels/late/hour/student/request/1",
                                        "Hostels"
                                ),
                                MenuItem("Leave Request", "hostels/student/leave/1", "Hostels"),
                                MenuItem(
                                        "Leave History",
                                        "hostels/student/barcodeleave",
                                        "Hostels"
                                ),
                                MenuItem(
                                        "Hostel Room Allotment",
                                        "hostels/room/allotment/home",
                                        "Hostels"
                                ),
                        ),
                "Finance" to
                        listOf(
                                MenuItem("Payments", "finance/Payments", "Finance"),
                                MenuItem(
                                        "Payment Receipts",
                                        "finance/getStudentReceipts",
                                        "Finance"
                                ),
                                MenuItem("Fees Intimation", "finance/getFeesIntimation", "Finance"),
                        ),
                "Others" to
                        listOf(
                                MenuItem(
                                        "Apply Bonafide",
                                        "others/bonafide/StudentBonafidePageControl",
                                        "Others"
                                ),
                                MenuItem(
                                        "Support & Complaints",
                                        "ticketrise/createTicket",
                                        "Others"
                                ),
                                MenuItem(
                                        "Change Password",
                                        "controlpanel/ChangePassword",
                                        "Others"
                                ),
                        ),
        )
