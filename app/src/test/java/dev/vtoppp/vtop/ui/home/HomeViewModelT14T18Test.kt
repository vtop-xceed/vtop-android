package dev.vtoppp.vtop.ui.home

import dev.vtoppp.vtop.data.AutoCaptchaClient
import dev.vtoppp.vtop.data.PillKind
import dev.vtoppp.vtop.data.WarmState
import dev.vtoppp.vtop.data.pillOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
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

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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
            "src/main/java/dev/vtoppp/vtop/ui/home/HomeViewModel.kt",
        ).readText()
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
}
