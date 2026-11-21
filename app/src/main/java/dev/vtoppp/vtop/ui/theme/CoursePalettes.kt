package dev.vtoppp.vtop.ui.theme

import androidx.compose.ui.graphics.Color

// Shared by every course-colored surface: TimetableDetailsPage grid/chips.
data class CoursePalette(val container: Color, val onContainer: Color, val border: Color)

val LightCoursePalettes = listOf(
    CoursePalette(Color(0xFFDBEAFE), Color(0xFF1E40AF), Color(0xFF3B82F6)), // Sapphire Blue
    CoursePalette(Color(0xFFDCFCE7), Color(0xFF166534), Color(0xFF22C55E)), // Emerald Green
    CoursePalette(Color(0xFFF3E8FF), Color(0xFF6B21A8), Color(0xFFA855F7)), // Purple / Violet
    CoursePalette(Color(0xFFFFEDD5), Color(0xFF9A3412), Color(0xFFF97316)), // Warm Amber
    CoursePalette(Color(0xFFFCE7F3), Color(0xFF9D174D), Color(0xFFEC4899)), // Rose / Magenta
    CoursePalette(Color(0xFFCFFAFE), Color(0xFF155E75), Color(0xFF06B6D4)), // Cyan / Teal
    CoursePalette(Color(0xFFECFCCB), Color(0xFF3F6212), Color(0xFF84CC16)), // Lime
    CoursePalette(Color(0xFFFFE4E6), Color(0xFF9F1239), Color(0xFFF43F5E)), // Crimson Coral
    CoursePalette(Color(0xFFFEF9C3), Color(0xFF854D0E), Color(0xFFEAB308)), // Yellow Gold
    CoursePalette(Color(0xFFE2E8F0), Color(0xFF334155), Color(0xFF64748B)), // Slate
    CoursePalette(Color(0xFFEDE9FE), Color(0xFF581C87), Color(0xFF9333EA)), // Deep Violet
    CoursePalette(Color(0xFFCCFBF1), Color(0xFF115E59), Color(0xFF14B8A6)), // Mint / Seafoam
)

val DarkCoursePalettes = listOf(
    CoursePalette(Color(0xFF132B45), Color(0xFF93C5FD), Color(0xFF3B82F6)), // Sapphire Blue
    CoursePalette(Color(0xFF11352A), Color(0xFF86EFAC), Color(0xFF22C55E)), // Emerald Green
    CoursePalette(Color(0xFF321A45), Color(0xFFD8B4FE), Color(0xFFA855F7)), // Purple / Violet
    CoursePalette(Color(0xFF3D2115), Color(0xFFFDBA74), Color(0xFFF97316)), // Warm Amber
    CoursePalette(Color(0xFF3B1828), Color(0xFFF472B6), Color(0xFFEC4899)), // Rose / Magenta
    CoursePalette(Color(0xFF0F313A), Color(0xFF67E8F9), Color(0xFF06B6D4)), // Cyan / Teal
    CoursePalette(Color(0xFF2D2E16), Color(0xFFD9F99D), Color(0xFF84CC16)), // Lime
    CoursePalette(Color(0xFF3D151B), Color(0xFFFDA4AF), Color(0xFFF43F5E)), // Crimson Coral
    CoursePalette(Color(0xFF382314), Color(0xFFFDE047), Color(0xFFEAB308)), // Yellow Gold
    CoursePalette(Color(0xFF1E293B), Color(0xFFCBD5E1), Color(0xFF64748B)), // Slate
    CoursePalette(Color(0xFF2A1B3D), Color(0xFFE9D5FF), Color(0xFF9333EA)), // Deep Violet
    CoursePalette(Color(0xFF133337), Color(0xFF99F6E4), Color(0xFF14B8A6)), // Mint / Seafoam
)

val AmoledCoursePalettes = listOf(
    CoursePalette(Color(0xFF071424), Color(0xFF93C5FD), Color(0xFF3B82F6)), // Sapphire Blue
    CoursePalette(Color(0xFF061A13), Color(0xFF86EFAC), Color(0xFF22C55E)), // Emerald Green
    CoursePalette(Color(0xFF1C0D29), Color(0xFFD8B4FE), Color(0xFFA855F7)), // Purple / Violet
    CoursePalette(Color(0xFF261208), Color(0xFFFDBA74), Color(0xFFF97316)), // Warm Amber
    CoursePalette(Color(0xFF260C18), Color(0xFFF472B6), Color(0xFFEC4899)), // Rose / Magenta
    CoursePalette(Color(0xFF061A20), Color(0xFF67E8F9), Color(0xFF06B6D4)), // Cyan / Teal
    CoursePalette(Color(0xFF181A08), Color(0xFFD9F99D), Color(0xFF84CC16)), // Lime
    CoursePalette(Color(0xFF260A0E), Color(0xFFFDA4AF), Color(0xFFF43F5E)), // Crimson Coral
    CoursePalette(Color(0xFF241506), Color(0xFFFDE047), Color(0xFFEAB308)), // Yellow Gold
    CoursePalette(Color(0xFF0F1722), Color(0xFFCBD5E1), Color(0xFF64748B)), // Slate
    CoursePalette(Color(0xFF170C24), Color(0xFFE9D5FF), Color(0xFF9333EA)), // Deep Violet
    CoursePalette(Color(0xFF071B1D), Color(0xFF99F6E4), Color(0xFF14B8A6)), // Mint / Seafoam
)
