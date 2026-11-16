package dev.vtoppp.vtop.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class AccentHsvTest {

    @Test
    fun hsvRoundTripKeepsTheColour() {
        listOf(
            Color(0xFF6B4EA2),
            Color(0xFF0B57D0),
            Color(0xFF006D44),
            Color(0xFFFF0000),
            Color(0xFF00FF00),
            Color(0xFF0000FF),
        ).forEach { color ->
            val hsv = color.hsv()
            assertEquals(color, Color.hsv(hsv[0], hsv[1], hsv[2]))
        }
    }
}
