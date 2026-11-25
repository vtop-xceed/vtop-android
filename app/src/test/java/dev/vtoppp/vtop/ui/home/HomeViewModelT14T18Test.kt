package dev.vtoppp.vtop.ui.home

import dev.vtoppp.vtop.data.AutoCaptchaClient
import dev.vtoppp.vtop.data.PillKind
import dev.vtoppp.vtop.data.SessionCache
import dev.vtoppp.vtop.data.VtopError
import dev.vtoppp.vtop.data.WarmState
import dev.vtoppp.vtop.data.pillOf
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pure-JVM tests for T14 (backToMenu invalidates in-flight state) and T18.3
 * (session pill keyed on the live session). The client object is constructed
 * without touching the network; open() is only asserted up to its
 * synchronous Loading publication (T18.5), never past the IO launch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelT14T18Test {

    private val testDispatcher = UnconfinedTestDispatcher()
    private var tempDir: File? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tempDir = Files.createTempDirectory("session_cache_test").toFile()
        SessionCache.init(tempDir!!)
    }

    @After
    fun tearDown() {
        tempDir?.deleteRecursively()
        // T-test-leak (observed 2026-10-27): do NOT resetMain. vm.open()
        // launches the real client on the global IO pool, and a straggler
        // continuation that outlives the test evaluates Dispatchers.Main;
        // after resetMain() that getter throws (main looper never mocked) and
        // the uncaught exception poisons whichever runTest opens next, failing
        // an unrelated test ~4/6 runs. With Main kept valid nothing can crash:
        // a cancelled continuation fails fast, a leaked one writes its state
        // to an already-dropped ViewModel instance. Every other class that
        // needs Main re-declares its own dispatchers in @Before.
    }

    private fun newVm() = HomeViewModel(
        client = dev.vtoppp.vtop.data.VtopClient(AutoCaptchaClient()),
        authorizedId = "EXAMPLE0000001",
    )

    private val sample = MenuItem("Profile", "studentsRecord/StudentProfileAllView", "Student")

    // ------------------------------------------------------------- T14

    @Test
    fun backToMenuReturnsToReadyAndClearsInFlightTitleFlow() = runTest {
        val vm = newVm()
        // Start a section open (publishes Loading synchronously, launches IO).
        vm.open(sample)
        assertTrue(vm.state.value is HomeUiState.Loading)
        // Leaving the section must invalidate the in-flight job and land on
        // the menu, whatever the abandoned fetch would later resolve to.
        vm.backToMenu()
        val state = vm.state.value
        assertTrue(state is HomeUiState.Ready)
        assertEquals(
            "EXAMPLE0000001",
            (state as HomeUiState.Ready).authorizedId,
        )
        assertNull(vm.staleNotice.value)
    }

    @Test
    fun backToMenuIdsStaleOutcomeGenerationViaCancel() {
        // Direct evidence for the T14 contract: openJob is cancelled and
        // openGeneration is bumped (static check mirrored in the diff).
        val source = java.io.File(
            "app/src/main/java/dev/vtoppp/vtop/ui/home/HomeViewModel.kt",
        ).let { if (it.exists()) it.readText() else java.io.File("src/main/java/dev/vtoppp/vtop/ui/home/HomeViewModel.kt").readText() }
        assertTrue(source.contains("openJob?.cancel()\n        openGeneration++"))
    }

    // ------------------------------------------------------------- T18.3

    @Test
    fun idleWarmWithoutLiveSessionIsNotClaimedReady() {
        val pill = pillOf(WarmState.Idle, hasSession = false)
        assertEquals(PillKind.Busy, pill.kind)
        assertTrue(pill.pulse)
    }

    @Test
    fun idleWarmWithLiveSessionIsReady() {
        val pill = pillOf(WarmState.Idle, hasSession = true)
        assertEquals(PillKind.Ready, pill.kind)
    }

    @Test
    fun captchaReadyWithoutLiveSessionStaysBusy() {
        val pill = pillOf(WarmState.CaptchaReady, hasSession = false)
        assertEquals(PillKind.Busy, pill.kind)
    }

    // ------------------------------------------------ T18.5 Loading first

    @Test
    fun openPublishesLoadingBeforeAnyDiskRead() = runTest {
        val vm = newVm()
        vm.open(sample)
        // Deterministic: the Loading publication is synchronous before the
        // IO coroutine can publish cached/network content.
        assertTrue(vm.state.value is HomeUiState.Loading)
    }

    @Test
    fun loadAttendanceDetailsHandlesBlankCourseId() {
        val vm = newVm()
        vm.loadAttendanceDetails("BL20262701", "", "LT", "CSE1001")
    }

    // ------------------------------------------------------------- Offline Fail-Fast & Rebuild Guard

    @Test
    fun offlineStartupDoesNotEnterRebuildingAndFailsFast() = runTest {
        val vm = HomeViewModel(
            client = dev.vtoppp.vtop.data.VtopClient(AutoCaptchaClient()),
            authorizedId = "EMPTY_USER_OFFLINE",
            isOnline = { false },
        )
        // Does not enter isRebuilding on offline init
        assertTrue(!vm.isRebuilding.value)

        // refresh methods fail-fast without network calls
        vm.refreshOverview(force = true)
        vm.refreshMarksAndGrades(force = true)
        vm.refreshCalendar(force = true)
        vm.loadAttendanceDetails("BL20262701", "CSE1001", "LT", "CSE1001")

        assertTrue(!vm.isRebuilding.value)
        assertEquals("Offline — showing cached copy", vm.marksNotice.value)
    }

    @Test
    fun offlineOpenUncachedEmitsNetworkFailureImmediately() = runTest {
        SessionCache.clear()
        val uncachedItem = MenuItem("Uncached Section", "examinations/UncachedEndpoint", "COE")
        val vm = HomeViewModel(
            client = dev.vtoppp.vtop.data.VtopClient(AutoCaptchaClient()),
            authorizedId = "EXAMPLE0000001",
            isOnline = { false },
        )
        vm.open(uncachedItem)
        var attempts = 0
        while (attempts++ < 50 && vm.state.value is HomeUiState.Loading) {
            kotlinx.coroutines.delay(20)
        }
        val state = vm.state.value
        assertTrue("Expected Failure state, got $state", state is HomeUiState.Failure)
        val failure = state as HomeUiState.Failure
        assertEquals(VtopError.Network, failure.error)
        assertEquals(uncachedItem, failure.item)
    }

    @Test
    fun offlineOpenCachedKeepsSectionReadyWithOfflineNotice() = runTest {
        val cachedItem = MenuItem("Cached Section", "academics/common/Curriculum", "Academics")
        SessionCache.save(cachedItem.endpoint, "<html><body>Cached Curriculum Content</body></html>")
        val vm = HomeViewModel(
            client = dev.vtoppp.vtop.data.VtopClient(AutoCaptchaClient()),
            authorizedId = "EXAMPLE0000001",
            isOnline = { false },
        )
        vm.open(cachedItem)
        var attempts = 0
        while (attempts++ < 50 && (vm.state.value is HomeUiState.Loading || vm.staleNotice.value == null)) {
            kotlinx.coroutines.delay(20)
        }
        val state = vm.state.value
        assertTrue("Expected SectionReady state, got $state", state is HomeUiState.SectionReady)
        val ready = state as HomeUiState.SectionReady
        assertTrue(ready.fromCache)
        assertEquals("Offline — showing the cached copy", vm.staleNotice.value)
    }

    @Test
    fun offlineManualPurgeAndRebuildDoesNotClearCacheOrDatabase() = runTest {
        SessionCache.save("test_key_preserve", "test_data_to_preserve")
        val vm = HomeViewModel(
            client = dev.vtoppp.vtop.data.VtopClient(AutoCaptchaClient()),
            authorizedId = "EXAMPLE0000001",
            isOnline = { false },
        )
        vm.purgeAndRebuild(isInitialStartup = false)
        // Cache is preserved and not cleared
        assertEquals("test_data_to_preserve", SessionCache.load("test_key_preserve")?.first)
        assertEquals("Offline — cached data preserved", vm.rebuildStep.value)
        assertTrue(!vm.isRebuilding.value)
    }

    @Test
    fun onSessionReadyClearsStaleAndMarksNotices() {
        val vm = HomeViewModel(
            client = dev.vtoppp.vtop.data.VtopClient(AutoCaptchaClient()),
            authorizedId = "EXAMPLE0000001",
            isOnline = { false },
        )
        vm.onSessionReady()
        assertNull(vm.staleNotice.value)
        assertNull(vm.marksNotice.value)
    }
}
