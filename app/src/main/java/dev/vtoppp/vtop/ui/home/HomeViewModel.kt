package dev.vtoppp.vtop.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vtoppp.vtop.data.AcademicCalendarParser
import dev.vtoppp.vtop.data.AppLog
import dev.vtoppp.vtop.data.AttendanceDetailParser
import dev.vtoppp.vtop.data.AttendanceEntry
import dev.vtoppp.vtop.data.AttendanceParser
import dev.vtoppp.vtop.data.AttendanceSession
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
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
    private val syncInProgress = AtomicBoolean(false)
    private val activeTasks = ConcurrentHashMap.newKeySet<String>()
    private val refreshedInSession = ConcurrentHashMap.newKeySet<String>()
    // ponytail: cap concurrent VTOP portal requests at 3 to speed up sync without tripping WAF limits
    private val portalGate = Semaphore(3)

    companion object {
        private const val ACTIVE_FRESH_TTL_MS = 10 * 60 * 1000L // 10 min
        private const val CALENDAR_FRESH_TTL_MS = 6 * 60 * 60 * 1000L // 6 hr
        private val LOG_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            if (UserDatabase.isDatabaseEmpty(authorizedId)) {
                hydrateFromSessionCache()
                if (UserDatabase.isDatabaseEmpty(authorizedId) &&
                    isOnline() &&
                    (client.hasSession || client.warm.value !is WarmState.Failed)
                ) {
                    purgeAndRebuild(isInitialStartup = true)
                }
            } else {
                UserDatabase.loadAllCached(authorizedId)
            }
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
                    CALENDAR_SEMESTER_ID).distinct()
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
        val time = LocalTime.now().format(LOG_TIME_FORMATTER)
        _rebuildLogs.update { it + "[$time] $msg" }
    }

    private suspend fun <T> withPortalGate(block: suspend () -> T): T =
        portalGate.withPermit { block() }

    private suspend fun fetchProfileAndGrades(
        fetchGrades: Boolean = true,
        log: ((String) -> Unit)? = null,
    ): Boolean = coroutineScope {
        if (!isOnline()) return@coroutineScope false
        val gradeDeferred = if (fetchGrades) {
            async {
                if (!isOnline()) return@async null
                withPortalGate { client.fetchSection("examinations/examGradeView/StudentGradeHistory") }
            }
        } else null
        val profileDeferred = async {
            if (!isOnline()) return@async null
            withPortalGate { client.fetchSection("studentsRecord/StudentProfileAllView") }
        }

        var gradesOk = true
        if (gradeDeferred != null) {
            val gradeResult = gradeDeferred.await()
            if (gradeResult == null) {
                profileDeferred.cancel()
                return@coroutineScope false
            }
            val err = gradeResult.exceptionOrNull()
            if (err != null) {
                log?.invoke("Warning: Could not fetch StudentGradeHistory: ${err.message}")
                if (isConnectivityOrSessionError(err)) {
                    profileDeferred.cancel()
                    return@coroutineScope false
                }
                gradesOk = false
            } else {
                val html = gradeResult.getOrNull().orEmpty()
                SessionCache.save("examinations/examGradeView/StudentGradeHistory", html)
                saveGradeHistoryFromPage(html)
                log?.invoke("Parsed cumulative grade history & profile")
            }
        }

        val profileResult = profileDeferred.await() ?: return@coroutineScope false
        var profileOk = true
        profileResult.onSuccess { html ->
            saveProfileFromPage(html)
            log?.invoke("Parsed StudentProfileAllView details")
        }.onFailure { err ->
            if (isConnectivityOrSessionError(err)) return@coroutineScope false
            profileOk = false
        }
        if (fetchGrades) (gradesOk || profileOk) else profileOk
    }

    private suspend fun fetchCalendarFromVtop(log: ((String) -> Unit)? = null): Boolean {
        if (!isOnline()) return false
        val calSemId = CALENDAR_SEMESTER_ID
        val dateResult = withPortalGate {
            client.postSubAction(
                "getDateForSemesterPreview",
                mapOf("semSubId" to calSemId, "paramReturnId" to "getDateForSemesterPreview"),
            )
        }
        if (dateResult.isFailure) return false
        val dateHtml = dateResult.getOrNull().orEmpty()
        val monthDates = AcademicCalendarParser.extractMonthDates(dateHtml).ifEmpty {
            listOf(
                "01-JUN-2026", "01-JUL-2026", "01-AUG-2026",
                "01-SEP-2026", "01-OCT-2026", "01-NOV-2026",
            )
        }
        val allEvents = mutableListOf<CalendarDayEvent>()
        val aborted = AtomicBoolean(false)
        coroutineScope {
            val jobs = monthDates.map { mDate ->
                async {
                    if (aborted.get() || !isOnline()) return@async emptyList()
                    val monthRes = withPortalGate {
                        client.postSubAction(
                            "processViewCalendar",
                            mapOf("semSubId" to calSemId, "classGroupId" to "COMB", "calDate" to mDate),
                        )
                    }
                    monthRes.fold(
                        onSuccess = { mHtml -> AcademicCalendarParser.parse(mHtml, mDate) },
                        onFailure = { err ->
                            if (isConnectivityOrSessionError(err)) aborted.set(true)
                            emptyList()
                        },
                    )
                }
            }
            for (job in jobs) {
                allEvents.addAll(job.await())
            }
        }
        if (aborted.get()) return false
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
        val attResult = withPortalGate {
            client.postSubAction("processViewStudentAttendance", mapOf("semesterSubId" to semId))
        }
        if (attResult.isFailure) {
            log?.invoke("[$semId] Attendance fetch error: ${attResult.exceptionOrNull()?.message}")
            return false
        }
        val attHtml = attResult.getOrNull().orEmpty()
        SessionCache.save("processViewStudentAttendance_$semId", attHtml)
        val attEntries = AttendanceParser.parse(attHtml, authorizedId, semId)
        if (attEntries.isEmpty()) {
            log?.invoke("[$semId] No attendance records found")
            return true
        }

        // Compute changed courses BEFORE saveAttendance overwrites the prior rows.
        val coursesToFetch = if (includeAllDetails) {
            attEntries.filter { it.courseId.isNotBlank() }
        } else {
            val previousByCode = UserDatabase.loadAttendance(authorizedId, semId)
                .associateBy { it.courseCode.uppercase() }
            val existingSessionsByCourse = UserDatabase.attendanceSessionsFlow.value
                .filter { it.regNo == authorizedId && it.semesterSubId == semId }
                .ifEmpty {
                    UserDatabase.loadAttendanceSessions(authorizedId)
                        .filter { it.semesterSubId == semId }
                }
                .groupBy { it.courseCode.uppercase() }

            attEntries.filter { entry ->
                if (entry.courseId.isBlank()) return@filter false
                val code = entry.courseCode.uppercase()
                val prev = previousByCode[code]
                val hasSessions = !existingSessionsByCourse[code].isNullOrEmpty()
                val changed = prev == null ||
                    prev.attendedClasses != entry.attendedClasses ||
                    prev.totalClasses != entry.totalClasses
                !hasSessions || changed
            }
        }

        UserDatabase.saveAttendance(authorizedId, attEntries)
        log?.invoke("[$semId] Cached ${attEntries.size} attendance records")

        if (coursesToFetch.isEmpty()) return true

        val batchedSessions = mutableListOf<AttendanceSession>()
        val aborted = AtomicBoolean(false)
        try {
            coroutineScope {
                val jobs = coursesToFetch.map { entry ->
                    async {
                        if (aborted.get() || !isOnline()) return@async emptyList()
                        val detailRes = withPortalGate {
                            client.postSubAction(
                                "processViewAttendanceDetail",
                                mapOf(
                                    "semesterSubId" to semId,
                                    "registerNumber" to authorizedId,
                                    "courseId" to entry.courseId,
                                    "courseType" to entry.courseTypeA.ifBlank { "LT" },
                                ),
                            )
                        }
                        val err = detailRes.exceptionOrNull()
                        if (err != null) {
                            if (isConnectivityOrSessionError(err)) aborted.set(true)
                            return@async emptyList()
                        }
                        val parsed = parseAndCacheAttendanceDetail(
                            semId = semId,
                            courseId = entry.courseId,
                            courseCode = entry.courseCode,
                            detailHtml = detailRes.getOrNull().orEmpty(),
                        )
                        if (parsed.isNotEmpty()) {
                            log?.invoke("[$semId] Cached ${parsed.size} classes for ${entry.courseCode}")
                        }
                        parsed
                    }
                }
                for (job in jobs) {
                    batchedSessions.addAll(job.await())
                }
            }
        } finally {
            if (batchedSessions.isNotEmpty()) {
                UserDatabase.saveAttendanceSessions(authorizedId, semId, batchedSessions)
            }
        }
        return !aborted.get()
    }

    private suspend fun fetchTimetableForSemester(semId: String, log: ((String) -> Unit)? = null): Boolean {
        if (!isOnline()) return false
        val ttResult = withPortalGate {
            client.postSubAction("processViewTimeTable", mapOf("semesterSubId" to semId))
        }
        if (ttResult.isFailure) {
            log?.invoke("[$semId] Timetable fetch error: ${ttResult.exceptionOrNull()?.message}")
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
    ): Boolean = coroutineScope {
        if (!isOnline()) return@coroutineScope false
        val gpaDeferred = if (includeGpa) {
            async {
                if (!isOnline()) return@async null
                withPortalGate {
                    client.postSubAction(
                        "examinations/examGradeView/doStudentGradeView",
                        mapOf("semesterSubId" to semId),
                    )
                }
            }
        } else null
        val marksDeferred = async {
            if (!isOnline()) return@async null
            withPortalGate {
                client.postSubAction(
                    "examinations/doStudentMarkView",
                    mapOf("semesterSubId" to semId),
                )
            }
        }

        if (gpaDeferred != null) {
            val gpaRes = gpaDeferred.await()
            if (gpaRes == null) {
                marksDeferred.cancel()
                return@coroutineScope false
            }
            val err = gpaRes.exceptionOrNull()
            if (err != null && isConnectivityOrSessionError(err)) {
                marksDeferred.cancel()
                return@coroutineScope false
            }
            gpaRes.onSuccess { resultHtml ->
                SessionCache.save("examinations/doStudentGradeView_$semId", resultHtml)
                ResultPageParser.parseGpa(resultHtml)?.let { gpa ->
                    UserDatabase.saveSemesterResultGpa(authorizedId, semId, gpa)
                }
            }
        }

        val marksRes = marksDeferred.await() ?: return@coroutineScope false
        if (marksRes.isFailure) {
            log?.invoke("[$semId] Marks fetch error: ${marksRes.exceptionOrNull()?.message}")
            return@coroutineScope false
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
        true
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

                    _rebuildStep.value = "Caching $cleanSemName (Attendance, Timetable, Marks)..."
                    appendLog("[$semId] Syncing attendance, timetable & marks...")
                    val doneInSem = AtomicInteger(0)
                    fun bumpProgress() {
                        val stepFraction = when (doneInSem.incrementAndGet()) {
                            1 -> 0.35f
                            2 -> 0.70f
                            else -> 1.0f
                        }
                        _rebuildProgress.update { maxOf(it, baseProgress + (semFraction * stepFraction)) }
                    }
                    val (attOk, ttOk, marksOk) = coroutineScope {
                        val attDef = async {
                            fetchAttendanceForSemester(semId, includeAllDetails = true, log = ::appendLog)
                                .also { bumpProgress() }
                        }
                        val ttDef = async {
                            fetchTimetableForSemester(semId, log = ::appendLog)
                                .also { bumpProgress() }
                        }
                        val marksDef = async {
                            fetchMarksForSemester(semId, includeGpa = true, log = ::appendLog)
                                .also { bumpProgress() }
                        }
                        Triple(attDef.await(), ttDef.await(), marksDef.await())
                    }
                    if ((!attOk || !ttOk || !marksOk) && !isOnline()) break
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

    private suspend fun ensureLatestSemesterId(): String? {
        val existing = UserDatabase.getLatestSemesterId(authorizedId)
        if (!existing.isNullOrBlank()) return existing
        val sems = resolveAttendedSemesters()
        saveAttendedSemesters(sems)
        return sems.firstOrNull()?.first
    }

    private inline fun semesterStalenessTs(
        semesterSubId: String?,
        maxUpdatedAt: (String) -> Long,
    ): Long {
        val latestId = UserDatabase.getLatestSemesterId(authorizedId)
        val semId = semesterSubId ?: latestId ?: return 0L
        val maxTs = maxUpdatedAt(semId)
        return when {
            maxTs == 0L -> 0L
            latestId != null && semId != latestId -> System.currentTimeMillis()
            else -> maxTs
        }
    }

    fun refreshOverview(force: Boolean = false) {
        launchLazy(
            key = "overview",
            force = force,
            ttlMs = ACTIVE_FRESH_TTL_MS,
            lastUpdatedMs = {
                val latestSem = UserDatabase.getLatestSemesterId(authorizedId) ?: return@launchLazy 0L
                val attTs = UserDatabase.loadAttendance(authorizedId, latestSem).maxOfOrNull { it.updatedAtMs } ?: 0L
                if (attTs == 0L) return@launchLazy 0L
                val ttTs = UserDatabase.loadTimetable(authorizedId, latestSem).maxOfOrNull { it.updatedAtMs } ?: 0L
                minOf(attTs, ttTs)
            },
        ) {
            val latestSem = ensureLatestSemesterId()
            val profileMissing = UserDatabase.profileFlow.value?.takeIf { it.regNo == authorizedId } == null &&
                UserDatabase.loadProfile(authorizedId) == null
            val needProfile = force || profileMissing
            coroutineScope {
                val attDef = if (!latestSem.isNullOrBlank()) {
                    async { fetchAttendanceForSemester(latestSem, includeAllDetails = false) }
                } else null
                val ttDef = if (!latestSem.isNullOrBlank()) {
                    async { fetchTimetableForSemester(latestSem) }
                } else null
                val profDef = if (needProfile) async { fetchProfileAndGrades() } else null
                val attOk = attDef?.await() ?: false
                val ttOk = ttDef?.await() ?: false
                val profOk = profDef?.await() ?: true
                attOk && ttOk && profOk
            }
        }
    }

    fun refreshAttendance(semesterSubId: String? = null, force: Boolean = false) {
        val targetKey = "att_${semesterSubId ?: "latest"}"
        launchLazy(
            key = targetKey,
            force = force,
            ttlMs = ACTIVE_FRESH_TTL_MS,
            lastUpdatedMs = {
                semesterStalenessTs(semesterSubId) { semId ->
                    UserDatabase.loadAttendance(authorizedId, semId).maxOfOrNull { it.updatedAtMs } ?: 0L
                }
            },
        ) {
            val semId = semesterSubId ?: ensureLatestSemesterId()
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
                semesterStalenessTs(semesterSubId) { semId ->
                    UserDatabase.loadTimetable(authorizedId, semId).maxOfOrNull { it.updatedAtMs } ?: 0L
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
            withPortalGate { client.fetchSection("academics/common/StudentAttendance") }.onSuccess { html ->
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
                val latestId = UserDatabase.getLatestSemesterId(authorizedId)
                val targetSem = semesterSubId ?: latestId ?: return@launchLazy 0L
                // Load all marks (or reuse full flow) so _marksFlow isn't narrowed to a single semester.
                val marksTs = UserDatabase.loadMarks(authorizedId)
                    .asSequence()
                    .filter { it.semesterSubId == targetSem }
                    .maxOfOrNull { it.updatedAtMs } ?: 0L
                if (marksTs == 0L) return@launchLazy 0L
                if (latestId != null && targetSem != latestId) return@launchLazy System.currentTimeMillis()
                val (summary, grades) = UserDatabase.loadGradeHistory(authorizedId)
                val gradesTs = maxOf(summary?.updatedAtMs ?: 0L, grades.maxOfOrNull { it.updatedAtMs } ?: 0L)
                minOf(marksTs, gradesTs)
            },
        ) {
            val existingGrades = UserDatabase.gradeHistoryFlow.value
                .filter { it.regNo == authorizedId }
                .ifEmpty { UserDatabase.loadGradeHistory(authorizedId).second }
            val existingMarks = UserDatabase.marksFlow.value
                .filter { it.regNo == authorizedId }
                .ifEmpty { UserDatabase.loadMarks(authorizedId) }
            val hasCachedData = existingGrades.isNotEmpty() || existingMarks.isNotEmpty()
            try {
                val profileOk = fetchProfileAndGrades()
                if (!profileOk && hasCachedData) {
                    if (force) _marksNotice.value = "VTOP unreachable — showing cached copy"
                    return@launchLazy false
                }
                var attended = UserDatabase.semestersFlow.value
                    .filter { it.regNo == authorizedId }
                    .ifEmpty { UserDatabase.loadSemesters(authorizedId) }
                    .map { it.semesterSubId to it.semesterName }
                if (attended.isEmpty()) {
                    attended = resolveAttendedSemesters()
                    saveAttendedSemesters(attended)
                }
                val latestId = attended.firstOrNull()?.first
                val existingResults = UserDatabase.semesterResultsFlow.value
                    .filter { it.regNo == authorizedId }
                    .ifEmpty { UserDatabase.loadSemesterResults(authorizedId) }
                    .map { it.semesterSubId }
                    .toSet()
                val existingMarksSems = existingMarks.map { it.semesterSubId }.toSet()
                val targets = attended.map { it.first }.filter { semId ->
                    semId == (semesterSubId ?: latestId) ||
                        semId !in existingResults ||
                        semId !in existingMarksSems
                }
                val anyMarksFailed = AtomicBoolean(false)
                coroutineScope {
                    targets.map { semId ->
                        async {
                            if (!fetchMarksForSemester(semId, includeGpa = true)) {
                                anyMarksFailed.set(true)
                            }
                        }
                    }.forEach { it.await() }
                }
                if (anyMarksFailed.get() && hasCachedData) {
                    if (force) _marksNotice.value = "VTOP unreachable — showing cached copy"
                    return@launchLazy false
                }
                val hasAnyNow = hasCachedData ||
                    UserDatabase.gradeHistoryFlow.value.any { it.regNo == authorizedId } ||
                    UserDatabase.marksFlow.value.any { it.regNo == authorizedId }
                if (!hasAnyNow) {
                    _marksNotice.value = "VTOP unreachable — no data cached yet"
                    false
                } else {
                    _marksNotice.value = null
                    !anyMarksFailed.get() && profileOk
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                AppLog.d("refreshMarksAndGrades failed: ${t.javaClass.simpleName}")
                val hasCached = hasCachedData ||
                    UserDatabase.gradeHistoryFlow.value.any { it.regNo == authorizedId } ||
                    UserDatabase.marksFlow.value.any { it.regNo == authorizedId }
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
                    val hasCached = cached != null
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
                                if (hasCached) {
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
                                                    when (item.endpoint) {
                                                        "studentsRecord/StudentProfileAllView" ->
                                                            saveProfileFromPage(html)
                                                        "academics/common/StudentTimeTable" -> {
                                                            val entries = TimetableParser.parse(html, authorizedId)
                                                            if (entries.isNotEmpty()) {
                                                                UserDatabase.saveTimetable(authorizedId, entries)
                                                            }
                                                        }
                                                        "examinations/examGradeView/StudentGradeHistory" ->
                                                            saveGradeHistoryFromPage(html)
                                                        "examinations/StudentMarkView",
                                                        "examinations/doStudentMarkView" -> {
                                                            val marks = MarksParser.parse(html, authorizedId)
                                                            if (marks.isNotEmpty()) {
                                                                UserDatabase.saveMarks(authorizedId, marks)
                                                            }
                                                        }
                                                    }
                                                    HomeUiState.SectionReady(
                                                            title = item.label,
                                                            html = html,
                                                            cookies = client.cookiePairs(),
                                                    ) as
                                                            HomeUiState?
                                                },
                                                onFailure = { e -> refreshFailure(e, item, hasCached) },
                                        )
                            } catch (e: CancellationException) {
                                throw e
                            } catch (t: Throwable) {
                                refreshFailure(t, item, hasCached)
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
    private fun refreshFailure(error: Throwable, item: MenuItem, hasCached: Boolean): HomeUiState? {
        val err = error as? VtopError ?: VtopError.Unknown(error.message ?: "unknown")
        if (hasCached) {
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

    private fun parseAndCacheAttendanceDetail(
        semId: String,
        courseId: String,
        courseCode: String,
        detailHtml: String,
    ): List<AttendanceSession> {
        SessionCache.save("processViewAttendanceDetail_${semId}_$courseId", detailHtml)
        return AttendanceDetailParser.parse(detailHtml, authorizedId, semId, courseCode)
    }

    private fun findCourseMetaInHtml(html: String, sem: String, courseCode: String): Pair<String, String>? =
        AttendanceParser.parse(html, authorizedId, sem)
            .firstOrNull { it.courseCode.equals(courseCode, ignoreCase = true) && it.courseId.isNotBlank() }
            ?.let { it.courseId to it.courseTypeA }

    fun loadAttendanceDetails(
            semId: String,
            courseId: String,
            courseType: String,
            courseCode: String
    ) {
        if (courseCode.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            val sem = semId.ifBlank { UserDatabase.getLatestSemesterId(authorizedId) ?: return@launch }
            val key = "${sem}_${courseCode.uppercase()}"
            if (!activeTasks.add("detail_$key")) return@launch
            _loadingAttendanceKeys.update { it + key }

            try {
                var resolvedId = courseId
                var resolvedType = courseType
                if (resolvedId.isBlank()) {
                    // Check in-memory attendance flow first before parsing cached HTML.
                    UserDatabase.attendanceFlow.value
                        .firstOrNull {
                            it.regNo == authorizedId &&
                                it.semesterSubId == sem &&
                                it.courseCode.equals(courseCode, ignoreCase = true) &&
                                it.courseId.isNotBlank()
                        }
                        ?.let {
                            resolvedId = it.courseId
                            resolvedType = it.courseTypeA
                        }
                }
                if (resolvedId.isBlank()) {
                    SessionCache.load("processViewStudentAttendance_$sem")?.first?.let { cachedAtt ->
                        findCourseMetaInHtml(cachedAtt, sem, courseCode)?.let { (id, type) ->
                            resolvedId = id
                            resolvedType = type
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

                if (resolvedId.isBlank() && client.hasSession) {
                    client.postSubAction("processViewStudentAttendance", mapOf("semesterSubId" to sem))
                        .getOrNull()?.let { html ->
                            SessionCache.save("processViewStudentAttendance_$sem", html)
                            val att = AttendanceParser.parse(html, authorizedId, sem)
                            if (att.isNotEmpty()) UserDatabase.saveAttendance(authorizedId, att)
                            att.firstOrNull { it.courseCode.equals(courseCode, ignoreCase = true) }?.let { match ->
                                resolvedId = match.courseId
                                resolvedType = match.courseTypeA
                            }
                        }
                }
                if (resolvedId.isBlank() || !isOnline()) return@launch

                client.postSubAction(
                    "processViewAttendanceDetail",
                    mapOf(
                        "semesterSubId" to sem,
                        "registerNumber" to authorizedId,
                        "courseId" to resolvedId,
                        "courseType" to resolvedType.ifBlank { "LT" },
                    ),
                ).onSuccess { detailHtml ->
                    val parsed = parseAndCacheAttendanceDetail(sem, resolvedId, courseCode, detailHtml)
                    if (parsed.isNotEmpty()) {
                        UserDatabase.saveAttendanceSessions(authorizedId, sem, parsed)
                    }
                }
            } finally {
                activeTasks.remove("detail_$key")
                _loadingAttendanceKeys.update { it - key }
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
