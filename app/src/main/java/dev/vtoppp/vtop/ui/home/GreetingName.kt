package dev.vtoppp.vtop.ui.home

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import dev.vtoppp.vtop.R

internal data class ParsedDisplayName(
    val firstName: String,
    val secondaryName: String?,
    val secondaryInitials: String? = null,
)

/** Splits raw name into primary first name and secondary middle/last names + initials. */
internal fun parseDisplayName(rawName: String?, fallbackId: String = "Student"): ParsedDisplayName {
    val trimmed = rawName?.trim().orEmpty()
    if (trimmed.isBlank()) {
        val fb = fallbackId.ifBlank { "Student" }
        return ParsedDisplayName(firstName = fb, secondaryName = null, secondaryInitials = null)
    }
    // If it contains digits (like a registration number ID e.g. 24BCY10147), keep as single ID
    if (trimmed.any { it.isDigit() }) {
        return ParsedDisplayName(firstName = trimmed.uppercase(), secondaryName = null, secondaryInitials = null)
    }
    val words = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) {
        return ParsedDisplayName(firstName = fallbackId.ifBlank { "Student" }, secondaryName = null, secondaryInitials = null)
    }
    val first = words.first().lowercase().replaceFirstChar { it.uppercase() }
    val secondary = if (words.size > 1) {
        words.drop(1).joinToString(" ") { word ->
            if (word.length <= 2 && word.all { it.isLetter() }) word.uppercase()
            else word.lowercase().replaceFirstChar { it.uppercase() }
        }
    } else null
    val initials = if (words.size > 1) {
        words.drop(1).mapNotNull { word ->
            val char = word.firstOrNull { it.isLetter() }?.uppercaseChar()
            if (char != null) "$char." else null
        }.joinToString(" ").ifBlank { null }
    } else null
    return ParsedDisplayName(firstName = first, secondaryName = secondary, secondaryInitials = initials)
}


internal data class GreetingTypography(
    val fontFamily: FontFamily,
    val greetingWeight: FontWeight = FontWeight.Medium,
    val nameWeight: FontWeight = FontWeight.ExtraBold,
    val letterSpacing: TextUnit = TextUnit.Unspecified,
)

// ponytail: bundled OFL display fonts; system GenericFontFamily collapses to SysFont on OxygenOS
internal val GreetingFontStyles = listOf(
    GreetingTypography(
        fontFamily = FontFamily(Font(R.font.archivo_black)),
        nameWeight = FontWeight.ExtraBold,
    ),
    GreetingTypography(
        fontFamily = FontFamily(Font(R.font.pacifico)),
        nameWeight = FontWeight.Bold,
    ),
    GreetingTypography(
        fontFamily = FontFamily(Font(R.font.dm_serif_display)),
        greetingWeight = FontWeight.Normal,
        nameWeight = FontWeight.Bold,
    ),
    GreetingTypography(
        fontFamily = FontFamily(Font(R.font.dancing_script)),
        nameWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
    ),
    GreetingTypography(
        fontFamily = FontFamily(Font(R.font.rubik_mono_one)),
        nameWeight = FontWeight.Normal,
        letterSpacing = 1.sp,
    ),
    GreetingTypography(
        fontFamily = FontFamily(Font(R.font.young_serif)),
        nameWeight = FontWeight.ExtraBold,
    ),
)

internal val EnglishGreetings = listOf(
    "Hello,",
    "Hey,",
    "What's up,",
    "Howdy,",
    "Welcome,",
    "Greetings,",
    "Good day,",
    "Yo,",
)

// Material 3 expressive polygon shapes for the organic question card, kept 1:1 aspect ratio
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
internal val QuestionCardShapes = listOf(
    androidx.compose.material3.MaterialShapes.Cookie9Sided,
    androidx.compose.material3.MaterialShapes.Cookie6Sided,
    androidx.compose.material3.MaterialShapes.Cookie4Sided,
    androidx.compose.material3.MaterialShapes.Cookie12Sided,
    androidx.compose.material3.MaterialShapes.Cookie7Sided,
    androidx.compose.material3.MaterialShapes.Sunny,
    androidx.compose.material3.MaterialShapes.VerySunny,
    androidx.compose.material3.MaterialShapes.SoftBurst,
    androidx.compose.material3.MaterialShapes.Flower,
    androidx.compose.material3.MaterialShapes.Puffy,
    androidx.compose.material3.MaterialShapes.PuffyDiamond,
    androidx.compose.material3.MaterialShapes.Clover4Leaf,
    androidx.compose.material3.MaterialShapes.Clover8Leaf,
    androidx.compose.material3.MaterialShapes.SoftBoom,
    androidx.compose.material3.MaterialShapes.Gem,
    androidx.compose.material3.MaterialShapes.ClamShell,
    androidx.compose.material3.MaterialShapes.Bun,
    androidx.compose.material3.MaterialShapes.Arch,
    androidx.compose.material3.MaterialShapes.Slanted,
)

// short campus questions cycled by the question card
internal val IntriguingQuestions = listOf(
    "Ready for CAT-2?",
    "Skipping 8 AM?",
    "Touched grass today?",
    "Night canteen run?",
    "Attendance safe?",
    "Got notes for TEE?",
    "Freshers' party?",
    "Mess food edible?",
    "WiFi working?",
    "OD sanctioned?",
)

// one pick per process so the header is stable mid-session and rerolls only on cold start
internal object SessionGreeting {
    val greeting: String = EnglishGreetings.random()
    val fontIndex: Int = (0 until GreetingFontStyles.size).random()
    val questionIndex: Int = (0 until IntriguingQuestions.size).random()
    val shapeIndex: Int = (0 until QuestionCardShapes.size).random()
}
