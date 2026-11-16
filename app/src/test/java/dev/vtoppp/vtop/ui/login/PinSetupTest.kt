package dev.vtoppp.vtop.ui.login

import org.junit.Assert.assertEquals
import org.junit.Test

class PinSetupTest {

    @Test
    fun theFirstEntryAsksForConfirmation() {
        assertEquals(
            PinSetupOutcome.NeedsConfirm,
            pinSetupOutcome(PinSetupStep.Create, null, "1234"),
        )
    }

    @Test
    fun matchingEntriesSave() {
        assertEquals(
            PinSetupOutcome.Saved,
            pinSetupOutcome(PinSetupStep.Confirm, "1234", "1234"),
        )
    }

    @Test
    fun differingEntriesStartOver() {
        assertEquals(
            PinSetupOutcome.Mismatch,
            pinSetupOutcome(PinSetupStep.Confirm, "1234", "4321"),
        )
    }
}
