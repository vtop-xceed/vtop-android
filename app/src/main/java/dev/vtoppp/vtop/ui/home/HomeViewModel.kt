package dev.vtoppp.vtop.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vtoppp.vtop.data.SessionCache
import dev.vtoppp.vtop.data.VtopClient
import dev.vtoppp.vtop.data.VtopError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    private val _lastSectionTitle = MutableStateFlow<String?>(null)
    val lastSectionTitle: StateFlow<String?> = _lastSectionTitle.asStateFlow()

    private var openJob: Job? = null
    private var openGeneration = 0
    private var lastItem: MenuItem? = null

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
