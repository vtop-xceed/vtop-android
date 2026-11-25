package dev.vtoppp.vtop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Surface, foreground, and border colors for an attendance or status badge. */
data class AttendanceTone(
    val container: Color,
    val content: Color,
    val border: Color,
)

object StatusColors {
    val Success = Color(0xFF2E7D32)
    val Warning = Color(0xFFFFA000)

    /** Resolves light/dark/AMOLED badge tones for an attendance percentage (<75 danger, 75..79 warning, >=80 good). */
    fun attendanceTone(percentage: Int, isDark: Boolean, isAmoled: Boolean = false): AttendanceTone = when {
        percentage < 75 -> resolveTone(
            lightBg = Color(0xFFFEE2E2),
            darkBg = Color(0xFF381414),
            amoledBg = Color(0xFF260A0A),
            lightFg = Color(0xFFB91C1C),
            darkFg = Color(0xFFF87171),
            borderBase = Color(0xFFEF4444),
            amoledBorderAlpha = 0.65f,
            isDark = isDark,
            isAmoled = isAmoled,
        )
        percentage in 75..79 -> resolveTone(
            lightBg = Color(0xFFFEF3C7),
            darkBg = Color(0xFF382808),
            amoledBg = Color(0xFF261904),
            lightFg = Color(0xFFB45309),
            darkFg = Color(0xFFFBBF24),
            borderBase = Color(0xFFF59E0B),
            amoledBorderAlpha = 0.65f,
            isDark = isDark,
            isAmoled = isAmoled,
        )
        else -> resolveTone(
            lightBg = Color(0xFFDCFCE7),
            darkBg = Color(0xFF0F2E1B),
            amoledBg = Color(0xFF072111),
            lightFg = Color(0xFF15803D),
            darkFg = Color(0xFF4ADE80),
            borderBase = AttendanceGreen,
            amoledBorderAlpha = 0.60f,
            isDark = isDark,
            isAmoled = isAmoled,
        )
    }

    /** Resolves light/dark/AMOLED badge tones for informational statuses (e.g., On Duty). */
    fun infoTone(isDark: Boolean, isAmoled: Boolean = false): AttendanceTone = resolveTone(
        lightBg = Color(0xFFDBEAFE),
        darkBg = Color(0xFF102544),
        amoledBg = Color(0xFF081C36),
        lightFg = Color(0xFF1D4ED8),
        darkFg = Color(0xFF60A5FA),
        borderBase = Color(0xFF3B82F6),
        amoledBorderAlpha = 0.65f,
        isDark = isDark,
        isAmoled = isAmoled,
    )

    /** Composable convenience wrapper around [attendanceTone] reading dark/AMOLED state from [MaterialTheme]. */
    @Composable
    fun attendanceBadge(percentage: Int): AttendanceTone {
        val surface = MaterialTheme.colorScheme.surface
        val isDark = surface.luminance() < 0.5f
        return attendanceTone(percentage, isDark = isDark, isAmoled = isDark && surface == Color.Black)
    }

    /** Composable convenience wrapper around [infoTone] reading dark/AMOLED state from [MaterialTheme]. */
    @Composable
    fun infoBadge(): AttendanceTone {
        val surface = MaterialTheme.colorScheme.surface
        val isDark = surface.luminance() < 0.5f
        return infoTone(isDark = isDark, isAmoled = isDark && surface == Color.Black)
    }

    private fun resolveTone(
        lightBg: Color,
        darkBg: Color,
        amoledBg: Color,
        lightFg: Color,
        darkFg: Color,
        borderBase: Color,
        amoledBorderAlpha: Float,
        isDark: Boolean,
        isAmoled: Boolean,
    ): AttendanceTone = AttendanceTone(
        container = when {
            !isDark -> lightBg
            isAmoled -> amoledBg
            else -> darkBg
        },
        content = if (!isDark) lightFg else darkFg,
        border = borderBase.copy(alpha = if (isAmoled) amoledBorderAlpha else 0.45f),
    )
}

val AttendanceGreen = Color(0xFF22C55E)
